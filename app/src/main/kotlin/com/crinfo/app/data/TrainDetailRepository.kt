package com.crinfo.app.data

import android.content.Context
import com.crinfo.app.data.model.SeatOffer
import com.crinfo.app.data.model.TrainDetail
import com.crinfo.app.data.model.RunningDays
import com.crinfo.app.data.model.TrainTimetable
import com.crinfo.app.data.model.mergeSchedule
import com.crinfo.app.data.model.timeToMinutes
import com.crinfo.app.data.offline.OfflineDbRepository
import com.crinfo.app.data.offline.OfflineFallback
import com.crinfo.app.data.remote.LiechetimeApi
import com.crinfo.app.data.remote.LeftTicketApi
import com.crinfo.app.data.remote.LltskbCarApi
import com.crinfo.app.data.remote.RailReApi
import com.crinfo.app.data.remote.WxxcxApi
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 12306 的车次号形如 G1 / D7 / K516 / Z96 / C2002 / 7214（纯数字普客）。
 * 输入容忍空格与大小写，非法直接返回 null 让界面提示，绝不打接口。
 */
fun parseTrainCode(raw: String): String? {
    val code = raw.trim().uppercase().replace(" ", "").replace("-", "")
    if (code.isEmpty()) return null
    return if (Regex("^[A-Z]{0,2}\\d{1,4}[A-Z]?$").matches(code)) code else null
}

/** 接口给的路局短名是「上海」，界面统一读作「上海局」。 */
fun normalizeBureauName(raw: String): String {
    val name = raw.trim()
    return when {
        name.isEmpty() -> ""
        name.endsWith("局") -> name
        else -> "${name}局"
    }
}

/**
 * 车站所属路局的字母码 → 局名。码表取自 moerail 的 `bureau.js`（开源路局码表），
 * 已撤销的旧局码归到现属局（齐齐哈尔 → 哈尔滨局）；`H` 由项目实测补齐
 * （G1 担当 `corporation_code` = `H00`，对应上海局）。
 * 码表里没有的字母一律返回 null 由界面留空，绝不猜一个局名糊上去。
 */
private val bureauNamesByCode = mapOf(
    "B" to "哈尔滨局", "X" to "哈尔滨局",
    "T" to "沈阳局", "D" to "沈阳局", "L" to "沈阳局",
    "P" to "北京局",
    "V" to "太原局",
    "C" to "呼和浩特局",
    "F" to "郑州局",
    "N" to "武汉局",
    "Y" to "西安局",
    "K" to "济南局",
    "H" to "上海局",
    "G" to "南昌局", "S" to "南昌局",
    "Q" to "广州局",
    "Z" to "南宁局",
    "W" to "成都局",
    "M" to "昆明局",
    "J" to "兰州局",
    "R" to "乌鲁木齐局",
    "O" to "青藏公司",
)

/** 码表里的码都是单字母，取 `station_corporation_code` 的 `#` 前段首字母即可。 */
fun railwayBureauName(code: String): String? =
    bureauNamesByCode[code.trim().take(1).uppercase()]

/**
 * 一张板面推车站隶属路局：每行都带本站的车站管辖单位码，同站本就一致，
 * 多数投票只用来防个别脏行。码表外的字母给空串，界面按缺项不显示，绝不猜一个局名糊上去。
 * 注意别再叠 [normalizeBureauName]：码表给的已经是「北京局」这种全名，
 * 那层补字是给接口短名（`bureau_code_name` 只回「上海」）用的，套两遍会得到「青藏公司局」。
 */
fun stationBureauNameFromCodes(codes: List<String>): String {
    val top = codes.map { it.trim() }.filter { it.isNotEmpty() }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key ?: return ""
    return railwayBureauName(top).orEmpty()
}

/**
 * 余票接口只覆盖今天起 15 天；数据层与界面共用这个判定，避免出现两种口径。
 * `today` 只是给单测留的注入口，业务调用一律用默认值。
 */
fun seatsQueryable(date: String, today: LocalDate = LocalDate.now()): Boolean {
    val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
    return !day.isBefore(today) && !day.isAfter(today.plusDays(LeftTicketApi.PRESALE_DAYS.toLong()))
}

/** 发车前多久以内就不该再打余票接口：这个窗口里接口只给席别列全空的废行。 */
private const val DEPART_GUARD_MINUTES = 15

/**
 * 值不值得为这趟车打一次余票接口。除了预售期，还要卡「查询日是今天且已临近发车」：
 * 图定发车过了或不足 [DEPART_GUARD_MINUTES] 分钟，12306 仍会返回这一行，
 * 但所有席别列都是空串（解析后全归成售罄、无票价），白打一次握手还看着像「无票」。
 * 图定发车解析不出来时按不打处理——宁缺一块信息，也别拿全空的席别糊上卡片。
 */
fun seatsWorthQuerying(date: String, departTime: String?, now: LocalDateTime = LocalDateTime.now()): Boolean {
    if (!seatsQueryable(date, now.toLocalDate())) return false
    // seatsQueryable 已保证 date 可解析，这里不再兜异常
    if (LocalDate.parse(date) != now.toLocalDate()) return true
    val departMinutes = timeToMinutes(departTime.orEmpty())
    if (departMinutes < 0) return false
    return departMinutes >= now.hour * 60 + now.minute + DEPART_GUARD_MINUTES
}

/**
 * 车次详情的聚合入口。一次查询最多打 5 个接口：
 * 时刻表（必需）→ 开行计划/担当路局、终到站板面（里程）、正晚点（当日及以前）、余票（预售期内且未临近发车）。
 * 除时刻表外全部容错——少一项只是少一块信息，不该让整页失败。
 */
object TrainDetailRepository {

    suspend fun load(context: Context, code: String, date: String): TrainDetail = coroutineScope {
        val timetable = WxxcxApi.trainTimetable(code, date)
        val fromCode = timetable.fromTelecode
        val toCode = timetable.toTelecode
        val ymd = date.replace("-", "")
        val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: LocalDate.now()
        val today = LocalDate.now()
        // 图定发车：与概览卡左上、`TrainDetail.departTime` 同一个取法，避免出现两种口径。
        val departTime = timetable.stops.firstOrNull()?.departTime ?: timetable.startTime

        val plan = async { bureauAndPlan(code, date) }
        val km = async {
            runCatching {
                WxxcxApi.screenTrains(toCode, ymd)
                    .firstOrNull { it.trainNo == timetable.trainNo }
                    ?.distanceKm ?: 0
            }.getOrDefault(0)
        }
        // 正晚点只对已开行的车次有意义；未来日期这个备用接口是空的
        val delay = async {
            if (day.isAfter(today)) null
            else runCatching { LiechetimeApi.trainDelay(code, day.toString()) }.getOrNull()
        }
        // 预售期外（含过去日期）接口必然无数据；查询日是今天却已临近/错过图定发车，
        // 只剩席别全空的废行。两种情况都不打，省一次会话握手。
        val seats = async {
            if (!seatsWorthQuerying(date, departTime)) return@async null
            runCatching {
                LeftTicketApi.query(fromCode, toCode, date) { StationRepository.byCode(context, it)?.name }
                    .firstOrNull { it.trainNo == timetable.trainNo || it.trainCode == code }
                    ?.seats
                    ?.takeIf { it.isNotEmpty() }
            }.getOrNull()
        }
        val car = async {
            runCatching { WxxcxApi.carDetail(code, date) }
                .onFailure { android.util.Log.w("CRInfo", "carDetail 请求失败: ${it.message}") }
                .getOrNull()
        }

        val (planBureau, planDays) = plan.await()
        val carDetail = car.await()
        // 官方车底接口没给东西才找第三方补车底号。
        val emuFallback = if (carDetail == null) {
            runCatching { RailReApi.emuFor(code) }.getOrNull()
        } else {
            null
        }
        // 官方车厢图偶发缺图，所以有官方详情时也查路路通，供弹窗切换。
        // 车型优先用官方 trainStyle（G1=CR400BF-A、D2206=CRH3A），没有再用车底号。
        // 第三方站点慢/挂时不能拖住详情页，限时 4 秒、失败当没图。
        val lltskbModel = carDetail?.trainStyle?.takeIf { it.isNotBlank() }
            ?: carDetail?.carCode?.takeIf { it.isNotBlank() }
            ?: emuFallback?.emuNo
        val lltskb = lltskbModel?.let { model ->
            withTimeoutOrNull(4_000) {
                runCatching { LltskbCarApi.imagesFor(model) }.getOrNull()
            }
        }
        var totalKm = km.await()
        var bureauName = planBureau
        var runningDays = planDays
        // 任一字段空了才碰离线库：读一次库里 T 记录，全程里程、担当局段、开行日历一起补。
        // 库里这份与板面同源（G1 实测逐站一致）、日历就是图定起止 + 周期位图；
        // 段名（如「上局上海段」）按用户定的口径原样显示，不再套 normalizeBureauName 补「局」。
        if (totalKm <= 0 || bureauName.isBlank() || runningDays == null) {
            val off = runCatching { offlineSupplement(context, code, timetable) }.getOrNull()
            if (off != null) {
                if (totalKm <= 0) totalKm = off.totalKm
                if (bureauName.isBlank()) bureauName = off.corporation
                if (runningDays == null) runningDays = off.runningDays
            }
        }
        TrainDetail(
            timetable = timetable,
            fromName = timetable.stops.firstOrNull()?.name ?: fromCode,
            toName = timetable.stops.lastOrNull()?.name ?: toCode,
            bureauName = bureauName,
            totalKm = totalKm,
            rows = mergeSchedule(timetable, delay.await()),
            runningDays = runningDays,
            seats = seats.await(),
            carDetail = carDetail,
            emuFallback = emuFallback,
            lltskb = lltskb,
        )
    }

    /**
     * 该站板面上这趟车的累计里程（相对其始发站）。三个时刻表接口都不给逐站里程，
     * 只有板面有；点开站点才取一次，免得一次查询打出二十几个板面请求。
     */
    suspend fun stopMileage(telecode: String, trainNo: String, date: String): Int =
        runCatching {
            WxxcxApi.screenTrains(telecode, date)
                .firstOrNull { it.trainNo == trainNo }?.distanceKm ?: 0
        }.getOrDefault(0)

    /** 开行计划里自带路局全称，拿不到再退担当路局接口，省一次请求。 */
    private suspend fun bureauAndPlan(code: String, date: String): Pair<String, RunningDays?> {
        val plan = runCatching { WxxcxApi.runningDays(code, date) }.getOrNull()
        val fromPlan = normalizeBureauName(plan?.bureau?.name.orEmpty())
        if (fromPlan.isNotBlank()) return fromPlan to plan
        val short = runCatching { WxxcxApi.operatingBureau(code, date) }.getOrNull()
        return normalizeBureauName(short?.name.orEmpty()) to plan
    }

    /** 联网空了才读的三项兜底：读一次库记录，里程/担当/日历一起给。 */
    private class OfflineSupplement(val totalKm: Int, val corporation: String, val runningDays: RunningDays?)

    private suspend fun offlineSupplement(context: Context, code: String, timetable: TrainTimetable): OfflineSupplement {
        val repo = OfflineDbRepository(context)
        val db = repo.current() ?: return OfflineSupplement(0, "", null)
        val record = db.trainIdOf(code)?.let { repo.timetable(it) }
        return OfflineSupplement(
            totalKm = OfflineFallback.totalKm(db, record, timetable),
            corporation = OfflineFallback.corporation(db, code),
            runningDays = record?.let { OfflineFallback.runningDays(db, it) },
        )
    }
}
