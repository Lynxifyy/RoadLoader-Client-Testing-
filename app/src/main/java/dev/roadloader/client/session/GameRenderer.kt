package dev.roadloader.client.session

import android.content.Context
import android.opengl.GLSurfaceView
import dev.roadloader.client.data.ClientSettings
import dev.roadloader.client.data.SettingsStore
import dev.roadloader.client.model.Instance
import dev.roadloader.core.RoadConstants
import java.io.File
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLES 3 renderer: uploads the newest companion frame, then lets the native
 * overlay composite the HUD and the ImGui menu on top.
 *
 * Runs entirely on the GL thread. Anything that touches app storage or the
 * engine is posted back to the main thread.
 */
class GameRenderer(
    private val context: Context,
    private val host: EngineHost,
    private val settings: SettingsStore,
    private val onCommand: (NativeOverlay.Command) -> Unit,
    private val onFrameDrawn: () -> Unit,
) : GLSurfaceView.Renderer {

    private var handle = 0L
    private var instance: Instance? = null
    private var lastFrameAt = 0L
    private var startedAt = 0L

    @Volatile
    private var configuration = settings.current()

    @Volatile
    var menuOpen: Boolean = false
        private set

    fun bind(instance: Instance) {
        this.instance = instance
        val current = handle
        if (current != 0L) {
            NativeOverlay.nativeSetLabels(
                current,
                instance.displayName,
                instance.shortVersion,
                dev.roadloader.core.NativeHost.coreVersion().toString(),
            )
        }
    }

    fun reconfigure(settings: ClientSettings) {
        configuration = settings
        val current = handle
        if (current != 0L) NativeOverlay.configure(current, settings)
    }

    fun setMenuOpen(open: Boolean) {
        menuOpen = open
        val current = handle
        if (current != 0L) NativeOverlay.nativeSetMenuOpen(current, open)
    }

    fun toast(message: String) {
        val current = handle
        if (current != 0L) NativeOverlay.nativeToast(current, message)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val configPath = File(context.filesDir, "hud.json").absolutePath
        handle = NativeOverlay.nativeCreate(context.assets, configPath)
        startedAt = System.nanoTime()
        val active = handle
        if (active != 0L) {
            NativeOverlay.nativeSetDensity(active, context.resources.displayMetrics.density)
            NativeOverlay.configure(active, configuration)
            instance?.let {
                NativeOverlay.nativeSetLabels(
                    active,
                    it.displayName,
                    it.shortVersion,
                    dev.roadloader.core.NativeHost.coreVersion().toString(),
                )
            }
            NativeOverlay.nativeSetMenuOpen(active, menuOpen)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        if (handle != 0L) NativeOverlay.nativeSurfaceChanged(handle, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val active = handle
        if (active == 0L) return
        val now = System.nanoTime()
        val dt = if (lastFrameAt == 0L) 1f / 60f else ((now - lastFrameAt) / 1_000_000_000f)
        lastFrameAt = now

        var buffer: java.nio.ByteBuffer? = null
        var frameWidth = 0
        var frameHeight = 0
        var stride = 0
        var slot = -1
        host.session?.let { session ->
            session.pollFrame()?.let { frame ->
                buffer = frame.buffer
                frameWidth = frame.width
                frameHeight = frame.height
                stride = frame.stride
                slot = frame.slot
            }
        }

        NativeOverlay.nativeRender(
            handle,
            buffer,
            frameWidth,
            frameHeight,
            stride,
            dt.coerceIn(0f, 0.1f),
            statsArray(),
        )
        if (slot >= 0) host.session?.releaseFrame(slot)
        if (buffer != null) onFrameDrawn()
        drainCommands()
    }

    private fun statsArray(): LongArray {
        val stats = host.stats.value
        return longArrayOf(
            stats.fps.toLong(),
            stats.frameCount,
            stats.pingMs,
            stats.heapUsedMb,
            stats.heapLimitMb,
            stats.renderAvgUs,
            stats.inputPushed,
            stats.inputConsumed,
            stats.inputDropped,
            stats.pid.toLong(),
            if (stats.alive) 1L else 0L,
            stats.elapsedMs,
            stats.audioPushed,
            stats.status.toLong(),
            stats.selfTest,
            0L,
        )
    }

    private fun drainCommands() {
        val active = handle
        if (active == 0L) return
        repeat(8) {
            val packed = NativeOverlay.nativePollCommand(active)
            if (packed == 0L) return
            NativeOverlay.decode(packed)?.let(onCommand)
        }
    }

    /** True when the overlay consumed the touch, so the engine must not see it. */
    fun touch(action: Int, x: Float, y: Float): Boolean {
        val active = handle
        if (active == 0L) return false
        val consumed = NativeOverlay.nativeTouch(active, action, x, y)
        menuOpen = NativeOverlay.nativeIsMenuOpen(active)
        return consumed
    }

    fun release() {
        val active = handle
        handle = 0L
        if (active != 0L) NativeOverlay.nativeDestroy(active)
    }

    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val ACTION_CANCEL = 3
    }
}
