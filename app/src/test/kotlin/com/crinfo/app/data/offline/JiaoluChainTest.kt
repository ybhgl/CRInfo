package com.crinfo.app.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JiaoluChainTest {
    private val g1Rake = JiaoluRake(
        carType = "CR400BF-S型重联",
        note = "G1955/6/7/8石家庄调向",
        group = listOf("G1806/G1807", "G82/G79", "G32", "G1", "G1956/G1957", "G1958/G1955"),
    )

    private val repositioningRake = JiaoluRake(
        carType = "CR400BF-A",
        note = "",
        group = listOf("0G7200", "G7199", "G14", "G13"),
    )

    @Test
    fun `链内任一一程都能命中整段`() {
        val at = locateJiaolu(listOf(g1Rake), "G1")!!
        assertEquals(3, at.segmentIndex)
        assertEquals(listOf("G1"), at.currentSegment.codes)
        assertEquals("G32", at.previousCode)
        assertEquals("G1956/G1957", at.nextCode)

        // 带斜杠的段：输入 G1957 也要定位到 G1956/G1957 这一项
        assertEquals(4, locateJiaolu(listOf(g1Rake), "G1957")!!.segmentIndex)
    }

    @Test
    fun `前导零是空驶段，与裸车次同指一程`() {
        val at = locateJiaolu(listOf(repositioningRake), "G7200")!!
        assertEquals(0, at.segmentIndex)
        assertEquals(listOf("0G7200"), at.currentSegment.codes)
        assertTrue(at.currentSegment.emptyRun)
        // 前导 0 只是库内不载客标记，显示与查询都不该带着它
        assertEquals("G7200", at.currentSegment.display)
        assertEquals("G7200", at.currentSegment.queryCode)
        assertEquals("G7199", at.nextCode)
        assertFalse(at.window[1].emptyRun)
    }

    @Test
    fun `中途改号是一趟车，整体显示整体查询`() {
        val rake = JiaoluRake("CR400BF-A", "", listOf("0G7806/5", "G7804", "G669", "G7803", "0G7804/3"))
        val at = locateJiaolu(listOf(rake), "G669")!!
        assertEquals(listOf("G7806/G7805", "G7804", "G669", "G7803", "G7804/G7803"), at.window.map { it.display })
        // 点整串查斜杠前那一程，而不是把 `G7806/G7805` 整串当号去查
        assertEquals("G7806", at.window[0].queryCode)
        assertEquals("G7804", at.window[4].queryCode)
        // 一程一行时用逐行形式，行与行合起来仍等于整体串
        assertEquals(listOf("G7806", "G7805"), at.window[0].displayCodes)
        assertEquals("G7806/G7805", at.window[0].display)
    }

    @Test
    fun `批量车型索引与逐条定位同一套补全规则`() {
        val index = carTypeIndex(listOf(g1Rake, repositioningRake))
        assertEquals("CR400BF-S型重联", carTypeIndexLookup(index, "G1"))
        // 带斜杠段里的任意一程都要能查到
        assertEquals("CR400BF-S型重联", carTypeIndexLookup(index, "g1957"))
        // 空驶段的前导 0 在查表时一起剥掉
        assertEquals("CR400BF-A", carTypeIndexLookup(index, "0G7200"))
        assertEquals("CR400BF-A", carTypeIndexLookup(index, "G7200"))
        assertNull(carTypeIndexLookup(index, "G9999"))
    }

    @Test
    fun `只剥首位零，车次号中间的数字不能被吞`() {
        val rake = JiaoluRake("CR400BF-A", "", listOf("G1024", "G124"))
        assertEquals(0, locateJiaolu(listOf(rake), "G1024")!!.segmentIndex)
        assertEquals(1, locateJiaolu(listOf(rake), "G124")!!.segmentIndex)
    }

    @Test
    fun `窗口只给前后各一段`() {
        val at = locateJiaolu(listOf(g1Rake), "G1")!!
        assertEquals(listOf("G82/G79", "G32", "G1", "G1956/G1957", "G1958/G1955"), at.window.map { it.display })
        // 链首的车只往前取，不越界
        assertEquals(
            listOf("G7200", "G7199", "G14"),
            locateJiaolu(listOf(repositioningRake), "G7200")!!.window.map { it.display },
        )
    }

    @Test
    fun `段内简写在定位前补全成完整车次号`() {
        val rake = JiaoluRake("CR400BF-A", "", listOf("G82/79", "G1956/7", "G1955/6/7/8", "0G7200/1"))
        assertEquals(listOf("G82", "G79"), expandSegmentCodes("G82/79"))
        assertEquals(listOf("G1956", "G1957"), expandSegmentCodes("G1956/7"))
        assertEquals(listOf("G1955", "G1956", "G1957", "G1958"), expandSegmentCodes("G1955/6/7/8"))
        // 空驶标记的首位 0 属于字母头，简写要接在 0G720 后面
        assertEquals(listOf("0G7200", "0G7201"), expandSegmentCodes("0G7200/1"))
        // 完整形式原样保留
        assertEquals(listOf("G1806", "G1807"), expandSegmentCodes("G1806/G1807"))

        // 简写段里的每一程都要能定位，且窗口给的是补全后的串（链首只往后取）
        val at = locateJiaolu(listOf(rake), "G79")!!
        assertEquals(0, at.segmentIndex)
        assertEquals(
            listOf("G82/G79", "G1956/G1957", "G1955/G1956/G1957/G1958"),
            at.window.map { it.display },
        )
        assertEquals(1, locateJiaolu(listOf(rake), "G1957")!!.segmentIndex)
        assertEquals(2, locateJiaolu(listOf(rake), "G1958")!!.segmentIndex)
    }

    @Test
    fun `不在链上就返回空`() {
        assertNull(locateJiaolu(listOf(g1Rake), "G9998"))
        assertNull(locateJiaolu(emptyList(), "G1"))
        assertNull(locateJiaolu(listOf(g1Rake), "0"))
    }

    @Test
    fun `链文案标出当前车次并在两端补省略号`() {
        val at = locateJiaolu(listOf(g1Rake), "G1")!!
        assertEquals("G82/G79 › G32 › 【G1】 › G1956/G1957 › G1958/G1955", at.describe())
        // 链尾取满窗口时前面还藏着三段，应当挂省略号；后面没有就不挂
        assertEquals("… G1 › G1956/G1957 › 【G1958/G1955】", locateJiaolu(listOf(g1Rake), "G1958")!!.describe())
    }
}
