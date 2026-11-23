package dev.roadloader.client.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Engine + client preferences, mirrored into the native HUD config. */
@Serializable
data class ClientSettings(
    val shaders: Boolean = true,
    val safeMode: Boolean = false,
    val renderDistance: Int = 12,
    val heapLimitMb: Int = 1024,
    val volume: Float = 0.8f,
    val autoSignIn: Boolean = true,
    val hudScale: Float = 1f,
    val accentIndex: Int = 0,
    val developerMode: Boolean = false,
    val openMenuOnLaunch: Boolean = false,
    val streamAudio: Boolean = true,
    val menuBackdropBlur: Boolean = true,
    val modules: Map<String, Boolean> = DEFAULT_MODULES,
) {
    companion object {
        val DEFAULT_MODULES: Map<String, Boolean> = linkedMapOf(
            "watermark" to true,
            "fps" to true,
            "ping" to true,
            "memory" to true,
            "cps" to true,
            "session" to true,
            "render" to false,
            "input" to false,
            "engine" to true,
        )
    }
}

class SettingsStore(private val context: Context) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(context.filesDir, "settings.json")
    private val _settings = MutableStateFlow(load())

    val settings: StateFlow<ClientSettings> = _settings.asStateFlow()

    fun current(): ClientSettings = _settings.value

    fun update(transform: (ClientSettings) -> ClientSettings) {
        synchronized(this) {
            val next = transform(_settings.value)
            _settings.value = next
            persist(next)
        }
    }

    fun setModule(id: String, enabled: Boolean) = update {
        it.copy(modules = it.modules.toMutableMap().apply { put(id, enabled) })
    }

    private fun load(): ClientSettings {
        if (!file.exists()) return ClientSettings()
        return runCatching {
            json.decodeFromString(ClientSettings.serializer(), file.readText())
        }.getOrElse { ClientSettings() }
    }

    private fun persist(settings: ClientSettings) {
        runCatching {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(ClientSettings.serializer(), settings))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }
    }

    companion object {
        @Volatile
        private var shared: SettingsStore? = null

        fun get(context: Context): SettingsStore = shared ?: synchronized(this) {
            shared ?: SettingsStore(context.applicationContext).also { shared = it }
        }
    }
}
