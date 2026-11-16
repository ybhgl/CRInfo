package com.crinfo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 小按钮的配色档位。 */
enum class MiniActionTone {
    /** 实心强调色：这一组里的主动作。 */
    Primary,

    /** 中性底色：辅助动作。 */
    Neutral,

    /** 危险色：不可逆的删除类动作。 */
    Danger,

    /** 浅底 + 主色字：行尾动作，既要有一圈底色又要小。 */
    Accent,
}

/**
 * 紧凑小按钮。
 *
 * Miuix 的 [top.yukonga.miuix.kmp.basic.TextButton] 内边距与最小高度都偏大，
 * 塞进偏好行的行尾会把整行撑高，所以这里自绘一个 28dp 高的小胶囊。
 *
 * 用途：设置页行尾的「清空」、离线数据库那一条底下的多按钮组。
 */
@Composable
fun MiniActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: MiniActionTone = MiniActionTone.Neutral,
) {
    val colors = MiuixTheme.colorScheme
    val background = when (tone) {
        MiniActionTone.Primary -> colors.primary
        MiniActionTone.Neutral -> colors.surfaceContainerHigh
        MiniActionTone.Danger -> colors.error.copy(alpha = 0.12f)
        MiniActionTone.Accent -> colors.primary.copy(alpha = 0.12f)
    }
    val foreground = when (tone) {
        MiniActionTone.Primary -> colors.onPrimary
        MiniActionTone.Neutral -> colors.onSurface
        MiniActionTone.Danger -> colors.error
        MiniActionTone.Accent -> colors.primary
    }
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 28.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 13.sp, color = foreground)
    }
}
