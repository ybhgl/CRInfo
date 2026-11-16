package com.crinfo.app.ui.theme

import android.content.res.Configuration
import com.crinfo.app.data.model.AppSettings
import com.crinfo.app.data.model.PaletteStyle
import com.crinfo.app.data.model.ThemeColorType
import com.crinfo.app.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/** 主题设置到 Miuix 配色模式、再到系统栏前景色的映射，六格都要对上，否则会出现「深色页面配浅色状态栏图标」。 */
class ThemeSettingTest {

    @Test
    fun `monet on maps to the monet modes`() {
        assertEquals(ColorSchemeMode.MonetSystem, colorSchemeModeOf(AppSettings(ThemeMode.System, monet = true)))
        assertEquals(ColorSchemeMode.MonetLight, colorSchemeModeOf(AppSettings(ThemeMode.Light, monet = true)))
        assertEquals(ColorSchemeMode.MonetDark, colorSchemeModeOf(AppSettings(ThemeMode.Dark, monet = true)))
    }

    @Test
    fun `monet off falls back to miuix built-in palettes`() {
        assertEquals(ColorSchemeMode.System, colorSchemeModeOf(AppSettings(ThemeMode.System, monet = false)))
        assertEquals(ColorSchemeMode.Light, colorSchemeModeOf(AppSettings(ThemeMode.Light, monet = false)))
        assertEquals(ColorSchemeMode.Dark, colorSchemeModeOf(AppSettings(ThemeMode.Dark, monet = false)))
    }

    @Test
    fun `forced modes ignore the system night flag`() {
        val night = Configuration.UI_MODE_NIGHT_YES
        val day = Configuration.UI_MODE_NIGHT_NO
        assertTrue(resolvesDark(AppSettings(ThemeMode.Dark, monet = false), day))
        assertFalse(resolvesDark(AppSettings(ThemeMode.Light, monet = false), night))
    }

    @Test
    fun `follow system reads the night flag`() {
        assertTrue(resolvesDark(AppSettings(ThemeMode.System), Configuration.UI_MODE_NIGHT_YES))
        assertFalse(resolvesDark(AppSettings(ThemeMode.System), Configuration.UI_MODE_NIGHT_NO))
    }

    @Test
    fun `seed color keeps monet mode family so keyColor is consumed`() {
        val seeded = AppSettings(ThemeMode.Dark, monet = false, colorType = ThemeColorType.Blue)
        assertEquals(ColorSchemeMode.MonetDark, effectiveColorSchemeModeOf(seeded))
        // monet 开启时系统动态色优先，仍走原映射
        assertEquals(
            ColorSchemeMode.MonetSystem,
            effectiveColorSchemeModeOf(AppSettings(ThemeMode.System, monet = true, colorType = ThemeColorType.Blue)),
        )
        assertEquals(
            ColorSchemeMode.Light,
            effectiveColorSchemeModeOf(AppSettings(ThemeMode.Light, monet = false)),
        )
    }

    @Test
    fun `palette maps one-to-one onto miuix styles`() {
        assertEquals(ThemePaletteStyle.TonalSpot, miuixPaletteOf(PaletteStyle.TonalSpot))
        assertEquals(ThemePaletteStyle.FruitSalad, miuixPaletteOf(PaletteStyle.FruitSalad))
        assertEquals(ThemePaletteStyle.Content, miuixPaletteOf(PaletteStyle.Content))
        assertEquals(ThemePaletteStyle.entries.size, PaletteStyle.entries.size)
    }
}
