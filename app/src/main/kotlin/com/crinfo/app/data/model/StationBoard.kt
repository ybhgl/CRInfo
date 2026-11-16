package com.crinfo.app.data.model

import com.crinfo.app.core.blankToNull

/** 大屏方向。一块板面只演一个方向，12306 官网也是如此拆分。 */
enum class BoardDirection(val label: String) {
    Departure("出发"),
    Arrival("到达"),
}

/** 实时大屏的候乘状态（携程/liechetime 同源），只有当日板面才有。 */
data class LiveBoard(
    val trainCode: String,
    val state: String,
    val time: String? = null,
    /** 出发屏 = 终到站，到达屏 = 始发站。 */
    val otherEnd: String? = null,
    val wicket: String? = null,
    val waitingRoom: String? = null,
    val exit: String? = null,
    val dayDiff: Int = 0,
) {
    val delayMinutes: Int get() = Regex("晚点?(\\d+)\\s*分").find(state)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    val isDelayed: Boolean get() = delayMinutes > 0

    /** 正在检票/正在候车这类「此刻正在办理」的状态，用强调色顶出来。 */
    val isHandling: Boolean get() = state.contains("检票") || state.contains("候车") || state.contains("延误")
}

/** 列表里的一行。同一趟经停车会同时出现在出发与到达两张屏上。 */
data class BoardEntry(
    val direction: BoardDirection,
    val trainCode: String,
    val trainNo: String,
    val time: String,
    val dayDiff: Int,
    /** 出发屏 = 终到站，到达屏 = 始发站。 */
    val otherEnd: String,
    /**
     * 板面票口码（在线源的 `platform_no`，实测各站存的其实是**检票口**编号）。
     * 只有它是 `gateText` 的合法来源；离线库那类图定静态值一律走 [referencePlatform]，不许混进来。
     */
    val platform: String?,
    /**
     * 参考站台：离线库 `plat.dat` 的 (车次,站)→股道（真库 96527 条）。
     *
     * 它是**图定静态值**，不是当日实时票口/站台，所以界面标签必须带「参考」二字
     * （用户定的口径），并且**绝不进 `gateText`** —— 把股道画在「检票口」那一行是把静态数据冒充实时数据。
     * 到达方向也可以显示：站台对上下车的旅客都有意义，而检票口只服务出发候车的人。
     */
    val referencePlatform: String? = null,
    val trainStyle: String,
    val className: String,
    val distanceKm: Int,
    val stationOrder: Int,
    val live: LiveBoard?,
) {
    val tags: List<TrainTag> get() = trainTags(trainStyle)

    /** 跨天车次要排在当日车次之后，所以排序键要把 dayDiff 折进去。 */
    val sortMinutes: Int get() = (timeToMinutes(time) + dayDiff * 1440).let { if (it < 0) Int.MAX_VALUE else it }

    val stateText: String? get() = live?.state?.takeIf { it.isNotBlank() }

    val isDelayed: Boolean get() = live?.isDelayed == true

    /**
     * 检票口。实时源的票口串更新且更全，优先用它；没有实时时退到板面票口码。
     * 板面那个字段名叫 `platform_no`，实测各站存的其实都是**检票口**编号（`11A11B#`、`10或A10#`），
     * 而且那是本站出发旅客要走的口，对到达屏的旅客没有意义 → 到达方向不取板面值。
     */
    val gateText: String?
        get() = normalizeGate(live?.wicket ?: platform.takeIf { direction == BoardDirection.Departure })

    /** 候车室只在实时源有。 */
    val waitingRoomText: String? get() = live?.waitingRoom?.takeIf { it.isNotBlank() }

    /** 出站口是「到达(北)1,到达(南)1」这种中文，不做抽码归一，原样给。 */
    val exitText: String? get() = live?.exit?.takeIf { it.isNotBlank() }
}

/** 票口码形状：可选字母 + 必选数字 + 可选字母（`11A`、`A10`、`7`）。 */
private val GateCodePattern = Regex("[A-Za-z]*\\d+[A-Za-z]*")

/**
 * 拆实时票口串。携程把「候车区/检票口」并成一段，liechetime 早给成两个字段，
 * 拆分口径按**第一个** `/`：实测六站 1480 行逐条与 liechetime 一致。
 * 没有 `/` 的整串当检票口（如广州南「三层候车室A1」），候车室留空。
 */
fun splitCheckingPort(raw: String?): Pair<String?, String?> {
    val text = blankToNull(raw) ?: return null to null
    val index = text.indexOf('/')
    if (index < 0) return null to text
    return blankToNull(text.take(index)) to blankToNull(text.substring(index + 1))
}

/**
 * 票口码归一。各站写法互不相同：`11A11B#`（连写）、`12A#12B#1A#`（# 分隔）、
 * `10或A10#10或A10#`（中文「或」且整串重复）、`22A、23A检票口,22B、23B检票口`（自带字面标签）。
 * 统一抽出票口码、保序去重再用「、」连接；一个都抽不到就原样返回，不猜。
 */
fun normalizeGate(raw: String?): String? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val codes = GateCodePattern.findAll(text)
        .map { it.value.uppercase() }
        .toCollection(LinkedHashSet())
    return if (codes.isEmpty()) text else codes.joinToString("、")
}

/** 一座车站某日（或某一时刻）的两张屏，出发与到达永远分开演。 */
data class StationBoard(
    val departures: List<BoardEntry>,
    val arrivals: List<BoardEntry>,
) {
    fun entries(direction: BoardDirection): List<BoardEntry> = when (direction) {
        BoardDirection.Departure -> departures
        BoardDirection.Arrival -> arrivals
    }

    val total: Int get() = departures.size + arrivals.size

    companion object {
        val EMPTY = StationBoard(emptyList(), emptyList())
    }
}

/**
 * 查询用的主车次号。离线库把「同一车底中途换号」存成一格（`G3741/G3744`、`D2147/D2150`），
 * 板面**照原样显示才是事实**；但点进去查必须给单个号 —— 12306 与我们的车次号解析都不认斜杠串，
 * 所以取前一段（担当这一程的号）。
 */
fun primaryTrainCode(raw: String): String = raw.substringBefore('/').trim()

/** 车次号归一：两源都可能出现 `G101`、`g101`、带空格或斜杠的写法。 */
fun normalizeTrainCode(raw: String): String = raw.trim().uppercase().filter { it.isLetterOrDigit() }

/**
 * 板面拆两张屏。规则：终到本站的只进到达屏、始发本站的只进出发屏、经停的两张都进
 * （经停车的 arrive_time 与 start_time 在接口里同时存在）。
 * 实时状态按车次号挂到对应方向的那一行上，挂不到就留空——大屏没有实时状态也照样能看。
 *
 * ⚠️ 板面行的跨天数**一律 0**：接口那两列 `day_diff` 是「距该车次始发日几天」，不是「距本站查询日几天」。
 * 实测隔日开的 K329 在 11-02 板面里（11-01 始发、+1 到站），11-03 板面就没它了（要 11-02 始发，那天不开）
 * ——**12306 已按到站日筛过开行**，板面给的就是「这天在站的车」。再把它折进排序键会把当天凌晨的车甩到队尾，
 * 还给它们挂上假的「+N天」（郑州 293 行里 127 行受影响）。
 */
fun buildBoard(
    trains: List<ScreenTrain>,
    telecode: String,
    departures: List<LiveBoard> = emptyList(),
    arrivals: List<LiveBoard> = emptyList(),
): StationBoard {
    val code = telecode.trim().uppercase()
    val liveDep = departures.associateBy { normalizeTrainCode(it.trainCode) }
    val liveArr = arrivals.associateBy { normalizeTrainCode(it.trainCode) }

    val dep = ArrayList<BoardEntry>()
    val arr = ArrayList<BoardEntry>()
    for (train in trains) {
        val origin = train.fromTelecode.trim().uppercase() == code
        val terminal = train.toTelecode.trim().uppercase() == code
        val departTime = train.departTime
        val arriveTime = train.arriveTime
        // 终到本站的车即使接口填了 start_time 也不算发车（多为终到后折返，另成另一趟车次）
        if (!terminal && departTime != null) {
            dep += train.entry(BoardDirection.Departure, departTime, liveDep)
        }
        if (!origin && arriveTime != null) {
            arr += train.entry(BoardDirection.Arrival, arriveTime, liveArr)
        }
    }
    return StationBoard(dep.sortedBy { it.sortMinutes }, arr.sortedBy { it.sortMinutes })
}

private fun ScreenTrain.entry(
    direction: BoardDirection,
    time: String,
    live: Map<String, LiveBoard>,
) = BoardEntry(
    direction = direction,
    trainCode = trainCode,
    trainNo = trainNo,
    time = time,
    dayDiff = 0,
    otherEnd = if (direction == BoardDirection.Departure) toStation else fromStation,
    platform = platform,
    trainStyle = trainStyle,
    className = className,
    distanceKm = distanceKm,
    stationOrder = stationOrder.toIntOrNull() ?: 0,
    live = live[normalizeTrainCode(trainCode)],
)

/** 大屏只演「正在办理与即将办理」的车：实测北京南 21:23 只剩 出发 12 / 到达 32 条，不是全天表。 */
const val SCREEN_WINDOW_BEFORE_MIN = 30
const val SCREEN_WINDOW_AFTER_MIN = 240

/** 跨天推导的门槛：时刻比此刻早出这么多才算「明天那张屏」。取值见 [stationDayDiff]。 */
const val NEXT_DAY_GRACE_MIN = 180

/**
 * 车站视角的跨天判定：时刻明显早于此刻的，是**明天那张屏**上的车。
 *
 * 实时屏只演「正在办理与之后」，所以比现在早的车只有两种来历，实测（16 站双向 1875 条）分得很开：
 * - 今天刚办完、仍留在屏上的：最多早 **53 分钟**（兰州 C681 到 14:06 而此刻 14:59，状态还是「正点」）；
 * - 明天凌晨那几条尾巴：最少也早 **609 分钟**（吐鲁番 Y934 到 04:50 出现在今天 14:59 的屏上）。
 * 门槛取中间 [NEXT_DAY_GRACE_MIN]，两边各有三倍余量。
 * ⚠️ 别退回用 [SCREEN_WINDOW_BEFORE_MIN]=30：30~53 分钟这段全是今天已到站的车，会被误标成明天。
 */
fun stationDayDiff(time: String?, nowMinutes: Int): Int {
    val minutes = time?.let(::timeToMinutes)?.takeIf { it >= 0 } ?: return 0
    return if (minutes + NEXT_DAY_GRACE_MIN < nowMinutes) 1 else 0
}

/**
 * 车站大屏。主源是携程——它给的就是站厅那块屏此刻在演什么，挂了才换 liechetime；
 * 每条再按车次号去全天板面取车型/担当/站台，取不到也照原样演（只是少了车型）。
 * 实时源整段挂掉时退回「全天板面 + 时间窗」，此时一条实时状态都没有，界面要改口。
 */
data class StationScreen(
    val departures: List<BoardEntry>,
    val arrivals: List<BoardEntry>,
    val fromLiveSource: Boolean,
) {
    fun entries(direction: BoardDirection): List<BoardEntry> = when (direction) {
        BoardDirection.Departure -> departures
        BoardDirection.Arrival -> arrivals
    }
}

fun buildScreen(
    liveDepartures: List<LiveBoard>,
    liveArrivals: List<LiveBoard>,
    board: StationBoard,
    nowMinutes: Int,
): StationScreen {
    val fromLive = liveDepartures.isNotEmpty() || liveArrivals.isNotEmpty()
    return StationScreen(
        departures = screenRows(BoardDirection.Departure, liveDepartures, board, nowMinutes, fromLive),
        arrivals = screenRows(BoardDirection.Arrival, liveArrivals, board, nowMinutes, fromLive),
        fromLiveSource = fromLive,
    )
}

private fun screenRows(
    direction: BoardDirection,
    live: List<LiveBoard>,
    board: StationBoard,
    nowMinutes: Int,
    fromLive: Boolean,
): List<BoardEntry> {
    val all = board.entries(direction)
    if (!fromLive || live.isEmpty()) {
        val window = (nowMinutes - SCREEN_WINDOW_BEFORE_MIN)..(nowMinutes + SCREEN_WINDOW_AFTER_MIN)
        return all.filter { it.sortMinutes in window }
    }
    val byCode = all.associateBy { normalizeTrainCode(it.trainCode) }
    return live.mapNotNull { item ->
        val base = byCode[normalizeTrainCode(item.trainCode)]
        val time = item.time ?: base?.time ?: return@mapNotNull null
        BoardEntry(
            direction = direction,
            trainCode = item.trainCode.trim(),
            trainNo = base?.trainNo.orEmpty(),
            time = time,
            // 跨天只认实时源：板面那个值是「距列车始发日」，拿来当车站日偏移会把今天的凌晨车甩到队尾
            dayDiff = item.dayDiff,
            otherEnd = item.otherEnd ?: base?.otherEnd.orEmpty(),
            platform = base?.platform,
            trainStyle = base?.trainStyle.orEmpty(),
            className = base?.className.orEmpty(),
            distanceKm = base?.distanceKm ?: 0,
            stationOrder = base?.stationOrder ?: 0,
            live = item,
        )
    }.sortedBy { it.sortMinutes }
}

/**
 * 车型为空的行按车次号补离线车底型号。徽标与型号串都从 `trainStyle` 派生，
 * 补上型号，「复」「智」徽标也会一起回来；补不到就保持空白，不编型号。
 */
fun List<BoardEntry>.filledByCarTypes(carTypeOf: (String) -> String?): List<BoardEntry> =
    map { if (it.trainStyle.isNotBlank()) it else it.copy(trainStyle = carTypeOf(it.trainCode).orEmpty()) }

fun StationScreen.filledByCarTypes(carTypeOf: (String) -> String?): StationScreen = copy(
    departures = departures.filledByCarTypes(carTypeOf),
    arrivals = arrivals.filledByCarTypes(carTypeOf),
)

/**
 * 拿另一张板面（实测是明日图）按车次号补空着的车型与检票口。
 * 明日图只当补充表用，绝不参与拆屏合并——两天的车混进一张列表会把时刻演错。
 */
fun StationScreen.filledFrom(supplement: Map<String, BoardEntry>): StationScreen = copy(
    departures = departures.map { it.filledWith(supplement) },
    arrivals = arrivals.map { it.filledWith(supplement) },
)

/** 邻日板面按车次号建的补充表：同一车次号可能在出发/到达各留一行，取哪行的车型与票口都一样。 */
fun StationBoard.supplementByCode(): Map<String, BoardEntry> =
    (departures + arrivals).associateBy { normalizeTrainCode(it.trainCode) }

private fun BoardEntry.filledWith(supplement: Map<String, BoardEntry>): BoardEntry {
    val from = supplement[normalizeTrainCode(trainCode)] ?: return this
    if (trainStyle.isNotBlank() && platform != null) return this
    return copy(
        trainStyle = trainStyle.ifBlank { from.trainStyle },
        platform = platform ?: from.platform,
    )
}

/** 车站查询的两种演法：目标日期的全天车次，与此刻正在演的大屏。 */enum class StationView(val label: String) {
    Trains("车次"),
    Screen("大屏"),
}

/** 两类页面共用的数据结构，出发与到达始终分开。 */
data class StationPage(
    val view: StationView,
    val stationName: String,
    val telecode: String,
    val city: String,
    val address: StationAddress?,
    /** 车站隶属路局（板面行的车站管辖单位码推的局名）；离线板面没这一列，恒为空串。 */
    val stationBureau: String = "",
    val departures: List<BoardEntry>,
    val arrivals: List<BoardEntry>,
    /** 大屏内容是否真来自实时源；false 表示实时源不可用、已退化成时间窗内的图定板面。 */
    val fromLiveSource: Boolean,
    val updatedAt: String,
) {
    fun entries(direction: BoardDirection): List<BoardEntry> = when (direction) {
        BoardDirection.Departure -> departures
        BoardDirection.Arrival -> arrivals
    }

    val summary: String
        get() = when (view) {
            StationView.Trains -> "全天 出发 ${departures.size} · 到达 ${arrivals.size}"
            // 实时源挂掉时演的是「图定 + 时间窗」的兜底行，但副标题不再另说一套（用户定稿：
            // 三处说同一件事太啰嗦），口径由 [StationPage.fromLiveSource] 留给空态那一条去说
            StationView.Screen -> "实时大屏 出发 ${departures.size} · 到达 ${arrivals.size}"
        }
}
