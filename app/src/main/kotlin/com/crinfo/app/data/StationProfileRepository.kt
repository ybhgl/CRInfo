package com.crinfo.app.data

import android.content.Context
import com.crinfo.app.data.model.StationPage
import com.crinfo.app.data.model.StationProfile
import com.crinfo.app.data.model.SaleStart
import com.crinfo.app.data.offline.OfflineDb
import com.crinfo.app.data.remote.WxxcxApi
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 车站信息弹窗的数据聚合。五路来源各自容错：任何一路挂了只让那一项空着，
 * 绝不把整个弹窗拖成错误页——站名、电报码、路局这些本页已有的信息不该被网络连坐。
 */
object StationProfileRepository {

    /**
     * 与板面同一套缓存口径。键**只用 station 码、不带日期**：这五路里只有换乘接口收 `train_date`，
     * 实测返回与日期无关（见 [WxxcxApi.relayTimes]），带上日期会让用户翻 15 天日历就打 15×5 个请求。
     */
    private val cache = ConcurrentHashMap<String, Pair<Long, StationProfile>>()
    private const val TTL_MS = 10 * 60 * 1000L

    /** 一次最多列几座同城站。站名是本地取的，限制只为不让弹窗拖成一长串。 */
    private const val MAX_CITY_STATIONS = 16

    fun clearCache() = cache.clear()

    /**
     * 弹窗点开那一刻用的**零网络档案**：站名、电报码、城市、隶属路局、地址都已在板面页里，
     * 同城站名走常驻内存的内置站表——所以这份能同步算出来，弹窗不必等接口才亮（跟手）。
     * 远程五项留空、`remoteReady=false`，由界面显示「加载中…」，随后被 [load] 的完整档案替换。
     */
    fun localProfile(context: Context, page: StationPage, offline: Boolean): StationProfile {
        val cityRows = cityStations(context, db = null, stationId = null, page = page)
        return StationProfile(
            stationName = page.stationName,
            telecode = page.telecode,
            city = page.city,
            bureau = page.stationBureau,
            address = page.address,
            offlineLocation = null,
            saleTimes = cityRows.map { SaleStart(it.name, "", it.code) },
            relays = emptyList(),
            weather = null,
            intro = null,
            services = emptyList(),
            // 离线态五项一概不显示，也就没有「加载中」可等，直接算就位
            remoteReady = offline,
        )
    }

    /**
     * 取一座车站的完整档案信息。
     *
     * @param offline 离线查询态：起售与换乘只有联网接口有，直接不发消息。
     * @param db 已装好的离线库，用 sp.dat 补地址与车站归属段名、sn.dat 补同城站。
     */
    suspend fun load(
        context: Context,
        page: StationPage,
        date: String,
        offline: Boolean,
        db: OfflineDb?,
    ): StationProfile {
        // 联网档案的缓存键要带上「当时装没装离线库」：装库之前缓存下来的档案里没有段名与兜底地址，
        // 共用一个键的话它会在 TTL 内一直顶着一个明明能补全的空值。
        val key = if (offline) "${page.telecode}@offline" else "${page.telecode}@${if (db == null) "nodb" else "db"}"
        if (!offline) {
            cache[key]?.takeIf { System.currentTimeMillis() - it.first < TTL_MS }?.let { return it.second }
        }
        val profile = build(context, page, date, offline, db)
        if (!offline) cache[key] = System.currentTimeMillis() to profile
        return profile
    }

    private suspend fun build(
        context: Context,
        page: StationPage,
        date: String,
        offline: Boolean,
        db: OfflineDb?,
    ): StationProfile = coroutineScope {
        val stationId = db?.stationIdByName?.get(page.stationName.trim())
        val segment = db?.let { d ->
            stationId?.let { d.extras.stationSegmentName(d.enterprises, it) }
        }.orEmpty()
        val offlineLocation = db?.let { d -> stationId?.let { d.extras.locationOf(it, page.stationName) } }

        // 起售接口按城市名查、一次返回整城车站；城市字段缺失时拿站名去掉方位后缀去撞
        val cityName = page.city.ifBlank { page.stationName.removeSuffix("站").trim() }
        // 板面页地址可能为空（离线退化的大屏、接口那次没挂上），联网时补打一次，查看地图才不缺坐标
        val freshAddress = if (offline || page.address != null) null else async {
            runCatching { WxxcxApi.stationAddress(page.telecode) }.getOrNull()
        }
        val sale = if (offline) null else async {
            runCatching { WxxcxApi.saleStartTimes(cityName) }.getOrDefault(emptyList())
        }
        val relay = if (offline) null else async {
            runCatching { WxxcxApi.relayTimes(page.telecode, date) }.getOrDefault(emptyList())
        }
        val weather = if (offline) null else async {
            runCatching { WxxcxApi.stationWeather(page.telecode) }.getOrNull()
        }
        val intro = if (offline) null else async {
            runCatching { WxxcxApi.stationIntroduction(page.telecode) }.getOrNull()
        }
        val services = if (offline) null else async {
            runCatching { WxxcxApi.stationServices(page.telecode) }.getOrDefault(emptyList())
        }

        // 同城站两种优先序：离线态库里那份是官方分组，直接用；
        // 联网态必须先走内置站表与起售接口，两边都给不出才拿库补 —— 静态分组不许压过实时来源
        val cityRows = cityStations(context, if (offline) db else null, stationId, page)
        val saleTimes = sale?.await().orEmpty()
        val merged = when {
            cityRows.isNotEmpty() -> mergeCityStations(context, cityRows, saleTimes)
            // 接口一次就返回整城车站，本地站表没覆盖到（没装库、小站未入分组）时直接用它兜底
            saleTimes.isNotEmpty() -> saleTimes.map { it.withCode(context) }
            else -> cityStations(context, db, stationId, page).map { SaleStart(it.name, "", it.code) }
        }

        StationProfile(
            stationName = page.stationName,
            telecode = page.telecode,
            city = page.city,
            bureau = page.stationBureau,
            stationSegment = segment,
            address = page.address ?: freshAddress?.await(),
            offlineLocation = offlineLocation,
            saleTimes = merged,
            relays = relay?.await().orEmpty(),
            weather = weather?.await(),
            intro = intro?.await(),
            services = services?.await().orEmpty(),
        )
    }

    /**
     * 同城站候选：离线库 sn.dat 的官方分组优先，没装库时退回内置站表按城市取。
     * 公开给界面在「联网起售行缺电报码」时补一次本地查（内置站表是内存缓存，与选站面板同一口径）。
     */
    fun cityStations(context: Context, db: OfflineDb?, stationId: Int?, page: StationPage): List<Station> {
        if (db != null && stationId != null) {
            val members = db.extras.cityMembers(stationId).toList()
                .mapNotNull { db.stationNames.getOrNull(it)?.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (members.isNotEmpty()) {
                return members.take(MAX_CITY_STATIONS).map { name ->
                    StationRepository.byName(context, name) ?: Station(name, "", "", "", page.city)
                }
            }
        }
        if (page.city.isBlank()) return emptyList()
        return StationRepository.cityStations(context, page.city, MAX_CITY_STATIONS)
    }

    /** 站名 → 电报码：起售接口只给名字，本地站表能落上就落，落不到留空由界面拒绝切换。 */
    private fun SaleStart.withCode(context: Context): SaleStart =
        copy(code = code.ifBlank { StationRepository.byName(context, name)?.code.orEmpty() })

    /**
     * 同城站与起售时间对齐。两边站名都是 12306 的正式站名，直接同名匹配；
     * 接口没给的站保留成空时间，界面按「未提供」显示而不是把站整条丢掉。
     */
    private fun mergeCityStations(
        context: Context,
        stations: List<Station>,
        saleTimes: List<SaleStart>,
    ): List<SaleStart> {
        val withCode = stations.map { SaleStart(it.name, "", it.code) }
        if (saleTimes.isEmpty()) return withCode
        val byName = saleTimes.associateBy({ it.name.trim() }, { it.time })
        val ordered = withCode.map { SaleStart(it.name, byName[it.name.trim()].orEmpty(), it.code) }
        // 接口多给的站（离线库没收录、或城市分组没覆盖到）补在末尾，别丢信息
        val known = stations.map { it.name.trim() }.toSet()
        val extra = saleTimes.filter { it.name.trim() !in known }.map { it.withCode(context) }
        return (ordered + extra).distinctBy { it.name }
    }
}
