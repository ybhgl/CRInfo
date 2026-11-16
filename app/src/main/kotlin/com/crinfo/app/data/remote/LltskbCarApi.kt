package com.crinfo.app.data.remote

import com.crinfo.app.core.net.Http
import com.crinfo.app.data.model.LltskbCarImages
import com.crinfo.app.data.model.LltskbCarModel
import com.crinfo.app.data.model.LltskbImage
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 路路通「高速列车车型信息」没有 JSON 接口，也没有按车底号查询的入口，
 * 只能由车底号反推车型、按车型抓静态 HTML，再解析出逐车图片。
 *
 * 页面的组织方式不统一：有的车型页直接给图片，有的只给子车型链接；
 * 因此这里先按车型精确匹配，匹配不到再按最长前缀退到同系列，并把系列下所有车型都取回来，
 * 交给界面上的 Spinner 让用户切换。解析失败一律静默，由界面决定整块不显示。
 */
object LltskbCarApi {
    private const val BASE = "http://wap.lltskb.com/shfw/lcxh/"
    private const val INDEX_URL = BASE + "index.html"

    private val imageRegex = Regex(
        """<img\s+[^>]*?src\s*=\s*["']?([^"'\s>]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val linkRegex = Regex(
        """<a\s+[^>]*?href\s*=\s*["']?([^"'\s>]+)[^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** 逐车厢的图文都放在表格单元格里，说明在图片上一行或同一单元格的图片前。 */
    private val cellRegex = Regex(
        """<td\b[^>]*>(.*?)</td>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** `CR400BF-A-5092` / `CR200JC6053` 这类写法去掉末尾四位车号，留下车型。 */
    private val unitSuffix = Regex("""^(.+?)-?\d{4}$""")
    private val htmlTag = Regex("<[^>]*>")
    private val whitespace = Regex("\\s+")

    /** 车型页变动极少，进程内缓存原文足够，别每次查车都重打一遍路路通。 */
    private val pageCache = ConcurrentHashMap<String, String>()
    private val pageLock = Mutex()

    internal data class Entry(val folder: String, val label: String)

    suspend fun imagesFor(emuNo: String): LltskbCarImages? = withContext(Dispatchers.IO) {
        val modelKey = modelKey(emuNo) ?: return@withContext null
        val entries = parseIndexLinks(safePage(INDEX_URL) ?: return@withContext null)
        if (entries.isEmpty()) return@withContext null
        val (family, exactEntry) = pickEntry(entries, modelKey) ?: return@withContext null

        val familyUrl = pageUrlOf(family.folder) ?: return@withContext null
        val familyHtml = safePage(familyUrl) ?: return@withContext null
        val direct = parseImageItems(familyHtml, familyUrl)
        val models = buildList {
            if (direct.isNotEmpty()) add(LltskbCarModel(shortName(family.label), direct))
            parseModelLinks(familyHtml).forEach { sub ->
                // 子车型链接相对车型页目录（cr200j/cr200j3-a/index.html），不能按站点根目录拼
                val url = resolveUrl(familyUrl, "${sub.folder}/index.html") ?: return@forEach
                val images = safePage(url)?.let { parseImageItems(it, url) }.orEmpty()
                if (images.isNotEmpty()) add(LltskbCarModel(shortName(sub.label), images))
            }
        }.distinctBy { it.name }.filter { it.images.isNotEmpty() }
        if (models.isEmpty()) return@withContext null
        // 车型页本身没有直接图片时只能算同系列；有直接图片才算命中本次车型。
        LltskbCarImages(exact = exactEntry && direct.isNotEmpty(), models = models)
    }

    /**
     * 由车底号推车型键：去掉末尾车号、忽略大小写与连字符。
     * `CR400BF-A-5092` → `cr400bfa`，`CR200JC-6053` → `cr200jc`。
     */
    internal fun modelKey(emuNo: String): String? {
        val raw = emuNo.trim()
        if (raw.isEmpty()) return null
        val model = unitSuffix.matchEntire(raw)?.groupValues?.get(1) ?: raw
        return normalizeKey(model).ifBlank { null }
    }

    /** 先精确匹配车型目录；匹配不到就取能作为车型键前缀的最长目录，作为同系列。 */
    internal fun pickEntry(entries: List<Entry>, modelKey: String): Pair<Entry, Boolean>? {
        entries.firstOrNull { normalizeKey(it.folder) == modelKey }?.let { return it to true }
        return entries
            .filter { entry ->
                val key = normalizeKey(entry.folder)
                key.isNotEmpty() && modelKey.startsWith(key)
            }
            .maxByOrNull { normalizeKey(it.folder).length }
            ?.let { it to false }
    }

    /** 车型首页的入口：只认本目录下的 `xxx/index.html`，返回去重后的目录与显示名。 */
    internal fun parseIndexLinks(html: String): List<Entry> =
        linkEntries(html)
            .mapNotNull { (folder, label) -> label.takeIf { it.isNotBlank() }?.let { Entry(folder, it) } }
            .distinctBy { it.folder }

    internal fun parseModelLinks(html: String): List<Entry> =
        linkEntries(html)
            .mapNotNull { (folder, label) -> label.takeIf { it.isNotBlank() }?.let { Entry(folder, it) } }
            .distinctBy { it.folder }

    /**
     * 解析页面里的图片。站点有时写 `src=1.jpg` 有时写 `src="1.jpg"`，
     * 还有 `../../pic/x.gif` 这类相对路径，统一转绝对地址后按扩展名过滤并去重。
     */
    internal fun parseImageUrls(html: String, pageUrl: String): List<String> {
        val urls = LinkedHashSet<String>()
        imageRegex.findAll(html).forEach { match ->
            val raw = match.groupValues[1].trim()
            if (raw.isEmpty() || raw.startsWith("data:", ignoreCase = true)) return@forEach
            val resolved = resolveUrl(pageUrl, raw) ?: return@forEach
            if (isImageUrl(resolved)) urls.add(resolved)
        }
        return urls.toList()
    }

    /**
     * 逐单元格解析图片与说明。车厢页的说明单独占一行（如「1号车(二等) 定员98人」），
     * 同一说明下的多张图都复用它；座席说明页则把「一等座: …」写在图片前，取冒号前短语。
     */
    internal fun parseImageItems(html: String, pageUrl: String): List<LltskbImage> {
        val items = LinkedHashSet<LltskbImage>()
        var rowCaption: String? = null
        var sawCellImage = false
        cellRegex.findAll(html).forEach { cell ->
            val cellHtml = cell.groupValues[1]
            val urls = parseImageUrls(cellHtml, pageUrl)
            if (urls.isNotEmpty()) {
                sawCellImage = true
                val caption = captionBeforeFirstImage(cellHtml) ?: rowCaption
                urls.forEach { url -> items.add(LltskbImage(url, caption)) }
            } else if (cell.value.contains("bgcolor", ignoreCase = true)) {
                cleanLabel(cellHtml).takeIf { it.isNotBlank() }?.let { rowCaption = it }
            }
        }
        // 有些车型页图片不在表格单元格里，退回只取图片、不带说明。
        if (sawCellImage) return items.toList()
        return parseImageUrls(html, pageUrl).map { LltskbImage(it) }
    }

    /** 取同一单元格里图片前的文字；座席说明「一等座: …」只留冒号前的标题。 */
    private fun captionBeforeFirstImage(cellHtml: String): String? {
        val first = imageRegex.find(cellHtml) ?: return null
        val text = cleanLabel(cellHtml.substring(0, first.range.first))
        if (text.isBlank()) return null
        return text.substringBefore(':').substringBefore('：').trim().ifBlank { null }
    }

    internal fun cleanLabel(raw: String): String =
        raw.replace(htmlTag, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(whitespace, " ")
            .trim()

    /** `CR200J3-A型，定员720人` → `CR200J3-A型`，Spinner 里只留车型名。 */
    internal fun shortName(label: String): String {
        val first = label.substringBefore('，').substringBefore(',').trim()
        val typeIndex = first.indexOf('型')
        return if (typeIndex >= 0) first.substring(0, typeIndex + 1) else first
    }

    private fun linkEntries(html: String): List<Pair<String, String>> {
        val result = LinkedHashMap<String, String>()
        linkRegex.findAll(html).forEach { match ->
            val href = match.groupValues[1].trim().substringBefore('#')
            val folder = folderOf(href) ?: return@forEach
            val label = cleanLabel(match.groupValues[2])
            if (label.isNotEmpty()) result.putIfAbsent(folder, label)
        }
        return result.map { it.key to it.value }
    }

    private fun folderOf(href: String): String? {
        if (href.contains("..") || href.startsWith("http", ignoreCase = true) ||
            href.startsWith("javascript", ignoreCase = true)
        ) {
            return null
        }
        val path = href.removePrefix("./").trim()
        if (!path.endsWith("/index.html", ignoreCase = true)) return null
        val folder = path.removeSuffix("/index.html").trim('/')
        return folder.takeIf { it.isNotEmpty() && !it.contains('/') }
    }

    private fun pageUrlOf(folder: String): String? =
        folder.takeIf { it.isNotBlank() && !it.contains('/') && !it.contains("..") }
            ?.let { "$BASE$it/index.html" }

    private fun normalizeKey(raw: String): String =
        raw.lowercase().filter { it.isLetterOrDigit() }

    internal fun resolveUrl(pageUrl: String, raw: String): String? = runCatching {
        if (raw.startsWith("//")) "http:$raw" else URI(pageUrl).resolve(raw).toString()
    }.getOrNull()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    private fun isImageUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
            path.endsWith(".gif") || path.endsWith(".webp")
    }

    private suspend fun safePage(url: String): String? =
        runCatching { page(url) }.getOrNull()

    private suspend fun page(url: String): String {
        pageCache[url]?.let { return it }
        return pageLock.withLock {
            pageCache[url] ?: Http.get(url, referer = "http://wap.lltskb.com/").also { pageCache[url] = it }
        }
    }
}
