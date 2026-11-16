package com.crinfo.app.data.offline

import com.crinfo.app.data.model.Seat
import com.crinfo.app.data.model.SeatOffer
import com.crinfo.app.data.model.SeatStatus
import com.crinfo.app.data.offline.OfflineFareParser.PairedFile
import com.crinfo.app.data.offline.OfflineFareParser.PriceRule
import com.crinfo.app.data.offline.OfflineFareParser.PairedFare
import com.crinfo.app.data.offline.OfflineFareParser.ScheduleEntry
import com.crinfo.app.data.offline.OfflineFareParser.SeatFile
import com.crinfo.app.data.offline.OfflineFareParser.SeatFare

/**
 * 本轮逆向补齐的那些表（sn/sp/plat/xw/extra 表2-7/票价族/ime/cdn）。
 *
 * 单独成组而不是平铺进 [OfflineDb]：那张 data class 已经有 9 个字段和一批派生索引，
 * 再塞 15 份表会让「库里到底装了什么」一眼看不出。这里按**数据来源**分组，
 * 每一组的走步都在自己的文件里，缺哪组不影响别组（一律给空表兜底）。
 */
class OfflineExtras(
    val sp: SpData = SpData.EMPTY,
    val cities: StationGroupData = StationGroupData.EMPTY,
    val extra: ExtraData = ExtraData.EMPTY,
    val platforms: PlatformTable = PlatformTable.EMPTY,
    val seats: SeatTable = SeatTable.EMPTY,
    val pairedFares: List<PairedFile> = emptyList(),
    val seatFares: List<SeatFile> = emptyList(),
    val priceRules: List<PriceRule> = emptyList(),
    val schedule: List<ScheduleEntry> = emptyList(),
    val imeIndex: Map<String, String> = emptyMap(),
    val cdnBlockIds: List<String> = emptyList(),
    val zcdnBlockIds: List<String> = emptyList(),
) {

    /* ---------------- 车次号 ↔ 12 位 trainNo ---------------- */

    /** 车次 ID → 12 位 trainNo（`sp.dat` 表2，下标即 ID）。 */
    fun trainNoOf(trainId: Int): String? = sp.trainNoOf(trainId)

    /** 12 位 trainNo → 车次 ID。这张反表是 §2 官方径路能精确命中的前提。 */
    val trainIdByTrainNo: Map<String, Int> = buildMap {
        sp.trainNos.forEachIndexed { index, no -> if (no.isNotEmpty()) putIfAbsent(no, index) }
    }

    fun trainIdOfTrainNo(trainNo: String): Int? = trainIdByTrainNo[trainNo.trim()]

    /* ---------------- 担当 ---------------- */

    /**
     * 车次 ID → 担当企业代码。优先 extra.dat 表2（车次级、真库 14753 条），
     * 其次 sp.dat 表1（带生效日期，本版本只有零星十几条）。查不到返回 null，
     * 意思是「库里没说」，不是「没有担当」。
     */
    fun dutyCode(trainId: Int, date: Int? = null): Int? {
        extra.dutyCode(trainId)?.let { return it }
        return if (date != null) sp.dutyCode(trainId, date) else sp.duties.firstOrNull { it.trainId == trainId }?.enterpriseCode
    }

    /** 代码 → 局段名。企业名表的下标就是代码，越界给空串（与官方 `getQiYe` 同行为）。 */
    fun dutyName(enterprises: List<String>, trainId: Int, date: Int? = null): String? =
        dutyCode(trainId, date)?.let { enterprises.getOrNull(it)?.takeIf { name -> name.isNotBlank() } }

    /** 车次在某站的细分担当文字（本版本站级记录为 0 条，所以基本拿不到）。 */
    fun stationDutyText(trainId: Int, stationId: Int, date: Int): String? =
        extra.dutyText(trainId, stationId, date)

    /* ---------------- 股道 / 席别 / 换号 ---------------- */

    fun platformOf(trainId: Int, stationId: Int): String? = platforms.platformOf(trainId, stationId)

    fun seatsOfTrain(trainId: Int): Int? = seats.seatFlags(trainId)

    /** 位含义见 `Consts.XW_TYPE_*` 同一套：硬座1 软座2 硬卧4 软卧8 一等16 二等32 动卧64。 */
    fun hasSeat(trainId: Int, seatBit: Int): Boolean = seats.hasSeat(trainId, seatBit)

    /**
     * 席别种类 → 可渲染的 `SeatOffer`（离线态的席别区数据源）。
     *
     * 库里只说这趟车**有**这些席别，没给余票数也没给票价，所以一律 [SeatStatus.Unknown]：
     * 界面按无票的非高亮样式出字，而 `Trip.ticketKnown=false` 保证它既不会被打成「售罄」，
     * 也不会被「只看有票」整列杀掉 —— 离线态是「不知道有没有票」，不是「卖完了」。
     * 顺序跟着 `Seat` 声明序，与在线卡片同一排法。
     *
     * 库里没有这趟车的席别记录时返回 **null**：「没数据」不等于「这趟车没有任何席别」。
     */
    fun seatOffers(trainId: Int): List<SeatOffer>? = seatsOfTrain(trainId)?.let { flags ->
        Seat.entries
            .filter { seat -> SEAT_BITS[seat]?.let { bit -> flags and bit != 0 } == true }
            .map { SeatOffer(it, SeatStatus.Unknown, null) }
    }

    /** 卧铺席别位图合起来的一位：离线态「铺」徽标与席别区同源（有席别记录且含任一种卧铺）。 */
    fun hasSleeperCabin(trainId: Int): Boolean {
        val flags = seatsOfTrain(trainId) ?: return false
        return flags and (SEAT_BITS.getValue(Seat.HARD_SLEEPER) or
            SEAT_BITS.getValue(Seat.SOFT_SLEEPER) or
            SEAT_BITS.getValue(Seat.MOVING_SLEEPER)) != 0
    }

    /** 换号车在给定区间里程上真实显示的那一段车次号。 */
    fun exactCode(trainId: Int, code: String, km: Int): String = seats.exactCode(trainId, code, km)

    fun isSTrain(trainId: Int): Boolean = extra.isSTrain(trainId)

    fun fuXingLevel(trainId: Int): Int = extra.fuXingLevel(trainId)

    fun turnStationsOf(trainId: Int): IntArray = extra.turnStationIds[trainId] ?: IntArray(0)

    /* ---------------- 车站归属 / 同城站 / 地址 ---------------- */

    /** 站 ID → 同城站集合（请求站置顶）。这就是 `s*.dat` 候选被城市主站包住时的官方解释。 */
    fun cityMembers(stationId: Int): IntArray = cities.cityMembers(stationId)

    fun cityGroupKey(stationId: Int): Int? = cities.groupKeyOfStation[stationId]

    /** 站 ID → 电报码：先吃 sn.dat 的补充表（364 条），没有再交回调用方走 routes 节点。 */
    fun telecodeOf(stationName: String): String? = cities.telecodes[stationName.trim()]

    fun belongOf(stationId: Int): StationBelong? = sp.belongs[stationId]

    /** 站 ID → 省名（sp.dat 表4 + 表3 两级查）。 */
    fun provinceOf(stationId: Int): String? = sp.belongs[stationId]?.let { sp.provinceNames[it.provinceId] }

    /**
     * 站 ID → 车站归属单位名（sp.dat 表4 的企业代码落到 extra.dat 企业名表，
     * 形如「京局北京段」）。库里没这条记录返回 null，意思是「库里没说」，不是「不归属任何局段」。
     */
    fun stationSegmentName(enterprises: List<String>, stationId: Int): String? =
        sp.belongs[stationId]?.let { enterprises.getOrNull(it.enterpriseCode)?.takeIf { name -> name.isNotBlank() } }

    /** 站 ID → 「省 市 区」串；库里没这条时退回按站名查补充表。 */
    fun locationOf(stationId: Int, stationName: String? = null): String? =
        sp.locationById[stationId] ?: stationName?.let { sp.locationByName[it.trim()] }

    /* ---------------- 票价（本版本近乎空壳，单位角） ---------------- */

    fun pairedFare(from: Int, to: Int): PairedFare? = pairedFares.firstNotNullOfOrNull { it.fare(from, to) }

    fun seatFare(from: Int, to: Int): SeatFare? = seatFares.firstNotNullOfOrNull { it.fare(from, to) }

    /** 某票价号在查询日是否被 ts.dat 改写；0 字节版本恒为 null。 */
    fun priceRule(priceKey: Int, date: Int): PriceRule? =
        priceRules.firstOrNull { it.priceKey == priceKey && date in it.startDate..it.endDate }

    /**
     * 某车某站在查询日的按日调整行。库里 `sch.dat` 是 0 字节，恒为空列表 ——
     * 界面**不许**据此显示「停运/加开」，这是本轮反复强调的那条底线。
     */
    fun scheduleOf(trainId: Int, stationId: Int): List<ScheduleEntry> =
        schedule.filter { it.trainId == trainId && (it.isTrainWide || it.stationId == stationId) }

    companion object {
        val EMPTY = OfflineExtras()

        /**
         * `xw.dat` 席别位图 → 本项目的 `Seat`。位值与路路通 `Consts.XW_TYPE_*` 一一对应
         * （硬座1 软座2 硬卧4 软卧8 一等16 二等32 动卧64）；商务座/特等座/高级软卧等更细的档位在库里没有位，
         * 所以离线席别区只可能出这七种，别指望它替代在线的完整席别清单。
         */
        private val SEAT_BITS = mapOf(
            Seat.HARD_SEAT to 1,
            Seat.SOFT_SEAT to 2,
            Seat.HARD_SLEEPER to 4,
            Seat.SOFT_SLEEPER to 8,
            Seat.FIRST to 16,
            Seat.SECOND to 32,
            Seat.MOVING_SLEEPER to 64,
        )

        /**
         * 逐份解，任何一份坏掉只让那一组为空：装载整库的主流程不能因为某张附表错位就整体失败，
         * 否则一个未来图定的字段变化会把经由、担当、时刻全部拖没。
         *
         * [onProgress] 按**已解份数 / 总份数**回报 0~1 —— 附表有二十来份，逐份推进比假动画实在。
         */
        fun parse(
            entries: Map<String, ByteArray>,
            trainCount: Int,
            onProgress: ((Float) -> Unit)? = null,
        ): OfflineExtras {
            // 总份数：5 份主附表 + 7 份成对票价 + 10 份席位票价 + 5 份零散表
            val total = 27
            var done = 0
            fun step() {
                done++
                onProgress?.invoke((done.toFloat() / total).coerceIn(0f, 1f))
            }

            fun <T> load(key: String, block: (ByteArray) -> T): T? {
                val out = entries[key]?.let { runCatching { block(it) }.getOrNull() }
                step()
                return out
            }

            val sp = load("sp.dat", SpData::parse) ?: SpData.EMPTY
            val cities = load("sn.dat", StationGroupData::parseSn) ?: StationGroupData.EMPTY
            val extra = load("extra.dat", { ExtraData.parse(it, trainCount) }) ?: ExtraData.EMPTY
            val platforms = load("plat.dat", PlatformTable::parse) ?: PlatformTable.EMPTY
            val seats = load("xw.dat", { SeatTable.parse(it, trainCount) }) ?: SeatTable.EMPTY
            val paired = listOf("rwp.dat", "dwp.dat", "p0.dat", "p1.dat", "p2.dat", "p3.dat", "p4.dat")
                .mapNotNull { name ->
                    val r = entries[name]?.let { runCatching { OfflineFareParser.parsePairedFares(it, name) }.getOrNull() }
                    step()
                    r
                }
            val seat = (0 until 10).mapNotNull { i ->
                val name = "pk$i.dat"
                val r = entries[name]?.let { runCatching { OfflineFareParser.parseSeatFares(it, name) }.getOrNull() }
                step()
                r
            }
            val rules = load("ts.dat", OfflineFareParser::parsePriceRules).orEmpty()
            val schedule = load("sch.dat", OfflineFareParser::parseSchedule).orEmpty()
            val ime = load("ime.dat", StationGroupData::parseIme).orEmpty()
            val cdn = load("cdn.dat", StationGroupData::parseBlockIds).orEmpty()
            val zcdn = load("zcdn.dat", StationGroupData::parseBlockIds).orEmpty()
            onProgress?.invoke(1f)
            return OfflineExtras(sp, cities, extra, platforms, seats, paired, seat, rules, schedule, ime, cdn, zcdn)
        }
    }
}
