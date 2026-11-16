package com.crinfo.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crinfo.app.data.model.AppSettings
import top.yukonga.miuix.kmp.basic.Scaffold

/** 页面级偏好，供 AppScaffold 就地决定是否模糊、是否用渐进顶栏模糊。 */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

/** 壳层底栏已占的高度（含手势条内边距），由 CRInfoApp 提供。 */
val LocalShellBottom = staticCompositionLocalOf { 0.dp }

/**
 * 顶栏下沿到页面首个部件的固定间距，全应用统一在这里给。
 *
 * ⚠️ 满幅滚动（`contentPadding` 让列表一直滑到栏下方）的页面要自己减掉它：
 * 这段间隔只在静止位存在，一滚动就随内容走掉，与顶栏 `bottomContent` 尾部的常驻留白叠在一起，
 * 会变成「展开时很大、折叠后很小」。
 */
internal val BarContentGap = 24.dp

/**
 * 页面壳：只管顶栏。每页一张采样层，顶栏画在采样层之外，否则栏模糊自己会形成反馈回路。
 *
 * 系统 insets 在本层算一次：顶部与左右走 `contentWindowInsets`，**底部按「至少让出手势条」兜**——
 * Tab 页的底栏高（[LocalShellBottom]，库件自己已含手势条）本来就更厚，取它；二级页没有底栏，
 * [LocalShellBottom] 是默认 0.dp，早先列表尾行会被手势小白条压住，这里回落到 `navigationBars`。
 * Miuix Scaffold 把内容摆在 (0,0) 满屏、栏叠在其上，所以 content 收到的 padding
 * 要当 `contentPadding` 用，别 `.padding()` 内缩——内容铺到栏下方，模糊与半透明才有东西可采。
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    /**
     * 顶栏自身是否画背景。关于页未折叠时传 false：顶栏完全透明，让流动渐变背景铺到状态栏，
     * 滚到底（折叠）才恢复顶栏背景。
     */
    topBarSurface: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val settings = LocalAppSettings.current
    val backdrop = rememberBarBackdrop(settings.blurBars)
    val shellBottom = LocalShellBottom.current
    val layoutDirection = LocalLayoutDirection.current
    val gestureBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalBarBackdrop provides backdrop) {
        Scaffold(
            modifier = modifier,
            topBar = {
                BarSurface(
                    drawBackground = topBarSurface,
                    // 渐进档只给顶栏：沿 Y 轴由透明渐变到模糊；底栏仍走普通模糊
                    progressive = settings.progressiveBlur,
                ) { topBar() }
            },
            contentWindowInsets = WindowInsets.statusBars
                .union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().then(backdrop?.recordModifier() ?: Modifier)) {
                val bottomBase = padding.calculateBottomPadding() + shellBottom
                content(
                    PaddingValues(
                        top = padding.calculateTopPadding() + BarContentGap,
                        bottom = if (bottomBase > gestureBarBottom) bottomBase else gestureBarBottom,
                        start = padding.calculateLeftPadding(layoutDirection),
                        end = padding.calculateRightPadding(layoutDirection),
                    ),
                )
            }
        }
    }
}
