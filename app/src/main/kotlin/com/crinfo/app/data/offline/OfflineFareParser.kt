package com.crinfo.app.data.offline

import com.crinfo.app.data.offline.OfflineBinary.be
import com.crinfo.app.data.offline.OfflineBinary.date128
import com.crinfo.app.data.offline.OfflineBinary.id128
import com.crinfo.app.data.offline.OfflineBinary.id255
import com.crinfo.app.data.offline.OfflineBinary.seatSlots
import com.crinfo.app.data.offline.OfflineBinary.u8

/**
 * 票价族（`p0–p4.dat`、`pk0–pk9.dat`、`rwp.dat`、`dwp.dat`、`ts.dat`）与按日调整（`sch.dat`）。
 *
 * ⚠️ **20261108 版实测：这一族基本是空壳。** `p*` 合计 46 条、`pk*` 合计 197 条、
 * `rwp/dwp` 各 105 条且是 2021-07-15 的旧档、`ts.dat` 与 `sch.dat` **0 字节**。
 * 官方界面里的票价主要走线上接口（`PriceQuery` 里还有一张内置的里程→基准价表在 APK 代码里，不在库里）。
 * 格式照样全部实现，是因为：①「数据库所有文件都能解」这条要求要成立；②换图后这几张表可能被填上，
 * 届时无须再逆向一次。但**界面上不许拿它当权威**，也不许因为表空就假装知道按日停运。
 *
 * 库内价格单位是**角**：官方 `PriceQuery.OooO0Oo(v)` = `v/10`，奇数再补 `.5`，所以 `v` 就是角。
 */
object OfflineFareParser {

    /** `p*`/`rwp`/`dwp` 的一条：某站到某站的一对基准价，外加按「票价号」覆写的若干条。 */
    data class PairedFare(
        val from: Int,
        val to: Int,
        /** 官方在返回串里把它放**后面**的那个价（高铁口径对应一等座那格）。 */
        val firstPrice: Int,
        /** 官方放在**前面**返回的价（高铁口径对应二等座那格）。 */
        val secondPrice: Int,
        val overrides: List<FareOverride>,
    )

    /** 票价号命中时用的一组价。 */
    data class FareOverride(val priceKey: Int, val low: Int, val high: Int)

    /** `pk*` 的一条：站到站，按席别位图给出若干价。 */
    data class SeatFare(val from: Int, val to: Int, val entries: List<SeatFareEntry>)

    /**
     * 席别位图那一格对应的若干价。存的是**原始顺序**：
     * bit0 硬座(1) | bit1 软座(1) | bit2 硬卧(下、中、上 3 个) | bit3 软卧(下、上 2 个) | bit4/bit5 各 1 个。
     * 官方打印卧铺时是反着取的（先取最后那个再取前一个），别以为存的是「上中下」。
     */
    data class SeatFareEntry(val priceKey: Int, val seatFlag: Int, val prices: IntArray)

    /** `ts.dat` 的一条：某票价号在日期区间内按周期位图浮动到的价。 */
    data class PriceRule(
        val priceKey: Int,
        val startDate: Int,
        val endDate: Int,
        val period: Int,
        val bitmap: Int,
        val price: Int,
    )

    /** `sch.dat` 的一行：`type` 0=时刻调整（带 3 个未验明的附加字节）、1=停运、2=加开。 */
    data class ScheduleItem(val type: Int, val startDate: Int, val endDate: Int, val extra: IntArray)

    /** `sch.dat` 的一条记录：某车次在某一站（或 `stationId = 16384` 表示整车次）的调整行列表。 */
    data class ScheduleEntry(val trainId: Int, val stationId: Int, val items: List<ScheduleItem>) {
        /** 整车次级记录（官方用 16384 当「所有站」的哨兵）。 */
        val isTrainWide: Boolean get() = stationId == TRAIN_WIDE_STATION
    }

    /** 键 = 小站 ID 在高 16 位、大站 ID 在低 16 位：官方查票价两个方向都认，所以先归一化。 */
    private fun pairKey(a: Int, b: Int): Int {
        val lo = minOf(a, b)
        val hi = maxOf(a, b)
        return (lo shl 16) or (hi and 0xFFFF)
    }

    /**
     * `p*.dat` / `rwp.dat` / `dwp.dat` 共用同一种走步：
     * `[2B(128 进制) 起站][2B(128 进制) 到站][1B 段数 n][2B 价A][2B 价B]` + (n-1) × `[2B 票价号][2B 低][2B 高]`，
     * 记录长 `6n+3`（官方步长就是这么算的，`n` 那个字节同时是负载的第一字节）。
     */
    fun parsePairedFares(b: ByteArray, label: String): PairedFile {
        var p = 0
        val out = ArrayList<PairedFare>()
        while (p + 5 <= b.size) {
            val from = id128(b, p)
            val to = id128(b, p + 2)
            val n = u8(b, p + 4)
            if (n < 1 || p + 6 * n + 3 > b.size) return PairedFile(label, out, b.size - p, "停在偏移 $p")
            val first = id255(b, p + 5)
            val second = id255(b, p + 7)
            val overrides = ArrayList<FareOverride>(n - 1)
            var q = p + 9
            repeat(n - 1) {
                overrides.add(FareOverride(id255(b, q), id255(b, q + 2), id255(b, q + 4)))
                q += 6
            }
            out.add(PairedFare(from, to, first, second, overrides))
            p += 6 * n + 3
        }
        return PairedFile(label, out, b.size - p, "ok")
    }

    /**
     * `pk*.dat`：`[2B(255) 起站][2B(255) 到站][1B 条数 n]` + n × `[2B 票价号][1B 席别位图][按位加权个 2B 价]`。
     * 权重表见 [OfflineBinary.seatSlots]，硬卧占 3 格、软卧占 2 格，所以不能按固定行宽跳。
     */
    fun parseSeatFares(b: ByteArray, label: String): SeatFile {
        var p = 0
        val out = ArrayList<SeatFare>()
        while (p + 5 <= b.size) {
            val from = id255(b, p)
            val to = id255(b, p + 2)
            val n = u8(b, p + 4)
            if (n < 0) return SeatFile(label, out, b.size - p, "停在偏移 $p")
            p += 5
            val entries = ArrayList<SeatFareEntry>(n)
            var broken = false
            repeat(n) {
                if (broken) return@repeat
                val key = id255(b, p)
                val flag = u8(b, p + 2)
                if (key < 0 || flag < 0 || p + 3 + seatSlots(flag) * 2 > b.size) {
                    broken = true
                    return@repeat
                }
                val slots = seatSlots(flag)
                entries.add(SeatFareEntry(key, flag, IntArray(slots) { id255(b, p + 3 + it * 2) }))
                p += 3 + slots * 2
            }
            if (broken) return SeatFile(label, out, b.size - p, "行内错位")
            out.add(SeatFare(from, to, entries))
        }
        return SeatFile(label, out, b.size - p, "ok")
    }

    /** `ts.dat`：定长 16 字节记录平铺（[2B 票价号][4B 起][4B 止][2B 周期][2B 位图][2B 价]）。本版本 0 字节。 */
    fun parsePriceRules(b: ByteArray): List<PriceRule> {
        val out = ArrayList<PriceRule>(b.size / 16)
        var p = 0
        while (p + 16 <= b.size) {
            out.add(
                PriceRule(
                    priceKey = id255(b, p),
                    startDate = be(b, p + 2, 4),
                    endDate = be(b, p + 6, 4),
                    period = be(b, p + 10, 2),
                    bitmap = be(b, p + 12, 2),
                    price = id255(b, p + 14),
                ),
            )
            p += 16
        }
        return out
    }

    /**
     * `sch.dat`：`[2B 车次ID][2B 站ID][1B 行数 n]` + n 行，行宽 `type==0 ? 12 : 9`。
     * 官方 `Schedule.get_schedule` 的合并优先级是 未定 → 时刻调整/停运 → 停运遇加开变加开 → 加开遇时刻调整回到时刻，
     * 这套判定属于「查询期」逻辑，这里只把行原样解出来、不提前替调用方决定。本版本 0 字节。
     */
    fun parseSchedule(b: ByteArray): List<ScheduleEntry> {
        val out = ArrayList<ScheduleEntry>()
        var p = 0
        while (p + 5 <= b.size) {
            val trainId = id255(b, p)
            val stationId = id255(b, p + 2)
            val n = u8(b, p + 4)
            if (trainId < 0 || stationId < 0 || n < 0) break
            p += 5
            val items = ArrayList<ScheduleItem>(n)
            var broken = false
            repeat(n) {
                if (broken) return@repeat
                if (p + 9 > b.size) {
                    broken = true
                    return@repeat
                }
                val type = u8(b, p)
                val start = date128(b, p + 1)
                val end = date128(b, p + 5)
                if (type == 0) {
                    if (p + 12 > b.size) {
                        broken = true
                        return@repeat
                    }
                    items.add(ScheduleItem(type, start, end, IntArray(3) { u8(b, p + 9 + it) }))
                    p += 12
                } else {
                    items.add(ScheduleItem(type, start, end, IntArray(0)))
                    p += 9
                }
            }
            if (broken) break
            out.add(ScheduleEntry(trainId, stationId, items))
        }
        return out
    }

    /** 一个票价文件的解出结果 + 对账信息（残留字节、停在哪）。 */
    data class PairedFile(val label: String, val fares: List<PairedFare>, val residual: Int, val stoppedAt: String) {
        private val index: Map<Int, List<PairedFare>> = fares.groupBy { pairKey(it.from, it.to) }

        /** 两个方向都能查到，与官方 `get_price_*` 先正查再反查的行为一致。 */
        fun fare(from: Int, to: Int): PairedFare? = index[pairKey(from, to)]?.firstOrNull {
            (it.from == from && it.to == to) || (it.from == to && it.to == from)
        }
    }

    /** [PairedFile] 的席别版：`pk*.dat`。 */
    data class SeatFile(val label: String, val fares: List<SeatFare>, val residual: Int, val stoppedAt: String) {
        private val index: Map<Int, List<SeatFare>> = fares.groupBy { pairKey(it.from, it.to) }

        fun fare(from: Int, to: Int): SeatFare? = index[pairKey(from, to)]?.firstOrNull {
            (it.from == from && it.to == to) || (it.from == to && it.to == from)
        }
    }

    /** 官方用来表示「这条记录适用于整车次所有站」的站 ID 哨兵。 */
    const val TRAIN_WIDE_STATION = 16384
}