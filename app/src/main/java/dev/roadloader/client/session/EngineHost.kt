package dev.roadloader.client.session

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import dev.roadloader.client.data.ClientSettings
import dev.roadloader.client.model.Instance
import dev.roadloader.core.CompanionServiceBase
import dev.roadloader.core.CrashGuard
import dev.roadloader.core.RoadAudioBridge
import dev.roadloader.core.RoadConstants
import dev.roadloader.core.RoadMemory
import dev.roadloader.core.RoadSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Snapshot of the companion's counters, refreshed by the stats poller. */
data class EngineStats(
    val fps: Int = 0,
    val frameCount: Long = 0,
    val pingMs: Long = 0,
    val heapUsedMb: Long = 0,
    val heapLimitMb: Long = 0,
    val heapPeakMb: Long = 0,
    val renderAvgUs: Long = 0,
    val inputPushed: Long = 0,
    val inputConsumed: Long = 0,
    val inputDropped: Long = 0,
    val pid: Int = 0,
    val alive: Boolean = false,
    val selfTest: Long = 0,
    val crashSignal: Long = 0,
    val crashAddress: Long = 0,
    val elapsedMs: Long = 0,
    val audioPushed: Long = 0,
    val status: Int = RoadConstants.STATUS_EMPTY,
) {
    val framesPerSecond: Int get() = fps
}

sealed interface EngineState {
    data object Idle : EngineState
    data class Starting(val instance: Instance) : EngineState
    data class Running(val instance: Instance) : EngineState
    data class Failed(val instance: Instance?, val message: String) : EngineState
}

/**
 * Owns one 32-bit companion: it creates the shared region, binds the `:road32`
 * service, hands the descriptor over Binder, and publishes frames plus counters
 * to the renderer and the HUD.
 */
class EngineHost(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val guard = CrashGuard(context)

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    private val _stats = MutableStateFlow(EngineStats())
    private val _firstFrame = MutableStateFlow(false)
    private val _events = MutableStateFlow<List<String>>(emptyList())

    val state: StateFlow<EngineState> = _state.asStateFlow()
    val stats: StateFlow<EngineStats> = _stats.asStateFlow()
    val firstFrame: StateFlow<Boolean> = _firstFrame.asStateFlow()
    val events: StateFlow<List<String>> = _events.asStateFlow()

    @Volatile
    var session: RoadSession? = null
        private set

    private var audio: RoadAudioBridge? = null
    private var bound = false
    private var messenger: Messenger? = null
    private var pendingDescriptor: ParcelFileDescriptor? = null
    private var pendingSize = 0L
    private var pendingFlags = 0
    private var instance: Instance? = null
    private var startedAt = 0L
    private var lastFrameCount = 0L
    private var lastSampleAt = 0L

    val isRunning: Boolean get() = session != null

    fun launchGuardLocked(): Boolean = guard.isLocked()

    fun resetGuard() = guard.reset()

    /** Creates the region and binds the companion. Must run on the main thread. */
    fun start(
        instance: Instance,
        width: Int,
        height: Int,
        settings: ClientSettings,
    ): String? {
        if (session != null) return "The engine is already running."
        val created = RoadSession.create(width, height)
            ?: return "Could not create the shared region. Native core unavailable."
        session = created
        this.instance = instance
        startedAt = System.currentTimeMillis()
        lastFrameCount = 0
        lastSampleAt = startedAt
        _firstFrame.value = false
        _stats.value = EngineStats()
        _state.value = EngineState.Starting(instance)
        guard.beginAttempt()

        RoadMemory.setLimit(settings.heapLimitMb.coerceAtLeast(256) * 1024L * 1024L)
        val flags = (if (settings.shaders) RoadConstants.FLAG_SHADERS else 0) or
            (if (settings.safeMode) RoadConstants.FLAG_SAFE_MODE else 0)
        pendingDescriptor = ParcelFileDescriptor.fromFd(created.duplicateFd())
        pendingSize = created.regionSize
        pendingFlags = flags

        val intent = Intent(context, CompanionService::class.java)
        val started = runCatching {
            context.startForegroundService(intent)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrElse { error ->
            cleanup()
            _state.value = EngineState.Failed(instance, "Could not start the 32-bit process: ${error.message}")
            return "Could not start the 32-bit process: ${error.message}"
        }
        if (!started) {
            cleanup()
            _state.value = EngineState.Failed(instance, "Android refused to start the 32-bit process.")
            return "Android refused to start the 32-bit process."
        }

        if (settings.streamAudio) {
            audio = RoadAudioBridge(created).also { it.start() }
        }
        handler.post(statsPoll)
        return null
    }

    fun stop(reason: String? = null) {
        val active = session ?: return
        runCatching { active.pushCommand(RoadConstants.CMD_SHUTDOWN, 0) }
        val intent = Intent(context, CompanionService::class.java)
        if (bound) {
            runCatching { context.unbindService(connection) }
            bound = false
        }
        runCatching { context.stopService(intent) }
        lastElapsed = System.currentTimeMillis() - startedAt
        cleanup()
        _state.value = EngineState.Idle
        if (reason != null) pushEvent(reason)
    }

    var lastElapsed: Long = 0
        private set

    fun setShaders(enabled: Boolean) {
        session?.setShaders(enabled)
    }

    fun setSafeMode(enabled: Boolean) {
        session?.setSafeMode(enabled)
    }

    fun setRenderDistance(chunks: Int) {
        session?.setRenderDistance(chunks)
    }

    fun setVolume(volume: Float) {
        session?.setVolume(volume)
    }

    fun requestSelfTest() {
        session?.requestSelfTest()
    }

    fun pushTouch(kind: Int, pointerId: Int, action: Int, x: Float, y: Float, pressure: Float): Boolean =
        session?.pushInput(kind, pointerId, action, x, y, pressure) ?: false

    fun pushKey(keyCode: Int, pressed: Boolean): Boolean =
        session?.pushInput(
            kind = RoadConstants.INPUT_KEY,
            pointerId = 0,
            action = if (pressed) 1 else 0,
            x = 0f,
            y = 0f,
            keyCode = keyCode,
        ) ?: false

    fun pushButton(button: Int, pressed: Boolean): Boolean =
        session?.pushInput(
            kind = RoadConstants.INPUT_BUTTON,
            pointerId = 0,
            action = if (pressed) 1 else 0,
            x = 0f,
            y = 0f,
            button = button,
        ) ?: false

    fun pushEvent(message: String) {
        _events.value = (_events.value + message).takeLast(20)
    }

    private fun cleanup() {
        handler.removeCallbacks(statsPoll)
        audio?.stop()
        audio = null
        pendingDescriptor?.let { runCatching { it.close() } }
        pendingDescriptor = null
        messenger = null
        session?.close()
        session = null
        _firstFrame.value = false
    }

    private val attachReply = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what == CompanionServiceBase.MSG_ATTACHED) {
                if (message.arg1 != 0) {
                    _state.value = EngineState.Failed(
                        instance,
                        "The 32-bit process refused the shared region (code ${message.arg1}).",
                    )
                    stop()
                }
            }
            true
        },
    )

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            bound = true
            val descriptor = pendingDescriptor ?: return
            val target = binder?.let { Messenger(it) } ?: return
            messenger = target
            val data = Bundle().apply {
                putParcelable(CompanionServiceBase.EXTRA_DESCRIPTOR, descriptor)
                putLong(CompanionServiceBase.EXTRA_SIZE, pendingSize)
                putInt(CompanionServiceBase.EXTRA_FLAGS, pendingFlags)
                putLong(CompanionServiceBase.EXTRA_HEAP_LIMIT, RoadMemory.DEFAULT_LIMIT_BYTES)
            }
            val message = Message.obtain(null, CompanionServiceBase.MSG_ATTACH).apply {
                this.data = data
                replyTo = attachReply
            }
            runCatching { target.send(message) }
            pendingDescriptor = null
            runCatching { descriptor.close() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            messenger = null
            if (session != null) {
                _state.value = EngineState.Failed(instance, "The 32-bit process stopped unexpectedly.")
                cleanup()
            }
        }
    }

    private val statsPoll = object : Runnable {
        override fun run() {
            val active = session
            if (active == null) return
            val raw = active.readStats()
            val now = System.currentTimeMillis()
            val deltaFrames = raw.frameCount - lastFrameCount
            val deltaMs = (now - lastSampleAt).coerceAtLeast(1)
            val fps = ((deltaFrames * 1000) / deltaMs).toInt().coerceIn(0, 240)
            if (deltaFrames > 0) {
                lastFrameCount = raw.frameCount
                lastSampleAt = now
            }
            _stats.value = EngineStats(
                fps = fps,
                frameCount = raw.frameCount,
                pingMs = raw.heartbeatAgeMs,
                heapUsedMb = raw.heapUsed / (1024 * 1024),
                heapLimitMb = raw.heapLimit / (1024 * 1024),
                heapPeakMb = raw.heapPeak / (1024 * 1024),
                renderAvgUs = raw.renderAvgUs,
                inputPushed = raw.inputPushed,
                inputConsumed = raw.inputConsumed,
                inputDropped = raw.inputDropped,
                pid = raw.pid,
                alive = raw.isAlive(),
                selfTest = raw.selfTest,
                crashSignal = raw.crashSignal,
                crashAddress = raw.crashAddress,
                elapsedMs = now - startedAt,
                audioPushed = raw.audioPushed,
                status = raw.status,
            )
            if (raw.status == RoadConstants.STATUS_CRASHED) {
                _state.value = EngineState.Failed(
                    instance,
                    "The 32-bit engine crashed (signal ${raw.crashSignal} at 0x${raw.crashAddress.toString(16)}).",
                )
                stop()
                return
            }
            if (raw.frameCount > 0 && !_firstFrame.value) {
                _firstFrame.value = true
                guard.markHealthy()
                _state.value = instance?.let { EngineState.Running(it) } ?: EngineState.Idle
            }
            if (raw.isAlive()) {
                handler.postDelayed(this, STATS_INTERVAL_MS)
            } else if (session != null && raw.status != RoadConstants.STATUS_EMPTY) {
                _state.value = EngineState.Failed(instance, "Lost the heartbeat from the 32-bit engine.")
                stop()
            } else {
                handler.postDelayed(this, STATS_INTERVAL_MS)
            }
        }
    }

    companion object {
        private const val STATS_INTERVAL_MS = 250L
    }
}
