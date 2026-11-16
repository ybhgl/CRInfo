package com.crinfo.app.data.offline

import com.crinfo.app.data.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 车票列表兜底合并的纯逻辑：缺项才补、已有值绝不覆盖、合并号分段命中。 */
class OfflineFallbackTripsTest {

    private fun trip(
        code: String,
        style: String = "",
        km: Int = 0,
    ) = Trip(
        trainCode = code, trainNo = "", date = "2026-11-02",
        fromCode = "VNP", toCode = "AOH", fromName = "北京南", toName = "上海虹桥",
        startCode = "VNP", endCode = "AOH", startName = "北京南", endName = "上海虹桥",
        departTime = "07:00", arriveTime = "11:00", durationText = "4小时0分",
        canWait = true, seats = emptyList(), trainStyle = style, distanceKm = km,
    )

    @Test
    fun `缺项才补且板面已有值优先`() {
        val reference = listOf(
            trip("G3741/G3744", style = "CR400AF-A", km = 828),
            trip("G10", style = "CR400BF-A", km = 1318),
        )
        val online = listOf(
            trip("G3744"),
            trip("G10", style = "CR400AF-A", km = 0),
            trip("G123", style = "", km = 100),
        )
        val out = OfflineFallback.enrichTrips(online, reference)

        // 线上出现合并串的一段也要能命中库里的整串记录
        assertEquals("CR400AF-A", out[0].trainStyle)
        assertEquals(828, out[0].distanceKm)
        // 板面已给车型就不覆盖，只补 0 里程
        assertEquals("CR400AF-A", out[1].trainStyle)
        assertEquals(1318, out[1].distanceKm)
        // 库里没有的车原样保留
        assertEquals("", out[2].trainStyle)
        assertEquals(100, out[2].distanceKm)
    }

    @Test
    fun `没有缺项时不产生新列表`() {
        val online = listOf(trip("G1", style = "CR400BF-A", km = 1318))
        assertSame(online, OfflineFallback.enrichTrips(online, emptyList()))
    }
}
