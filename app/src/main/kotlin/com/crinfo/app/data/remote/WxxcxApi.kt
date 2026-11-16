package com.crinfo.app.data.remote

import com.crinfo.app.core.net.ApiException
import com.crinfo.app.core.net.Http
import com.crinfo.app.core.blankToNull
import com.crinfo.app.core.normalizeTime
import com.crinfo.app.data.model.CarDetail
import com.crinfo.app.data.model.CarFact
import com.crinfo.app.data.model.CarPicture
import com.crinfo.app.data.model.CarSeat
import com.crinfo.app.data.model.OperatingBureau
import com.crinfo.app.data.model.RelayLeg
import com.crinfo.app.data.model.RunningDays
import com.crinfo.app.data.model.SaleStart
import com.crinfo.app.data.model.ScreenTrain
import com.crinfo.app.data.model.StationAddress
import com.crinfo.app.data.model.StationIntro
import com.crinfo.app.data.model.StationServiceGroup
import com.crinfo.app.data.model.StationWeather
import com.crinfo.app.data.model.TrainFacts
import com.crinfo.app.data.model.TrainStop
import com.crinfo.app.data.model.TrainTimetable
import com.crinfo.app.data.model.Trip
import com.crinfo.app.data.model.dropEmptyServiceGroups
import com.crinfo.app.data.model.normalizeTerminalTimes
import com.crinfo.app.data.model.timeToMinutes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val WX_BASE = "https://mobile.12306.cn/wxxcx/wechat/"
private const val REFERER = "https://mobile.12306.cn/"

/** 车底详情不在 `wechat/` 命名空间下，实测拼成 `wechat/openplatform-inner/…` 会 403。 */
private const val CAR_DETAIL_URL =
    "https://mobile.12306.cn/wxxcx/openplatform-inner/miniprogram/wifiapps/appFrontEnd/v2/lounge/" +
        "open-smooth-common/trainStyleBatch/getCarDetail"

/** 车站简介/设施这一族接口的前缀。微信版 weixin/wxcore 命名空间实测 403，只有这个命名空间可用。 */
private const val LOUNGE_BASE =
    "https://mobile.12306.cn/wxxcx/openplatform-inner/miniprogram/wifiapps/appFrontEnd/v2/lounge/" +
        "open-smooth-common/"

/** 起售时间接口挂在小程序页子上，日期是 `HH:mm` 文本，信封也和 wxxcx 不一样。 */
private const val QSSJ_URL = "https://mobile.12306.cn/weixin/wxcore/queryQssj"
private const val QSSJ_REFERER = "https://mobile.12306.cn/weixin/wxcore/qssj"

@Serializable
private data class WxcEnvelope<T>(
    val data: T? = null,
    val status: Boolean = false,
    val errorMsg: String? = null,
)

@Serializable
private data class TravelStopDto(
    val stationName: String = "",
    val stationTelecode: String = "",
    val stationNo: String = "",
    val arriveTime: String = "",
    val startTime: String = "",
    val stopover_time: String = "",
    val runningTime: String = "",
    val dayDifference: String = "",
    val lat: String = "",
    val lon: String = "",
    val wicket: String = "",
    val waitingRoom: String = "",
    val exit: String = "",
    val bureau_code: String = "",
    val station_corporation_code: String = "",
    val ticketDelay: String = "",
    val train_style: String = "",
    val jiaolu_train_style: String = "",
    val jiaolu_dept_train: String = "",
    val jiaolu_corporation_code: String = "",
    val corporation_code: String = "",
    /** 换号车逐站生效的车次号（K1276/K1277 实测丰台起为 K1277），此前被整字段丢弃。 */
    val stationTrainCode: String = "",
    val dispTrainCode: String = "",
)

@Serializable
private data class TravelDataDto(
    val trainNo: String = "",
    val startTime: String = "",
    val arriveTime: String = "",
    val startTrainDate: String = "",
    val start_station_telecode: String = "",
    val end_station_telecode: String = "",
    val trainStatus: TravelStatusDto? = null,
    val trainDetail: TravelDetailDto? = null,
)

@Serializable
private data class TravelStatusDto(
    val trainStatusTxt: String = "",
    val trainWarmTips: String = "",
)

@Serializable
private data class TravelDetailDto(
    val trainCode: String = "",
    val stopTime: List<TravelStopDto> = emptyList(),
)

@Serializable
private data class AddressDto(
    val stationCode: String = "",
    val stationName: String = "",
    val cityName: String = "",
    val stationAddress: String = "",
    val latitude: String = "",
    val longitute: String = "",
)

@Serializable
private data class BureauDto(
    val bureau_code: String = "",
    val bureau_code_name: String = "",
)

@Serializable
private data class DiagramDto(
    val startDate: String = "",
    val bureau_code: String = "",
    val bureau_code_name: String = "",
    val subbureau_code_name: String = "",
    val running_list: List<RunningDayDto> = emptyList(),
)

@Serializable
private data class RunningDayDto(val date: String = "", val flag: String = "")

@Serializable
private data class CarDetailEnvelope(val status: Int = -1, val content: CarDetailContent? = null)

@Serializable
private data class CarDetailContent(val status: Int = -1, val data: CarDetailDto? = null)

@Serializable
private data class CarDetailDto(
    val carType: String = "",
    val carCode: String = "",
    val trainStyle: String = "",
    val carPic: String = "",
    val carInfo: List<CarItemDto> = emptyList(),
    val coachPicList: List<CarPictureDto> = emptyList(),
    val coachDetailPicList: List<CarPictureDto> = emptyList(),
)

@Serializable
private data class CarItemDto(val pictureName: String = "", val pictureValue: String = "")

@Serializable
private data class CarPictureDto(val pictureName: String = "", val pictureUrl: String = "")

/* ---------------- 车站信息弹窗那一批接口 ---------------- */

/** openplatform-inner 这一族的信封：业务数据再套一层 `content`。 */
@Serializable
private data class LoungeEnvelope<T>(val httpCode: Int = -1, val content: LoungeContent<T>? = null)

@Serializable
private data class LoungeContent<T>(val status: Int = -1, val data: T? = null)

@Serializable
private data class WeatherEnvelope(val status: Boolean = false, val data: WeatherDto? = null)

@Serializable
private data class WeatherDto(
    val city: String = "",
    val observe: ObserveDto? = null,
    val air: AirDto? = null,
    val index: WeatherIndexDto? = null,
    val forecast: ForecastDto? = null,
)

@Serializable
private data class ObserveDto(
    val phenomena_name: String = "",
    val temp: String = "",
    val humidity: String = "",
    val wind_direct: String = "",
    val wind_power: String = "",
    val time: String = "",
)

@Serializable
private data class AirDto(val AQI: String = "")

/** 生活指数的键是固定字段名（不是 days 里那种动态键），值都是 `{name, level, explain}`。 */
@Serializable
private data class WeatherIndexDto(
    val dressing: IndexItemDto? = null,
    val cold: IndexItemDto? = null,
    val ultraviolet_rays: IndexItemDto? = null,
    val car_wash: IndexItemDto? = null,
)

@Serializable
private data class IndexItemDto(val name: String = "", val level: String = "")

@Serializable
private data class ForecastDto(val weather: List<ForecastDayDto> = emptyList())

@Serializable
private data class ForecastDayDto(
    val date: String = "",
    val high_temp: String = "",
    val low_temp: String = "",
    val day: String = "",
    val night: String = "",
)

@Serializable
private data class IntroItemDto(
    val introductionContent: String = "",
    val introductionType: String = "",
    val introductionOrder: Int = 0,
)

@Serializable
private data class EquipmentServiceDto(val serviceName: String = "")

@Serializable
private data class EquipmentGroupDto(val title: String = "", val services: List<EquipmentServiceDto> = emptyList())

@Serializable
private data class NameValueDto(val name: String = "", val value: String = "")

/** 起售接口只在最外层多一个 `httpstatus`，`data` 失败时是串不是数组。 */
@Serializable
private data class SaleEnvelope(val status: Boolean = false, val data: List<NameValueDto>? = null)

@Serializable
private data class RelayRowDto(
    val to_station_name: String = "",
    val to_station_telecode: String = "",
    val relay_time: String = "",
)

@Serializable
private data class ScreenRowDto(
    val station_train_code: String = "",
    val train_no: String = "",
    val start_station_name: String = "",
    val end_station_name: String = "",
    val start_station_telecode: String = "",
    val end_station_telecode: String = "",
    val arrive_time: String = "",
    val start_time: String = "",
    val arrive_day_diff: String = "",
    val start_day_diff: String = "",
    val running_time: String = "",
    val distance: String = "",
    val platform_no: String = "",
    val train_style: String = "",
    val train_class_name: String = "",
    val train_type_name: String = "",
    val bureau_code: String = "",
    val corporation_code: String = "",
    val station_corporation_code: String = "",
    val jiaolu_corporation_code: String = "",
    val jiaolu_dept_train: String = "",
    val jiaolu_train_style: String = "",
    val display_station_no: String = "",
)

/**
 * 12306 微信小程序同源接口：免登录、POST 表单，日期 `YYYYMMDD`、时刻 `HHmm`。
 * 停站接口 `ticketInfo/getStopStation` 实测稳定 403，停站一律走 travelServiceQrcodeTrainInfo。
 */
object WxxcxApi {
    // coerceInputValues：天气/起售这类接口失败时会把 data 换成一个错误对象或串，
    // 声明成可空就该归 null，而不是让解码器抛异常把整块信息一起带走
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** 大屏板面缓存（站+日期），避免同一次查询里反复打接口。 */
    private val boardCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<ScreenTrain>>>()
    private const val BOARD_TTL_MS = 10 * 60 * 1000L

    /** 一次查询最多拉多少座车站的板面，防止同城车站过多时打爆接口。 */
    private const val MAX_BOARD_STATIONS = 12

    /** 车次运行信息：逐站到发、坐标、车型、交路、担当、路局。 */
    suspend fun trainTimetable(trainCode: String, date: String): TrainTimetable {
        val ymd = date.replace("-", "")
        val body = Http.postForm(
            "${WX_BASE}main/travelServiceQrcodeTrainInfo",
            mapOf(
                "trainCode" to trainCode,
                "startDay" to ymd,
                "startTime" to "0000",
                "endDay" to ymd,
                "endTime" to "2359",
                "reqType" to "form",
            ),
            referer = REFERER,
        )
        val env = json.decodeFromString<WxcEnvelope<TravelDataDto>>(body)
        val data = env.data
        val detail = data?.trainDetail
        if (data == null || detail == null || detail.stopTime.isEmpty()) {
            throw ApiException(env.errorMsg?.takeIf { it.isNotBlank() } ?: "$trainCode 当日无运行信息")
        }
        // 车型与交路/担当挂在每个 stop 上且全车一致，取首站
        val head = detail.stopTime.first()
        return TrainTimetable(
            trainCode = detail.trainCode.ifBlank { trainCode },
            trainNo = data.trainNo,
            startDate = data.startTrainDate.ifBlank { ymd },
            fromTelecode = data.start_station_telecode.ifBlank { head.stationTelecode },
            toTelecode = data.end_station_telecode.ifBlank { detail.stopTime.last().stationTelecode },
            startTime = normalizeTime(data.startTime) ?: normalizeTime(head.startTime),
            arriveTime = normalizeTime(data.arriveTime) ?: normalizeTime(detail.stopTime.last().arriveTime),
            trainStyle = head.train_style,
            jiaoluTrainStyle = head.jiaolu_train_style,
            jiaoluDept = head.jiaolu_dept_train,
            jiaoluCorporation = head.jiaolu_corporation_code,
            corporationCode = head.corporation_code,
            statusText = blankToNull(data.trainStatus?.trainStatusTxt),
            warmTips = blankToNull(data.trainStatus?.trainWarmTips),
            // 接口在首末站把到发填成同一时刻，进模型前先去掉无向的那一半
            stops = detail.stopTime.map { it.toTrainStop() }.normalizeTerminalTimes(),
        )
    }

    /** 车站地址与坐标（GCJ-02）。 */
    suspend fun stationAddress(telecode: String): StationAddress {
        val body = Http.postForm(
            "${WX_BASE}bigScreen/getStationAddress",
            mapOf("stationCode" to telecode, "reqType" to "form"),
            referer = REFERER,
        )
        val dto = json.decodeFromString<WxcEnvelope<AddressDto>>(body).data
            ?: throw ApiException("无 $telecode 车站信息")
        return StationAddress(
            telecode = dto.stationCode,
            name = dto.stationName,
            city = dto.cityName,
            address = dto.stationAddress,
            lat = dto.latitude.toDoubleOrNull(),
            lon = dto.longitute.toDoubleOrNull(),
        )
    }

    /**
     * 车站天气（实况 + 空气质量 + 生活指数 + 预报）。
     * 参数名是 `station_code`（下划线），`type` 用竖线分隔的一串块名——抓包发什么就发什么。
     * 覆盖度看站：实测北京西有、北京南回「查询天气失败」，所以拿不到只当缺项，不报错给界面。
     */
    suspend fun stationWeather(telecode: String): StationWeather? {
        val body = Http.postForm(
            "${WX_BASE}weather/total",
            mapOf("station_code" to telecode, "isUseCache" to "true", "type" to "air|forecast|alarm|index|observe"),
            referer = REFERER,
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        )
        val data = json.decodeFromString<WeatherEnvelope>(body).data ?: return null
        val observe = data.observe
        val air = data.air
        val index = data.index
        val today = data.forecast?.weather?.firstOrNull()
        return StationWeather(
            city = data.city,
            phenomena = observe?.phenomena_name.orEmpty(),
            temperature = observe?.temp.orEmpty(),
            humidity = observe?.humidity.orEmpty(),
            windDirection = observe?.wind_direct.orEmpty(),
            windPower = observe?.wind_power.orEmpty(),
            aqi = air?.AQI.orEmpty(),
            indices = listOfNotNull(index?.dressing, index?.cold, index?.ultraviolet_rays, index?.car_wash)
                .filter { it.name.isNotBlank() }
                .map { it.name to it.level },
            todayRange = if (today == null || today.high_temp.isBlank() || today.low_temp.isBlank()) ""
            else "${today.low_temp}~${today.high_temp}℃",
            dayPhenomena = today?.day.orEmpty(),
            nightPhenomena = today?.night.orEmpty(),
            highTemp = today?.high_temp.orEmpty(),
            lowTemp = today?.low_temp.orEmpty(),
            observedAt = observe?.time.orEmpty(),
        ).takeIf {
            it.phenomena.isNotBlank() || it.temperature.isNotBlank() || it.indices.isNotEmpty()
        }
    }

    /**
     * 车站简介：分段返回 title/pic/text，`pic` 是裸文件名（基址与车底图同一个 CDN）。
     * 只有部分大站有内容，其余站实测给空列表，返回 null 交界面显示「暂无简介数据」。
     */
    suspend fun stationIntroduction(telecode: String): StationIntro? {
        val body = Http.get(
            "${LOUNGE_BASE}stationIntroduction/getStationIntroduction/$telecode?reqType=form",
            referer = REFERER,
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        )
        val items = json.decodeFromString<LoungeEnvelope<List<IntroItemDto>>>(body).content?.data.orEmpty()
        if (items.isEmpty()) return null
        val ordered = items.sortedBy { it.introductionOrder }
        return StationIntro(
            title = ordered.firstOrNull { it.introductionType == "title" }?.introductionContent.orEmpty(),
            paragraphs = ordered.filter { it.introductionType == "text" && it.introductionContent.isNotBlank() }
                .map { it.introductionContent },
            pictureNames = ordered.filter { it.introductionType == "pic" && it.introductionContent.isNotBlank() }
                .map { it.introductionContent },
        ).takeIf { !it.isBlank }
    }

    /** 站内服务：固定六类，空类别原样带出，由 [dropEmptyServiceGroups] 决定不显示。 */
    suspend fun stationServices(telecode: String): List<StationServiceGroup> {
        val body = Http.postForm(
            "${LOUNGE_BASE}stationEquipment/getStationEquipmentInfo",
            mapOf("stationTelecode" to telecode, "reqType" to "json"),
            referer = REFERER,
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        )
        val groups = json.decodeFromString<LoungeEnvelope<List<EquipmentGroupDto>>>(body).content?.data.orEmpty()
        return dropEmptyServiceGroups(
            groups.map { group ->
                StationServiceGroup(group.title, group.services.map { it.serviceName }.filter { it.isNotBlank() })
            },
        )
    }

    /**
     * 整城车站的起售时刻（`HH:mm`）。接口按**城市名**查、一次返回该市所有车站，
     * 所以同城站列表的起售时间不用逐站打请求。离线库没有这一族数据，只能联网取。
     * 失败时 `data` 不是数组而是串，靠 `coerceInputValues` 归成 null，别指望解码器抛错被兜住。
     */
    suspend fun saleStartTimes(cityStationName: String): List<SaleStart> {
        val url = "$QSSJ_URL?stationName=" +
            java.net.URLEncoder.encode(cityStationName, "UTF-8").replace("+", "%20")
        val body = Http.get(url, referer = QSSJ_REFERER)
        val rows = json.decodeFromString<SaleEnvelope>(body).data.orEmpty()
        return rows.mapNotNull { row ->
            val name = row.name.trim()
            val time = row.value.trim()
            if (name.isBlank() || time.isBlank()) null else SaleStart(name, time)
        }
    }

    /**
     * 本站到同城各站的换乘预留分钟。走 wxxcx 的 `main/getLCLimitWaitTime`（POST）：
     * 比官网 `index/otn/index12306/queryLCtime` 多给站名、且不要求登录态，同城一次返回。
     * `train_date` 影响的是按日调整，本版本返回与日期无关，仍按抓包带上。
     */
    suspend fun relayTimes(telecode: String, date: String): List<RelayLeg> {
        val body = Http.postForm(
            "${WX_BASE}main/getLCLimitWaitTime",
            mapOf("station_telecode" to telecode, "train_date" to date.replace("-", ""), "reqType" to "form"),
            referer = REFERER,
        )
        val rows = json.decodeFromString<WxcEnvelope<List<RelayRowDto>>>(body).data.orEmpty()
        return rows.mapNotNull { row ->
            val minutes = row.relay_time.trim().toIntOrNull() ?: return@mapNotNull null
            RelayLeg(
                toName = row.to_station_name,
                toTelecode = row.to_station_telecode,
                minutes = minutes,
                sameStation = row.to_station_telecode == telecode,
            )
        }
    }

    /** 当日列车担当路局。 */
    suspend fun operatingBureau(trainCode: String, date: String): OperatingBureau {
        val body = Http.postForm(
            "${WX_BASE}bigScreen/queryTrainBureau",
            mapOf("trainCode" to trainCode, "queryDate" to date.replace("-", ""), "reqType" to "form"),
            referer = REFERER,
        )
        val dto = json.decodeFromString<WxcEnvelope<BureauDto>>(body).data
        return OperatingBureau(dto?.bureau_code.orEmpty(), dto?.bureau_code_name.orEmpty())
    }

    /** 三个月内的开行计划。 */
    suspend fun runningDays(trainCode: String, date: String): RunningDays {
        val body = Http.postForm(
            "${WX_BASE}bigScreen/queryTrainDiagram",
            mapOf("trainCode" to trainCode, "queryDate" to date.replace("-", ""), "reqType" to "form"),
            referer = REFERER,
        )
        val dto = json.decodeFromString<WxcEnvelope<DiagramDto>>(body).data
            ?: throw ApiException("无 $trainCode 开行信息")
        return RunningDays(
            startDate = dto.startDate,
            bureau = OperatingBureau(dto.bureau_code, dto.bureau_code_name),
            subBureauName = dto.subbureau_code_name,
            days = dto.running_list.map { it.date to (it.flag == "1") },
        )
    }

    /**
     * 车底详情：车型车号、编组、定员、服务设施与图片。
     * 这个接口和 wxxcx 其他接口不一样——GET 带查询串，信封是 `{httpCode, content:{status, data}, status}` 这样的嵌套结构，
     * 且实测必须同时给 `carCode`（值就是车次号），少一个参数就返回业务态「系统错误」。
     * 图片只给裸文件名，基址不在响应里。
     */
    suspend fun carDetail(trainCode: String, date: String): CarDetail? {
        // 这个接口挂在 /wxxcx/openplatform-inner/… 而不是其他小程序接口的 /wxxcx/wechat/…，
        // 走 WX_BASE 会多拼一层 wechat/ 直接 403，所以写绝对路径
        val url = CAR_DETAIL_URL
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("trainCode", trainCode)
            .addQueryParameter("runningDay", date.replace("-", ""))
            .addQueryParameter("reqType", "form")
            .addQueryParameter("carCode", trainCode)
            .build()
            .toString()
        // 抓包里请求方发的就是这三条头、且不带 Referer，这里逐条对齐，
        // 免得被按 UA/Content-Type 分流成业务态「系统错误」却静默失败
        val body = Http.get(
            url,
            headers = mapOf(
                "Accept" to "*/*",
                "Content-Type" to "application/x-www-form-urlencoded",
                "User-Agent" to "okhttp/4.12.0",
            ),
        )
        val data = json.decodeFromString<CarDetailEnvelope>(body).content?.data
        if (data == null) android.util.Log.w("CRInfo", "carDetail 空响应: ${body.take(300)}")
        return data?.let {
            CarDetail(
                typeName = data.carType,
                carCode = data.carCode,
                trainStyle = data.trainStyle,
                exteriorUrl = data.carPic,
                // 静音车厢这类没分配的项接口给空串，直接不收进列表，省得界面写一堆「暂无」
                facts = data.carInfo.filter { it.pictureValue.isNotBlank() }
                    .map { CarFact(it.pictureName, it.pictureValue) },
                coaches = data.coachPicList.map { CarPicture(it.pictureName, it.pictureUrl) },
                seats = data.coachDetailPicList.map {
                    val name = it.pictureName.substringBefore('#')
                    CarSeat(name, it.pictureName.substringAfter('#', "").ifBlank { name }, it.pictureUrl)
                },
            )
        }
    }

    /** 车站大屏：当日全部车次（含通过），到发时间与担当信息比官网更全。结果缓存 10 分钟。 */
    suspend fun screenTrains(telecode: String, date: String): List<ScreenTrain> {
        val key = "$telecode@$date"
        boardCache[key]?.takeIf { System.currentTimeMillis() - it.first < BOARD_TTL_MS }?.let { return it.second }
        val body = Http.postForm(
            "${WX_BASE}bigScreen/queryTrainByStation",
            mapOf("train_station_code" to telecode, "train_start_date" to date.replace("-", ""), "reqType" to "form"),
            referer = REFERER,
        )
        val rows = json.decodeFromString<WxcEnvelope<List<ScreenRowDto>>>(body).data.orEmpty()
        val mapped = rows.map { row ->
            ScreenTrain(
                trainCode = row.station_train_code,
                trainNo = row.train_no,
                fromStation = row.start_station_name,
                toStation = row.end_station_name,
                fromTelecode = row.start_station_telecode,
                toTelecode = row.end_station_telecode,
                arriveTime = normalizeTime(row.arrive_time),
                departTime = normalizeTime(row.start_time),
                runningTimeText = row.running_time,
                distanceKm = row.distance.toIntOrNull() ?: 0,
                platform = blankToNull(row.platform_no),
                trainStyle = row.train_style,
                className = row.train_class_name,
                typeName = row.train_type_name,
                stationBureauCode = row.bureau_code,
                corporationCode = row.corporation_code,
                stationCorporation = row.station_corporation_code,
                jiaoluCorporation = row.jiaolu_corporation_code,
                jiaoluDept = row.jiaolu_dept_train,
                jiaoluStyle = row.jiaolu_train_style,
                stationOrder = row.display_station_no,
            )
        }
        boardCache[key] = System.currentTimeMillis() to mapped
        return mapped
    }

    /**
     * 车型与区间里程。余票响应里既没有车型列也没有里程列，只能从板面捞：
     * 车型取出发站板面；里程 = 到达站板面里程 − 出发站板面里程
     * （板面的 distance 是相对该趟车始发站的累计里程）。
     * 捞不到就返回空由调用方降级，绝不让查询失败。
     */
    suspend fun factsFor(trips: List<Trip>, date: String): Map<String, TrainFacts> = coroutineScope {
        if (trips.isEmpty()) return@coroutineScope emptyMap()
        val ymd = date.replace("-", "")
        val codes = (trips.map { it.fromCode } + trips.map { it.toCode }).distinct().take(MAX_BOARD_STATIONS)
        val boards = codes.map { code ->
            // 逐张容错：模糊查询下要并发拉十几张板面，任何一张超时都不该让整批 enrichment 作废
            async {
                runCatching { code to screenTrains(code, ymd).associateBy { it.trainNo } }.getOrNull()
            }
        }.awaitAll().filterNotNull().toMap()

        trips.mapNotNull { trip ->
            val atFrom = boards[trip.fromCode]?.get(trip.trainNo) ?: return@mapNotNull null
            val atTo = boards[trip.toCode]?.get(trip.trainNo)
            val km = if (atTo != null) atTo.distanceKm - atFrom.distanceKm else 0
            val facts = TrainFacts(style = atFrom.trainStyle, distanceKm = if (km > 0) km else 0)
            if (facts.style.isBlank() && facts.distanceKm == 0) null else trip.trainNo to facts
        }.toMap()
    }

    private fun TravelStopDto.toTrainStop() = TrainStop(
        name = stationName,
        telecode = stationTelecode,
        order = stationNo.toIntOrNull() ?: 0,
        arriveTime = normalizeTime(arriveTime),
        departTime = normalizeTime(startTime),
        dwellMinutes = stopover_time.toIntOrNull() ?: 0,
        minutesFromStart = runningTime.toIntOrNull() ?: 0,
        dayDiff = dayDifference.toIntOrNull() ?: 0,
        lat = lat.toDoubleOrNull(),
        lon = lon.toDoubleOrNull(),
        wicket = blankToNull(wicket),
        waitingRoom = blankToNull(waitingRoom),
        exit = blankToNull(exit),
        stationBureauCode = bureau_code,
        stationCorporation = station_corporation_code,
        ticketDelay = blankToNull(ticketDelay),
        stationTrainCode = stationTrainCode.ifBlank { dispTrainCode },
    )
}
