package com.crinfo.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 项目自有图标。底栏四个取自用户提供的 iconfont SVG（home/tickets/train/setting），
 * viewBox 与原始文件一致，勿手改；`FilterAltOff`/`GppMaybe` 是 Material Icons round 版，
 * 数字已按空格拆开（原串有 `.998.998` 这类 SVG 紧凑写法，PathParser 解析不稳）；
 * `CloudQueue`/`CloudOff` 是 Material Icons 标准版（详情页顶栏联网/离线开关）。
 */
object AppIcons {
    val Ticket: ImageVector by lazy {
        build(
            name = "Ticket",
            viewportWidth = 1024f,
            viewportHeight = 1024f,
            paths = listOf(
                "M139.465143 305.316571L435.419429 98.084571a73.142857 73.142857 0 0 1 101.851428 17.974858l46.683429 66.633142c0.676571 0.987429 1.334857 1.993143 1.956571 2.998858L139.465143 305.316571z",
                "M223.195429 642.56a171.300571 171.300571 0 0 1-52.205715 172.891429l14.464 53.997714c2.742857 10.148571 13.165714 16.182857 23.314286 13.458286l735.451429-197.065143c10.148571-2.724571 16.182857-13.165714 13.458285-23.314286l-14.445714-53.979429a171.318857 171.318857 0 0 1-131.657143-123.629714 171.300571 171.300571 0 0 1 52.187429-172.891428l-14.464-53.997715a19.035429 19.035429 0 0 0-23.314286-13.458285L90.532571 441.636571c-10.148571 2.724571-16.182857 13.165714-13.458285 23.314286l14.464 53.997714a171.300571 171.300571 0 0 1 131.657143 123.611429zM19.017143 470.125714A73.142857 73.142857 0 0 1 70.765714 380.525714L812.562286 181.76a73.142857 73.142857 0 0 1 89.6 51.712l21.046857 78.592a58.514286 58.514286 0 0 1-20.516572 61.257143 91.446857 91.446857 0 0 0 44.507429 162.797714 56.265143 56.265143 0 0 1 47.085714 41.234286l21.449143 80.018286a73.142857 73.142857 0 0 1-51.712 89.6L222.171429 945.737143a73.142857 73.142857 0 0 1-89.563429-51.730286l-20.169143-75.282286a63.707429 63.707429 0 0 1 21.211429-65.828571 91.446857 91.446857 0 0 0-43.337143-161.133714 63.268571 63.268571 0 0 1-51.035429-46.08L19.017143 470.107429z m668.982857-46.738285a54.857143 54.857143 0 1 1-28.397714-105.984 54.857143 54.857143 0 0 1 28.397714 105.984z m37.851429 141.293714a54.857143 54.857143 0 1 1-28.379429-105.965714 54.857143 54.857143 0 0 1 28.397714 105.965714z m37.869714 141.312a54.857143 54.857143 0 1 1-28.397714-105.984 54.857143 54.857143 0 0 1 28.397714 105.984z",
            ),
            scale = 0.92f,
        )
    }

    val Train: ImageVector by lazy {
        build(
            name = "Train",
            viewportWidth = 1024f,
            viewportHeight = 1024f,
            paths = listOf(
                "M512 640c-17.64352 0-32 14.35648-32 32s14.35648 32 32 32 32-14.35648 32-32S529.64352 640 512 640z m0-64c53.0176 0 96 42.9824 96 96S565.0176 768 512 768s-96-42.9824-96-96 42.9824-96 96-96zM704 0c130.00192 0 256 85.9648 256 192v512c0 94.336-101.30944 172.77952-213.80096 188.94336l110.5664 110.57152c7.55712 7.56224 2.20672 20.48512-8.48384 20.48512h-51.59424c-5.48352 0-13.09184-3.1488-16.96768-7.02976L658.74432 896H365.25568l-120.97536 120.97536a23.99744 23.99744 0 0 1-16.96768 7.02976H175.7184c-10.69056 0-16.04608-12.928-8.48384-20.48512l110.57152-110.57152C165.31968 876.78464 64.00512 798.34112 64.00512 704.00512v-512c0-106.0352 128-192 256-192h384zM128 256v192h768V256h-768zM320 64c-96.896 0-192 63.40096-192 128h768c0-52.23936-75.52512-128-192-128H320z m384 768c96.896 0 192-63.40096 192-128V512h-768v192c0 64.59904 95.104 128 192 128h384z",
            ),
            scale = 0.86f,
        )
    }

    val Home: ImageVector by lazy {
        build(
            name = "Home",
            viewportWidth = 1025f,
            viewportHeight = 1024f,
            paths = listOf(
                "M992.090881 443.129l-388.66-404.16C579.320881 13.934 546.998881 0.023 512.424881 0.023s-66.896 13.776-91.006 38.946L32.889881 443.129c-40.138 41.728-34.972 77.759-28.348 92.86 4.769 10.731 20.665 40.005 66.63 40.005l56.961 0 0 310.902c0 70.604 50.736 137.104 122.797 137.104l165.454 0L416.383881 694.949c0-35.236-5.298-54.974 30.733-54.974l130.745 0c36.031 0 30.733 19.605 30.733 54.974L608.594881 1024l165.321 0c72.063 0 122.797-66.499 122.797-137.104L896.712881 575.994l56.961 0c45.966 0 61.862-29.407 66.63-40.005C1026.927881 520.888 1032.094881 484.856 992.090881 443.129L992.090881 443.129zM953.676881 512.013 832.601881 512.013l0 374.884c0 35.237-22.784 72.99-58.815 72.99L672.446881 959.887 672.446881 694.949c0-70.604-22.652-118.956-94.717-118.956L446.985881 575.993c-72.063 0-94.717 48.352-94.717 118.956l0 265.069L250.929881 960.018c-36.031 0-58.816-37.754-58.816-72.99L192.113881 512.145 71.038881 512.145c-1.191 0-2.118-4.371-3.047-4.503 2.253-3.843 6.094-13.646 12.452-20.269l388.66-403.895c11.525-12.187 27.95-19.473 43.848-19.34 15.895-0.132 31.262 7.285 42.919 19.34L944.398881 487.24c6.358 6.623 10.199 16.426 12.32 20.269-0.796 0.132-1.856 4.503-3.047 4.503L953.676881 512.013z",
            ),
            scale = 0.86f,
        )
    }

    val Settings: ImageVector by lazy {
        build(
            name = "Settings",
            viewportWidth = 1024f,
            viewportHeight = 1024f,
            paths = listOf(
                "M812.6976 195.976533L936.021333 409.6a204.8 204.8 0 0 1 0 204.8L812.714667 828.023467a204.8 204.8 0 0 1-177.373867 102.4H388.676267a204.8 204.8 0 0 1-177.373867-102.4L87.978667 614.4a204.8 204.8 0 0 1 0-204.8l123.323733-213.623467a204.8 204.8 0 0 1 177.373867-102.4h246.647466a204.8 204.8 0 0 1 177.373867 102.4z m-59.118933 34.133334a136.533333 136.533333 0 0 0-118.254934-68.266667H388.676267a136.533333 136.533333 0 0 0-118.254934 68.266667L147.114667 443.733333a136.533333 136.533333 0 0 0 0 136.533334l123.323733 213.623466a136.533333 136.533333 0 0 0 118.254933 68.266667h246.647467a136.533333 136.533333 0 0 0 118.254933-68.266667L876.885333 580.266667a136.533333 136.533333 0 0 0 0-136.533334l-123.323733-213.623466z",
                "M512 682.666667c94.2592 0 170.666667-76.407467 170.666667-170.666667s-76.407467-170.666667-170.666667-170.666667-170.666667 76.407467-170.666667 170.666667 76.407467 170.666667 170.666667 170.666667z m0-68.266667a102.4 102.4 0 1 1 0-204.8 102.4 102.4 0 0 1 0 204.8z",
            ),
        )
    }

    /** Material Icons round「筛选已关」，用作筛选结果为空的提示图标。 */
    val FilterAltOff: ImageVector by lazy {
        build(
            name = "FilterAltOff",
            viewportWidth = 24f,
            viewportHeight = 24f,
            paths = listOf(
                "M 19.79 5.61 A .998 .998 0 0 0 19 4 H 6.83 l 7.97 7.97 l 4.99 -6.36 z m .7 14.88 L 3.51 3.51 A .996 .996 0 1 0 2.1 4.92 L 10 13 v 5 c 0 1.1 .9 2 2 2 s 2 -.9 2 -2 v -1.17 l 5.07 5.07 c .39 .39 1.02 .39 1.41 0 s .4 -1.02 .01 -1.41 z",
            ),
        )
    }

    /** Material Icons round「盾牌问号」，用作非筛选类空结果（当日确无数据）的提示图标。 */
    val GppMaybe: ImageVector by lazy {
        build(
            name = "GppMaybe",
            viewportWidth = 24f,
            viewportHeight = 24f,
            paths = listOf(
                "M 11.3 2.26 l -6 2.25 C 4.52 4.81 4 5.55 4 6.39 v 4.7 c 0 5.05 3.41 9.76 8 10.91 c 4.59 -1.15 8 -5.86 8 -10.91 v -4.7 a 2 2 0 0 0 -1.3 -1.87 l -6 -2.25 c -.45 -.18 -.95 -.18 -1.4 -.01 z M 12 16 c -.55 0 -1 -.45 -1 -1 s .45 -1 1 -1 s 1 .45 1 1 s -.45 1 -1 1 z m 0 -4 c -.55 0 -1 -.45 -1 -1 V 8 c 0 -.55 .45 -1 1 -1 s 1 .45 1 1 v 3 c 0 .55 -.45 1 -1 1 z",
            ),
        )
    }

    /** Material Icons「空心云」，联网态下的顶栏按钮图标：点击切到离线。 */
    val CloudQueue: ImageVector by lazy {
        build(
            name = "CloudQueue",
            viewportWidth = 24f,
            viewportHeight = 24f,
            paths = listOf(
                "M 19.35 10.04 C 18.67 6.59 15.64 4 12 4 c -2.89 0 -5.4 1.64 -6.65 4.04 C 2.34 8.36 0 10.91 0 14 c 0 3.31 2.69 6 6 6 h 13 c 2.76 0 5 -2.24 5 -5 c 0 -2.64 -2.05 -4.78 -4.65 -4.96 z M 19 18 H 6 c -2.21 0 -4 -1.79 -4 -4 s 1.79 -4 4 -4 h .71 C 7.37 7.69 9.48 6 12 6 c 3.04 0 5.5 2.46 5.5 5.5 v .5 H 19 c 1.66 0 3 1.34 3 3 s -1.34 3 -3 3 z",
            ),
        )
    }

    /** Material Icons「划掉的云」，离线态下的顶栏按钮图标：点击切到联网。 */
    val CloudOff: ImageVector by lazy {
        build(
            name = "CloudOff",
            viewportWidth = 24f,
            viewportHeight = 24f,
            paths = listOf(
                "M 24 15 c 0 -2.64 -2.05 -4.78 -4.65 -4.96 C 18.67 6.59 15.64 4 12 4 c -1.33 0 -2.57 .36 -3.65 .97 l 1.49 1.49 C 10.51 6.17 11.23 6 12 6 c 3.04 0 5.5 2.46 5.5 5.5 v .5 H 19 c 1.66 0 3 1.34 3 3 c 0 .99 -.48 1.85 -1.21 2.4 l 1.41 1.41 c 1.09 -.92 1.8 -2.27 1.8 -3.81 z",
                "M 3.71 4.56 c -.39 .39 -.39 1.02 0 1.41 l 2.06 2.06 h -.42 c -3.28 .35 -5.76 3.34 -5.29 6.79 C .46 17.84 3.19 20 6.22 20 h 11.51 l 1.29 1.29 c .39 .39 1.02 .39 1.41 0 c .39 -.39 .39 -1.02 0 -1.41 L 5.12 4.56 c -.39 -.39 -1.02 -.39 -1.41 0 z",
                "M 6 18 c -2.21 0 -4 -1.79 -4 -4 s 1.79 -4 4 -4 h 1.73 l 8 8 H 6 z",
            ),
        )
    }

    // ImageVector.Builder.addPath 只收 List<PathNode>，SVG 字符串要先过 PathParser。
    // iconfont 图形普遍贴满 viewBox，直接放进 24dp 画布会比 Material 系图标显大；
    // scale 按视口中心等比收拢节点坐标，补出与 Material 相当的安全边距
    private fun build(
        name: String,
        viewportWidth: Float,
        viewportHeight: Float,
        paths: List<String>,
        scale: Float = 1f,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    ).apply {
        paths.forEach { data ->
            val nodes = PathParser().parsePathString(data).toNodes()
            addPath(
                pathData = if (scale == 1f) nodes else scaleNodes(nodes, scale, viewportWidth / 2f, viewportHeight / 2f),
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                pathFillType = PathFillType.NonZero,
            )
        }
    }.build()

    /** 绝对节点按 (v − 中心) × scale 换算，相对节点与圆弧半径只乘比例。 */
    private fun scaleNodes(nodes: List<PathNode>, scale: Float, cx: Float, cy: Float): List<PathNode> {
        fun px(v: Float) = cx + (v - cx) * scale
        fun py(v: Float) = cy + (v - cy) * scale
        return nodes.map { node ->
            when (node) {
                is PathNode.MoveTo -> PathNode.MoveTo(px(node.x), py(node.y))
                is PathNode.RelativeMoveTo -> PathNode.RelativeMoveTo(node.dx * scale, node.dy * scale)
                is PathNode.LineTo -> PathNode.LineTo(px(node.x), py(node.y))
                is PathNode.RelativeLineTo -> PathNode.RelativeLineTo(node.dx * scale, node.dy * scale)
                is PathNode.HorizontalTo -> PathNode.HorizontalTo(px(node.x))
                is PathNode.RelativeHorizontalTo -> PathNode.RelativeHorizontalTo(node.dx * scale)
                is PathNode.VerticalTo -> PathNode.VerticalTo(py(node.y))
                is PathNode.RelativeVerticalTo -> PathNode.RelativeVerticalTo(node.dy * scale)
                is PathNode.CurveTo -> PathNode.CurveTo(
                    px(node.x1), py(node.y1), px(node.x2), py(node.y2), px(node.x3), py(node.y3),
                )
                is PathNode.RelativeCurveTo -> PathNode.RelativeCurveTo(
                    node.dx1 * scale, node.dy1 * scale, node.dx2 * scale, node.dy2 * scale, node.dx3 * scale, node.dy3 * scale,
                )
                is PathNode.QuadTo -> PathNode.QuadTo(px(node.x1), py(node.y1), px(node.x2), py(node.y2))
                is PathNode.RelativeQuadTo -> PathNode.RelativeQuadTo(
                    node.dx1 * scale, node.dy1 * scale, node.dx2 * scale, node.dy2 * scale,
                )
                is PathNode.ReflectiveCurveTo -> PathNode.ReflectiveCurveTo(
                    px(node.x1), py(node.y1), px(node.x2), py(node.y2),
                )
                is PathNode.RelativeReflectiveCurveTo -> PathNode.RelativeReflectiveCurveTo(
                    node.dx1 * scale, node.dy1 * scale, node.dx2 * scale, node.dy2 * scale,
                )
                is PathNode.ReflectiveQuadTo -> PathNode.ReflectiveQuadTo(px(node.x), py(node.y))
                is PathNode.RelativeReflectiveQuadTo -> PathNode.RelativeReflectiveQuadTo(node.dx * scale, node.dy * scale)
                is PathNode.ArcTo -> PathNode.ArcTo(
                    node.horizontalEllipseRadius * scale, node.verticalEllipseRadius * scale, node.theta,
                    node.isMoreThanHalf, node.isPositiveArc, px(node.arcStartX), py(node.arcStartY),
                )
                is PathNode.RelativeArcTo -> PathNode.RelativeArcTo(
                    node.horizontalEllipseRadius * scale, node.verticalEllipseRadius * scale, node.theta,
                    node.isMoreThanHalf, node.isPositiveArc, node.arcStartDx * scale, node.arcStartDy * scale,
                )
                is PathNode.Close -> node
            }
        }
    }
}
