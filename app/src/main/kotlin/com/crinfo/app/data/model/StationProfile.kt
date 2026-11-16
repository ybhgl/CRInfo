package com.crinfo.app.data.model

/** 车站信息弹窗的数据集合。每一项都可能是空的：接口挂了或该站没这条。 */
data class StationProfile(
    val stationName: String,
    val telecode: String,
    val city: String,
    /** 隶属路局（站归属），来自板面行的车站管辖单位码。 */
    val bureau: String,
    /** 离线库 sp.dat 表4 的车站归属单位名（形如「京局北京段」），联网态也带上。 */
    val stationSegment: String = "",
    /** 联网地址（含门牌）；离线态为 null。 */
    val address: StationAddress?,
    /** 离线库 sp.dat 表5/表6 的「省 市 区」，联网地址缺失时兜底。 */
    val offlineLocation: String?,
    val saleTimes: List<SaleStart>,
    val relays: List<RelayLeg>,
    val weather: StationWeather?,
    val intro: StationIntro?,
    val services: List<StationServiceGroup>,
    /**
     * 远程五项（起售/换乘/天气/简介/服务）是否已就位。
     * 弹窗是**点开就亮**的（先用本页已有字段渲染），false 时那五项写「加载中…」而不是「暂无」，
     * 免得远程回来后文案从「暂无」翻成数字，读起来像数据变了。
     */
    val remoteReady: Boolean = true,
) {
    /** 展示用的地址：联网地址最全（带门牌与坐标），缺了才退回离线库的省市区。 */
    val displayAddress: String
        get() = address?.address?.trim().orEmpty().ifBlank { offlineLocation?.trim().orEmpty() }

    /** 本站的起售时间。同城表里没自己的行时返回 null，界面按缺项处理。 */
    val ownSaleTime: String?
        get() = saleTimes.firstOrNull { it.name == stationName }?.time

    /**
     * 地图检索名与弹窗标题用的站名全名：优先接口给的「南宁东站」，接口缺失时拿本页站名补「站」字
     * （12306 的正式站名多数带「站」，`stations.json` 里不带，见项目记忆「站名一律以车站表为准」）。
     */
    val displayName: String
        get() {
            val raw = address?.name?.trim().orEmpty().ifBlank { stationName.trim() }
            return if (raw.isEmpty() || raw.endsWith("站")) raw else "${raw}站"
        }
}

/** 一座车站的起售时刻，`time` 形如 `08:00`；`code` 是电报码，起售接口没给且本地也查不到时空串。 */
data class SaleStart(val name: String, val time: String, val code: String = "")

/** 一条换乘预留：从查询站到 [toName] 至少要在站里留 [minutes] 分钟。 */
data class RelayLeg(val toName: String, val toTelecode: String, val minutes: Int, val sameStation: Boolean)

/**
 * 换乘分钟按档分组。同城换乘的预留值高度重复（北京各站几乎全是 180），
 * 逐站列十几行不如并成「180 分钟：北京南、北京西…」一行。
 * 组内保持接口返回顺序，档位从短到长。
 */
fun groupRelaysByMinutes(relays: List<RelayLeg>): List<Pair<Int, List<RelayLeg>>> =
    relays.filter { it.minutes > 0 }.groupBy { it.minutes }.entries.sortedBy { it.key }
        .map { entry -> entry.key to entry.value }

/** 车站天气，只收弹窗要显示的字段。 */
data class StationWeather(
    val city: String,
    val phenomena: String,
    val temperature: String,
    val humidity: String,
    val windDirection: String,
    val windPower: String,
    val aqi: String,
    /** 生活指数（穿衣/感冒/紫外线/洗车），接口原样给；弹窗按用户要求**不显示**，仅留作数据源。 */
    val indices: List<Pair<String, String>>,
    /** 今日预报温度区间，缺省时为空串。 */
    val todayRange: String,
    /** 今日白天/夜间天气现象（`forecast.weather[0].day/night`），拼「晴转多云」用。 */
    val dayPhenomena: String = "",
    val nightPhenomena: String = "",
    /** 今日预报高/低温（原始值，`todayRange` 由它们拼）。 */
    val highTemp: String = "",
    val lowTemp: String = "",
    val observedAt: String,
)

/**
 * 概况里的天气/气温写法（用户定稿）：「晴转多云 18-20℃」——白天与夜间现象不同就说「X转Y」，
 * 相同只写一个；温度恒为「高-低」，缺任一端就不画半截区间。拿不到预报时返回空串。
 */
fun StationWeather.dayNightSummary(): String {
    if (dayPhenomena.isBlank() && nightPhenomena.isBlank()) return ""
    val phenomena = if (dayPhenomena.isBlank() || nightPhenomena.isBlank() || dayPhenomena == nightPhenomena) {
        dayPhenomena.ifBlank { nightPhenomena }
    } else {
        "${dayPhenomena}转$nightPhenomena"
    }
    val range = if (highTemp.isNotBlank() && lowTemp.isNotBlank()) " $highTemp-$lowTemp℃" else ""
    return phenomena + range
}

/** 车站简介：接口按 title/pic/text 分段返回，图片是裸文件名。 */
data class StationIntro(val title: String, val paragraphs: List<String>, val pictureNames: List<String>) {
    val isBlank: Boolean get() = paragraphs.isEmpty() && pictureNames.isEmpty()
}

/** 站内服务的一个类别，如「进出站服务」下的一串项目。 */
data class StationServiceGroup(val title: String, val items: List<String>)

/**
 * 空类别整组不显示：接口固定回六组，其中「其他服务」常年是空列表，
 * 留标题挂「暂无」会把一屏空间花在没信息量的行上。
 */
fun dropEmptyServiceGroups(groups: List<StationServiceGroup>): List<StationServiceGroup> =
    groups.filter { it.items.isNotEmpty() }
