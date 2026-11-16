package com.crinfo.app.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crinfo.app.data.offline.OfflineDbRepository
import com.crinfo.app.data.remote.LltApi
import com.crinfo.app.data.remote.OfflineVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** 离线数据库（路路通 an.db）的检查更新 / 下载状态机。 */
class OfflineDbViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = OfflineDbRepository(app)

    @Serializable
    sealed interface UpdateStatus {
        data object Idle : UpdateStatus
        data object Checking : UpdateStatus
        data class UpToDate(val version: String) : UpdateStatus
        /** firstInstall = 本机还没有库，文案要说「安装」而不是「更新」。 */
        data class Available(val version: String, val desc: String, val firstInstall: Boolean) : UpdateStatus
        data class Downloading(val done: Long, val total: Long) : UpdateStatus
        data class Failed(val message: String) : UpdateStatus
    }

    var installedVersion by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf<UpdateStatus>(UpdateStatus.Idle)
        private set

    /** 重扫中：这一条动作要重新解析整库，耗时几百毫秒，界面得有个在忙的提示。 */
    var rescanning by mutableStateOf(false)
        private set

    /** 重扫的真实进度 0~1，由解析器按实际处理量给出（不是定时动画）。 */
    var rescanProgress by mutableStateOf(0f)
        private set

    /** 重扫当前在做什么：解压 / 解析线路与里程 / 解析交路车底 / 解析附表 … */
    var rescanStage by mutableStateOf<String?>(null)
        private set

    private var manual = false
    private var downloadJob: Job? = null

    /** 检查拿到的版本里带着镜像地址，下载时直接复用，不再重复请求 android.ver。 */
    private var pendingVersion: OfflineVersion? = null

    /**
     * 离线查询缺库时记下的「下载完成后接着查」动作。
     * 只在「拦截 → 下载成功」这条路上活一次；取消与失败都要清掉，否则攒着一个永远不跑的闭包。
     */
    private var pendingResume: (() -> Unit)? = null

    init {
        // DataStore 是异步的，不预读的话第一帧拿到的是 null，会在「其实早就装过库」的设备上
        // 误弹一次下载框。跟设置项一样用同步快照当初始值。
        installedVersion = com.crinfo.app.data.blockingSnapshot { repo.installedVersionSnapshot() }
        viewModelScope.launch { repo.installedVersion.collect { installedVersion = it } }
        // 车次联想的池子（读表 + 排序一两百毫秒）在这里就做掉，别留给用户按下第一个字符的时候
        viewModelScope.launch(Dispatchers.Default) { repo.warmTrainSuggestions() }
        // 车次联想的池子在这里就做掉：读表 + 排序一两百毫秒，留给用户按下第一个字符时就成了一次卡顿
        viewModelScope.launch(Dispatchers.Default) { repo.warmTrainSuggestions() }
    }

    /**
     * 离线查询前的缺库拦截。库里已装返回 false（调用方照常查）；没装就把续跑动作记下来、
     * 触发一次检查让弹窗出现，返回 true 表示「这次先别查」。
     *
     * 拦截点放在**点查询**时而不是翻开关时：翻开关只是表态，真要出结果了才需要库，
     * 纯翻设置的人不该被下载框打断。
     */
    /**
     * 错误页的「安装数据库」按钮：缺库就弹下载框（返回 true），
     * 其实已经装好则什么都不弹、返回 false，由调用方改走去重查。
     */
    fun promptInstall(): Boolean = interceptWhenMissing { }

    fun interceptWhenMissing(resume: () -> Unit): Boolean {
        if (installedVersion != null) return false
        pendingResume = resume
        when (status) {
            // 闲置，或上一轮判过「已最新」但本地其实没文件（被清过）→ 现查一次版本再弹
            is UpdateStatus.Idle, is UpdateStatus.UpToDate -> check(manual = true)
            else -> Unit
        }
        return true
    }

    /**
     * 检测更新。自动（进应用）与手动（点按钮）走同一条路，差别只在提示：
     * 手动才有 Toast；自动时「已是最新版 / 失败」一律静默，检测到更新才让 UI 弹窗。
     */
    fun check(manual: Boolean) {
        if (status is UpdateStatus.Checking || status is UpdateStatus.Downloading) return
        this.manual = manual
        status = UpdateStatus.Checking
        viewModelScope.launch {
            try {
                val remote = LltApi.fetchVersion()
                val local = repo.installedVersionSnapshot()
                when {
                    !remote.isValid -> notify("未取到有效版本号")
                    // 只有「远端严格新于本地」才提示：相等、或服务器回滚到旧版本号，都不该下载
                    !remote.isDataNewer(local) -> notify("数据库已是最新版")
                    else -> {
                        pendingVersion = remote
                        status = UpdateStatus.Available(
                            remote.data,
                            remote.desc,
                            firstInstall = local.isNullOrBlank(),
                        )
                    }
                }
            } catch (e: Throwable) {
                // 自检跑在壳层协程上：这里漏出去就是崩应用，而它只是「顺便看看有没有新数据」。
                // ExceptionInInitializerError 这类 Error 不是 Exception，必须兜 Throwable。
                android.util.Log.w("CRInfo", "离线库检查更新失败: ${e.javaClass.simpleName}: ${e.message}")
                notify("检查更新失败，请稍后再试")
            }
        }
    }

    fun autoCheckOnLaunch() = check(manual = false)

    /**
     * 重扫：丢掉运行期所有派生表，用已装的库文件重新解析一遍。
     *
     * 不删文件、不重下 —— 只重建解析产物。解析器升级后旧缓存解不动新字段时靠它自愈。
     * 进度由解析器按实际处理量给出，走 [rescanProgress] / [rescanStage] 暴露给界面。
     */
    fun rescan() {
        if (rescanning) return
        if (installedVersion == null) {
            toast("尚未安装离线数据库")
            return
        }
        rescanning = true
        rescanProgress = 0f
        rescanStage = "准备中"
        viewModelScope.launch {
            try {
                // 整库解析要几百毫秒，必须离开主线程；状态与提示回主线程收口
                val db = withContext(Dispatchers.Default) {
                    repo.rescan { fraction, stage ->
                        // 回调来自后台线程，改 Compose 状态要回主线程
                        viewModelScope.launch {
                            rescanProgress = fraction.coerceIn(0f, 1f)
                            rescanStage = stage
                        }
                    }
                }
                if (db == null) {
                    toast("重扫失败，数据库可能已损坏，请重新更新")
                } else {
                    // 版本号没变，但解析产物全换了，重新建一遍联想池
                    withContext(Dispatchers.Default) { repo.warmTrainSuggestions() }
                    rescanProgress = 1f
                    rescanStage = "完成"
                    toast("重扫完成，版本 ${db.version}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.w("CRInfo", "离线库重扫失败: ${e.javaClass.simpleName}: ${e.message}")
                toast("重扫失败：${e.message ?: "未知错误"}")
            } finally {
                rescanning = false
            }
        }
    }

    /** 删除已装的离线库文件与全部派生表。 */
    fun delete() {
        viewModelScope.launch {
            try {
                repo.clear()
                installedVersion = null
                status = UpdateStatus.Idle
                toast("已删除离线数据库")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.w("CRInfo", "离线库删除失败: ${e.javaClass.simpleName}: ${e.message}")
                toast("删除失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun requestDownload() {
        val available = status as? UpdateStatus.Available ?: return
        val version = pendingVersion ?: return
        status = UpdateStatus.Downloading(0, 0)
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            try {
                val bytes = LltApi.downloadAnDb(version) { done, total -> status = UpdateStatus.Downloading(done, total) }
                // 解析通过才算装上；坏包保留上一版，这时不该谎报成功
                repo.install(bytes)
                status = UpdateStatus.UpToDate(available.version)
                // 刚装完就顺手把联想池建好：install 已经把车次表读进内存，这里只差排序
                viewModelScope.launch(Dispatchers.Default) { repo.warmTrainSuggestions() }
                // 先同步刷版本号，再跑续查闭包：installedVersion 靠 Flow 异步回填的话，
                // resume 里那句「还是没库」的判断会把用户重新弹回下载框，转圈自锁。
                installedVersion = repo.installedVersionSnapshot() ?: available.version
                toast("离线数据库已更新至 ${available.version}")
                // 缺库拦截攒下的那次查询，装好就该自动接上，不用用户再点一遍
                val resume = pendingResume
                pendingResume = null
                resume?.invoke()
            } catch (e: CancellationException) {
                status = UpdateStatus.Idle
            } catch (e: Throwable) {
                status = UpdateStatus.Failed(e.message ?: "下载失败")
            }
        }
    }

    /** 弹窗的「取消」/「关闭」：下载中点取消会真的掐掉这次下载。 */
    fun dismissDialog() {
        pendingResume = null
        when (status) {
            is UpdateStatus.Downloading -> downloadJob?.cancel()
            is UpdateStatus.Available, is UpdateStatus.Failed -> status = UpdateStatus.Idle
            else -> Unit
        }
    }

    /** 无更新或检查失败：手动才提示，自动一律静默回到闲置。 */
    private fun notify(message: String) {
        // 走到这里说明这次拿不到可下载的库（已最新或失败），续跑动作不该再留着
        pendingResume = null
        if (manual) {
            status = UpdateStatus.Idle
            toast(message)
        } else {
            status = UpdateStatus.Idle
        }
    }

    private fun toast(message: String) = android.widget.Toast
        .makeText(getApplication(), message, android.widget.Toast.LENGTH_SHORT)
        .show()
}
