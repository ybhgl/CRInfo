package com.crinfo.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 把点击包一层振动。触感开关关掉时根上提供的是 NoopHapticFeedback，这里自动静音。
 */
@Composable
fun rememberHapticTap(onClick: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic, onClick) {
        {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    }
}
