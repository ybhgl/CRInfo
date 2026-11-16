package com.crinfo.app.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

/**
 * 全项目统一的条件显隐规格（参考 Melox）：淡入 200ms + 展开 250ms / 淡出 150ms + 收起 200ms。
 *
 * 收尾比起步略慢，展开时不抢内容、收起时不拖沓；所有「展开更多 / 子开关 / 保存提示」类
 * `AnimatedVisibility` 都复用这一对常量，避免各页各写一套默认 spec 导致节奏不一致。
 * 方向性位移（左右指示箭头）、罗盘显隐、底栏收起有各自的语义时长，不走这里。
 */
val AppExpandEnter: EnterTransition = fadeIn(tween(200)) + expandVertically(tween(250))
val AppExpandExit: ExitTransition = fadeOut(tween(150)) + shrinkVertically(tween(200))
