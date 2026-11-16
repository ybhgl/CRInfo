package com.crinfo.app.data.remote

import com.crinfo.app.core.net.Http
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * 路路通离线库（an.db）的更新服务端。整个域名只有明文 HTTP、也没有校验字段，
 * 所以这里只做「整包替换」：版本不等就重下，下载完必须解析通过才算装好。
 */
private const val VER_URL = "http://down.lltskb.com/android.ver"
private const val FALLBACK_URL = "http://down.lltskb.com/an.db"

/** 图定数据版本，形如 `20261108`；只有 8 位数字才可信。`dataUrls` 按服务端给的镜像顺序。 */
data class OfflineVersion(
    val data: String,
    val desc: String,
    val dataUrls: List<String>,
) {
    val isValid: Boolean get() = data.matches(Regex("^\\d{8}$"))

    /**
     * 有没有必要下载。语义照路路通自己的 `UpgradeModel.dataNewer()`：
     * `remote.data.compareTo(local.data) > 0` —— **严格更新才提示**，8 位日期串按字典序就是时间序。
     *
     * 早先这里只判「相不相等」，服务器回滚或灰度到旧版本号时会提示「发现新版本 20261101」，
     * 点下去就用**旧包覆盖本地新库**（降级），且版本号写回后不再自愈。
     *
     * 本机没有库时官方那条会返回 false —— 他们的库内置在 APK 的 `res/raw` 里、`ver.txt` 永远读得到，
     * 不存在「首次安装」；我们的库是下载的，首装必须能提示，所以这里单独放行。
     */
    fun isDataNewer(local: String?): Boolean {
        if (!isValid) return false
        if (local.isNullOrBlank() || !local.matches(Regex("^\\d{8}$"))) return true
        return data > local
    }
}

object LltApi {
    // 顶层 `"version": {` 带冒号，market 各渠道那几处是嵌在对象里的同名键，
    // 用它锚定版本号，避免读到渠道版本。
    // 两个坑：① 模式不能用三引号字符串——以引号结尾时 Kotlin 会吞掉一个引号；
    // ② Android 用 ICU 正则引擎，字面 `}` 不转义会直接抛 PatternSyntaxException（JVM 上却能用）。
    private val versionBlock = Regex("\"version\"\\s*:\\s*\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL)
    private val dataField = Regex("\"data\"\\s*:\\s*\"([^\"]*)\"")
    private val descField = Regex("\"desc\"\\s*:\\s*\"([^\"]*)\"")
    private val dataUrlBlock = Regex("\"dataUrl\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL)
    private val urlItem = Regex("\"(https?://[^\"]+)\"")

    // 共享 client 的 25s 读超时按「一次响应」算，2.3MB 的包在慢镜像上会被腰斩
    private val downloadClient by lazy {
        Http.client.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 取版本与下载地址。
     *
     * 服务端这份 `android.ver` **不是合法 JSON**（实测 `"name": 应用宝"` 缺前引号），
     * 整体交给序列化器必然抛错、功能永久不可用；路路通客户端自己能读，说明它也是宽松取值。
     * 所以这里只按字段抠需要的三项，不去修也不依赖对方哪天补引号。
     */
    suspend fun fetchVersion(): OfflineVersion = parseVersion(Http.get(VER_URL))

    /** 纯取值，便于用真实（非法）响应做单测。 */
    fun parseVersion(body: String): OfflineVersion {
        val block = versionBlock.find(body)?.groupValues?.get(1).orEmpty()
        val urls = dataUrlBlock.find(body)?.groupValues?.get(1)
            ?.let { seg -> urlItem.findAll(seg).map { it.groupValues[1] }.toList() }
            .orEmpty()
            .ifEmpty { listOf(FALLBACK_URL) }
        return OfflineVersion(
            data = dataField.find(block)?.groupValues?.get(1).orEmpty(),
            desc = descField.find(block)?.groupValues?.get(1).orEmpty(),
            dataUrls = urls,
        )
    }

    /**
     * 按 `dataUrls` 顺序试各镜像，全部失败才抛错。回调参数为（已下载字节，总字节），
     * 总字节拿不到时传 0，由 UI 侧降级成不确定进度。
     */
    suspend fun downloadAnDb(version: OfflineVersion, onProgress: (Long, Long) -> Unit): ByteArray {
        var lastError: IOException? = null
        for (url in version.dataUrls) {
            try {
                return download(url, onProgress)
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("an.db 无可用下载地址")
    }

    private suspend fun download(url: String, onProgress: (Long, Long) -> Unit): ByteArray =
        withContext(Dispatchers.IO) {
            downloadClient.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("an.db HTTP ${resp.code}")
                val source = resp.body?.byteStream() ?: throw IOException("an.db 空响应")
                val total = resp.body?.contentLength() ?: 0L
                val out = java.io.ByteArrayOutputStream(if (total > 0) total.toInt() else 1 shl 20)
                val buf = ByteArray(16 * 1024)
                var done = 0L
                while (true) {
                    ensureActive()
                    val read = source.read(buf)
                    if (read < 0) break
                    out.write(buf, 0, read)
                    done += read
                    onProgress(done, total)
                }
                // ZIP 头尾都要落到，半截文件后面解析必然失败，这里先挡掉
                val bytes = out.toByteArray()
                if (bytes.size < 1024 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) {
                    throw IOException("an.db 响应不是合法 ZIP")
                }
                bytes
            }
        }
}
