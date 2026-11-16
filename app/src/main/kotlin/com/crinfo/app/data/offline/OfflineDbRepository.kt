package com.crinfo.app.data.offline

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.offlineDbStore by preferencesDataStore(name = "crinfo_offline_db")

/**
 * 已安装离线库的落地位置。原始 an.db 随包体积太大，改成下载后放 filesDir，
 * 只有解析通过才提交版本号——半截文件或坏包一律保留上一版，功能不会被打死。
 */
class OfflineDbRepository(private val context: Context) {
    private val versionKey = stringPreferencesKey("offline_db_version")
    private val lock = Mutex()

    /** 分片随机读走这把锁：`current()` 已经拿着 `lock`，同一把锁嵌套会自己锁死自己。 */
    private val ioLock = Mutex()
    private var cached: OfflineDb? = null
    private var cachedVersion: String? = null

    /**
     * 车次表（`t.i`）的常驻值，键是库版本；只给输入联想用，见 [trainCodesSnapshot]。
     *
     * 存**进程级**而不是实例级：车次页、详情页、离线库页各 new 一份仓库，
     * 挂在实例上就变成「谁预热谁快」，别的实例照样重新读盘、同步快路也一直命不中。
     */
    private var codesMemo: Pair<String, List<String>>?
        get() = sharedCodes
        set(value) {
            sharedCodes = value
        }

    private val file: File get() = File(context.filesDir, "llt_an.db")

    val installedVersion: Flow<String?> =
        context.offlineDbStore.data.map { it[versionKey]?.takeIf { v -> v.isNotBlank() } }

    suspend fun installedVersionSnapshot(): String? = installedVersion.first()

    /** 校验并安装一份新下载的数据；失败抛 OfflineDbException，旧库不动。 */
    suspend fun install(bytes: ByteArray): OfflineDb = lock.withLock {
        val db = try {
            OfflineDbParser.parse(bytes)
        } catch (e: OfflineDbException) {
            throw e
        } catch (e: Exception) {
            throw OfflineDbException("数据包解析失败：${e.message}")
        }
        // 先解析再落盘：坏包绝不会覆盖掉能用的旧库
        val tmp = File(context.filesDir, "llt_an.db.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        cached = db
        cachedVersion = db.version
        // 刚装完库正是用户最想试联想的时候，别让他再等一次读盘
        codesMemo = db.version to db.trainCodes
        context.offlineDbStore.edit { prefs -> prefs[versionKey] = db.version }
        db
    }

    /** 已装库的解析结果；没装过或文件损坏返回 null，调用方自行回落在线接口。 */
    suspend fun current(): OfflineDb? = lock.withLock {
        val version = installedVersionSnapshot() ?: return@withLock null
        if (cached != null && cachedVersion == version) return@withLock cached
        if (!file.isFile) {
            // 文件没了但 DataStore 还留着版本号（用户清了应用数据、或装完被手删）时，
            // 检查更新会一直回「已是最新版」，离线功能永久起不来 —— 版本号得跟着文件一起失效。
            context.offlineDbStore.edit { prefs -> prefs.remove(versionKey) }
            cached = null
            cachedVersion = null
            return@withLock null
        }
        val db = runCatching { OfflineDbParser.parse(file.readBytes()) }.getOrNull()
        if (db == null) {
            // 解析器升级后旧包可能解不动，但版本号没变 → 「版本一致不重下」会把新功能永久卡死；
            // 清掉版本号让下次进应用重新拉一份（文件留着，不当坏包删）。
            context.offlineDbStore.edit { prefs -> prefs.remove(versionKey) }
            cached = null
            cachedVersion = null
            return@withLock null
        }
        cached = db
        cachedVersion = db.version
        db
    }

    suspend fun clear() = lock.withLock {
        file.delete()
        cached = null
        cachedVersion = null
        codesMemo = null
        readers.clear()
        indices.clear()
        TrainSuggestionIndex.invalidate()
        context.offlineDbStore.edit { prefs -> prefs.remove(versionKey) }
    }

    /**
     * 重扫：丢掉内存里所有派生表，用已装的库文件重新解析一遍。
     *
     * 落地的文件本身不动、版本号不动 —— 这一步只重建运行期的解析产物
     * （[OfflineDb]、分片偏移表、车次表常驻值、联想池）。
     * 解析器升级后旧缓存解不动新字段，靠这条路自愈。
     *
     * [onProgress] 回报真实进度（0~1）与阶段说明，由解析器按实际处理量给出。
     */
    suspend fun rescan(onProgress: (Float, String) -> Unit = { _, _ -> }): OfflineDb? {
        lock.withLock {
            cached = null
            cachedVersion = null
            codesMemo = null
            synchronized(readers) { readers.clear() }
            synchronized(indices) { indices.clear() }
        }
        TrainSuggestionIndex.invalidate()
        // 这里不走 current()：它只在缓存失效时重新解析、且不回报进度，
        // 而重扫要的是「从头完整走一遍并把进度报出来」。
        val version = installedVersionSnapshot() ?: return null
        if (!file.isFile) {
            context.offlineDbStore.edit { prefs -> prefs.remove(versionKey) }
            return null
        }
        val db = runCatching { OfflineDbParser.parse(file.readBytes(), onProgress) }.getOrNull() ?: run {
            // 解析不出来就说明文件坏了：版本号跟着失效，好让「更新」能重新拉一份
            context.offlineDbStore.edit { prefs -> prefs.remove(versionKey) }
            return null
        }
        lock.withLock {
            cached = db
            cachedVersion = db.version
            codesMemo = db.version to db.trainCodes
        }
        onProgress(1f, "完成")
        return db
    }

    /* ---------------- 时刻表与板面：按分片随机读 ---------------- */

    /**
     * 车次 ID 落在 `t[(id+1)%20]`、车站 ID 落在 `s[(id+1)%10]`。
     * **片内声明的分片号是 `id%20`，与文件名差一格**，所以路由一律按这条公式、别拿文件名下标当语义。
     */
    fun trainShardOf(trainId: Int): Int = (trainId + 1) % TRAIN_SHARDS
    fun stationShardOf(stationId: Int): Int = (stationId + 1) % STATION_SHARDS

    private fun trainShardName(shard: Int) = "T$shard.dat"
    private fun stationShardName(shard: Int) = "S$shard.dat"

    /** 只留最近几片：一片约 62KB，全摊着就失去按需读取的意义了。 */
    private val readers = object : LinkedHashMap<String, OfflineShardReader>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OfflineShardReader>) = size > 3
    }
    private val indices = HashMap<String, ShardIndex>()

    /** 读一个分片的字节并包成 reader；库里没有这一片就返回 null（由调用方决定回落还是报缺数据）。 */
    suspend fun shardReader(name: String): OfflineShardReader? {
        synchronized(readers) { readers[name] }?.let { return it }
        val bytes = readEntry(name) ?: return null
        val reader = OfflineShardReader(bytes)
        synchronized(readers) { readers[name] = reader }
        return reader
    }

    /** 走步建某一片的偏移表；同一片只建一次，常驻的是偏移表（千来条）而不是整片时刻。 */
    suspend fun trainIndex(shard: Int): ShardIndex? {
        val name = trainShardName(shard)
        synchronized(indices) { indices[name] }?.let { return it }
        val built = shardReader(name)?.trainIndex() ?: return null
        synchronized(indices) { indices[name] = built }
        return built
    }

    suspend fun stationIndex(shard: Int): ShardIndex? {
        val name = stationShardName(shard)
        synchronized(indices) { indices[name] }?.let { return it }
        val built = shardReader(name)?.stationIndex() ?: return null
        synchronized(indices) { indices[name] = built }
        return built
    }

    /** 取某趟车的图定时刻表（记录级随机读，不解全库）。 */
    suspend fun timetable(trainId: Int): OfflineTimetable? {
        val shard = trainShardOf(trainId)
        val index = trainIndex(shard) ?: return null
        val at = index.indexOf(trainId)
        if (at < 0) return null
        return shardReader(trainShardName(shard))?.readTrain(index.offsets[at])
            ?.takeIf { it.trainId == trainId }
    }

    /**
     * 某站的板面车次 ID 列表。
     *
     * ⚠️ 「北京」「上海」「广州」这类**城市主站**在这一列里挂的是**同城合集**
     * （实测北京站 1389 个，而时刻表里真正停靠本站的只有 263 个；带方位后缀的站两边精确相等）。
     * 所以这份列表只当**候选**，要显示到发时刻、要断言「这趟车真的停本站」，必须再用时刻表记录核对。
     */
    suspend fun stationBoardCandidates(stationId: Int): IntArray {
        val shard = stationShardOf(stationId)
        val index = stationIndex(shard) ?: return IntArray(0)
        val at = index.indexOf(stationId)
        if (at < 0) return IntArray(0)
        val board = shardReader(stationShardName(shard))?.readStationBoard(index.offsets[at])
            ?: return IntArray(0)
        return board.filter { it in 0 until (cached?.trainCount ?: Int.MAX_VALUE) }.toIntArray()
    }

    /** 按站名查库内车站 ID（跨源只用站名对齐：库里没有 12306 车站代码）。 */
    suspend fun stationIdOf(name: String): Int? = current()?.stationIdByName?.get(name.trim())

    /** 按车次号查库内车次 ID。 */
    suspend fun trainIdOf(code: String): Int? = current()?.trainIdOf(code)

    /**
     * 只读 `t.i` 拿 (库版本, 车次表)，给输入联想用。
     *
     * 整包 2.2MB 里 routes/plat/jlb 就占了九成，而猜一串车次号只要 `t.i` 那 127KB：
     * 走 `current()` 等于为联想背一次全库解析（几百毫秒起步），首次输入的那下卡顿就是这么来的。
     * 解出来的表按版本常驻，之后每次按键都是纯内存匹配。库没装或表异常返回 null。
     */
    suspend fun trainCodesSnapshot(): Pair<String, List<String>>? {
        codesMemo?.let { return it }
        val version = installedVersionSnapshot() ?: return null
        val bytes = readEntry("t.i") ?: return null
        // 与 OfflineDbParser.parse 同一张字符集与同一道数量校验，别在这里放宽标准
        val codes = runCatching { OfflineDbParser.parseIndexTable(bytes, Charsets.ISO_8859_1) }.getOrNull()
        if (codes == null || codes.size < MIN_TRAIN_CODES) return null
        return (version to codes).also { codesMemo = it }
    }

    /**
     * 已在内存里的 (库版本, 车次表)，没读过就 null。非挂起：界面用它判「能不能当场同步算候选」。
     */
    val trainCodesMemo: Pair<String, List<String>>? get() = codesMemo

    /**
     * 把联想要读的车次表读出来并建好池子供预热。读盘与排序都在调用方线程，所以请放后台调。
     *
     * 真机口径（本机 JVM 实测，手机更慢一档）：读 `t.i` 21ms + 解表 4ms + 建池排序 110ms。
     * 这些活儿留在「按下第一个字符」那条路上，用户就得白等这一截，所以应用启动时先做掉。
     */
    suspend fun warmTrainSuggestions() {
        trainCodesSnapshot()?.let { (version, codes) -> TrainSuggestionIndex.of(version, codes) }
    }

    /**
     * 装配离线查询引擎。库里没装就返回 null —— 调用方据此提示下载，
     * 不能返回一个「查什么都空」的引擎，那样界面会把缺库当成无车次。
     */
    suspend fun queryEngine(context: android.content.Context): OfflineQueryEngine? {
        val db = current() ?: return null
        return OfflineQueryEngine(
            db = db,
            readTrain = { id -> timetable(id) },
            readCandidates = { id -> stationBoardCandidates(id) },
            stationOf = { name -> com.crinfo.app.data.StationRepository.byName(context, name) },
        )
    }

    private suspend fun readEntry(name: String): ByteArray? = ioLock.withLock {
        if (!file.isFile) return@withLock null
        runCatching {
            ZipFile(file).use { zip ->
                val entry = zip.entries().toList().firstOrNull {
                    it.name.substringAfterLast('/').equals(name, ignoreCase = true)
                } ?: return@use null
                zip.getInputStream(entry).use { it.readBytes() }
            }
        }.getOrNull()
    }

    private companion object {
        const val TRAIN_SHARDS = 20
        const val STATION_SHARDS = 10

        /** 车次表的最低条数门槛，与 OfflineDbParser.parse 里的校验同源：低于它就是读歪了。 */
        const val MIN_TRAIN_CODES = 1000

        /** 进程级车次表常驻值，[codesMemo] 读写都落到这里。 */
        @Volatile
        var sharedCodes: Pair<String, List<String>>? = null
    }
}
