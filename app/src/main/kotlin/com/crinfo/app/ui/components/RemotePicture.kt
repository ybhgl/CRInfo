package com.crinfo.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.crinfo.app.core.net.Http
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 12306 的车辆接口只返回裸文件名（`c9ef4e80….png`），实际图片在
 * `wifi.12306.cn/resourcecenter/cateringimages/` 下，基址单独放这一处便于更换。
 */
object CarImages {
    const val BASE_URL: String = "https://wifi.12306.cn/resourcecenter/cateringimages"

    fun urlOf(name: String): String? = when {
        name.isBlank() -> null
        name.startsWith("http") -> name
        BASE_URL.isBlank() -> null
        else -> "${BASE_URL.trimEnd('/')}/$name"
    }
}

/** 图片 CDN 常回 302，而业务接口的 client 刻意不跟跳转，这里单独派生一个跟随重定向的。 */
private val imageClient by lazy { Http.client.newBuilder().followRedirects(true).build() }

/**
 * 位图缓存。`ConcurrentHashMap` 不接受 null 值，所以「取到过」和「试过但取不到」
 * 分成两个容器存，别想着往里塞 null 当占位（会直接 NPE 闪退）。
 */
private val pictureCache = ConcurrentHashMap<String, ImageBitmap>()
private val failedPictures = ConcurrentHashMap.newKeySet<String>()

/** 一个弹窗里可能同时有 20 张图，限制并发解码，避免一次性占满内存和主线程上传队列。 */
private val decodeSlots = Semaphore(3)

/**
 * 12306 的座席图实测 7200×4000，按原尺寸解码一张就约 115MB；界面里一次有 20 张图，
 * 直接全尺寸解码必然在滚动时掉帧。统一把最长边压到 1280px，显示尺寸仍然够清晰。
 */
private const val MAX_DECODE_DIMENSION = 1280

/** 先按 2 的幂降采样，再按最长边精确缩放，兼顾峰值内存与显示清晰度。 */
private fun decodeSampled(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_DIMENSION * 1.5f) {
        sample *= 2
    }
    val decoded = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    ) ?: return null

    val longest = maxOf(decoded.width, decoded.height)
    if (longest <= MAX_DECODE_DIMENSION) return decoded.asImageBitmap()

    val scale = MAX_DECODE_DIMENSION.toFloat() / longest
    val scaled = Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * scale).toInt().coerceAtLeast(1),
        (decoded.height * scale).toInt().coerceAtLeast(1),
        true,
    )
    if (scaled !== decoded) decoded.recycle()
    return scaled.asImageBitmap()
}

@Composable
fun RemotePicture(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    var bitmap by remember(url) { mutableStateOf(url?.let { pictureCache[it] }) }
    LaunchedEffect(url) {
        val target = url ?: return@LaunchedEffect
        if (pictureCache.containsKey(target) || failedPictures.contains(target)) return@LaunchedEffect
        val decoded = runCatching {
            withContext(Dispatchers.IO) {
                decodeSlots.withPermit {
                    val request = Request.Builder()
                        .url(target)
                        .header("User-Agent", Http.USER_AGENT)
                        .header("Referer", "https://mobile.12306.cn/")
                        .build()
                    imageClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        response.body?.bytes()?.let { bytes -> decodeSampled(bytes) }
                    }
                }
            }
        }.getOrNull()
        // 成功的进位图缓存，失败的进黑名单，两种都只打一次网络
        if (decoded != null) pictureCache[target] = decoded else failedPictures.add(target)
        bitmap = decoded
    }
    val clickableModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = contentDescription,
            modifier = clickableModifier,
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * 大图查看器的资源入口，与缩略图共用同一套下载/解码口径（跟随 302、带 UA 与 Referer），
 * 但按原图全尺寸解码，并单独缓存。查看器只从这几个函数取图，不碰上面的缩略图缓存。
 */
object CarImageViewerSource {
    // 12306 原图实测 7200×4000，ARGB_8888 单张约 115MB；串行解码 + 小容量 LRU 控制峰值。
    private const val MAX_CACHED_FULL = 2
    private val fullDecodeSlots = Semaphore(1)
    private val fullCache = object : LinkedHashMap<String, ImageBitmap>(
        MAX_CACHED_FULL + 1,
        1f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > MAX_CACHED_FULL
    }
    private val cacheLock = Any()

    /** 缩略图（最长边 1280px），大图未就绪时先显示它，避免白屏。 */
    fun thumbnail(url: String): ImageBitmap? = pictureCache[url]

    /** 取已解码的原图；命中直接返回，未命中返回 null 由调用方安排加载。 */
    fun peekFull(url: String): ImageBitmap? = synchronized(cacheLock) { fullCache[url] }

    /**
     * 原图全尺寸解码。串行执行避免多页同时解码叠加峰值；失败（含 OOM 与网络错）返回 null，
     * 调用方据此保留缩略图占位。解码后写入小 LRU，淘汰的位图交给 GC 回收。
     */
    suspend fun loadFull(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        synchronized(cacheLock) { fullCache[url] }?.let { return@withContext it }
        fullDecodeSlots.withPermit {
            synchronized(cacheLock) { fullCache[url] }?.let { return@withPermit it }
            val decoded = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", Http.USER_AGENT)
                    .header("Referer", "https://mobile.12306.cn/")
                    .build()
                imageClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.bytes()?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                }
            }.getOrNull()
            if (decoded != null) synchronized(cacheLock) { fullCache[url] = decoded }
            decoded
        }
    }
}
