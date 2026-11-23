package dev.roadloader.client.auth

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class HttpResult(val code: Int, val body: String) {
    val isSuccess: Boolean get() = code in 200..299
}

/**
 * The auth flow only needs four verbs, so the transport stays this small and
 * the whole sign-in chain can be unit tested against a fake.
 */
interface HttpTransport {
    fun postForm(url: String, fields: Map<String, String>): HttpResult
    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): HttpResult
    fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult
}

class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : HttpTransport {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override fun postForm(url: String, fields: Map<String, String>): HttpResult {
        val body = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        return execute(Request.Builder().url(url).post(body).build())
    }

    override fun postJson(url: String, body: String, headers: Map<String, String>): HttpResult {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMedia))
        headers.forEach { (key, value) -> builder.header(key, value) }
        return execute(builder.build())
    }

    override fun get(url: String, headers: Map<String, String>): HttpResult {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (key, value) -> builder.header(key, value) }
        return execute(builder.build())
    }

    private fun execute(request: Request): HttpResult = client.newCall(request).execute().use { response ->
        HttpResult(response.code, response.body?.string().orEmpty())
    }
}
