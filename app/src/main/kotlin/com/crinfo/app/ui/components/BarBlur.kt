package com.crinfo.app.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 栏的面色：模糊时必须是 Transparent（底下已有模糊层），否则是页面底色。 */
internal val LocalBarColor = staticCompositionLocalOf { Color.Transparent }

/** 当前页面的采样层。栏自己不收在这里，由 AppScaffold 只录内容区。 */
internal val LocalBarBackdrop = staticCompositionLocalOf<BarBackdrop?> { null }

private const val BLUR_MIN_SDK = 33
private const val BLUR_RADIUS = 25f
/** 渐进档：半径更小、底色更透，靠空间渐变而不是随滚动改半径（参考 Melox `MiuixBlur.kt`）。 */
private const val PROGRESSIVE_BLUR_RADIUS = 12f
private const val PROGRESSIVE_SURFACE_ALPHA = 0.3f

/** 把 RenderEffect/AGSL 类型挡在库内，低版本不会碰到。 */
internal interface BarBackdrop {
    fun recordModifier(): Modifier

    /** [progressive] 只给顶栏用；底栏/浮条保持普通模糊，默认参数让既有调用点零改动。 */
    @Composable
    fun blurModifier(shape: Shape, progressive: Boolean = false): Modifier
}

@Composable
internal fun rememberBarBackdrop(enabled: Boolean): BarBackdrop? =
    if (enabled && Build.VERSION.SDK_INT >= BLUR_MIN_SDK) rememberApi33BarBackdrop() else null

@RequiresApi(BLUR_MIN_SDK)
@Composable
private fun rememberApi33BarBackdrop(): BarBackdrop {
    val surface = MiuixTheme.colorScheme.surface
    val layer = rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
    return remember(layer) { Api33BarBackdrop(layer) }
}

@RequiresApi(BLUR_MIN_SDK)
private class Api33BarBackdrop(private val layer: LayerBackdrop) : BarBackdrop {
    override fun recordModifier(): Modifier = Modifier.layerBackdrop(layer)

    @Composable
    override fun blurModifier(shape: Shape, progressive: Boolean): Modifier {
        val surface = MiuixTheme.colorScheme.surface
        return if (progressive) {
            Modifier.progressiveTextureBlur(
                backdrop = layer,
                shape = shape,
                gradient = ProgressiveBlur.Top.copy(
                    startFraction = 0.2f,
                    endFraction = 1f,
                    curve = 3f,
                ),
                blurRadius = PROGRESSIVE_BLUR_RADIUS,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(BlendColorEntry(surface.copy(alpha = PROGRESSIVE_SURFACE_ALPHA))),
                ),
            )
        } else {
            Modifier.textureBlur(
                backdrop = layer,
                shape = shape,
                blurRadius = BLUR_RADIUS,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(BlendColorEntry(surface.copy(alpha = 0.8f))),
                ),
            )
        }
    }
}

/**
 * 采样层可用时先铺模糊，`LocalBarColor` 随之变透明；不可用时铺纯色。
 * [border] 画在模糊层之上——悬浮胶囊需要它才能在任何背景上看清边界。
 */
@Composable
internal fun BarSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    containerColor: Color = MiuixTheme.colorScheme.surface,
    border: BorderStroke? = null,
    /** 背景由外部（如顶栏+页签合并成的一整块）绘制时传 false，只提供透明前景色。 */
    drawBackground: Boolean = true,
    /** 顶栏渐进模糊（空间渐变）；底栏、浮条、提示条一律保持 false。 */
    progressive: Boolean = false,
    content: @Composable () -> Unit,
) {
    val borderModifier = if (border != null) Modifier.border(border, shape) else Modifier
    val backdrop = LocalBarBackdrop.current
    if (!drawBackground) {
        Box(modifier = modifier) {
            CompositionLocalProvider(LocalBarColor provides Color.Transparent, content = content)
        }
    } else if (backdrop != null && Build.VERSION.SDK_INT >= BLUR_MIN_SDK) {
        Box(modifier = modifier.then(backdrop.blurModifier(shape, progressive)).then(borderModifier)) {
            CompositionLocalProvider(LocalBarColor provides Color.Transparent, content = content)
        }
    } else {
        Box(modifier = modifier.background(containerColor, shape).then(borderModifier)) {
            CompositionLocalProvider(LocalBarColor provides containerColor, content = content)
        }
    }
}

/** 给「自己就是那张圆角面板」的悬浮栏用：模糊层直接挂在它身上，没有采样层就原样返回。 */
@Composable
internal fun Modifier.barBlurModifier(backdrop: BarBackdrop?, shape: Shape = RectangleShape): Modifier =
    if (backdrop != null && Build.VERSION.SDK_INT >= BLUR_MIN_SDK) then(backdrop.blurModifier(shape))
    else this
