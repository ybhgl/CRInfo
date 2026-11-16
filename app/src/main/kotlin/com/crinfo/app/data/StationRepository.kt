package com.crinfo.app.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

/** 内置 stations.json 的只读索引，支持站名/拼音/简拼/电报码搜索。 */
object StationRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Station.serializer())

    @Volatile
    private var cache: List<Station>? = null
    private val byCode = HashMap<String, Station>()
    private val byName = HashMap<String, Station>()

    fun all(context: Context): List<Station> =
        cache ?: synchronized(this) {
            cache ?: context.applicationContext.assets
                .open("stations.json")
                .buffered()
                .reader().use { json.decodeFromString(serializer, it.readText()) }
                .also {
                    cache = it
                    byCode.clear()
                    byName.clear()
                    it.forEach { s ->
                        byCode[s.code] = s
                        byName[s.name] = s
                    }
                }
        }

    /**
     * 按站名取站。离线库只有站名、没有 12306 车站代码，跨源一律靠站名对齐
     * （实测 3331 个库内站名有 2967 个与本项目站表完全同名，且站名在站表里零重复）。
     */
    fun byName(context: Context, name: String): Station? {
        all(context)
        return byName[name.trim()]
    }

    /** 缓存未加载时索引是空的，所以必须先 all() 再查，统一走带 context 的入口。 */
    fun byCode(context: Context, code: String): Station? {
        all(context)
        return byCode[code.uppercase()]
    }

    /**
     * 一座城市的主要客运车站。城市字段下常有几十个小站（如北京 65 个，含八达岭长城、雁翅），
     * 全部拿去拉板面会打出几十个请求、触发 12306 封 IP 风险，
     * 因此只取「站名以城市名开头」的站，并按名字长度排序让主城站靠前。
     */
    fun cityStations(context: Context, city: String, limit: Int): List<Station> {
        if (city.isBlank()) return emptyList()
        return all(context)
            .filter { it.city == city && it.name.startsWith(city) }
            .sortedWith(compareBy({ it.name.length }, { it.name }))
            .take(limit)
    }

    /** 排序：完全同名 > 电报码 > 站名前缀 > 拼音前缀 > 简拼前缀 > 同城 > 包含命中。 */
    fun rank(stations: List<Station>, query: String, limit: Int = 50): List<Station> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val needle = q.lowercase()
        val scored = ArrayList<Pair<Int, Station>>()
        for (s in stations) {
            val score = when {
                s.name == q -> 0
                s.code.equals(needle, ignoreCase = true) -> 1
                s.name.startsWith(q) -> 2
                s.pinyin.startsWith(needle) -> 3
                s.initials.startsWith(needle) -> 4
                s.city == q -> 5
                s.name.contains(q) -> 6
                s.pinyin.contains(needle) -> 7
                else -> continue
            }
            scored += score to s
        }
        // 同分时短名优先：否则「冠豸山南」会因码位小于「广州南」而压过它
        return scored.sortedWith(
            compareBy({ it.first }, { it.second.name.length }, { it.second.name }),
        )
            .take(limit)
            .map { it.second }
    }

    fun search(context: Context, query: String, limit: Int = 50): List<Station> =
        rank(all(context), query, limit)
}
