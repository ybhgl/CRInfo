package com.crinfo.app.data.offline

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 线路上的一个节点：略号就是 12306 电报码，km 相对**本线路**自身起点。
 *
 * `routeFlag` 是 `RoutesMgr` 里的正式字段（4 字节大端，旧实现把它的字节当成哨兵）。
 * ⚠️ 实测全库 7366 个节点**取值只有一个 -1**，对「营业站 / 线路所」没有任何区分力 ——
 * 别拿它筛节点。要判断是不是线路所，只能看站名能否命中 `s.i`（不在的样例：化新村所、龙山所、米宁庄所）。
 */
data class LineStop(val telecode: String, val name: String, val km: Int, val routeFlag: Int = 0)

/**
 * routes.dat 解出的一条线路。`lineId` 是库内引用线路的**唯一句柄**（竞品每张表都按它索引），
 * 和数组下标不等价 —— 有单站线路和空号，按下标 join 会整体漂移。
 */
data class RouteLine(val id: Int, val name: String, val stops: List<LineStop>)

/** jlb.dat 解出的一列车底。carType 含调向/重联备注，group 是按周转顺序排列的担当车次组。 */
data class JiaoluRake(
    val carType: String,
    val note: String,
    val group: List<String>,
    /**
     * 记录头前两个 int32。实测取值是 `0x0135004f`、`0x01404cff` 这类**结构字节**
     * （高位 `01`、次位 `35`/`34` 正是当年被我误认成「记录族标记」的那两下）。
     * 路路通把它们当数值区间门槛用（`key0 <= 日期整数 <= key1`），
     * 而 2024 万～2100 万必然盖住任何 `yyyyMMdd`，**等于恒成立** —— 不要当成日期区间。
     */
    val key0: Int = 0,
    val key1: Int = 0,
    /** 再往后两个 int32：3862 条里 3860 条恒为 1，只有 2 条厂商测试记录是 7/15，无信息量。 */
    val field8: Int = 0,
    val field12: Int = 0,
    /**
     * 担当企业代码，**就是 `extra.dat` 企业名表的下标**（`ExtraDataMgr.getQiYe` 即 `list[i]`）。
     * 实测 58 个不同取值、全部在表内不越界：G1/G3/1461 → 83「上局上海段」，D1 → 218「广铁长沙段」
     * —— 担当局段离线可得。
     */
    val enterpriseCode: Int = 0,
)

/**
 * jlb.dat 尾部的餐车表：某车次在 `[startDate, endDate]`（`yyyyMMdd` 整数，真日期）内餐车在 `coach` 车厢。
 * 路路通按 `车次ID == trainId && startDate <= 日期 <= endDate` 取，实测 13513 条、串共 53 种，
 * 形如 `9车[餐]`、`10车有餐座`、`4车无餐座`、`无餐车`。
 */
data class FoodCoach(
    val trainId: Int,
    val startDate: Int,
    val endDate: Int,
    val coach: String,
)

/** 径路上的一次换线：从 `station` 起改走 `lineId` 那条线。 */
data class RouteLeg(val station: String, val lineId: Int)

/**
 * routes.dat §2 的每车官方径路：竞品界面显示的「经由」就是这一份。
 * 键与 12306 时刻表接口的 `trainNo`（12 位，如 `4f000G373907`）完全一致，可直接查。
 */
data class TrainRoute(val terminus: String, val legs: List<RouteLeg>)

/** routes.dat 的两张表：§1 线路与节点里程，§2 每车径路。 */
data class RouteTable(
    val declaredLines: Int,
    val lines: List<RouteLine>,
    val declaredTrains: Int,
    val trainRoutes: Map<String, TrainRoute>,
)

/**
 * 路路通 an.db 的解析结果。lines/trainRoutes 是经由与里程（库里没装时刻表时也只有这两份），
 * jlb 解不出来只让 rakes 为空，不连坐整个库。
 *
 * trainCodes/stationNames 是 t.i、s.i 两张主键表，**下标即 ID**；
 * rules 是 t.rule，按下标被车次记录里的 ruleIndex 引用。
 * 时刻与板面不在这里 —— 它们按分片随机读，见 [OfflineShardReader]。
 */
data class OfflineDb(
    val version: String,
    val lines: List<RouteLine>,
    val trainRoutes: Map<String, TrainRoute> = emptyMap(),
    val rakes: List<JiaoluRake>,
    /** jlb 尾部的餐车表；解不出来只让这一项为空，不连坐整库。 */
    val foodCoaches: List<FoodCoach> = emptyList(),
    /** extra.dat 的企业名表；下标 = [JiaoluRake.enterpriseCode]，解不出来只是担当名缺失。 */
    val enterprises: List<String> = emptyList(),
    val trainCodes: List<String> = emptyList(),
    val stationNames: List<String> = emptyList(),
    val rules: List<RunningRule> = emptyList(),
    /**
     * 本轮补齐的其余各表（担当、股道、席别、同城站、地址、票价族等）。
     * 缺哪组只是那组为空，主流程（时刻/经由/交路）不受影响。
     */
    val extras: OfflineExtras = OfflineExtras.EMPTY,
) {
    /** 电报码 → 该站所在线路及里程。 */
    val stopsByTelecode: Map<String, List<Pair<String, Int>>> =
        lines.flatMap { line -> line.stops.map { it.telecode to (line.name to it.km) } }
            .groupBy({ it.first }, { it.second })

    /**
     * 站名 → 电报码：routes §1 里带略号的节点，再加 sn.dat 表3 的 364 条补充。
     *
     * 离线态的电报码本来只能问 12306 站表（`stationOf`），站表查不到的站就直接丢掉 ——
     * 换号提示与逐站里程都按电报码建键，于是整行信息一起消失。库里这份是同源的第二层来源，
     * 同名冲突时先到先得（线路节点按线路ID顺序排列，sn 表3 在最后补）。
     */
    val telecodeByStationName: Map<String, String> = buildMap {
        lines.forEach { line ->
            line.stops.forEach { stop ->
                if (stop.telecode.isNotBlank()) putIfAbsent(stop.name.trim(), stop.telecode)
            }
        }
        extras.cities.telecodes.forEach { (name, tele) -> putIfAbsent(name, tele) }
    }

    /** 按站名要电报码；库里也没有才返回 null，交回调用方决定空着还是猜。 */
    fun telecodeOfStation(name: String): String? = telecodeByStationName[name.trim()]

    /**
     * 本车次在哪些站调向（extra.dat 表6，真库 1073 条、其中 111 条多站）。
     * 站名按库内 `s.i` 落地；库里没这条记录就是空表 —— 不表示「这趟车不调向」，
     * 只表示库里没说，所以界面上没值就整行不出现。
     */
    fun turnStationNames(trainId: Int): List<String> = extras.turnStationsOf(trainId)
        .map { stationId -> stationNames.getOrNull(stationId)?.trim().orEmpty() }
        .filter { it.isNotEmpty() }

    /** 线路ID → 线路。径路表里的 ID 只能靠它落地成线路名。 */
    val linesById: Map<Int, RouteLine> = lines.associateBy { it.id }

    /**
     * 车次号 → 库内 ID。库里把同车底多车次写成 `G1956/G1957`、`4167/4170` 一格，
     * 所以除了整串还要把每一段单独建键，用户只输 `G1957` 也要能命中；
     * 整串优先占位，段键用 putIfAbsent，避免裸数字段把真车次挤掉。
     */
    val trainIdByCode: Map<String, Int> = buildMap {
        trainCodes.forEachIndexed { index, code ->
            put(OfflineDb.normalizeTrainCode(code), index)
            code.split('/').map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                putIfAbsent(OfflineDb.normalizeTrainCode(it), index)
            }
        }
    }

    /** 站名 → 库内 ID。站名是跨源唯一可靠的键（库里没有电报码之外的站标识）。 */
    val stationIdByName: Map<String, Int> =
        stationNames.withIndex().associate { (i, name) -> name.trim() to i }

    /**
     * 车次号 → 库内 ID，内部就归一大小写与空白。
     * 别直接索引 [trainIdByCode]：那张表的键是归一过的，忘了归一只会静默拿到 null。
     */
    fun trainIdOf(code: String): Int? = trainIdByCode[normalizeTrainCode(code)]

    /**
     * 按**车次 ID** 取 §2 官方径路。`sp.dat` 表2 是「下标即车次 ID → 12 位 trainNo」，
     * 而 §2 的键正是那份 12 位号（真库 14766 个键 100% 命中它），所以这条路径是精确查表；
     * [findTrainRouteByDisplayCode] 那套「车号后恰好剩 2 字符」的锚定，只在库里没有 sp.dat 时才需要。
     */
    fun findTrainRouteByTrainId(trainId: Int): TrainRoute? =
        extras.trainNoOf(trainId)?.let { trainRoutes[it] }

    /**
     * 按**显示车次号**查 §2 官方径路 —— 库里装了 sp.dat 时优先走 [findTrainRouteByTrainId]（精确查表），
     * 这条只做退化兜底：12 位键的规律是 `[变长前缀][显示车次号原样][2 位后缀]`
     * （真库实测 D195 → `550000D19501`、G3739 → `4f000G373907`、1461 → `240000146100`），
     * 所以车次号后面必须**恰好剩 2 个字符**才算锚定 —— `D195` 同时是 `D1950` 这类真车号的前缀，
     * `1461` 也被 `G1461` 的键包含（真库都有）。多候选时按终到站名、再按首腿站名（= 始发站）过滤；
     * 解不开就返回 null 交回图搜索，绝不把别的车的径路糊上来。
     */
    fun findTrainRouteByDisplayCode(code: String, origin: String, terminus: String): TrainRoute? {
        // 换号车（K1276/K1277）的 §2 只有**一条全程径路**，键只嵌其中一段的号；
        // 整串永远匹配不上，必须拆段逐段锚定，任一段命中就是这趟车的全程。
        for (segment in code.split('/')) {
            anchoredRoute(segment.trim(), terminus, origin)?.let { return it }
        }
        return null
    }

    private fun anchoredRoute(needleRaw: String, terminus: String, origin: String): TrainRoute? {
        val needle = normalizeTrainCode(needleRaw)
        if (needle.isEmpty()) return null
        val hits = trainRoutes.mapNotNull { (key, route) ->
            val upper = key.uppercase()
            var idx = upper.indexOf(needle)
            while (idx >= 0) {
                if (upper.length - idx - needle.length == 2) return@mapNotNull route
                idx = upper.indexOf(needle, idx + 1)
            }
            null
        }
        if (hits.isEmpty()) return null
        if (hits.size == 1) return hits.single()
        val byTerm = hits.filter { it.terminus == terminus }
        if (byTerm.size == 1) return byTerm.single()
        val pool = if (byTerm.isEmpty()) hits else byTerm
        return pool.filter { it.legs.firstOrNull()?.station == origin }.singleOrNull()
    }

    fun foodCoachOf(trainId: Int, date: Int): String? =
        foodCoaches.firstOrNull { it.trainId == trainId && (date == 0 || (date >= it.startDate && date <= it.endDate)) }?.coach

    /** 按显示车次号取图定餐车串（如「9车[餐]」）；车次不在库或无该日记录为 null。 */
    fun foodCoachFor(code: String, date: Int): String? = trainIdOf(code)?.let { foodCoachOf(it, date) }

    val lineCount: Int get() = lines.size
    val stopCount: Int get() = lines.sumOf { it.stops.size }
    val trainCount: Int get() = trainCodes.size
    val stationCount: Int get() = stationNames.size

    companion object {
        /** 同一车底多车次写成 `G1956/G1957`，库内把它当一个键；查询侧要能对上。 */
        fun normalizeTrainCode(code: String): String = code.trim().uppercase()
    }
}

/**
 * an.db 不是 SQLite 而是 ZIP，内部是私有二进制。字段布局（全部大端）：
 *
 * routes.dat = `[4B 线路数][列定义]` + §1 每线 `[4B 线路ID][1B 名字节数][线路名][4B 节点数]` + 节点
 * 节点   = `[站名字节数 nl][站名 UTF-8][略号字节数=3][略号][FF FF FF FF][里程 4B]`
 * §2     = `[4B 车次数]` + 每车 `[0x0C][12B trainNo][1B nl][终到站][4B 换线点数]` + `nl 站名 + 4B 线路ID`
 * jlb.dat = `[4B 车底数]` + 车底记录紧接排列，之后是另一张餐座备注表
 *
 * 略号实测就是 12306 电报码，可直接和项目站表 join。
 */
object OfflineDbParser {
    private const val MAX_RAKES = 20000
    private val NAME_OK = Regex("^[\\u4e00-\\u9fffA-Za-z0-9（）()·\\-_/，, ]{2,30}$")

    /** 担当组段：字母+数字，可带 `/`（同车底多车次）、`;`、`+`、`-` 和括号备注。 */
    private val SEGMENT_OK = Regex("^[0-9A-Za-z;/+\\-()（）]{1,24}$")

    /**
     * 解析整库。[onProgress] 回报真实进度（0~1）与当前阶段说明。
     *
     * 权重按实测耗时分配：解压与 routes.dat 占大头（routes.dat 单独就占整包九成），
     * 附表次之，主键表很快。进度是**按字节/条数算出来的真值**，不是定时动画。
     */
    fun parse(bytes: ByteArray, onProgress: ((Float, String) -> Unit)? = null): OfflineDb {
        onProgress?.invoke(0f, "解压数据包")
        val entries = unzip(bytes) { p -> onProgress?.invoke(p * 0.20f, "解压数据包") }
        val version = entries["ver.txt"]?.let {
            Regex("\\d{8}").find(String(it, Charsets.ISO_8859_1))?.value
        } ?: throw OfflineDbException("缺少 ver.txt 或版本号不是 8 位日期")
        val routes = entries["routes.dat"] ?: throw OfflineDbException("缺少 routes.dat")
        val table = parseRouteTable(routes) { p -> onProgress?.invoke(0.20f + p * 0.35f, "解析线路与里程") }
        if (table.lines.size < 100) throw OfflineDbException("线路条数异常（${table.lines.size}）")
        onProgress?.invoke(0.57f, "解析交路车底")
        val rakes = entries["jlb.dat"]?.let {
            runCatching { parseJlb(it) { p -> onProgress?.invoke(0.57f + p * 0.13f, "解析交路车底") } }.getOrNull()
        } ?: emptyList()
        val food = entries["jlb.dat"]?.let { runCatching { parseFoodCoaches(it) }.getOrNull() } ?: emptyList()
        onProgress?.invoke(0.72f, "读取车次与车站表")
        // 主键表解不出来不该连坐整个库（经由/里程照旧能用），所以各表单独兜底
        val trainCodes = entries["t.i"]?.let { runCatching { parseIndexTable(it, Charsets.ISO_8859_1) }.getOrNull() }
            .orEmpty()
        val stationNames = entries["s.i"]?.let { runCatching { parseIndexTable(it, Charsets.UTF_8) }.getOrNull() }
            .orEmpty()
        if (trainCodes.size < 1000) throw OfflineDbException("车次表 t.i 异常（${trainCodes.size}）")
        if (stationNames.size < 1000) throw OfflineDbException("车站表 s.i 异常（${stationNames.size}）")
        onProgress?.invoke(0.76f, "解析规则与企业表")
        val rules = entries["t.rule"]?.let { runCatching { RunningRuleTable.parse(it.toString(Charsets.UTF_8)) }.getOrNull() }
            .orEmpty()
        val enterprises = entries["extra.dat"]?.let { runCatching { parseEnterprises(it) }.getOrNull() }.orEmpty()
        onProgress?.invoke(0.80f, "解析附表")
        val extras = runCatching {
            OfflineExtras.parse(entries, trainCodes.size) { p -> onProgress?.invoke(0.80f + p * 0.19f, "解析附表") }
        }.getOrNull() ?: OfflineExtras.EMPTY
        onProgress?.invoke(1f, "完成")
        return OfflineDb(
            version = version,
            lines = table.lines,
            trainRoutes = table.trainRoutes,
            rakes = rakes,
            foodCoaches = food,
            enterprises = enterprises,
            trainCodes = trainCodes,
            stationNames = stationNames,
            rules = rules,
            extras = extras,
        )
    }

    /**
     * t.i / s.i：`[2B 大端条数]` + 每条 `[2B 大端字节长度][内容]` —— 就是 Java 的
     * `DataInputStream.readShort()` 配 `readUTF()`，**数组下标即 ID**。
     */
    fun parseIndexTable(b: ByteArray, charset: java.nio.charset.Charset): List<String> {
        if (b.size < 2) return emptyList()
        val count = be(b, 0, 2)
        if (count !in 1..100000) return emptyList()
        var p = 2
        val out = ArrayList<String>(count)
        while (out.size < count && p + 2 <= b.size) {
            val len = be(b, p, 2)
            p += 2
            if (len <= 0 || len > 64 || p + len > b.size) break
            out.add(String(b, p, len, charset))
            p += len
        }
        return out
    }

    /**
     * 解出需要的那些条目。[onProgress] 按**已读字节数 / 总字节数**回报 0~1。
     *
     * 用字节数而不是条目数：几个大表（routes.dat、jlb.dat）本身就占了绝大部分字节，
     * 按条目算会全程趴在 0。
     */
    private fun unzip(bytes: ByteArray, onProgress: ((Float) -> Unit)?): Map<String, ByteArray> {
        // T*/S* 分片不在这里：它们由 OfflineDbRepository 按需随机读，常驻的只是偏移表。
        // station_name.js 也不解：它与项目 tools/station_name.js 同源，stations.json 已经是它的产物。
        val wanted = setOf(
            "routes.dat", "jlb.dat", "ver.txt", "t.i", "s.i", "t.rule", "extra.dat",
            "sp.dat", "sn.dat", "plat.dat", "xw.dat", "ts.dat", "sch.dat", "ime.dat",
            "cdn.dat", "zcdn.dat", "rwp.dat", "dwp.dat",
            "p0.dat", "p1.dat", "p2.dat", "p3.dat", "p4.dat",
            "pk0.dat", "pk1.dat", "pk2.dat", "pk3.dat", "pk4.dat",
            "pk5.dat", "pk6.dat", "pk7.dat", "pk8.dat", "pk9.dat",
        )
        val out = HashMap<String, ByteArray>()
        val total = bytes.size.coerceAtLeast(1)
        var read = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            var entry: ZipEntry? = zin.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/')
                val hit = wanted.firstOrNull { it.equals(name, ignoreCase = true) }
                if (hit != null && !entry.isDirectory) {
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(8 * 1024)
                    while (true) {
                        val n = zin.read(chunk)
                        if (n < 0) break
                        buf.write(chunk, 0, n)
                        read += n
                    }
                    out[hit] = buf.toByteArray()
                } else {
                    // 不需要的条目直接跳过，但字节数仍要计进去 —— 否则进度会提前冲顶
                    read += entry.size.coerceAtLeast(0).toInt()
                }
                onProgress?.invoke((read.toFloat() / total).coerceIn(0f, 1f))
                entry = zin.nextEntry
            }
        }
        return out
    }

    /**
     * routes.dat 的两张表。§1 按「线路块紧接排列」顺序走步（块头 = `[4B 线路ID][1B 名字节数]`），
     * 不是按节点链猜 —— 早先按节点链扫描会把只有 1 个节点的线路（三江线、塘雅线）整条丢掉，
     * 而 §2 的径路是按**线路ID**索引的，少一条线就会连带错一批车。
     * §1 走完后紧跟 `[4B 车次数]` 和 §2，两份声明条数都用来兜住格式漂移：对不上就判解析失败。
     */
    fun parseRouteTable(b: ByteArray, onProgress: ((Float) -> Unit)? = null): RouteTable {
        val start = firstRouteBlock(b) ?: throw OfflineDbException("routes.dat 找不到线路块起点")
        val lines = ArrayList<RouteLine>()
        val declaredLines = be(b, 0, 4)
        // 声明条数就是分母；声明不可信时退回按字节位置估
        val denom = if (declaredLines in 1..20000) declaredLines else 0
        var p = start
        var sec2At = -1
        while (true) {
            val line = readRouteBlock(b, p) ?: break
            lines.add(line.first)
            p = line.second
            if (p + 4 > b.size) break
            if (denom > 0) {
                // §1 走完还要解 §2 径路表，§1 只占这段进度的一半
                onProgress?.invoke((lines.size.toFloat() / denom * 0.5f).coerceIn(0f, 0.5f))
            }
        }
        lines.sortBy { it.id }
        if (lines.isEmpty()) throw OfflineDbException("线路条数异常（0）")
        if (declaredLines in 1..20000 && kotlin.math.abs(declaredLines - lines.size) > 4) {
            throw OfflineDbException("线路条数与声明不符：解出 ${lines.size}，声明 $declaredLines")
        }
        val declaredTrains = be(b, p, 4)
        val trains = LinkedHashMap<String, TrainRoute>()
        if (declaredTrains in 1..60000) {
            val table = parseTrainRoutes(b, p + 4, declaredTrains, lines.associateBy { it.id }) { f ->
                onProgress?.invoke(0.5f + f * 0.5f)
            }
            trains.putAll(table)
            sec2At = p + 4
        }
        onProgress?.invoke(1f)
        return RouteTable(declaredLines, lines, declaredTrains, trains).also {
            if (declaredTrains > 0 && it.trainRoutes.size < declaredTrains / 2) {
                throw OfflineDbException("径路表覆盖率异常：${it.trainRoutes.size}/$declaredTrains @$sec2At")
            }
        }
    }

    /**
     * 首条线路的块起点。§1 的线路ID 实测从 1 起连续递增，所以拿「前若干块必须连号」当判据，
     * 比猜线路名里有没有「线」字稳得多（成渝客专、西成客专都不带）。
     */
    private fun firstRouteBlock(b: ByteArray): Int? {
        var p = 6
        while (p + 14 < b.size) {
            if (contiguousFrom(b, p) >= 2) return p
            p++
        }
        // 只有一条线路的极端包：退回「第一个 ID=1 且能解出块」的位置
        var q = 6
        while (q + 14 < b.size) {
            if (be(b, q, 4) == 1 && readRouteBlock(b, q) != null) return q
            q++
        }
        return null
    }

    /** 从 p 起能连续解出的线路块数，且线路ID 必须是 1,2,3…（列定义段解不出这种链）。 */
    private fun contiguousFrom(b: ByteArray, p: Int): Int {
        if (be(b, p, 4) != 1) return 0
        var cur = p
        var count = 0
        while (count < 8) {
            val line = readRouteBlock(b, cur) ?: break
            if (line.first.id != count + 1) break
            cur = line.second
            count++
        }
        return count
    }

    private fun readRouteBlock(b: ByteArray, p: Int): Pair<RouteLine, Int>? {
        if (p + 10 > b.size) return null
        val id = be(b, p, 4)
        if (id !in 1..20000) return null
        val ln = b[p + 4].toInt() and 0xFF
        if (ln !in 2..90 || p + 5 + ln + 4 > b.size) return null
        val name = utf8(b, p + 5, ln) ?: return null
        if (!name.matches(NAME_OK)) return null
        val cnt = be(b, p + 5 + ln, 4)
        if (cnt !in 1..4000) return null
        var q = p + 9 + ln
        val stops = ArrayList<LineStop>(cnt)
        repeat(cnt) {
            val node = readNode(b, q) ?: return null
            stops.add(node.first)
            q = node.second
        }
        return RouteLine(id, name, stops) to q
    }

    /**
     * §2 每车官方径路。块 = `[0x0C][12B trainNo][1B nl][终到站][4B N]` + N × `[1B nl][站名][4B 线路ID]`，
     * 记录按运行顺序，站名后那个 ID 是**离开该站之后**所走的线路（即换线点），终到站不带 ID。
     * 站名是内联 UTF-8 而不是站 ID，所以这里能装下线路所和联络线点 —— 营业站表根本没有它们。
     */
    fun parseTrainRoutes(
        b: ByteArray,
        from: Int,
        expect: Int,
        linesById: Map<Int, RouteLine>,
        onProgress: ((Float) -> Unit)? = null,
    ): Map<String, TrainRoute> {
        val out = LinkedHashMap<String, TrainRoute>()
        var p = from
        while (out.size < expect && p + 14 < b.size) {
            // 那个字节是**长度前缀**，不是标记：12 位车号读出来正好是 0x0C。
            // 写死 12 会让非 12 位车号（临客、带字母后缀的）从这里开始整体错位。
            val keyLen = b[p].toInt() and 0xFF
            if (keyLen !in 1..40) break
            val key = asciiRun(b, p + 1, keyLen) ?: break
            var q = p + 1 + keyLen
            val nl = b[q].toInt() and 0xFF
            if (nl !in 2..60) break
            val terminus = utf8(b, q + 1, nl) ?: break
            q += 1 + nl
            val cnt = be(b, q, 4)
            if (cnt !in 0..2000) break
            q += 4
            val legs = ArrayList<RouteLeg>(cnt)
            var broken = false
            repeat(cnt) {
                val l2 = b[q].toInt() and 0xFF
                if (l2 !in 1..60) {
                    broken = true
                    return@repeat
                }
                val station = utf8(b, q + 1, l2) ?: run {
                    broken = true
                    return@repeat
                }
                val lineId = be(b, q + 1 + l2, 4)
                if (lineId !in linesById) {
                    broken = true
                    return@repeat
                }
                legs.add(RouteLeg(station, lineId))
                q += 1 + l2 + 4
            }
            if (broken) break
            out[key] = TrainRoute(terminus, legs)
            p = q
            if (expect > 0) onProgress?.invoke((out.size.toFloat() / expect).coerceIn(0f, 1f))
        }
        return out
    }

    /**
     * 节点（p = 站名字节数 nl，q = p+1+nl），照路路通 `RoutesMgr` 的字段序读：
     * `[1B nl][站名][1B tl][略号][4B routeFlag][4B 里程]`，步长 `nl + tl + 10`（略号实测 tl=3 → nl+13）。
     *
     * 早先那版把 `FF FF FF FF` 当哨兵硬校验，其实它是 `RoutesMgr` 里的正式字段 `routeFlag`
     * （营业站读出来是 -1）——按哨兵判会把非 -1 的节点整条线路丢掉，属于「遗漏」而不是「容错」。
     * 现在只校验结构合理性（名字节能解码、里程在量程内），routeFlag 原样带出去。
     */
    private fun readNode(b: ByteArray, p: Int): Pair<LineStop, Int>? {
        val n = b.size
        val nl = b[p].toInt() and 0xFF
        if (nl !in 1..60) return null
        val nameAt = p + 1
        val q = nameAt + nl
        if (q + 2 > n) return null
        val tl = b[q].toInt() and 0xFF
        if (tl > 8 || q + 1 + tl + 8 > n) return null
        val tele = asciiUpper(b, q + 1, tl).orEmpty()
        val name = utf8(b, nameAt, nl) ?: return null
        val flag = be(b, q + 1 + tl, 4)
        val km = be(b, q + 5 + tl, 4)
        if (km !in 0..200000) return null
        return LineStop(tele, name, km, flag) to (q + 1 + tl + 8)
    }

    /**
     * extra.dat 开头的企业名表：`[2B(255 进制) 条数]` + 每条 `[1B 字节长][UTF-8 名]`。
     * **下标就是 jlb 记录里的 [JiaoluRake.enterpriseCode]** —— 路路通的 `getQiYe(i)` 就是 `list[i]`，
     * 所以「代码↔局段名」的配对方式是顺序下标，不是键值对（早先据此断言「建不出局名表」是错的）。
     */
    fun parseEnterprises(b: ByteArray): List<String> {
        val count = be(b, 0, 2)
        if (count !in 1..5000) return emptyList()
        var p = 2
        val out = ArrayList<String>(count)
        while (out.size < count && p + 1 <= b.size) {
            val len = b[p].toInt() and 0xFF
            if (len == 0 || len > 60 || p + 1 + len > b.size) break
            out.add(utf8(b, p + 1, len) ?: break)
            p += 1 + len
        }
        return out
    }

    /** 按企业代码取担当名；越界返回空串，由界面决定不显示这一行。 */
    fun enterpriseName(entries: List<String>, code: Int): String = entries.getOrElse(code) { "" }

    /**
     * jlb.dat 车底记录。字段序取自路路通自己的 `JlbDataMgr`，不是从 hex 猜的：
     *
     * `[4B 车底数]` + 每条 `int32 +0 | int32 +4 | int32 +8 | int32 +12 | int16(255 进制) +16 | 1B 车型长 + 车型 | 1B 组长 + 担当组`
     *
     * 记录长度 = `19 + 车型长 + 1 + 组长`，**走步完全确定**。早先那版要先认 `01 00 53` 锚、
     * 再补「`01 34`/`01 35` 是同一记录的别名前缀」才勉强跑通，根因是没认出这是定长字段头 ——
     * 按锚点只命中对齐的那一形，另一族整条丢掉（G3739 当时整个查不到）。
     * 结构一不合就停在这条、不再往后猜：宁可少解也要能在测试里暴露，而不是静默错位。
     */
    fun parseJlb(b: ByteArray, onProgress: ((Float) -> Unit)? = null): List<JiaoluRake> {
        val expect = be(b, 0, 4)
        val out = ArrayList<JiaoluRake>()
        var p = 4
        var parsed = 0
        while (p + 19 <= b.size && (expect !in 1..MAX_RAKES || parsed < expect)) {
            val row = readJlbRow(b, p) ?: break
            toRake(row.carTypeRaw, row.groupRaw)?.let {
                out.add(
                    it.copy(
                        key0 = row.key0,
                        key1 = row.key1,
                        field8 = row.field8,
                        field12 = row.field12,
                        enterpriseCode = row.enterpriseCode,
                    )
                )
            }
            p = row.next
            parsed++
            if (expect in 1..MAX_RAKES) {
                onProgress?.invoke((parsed.toFloat() / expect).coerceIn(0f, 1f))
            }
        }
        return out
    }

    /** 车底记录走完后的落点，也就是餐车表声明条数所在的位置。 */
    private fun jlbRakeEnd(b: ByteArray): Int {
        val expect = be(b, 0, 4)
        var p = 4
        var parsed = 0
        while (p + 19 <= b.size && (expect !in 1..MAX_RAKES || parsed < expect)) {
            val row = readJlbRow(b, p) ?: break
            p = row.next
            parsed++
        }
        return p
    }

    /**
     * jlb.dat 尾部：`[4B 条数]` + 每条 `int32 车次ID | int32 起 | int32 止 | 1B 长 + 车厢串`。
     * 路路通用 `trainId 命中 && (起 <= i && 止 >= i)` 取某日该车餐车在第几节。
     */
    fun parseFoodCoaches(b: ByteArray): List<FoodCoach> {
        val at = jlbRakeEnd(b)
        if (at + 4 > b.size) return emptyList()
        val count = be(b, at, 4)
        if (count !in 1..200000) return emptyList()
        var p = at + 4
        val out = ArrayList<FoodCoach>(count)
        while (out.size < count && p + 13 <= b.size) {
            val trainId = be(b, p, 4)
            val start = be(b, p + 4, 4)
            val end = be(b, p + 8, 4)
            val len = b[p + 12].toInt() and 0xFF
            if (trainId < 0 || len > 100 || p + 13 + len > b.size) break
            val coach = utf8(b, p + 13, len) ?: break
            out.add(FoodCoach(trainId, start, end, coach))
            p += 13 + len
        }
        return out
    }

    /** 定长头 + 两个长度前缀字符串；解不出来就是真不合结构，返回 null 让上层停下。 */
    private fun readJlbRow(b: ByteArray, p: Int): JlbRow? {
        if (p + 19 > b.size) return null
        val typeLen = b[p + 18].toInt() and 0xFF
        if (typeLen !in 1..200 || p + 19 + typeLen + 1 > b.size) return null
        val carType = utf8(b, p + 19, typeLen) ?: return null
        val groupAt = p + 19 + typeLen
        val groupLen = b[groupAt].toInt() and 0xFF
        if (groupAt + 1 + groupLen > b.size) return null
        val group = if (groupLen == 0) "" else utf8(b, groupAt + 1, groupLen) ?: return null
        return JlbRow(
            key0 = be(b, p, 4),
            key1 = be(b, p + 4, 4),
            field8 = be(b, p + 8, 4),
            field12 = be(b, p + 12, 4),
            enterpriseCode = ((b[p + 16].toInt() and 0xFF) * 255) + (b[p + 17].toInt() and 0xFF),
            carTypeRaw = carType,
            groupRaw = group,
            next = groupAt + 1 + groupLen,
        )
    }

    private data class JlbRow(
        val key0: Int,
        val key1: Int,
        val field8: Int,
        val field12: Int,
        val enterpriseCode: Int,
        val carTypeRaw: String,
        val groupRaw: String,
        val next: Int,
    )

    /** 车型按**最先出现**的逗号（「，」与「,」混用）切车型/备注；组先归一化全角再切段。 */
    private fun toRake(type: String, group: String): JiaoluRake? {
        val comma = type.indexOfFirst { it == '，' || it == ',' }
        val carType = if (comma > 0) type.substring(0, comma) else type
        val note = if (comma > 0) type.substring(comma + 1) else ""
        if (group.isEmpty()) return JiaoluRake(carType, note, emptyList())
        val codes = halfWidth(group).split('#').map { it.trim() }.filter { it.isNotEmpty() }
        if (codes.size < 2) return null
        if (!codes.all { SEGMENT_OK.matches(it) }) return null
        return JiaoluRake(carType, note, codes)
    }

    /** §2 的键是 12 位 trainNo，必须是可见 ASCII；出现别的字节说明已经不在径路表里。 */
    private fun asciiRun(b: ByteArray, at: Int, len: Int): String? {
        if (at < 0 || at + len > b.size) return null
        val sb = StringBuilder(len)
        for (k in 0 until len) {
            val c = b[at + k].toInt() and 0xFF
            if (c !in '0'.code..'z'.code) return null
            sb.append(c.toChar())
        }
        return sb.toString()
    }

    /** 库里个别担当组用全角字母数字（`Ｄ4595/8`），统一转半角后再比对车次。 */
    private fun halfWidth(s: String): String = buildString(s.length) {
        for (c in s) {
            val cp = c.code
            append(
                when {
                    cp in 0xFF10..0xFF19 -> Char(cp - 0xFEE0)
                    cp in 0xFF21..0xFF3A -> Char(cp - 0xFEE0)
                    cp in 0xFF41..0xFF5A -> Char(cp - 0xFEE0)
                    else -> c
                },
            )
        }
    }

    private fun be(b: ByteArray, at: Int, len: Int): Int {
        if (at < 0 || at + len > b.size) return -1
        var v = 0
        for (k in 0 until len) v = (v shl 8) or (b[at + k].toInt() and 0xFF)
        return v
    }

    /** `[2B 大端][...]` 这类定宽头/键，库里反复出现，单独给个名字免得写成 magic number。 */
    private fun be2(b: ByteArray, at: Int): Int = be(b, at, 2)

    /** 同上，4 字节大端。 */
    private fun be4(b: ByteArray, at: Int): Int = be(b, at, 4)

    private fun asciiUpper(b: ByteArray, at: Int, len: Int): String? {
        if (at < 0 || at + len > b.size) return null
        val sb = StringBuilder(len)
        for (k in 0 until len) {
            val c = b[at + k].toInt() and 0xFF
            if (!((c in 'A'.code..'Z'.code) || (c in '0'.code..'9'.code))) return null
            sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun utf8(b: ByteArray, at: Int, len: Int): String? {
        if (at < 0 || len < 0 || at + len > b.size) return null
        return runCatching { String(b, at, len, Charsets.UTF_8) }.getOrNull()
    }
}

class OfflineDbException(message: String) : Exception(message)
