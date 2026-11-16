package com.crinfo.app.data.offline

import com.crinfo.app.data.parseTrainCode

/**
 * 一条车次联想候选。
 *
 * @param code 可直接查询的车次号（库里的 `G1956/G1957` 已拆成单段，填进输入框后走的就是普通查询链路）
 * @param matchStart 命中片段在 [code] 里的起点，-1 表示不标亮
 * @param matchLength 命中片段长度
 */
data class TrainSuggestion(
    val code: String,
    val matchStart: Int,
    val matchLength: Int,
)

/**
 * 离线库车次表（t.i）的**联想索引**：把「输入一半猜车次」这件事和界面彻底分开，
 * 纯 JVM 可验算，也方便在单测里把排序口径钉死。
 *
 * 池子来自 [OfflineDb.trainCodes]（下标即车次 ID 的那张主键表），合并串按 `/` 拆段收录，
 * 并只用 [parseTrainCode] 校验过的号入池 —— 点了候选就等于用户自己敲这个号，不留格式报错的口子。
 *
 * 排序在**建池时一次性做完**（短号在前、同长按数字升序、再按字典序），所以 [suggest] 只做单遍
 * 过滤并保持池序，输入多少位都不会再触发排序。全库万把条，一遍 `startsWith` 只要几毫秒，
 * 调用方把它放到 Default 线程即可。
 */
class TrainSuggestionIndex private constructor(
    private val codes: Array<String>,
    /** 与 [codes] 同序的数字段（`G1461` → `1461`），供「中段包含」匹配用，避免每次输入都重算。 */
    private val digits: Array<String>,
) {
    /** 池内候选总数（去重后）。 */
    val size: Int get() = codes.size

    /**
     * 按输入串取候选，前缀命中优先、纯数字输入再补数字段包含。
     *
     * 口径：
     * - 整串前缀（`G1` → `G1`、`G12`、`G100`）永远排最前；
     * - 只有**纯数字**输入才做包含（`461` → `1461`、`G461`），带字母时不做——
     *   否则 `G1` 会把 `ZG12` 这类不相干的车拖进候选；
     * - 两轮共用池序，够 [limit] 条立即返回，不再扫完剩下的库。
     */
    fun suggest(raw: String, limit: Int = DEFAULT_LIMIT): List<TrainSuggestion> {
        val query = normalize(raw)
        if (query.isEmpty() || limit <= 0) return emptyList()
        val out = ArrayList<TrainSuggestion>(limit)
        for (i in codes.indices) {
            if (codes[i].startsWith(query)) {
                out += TrainSuggestion(codes[i], 0, query.length)
                if (out.size == limit) return out
            }
        }
        if (!query.all { it.isDigit() }) return out
        for (i in codes.indices) {
            val code = codes[i]
            // 第一轮已经收过前缀命中，这里跳过，别让同一趟车出现两次
            if (code.startsWith(query)) continue
            if (digits[i].indexOf(query) < 0) continue
            // 合法车次号的数字段是连续一段，digits 命中就等价于 code 命中；取不到起点就不标亮
            out += TrainSuggestion(code, code.indexOf(query), query.length)
            if (out.size == limit) return out
        }
        return out
    }

    companion object {
        /** 弹层里最多摆几条候选（一屏 4 个半，其余靠滑动）。 */
        const val DEFAULT_LIMIT = 30

        /** 输入侧归一：与 [parseTrainCode] 同一口径，否则「g 1」这类输入对不上池里的键。 */
        fun normalize(raw: String): String =
            raw.trim().uppercase().replace(" ", "").replace("-", "")

        /**
         * 按库版本缓存的进程级实例。
         *
         * 车次页与详情页各有一个 [com.crinfo.app.ui.query.TrainQueryViewModel]，仓库也不是单例，
         * 不做这层缓存就得每次联想都重新读盘建池。库更新后版本号变了，自然走重建。
         */
        @Volatile
        private var cached: Pair<String, TrainSuggestionIndex>? = null

        fun of(version: String, trainCodes: List<String>): TrainSuggestionIndex {
            cached?.takeIf { it.first == version }?.let { return it.second }
            val built = build(trainCodes)
            cached = version to built
            return built
        }

        /**
         * 只取现成池子，**绝不建**：界面用它在「池子已就绪」时同步出候选，
         * 没就绪就走后台那条路。少了这道判空，主线程就可能撞上一两百毫秒的建池排序。
         */
        fun peek(version: String): TrainSuggestionIndex? = cached?.takeIf { it.first == version }?.second

        /** 库被清掉时主动丢弃旧池，免得留着万把条没人用的字符串。 */
        fun invalidate() {
            cached = null
        }

        /** 建池：拆段 → 归一 → 过滤非法 → 去重 → 一次性排序。测试可直接喂合成车次表。 */
        internal fun build(trainCodes: List<String>): TrainSuggestionIndex {
            val seen = HashSet<String>()
            val pool = ArrayList<Entry>(trainCodes.size)
            for (raw in trainCodes) {
                // 库里同车底多车次写成 `G1956/G1957`，用户只会输其中一段，候选也按段给
                for (segment in raw.split('/')) {
                    val code = normalize(segment)
                    if (code.isEmpty() || !seen.add(code)) continue
                    if (parseTrainCode(code) == null) continue
                    pool += Entry(code, digitValueOf(code))
                }
            }
            pool.sortWith(POOL_ORDER)
            val codes = Array(pool.size) { pool[it].code }
            val digits = Array(pool.size) { digitsOnly(pool[it].code) }
            return TrainSuggestionIndex(codes, digits)
        }

        private class Entry(val code: String, val digitValue: Int)

        /** 短号在前、同长按数字升序、再按字典序：`G1` < `G12` < `G100` < `K1`。 */
        private val POOL_ORDER: Comparator<Entry> =
            compareBy<Entry> { it.code.length }
                .thenBy { it.digitValue }
                .thenBy { it.code }

        private fun digitsOnly(code: String): String = buildString(code.length) {
            for (c in code) if (c.isDigit()) append(c)
        }

        private fun digitValueOf(code: String): Int = digitsOnly(code).toIntOrNull() ?: 0
    }
}
