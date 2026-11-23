package dev.roadloader.client.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** Public (non-secret) account list persistence. */
interface AccountStorage {
    fun load(): List<Account>
    fun save(accounts: List<Account>)
    fun loadActiveId(): String?
    fun saveActiveId(id: String?)
}

class SharedPrefsAccountStorage(context: Context) : AccountStorage {
    private val prefs = context.applicationContext.getSharedPreferences("roadclient.accounts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun load(): List<Account> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Account.serializer()), raw)
        }.getOrElse { emptyList() }
    }

    override fun save(accounts: List<Account>) {
        prefs.edit().putString(
            KEY_ACCOUNTS,
            json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Account.serializer()), accounts),
        ).apply()
    }

    override fun loadActiveId(): String? = prefs.getString(KEY_ACTIVE, null)

    override fun saveActiveId(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, id)
        }.apply()
    }

    private companion object {
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_ACTIVE = "active"
    }
}

/**
 * Owns the account list, the active account and the silent refresh path.
 *
 * Tokens are written to [SecretStore] only; the JSON that reaches
 * SharedPreferences carries the gamertag and nothing that can be replayed.
 */
class AccountRepository(
    private val storage: AccountStorage,
    private val secrets: SecretStore,
    private val auth: MicrosoftAuth,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val initialAccounts = storage.load()
    private val _accounts = MutableStateFlow(initialAccounts)
    private val _active = MutableStateFlow(
        initialAccounts.firstOrNull { it.id == storage.loadActiveId() },
    )

    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    val active: StateFlow<Account?> = _active.asStateFlow()

    fun activeAccount(): Account? = _active.value

    fun select(id: String?) {
        _active.value = _accounts.value.firstOrNull { it.id == id }
        storage.saveActiveId(id)
    }

    /** Full interactive sign-in; call from a background dispatcher. */
    fun signIn(device: DeviceCode, onStage: (String) -> Unit = {}): Account {
        val credentials = auth.signIn(device, onStage)
        return persist(credentials)
    }

    /** Refreshes the stored credentials when they are close to expiry. */
    fun refreshIfNeeded(account: Account): Account? {
        val credentials = credentials(account.id) ?: return null
        if (!credentials.isMsaExpired(clock()) && !credentials.isMcExpired(clock())) return account
        val refreshed = runCatching {
            val msa = auth.refresh(credentials.msaRefreshToken)
            auth.reauthorize(msa, credentials)
        }.getOrNull() ?: return null
        return persist(refreshed)
    }

    fun credentials(accountId: String): Credentials? {
        val raw = secrets.load(secretKey(accountId)) ?: return null
        return runCatching { json.decodeFromString(Credentials.serializer(), raw) }.getOrNull()
    }

    fun signOut(account: Account) {
        secrets.delete(secretKey(account.id))
        val remaining = _accounts.value.filterNot { it.id == account.id }
        _accounts.value = remaining
        storage.save(remaining)
        if (_active.value?.id == account.id) select(remaining.firstOrNull()?.id)
    }

    private fun persist(credentials: Credentials): Account {
        secrets.save(secretKey(credentials.xuid), json.encodeToString(Credentials.serializer(), credentials))
        val account = Account(
            id = credentials.xuid,
            gamertag = credentials.gamertag,
            minecraftName = credentials.mcName,
            minecraftUuid = credentials.mcUuid,
            addedAt = _accounts.value.firstOrNull { it.id == credentials.xuid }?.addedAt ?: clock(),
        )
        val merged = (listOf(account) + _accounts.value.filterNot { it.id == account.id })
        _accounts.value = merged
        storage.save(merged)
        select(account.id)
        return account
    }

    private fun secretKey(accountId: String) = "credentials.$accountId"
}
