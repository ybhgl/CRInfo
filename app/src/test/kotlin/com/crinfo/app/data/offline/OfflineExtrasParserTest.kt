package com.crinfo.app.data.offline

import com.crinfo.app.data.offline.OfflineFareParser.FareOverride
import com.crinfo.app.data.offline.OfflineFareParser.PairedFare
import com.crinfo.app.data.offline.OfflineFareParser.SeatFare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本轮补齐的各表的合成夹具测试。
 *
 * 刻意只覆盖「编码/解码必须对称」和「查询语义」两类断言；**字段偏移是否与真库一致**由
 * [OfflineDbRealFileTest] 用 Python 那轮对出来的锚点数字钉住 —— 合成夹具会跟解析器一起写错，
 * 这是上一轮踩过的坑（夹具多一个 00，三条测试全绿而真实库解析 0 条）。
 */
class OfflineExtrasParserTest {

    private fun be2(v: Int) = byteArrayOf((v ushr 8).toByte(), v.toByte())
    private fun be4(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    /** 255 进制编码：与读侧 `b0*255 + b1` 严格互逆。 */
    private fun id255(v: Int) = byteArrayOf((v / 255).toByte(), (v % 255).toByte())

    /** 128 进制编码（票价族那两个站键）。 */
    private fun id128(v: Int) = byteArrayOf((v / 128).toByte(), (v % 128).toByte())

    /** 4 字节 128 进制小端编码，读侧是 `b0 + b1*128 + b2*16384 + b3*2097152`。 */
    private fun date128(v: Int): ByteArray {
        var rest = v
        val out = ByteArray(4)
        for (i in 0 until 4) {
            out[i] = (rest % 128).toByte()
            rest /= 128
        }
        return out
    }

    private fun s1(text: String): ByteArray {
        val body = text.toByteArray(Charsets.UTF_8)
        return byteArrayOf(body.size.toByte()) + body
    }

    private fun s2(text: String): ByteArray {
        val body = text.toByteArray(Charsets.UTF_8)
        return be2(body.size) + body
    }

    private fun List<ByteArray>.concat(): ByteArray = fold(byteArrayOf()) { acc, part -> acc + part }

    /* ---------------- OfflineBinary ---------------- */

    @Test
    fun `seatSlots 按席别权重给格子数`() {
        assertEquals(0, OfflineBinary.seatSlots(0))
        assertEquals(1, OfflineBinary.seatSlots(1))
        assertEquals(1, OfflineBinary.seatSlots(32))
        assertEquals(3, OfflineBinary.seatSlots(4))
        assertEquals(2, OfflineBinary.seatSlots(8))
        // 硬座+软座+硬卧+软卧 = 1+1+3+2，普速票价行就是这么长
        assertEquals(7, OfflineBinary.seatSlots(0b1111))
        assertEquals(9, OfflineBinary.seatSlots(0b111111))
    }

    @Test
    fun `date128 编解码互逆且能装下长期哨兵`() {
        listOf(20261124, 20991231, 20261101).forEach { v ->
            assertTrue("解码不互逆：$v", v contentEquals date128(v))
        }
    }

    private infix fun Int.contentEquals(encoded: ByteArray): Boolean =
        OfflineBinary.date128(encoded, 0) == this

    /* ---------------- sp.dat ---------------- */

    @Test
    fun `sp 六张表走步不留残余`() {
        val bytes = be4(1) +
            (be4(6012) + be4(20261101) + be4(20301231) + id255(106) + id255(83)) +
            be4(2) + s1("4f000G373907") + s1("240000146100") +
            id255(2) + (id255(1) + s1("河北省")) + (id255(2) + s1("山西省")) +
            be4(1) + (be4(171) + id255(207) + id255(1)) +
            be4(1) + (id255(171) + s1("河北省 石家庄市")) +
            be4(1) + s1("安图,吉林省 延边州 安图县")
        val sp = SpData.parse(bytes)
        assertEquals(0, sp.residual)
        assertEquals(listOf("4f000G373907", "240000146100"), sp.trainNos)
        assertEquals("下标即车次 ID", "4f000G373907", sp.trainNoOf(0))
        assertEquals("河北省 石家庄市", sp.locationById[171])
        assertEquals("吉林省 延边州 安图县", sp.locationByName["安图"])
        assertEquals(StationBelong(207, 1), sp.belongs[171])
        assertEquals("河北省", sp.provinceNames[1])
        assertEquals(
            listOf(TrainDuty(6012, 20261101, 20301231, 106, 83)),
            sp.duties,
        )
        assertEquals(83, sp.dutyCode(6012, 20261201))
        assertNull("超出生效期就不该命中", sp.dutyCode(6012, 20310101))
    }

    /* ---------------- sn.dat / ime / cdn ---------------- */

    @Test
    fun `sn 组表按官方口径把主站轮到首位`() {
        val bytes = be2(1) + s2("安图") +
            be2(2) +
            // 组键 10000（= 表1 的「安图」）→ 成员 5
            (be2(10000) + byteArrayOf(1) + be2(5)) +
            // 组键 4 → 成员 1、9；官方把「第一个 ≥ 键」的成员轮转到最前，所以 9 在前
            (be2(4) + byteArrayOf(2) + be2(1) + be2(9)) +
            be2(1) + s1("叆河-AH")
        val sn = StationGroupData.parseSn(bytes)
        assertEquals(0, sn.residual)
        assertEquals(listOf("安图"), sn.groupNames)
        assertEquals(listOf(9, 1), sn.groups[4]?.toList())
        assertEquals("叆河 的电报码", "AH", sn.telecodes["叆河"])
        assertEquals("站 ID 与 10000+ 组键都反建", 4, sn.groupKeyOfStation[1])
        assertEquals(StationGroupData.CITY_KEY_BASE, sn.groupKeyOfStation[5])
        assertEquals("请求站置顶", listOf(1, 9), sn.cityMembers(1).toList())
        assertEquals("不在任何组里就是自己", listOf(77), sn.cityMembers(77).toList())
    }

    @Test
    fun `ime 与 cdn 行表按行解`() {
        val ime = StationGroupData.parseIme(be2(2) + s2("22 长春蚌埠") + s2("222 叉车厂"))
        assertEquals(2, ime.size)
        assertEquals("长春蚌埠", ime["22"])
        assertEquals(listOf("11B7ZD7Z39Z8DA8", "1173ZDFZ07ZCF50"), StationGroupData.parseBlockIds("11B7ZD7Z39Z8DA8\r\n1173ZDFZ07ZCF50\r\n".toByteArray()))
    }

    /* ---------------- plat.dat ---------------- */

    @Test
    fun `plat 排序后二分查得到也查不出串位`() {
        val records = listOf(
            Triple(0, 165, "13"),
            Triple(6012, 171, "17"),
            Triple(0, 2313, "19"),
        )
        val bytes = be4(records.size) + records.map { (trainId, stationId, text) ->
            be4(trainId) + be4(stationId) + s1(text)
        }.concat()
        val table = PlatformTable.parse(bytes)
        assertEquals(3, table.size)
        assertEquals(0, table.residual)
        assertEquals("17", table.platformOf(6012, 171))
        assertEquals("13", table.platformOf(0, 165))
        assertEquals("19", table.platformOf(0, 2313))
        assertNull("同站不同车不能串", table.platformOf(6012, 165))
        assertEquals(listOf(165, 2313), table.entriesOfTrain(0).map { it.first })
        assertEquals(0, table.duplicateCount())
    }

    /* ---------------- xw.dat ---------------- */

    @Test
    fun `xw 席别位图与按里程换号`() {
        val bytes = id255(2) +
            // 车次 26 = 「4167/4170」：0km 起用第 1 段，82km 起回到第 0 段
            (id255(26) + id255(5) + byteArrayOf(2) + id255(0) + byteArrayOf(1) + id255(82) + byteArrayOf(0)) +
            (id255(6012) + id255(48) + byteArrayOf(0))
        val seats = SeatTable.parse(bytes, trainCount = 7000)
        assertEquals(2, seats.parsed)
        assertEquals(0, seats.residual)
        assertEquals(5, seats.seatFlags(26))
        assertEquals(48, seats.seatFlags(6012))
        assertTrue("一等座存在", seats.hasSeat(6012, 16))
        assertFalse("动卧不存在", seats.hasSeat(6012, 64))
        assertTrue("库里没这车时按不限制处理", seats.hasSeat(999, 1))
        assertEquals("4170", seats.exactCode(26, "4167/4170", km = 50))
        assertEquals("4167", seats.exactCode(26, "4167/4170", km = 100))
        assertEquals("没有换号表就原样给", "G1", seats.exactCode(6012, "G1", km = 1000))
    }

    /* ---------------- extra.dat ---------------- */

    @Test
    fun `extra 三档复兴号名单靠后的覆盖靠前的`() {
        val bytes = id255(2) + s1("上局上海段") + s1("广铁长沙段") +
            // 表2：车次 0 担当 83（站级细分给了 1 站 1 段）
            (id255(2) + (id255(0) + id255(83) + byteArrayOf(0)) + (id255(1) + id255(218) + byteArrayOf(1) + id255(165) + byteArrayOf(1) + date128(20261101) + date128(20271231) + s1("候检室B"))) +
            id255(1) + id255(0) +   // 表3 → 1 档
            id255(1) + id255(0) +   // 表4 → 2 档
            id255(1) + id255(0) +   // 表5 → 3 档（最终值）
            id255(1) + (id255(1) + byteArrayOf(2) + id255(677) + id255(1731)) +
            id255(1) + id255(1)
        val extra = ExtraData.parse(bytes, trainCount = 4)
        assertEquals(0, extra.residual)
        assertEquals(83, extra.dutyCode(0))
        assertEquals("没有记录的车次要给 null 而不是 0", null, extra.dutyCode(2))
        assertEquals("后一张名单覆盖前一张", 3, extra.fuXingLevel(0))
        assertEquals(listOf(677, 1731), extra.turnStationIds[1]?.toList())
        assertTrue(extra.isSTrain(1))
        assertFalse(extra.isSTrain(0))
        assertEquals("站级细分挂在车次 1 上", "候检室B", extra.dutyText(1, 165, 20261201))
        assertEquals("日期不在区间内退回最后一段", "候检室B", extra.dutyText(1, 165, 20300101))
        assertNull("站没记录就是没记录", extra.dutyText(1, 999, 20261201))
        assertNull("车次 0 只有车次级担当", extra.dutyText(0, 165, 20261201))
    }

    /* ---------------- 票价族 ---------------- */

    @Test
    fun `p 族成对价两方向都认`() {
        // [起 1][到 2][n=2][默认A=10][默认B=20] + 1 行 [票价号 5][30][40]
        val bytes = id128(1) + id128(2) + byteArrayOf(2) + id255(10) + id255(20) + id255(5) + id255(30) + id255(40)
        val file = OfflineFareParser.parsePairedFares(bytes, "p0.dat")
        assertEquals(0, file.residual)
        assertEquals("ok", file.stoppedAt)
        val fare: PairedFare = file.fare(1, 2)!!
        assertEquals(10, fare.firstPrice)
        assertEquals(20, fare.secondPrice)
        assertEquals(listOf(FareOverride(5, 30, 40)), fare.overrides)
        assertEquals("反查也要命中", fare, file.fare(2, 1))
        assertNull(file.fare(1, 3))
    }

    @Test
    fun `pk 族按席别位图取可变长度`() {
        // [起 1][到 2][1 条][票价号 7][位图 硬座+软座] + 2 个价
        val bytes = id255(1) + id255(2) + byteArrayOf(1) + id255(7) + byteArrayOf(0b0011) + id255(120) + id255(240)
        val file = OfflineFareParser.parseSeatFares(bytes, "pk0.dat")
        assertEquals(0, file.residual)
        val fare: SeatFare = file.fare(1, 2)!!
        assertEquals(1, fare.entries.size)
        assertEquals(7, fare.entries[0].priceKey)
        assertEquals("票价单位是角", listOf(120, 240), fare.entries[0].prices.toList())
    }

    @Test
    fun `sch 行宽随类型变化且整车次哨兵识别得出`() {
        val bytes = id255(6012) + id255(16384) + byteArrayOf(2) +
            (byteArrayOf(0) + date128(20261101) + date128(20261130) + byteArrayOf(1, 2, 3)) +
            (byteArrayOf(1) + date128(20261201) + date128(20261210))
        val rows = OfflineFareParser.parseSchedule(bytes)
        assertEquals(1, rows.size)
        val entry = rows[0]
        assertTrue(entry.isTrainWide)
        assertEquals(0, entry.items[0].type)
        assertEquals(listOf(1, 2, 3), entry.items[0].extra.toList())
        assertEquals("停运行的日期能解出来", 20261201, entry.items[1].startDate)
        assertEquals(1, entry.items[1].type)
    }

    @Test
    fun `ts 与 sch 空表安全`() {
        assertTrue(OfflineFareParser.parsePriceRules(ByteArray(0)).isEmpty())
        assertTrue(OfflineFareParser.parseSchedule(ByteArray(0)).isEmpty())
        assertEquals(0, OfflineFareParser.parsePriceRules(be4(0)).size)
    }

    /* ---------------- 装载与派生查询 ---------------- */

    @Test
    fun `extras 缺表只让那一组为空`() {
        val extras = OfflineExtras.parse(mapOf("xw.dat" to id255(1) + id255(3) + id255(48) + byteArrayOf(0)), trainCount = 8)
        assertEquals("没有 sp.dat 时 trainNo 查不出", null, extras.trainNoOf(3))
        assertEquals(48, extras.seatsOfTrain(3))
        assertTrue("票价族整组缺席也不炸", extras.pairedFares.isEmpty() && extras.seatFares.isEmpty())
        assertNull(extras.platformOf(3, 1))
        assertEquals("担当表空", null, extras.dutyCode(3))
    }

    /**
     * 联网板面缺检票口时才用 plat.dat 补「参考站台」：有实时票口不叠加（静态值不许稀释实时值），
     * 库里 (车次,站) 没记录就维持空。
     */
    @Test
    fun `缺检票口才补参考站台`() {
        // plat.dat：(车次 0, 站 1) → 17；(车次 1, 站 1) → 3
        val plat = be4(2) + be4(0) + be4(1) + s1("17") + be4(1) + be4(1) + s1("3")
        val db = OfflineDb(
            version = "20261108",
            lines = emptyList(),
            rakes = emptyList(),
            trainCodes = listOf("G1", "G9"),
            stationNames = listOf("甲站", "乙站"),
            extras = OfflineExtras(platforms = PlatformTable.parse(plat)),
        )
        fun row(code: String, gate: String?, ref: String? = null) = com.crinfo.app.data.model.BoardEntry(
            direction = com.crinfo.app.data.model.BoardDirection.Departure,
            trainCode = code,
            trainNo = "no$code",
            time = "07:00",
            dayDiff = 0,
            otherEnd = "丙站",
            platform = gate,
            referencePlatform = ref,
            trainStyle = "CR400BF-A",
            className = "",
            distanceKm = 0,
            stationOrder = 1,
            live = null,
        )

        val out = OfflineFallback.referencePlatforms(db, "乙站", listOf(row("G1", null), row("G9", "12A"), row("G9", null)))
        assertEquals("缺票口的补上图定股道", "17", out[0].referencePlatform)
        assertEquals("有实时检票口不叠加", null, out[1].referencePlatform)
        assertEquals("同一车次补位值可复用", "3", out[2].referencePlatform)
        assertTrue("站名对不上库里没收录时整批原样返回", OfflineFallback.referencePlatforms(db, "不存在的站", listOf(row("G1", null))).first().referencePlatform == null)
        assertTrue("没装库时不动数据", OfflineFallback.referencePlatforms(null, "乙站", listOf(row("G1", null))).first().referencePlatform == null)
    }

    @Test
    fun `extras 担当优先吃 extra 表再按日期吃 sp 表`() {
        val extra = id255(1) + s1("上局上海段") +
            id255(1) + (id255(7) + id255(0) + byteArrayOf(0)) +
            id255(0) + id255(0) + id255(0) + id255(0) + id255(0)
        val sp = be4(0) + be4(0) + id255(0) + be4(0) + be4(0) + be4(0)
        val entries = mapOf(
            "extra.dat" to extra,
            "sp.dat" to sp,
        )
        val extras = OfflineExtras.parse(entries, trainCount = 10)
        val enterprises = listOf("上局上海段")
        assertEquals("上局上海段", extras.dutyName(enterprises, 7))
        assertEquals("代码越界给 null", null, extras.dutyName(enterprises, 8))
        assertEquals(0, extras.dutyCode(7, date = 20261102))
    }
}
