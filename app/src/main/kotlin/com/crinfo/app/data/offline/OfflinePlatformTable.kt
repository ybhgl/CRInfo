package com.crinfo.app.data.offline

import com.crinfo.app.data.offline.OfflineBinary.be4
import com.crinfo.app.data.offline.OfflineBinary.u8
import com.crinfo.app.data.offline.OfflineBinary.utf8

/**
 * plat.dat：`(车次 ID, 车站 ID) → 股道/检票口串`。
 *
 * 布局直接来自路路通 `PlatformMgr`：`[4B 大端条数]` + 每条 `[4B 车次ID][4B 车站ID][1B 字节长][UTF-8 串]`。
 * **键序是「车次在前」**（官方 `getPlatform(trainIndex, stationIndex)` 就这么传），
 * 早先按「站表」理解 plat.dat 会把两份 ID 用反。
 *
 * 真库 96527 条、走步残留 0 字节，其中含「待定」的只有 112 条，覆盖 11814 个车次 / 1530 个站
 * —— 「字段大量待定」这个旧印象不成立，这份数据是能用的。
 *
 * 结构上刻意**不建 9 万条 HashMap**：那是十几 MB 的常驻装箱开销。这里排好序的整型键 +
 * 一个字节池，查询走二分，全表约 1.5MB。
 */
class PlatformTable private constructor(
    private val keys: IntArray,
    private val starts: IntArray,
    private val lens: IntArray,
    private val pool: ByteArray,
    val declared: Int,
    val residual: Int,
) {
    /** 表内条目数（与 [declared] 相等说明走步没漏）。 */
    val size: Int get() = keys.size

    /** 某车次在指定站的股道；库里没这条记录返回 null（不等于「无站台」）。 */
    fun platformOf(trainId: Int, stationId: Int): String? {
        val at = keys.binarySearch(key(trainId, stationId))
        return if (at < 0) null else text(at)
    }

    /** 某车次全部有股道记录的站，按车站 ID 升序（表已按「车次,站」排过序，所以是连续一段）。 */
    fun entriesOfTrain(trainId: Int): List<Pair<Int, String>> {
        val from = lowerBound(key(trainId, 0))
        val out = ArrayList<Pair<Int, String>>()
        var i = from
        while (i < keys.size && keys[i] ushr STATION_BITS == trainId) {
            out.add((keys[i] and STATION_MASK) to text(i))
            i++
        }
        return out
    }

    /** 同一车次在同一站的重复记录数（真库实测没有重复，留着给回归测试钉住走步质量）。 */
    fun duplicateCount(): Int {
        var dup = 0
        for (i in 1 until keys.size) if (keys[i] == keys[i - 1]) dup++
        return dup
    }

    private fun text(at: Int): String = utf8(pool, starts[at], lens[at]).orEmpty()

    private fun lowerBound(target: Int): Int {
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < target) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        val EMPTY = PlatformTable(IntArray(0), IntArray(0), IntArray(0), ByteArray(0), 0, 0)

        private const val STATION_BITS = 16
        private const val STATION_MASK = (1 shl STATION_BITS) - 1
        private const val INDEX_BITS = 20
        private const val INDEX_MASK = (1L shl INDEX_BITS) - 1

        /** 键 = 车次 ID 在高 16 位、车站 ID 在低 16 位；两者都远小于 32768，所以整型不会翻负。 */
        private fun key(trainId: Int, stationId: Int): Int = (trainId shl STATION_BITS) or (stationId and STATION_MASK)

        fun parse(b: ByteArray): PlatformTable {
            val count = be4(b, 0)
            if (count !in 1..400000) return PlatformTable(IntArray(0), IntArray(0), IntArray(0), ByteArray(0), count.coerceAtLeast(0), b.size)
            var p = 4
            val keys = IntArray(count)
            val lens = IntArray(count)
            val chunks = ArrayList<ByteArray>(count)
            var read = 0
            while (read < count && p + 9 <= b.size) {
                val trainId = be4(b, p)
                val stationId = be4(b, p + 4)
                val len = u8(b, p + 8)
                if (len < 0 || p + 9 + len > b.size) break
                // 每条记录一个块，所以「块下标 == 记录下标」，排序后只要把块下标换成字节偏移即可
                chunks.add(b.copyOfRange(p + 9, p + 9 + len))
                keys[read] = key(trainId, stationId)
                lens[read] = len
                p += 9 + len
                read++
            }
            // 只把真读到的部分排起来：截断时也不会把没填的 0 键混进二分
            val n = read
            val packed = LongArray(n) { (keys[it].toLong() shl INDEX_BITS) or it.toLong() }
            packed.sort()
            val sortedKeys = IntArray(n) { (packed[it] ushr INDEX_BITS).toInt() }
            val starts = IntArray(n + 1)
            for (i in 0 until n) starts[i + 1] = starts[i] + chunks[i].size
            val pool = ByteArray(starts[n])
            for (i in 0 until n) chunks[i].copyInto(pool, starts[i])
            val sortedStarts = IntArray(n)
            val sortedLens = IntArray(n)
            for (i in 0 until n) {
                val origin = (packed[i] and INDEX_MASK).toInt()
                sortedStarts[i] = starts[origin]
                sortedLens[i] = lens[origin]
            }
            return PlatformTable(sortedKeys, sortedStarts, sortedLens, pool, count, b.size - p)
        }
    }
}
