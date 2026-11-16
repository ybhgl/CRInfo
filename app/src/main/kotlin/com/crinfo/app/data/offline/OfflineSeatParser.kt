package com.crinfo.app.data.offline

import com.crinfo.app.data.offline.OfflineBinary.id255
import com.crinfo.app.data.offline.OfflineBinary.u8

/**
 * xw.dat：`车次 ID → 席别位图（+ 换号车的按里程切段表）`。
 *
 * 布局来自路路通 `DataMgr`（建索引 `OooOOO()`、取记录 `OooO(i)`）：
 * `[2B(255 进制) 条数]` + 每条 `[2B 车次ID][2B 席别位图][1B 段数 n]` + n × `[2B 里程阈值][1B 车号下标]`。
 * 真库 14752 条、残留 0 字节；位图含义与 `Consts.XW_TYPE_*` 完全一致：
 * 硬座 1、软座 2、硬卧 4、软卧 8、一等座 16、二等座 32、动卧 64。
 * 实测分布 48（一等+二等）9924 条、13（硬座+硬卧+软卧）1949 条、32（只有二等）1606 条。
 *
 * 那 n 段是官方 `QueryBase.get_exact_train_name` 用的：一趟车在库里把多个号写成 `4167/4170` 一格，
 * 跑到第几公里该显示哪一个就靠这张表。阈值量级是**里程**不是站序（最大 4161，而停站最多 49 站），
 * 所以拿区间里程去比，别拿站序号。
 */
class SeatTable private constructor(
    private val flags: IntArray,
    private val segments: Map<Int, IntArray>,
    val declared: Int,
    val parsed: Int,
    val residual: Int,
) {
    /** 席别位图；库里没这趟车的记录返回 null（官方的兜底是「不限制」= 65535，由调用方决定）。 */
    fun seatFlags(trainId: Int): Int? = flags.getOrNull(trainId)?.takeIf { it >= 0 }

    /** 该席别在这趟车上是否存在；**没有 xw 记录时按存在处理**，与官方 `i14 = 65535` 同口径。 */
    fun hasSeat(trainId: Int, seatBit: Int): Boolean = (seatFlags(trainId) ?: ALL_SEATS) and seatBit != 0

    /**
     * 给定区间里程，返回该显示 `code.split('/')` 里的第几段；没有换号表返回 -1。
     * 官方取「最后一个阈值 ≤ 里程」的那段，段数不足时返回 -1 让调用方回落到整串。
     */
    fun segmentIndexAt(trainId: Int, km: Int): Int {
        val table = segments[trainId] ?: return -1
        var picked = -1
        var i = 0
        while (i + 1 < table.size) {
            if (table[i] > km) break
            picked = table[i + 1]
            i += 2
        }
        return picked
    }

    /** 按里程取「这一段真实显示的车次号」，取不到就返回整串（等价 `get_exact_train_name`）。 */
    fun exactCode(trainId: Int, code: String, km: Int): String {
        val parts = code.split('/')
        if (parts.size <= 1) return code.trim()
        val at = segmentIndexAt(trainId, km)
        return if (at in 0 until parts.size) parts[at].trim() else parts[0].trim()
    }

    fun segmentTrainCount(): Int = segments.size

    companion object {
        /** 官方在无记录时的兜底位图（所有席别都不拦）。 */
        const val ALL_SEATS = 65535

        val EMPTY = SeatTable(IntArray(0), emptyMap(), 0, 0, 0)

        fun parse(b: ByteArray, trainCount: Int): SeatTable {
            if (trainCount <= 0) return SeatTable(IntArray(0), emptyMap(), 0, 0, b.size)
            val count = id255(b, 0)
            if (count !in 0..60000) return SeatTable(IntArray(trainCount) { NO_RECORD }, emptyMap(), count.coerceAtLeast(0), 0, b.size)
            val flags = IntArray(trainCount) { NO_RECORD }
            val segments = HashMap<Int, IntArray>()
            var p = 2
            var read = 0
            while (read < count && p + 5 <= b.size) {
                val trainId = id255(b, p)
                val seat = id255(b, p + 2)
                val n = u8(b, p + 4)
                if (trainId < 0 || seat < 0 || n < 0) break
                p += 5
                read++
                if (trainId in flags.indices) flags[trainId] = seat
                if (n > 0) {
                    if (p + n * 3 > b.size) break
                    val flat = IntArray(n * 2)
                    for (i in 0 until n) {
                        flat[i * 2] = id255(b, p + i * 3)
                        flat[i * 2 + 1] = u8(b, p + i * 3 + 2)
                    }
                    p += n * 3
                    segments[trainId] = flat
                }
            }
            return SeatTable(flags, segments, count, read, b.size - p)
        }

        private const val NO_RECORD = -1
    }
}
