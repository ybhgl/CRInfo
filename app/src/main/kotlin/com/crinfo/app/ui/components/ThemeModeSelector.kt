package com.crinfo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.crinfo.app.data.model.ThemeMode
import com.crinfo.app.ui.theme.AppText
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val CardCorner = 26.dp
private val PreviewCorner = 20.dp

/**
 * 深浅模式预览选择器：两张手绘预览图 + 主色 squircle 描边表示选中。
 *
 * 版式照参考项目 Melox `ThemeModeSelector`（Miuix 0.9.4），差异只有三处：
 * 1. 用本项目的 [ThemeMode]（System/Light/Dark）与 `systemDark` 直接算生效档位；
 * 2. 宽屏判定用 `screenWidthDp >= 600`，不依赖 Melox 自有的 `usesMiuixSmallTopAppBar()`；
 * 3. 文字样式走 `AppText`，颜色用本项目已有的 `onSurfaceVariantSummary`。
 * 「跟随系统」开关由调用方（外观页）单独提供，职责与 Melox 的 ThemeSettingsScreen 一致。
 */
@Composable
fun ThemeModeSelector(
    mode: ThemeMode,
    systemDark: Boolean,
    onModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val effective = when (mode) {
        ThemeMode.System -> if (systemDark) ThemeMode.Dark else ThemeMode.Light
        else -> mode
    }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeModePreview(
            dark = false,
            wide = wide,
            isSelected = effective == ThemeMode.Light,
            label = "浅色",
            onClick = { onModeChange(ThemeMode.Light) },
            modifier = Modifier.weight(1f),
        )
        ThemeModePreview(
            dark = true,
            wide = wide,
            isSelected = effective == ThemeMode.Dark,
            label = "深色",
            onClick = { onModeChange(ThemeMode.Dark) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ThemeModePreview(
    dark: Boolean,
    wide: Boolean,
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.RadioButton
            selected = isSelected
        },
        cornerRadius = CardCorner,
        insideMargin = PaddingValues(vertical = 6.dp),
        colors = CardDefaults.defaultColors(color = Color.Transparent),
        onClick = onClick,
        showIndication = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .squircleBorder(
                    width = 3.dp,
                    color = if (isSelected) MiuixTheme.colorScheme.primary else Color.Transparent,
                    cornerRadius = CardCorner,
                )
                .padding(6.dp),
        ) {
            ThemeModeIllustration(dark = dark, wide = wide)
        }
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = if (isSelected) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = AppText.body,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

/** 纯 Canvas 手绘的界面缩略图：渐变底 + 两块内容面板 + 若干文字占位条。 */
@Composable
private fun ThemeModeIllustration(
    dark: Boolean,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.fillMaxWidth().aspectRatio(if (wide) 2f else 1f).squircleClip(PreviewCorner),
    ) {
        drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF406EFF), Color(0xFF55B9FA))))
        val illustrationWidth = size.width * if (wide) 0.7f else 1f
        val start = (size.width - illustrationWidth) / 2f
        val unitX = illustrationWidth / 680f
        val unitY = size.height / 680f
        val cornerUnit = minOf(unitX, unitY)
        val panelColor = if (dark) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.2f)
        val lineColor = if (dark) Color.White.copy(alpha = 0.35f) else Color(0xFF285ABC).copy(alpha = 0.6f)
        for (top in listOf(148f, 356f)) {
            drawRoundRect(
                color = panelColor,
                topLeft = Offset(start + 48f * unitX, top * unitY),
                size = Size(584f * unitX, 176f * unitY),
                cornerRadius = CornerRadius(44f * cornerUnit),
            )
            for ((offset, width) in listOf(52f to 460f, 100f to 312f)) {
                drawRoundRect(
                    color = lineColor,
                    topLeft = Offset(start + 112f * unitX, (top + offset) * unitY),
                    size = Size(width * unitX, 24f * unitY),
                    cornerRadius = CornerRadius(12f * cornerUnit),
                )
            }
        }
    }
}
