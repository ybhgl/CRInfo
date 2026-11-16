package com.crinfo.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 月历首列固定周日，与开行计划卡同口径。 */
private val MonthWeekHead = listOf("日", "一", "二", "三", "四", "五", "六")

/**
 * 月历式日期选择弹窗：一页一个月，左右滑动翻页，页数锁死在 [dates] 覆盖的自然月内，
 * 弹窗标题即当前页月份（随翻页更新）。
 * 可选日常规字色、周六/周日用 error 红；范围外极淡灰不可点；
 * 选中 = 主题色描边圆圈；今天的数字用「今」顶替，今天且选中则「今」外再套描边圈。
 */
@Composable
fun MonthDatePickerDialog(
    show: Boolean,
    dates: List<LocalDate>,
    current: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val selectable = remember(dates) { dates.toHashSet() }
    val today = LocalDate.now()
    // dates 是 VM 里一次性算好的常量列表，月份跨度不会在弹窗存续期间变化
    val months = remember(dates) {
        val head = dates.minOrNull()?.let(YearMonth::from) ?: YearMonth.from(current)
        val tail = dates.maxOrNull()?.let(YearMonth::from) ?: head
        generateSequence(head) { it.plusMonths(1) }.takeWhile { !it.isAfter(tail) }.toList()
    }
    val pagerState = rememberPagerState(pageCount = { months.size })
    // 每次打开都落到选中日期所在月：弹窗常驻组合树，rememberPagerState 的初值只在首次生效
    LaunchedEffect(show, current) {
        if (show) {
            val page = months.indexOf(YearMonth.from(current)).coerceAtLeast(0)
            pagerState.scrollToPage(page)
        }
    }
    val pageMonth = months[pagerState.currentPage.coerceIn(months.indices)]

    OverlayDialog(show = show, title = "${pageMonth.year}年${pageMonth.monthValue}月", onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                MonthWeekHead.forEach {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                MonthGrid(
                    month = months[page],
                    selectable = selectable,
                    current = current,
                    today = today,
                    onPick = {
                        onPick(it)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** 一格一个月的日期：1 号落在周几，前面就补几个日子占位（补位与整月都在可选集合外，自然是灰字）。 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    selectable: Set<LocalDate>,
    current: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    // DayOfWeek 里周日是 7，首列是周日，所以取模换成 0..6
    val lead = first.dayOfWeek.value % 7
    val days = month.lengthOfMonth()
    val rowCount = ((lead + days + 6) / 7)

    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        repeat(rowCount) { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                repeat(7) { col ->
                    val index = row * 7 + col - lead
                    if (index in 0 until days) {
                        val day = month.atDay(index + 1)
                        MonthCell(
                            day = day,
                            selectable = day in selectable,
                            selected = day == current,
                            isToday = day == today,
                            onClick = { onPick(day) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f).height(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    day: LocalDate,
    selectable: Boolean,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val isWeekend = day.dayOfWeek.value >= 6
    // 不可选灰要和可选黑一眼分开：在同族灰上再压一档透明度
    val disabledGray = colors.onSurfaceVariantSummary.copy(alpha = 0.35f)
    val textColor = when {
        !selectable -> disabledGray
        isWeekend -> colors.error
        else -> colors.onSurface
    }
    Box(
        modifier = modifier
            .height(40.dp)
            .clickable(enabled = selectable, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .then(if (selected) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                // 今天不画实心圈，直接把数字换成「今」
                text = if (isToday) "今" else day.dayOfMonth.toString(),
                fontSize = 13.sp,
                fontWeight = if (selected || isToday) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                color = textColor,
            )
        }
    }
}
