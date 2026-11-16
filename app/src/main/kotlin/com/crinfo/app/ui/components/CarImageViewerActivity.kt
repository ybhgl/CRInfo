package com.crinfo.app.ui.components

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 车辆图片的全屏查看器宿主。透明 Activity 只负责数据传递与系统栏，
 * 手势与渲染全部在 [CarImageViewer] 里完成，不引入第三方图片库。
 */
class CarImageViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val urls = intent.getStringArrayListExtra(EXTRA_URLS).orEmpty()
        if (urls.isEmpty()) {
            finish()
            return
        }
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 只把系统栏图标改成浅色，不隐藏系统栏：
        // 隐藏会连带改变底层弹窗的 insets，导致进出大图时页面反复跳动。
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            CarImageViewer(
                urls = urls,
                initialIndex = intent.getIntExtra(EXTRA_INDEX, 0),
                onClose = { finishViewer() },
            )
        }
    }

    private fun finishViewer() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_URLS = "car_image_urls"
        private const val EXTRA_INDEX = "car_image_index"

        /** 从车辆图片弹窗点图进入，[index] 是该组图片里的第几张。 */
        fun start(context: Context, urls: List<String>, index: Int) {
            if (urls.isEmpty()) return
            context.startActivity(
                Intent(context, CarImageViewerActivity::class.java)
                    .putStringArrayListExtra(EXTRA_URLS, ArrayList(urls))
                    .putExtra(EXTRA_INDEX, index.coerceIn(0, urls.lastIndex)),
            )
        }
    }
}
