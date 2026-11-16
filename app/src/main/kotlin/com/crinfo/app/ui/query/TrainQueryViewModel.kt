package com.crinfo.app.ui.query

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crinfo.app.data.FavoritesStore
import com.crinfo.app.data.QueryMemoryStore
import com.crinfo.app.data.TrainDetailRepository
import com.crinfo.app.data.TrainHistoryStore
import com.crinfo.app.data.model.JourneyNode
import com.crinfo.app.data.model.TrainDetail
import com.crinfo.app.data.model.TrainProgress
import com.crinfo.app.data.model.TrainStop
import com.crinfo.app.data.model.TrainTimetable
import com.crinfo.app.data.model.deriveTrainChanges
import com.crinfo.app.data.model.trainProgress
import com.crinfo.app.data.offline.JiaoluEndpoints
import com.crinfo.app.data.offline.JiaoluLegInfo
import com.crinfo.app.data.offline.JiaoluPosition
import com.crinfo.app.data.offline.OfflineDb
import com.crinfo.app.data.offline.OfflineDbRepository
import com.crinfo.app.data.offline.OfflineFallback
import com.crinfo.app.data.offline.OfflineJourney
import com.crinfo.app.data.offline.RouteGraph
import com.crinfo.app.data.offline.RouteStop
import com.crinfo.app.data.offline.TrainSuggestion
import com.crinfo.app.data.offline.TrainSuggestionIndex
import com.crinfo.app.data.offline.locateJiaolu
import com.crinfo.app.data.remote.LeftTicketApi
import com.crinfo.app.data.remote.WxxcxApi
import com.crinfo.app.data.parseTrainCode
import com.crinfo.app.data.blockingSnapshot
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrainQueryViewModel(app: Application) : AndroidViewModel(app) {
    private val historyStore = TrainHistoryStore(app)
    private val memoryStore = QueryMemoryStore(app)
    private val favorites = FavoritesStore(app)
    private val offlineDb = OfflineDbRepository(app)

    /**
     * 输入框里的原始文本，允许用户打到一半。默认值只在上次没查过时才露脸。
     * 历史回填也要大写：老版本存过小写车次号的话，首帧不该显示成小写。
     */
    var input by mutableStateOf(blockingSnapshot { memoryStore.lastTrainCode() }?.uppercase() ?: "G1")
        private set
    var date by mutableStateOf(LocalDate.now().toString())
        private set
    var detail by mutableStateOf<TrainDetail?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /**
     * 目标日按图定不开行时的详情页提示。与 [error] 分开：这不是失败，
     * 时刻表照常展示，只在车次卡片上方加一张提醒卡。
     */
    var runNotice by mutableStateOf<RunNotice?>(null)
        private set
    var history by mutableStateOf<List<String>>(emptyList())
        private set
    var favoriteTrains by mutableStateOf<List<String>>(emptyList())
        private set

    /**
     * 输入联想候选，来自离线库的车次主键表。
     *
     * 只在用户手敲时更新：默认值回填、跳详情、换查都不带候选，避免一进页面就盖住日期卡。
     * 库没装就是空列表 —— 联想是离线库的附加能力，没库时保持原来的纯手输体验。
     */
    var suggestions by mutableStateOf<List<TrainSuggestion>>(emptyList())
        private set

    /** 当前这轮联想：取消旧的重算，防止连续输入时每一划都去扫全库。 */
    private var suggestJob: Job? = null

    /**
     * 离线查询态。由界面按「联网查询」开关同步写入（开关值存在设置里，不是本 VM 的状态）。
     * true 时一次在线请求都不发：时刻、里程、担当、交路全部取离线库。
     */
    var offline: Boolean = false

    /** 离线态但库没装/被清：错误页据此把「重试」换成「安装数据库」。 */
    private var dbMissing by mutableStateOf(false)
    val needsOfflineDb: Boolean get() = offline && dbMissing

    /** 逐站里程只有车站板面里有，且点开一站才取一站，键为电报码。 */
    var mileages by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** 离线库算出的径路：电报码 → 累计里程 + 到这一站走了哪些线。命中就不必再打板面。 */
    var offlineRoutes by mutableStateOf<Map<String, RouteStop>>(emptyMap())
        private set

    /** 离线库 T 记录的逐站里程（与板面 distance 同源）：板面空窗或离线态径路解不出时兜底。 */
    var offlineStopKm by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /**
     * 展开行里程的唯一读法：板面实值 > 官方径路/图搜索 > T 记录。
     * 板面返回 0（该站板面没这趟车）不当答案，继续往下兜；全兜不到才把 0 交给界面说「板面无此车次」。
     */
    fun mileageFor(telecode: String): Int? {
        val board = mileages[telecode]
        if (board != null && board > 0) return board
        return offlineRoutes[telecode]?.km ?: offlineStopKm[telecode] ?: board
    }

    /** 本车次在车底交路链里的位置；离线库没装、或该车不在任何链上时为空。 */
    var jiaolu by mutableStateOf<JiaoluPosition?>(null)
        private set

    /**
     * 本车的调向站名（extra.dat 表6）。库里没说就是空表 —— 空表不等于「这趟车不调向」。
     * 交路弹窗里只在 jlb 车型备注没写「调向」时显示这一行，避免同一件事说两处。
     */
    var jiaoluTurnStations by mutableStateOf<List<String>>(emptyList())
        private set

    /** 经由板面的节点序列（含线路所）；空表示离线库没装或该车接不上径路。 */
    var journey by mutableStateOf<List<JourneyNode>>(emptyList())
        private set

    /** 离线库里按查询日命中的图定餐车（如「9车[餐]」）；联网态车底接口没给餐车时也靠它兜底。 */
    var foodCoach by mutableStateOf<String?>(null)
        private set

    /** 今日列车的实时位置；非今天、未出发或数据缺失时为 null。 */
    var progress by mutableStateOf<TrainProgress?>(null)
        private set

    /**
     * 换号提示：换号站电报码 → 自该站起生效的新车次号，由时刻表逐站 `stationTrainCode` 推得。
     * 联网态号来自 12306 接口；离线态由 `xw.dat` 的按里程段表算出，两边同一条推导路径。
     */
    var changesAt by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** 链上各程的时刻，键是段在整条链里的下标；只在点开「车底交路」后才填。 */
    var jiaoluLegs by mutableStateOf<Map<Int, JiaoluLegInfo>>(emptyMap())
        private set

    /** 车次时刻表支持回溯（接口实测昨日/前日均有数据），所以日期条比车票查询多出过去 7 天。 */
    val dates: List<LocalDate> = (-7..LeftTicketApi.PRESALE_DAYS).map { LocalDate.now().plusDays(it.toLong()) }

    val queriedCode: String get() = detail?.trainCode ?: parseTrainCode(input) ?: input.trim().uppercase()

    init {
        viewModelScope.launch { historyStore.codes.collect { history = it } }
        viewModelScope.launch { favorites.trains.collect { favoriteTrains = it } }
    }

    fun updateInput(value: String) {
        // 车次号统一大写：KeyboardCapitalization.Characters 只是给软键盘的建议，
        // 粘贴小写、硬键盘与部分输入法都不生效。放在 VM 这一层，键盘输入、粘贴、
        // 联想选中（openFromCode 也走这里）全部走同一条路，查询与历史保存拿到的都是大写。
        input = value.uppercase()
        error = null
        refreshSuggestions()
    }

    /**
     * 重算输入联想。只在聚焦时算：没聚焦就没有可见的弹层，不该为它读盘建池。
     *
     * 两条路：
     * - 池子已就绪（应用启动预热过）→ **当场同步算**。真库实测单遍匹配 3ms，
     *   而「发协程 + 等 120ms 防抖」是白等一拍，首键那下可感延迟正是这么攒出来的；
     *   只认 [TrainSuggestionIndex.peek] 不认 of——没就绪时宁可走后台，也别在主线程撞一两百毫秒的建池排序。
     * - 还没就绪 → 停手 120ms 再在 Default 线程算：连续输入时每一划都触发一遍万把条匹配是白工，
     *   而 120ms 比人打完一个字符的间隔短，看不出延迟。
     */
    private fun refreshSuggestions() {
        suggestJob?.cancel()
        val query = input
        if (!fieldFocused || query.isBlank()) {
            suggestions = emptyList()
            return
        }
        val ready = offlineDb.trainCodesMemo?.first?.let(TrainSuggestionIndex::peek)
        if (ready != null) {
            suggestions = ready.suggest(query)
            return
        }
        suggestJob = viewModelScope.launch {
            // 只有「已经有候选在显示」时才防抖：那是在连续输入里避免反复重排。
            // 第一次出结果别再多等这一拍——池子没就绪本来就是最慢的那一次。
            if (suggestions.isNotEmpty()) delay(SUGGEST_DEBOUNCE_MS)
            suggestions = withContext(Dispatchers.Default) {
                // 只取车次表（t.i，127KB）：为猜一串号去解 2.2MB 全库，首键那下等待就是这么来的
                val (version, codes) = offlineDb.trainCodesSnapshot() ?: return@withContext emptyList()
                TrainSuggestionIndex.of(version, codes).suggest(query)
            }
        }
    }

    /**
     * 输入框焦点变化。失焦清候选并停掉在算的协程；重新聚焦把当前输入再算一遍，
     * 于是「只点一下输入框」也能看到候选，不必非要改一个字符。
     */
    fun onFieldFocused(focused: Boolean) {
        fieldFocused = focused
        if (focused) refreshSuggestions() else closeSuggestions()
    }

    /** 输入框当前是否聚焦；只在聚焦时算候选。 */
    private var fieldFocused: Boolean = false

    /**
     * 提前读好车次表并建池（进页面调一次，池子按库版本常驻）。
     *
     * 不预热的后果就是第一个字符要现读 `t.i` 现排序，用户看到的正是那一下停顿。
     */
    fun preloadSuggestions() {
        viewModelScope.launch(Dispatchers.Default) {
            val (version, codes) = offlineDb.trainCodesSnapshot() ?: return@launch
            TrainSuggestionIndex.of(version, codes)
        }
    }

    /** 收起联想：提交、点候选、换查都走这里，别让人离开输入框后还被候选盖住日期卡。 */
    fun closeSuggestions() {
        suggestJob?.cancel()
        suggestJob = null
        suggestions = emptyList()
    }

    fun setDate(value: LocalDate) {
        if (date == value.toString()) return
        date = value.toString()
        // 上一轮是报错（尤其「该日不开行」这类）时 detail 也是 null：不重查就会把人钉在错误页，
        // 切回运行期内明明有车却看不到
        if (detail != null || error != null) query()
    }

    /** 首页查询入口（按钮、收藏、历史）：校验通过返回 (车次号, 日期)，由页面据此压入详情条目。 */
    fun prepareQuery(): Pair<String, String>? {
        val code = parseTrainCode(input)
        if (code == null) {
            error = if (input.isBlank()) "请输入车次号" else "车次号格式不正确"
            return null
        }
        // 提交即记住：详情页查不到数据时，下次回来仍是这个车次号
        closeSuggestions()
        viewModelScope.launch { memoryStore.saveTrainCode(code) }
        return code to date
    }

    /** 详情条目首次出现时起查。 */
    fun startQuery(code: String, day: String) {
        input = code
        date = day
        closeSuggestions()
        query()
    }

    fun removeHistory(code: String) {
        viewModelScope.launch { historyStore.remove(code) }
    }

    /** 收藏以详情页那趟车为准，输入框里改到一半的文本不算。 */
    val isFavorite: Boolean get() = detail?.trainCode?.let { it in favoriteTrains } ?: (parseTrainCode(input) in favoriteTrains)

    fun toggleFavorite() {
        val code = detail?.trainCode ?: parseTrainCode(input) ?: return
        viewModelScope.launch { favorites.toggleTrain(code) }
    }

    fun removeFavorite(code: String) {
        viewModelScope.launch { favorites.toggleTrain(code) }
    }

    fun pullRefresh() {
        if (loading) return
        query()
    }

    fun query() {
        if (loading) return
        val code = parseTrainCode(input)
        if (code == null) {
            error = "车次号格式不正确"
            refreshing = false
            return
        }
        val day = date
        viewModelScope.launch {
            loading = true
            // 取数一律用下拉刷新的动画呈现（切日期、重查、下拉都一样），不再打「查询中/更新中」文字
            refreshing = true
            error = null
            runNotice = null
            mileages = emptyMap()
            offlineRoutes = emptyMap()
            offlineStopKm = emptyMap()
            journey = emptyList()
            progress = null
            jiaolu = null
            jiaoluLegs = emptyMap()
            jiaoluTurnStations = emptyList()
            foodCoach = null
            changesAt = emptyMap()
            try {
                if (offline) {
                    loadOffline(code, day)
                } else {
                    detail = TrainDetailRepository.load(getApplication(), code, day)
                    historyStore.add(code)
                    memoryStore.saveTrainCode(code)
                    applyOfflineRoute()
                    loadTrainChanges()
                    refreshProgress()
                }
            } catch (e: Exception) {
                detail = null
                error = e.message?.takeIf { it.isNotBlank() } ?: "$code 当日无运行信息"
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    /**
     * 离线取一趟车。三种「查不到」分开说，别拿空白页糊弄用户：
     * 库没装、库里没收这趟车、库里收了但这天不开行（后者还要报出图定起止日）。
     */
    private suspend fun loadOffline(code: String, day: String) {
        val engine = offlineDb.queryEngine(getApplication())
        if (engine == null) {
            dbMissing = true
            detail = null
            error = "离线数据库未安装"
            return
        }
        dbMissing = false
        val out = engine.train(code, day)
        if (out == null) {
            detail = null
            error = "离线库未收录 $code"
            return
        }
        // 目标日不开行也照常展示库里这趟车的完整时刻，只在卡片上方挂提示：
        // 「查不到」和「这天不开」是两件事，前者留错误页，后者不该把数据藏起来。
        runNotice = if (out.status.isRunning) null else RunNotice(
            trainCode = out.detail.trainCode,
            date = day,
            nextRunningDate = out.nextRunningDate,
        )
        detail = out.detail
        historyStore.add(code)
        memoryStore.saveTrainCode(code)
        // 经由、里程与餐车统一由 applyOfflineRoute 从库里补；时刻表里的逐站里程引擎直接给出
        applyOfflineRoute()
        // 换号提示走同一条推导（逐站号已在引擎里按 xw 段表算好），照旧不发请求
        loadTrainChanges()
    }

    /** 取某一站的累计里程；离线库已命中或已有结果就不再打板面。 */
    fun loadMileage(telecode: String) {
        if (offline) return
        val current = detail ?: return
        if (mileages.containsKey(telecode) || offlineRoutes.containsKey(telecode) || offlineStopKm.containsKey(telecode)) return
        viewModelScope.launch {
            val km = TrainDetailRepository.stopMileage(telecode, current.timetable.trainNo, date)
            mileages = mileages + (telecode to km)
        }
    }

    /**
     * 用离线库把整趟车的经由与里程补上。
     * 优先查库里的**每车官方径路**（竞品显示的就是这一份，键就是接口的 trainNo）；
     * 查不到才回落图搜索拼径路 —— 图搜索按「换线最少 + 里程」猜线路，任一站接不上就整体放弃，
     * 绝不给半截错误里程。
     */
    private suspend fun applyOfflineRoute() {
        val current = detail ?: return
        val db = offlineDb.current() ?: return
        val stops = current.timetable.stops
        // T 记录逐站里程先挂上：官方径路与图搜索都解不出时（离线态尤甚），展开行还有这层兜底
        val record = db.trainIdOf(current.trainCode)?.let { offlineDb.timetable(it) }
        offlineStopKm = OfflineFallback.stopKm(db, record, current.timetable)
        // 联网态有 12 位 trainNo 就直查；离线态（和 trainNo 对不上时）按显示车次号锚定。
        // 这一跳不补，离线经由就只剩图搜索兜底，接不上线的车（D195）整个经由消失。
        val officialRoute = db.trainRoutes[current.timetable.trainNo]
            ?: db.findTrainRouteByDisplayCode(
                current.trainCode,
                stops.firstOrNull()?.name.orEmpty(),
                stops.lastOrNull()?.name.orEmpty(),
            )
        val official = officialRoute?.let { OfflineJourney.build(it, db.linesById, stops) }
        if (official != null) {
            journey = official.nodes
            offlineRoutes = official.mileages.mapValues { RouteStop(it.value.km, it.value.lines) }
            jiaolu = locateJiaolu(db.rakes, current.trainCode)
            jiaoluTurnStations = db.trainIdOf(current.trainCode)?.let { db.turnStationNames(it) }.orEmpty()
            foodCoach = db.foodCoachFor(current.trainCode, ymd())
            return
        }
        val telecodes = stops.map { it.telecode }
        val route = RouteGraph(db.lines).route(telecodes)
        offlineRoutes = route?.let { telecodes.zip(it).toMap() }.orEmpty()
        journey = computeJourney(db, stops)
        jiaolu = locateJiaolu(db.rakes, current.trainCode)
        jiaoluTurnStations = db.trainIdOf(current.trainCode)?.let { db.turnStationNames(it) }.orEmpty()
        foodCoach = db.foodCoachFor(current.trainCode, ymd())
    }

    /** 查询日折成库里用的 `yyyyMMdd` 整数。 */
    private fun ymd(): Int = date.replace("-", "").toIntOrNull() ?: 0

    /**
     * 换号提示：**零额外请求**——12306 时刻表每站本就带 `stationTrainCode`（K1276/K1277 实测
     * 北京丰台起变号），从已取回的 stops 逐站比对推换号点。
     * 离线态同样零请求：逐站号是 `xw.dat` 段表 + 库里逐站里程算出来的，不碰网络。
     * 两个来源都没有段表的车 → 各站同值 → 空表 → 界面那一行不出现。
     */
    private fun loadTrainChanges() {
        val current = detail ?: return
        changesAt = deriveTrainChanges(current.timetable.stops)
    }

    /**
     * 补链上各程的时刻：库里只有顺序，只能按车次号现查时刻表。
     * 只在用户点开「车底交路」时打，已取过的段不重复请求。
     */
    fun loadJiaoluTimes() {
        val position = jiaolu ?: return
        val day = date
        position.window.forEachIndexed { offset, segment ->
            val index = position.windowStart + offset
            if (jiaoluLegs.containsKey(index)) return@forEachIndexed
            // 一段 = 一趟车（中途改号写成 `G3741/G3744`），所以只按主号查一次、只出一行；
            // 拆成几程各查各的，界面就得把同一趟车画成好几行
            val code = segment.queryCode
            if (code.isEmpty()) {
                jiaoluLegs = jiaoluLegs + (index to JiaoluLegInfo.Unavailable)
                return@forEachIndexed
            }
            jiaoluLegs = jiaoluLegs + (index to JiaoluLegInfo.Loading)
            viewModelScope.launch {
                // 离线态整条链都从库里取：交路本来就是离线库给的东西，没道理反过来打网络
                val engine = if (offline) offlineDb.queryEngine(getApplication()) else null
                val resolved = if (offline) {
                    engine?.train(code, day)?.let { endpointsOf(segment.display, it.detail) }
                } else {
                    runCatching { WxxcxApi.trainTimetable(code, day) }.getOrNull()
                        ?.let { info -> endpoints(segment.display, info) }
                        // 板面空窗/过运行图的车在线查不到时刻，退离线库的图定首末站（凌晨接续链全靠它）
                        ?: runCatching { offlineDb.queryEngine(getApplication())?.train(code, day) }
                            .getOrNull()
                            ?.let { endpointsOf(segment.display, it.detail) }
                }
                jiaoluLegs = jiaoluLegs + (
                    index to if (resolved == null) JiaoluLegInfo.Unavailable
                    else JiaoluLegInfo.Resolved(listOf(resolved))
                    )
            }
        }
    }

    /** 离线口径的接续时刻：取该车离线时刻表的首末站与到发时刻。 */
    private fun endpointsOf(code: String, detail: TrainDetail): JiaoluEndpoints? {
        val head = detail.stops.firstOrNull() ?: return null
        val tail = detail.stops.lastOrNull() ?: return null
        return JiaoluEndpoints(
            code = code,
            fromName = head.name,
            fromTime = head.departTime ?: head.arriveTime ?: "—",
            toName = tail.name,
            toTime = tail.arriveTime ?: tail.departTime ?: "—",
            toDayDiff = tail.dayDiff,
        )
    }

    private fun endpoints(code: String, info: TrainTimetable): JiaoluEndpoints? {
        val head = info.stops.firstOrNull() ?: return null
        val tail = info.stops.lastOrNull() ?: return null
        return JiaoluEndpoints(
            code = code,
            fromName = head.name,
            fromTime = head.departTime ?: head.arriveTime ?: "—",
            toName = tail.name,
            toTime = tail.arriveTime ?: tail.departTime ?: "—",
            toDayDiff = tail.dayDiff,
        )
    }

    /**
     * 经由板面的整条节点序列（含两站之间的线路所）。停站补上时刻表里的到发时刻，
     * 线路所不给时刻；里程用径路求解出的相对始发站累计值。
     */
    private suspend fun computeJourney(db: OfflineDb, stops: List<TrainStop>): List<JourneyNode> {
        val segments = RouteGraph(db.lines).fullRoute(stops.map { it.telecode }) ?: return emptyList()
        val byTelecode = stops.associateBy { it.telecode }
        val nodes = ArrayList<JourneyNode>()
        nodes.add(
            JourneyNode(
                name = stops.first().name,
                km = 0,
                line = "",
                arriveTime = null,
                departTime = stops.first().departTime,
                isStop = true,
            )
        )
        segments.forEachIndexed { i, seg ->
            seg.drop(1).forEach { node ->
                val stop = byTelecode[node.telecode]
                nodes.add(
                    JourneyNode(
                        name = node.name,
                        km = node.km,
                        line = node.line,
                        arriveTime = stop?.arriveTime,
                        // 终到站没有出发时刻，其余停站给出发
                        departTime = if (i == segments.lastIndex && node == seg.last()) null else stop?.departTime,
                        arriveDayDiff = stop?.dayDiff ?: 0,
                        departDayDiff = stop?.dayDiff ?: 0,
                        isStop = stop != null,
                    )
                )
            }
        }
        return nodes
    }

    /**
     * 今日列车的实时位置。只在看「今天」这一天才计算，历史/未来日期一律为空，
     * 由界面按分钟节拍重复调用刷新。
     */
    fun refreshProgress() {
        val current = detail ?: run { progress = null; return }
        if (date != LocalDate.now().toString()) {
            progress = null
            return
        }
        progress = trainProgress(current.rows, date)
    }

    private companion object {
        /** 联想防抖时长（毫秒）：够短到看不出等待，够长到连续输入只扫一次库。 */
        const val SUGGEST_DEBOUNCE_MS = 120L
    }
}

/**
 * 离线查到的车次在目标日按图定不开行时的提示事实。
 * 中文文案由界面拼（见 `RunNoticeCard`），数据层只给车次、查询日与下次开行日。
 */
data class RunNotice(
    val trainCode: String,
    /** 查询日，`yyyy-MM-dd`。 */
    val date: String,
    /** 图定下一个开行日，`yyyyMMdd`；null 表示已过运行期或 366 天内都扫不到。 */
    val nextRunningDate: Int?,
)
