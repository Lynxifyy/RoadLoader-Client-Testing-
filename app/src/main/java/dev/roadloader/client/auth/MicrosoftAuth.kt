package dev.roadloader.client.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Microsoft account sign-in for Minecraft.
 *
 * The chain is the one every third-party launcher uses:
 * device code -> Microsoft token -> Xbox Live user token -> XSTS token ->
 * Minecraft services token -> profile. Each step is a separate function so a
 * failure names the stage that failed instead of a generic "sign in failed".
 */
class MicrosoftAuth(
    private val transport: HttpTransport,
    private val clientId: String = DEFAULT_CLIENT_ID,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun requestDeviceCode(): DeviceCode {
        val result = transport.postForm(
            DEVICE_CODE_URL,
            mapOf(
                "client_id" to clientId,
                "scope" to SCOPE,
                "response_type" to "device_code",
            ),
        )
        val body = parse(result, "Microsoft rejected the device code request")
        val userCode = body["user_code"]?.jsonPrimitive?.content
            ?: throw AuthException("Microsoft did not return a user code")
        val deviceCode = body["device_code"]?.jsonPrimitive?.content
            ?: throw AuthException("Microsoft did not return a device code")
        return DeviceCode(
            userCode = userCode,
            deviceCode = deviceCode,
            verificationUri = body["verification_uri"]?.jsonPrimitive?.content ?: DEFAULT_VERIFY_URI,
            expiresInSec = body["expires_in"]?.jsonPrimitive?.longOrNull ?: 900,
            intervalSec = body["interval"]?.jsonPrimitive?.longOrNull ?: 5,
        )
    }

    /** Blocks until the user finishes the browser step, or the code expires. */
    fun awaitToken(device: DeviceCode): TokenPair {
        val deadline = clock() + device.expiresInSec * 1000
        var interval = device.intervalSec.coerceAtLeast(1)
        while (clock() < deadline) {
            sleep(interval * 1000)
            val result = transport.postForm(
                TOKEN_URL,
                mapOf(
                    "client_id" to clientId,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    "device_code" to device.deviceCode,
                ),
            )
            val body = runCatching { json.parseToJsonElement(result.body).jsonObject }.getOrNull()
            val error = body?.get("error")?.jsonPrimitive?.content
            when {
                result.isSuccess && body != null -> return tokenPair(body)
                error == AuthErrors.PENDING -> Unit
                error == AuthErrors.SLOW_DOWN -> interval += 2
                error == AuthErrors.EXPIRED -> throw AuthException("The sign-in code expired. Start again.", error)
                error == AuthErrors.DECLINED -> throw AuthException("Sign-in was declined.", error)
                else -> throw AuthException(
                    body?.get("error_description")?.jsonPrimitive?.content
                        ?: "Microsoft returned HTTP ${result.code} while polling",
                    error,
                )
            }
        }
        throw AuthException("The sign-in code expired. Start again.", AuthErrors.EXPIRED)
    }

    fun refresh(refreshToken: String): TokenPair {
        val result = transport.postForm(
            TOKEN_URL,
            mapOf(
                "client_id" to clientId,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "scope" to SCOPE,
            ),
        )
        return tokenPair(parse(result, "Could not refresh the Microsoft session"))
    }

    fun xboxLive(msaAccessToken: String): XboxToken {
        val payload = """
            {"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=$msaAccessToken"},
             "RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}
        """.trimIndent()
        val result = transport.postJson(
            XBL_URL,
            payload,
            mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
        )
        val body = parse(result, "Xbox Live refused the sign-in")
        val token = body["Token"]?.jsonPrimitive?.content
            ?: throw AuthException("Xbox Live did not return a token")
        val claims = (body["DisplayClaims"]?.jsonObject?.get("xui") as? JsonArray)
            ?.firstOrNull()
            ?.jsonObject
            ?: throw AuthException("Xbox Live did not return account claims")
        return XboxToken(
            token = token,
            userHash = claims["uhs"]?.jsonPrimitive?.content ?: throw AuthException("Missing user hash"),
            xuid = claims["xid"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    fun xsts(xblToken: String): String {
        val payload = """
            {"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},
             "RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}
        """.trimIndent()
        val result = transport.postJson(
            XSTS_URL,
            payload,
            mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
        )
        val body = runCatching { json.parseToJsonElement(result.body).jsonObject }.getOrNull()
        val xerr = body?.get("XErr")?.jsonPrimitive?.content
        if (xerr != null) {
            throw AuthException(
                when (xerr) {
                    AuthErrors.NO_XBOX_ACCOUNT -> "That Microsoft account has no Xbox profile. Create one at xbox.com, then retry."
                    AuthErrors.CHILD_ACCOUNT -> "This is a child account. An adult must add it to a Microsoft family first."
                    AuthErrors.BANNED -> "Xbox Live has suspended this account."
                    else -> "Xbox Live refused the request (XErr $xerr)."
                },
                xerr,
            )
        }
        val parsed = parse(result, "Xbox Live security token request failed")
        return parsed["Token"]?.jsonPrimitive?.content
            ?: throw AuthException("Xbox Live did not return an XSTS token")
    }

    fun minecraftLogin(userHash: String, xstsToken: String): TokenPair {
        val payload = """{"identityToken":"XBL3.0 x=$userHash;$xstsToken"}"""
        val result = transport.postJson(
            MC_LOGIN_URL,
            payload,
            mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
        )
        val body = parse(result, "Minecraft services refused the token")
        val token = body["access_token"]?.jsonPrimitive?.content
            ?: throw AuthException("Minecraft services did not return an access token")
        return TokenPair(
            accessToken = token,
            refreshToken = "",
            expiresInSec = body["expires_in"]?.jsonPrimitive?.longOrNull ?: 86_400,
        )
    }

    fun profile(mcAccessToken: String): MinecraftProfile {
        val result = transport.get(
            MC_PROFILE_URL,
            mapOf("Authorization" to "Bearer $mcAccessToken"),
        )
        if (result.code == 404) throw AuthException("That account does not own Minecraft.")
        val body = parse(result, "Could not read the Minecraft profile")
        return MinecraftProfile(
            id = body["id"]?.jsonPrimitive?.content ?: throw AuthException("Profile without an id"),
            name = body["name"]?.jsonPrimitive?.content ?: throw AuthException("Profile without a name"),
        )
    }

    /** Runs the full chain and returns the secret material to store. */
    fun signIn(device: DeviceCode, onStage: (String) -> Unit = {}): Credentials {
        onStage("Waiting for the browser")
        val msa = awaitToken(device)
        onStage("Signing in to Xbox Live")
        val xbl = xboxLive(msa.accessToken)
        onStage("Requesting an XSTS token")
        val xsts = xsts(xbl.token)
        onStage("Signing in to Minecraft")
        val mc = minecraftLogin(xbl.userHash, xsts)
        onStage("Reading the profile")
        val profile = runCatching { profile(mc.accessToken) }.getOrNull()
        val now = clock()
        return Credentials(
            xuid = xbl.xuid.ifBlank { xbl.userHash },
            gamertag = profile?.name ?: "Xbox player",
            msaAccessToken = msa.accessToken,
            msaRefreshToken = msa.refreshToken,
            msaExpiresAt = now + msa.expiresInSec * 1000,
            mcAccessToken = mc.accessToken,
            mcExpiresAt = now + mc.expiresInSec * 1000,
            mcName = profile?.name,
            mcUuid = profile?.id,
        )
    }

    /** Re-runs the Xbox + Minecraft stages on a refreshed Microsoft token. */
    fun reauthorize(msa: TokenPair, previous: Credentials): Credentials {
        val xbl = xboxLive(msa.accessToken)
        val xsts = xsts(xbl.token)
        val mc = minecraftLogin(xbl.userHash, xsts)
        val profile = runCatching { profile(mc.accessToken) }.getOrNull()
        val now = clock()
        return previous.copy(
            gamertag = profile?.name ?: previous.gamertag,
            msaAccessToken = msa.accessToken,
            msaRefreshToken = msa.refreshToken,
            msaExpiresAt = now + msa.expiresInSec * 1000,
            mcAccessToken = mc.accessToken,
            mcExpiresAt = now + mc.expiresInSec * 1000,
            mcName = profile?.name ?: previous.mcName,
            mcUuid = profile?.id ?: previous.mcUuid,
        )
    }

    private fun parse(result: HttpResult, message: String): JsonObject {
        val element = runCatching { json.parseToJsonElement(result.body) }.getOrNull()
        if (!result.isSuccess || element !is JsonObject) {
            val detail = element?.let { runCatching { it.jsonObject["error_description"]?.jsonPrimitive?.content }.getOrNull() }
            throw AuthException(detail ?: "$message (HTTP ${result.code})")
        }
        return element
    }

    private fun tokenPair(body: JsonObject): TokenPair {
        val access = body["access_token"]?.jsonPrimitive?.content
            ?: throw AuthException("Microsoft did not return an access token")
        return TokenPair(
            accessToken = access,
            refreshToken = body["refresh_token"]?.jsonPrimitive?.content.orEmpty(),
            expiresInSec = body["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600,
        )
    }

    companion object {
        /** Public client id used by the legacy Minecraft launcher device flow. */
        const val DEFAULT_CLIENT_ID = "00000000402b5328"
        const val SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"
        const val DEFAULT_VERIFY_URI = "https://www.microsoft.com/link"

        const val DEVICE_CODE_URL = "https://login.live.com/oauth20_connect.srf"
        const val TOKEN_URL = "https://login.live.com/oauth20_token.srf"
        const val XBL_URL = "https://user.auth.xboxlive.com/user/authenticate"
        const val XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"
        const val MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox"
        const val MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"
    }
}
