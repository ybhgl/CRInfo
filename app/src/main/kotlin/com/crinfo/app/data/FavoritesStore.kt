package com.crinfo.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.crinfo.app.data.model.AppSettings
import com.crinfo.app.data.model.NavigationTransitionStyle
import com.crinfo.app.data.model.PaletteStyle
import com.crinfo.app.data.model.ThemeColorType
import com.crinfo.app.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.favoriteStore by preferencesDataStore(name = "crinfo_favorites")
private val Context.settingsStore by preferencesDataStore(name = "crinfo_settings")

/**
 * 收藏：路线 / 车站 / 车次三类分开存，各自去重、上限 30。
 * 界面用「是否已在列表里」决定星标状态，所以这里不提供布尔返回值，避免两处状态打架。
 */
class FavoritesStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val routesKey = stringPreferencesKey("fav_routes")
    private val stationsKey = stringPreferencesKey("fav_stations")
    private val trainsKey = stringPreferencesKey("fav_trains")
    private val routeList = ListSerializer(Route.serializer())
    private val stationList = ListSerializer(Station.serializer())
    private val stringList = ListSerializer(String.serializer())

    val routes: Flow<List<Route>> = context.favoriteStore.data.map { decode(it[routesKey], routeList) }
    val stations: Flow<List<Station>> = context.favoriteStore.data.map { decode(it[stationsKey], stationList) }
    val trains: Flow<List<String>> = context.favoriteStore.data.map { decode(it[trainsKey], stringList) }

    suspend fun toggleRoute(route: Route) = mutate(routesKey, routeList) { list ->
        if (list.any { it.key == route.key }) list.filterNot { it.key == route.key } else list + route
    }

    suspend fun toggleStation(station: Station) = mutate(stationsKey, stationList) { list ->
        if (list.any { it.code == station.code }) list.filterNot { it.code == station.code } else list + station
    }

    suspend fun toggleTrain(code: String) = mutate(trainsKey, stringList) { list ->
        if (code in list) list - code else list + code
    }

    suspend fun clearRoutes() = clear(routesKey)
    suspend fun clearStations() = clear(stationsKey)
    suspend fun clearTrains() = clear(trainsKey)

    private suspend fun <T> mutate(key: Preferences.Key<String>, serializer: KSerializer<List<T>>, change: (List<T>) -> List<T>) {
        context.favoriteStore.edit { prefs ->
            val next = change(decode(prefs[key], serializer)).take(CAP)
            prefs[key] = json.encodeToString(serializer, next)
        }
    }

    private suspend fun clear(key: Preferences.Key<String>) {
        context.favoriteStore.edit { prefs -> prefs.remove(key) }
    }

    private fun <T> decode(raw: String?, serializer: KSerializer<List<T>>): List<T> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    private companion object {
        const val CAP = 30
    }
}

/** 外观设置：深浅、Material You 取色、主题颜色、调色板风格、纯黑、悬浮底栏、触感、模糊、渐进模糊、预测返回、转场样式。 */
class SettingsStore(private val context: Context) {
    private val modeKey = stringPreferencesKey("theme_mode")
    private val monetKey = booleanPreferencesKey("monet")
    private val colorKey = stringPreferencesKey("theme_color")
    private val customSeedKey = stringPreferencesKey("custom_seed_hex")
    private val paletteKey = stringPreferencesKey("palette_style")
    private val pureBlackKey = booleanPreferencesKey("pure_black")
    private val floatingBarKey = booleanPreferencesKey("floating_bar")
    private val hapticsKey = booleanPreferencesKey("haptics")
    private val blurKey = booleanPreferencesKey("blur_bars")
    private val progressiveBlurKey = booleanPreferencesKey("progressive_blur")
    private val predictiveBackKey = booleanPreferencesKey("predictive_back")
    private val transitionStyleKey = stringPreferencesKey("navigation_transition_style")
    private val ticketOnlineKey = booleanPreferencesKey("ticket_online_query")
    private val trainOnlineKey = booleanPreferencesKey("train_online_query")
    private val stationOnlineKey = booleanPreferencesKey("station_online_query")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            mode = resolve(prefs[modeKey]) { n -> ThemeMode.entries.find { it.name == n } } ?: ThemeMode.System,
            monet = prefs[monetKey] ?: true,
            colorType = resolve(prefs[colorKey]) { n -> ThemeColorType.entries.find { it.name == n } } ?: ThemeColorType.Default,
            customSeedHex = prefs[customSeedKey]?.takeIf { it.matches(Regex("^[0-9a-fA-F]{6}$")) } ?: "6650A4",
            palette = resolve(prefs[paletteKey]) { n -> PaletteStyle.entries.find { it.name == n } } ?: PaletteStyle.TonalSpot,
            pureBlack = prefs[pureBlackKey] ?: false,
            floatingBar = prefs[floatingBarKey] ?: false,
            haptics = prefs[hapticsKey] ?: true,
            blurBars = prefs[blurKey] ?: false,
            progressiveBlur = prefs[progressiveBlurKey] ?: true,
            predictiveBack = prefs[predictiveBackKey] ?: true,
            transitionStyle = resolve(prefs[transitionStyleKey]) { n ->
                NavigationTransitionStyle.entries.find { it.name == n }
            } ?: NavigationTransitionStyle.MIUIX,
            ticketOnline = prefs[ticketOnlineKey] ?: false,
            trainOnline = prefs[trainOnlineKey] ?: false,
            stationOnline = prefs[stationOnlineKey] ?: false,
        )
    }

    suspend fun setMode(mode: ThemeMode) {
        context.settingsStore.edit { prefs -> prefs[modeKey] = mode.name }
    }

    suspend fun setMonet(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[monetKey] = enabled }
    }

    suspend fun setColorType(type: ThemeColorType) {
        context.settingsStore.edit { prefs -> prefs[colorKey] = type.name }
    }

    suspend fun setCustomSeed(hex: String) {
        if (!hex.matches(Regex("^[0-9a-fA-F]{6}$"))) return
        context.settingsStore.edit { prefs -> prefs[customSeedKey] = hex.uppercase() }
    }

    suspend fun setPalette(palette: PaletteStyle) {
        context.settingsStore.edit { prefs -> prefs[paletteKey] = palette.name }
    }

    suspend fun setPureBlack(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[pureBlackKey] = enabled }
    }

    suspend fun setFloatingBar(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[floatingBarKey] = enabled }
    }

    suspend fun setHaptics(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[hapticsKey] = enabled }
    }

    suspend fun setBlurBars(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[blurKey] = enabled }
    }

    suspend fun setProgressiveBlur(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[progressiveBlurKey] = enabled }
    }

    suspend fun setPredictiveBack(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[predictiveBackKey] = enabled }
    }

    suspend fun setNavigationTransitionStyle(style: NavigationTransitionStyle) {
        context.settingsStore.edit { prefs -> prefs[transitionStyleKey] = style.name }
    }

    suspend fun setTicketOnline(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[ticketOnlineKey] = enabled }
    }

    suspend fun setTrainOnline(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[trainOnlineKey] = enabled }
    }

    suspend fun setStationOnline(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[stationOnlineKey] = enabled }
    }

    /** 首帧前同步取值用，避免先用默认配色渲染一帧再跳到用户设置。 */
    suspend fun snapshot(): AppSettings = settings.first()
}

private inline fun <T> resolve(name: String?, transform: (String) -> T?): T? = name?.let(transform)
