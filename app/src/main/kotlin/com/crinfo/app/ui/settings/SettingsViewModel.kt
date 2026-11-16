package com.crinfo.app.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crinfo.app.data.FavoritesStore
import com.crinfo.app.data.HistoryStore
import com.crinfo.app.data.Route
import com.crinfo.app.data.SettingsStore
import com.crinfo.app.data.Station
import com.crinfo.app.data.StationHistoryStore
import com.crinfo.app.data.TrainHistoryStore
import com.crinfo.app.data.blockingSnapshot
import com.crinfo.app.data.model.AppSettings
import com.crinfo.app.data.model.NavigationTransitionStyle
import com.crinfo.app.data.model.PaletteStyle
import com.crinfo.app.data.model.ThemeColorType
import com.crinfo.app.data.model.ThemeMode
import kotlinx.coroutines.launch

/** 设置页的单一入口：外观 + 收藏清单 + 历史清理。 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = SettingsStore(app)
    private val favorites = FavoritesStore(app)
    private val routeHistory = HistoryStore(app)
    private val trainHistory = TrainHistoryStore(app)
    private val stationHistory = StationHistoryStore(app)

    var settings by mutableStateOf(AppSettings())
        private set
    var favRoutes by mutableStateOf<List<Route>>(emptyList())
        private set
    var favStations by mutableStateOf<List<Station>>(emptyList())
        private set
    var favTrains by mutableStateOf<List<String>>(emptyList())
        private set
    var routeHistoryCount by mutableStateOf(0)
        private set
    var trainHistoryCount by mutableStateOf(0)
        private set
    var stationHistoryCount by mutableStateOf(0)
        private set

    init {
        // DataStore 是异步的，若首帧用 AppSettings() 默认值渲染，关掉了系统主题色的用户
        // 会先看到 Monet 配色再跳成自己的配色。VM 在首次组合前构造，这里同步预读一次。
        settings = blockingSnapshot { settingsStore.snapshot() } ?: AppSettings()
        viewModelScope.launch { settingsStore.settings.collect { settings = it } }
        viewModelScope.launch { favorites.routes.collect { favRoutes = it } }
        viewModelScope.launch { favorites.stations.collect { favStations = it } }
        viewModelScope.launch { favorites.trains.collect { favTrains = it } }
        viewModelScope.launch { routeHistory.routes.collect { routeHistoryCount = it.size } }
        viewModelScope.launch { trainHistory.codes.collect { trainHistoryCount = it.size } }
        viewModelScope.launch { stationHistory.stations.collect { stationHistoryCount = it.size } }
    }

    fun setMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setMode(mode) }
    }

    fun setMonet(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setMonet(enabled) }
    }

    fun updateColorType(type: ThemeColorType) {
        viewModelScope.launch { settingsStore.setColorType(type) }
    }

    fun updateCustomSeed(hex: String) {
        viewModelScope.launch { settingsStore.setCustomSeed(hex) }
    }

    fun updatePalette(palette: PaletteStyle) {
        viewModelScope.launch { settingsStore.setPalette(palette) }
    }

    fun updatePureBlack(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setPureBlack(enabled) }
    }

    fun updateFloatingBar(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setFloatingBar(enabled) }
    }

    fun updateHaptics(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setHaptics(enabled) }
    }

    fun updateBlurBars(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setBlurBars(enabled) }
    }

    /** 渐进顶栏模糊：只在 blurBars 开启时有效，但值独立记忆，关掉再开能回到上次的档。 */
    fun updateProgressiveBlur(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setProgressiveBlur(enabled) }
    }

    fun updatePredictiveBack(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setPredictiveBack(enabled) }
    }

    /** MIUI / AOSP 两套页面转场与预测返回手势样式。 */
    fun updateNavigationTransitionStyle(style: NavigationTransitionStyle) {
        viewModelScope.launch { settingsStore.setNavigationTransitionStyle(style) }
    }

    /* 三个查询页的「联网查询」开关：各自独立记忆，都默认关（=只走离线库）。 */

    fun updateTicketOnline(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setTicketOnline(enabled) }
    }

    fun updateTrainOnline(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setTrainOnline(enabled) }
    }

    fun updateStationOnline(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setStationOnline(enabled) }
    }

    fun clearFavRoutes() {
        viewModelScope.launch { favorites.clearRoutes() }
    }

    fun clearFavStations() {
        viewModelScope.launch { favorites.clearStations() }
    }

    fun clearFavTrains() {
        viewModelScope.launch { favorites.clearTrains() }
    }

    fun clearRouteHistory() {
        viewModelScope.launch { routeHistory.clear() }
    }

    fun clearTrainHistory() {
        viewModelScope.launch { trainHistory.clear() }
    }

    fun clearStationHistory() {
        viewModelScope.launch { stationHistory.clear() }
    }
}
