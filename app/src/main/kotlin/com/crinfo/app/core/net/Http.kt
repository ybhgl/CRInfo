package com.crinfo.app.core.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApiException(message: String, val httpCode: Int = 0) : IOException(message)

private class MemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val kept = cookies.filter { it.expiresAt > System.currentTimeMillis() }
        store.compute(url.host) { _, old -> (old ?: mutableListOf()).apply { clear(); addAll(kept) } }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[url.host]?.filter { it.matches(url) } ?: emptyList()
}

private val FORM = "application/x-www-form-urlencoded".toMediaType()
private val JSON = "application/json; charset=utf-8".toMediaType()

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(MemoryCookieJar())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        // 12306 用 302 跳错误页表示会话失效，必须自己看到 302 才能触发重握手
        .followRedirects(false)
        .build()

    suspend fun get(
        url: String,
        referer: String? = null,
        accept: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String =
        execute(newRequestBuilder(url).apply {
            referer?.let { header("Referer", it) }
            accept?.let { header("Accept", it) }
            // 个别接口按 UA / Content-Type 分流，抓包发什么就发什么；header() 会覆盖默认值
            headers.forEach { (name, value) -> header(name, value) }
        }.build())

    suspend fun postForm(
        url: String,
        params: Map<String, String>,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val body = params.entries.joinToString("&") { (k, v) ->
            "${k.encodeForm()}=${v.encodeForm()}"
        }.toRequestBody(FORM)
        return execute(newRequestBuilder(url).post(body).apply {
            referer?.let { header("Referer", it) }
            // 个别接口按 Content-Type/UA 分流业务态，抓包发什么就发什么（与 get 同一口径）
            headers.forEach { (name, value) -> header(name, value) }
        }.build())
    }

    suspend fun postJson(url: String, json: String): String =
        execute(newRequestBuilder(url).post(json.toRequestBody(JSON)).build())

    private fun newRequestBuilder(url: String) = Request.Builder()
        .url(url.toHttpUrl())
        .header("User-Agent", USER_AGENT)

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val code = response.code
            val body = response.body?.string() ?: ""
            if (code !in 200..299) {
                throw ApiException("HTTP $code ${request.url.host}${request.url.encodedPath}", code)
            }
            body
        }
    }

    private fun String.encodeForm(): String =
        java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15; CRInfo) AppleWebKit/537.36"
}
