// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package com.crinfo.app.ui.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSettle
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navDirectionalTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 参考项目 Melox 的 AOSP 窗口动画转场，完整移植（仅改包名）。
 *
 * `fast_out_extra_slow_in` 是两段三次贝塞尔：`M 0,0 C 0.05,0 0.133333,0.06 0.166666,0.4`
 * 再 `C 0.208333,0.82 0.25,1 1,1`。单段近似在中段最多偏差 0.14，会让滑入前沿明显变慢，
 * 所以按子矩形逐段求解再缩放回去。Tab 横向转场也复用这条曲线，保持全项目手感一致。
 */
internal val FastOutExtraSlowIn: Easing = run {
    val knotX = 0.166666f
    val knotY = 0.4f
    val first = CubicBezierEasing(0.05f / knotX, 0f, 0.133333f / knotX, 0.06f / knotY)
    val second = CubicBezierEasing(
        (0.208333f - knotX) / (1f - knotX),
        (0.82f - knotY) / (1f - knotY),
        (0.25f - knotX) / (1f - knotX),
        (1f - knotY) / (1f - knotY),
    )
    Easing { fraction ->
        if (fraction < knotX) {
            knotY * first.transform(fraction / knotX)
        } else {
            knotY + (1f - knotY) * second.transform((fraction - knotX) / (1f - knotX))
        }
    }
}

/** 参考实现提交前的手势插值（先轻微加速再减速）。 */
private val BackGestureEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/** 参考实现 fling 回弹弹簧，作用在 scale-x100 轴。 */
private const val BOUNCE_STIFFNESS = 200f
private const val BOUNCE_DAMPING = 0.75f
private const val BOUNCE_MAX_KICK = 1000f
private const val BOUNCE_MIN_KICK = 120f

/**
 * 提交后回弹的闭式解：欠阻尼弹簧对 commit kick 的响应，乘到两层 scale 上并封顶 1
 * （只会先缩后恢复，不会放大）。kick 保持在 progress-velocity 域，边缘手势翻倍，
 * 上限 1000、近乎瞬时提交时地板 120，保证轻点返回也有轻微回弹。
 */
private fun bounceScale(settle: NavSettle?, gesture: NavGesture?): Float {
    if (settle == null || settle.phase != NavSettlePhase.Commit || gesture == null) return 1f
    val factor = if (gesture.swipeEdge != NavSwipeEdge.None) 2f else 1f
    val floorKick = if (gesture.progress < 0.1f) BOUNCE_MIN_KICK else 0f
    val kick = (abs(settle.releaseVelocity) * 100f * (1f - CROSS_ACTIVITY_MIN_SCALE) * factor)
        .coerceIn(floorKick, BOUNCE_MAX_KICK)
    if (kick <= 0f) return 1f
    val omega = sqrt(BOUNCE_STIFFNESS)
    val omegaD = omega * sqrt(1f - BOUNCE_DAMPING * BOUNCE_DAMPING)
    val t = settle.elapsedMillis / 1000f
    val overlay = -(kick / omegaD) * exp(-BOUNCE_DAMPING * omega * t) * sin(omegaD * t)
    return ((100f + overlay) / 100f).coerceAtMost(1f)
}

/** 提交前对 pop 位移轴做手势整形：卡片前段行程更快；无手势时为恒等映射。 */
private fun shapedTopProgress(p: Float, gesture: NavGesture?): Float =
    if (gesture == null) p else 1f - BackGestureEasing.transform((1f - p).coerceIn(0f, 1f))

/** 无 settle 上下文时的行程回退窗口，把 alpha 斜坡端点放到 450ms 缓动时间线的正确位置。 */
private const val OPEN_FADE_START = 0.12f
private const val OPEN_FADE_SPAN = 0.71f
private const val CLOSE_FADE_START = 0.21f
private const val CLOSE_FADE_SPAN = 0.74f

/** 参考实现的 alpha 轨道：83ms 线性斜坡，打开偏移 50ms / 关闭偏移 35ms。 */
private const val CLASSIC_FADE_DURATION = 83f
private const val OPEN_FADE_OFFSET = 50f
private const val CLOSE_FADE_OFFSET = 35f

/** 程序式窗口动画：固定 450ms 缓动 tween。 */
private val ClassicMotion = NavMotion(
    programmatic = NavSettleSpec.Tween(durationMillis = 450, easing = FastOutExtraSlowIn),
)

/**
 * 经典 activity open（push）：新页从 [CrossActivityDrift] 处滑入并在自己的 83ms 时钟轨道淡入；
 * 被覆盖页同距离向左滑出且全程不透明。不加遮罩、不缩放——卡片效果只属于手势分支。
 */
private val ClassicActivityOpen: NavTransition = navGraphicsTransition(
    motion = ClassicMotion,
    scrim = { 0f },
) { scope ->
    val d = scope.relativeDepth
    val driftPx = with(scope.density) { CrossActivityDrift.toPx() }
    if (d <= 0f) {
        val p = topProgress(d)
        // 像素对齐，避免圆角裁切边缘在小数 X 上闪动
        translationX = ((1f - p) * driftPx).fastRoundToInt().toFloat()
        // alpha 斜坡只作用于正在进入的窗口；按角色门控，否则 push 的最初几帧会误伤顶层
        alpha = if (scope.role == NavRole.Incoming) {
            val settle = scope.settle
            if (settle != null) {
                ((settle.elapsedMillis - OPEN_FADE_OFFSET) / CLASSIC_FADE_DURATION).coerceIn(0f, 1f)
            } else {
                ((p - OPEN_FADE_START) / OPEN_FADE_SPAN).coerceIn(0f, 1f)
            }
        } else {
            1f
        }
    } else {
        translationX = (-coverProgress(d) * driftPx).fastRoundToInt().toFloat()
    }
}

/** 经典 activity close（pop）：离开页向右滑出并快速淡出，露出的下层从左侧归位、全程不透明。 */
private val ClassicActivityClose: NavTransition = navGraphicsTransition(
    motion = ClassicMotion,
    scrim = { 0f },
) { scope ->
    val d = scope.relativeDepth
    val driftPx = with(scope.density) { CrossActivityDrift.toPx() }
    if (d <= 0f) {
        val p = topProgress(d)
        translationX = ((1f - p) * driftPx).fastRoundToInt().toFloat()
        alpha = if (scope.role == NavRole.Outgoing) {
            val settle = scope.settle
            if (settle != null) {
                (1f - (settle.elapsedMillis - CLOSE_FADE_OFFSET) / CLASSIC_FADE_DURATION).coerceIn(0f, 1f)
            } else {
                ((p - CLOSE_FADE_START) / CLOSE_FADE_SPAN).coerceIn(0f, 1f)
            }
        } else {
            1f
        }
    } else {
        translationX = (-coverProgress(d) * driftPx).fastRoundToInt().toFloat()
    }
}

/**
 * 手势驱动的 cross-activity 卡片，按参考实现的两通道分解：
 * - 提交前：手势进度经 [BackGestureEasing] 整形后驱动几何，卡片居中缩到 0.9 且不淡出，
 *   非右边缘手势让右边缘贴屏幕减 [CrossActivityEdgeMargin]，两层随手指纵向跟随。
 * - 提交后：主轨道是固定 450ms 缓动 tween（速度不进轨道），卡片从缓动提交姿态长回原尺寸并
 *   再飞出 [CrossActivityDrift]，hug 冻结在提交值；卡片按墙钟 90ms 淡出，遮罩同步线性淡出；
 *   速度缩放的 [bounceScale] 作为独立闭式弹簧叠加在两层 scale 上。
 * - 取消：刚性无回弹弹簧 [NavSettleSpec.Spring] 1500f，几何沿同一缓动映射回退。
 */
private val CrossActivityPredictive: NavTransition = navGraphicsTransition(
    opaqueDepth = 1f,
    motion = NavMotion(
        commit = NavSettleSpec.Tween(durationMillis = 450, easing = FastOutExtraSlowIn),
        cancel = NavSettleSpec.Spring(stiffness = 1500f),
    ),
    scrim = { scope ->
        val s = scope.settle
        val g = scope.gesture
        when {
            s?.phase == NavSettlePhase.Commit -> (1f - s.elapsedMillis / 450f).coerceIn(0f, 1f)
            g != null -> (scope.relativeDepth.coerceIn(0f, 1f) / (1f - g.progress).coerceAtLeast(MIN_RELEASE_SPAN)).coerceIn(0f, 1f)
            else -> scope.relativeDepth.coerceIn(0f, 1f)
        }
    },
) { scope ->
    val d = scope.relativeDepth
    val gesture = scope.gesture
    val settle = scope.settle
    val committing = settle?.phase == NavSettlePhase.Commit
    val widthPx = scope.layoutSize.width.toFloat()
    val heightPx = scope.layoutSize.height.toFloat()
    val driftPx = with(scope.density) { CrossActivityDrift.toPx() }
    val bounce = bounceScale(settle, gesture)
    val hugMax = (
        widthPx * (1f - CROSS_ACTIVITY_MIN_SCALE) / 2f -
            with(scope.density) { CrossActivityEdgeMargin.toPx() }
        ).coerceAtLeast(0f)
    val hugs = gesture?.swipeEdge != NavSwipeEdge.Right
    if (d <= 0f) {
        val p = topProgress(d)
        if (scope.role == NavRole.Outgoing && committing && gesture != null) {
            // 提交后的关闭卡片：`post` 即轨道缓动时间分数，逐点对应参考实现的 rect lerp
            val releaseP = (1f - gesture.progress).coerceAtLeast(MIN_RELEASE_SPAN)
            val post = (1f - p / releaseP).coerceIn(0f, 1f)
            val releasePE = shapedTopProgress(releaseP, gesture)
            val committedScale = CROSS_ACTIVITY_MIN_SCALE + (1f - CROSS_ACTIVITY_MIN_SCALE) * releasePE
            val grown = committedScale + (1f - committedScale) * post
            scaleX = snapScaleToPixelWidth(grown * bounce, widthPx)
            scaleY = scaleX
            var tx = if (hugs) (1f - releasePE) * hugMax else 0f
            tx += post * driftPx
            alpha = (1f - 5f * (settle.elapsedMillis / 450f)).coerceAtLeast(0f)
            translationX = snapEdgeTranslation(tx, scaleX, widthPx)
            translationY = snapEdgeTranslation(crossActivityYShift(gesture, heightPx, scaleX, scope.density), scaleX, heightPx)
        } else {
            // 手指驱动 / 取消回弹 / 无手势独立使用：几何跑在缓动后的行程轴上
            val pE = shapedTopProgress(p, gesture)
            scaleX = snapScaleToPixelWidth((CROSS_ACTIVITY_MIN_SCALE + (1f - CROSS_ACTIVITY_MIN_SCALE) * pE) * bounce, widthPx)
            scaleY = scaleX
            translationX = snapEdgeTranslation(if (hugs) (1f - pE) * hugMax else 0f, scaleX, widthPx)
            alpha = when {
                scope.role == NavRole.Outgoing && gesture != null -> {
                    val releaseP = (1f - gesture.progress).coerceAtLeast(MIN_RELEASE_SPAN)
                    (1f - (1f - p / releaseP).coerceIn(0f, 1f) * 3.5f).coerceAtLeast(0f)
                }
                gesture != null -> 1f
                else -> (p / 0.2f).coerceIn(0f, 1f)
            }
            translationY = snapEdgeTranslation(crossActivityYShift(gesture, heightPx, scaleX, scope.density), scaleX, heightPx)
        }
    } else {
        val dc = coverProgress(d)
        val post = if (gesture != null) {
            val releaseProgress = gesture.progress
            if (releaseProgress >= 1f) 1f else (((1f - dc) - releaseProgress) / (1f - releaseProgress)).coerceIn(0f, 1f)
        } else {
            1f - dc
        }
        if (gesture != null) {
            val travel = if (committing) gesture.progress else (1f - dc)
            val eased = BackGestureEasing.transform(travel.coerceIn(0f, 1f))
            val liveScale = CROSS_ACTIVITY_MIN_SCALE + (1f - CROSS_ACTIVITY_MIN_SCALE) * (1f - eased)
            scaleX = snapScaleToPixelWidth((liveScale + (1f - liveScale) * post) * bounce, widthPx)
            scaleY = scaleX
        }
        translationX = snapEdgeTranslation(-(1f - post) * driftPx, scaleX, widthPx)
        translationY = snapEdgeTranslation(crossActivityYShift(gesture, heightPx, scaleX, scope.density), scaleX, heightPx)
    }
}

/** 平台 cross-activity 手感：程序式 push/pop 走经典窗口动画，预测返回/边缘滑动走卡片动画。 */
internal val AospNavigationTransition: NavTransition = navDirectionalTransition(
    push = ClassicActivityOpen,
    pop = ClassicActivityClose,
    predictivePop = CrossActivityPredictive,
)

/** 两层的纵向跟随（参考 getYOffset）：触摸位移经半屏 decelerate 阻尼，上限随 scale 收复而坍缩归零。 */
private fun crossActivityYShift(
    gesture: NavGesture?,
    height: Float,
    scale: Float,
    density: Density,
): Float {
    if (gesture == null || height <= 0f) return 0f
    val rawDelta = gesture.touchY - gesture.initialTouchY
    val half = height / 2f
    val ratio = min(half, abs(rawDelta)) / half
    val damped = 1f - (1f - ratio) * (1f - ratio)
    val marginPx = with(density) { CrossActivityEdgeMargin.toPx() }
    val maxShift = ((height - height * scale) / 2f - marginPx).coerceAtLeast(0f)
    return maxShift * damped * (if (rawDelta < 0f) -1f else 1f)
}

/** 让缩放后的宽度落在整数像素上，便于 [snapEdgeTranslation] 同时对齐左右两条边。 */
private fun snapScaleToPixelWidth(scale: Float, width: Float): Float =
    if (width <= 0f) scale else (scale * width).fastRoundToInt() / width

/** 让中心缩放下的位移落在整数像素上，消除圆角裁切边缘的细线闪动。 */
private fun snapEdgeTranslation(translation: Float, scale: Float, extent: Float): Float {
    val inset = extent * (1f - scale) / 2f
    return (translation + inset).fastRoundToInt() - inset
}

/** d ∈ [-1, 0] → [0, 1]，1 表示完全处于栈顶。 */
private fun topProgress(d: Float): Float = (1f + d).coerceIn(0f, 1f)

/** d ∈ [0, 1] → [0, 1]，0 表示处于栈顶、1 表示被完全覆盖。 */
private fun coverProgress(d: Float): Float = d.coerceIn(0f, 1f)

/** 卡片最小缩放（参考 MAX_SCALE = 0.9）。 */
private const val CROSS_ACTIVITY_MIN_SCALE = 0.9f

/** 所有 `1 - progress` 分母的地板，避免部分设备上报 progress > 1 时破坏拖拽恒等式。 */
private const val MIN_RELEASE_SPAN = 0.001f

/** 下层停靠偏移、卡片提交后飞出距离、经典 open/close 平移距离，均为参考的 96dp。 */
private val CrossActivityDrift = 96.dp

/** 卡片贴边时保留的边距，参考的 8dp。 */
private val CrossActivityEdgeMargin = 8.dp
