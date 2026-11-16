package com.crinfo.app.data.offline

import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 车次联想的匹配与排序口径验算：用合成车次表把规则钉死，不依赖 Android 与真实 an.db。
 * 真实库的取号正确性由 [OfflineDbRealFileTest] 那类端到端锚点负责，两边都要绿才算数。
 */
class TrainSuggestionIndexTest {
    /** 数据集属竞品不入仓库：真库校验只在机器上有下载件时跑，口径同 [OfflineDbRealFileTest]。 */
    private val path: String? = System.getenv("CRINFO_ANDB")

    private fun index(vararg codes: String) = TrainSuggestionIndex.build(codes.toList())

    private fun List<TrainSuggestion>.codes(): List<String> = map { it.code }

    @Test
    fun `输入单个数字时前缀命中的普客排在最前`() {
        val result = index("1461", "1462", "G1", "D1", "Z96").suggest("1")
        // 前缀组（1461、1462）整体排在包含组（D1、G1）之前，这就是「输 1 先看到 1461」
        assertEquals(listOf("1461", "1462", "D1", "G1"), result.codes())
    }

    @Test
    fun `纯数字输入能命中中段`() {
        val result = index("1461", "G461", "D1956").suggest("461")
        // 同长度按数字升序：G461(461) 排在 1461(1461) 前面
        assertEquals(listOf("G461", "1461"), result.codes())
        assertEquals(1 to 3, result[0].matchStart to result[0].matchLength)
        assertEquals(1 to 3, result[1].matchStart to result[1].matchLength)
    }

    @Test
    fun `短号在前且同长按数字升序`() {
        val result = index("G100", "G1", "G12", "G999", "K1").suggest("G1")
        // G999 不以 G1 开头；带字母的输入不进包含轮
        assertEquals(listOf("G1", "G12", "G100"), result.codes())
    }

    @Test
    fun `合并车次串按段给候选`() {
        val result = index("G1956/G1957", "G19").suggest("G19")
        assertEquals(listOf("G19", "G1956", "G1957"), result.codes())
    }

    @Test
    fun `带字母的输入不做数字包含`() {
        // ZG12 数字段也含 12，但整串不以 G1 开头，字母输入不该把它混进来
        val result = index("G1", "ZG12", "G12").suggest("G1")
        assertEquals(listOf("G1", "G12"), result.codes())
    }

    @Test
    fun `非法与重复串不进池`() {
        val built = index("G1", "G1", "G 1", "G1 ", "站", "AB123456", "")
        assertEquals(1, built.size)
        assertEquals(listOf("G1"), built.suggest("G").codes())
    }

    @Test
    fun `大小写与空格照常命中`() {
        val result = index("G1", "G102").suggest("  g 1 ")
        assertEquals(listOf("G1", "G102"), result.codes())
        assertEquals(0, result[0].matchStart)
    }

    @Test
    fun `够满上限就不再扫完整库`() {
        val codes = (1..40).map { "G$it" }.toTypedArray()
        val result = index(*codes).suggest("G", limit = 5)
        assertEquals(5, result.size)
        // 池序是短号在前、数字升序，所以先给 G1..G9 再 G10
        assertEquals(listOf("G1", "G2", "G3", "G4", "G5"), result.codes())
    }

    @Test
    fun `空输入不报错`() {
        val built = index("G1")
        assertTrue(built.suggest("").isEmpty())
        assertTrue(built.suggest("   ").isEmpty())
        assertTrue(built.suggest("ZZ").isEmpty())
        assertFalse(built.suggest("G").isEmpty())
    }

    @Test
    fun `同版本复用同一份池子`() {
        val first = TrainSuggestionIndex.of("20260101", listOf("G1", "G2"))
        assertSame(first, TrainSuggestionIndex.of("20260101", listOf("G1", "G2")))

        val upgraded = TrainSuggestionIndex.of("20260201", listOf("G1", "G2", "G3"))
        assertFalse(first === upgraded)
        assertEquals(3, upgraded.size)
    }

    @Test
    fun `peek 命中缓存但绝不建池`() {
        // 界面靠它判「能不能在主线程当场算」：peek 若顺手建池，预热就没意义了
        TrainSuggestionIndex.invalidate()
        assertNull(TrainSuggestionIndex.peek("20260301"))
        val built = TrainSuggestionIndex.of("20260301", listOf("G1", "G2"))
        assertSame(built, TrainSuggestionIndex.peek("20260301"))
        TrainSuggestionIndex.invalidate()
    }

    @Test
    fun `真实库的联想口径`() {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过真实库联想校验", file?.isFile == true)
        val real = OfflineDbParser.parse(file!!.readBytes())
        val index = TrainSuggestionIndex.build(real.trainCodes)
        // 拆段后候选只会比主键表多（合并串一段算一条），少了就是漏收
        assertTrue("池子 ${index.size} 不该小于车次表 ${real.trainCodes.size}", index.size >= real.trainCodes.size)

        // 用户举的例子钉成回归锚点：输一个 1，第一位就是 1461
        val one = index.suggest("1")
        assertEquals("1461", one.first().code)
        assertEquals(0, one.first().matchStart)
        // 候选必须个个查得到：点了候选等于用户自己敲这个号
        assertTrue("有候选取不到车次 ID", one.all { real.trainIdOf(it.code) != null })
        // 中段包含：记不全首位也要能捞出 1461
        assertTrue("输 461 没命中 1461", index.suggest("461").any { it.code == "1461" })
    }

    @Test
    fun `只读车次表的轻量路径与全库解析同一张表`() {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过真实库联想校验", file?.isFile == true)
        // 联想走的是只读 t.i 的轻量路径（OfflineDbRepository.trainCodesSnapshot），
        // 字符集或格式口径一旦与全库解析不一致，候选会静默变成乱码或整批缺失
        val light = ZipInputStream(file!!.inputStream()).use { zin ->
            var hit: ByteArray? = null
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.name.substringAfterLast('/').equals("t.i", ignoreCase = true)) hit = zin.readBytes()
            }
            checkNotNull(hit) { "库里没有 t.i" }
        }
        val codes = OfflineDbParser.parseIndexTable(light, Charsets.ISO_8859_1)
        assertEquals(OfflineDbParser.parse(file.readBytes()).trainCodes, codes)
        assertEquals("1461", TrainSuggestionIndex.build(codes).suggest("1").first().code)
    }
}
