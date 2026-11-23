package dev.roadloader.client.session

import android.content.res.AssetManager
import dev.roadloader.client.data.ClientSettings

/**
 * JNI surface of `libroadclient.so`: the GLES 3 renderer that composites the
 * streamed frame with the Dear ImGui HUD and mod menu.
 */
object NativeOverlay {
    init {
        System.loadLibrary("roadclient")
    }

    /** One decoded command raised by the mod menu. */
    data class Command(val id: Int, val value: Int)

    external fun nativeCreate(assets: AssetManager, configPath: String): Long
    external fun nativeDestroy(handle: Long)
    external fun nativeSurfaceChanged(handle: Long, width: Int, height: Int)
    external fun nativeRender(
        handle: Long,
        pixels: java.nio.ByteBuffer?,
        frameWidth: Int,
        frameHeight: Int,
        stride: Int,
        dt: Float,
        stats: LongArray,
    )
    external fun nativeTouch(handle: Long, action: Int, x: Float, y: Float): Boolean
    external fun nativeSetMenuOpen(handle: Long, open: Boolean)
    external fun nativeIsMenuOpen(handle: Long): Boolean
    external fun nativeSetDensity(handle: Long, density: Float)
    external fun nativeSetLabels(handle: Long, instance: String, version: String, engine: String)
    external fun nativeConfigure(
        handle: Long,
        accent: Int,
        hudScale: Float,
        opacity: Float,
        blur: Boolean,
        modules: Int,
        shaders: Boolean,
        safeMode: Boolean,
        volume: Float,
        renderDistance: Int,
    )
    external fun nativePollCommand(handle: Long): Long
    external fun nativeToast(handle: Long, text: String)
    external fun nativeVersion(): String

    fun configure(handle: Long, settings: ClientSettings) {
        if (handle == 0L) return
        nativeConfigure(
            handle = handle,
            accent = settings.accentIndex,
            hudScale = settings.hudScale,
            opacity = 1f,
            blur = settings.menuBackdropBlur,
            modules = moduleMask(settings),
            shaders = settings.shaders,
            safeMode = settings.safeMode,
            volume = settings.volume,
            renderDistance = settings.renderDistance,
        )
    }

    /** Packs the enabled HUD modules into the native bitmask. */
    fun moduleMask(settings: ClientSettings): Int {
        val order = listOf(
            "watermark",
            "fps",
            "ping",
            "memory",
            "cps",
            "session",
            "render",
            "input",
            "engine",
        )
        var mask = 0
        order.forEachIndexed { index, id ->
            if (settings.modules[id] == true) mask = mask or (1 shl index)
        }
        return mask
    }

    /** Module ids in the same order the native bitmask uses. */
    val moduleIds: List<String> = listOf(
        "watermark",
        "fps",
        "ping",
        "memory",
        "cps",
        "session",
        "render",
        "input",
        "engine",
    )

    fun decode(packed: Long): Command? {
        if (packed == 0L) return null
        val id = (packed ushr 32).toInt()
        val value = (packed and 0xFFFFFFFFL).toInt()
        return if (id == 0) null else Command(id, value)
    }

    object CommandId {
        const val SET_SHADERS = 1
        const val SET_SAFE_MODE = 2
        const val SET_RENDER_DISTANCE = 3
        const val REQUEST_SELF_TEST = 4
        const val SET_VOLUME = 5
        const val SET_ACCENT = 6
        const val SET_MODULES = 7
        const val SET_HUD_SCALE = 8
        const val SET_PANEL_OPACITY = 9
        const val SET_BLUR = 10
        const val CLOSE_MENU = 11
        const val STOP_ENGINE = 12
    }
}
