package com.crinfo.app.data.offline

import com.crinfo.app.data.model.TrainStop
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 真实 an.db 回归：只在机器上有下载件时跑（数据集属竞品，不入仓库）。
 * 用 `CRINFO_ANDB=<路径> ./gradlew :app:testDebugUnitTest --tests "*OfflineDbRealFileTest*"` 触发。
 */
class OfflineDbRealFileTest {
    private val path: String? = System.getenv("CRINFO_ANDB")

    private fun entry(name: String): ByteArray? {
        val file = path?.let(::File) ?: return null
        if (!file.isFile) return null
        var hit: ByteArray? = null
        ZipInputStream(file.inputStream()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.name.endsWith(name)) hit = zin.readBytes()
            }
        }
        return hit
    }

    @Test
    fun `parse real an db and match 路路通板面 mileage`() {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过真实库校验", file?.isFile == true)

        val db = OfflineDbParser.parse(file!!.readBytes())
        assertEquals("20261108", db.version)
        assertTrue("线路过少，疑似解析错位：${db.lineCount}", db.lineCount >= 700)
        assertTrue("节点过少：${db.stopCount}", db.stopCount >= 7000)

        val jh = db.lines.first { it.name == "京沪高速线" }
        assertEquals(listOf("VNP", "LJP", "JTP", "00A", "TIP", "CBP"), jh.stops.take(6).map { it.telecode })
        assertEquals(listOf(0, 60, 110, 116, 122, 210), jh.stops.take(6).map { it.km })
        assertEquals("上海虹桥", jh.stops.last().name)
        assertEquals(1318, jh.stops.last().km)

        val hb = db.lines.first { it.name == "合蚌高速线" }
        assertEquals(listOf(0, 132), listOf(hb.stops.first().km, hb.stops.last().km))

        // 库内略号确实就是 12306 电报码
        assertTrue("VNP 未入库", db.stopsByTelecode.containsKey("VNP"))
    }

    /** 交路链：jlb 解出的车底要能给出担当顺序（时刻仍需在线查）。 */
    @Test
    fun `jlb 解出交路链`() {
        val jlb = entry("jlb.dat")
        assumeTrue("未提供 CRINFO_ANDB，跳过", jlb != null)

        val rakes = OfflineDbParser.parseJlb(jlb!!)
        // 文件头声明 3862 条车底；旧实现（只认 01 00 53 锚）只能凑出约一半
        assertTrue("交路表覆盖率过低：仅解出 ${rakes.size} 条", rakes.size >= 3700)
        val g1 = rakes.firstOrNull { "G1" in it.group }
        assertTrue("没找到含 G1 的车底", g1 != null)
        assertEquals("CR400BF-S型重联", g1!!.carType)
        assertEquals("G1955/6/7/8石家庄调向", g1.note)

        // G547 曾被整条漏掉：它的记录类型字节不是 0x53，写死一种只能解出 104 条
        val g547 = rakes.firstOrNull { "G547" in it.group }
        assertTrue("没找到含 G547 的车底", g547 != null)
        assertEquals("CR400AF-BZ", g547!!.carType)
        assertEquals(listOf("G547", "G742", "G49", "G38", "G51", "G58"), g547.group)
    }

    /**
     * G3739 是这条链的终到程（`G3740#C8932#C8931#G3739`），旧实现整个查不到：
     * 它的记录是 `01 35` + 属性 `01 68` 那种形态，锚 `01 00 53` 对不上。
     */
    @Test
    fun `jlb 解出 G3739 担当组`() {
        val jlb = entry("jlb.dat")
        assumeTrue("未提供 CRINFO_ANDB，跳过", jlb != null)

        val rake = OfflineDbParser.parseJlb(jlb!!).firstOrNull { "G3739" in it.group }
        assertTrue("没找到含 G3739 的车底", rake != null)
        assertEquals("CR400AF-S", rake!!.carType)
        assertEquals(listOf("G3740", "C8932", "C8931", "G3739"), rake.group)
    }

    /** 全角担当组（`Ｄ4595/8`）必须转成半角，否则按车次查链会查空。 */
    @Test
    fun `jlb 全角车次已归一化`() {
        val jlb = entry("jlb.dat")
        assumeTrue("未提供 CRINFO_ANDB，跳过", jlb != null)

        val groups = OfflineDbParser.parseJlb(jlb!!).flatMap { it.group }
        assertTrue("仍有全角字符残留", groups.none { it.any { ch -> ch.code in 0xFF01..0xFF5E } })
    }

    /** §1 线路要带线路ID 且含单站线路；§2 是竞品的每车官方径路，G3739 的换线点必须能 join 上线路名。 */
    @Test
    fun `routes 解出每车官方径路`() {
        val routes = entry("routes.dat")
        assumeTrue("未提供 CRINFO_ANDB，跳过", routes != null)

        val table = OfflineDbParser.parseRouteTable(routes!!)
        assertTrue("线路过少：${table.lines.size}", table.lines.size >= 790)
        assertTrue("径路表过少：${table.trainRoutes.size}", table.trainRoutes.size >= 14000)
        assertTrue("单站线路被丢弃", table.lines.any { it.stops.size == 1 })

        val byId = table.lines.associateBy { it.id }
        val g3739 = table.trainRoutes["4f000G373907"]
        assertTrue("没有 G3739 的官方径路", g3739 != null)
        assertEquals("南宁东", g3739!!.terminus)
        assertEquals(
            listOf("西成客专线", "成渝高速线", "成渝渝贵线", "渝贵线", "长东联络线", "贵广客专线", "桂西联络线", "衡柳线", "柳南客专线"),
            g3739.legs.map { byId.getValue(it.lineId).name },
        )
    }

    /**
     * G3739（西安北→南宁东，跨 9 条线）走官方径路：这是旧实现「经由整行不出现」的那类车 ——
     * 停靠站虽然全在图里，但 4 对相邻站回溯不到起点，图搜索一票否决。
     */
    @Test
    fun `官方径路生成 G3739 的经由与里程`() {
        val routes = entry("routes.dat")
        assumeTrue("未提供 CRINFO_ANDB，跳过", routes != null)
        val table = OfflineDbParser.parseRouteTable(routes!!)
        val route = table.trainRoutes["4f000G373907"]!!
        // 停站取自板面（西安北→南宁东），跨源一律按站名对齐
        val stops = listOf("西安北", "广元", "绵阳", "成都东", "重庆西", "贵阳东", "桂林西", "柳州", "南宁东")
            .mapIndexed { i, n ->
                TrainStop(
                    name = n, telecode = "T%02d".format(i), order = i,
                    arriveTime = if (i == 0) null else "12:00", departTime = "12:05",
                    dwellMinutes = 0, minutesFromStart = 0, dayDiff = 0, lat = null, lon = null,
                    wicket = null, waitingRoom = null, exit = null, stationBureauCode = "",
                    stationCorporation = "", ticketDelay = null,
                )
            }
        val out = OfflineJourney.build(route, table.lines.associateBy { it.id }, stops)
        assertTrue("经由为空", out != null && out.nodes.isNotEmpty())
        assertEquals("西安北", out!!.nodes.first().name)
        assertEquals("南宁东", out.nodes.last().name)
        // 线路所（半边山所）不在 12306 站表里，只能从库里的径路拿到
        assertTrue("缺线路所节点", out.nodes.any { it.name == "半边山所" })
        assertEquals(
            "换线点线路名应取被换入的那条线",
            "成渝高速线",
            out.nodes.first { it.name == "成都东" }.line,
        )
        assertEquals("同名同里程只留一行", 1, out.nodes.count { it.name == "成都东" })
        // 板面停站全部要拿到里程，否则界面会继续打「获取中…」
        assertEquals(stops.size, out.mileages.size)
        assertEquals(2099, out.mileages.values.maxOf { it.km })
        val seq = stops.mapNotNull { out.mileages[it.telecode]?.km }
        assertTrue("累计里程必须单调：$seq", seq.sorted() == seq)
    }

    /**
     * G1958（太原南→上海虹桥）跨 12 条线，其中 6 段是**逆着线路里程轴**走的
     * （石太客专线 232→0、合宁联络线 10→0、宁蓉线 135→0、沪宁高速 50→29、虹安线 29→0）。
     * 这类车曾整趟显示「累计 0 公里」：负跨度被当成解析失败，还把后续所有站一起拉黑了。
     */
    @Test
    fun `官方径路复现 G1958 板面里程`() {
        val routes = entry("routes.dat")
        assumeTrue("未提供 CRINFO_ANDB，跳过", routes != null)
        val table = OfflineDbParser.parseRouteTable(routes!!)
        val route = table.trainRoutes["27000G19580E"]!!
        val names = listOf(
            "太原南", "阳泉北", "石家庄", "高邑西", "邢台东", "邯郸东", "安阳东", "新乡东", "郑州东",
            "阜阳西", "寿县", "合肥", "南京南", "镇江南", "常州北", "无锡东", "苏州北", "上海虹桥",
        )
        val stops = names.mapIndexed { i, n ->
            TrainStop(
                name = n, telecode = "T%02d".format(i), order = i,
                arriveTime = if (i == 0) null else "12:00", departTime = "12:05",
                dwellMinutes = 0, minutesFromStart = 0, dayDiff = 0, lat = null, lon = null,
                wicket = null, waitingRoom = null, exit = null, stationBureauCode = "",
                stationCorporation = "", ticketDelay = null,
            )
        }
        val out = OfflineJourney.build(route, table.lines.associateBy { it.id }, stops)
        assertTrue("经由为空", out != null && out.nodes.isNotEmpty())
        // 每个板面停站都要拿到里程，界面才不会再打「获取中…」、经由才不会是「—」
        assertEquals("停站里程应全覆盖", stops.size, out!!.mileages.size)
        val seq = stops.map { out.mileages.getValue(it.telecode).km }
        assertEquals(
            listOf(0, 129, 232, 283, 354, 407, 467, 577, 644, 960, 1073, 1180, 1334, 1399, 1464, 1521, 1548, 1629),
            seq,
        )
        assertEquals("石太客专线", out.nodes.first { it.name == "阳泉北" }.line)
    }

    /** G1 停靠站序列取自路路通板面，累计里程必须逐站复现。 */
    @Test
    fun `径路复现 G1 板面里程`() {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())
        val graph = RouteGraph(db.lines)
        val stops = listOf("VNP", "TIP", "CBP", "JGK", "QAK", "NKH", "ESH", "WGH", "KNH", "AOH")
        val route = graph.route(stops)
        assertTrue("径路不可达", route != null)
        assertEquals(listOf(0, 122, 210, 406, 535, 1023, 1153, 1210, 1268, 1318), route!!.map { it.km })
        // 始发站还没走过任何线路，经由从第二站起才有
        assertEquals(listOf("京沪高速线"), route.first { it.km == 122 }.lines)
        assertTrue("中途不应换线", route.drop(1).all { it.lines == listOf("京沪高速线") })
    }

    /** 廊坊→天津南：换线只能省到 96km，压换线的代价应当给出板面的 210km。 */
    @Test
    fun `径路不换线抄近路`() {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())
        val route = RouteGraph(db.lines).route(listOf("VNP", "LJP", "TIP"))
        assertEquals(listOf(0, 60, 122), route!!.map { it.km })
    }

    /** 经由完整节点：G1 只停 7 站，但板面要显示线路所等非停站节点。 */
    @Test
    fun `经由展开含线路所节点`() {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())
        val graph = RouteGraph(db.lines)
        val stops = listOf("VNP", "TIP", "CBP", "JGK", "QAK", "NKH", "AOH")
        val segments = graph.fullRoute(stops)
        assertTrue("径路节点不可达", segments != null)
        // 北京南→天津南之间应有京津所 JTP 与津沪所 00A
        val first = segments!!.first().map { it.telecode }
        assertEquals(listOf("VNP", "LJP", "JTP", "00A", "TIP"), first)
        assertEquals(listOf(0, 60, 110, 116, 122), segments.first().map { it.km })
        // 曲阜东→南京南之间要穿过滕州东/枣庄/徐州东/宿州东/蚌埠南/定远/滁州
        val qakToNkh = segments[stops.indexOf("QAK")]
        assertEquals(
            listOf("QAK", "TEK", "ZEK", "UUH", "SRH", "BMH", "EWH", "CXH", "NKH"),
            qakToNkh.map { it.telecode },
        )
        // 段内除首节点外都带进站线路（首节点是上一段的终点，本身没有"到达线路"）
        assertTrue(
            "节点应带线路名",
            segments.all { seg -> seg.drop(1).all { it.line.isNotBlank() } },
        )
    }

    /** 定长走步：车底表要走到餐车表头，餐车表要能按声明条数读满 —— 这是对账，不是抽样。 */
    @Test
    fun `jlb 定长走步与餐车表对账`() {
        val jlb = entry("jlb.dat")
        assumeTrue("未提供 CRINFO_ANDB，跳过", jlb != null)
        val bytes = jlb!!

        val declared = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        assertEquals("库内声明 3862 条车底", 3862, declared)

        val rakes = OfflineDbParser.parseJlb(bytes)
        // 结构合法但车次串非法的（厂商测试记录）会被内容过滤掉，条数略少于声明是预期的
        assertTrue("车底记录过少：${rakes.size}/$declared", rakes.size >= 3850)

        val food = OfflineDbParser.parseFoodCoaches(bytes)
        assertTrue("餐车表读不满，说明车底走步没落在表头：${food.size}", food.size >= 13000)
        assertTrue("餐车串为空", food.first().coach.isNotBlank())
        assertTrue("车次 ID 越界", food.all { it.trainId in 0 until 20000 })

        // +16 那个字段被路路通喂给 ExtraData.getQiYe，不该是常量
        val codes = rakes.map { it.enterpriseCode }.distinct()
        assertTrue("企业代码异常单一：$codes", codes.size >= 5)
    }

    /**
     * 数据层校验钉桩：`routeFlag` 全库 7366 个节点只有一个取值 -1，
     * 所以它不能用来区分营业站/线路所 —— 这条测试就是防止以后有人拿它写筛选。
     * 企业代码必须是 extra 表的合法下标（担当局段就靠它离线化）。
     */
    @Test
    fun `routeFlag 无区分力且企业代码可映射到局段名`() {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())

        val flags = db.lines.flatMap { it.stops }.map { it.routeFlag }.distinct()
        assertEquals("routeFlag 出现多值，需重新验它的语义：$flags", listOf(-1), flags)
        assertTrue("节点太少：${db.stopCount}", db.stopCount >= 7000)

        assertTrue("企业名表过少：${db.enterprises.size}", db.enterprises.size >= 300)
        assertTrue("企业名里既没局也没段", db.enterprises.any { "局" in it } && db.enterprises.any { "段" in it })
        val bad = db.rakes.filter { it.enterpriseCode !in db.enterprises.indices }
        assertTrue("有 ${bad.size} 条车底的企业代码越界：${bad.take(3).map { it.carType }}", bad.isEmpty())
        val g1 = db.rakes.firstOrNull { "G1" in it.group }
        assertTrue("没找到 G1 的车底", g1 != null)
        val qiye = db.enterprises[g1!!.enterpriseCode]
        assertTrue("G1 担当名为空", qiye.isNotBlank())
        assertTrue("担当企业至少该有 20 种，实测 $((db.rakes.map { it.enterpriseCode }.distinct().size))",
            db.rakes.map { it.enterpriseCode }.distinct().size >= 20)
    }

    /**
     * 引擎端到端：真实分片 → 时刻表 → 板面/直达。
     * G1 的 06:30/11:24、全程 1318km 与 12306 余票锚点同源；1461 在 11-24 之后停运，
     * 用它验日期过滤真的会改变板面内容。
     */
    @Test
    fun `引擎端到端复现时刻表与日期过滤`() = kotlinx.coroutines.runBlocking {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())
        val engine = realEngine(db)

        val g1 = engine.train("G1", "2026-11-02")
        assertTrue("引擎没解出 G1", g1 != null)
        assertTrue("G1 当天应判定开行，实际 ${g1!!.status}", g1.status.isRunning)
        assertEquals(7, g1.detail.stopCount)
        assertEquals("北京南", g1.detail.fromName)
        assertEquals("上海虹桥", g1.detail.toName)
        assertEquals("06:30", g1.detail.stops.first().departTime)
        assertEquals("11:24", g1.detail.stops.last().arriveTime)
        assertEquals(1318, g1.detail.totalKm)
        assertEquals(
            listOf("北京南", "沧州西", "德州东", "曲阜东", "南京南", "苏州北", "上海虹桥"),
            g1.detail.stops.map { it.name },
        )
        assertTrue("担当名应来自 extra 表：${g1.corporation}", g1.corporation.contains("局"))

        val direct = engine.trips("北京南", "上海虹桥", "2026-11-02")
        val g1Trip = direct.firstOrNull { it.trainCode.startsWith("G1") && it.trainCode != "G10" }
        assertTrue("直达列表里没有 G1", direct.any { it.trainCode == "G1" })
        val t = direct.first { it.trainCode == "G1" }
        assertEquals("06:30", t.departTime)
        assertEquals("11:24", t.arriveTime)
        assertEquals("4小时54分", t.durationText)
        assertEquals(1318, t.distanceKm)
        // 离线态席别来自 xw 位图：G1 是 48（一等+二等），有清单但**没有票量与票价**
        assertEquals("G1 离线席别种类", listOf(com.crinfo.app.data.model.Seat.FIRST, com.crinfo.app.data.model.Seat.SECOND), t.seats.map { it.seat })
        assertTrue(
            "离线席别一律按无票样式但不谎称售罄：${t.seats.map { it.status }}",
            t.seats.all { it.status == com.crinfo.app.data.model.SeatStatus.Unknown && it.price == null } &&
                !t.ticketKnown && !t.soldOut && t.lowestAvailablePrice == null,
        )
        assertTrue("直达数量不该少于板面级别：${direct.size}", direct.size > 20)
        assertTrue(g1Trip != null)

        val plan = engine.train("1461", "2026-11-02")!!.detail.runningDays
        assertTrue("离线态也该给出开行日历，不能整块消失", plan != null && plan.days.isNotEmpty())
        // 断言不依赖「今天是几号」：日历里每一天都应当严格等于「是否落在图定期限内」
        plan!!.days.forEach { (day, on) ->
            val v = day.toInt()
            val expect = v in 20260701..20261124
            assertEquals("$day 的开行判定与图定期限不符", expect, on)
        }
        assertTrue("日历窗口应不少于 90 天", plan.days.size >= 90)

        val board = engine.board("北京南", "2026-11-02")
        assertTrue("板面里没有 G1", board.any { it.trainCode == "G1" })
        assertTrue("板面时刻不该为空", board.all { it.time.matches(Regex("^\\d{2}:\\d{2}$")) })

        // 1461 图定只到 2026-11-24：过截止日之后板面里就不该再有它
        val before = engine.board("北京", "2026-11-02")
        val after = engine.board("北京", "2026-11-25")
        assertTrue("11-02 北京板面应有 1461", before.any { it.trainCode == "1461" })
        assertTrue("11-25 已过 1461 截止日，不该再出现", after.none { it.trainCode == "1461" })
        assertEquals(RunningStatus.Expired, engine.train("1461", "2026-11-25")!!.status)
    }

    /**
     * 模糊查询的同城扩站：北京南→上海虹桥放开成两侧主客站清单后，
     * G1 必须还在、fromName 落实际停靠站，条数只多不少（更多京沪走廊车进来看）。
     */
    @Test
    fun `tripsBetween 同城扩站`() = kotlinx.coroutines.runBlocking {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())
        val engine = realEngine(db)

        val froms = listOf("北京南", "北京", "北京西", "北京丰台", "北京朝阳")
        val tos = listOf("上海虹桥", "上海", "上海南", "上海西")
        val exact = engine.trips("北京南", "上海虹桥", "2026-11-02")
        val wide = engine.tripsBetween(froms, tos, "2026-11-02")

        assertTrue("精确查就有 G1（前提核对）", exact.any { it.trainCode == "G1" })
        assertTrue("扩站后 G1 还在", wide.any { it.trainCode == "G1" })
        assertTrue("扩站只会更多车次：wide=${wide.size} exact=${exact.size}", wide.size >= exact.size)
        assertTrue("乘车段出发站必须落在所选城市清单内", wide.all { it.fromName in froms })
        assertTrue("乘车段到达站必须落在所选城市清单内", wide.all { it.toName in tos })
    }

    /**
     * 换号车（北京丰台换号的 K1276/K1277、南宁东的 G3741/G3744）：t.i 与详情 trainCode 都是合并整串，
     * 而 §2 只存一条**全程**径路、键只嵌一段号，jlb 组写简写 —— 整串必须拆段逐段尝试，
     * 否则经由与车底交路双双消失（用户报缺数据的根因，真库探针 2026-11-02 钉死）。
     */
    @Test
    fun `换号车整串锚定径路与交路`() {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())

        val k = db.findTrainRouteByDisplayCode("K1276/K1277", "温州", "包头")
        assertTrue("K1276/K1277 锚不到全程径路", k != null)
        assertEquals("包头", k!!.terminus)

        val g = db.findTrainRouteByDisplayCode("G3741/G3744", "南宁东", "福州南")
        assertTrue("G3741/G3744 锚不到全程径路", g != null)
        assertEquals("福州南", g!!.terminus)

        // 边界（故意的，别「优化」成反查）：单段 K1277 锚不到 —— §2 没有它的键；
        // 若拿 K1276 的全程径路去喂联网 K1277 的半程停站（北京丰台→包头），build 会画出含温州段节点的错误经由。
        // 半程场景交给图搜索兜底（按真实乘车段算），宁可经由简略不可错画。
        assertNull(db.findTrainRouteByDisplayCode("K1277", "北京丰台", "包头"))

        // 交路：jlb 组是 `K1276/7` 简写，整串与任一段都要能定位同一车底
        val whole = db.rakes.firstOrNull { r -> r.group.any { "K1276" in it } }
        assumeTrue("库里 K1276 组形态变了，重验这条", whole != null)
        val at = locateJiaolu(db.rakes, "K1276/K1277")
        assertTrue("整串定位不到车底交路", at != null)
        assertEquals(1, at!!.segmentIndex)
    }

    /** 覆盖更多中间节点：把整条 京沪高速线 当停靠序列，累计里程应与线路内里程一致。 */
    @Test
    fun `径路覆盖全线路节点`() {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())
        val line = db.lines.first { it.name == "京沪高速线" }
        val route = RouteGraph(db.lines).route(line.stops.map { it.telecode })
        assertTrue("全线节点应可达", route != null)
        assertEquals(line.stops.map { it.km }, route!!.map { it.km })
    }

    /* ---------------- 时刻表与板面分片 ---------------- */

    /** 分片条目在库里是大写（T13.dat），按大小写不敏感取。 */
    private fun shard(name: String): ByteArray? {
        val file = path?.let(::File) ?: return null
        if (!file.isFile) return null
        var hit: ByteArray? = null
        ZipFile(file).use { zip ->
            val e = zip.entries().toList().firstOrNull {
                it.name.substringAfterLast('/').equals(name, ignoreCase = true)
            } ?: return@use
            hit = zip.getInputStream(e).use { it.readBytes() }
        }
        return hit
    }

    /** 用真实分片搭一个查询引擎（片按 LRU 手搓三份缓存，走步与仓储一致）。 */
    private suspend fun realEngine(
        db: OfflineDb,
        stationOf: (String) -> com.crinfo.app.data.Station? = { null },
    ): OfflineQueryEngine {
        val trainReaders = HashMap<Int, Pair<OfflineShardReader, ShardIndex>>()
        val boardReaders = HashMap<Int, Pair<OfflineShardReader, ShardIndex>>()
        suspend fun loadTrain(id: Int): OfflineTimetable? {
            val s = (id + 1) % 20
            val (reader, index) = trainReaders.getOrPut(s) {
                val r = OfflineShardReader(shard("T$s.dat")!!)
                r to r.trainIndex()
            }
            val at = index.indexOf(id)
            return if (at < 0) null else reader.readTrain(index.offsets[at])
        }
        suspend fun loadCandidates(stationId: Int): IntArray {
            val s = (stationId + 1) % 10
            val (reader, index) = boardReaders.getOrPut(s) {
                val r = OfflineShardReader(shard("S$s.dat")!!)
                r to r.stationIndex()
            }
            val at = index.indexOf(stationId)
            return if (at < 0) IntArray(0) else reader.readStationBoard(index.offsets[at])
        }
        return OfflineQueryEngine(
            db = db,
            readTrain = { id -> loadTrain(id) },
            readCandidates = { id -> loadCandidates(id) },
            stationOf = stationOf,
        )
    }

    /** 读某趟车的真实 T 记录（分片路由与仓储一致）。 */
    private fun readRealRecord(db: OfflineDb, trainId: Int): OfflineTimetable {
        val reader = OfflineShardReader(shard("T${(trainId + 1) % 20}.dat")!!)
        val index = reader.trainIndex()
        val at = index.indexOf(trainId)
        assertTrue("车次 $trainId 不在分片索引里", at >= 0)
        return reader.readTrain(index.offsets[at])!!
    }

    /** 项目站表（站名 → 真电报码），JVM 里直读 assets 文件。 */
    private fun stationTableByName(): Map<String, com.crinfo.app.data.Station> {
        val p = listOf("src/main/assets/stations.json", "app/src/main/assets/stations.json")
            .firstOrNull { File(it).exists() } ?: return emptyMap()
        val list = kotlinx.serialization.json.Json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(com.crinfo.app.data.Station.serializer()),
            File(p).readText(Charsets.UTF_8),
        )
        return list.associateBy { it.name }
    }

    /**
     * 里程兜底只在首末站与线上一致时放行：G1 的 T 记录末站累计里程就是板面锚点 1318，
     * 逐站值按线上电报码落地成表；改过区段（首末站不符）的车必须整体拒补 —— 错里程比没里程伤害大。
     */
    @Test
    fun `离线兜底里程复现 G1 板面锚点`() = kotlinx.coroutines.runBlocking {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())
        val byName = stationTableByName()
        assumeTrue("读不到 stations.json，跳过", byName.isNotEmpty())

        val engine = realEngine(db) { name -> byName[name] }
        val tt = engine.train("G1", "2026-11-02")!!.detail.timetable
        val record = readRealRecord(db, db.trainIdOf("G1")!!)

        assertEquals("末站累计里程即板面全程", 1318, OfflineFallback.totalKm(db, record, tt))
        val km = OfflineFallback.stopKm(db, record, tt)
        assertEquals("G1 七个停靠站都该给到里程", 7, km.size)
        assertEquals(0, km["VNP"])
        assertEquals(1318, km["AOH"])
        val seq = tt.stops.mapNotNull { km[it.telecode] }
        assertTrue("累计里程必须单调：$seq", seq.sorted() == seq)

        // 终到站不符（改区段）：整体拒补，宁可维持空值
        val changed = tt.copy(stops = tt.stops.dropLast(1) + tt.stops.last().copy(name = "上海"))
        assertEquals(0, OfflineFallback.totalKm(db, record, changed))
        assertTrue(OfflineFallback.stopKm(db, record, changed).isEmpty())
        // 缺库/缺记录：同样给 0 而不是猜
        assertEquals(0, OfflineFallback.totalKm(null, record, tt))
        assertEquals(0, OfflineFallback.totalKm(db, null, tt))
    }

    /**
     * 离线态没有 12 位 trainNo，官方径路必须按显示车次号锚定（D195 曾被报「经由整块消失」：
     * trainNo 空串查径路表必 miss，图搜索又接不上它的站）。真库钉住锚定规则与两类真实歧义。
     */
    @Test
    fun `按显示车次号锚定官方径路`() {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())

        // D195（上海南→南宁）：库里同时有 D1950/D1952/D1953 等长号，锚定只留真键
        val d195 = db.findTrainRouteByDisplayCode("D195", "上海南", "南宁")
        assertTrue("没锚到 D195 的官方径路", d195 != null)
        assertEquals("南宁", d195!!.terminus)

        // 「1461」同时是 G1461（济南西→南宁东）键的子串，靠终到站名消歧
        val p1461 = db.findTrainRouteByDisplayCode("1461", "北京", "上海")
        assertTrue("没锚到 1461", p1461 != null)
        assertEquals("上海", p1461!!.terminus)

        // 短号键里车次号后面还压着后缀（如 …G10L），锚定要容下这种形态
        val g1 = db.findTrainRouteByDisplayCode("G1", "北京南", "上海虹桥")
        assertTrue("没锚到 G1", g1 != null)
        assertEquals("上海虹桥", g1!!.terminus)

        assertTrue("不存在的车次不该命中", db.findTrainRouteByDisplayCode("D9999", "甲", "乙") == null)
        assertTrue("多候选且站名对不上时必须放弃", db.findTrainRouteByDisplayCode("1461", "不存在", "不存在") == null)
    }

    /** 用户报的完整链路：离线引擎时刻表 → 按车次号锚径路 → 经由非空且首末站正确。 */
    @Test
    fun `离线态 D195 经由端到端`() = kotlinx.coroutines.runBlocking {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())
        val engine = realEngine(db)

        val out = engine.train("D195", "2026-11-02")
        assertTrue("引擎没解出 D195", out != null)
        assertTrue("D195 当天应判定开行，实际 ${out!!.status}", out.status.isRunning)
        val stops = out.detail.stops
        assertEquals("上海南", stops.first().name)
        assertEquals("南宁", stops.last().name)

        val route = db.findTrainRouteByDisplayCode("D195", stops.first().name, stops.last().name)
        assertTrue("径路锚不到", route != null)
        val journey = OfflineJourney.build(route!!, db.linesById, stops)
        assertTrue("经由为空", journey != null && journey.nodes.isNotEmpty())
        assertEquals("上海南", journey!!.nodes.first().name)
        assertEquals("南宁", journey.nodes.last().name)
    }

    /** t.i / s.i 就是 readShort + readUTF：条数要走到声明值，序号即 ID。 */
    @Test
    fun `主键表条数与库内 ID 对齐`() {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())

        assertEquals("车次表应解满 14889 条", 14889, db.trainCount)
        assertEquals("车站表应解满 3331 条", 3331, db.stationCount)
        assertEquals(6012, db.trainIdByCode["G1"])
        assertEquals(0, db.trainIdByCode["1461"])
        assertEquals(171, db.stationIdByName["北京南"])
        assertEquals(2053, db.stationIdByName["上海虹桥"])
        // 库里同时存在「北京」和「北京南」，站名是精确键，不能做前缀匹配
        assertTrue("北京应独立成键", db.stationIdByName.containsKey("北京"))
    }

    /** 离线日期判定的主键表：t.rule 第 0 行必须是 (1,1)，否则 13774 条 ruleIndex=0 的车会被误判。 */
    @Test
    fun `t_rule 第 0 行是天天开`() {
        val rules = shard("t.rule")
        assumeTrue("未提供 CRINFO_ANDB，跳过", rules != null)
        val table = RunningRuleTable.parse(String(rules!!, Charsets.UTF_8))
        assertTrue("规则表过少：${table.size}", table.size >= 100)
        assertEquals(RunningRule(1, 1), table.first())
    }

    /**
     * 联网担当局段失败时的库兜底：jlb 企业代码 → extra 名表要能给出非空段名；
     * 库里没收录的号给空串（由界面维持原空值，绝不猜一个局糊上去）。
     */
    @Test
    fun `离线担当名兜底`() {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())

        val g1 = OfflineFallback.corporation(db, "G1")
        assertTrue("G1 担当名应来自 extra 表：$g1", "局" in g1 || "段" in g1)
        assertTrue("D195 担当名应非空", OfflineFallback.corporation(db, "D195").isNotBlank())
        assertTrue("库里没有的号必须给空", OfflineFallback.corporation(db, "G99999").isEmpty())
        assertTrue("缺库必须给空", OfflineFallback.corporation(null, "G1").isEmpty())
    }

    /** 完整遍历：20 片都要走到文件末尾（残留 0 字节），合计记录数要等于 t.i 的 14889。 */
    @Test
    fun `时刻表分片完整遍历不留残字节`() {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val declared = OfflineDbParser.parse(file!!.readBytes()).trainCount

        var total = 0
        val ids = HashSet<Int>()
        for (shard in 0 until 20) {
            val bytes = shard("T$shard.dat")
            assertTrue("缺分片 T$shard.dat", bytes != null)
            val reader = OfflineShardReader(bytes!!)
            val index = reader.trainIndex()
            assertEquals("T$shard 有残字节：走到 ${index.consumed}/${bytes.size}", bytes.size, index.consumed)
            // 片内声明的分片号 = 本片所载 ID 的余数，与文件名差一格
            assertEquals("T$shard 片内编号错位", (shard + 19) % 20, reader.declaredShardId)
            index.ids.forEach { assertTrue("ID 重复：$it", ids.add(it)) }
            total += index.offsets.size
        }
        assertEquals("分片记录总数应等于 t.i 声明的车次数", declared, total)
        assertEquals("覆盖率必须 100%", declared, ids.size)
    }

    /** G1 的时刻与逐站里程取自真实 12306 板面锚点：06:30 始发、11:24 终到、曲阜东 535、南京南 1023。 */
    @Test
    fun `t 片解出 G1 时刻表与逐站里程`() {
        val bytes = shard("T13.dat")   // (6012 + 1) % 20 = 13
        assumeTrue("未提供 CRINFO_ANDB，跳过", bytes != null)
        val reader = OfflineShardReader(bytes!!)
        val index = reader.trainIndex()
        val g1 = reader.readTrain(index.offsets[index.indexOf(6012)])
        assertTrue("没解出 G1", g1 != null)

        assertEquals(20260127, g1!!.startDate)
        assertEquals(7, g1.stops.size)
        assertEquals(171, g1.stops.first().stationId)      // 北京南
        assertEquals(listOf(6, 30), listOf(g1.stops.first().arriveHour, g1.stops.first().arriveMinute))
        assertEquals(0, g1.stops.first().dwellMinutes)     // 始发站没有停站分
        assertEquals(0, g1.stops.first().distanceKm)
        assertEquals(2053, g1.stops.last().stationId)      // 上海虹桥
        assertEquals(listOf(11, 24), listOf(g1.stops.last().arriveHour, g1.stops.last().arriveMinute))
        assertEquals(listOf(0, 210, 314, 535, 1023, 1237, 1318), g1.stops.map { it.distanceKm })
        // 里程是 255 进制：写成 256 会把上海虹桥变成 1323，与板面差 5 公里
        assertEquals(1318, g1.stops.last().distanceKm)
        val seq = g1.stops.map { it.distanceKm }
        assertTrue("累计里程必须单调：$seq", seq.sorted() == seq)
    }

    /** 1461 的有效期到 20261124 —— 这就是「切日期车次数量不同」的来源，判据要能断言。 */
    @Test
    fun `车次有效期与周期规则决定当天开不开`() {
        val bytes = shard("T1.dat")    // (0 + 1) % 20 = 1
        assumeTrue("未提供 CRINFO_ANDB，跳过", bytes != null)
        val reader = OfflineShardReader(bytes!!)
        val index = reader.trainIndex()
        val train = reader.readTrain(index.offsets[index.indexOf(0)])
        assertTrue("没解出 1461", train != null)
        assertEquals(20260701, train!!.startDate)
        assertEquals(20261124, train.endDate)

        assertEquals(RunningStatus.Running, RunningCheck.status(20261102, train.startDate, train.endDate, RunningRule(1, 1)))
        assertEquals(RunningStatus.Expired, RunningCheck.status(20261125, train.startDate, train.endDate, RunningRule(1, 1)))
        assertEquals(RunningStatus.NotStarted, RunningCheck.status(20260630, train.startDate, train.endDate, RunningRule(1, 1)))
        // 周期 7、只点亮周一/周二：距始发日 0 天开、1 天开、2 天不开
        val weekendOnly = RunningRule(7, 0b11)
        assertEquals(RunningStatus.Running, RunningCheck.status(20261102, 20261102, 20261231, weekendOnly))
        assertEquals(RunningStatus.OutOfRule, RunningCheck.status(20261104, 20261102, 20261231, weekendOnly))
    }

    /**
     * s 片给的是「在这个站能坐到哪些车」的**候选**：带方位后缀的站与时刻表反转真值精确相等，
     * 而「北京」这类城市主站是**同城合集**（1389 ≫ 真正停靠的 263）。
     * 这条测试锁死这个差异，防止以后有人直接拿 s 片当板面显示时刻。
     */
    @Test
    fun `s 片车站候选含同城合集`() {
        val vnp = shard("S2.dat")      // (171 + 1) % 10 = 2
        val bj = shard("S6.dat")       // (165 + 1) % 10 = 6
        assumeTrue("未提供 CRINFO_ANDB，跳过", vnp != null && bj != null)

        val vnpReader = OfflineShardReader(vnp!!)
        val vnpIndex = vnpReader.stationIndex()
        assertEquals("S2 有残字节", vnp!!.size, vnpIndex.consumed)
        val board = vnpReader.readStationBoard(vnpIndex.offsets[vnpIndex.indexOf(171)])
        assertEquals("北京南候选数", 553, board.size)
        assertTrue("候选里应有 G1", 6012 in board)

        val bjReader = OfflineShardReader(bj!!)
        val bjIndex = bjReader.stationIndex()
        val city = bjReader.readStationBoard(bjIndex.offsets[bjIndex.indexOf(165)])
        assertEquals("北京站候选数（同城合集）", 1389, city.size)
        // G4030 终到北京西，并不停靠北京站：s 片仍会把它列为候选
        assertTrue("候选里混有同城其他站的车（故不能直接当停靠表用）", 8105 in city)
    }

    /* ==================== 本轮补齐的其余各表（数字全部来自 20261108 真库对账） ==================== */

    /**
     * sp.dat 表2 是「下标即车次 ID → 12306 的 12 位 trainNo」。它把 §2 官方径路从
     * 「按显示车次号猜后缀」升级成精确查表，所以这条测试同时是两份表的联验：
     * §2 的每一个键都必须能反查到车次 ID。
     */
    @Test
    fun `sp 表 trainNo 与 routes §2 键完全对齐`() {
        val bytes = shard("sp.dat")
        assumeTrue("未提供 CRINFO_ANDB，跳过", bytes != null)
        val sp = SpData.parse(bytes!!)
        assertEquals("sp.dat 有残字节：走了 ${bytes.size - sp.residual}/${bytes.size}", 0, sp.residual)
        assertEquals("车次 ID → trainNo 的条数要等于 t.i", 14889, sp.trainNos.size)
        assertTrue("trainNo 长度只有 12 这一种", sp.trainNos.all { it.length == 12 })
        assertEquals("1461 的下标是 0", "240000146100", sp.trainNos[0])
        assertEquals("担当表条数", 12, sp.duties.size)
        assertEquals("省名条数", 33, sp.provinceNames.size)
        // 表4 声明 3329 条，但站 ID 有 11 个重复，putIfAbsent 去重后留 3318；residual=0 才是走步完整的证据
        assertEquals("车站归属去重后条数", 3318, sp.belongs.size)
        assertEquals("站→地址条数", 3312, sp.locationById.size)
        assertEquals("站名→地址补充条数", 677, sp.locationByName.size)

        val db = OfflineDbParser.parse(path!!.let(::File).readBytes())
        assertEquals(14889, db.extras.sp.trainNos.size)
        val orphan = db.trainRoutes.keys.filter { it !in db.extras.trainIdByTrainNo }
        assertTrue("§2 有 $orphan 个键反查不到车次 ID", orphan.isEmpty())
        val g1Id = db.trainIdOf("G1") ?: error("t.i 里找不到 G1")
        val route = db.findTrainRouteByTrainId(g1Id)
        assertTrue("G1 按 ID 应精确取到官方径路", route != null && route.legs.isNotEmpty())
        assertEquals(route, db.trainRoutes[db.extras.trainNoOf(g1Id)])
    }

    /** sn.dat 是官方同城站分组表 —— s 片候选被城市主站包住时，权威解释就在这张表里。 */
    @Test
    fun `sn 同城站分组与电报码补充`() {
        val bytes = shard("sn.dat")
        assumeTrue("未提供 CRINFO_ANDB，跳过", bytes != null)
        val sn = StationGroupData.parseSn(bytes!!)
        assertEquals("sn.dat 有残字节", 0, sn.residual)
        assertEquals("组名条数", 73, sn.groupNames.size)
        assertEquals("分组条数", 658, sn.groups.size)
        assertEquals("以 10000+ 组名为键的分组数", 69, sn.groups.keys.count { it >= StationGroupData.CITY_KEY_BASE })
        assertEquals("电报码补充条数", 364, sn.telecodes.size)
        assertEquals("叆河", "AH", sn.telecodes["叆河"])
        // 北京（165）挂的是同城合集：北京南 171 的组键应回到 165
        assertEquals(165, sn.groupKeyOfStation[171])
        assertEquals("北京这组含 10 个同城站", 10, sn.groups[165]?.size)
        val members = sn.cityMembers(171)
        assertTrue("请求站要置顶", members.first() == 171)
        assertTrue("同城合集要含北京南自己", 171 in members.toList())
    }

    /** plat.dat：(车次,站)→股道。旧记忆里的「字段大量待定」不成立，只有 112 条待定。 */
    @Test
    fun `plat 解出车次与站的股道`() {
        val bytes = shard("plat.dat")
        assumeTrue("未提供 CRINFO_ANDB，跳过", bytes != null)
        val table = PlatformTable.parse(bytes!!)
        assertEquals("声明数与实际数不等：${table.declared} vs ${table.size}", table.declared, table.size)
        assertEquals("plat.dat 有残字节", 0, table.residual)
        assertEquals("同一键不该有重复", 0, table.duplicateCount())
        assertEquals("96527", 96527, table.size)
        // G1(6012) 北京南(171) = 17 道；1461(0) 北京(165) = 13 道
        assertEquals("17", table.platformOf(6012, 171))
        assertEquals("13", table.platformOf(0, 165))
        assertEquals("G1 的股道记录数", 7, table.entriesOfTrain(6012).size)
        assertEquals("1461 的股道记录数", 12, table.entriesOfTrain(0).size)
        assertNull("跨车串站必须查不到", table.platformOf(6012, 165))
    }

    /** extra.dat 表2–7：担当企业、三档复兴号名单、调向站、S 车头名单。 */
    @Test
    fun `extra 担当与名单表走步不留残余`() {
        val bytes = shard("extra.dat")
        assumeTrue("未提供 CRINFO_ANDB，跳过", bytes != null)
        val extra = ExtraData.parse(bytes!!, trainCount = 14889)
        assertEquals("extra.dat 有残字节", 0, extra.residual)
        assertEquals("车次级担当条数", 14753, extra.declared)
        assertEquals("本版本站级细分为 0（担当只到车次）", 0, extra.stationDuties.size)
        assertEquals("库里没担当记录的车次要给 null", true, (0 until 14889).any { extra.dutyCode(it) == null })
        assertEquals("G1 担当代码 = 上局上海段", 83, extra.dutyCode(6012))
        assertEquals("1461 担当代码", 83, extra.dutyCode(0))
        assertTrue("复兴号名单非空", extra.fuXingLevel(6012) > 0)
        assertEquals("调向车次条数", 1073, extra.turnStationIds.size)
        assertEquals("多站调向的有 111 条", 111, extra.turnStationIds.values.count { it.size > 1 })
        assertEquals("S 车头名单", 122, extra.suburbanTrainIds.size)

        val db = OfflineDbParser.parse(path!!.let(::File).readBytes())
        val sCodes = db.extras.extra.suburbanTrainIds.map { db.trainCodes[it] }
        assertTrue("S 名单里的车号都该是 S 字头：$sCodes", sCodes.all { it.startsWith("S") })
        assertEquals("上局上海段", db.extras.dutyName(db.enterprises, 6012))
    }

    /** xw.dat：席别位图 + 换号车按里程切段。 */
    @Test
    fun `xw 席别位图与按里程换号`() {
        val bytes = shard("xw.dat")
        assumeTrue("未提供 CRINFO_ANDB，跳过", bytes != null)
        val seats = SeatTable.parse(bytes!!, trainCount = 14889)
        assertEquals("声明数与实际数不等：${seats.declared} vs ${seats.parsed}", seats.declared, seats.parsed)
        assertEquals("xw.dat 有残字节", 0, seats.residual)
        assertEquals(48, seats.seatFlags(6012))          // G1：一等+二等
        assertTrue("G1 没有软卧", seats.seatFlags(6012)!! and 8 == 0)
        assertEquals("带换号表的车次", 4542, seats.segmentTrainCount())
        assertEquals("4167/4170 前 82 公里显示 4170", "4170", seats.exactCode(26, "4167/4170", km = 50))
        assertEquals("82 公里后回到 4167", "4167", seats.exactCode(26, "4167/4170", km = 82))
    }

    /**
     * 票价族：格式全部实现，但 20261108 库里就是空壳。
     * 这条测试的意义是**把「空壳」这个事实钉住** —— 以后哪张表被图定填上了，
     * 数字会立刻变化，提醒重新评估要不要拿它当数据源，而不是让人误以为一直有数据。
     */
    @Test
    fun `票价族在本版库里是空壳但走步完整`() {
        val rwp = shard("rwp.dat")
        assumeTrue("未提供 CRINFO_ANDB，跳过", rwp != null)
        assertEquals(105, OfflineFareParser.parsePairedFares(rwp!!, "rwp.dat").fares.size)
        assertEquals(0, OfflineFareParser.parsePairedFares(rwp, "rwp.dat").residual)
        assertEquals(105, OfflineFareParser.parsePairedFares(shard("dwp.dat")!!, "dwp.dat").fares.size)

        val expected = mapOf("p0.dat" to 11, "p1.dat" to 5, "p2.dat" to 12, "p3.dat" to 9, "p4.dat" to 9)
        expected.forEach { (name, count) ->
            val file = OfflineFareParser.parsePairedFares(shard(name)!!, name)
            assertEquals("$name 条数", count, file.fares.size)
            assertEquals("$name 有残字节", 0, file.residual)
            assertEquals("$name 不该中途停住", "ok", file.stoppedAt)
        }
        var pkTotal = 0
        for (i in 0 until 10) {
            val name = "pk$i.dat"
            val file = OfflineFareParser.parseSeatFares(shard(name)!!, name)
            assertEquals("$name 有残字节", 0, file.residual)
            pkTotal += file.fares.size
        }
        assertEquals("pk 族总条数", 197, pkTotal)

        assertEquals("ts.dat 本版 0 字节", 0, OfflineFareParser.parsePriceRules(shard("ts.dat")!!).size)
        assertEquals("sch.dat 本版 0 字节", 0, OfflineFareParser.parseSchedule(shard("sch.dat")!!).size)
    }

    /** 三份非时刻数据：ime（官方零引用，只做结构对账）与 cdn/zcdn 的调度块 ID。 */
    @Test
    fun `ime 与 cdn 附表按结构解出`() {
        val ime = shard("ime.dat")
        assumeTrue("未提供 CRINFO_ANDB，跳过", ime != null)
        val index = StationGroupData.parseIme(ime!!)
        assertEquals("九宫格码表条数", 550, index.size)
        assertTrue("码 22 应含长春", index["22"]?.contains("长春") == true)

        assertEquals("cdn 行数", 200, StationGroupData.parseBlockIds(shard("cdn.dat")!!).size)
        assertEquals("zcdn 行数", 50, StationGroupData.parseBlockIds(shard("zcdn.dat")!!).size)
        assertEquals("块 ID 长度只有 15", setOf(15), StationGroupData.parseBlockIds(shard("cdn.dat")!!).map { it.length }.toSet())
    }

    /**
     * 离线态也要能推出换号点：`xw.dat` 给「按里程切段」的号表，配上 T 记录的逐站累计里程
     * 就能算出每站该显示哪个号（旧口径「离线库没有逐站号」已被本轮逆向推翻）。
     * 4167/4170 实测段表为 `0km→第1段(4170)`、`82km→第0段(4167)`。
     */
    @Test
    fun `离线态按 xw 段表推出换号点`() = kotlinx.coroutines.runBlocking {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())
        val out = realEngine(db).train("4167/4170", "2026-11-02")
        assertTrue("库里没收这趟车", out != null)
        val codes = out!!.detail.stops.map { it.stationTrainCode }
        assertTrue("逐站号不该全空：$codes", codes.all { it == "4167" || it == "4170" })
        val changes = com.crinfo.app.data.model.deriveTrainChanges(out.detail.stops)
        assertTrue("应推出换号点，实测 $changes", changes.isNotEmpty())
        assertTrue("换上去的号必须是这两个之一：$changes", changes.values.all { it == "4167" || it == "4170" })
    }

    /** 全库装载后各附表要对得上，防止以后加表时把主流程带坏。 */
    @Test
    fun `全库装载后附表与主表同源`() {
        val file = path?.let(::File)
        assumeTrue("未提供 CRINFO_ANDB，跳过", file?.isFile == true)
        val db = OfflineDbParser.parse(file!!.readBytes())
        assertEquals(db.trainCount, db.extras.sp.trainNos.size)
        assertTrue("股道表键要落在车次 ID 量程内", db.extras.platforms.size > 0)
        assertTrue("席别表按 t.i 长度铺", db.extras.seatsOfTrain(0) != null)
        assertEquals("换号表能给出真实段号", "4170", db.extras.exactCode(26, "4167/4170", km = 10))
        assertTrue("北京南 → 同城合集应有 10 站", db.extras.cityMembers(171).size >= 10)
        assertEquals("北京南的归属省", "北京市", db.extras.provinceOf(171))
        // 调向站：库里 1073 条，取一条已知记录（车次 48 → 站 677）落成站名
        assertEquals("调向名单条数", 1073, db.extras.extra.turnStationIds.size)
        assertTrue("48 号车的调向站应能落成站名", db.turnStationNames(48).isNotEmpty())
        assertEquals("库里没说的车给空表而不是猜", emptyList<String>(), db.turnStationNames(0))
        // 电报码回退：站表查不到时，routes 节点略号要能接住（换号提示靠它建键）
        assertEquals("海拉尔的略号来自库内", "HRX", db.telecodeOfStation("海拉尔"))
    }
}
