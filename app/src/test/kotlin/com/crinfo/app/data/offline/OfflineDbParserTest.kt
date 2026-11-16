package com.crinfo.app.data.offline

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 布局用合成样例钉死，防止再次逆向时把大端/小端、略号长度字节、记录头宽度写歪。
 * 期望值取自路路通板面实测：京沪高速线 廊坊 60km、京津所 110km。
 */
class OfflineDbParserTest {

    private fun intOf(v: Int) = byteArrayOf(
        (v ushr 24).toByte(),
        (v ushr 16).toByte(),
        (v ushr 8).toByte(),
        v.toByte(),
    )

    private fun node(name: String, tele: String, km: Int): ByteArray {
        val nb = name.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(nb.size)
        out.write(nb)
        out.write(3)
        out.write(tele.toByteArray(Charsets.UTF_8))
        out.write(byteArrayOf(-1, -1, -1, -1))
        out.write(intOf(km))
        return out.toByteArray()
    }

    /** §1 线路块 = `[4B 线路ID][1B 名字节数][线路名][4B 节点数]` + 节点。 */
    private fun block(id: Int, line: String, vararg nodes: ByteArray): ByteArray {
        val lb = line.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(intOf(id))
        out.write(lb.size)
        out.write(lb)
        out.write(intOf(nodes.size))
        nodes.forEach { out.write(it) }
        return out.toByteArray()
    }

    /** §2 径路块 = `[0x0C][12B trainNo][1B nl][终到站][4B N]` + N × `[1B nl][站名][4B 线路ID]`。 */
    private fun trainBlock(key: String, terminus: String, vararg legs: Pair<String, Int>): ByteArray {
        val tb = terminus.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(0x0C)
        out.write(key.toByteArray(Charsets.UTF_8))
        out.write(tb.size)
        out.write(tb)
        out.write(intOf(legs.size))
        legs.forEach { (station, lineId) ->
            val sb = station.toByteArray(Charsets.UTF_8)
            out.write(sb.size)
            out.write(sb)
            out.write(intOf(lineId))
        }
        return out.toByteArray()
    }

    private val jinghu = block(
        1,
        "京沪高速线",
        node("北京南", "VNP", 0),
        node("廊坊", "LJP", 60),
        node("京津所", "JTP", 110),
    )
    private val hebang = block(
        2,
        "合蚌高速线",
        node("蚌埠南", "BMH", 0),
        node("合肥", "HFH", 132),
    )

    /** 头 36B 是列定义段，第一个线路块的 ID 必须是 1，否则解析器找不到链头。 */
    private fun routeFile(vararg blocks: ByteArray, trains: List<ByteArray> = emptyList()): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(intOf(blocks.size))
        out.write(ByteArray(32))
        blocks.forEach { out.write(it) }
        out.write(intOf(trains.size))
        trains.forEach { out.write(it) }
        out.write(ByteArray(32))
        return out.toByteArray()
    }

    @Test
    fun `routes 块按大端里程与略号长度解析`() {
        val table = OfflineDbParser.parseRouteTable(routeFile(jinghu, hebang))

        assertEquals(2, table.lines.size)
        val jh = table.lines.first { it.name == "京沪高速线" }
        assertEquals(1, jh.id)
        assertEquals(listOf("VNP", "LJP", "JTP"), jh.stops.map { it.telecode })
        assertEquals(listOf("北京南", "廊坊", "京津所"), jh.stops.map { it.name })
        assertEquals(listOf(0, 60, 110), jh.stops.map { it.km })

        val hb = table.lines.first { it.name == "合蚌高速线" }
        assertEquals(listOf("BMH", "HFH"), hb.stops.map { it.telecode })
        assertEquals(listOf(0, 132), hb.stops.map { it.km })
    }

    /** 只有一个节点的线路也必须在表里：按节点链扫描会把它们整条丢掉，§2 的线路ID join 连带出错。 */
    @Test
    fun `单站线路不被丢弃`() {
        val table = OfflineDbParser.parseRouteTable(routeFile(jinghu, block(2, "三江线", node("三江", "SNW", 0))))
        val sanjiang = table.lines.first { it.name == "三江线" }
        assertEquals(listOf("SNW"), sanjiang.stops.map { it.telecode })
    }

    /** §2 给出的是换线点序列：站名后的线路ID 就是离开该站之后所走的线。 */
    @Test
    fun `每车径路按线路ID 索引`() {
        val table = OfflineDbParser.parseRouteTable(
            routeFile(
                jinghu,
                hebang,
                trains = listOf(trainBlock("4f000G373907", "南宁东", "北京南" to 1, "蚌埠南" to 2)),
            ),
        )
        val route = table.trainRoutes["4f000G373907"]
        assertEquals("南宁东", route?.terminus)
        assertEquals(listOf("北京南" to 1, "蚌埠南" to 2), route?.legs?.map { it.station to it.lineId })
    }

    /** 步长差一立刻散架：三节链必须完整解出，里程按 4 字节大端。 */
    @Test
    fun `节点步长差一会被识破`() {
        val line = OfflineDbParser.parseRouteTable(routeFile(jinghu)).lines.single()
        assertEquals(3, line.stops.size)
        assertEquals(listOf(0, 60, 110), line.stops.map { it.km })
        assertEquals("京津所", line.stops[2].name)
    }

    /**
     * 车底记录 = `[01 35][2B 短键][01 40 4c ff][8B 头][2B 属性][1B 车型长][车型][1B 组长][组]`。
     * 头形态还有「内层再一个 01 35 短键」（无常量）那种，见 [OfflineDbRealFileTest]。
     */
    /**
     * 按权威布局（`JlbDataMgr`）造一条车底记录：
     * `int32 +0 | int32 +4 | int32 +8 | int32 +12 | int16(255 进制) +16 | 1B 车型长 + 车型 | 1B 组长 + 担当组`。
     *
     * `family`（0x34/0x35）落在第一个 int32 里、`attr` 落在 int16 低位 —— 这两个字节都**不参与走步**，
     * 取什么都不该影响解析。旧实现按标记认形，才会被它们带歪、整族漏掉（G3739 当时查不到）。
     */
    private fun rakeRecord(
        type: String,
        group: String,
        family: Int = 0x35,
        attr: Int = 0x04,
    ): ByteArray {
        val tb = type.toByteArray(Charsets.UTF_8)
        val gb = group.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(intOf((0x01 shl 24) or (family shl 16) or 0x4f))     // 01 family 00 4f
        out.write(intOf((0x01 shl 24) or (0x40 shl 16) or (0x4c shl 8) or 0xff))
        out.write(intOf(1))                                            // +8
        out.write(intOf(1))                                            // +12
        out.write(byteArrayOf(0, attr.toByte()))                       // +16 企业/局段代码
        out.write(tb.size)
        out.write(tb)
        out.write(gb.size)
        out.write(gb)
        return out.toByteArray()
    }

    private fun jlbFile(vararg records: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(intOf(records.size))
        records.forEach { out.write(it) }
        return out.toByteArray()
    }

    @Test
    fun `jlb 记录拆出车型 备注与担当车次组`() {
        val rakes = OfflineDbParser.parseJlb(
            jlbFile(
                rakeRecord("CR400BF-S型重联，G1955/6/7/8石家庄调向", "G1806/G1807#G82/G79#G32#G1#G1956/G1957"),
            ),
        )

        assertEquals(1, rakes.size)
        val rake = rakes[0]
        assertEquals("CR400BF-S型重联", rake.carType)
        assertEquals("G1955/6/7/8石家庄调向", rake.note)
        assertEquals(listOf("G1806/G1807", "G82/G79", "G32", "G1", "G1956/G1957"), rake.group)
    }

    /** 记录只有一种形态：族字节 0x34/0x35、企业代码取什么都不影响走步（旧实现在这儿整族漏过车）。 */
    @Test
    fun `族字节与属性字节不影响走步`() {
        val rakes = OfflineDbParser.parseJlb(
            jlbFile(
                rakeRecord("CRH380A", "G3744/1#G3743/2", family = 0x34),
                // G3739 那条的 +16 低位是 0x68，不是 0x00：按「属性必须为 0」判会把整条判死
                rakeRecord("CR400AF-S", "G3740#C8932#C8931#G3739", attr = 0x68),
            ),
        )
        assertEquals(listOf("CRH380A", "CR400AF-S"), rakes.map { it.carType })
        assertEquals("G3740#C8932#C8931#G3739", rakes[1].group.joinToString("#"))
        assertEquals(0x68, rakes[1].enterpriseCode)
    }

    /** 属性字节非 00 的记录（G3739 就是 `01 68`）曾经因长度字节写歪而整体错位。 */
    @Test
    fun `担当组里的全角车次转半角`() {
        val rake = OfflineDbParser.parseJlb(jlbFile(rakeRecord("高峰线", "C1323/2#Ｄ4595/8#C1321/4"))).single()
        assertEquals(listOf("C1323/2", "D4595/8", "C1321/4"), rake.group)
    }

    /** 厂商遗留测试记录：结构合法但车次串非法，剔除且不能带崩后面的记录。 */
    @Test
    fun `非法担当组不入表但保持走步`() {
        val rakes = OfflineDbParser.parseJlb(
            jlbFile(
                rakeRecord("热热热WWW", "3333~~5555"),
                rakeRecord("CR400AF", "G102#G121"),
            ),
        )
        assertEquals(1, rakes.size)
        assertEquals("CR400AF", rakes[0].carType)
    }

    /** 组为空是当日无担当的备用车底：记录本身合法，但交路链无从定位。 */
    @Test
    fun `空担当组保留记录`() {
        val rakes = OfflineDbParser.parseJlb(jlbFile(rakeRecord("CRH1A", "")))
        assertEquals(1, rakes.size)
        assertTrue(rakes[0].group.isEmpty())
    }

    @Test
    fun `段内前导零只剥第一个字符`() {
        val position = locateJiaolu(listOf(JiaoluRake("CR400AF", "", listOf("G1024", "0G124"))), "G124")
        assertEquals(listOf("0G124"), position?.currentSegment?.codes)
        assertNull(locateJiaolu(listOf(JiaoluRake("CR400AF", "", listOf("G1024"))), "G124"))
    }
}
