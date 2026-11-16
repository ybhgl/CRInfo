package com.crinfo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.crinfo.app.ui.icons.AppIcons

/**
 * 筛选用的胶囊。[enabled]=false 时压暗且不接收点击 —— 用于「离线态这个条件无从判断」的开关，
 * 比直接隐藏更好：用户能看到这个功能存在，只是当前模式下不适用。
 */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .then(
                if (enabled) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) colors.onPrimary else colors.onSurface,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = modifier,
    )
}

/**
 * 卡片与小节的标题。和 [SectionLabel] 分工不同：SectionLabel 是 chip 组上方那种
 * 浅色小注，标题必须比正文更大更实，否则层级读起来是倒的。
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/** 顶栏的收藏开关：实心星标 + 强调色表示已收藏。 */
@Composable
fun FavoriteAction(isFavorite: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (isFavorite) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
            contentDescription = if (isFavorite) "取消收藏" else "收藏",
            modifier = Modifier.size(22.dp),
            tint = if (isFavorite) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 顶栏的联网/离线开关，图标预告切换目标：
 * 联网中显示实心划云（点击切到离线），离线中显示空心云（点击切到联网）。
 */
@Composable
fun OnlineToggleAction(online: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (online) AppIcons.CloudOff else AppIcons.CloudQueue,
            contentDescription = if (online) "切换到离线查询" else "切换到联网查询",
            modifier = Modifier.size(22.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/** 一条收藏快捷入口：点=直达，长按=取消收藏。 */
data class FavoriteEntry(val label: String, val onOpen: () -> Unit, val onRemove: () -> Unit)

/** 首页的收藏区块，三个查询页共用；空收藏整块不渲染。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoriteSection(title: String, entries: List<FavoriteEntry>) {
    if (entries.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(title)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "长按取消收藏",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            entries.forEach { entry ->
                Chip(text = entry.label, selected = false, onLongClick = entry.onRemove, onClick = entry.onOpen)
            }
        }
    }
}
