package com.crinfo.app.ui.theme

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.crinfo.app.data.model.AppSettings
import com.crinfo.app.data.model.PaletteStyle
import com.crinfo.app.data.model.ThemeMode
import com.crinfo.app.data.model.effectiveSeed
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/** 深浅 × 是否用 Material You 取色 → Miuix 的六种模式。 */
fun colorSchemeModeOf(settings: AppSettings): ColorSchemeMode = when (settings.mode) {
    ThemeMode.System -> if (settings.monet) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
    ThemeMode.Light -> if (settings.monet) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
    ThemeMode.Dark -> if (settings.monet) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
}

/** 关掉系统取色并选了种子色时，深浅仍走 Monet 模式族——Miuix 只在 Monet 模式下消费 keyColor。 */
fun effectiveColorSchemeModeOf(settings: AppSettings): ColorSchemeMode =
    if (settings.effectiveSeed() != null) {
        when (settings.mode) {
            ThemeMode.System -> ColorSchemeMode.MonetSystem
            ThemeMode.Light -> ColorSchemeMode.MonetLight
            ThemeMode.Dark -> ColorSchemeMode.MonetDark
        }
    } else {
        colorSchemeModeOf(settings)
    }

fun miuixPaletteOf(palette: PaletteStyle): ThemePaletteStyle = when (palette) {
    PaletteStyle.TonalSpot -> ThemePaletteStyle.TonalSpot
    PaletteStyle.Neutral -> ThemePaletteStyle.Neutral
    PaletteStyle.Vibrant -> ThemePaletteStyle.Vibrant
    PaletteStyle.Expressive -> ThemePaletteStyle.Expressive
    PaletteStyle.Rainbow -> ThemePaletteStyle.Rainbow
    PaletteStyle.FruitSalad -> ThemePaletteStyle.FruitSalad
    PaletteStyle.Monochrome -> ThemePaletteStyle.Monochrome
    PaletteStyle.Fidelity -> ThemePaletteStyle.Fidelity
    PaletteStyle.Content -> ThemePaletteStyle.Content
}

/** 用户手动指定浅/深时，配色与系统设置会不一致，系统栏要按 App 自己的深浅来上色。 */
fun resolvesDark(settings: AppSettings, uiMode: Int): Boolean = when (settings.mode) {
    ThemeMode.Dark -> true
    ThemeMode.Light -> false
    ThemeMode.System -> uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}

/** 「纯黑背景」关闭时的深色页面底色：近黑深灰，与卡片 surfaceContainer(#242424) 保留分层。 */
private val OffBlackPage = Color(0xFF101010)

@Composable
fun CRInfoTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit,
) {
    val controller = remember(settings) {
        ThemeController(
            colorSchemeMode = effectiveColorSchemeModeOf(settings),
            keyColor = settings.effectiveSeed(),
            paletteStyle = miuixPaletteOf(settings.palette),
        )
    }
    val context = LocalContext.current
    val dark = resolvesDark(settings, context.resources.configuration.uiMode)
    DisposableEffect(dark) {
        (context as? Activity)?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        onDispose { }
    }
    // 外层保留 controller（Monet 组件默认值），内层只覆写 colors：换主题时整包配色走
    // 600ms 插值，内容树保持稳定（参考 Melox Theme.kt 的双层 MiuixTheme 写法）。
    MiuixTheme(controller = controller) {
        val base = MiuixTheme.colorScheme
        val targetColors = if (dark) {
            // Miuix 的 Scaffold 用 surface 当页面底色（深色默认就是 #000000），background 只给转场用，
            // 两个槽位必须一起覆写，否则「纯黑」开关看不出变化。卡片走 surfaceContainer，仍保分层。
            val page = if (settings.pureBlack) Color.Black else OffBlackPage
            base.copy(surface = page, background = page)
        } else {
            base
        }
        MiuixTheme(
            colors = rememberAnimatedThemeColors(targetColors, settings),
            content = content,
        )
    }
}
