package com.crinfo.app.ui.screens

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinfo.app.R
import com.crinfo.app.ui.components.AppExpandEnter
import com.crinfo.app.ui.components.AppExpandExit
import com.crinfo.app.ui.components.AppScaffold
import com.crinfo.app.ui.components.AppTopBar
import com.crinfo.app.ui.components.appBounceScroll
import com.crinfo.app.ui.components.effect.AboutEffectBackground
import com.crinfo.app.ui.theme.AppText
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PROJECT_URL = "https://github.com/ybhgl/CRInfo"

/**
 * 关于页：1:1 移植参考项目的视觉与交互——
 * 流动渐变 AGSL 背景、material 大图标（aboutHeaderBlur 融合）、三段错峰渐隐、
 * 顶栏标题随滚动渐显、header spacer 把卡片推到下方、卡片走毛玻璃采样。
 *
 * 文案保留 CRInfo 自己的应用信息（应用名 / 版本 / 数据来源 / 项目主页）。
 * 壳层用 [AppScaffold] + [AppTopBar]（forceCollapsed 等价 SmallTopAppBar）；
 * 采样层用 `rememberLayerBackdrop` 自建，header 与卡片的 textureBlur 都留在采样层之外，
 * 避免 RenderNode 自引用。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val behavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // 首个 item 是 header spacer，用它的实测高度把滚动量归一化到 0→1
    val scrollProgress = remember {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex > 0 -> 1f
                else -> {
                    val spacer = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == "aboutHeaderSpacer" }
                    if (spacer != null && spacer.size > 0) {
                        (listState.firstVisibleItemScrollOffset.toFloat() / spacer.size)
                            .coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }
    val collapsed = remember { derivedStateOf { scrollProgress.value == 1f } }
    var headerHeight by remember { mutableStateOf(190.dp) }
    var showSourceDetail by remember { mutableStateOf(false) }
    val version = remember(context) { appVersion(context) }

    val surface = MiuixTheme.colorScheme.surface
    val isDark = surface.luminance() < 0.5f
    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
            )
        }
    }
    val cardBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
                BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
                BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
            )
        }
    }
    // 采样层只记录背景 shader；header 与卡片的 textureBlur 都留在采样层之外的 content 里
    val contentBackdrop = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isRuntimeShaderSupported()
    ) {
        rememberLayerBackdrop {
            drawRect(surface)
            drawContent()
        }
    } else {
        null
    }
    val backgroundModifier = contentBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "关于",
                // 等价参考项目的 SmallTopAppBar：恒折叠，大标题交给下方 material header
                forceCollapsed = true,
                scrollBehavior = behavior,
                titleColor = MiuixTheme.colorScheme.onSurface.copy(
                    alpha = ((scrollProgress.value - 0.35f) / 0.65f).coerceIn(0f, 1f),
                ),
                navigationIcon = { AboutBackButton(onBack) },
            )
        },
        // 未折叠时顶栏完全透明，让流动背景铺到状态栏；滚到底才画顶栏背景
        topBarSurface = collapsed.value,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AboutEffectBackground(
                modifier = Modifier.fillMaxSize(),
                backgroundModifier = backgroundModifier,
                alpha = { 1f - scrollProgress.value },
            ) {
                AboutHeader(
                    versionName = version,
                    contentPadding = padding,
                    scrollProgress = { scrollProgress.value },
                    logoBlend = logoBlend,
                    backdrop = contentBackdrop,
                    onHeightChanged = { headerHeight = with(density) { it.toDp() } },
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .appBounceScroll()
                        .nestedScroll(behavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 16.dp,
                    ),
                    overscrollEffect = null,
                ) {
                    item(key = "aboutHeaderSpacer") {
                        Spacer(Modifier.fillMaxWidth().height(headerHeight + 218.dp))
                    }
                    item(key = "aboutOptions") {
                        Box {
                            Spacer(Modifier.fillParentMaxHeight())
                            Column {
                                AboutCard(contentBackdrop, cardBlend) {
                                    BasicComponent(title = "CRInfo", summary = "国铁信息聚合工具")
                                    BasicComponent(title = "版本", summary = version)
                                }
                                SmallTitle(text = "数据来源")
                                AboutCard(contentBackdrop, cardBlend) {
                                    BasicComponent(
                                        title = "12306 官方接口",
                                        summary = "来自网络公开官方接口",
                                    )
                                    ArrowPreference(
                                        title = "第三方数据源",
                                        summary = if (showSourceDetail) "点击收起详细说明" else "点击查看详细说明",
                                        onClick = { showSourceDetail = !showSourceDetail },
                                    )
                                    AnimatedVisibility(
                                        visible = showSourceDetail,
                                        enter = AppExpandEnter,
                                        exit = AppExpandExit,
                                    ) {
                                        Text(
                                            text = "时刻表、正晚点、车站大屏等信息来自第三方公开数据源，仅作聚合展示，请以 12306 官方信息为准。",
                                            style = AppText.footnote,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        )
                                    }
                                }
                                SmallTitle(text = "开源")
                                AboutCard(contentBackdrop, cardBlend) {
                                    ArrowPreference(
                                        title = "项目主页",
                                        endActions = {
                                            Text(
                                                text = "GitHub",
                                                fontSize = 14.sp,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                            )
                                        },
                                        onClick = {
                                            runCatching { uriHandler.openUri(PROJECT_URL) }
                                                .onFailure {
                                                    Toast.makeText(
                                                        context,
                                                        "未找到可打开链接的应用",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun appVersion(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    "${info.versionName}（${info.versionCode}）"
}.getOrNull() ?: "1.0"

@Composable
private fun AboutBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
    }
}

/** 与参考项目同款毛玻璃卡片：60f 采样模糊 + 主题相关的 blend 色，无 backdrop 时退化为普通卡片。 */
@Composable
private fun AboutCard(
    backdrop: LayerBackdrop?,
    cardBlend: List<BlendColorEntry>,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .then(
                backdrop?.let {
                    Modifier.textureBlur(
                        backdrop = it,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 60f,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = BlurDefaults.blurColors(blendColors = cardBlend),
                    )
                } ?: Modifier,
            ),
        colors = CardDefaults.defaultColors(
            color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
        ),
    ) {
        content()
    }
}

/** material 大图标 + 应用名 + 版本，三段错峰渐隐；图标与应用名用 DstIn 与背景融合。 */
@Composable
private fun AboutHeader(
    versionName: String,
    contentPadding: PaddingValues,
    scrollProgress: () -> Float,
    logoBlend: List<BlendColorEntry>,
    backdrop: LayerBackdrop?,
    onHeightChanged: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                top = contentPadding.calculateTopPadding() + 92.dp,
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            )
            .onSizeChanged { onHeightChanged(it.height) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val iconProgress = ((scrollProgress() - 0.35f) / 0.15f).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .requiredSize(112.dp)
                .graphicsLayer {
                    alpha = 1f - iconProgress
                    scaleX = 1f - iconProgress * 0.05f
                    scaleY = 1f - iconProgress * 0.05f
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_monochrome),
                contentDescription = "CRInfo",
                modifier = Modifier
                    .requiredSize(200.dp)
                    .aboutHeaderBlur(backdrop, 24.dp, 200f, logoBlend),
            )
        }

        val nameProgress = ((scrollProgress() - 0.20f) / 0.15f).coerceIn(0f, 1f)
        Text(
            text = "CRInfo",
            modifier = Modifier
                .padding(top = 12.dp, bottom = 5.dp)
                .graphicsLayer {
                    alpha = 1f - nameProgress
                    scaleX = 1f - nameProgress * 0.05f
                    scaleY = 1f - nameProgress * 0.05f
                }
                .aboutHeaderBlur(backdrop, 16.dp, 150f, logoBlend),
            fontWeight = FontWeight.Bold,
            fontSize = 35.sp,
        )

        val versionProgress = ((scrollProgress() - 0.05f) / 0.15f).coerceIn(0f, 1f)
        Text(
            text = "版本 $versionName",
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = 1f - versionProgress
                    scaleX = 1f - versionProgress * 0.05f
                    scaleY = 1f - versionProgress * 0.05f
                },
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Modifier.aboutHeaderBlur(
    backdrop: LayerBackdrop?,
    cornerRadius: Dp,
    blurRadius: Float,
    logoBlend: List<BlendColorEntry>,
): Modifier = if (backdrop == null) {
    this
} else {
    textureBlur(
        backdrop = backdrop,
        shape = RoundedCornerShape(cornerRadius),
        blurRadius = blurRadius,
        noiseCoefficient = BlurDefaults.NoiseCoefficient,
        colors = BlurColors(blendColors = logoBlend),
        contentBlendMode = BlendMode.DstIn,
    )
}
