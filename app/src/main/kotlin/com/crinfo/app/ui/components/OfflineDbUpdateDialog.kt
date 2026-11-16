package com.crinfo.app.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinfo.app.ui.settings.OfflineDbViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 离线数据库更新弹窗。自动检测只在真的发现新版本时才弹这个框；
 * 点「下载」后留在框内显示进度，total 取不到（服务端没给 Content-Length）时降级成不确定进度。
 */
@Composable
fun OfflineDbUpdateDialog(vm: OfflineDbViewModel) {
    val status = vm.status
    val available = status as? OfflineDbViewModel.UpdateStatus.Available
    val downloading = status as? OfflineDbViewModel.UpdateStatus.Downloading
    val failed = status as? OfflineDbViewModel.UpdateStatus.Failed
    val show = available != null || downloading != null || failed != null
    if (!show) return

    val context = LocalContext.current
    OverlayDialog(show = true, title = when {
        failed != null -> "更新失败"
        downloading != null -> "正在下载离线数据库"
        available?.firstInstall == true -> "安装离线数据库"
        else -> "发现新版本"
    }, onDismissRequest = { vm.dismissDialog() }) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)) {
            when {
                available != null -> Text(
                    text = if (available.firstInstall) {
                        // 这条现在多由「离线查询点下查询」触发，要说清是它拦下来的、下完会自动续查
                        "离线查询需安装离线数据库（约 2.3MB）"
                    } else {
                        "发现数据库更新 ${available.version}"
                    },
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                downloading != null -> {
                    val fraction = if (downloading.total > 0) {
                        (downloading.done.toFloat() / downloading.total).coerceIn(0f, 1f)
                    } else null
                    LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (downloading.total > 0) {
                            "${Formatter.formatShortFileSize(context, downloading.done)} / " +
                                Formatter.formatShortFileSize(context, downloading.total)
                        } else {
                            "已下载 ${Formatter.formatShortFileSize(context, downloading.done)}"
                        },
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                failed != null -> Text(
                    text = failed.message,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(
                    onClick = { vm.dismissDialog() },
                    enabled = downloading == null,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(
                        text = if (downloading != null) "取消" else "关闭",
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                if (available != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { vm.requestDownload() },
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = MiuixTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(
                            text = "下载",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
