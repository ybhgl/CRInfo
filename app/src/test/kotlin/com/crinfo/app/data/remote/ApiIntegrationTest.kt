package com.crinfo.app.data.remote

import com.crinfo.app.data.model.LiveBoard
import com.crinfo.app.data.model.Seat
import com.crinfo.app.data.model.buildBoard
import com.crinfo.app.data.model.buildScreen
import com.crinfo.app.data.model.dayNightSummary
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真联网集成测试：验证四套接口在 App 侧（OkHttp + Cookie + 表单/JSON 编码）确实可用。
 * 只在需要回归「接口是否还活着」时运行，日常 `testDebugUnitTest` 请靠 LeftTicketParserTest。
 */
class ApiIntegrationTest {
    private val futureDate: String get() = LocalDate.now().plusDays(3).toString()

    @Test
    fun `leftTicket returns real trips with seats and prices`() = runBlocking {
        val trips = LeftTicketApi.query("VNP", "AOH", futureDate) { null }
        assertTrue("余票结果为空", trips.isNotEmpty())
        val highSpeed = trips.first { it.trainCode.startsWith("G") }
        assertNotNull("缺二等座", highSpeed.seat(Seat.SECOND))
        assertTrue("票价未解出", (highSpeed.seat(Seat.SECOND)?.price ?: 0.0) > 0)
        println("trips=${trips.size} sample=$highSpeed")
    }

    @Test
    fun `wxxcx timetable carries route consist and operator`() = runBlocking {
        val info = WxxcxApi.trainTimetable("G1", futureDate)
        assertTrue("停站过少", info.stops.size > 3)
        assertEquals("北京南", info.stops.first().name)
        assertEquals("上海虹桥", info.stops.last().name)
        assertTrue("车型缺失", info.trainStyle.isNotBlank())
        assertTrue("担当客运段缺失", info.jiaoluCorporation.isNotBlank())
        assertTrue("交路动车段缺失", info.jiaoluDept.isNotBlank())
        println("style=${info.trainStyle} jiaolu=${info.jiaoluTrainStyle}/${info.jiaoluDept} corp=${info.jiaoluCorporation}/${info.corporationCode}")
    }

    @Test
    fun `wxxcx station address and bureau resolve`() = runBlocking {
        val address = WxxcxApi.stationAddress("VNP")
        // 注意：地址接口返回「北京南站」带「站」字，与车站表的「北京南」不一致，UI 以车站表为准
        assertTrue("站名异常: ${address.name}", address.name.startsWith("北京南"))
        assertNotNull(address.lat)
        val bureau = WxxcxApi.operatingBureau("G1", futureDate)
        assertTrue("路局代码缺失", bureau.code.isNotBlank())
    }

    @Test
    fun `ctrip boards return live states and split the port string`() = runBlocking {
        val departures = CtripApi.departures("北京南")
        assertTrue("携程出发大屏为空", departures.isNotEmpty())
        assertTrue("缺检票口", departures.any { !it.wicket.isNullOrBlank() })
        // 携程把候车区与检票口并成一串，拆不开就是 split 口径错了
        assertTrue("候车室未拆出", departures.any { !it.waitingRoom.isNullOrBlank() })
        val arrivals = CtripApi.arrivals("北京南")
        assertTrue("携程到达大屏为空", arrivals.isNotEmpty())
        assertTrue("缺出站口", arrivals.any { !it.exitingPort.isNullOrBlank() })
        assertTrue("到达状态未落在 waitingState 上", arrivals.any { it.state.isNotBlank() })
        println("携程大屏 出发${departures.size} 到达${arrivals.size} sample=${departures.first()}")
    }

    @Test
    fun `liechetime boards return live states`() = runBlocking {
        val departures = LiechetimeApi.departures("北京南")
        assertTrue("出发大屏为空", departures.isNotEmpty())
        assertTrue("无检票口信息", departures.any { !it.wicket.isNullOrBlank() })
        val arrivals = LiechetimeApi.arrivals("北京南")
        assertTrue("到达大屏为空", arrivals.isNotEmpty())
    }

    @Test
    fun `liechetime delay data covers every stop`() = runBlocking {
        val delay = LiechetimeApi.trainDelay("G1", LocalDate.now().toString())
        assertTrue("正晚点为空", delay.stops.isNotEmpty())
        assertTrue("缺图定时刻", delay.stops.first().plannedDepart != null)
    }

    @Test
    fun `wxxcx big screen lists trains passing a station`() = runBlocking {
        val trains = WxxcxApi.screenTrains("VNP", futureDate.replace("-", ""))
        assertTrue("大屏为空", trains.isNotEmpty())
        assertTrue("缺担当信息", trains.any { it.jiaoluCorporation.isNotBlank() })
    }

    @Test
    fun `station board splits into departure and arrival`() = runBlocking {
        val trains = WxxcxApi.screenTrains("VNP", LocalDate.now().toString().replace("-", ""))
        val board = buildBoard(trains, "VNP")
        assertTrue("出发屏为空", board.departures.isNotEmpty())
        assertTrue("到达屏为空", board.arrivals.isNotEmpty())
        // 拆屏正确性的硬指标：终到本站的车不该出现在出发屏，始发本站的不该出现在到达屏
        assertTrue("出发屏混进终到本站的车", board.departures.none { it.otherEnd == "北京南" })
        assertTrue("到达屏混进始发本站的车", board.arrivals.none { it.otherEnd == "北京南" })
        val passing = board.departures.map { it.trainNo }.toSet() intersect board.arrivals.map { it.trainNo }.toSet()
        assertTrue("经停车应当两张屏都出现", passing.isNotEmpty())
        println("北京南 出发${board.departures.size} 到达${board.arrivals.size} 经停${passing.size}")
    }

    @Test
    fun `live screen rows enrich from the all-day board`() = runBlocking {
        val trains = WxxcxApi.screenTrains("VNP", LocalDate.now().toString().replace("-", ""))
        val board = buildBoard(trains, "VNP")
        val now = java.time.LocalTime.now()
        val live = LiechetimeApi.departures("北京南").map {
            LiveBoard(trainCode = it.trainCode, state = it.state, time = it.departTime, otherEnd = it.toStation, wicket = it.wicket)
        }
        val screen = buildScreen(live, emptyList(), board, now.hour * 60 + now.minute)
        assertTrue("大屏一条都没有", screen.departures.isNotEmpty())
        assertTrue("实时状态一条都没挂上", screen.departures.any { it.live != null })
        assertTrue("没有从板面补到车型", screen.departures.any { it.trainStyle.isNotBlank() })
        println("大屏实时 ${live.size} 条 → 演出 ${screen.departures.size} 条，带车型 ${screen.departures.count { it.trainStyle.isNotBlank() }} 条")
    }

    @Test
    fun `board facts supply train style and distance`() = runBlocking {
        val trips = LeftTicketApi.query("VNP", "AOH", futureDate) { null }
        val facts = WxxcxApi.factsFor(trips, futureDate)
        assertTrue("板面未补到任何车型/里程", facts.isNotEmpty())
        val g1 = trips.first { it.trainCode == "G1" }
        assertEquals("CR400BF-A", facts[g1.trainNo]?.style)
        // 京沪全程约 1318km，两站板面里程之差应落在合理区间
        assertTrue("里程异常: ${facts[g1.trainNo]?.distanceKm}", (facts[g1.trainNo]?.distanceKm ?: 0) in 1200..1400)
    }

    @Test
    fun `qssj gives sale time for every station of the city`() = runBlocking {
        val rows = WxxcxApi.saleStartTimes("北京")
        assertTrue("起售时间为空", rows.isNotEmpty())
        assertTrue("缺本站", rows.any { it.name == "北京南" })
        // 时间既可能是 `08:00` 也可能是 `8:00`，别拿长度当格式校验
        assertTrue("时刻格式异常: ${rows.first().time}", rows.all { it.time.matches(Regex("""\d{1,2}:\d{2}""")) })
        println("北京起售 ${rows.size} 站 sample=${rows.take(3)}")
    }

    @Test
    fun `relay wait covers the same-city stations`() = runBlocking {
        val legs = WxxcxApi.relayTimes("BJP", futureDate)
        assertTrue("换乘预留为空", legs.isNotEmpty())
        assertTrue("缺北京南的换乘行", legs.any { it.toName == "北京南" })
        // 本站自换乘这行一定在，但档位值别钉死：wxxcx 给 90、官网 queryLCtime 给 30，两端口径本就不同
        assertTrue("缺本站自换乘行", legs.any { it.sameStation && it.minutes > 0 })
        println("BJP 换乘 ${legs.size} 条 档位=${legs.map { it.minutes }.distinct()}")
    }

    @Test
    fun `weather gives observation for stations it covers`() = runBlocking {
        val bxp = WxxcxApi.stationWeather("BXP")
        assertNotNull("北京西应有天气", bxp)
        assertTrue("缺气温", bxp!!.temperature.isNotBlank())
        // 概况那行靠白天/夜间现象与高低温拼「晴转多云 18-3℃」，缺任何一项都会写不成句
        assertTrue("缺白天天气现象", bxp.dayPhenomena.isNotBlank())
        assertTrue("缺预报温度", bxp.highTemp.isNotBlank() && bxp.lowTemp.isNotBlank())
        assertTrue("概况写法异常: ${bxp.dayNightSummary()}", bxp.dayNightSummary().contains("-"))
        // 覆盖度看站：北京南实测回「查询天气失败」，这类缺项必须静默成 null 而不是抛异常
        val vnp = WxxcxApi.stationWeather("VNP")
        println("北京西 ${bxp.phenomena}${bxp.temperature}℃ AQI=${bxp.aqi} 指数${bxp.indices.size} 个；北京南=${vnp?.phenomena ?: "无数据"}")
    }

    @Test
    fun `station services come back grouped without empty categories`() = runBlocking {
        val groups = WxxcxApi.stationServices("VNP")
        assertTrue("站内服务为空", groups.isNotEmpty())
        assertTrue("混进空类别", groups.all { it.items.isNotEmpty() })
        println("北京南服务 ${groups.map { "${it.title}:${it.items.size}" }}")
    }

    @Test
    fun `station introduction only exists for some stations`() = runBlocking {
        val zzf = WxxcxApi.stationIntroduction("ZZF")
        assertNotNull("郑州站应有简介", zzf)
        assertTrue("简介正文为空", zzf!!.paragraphs.isNotEmpty())
        // 北京实测 0 段：接口给空列表，界面据此显示「暂无简介数据」
        assertEquals("北京不该有简介", null, WxxcxApi.stationIntroduction("BJP"))
        println("郑州站简介 ${zzf.paragraphs.size} 段 图${zzf.pictureNames.size} 张")
    }
}
