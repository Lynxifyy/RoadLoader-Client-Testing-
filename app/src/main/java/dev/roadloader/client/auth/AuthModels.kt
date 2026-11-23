package dev.roadloader.client.auth

import kotlinx.serialization.Serializable

/** Device-code challenge shown to the user while signing in. */
data class DeviceCode(
    val userCode: String,
    val deviceCode: String,
    val verificationUri: String,
    val expiresInSec: Long,
    val intervalSec: Long,
)

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSec: Long,
)

data class XboxToken(val token: String, val userHash: String, val xuid: String)

data class MinecraftProfile(val id: String, val name: String)

/** Secret material. Never persisted in plain text; see [SecretStore]. */
@Serializable
data class Credentials(
    val xuid: String,
    val gamertag: String,
    val msaAccessToken: String,
    val msaRefreshToken: String,
    val msaExpiresAt: Long,
    val mcAccessToken: String? = null,
    val mcExpiresAt: Long = 0,
    val mcName: String? = null,
    val mcUuid: String? = null,
) {
    fun isMsaExpired(now: Long = System.currentTimeMillis()): Boolean = now >= msaExpiresAt - 60_000
    fun isMcExpired(now: Long = System.currentTimeMillis()): Boolean =
        mcAccessToken == null || now >= mcExpiresAt - 60_000
}

/** Public account card shown in the UI. */
@Serializable
data class Account(
    val id: String,
    val gamertag: String,
    val minecraftName: String? = null,
    val minecraftUuid: String? = null,
    val addedAt: Long = 0,
) {
    val displayName: String get() = minecraftName ?: gamertag
    val initial: String get() = displayName.take(1).uppercase()
}

class AuthException(message: String, val code: String? = null) : Exception(message)

object AuthErrors {
    const val NO_XBOX_ACCOUNT = "2148916233"
    const val CHILD_ACCOUNT = "2148916238"
    const val BANNED = "2148916235"
    const val PENDING = "authorization_pending"
    const val SLOW_DOWN = "slow_down"
    const val EXPIRED = "expired_token"
    const val DECLINED = "authorization_declined"
}
