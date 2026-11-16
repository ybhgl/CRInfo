package com.crinfo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 车站信息弹窗那批纯函数：路局投票、换乘分档、空类别过滤、地址与起售取本站。 */
class StationProfileTest {

    private fun relay(name: String, minutes: Int, same: Boolean = false) =
        RelayLeg(name, "T$name", minutes, same)

    @Test
    fun `relay legs collapse into minute buckets from short to long`() {
        val groups = groupRelaysByMinutes(
            listOf(relay("北京南", 180), relay("北京西", 180), relay("北京北", 90), relay("北京", 30, same = true)),
        )
        assertEquals(listOf(30, 90, 180), groups.map { it.first })
        assertEquals(listOf("北京南", "北京西"), groups.last().second.map { it.toName })
    }

    @Test
    fun `zero minute relay rows are dropped instead of showing as no wait`() {
        // 接口对个别站给 0，读成「不用预留」是危险的，宁可整条不显示
        assertTrue(groupRelaysByMinutes(listOf(relay("北京", 0))).isEmpty())
    }

    @Test
    fun `empty service groups never reach the dialog`() {
        val kept = dropEmptyServiceGroups(
            listOf(
                StationServiceGroup("服务区域", listOf("哺乳室")),
                StationServiceGroup("其他服务", emptyList()),
            ),
        )
        assertEquals(listOf("服务区域"), kept.map { it.title })
    }

    @Test
    fun `online address wins over the offline province-city-district string`() {
        val base = StationProfile(
            stationName = "北京南", telecode = "VNP", city = "北京", bureau = "北京局",
            address = null, offlineLocation = null,
            saleTimes = emptyList(), relays = emptyList(), weather = null, intro = null,
            services = emptyList(),
        )
        assertEquals("", base.displayAddress)
        assertEquals("北京市 丰台区", base.copy(offlineLocation = "北京市 丰台区").displayAddress)
        assertEquals(
            "北京市丰台区车站路12号",
            base.copy(
                offlineLocation = "北京市 丰台区",
                address = StationAddress("VNP", "北京南站", "北京", "北京市丰台区车站路12号", null, null),
            ).displayAddress,
        )
    }

    @Test
    fun `own sale time is matched by exact station name`() {
        val profile = StationProfile(
            stationName = "北京南", telecode = "VNP", city = "北京", bureau = "",
            address = null, offlineLocation = null,
            saleTimes = listOf(SaleStart("北京", "10:00"), SaleStart("北京南", "12:45")),
            relays = emptyList(), weather = null, intro = null, services = emptyList(),
        )
        assertEquals("12:45", profile.ownSaleTime)
        assertNull(profile.copy(stationName = "北京北").ownSaleTime)
    }

    @Test
    fun `intro with only a title counts as blank`() {
        // 接口会给一段只有站名的 title，没有正文也没有图就不该占一整块「简介」
        assertTrue(StationIntro(title = "郑州站", paragraphs = emptyList(), pictureNames = emptyList()).isBlank)
        assertTrue(StationIntro(title = "", paragraphs = listOf("正文"), pictureNames = emptyList()).isBlank.not())
    }

    private fun weather(
        day: String = "晴",
        night: String = "多云",
        high: String = "18",
        low: String = "3",
    ) = StationWeather(
        city = "北京", phenomena = "晴", temperature = "10", humidity = "62",
        windDirection = "东北风", windPower = "3", aqi = "73", indices = emptyList(),
        todayRange = "3~18℃", dayPhenomena = day, nightPhenomena = night,
        highTemp = high, lowTemp = low, observedAt = "18:40",
    )

    @Test
    fun `day night summary reads as 晴转多云 18-3℃`() {
        assertEquals("晴转多云 18-3℃", weather().dayNightSummary())
    }

    @Test
    fun `same day and night phenomenon is written once, missing temperature drops the range`() {
        assertEquals("晴 18-3℃", weather(day = "晴", night = "晴").dayNightSummary())
        // 只有白天有值时不能说「晴转」，那是半句话
        assertEquals("晴 18-3℃", weather(night = "").dayNightSummary())
        // 缺温度只丢区间，天气照说，且不留下尾空格
        assertEquals("晴转多云", weather(high = "").dayNightSummary())
        assertEquals("", weather(day = "", night = "").dayNightSummary())
    }

    @Test
    fun `display name prefers the address full name and never doubles the 站 suffix`() {
        val base = StationProfile(
            stationName = "南宁东", telecode = "NNZ", city = "南宁", bureau = "",
            address = null, offlineLocation = null,
            saleTimes = emptyList(), relays = emptyList(), weather = null, intro = null,
            services = emptyList(),
        )
        assertEquals("南宁东站", base.displayName)
        assertEquals(
            "南宁东站",
            base.copy(address = StationAddress("NNZ", "南宁东站", "南宁", "南宁市", null, null)).displayName,
        )
        // 站名已带「站」就不再补，否则成「北京南站站」
        assertEquals("北京南站", base.copy(stationName = "北京南站").displayName)
    }
}
