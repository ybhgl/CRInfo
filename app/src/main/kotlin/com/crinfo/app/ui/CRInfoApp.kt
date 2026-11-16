package com.crinfo.app.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.crinfo.app.data.model.NavigationTransitionStyle
import com.crinfo.app.data.model.StationView
import com.crinfo.app.ui.components.BarBackdrop
import com.crinfo.app.ui.components.LocalAppSettings
import com.crinfo.app.ui.components.LocalShellBottom
import com.crinfo.app.ui.components.OfflineDbUpdateDialog
import com.crinfo.app.ui.components.barBlurModifier
import com.crinfo.app.ui.components.rememberBarBackdrop
import com.crinfo.app.ui.components.rememberHapticTap
import com.crinfo.app.ui.icons.AppIcons
import com.crinfo.app.ui.navigation.AospNavigationTransition
import com.crinfo.app.ui.navigation.FastOutExtraSlowIn
import com.crinfo.app.ui.query.StationToStationViewModel
import com.crinfo.app.ui.screens.SettingsAboutEntry
import com.crinfo.app.ui.screens.SettingsAppearanceEntry
import com.crinfo.app.ui.screens.SettingsDataEntry
import com.crinfo.app.ui.screens.SettingsMasterEntry
import com.crinfo.app.ui.screens.SettingsSection
import com.crinfo.app.ui.screens.StationBoardEntry
import com.crinfo.app.ui.screens.StationHome
import com.crinfo.app.ui.screens.StationToStationHome
import com.crinfo.app.ui.screens.StationToStationResults
import com.crinfo.app.ui.screens.TrainDetailEntry
import com.crinfo.app.ui.screens.TrainHome
import com.crinfo.app.ui.settings.OfflineDbViewModel
import com.crinfo.app.ui.settings.SettingsViewModel
import com.crinfo.app.ui.theme.resolvesDark
import kotlin.random.Random
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class Dest(val label: String) {
    StationToStation("车票查询"),
    Train("车次查询"),
    Station("车站查询"),
    Settings("设置"),
}

/**
 * 壳层唯一导航栈：Tab 根条目只在栈底，一切二级页（结果、详情、板面、设置子页）
 * 都是压栈条目，返回键与右滑逐级出栈，回到刚刚来的那页而不是 Tab 首页。
 * rememberNavController 的栈走 rememberSaveable 多态序列化，路由必须 @Serializable。
 */
@Serializable
private sealed interface AppRoute : NavKey {
    /**
     * 二级页都带一个随机实例 id：NavDisplay 不传 contentKey 时直接拿路由对象当 key，
     * 栈里撞 key 就崩。有了 id，同一趟车才能以多个栈位存在（K1→K2→K1→K4），
     * 返回时逐级回退而不是被 popUntil 折叠掉中间页。
     */
    companion object {
        fun trainDetail(code: String, date: String) = TrainDetail(code, date, Random.nextLong())

        fun stationBoard(telecode: String, view: StationView, date: String) =
            StationBoard(telecode, view, date, Random.nextLong())
    }

    @Serializable
    data class Tab(val dest: Dest) : AppRoute

    @Serializable
    data object S2sResults : AppRoute

    @Serializable
    data class TrainDetail(val code: String, val date: String, val uid: Long) : AppRoute

    @Serializable
    data class StationBoard(val telecode: String, val view: StationView, val date: String, val uid: Long) : AppRoute

    @Serializable
    data object SettingsAppearance : AppRoute

    @Serializable
    data object SettingsData : AppRoute

    @Serializable
    data object SettingsAbout : AppRoute
}

@Composable
fun CRInfoApp(vm: SettingsViewModel = viewModel()) {
    val nav = rememberNavController<AppRoute>(AppRoute.Tab(Dest.StationToStation))
    val dbVm: OfflineDbViewModel = viewModel()
    // 车票查询的首页与结果页共享一次查询会话，VM 提到壳层，其它条目用各自的实例
    val s2sVm: StationToStationViewModel = viewModel()
    // 每次进入应用查一次离线库版本：无更新完全静默，只在真发现新版本时弹安装/更新框
    LaunchedEffect(Unit) { dbVm.autoCheckOnLaunch() }

    // 四个 Tab 各自的滚动位置/展开状态：切走时保存、切回来恢复；顶栏 ScrollBehavior 不保存
    val tabStateHolder = rememberSaveableStateHolder()
    // 根条目固定为栈底那一条，切换只改这个状态，由 TabPage 内部做横向转场。
    // 早先走 nav.replace 换同深度页面，NavDisplay 对同深度替换没有任何动画。
    var selectedTab by rememberSaveable { mutableStateOf(Dest.StationToStation) }

    val settings = vm.settings
    val predictive = settings.predictiveBack
    // 关掉预测返回后改成「提交式」返回：右滑不再跟手预览，系统返回在提交时才翻页
    val swipeDismiss = if (predictive) NavSwipeDirection.LeftToRight else NavSwipeDirection.None

    // 底栏提到 NavDisplay 外层（z-order 最顶层）：所有页面，包括压入的二级页，都在它后方。
    // 采样层同步上移到包住 NavDisplay 的 Box 上，底栏在采样层之外做模糊，不构成自引用。
    // 直接读 settings：此处还在 CompositionLocalProvider(LocalAppSettings) 之外，
    // 读 LocalAppSettings.current 会拿到默认值（blurBars=false），底栏模糊永远不生效。
    val barBackdrop = rememberBarBackdrop(settings.blurBars)
    val density = LocalDensity.current
    // 只记非零测量值：底栏收起动画中高度会渐变到 0，保留上次高度可避免内容底部留白跳动
    var shellBottom by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(LocalAppSettings provides settings) {
        Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
            // 必须待在 Scaffold 的内容区里：OverlayDialog 默认 renderInRootScaffold，
            // 挂在 Scaffold 外面就没有宿主，状态对了也画不出来
            Box(modifier = Modifier.fillMaxSize()) {
                // 压入二级页时底栏带动画收起、返回时滑回来。backStack 是 SnapshotStateList，
                // 在组合里读 size 就能订阅变化，无需额外状态，也不动 NavDisplay 的结构。
                val bottomBarVisible = nav.backStack.size <= 1
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(barBackdrop?.recordModifier() ?: Modifier),
                ) {
                    // 两套转场：MIUI 用库默认；AOSP 除了换 transition，
                    // effects 的裁角模式 / 压暗 / 底色 / 圆角兜底也要一起换，否则返回卡片观感对不上。
                    val useAospTransition = settings.transitionStyle == NavigationTransitionStyle.AOSP
                    val systemCornerRadius = rememberNavSystemCornerRadius()
                    val windowSize = LocalWindowInfo.current.containerSize
                    val windowWidth = with(density) { windowSize.width.toDp() }
                    val windowHeight = with(density) { windowSize.height.toDp() }
                    // 与参考项目 isMiuixWideLayout 同口径：宽屏下 MIUI 档不裁圆角
                    val wideLayout = windowWidth > 0.dp &&
                        (windowWidth >= 840.dp ||
                            (windowWidth >= 600.dp && windowHeight.value / windowWidth.value < 1.2f))
                    val isDark = resolvesDark(settings, LocalContext.current.resources.configuration.uiMode)
                    val transitionCornerRadius = when {
                        !useAospTransition && wideLayout -> 0.dp
                        useAospTransition && systemCornerRadius <= 0.dp -> 32.dp
                        else -> systemCornerRadius
                    }
                    NavDisplay(
                        navController = nav,
                        transition = if (useAospTransition) AospNavigationTransition else NavTransitions.MiuixDefault,
                        effects = if (useAospTransition) {
                            NavDisplayEffects(
                                cornerClipRadius = transitionCornerRadius,
                                cornerClipMode = NavCornerClipMode.All,
                                dimAmount = if (isDark) 0.8f else 0.2f,
                                backdropColor = MiuixTheme.colorScheme.surface,
                            )
                        } else {
                            // MIUI 档保持库的默认裁角 / 压暗，只给圆角与底色
                            NavDisplayEffects(
                                cornerClipRadius = transitionCornerRadius,
                                backdropColor = MiuixTheme.colorScheme.background,
                            )
                        },
                    ) {
                        entry<AppRoute.Tab> {
                            completionOnlyBack(nav, predictive)
                            // 压入二级页时底栏在最顶层收起，页面本身仍由 NavDisplay 整层转场盖住
                            TabPage(dest = selectedTab, shellBottom = shellBottom) { dest ->
                                tabStateHolder.SaveableStateProvider(dest.name) {
                                    when (dest) {
                                        Dest.StationToStation -> StationToStationHome(
                                            vm = s2sVm,
                                            settings = vm,
                                            dbVm = dbVm,
                                        ) {
                                            // 栈里只允许一个结果条目：重复提交原地刷新数据即可
                                            nav.openEntry(AppRoute.S2sResults)
                                        }
                                        Dest.Train -> TrainHome(
                                            settings = vm,
                                            dbVm = dbVm,
                                            onOpenDetail = { code, date -> nav.openEntry(AppRoute.trainDetail(code, date)) },
                                        )
                                        Dest.Station -> StationHome(
                                            settings = vm,
                                            dbVm = dbVm,
                                            onOpenBoard = { telecode, view, date ->
                                                nav.openEntry(AppRoute.stationBoard(telecode, view, date))
                                            },
                                        )
                                        Dest.Settings -> SettingsMasterEntry { section ->
                                            // 设置子页同样是单例子路由，双击一样会撞 contentKey
                                            nav.openEntry(
                                                when (section) {
                                                    SettingsSection.Appearance -> AppRoute.SettingsAppearance
                                                    SettingsSection.Data -> AppRoute.SettingsData
                                                    SettingsSection.About -> AppRoute.SettingsAbout
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        entry<AppRoute.S2sResults>(swipeDismiss = swipeDismiss) {
                            completionOnlyBack(nav, predictive)
                            StationToStationResults(
                                vm = s2sVm,
                                settings = vm,
                                onBack = { nav.pop() },
                                onOpenTrain = { code, date -> nav.openEntry(AppRoute.trainDetail(code, date)) },
                            )
                        }
                        entry<AppRoute.TrainDetail>(swipeDismiss = swipeDismiss) { route ->
                            completionOnlyBack(nav, predictive)
                            TrainDetailEntry(
                                code = route.code,
                                date = route.date,
                                settings = vm,
                                dbVm = dbVm,
                                onOpenStation = { telecode, date ->
                                    nav.openEntry(AppRoute.stationBoard(telecode, StationView.Trains, date))
                                },
                                onOpenTrain = { code, date -> nav.openEntry(AppRoute.trainDetail(code, date)) },
                                onBack = { nav.pop() },
                            )
                        }
                        entry<AppRoute.StationBoard>(swipeDismiss = swipeDismiss) { route ->
                            completionOnlyBack(nav, predictive)
                            StationBoardEntry(
                                telecode = route.telecode,
                                view = route.view,
                                date = route.date,
                                settings = vm,
                                dbVm = dbVm,
                                onOpenTrain = { code, date -> nav.openEntry(AppRoute.trainDetail(code, date)) },
                                onBack = { nav.pop() },
                            )
                        }
                        entry<AppRoute.SettingsAppearance>(swipeDismiss = swipeDismiss) {
                            completionOnlyBack(nav, predictive)
                            // vm 用壳层那份：子页改动要立刻反映到全局主题与底栏形态
                            SettingsAppearanceEntry(vm = vm, onBack = { nav.pop() })
                        }
                        entry<AppRoute.SettingsData>(swipeDismiss = swipeDismiss) {
                            completionOnlyBack(nav, predictive)
                            SettingsDataEntry(vm = vm, dbVm = dbVm, onBack = { nav.pop() })
                        }
                        entry<AppRoute.SettingsAbout>(swipeDismiss = swipeDismiss) {
                            completionOnlyBack(nav, predictive)
                            SettingsAboutEntry(onBack = { nav.pop() })
                        }
                    }
                }
                // 底栏画在 NavDisplay 之上：压入的二级页从它后方滑入，而不是把它盖住
                AnimatedVisibility(
                    visible = bottomBarVisible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(animationSpec = tween(280)) { it } +
                        expandVertically(animationSpec = tween(280)) +
                        fadeIn(animationSpec = tween(180)),
                    exit = slideOutVertically(animationSpec = tween(240)) { it } +
                        shrinkVertically(animationSpec = tween(240)) +
                        fadeOut(animationSpec = tween(140)),
                    label = "bottomBarVisibility",
                ) {
                    Box(
                        modifier = Modifier.onSizeChanged { size ->
                            if (size.height > 0) shellBottom = with(density) { size.height.toDp() }
                        },
                    ) {
                        AppBottomBar(
                            selected = selectedTab,
                            backdrop = barBackdrop,
                            onSelect = { dest -> if (dest != selectedTab) selectedTab = dest },
                        )
                    }
                }
                OfflineDbUpdateDialog(dbVm)
            }
        }
    }
}

/**
 * Tab 首页壳：只负责四个 Tab 之间的横向转场。底栏已提到 [CRInfoApp] 的 NavDisplay 外层，
 * 压入二级页时它浮在最顶层收起，页面本身仍由 NavDisplay 整层转场（新页在底栏后方）。
 * 只有四个 Tab 页需要让出底栏高度，二级页走 LocalShellBottom 默认值 0.dp。
 */
@Composable
private fun TabPage(
    dest: Dest,
    shellBottom: Dp,
    content: @Composable (Dest) -> Unit,
) {
    // Tab 转场缓动跟随导航样式：AOSP 档与页面转场同曲线，MIUI 档用 Compose 默认曲线
    val tabEasing = if (LocalAppSettings.current.transitionStyle == NavigationTransitionStyle.AOSP) {
        FastOutExtraSlowIn
    } else {
        FastOutSlowInEasing
    }
    CompositionLocalProvider(LocalShellBottom provides shellBottom) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Tab 切换：新页从索引方向滑入、旧页向反方向轻退并淡出。
            // 两个页面同时在屏，滚动位置各由 SaveableStateProvider 保管。
            AnimatedContent(
                targetState = dest,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    // slide 要 IntOffset、fade 要 Float，tween 的 T 交给调用点推断，不能共用同一个 spec
                    val slideEasing = tween<IntOffset>(durationMillis = 240, easing = tabEasing)
                    val fadeEasing = tween<Float>(durationMillis = 200, easing = tabEasing)
                    if (forward) {
                        (slideInHorizontally(slideEasing) { it / 4 } + fadeIn(fadeEasing)) togetherWith
                            (slideOutHorizontally(slideEasing) { -it / 4 } + fadeOut(fadeEasing))
                    } else {
                        (slideInHorizontally(slideEasing) { -it / 4 } + fadeIn(fadeEasing)) togetherWith
                            (slideOutHorizontally(slideEasing) { it / 4 } + fadeOut(fadeEasing))
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "tabSwitch",
            ) { current -> content(current) }
        }
    }
}

/**
 * 二级页跳转的唯一入口：一律压栈，返回键与右滑逐级出栈 —— K1→K2→K3→K4 就按
 * K4→K3→K2→K1 一层层回去。同一条内容重复打开也能各占一个栈位，靠的是路由上的实例 id
 * （`contentKey` 默认取路由本身，撞 key 会直接 `IllegalArgumentException` 闪退）。
 *
 * 详情与板面带实例 id，只按「栈顶已是同一页」拦双击；结果页与设置子页是无 id 的单例路由，
 * 全栈只能有一份，已在栈里就顶回栈顶。压完再按 [MAX_STACKED_ENTRIES] 收口，从最靠近首页的
 * 一侧丢掉最旧的二级页，免得长链路把每一页的查询 VM 全留在内存里。
 */
private fun NavController.openEntry(route: AppRoute) {
    if (route is AppRoute.TrainDetail || route is AppRoute.StationBoard) {
        // 双击：第一次的转场还没提交，第二次仍打在同一处
        if ((backStack.lastOrNull() as? AppRoute)?.isSamePageAs(route) == true) return
        push(route)
    } else if (route in backStack) {
        // 无实例 id 的单例路由（结果页、设置子页）全栈只能有一份，再压一次必撞 contentKey；
        // 已在栈里就把它顶回栈顶（等于回到那一页）
        popUntil { it == route }
    } else {
        push(route)
    }
    while (backStack.size - 1 > MAX_STACKED_ENTRIES) backStack.removeAt(1)
}

/** 同页只看内容，不看实例 id。 */
private fun AppRoute.isSamePageAs(other: AppRoute): Boolean = when {
    this is AppRoute.TrainDetail && other is AppRoute.TrainDetail -> code == other.code && date == other.date
    this is AppRoute.StationBoard && other is AppRoute.StationBoard ->
        telecode == other.telecode && view == other.view && date == other.date

    else -> this == other
}

/** 栈底的 Tab 根条目不计入上限。 */
private const val MAX_STACKED_ENTRIES = 20

/** 预测返回开启时交给 NavDisplay 的跟手驱动；关闭时这里补一个只在提交后翻页的处理器。 */
@Composable
private fun completionOnlyBack(nav: NavController, predictive: Boolean) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !predictive && nav.backStack.size > 1,
        onBackCompleted = { nav.pop() },
    )
}

/**
 * 底栏。悬浮态用 Miuix 原生 [FloatingNavigationBar]（自带 50dp 胶囊、投影与手势条内边距）；
 * 能模糊时把模糊层直接挂在栏上、栏本体转透明，否则铺各自的面色。
 */
@Composable
private fun AppBottomBar(selected: Dest, backdrop: BarBackdrop?, onSelect: (Dest) -> Unit) {
    val floating = LocalAppSettings.current.floatingBar
    val blurable = backdrop != null && Build.VERSION.SDK_INT >= 33

    if (floating) {
        FloatingNavigationBar(
            color = if (blurable) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            modifier = Modifier.barBlurModifier(backdrop, RoundedCornerShape(FloatingToolbarDefaults.CornerRadius)),
        ) {
            Dest.entries.forEach { item ->
                FloatingNavigationBarItem(
                    selected = selected == item,
                    onClick = rememberHapticTap { onSelect(item) },
                    icon = navIcon(item),
                    label = item.label,
                )
            }
        }
    } else if (blurable) {
        Box(modifier = Modifier.fillMaxWidth().then(backdrop.blurModifier(RectangleShape))) {
            NavigationBar(color = Color.Transparent) {
                Dest.entries.forEach { item ->
                    NavigationBarItem(
                        selected = selected == item,
                        onClick = rememberHapticTap { onSelect(item) },
                        icon = navIcon(item),
                        label = item.label,
                    )
                }
            }
        }
    } else {
        NavigationBar(color = MiuixTheme.colorScheme.surface) {
            Dest.entries.forEach { item ->
                NavigationBarItem(
                    selected = selected == item,
                    onClick = rememberHapticTap { onSelect(item) },
                    icon = navIcon(item),
                    label = item.label,
                )
            }
        }
    }
}

@Composable
private fun navIcon(item: Dest) = when (item) {
    Dest.StationToStation -> AppIcons.Ticket
    Dest.Train -> AppIcons.Train
    Dest.Station -> AppIcons.Home
    Dest.Settings -> AppIcons.Settings
}
