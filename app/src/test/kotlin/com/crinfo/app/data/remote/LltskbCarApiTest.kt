package com.crinfo.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LltskbCarApiTest {
    private val entries = listOf(
        LltskbCarApi.Entry("cr400af", "CR400AF型"),
        LltskbCarApi.Entry("cr400af-a", "CR400AF-A型"),
        LltskbCarApi.Entry("cr400bf", "CR400BF型"),
        LltskbCarApi.Entry("cr400bf-a", "CR400BF-A型"),
        LltskbCarApi.Entry("cr200j", "CR200J型"),
        LltskbCarApi.Entry("crh380a", "CRH380A型"),
    )

    @Test
    fun modelKeyStripsUnitNumber() {
        assertEquals("cr400bfa", LltskbCarApi.modelKey("CR400BF-A-5092"))
        assertEquals("cr200jc", LltskbCarApi.modelKey("CR200JC-6053"))
        assertEquals("crh380a", LltskbCarApi.modelKey("CRH380A-2501"))
        assertEquals("cr200j", LltskbCarApi.modelKey("CR200J"))
        assertNull(LltskbCarApi.modelKey("  "))
    }

    @Test
    fun pickEntryPrefersExactThenLongestPrefix() {
        val exact = LltskbCarApi.pickEntry(entries, LltskbCarApi.modelKey("CR400BF-A-5092")!!)
        assertEquals("cr400bf-a", exact?.first?.folder)
        assertEquals(true, exact?.second)

        // CR200J-C 在路路通没有独立目录，退到最长前缀 CR200J。
        val series = LltskbCarApi.pickEntry(entries, LltskbCarApi.modelKey("CR200JC-6053")!!)
        assertEquals("cr200j", series?.first?.folder)
        assertEquals(false, series?.second)

        val base = LltskbCarApi.pickEntry(entries, LltskbCarApi.modelKey("CR400BF-5092")!!)
        assertEquals("cr400bf", base?.first?.folder)
        assertEquals(true, base?.second)
    }

    @Test
    fun indexLinksKeepModelEntriesOnly() {
        val html = """
            <a href="cr200j/index.html">CR200J型动车组</a>
            <a href="crh5g/index.html"><font color=red>CRH5G型</font>(高寒车)</a>
            <a href="../index.html">返回</a>
            <a href="dy.html">插座说明</a>
        """.trimIndent()

        val parsed = LltskbCarApi.parseIndexLinks(html)
        assertEquals(listOf("cr200j", "crh5g"), parsed.map { it.folder })
        assertEquals(listOf("CR200J型动车组", "CRH5G型(高寒车)"), parsed.map { it.label })
    }

    @Test
    fun imageUrlsResolveRelativeAndDedupe() {
        val html = """<img src=1.jpg><img src="2.png"><img src=1.jpg><img src=dy.png>"""
        val parsed = LltskbCarApi.parseImageUrls(
            html,
            "http://wap.lltskb.com/shfw/lcxh/cr200j/index.html",
        )
        assertEquals(
            listOf(
                "http://wap.lltskb.com/shfw/lcxh/cr200j/1.jpg",
                "http://wap.lltskb.com/shfw/lcxh/cr200j/2.png",
                "http://wap.lltskb.com/shfw/lcxh/cr200j/dy.png",
            ),
            parsed,
        )
    }

    @Test
    fun imageItemsKeepCaptionsAndRepeatedCarImage() {
        val pageUrl = "http://wap.lltskb.com/shfw/lcxh/cr200j/cr200j3-a/index.html"
        val html = """
            <table>
              <tr><td bgcolor=#3b99fc><font color="white">1号车(二等) 定员98人</font></td></tr>
              <tr><td><center><img src=0.jpg></center><center><img src=1.jpg></center></td></tr>
              <tr><td bgcolor=#3b99fc><font color="white">2号车(二等座车) 定员98人</font></td></tr>
              <tr><td><center><img src=1.jpg></center></td></tr>
              <tr><td bgcolor=#3b99fc>一等座</td><td bgcolor=#3b99fc>二等座</td></tr>
              <tr>
                <td>一等座: 动车组采用直型车体<center><img src=111.jpg></center></td>
                <td>二等座: 客室采用固定座椅<center><img src=222.jpg></center></td>
              </tr>
            </table>
        """.trimIndent()

        val items = LltskbCarApi.parseImageItems(html, pageUrl)
        assertEquals(5, items.size)
        assertEquals("1号车(二等) 定员98人", items[0].caption)
        assertEquals("1号车(二等) 定员98人", items[1].caption)
        // 同一张 1.jpg 在不同车厢下要各自保留，不能按 URL 去重。
        assertEquals("http://wap.lltskb.com/shfw/lcxh/cr200j/cr200j3-a/1.jpg", items[2].url)
        assertEquals("2号车(二等座车) 定员98人", items[2].caption)
        assertEquals("一等座", items[3].caption)
        assertEquals("二等座", items[4].caption)
    }

    @Test
    fun subtypeLinkResolvesAgainstFamilyPage() {
        // 子车型链接是相对车型页目录的，曾按站点根目录拼接导致 404，这里锁住正确口径。
        assertEquals(
            "http://wap.lltskb.com/shfw/lcxh/cr200j/cr200j3-a/index.html",
            LltskbCarApi.resolveUrl(
                "http://wap.lltskb.com/shfw/lcxh/cr200j/index.html",
                "cr200j3-a/index.html",
            ),
        )
    }

    @Test
    fun modelNameCutAtTypeSuffix() {
        assertEquals("CR200J3-A型", LltskbCarApi.shortName("CR200J3-A型，定员720人,餐车04车"))
        assertEquals("CRH5G型", LltskbCarApi.shortName("CRH5G型(高寒车)"))
    }
}
