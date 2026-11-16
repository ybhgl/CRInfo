package com.crinfo.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import com.crinfo.app.data.model.AppSettings
import com.crinfo.app.data.model.PaletteStyle
import com.crinfo.app.data.model.ThemeColorType
import com.crinfo.app.data.model.ThemeMode
import top.yukonga.miuix.kmp.theme.Colors

/** 换主题时整包配色插值时长（参考 Melox `Theme.kt` 的 600ms）。 */
private const val THEME_COLOR_TRANSITION_MS = 600

/** 只有用户主动改这些字段才做插值；系统深浅切换（跟随系统）直接跟随，避免系统栏与界面脱节。 */
private data class PaletteKey(
    val mode: ThemeMode,
    val monet: Boolean,
    val colorType: ThemeColorType,
    val customSeedHex: String,
    val palette: PaletteStyle,
    val pureBlack: Boolean,
)

private fun paletteKeyOf(settings: AppSettings) = PaletteKey(
    mode = settings.mode,
    monet = settings.monet,
    colorType = settings.colorType,
    customSeedHex = settings.customSeedHex,
    palette = settings.palette,
    pureBlack = settings.pureBlack,
)

/**
 * 用户切换深浅 / 主题色 / 调色板 / 纯黑时，把整套 [Colors] 从上一次配色平滑插值到目标配色。
 *
 * 插值结果只在动画期间写入 `displayed`；结束后置回 null 直接返回目标配色，避免长期持有
 * 一份「会过期的」颜色快照。中途再次切换会从当前插值位置接着走，不会闪回旧色。
 *
 * 两条约束缺一不可：
 * 1. 这里保存的配色必须是 [Colors.copy] 出来的快照。Miuix 外层主题的 colors 是可变实例
 *    （每次组合 `updateColorsFrom` 原地改写），浅色档若直接引用它，「上一轮配色」会随新主题
 *    一起变成目标色，浅→深就退化成深→深、完全没有过渡动画；
 * 2. key 刚变的那一帧要在组合期就返回上一轮配色。动画起点只在 [LaunchedEffect] 里落定，
 *    这里若先返回目标色，就会先画一帧目标色（深→浅表现为整屏闪白，之后才开始插值）。
 */
@Composable
internal fun rememberAnimatedThemeColors(targetColors: Colors, settings: AppSettings): Colors {
    val key = paletteKeyOf(settings)
    var displayed by remember { mutableStateOf<Colors?>(null) }
    var previousKey by remember { mutableStateOf(key) }
    var previousColors by remember { mutableStateOf(targetColors.copy()) }

    val keyChanged = key != previousKey
    val current = if (keyChanged) displayed ?: previousColors else displayed ?: targetColors

    LaunchedEffect(key) {
        if (key == previousKey) {
            displayed = null
            previousColors = targetColors.copy()
            return@LaunchedEffect
        }
        val start = current.copy()
        val target = targetColors.copy()
        displayed = start
        previousKey = key
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = THEME_COLOR_TRANSITION_MS,
                easing = FastOutSlowInEasing,
            ),
        ) { progress, _ ->
            displayed = lerpThemeColors(start, target, progress)
        }
        displayed = null
        previousColors = target
    }
    return current
}

/** 逐字段插值：Colors 每个槽位都是独立 MutableState，必须 copy 出一份新实例，不能原地改。 */
private fun lerpThemeColors(start: Colors, stop: Colors, fraction: Float): Colors = start.copy(
    primary = lerp(start.primary, stop.primary, fraction),
    onPrimary = lerp(start.onPrimary, stop.onPrimary, fraction),
    primaryVariant = lerp(start.primaryVariant, stop.primaryVariant, fraction),
    onPrimaryVariant = lerp(start.onPrimaryVariant, stop.onPrimaryVariant, fraction),
    error = lerp(start.error, stop.error, fraction),
    onError = lerp(start.onError, stop.onError, fraction),
    errorContainer = lerp(start.errorContainer, stop.errorContainer, fraction),
    onErrorContainer = lerp(start.onErrorContainer, stop.onErrorContainer, fraction),
    disabledPrimary = lerp(start.disabledPrimary, stop.disabledPrimary, fraction),
    disabledOnPrimary = lerp(start.disabledOnPrimary, stop.disabledOnPrimary, fraction),
    disabledPrimaryButton = lerp(start.disabledPrimaryButton, stop.disabledPrimaryButton, fraction),
    disabledOnPrimaryButton = lerp(start.disabledOnPrimaryButton, stop.disabledOnPrimaryButton, fraction),
    disabledPrimarySlider = lerp(start.disabledPrimarySlider, stop.disabledPrimarySlider, fraction),
    primaryContainer = lerp(start.primaryContainer, stop.primaryContainer, fraction),
    onPrimaryContainer = lerp(start.onPrimaryContainer, stop.onPrimaryContainer, fraction),
    secondary = lerp(start.secondary, stop.secondary, fraction),
    onSecondary = lerp(start.onSecondary, stop.onSecondary, fraction),
    secondaryVariant = lerp(start.secondaryVariant, stop.secondaryVariant, fraction),
    onSecondaryVariant = lerp(start.onSecondaryVariant, stop.onSecondaryVariant, fraction),
    disabledSecondary = lerp(start.disabledSecondary, stop.disabledSecondary, fraction),
    disabledOnSecondary = lerp(start.disabledOnSecondary, stop.disabledOnSecondary, fraction),
    disabledSecondaryVariant = lerp(start.disabledSecondaryVariant, stop.disabledSecondaryVariant, fraction),
    disabledOnSecondaryVariant = lerp(start.disabledOnSecondaryVariant, stop.disabledOnSecondaryVariant, fraction),
    secondaryContainer = lerp(start.secondaryContainer, stop.secondaryContainer, fraction),
    onSecondaryContainer = lerp(start.onSecondaryContainer, stop.onSecondaryContainer, fraction),
    secondaryContainerVariant = lerp(start.secondaryContainerVariant, stop.secondaryContainerVariant, fraction),
    onSecondaryContainerVariant = lerp(start.onSecondaryContainerVariant, stop.onSecondaryContainerVariant, fraction),
    tertiaryContainer = lerp(start.tertiaryContainer, stop.tertiaryContainer, fraction),
    onTertiaryContainer = lerp(start.onTertiaryContainer, stop.onTertiaryContainer, fraction),
    tertiaryContainerVariant = lerp(start.tertiaryContainerVariant, stop.tertiaryContainerVariant, fraction),
    background = lerp(start.background, stop.background, fraction),
    onBackground = lerp(start.onBackground, stop.onBackground, fraction),
    onBackgroundVariant = lerp(start.onBackgroundVariant, stop.onBackgroundVariant, fraction),
    surface = lerp(start.surface, stop.surface, fraction),
    onSurface = lerp(start.onSurface, stop.onSurface, fraction),
    surfaceVariant = lerp(start.surfaceVariant, stop.surfaceVariant, fraction),
    onSurfaceSecondary = lerp(start.onSurfaceSecondary, stop.onSurfaceSecondary, fraction),
    onSurfaceVariantSummary = lerp(start.onSurfaceVariantSummary, stop.onSurfaceVariantSummary, fraction),
    onSurfaceVariantActions = lerp(start.onSurfaceVariantActions, stop.onSurfaceVariantActions, fraction),
    disabledOnSurface = lerp(start.disabledOnSurface, stop.disabledOnSurface, fraction),
    surfaceContainer = lerp(start.surfaceContainer, stop.surfaceContainer, fraction),
    onSurfaceContainer = lerp(start.onSurfaceContainer, stop.onSurfaceContainer, fraction),
    onSurfaceContainerVariant = lerp(start.onSurfaceContainerVariant, stop.onSurfaceContainerVariant, fraction),
    surfaceContainerHigh = lerp(start.surfaceContainerHigh, stop.surfaceContainerHigh, fraction),
    onSurfaceContainerHigh = lerp(start.onSurfaceContainerHigh, stop.onSurfaceContainerHigh, fraction),
    surfaceContainerHighest = lerp(start.surfaceContainerHighest, stop.surfaceContainerHighest, fraction),
    onSurfaceContainerHighest = lerp(start.onSurfaceContainerHighest, stop.onSurfaceContainerHighest, fraction),
    outline = lerp(start.outline, stop.outline, fraction),
    dividerLine = lerp(start.dividerLine, stop.dividerLine, fraction),
    windowDimming = lerp(start.windowDimming, stop.windowDimming, fraction),
    sliderKeyPoint = lerp(start.sliderKeyPoint, stop.sliderKeyPoint, fraction),
    sliderKeyPointForeground = lerp(start.sliderKeyPointForeground, stop.sliderKeyPointForeground, fraction),
    sliderBackground = lerp(start.sliderBackground, stop.sliderBackground, fraction),
)
