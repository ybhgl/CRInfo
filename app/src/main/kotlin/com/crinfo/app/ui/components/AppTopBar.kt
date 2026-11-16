package com.crinfo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import kotlin.math.abs
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TitleWidthFraction = 0.9f

/**
 * 折叠顶栏。从 CRInfo 工程原样迁移（其上游是 Miuix 0.9.4 `basic/TopAppBar.kt`，
 * Apache-2.0，compose-miuix-ui contributors），**只删掉居中态的 smallSubtitle**：
 * 库版在 `collapsedFraction ≥ 1/3` 时把副标题和标题一起居中显示，而副标题在收缩态就该彻底不存在。
 * 外层挡不住它——副标题可见性由库内 `subtitle.isNotEmpty()` 与小标题 spring 决定，
 * 用空格替换文本会在回弹的最后几帧漏出残影，用 `subtitleColor` 又受库里 50ms 颜色 tween 拖累追不上滚动。
 *
 * 大标题组（标题 + 副标题同一个 Column、共享绘制期读取的 `1 − frac×3` alpha）
 * 与小标题的 spring 都保持库版行为。**升级 Miuix 时这份拷贝需要重新比对。**
 *
 * 第二处与库版的有意差异：`bottomContent` 槽用 `Column` 而非 `Box`——多行槽内内容是兄弟节点，
 * `Box` 会让它们重叠，栏底留白因此永远不生效（CRInfo §11 顶栏条）。
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    // BarSurface 已经把模糊层画好；这里必须用 LocalBarColor（模糊时透明），
    // 否则默认的不透明 surface 会把模糊盖住，看起来「模糊没生效」。
    color: Color = LocalBarColor.current,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    largeTitle: String = title,
    subtitle: String = "",
    subtitleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    /**
     * 强制折叠：大标题整块收起、只留居中小标题，栏高恒为 [TopAppBarDefaults.CollapsedHeight]。
     * 给「内容不可滚动、又要把纵向空间让给下方内容」的页面用（如模拟定位页的地图卡）。
     */
    forceCollapsed: Boolean = false,
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    onTitleClick: (() -> Unit)? = null,
    bottomContent: @Composable () -> Unit = {},
) {
    val actionsRow = @Composable {
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
    AppTopBarLayout(
        title = title,
        color = color,
        titleColor = titleColor,
        largeTitle = largeTitle,
        largeTitleColor = titleColor,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        navigationIcon = navigationIcon,
        actions = actionsRow,
        titlePadding = titlePadding,
        navigationIconPadding = navigationIconPadding,
        actionIconPadding = actionIconPadding,
        scrollBehavior = scrollBehavior,
        forceCollapsed = forceCollapsed,
        modifier = modifier,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        onTitleClick = onTitleClick,
        bottomContent = bottomContent,
    )
}

@Composable
private fun AppTopBarLayout(
    title: String,
    color: Color,
    titleColor: Color,
    largeTitle: String,
    largeTitleColor: Color,
    subtitle: String,
    subtitleColor: Color,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    titlePadding: Dp,
    navigationIconPadding: Dp,
    actionIconPadding: Dp,
    scrollBehavior: ScrollBehavior?,
    forceCollapsed: Boolean = false,
    modifier: Modifier = Modifier,
    defaultWindowInsetsPadding: Boolean = true,
    onTitleClick: (() -> Unit)? = null,
    bottomContent: @Composable () -> Unit = {},
) {
    // 读取留在布局/绘制阶段，滚动时不重组这棵子树
    val scrolledOffset = remember(scrollBehavior) {
        { scrollBehavior?.state?.heightOffset ?: 0f }
    }
    val largeTitleAlpha = remember(scrollBehavior, forceCollapsed) {
        {
            if (forceCollapsed) 0f else {
                val frac = scrollBehavior?.state?.collapsedFraction ?: 0f
                1f - (frac * 3f).coerceIn(0f, 1f)
            }
        }
    }
    val updateHeightOffsetLimit = remember(scrollBehavior) {
        { height: Int ->
            scrollBehavior?.state?.let { state ->
                val limit = -height.toFloat()
                if (state.heightOffsetLimit != limit) state.heightOffsetLimit = limit
            }
            Unit
        }
    }

    val smallTitleVisible by remember(scrollBehavior, forceCollapsed) {
        derivedStateOf {
            if (forceCollapsed) true else {
                scrollBehavior?.state?.let { it.collapsedFraction * 3f >= 1f } ?: false
            }
        }
    }
    val smallTitleAlpha = remember { Animatable(if (smallTitleVisible) 1f else 0f) }
    val smallTitleTranslationY = remember { Animatable(if (smallTitleVisible) 0f else 20f) }

    LaunchedEffect(smallTitleVisible) {
        if (smallTitleVisible) {
            val showSpec = folmeSpring<Float>(damping = 1.0f, response = 0.3f)
            launch { smallTitleAlpha.animateTo(1f, showSpec) }
            launch { smallTitleTranslationY.animateTo(0f, showSpec) }
        } else {
            val hideSpec = folmeSpring<Float>(damping = 1.0f, response = 0.15f)
            launch { smallTitleAlpha.animateTo(0f, hideSpec) }
            launch { smallTitleTranslationY.animateTo(20f, hideSpec) }
        }
    }

    val animatedTitleColor by animateColorAsState(
        targetValue = titleColor,
        animationSpec = tween(durationMillis = 50),
    )
    val animatedLargeTitleColor by animateColorAsState(
        targetValue = largeTitleColor,
        animationSpec = tween(durationMillis = 50),
    )
    val animatedSubtitleColor by animateColorAsState(
        targetValue = subtitleColor,
        animationSpec = tween(durationMillis = 50),
    )
    // 点击只挂在两个标题 Text 上（不是外层 Box），传 null 时完全不挂 clickable 节点；
    // 外扩 4×2dp 内衬并裁圆角，按压层不再紧贴文字边缘
    val titleClickModifier = if (onTitleClick != null) {
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onTitleClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    } else {
        Modifier
    }

    Layout(
        {
            Box(
                Modifier
                    .layoutId("navigationIcon")
                    .padding(start = navigationIconPadding),
            ) {
                navigationIcon()
            }
            Box(
                Modifier
                    .layoutId("title")
                    .padding(horizontal = titlePadding)
                    .graphicsLayer {
                        alpha = smallTitleAlpha.value
                        translationY = smallTitleTranslationY.value
                    },
            ) {
                Text(
                    text = title,
                    modifier = titleClickModifier,
                    color = animatedTitleColor,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Medium,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
            Box(
                Modifier
                    .layoutId("actionIcons")
                    .padding(end = actionIconPadding),
            ) {
                actions()
            }
            Box(
                Modifier
                    .layoutId("largeTitle")
                    .padding(top = TopAppBarDefaults.CollapsedHeight)
                    .padding(horizontal = titlePadding)
                    .graphicsLayer { alpha = largeTitleAlpha() },
            ) {
                Column(
                    modifier = Modifier
                        .offset {
                            val v = scrolledOffset()
                            IntOffset(0, v.fastRoundToInt())
                        }
                        .onSizeChanged { updateHeightOffsetLimit(it.height) },
                ) {
                    Text(
                        text = largeTitle,
                        modifier = titleClickModifier,
                        color = animatedLargeTitleColor,
                        style = MiuixTheme.textStyles.title1,
                        fontWeight = FontWeight.Normal,
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            color = animatedSubtitleColor,
                            style = MiuixTheme.textStyles.body2,
                            // 「起点 开往 终点」这类长副标题允许折两行，不截成一行省略
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // 与库版的有意差异：这里必须是 Column。库版用 Box，槽内多个兄弟节点会叠在同一位置，
            // 「内容 + 底部留白」里的 Spacer 直接被压成 0 高度，多行选择器也会互相盖住。
            Column(Modifier.layoutId("bottomContent")) {
                bottomContent()
            }
        },
        modifier = modifier
            .background(color)
            .then(
                if (defaultWindowInsetsPadding) {
                    Modifier
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                } else {
                    Modifier
                },
            )
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures { /* 吃掉点击 */ }
            },
    ) { measurables, constraints ->
        val navigationIconPlaceable =
            measurables
                .fastFirst { it.layoutId == "navigationIcon" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val actionIconsPlaceable =
            measurables
                .fastFirst { it.layoutId == "actionIcons" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val maxTitleWidth =
            if (constraints.maxWidth == Constraints.Infinity) {
                constraints.maxWidth
            } else {
                (constraints.maxWidth - navigationIconPlaceable.width - actionIconsPlaceable.width)
                    .coerceAtLeast(0)
            }
        val titleMaxWidth =
            if (maxTitleWidth == Constraints.Infinity) maxTitleWidth else (maxTitleWidth * TitleWidthFraction).fastRoundToInt()

        val titlePlaceable =
            measurables
                .fastFirst { it.layoutId == "title" }
                .measure(constraints.copy(minWidth = 0, maxWidth = titleMaxWidth, minHeight = 0))

        val largeTitlePlaceable =
            measurables
                .fastFirst { it.layoutId == "largeTitle" }
                .measure(
                    constraints.copy(
                        minWidth = 0,
                        minHeight = 0,
                        maxHeight = Constraints.Infinity,
                    ),
                )

        val bottomContentPlaceable =
            measurables
                .fastFirst { it.layoutId == "bottomContent" }
                .measure(constraints.copy(minWidth = 0, minHeight = 0))

        val collapsedHeight = TopAppBarDefaults.CollapsedHeight.roundToPx()
        // largeTitle Box 顶部有 collapsedHeight 的内边距，要减回去才是纯展开量
        val expansion = (largeTitlePlaceable.height - collapsedHeight).coerceAtLeast(0)
        val barHeight = if (forceCollapsed) {
            collapsedHeight
        } else if (expansion > 0) {
            val offset = scrolledOffset()
            val collapseFraction = if (offset.isNaN()) {
                0f
            } else {
                (abs(offset) / expansion.toFloat()).coerceIn(0f, 1f)
            }
            lerp(
                start = collapsedHeight,
                stop = collapsedHeight + expansion,
                fraction = 1f - collapseFraction,
            )
        } else {
            collapsedHeight
        }

        val verticalCenter = collapsedHeight / 2
        // 沿用库版规则：有副标题时栏底用 SubtitleBottomPadding，否则用 LargeTitleBottomPadding，
        // 保证删掉 smallSubtitle 之后栏的总高与原来一致
        val expandedBottomPadding = if (subtitle.isNotEmpty()) {
            TopAppBarDefaults.SubtitleBottomPadding
        } else {
            TopAppBarDefaults.LargeTitleBottomPadding
        }.roundToPx()
        val contentTop = barHeight + expandedBottomPadding
        val layoutHeight = contentTop + bottomContentPlaceable.height

        layout(constraints.maxWidth, layoutHeight) {
            navigationIconPlaceable.placeRelative(
                x = 0,
                y = verticalCenter - navigationIconPlaceable.height / 2,
            )

            var baseX = (constraints.maxWidth - titlePlaceable.width) / 2
            if (baseX < navigationIconPlaceable.width) {
                baseX += (navigationIconPlaceable.width - baseX)
            } else if (baseX + titlePlaceable.width > constraints.maxWidth - actionIconsPlaceable.width) {
                baseX += ((constraints.maxWidth - actionIconsPlaceable.width) - (baseX + titlePlaceable.width))
            }
            titlePlaceable.placeRelative(
                x = baseX,
                y = verticalCenter - titlePlaceable.height / 2,
            )

            actionIconsPlaceable.placeRelative(
                x = constraints.maxWidth - actionIconsPlaceable.width,
                y = verticalCenter - actionIconsPlaceable.height / 2,
            )

            // 大标题组（标题 + 副标题）整块随 heightOffset 平移，共享同一个 alpha
            largeTitlePlaceable.placeRelative(x = 0, y = 0)

            bottomContentPlaceable.placeRelative(x = 0, y = contentTop)
        }
    }
}
