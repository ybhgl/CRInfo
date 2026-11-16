package com.crinfo.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `android.ver` 的取值测试。服务端这份文件实测带语法错误
 * （`"name": 应用宝"` 缺前引号），整体 JSON 解析必然失败，所以取值只能按字段抠。
 */
class LltApiVersionTest {
    private val realBody = """
        {
          "version": {
                "app": "5.3.6",
                "desc": "增加车型图片在线查看功能",
                "data": "20261108",
                "minSdk": 19
          },
          "appUrl": [
            "http://down.lltskb.com/lulutong20261108.apk"
          ],
          "dataUrl": [
            "http://223.107.87.50:8011/an.db",
            "http://down.lltskb.com/an.db"
          ],
          "market": [
            {
              "name": "小米",
              "version": { "app": "5.2.8", "data": "20260328", "minSdk": 16 }
            },
            {
              "name": "荣耀",
              "version": { "app": "5.3.6", "data": "20261105", "minSdk": 19 }
            },
            {
              "name": 应用宝",
              "id": "com.tencent.android.qqdownloader"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `从非法 JSON 里取到顶层版本与镜像地址`() {
        val v = LltApi.parseVersion(realBody)
        assertEquals("20261108", v.data)
        assertEquals("增加车型图片在线查看功能", v.desc)
        assertEquals(
            listOf("http://223.107.87.50:8011/an.db", "http://down.lltskb.com/an.db"),
            v.dataUrls,
        )
        assertTrue(v.isValid)
    }

    @Test
    fun `不能读到渠道版本号`() {
        val v = LltApi.parseVersion(realBody)
        assertFalse("误读渠道版本", v.data in listOf("20260328", "20261105"))
    }

    @Test
    fun `缺 dataUrl 时回落到官方直链`() {
        val v = LltApi.parseVersion("""{ "version": { "data": "20261108" } }""")
        assertEquals(listOf("http://down.lltskb.com/an.db"), v.dataUrls)
    }

    @Test
    fun `版本号不是八位日期就判无效`() {
        assertFalse(LltApi.parseVersion("""{ "version": { "data": "2026-11-08" } }""").isValid)
        assertFalse(LltApi.parseVersion("{}").isValid)
    }

    /**
     * 字面 `{` `}` 不转义时，Android 的 ICU 正则会抛 PatternSyntaxException，
     * 而 JVM 上照跑 —— 这条只能在设备启动时暴露，所以把「模式能编译」显式测出来。
     */
    @Test
    fun `所有取值正则都能编译`() {
        listOf(
            Regex("\"version\"\\s*:\\s*\\{(.*?)\\}"),
            Regex("\"dataUrl\"\\s*:\\s*\\[(.*?)\\]"),
            Regex("\"data\"\\s*:\\s*\"([^\"]*)\""),
            Regex("\"desc\"\\s*:\\s*\"([^\"]*)\""),
            Regex("\"(https?://[^\"]+)\""),
        ).forEach { Regex(it.pattern) }
        assertEquals("20261108", LltApi.parseVersion(realBody).data)
    }

    private fun version(data: String) = OfflineVersion(data, "", listOf("http://down.lltskb.com/an.db"))

    /**
     * 比较语义照路路通 `dataNewer()`：**严格更新才提示**。
     * 早先写的是「不相等就提示」，服务器回滚/灰度到旧版本号时会提示"发现新版本"，
     * 点下去直接用旧包覆盖本地新库（降级），版本号写回后还不再自愈。
     */
    @Test
    fun `版本比较只在远端严格更新时放行`() {
        assertTrue(version("20261108").isDataNewer("20261101"))
        assertFalse("版本号相同不该重复下载", version("20261108").isDataNewer("20261108"))
        assertFalse("远端更旧不该降级覆盖", version("20261101").isDataNewer("20261108"))
        assertTrue("跨年也要比得对", version("20270101").isDataNewer("20261231"))
        assertFalse("远端版本号不合规则不提示", version("").isDataNewer("20261108"))
        assertFalse("远端版本号不合规则不提示", version("2026-11-08").isDataNewer("20261108"))
    }

    /** 本机没有库时必须能提示首装 —— 官方不需要这条路，他们的库内置在 APK 的 res/raw 里。 */
    @Test
    fun `无库或本地版本号异常时放行安装`() {
        assertTrue(version("20261108").isDataNewer(null))
        assertTrue(version("20261108").isDataNewer(""))
        assertTrue("本地存了个非日期串（历史脏数据）也要能重装", version("20261108").isDataNewer("unknown"))
    }
}
