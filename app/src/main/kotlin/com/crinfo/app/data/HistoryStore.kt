package com.crinfo.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class Route(
    val fromCode: String,
    val fromName: String,
    val toCode: String,
    val toName: String,
) {
    val key: String get() = "$fromCode-$toCode"
    val display: String get() = "$fromName → $toName"
}

private val Context.routeStore by preferencesDataStore(name = "crinfo_routes")

/** 查询历史：按 出发-到达 去重，保留最近 20 条。 */
class HistoryStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Route.serializer())
    private val key = stringPreferencesKey("history")

    val routes: Flow<List<Route>> = context.routeStore.data.map { prefs -> decode(prefs[key]) }

    suspend fun add(route: Route) {
        context.routeStore.edit { prefs ->
            val next = (listOf(route) + decode(prefs[key])).distinctBy { it.key }.take(20)
            prefs[key] = json.encodeToString(serializer, next)
        }
    }

    suspend fun clear() {
        context.routeStore.edit { prefs -> prefs.remove(key) }
    }

    suspend fun remove(route: Route) {
        context.routeStore.edit { prefs ->
            val next = decode(prefs[key]).filterNot { it.key == route.key }
            prefs[key] = json.encodeToString(serializer, next)
        }
    }

    private fun decode(raw: String?): List<Route> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
}

/** 上次选择的出发/到达站，冷启动时恢复。 */
class SelectionStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("selected_route")

    val selected: Flow<Route?> = context.routeStore.data.map { prefs ->
        prefs[key]?.let { raw -> runCatching { json.decodeFromString(Route.serializer(), raw) }.getOrNull() }
    }

    suspend fun save(route: Route) {
        context.routeStore.edit { prefs -> prefs[key] = json.encodeToString(Route.serializer(), route) }
    }

    /** 首帧前同步取值用，避免先渲染默认区间再跳成上次选的。 */
    suspend fun selectedSnapshot(): Route? = selected.first()

    /** 提前把这份 DataStore 的首次读转起来（冷启动只开一次文件，后续快照共享同一结果）。 */
    suspend fun warmUp() {
        context.routeStore.data.first()
    }
}

/** 车次查询与车站查询各自「上次输入的内容」，冷启动时回填首页。 */
class QueryMemoryStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val trainKey = stringPreferencesKey("last_train")
    private val stationKey = stringPreferencesKey("last_station")

    suspend fun lastTrainCode(): String? =
        context.routeStore.data.first()[trainKey]?.takeIf { it.isNotBlank() }

    suspend fun saveTrainCode(code: String) {
        context.routeStore.edit { prefs -> prefs[trainKey] = code }
    }

    suspend fun lastStation(): Station? = context.routeStore.data.first()[stationKey]
        ?.let { raw -> runCatching { json.decodeFromString(Station.serializer(), raw) }.getOrNull() }

    suspend fun saveStation(station: Station) {
        context.routeStore.edit { prefs -> prefs[stationKey] = json.encodeToString(Station.serializer(), station) }
    }
}

/** 车次号查询历史，与车站历史共用同一个 DataStore 文件。 */
class TrainHistoryStore(private val context: Context) {
    private val key = stringPreferencesKey("train_history")

    val codes: Flow<List<String>> = context.routeStore.data.map { prefs ->
        prefs[key]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun add(code: String) {
        context.routeStore.edit { prefs ->
            val next = (listOf(code) + stored(prefs)).distinct().take(20)
            prefs[key] = next.joinToString(",")
        }
    }

    suspend fun clear() {
        context.routeStore.edit { prefs -> prefs.remove(key) }
    }

    suspend fun remove(code: String) {
        context.routeStore.edit { prefs ->
            prefs[key] = stored(prefs).filterNot { it == code }.joinToString(",")
        }
    }

    private fun stored(prefs: Preferences): List<String> =
        prefs[key]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
}

/** 车站查询历史：按电报码去重，保留最近 20 座。 */
class StationHistoryStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Station.serializer())
    private val key = stringPreferencesKey("station_history")

    val stations: Flow<List<Station>> = context.routeStore.data.map { prefs -> decode(prefs[key]) }

    suspend fun add(station: Station) {
        context.routeStore.edit { prefs ->
            val next = (listOf(station) + decode(prefs[key])).distinctBy { it.code }.take(20)
            prefs[key] = json.encodeToString(serializer, next)
        }
    }

    suspend fun clear() {
        context.routeStore.edit { prefs -> prefs.remove(key) }
    }

    suspend fun remove(station: Station) {
        context.routeStore.edit { prefs ->
            val next = decode(prefs[key]).filterNot { it.code == station.code }
            prefs[key] = json.encodeToString(serializer, next)
        }
    }

    private fun decode(raw: String?): List<Station> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
}
