package com.crinfo.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinfo.app.data.Route
import com.crinfo.app.data.model.Seat
import com.crinfo.app.data.model.primaryTrainCode
import com.crinfo.app.data.model.TrainKind
import com.crinfo.app.ui.components.AppScaffold
import com.crinfo.app.ui.components.InlineSwitchRow
import com.crinfo.app.ui.components.LocalAppSettings
import com.crinfo.app.ui.settings.OfflineDbViewModel
import com.crinfo.app.ui.settings.SettingsViewModel
import com.crinfo.app.ui.components.AppTopBar
import com.crinfo.app.ui.components.BarContentGap
import com.crinfo.app.ui.components.Chip
import com.crinfo.app.ui.components.ErrorState
import com.crinfo.app.ui.components.EmptyResultState
import com.crinfo.app.ui.components.FavoriteAction
import com.crinfo.app.ui.components.FavoriteEntry
import com.crinfo.app.ui.components.FavoriteSection
import com.crinfo.app.ui.components.LocalBarColor
import com.crinfo.app.ui.components.MonthDatePickerDialog
import com.crinfo.app.ui.components.OnlineToggleAction
import com.crinfo.app.ui.components.PullToRefreshTextsCn
import com.crinfo.app.ui.components.SectionLabel
import com.crinfo.app.ui.components.StationPickerSheet
import com.crinfo.app.ui.components.rememberHapticTap
import com.crinfo.app.ui.components.TripRow
import com.crinfo.app.ui.query.StationToStationViewModel
import com.crinfo.app.ui.query.TimeBucket
import com.crinfo.app.ui.query.TripFilter
import com.crinfo.app.ui.query.TripSort
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 卡片与日期条共用的左右边距，保证选中的日期标签能对齐卡片边缘 */
internal val CardEdge = 12.dp

/**
 * 全站统一日期格式：`10月31日 今天`、`10月30日 昨天`、`11月2日 周一`。
 * 前后两天内用相对词，其余用星期，两者不同时出现。
 */
fun dayLabel(date: LocalDate): String {
    val head = "${date.monthValue}月${date.dayOfMonth}日"
    val relative = when (ChronoUnit.DAYS.between(LocalDate.now(), date)) {
        -2L -> "前天"
        -1L -> "昨天"
        0L -> "今天"
        1L -> "明天"
        2L -> "后天"
        else -> null
    }
    return if (relative != null) "$head $relative" else "$head 周${WEEKDAYS[date.dayOfWeek.value - 1]}"
}

@Composable
fun StationToStationHome(
    vm: StationToStationViewModel,
    settings: SettingsViewModel,
    dbVm: OfflineDbViewModel,
    onOpenResults: () -> Unit,
) {
    QueryHome(vm, settings, dbVm, onOpenResults)
}

@Composable
fun StationToStationResults(
    vm: StationToStationViewModel,
    settings: SettingsViewModel,
    onBack: () -> Unit,
    onOpenTrain: (code: String, date: String) -> Unit,
) {
    QueryResults(vm, settings, onBack, onOpenTrain)
}

/* ---------------- 首页：条件表单 + 历史 ---------------- */

@Composable
private fun QueryHome(
    vm: StationToStationViewModel,
    settings: SettingsViewModel,
    dbVm: OfflineDbViewModel,
    onOpenResults: () -> Unit,
) {
    val online = LocalAppSettings.current.ticketOnline
    LaunchedEffect(online) { vm.offline = !online }

    fun submit() {
        vm.offline = !online
        // 关掉联网又没装库 → 拦下这次查询，装完自动接着提交
        if (!online && dbVm.interceptWhenMissing { submit() }) return
        vm.query()
        onOpenResults()
    }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(appBarState)

    AppScaffold(
        topBar = {
            AppTopBar(
                color = LocalBarColor.current,
                title = "车票查询",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                // padding 挂在 scroll 之后等于 contentPadding，内容才会从栏下滑过
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RouteCard(
                from = vm.from.name,
                to = vm.to.name,
                onFrom = { pickFrom = true },
                onTo = { pickTo = true },
                onSwap = { vm.swap() },
            )
            Spacer(modifier = Modifier.height(12.dp))
            DateCard(date = vm.date, onClick = { pickDate = true })
            Spacer(modifier = Modifier.height(12.dp))
            KindCard(vm.filter.kinds) { vm.filter = vm.filter.copy(kinds = it) }
            Spacer(modifier = Modifier.height(12.dp))
            // 两个开关并排一行：都是「怎么查」的口径，不该各占一行把查询按钮推远
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineSwitchRow(label = "模糊查询", checked = vm.fuzzy, onCheckedChange = { vm.fuzzy = it })
                InlineSwitchRow(label = "联网查询", checked = online, onCheckedChange = { settings.updateTicketOnline(it) })
            }
            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = rememberHapticTap { submit() },
                // Miuix Button 默认走 secondaryVariant（淡粉），主操作要显式用强调色
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
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
                title = "收藏路线",
                entries = vm.favoritesRoutes.map { route ->
                    FavoriteEntry(route.display, onOpen = { vm.selectRoute(route); submit() }, onRemove = { vm.removeFavorite(route) })
                },
            )
            Spacer(modifier = Modifier.height(20.dp))
            HistorySection(vm) { route ->
                vm.selectRoute(route)
                submit()
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        StationPickerSheet(pickFrom, "选择出发站", { pickFrom = false }) { vm.selectFrom(it) }
        StationPickerSheet(pickTo, "选择到达站", { pickTo = false }) { vm.selectTo(it) }
        MonthDatePickerDialog(pickDate, vm.dates, LocalDate.parse(vm.date), { pickDate = false }) {
            vm.setDate(it)
        }
    }
}

@Composable
internal fun DateCard(date: String, onClick: () -> Unit, label: String = "出发日期") {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
        showIndication = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel(label)
                Text(text = dayLabel(LocalDate.parse(date)), fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = "选择日期",
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindCard(selected: Set<TrainKind>, onChange: (Set<TrainKind>) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // 收起时箭头朝上、展开后朝下（ChevronForward 基线朝右，+90 向下、-90 向上）
    val rotation by animateFloatAsState(if (expanded) 90f else 270f, label = "车次类型箭头")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        // 内衬改放到 clickable 内侧：按压层左右铺到卡片边缘，而不是缩在内容区
        insideMargin = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selected.isEmpty()) "车次类型" else "车次类型 · 已选 ${selected.size}",
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = if (expanded) "收起车次类型" else "展开车次类型",
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TrainKind.entries.forEach { kind ->
                        Chip(
                            text = kind.chipLabel,
                            selected = kind in selected,
                        ) {
                            onChange(if (kind in selected) selected - kind else selected + kind)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistorySection(vm: StationToStationViewModel, onOpen: (Route) -> Unit) {
    // 空历史整块不渲染，避免首页出现一段无意义的占位
    if (vm.history.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("历史查询")
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
            vm.history.forEach { route ->
                Chip(
                    text = route.display,
                    selected = false,
                    onLongClick = { vm.removeRoute(route) },
                ) {
                    onOpen(route)
                }
            }
        }
    }
}

/* ---------------- 结果页 ---------------- */

@Composable
private fun QueryResults(
    vm: StationToStationViewModel,
    settings: SettingsViewModel,
    onBack: () -> Unit,
    onOpenTrain: (String, String) -> Unit,
) {
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(appBarState)
    // 结果页顶栏就地翻转联网开关：设置一变就同步 offline 标志并重查。
    // 进页时数据已由首页查询带出，首帧只记基准值、不触发重查。
    val online = LocalAppSettings.current.ticketOnline
    var seenOnline by rememberSaveable { mutableStateOf(online) }
    LaunchedEffect(online) {
        if (online == seenOnline) return@LaunchedEffect
        seenOnline = online
        vm.offline = !online
        if (!vm.loading) vm.query()
    }
    // 用 subtitleColor 淡出而不是清空字符串，是为了保留 smallSubtitle 的布局槽位，
    // 避免折叠时顶栏高度跳动

    AppScaffold(
        topBar = {
            AppTopBar(
                color = LocalBarColor.current,
                title = "车票查询",
                subtitle = "${vm.from.name} → ${vm.to.name}",
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
                    OnlineToggleAction(online = online, onClick = { settings.updateTicketOnline(!online) })
                    IconButton(onClick = { showFilter = true }) {
                        Icon(
                            imageVector = MiuixIcons.Filter,
                            contentDescription = "筛选",
                            modifier = Modifier.size(22.dp),
                            tint = if (vm.filter.activeCount > 0) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
                bottomContent = {
                    val dateState = rememberLazyListState()
                    val selectedIndex = vm.dates.indexOfFirst { it.toString() == vm.date }
                    LaunchedEffect(selectedIndex) {
                        // 实测：有 contentPadding 时 offset=0 就已把标签左边缘落在卡片边距上，
                        // 传 ±CardEdge 反而会分别顶到屏幕边 / 多出一格
                        // 0 也得滚：离线选了过去日切回联网，今天回落到底列表首位，
                        // 不滚的话栏还停在后半截，看着像没回落
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
                                    onClick = {
                                        if (vm.date == day.toString()) return@Chip
                                        vm.setDate(day)
                                        vm.query()
                                    },
                                )
                            }
                        }
                        // 月历入口：chip 条只给得下近几天的紧迫感，跨半月挑日子还是翻日历直观
                        IconButton(onClick = { pickDate = true }) {
                            Icon(
                                imageVector = MiuixIcons.Months,
                                contentDescription = "月历选择日期",
                                modifier = Modifier.size(22.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                    // 与卡下留白（卡片自带 8dp + 栏尾 12dp）对称
                    Spacer(modifier = Modifier.height(20.dp))
                    // 路线卡钉在栏内：随顶栏常驻不滚走，但它下方不留白——
                    // 列表满幅从栏底起滚，栏与内容间的可见间隔由尾部 Spacer 常驻承担（同板面页）
                    RouteCard(
                        from = vm.from.name,
                        to = vm.to.name,
                        onFrom = { pickFrom = true },
                        onTo = { pickTo = true },
                        onSwap = {
                            vm.swap()
                            vm.query()
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                },
            )
        },
    ) { innerPadding ->
        // OverlayDialog / OverlayBottomSheet 依赖 Scaffold 提供的弹窗宿主，必须留在其内容里
        // 列表满幅（同板面页）：内容要从顶栏与底栏下方穿过，两处栏的模糊才有东西可采
        Results(
            vm = vm,
            scrollBehavior = scrollBehavior,
            innerPadding = innerPadding,
            onOpenTrain = onOpenTrain,
            onOpenFilter = { showFilter = true },
        )

        StationPickerSheet(pickFrom, "选择出发站", { pickFrom = false }) {
            vm.selectFrom(it)
            vm.query()
        }
        StationPickerSheet(pickTo, "选择到达站", { pickTo = false }) {
            vm.selectTo(it)
            vm.query()
        }
        FilterDialog(
            show = showFilter,
            current = vm.filter,
            presentSeats = vm.presentSeats,
            presentFromStations = vm.presentFromStations,
            presentToStations = vm.presentToStations,
            fuzzy = vm.fuzzy,
            offline = vm.offline,
            onFuzzy = { vm.fuzzy = it },
            onDismiss = { showFilter = false },
            onChange = { vm.filter = it },
        )
        MonthDatePickerDialog(pickDate, vm.dates, LocalDate.parse(vm.date), { pickDate = false }) {
            vm.setDate(it)
            vm.query()
        }
    }
}

@Composable
private fun RouteCard(
    from: String,
    to: String,
    onFrom: () -> Unit,
    onTo: () -> Unit,
    onSwap: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 顶栏到首个部件的间距已由 AppScaffold 统一给出，这里只留卡片下沿的呼吸位
            .padding(start = CardEdge, end = CardEdge, bottom = 8.dp),
        // 内衬改放到各点击列的内侧：出发/到达两列的按压层分别铺到卡片左右边缘
        insideMargin = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onFrom)
                    .padding(start = 16.dp, end = 4.dp),
            ) {
                SectionLabel("出发站")
                Text(text = from, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
            IconButton(onClick = onSwap) {
                Icon(
                    imageVector = MiuixIcons.ConvertFile,
                    contentDescription = "交换出发到达站",
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onTo)
                    .padding(start = 4.dp, end = 16.dp),
                horizontalAlignment = Alignment.End,
            ) {
                SectionLabel("到达站")
                Text(text = to, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun Results(
    vm: StationToStationViewModel,
    scrollBehavior: ScrollBehavior,
    innerPadding: PaddingValues,
    onOpenTrain: (String, String) -> Unit,
    onOpenFilter: () -> Unit,
) {
    val direction = LocalLayoutDirection.current
    // 顶栏到列表首项的间距只在静止位存在，一滚就随内容走，
    // 满幅视口统一用 contentPadding 承担，见 BarContentGap 的注释
    val listTop = innerPadding.calculateTopPadding() - BarContentGap
    PullToRefresh(
        // 取数一律走刷新圆环（点查询、进详情、下拉都一样），见 VM.query
        isRefreshing = vm.refreshing,
        onRefresh = rememberHapticTap { vm.pullRefresh() },
        refreshTexts = PullToRefreshTextsCn,
        modifier = Modifier.fillMaxSize(),
        // 交给 PullToRefresh 统一转发滚动事件：它内部已挂 nestedScroll，
        // 再往 LazyColumn 上挂一份会让顶栏折叠与下拉刷新互相抢事件
        topAppBarScrollBehavior = scrollBehavior,
        contentPadding = PaddingValues(top = listTop),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().scrollEndHaptic(),
            // 栏高做成列表的 contentPadding，行才能滚进栏下方供模糊采样
            contentPadding = PaddingValues(
                top = listTop,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start = innerPadding.calculateLeftPadding(direction),
                end = innerPadding.calculateRightPadding(direction),
            ),
        ) {
            item { StatusLine(vm, onOpenFilter) }
            items(vm.visibleTrips) { trip ->
                TripRow(trip = trip, onClick = { onOpenTrain(primaryTrainCode(trip.trainCode), trip.date) })
            }
        }
    }
}

@Composable
private fun StatusLine(vm: StationToStationViewModel, onOpenFilter: () -> Unit) {
    // 取数中整行静音：重试瞬间 error 被清、旧列表又被清空，空态文案会闪一行；
    // loading 一律由顶部刷新圆环呈现（见 StationToStationViewModel.query），这里不再打文字
    if (vm.loading) return
    if (vm.error != null) {
        ErrorState(message = vm.error!!, onRetry = { vm.query() })
        return
    }
    val filteredEmpty = vm.trips.isNotEmpty() && vm.visibleTrips.isEmpty()
    val message = when {
        filteredEmpty -> "没有符合条件的结果"
        vm.trips.isEmpty() -> "未查询到车次"
        else -> null
    }
    if (message == null) {
        SectionLabel(
            text = "共 ${vm.trips.size} 趟 · 显示 ${vm.visibleTrips.size} 趟",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        return
    }
    EmptyResultState(
        message = message,
        filterActive = filteredEmpty,
        onFilter = onOpenFilter,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterDialog(
    show: Boolean,
    current: TripFilter,
    presentSeats: List<Seat>,
    presentFromStations: List<Pair<String, String>>,
    presentToStations: List<Pair<String, String>>,
    fuzzy: Boolean,
    offline: Boolean,
    onFuzzy: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onChange: (TripFilter) -> Unit,
) {
    var draft by remember(show) { mutableStateOf(current) }

    OverlayDialog(show = show, title = "筛选与排序", onDismissRequest = onDismiss) {
        // 外层不滚动，只为把「重置/确定」钉在弹窗底部；分区列表进内层滚动 Column
        Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            // fill = false：内容装得下时按内容贴合，不把弹窗顶到 OverlayDialog 的高度上限
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
            ) {
                InlineSwitchRow(
                    label = "模糊查询",
                    checked = fuzzy,
                    onCheckedChange = onFuzzy,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                // 站列表来自本次真实结果，没扩出第二个站就没有可筛的；关模糊只隐藏、不清空已选
                if (fuzzy && presentFromStations.size > 1) {
                    FilterSection("出发站") {
                        presentFromStations.forEach { (code, name) ->
                            Chip(
                                text = name,
                                selected = code in draft.fromStations,
                            ) {
                                draft = draft.copy(
                                    fromStations = if (code in draft.fromStations) draft.fromStations - code else draft.fromStations + code,
                                )
                            }
                        }
                    }
                }
                if (fuzzy && presentToStations.size > 1) {
                    FilterSection("到达站") {
                        presentToStations.forEach { (code, name) ->
                            Chip(
                                text = name,
                                selected = code in draft.toStations,
                            ) {
                                draft = draft.copy(
                                    toStations = if (code in draft.toStations) draft.toStations - code else draft.toStations + code,
                                )
                            }
                        }
                    }
                }
                FilterSection("车次类型") {
                    TrainKind.entries.forEach { kind ->
                        Chip(
                            text = kind.chipLabel,
                            selected = kind in draft.kinds,
                        ) {
                            draft = draft.copy(
                                kinds = if (kind in draft.kinds) draft.kinds - kind else draft.kinds + kind,
                            )
                        }
                    }
                }
                FilterSection("出发时段") {
                    TimeBucket.entries.forEach { bucket ->
                        Chip(
                            text = bucket.label,
                            selected = bucket in draft.buckets,
                        ) {
                            draft = draft.copy(
                                buckets = if (bucket in draft.buckets) draft.buckets - bucket else draft.buckets + bucket,
                            )
                        }
                    }
                }
                if (presentSeats.isNotEmpty()) {
                    // 离线库只给席别种类、不给余票，所以这里的语义是「只看收录了该席别的车」
                    FilterSection(if (offline) "席别（离线只看库内收录的席别）" else "席别（选中即只看该席别有票）") {
                        presentSeats.forEach { seat ->
                            Chip(text = seat.label, selected = draft.seat == seat) {
                                draft = draft.copy(seat = if (draft.seat == seat) null else seat)
                            }
                        }
                    }
                }
                FilterSection("附加条件") {
                    // 离线态没有余票数据，这个条件无从判断：置灰即可，不再加说明文字
                    Chip("只看有票", draft.onlyAvailable, enabled = !offline) {
                        draft = draft.copy(onlyAvailable = !draft.onlyAvailable)
                    }
                    Chip("始发站发车", draft.onlyOrigin) {
                        draft = draft.copy(onlyOrigin = !draft.onlyOrigin)
                    }
                    Chip("终到站到达", draft.onlyTerminal) {
                        draft = draft.copy(onlyTerminal = !draft.onlyTerminal)
                    }
                }
                FilterSection("排序") {
                    TripSort.entries.forEach { sort ->
                        Chip(text = sort.label, selected = draft.sort == sort) {
                            draft = draft.copy(sort = sort)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            ) {
                Button(
                    onClick = { draft = TripFilter() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(
                        text = "重置",
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        onChange(draft)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary,
                        contentColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(
                        text = "确定",
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        SectionLabel(title)
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}
