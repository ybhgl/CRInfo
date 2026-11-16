package com.crinfo.app.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinfo.app.data.model.NavigationTransitionStyle
import com.crinfo.app.data.model.PaletteStyle
import com.crinfo.app.data.model.ThemeColorType
import com.crinfo.app.data.model.ThemeMode
import com.crinfo.app.ui.components.AppExpandEnter
import com.crinfo.app.ui.components.AppExpandExit
import com.crinfo.app.ui.components.AppScaffold
import com.crinfo.app.ui.components.AppTopBar
import com.crinfo.app.ui.components.BasicListItem
import com.crinfo.app.ui.components.DropdownListItem
import com.crinfo.app.ui.components.LocalBarColor
import com.crinfo.app.ui.components.MiniActionButton
import com.crinfo.app.ui.components.MiniActionTone
import com.crinfo.app.ui.components.PreferenceGroup
import com.crinfo.app.ui.components.SelectionItem
import com.crinfo.app.ui.components.SwitchListItem
import com.crinfo.app.ui.components.ThemeModeSelector
import com.crinfo.app.ui.components.appBounceScroll
import com.crinfo.app.ui.settings.OfflineDbViewModel
import com.crinfo.app.ui.settings.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class SettingsSection(val title: String, val summary: String) {
    Appearance("外观", "主题、显示与触感"),
    Data("数据", "管理应用数据"),
    About("关于", "版本与项目主页"),
}

/* 设置各页不再是独立导航栈：由壳层 AppRoute 直接挂进统一 NavDisplay，
   子页入栈、返回出栈都跟其它二级页同一套逻辑。 */

@Composable
internal fun SettingsMasterEntry(onSection: (SettingsSection) -> Unit) {
    SettingsMaster(version = appVersion(), onSectionClick = onSection)
}

@Composable
fun SettingsAppearanceEntry(vm: SettingsViewModel, onBack: () -> Unit) {
    AppearancePanel(vm = vm, onBack = onBack)
}

@Composable
fun SettingsDataEntry(vm: SettingsViewModel, dbVm: OfflineDbViewModel, onBack: () -> Unit) {
    // dbVm 由壳层传入：启动自检、设置页按钮和更新弹窗必须共用同一份状态
    DataPanel(vm = vm, dbVm = dbVm, onBack = onBack)
}

@Composable
fun SettingsAboutEntry(onBack: () -> Unit) {
    // 关于页独立成屏：流动渐变背景 + material 大图标 + 毛玻璃卡片，见 AboutScreen.kt
    AboutScreen(onBack = onBack)
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName}(${info.versionCode})"
        }.getOrNull() ?: "1.0"
    }
}

@Composable
private fun SettingsMaster(version: String, onSectionClick: (SettingsSection) -> Unit) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    AppScaffold(
        topBar = {
            AppTopBar(
                color = LocalBarColor.current,
                title = "设置",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .appBounceScroll()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
        ) {
            PreferenceGroup {
                SettingsSection.entries.forEach { item ->
                    val summary = if (item == SettingsSection.About) version else item.summary
                    BasicListItem(
                        headlineText = item.title,
                        supportingText = summary,
                        leadingImageVector = when (item) {
                            SettingsSection.Appearance -> MiuixIcons.Theme
                            SettingsSection.Data -> MiuixIcons.Favorites
                            SettingsSection.About -> MiuixIcons.Info
                        },
                        onClick = { onSectionClick(item) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    AppScaffold(
        topBar = {
            AppTopBar(
                color = LocalBarColor.current,
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .appBounceScroll()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                // padding 挂在 scroll 之后等于 contentPadding：偏好组会从顶栏与底栏下方滑过，
                // 栏的模糊才有内容可采样
                .padding(innerPadding),
        ) {
            content()
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppearancePanel(vm: SettingsViewModel, onBack: () -> Unit) {
    var showSeedDialog by remember { mutableStateOf(false) }
    val s = vm.settings
    val systemDark = isSystemInDarkTheme()
    // 选「自定义」那一刻 updateColorType 已生效，取消或点弹窗外部要回退到原档位
    var colorBeforeDialog by remember { mutableStateOf<ThemeColorType?>(null) }

    SectionPage(title = "外观", onBack = onBack) {
        // 深浅模式预览选择器：两张手绘预览卡 + 主色 squircle 描边表示选中
        ThemeModeSelector(
            mode = s.mode,
            systemDark = systemDark,
            onModeChange = vm::setMode,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        )
        PreferenceGroup(title = "主题") {
            SwitchListItem(
                checked = s.mode == ThemeMode.System,
                headlineText = "跟随系统",
                supportingText = "关闭时按当前系统深浅固定为浅色或深色",
                onCheckedChange = { follow ->
                    vm.setMode(
                        if (follow) ThemeMode.System
                        else if (systemDark) ThemeMode.Dark else ThemeMode.Light,
                    )
                },
            )
            SwitchListItem(
                checked = s.monet,
                headlineText = "系统主题色",
                supportingText = "关闭后改用下方主题颜色取色",
                onCheckedChange = { vm.setMonet(it) },
            )
            DropdownListItem(
                value = s.colorType,
                headlineText = "主题颜色",
                enabled = !s.monet,
                selections = ThemeColorType.entries.map { SelectionItem(it.label, it) },
                itemIcon = { mod, type -> ThemeColorDot(mod, type) },
                onValueChanged = { _, type ->
                    if (type == ThemeColorType.Custom) {
                        // 已在自定义档重开弹窗：无可回退（「之前」就是自定义本身）
                        colorBeforeDialog =
                            if (s.colorType == ThemeColorType.Custom) null else s.colorType
                        showSeedDialog = true
                    }
                    vm.updateColorType(type)
                },
            )
            DropdownListItem(
                value = s.palette,
                headlineText = "调色板风格",
                enabled = !s.monet && s.colorType != ThemeColorType.Default,
                selections = PaletteStyle.entries.map { SelectionItem(it.label, it) },
                onValueChanged = { _, palette -> vm.updatePalette(palette) },
            )
            SwitchListItem(
                checked = s.pureBlack,
                headlineText = "纯黑背景",
                supportingText = "深色时窗口底色改为纯黑",
                onCheckedChange = { vm.updatePureBlack(it) },
            )
        }
        PreferenceGroup(title = "显示与导航") {
            SwitchListItem(
                checked = s.floatingBar,
                headlineText = "悬浮底栏",
                supportingText = "底部导航改为悬浮圆角样式",
                onCheckedChange = { vm.updateFloatingBar(it) },
            )
            val atLeast33 = Build.VERSION.SDK_INT >= 33
            SwitchListItem(
                checked = s.blurBars && atLeast33,
                enabled = atLeast33,
                headlineText = "模糊效果",
                supportingText = if (atLeast33) "模糊顶部栏和底部栏后方的内容" else "需要 Android 13 及以上",
                onCheckedChange = { vm.updateBlurBars(it) },
            )
            AnimatedVisibility(
                visible = s.blurBars && atLeast33,
                enter = AppExpandEnter,
                exit = AppExpandExit,
            ) {
                SwitchListItem(
                    checked = s.progressiveBlur,
                    headlineText = "渐进顶栏模糊",
                    supportingText = "顶栏由透明渐变到模糊，滚动时更自然",
                    onCheckedChange = vm::updateProgressiveBlur,
                )
            }
            SwitchListItem(
                checked = s.haptics,
                headlineText = "触感反馈",
                supportingText = "关闭后全应用停止振动反馈",
                onCheckedChange = { vm.updateHaptics(it) },
            )
            SwitchListItem(
                checked = s.predictiveBack,
                headlineText = "预测性返回",
                supportingText = "页内右滑返回时跟随手势预览上一页，关闭后在松手时才返回",
                onCheckedChange = { vm.updatePredictiveBack(it) },
            )
            AnimatedVisibility(
                visible = s.predictiveBack,
                enter = AppExpandEnter,
                exit = AppExpandExit,
            ) {
                DropdownListItem(
                    headlineText = "预测返回样式",
                    value = s.transitionStyle,
                    selections = NavigationTransitionStyle.entries.map { SelectionItem(it.label, it) },
                    onValueChanged = { _, style -> vm.updateNavigationTransitionStyle(style) },
                )
            }
        }
    }

    if (showSeedDialog) {
        SeedColorDialog(
            initial = if (s.colorType == ThemeColorType.Custom) s.customSeedHex else "6650A4",
            onDismiss = {
                showSeedDialog = false
                val prev = colorBeforeDialog
                colorBeforeDialog = null
                if (prev != null) vm.updateColorType(prev)
            },
            onSave = { hex ->
                colorBeforeDialog = null
                vm.updateCustomSeed(hex)
                showSeedDialog = false
            },
        )
    }
}

private fun hexToColor(hex: String): Color {
    val rgb = hex.toIntOrNull(16) ?: 0x6650A4
    return Color(0xFF000000L or rgb.toLong())
}

private fun colorToHex(color: Color): String =
    (color.toArgb() and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/**
 * 主题颜色下拉项前的圆形颜色指示器：
 * 自定义 = 七彩混色（锥形彩虹渐变），默认 = 蓝色，其余预设 = 各自种子色的实心圆。
 *
 * [modifier] 由 Miuix DropdownItem 传入（sizeIn 最小图标格 + 尾部间距）。
 * 注意：**不能在里面再挂 fillMaxWidth**——图标格在选项 Row 里，撑满会把文字和行尾选中勾全部挤出去；
 * 这里让 Box 只吃最小格尺寸，圆点在格内居中。
 */
@Composable
private fun ThemeColorDot(modifier: Modifier, type: ThemeColorType) {
    val brush = when (type) {
        ThemeColorType.Custom -> Brush.sweepGradient(
            listOf(
                Color(0xFFF44336), Color(0xFFFF9800), Color(0xFFFFEB3B),
                Color(0xFF4CAF50), Color(0xFF00BCD4), Color(0xFF3F51B5),
                Color(0xFF9C27B0), Color(0xFFF44336),
            ),
        )
        else -> SolidColor(
            if (type == ThemeColorType.Default) Color(0xFF4E77F5) else hexToColor(type.hex ?: "4E77F5"),
        )
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(brush = brush, shape = CircleShape),
        )
    }
}

@Composable
private fun SeedColorDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initial)) }
    var seed by remember { mutableStateOf(hexToColor(initial)) }
    OverlayDialog(show = true, title = "自定义主题颜色", onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Miuix 调色盘：拖选即时回填下方 hex 输入框
            ColorPalette(
                color = seed,
                onColorChanged = { c ->
                    seed = c
                    text = TextFieldValue(colorToHex(c))
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = text,
                onValueChange = { next ->
                    if (next.text.length <= 6 && next.text.matches(Regex("^[0-9a-fA-F]*$"))) {
                        text = next
                        if (next.text.length == 6) seed = hexToColor(next.text)
                    }
                },
                label = "6650A4",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(
                        text = "取消",
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = { if (text.text.length == 6) onSave(text.text.uppercase()) },
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary,
                        contentColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(
                        text = "保存",
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

@Composable
private fun DataPanel(vm: SettingsViewModel, dbVm: OfflineDbViewModel, onBack: () -> Unit) {
    var pendingClear by remember { mutableStateOf<String?>(null) }
    var pendingDeleteDb by remember { mutableStateOf(false) }
    // 重扫进度条：走满后**不自动消失**，一直留到下一次布局刷新（重进页面、点删除/更新）
    var rescanStarted by remember { mutableStateOf(false) }
    LaunchedEffect(dbVm.rescanning) {
        if (dbVm.rescanning) rescanStarted = true
    }

    SectionPage(title = "数据", onBack = onBack) {
        PreferenceGroup(title = "数据库") {
            val checking = dbVm.status is OfflineDbViewModel.UpdateStatus.Checking
            val downloading = dbVm.status is OfflineDbViewModel.UpdateStatus.Downloading
            val installed = dbVm.installedVersion != null
            BasicListItem(
                headlineText = "离线数据库",
                supportingText = dbVm.installedVersion?.let { "版本 $it" } ?: "未安装",
                leadingImageVector = MiuixIcons.Download,
            )
            // 三个动作放在标题行下面：Miuix 没有多按钮组件，塞进行尾会把行撑得很高。
            // 删除/重扫按内容宽度靠左，「更新」这个主动作单独靠右 —— 撑满余宽会像一条横幅。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniActionButton(
                    text = "删除",
                    tone = MiniActionTone.Danger,
                    enabled = installed,
                    onClick = {
                        rescanStarted = false
                        pendingDeleteDb = true
                    },
                )
                MiniActionButton(
                    text = if (dbVm.rescanning) "重扫中…" else "重扫",
                    tone = MiniActionTone.Neutral,
                    enabled = installed && !dbVm.rescanning,
                    onClick = { dbVm.rescan() },
                )
                Spacer(modifier = Modifier.weight(1f))
                MiniActionButton(
                    text = when {
                        downloading -> "下载中…"
                        checking -> "检查中…"
                        installed -> "更新"
                        else -> "下载"
                    },
                    tone = MiniActionTone.Primary,
                    enabled = !checking && !downloading,
                    onClick = {
                        rescanStarted = false
                        dbVm.check(manual = true)
                    },
                )
            }
            if (rescanStarted) {
                // 进度条放在按钮下方。走满后保留在界面上，直到下一次布局刷新才被清掉。
                // 进度是解析器按实际处理量给的真实值，不是定时动画。
                val progress = dbVm.rescanProgress.coerceIn(0f, 1f)
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    Text(
                        text = if (dbVm.rescanning) {
                            "${dbVm.rescanStage ?: "重扫"}… ${(progress * 100).toInt()}%"
                        } else {
                            "重扫完成，数据库已重新解析"
                        },
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        PreferenceGroup(title = "收藏") {
            BasicListItem(
                headlineText = "收藏路线",
                supportingText = "${vm.favRoutes.size} 条",
                leadingImageVector = MiuixIcons.Favorites,
                trailingContent = {
                    MiniActionButton(
                        text = "清空",
                        tone = MiniActionTone.Accent,
                        onClick = { pendingClear = "路线收藏" },
                    )
                },
            )
            BasicListItem(
                headlineText = "收藏车站",
                supportingText = "${vm.favStations.size} 条",
                leadingImageVector = MiuixIcons.Favorites,
                trailingContent = {
                    MiniActionButton(
                        text = "清空",
                        tone = MiniActionTone.Accent,
                        onClick = { pendingClear = "车站收藏" },
                    )
                },
            )
            BasicListItem(
                headlineText = "收藏车次",
                supportingText = "${vm.favTrains.size} 条",
                leadingImageVector = MiuixIcons.Favorites,
                trailingContent = {
                    MiniActionButton(
                        text = "清空",
                        tone = MiniActionTone.Accent,
                        onClick = { pendingClear = "车次收藏" },
                    )
                },
            )
        }
        PreferenceGroup(title = "历史") {
            BasicListItem(
                headlineText = "车票查询历史",
                supportingText = "${vm.routeHistoryCount} 条",
                leadingImageVector = MiuixIcons.Recent,
                trailingContent = {
                    MiniActionButton(
                        text = "清空",
                        tone = MiniActionTone.Accent,
                        onClick = { pendingClear = "车票查询历史" },
                    )
                },
            )
            BasicListItem(
                headlineText = "车次查询历史",
                supportingText = "${vm.trainHistoryCount} 条",
                leadingImageVector = MiuixIcons.Recent,
                trailingContent = {
                    MiniActionButton(
                        text = "清空",
                        tone = MiniActionTone.Accent,
                        onClick = { pendingClear = "车次查询历史" },
                    )
                },
            )
            BasicListItem(
                headlineText = "车站查询历史",
                supportingText = "${vm.stationHistoryCount} 条",
                leadingImageVector = MiuixIcons.Recent,
                trailingContent = {
                    MiniActionButton(
                        text = "清空",
                        tone = MiniActionTone.Accent,
                        onClick = { pendingClear = "车站查询历史" },
                    )
                },
            )
        }
    }

    OverlayDialog(show = pendingDeleteDb, title = "确认删除", onDismissRequest = { pendingDeleteDb = false }) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)) {
            Text(
                text = "将删除已下载的离线数据库文件，之后需要重新下载才能使用离线功能。确定删除吗？",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(
                    onClick = { pendingDeleteDb = false },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(
                        text = "取消",
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        dbVm.delete()
                        pendingDeleteDb = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(
                        text = "删除",
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    OverlayDialog(show = pendingClear != null, title = "确认清空", onDismissRequest = { pendingClear = null }) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)) {
            Text(
                text = "确定清空${pendingClear.orEmpty()}吗？",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(
                    onClick = { pendingClear = null },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(
                        text = "取消",
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        when (pendingClear) {
                            "路线收藏" -> vm.clearFavRoutes()
                            "车站收藏" -> vm.clearFavStations()
                            "车次收藏" -> vm.clearFavTrains()
                            "车票查询历史" -> vm.clearRouteHistory()
                            "车次查询历史" -> vm.clearTrainHistory()
                            "车站查询历史" -> vm.clearStationHistory()
                        }
                        pendingClear = null
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
                        text = "清空",
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

