package com.crinfo.app.ui.components

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 捏合缩放上限。 */
private const val MaxScale = 4f

/** 双击放大到的倍数。 */
private const val DoubleTapScale = 2.5f

/** 拖拽关闭阈值：屏高的 16%，对齐原 Mojito 的 maxTransYRatio 默认值。 */
private const val CloseDragRatio = 0.16f

/** 单击关闭前等待双击的判定窗口，毫秒。 */
private const val DoubleTapTimeout = 260L

/** 按住超过该时长再抬起不再算点击，避免长按关图。 */
private const val TapTimeout = 300L

/**
 * 横向判定余量：|dy| < |dx| * 该值视为横向翻页，其余方向（垂直、斜向）都触发退出拖拽。
 * 0.5 约等于与水平方向夹角 26.6°。
 */
private const val HorizontalSlopRatio = 0.5f

/**
 * 放大态单指横向贴边后的方向判据：累计位移里纵向分量超过横向的该比例，视为斜滑、继续纵向滚动；
 * 否则（近乎纯横滑）才把横向位移让给 pager 翻页。0.3 约等于与水平方向夹角 16.7°。
 */
private const val EdgePanRatio = 0.3f

/**
 * 车辆图片的全屏查看器，移植自 TransitOn 的公告大图查看器。
 *
 * 手势口径：左右滑动翻页；双指缩放（1x~4x）；双击放大/还原；放大后单指按轴独立平移，
 * 能移动的方向都跟手（斜滑时横纵同时位移），只有已贴边那一侧的分量被丢弃、另一方向继续滚动；
 * 横向贴边且几乎纯横滑时才让给 pager 翻页；1x 时除横向外的任意方向
 * 拖拽都进入退出手势（图片跟手并随距离缩小），超过 1/6 屏高关闭，否则回弹；单击关闭。
 * 窗口是透明 Activity，退场时会透出底下的车辆图片弹窗。
 *
 * 图片先显示缩略图（弹窗里已解码的 1280px 缓存）秒开，当前页再异步换原图；
 * 原图解码失败/内存不足时保留缩略图，不白屏。
 */
@Composable
fun CarImageViewer(
    urls: List<String>,
    initialIndex: Int,
    onClose: () -> Unit,
) {
    if (urls.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, urls.lastIndex),
    ) { urls.size }
    val scope = rememberCoroutineScope()
    val playback = remember { Animatable(0f) }
    val dragProgress = remember { mutableFloatStateOf(0f) }
    // 按图片 URL 记住缩放与平移：横滑切走后滑回来仍是刚才的放大状态
    val zoomStates = remember { mutableStateMapOf<String, Pair<Float, Offset>>() }
    var exiting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { playback.animateTo(1f, tween(220)) }

    val requestExit: () -> Unit = {
        if (!exiting) {
            exiting = true
            scope.launch {
                playback.stop()
                playback.animateTo(0f, tween(160))
                onClose()
            }
        }
    }
    BackHandler(onBack = requestExit)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(
                    alpha = (1f - dragProgress.floatValue * 0.9f) * playback.value,
                ),
            ),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 进出场：整页轻微缩放 + 淡入淡出
                    alpha = playback.value
                    val entrance = 0.94f + 0.06f * playback.value
                    scaleX = entrance
                    scaleY = entrance
                },
            // 放大状态下翻页不靠禁用 pager，而是由页面消费横向位移拦截；
            // 图片顶到水平边界后不再消费，位移自然交给 pager 翻页。
            userScrollEnabled = dragProgress.floatValue == 0f,
            beyondViewportPageCount = 1,
        ) { page ->
            ImageViewerPage(
                url = urls[page],
                isCurrent = pagerState.currentPage == page,
                dragProgress = dragProgress,
                zoomStates = zoomStates,
                onClose = requestExit,
            )
        }
        BasicText(
            text = "${pagerState.currentPage + 1} / ${urls.size}",
            style = TextStyle(
                color = Color.White.copy(
                    alpha = 0.85f * (1f - dragProgress.floatValue) * playback.value,
                ),
                fontSize = 13.sp,
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun ImageViewerPage(
    url: String,
    isCurrent: Boolean,
    dragProgress: MutableFloatState,
    zoomStates: MutableMap<String, Pair<Float, Offset>>,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val doubleTapSlop = with(LocalDensity.current) { 48.dp.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(url) { mutableFloatStateOf(zoomStates[url]?.first ?: 1f) }
    var offset by remember(url) { mutableStateOf(zoomStates[url]?.second ?: Offset.Zero) }
    var dragOffset by remember(url) { mutableStateOf(Offset.Zero) }
    var lastTapAt by remember(url) { mutableLongStateOf(0L) }
    var lastTapPos by remember(url) { mutableStateOf(Offset.Zero) }
    var pendingClose by remember(url) { mutableStateOf<Job?>(null) }

    // 图片来源：缩略图先显示（弹窗已解码），原图按需加载；两者宽高比一致，平移边界用任一尺寸都行。
    val cachedFull = remember(url) { CarImageViewerSource.peekFull(url) }
    val thumbnail = remember(url) { CarImageViewerSource.thumbnail(url) }
    var fullBitmap by remember(url) { mutableStateOf<ImageBitmap?>(cachedFull) }
    var imageSize by remember(url) {
        mutableStateOf((cachedFull ?: thumbnail)?.let { IntSize(it.width, it.height) } ?: IntSize.Zero)
    }
    var loadFailed by remember(url) { mutableStateOf(false) }
    val shownBitmap = fullBitmap ?: thumbnail

    // 只在当前页加载原图：pager 会组合相邻页，若每页都解码 7200×4000 的原图会瞬间吃掉数百 MB。
    LaunchedEffect(url, isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        if (fullBitmap == null) {
            val loaded = CarImageViewerSource.loadFull(url)
            if (loaded != null) {
                fullBitmap = loaded
                imageSize = IntSize(loaded.width, loaded.height)
            } else if (thumbnail == null) {
                // 没有缩略图兜底才标记失败；有缩略图时保留它，不打断浏览。
                loadFailed = true
            }
        }
    }

    // 容器尺寸要现读：pointerInput 只在 url 变化时重启，不能捕获组合期的旧值
    fun currentCenter(): Offset = Offset(size.width / 2f, size.height / 2f)

    fun applyScale(value: Float) {
        // 双指捏合的 zoom 在两指重合瞬间可能是 NaN/∞，必须挡住，否则 graphicsLayer 直接失效黑屏
        scale = if (value.isFinite()) value.coerceIn(1f, MaxScale) else scale
    }

    /** 退出拖拽的位移，同时驱动背景透明度；图片的跟手与缩小在绘制层按距离计算。 */
    fun applyDrag(value: Offset) {
        dragOffset = value
        val reference = if (size.height > 0) size.height * 0.6f else 1f
        dragProgress.floatValue = (value.getDistance() / reference).coerceIn(0f, 1f)
    }

    // 切走时只保存缩放与位置、清掉拖拽位移；滑回来仍恢复成刚才的放大状态
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            zoomStates[url] = scale to offset
            applyDrag(Offset.Zero)
        }
    }

    DisposableEffect(Unit) {
        onDispose { pendingClose?.cancel() }
    }

    /**
     * 按图片真实显示尺寸算可平移范围：
     * fit 之后高度不足一屏时纵向完全不能动，超过一屏时最多拖到图片边缘。
     */
    fun translationBounds(): Offset {
        if (size.width <= 0 || size.height <= 0) return Offset.Zero
        val containerW = size.width.toFloat()
        val containerH = size.height.toFloat()
        val imageW = imageSize.width.toFloat()
        val imageH = imageSize.height.toFloat()
        if (imageW <= 0f || imageH <= 0f) {
            // 图片尺寸未知（仍在加载）时退回按容器近似
            return Offset(
                max(0f, containerW * (scale - 1f) / 2f),
                max(0f, containerH * (scale - 1f) / 2f),
            )
        }
        val fit = min(containerW / imageW, containerH / imageH)
        val displayW = imageW * fit
        val displayH = imageH * fit
        return Offset(
            x = max(0f, (displayW * scale - containerW) / 2f),
            y = max(0f, (displayH * scale - containerH) / 2f),
        )
    }

    fun clampToBounds(value: Offset): Offset {
        val bounds = translationBounds()
        return Offset(
            x = value.x.coerceIn(-bounds.x, bounds.x),
            y = value.y.coerceIn(-bounds.y, bounds.y),
        )
    }

    /** 双击在图片中心放大/还原，不跟随点击位置。 */
    fun toggleZoom() {
        scope.launch {
            val startScale = scale
            val startOffset = offset
            val targetScale = if (startScale > 1.01f) 1f else DoubleTapScale
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(200),
            ) { fraction, _ ->
                applyScale(startScale + (targetScale - startScale) * fraction)
                offset = lerp(startOffset, Offset.Zero, fraction)
            }
        }
    }

    fun handleTap(position: Offset) {
        val now = SystemClock.uptimeMillis()
        val isDoubleTap = now - lastTapAt <= DoubleTapTimeout &&
            (position - lastTapPos).getDistance() <= doubleTapSlop
        if (isDoubleTap) {
            lastTapAt = 0L
            pendingClose?.cancel()
            pendingClose = null
            toggleZoom()
        } else {
            lastTapAt = now
            lastTapPos = position
            pendingClose?.cancel()
            pendingClose = scope.launch {
                delay(DoubleTapTimeout)
                onClose()
            }
        }
    }

    /** 纵向平移，越界收敛到图片边缘；返回是否真的移动了。 */
    fun panByY(dy: Float): Boolean {
        val bounds = translationBounds()
        val nextY = (offset.y + dy).coerceIn(-bounds.y, bounds.y)
        if (nextY == offset.y) return false
        offset = Offset(offset.x, nextY)
        return true
    }

    /**
     * 横向平移；返回 false 表示本次横向位移没有落到图上（贴边朝外或图片不宽于屏幕），
     * 由调用方决定是继续纵向滚动还是把横向让给 pager 翻页。
     */
    fun panByX(dx: Float): Boolean {
        val bounds = translationBounds()
        if (bounds.x <= 0f) return false
        val nextX = (offset.x + dx).coerceIn(-bounds.x, bounds.x)
        if (nextX == offset.x) return false
        offset = Offset(nextX, offset.y)
        return true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(url) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPosition = down.position
                    // 新的触摸说明用户还在操作：撤销等待中的单击关闭，第二次点击才能被识别成双击
                    pendingClose?.cancel()
                    pendingClose = null
                    val downAt = SystemClock.uptimeMillis()
                    var multiTouch = false
                    var closing = false
                    var horizontalScroll = false
                    // 横向贴边后的累计位移：用来区分斜滑（继续纵向）和纯横滑（让给 pager 翻页）
                    var edgeAccum = Offset.Zero
                    var total = Offset.Zero
                    var upPosition = downPosition
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount > 1) multiTouch = true
                        val zooming = pressedCount > 1
                        // 两指重合瞬间 calculateZoom/calculatePan 会给出 NaN 或 ∞，先清洗再用
                        val rawZoom = event.calculateZoom()
                        val zoomChange = if (rawZoom.isFinite() && rawZoom > 0f) rawZoom else 1f
                        val rawPan = event.calculatePan()
                        val panChange = if (rawPan.x.isFinite() && rawPan.y.isFinite()) {
                            rawPan
                        } else {
                            Offset.Zero
                        }
                        event.changes.firstOrNull()?.let { upPosition = it.position }

                        when {
                            zooming -> {
                                // 从退出拖拽切到双指缩放：撤销位移，避免松手仍按关闭处理
                                if (closing) {
                                    closing = false
                                    applyDrag(Offset.Zero)
                                }
                                // 缩放后可能只剩一指继续滑，贴边累计要重新开始
                                edgeAccum = Offset.Zero
                                val rawCentroid = event.calculateCentroid(useCurrent = false)
                                val anchor = if (rawCentroid.x.isFinite() && rawCentroid.y.isFinite()) {
                                    rawCentroid
                                } else {
                                    currentCenter()
                                }
                                val nextScale = (scale * zoomChange).let {
                                    if (it.isFinite()) it.coerceIn(1f, MaxScale) else scale
                                }
                                if (nextScale <= 1f) {
                                    applyScale(1f)
                                    offset = Offset.Zero
                                } else {
                                    val ratio = nextScale / scale
                                    val nextOffset = (anchor - currentCenter()) * (1f - ratio) +
                                        offset * ratio + panChange
                                    applyScale(nextScale)
                                    offset = clampToBounds(nextOffset)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }

                            scale > 1.01f -> {
                                // 逐轴判定：能移动的方向都跟手（斜滑时横纵同时位移），
                                // 只有真正贴边的那一侧分量被丢弃，另一方向继续滚动
                                val xMoved = panByX(panChange.x)
                                val yMoved = panByY(panChange.y)
                                // 纵向抖动不算纵向意图，否则纯横滑会被手指的上下微抖吃掉翻页
                                val verticalIntent =
                                    yMoved && abs(panChange.y) > abs(panChange.x) * EdgePanRatio
                                if (xMoved || verticalIntent) {
                                    // 任一方向确实动了：本页跟手，并可从翻页接管中抢回。
                                    // 滑到角落不松手反向滑必须能继续跟手，所以让位不是单向门
                                    horizontalScroll = false
                                    edgeAccum = Offset.Zero
                                    event.changes.forEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                } else {
                                    // 两轴都没动：只有横向朝外的纯横滑才让给 pager 翻页，
                                    // 斜滑一律占住手势，等反向输入恢复跟手
                                    edgeAccum += panChange
                                    val verticalDominant =
                                        abs(edgeAccum.y) > abs(edgeAccum.x) * EdgePanRatio
                                    if (verticalDominant) {
                                        edgeAccum = Offset.Zero
                                        event.changes.forEach {
                                            if (it.positionChanged()) it.consume()
                                        }
                                    } else if (abs(edgeAccum.x) > touchSlop) {
                                        horizontalScroll = true
                                    } else {
                                        event.changes.forEach {
                                            if (it.positionChanged()) it.consume()
                                        }
                                    }
                                }
                            }

                            else -> {
                                total += panChange
                                if (!closing && !horizontalScroll &&
                                    total.getDistance() > touchSlop
                                ) {
                                    // 除横向外的任意方向（垂直、斜向）都进入退出拖拽
                                    if (abs(total.y) > abs(total.x) * HorizontalSlopRatio) {
                                        closing = true
                                    } else {
                                        horizontalScroll = true
                                    }
                                }
                                if (closing) {
                                    applyDrag(total)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    when {
                        closing -> {
                            val distance = dragOffset.getDistance()
                            if (size.height > 0 && distance > size.height * CloseDragRatio) {
                                // 沿当前方向继续飞出并缩小淡出
                                val from = dragOffset
                                val unit = if (distance > 0f) from / distance else Offset(0f, 1f)
                                val to = from + unit * (size.height * 0.8f)
                                scope.launch {
                                    animate(
                                        initialValue = 0f,
                                        targetValue = 1f,
                                        animationSpec = tween(180),
                                    ) { fraction, _ ->
                                        applyDrag(lerp(from, to, fraction))
                                    }
                                    onClose()
                                }
                            } else {
                                val from = dragOffset
                                scope.launch {
                                    animate(
                                        initialValue = 0f,
                                        targetValue = 1f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    ) { fraction, _ ->
                                        applyDrag(from * (1f - fraction))
                                    }
                                }
                            }
                        }

                        else -> {
                            val moved = (upPosition - downPosition).getDistance()
                            if (!multiTouch &&
                                SystemClock.uptimeMillis() - downAt <= TapTimeout &&
                                moved <= touchSlop
                            ) {
                                handleTap(upPosition)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 退出拖拽：图片同时跟随手指的横纵位移，并按距离逐渐缩小
        val dragDistance = dragOffset.getDistance()
        val shrink = if (size.height > 0) {
            (dragDistance / (size.height * 0.8f)).coerceIn(0f, 0.45f)
        } else {
            0f
        }
        val fade = if (size.height > 0) {
            (dragDistance / (size.height * 0.9f)).coerceIn(0f, 1f) * 0.75f
        } else {
            0f
        }
        shownBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offset.x + dragOffset.x
                        translationY = offset.y + dragOffset.y
                        val shrinkScale = scale * (1f - shrink)
                        scaleX = shrinkScale
                        scaleY = shrinkScale
                        alpha = 1f - fade
                    },
            )
        }
        when {
            shownBitmap == null && loadFailed -> BasicText(
                text = "图片加载失败",
                style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp),
            )

            shownBitmap == null -> ViewerSpinner()

            else -> Unit
        }
    }
}

@Composable
private fun ViewerSpinner() {
    val transition = rememberInfiniteTransition(label = "viewer-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "viewer-spinner-angle",
    )
    Canvas(modifier = Modifier.size(30.dp)) {
        drawArc(
            color = Color.White.copy(alpha = 0.85f),
            startAngle = angle,
            sweepAngle = 280f,
            useCenter = false,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
