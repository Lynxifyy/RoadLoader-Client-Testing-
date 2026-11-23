package dev.roadloader.client.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.roadloader.client.auth.Account
import dev.roadloader.client.auth.AccountRepository
import dev.roadloader.client.auth.AuthException
import dev.roadloader.client.auth.DeviceCode
import dev.roadloader.client.auth.KeystoreSecretStore
import dev.roadloader.client.auth.MicrosoftAuth
import dev.roadloader.client.auth.OkHttpTransport
import dev.roadloader.client.auth.SharedPrefsAccountStorage
import dev.roadloader.client.data.ClientSettings
import dev.roadloader.client.data.InstanceImporter
import dev.roadloader.client.data.InstanceStore
import dev.roadloader.client.data.SettingsStore
import dev.roadloader.client.model.Instance
import dev.roadloader.client.session.EngineHost
import dev.roadloader.client.session.EngineState
import dev.roadloader.client.session.EngineStats
import dev.roadloader.client.session.NativeOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ClientTab { Home, Worlds, Mods, Account, Settings }

sealed interface ImportState {
    data object Idle : ImportState
    data class Working(val label: String) : ImportState
    data class Done(val instance: Instance) : ImportState
    data class Failed(val message: String) : ImportState
}

sealed interface AuthState {
    data object Idle : AuthState
    data class Requesting(val message: String) : AuthState
    data class Waiting(val code: DeviceCode, val stage: String) : AuthState
    data class Failed(val message: String) : AuthState
}

data class ClientUiState(
    val tab: ClientTab = ClientTab.Home,
    val instances: List<Instance> = emptyList(),
    val selectedId: String? = null,
    val settings: ClientSettings = ClientSettings(),
    val accounts: List<Account> = emptyList(),
    val activeAccount: Account? = null,
    val engine: EngineState = EngineState.Idle,
    val stats: EngineStats = EngineStats(),
    val firstFrame: Boolean = false,
    val importState: ImportState = ImportState.Idle,
    val auth: AuthState = AuthState.Idle,
    val inGame: Boolean = false,
    val banner: String? = null,
    val autoSignInNote: String? = null,
) {
    val selected: Instance? get() = instances.firstOrNull { it.id == selectedId } ?: instances.firstOrNull()
}

/**
 * Single source of truth for the client: library, settings, account, engine and
 * the import/sign-in flows. The Compose tree renders from this and calls the
 * actions below.
 */
class ClientViewModel(application: Application) : AndroidViewModel(application) {
    private val instanceStore = InstanceStore.get(application)
    private val settingsStore = SettingsStore.get(application)
    private val importer = InstanceImporter(application)
    private val engineHost = EngineHost(application)
    private val auth = MicrosoftAuth(OkHttpTransport())
    private val accounts = AccountRepository(
        storage = SharedPrefsAccountStorage(application),
        secrets = KeystoreSecretStore(application),
        auth = auth,
    )

    private val _state = MutableStateFlow(ClientUiState())
    val state: StateFlow<ClientUiState> = _state.asStateFlow()

    val host: EngineHost get() = engineHost

    private var signInJob: Job? = null
    private var importJob: Job? = null
    private var autoSignInAttempted = false

    init {
        viewModelScope.launch {
            instanceStore.library.collect { library ->
                _state.update { it.copy(instances = library.instances, selectedId = library.selectedId) }
            }
        }
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            accounts.accounts.collect { list -> _state.update { it.copy(accounts = list) } }
        }
        viewModelScope.launch {
            accounts.active.collect { account -> _state.update { it.copy(activeAccount = account) } }
        }
        viewModelScope.launch {
            engineHost.state.collect { engine ->
                _state.update {
                    it.copy(
                        engine = engine,
                        inGame = engine is EngineState.Running || engine is EngineState.Starting,
                    )
                }
            }
        }
        viewModelScope.launch {
            engineHost.stats.collect { stats -> _state.update { it.copy(stats = stats) } }
        }
        viewModelScope.launch {
            engineHost.firstFrame.collect { first -> _state.update { it.copy(firstFrame = first) } }
        }
        viewModelScope.launch {
            engineHost.events.collect { events ->
                events.lastOrNull()?.let { message -> _state.update { it.copy(banner = message) } }
            }
        }
    }

    // ------------------------------------------------------------------ ui

    fun selectTab(tab: ClientTab) = _state.update { it.copy(tab = tab) }

    fun dismissBanner() = _state.update { it.copy(banner = null, autoSignInNote = null) }

    fun selectInstance(id: String) {
        instanceStore.select(id)
        _state.update { it.copy(selectedId = id) }
    }

    fun removeInstance(id: String) {
        instanceStore.remove(id)
        _state.update { it.copy(banner = "Instance removed") }
    }

    fun updateSettings(transform: (ClientSettings) -> ClientSettings) {
        settingsStore.update(transform)
        settingsStore.current().let { updated ->
            engineHost.setShaders(updated.shaders)
            engineHost.setSafeMode(updated.safeMode)
            engineHost.setRenderDistance(updated.renderDistance)
            engineHost.setVolume(updated.volume)
        }
    }

    fun setModule(id: String, enabled: Boolean) = updateSettings { settings ->
        settings.copy(modules = settings.modules.toMutableMap().apply { put(id, enabled) })
    }

    // -------------------------------------------------------------- import

    fun importApk(uri: Uri) {
        if (importJob?.isActive == true) return
        _state.update { it.copy(importState = ImportState.Working("Reading the APK")) }
        importJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { importer.import(uri) }
            val instance = result.instance
            if (instance == null) {
                _state.update { it.copy(importState = ImportState.Failed(result.error ?: "Import failed")) }
                return@launch
            }
            instanceStore.upsert(instance)
            _state.update {
                it.copy(importState = ImportState.Done(instance), banner = "Added ${instance.displayName}")
            }
        }
    }

    fun dismissImport() = _state.update { it.copy(importState = ImportState.Idle) }

    // --------------------------------------------------------------- auth

    fun signIn() {
        if (signInJob?.isActive == true) return
        _state.update { it.copy(auth = AuthState.Requesting("Contacting Microsoft")) }
        signInJob = viewModelScope.launch {
            val device = withContext(Dispatchers.IO) {
                runCatching { auth.requestDeviceCode() }
            }.getOrElse { error ->
                _state.update {
                    it.copy(auth = AuthState.Failed(error.message ?: "Could not reach Microsoft"))
                }
                return@launch
            }
            _state.update { it.copy(auth = AuthState.Waiting(device, "Waiting for the browser")) }
            val account = withContext(Dispatchers.IO) {
                runCatching {
                    accounts.signIn(device) { stage ->
                        _state.update { current ->
                            current.copy(
                                auth = (current.auth as? AuthState.Waiting)?.copy(stage = stage)
                                    ?: AuthState.Waiting(device, stage),
                            )
                        }
                    }
                }
            }.getOrElse { error ->
                val message = if (error is AuthException) {
                    error.message ?: "Sign-in failed"
                } else {
                    error.message ?: "Sign-in failed"
                }
                _state.update { it.copy(auth = AuthState.Failed(message)) }
                return@launch
            }
            _state.update {
                it.copy(auth = AuthState.Idle, banner = "Signed in as ${account.displayName}")
            }
        }
    }

    fun cancelSignIn() {
        signInJob?.cancel()
        signInJob = null
        _state.update { it.copy(auth = AuthState.Idle) }
    }

    fun signOut() {
        val account = accounts.activeAccount() ?: return
        accounts.signOut(account)
        _state.update { it.copy(banner = "Signed out") }
    }

    fun setAutoSignIn(enabled: Boolean) = updateSettings { it.copy(autoSignIn = enabled) }

    /** Refreshes the stored session quietly so launching never blocks on it. */
    fun ensureAccountFresh(onResult: (Account?) -> Unit = {}) {
        val account = accounts.activeAccount()
        if (account == null) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val refreshed = withContext(Dispatchers.IO) { accounts.refreshIfNeeded(account) }
            if (refreshed != null && refreshed.displayName != account.displayName) {
                _state.update { it.copy(autoSignInNote = "Signed in as ${refreshed.displayName}") }
            }
            onResult(refreshed ?: account)
        }
    }

    // ------------------------------------------------------------- engine

    /**
     * Starts the companion for [instance]. Returns null on success or a message
     * the caller should surface.
     */
    fun launch(instance: Instance, width: Int, height: Int): String? {
        val settings = settingsStore.current()
        if (engineHost.launchGuardLocked()) {
            return "The 32-bit engine failed three times in a row. Enable Safe Mode and retry."
        }
        val account = accounts.activeAccount()
        if (settings.autoSignIn && account != null) {
            viewModelScope.launch {
                val refreshed = withContext(Dispatchers.IO) { accounts.refreshIfNeeded(account) }
                if (refreshed != null) instanceStore.bindAccount(instance.id, refreshed.id)
            }
        }
        val error = engineHost.start(instance, width, height, settings)
        if (error == null) {
            _state.update { it.copy(inGame = true, firstFrame = false) }
        }
        return error
    }

    fun stopEngine() {
        val instance = state.value.selected
        val elapsed = engineHost.lastElapsed
        engineHost.stop("Engine stopped")
        instance?.let { instanceStore.recordPlay(it.id, elapsed) }
        _state.update { it.copy(inGame = false) }
    }

    fun resetCrashGuard() {
        engineHost.resetGuard()
        _state.update { it.copy(banner = "Crash guard reset") }
    }

    /** Applies a command raised by the in-game ImGui menu. */
    fun handleMenuCommand(command: NativeOverlay.Command) {
        when (command.id) {
            NativeOverlay.CommandId.SET_SHADERS ->
                updateSettings { it.copy(shaders = command.value != 0) }
            NativeOverlay.CommandId.SET_SAFE_MODE ->
                updateSettings { it.copy(safeMode = command.value != 0) }
            NativeOverlay.CommandId.SET_RENDER_DISTANCE ->
                updateSettings { it.copy(renderDistance = command.value.coerceIn(2, 32)) }
            NativeOverlay.CommandId.REQUEST_SELF_TEST -> engineHost.requestSelfTest()
            NativeOverlay.CommandId.SET_VOLUME ->
                updateSettings { it.copy(volume = (command.value / 100f).coerceIn(0f, 1f)) }
            NativeOverlay.CommandId.SET_ACCENT ->
                updateSettings { it.copy(accentIndex = command.value.coerceIn(0, 4)) }
            NativeOverlay.CommandId.SET_MODULES -> {
                val ids = NativeOverlay.moduleIds
                updateSettings { settings ->
                    settings.copy(
                        modules = settings.modules.toMutableMap().apply {
                            ids.forEachIndexed { index, id ->
                                put(id, command.value and (1 shl index) != 0)
                            }
                        },
                    )
                }
            }
            NativeOverlay.CommandId.SET_HUD_SCALE ->
                updateSettings { it.copy(hudScale = (command.value / 100f).coerceIn(0.75f, 1.55f)) }
            NativeOverlay.CommandId.SET_PANEL_OPACITY -> Unit
            NativeOverlay.CommandId.SET_BLUR ->
                updateSettings { it.copy(menuBackdropBlur = command.value != 0) }
            NativeOverlay.CommandId.STOP_ENGINE -> stopEngine()
            else -> Unit
        }
    }

    override fun onCleared() {
        engineHost.stop("Client closed")
        super.onCleared()
    }
}
