package com.crinfo.app.data.offline

/**
 * 链上的**一段**。同底中途改号时一段含多个车次（真库例：南宁东的 `G3741/G3744`），
 * 它们是一趟车、该整体点击整体查，不该拆成几个链接。
 *
 * [emptyRun] = 库里这一段带前导 `0`（`0G7806/5`），是**空驶/回送、不载客**。
 * 前导 0 是库内标记，不是车次号的一部分：显示要去掉，但只在「0 + 字母」这一种形态下去，
 * 免得把 `G1024` 之类削成别的号。
 */
data class JiaoluSegment(
    /** 库内原样（含空驶前导 0）的完整车次号，按链内给出的顺序。 */
    val codes: List<String>,
    val emptyRun: Boolean,
) {
    /** 界面用的整体串：削掉空驶标记，多程仍用 `/` 连成一串。 */
    val display: String get() = displayCodes.joinToString("/")

    /** 段内每一程的显示形式：一程一行时用它，空驶标记同样已经削掉。 */
    val displayCodes: List<String> get() = codes.map { it.trimEmptyRunMark() }

    /** 查询与跳转用的主号：取斜杠前那一段，与车站板面同一套口径。 */
    val queryCode: String get() = codes.firstOrNull()?.trimEmptyRunMark().orEmpty()
}

/** 只削「0 + 字母」这种空驶回送标记；号身中间的 0 一概不碰。 */
private fun String.trimEmptyRunMark(): String =
    if (length > 1 && this[0] == '0' && this[1].isLetter()) substring(1) else this

/**
 * 一趟车在交路链里的位置。车底 = 一列动车组，按 `担当车次组` 的顺序全天连续周转，
 * 一个「段」是组里 `#` 隔开的一项。
 */
data class JiaoluPosition(
    val carType: String,
    val note: String,
    /** 整条链（含本段），按车底周转顺序。 */
    val group: List<JiaoluSegment>,
    val segmentIndex: Int,
    /** 当前所在的那一段。 */
    val currentSegment: JiaoluSegment,
) {
    val previousCode: String? get() = group.getOrNull(segmentIndex - 1)?.display
    val nextCode: String? get() = group.getOrNull(segmentIndex + 1)?.display

    /** 展示用的链：当前段加前后各一段，够看清接续关系，又不至于长到没法读。 */
    val window: List<JiaoluSegment>
        get() {
            val from = (segmentIndex - CONTEXT).coerceAtLeast(0)
            val to = (segmentIndex + CONTEXT).coerceAtMost(group.lastIndex)
            return group.subList(from, to + 1)
        }

    /** 窗口在整条链里的起始下标，供按段号取时刻用。 */
    val windowStart: Int get() = (segmentIndex - CONTEXT).coerceAtLeast(0)

    /**
     * 「G82/G79 › G32 › 【G1】 › G1956/G1957 › G1958/G1955」；某侧还藏着两段以上才补省略号，
     * 只藏一段没必要提示。库里只给顺序不给时刻，所以文案只表达接续关系，不假装知道几点。
     */
    fun describe(): String {
        val head = if (segmentIndex - CONTEXT >= 2) "… " else ""
        val tail = if (group.lastIndex - (segmentIndex + CONTEXT) >= 2) " …" else ""
        val text = window.joinToString(" › ") {
            if (it == group[segmentIndex]) "【${it.display}】" else it.display
        }
        return head + text + tail
    }

    private companion object {
        const val CONTEXT = 2
    }
}

/** 一程车次的两端站与时刻；离线库只有顺序，时刻需按车次号现查时刻表。 */
data class JiaoluEndpoints(
    val code: String,
    val fromName: String,
    val fromTime: String,
    val toName: String,
    val toTime: String,
    /** 终到站距始发日的跨天数，一程行尾的「+N」小字用。 */
    val toDayDiff: Int = 0,
)

/** 交路链上某一段的查询结果。 */
sealed interface JiaoluLegInfo {
    data object Loading : JiaoluLegInfo
    /** 这一项是空驶/回送段（如 `0D7914`）或查不到时刻。 */
    data object Unavailable : JiaoluLegInfo
    data class Resolved(val endpoints: List<JiaoluEndpoints>) : JiaoluLegInfo
}

/**
 * 在交路链里定位当前车次。
 *
 * `G1956/G1957` 这类带 `/` 的段，输入任意一程都要能命中整段；
 * `0G7200` / `0D7914` 这种前导 0 是空驶回库段，与裸车次同指一程，所以只剥**首字符**的 0
 * （绝不能删掉所有 0，否则 G1024 会变成 G124，把两趟不同的车判成同一程）。
 * 段内简写在定位前就补全，界面与按号查时刻拿到的都是完整车次号。
 *
 * 输入本身也可能是换号车的合并整串（t.i 的 `K1276/K1277`，离线详情的 trainCode 就是它）——
 * 链里没有整串这种号，必须拆段逐段定位，任一段命中即同一条车底链。
 */
fun locateJiaolu(rakes: List<JiaoluRake>, code: String): JiaoluPosition? {
    for (segment in code.split('/')) {
        locateSingle(rakes, segment)?.let { return it }
    }
    return null
}

private fun locateSingle(rakes: List<JiaoluRake>, code: String): JiaoluPosition? {
    val wanted = normalizeCode(code)
    if (wanted.isEmpty()) return null
    rakes.forEach { rake ->
        rake.group.forEachIndexed { index, part ->
            val codes = expandSegmentCodes(part)
            if (codes.any { normalizeCode(it) == wanted }) {
                val chain = rake.group.map { it.toSegment() }
                return JiaoluPosition(
                    carType = rake.carType,
                    note = rake.note,
                    group = chain,
                    segmentIndex = index,
                    currentSegment = chain[index],
                )
            }
        }
    }
    return null
}

/**
 * 段内 `/` 后面的简写补全成完整车次号。铁路写法省略与前一程相同的部分，只写不同的末位：
 * `G82/79` 是 G82 与 G79，`G1956/7` 是 G1956 与 G1957，`G1955/6/7/8` 是连号四程。
 * 已经带字母的就是完整形式，原样保留。
 */
fun expandSegmentCodes(part: String): List<String> = buildList {
    part.split('/').forEach { raw ->
        val token = raw.trim().uppercase()
        if (token.isEmpty()) return@forEach
        val previous = lastOrNull()
        add(if (previous == null || token.any { it.isLetter() }) token else completeCode(token, previous))
    }
}

/**
 * 简写 = 前一程削掉「被省略的那几位数字」再接上简写；
 * 简写不比前一程的数字短，说明它本身就是完整号（`G82/79` 那种），直接补字母头。
 */
private fun completeCode(short: String, previous: String): String {
    // 号身取「末尾连续数字」，这样 `0G7200` 的前导 0 会留在字母头里一起继承
    val digits = previous.takeLastWhile { it.isDigit() }
    val prefix = previous.dropLast(digits.length)
    if (prefix.isEmpty() || digits.isEmpty() || short.length >= digits.length) return prefix + short
    return prefix + digits.take(digits.length - short.length) + short
}

/** 一段原文 → [JiaoluSegment]：先补全段内简写，再按有没有前导 0 判空驶。 */
private fun String.toSegment(): JiaoluSegment {
    val codes = expandSegmentCodes(this)
    return JiaoluSegment(codes, codes.any { it.length > 1 && it[0] == '0' && it[1].isLetter() })
}

private fun normalizeCode(raw: String): String =
    raw.trim().uppercase().trimStart('0')

/**
 * 车次号 → 车底型号的批量索引。`locateJiaolu` 每查一趟都要全表扫，
 * 大屏一页几百行逐个查就是几十万段；兜底只要型号，一遍建表就够。
 */
fun carTypeIndex(rakes: List<JiaoluRake>): Map<String, String> {
    val out = HashMap<String, String>(rakes.size * 4)
    rakes.forEach { rake ->
        if (rake.carType.isBlank()) return@forEach
        rake.group.forEach { part ->
            expandSegmentCodes(part).forEach { code ->
                val key = normalizeCode(code)
                if (key.isNotEmpty()) out.putIfAbsent(key, rake.carType)
            }
        }
    }
    return out
}

/** 按建表同一套规则取型号：空驶段的前导 0 与简写都在这里对齐。 */
fun carTypeIndexLookup(index: Map<String, String>, code: String): String? =
    index[normalizeCode(code)]
