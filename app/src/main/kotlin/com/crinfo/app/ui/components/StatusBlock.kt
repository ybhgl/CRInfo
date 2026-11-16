package com.crinfo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinfo.app.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 加载失败：淡红 Close + 错误说明（含代码）+ 重试。 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryText: String = "重试",
) {
    StateBlock(
        icon = MiuixIcons.Basic.Close,
        iconTint = MiuixTheme.colorScheme.error.copy(alpha = 0.3f),
        message = message,
        actionLabel = retryText,
        onAction = onRetry,
        modifier = modifier,
    )
}

/** 空结果：淡灰图标 + 说明；筛选吃掉全部时用「筛选已关」图标并给「继续筛选」，否则盾牌问号、无按钮。 */
@Composable
fun EmptyResultState(
    message: String,
    filterActive: Boolean,
    onFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StateBlock(
        icon = if (filterActive) AppIcons.FilterAltOff else AppIcons.GppMaybe,
        iconTint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
        message = message,
        actionLabel = "继续筛选".takeIf { filterActive },
        onAction = onFilter,
        modifier = modifier,
    )
}

/**
 * 行内提示条：error 色淡底 + 一句说明，用于「目标日不开行」这类**不阻断阅读**的提醒。
 * 与整页 [ErrorState] 分工不同：那条是失败后没有内容可看，这条只是给正文补一个前提。
 */
@Composable
fun InlineErrorNotice(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.error.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.error,
        )
    }
}

/** 大淡色图标 + 说明 + 可选动作按钮，列表状态位的唯一样式。 */@Composable
private fun StateBlock(
    icon: ImageVector,
    iconTint: Color,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = iconTint,
        )
        Text(
            text = message,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .padding(horizontal = 24.dp),
        )
        if (actionLabel != null) {
            Button(
                onClick = rememberHapticTap(onAction),
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .padding(top = 20.dp)
                    .height(44.dp),
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
            }
        }
    }
}
