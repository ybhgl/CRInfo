package com.crinfo.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 应用字号表：全项目文本只从这里取字号，不再散写 `fontSize = N.sp`。
 *
 * 能对上 Miuix `textStyles` 档位的直接用库档位；Miuix 0.9.4 缺 8 / 12 / 15 / 22 四档，
 * 这四档以最接近的库档位为基底 + 显式覆盖 `fontSize`：观感与改造前逐像素一致，
 * 但「字号从哪来」收敛到一处——以后整体调排版只改这个文件。
 *
 * 用法：`Text(text = ..., style = AppText.body)`。
 * 注意 `style` 会被显式传的 `fontWeight` / `lineHeight` 参数覆盖（Compose 的参数优先级），
 * 所以原来「字号 + 中等字重」的组合不需要改写法。
 */
object AppText {

    /** 8sp：时间轴巴士数量角标（13dp 圆内只放得下这个字号）。 */
    val badge: TextStyle
        @Composable get() = MiuixTheme.textStyles.footnote2.copy(fontSize = 8.sp)

    /** 9sp：时间轴起终点圆标里的「始 / 终」（18dp 圆内，比角标大一档刚好填满）。 */
    val endMark: TextStyle
        @Composable get() = MiuixTheme.textStyles.footnote2.copy(fontSize = 9.sp)

    /** 11sp：来源标签、时刻表「已过」等最次要的标记（= Miuix footnote2）。 */
    val caption: TextStyle
        @Composable get() = MiuixTheme.textStyles.footnote2

    /** 12sp：脚注小字（首末班/票价那类）。 */
    val footnote: TextStyle
        @Composable get() = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp)

    /** 13sp：辅助说明、副行（= Miuix footnote1）。 */
    val label: TextStyle
        @Composable get() = MiuixTheme.textStyles.footnote1

    /** 14sp：正文与常规信息（= Miuix body2）。 */
    val body: TextStyle
        @Composable get() = MiuixTheme.textStyles.body2

    /** 15sp：方向条、时间轴站名、空态按钮。 */
    val bodyLarge: TextStyle
        @Composable get() = MiuixTheme.textStyles.body2.copy(fontSize = 15.sp)

    /** 16sp：列表主标题、行内强调（= Miuix headline2）。 */
    val title: TextStyle
        @Composable get() = MiuixTheme.textStyles.headline2

    /** 17sp：收藏卡片标题等（= Miuix main）。 */
    val main: TextStyle
        @Composable get() = MiuixTheme.textStyles.main

    /** 18sp：站牌站级的大号分钟数（= Miuix title4）。 */
    val headline: TextStyle
        @Composable get() = MiuixTheme.textStyles.title4

    /** 22sp：车辆清单的大号 ETA、时刻表班次。 */
    val display: TextStyle
        @Composable get() = MiuixTheme.textStyles.title3.copy(fontSize = 22.sp)

    /** 27sp：附近页站点卡的大号到站读数——卡片内唯一的视觉锚点。 */
    val eta: TextStyle
        @Composable get() = MiuixTheme.textStyles.title3.copy(fontSize = 27.sp)

    /** 24sp：与 ETA 数字同级的文案态（即将进站 / 已进站），不能比数字弱太多。 */
    val etaStatus: TextStyle
        @Composable get() = MiuixTheme.textStyles.title3.copy(fontSize = 24.sp)
}
