package com.crinfo.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crinfo.app.data.StationRepository
import com.crinfo.app.data.model.CarDetail
import com.crinfo.app.data.model.EmuFallback
import com.crinfo.app.data.model.LltskbCarModel
import com.crinfo.app.data.offline.TrainSuggestion
import com.crinfo.app.data.model.JourneyNode
import com.crinfo.app.data.model.RunningDays
import com.crinfo.app.data.model.ScheduleRow
import com.crinfo.app.data.model.TrainDetail
import com.crinfo.app.data.model.TrainTag
import com.crinfo.app.data.model.chineseDuration
import com.crinfo.app.data.model.trainTags
import com.crinfo.app.data.railwayBureauName
import com.crinfo.app.data.offline.JiaoluLegInfo
import com.crinfo.app.data.offline.JiaoluPosition
import com.crinfo.app.ui.components.AppScaffold
import com.crinfo.app.ui.components.InlineSwitchRow
import com.crinfo.app.ui.components.LocalAppSettings
import com.crinfo.app.ui.components.AppTopBar
import com.crinfo.app.ui.components.BarContentGap
import com.crinfo.app.ui.components.CarImages
import com.crinfo.app.ui.components.CarImageViewerActivity
import com.crinfo.app.ui.components.Chip
import com.crinfo.app.ui.components.ErrorState
import com.crinfo.app.ui.components.InlineErrorNotice
import com.crinfo.app.ui.components.FavoriteAction
import com.crinfo.app.ui.components.OnlineToggleAction
import com.crinfo.app.ui.components.FavoriteEntry
import com.crinfo.app.ui.components.FavoriteSection
import com.crinfo.app.ui.components.LocalBarColor
import com.crinfo.app.ui.components.MonthDatePickerDialog
import com.crinfo.app.ui.components.PullToRefreshTextsCn
import com.crinfo.app.ui.components.RemotePicture
import com.crinfo.app.ui.components.SeatGrid
import com.crinfo.app.ui.components.SectionLabel
import com.crinfo.app.ui.components.SectionTitle
import com.crinfo.app.ui.components.rememberHapticTap
import com.crinfo.app.ui.components.TagBadge
import com.crinfo.app.ui.query.RunNotice
import com.crinfo.app.ui.query.TrainQueryViewModel
import com.crinfo.app.ui.settings.OfflineDbViewModel
import com.crinfo.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun TrainHome(
    vm: TrainQueryViewModel = viewModel(),
    settings: SettingsViewModel,
    dbVm: OfflineDbViewModel,
    onOpenDetail: (code: String, date: String) -> Unit,
) {
    var pickDate by remember { mutableStateOf(false) }
    // 开关值存在全局设置里（三个 Tab 各记各的），VM 只拿一个 offline 标志
    val online = LocalAppSettings.current.trainOnline
    LaunchedEffect(online) { vm.offline = !online }

    /** 车次查询的统一出口：离线且没装库时先拦下，装完自动回到这里继续跳详情。 */
    fun open(code: String, day: String) {
        vm.offline = !online
        if (!online && dbVm.interceptWhenMissing { open(code, day) }) return
        onOpenDetail(code, day)
    }
    val focusManager = LocalFocusManager.current
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(appBarState)

    fun openFromCode(code: String) {
        vm.updateInput(code)
        vm.prepareQuery()?.let { (c, d) -> open(c, d) }
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                color = LocalBarColor.current,
                title = "车次查询",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                // padding 挂在 scroll 之后等于 contentPadding：视口铺满整屏，
                // 表单项会从顶栏与底栏下方滑过，栏的模糊/半透明才有内容可采样
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 输入框和候选弹层共用一个父 Box：Miuix 的 ListPopup 拿「调用点的父级矩形」当锚点，
            // 摆在这个 Box 里就自动贴着输入框下沿左对齐展开，下方放不下才翻到上方。
            var fieldRect by remember { mutableStateOf(IntRect.Zero) }
            // 点弹层外面只收弹层：不加这道闩，收起后门槛原样成立，弹层会立刻自己开回来
            var suggestionsDismissed by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(dbVm.installedVersion) { vm.preloadSuggestions() }
            LaunchedEffect(vm.input, vm.suggestions) { suggestionsDismissed = false }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CardEdge)
                    .onGloballyPositioned { coords ->
                        val at = coords.positionInWindow()
                        fieldRect = IntRect(
                            left = at.x.toInt(),
                            top = at.y.toInt(),
                            right = (at.x + coords.size.width).toInt(),
                            bottom = (at.y + coords.size.height).toInt(),
                        )
                    },
            ) {
                TextField(
                    value = vm.input,
                    onValueChange = { vm.updateInput(it) },
                    label = "车次号，如 G1 / D7 / K516",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            vm.prepareQuery()?.let { (c, d) -> open(c, d) }
                        },
                    ),
                    // 联想只在聚焦时算：Miuix TextField 没有 onFocusChange 参数，焦点事件挂在 modifier 链上收
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusEvent { vm.onFieldFocused(it.isFocused) },
                )
                TrainSuggestionPopup(
                    show = !suggestionsDismissed && vm.suggestions.isNotEmpty(),
                    anchorBounds = fieldRect,
                    suggestions = vm.suggestions,
                    onSelect = { code ->
                        suggestionsDismissed = true
                        focusManager.clearFocus()
                        openFromCode(code)
                    },
                    // 点暗掉的地方：收弹层 + 输入框失焦（键盘一起收）。
                    // 落在输入框里的那一下被上面吞掉了，不会走到这里，所以点框本身不会丢焦点。
                    onDismiss = {
                        suggestionsDismissed = true
                        focusManager.clearFocus()
                    },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            DateCard(date = vm.date, label = "运行日期", onClick = { pickDate = true })
            Spacer(modifier = Modifier.height(14.dp))
            InlineSwitchRow(
                label = "联网查询",
                checked = online,
                onCheckedChange = { settings.updateTrainOnline(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = rememberHapticTap {
                    focusManager.clearFocus()
                    vm.prepareQuery()?.let { (c, d) -> open(c, d) }
                },
                // 与车票查询一致：主操作要显式用强调色，Miuix Button 默认是次级配色
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CardEdge),
            ) {
                Text(
                    text = "查询",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
            FavoriteSection(
                title = "收藏车次",
                entries = vm.favoriteTrains.map { code ->
                    FavoriteEntry(code, onOpen = { openFromCode(code) }, onRemove = { vm.removeFavorite(code) })
                },
            )
            Spacer(modifier = Modifier.height(20.dp))
            TrainHistorySection(vm) { code -> openFromCode(code) }
            Spacer(modifier = Modifier.height(24.dp))
        }

        MonthDatePickerDialog(pickDate, vm.dates, LocalDate.parse(vm.date), { pickDate = false }) {
            vm.setDate(it)
        }
    }
}

/** 候选行高：弹层最多摆 4 个半，用被截断的半条暗示还能往下滑。 */
private val SuggestionRowHeight = 44.dp

/** 弹层宽 = 输入框宽 × 0.4：车次号最长 6 位，这个宽度刚好放得下。 */
private const val SuggestionWidthRatio = 0.4f

/**
 * 车次输入的候选弹层，用 Miuix 的 ListPopup 家族（与系统下拉列表同一套样式与展开动画）。
 *
 * 宽度必须从外面钉死：`ListPopupColumn` 自己把宽度钳在 200–288dp（库内写死的下限），
 * 但父约束更窄时它跟随父宽，所以套一层固定宽度的 Box 才压得到 0.4 倍。
 * 别拿 `popupModifier` 当宽度用——那条修饰符挂在铺满全屏的点击捕获层上，
 * 缩窄它等于连捕获层一起缩窄，候选反而点不动了。
 *
 * 锚点是「调用点的父级矩形」，所以本函数只能在包住输入框的那个 Box 里调用；
 * 默认的 `Align.Start` + `DropdownPositionProvider` 就是左对齐贴下沿、留 8dp 间隙，
 * 下方摆不下才翻到上方。高度上限 4 个半条目：装得下按内容自适应，装不下靠库自带的滚动上下滑。
 */
@Composable
private fun TrainSuggestionPopup(
    show: Boolean,
    anchorBounds: IntRect,
    suggestions: List<TrainSuggestion>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (anchorBounds.width <= 0) return
    val width = with(LocalDensity.current) { (anchorBounds.width * SuggestionWidthRatio).toDp() }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    val dim = MiuixTheme.colorScheme.windowDimming
    // 暗幕自己画就没有库的淡入淡出，这里补一节：show 翻false 时弹层还在播退场动画，
    // 宿主依然挂载，所以这一段 alpha 动画能跟着走完，不会看见暗幕「啪」地消失
    val dimAlpha by animateFloatAsState(if (show) 1f else 0f, tween(180), label = "suggestDim")
    // 库的遮罩是整窗一块实色，压不出洞；暗幕改由 popupModifier 自己画，坐标要从窗口系换算回捕获层自身系。
    //
    // 「点输入框不收起」必须走 PointerEventPass.Initial：同一个节点上的修饰符链，Main pass 是内层先收，
    // 库自带的 detectTapGestures 排在我们更内一侧，等我们在 Main 上消费已经晚了；
    // Initial pass 外层先收，把落在输入框里的那次抬手消费掉，内层判定「up 已被消费」就不发 dismiss。
    // 落在别处原样放行，照旧只收弹层。
    OverlayListPopup(
        show = show,
        minWidth = width,
        maxHeight = SuggestionRowHeight * 4.5f,
        enableWindowDim = false,
        popupModifier = Modifier
            .onGloballyPositioned { hostOrigin = it.positionInWindow() }
            // Clear 只在该层内起作用，所以暗幕要画在独立离屏层里，才不会把页面真擦穿
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawRect(dim.copy(alpha = dim.alpha * dimAlpha))
                val origin = hostOrigin
                val pad = SpotlightInset.toPx()
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(
                        anchorBounds.left.toFloat() - origin.x - pad,
                        anchorBounds.top.toFloat() - origin.y - pad,
                    ),
                    size = Size(
                        anchorBounds.width.toFloat() + pad * 2f,
                        anchorBounds.height.toFloat() + pad * 2f,
                    ),
                    cornerRadius = CornerRadius(SpotlightRadius.toPx()),
                    blendMode = BlendMode.Clear,
                )
                drawContent()
            }
            .pointerInput(anchorBounds) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (!(hostOrigin + down.position).inside(anchorBounds)) return@awaitEachGesture
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial)
                            .changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                    }
                }
            },
        onDismissRequest = onDismiss,
    ) {
        Box(modifier = Modifier.width(width)) {
            ListPopupColumn {
                suggestions.forEach { item ->
                    TrainSuggestionRow(item = item, onClick = { onSelect(item.code) })
                }
            }
        }
    }
}

/** 捕获层的原点与输入框矩形不在同一坐标系，先把落点换成窗口坐标再判命中。 */
private fun Offset.inside(rect: IntRect): Boolean =
    x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom

/** 亮区紧贴输入框，不额外外扩。 */
private val SpotlightInset = 0.dp

/** 亮区圆角 = 输入框 16dp 圆角，与 Miuix TextFieldDefaults.CornerRadius 同源。 */
private val SpotlightRadius = 16.dp

@Composable
private fun TrainSuggestionRow(item: TrainSuggestion, onClick: () -> Unit) {
    val primary = MiuixTheme.colorScheme.primary
    val onSurface = MiuixTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SuggestionRowHeight)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = item.highlighted(primary, onSurface),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 命中片段用主色、其余用正文色：一眼看出这条候选是靠哪几位字符匹配上的。 */
private fun TrainSuggestion.highlighted(primary: Color, onSurface: Color): AnnotatedString =
    buildAnnotatedString {
        val end = (matchStart + matchLength).coerceAtMost(code.length)
        if (matchStart < 0 || matchStart >= end) {
            withStyle(SpanStyle(color = onSurface)) { append(code) }
        } else {
            withStyle(SpanStyle(color = onSurface)) { append(code.substring(0, matchStart)) }
            withStyle(SpanStyle(color = primary)) { append(code.substring(matchStart, end)) }
            withStyle(SpanStyle(color = onSurface)) { append(code.substring(end)) }
        }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainHistorySection(vm: TrainQueryViewModel, onOpen: (String) -> Unit) {
    if (vm.history.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("历史车次")
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "长按删除",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            vm.history.forEach { code ->
                Chip(
                    text = code,
                    selected = false,
                    onLongClick = { vm.removeHistory(code) },
                ) {
                    onOpen(code)
                }
            }
        }
    }
}

/* ---------------- 详情页 ---------------- */

/**
 * 车次详情导航条目：VM 随条目创建、随条目出栈销毁，
 * 返回到栈里更早的详情页时各看各的车次，不会被最新一次查询顶掉。
 */
@Composable
fun TrainDetailEntry(
    code: String,
    date: String,
    vm: TrainQueryViewModel = viewModel(),
    settings: SettingsViewModel,
    dbVm: OfflineDbViewModel,
    onOpenStation: (telecode: String, date: String) -> Unit,
    onOpenTrain: (code: String, date: String) -> Unit,
    onBack: () -> Unit,
) {
    val online = LocalAppSettings.current.trainOnline
    // 详情页顶栏就地翻转开关后要能立刻换源重查；进页首帧仍按原逻辑只起首查
    var seenOnline by rememberSaveable { mutableStateOf(online) }
    LaunchedEffect(online) {
        // 必须先赋值再查：startQuery 里读的就是这个标志
        vm.offline = !online
        if (seenOnline != online) {
            seenOnline = online
            if (!vm.loading) vm.query()
        } else if (vm.detail == null && !vm.loading) {
            vm.startQuery(code, date)
        }
    }
    TrainDetailPage(vm, settings, dbVm, online, onOpenStation, onOpenTrain, onBack)
}

@Composable
private fun TrainDetailPage(
    vm: TrainQueryViewModel,
    settings: SettingsViewModel,
    dbVm: OfflineDbViewModel,
    online: Boolean,
    onOpenStation: (telecode: String, date: String) -> Unit,
    onOpenTrain: (code: String, date: String) -> Unit,
    onBack: () -> Unit,
) {
    var showCarDetail by rememberSaveable { mutableStateOf(false) }
    var showJiaoluTimes by rememberSaveable { mutableStateOf(false) }
    var showJourney by rememberSaveable { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(appBarState)

    AppScaffold(
        topBar = {
            AppTopBar(
                color = LocalBarColor.current,
                title = vm.queriedCode,
                subtitle = vm.detail?.let { "${it.fromName} → ${it.toName}" }.orEmpty(),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.ChevronBackward,
                            contentDescription = "返回",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                actions = {
                    FavoriteAction(isFavorite = vm.isFavorite, onClick = { vm.toggleFavorite() })
                    OnlineToggleAction(online = online, onClick = { settings.updateTrainOnline(!online) })
                },
                bottomContent = {
                    val dateState = rememberLazyListState()
                    val selectedIndex = vm.dates.indexOfFirst { it.toString() == vm.date }
                    LaunchedEffect(selectedIndex) {
                        // 日期条含过去 7 天，今天落在中间，进入页面就要先滚到今天；
                        // 选中项回到首位时同样要滚，所以门槛取 ≥0
                        if (selectedIndex >= 0) dateState.scrollToItem(selectedIndex)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            state = dateState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(start = CardEdge, end = CardEdge),
                        ) {
                            items(vm.dates) { day ->
                                Chip(
                                    text = dayLabel(day),
                                    selected = vm.date == day.toString(),
                                    onClick = { vm.setDate(day) },
                                )
                            }
                        }
                        // 月历入口：日期条横跨过去 7 天与预售期，翻日历比横滑到底更快
                        IconButton(onClick = { pickDate = true }) {
                            Icon(
                                imageVector = MiuixIcons.Months,
                                contentDescription = "月历选择日期",
                                modifier = Modifier.size(22.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                    // 栏底的这段留白是日期条与内容之间唯一的可见间隔：
                    // 列表一滚动，AppScaffold 的 BarContentGap 就只作用于初始位置了
                    Spacer(modifier = Modifier.height(16.dp))
                },
            )
        },
    ) { innerPadding ->
        val direction = LocalLayoutDirection.current
        // 日期条下方那 16dp 由顶栏 bottomContent 的尾部 Spacer 常驻承担；
        // BarContentGap 只在静止位存在、一滚就没了，再叠上去就成了「展开 40dp / 折叠 16dp」。
        // 减掉它，两种状态都只剩栏底那段固定留白。
        val listTop = innerPadding.calculateTopPadding() - BarContentGap
        PullToRefresh(
            isRefreshing = vm.refreshing,
            onRefresh = rememberHapticTap { vm.pullRefresh() },
            refreshTexts = PullToRefreshTextsCn,
            // 视口不内缩，列表一直滑到栏下方，模糊与半透明才有东西可采
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = listTop),
            topAppBarScrollBehavior = scrollBehavior,
        ) {
            LazyColumn(
                // 撑满视口：PullToRefresh 内部用 Column 包内容，不满一屏时列表只到内容底部，
                // 下方空白不属于任何滚动容器，nestedScroll 派发不到 → 下拉刷新与折叠顶栏都失效
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = listTop,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                    start = innerPadding.calculateLeftPadding(direction),
                    end = innerPadding.calculateRightPadding(direction),
                ),
            ) {
                item { TrainStatusLine(vm, dbVm) }
                // 目标日不开行时数据照常展示，只在车次卡片上方补一条说明
                vm.runNotice?.let { notice -> item { RunNoticeCard(notice) } }
                val detail = vm.detail
                if (detail != null) {
                    item { OverviewCard(detail) }
                    item {
                        FactCard(
                            detail,
                            vm.jiaolu,
                            vm.foodCoach,
                            offline = vm.offline,
                            onShowCarDetail = { showCarDetail = true },
                            onShowJiaoluTimes = {
                                vm.loadJiaoluTimes()
                                showJiaoluTimes = true
                            },
                        )
                    }
                    item { TimelineCard(detail, vm, onOpenStation, onShowJourney = { showJourney = true }) }
                    detail.runningDays?.let { item { RunningPlanCard(it) } }
                }
            }
        }
        CarDetailSheet(
            show = showCarDetail,
            detail = vm.detail,
            onDismiss = { showCarDetail = false },
        )
        JiaoluTimesSheet(
            show = showJiaoluTimes,
            position = vm.jiaolu,
            legs = vm.jiaoluLegs,
            turnStations = vm.jiaoluTurnStations,
            onOpenTrain = { code ->
                // 必须先收弹层再跳：OverlayBottomSheet 画在根 Scaffold 上，
                // 不收掉它就会盖在新压入的详情页上面
                showJiaoluTimes = false
                onOpenTrain(code, vm.date)
            },
            onDismiss = { showJiaoluTimes = false },
        )
        JourneySheet(
            show = showJourney,
            nodes = vm.journey,
            onDismiss = { showJourney = false },
        )
        MonthDatePickerDialog(pickDate, vm.dates, LocalDate.parse(vm.date), { pickDate = false }) {
            // 车次 VM 的 setDate 内部已负责换日重查，这里只管落值
            vm.setDate(it)
        }
    }
}

/**
 * 车底接续时刻。库里只给周转顺序，时刻是按车次号现查时刻表得来的，一段查一次、显示一行。
 * 左列仍一程一行（段内多程各自一行更清楚），但整段是一个点击整体，点了就跳看那一趟；
 * 当前这趟本身就在页面上，不做成链接。
 */
@Composable
private fun JiaoluTimesSheet(
    show: Boolean,
    position: JiaoluPosition?,
    legs: Map<Int, JiaoluLegInfo>,
    turnStations: List<String>,
    onOpenTrain: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (position == null) return
    OverlayBottomSheet(
        show = show,
        title = "车底交路",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 高度由内容撑开，上限交给 OverlayBottomSheet（windowHeight - 状态栏）；
                // 这里不能用 fillMaxHeight，否则短内容也会一路顶到状态栏
                .verticalScroll(rememberScrollState()),
        ) {
            if (position.note.isNotBlank()) {
                Text(
                    text = position.note,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            // 调向站来自 extra.dat 表6。jlb 的车型备注里常写「××调向」，那种情况不再单列一行，
            // 免得同一件事在同一屏里说两遍（用户定稿：同一状态只说一处）。
            if (turnStations.isNotEmpty() && !position.note.contains("调向")) {
                Text(
                    text = "调向站 " + turnStations.joinToString("、"),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            position.window.forEachIndexed { offset, segment ->
                val index = position.windowStart + offset
                val isCurrent = index == position.segmentIndex
                // 右侧时刻与左侧车次号块上下居中；单程行看着对齐，两段（`G7806`/`G7805`）时
                // 时刻也会落在那两行的中间，而不是顶在第一行上
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 段内多程仍一程一行，但整段是一个可点整体：中途改号是同一趟车，
                    // 拆成几个链接就点出几趟不同的车
                    JiaoluSegmentCodes(
                        codes = segment.displayCodes,
                        enabled = !isCurrent,
                        onClick = { onOpenTrain(segment.queryCode) },
                    )
                    // 信息一律灰色；多字自己折行，右列仍从同一条竖线起
                    Column(modifier = Modifier.weight(1f)) {
                        val (line, toDayDiff) = jiaoluLegLine(legs[index])
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = line,
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            // 终到次日挂在行尾右上角，与概览卡的「+1」同一套读法
                            if (toDayDiff > 0) {
                                Text(
                                    text = "+$toDayDiff",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.primary,
                                    // 上标全靠行顶对齐；再垫 top padding 就把它压回基线，看着跟时刻平齐
                                    modifier = Modifier.padding(start = 2.dp),
                                )
                            }
                            // 库里的前导 0 只表示这段不载客；标记跟时刻同色同字号，不做成第二种灰
                            if (segment.emptyRun && jiaoluLegReady(legs[index])) {
                                Text(
                                    text = "（空载）",
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
            SheetEndPad()
        }
    }
}

/**
 * 车底交路弹窗左列的一段：一程一行，整段共用一个点击区。
 * 可点的那些每行都画下划线，当前这趟不响应点击也不画线。
 */
@Composable
private fun JiaoluSegmentCodes(codes: List<String>, enabled: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.width(JiaoluCodeWidth)) {
        val rowModifier = if (enabled) {
            Modifier.clickable(onClick = rememberHapticTap(onClick = onClick))
        } else {
            Modifier
        }
        codes.forEach { label ->
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = if (enabled) TextDecoration.Underline else null,
                color = if (enabled) colors.onSurface else colors.primary,
                modifier = rowModifier,
            )
        }
    }
}

/** 车底交路弹窗右列：一段一行，不带车次号（号在左列，宽度固定以保证竖直对齐）。 */
private fun jiaoluLegLine(info: JiaoluLegInfo?): Pair<String, Int> = when (info) {
    null, JiaoluLegInfo.Loading -> "查询中…" to 0
    JiaoluLegInfo.Unavailable -> "无时刻数据" to 0
    is JiaoluLegInfo.Resolved ->
        info.endpoints.firstOrNull()
            ?.let { ("${it.fromName} ${it.fromTime} — ${it.toName} ${it.toTime}") to it.toDayDiff }
            ?: ("无时刻数据" to 0)
}

/** 空驶标记只在时刻那次查询落地后才挂，「查询中…」下面不该跟着变出一串字。 */
private fun jiaoluLegReady(info: JiaoluLegInfo?): Boolean = info != null && info != JiaoluLegInfo.Loading

/** 左列车次号的固定宽度；一程一行，最长的单号是 `G7806` 这类五位号，15sp 下留 76dp 还有富余。 */
private val JiaoluCodeWidth = 76.dp

/**
 * sheet 滚动容器尾部的垫层：Miuix OverlayBottomSheet 不补导航栏，视口铺到屏幕底、
 * 内容穿过小白条滚动，尾部多垫一个条的高度，末行才能完整滚到小白条之上。
 */
@Composable
private fun SheetEndPad() {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Spacer(modifier = Modifier.height(16.dp + navBarBottom))
}

/**
 * 经由板面：按时刻表同一套时间轴排版，但节点是离线线路表解出的完整径路
 * （含两站之间的线路所）。停站显示到发时刻与灰色点，线路所不给时刻。
 */
@Composable
private fun JourneySheet(show: Boolean, nodes: List<JourneyNode>, onDismiss: () -> Unit) {
    if (!show || nodes.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    OverlayBottomSheet(
        show = show,
        title = "经由",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "${nodes.first().name} → ${nodes.last().name}    ${nodes.last().km}km",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            nodes.forEachIndexed { index, node ->
                JourneyRow(
                    node = node,
                    isFirst = index == 0,
                    isLast = index == nodes.lastIndex,
                )
            }
            SheetEndPad()
        }
    }
}

@Composable
private fun JourneyRow(node: JourneyNode, isFirst: Boolean, isLast: Boolean) {
    val colors = MiuixTheme.colorScheme
    val arrive = node.arriveTime
    val depart = node.departTime
    val twoTimes = arrive != null && depart != null
    val dotY = if (twoTimes) TimelineLineHeight else TimelineLineHeight / 2
    val line = colors.onSurfaceVariantSummary.copy(alpha = 0.3f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        // 左列：到/发两行；线路所留空，保持与时刻表相同的对齐。
        // 跨天不在时刻角上标小字，统一走站名行后的 DayBadge（用户定稿，详情页同款）
        Column(
            modifier = Modifier.width(56.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (arrive != null) TimelineTime(arrive, colors.onSurfaceVariantSummary)
            if (depart != null) TimelineTime(depart, colors.onSurfaceVariantSummary)
        }
        // 线路所的小点与线条同色，盖住也看不出盖——改为线在点处**断开**（上下各让 3dp），
        // 灰点悬浮在缺口里，完整且不依赖任何底色（光环方案因 sheet 底色对不上成了白圈，已弃）。
        val lineGap = if (node.isStop) 0.dp else 3.dp
        Box(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight(),
        ) {
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .height(dotY - lineGap)
                        .width(2.dp)
                        .background(line),
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = dotY + lineGap)
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(line),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = dotY - if (node.isStop) 4.dp else 2.5.dp)
                    .size(if (node.isStop) 8.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (node.isStop) colors.primary else line),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, top = dotY - TimelineLineHeight / 2, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = node.name,
                    fontSize = 15.sp,
                    fontWeight = if (node.isStop) FontWeight.Medium else FontWeight.Normal,
                    color = if (node.isStop) colors.onSurface else colors.onSurfaceVariantSummary,
                )
                if (isFirst) StopLabel("始发")
                if (isLast) StopLabel("终到")
                val dayDiff = if (node.arriveDayDiff > 0) node.arriveDayDiff else node.departDayDiff
                if (dayDiff > 0) DayBadge(dayDiff)
            }
            Text(
                text = listOf(node.line, "累计 ${node.km}km").filter { it.isNotBlank() }.joinToString("    "),
                fontSize = 11.sp,
                color = colors.onSurfaceVariantSummary,
            )
        }
    }
}

/** 加载态由下拉刷新动画表达，这里只剩错误与重试。 */
@Composable
private fun TrainStatusLine(vm: TrainQueryViewModel, dbVm: OfflineDbViewModel) {
    val message = vm.error ?: return
    // 离线态缺库时「重试」是空转：重查还是缺库。按钮要说清按下去会发生什么。
    val needInstall = vm.needsOfflineDb
    ErrorState(
        message = message,
        retryText = if (needInstall) "安装数据库" else "重试",
        onRetry = { if (needInstall) dbVm.promptInstall() else vm.query() },
    )
}

/** 目标日不开行：时刻表照常看，只在车次卡片上方说明原因与下次开行日。 */
@Composable
private fun RunNoticeCard(notice: RunNotice) {
    val next = notice.nextRunningDate
        ?.let { "，图定${yearMonthDayText(it)}开行" }
        .orEmpty()
    InlineErrorNotice(
        text = "${notice.trainCode} ${monthDayText(notice.date)}不开行$next，请关注实际运行公告",
    )
}

/** `2026-07-01` → `7月1日`；解析失败原样返回，不给用户看空白。 */
private fun monthDayText(iso: String): String =
    runCatching { java.time.LocalDate.parse(iso) }
        .map { "${it.monthValue}月${it.dayOfMonth}日" }
        .getOrDefault(iso)

/** `20260701` → `2026年7月1日`（下次开行日带年份，跨年时不会误读）。 */
private fun yearMonthDayText(value: Int): String =
    "%d年%d月%d日".format(value / 10000, value / 100 % 100, value % 100)

/** 区间概览：与车票查询卡片同一套读法，降低跨页学习成本。 */
@Composable
private fun OverviewCard(detail: TrainDetail) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardEdge, vertical = 5.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = detail.trainCode, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            detail.tags.forEach { TagBadge(it) }
            Spacer(modifier = Modifier.weight(1f))
            if (detail.bureauName.isNotBlank()) {
                Text(text = detail.bureauName, fontSize = 12.sp, color = colors.primary)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(82.dp), horizontalAlignment = Alignment.End) {
                Text(text = detail.departTime ?: "—", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = detail.fromName,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = detail.durationText, fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                        .height(1.dp)
                        .background(colors.onSurfaceVariantSummary.copy(alpha = 0.35f)),
                )
                Text(
                    text = if (detail.totalKm > 0) "${detail.totalKm}km" else "",
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            Column(modifier = Modifier.width(82.dp), horizontalAlignment = Alignment.Start) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = detail.arriveTime ?: "—",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (detail.crossesDay) {
                        Text(
                            text = "+1",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.primary,
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                        )
                    }
                }
                Text(
                    text = detail.toName,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
        // 席别与票价走车票查询卡片同一个 SeatGrid，观感与读法完全一致。
        // 数据层没取到（离线态、预售期外、当日已临近发车）就是 null，这里整块不出现，不留空壳。
        val seats = detail.seats
        if (!seats.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            SeatGrid(seats)
        }
    }
}

@Composable
private fun FactCard(
    detail: TrainDetail,
    jiaolu: JiaoluPosition?,
    foodCoach: String?,
    offline: Boolean,
    onShowCarDetail: () -> Unit,
    onShowJiaoluTimes: () -> Unit,
) {
    val info = detail.timetable
    val car = detail.carDetail
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardEdge, vertical = 5.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        SectionTitle("担当与交路")
        Spacer(modifier = Modifier.height(6.dp))
        // 按「谁担当 → 什么车底 → 怎么跑」三段排，同类信息不再被拆散
        InfoRow("担当路局", detail.bureauName)
        InfoRow("客运段", info.jiaoluCorporation)
        InfoRow("交路担当", info.jiaoluDept)
        InfoRow("列车车型", carModelText(detail, jiaolu?.carType))
        car?.facts?.forEach { InfoRow(it.name, it.value) }
        // 车底接口的 facts 里已有「餐车」就不再叠库值，同屏两行会互相打脸；
        // 离线态压根没有 facts，库值就是唯一来源
        if (car?.facts?.any { it.name == "餐车" && it.value.isNotBlank() } != true) {
            InfoRow("餐车", foodCoach.orEmpty())
        }
        // 「未获取到」说的是 12306 车底详情接口 + 第三方车号服务没返回，跟离线库无关；
        // 离线态压根没打这两个源，写这行只会让人以为库里缺数据（车型离线库其实有，就在上面一行）。
        if (!offline && car == null && detail.emuFallback == null) InfoRow("车底信息", "未获取到")
        InfoRow("交路车型", info.jiaoluTrainStyle)
        // 交路链来自离线库：没装库时整块不出现，不显示空壳。
        // 链内容与时点都在「车底交路」弹窗里讲，卡片只留一个入口，不再拆成三行重复说。
        if (jiaolu != null) {
            LinkRow("车底交路", "点击查看", onClick = onShowJiaoluTimes)
        }
        InfoRow("沿途停站", "${detail.stopCount} 站")
        // 官方车底与路路通都没有图时不显示入口，避免点开一个空弹窗。
        if (car != null || detail.lltskb?.hasImages == true) {
            LinkRow("车辆图片", "点击查看图片", onClick = onShowCarDetail)
        }
    }
}

/**
 * 「CR400BF-A-5092 复兴号」：官方车底接口 → 第三方车底号 → 时刻表车型串，逐级降级；
 * 链尾再垫离线库的车底型号（普速车板面 `train_style` 恒空，这类缺口库里是有的）。
 */
private fun carModelText(detail: TrainDetail, offlineCarType: String?): String {
    val car = detail.carDetail
    val model = car?.carCode?.ifBlank { null }
        ?: car?.trainStyle?.ifBlank { null }
        ?: detail.emuFallback?.emuNo?.ifBlank { null }
        ?: detail.timetable.trainStyle.ifBlank { null }
        ?: offlineCarType?.ifBlank { null }
    return listOf(model, car?.typeName?.ifBlank { null } ?: model?.let { emuNickname(it) })
        .filter { !it.isNullOrBlank() }
        .joinToString(" ")
}

/** 接口没给称谓时按车型号判：`CR`+数字是复兴号、`CRH` 是和谐号，与 `trainTags` 同一套规则。 */
private fun emuNickname(model: String): String? = when {
    TrainTag.FUXING in trainTags(model) -> "复兴号"
    model.startsWith("CRH") -> "和谐号"
    else -> null
}

/**
 * 车辆图片弹窗：官方图与第三方（路路通）图是两种来源，用互斥胶囊切换，默认官方。
 * 车型只属于第三方，所以车型下拉只在选中第三方时才出现，避免和「12306 图片」混在同一层。
 * 只有一种来源时不显示来源切换控件。
 */
@Composable
private fun CarDetailSheet(show: Boolean, detail: TrainDetail?, onDismiss: () -> Unit) {
    val car = detail?.carDetail
    val lltskb = detail?.lltskb
    val models = lltskb?.models?.filter { it.images.isNotEmpty() }.orEmpty()
    if (car == null && models.isEmpty()) return
    val hasOfficial = car != null
    var thirdParty by remember(detail?.carDetail, lltskb) { mutableStateOf(false) }
    var modelIndex by remember(detail?.carDetail, lltskb) { mutableIntStateOf(0) }
    // 没有官方图时第三方是唯一来源，不必（也无法）切回官方。
    val showThirdParty = thirdParty || !hasOfficial
    val index = if (models.isEmpty()) 0 else modelIndex.coerceIn(0, models.lastIndex)
    val model = models.getOrNull(index)

    OverlayBottomSheet(
        show = show,
        title = car?.carCode?.ifBlank { null } ?: detail?.emuFallback?.emuNo ?: "车辆详情",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 高度由内容撑开，上限交给 OverlayBottomSheet（windowHeight - 状态栏）；
                // 这里不能用 fillMaxHeight，否则短内容也会一路顶到状态栏
                .verticalScroll(rememberScrollState()),
        ) {
            if (hasOfficial && models.isNotEmpty()) {
                SectionLabel("图片来源")
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(text = "12306", selected = !showThirdParty) { thirdParty = false }
                    Chip(text = "第三方", selected = showThirdParty) { thirdParty = true }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (!showThirdParty) {
                CarDetailBody(car)
            } else {
                model?.let { current ->
                    // 提示是「这组图从哪来、匹不匹配」的说明，属于控件而不是图片，所以放在车型选择上方。
                    Text(
                        text = when {
                            // 官方有图时只是切到第三方补充，不能再说「12306 暂未提供」。
                            hasOfficial -> "下方图片来自第三方（路路通）"
                            lltskb?.exact == true -> "12306暂未提供该车型图片，下方图片来自第三方"
                            else -> "该车型暂无车辆图片，下方为同系列车型图片"
                        },
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (models.size > 1) {
                        // 右侧本来就显示当前车型，去掉第二行说明并收紧内边距，避免比来源胶囊高出太多。
                        OverlaySpinnerPreference(
                            items = models.map { DropdownItem(text = it.name) },
                            selectedIndex = index,
                            title = "车型",
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            onSelectedIndexChange = { modelIndex = it },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Text(
                            text = current.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    LltskbCarBody(current)
                }
            }
            SheetEndPad()
        }
    }
}

/**
 * 路路通车型参考图：它按车型而不是按车底号给图。
 * 来源提示与车型选择都在弹窗里完成，这里只展示当前车型的全部图片。
 */
@Composable
private fun LltskbCarBody(model: LltskbCarModel) {
    val context = LocalContext.current
    val gallery = model.images.map { it.url }
    model.images.forEachIndexed { index, image ->
        image.caption?.takeIf { it.isNotBlank() }?.let { caption ->
            Text(
                text = caption,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        // 不给固定高度：路路通车厢图是超宽条形图，固定高度 + Fit 会在图上下留大片空白。
        RemotePicture(
            url = image.url,
            contentDescription = image.caption ?: model.name,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (image.caption.isNullOrBlank()) 8.dp else 4.dp),
            onClick = { CarImageViewerActivity.start(context, gallery, index) },
        )
    }
}

/** 12306 车底接口给的那部分：整车图、逐辆席别与定员、座席介绍。 */
@Composable
private fun CarDetailBody(car: CarDetail) {
    val context = LocalContext.current
    // 整车图 + 逐车厢 + 座席说明拼成一组，点任意一张都能左右翻看全组大图。
    // 只用非空 URL 建组，保证列表下标与点击的那张一一对应。
    val gallery = buildList {
        CarImages.urlOf(car.exteriorUrl)?.let(::add)
        car.coaches.forEach { CarImages.urlOf(it.url)?.let(::add) }
        car.seats.forEach { CarImages.urlOf(it.url)?.let(::add) }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        CarImages.urlOf(car.exteriorUrl)?.let { url ->
            RemotePicture(
                url = url,
                contentDescription = "列车图片",
                modifier = Modifier.fillMaxWidth().height(180.dp),
                onClick = { CarImageViewerActivity.start(context, gallery, gallery.indexOf(url)) },
            )
        }
        if (car.coaches.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            SectionTitle("车厢编组")
            car.coaches.forEach { coach ->
                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text(text = coach.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    // 车厢图本身是约 4:1 的宽图，按原始比例铺满宽度即可；
                    // 固定 120dp + Fit 会在图上下留出约 30dp 空白，看起来像多余的间距。
                    CarImages.urlOf(coach.url)?.let { url ->
                        RemotePicture(
                            url = url,
                            contentDescription = coach.name,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                CarImageViewerActivity.start(context, gallery, gallery.indexOf(url))
                            },
                        )
                    }
                }
            }
        }
        if (car.seats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            SectionTitle("座席说明")
            car.seats.forEach { seat ->
                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text(text = seat.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = seat.description,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    CarImages.urlOf(seat.url)?.let { url ->
                        RemotePicture(
                            url = url,
                            contentDescription = seat.name,
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            onClick = {
                                CarImageViewerActivity.start(context, gallery, gallery.indexOf(url))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(76.dp),
        )
        Text(text = value, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

/** 与 InfoRow 同一套左右结构，右侧换成强调色链接：卡片正文里不该站一个大按钮。 */
@Composable
private fun LinkRow(label: String, text: String, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = text,
            fontSize = 13.sp,
            color = colors.primary,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

/**
 * 纵向时间轴。到达与出发当成一个整体与圆点居中对齐，竖轴分上下两段画，
 * 因此在终到站的圆点处收口。点击站点看里程、所属局、管辖段与所属地区，长按仍是车站大屏。
 */
@Composable
private fun TimelineCard(
    detail: TrainDetail,
    vm: TrainQueryViewModel,
    onOpenStation: (telecode: String, date: String) -> Unit,
    onShowJourney: () -> Unit = {},
) {
    // -1 = 全部收起。原先默认 0 会把始发站展开，而且点击时「收起」被写成 0，
    // 正好等于始发站的序号，根本关不掉。
    var expandedOrder by rememberSaveable { mutableStateOf(-1) }
    val expandedIndex = detail.rows.indexOfFirst { it.stop.order == expandedOrder }
    val expandedTelecode = detail.rows.getOrNull(expandedIndex)?.stop?.telecode
    LaunchedEffect(expandedIndex, expandedTelecode) {
        // 始发站里程恒为 0，不值得为它白打一次板面接口
        if (expandedIndex > 0) expandedTelecode?.let { vm.loadMileage(it) }
    }

    // 今日列车每 15 秒校正一次位置；非今天 refreshProgress() 自己会置空
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm.date, tick) { vm.refreshProgress() }
    LaunchedEffect(vm.date) {
        while (true) {
            delay(15_000)
            tick++
        }
    }

    // 进度线要按圆点位置落笔，而行高随展开/折叠变化，只能实测。
    // 存 LayoutCoordinates 而不是实测坐标：绘制时才换算成时间轴自己的坐标系，
    // 滚动只是整层平移，线、涟漪、圆点始终锁在一起，不会出现拖影。
    val timelineCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val dotCoords = remember { mutableStateMapOf<Int, LayoutCoordinates>() }
    val colors = MiuixTheme.colorScheme
    val progress = vm.progress
    val ripple = progress != null && progress.atStation &&
        progress.reachedIndex < detail.rows.lastIndex
    // 只交出 State，不在组合阶段读 .value：涟漪每帧重绘图层，绝不带着整张卡片重组
    val ripplePhase: State<Float>? = if (ripple) {
        rememberInfiniteTransition(label = "progressRipple").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
            label = "progressRipplePhase",
        )
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardEdge, vertical = 5.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        SectionTitle("时刻表")
        Spacer(modifier = Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { timelineCoords.value = it }
                // 进度线必须压在行内灰线**之上**：drawBehind 在内容下层，每行自己的半透明灰线
                // 会盖住它，混出「主题色叠灰」的脏色。drawWithContent 先画内容再画进度线，
                // 坐标系仍是这块 Column（换算逻辑不变，滚动依旧整体平移无拖影）。
                .drawWithContent {
                    drawContent()
                    val prog = progress ?: return@drawWithContent
                    val host = timelineCoords.value ?: return@drawWithContent
                    // 圆点中心相对时间轴原点；圆点脱离布局时整条进度线不画，绝不画在旧位置
                    fun center(index: Int): Offset? {
                        val dot = dotCoords[index] ?: return null
                        if (!dot.isAttached) return null
                        val at = host.localPositionOf(dot, Offset.Zero)
                        return Offset(at.x + dot.size.width / 2f, at.y + dot.size.height / 2f)
                    }
                    val origin = center(0) ?: return@drawWithContent
                    val reached = center(prog.reachedIndex) ?: return@drawWithContent
                    val last = detail.rows.lastIndex
                    val targetY = when {
                        prog.reachedIndex >= last || prog.atStation -> reached.y
                        else -> (reached.y + (center(prog.reachedIndex + 1)?.y ?: return@drawWithContent)) / 2f
                    }
                    drawLine(
                        color = colors.primary,
                        start = Offset(origin.x, origin.y),
                        end = Offset(origin.x, targetY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    val phase = ripplePhase?.value ?: return@drawWithContent
                    drawCircle(
                        color = colors.primary.copy(alpha = (1f - phase) * 0.5f),
                        radius = 6.dp.toPx() + phase * 14.dp.toPx(),
                        center = Offset(origin.x, targetY),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                },
        ) {
            detail.rows.forEachIndexed { index, row ->
                TimelineRow(
                    row = row,
                    isFirst = index == 0,
                    isLast = index == detail.rows.lastIndex,
                    expanded = row.stop.order == expandedOrder,
                    mileage = vm.mileageFor(row.stop.telecode),
                    offline = vm.offline,
                    changeNote = vm.changesAt[row.stop.telecode],
                    routeLines = vm.offlineRoutes[row.stop.telecode]?.lines.orEmpty(),
                    onDot = { dotCoords[index] = it },
                    onClick = { expandedOrder = if (expandedOrder == row.stop.order) -1 else row.stop.order },
                    onLongClick = { onOpenStation(row.stop.telecode, vm.date) },
                )
            }
        }
        // 经由来自离线线路表（含线路所），没装库时不给入口
        if (vm.journey.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            LinkRow("查看经由", "含线路所与逐站里程", onClick = onShowJourney)
        }
    }
}

/** 时间轴按固定行带排版，圆点、站名首行与时刻整体都落在同一条基准线上。 */
private val TimelineLineHeight = 26.dp

/** 换号提示的行色：Miuix 调色板没有 warning 位，取深橙（琥珀两档之下），深浅色下都压得住。 */
private val TrainChangeOrange = Color(0xFFE65100)

@Composable
private fun TimelineTime(text: String, color: Color, dayDiff: Int = 0) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TimelineLineHeight),
        contentAlignment = Alignment.CenterEnd,
    ) {
        // 跨天标成右上角小字，与概览卡/板面行的「+1」同一套读法
        Row(verticalAlignment = Alignment.Top) {
            Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = color)
            if (dayDiff > 0) {
                Text(
                    text = "+$dayDiff",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp, start = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    row: ScheduleRow,
    isFirst: Boolean,
    isLast: Boolean,
    expanded: Boolean,
    mileage: Int?,
    offline: Boolean = false,
    changeNote: String? = null,
    routeLines: List<String> = emptyList(),
    onDot: (LayoutCoordinates) -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    val stop = row.stop
    // 始发站没有到达、终到站没有出发（接口给的是 "----"，已归一为 null）
    // 主轴是实时时刻：拿到正晚点就把图定推后，图定本身退到副文案
    val arrive = if (isFirst) null else row.displayArrive
    val depart = if (isLast) null else row.displayDepart
    // 始发站是基准日，跨天只对之后的站有意义；晚点推过零点还要再进一天
    val dayDiff = if (isFirst) 0 else stop.dayDiff + row.dayShift
    // 到与发当一个整体：两个时刻时圆点压在两行正中，站名同步抬到同一条线
    val twoTimes = arrive != null && depart != null
    val dotY = if (twoTimes) TimelineLineHeight else TimelineLineHeight / 2

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(56.dp)) {
            // 始发站没有到达，第一行画的是发车，晚点判定也得跟着看发车
            val headLate = if (arrive != null) row.arriveLate else row.departLate
            TimelineTime(arrive ?: depart ?: "—", if (headLate) colors.error else colors.onSurface)
            if (arrive != null && depart != null) {
                TimelineTime(depart, if (row.departLate) colors.error else colors.onSurfaceVariantSummary)
            }
        }
        Box(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight()
        ) {
            val line = colors.onSurfaceVariantSummary.copy(alpha = 0.3f)
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .height(dotY)
                        .width(2.dp)
                        .background(line),
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = dotY)
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(line),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = dotY - 4.dp)
                    .size(8.dp)
                    .onGloballyPositioned { coords -> onDot(coords) }
                    .clip(CircleShape)
                    // 晚点不再换成红点：进度线终点正好落在圆点上，两种颜色会互相盖住；
                    // 晚点信息由红色时刻文字与「晚 N 分」标签承担
                    .background(colors.primary),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, top = dotY - TimelineLineHeight / 2, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TimelineLineHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stop.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (isFirst) StopLabel("始发")
                if (isLast) StopLabel("终到")
                if (dayDiff > 0) DayBadge(dayDiff)
                Spacer(modifier = Modifier.weight(1f))
                if (row.delayMinutes > 0) {
                    Text(text = "晚${row.delayMinutes}分", fontSize = 11.sp, color = colors.error)
                }
            }
            // 换号站提示：只有联网态查得到（库没有逐站车次号），橙色独立一行压在副文案上
            if (changeNote != null) {
                Text(
                    text = "•本次列车自当前车站起车次号变更为$changeNote",
                    fontSize = 11.sp,
                    color = TrainChangeOrange,
                )
            }
            val summary = stopSummary(row, isFirst)
            if (summary.isNotBlank()) {
                // 副文案是图定与旅时，红色只留给主时刻列的晚点值
                Text(text = summary, fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(2.dp))
                InfoRow(
                    label = "里程",
                    value = when {
                        isFirst -> "距始发 0km"
                        // 离线态根本不打板面，null 只能是「库里也没这趟车的里程」，不能让「获取中」空转
                        mileage == null -> if (offline) "离线库无数据" else "获取中…"
                        mileage == 0 -> "该站板面无此车次"
                        else -> "距始发 ${mileage}km"
                    },
                )
                // 经由来自离线线路表；没装库或这趟车接不上径路时整行不出现，不显示空壳
                if (mileage != null && !isFirst) {
                    InfoRow(
                        label = "经由",
                        value = if (routeLines.isEmpty()) "—" else routeLines.joinToString("、"),
                    )
                }
                val (bureauCode, stationUnit) = parseStationCorporation(stop.stationCorporation)
                bureauCode?.let { railwayBureauName(it)?.let { name -> InfoRow("所属局", name) } }
                stationUnit?.let { InfoRow("所属站段", it) }
                StationRepository.byCode(context, stop.telecode)?.city
                    ?.takeIf { it.isNotBlank() }
                    ?.let { InfoRow("所属地区", it) }
            }
        }
    }
}

@Composable
private fun StopLabel(text: String) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text = text, fontSize = 10.sp, color = colors.onSurfaceVariantSummary)
    }
}

/** 跨天徽标：贴在站名同一行，比埋在副文案里「+1天」好认。 */
@Composable
private fun DayBadge(dayDiff: Int) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.primary.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = "+${dayDiff}天",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colors.primary,
        )
    }
}

/** 折叠态副文案只留图定到发、停留与累计运行时分；跨天已由徽标表达，不再重复。 */
private fun stopSummary(row: ScheduleRow, isFirst: Boolean): String = buildList {
    val stop = row.stop
    if (row.arriveLate || row.departLate) {
        val planned = listOfNotNull(
            row.plannedArriveText?.let { "到 $it" },
            row.plannedDepartText?.let { "发 $it" },
        )
        if (planned.isNotEmpty()) add("图定${planned.joinToString("/")}")
    }
    if (!isFirst && stop.dwellMinutes > 0) add("停留${stop.dwellMinutes}分")
    if (!isFirst && stop.minutesFromStart > 0) add("运行${chineseDuration(stop.minutesFromStart)}")
}.joinToString(" · ")

/**
 * 车站管辖单位串 `H#南京`：`#` 前是车站所属路局码（不是列车担当局），后面是站段名。
 * 名字已经带「站/段/公司/中心」的不再补字，其余补成「南京站」这种读法。
 */
private fun parseStationCorporation(raw: String): Pair<String?, String?> {
    if (raw.isBlank()) return null to null
    val split = '#' in raw
    val code = if (split) raw.substringBefore('#').trim() else ""
    val name = (if (split) raw.substringAfter('#') else raw).trim()
    return code.takeIf { it.isNotEmpty() } to name.takeIf { it.isNotEmpty() }?.let {
        if (it.endsWith("站") || it.endsWith("段") || it.endsWith("公司") || it.endsWith("中心")) it else "${it}站"
    }
}

private val PlanWeekLabels = listOf("日", "一", "二", "三", "四", "五", "六")

/** 开行计划是星期对齐的连续日期条：每页固定 5 行 35 天，页与页首尾相接，不按自然月重新起排。 */
private const val PlanRows = 5
private const val PlanCells = PlanRows * 7

/**
 * 分页区间：锚点 = 今天所在月份 1 号所在周的周日，初始页从锚点起排，
 * 保证 1 号必然落在初始页的第一行；数据早于锚点时向前整页平铺，晚于锚点则开头几天自然灰底。
 */
internal class PlanPages(val start: LocalDate, val pageCount: Int, val initialPage: Int)

internal fun planPages(running: Map<String, Boolean>, today: LocalDate = LocalDate.now()): PlanPages? {
    val dates = running.keys.mapNotNull {
        runCatching { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
    }
    val head = dates.minOrNull() ?: return null
    val tail = dates.maxOrNull() ?: return null
    // 锚点对准 1 号所在周的周日，之后每页正好 5 个整周，列与星期表头始终对得上
    val firstOfMonth = today.withDayOfMonth(1)
    val anchor = firstOfMonth.minusDays((firstOfMonth.dayOfWeek.value % 7).toLong())
    val backPages = if (head.isBefore(anchor)) {
        (ChronoUnit.DAYS.between(head, anchor).toInt() + PlanCells - 1) / PlanCells
    } else {
        0
    }
    val start = anchor.minusDays(backPages.toLong() * PlanCells)
    val span = ChronoUnit.DAYS.between(start, tail).toInt() + 1
    val pageCount = maxOf(1, (span + PlanCells - 1) / PlanCells)
    val initial = (ChronoUnit.DAYS.between(start, today).toInt() / PlanCells).coerceIn(0, pageCount - 1)
    return PlanPages(start, pageCount, initial)
}

/**
 * 开行计划：按周日→周六连排，一页 5 行 35 天，左右滑就是往后/往前 5 周。
 * 初始页从「本月 1 号所在周的周日」起排，1 号固定出现在第一行；
 * 不按自然月分页：以前每月重置网格并补相邻月的日子，翻页时上页最后一行会原样出现在下页第一行。
 */
@Composable
private fun RunningPlanCard(plan: RunningDays) {
    val running = remember(plan.days) { plan.days.toMap() }
    val pages = remember(plan.days) { planPages(running) } ?: return
    val pagerState = rememberPagerState(
        initialPage = pages.initialPage,
        pageCount = { pages.pageCount },
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardEdge, vertical = 5.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("开行计划", modifier = Modifier.weight(1f))
            PlanLegend(colors = MiuixTheme.colorScheme.primary, text = "开行")
            Spacer(modifier = Modifier.width(10.dp))
            PlanLegend(colors = MiuixTheme.colorScheme.surfaceContainerHigh, text = "不开行/无计划")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            PlanWeekLabels.forEach {
                Text(
                    text = it,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            PlanDaysGrid(start = pages.start.plusDays((page * PlanCells).toLong()), running = running)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = planSummary(plan),
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 标题行右侧的颜色图例：小圆点对应日历胶囊的底色。 */
@Composable
private fun PlanLegend(colors: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(colors, CircleShape),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 注释只报数据时段与开行统计，颜色含义已由标题行的图例承担。 */
private fun planSummary(plan: RunningDays): String {
    val dates = plan.days.mapNotNull {
        runCatching { LocalDate.parse(it.first, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
    }
    val head = dates.minOrNull() ?: return ""
    val tail = dates.maxOrNull() ?: return ""
    val total = plan.days.size
    val run = plan.days.count { it.second }
    return "${formatPlanDay(head)}至${formatPlanDay(tail)}，共${total}天，开行${run}天，不开行${total - run}天"
}

/** `2026-10-01` 不补零，跟用户口径一致。 */
private fun formatPlanDay(day: LocalDate): String = "${day.year}-${day.monthValue}-${day.dayOfMonth}"

/**
 * 一页 5 行 35 天，从 [start]（周日）起连排，翻到下一页就是紧接着的 35 天，
 * 页与页不重叠，所以不会出现「上一页最后一行又在下一页第一行」。
 */
@Composable
private fun PlanDaysGrid(start: LocalDate, running: Map<String, Boolean>) {
    val today = remember { LocalDate.now() }
    val days = remember(start) { (0 until PlanCells).map { start.plusDays(it.toLong()) } }
    val states = days.map { isRunningOn(it, running) }
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(PlanRows) { row ->
            val rowStart = row * 7
            val rowEnd = rowStart + 7
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                // 一行内按状态切成几段；跨行的连续段在行边界处断开，各自圆角
                var index = rowStart
                while (index < rowEnd) {
                    var end = index + 1
                    while (end < rowEnd && states[end] == states[index]) end++
                    PlanRun(dates = days.subList(index, end), running = running, today = today)
                    index = end
                }
            }
        }
    }
}

/** 没计划的日期和计划里 flag=0 的日期都是灰底，界面上不作区分，所以合并判定也按这一口径。 */
private fun isRunningOn(day: LocalDate, running: Map<String, Boolean>): Boolean =
    running[day.format(DateTimeFormatter.BASIC_ISO_DATE)] == true

/**
 * 连续同状态的日期合成一条胶囊，每根条的头尾都圆角。
 * 今天用反色圆徽标标出（开行胶囊里白底主色字、灰胶囊里主色底白字），
 * 徽标走 drawBehind 不撑高行，也不拆断胶囊；1 号恰好是今天时徽标优先于「11月」标签。
 */
@Composable
private fun RowScope.PlanRun(dates: List<LocalDate>, running: Map<String, Boolean>, today: LocalDate) {
    val colors = MiuixTheme.colorScheme
    val isRunning = isRunningOn(dates.first(), running)
    Box(
        modifier = Modifier
            .weight(dates.size.toFloat())
            .clip(RoundedCornerShape(6.dp))
            .background(if (isRunning) colors.primary else colors.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
        ) {
            dates.forEach { day ->
                val isToday = day == today
                val isMonthStart = day.dayOfMonth == 1 && !isToday
                if (isToday) {
                    Text(
                        text = day.dayOfMonth.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (isRunning) colors.primary else colors.onPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .drawBehind {
                                drawCircle(
                                    color = if (isRunning) colors.onPrimary else colors.primary,
                                    radius = 9.dp.toPx(),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                )
                            },
                    )
                } else {
                    Text(
                        // 每月 1 号用月份名顶替日期，补位的月末日子也照这个规则走
                        text = if (isMonthStart) "${day.monthValue}月" else day.dayOfMonth.toString(),
                        fontSize = if (isMonthStart) 10.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = when {
                            isRunning -> colors.onPrimary
                            isMonthStart -> colors.primary
                            else -> colors.onSurfaceVariantSummary
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
