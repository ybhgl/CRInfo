package com.crinfo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crinfo.app.data.Station
import com.crinfo.app.data.model.BoardDirection
import com.crinfo.app.data.model.primaryTrainCode
import com.crinfo.app.data.model.BoardEntry
import com.crinfo.app.data.model.StationPage
import com.crinfo.app.data.model.StationView
import com.crinfo.app.data.model.TrainKind
import com.crinfo.app.ui.components.AppScaffold
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
import com.crinfo.app.ui.components.StationInfoDialog
import com.crinfo.app.ui.components.StationPickerSheet
import com.crinfo.app.ui.components.rememberHapticTap
import com.crinfo.app.ui.components.TagBadge
import com.crinfo.app.ui.query.StationQueryViewModel
import com.crinfo.app.ui.components.InlineSwitchRow
import com.crinfo.app.ui.components.LocalAppSettings
import com.crinfo.app.ui.settings.OfflineDbViewModel
import com.crinfo.app.ui.settings.SettingsViewModel
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/* ---------------- 首页：选站 + 日期 + 两个入口 ---------------- */

@Composable
fun StationHome(
    vm: StationQueryViewModel = viewModel(),
    settings: SettingsViewModel,
    dbVm: OfflineDbViewModel,
    onOpenBoard: (telecode: String, view: StationView, date: String) -> Unit,
) {
    val online = LocalAppSettings.current.stationOnline
    LaunchedEffect(online) { vm.offline = !online }

    fun openBoard(station: Station, view: StationView = vm.view) {
        vm.selectStation(station)
        vm.offline = !online
        // 只有「车次」这一路需要拦缺库：大屏演的是此刻，离线库压根给不了，它永远走实时源
        if (view == StationView.Trains && !online && dbVm.interceptWhenMissing { openBoard(station, view) }) return
        onOpenBoard(station.code, view, vm.date)
    }
    var pickStation by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(appBarState)

    AppScaffold(
        topBar = {
            AppTopBar(
                color = LocalBarColor.current,
                title = "车站查询",
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
            StationCard(vm.station.name, onClick = { pickStation = true })
            Spacer(modifier = Modifier.height(12.dp))
            DateCard(date = vm.date, label = "查询日期", onClick = { pickDate = true })
            Spacer(modifier = Modifier.height(14.dp))
            InlineSwitchRow(
                label = "联网查询",
                checked = online,
                onCheckedChange = { settings.updateStationOnline(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            Spacer(modifier = Modifier.height(22.dp))
            // 车次 = 目标日期全天；大屏 = 此刻正在演的班次。两者不是一回事，所以给两个入口
            // 横排一行：左副入口「大屏」（次级底），右主入口「车次」（强调色）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CardEdge),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = rememberHapticTap { openBoard(vm.station, StationView.Screen) },
                    // Miuix 0.9.4 没有 outlined 按钮配色，Button 默认就是次级底，正好用来做副入口
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(
                        text = "查看大屏",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Button(
                    onClick = rememberHapticTap { openBoard(vm.station, StationView.Trains) },
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary,
                        contentColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(
                        text = "查看车次",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            FavoriteSection(
                title = "收藏车站",
                entries = vm.favoriteStations.map { station ->
                    FavoriteEntry(station.name, onOpen = { openBoard(station) }, onRemove = { vm.removeFavorite(station) })
                },
            )
            Spacer(modifier = Modifier.height(20.dp))
            StationHistorySection(vm) { station -> openBoard(station) }
            Spacer(modifier = Modifier.height(24.dp))
        }

        StationPickerSheet(pickStation, "选择车站", { pickStation = false }) { vm.selectStation(it) }
        MonthDatePickerDialog(pickDate, vm.dates, LocalDate.parse(vm.date), { pickDate = false }) {
            vm.setDate(it)
        }
    }
}

@Composable
private fun StationCard(name: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardEdge),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
        showIndication = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel("车站")
                Text(text = name, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = "选择车站",
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StationHistorySection(vm: StationQueryViewModel, onOpen: (Station) -> Unit) {
    if (vm.history.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("历史车站")
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
            vm.history.forEach { station ->
                Chip(
                    text = station.name,
                    selected = false,
                    onLongClick = { vm.removeHistory(station) },
                ) {
                    onOpen(station)
                }
            }
        }
    }
}

/* ---------------- 车次页 / 大屏页 ---------------- */

/**
 * 车站板面导航条目：VM 随条目创建与出栈销毁，
 * 页内换站、换视图都只影响自己，返回上一个板面时各看各的站。
 */
@Composable
fun StationBoardEntry(
    telecode: String,
    view: StationView,
    date: String,
    vm: StationQueryViewModel = viewModel(),
    settings: SettingsViewModel,
    dbVm: OfflineDbViewModel,
    onOpenTrain: (code: String, date: String) -> Unit,
    onBack: () -> Unit,
) {
    val online = LocalAppSettings.current.stationOnline
    // 详情页顶栏就地翻转开关后要能立刻换源重查；进页首帧仍按原逻辑只起首查
    var seenOnline by rememberSaveable { mutableStateOf(online) }
    LaunchedEffect(online) {
        vm.offline = !online
        if (seenOnline != online) {
            seenOnline = online
            if (!vm.loading) vm.query()
        } else if (vm.page == null && !vm.loading) {
            vm.startBoard(telecode, view, date)
        }
    }
    StationBoardPage(vm, settings, dbVm, online, onOpenTrain, onBack)
}

@Composable
private fun StationBoardPage(
    vm: StationQueryViewModel,
    settings: SettingsViewModel,
    dbVm: OfflineDbViewModel,
    online: Boolean,
    onOpenTrain: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var pickStation by remember { mutableStateOf(false) }
    var showStationInfo by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(appBarState)

    AppScaffold(
        topBar = {
            AppTopBar(
                color = LocalBarColor.current,
                title = vm.station.name,
                subtitle = vm.page?.summary.orEmpty(),
                scrollBehavior = scrollBehavior,
                // 顶栏站名维持「换站」语义，信息弹窗只挂在车站卡片上（用户明确要求，别再改这里）
                onTitleClick = { pickStation = true },
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
                    // 大屏数据只有实时源，联网开关对它无意义 → 只在车次视图给出切换入口
                    if (vm.view == StationView.Trains) {
                        OnlineToggleAction(online = online, onClick = { settings.updateStationOnline(!online) })
                    }
                    IconButton(onClick = { showFilter = true }) {
                        Icon(
                            imageVector = MiuixIcons.Filter,
                            contentDescription = "筛选",
                            modifier = Modifier.size(22.dp),
                            tint = if (vm.filterCount > 0) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
                bottomContent = {
                    // 看哪张屏（出发/到达）用库内轮廓 Tab 条；车次/大屏互切已收回查询页入口
                    TabRowWithContour(
                        tabs = BoardDirection.entries.map { it.label },
                        selectedTabIndex = vm.direction.ordinal,
                        onTabSelected = { vm.pickDirection(BoardDirection.entries[it]) },
                        modifier = Modifier.padding(horizontal = CardEdge),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (vm.view == StationView.Trains) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val dateState = rememberLazyListState()
                        val selectedIndex = vm.dates.indexOfFirst { it.toString() == vm.date }
                        LaunchedEffect(selectedIndex) {
                            // 与车票页同口径：选中项回到首位（0）也要滚，门槛取 ≥0
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
                            // 月历入口：大屏视图恒为今天不给日期条，这个按钮也就只在车次视图出现
                            IconButton(onClick = { pickDate = true }) {
                                Icon(
                                    imageVector = MiuixIcons.Months,
                                    contentDescription = "月历选择日期",
                                    modifier = Modifier.size(22.dp),
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    // 栏底的这段留白是最后一行选择器与内容之间唯一的可见间隔：
                    // 列表一滚动，AppScaffold 的 BarContentGap 就只作用于初始位置了
                    Spacer(modifier = Modifier.height(16.dp))
                },
            )
        },
    ) { innerPadding ->
        val direction = LocalLayoutDirection.current
        // 选择器下方那 16dp 由顶栏 bottomContent 的尾部 Spacer 常驻承担；
        // BarContentGap 只在静止位存在、一滚就没了，再叠上去就成了「展开 40dp / 折叠 16dp」。
        val listTop = innerPadding.calculateTopPadding() - BarContentGap
        PullToRefresh(
            isRefreshing = vm.refreshing,
            onRefresh = rememberHapticTap { vm.pullRefresh() },
            refreshTexts = PullToRefreshTextsCn,
            // 栏高走 contentPadding：视口铺满整屏，列表才会从栏下方滑过供模糊采样
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = listTop),
            topAppBarScrollBehavior = scrollBehavior,
        ) {
            LazyColumn(
                // 撑满视口：PullToRefresh 内部是 Column 包内容，不满一屏时列表只到内容底部，
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
                // 错误/空结果的状态块排在车站卡片下方，正常的趟数标签仍在上方
                val belowCard = vm.blockBelowCard
                if (!belowCard) {
                    item { BoardStatusLine(vm, dbVm, onOpenFilter = { showFilter = true }) }
                }
                val page = vm.page
                if (page != null) {
                    item {
                        StationInfoCard(
                            page,
                            onOpenInfo = {
                                vm.loadProfile()
                                showStationInfo = true
                            },
                        )
                    }
                }
                if (belowCard) {
                    item { BoardStatusLine(vm, dbVm, onOpenFilter = { showFilter = true }) }
                }
                items(vm.visibleEntries) { entry ->
                    BoardRow(
                        entry = entry,
                        onClick = { onOpenTrain(primaryTrainCode(entry.trainCode), vm.queryDate) },
                    )
                }
            }
        }

        StationPickerSheet(pickStation, "选择车站", { pickStation = false }) { vm.selectStation(it) }
        // 弹窗数据由打开时现取（loadProfile 幂等，同站同日不重复打接口），这里只管展示。
        // 大屏视图无视联网开关（实时源只有在线），弹窗信息也必须按联网口径走
        StationInfoDialog(
            show = showStationInfo,
            profile = vm.profile,
            offline = vm.profileOffline,
            onDismiss = { showStationInfo = false },
            onPickStation = {
                showStationInfo = false
                vm.selectStation(it)
                vm.loadProfile()
            },
        )
        BoardFilterDialog(
            show = showFilter,
            kinds = vm.kinds,
            keyword = vm.keyword,
            onDismiss = { showFilter = false },
            onKinds = { vm.kinds = it },
            onKeyword = { vm.keyword = it },
            onReset = { vm.clearFilter() },
        )
        MonthDatePickerDialog(pickDate, vm.dates, LocalDate.parse(vm.date), { pickDate = false }) {
            // 车站 VM 的 setDate 内部已负责换日重查，这里只管落值
            vm.setDate(it)
        }
    }
}

/** 状态块（错误或空结果）要落在车站卡片下方；口径须与 [BoardStatusLine] 的分支一致。 */
private val StationQueryViewModel.blockBelowCard: Boolean
    get() = !loading && (error != null || (page != null && visibleEntries.isEmpty()))

@Composable
private fun BoardStatusLine(vm: StationQueryViewModel, dbVm: OfflineDbViewModel, onOpenFilter: () -> Unit) {
    // 取数中整行静音：重试瞬间 error 被清、旧页数据未就位，空态文案会闪一行；
    // loading 一律由顶部刷新圆环呈现（见 StationQueryViewModel.query），这里不再打文字
    if (vm.loading) return
    if (vm.error != null) {
        // 离线态缺库时「重试」是句空话——重查还是缺库。按钮要说清能干什么。
        val needInstall = vm.needsOfflineDb
        ErrorState(
            message = vm.error!!,
            retryText = if (needInstall) "安装数据库" else "重试",
            onRetry = { if (needInstall) dbVm.promptInstall() else vm.query() },
        )
        return
    }
    val page = vm.page
    val empty = page != null && vm.visibleEntries.isEmpty()
    val filterActive = vm.filterCount > 0
    val message = when {
        empty && filterActive -> "没有符合条件的结果"
        empty && vm.view == StationView.Screen && !page.fromLiveSource -> "当前没有办理中的车次"
        empty -> "${vm.direction.label}屏没有符合条件的车次"
        else -> null
    }
    if (message == null) {
        SectionLabel(
            text = buildString {
                append(vm.direction.label)
                append("屏 共 ${vm.visibleEntries.size} 趟")
                if (vm.view == StationView.Screen && page != null) append(" · 更新于 ${page.updatedAt}")
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        return
    }
    EmptyResultState(message = message, filterActive = filterActive, onFilter = onOpenFilter)
}

@Composable
private fun StationInfoCard(page: StationPage, onOpenInfo: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardEdge, vertical = 5.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        // 整卡都是点击区（Card 自己的 onClick，与车次卡同款），不是只有标题那一行
        onClick = onOpenInfo,
        showIndication = true,
        // 车站卡是「这一屏属于谁」，比下面的车次卡高一层，底色加深拉开层级
        colors = CardDefaults.defaultColors(
            color = colors.surfaceContainerHighest,
            contentColor = colors.onSurface,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = page.stationName, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = listOf(page.city, page.telecode).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = "查看车站信息",
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariantSummary,
            )
        }
        page.address?.address?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = it, fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun BoardRow(entry: BoardEntry, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardEdge, vertical = 5.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        onClick = onClick,
        showIndication = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 左两行（车次号+徽标 / 类型+型号）与右两行（时刻 / 状态）对称；
            // 左栏吃满剩余宽度，右栏才被钉在行尾，型号过长时由它自己折给省略号
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = entry.trainCode, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    entry.tags.forEach { TagBadge(it) }
                }
                Row {
                    Text(
                        text = entry.className.ifBlank { TrainKind.of(entry.trainCode).label },
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariantSummary,
                    )
                    if (entry.trainStyle.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = entry.trainStyle,
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(text = entry.time, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    // 跨天标成右上角小字，与车次详情页的「+1」同一套读法
                    if (entry.dayDiff > 0) {
                        Text(
                            text = "+${entry.dayDiff}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.primary,
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                        )
                    }
                }
                // 状态跟在时刻下面右对齐：正点在列，办理中（候车/检票）在列，只有晚点变红
                entry.stateText?.let { state ->
                    Text(
                        text = state,
                        fontSize = 12.sp,
                        color = if (entry.isDelayed) colors.error else colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = (if (entry.direction == BoardDirection.Departure) "终到 " else "来自 ") + entry.otherEnd,
            fontSize = 13.sp,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 票口串可能长到「22A、23A、22B、23B」，各自独占一行、允许折两行，不做单行截断
        val gate = entry.gateText
        gate?.let {
            BoardInfoLine("检票口", it)
        }
        // 离线库的股道是图定静态值，标签必须带「参考」，别让人当成当日实时票口/站台。
        // 只在**没有检票口**时补位：两个都放会把同一件事说两遍，还会让实时值被静态值稀释。
        if (gate == null) {
            entry.referencePlatform?.let {
                BoardInfoLine("参考站台", it)
            }
        }
        entry.waitingRoomText?.let {
            BoardInfoLine("候车室", it)
        }
        entry.exitText?.let {
            BoardInfoLine("出站口", it)
        }
    }
}

/** 卡片里的地点信息行：12sp 副色，最多两行，长文案折行而不是截断。 */
@Composable
private fun BoardInfoLine(label: String, value: String) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "$label $value",
        fontSize = 12.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardFilterDialog(
    show: Boolean,
    kinds: Set<TrainKind>,
    keyword: String,
    onDismiss: () -> Unit,
    onKinds: (Set<TrainKind>) -> Unit,
    onKeyword: (String) -> Unit,
    onReset: () -> Unit,
) {
    var draftKinds by remember(show) { mutableStateOf(kinds) }
    var draftKeyword by remember(show) { mutableStateOf(keyword) }

    OverlayDialog(show = show, title = "筛选", onDismissRequest = onDismiss) {
        // 与往返页的筛选弹窗同一套结构：外层不滚、把「重置/确定」钉底，内容进内层滚动 Column
        Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            // fill = false：内容装得下时按内容贴合，不把弹窗顶到 OverlayDialog 的高度上限
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
            ) {
                TextField(
                    value = draftKeyword,
                    onValueChange = { draftKeyword = it },
                    label = "车次号 / 通到站名",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                Spacer(modifier = Modifier.height(12.dp))
                SectionLabel("车次类型")
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TrainKind.entries.forEach { kind ->
                        Chip(
                            text = kind.chipLabel,
                            selected = kind in draftKinds,
                        ) {
                            draftKinds = if (kind in draftKinds) draftKinds - kind else draftKinds + kind
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
                    onClick = {
                        draftKinds = emptySet()
                        draftKeyword = ""
                        onReset()
                    },
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
                        onKinds(draftKinds)
                        onKeyword(draftKeyword)
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
