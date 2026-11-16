package com.crinfo.app.data.model

import androidx.compose.ui.graphics.Color

/** 深浅偏好。界面沿用参考项目的「深色主题：跟随系统/总是开启/总是关闭」句式。 */
enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("总是关闭"),
    Dark("总是开启"),
}

/** 预设取色板，hex 为 6 位种子色（不含 #）；Default 表示用 Miuix 内置配色。 */
enum class ThemeColorType(val label: String, val hex: String?) {
    Default("默认", null),
    Red("红色", "D1554F"),
    Pink("粉色", "E2497C"),
    Purple("紫色", "9368E8"),
    Indigo("靛紫", "6E7DF0"),
    Blue("蓝色", "4E77F5"),
    LightBlue("浅蓝", "4DA4E6"),
    Cyan("青色", "26B2BF"),
    Teal("蓝绿", "2FA883"),
    Green("绿色", "4CAF50"),
    LightGreen("浅绿", "7BC65C"),
    Lime("黄绿", "AFB843"),
    Yellow("黄色", "F7C245"),
    Amber("琥珀", "F4A13E"),
    Orange("橙色", "E98548"),
    DeepOrange("深橙", "E25F47"),
    Brown("棕色", "A07963"),
    BlueGrey("蓝灰", "7589A0"),
    Sakura("樱花", "F595A8"),
    Custom("自定义", null),
}

/** 种子色生成配色时的调色板风格，与 Miuix ThemePaletteStyle 一一对应。 */
enum class PaletteStyle(val label: String) {
    TonalSpot("色调点"),
    Neutral("中性"),
    Vibrant("鲜艳"),
    Expressive("表现力"),
    Rainbow("彩虹"),
    FruitSalad("水果沙拉"),
    Monochrome("单色"),
    Fidelity("保真"),
    Content("内容"),
}

/**
 * 页面转场 / 预测返回动画样式（参考项目的 `NavigationTransitionStyle`）。
 * MIUIX 是 Miuix 默认手感；AOSP 是完整移植的 AOSP 窗口动画 + cross-activity 返回卡片。
 */
enum class NavigationTransitionStyle(val label: String) {
    MIUIX("MIUI"),
    AOSP("AOSP"),
}

data class AppSettings(
    val mode: ThemeMode = ThemeMode.System,
    /** 系统主题色（Monet 动态取色），出厂默认关：走下方 colorType/palette 静态配色。 */
    val monet: Boolean = false,
    val colorType: ThemeColorType = ThemeColorType.Default,
    /** 自定义种子色的 6 位 hex（不含 #），仅 colorType == Custom 时使用。 */
    val customSeedHex: String = "6650A4",
    val palette: PaletteStyle = PaletteStyle.TonalSpot,
    val pureBlack: Boolean = false,
    val floatingBar: Boolean = false,
    val haptics: Boolean = true,
    /** 顶栏/底栏模糊内容，需 Android 13 及以上，低版本自动不模糊。 */
    val blurBars: Boolean = false,
    /** 渐进顶栏模糊：沿 Y 轴由透明渐变到模糊，仅在 [blurBars] 开启时生效；出厂默认关。 */
    val progressiveBlur: Boolean = false,
    val predictiveBack: Boolean = true,
    /** 转场 / 预测返回样式，默认 MIUI（与参考项目默认值一致）。 */
    val transitionStyle: NavigationTransitionStyle = NavigationTransitionStyle.MIUIX,
    /**
     * 三个查询页各自的「联网查询」开关，**按 Tab 独立记忆**（用户定的口径，不是一枚全局开关）。
     * 默认 false = 只走离线库；关掉且没装库时，点查询会拦下来提示下载。
     */
    val ticketOnline: Boolean = false,
    val trainOnline: Boolean = false,
    val stationOnline: Boolean = false,
)

/** 当前设置实际使用的种子色；null 表示不覆写配色。monet 开启时由系统动态取色接管。 */
fun AppSettings.effectiveSeed(): Color? {
    if (monet) return null
    val hex = when (colorType) {
        ThemeColorType.Custom -> customSeedHex
        ThemeColorType.Default -> return null
        else -> colorType.hex
    }
    val rgb = hex?.toIntOrNull(16) ?: return null
    return Color(0xFF000000L or rgb.toLong())
}
