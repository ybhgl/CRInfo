package com.crinfo.app.data

import android.content.Context
import com.crinfo.app.data.model.ArrivalItem
import com.crinfo.app.data.model.BoardDirection
import com.crinfo.app.data.model.BoardEntry
import com.crinfo.app.data.model.DepartureItem
import com.crinfo.app.data.model.LiveBoard
import com.crinfo.app.data.model.StationAddress
import com.crinfo.app.data.model.StationPage
import com.crinfo.app.data.model.StationScreen
import com.crinfo.app.data.model.StationView
import com.crinfo.app.data.model.TrainKind
import com.crinfo.app.data.model.buildBoard
import com.crinfo.app.data.model.buildScreen
import com.crinfo.app.data.model.filledByCarTypes
import com.crinfo.app.data.model.filledFrom
import com.crinfo.app.data.model.stationDayDiff
import com.crinfo.app.data.model.supplementByCode
import com.crinfo.app.data.offline.OfflineDbRepository
import com.crinfo.app.data.offline.OfflineFallback
import com.crinfo.app.data.offline.carTypeIndexLookup
import com.crinfo.app.data.remote.CtripApi
import com.crinfo.app.data.remote.LiechetimeApi
import com.crinfo.app.data.remote.WxxcxApi
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 车站查询的两类数据源，两者都要读全天板面，区别只在要不要挂实时状态：
 * - **车次**：目标日期该站的所有车辆，图定时刻，来自 wxxcx 板面（实测北京南 517 条，含通过车）。
 * - **大屏**：此刻站厅那块屏在演什么，主源是携程（实测同时刻只有 出发 12 / 到达 32 条），
 *   再用板面补车型/担当/站台；携程出错或给空就按方向回落 liechetime（实测两源同源同值），
 *   实时两路都拿不到时退化为「板面 + 时间窗」，界面改口说明。
 */
object StationBoardRepository {

    /**
     * 车次页：目标日期该站全天图定。板面偶尔缺个别车次的车型串（临客、新开车），
     * 型号与徽标都从它派生，所以同样拿离线车底补最后一级空缺。
     */
    suspend fun loadTrains(
        context: Context,
        telecode: String,
        date: String,
        carTypes: Map<String, String> = emptyMap(),
    ): StationPage {
        val (trains, address, name, city, bureau) = fetchStation(context, telecode, date.replace("-", ""))
        val board = buildBoard(trains, telecode)
        val offlineDb = OfflineDbRepository(context).current()
        val fill: (List<BoardEntry>) -> List<BoardEntry> = { rows ->
            val styled = if (carTypes.isEmpty()) rows else rows.filledByCarTypes { code -> carTypeIndexLookup(carTypes, code) }
            OfflineFallback.referencePlatforms(offlineDb, name, styled)
        }
        val departures = fill(board.departures)
        val arrivals = fill(board.arrivals)
        return StationPage(
            view = StationView.Trains,
            stationName = name,
            telecode = telecode,
            city = city,
            address = address,
            stationBureau = bureau,
            departures = departures,
            arrivals = arrivals,
            fromLiveSource = false,
            updatedAt = clockText(),
        )
    }

    /**
     * 大屏。车型与检票口都要靠板面补，而刚过零点时 12306 的**当日**图几乎没灌
     * （实测 11-02 00:31 上海虹桥：今日 95 行，昨日 635、明日 638，`status=true` 不报错），
     * 实时源照旧几百条 → 板面挂不上，整屏既没型号也没徽标。所以按
     * **今日板面 → 明日板面 → 离线车底** 三级兜底；覆盖率还够时不多打请求，
     * 白天不会为零星几趟临客白跑一趟。
     */
    suspend fun loadScreen(
        context: Context,
        telecode: String,
        carTypes: Map<String, String> = emptyMap(),
    ): StationPage = coroutineScope {
        val today = LocalDate.now()
        val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
        val (trains, address, name, city, bureau) = fetchStation(context, telecode, compact(today))
        // 大屏是第三方实时源，两路并发各自容错：挂了只是没有状态，不能连累板面
        val dep = async { screenDepartures(name, nowMinutes) }
        val arr = async { screenArrivals(name, nowMinutes) }
        val liveDepartures = dep.await()
        val liveArrivals = arr.await()
        var screen = buildScreen(liveDepartures, liveArrivals, buildBoard(trains, telecode), nowMinutes)
        if (sparseAgainst(liveDepartures, liveArrivals, screen)) {
            val next = WxxcxApi.screenTrains(telecode, compact(today.plusDays(1)))
            screen = screen.filledFrom(buildBoard(next, telecode).supplementByCode())
        }
        if (stillMissingStyle(screen) && carTypes.isNotEmpty()) {
            screen = screen.filledByCarTypes { code -> carTypeIndexLookup(carTypes, code) }
        }
        // 实时检票口缺的车次补一条图定参考站台。同一口径两处共用：只在缺位时补，
        // 有检票口就不并列，免得静态值稀释实时值
        val offlineDb = OfflineDbRepository(context).current()
        val depRows = OfflineFallback.referencePlatforms(offlineDb, name, screen.departures)
        val arrRows = OfflineFallback.referencePlatforms(offlineDb, name, screen.arrivals)
        StationPage(
            view = StationView.Screen,
            stationName = name,
            telecode = telecode,
            city = city,
            address = address,
            stationBureau = bureau,
            departures = depRows,
            arrivals = arrRows,
            fromLiveSource = screen.fromLiveSource,
            updatedAt = clockText(),
        )
    }

    private fun compact(day: LocalDate): String = day.toString().replace("-", "")

    /** 当日板面稀到连实时行都挂不满（车型缺失超过两成）才值得再打一次明日请求。 */
    private fun sparseAgainst(
        liveDepartures: List<LiveBoard>,
        liveArrivals: List<LiveBoard>,
        screen: StationScreen,
    ): Boolean {
        if ((liveDepartures + liveArrivals).isEmpty()) return false
        return blankStyleRatio(screen) > 0.2
    }

    private fun blankStyleRatio(screen: StationScreen): Double {
        val all = screen.departures + screen.arrivals
        if (all.isEmpty()) return 0.0
        return all.count { it.trainStyle.isBlank() }.toDouble() / all.size
    }

    private fun stillMissingStyle(screen: StationScreen): Boolean =
        (screen.departures + screen.arrivals).any { it.trainStyle.isBlank() }

    /** 板面 + 地址 + 站名（站名一律以离线车站表为准，地址接口给的是「北京南站」）。 */
    private suspend fun fetchStation(
        context: Context,
        telecode: String,
        ymd: String,
    ): StationFacts = coroutineScope {
        val address = async { runCatching { WxxcxApi.stationAddress(telecode) }.getOrNull() }
        val trains = WxxcxApi.screenTrains(telecode, ymd)
        val known = StationRepository.byCode(context, telecode)
        val fallbackName = trains.firstOrNull()?.let {
            if (it.fromTelecode == telecode) it.fromStation else it.toStation
        } ?: telecode
        StationFacts(
            trains = trains,
            address = address.await(),
            name = known?.name ?: fallbackName,
            city = known?.city.orEmpty(),
            bureau = stationBureauNameFromCodes(trains.map { it.stationCorporation }),
        )
    }

    private data class StationFacts(
        val trains: List<com.crinfo.app.data.model.ScreenTrain>,
        val address: StationAddress?,
        val name: String,
        val city: String,
        val bureau: String,
    )

    /**
     * 实时大屏出发条：主源携程，出错或整方向为空才补打 liechetime 同一方向。
     * 两路都空就是真没有（小站此刻确实没有到达车次），那种情况照旧退到「板面 + 时间窗」。
     *
     * 跨天数两源口径不同，各按各的来：携程**不给** dayDiff，只能按「时刻明显早于此刻＝明天那张屏」推导；
     * liechetime 自带 dayDiff（实测就是「车站日 − 今天」），直接以它为准。
     */
    private suspend fun screenDepartures(station: String, nowMinutes: Int): List<LiveBoard> =
        runCatching { CtripApi.departures(station).map { it.toLive(stationDayDiff(it.departTime, nowMinutes)) } }
            .getOrDefault(emptyList())
            .ifEmpty { runCatching { LiechetimeApi.departures(station).map { it.toLive(it.dayDiff) } }.getOrDefault(emptyList()) }

    private suspend fun screenArrivals(station: String, nowMinutes: Int): List<LiveBoard> =
        runCatching { CtripApi.arrivals(station).map { it.toLive(stationDayDiff(it.arriveTime, nowMinutes)) } }
            .getOrDefault(emptyList())
            .ifEmpty { runCatching { LiechetimeApi.arrivals(station).map { it.toLive(it.dayDiff) } }.getOrDefault(emptyList()) }

    private fun DepartureItem.toLive(dayDiff: Int) =
        LiveBoard(trainCode, state, departTime, toStation, wicket, waitingRoom, null, dayDiff)

    private fun ArrivalItem.toLive(dayDiff: Int) =
        LiveBoard(trainCode, state, arriveTime, fromStation, null, null, exitingPort, dayDiff)

    private fun clockText(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
}
