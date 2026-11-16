package com.crinfo.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 板面行的车站管辖单位码 → 局名：弹窗的「隶属路局」唯一来源，取错局比不取更糟。 */
class StationBureauTest {

    @Test
    fun `majority corporation code wins over a dirty row`() {
        val codes = listOf("P#北京", "P#北京", "P#北京", "H#上海", "")
        assertEquals("北京局", stationBureauNameFromCodes(codes))
    }

    @Test
    fun `short bureau name gets the 局 suffix once`() {
        assertEquals("北京局", stationBureauNameFromCodes(listOf("P#沧州车务段")))
        // 码表已是全名，青藏这种「公司」结尾的不能再被 normalizeBureauName 补一遍局字
        assertEquals("青藏公司", stationBureauNameFromCodes(listOf("O#青藏")))
    }

    @Test
    fun `unknown letters and empty boards give no bureau instead of a guess`() {
        assertEquals("", stationBureauNameFromCodes(emptyList()))
        assertEquals("", stationBureauNameFromCodes(listOf("", "  ")))
        // 码表外字母宁可留空，界面显示「暂无隶属路局数据」
        assertEquals("", stationBureauNameFromCodes(listOf("U#未知")))
    }
}
