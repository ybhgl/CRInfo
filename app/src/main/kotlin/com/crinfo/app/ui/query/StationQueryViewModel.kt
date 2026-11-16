package com.crinfo.app.ui.query

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crinfo.app.data.Station
import com.crinfo.app.data.FavoritesStore
import com.crinfo.app.data.QueryMemoryStore
import com.crinfo.app.data.StationBoardRepository
import com.crinfo.app.data.StationHistoryStore
import com.crinfo.app.data.StationProfileRepository
import com.crinfo.app.data.StationRepository
import com.crinfo.app.data.blockingSnapshot
import com.crinfo.app.data.model.SCREEN_WINDOW_AFTER_MIN
import com.crinfo.app.data.model.SCREEN_WINDOW_BEFORE_MIN
import com.crinfo.app.data.model.BoardDirection
import com.crinfo.app.data.model.BoardEntry
import com.crinfo.app.data.model.StationPage
import com.crinfo.app.data.model.StationProfile
import com.crinfo.app.data.model.StationView
import com.crinfo.app.data.model.TrainKind
import com.crinfo.app.data.offline.OfflineDbRepository
import com.crinfo.app.data.offline.carTypeIndex
import com.crinfo.app.data.remote.LeftTicketApi
import java.time.LocalDate
import kotlinx.coroutines.launch

class StationQueryViewModel(app: Application) : AndroidViewModel(app) {
    private val historyStore = StationHistoryStore(app)
    private val memoryStore = QueryMemoryStore(app)
    private val favorites = FavoritesStore(app)
    private val offlineDb = OfflineDbRepository(app)

    /** 车次号 → 车底型号。只在要大屏时建一次，板面稀的时候用它兜底车型与徽标。 */
    private var carTypes: Map<String, String>? = null

    /**
     * 离线查询态（由界面按「联网查询」开关同步写入）。
     * true 时候选取自 `s*.dat`、停靠与时刻回 `t*.dat` 核对，实时状态与检票口一概没有。
     */
    var offline: Boolean = false

    /**
     * 离线态但库还没装（或被清掉）。界面据此把错误页的按钮从「重试」改成「安装数据库」——
     * 用状态判断而不是去匹配错误文案，文案改一个字就让字符串嗅探静默失效。
     */
    private var dbMissing by mutableStateOf(false)
    val needsOfflineDb: Boolean get() = offline && dbMissing

    var station by mutableStateOf(blockingSnapshot { memoryStore.lastStation() } ?: Station("北京南", "VNP", "beijingnan", "bjn", "北京"))
        private set
    var date by mutableStateOf(LocalDate.now().toString())
        private set
    var view by mutableStateOf(StationView.Trains)
        private set
    var direction by mutableStateOf(BoardDirection.Departure)
        private set
    var page by mutableStateOf<StationPage?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** 筛选：车次类型 + 关键词（车次号或通到站名）。 */
    var kinds by mutableStateOf<Set<TrainKind>>(emptySet())
    var keyword by mutableStateOf("")

    var history by mutableStateOf<List<Station>>(emptyList())
        private set
    var favoriteStations by mutableStateOf<List<Station>>(emptyList())
        private set

    val dates: List<LocalDate> = (-7..LeftTicketApi.PRESALE_DAYS).map { LocalDate.now().plusDays(it.toLong()) }

    /** 当前页面演的是哪一天的车：大屏恒为此刻（今天），车次页跟随所选日期。 */
    val queryDate: String get() = if (view == StationView.Screen) LocalDate.now().toString() else date

    val visibleEntries: List<BoardEntry>
        get() {
            val current = page ?: return emptyList()
            val q = keyword.trim()
            return current.entries(direction).filter { entry ->
                (kinds.isEmpty() || TrainKind.of(entry.trainCode) in kinds) &&
                    (q.isEmpty() ||
                        entry.trainCode.contains(q, ignoreCase = true) ||
                        entry.otherEnd.contains(q))
            }
        }

    val filterCount: Int get() = (if (kinds.isEmpty()) 0 else 1) + (if (keyword.isBlank()) 0 else 1)

    init {
        viewModelScope.launch { historyStore.stations.collect { history = it } }
        viewModelScope.launch { favorites.stations.collect { favoriteStations = it } }
    }

    /* 板面页与首页共用同一个 VM 类但实例按导航条目隔离：
       首页的 VM 永远没查过（page == null），换条件只记录；板面页才重查。 */

    fun selectStation(value: Station) {
        station = value
        // 首页选完站不查询，但那就是用户最后一次输入，得当场记住
        if (page != null) query() else viewModelScope.launch { memoryStore.saveStation(value) }
    }

    fun setDate(value: LocalDate) {
        if (date == value.toString()) return
        date = value.toString()
        // 大屏演的是此刻，日期只在车次页参与查询；上一轮报错（含缺库/未收录）也要能靠切日期自救
        if (view == StationView.Trains && (page != null || error != null)) query()
    }

    fun pickDirection(value: BoardDirection) {
        direction = value
    }

    /** 板面页内 Trains/Screen 切换；同页重复点击不重查。 */
    fun openView(value: StationView) {
        val already = page != null && view == value
        view = value
        if (!already) query()
    }

    fun clearFilter() {
        kinds = emptySet()
        keyword = ""
    }

    /** 板面条目首次出现：按入栈时带的站、视图与日期起查。 */
    fun startBoard(telecode: String, view: StationView, day: String) {
        StationRepository.byCode(getApplication(), telecode)?.let { station = it }
        this.view = view
        date = day
        query()
    }

    fun removeHistory(station: Station) {
        viewModelScope.launch { historyStore.remove(station) }
    }

    val isFavorite: Boolean get() = favoriteStations.any { it.code == station.code }

    fun toggleFavorite() {
        viewModelScope.launch { favorites.toggleStation(station) }
    }

    fun removeFavorite(station: Station) {
        viewModelScope.launch { favorites.toggleStation(station) }
    }

    fun pullRefresh() {
        if (loading) return
        // 下拉就是要新数据，弹窗那份 10 分钟缓存也得清，否则预热到的还是旧档案
        StationProfileRepository.clearCache()
        profileKey = null
        profile = null
        query()
    }

    /* ---------------- 车站信息弹窗 ---------------- */

    var profile by mutableStateOf<StationProfile?>(null)
        private set
    var loadingProfile by mutableStateOf(false)
        private set

    /** 已经为哪一站+哪一天取过档案，避免每次开弹窗都重打五路请求。 */
    private var profileKey: String? = null

    /**
     * 弹窗信息的取数口径。**大屏无视联网开关**（实时源只有在线，见 [query]），
     * 所以离线态的大屏页里，卡片信息也必须按联网获取，否则起售/换乘/简介整块被误藏。
     */
    val profileOffline: Boolean get() = offline && view != StationView.Screen

    /**
     * 打开弹窗就调，`query()` 成功后也会调一次（预热）。两段式：
     * ① 同步用本页已有字段建档案 → 弹窗当场亮，手指不空等；
     * ② 异步补齐远程五项（起售/换乘/天气/简介/服务）再替换。
     * 键不含日期（同站换日期不重打五路请求，起售/换乘/天气实测都与日期无关）。
     *
     * 两种模式都带离线库进去，但**逐项各找各的后路**：那一项接口给得出就用接口，
     * 给不出才用库（隶属段名、地址、同城站分组），库也没有就照旧显示「暂无…」空态。
     * 库里本来就没有的远程五项不受影响。
     */
    fun loadProfile() {
        val page = page ?: return
        val offline = profileOffline
        val key = "${page.telecode}@${if (offline) "offline" else "online"}"
        if (profileKey == key && profile?.remoteReady == true) return
        if (profileKey != key) {
            profileKey = key
            profile = StationProfileRepository.localProfile(getApplication(), page, offline)
        }
        if (offline) {
            // 离线态没有远程项可等，直接用离线库补地址/局段/同城分组后收工
            viewModelScope.launch {
                val full = StationProfileRepository.load(
                    context = getApplication(),
                    page = page,
                    date = queryDate,
                    offline = true,
                    db = offlineDb.current(),
                )
                if (profileKey == key) profile = full
            }
            return
        }
        if (loadingProfile) return
        loadingProfile = true
        viewModelScope.launch {
            try {
                val full = StationProfileRepository.load(
                    context = getApplication(),
                    page = page,
                    date = queryDate,
                    offline = false,
                    // 联网态也带上库：弹窗里每一项各找各的后路——接口给得出就用接口，
                    // 那一项空了才拿库补（隶属段名、地址、同城站），库也没有就照旧显示空态文案
                    db = offlineDb.current(),
                )
                // 期间用户可能已换站：只有还是那一站才覆盖
                if (profileKey == key) profile = full
            } finally {
                loadingProfile = false
            }
        }
    }

    /** 离线库没装就返回空表，两级板面兜底照旧；装了则一遍建表后复用，换站换日期不重建。 */
    private suspend fun offlineCarTypes(): Map<String, String> {
        carTypes?.let { return it }
        val built = offlineDb.current()?.rakes?.let { carTypeIndex(it) }.orEmpty()
        carTypes = built
        return built
    }

    fun query() {
        if (loading) return
        val telecode = station.code
        val day = date
        val target = view
        viewModelScope.launch {
            loading = true
            // 取数一律用下拉刷新的动画呈现（切日期、换站、下拉都一样），不再打「查询中/更新中」文字
            refreshing = true
            error = null
            try {
                val carTypes = offlineCarTypes()
                // 大屏演的是「此刻」，数据只有实时源有，离线库给不了 —— 所以它无视联网开关
                page = if (offline && target != StationView.Screen) {
                    offlinePage(station.name, telecode, station.city, day, target)
                } else if (target == StationView.Screen) {
                    StationBoardRepository.loadScreen(getApplication(), telecode, carTypes)
                } else {
                    StationBoardRepository.loadTrains(getApplication(), telecode, day, carTypes)
                }
                historyStore.add(station)
                memoryStore.saveStation(station)
                // 预热车站信息弹窗的五路远程数据：点开时通常已就位，第一次点也不空等
                loadProfile()
            } catch (e: Exception) {
                page = null
                error = e.message?.takeIf { it.isNotBlank() } ?: "车站数据获取失败"
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    /**
     * 离线板面。站没被库收录要**明确报出来**，不能回一张空表——那和「这天真的没车」是两回事。
     * 大屏视图在离线态退化成「图定 + 时间窗」，`fromLiveSource=false` 只在空态那一条文案里说话
     * （顶栏副标题与车站卡都不再复述，用户定稿：同一状态只说一处）。
     */
    private suspend fun offlinePage(
        name: String,
        telecode: String,
        city: String,
        day: String,
        target: StationView,
    ): StationPage {
        val engine = offlineDb.queryEngine(getApplication()) ?: run {
            dbMissing = true
            throw Exception("离线数据库未安装")
        }
        dbMissing = false
        if (!engine.knowsStation(name)) throw Exception("离线库未收录 $name")
        val rows = engine.board(name, day)
        val dep = rows.filter { it.direction == BoardDirection.Departure }
        val arr = rows.filter { it.direction == BoardDirection.Arrival }
        val (depOut, arrOut) = if (target == StationView.Screen) {
            val now = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            val window = (now - SCREEN_WINDOW_BEFORE_MIN)..(now + SCREEN_WINDOW_AFTER_MIN)
            dep.filter { it.sortMinutes in window } to arr.filter { it.sortMinutes in window }
        } else {
            dep to arr
        }
        return StationPage(
            view = target,
            stationName = name,
            telecode = telecode,
            city = city,
            // 地址与坐标只有在线接口有，离线给 null，界面按缺项不显示
            address = null,
            departures = depOut,
            arrivals = arrOut,
            fromLiveSource = false,
            updatedAt = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
        )
    }
}
