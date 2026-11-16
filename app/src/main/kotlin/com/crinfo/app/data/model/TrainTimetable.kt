package com.crinfo.app.data.model

/** 车次详情里的一站。数据来自 wxxcx travelServiceQrcodeTrainInfo。 */
data class TrainStop(
    val name: String,
    val telecode: String,
    val order: Int,
    val arriveTime: String?,
    val departTime: String?,
    val dwellMinutes: Int,
    /** 距始发站的累计运行分钟（接口 runningTime），始发站为 0。 */
    val minutesFromStart: Int,
    val dayDiff: Int,
    val lat: Double?,
    val lon: Double?,
    val wicket: String?,
    val waitingRoom: String?,
    val exit: String?,
    /** 车站所属路局代码，与列车担当路局不是一回事。 */
    val stationBureauCode: String,
    /** 车站管辖段，形如 `P#沧州车务段`。 */
    val stationCorporation: String,
    val ticketDelay: String?,
    /**
     * 该站生效的车次号（接口 `stationTrainCode`，实测另有同值 `dispTrainCode`）。
     * 中途换号的车（温州→包头的 K1276/K1277）从这里分段：北京丰台起变成 K1277。
     * 离线态由 `xw.dat` 的按里程段表算出（无段表的车各站同值，等于不换号）。
     */
    val stationTrainCode: String = "",
)

/**
 * 首末站只保留有向时刻：始发站没有「到达」，终到站没有「出发」。
 * 12306 停站接口会把两端填成同一个时刻（实测 D8414：北海 19:23/19:23、柳州 22:13/22:13），
 * 原样用会让终到站在晚点时报出「图定发」、始发站报出「图定到」。
 * 离线库解析出来本来就是 null，这里把联网数据对齐到同一口径。
 */
fun List<TrainStop>.normalizeTerminalTimes(): List<TrainStop> {
    if (isEmpty()) return this
    val last = lastIndex
    return mapIndexed { index, stop ->
        stop.copy(
            arriveTime = if (index == 0) null else stop.arriveTime,
            departTime = if (index == last) null else stop.departTime,
        )
    }
}

/**
 * 从逐站车次号推换号点：与**前一站**号不同的站 = 新号自该站起生效（telecode → 新号）。
 * 拿整趟车逐站推而不是比查询号：一趟车可能换多次号，且始发站那行没有比较对象。
 * 离线库逐站号为空 → 返回空表，界面整行不出现。
 */
fun deriveTrainChanges(stops: List<TrainStop>): Map<String, String> {
    if (stops.size < 2) return emptyMap()
    return buildMap {
        for (i in 1 until stops.size) {
            val prev = stops[i - 1].stationTrainCode
            val cur = stops[i].stationTrainCode
            val telecode = stops[i].telecode
            if (prev.isNotBlank() && cur.isNotBlank() && prev != cur && telecode.isNotBlank()) {
                put(telecode, cur)
            }
        }
    }
}

/**
 * 一趟车的静态运行信息。注意接口的 `distance`（里程）实测为空串，
 * 所以纵向时间轴用 minutesFromStart 而不是里程来定位。
 */
data class TrainTimetable(
    val trainCode: String,
    val trainNo: String,
    val startDate: String,
    val fromTelecode: String,
    val toTelecode: String,
    val startTime: String?,
    val arriveTime: String?,
    val trainStyle: String,
    val jiaoluTrainStyle: String,
    val jiaoluDept: String,
    val jiaoluCorporation: String,
    val corporationCode: String,
    val statusText: String?,
    val warmTips: String?,
    val stops: List<TrainStop>,
) {
    /** 列车担当路局代码（H=上海局，P=北京局）。 */
    val operatingBureauCode: String get() = corporationCode.take(1)

    val stopCount: Int get() = stops.size
}

/**
 * 经由板面的一行：营业停站带到发时刻，线路所/通过站只有名字与里程。
 * 数据来自离线线路表，没装库时整块不显示。
 */
data class JourneyNode(
    val name: String,
    val km: Int,
    val line: String,
    val arriveTime: String?,
    val departTime: String?,
    /** 12306 电报码；库里没有对应节点时为空串。 */
    val telecode: String = "",
    /** 距始发日的跨天数（站粒度，与时刻表同源），右上角「+N」小字用。 */
    val arriveDayDiff: Int = 0,
    val departDayDiff: Int = 0,
    val isStop: Boolean,
)

data class StationAddress(
    val telecode: String,
    val name: String,
    val city: String,
    val address: String,
    val lat: Double?,
    val lon: Double?,
)

data class OperatingBureau(val code: String, val name: String)

/** 三个月内的开行计划。flag=1 表示当日开行。 */
data class RunningDays(
    val startDate: String,
    val bureau: OperatingBureau,
    val subBureauName: String,
    val days: List<Pair<String, Boolean>>,
)

/** 车辆的一项属性，形如「列车编组 = 16辆」。 */
data class CarFact(val name: String, val value: String)

/** 一张车厢图：`name` 是「01车 商务 17」这样的席别与定员，`url` 是接口给的裸文件名。 */
data class CarPicture(val name: String, val url: String)

/** 一种座席的介绍，`description` 来自 `商务座#说明文字` 的后半段。 */
data class CarSeat(val name: String, val description: String, val url: String)

/**
 * 车底详情，来自 `trainStyleBatch/getCarDetail`。这个接口的信封和 wxxcx 其他接口不同，
 * 且图片只给裸文件名（基址需另配），拿不到就整块为空，界面按缺数据处理。
 */
data class CarDetail(
    val typeName: String,
    val carCode: String,
    val trainStyle: String,
    val exteriorUrl: String,
    val facts: List<CarFact>,
    val coaches: List<CarPicture>,
    val seats: List<CarSeat>,
)

/**
 * 第三方交路服务补的底：12306 车底接口拿不到时，至少给出车底号。
 * 路路通图片不再挂在这里，而是随 `TrainDetail.lltskb` 一起返回，官方图存在时也能切换。
 */
data class EmuFallback(val emuNo: String)

/**
 * 路路通的一组车型图片。`exact` 为 true 表示命中了本次车底号对应的车型；
 * 为 false 表示车型页没有直接图片或只匹配到同系列，界面按「同系列车型」提示。
 */
data class LltskbCarImages(
    val exact: Boolean,
    val models: List<LltskbCarModel>,
) {
    /** 有图才显示「车辆图片」入口；路路通连同系车型都没命中时整栏不出现。 */
    val hasImages: Boolean get() = models.any { it.images.isNotEmpty() }
}

/** 路路通一个车型的显示名与全部图片；同系列多车型时由界面用 Spinner 切换。 */
data class LltskbCarModel(
    val name: String,
    val images: List<LltskbImage>,
)

/** 一张车型图片及其页面上方的说明（如「1号车(二等) 定员98人」），说明可能为空。 */
data class LltskbImage(
    val url: String,
    val caption: String? = null,
)
