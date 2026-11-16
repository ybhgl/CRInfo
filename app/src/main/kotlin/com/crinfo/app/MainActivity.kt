package com.crinfo.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crinfo.app.data.SelectionStore
import com.crinfo.app.ui.CRInfoApp
import com.crinfo.app.ui.settings.SettingsViewModel
import com.crinfo.app.ui.theme.CRInfoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private object NoopHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        // 弹层内部已用 imePadding() 补偿输入法高度；若窗口再随输入法缩放就会双重补偿，
        // 在 sheet 底边与键盘顶之间留出一条缝
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        super.onCreate(savedInstanceState)
        // 提前转起 crinfo_routes 的首次读：首页「上次区间」快照只给 400ms 预算，
        // 冷启动首次打开该 DataStore 要现场读文件，常常等不到就落成默认区间。
        // 预热与快照共享同一次读，快照大概率直接命中；读不到时 VM 里还有异步回填兜底。
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { SelectionStore(applicationContext).warmUp() }
        }
        setContent {
            val settings: SettingsViewModel = viewModel()
            val haptics = if (settings.settings.haptics) {
                LocalHapticFeedback.current
            } else {
                NoopHapticFeedback
            }
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                CRInfoTheme(settings = settings.settings) {
                    CRInfoApp()
                }
            }
        }
    }
}
