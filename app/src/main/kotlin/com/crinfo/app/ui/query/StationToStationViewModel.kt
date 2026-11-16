package com.crinfo.app.ui.query

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crinfo.app.data.FavoritesStore
import com.crinfo.app.data.HistoryStore
import com.crinfo.app.data.Route
import com.crinfo.app.data.SelectionStore
import com.crinfo.app.data.Station
import com.crinfo.app.data.StationRepository
import com.crinfo.app.data.blockingSnapshot
import com.crinfo.app.data.model.Seat
import com.crinfo.app.data.model.Trip
import com.crinfo.app.data.offline.OfflineDbRepository
import com.crinfo.app.data.offline.OfflineFallback
import com.crinfo.app.data.remote.LeftTicketApi
import com.crinfo.app.data.remote.WxxcxApi
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch

/** 18 点后默认选次日：当天多数车次已发车、余票也基本售罄。 */
internal fun defaultDepartureDate(today: LocalDate, hourOfDay: Int): LocalDate =
    if (hourOfDay >= 18) today.plusDays(1) else today

class StationToStationViewModel(app: Application) : AndroidViewModel(app) {
    private val historyStore = HistoryStore(app)
    private val selectionStore = SelectionStore(app)
    private val favorites = FavoritesStore(app)
    private val offlineDb = OfflineDbRepository(app)

    private val offlineState = mutableStateOf(false)

    /**
     * 离线查询态（由界面按「联网查询」开关同步写入）。
     * true 时不碰余票接口：直达车次、发到时刻、历时、区间里程全取离线库，
     * 而余票与票价是实时数据，离线压根没有 —— 席别与价格整列交给界面隐藏。
     * 状态化是为了让 [dates] 随模式切换即时重算，chip 条与月历跟着换窗。
     */
    var offline: Boolean
        get() = offlineState.value
        set(value) {
            if (offlineState.value == value) return
            offlineState.value = value
            // 切回联网的瞬间就要回落（首页不点查询也得让日期卡先跳回今天/明天），
            // 不能等 query() 才补这一刀
            if (!value) clampDateToOnline()
            // 进离线的同一瞬间摘掉「只看有票」：离线库没有票量，这个条件无从判断，
            // 留着会让筛选弹窗的开关与列表实际口径不一致（弹窗里它同时置灰）
            if (value && filter.onlyAvailable) filter = filter.copy(onlyAvailable = false)
        }

    /** 上次查的区间：首帧前同步取，避免先闪一帧默认的北京南→上海虹桥。 */
    private val lastRoute = blockingSnapshot { selectionStore.selectedSnapshot() }

    var from by mutableStateOf(
        lastRoute?.let { Station(it.fromName, it.fromCode, "", "", "") }
            ?: Station("北京南", "VNP", "beijingnan", "bjn", "北京"),
    )
        private set
    var to by mutableStateOf(
        lastRoute?.let { Station(it.toName, it.toCode, "", "", "") }
            ?: Station("上海虹桥", "AOH", "shanghaihongqiao", "shhq", "上海"),
    )
        private set
    var date by mutableStateOf(
        defaultDepartureDate(LocalDate.now(), LocalTime.now().hour).toString(),
    )
        private set
    var filter by mutableStateOf(TripFilter())
    var trips by mutableStateOf<List<Trip>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    /** 只有用户主动下拉才置真，用于区分「下拉刷新圆环」与「普通查询中」。 */
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** 模糊查询：开=保留 12306 自动扩出的同城车站区间；关=只看所选两站。 */
    var fuzzy by mutableStateOf(true)

    /**
     * 首帧快照超时（冷启动 DataStore 还没热）时挂起的异步回填标志：
     * 读到上次区间就补一次；用户一旦自己动过选择或点过查询立即作废，绝不盖掉操作。
     */
    private var restorePending = lastRoute == null

    var history by mutableStateOf<List<Route>>(emptyList())
        private set
    var favoritesRoutes by mutableStateOf<List<Route>>(emptyList())
        private set

    /**
     * 可选日期窗随模式换：联网=12306 预售期且过去不可查（今天起 15 天）；
     * 离线取图定时刻库、支持回溯，与车次/车站页同窗（-7～+15，用户定稿口径）。
     */
    val dates: List<LocalDate>
        get() = ((if (offline) -7 else 0)..LeftTicketApi.PRESALE_DAYS)
            .map { LocalDate.now().plusDays(it.toLong()) }

    val visibleTrips: List<Trip>
        get() {
            val scoped = if (fuzzy) trips else trips.filter { it.fromCode == from.code && it.toCode == to.code }
            return filter.apply(scoped)
        }

    /** 本次结果里真实出现的席别，筛选面板只列这些。 */
    val presentSeats: List<Seat> get() = Seat.entries.filter { seat -> trips.any { it.seat(seat) != null } }

    /** 模糊扩站后结果里真实出现的出发/到达站（电报码→站名），筛选面板只列这些、按短名靠前排。 */
    val presentFromStations: List<Pair<String, String>> get() = distinctStations { it.fromCode to it.fromName }

    val presentToStations: List<Pair<String, String>> get() = distinctStations { it.toCode to it.toName }

    private fun distinctStations(of: (Trip) -> Pair<String, String>): List<Pair<String, String>> =
        trips.map(of).distinctBy { it.first }
            .sortedWith(compareBy({ it.second.length }, { it.second }))

    init {
        viewModelScope.launch { historyStore.routes.collect { history = it } }
        viewModelScope.launch { favorites.routes.collect { favoritesRoutes = it } }
        if (restorePending) {
            viewModelScope.launch {
                selectionStore.selected.collect { route ->
                    if (!restorePending || route == null) return@collect
                    restorePending = false
                    from = Station(route.fromName, route.fromCode, "", "", "")
                    to = Station(route.toName, route.toCode, "", "", "")
                }
            }
        }
    }

    /** 用户动了选择：快照超时的异步回填立即作废，不能半路盖掉手挑的站。 */
    private fun suppressRestore() {
        restorePending = false
    }

    /* 换条件后是否重查由结果页自己决定：首页只记录选择，不做无谓请求。 */

    fun setDate(value: LocalDate) {
        if (date == value.toString()) return
        date = value.toString()
    }

    /** 联网态查不了过去的日子：选了历史日期一律拉回默认日（今天，18 点后明天）。 */
    private fun clampDateToOnline() {
        if (LocalDate.parse(date).isBefore(LocalDate.now())) {
            date = defaultDepartureDate(LocalDate.now(), LocalTime.now().hour).toString()
        }
    }

    fun selectFrom(station: Station) {
        suppressRestore()
        from = station
    }

    fun selectTo(station: Station) {
        suppressRestore()
        to = station
    }

    fun swap() {
        suppressRestore()
        val old = from
        from = to
        to = old
    }

    fun selectRoute(route: Route) {
        suppressRestore()
        from = Station(route.fromName, route.fromCode, "", "", "")
        to = Station(route.toName, route.toCode, "", "", "")
    }

    fun removeRoute(route: Route) {
        viewModelScope.launch { historyStore.remove(route) }
    }

    /** 当前所选区间，收藏状态与收藏动作都以它为准。 */
    private val currentRoute: Route
        get() = Route(from.code, from.name, to.code, to.name)

    val isFavorite: Boolean get() = favoritesRoutes.any { it.key == currentRoute.key }

    fun toggleFavorite() {
        viewModelScope.launch { favorites.toggleRoute(currentRoute) }
    }

    fun removeFavorite(route: Route) {
        viewModelScope.launch { favorites.toggleRoute(route) }
    }

    /** 刷新圆环对任意取数路径统一呈现（点查询、进详情、下拉都一样）。 */
    fun pullRefresh() {
        if (loading) return
        query()
    }

    /**
     * 离线直达。两站里有任何一站没被库收录都要**点名报出来**，
     * 让它看起来像「这两站之间没车」是最坏的一种沉默（项目站表有 440 个站库里没有）。
     */
    private suspend fun queryOffline(fromStation: Station, toStation: Station, day: String) {
        val engine = offlineDb.queryEngine(getApplication())
        if (engine == null) {
            trips = emptyList()
            error = "离线数据库未安装"
            return
        }
        val missing = listOf(fromStation.name, toStation.name).filterNot { engine.knowsStation(it) }
        if (missing.isNotEmpty()) {
            trips = emptyList()
            error = "离线库未收录 ${missing.joinToString("、")}"
            return
        }
        // 模糊 = 两侧按城市扩到主客站（与联网板面模式同一口径）；关掉时界面再按所选两站过滤
        val context = getApplication<Application>()
        val found = engine.tripsBetween(
            sameCityOrSelf(context, fromStation),
            sameCityOrSelf(context, toStation),
            day,
        )
        trips = found
        error = null
        val route = Route(fromStation.code, fromStation.name, toStation.code, toStation.name)
        historyStore.add(route)
        selectionStore.save(route)
    }

    /** 站所在城市的「名以城市开头」主客站清单；城市查不到或站表没这个站时退回它自己。 */
    private fun sameCityOrSelf(context: Application, station: Station): List<String> {
        val city = StationRepository.byCode(context, station.code)?.city.orEmpty()
        if (city.isBlank()) return listOf(station.name)
        val names = StationRepository.cityStations(context, city, 12).map { it.name }
        return if (station.name in names) names else names + station.name
    }

    fun query() {
        if (loading) return
        suppressRestore()
        val fromCode = from.code
        val toCode = to.code
        val fromStation = from
        val toStation = to
        // 兜底：跨午夜后「今天」已变昨天，赋值不走 setter 时也在这里拉回
        if (!offline) clampDateToOnline()
        val day = date
        if (fromCode == toCode) {
            error = "出发站与到达站相同"
            refreshing = false
            return
        }
        viewModelScope.launch {
            loading = true
            // 取数一律用下拉刷新的动画呈现（换区间、切日期、下拉都一样），不再打「查询中…」文字
            refreshing = true
            error = null
            try {
                if (offline) {
                    queryOffline(fromStation, toStation, day)
                    return@launch
                }
                val context = getApplication<Application>()
                val fetched = LeftTicketApi.query(fromCode, toCode, day) { code ->
                    StationRepository.byCode(context, code)?.name
                }
                trips = fetched
                loading = false
                refreshing = false
                historyStore.add(
                    Route(fromStation.code, fromStation.name, toStation.code, toStation.name),
                )
                selectionStore.save(
                    Route(fromStation.code, fromStation.name, toStation.code, toStation.name),
                )
                // 车型与里程要额外拉板面（模糊查询下最多 12 张、每张约 90KB），
                // 放在列表出结果之后回填，别让主列表等它
                val facts = runCatching { WxxcxApi.factsFor(fetched, day) }.getOrDefault(emptyMap())
                if (facts.isNotEmpty() && trips === fetched) {
                    trips = fetched.map { trip ->
                        val fact = facts[trip.trainNo]
                        if (fact == null) trip else trip.copy(
                            trainStyle = fact.style.ifBlank { trip.trainStyle },
                            distanceKm = if (fact.distanceKm > 0) fact.distanceKm else trip.distanceKm,
                        )
                    }
                }
                // 板面空窗与普速车型恒空的两类缺口，退离线库的车底型号与区间里程（与板面同源）
                val pending = trips
                if (pending.any { it.trainStyle.isBlank() || it.distanceKm <= 0 }) {
                    val reference = runCatching {
                        offlineDb.queryEngine(context)?.trips(fromStation.name, toStation.name, day)
                    }.getOrNull()
                    if (!reference.isNullOrEmpty() && trips === pending) {
                        trips = OfflineFallback.enrichTrips(pending, reference)
                    }
                }
            } catch (e: Exception) {
                trips = emptyList()
                error = e.message ?: "查询失败，请稍后重试"
            } finally {
                loading = false
                refreshing = false
            }
        }
    }
}
