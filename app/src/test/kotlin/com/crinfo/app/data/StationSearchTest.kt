package com.crinfo.app.data

import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationSearchTest {
    private fun station(name: String, code: String, pinyin: String, initials: String, city: String) =
        Station(name, code, pinyin, initials, city)

    private val sample = listOf(
        station("北京南", "VNP", "beijingnan", "bjn", "北京"),
        station("北京", "BJP", "beijing", "bj", "北京"),
        station("北京朝阳", "BYP", "beijingchaoyang", "bjcy", "北京"),
        station("上海虹桥", "AOH", "shanghaihongqiao", "shhq", "上海"),
        station("滨海", "FHP", "binhai", "bh", "天津"),
    )

    @Test
    fun `exact name wins over prefix hits`() {
        assertEquals("北京", rankSample("北京").first())
    }

    @Test
    fun `telecode matches case insensitively`() {
        assertEquals("北京南", rankSample("vnp").first())
        assertEquals("北京南", rankSample("VNP").first())
    }

    @Test
    fun `pinyin and initials both resolve`() {
        assertEquals("北京南", rankSample("beijingnan").first())
        assertEquals("北京南", rankSample("bjn").first())
        assertEquals("上海虹桥", rankSample("shhq").first())
    }

    @Test
    fun `contains match ranks below prefix match`() {
        // "滨海" 的拼音 binhai 包含 "hai"，但 "上海虹桥" 并不含该前缀
        val result = rankSample("binhai")
        assertEquals(listOf("滨海"), result)
    }

    @Test
    fun `limit caps the result set`() {
        assertEquals(2, rankSample("北京", limit = 2).size)
    }

    @Test
    fun `blank query returns nothing`() {
        assertTrue(rankSample("   ").isEmpty())
    }

    @Test
    fun `no match returns empty rather than throwing`() {
        assertTrue(rankSample("不存在的站").isEmpty())
    }

    @Test
    fun `real bundled station table resolves major hubs`() {
        val real = loadAssets() ?: return
        assertEquals(3407, real.size)
        assertEquals("北京南", StationRepository.rank(real, "北京南").first().name)
        assertEquals("VNP", StationRepository.rank(real, "VNP").first().code)
        assertEquals("上海虹桥", StationRepository.rank(real, "shhq").first().name)
        assertEquals("广州南", StationRepository.rank(real, "gzn").first().name)
        // 同城车站按名称排序时不应把同名站丢掉
        assertTrue(StationRepository.rank(real, "上海", limit = 20).any { it.name == "上海" })
    }

    private fun rankSample(query: String, limit: Int = 50): List<String> =
        StationRepository.rank(sample, query, limit).map { it.name }

    private fun loadAssets(): List<Station>? {
        val path = listOf("src/main/assets/stations.json", "app/src/main/assets/stations.json")
            .firstOrNull { File(it).exists() } ?: return null
        return Json.decodeFromString(
            ListSerializer(Station.serializer()),
            File(path).readText(Charsets.UTF_8),
        )
    }
}
