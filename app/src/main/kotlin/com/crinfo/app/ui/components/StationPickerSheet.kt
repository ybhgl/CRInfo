package com.crinfo.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinfo.app.data.Station
import com.crinfo.app.data.StationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 车站选择弹层：离线车站表即时检索，支持站名 / 拼音 / 简拼 / 电报码。
 *
 * 这里刻意不用 InputField：它是「折叠态搜索胶囊」，聚焦会切到 expanded 并清空 label，
 * 且需要外层 SearchBar 容器才能定位，单独放进弹层会被输入法顶出屏幕。
 */
@Composable
fun StationPickerSheet(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onPick: (Station) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var query by remember(show) { mutableStateOf("") }
    val results = remember(query) { StationRepository.search(context, query, limit = 80) }
    val focusRequester = remember { FocusRequester() }

    // 先收输入法、再收面板：反过来的话会看到面板先掉下去而键盘还挂在屏幕上
    val close: () -> Unit = {
        focusManager.clearFocus(force = true)
        scope.launch {
            delay(180)
            onDismiss()
        }
    }

    OverlayBottomSheet(show = show, title = title, onDismissRequest = close) {
        // 弹层有入场动画，等它把节点挂上再抢焦点，否则 FocusRequester 未 attach 会抛异常
        LaunchedEffect(show) {
            if (show) {
                delay(180)
                focusRequester.requestFocus()
            }
        }
        // 不写死高度：让弹层贴合内容，列表只在结果多时封顶，避免空态下一大片背景。
        // animateContentSize 补上收缩/展开之间缺失的高度过渡
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = "站名 / 拼音 / 电报码",
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                leadingIcon = {
                    // TextFieldDecorationBox 对图标不放任何 padding，16dp 与 InsideMargin.width 对齐
                    Icon(
                        imageVector = MiuixIcons.Basic.Search,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 16.dp, end = 10.dp).size(20.dp),
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Icon(
                            imageVector = MiuixIcons.Basic.SearchCleanup,
                            contentDescription = "清空",
                            modifier = Modifier
                                .padding(start = 10.dp, end = 16.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { query = "" },
                            tint = MiuixTheme.colorScheme.onSurfaceContainerHighest,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Miuix OverlayBottomSheet 不补导航栏：视口铺到屏幕底、内容穿过小白条滚动，
            // 底部留一个条的高度让末行能完整滚到小白条之上
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).scrollEndHaptic(),
                contentPadding = PaddingValues(bottom = navBarBottom),
            ) {
                items(results.size) { index ->
                    // 选中即收起面板，之前要再点一次空白才关，手感断裂
                    StationRow(results[index]) { station ->
                        onPick(station)
                        close()
                    }
                }
                if (results.isEmpty()) {
                    item {
                        Text(
                            text = if (query.isBlank()) "输入关键词搜索车站" else "未找到「$query」",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StationRow(station: Station, onPick: (Station) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(station) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = station.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = station.city,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = station.code,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
