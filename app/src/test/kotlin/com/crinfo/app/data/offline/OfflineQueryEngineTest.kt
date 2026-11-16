package com.crinfo.app.data.offline

import com.crinfo.app.data.model.BoardDirection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 查询引擎的纯逻辑验算：用合成数据把规则钉死，不依赖 Android 与真实库。
 * 真实库的端到端锚点在 [OfflineDbRealFileTest]，两边都要绿才算数。
 */
class OfflineQueryEngineTest {
    private val stationNames = listOf("甲站", "乙站", "丙站", "丁站")
    private val trainCodes = listOf("G1", "G9", "K1", "G1956/G1957")

    private fun db(rules: List<RunningRule> = emptyList()) = OfflineDb(
        version = "20260101",
        lines = emptyList(),
        rakes = emptyList(),
        trainCodes = trainCodes,
        stationNames = stationNames,
        rules = rules,
    )

    /** 合成引擎：记录与候选都由 Map 直接给，读一次算一次。 */
    private fun engine(
        records: Map<Int, OfflineTimetable>,
        candidates: Map<Int, IntArray>,
        db: OfflineDb = db(),
    ) = OfflineQueryEngine(
        db = db,
        readTrain = { id -> records[id] },
        readCandidates = { id -> candidates[id] ?: IntArray(0) },
        stationOf = { null },
    )

    private fun record(id: Int, stops: List<TimetableStop>, start: Int = 20260101, end: Int = 20501231, rule: Int = 0) =
        OfflineTimetable(id, 6, 0, start, end, rule, stops)

    /**
     * 引擎的输出要把 sp.dat 的 12 位 trainNo 与 plat.dat 的股道真的带出来 ——
     * 这两格在离线态以前是写死的空串/null，本轮补齐数据层后必须由引擎透出，否则白解。
     */
    @Test
    fun `离线输出带上 12 位 trainNo 与股道`() = runBlocking {
        fun be4(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
        fun id255(v: Int) = byteArrayOf((v / 255).toByte(), (v % 255).toByte())
        fun s1(text: String): ByteArray {
            val body = text.toByteArray(Charsets.UTF_8)
            return byteArrayOf(body.size.toByte()) + body
        }
        // sp.dat：表1 无细分 → 表2 两条 trainNo（下标即车次 ID）→ 表3 省名 0 条 → 表4/5/6 各 0 条
        val spBytes = be4(0) + be4(2) + s1("4f0000G1021") + s1("240000146100") +
            id255(0) + be4(0) + be4(0) + be4(0)
        // plat.dat：(车次 0, 站 1) → 股道 13
        val platBytes = be4(1) + be4(0) + be4(1) + byteArrayOf(2) + "13".toByteArray(Charsets.UTF_8)
        val withExtras = db().copy(
            extras = OfflineExtras(sp = SpData.parse(spBytes), platforms = PlatformTable.parse(platBytes)),
        )
        val stops = listOf(
            TimetableStop(0, 7, 0, 0, 0),
            TimetableStop(1, 9, 10, 3, 100),
            TimetableStop(2, 12, 0, 0, 300),
        )
        val e = engine(
            mapOf(0 to record(0, stops)),
            // 直达走的是「两侧候选求交」，三个站都得给出候选
            mapOf(0 to intArrayOf(0), 1 to intArrayOf(0), 2 to intArrayOf(0)),
            db = withExtras,
        )

        assertEquals("4f0000G1021", e.train("G1", "2026-03-01")!!.detail.timetable.trainNo)
        val board = e.board("乙站", "2026-03-01")
        assertTrue("板面非空才有意义", board.isNotEmpty())
        assertTrue("板面每行都要带 trainNo：${board.map { it.trainNo }}", board.all { it.trainNo == "4f0000G1021" })
        val first = board.first()
        assertEquals("参考站台来自 plat.dat", "13", first.referencePlatform)
        assertNull("股道不许冒充检票口", first.platform)
        assertNull("离线态没有实时票口，整行不出现", first.gateText)
        // 直达查询里这趟车终到丙站、不停乙站，所以 plat.dat 没有对应记录，必须给 null 而不是糊上去
        assertEquals("4f0000G1021", e.trips("甲站", "丙站", "2026-03-01").first().trainNo)
    }

    @Test
    fun `始发与终到的时刻字段按在线口径补齐`() = runBlocking {
        val stops = listOf(
            TimetableStop(0, 7, 0, 0, 0),
            TimetableStop(1, 9, 10, 3, 100),
            TimetableStop(2, 12, 0, 0, 300),
        )
        val e = engine(mapOf(0 to record(0, stops)), emptyMap())
        val out = e.train("G1", "2026-03-01")!!
        val list = out.detail.stops
        assertNull("始发站不该有到达时刻", list[0].arriveTime)
        assertEquals("07:00", list[0].departTime)
        // 中间站的出发时刻 = 到达 + 停站分
        assertEquals("09:10", list[1].arriveTime)
        assertEquals("09:13", list[1].departTime)
        assertEquals("12:00", list[2].arriveTime)
        assertNull("终到站不该有出发时刻", list[2].departTime)
        assertEquals(3, list[1].dwellMinutes)
        assertEquals(listOf(0, 130, 300), list.map { it.minutesFromStart })
        assertEquals("全程里程直接取末站累计值", 300, out.detail.totalKm)
    }

    /** 库里每行只有时分两个字节、不存跨天，跨零点只能顺站序推。 */
    @Test
    fun `跨零点按站序推进并给出跨天数`() = runBlocking {
        val stops = listOf(
            TimetableStop(0, 22, 0, 0, 0),
            TimetableStop(1, 6, 10, 2, 500),
            TimetableStop(2, 8, 0, 0, 700),
        )
        val out = engine(mapOf(2 to record(2, stops)), emptyMap()).train("K1", "2026-03-01")!!
        val list = out.detail.stops
        assertEquals("06:10", list[1].arriveTime)
        assertEquals(1, list[1].dayDiff)
        assertEquals(1, list[2].dayDiff)
        assertEquals(0, list[0].dayDiff)
        // 次日 6:10 距始发 22:00 是 8 小时 10 分
        assertEquals(490, list[1].minutesFromStart)
        assertTrue(out.detail.crossesDay)
    }

    /** s 片的城市主站候选混着不停本站的车，必须拿时刻表记录核对后再上屏。 */
    @Test
    fun `板面剔除不停本站的候选`() = runBlocking {
        val through = record(0, listOf(TimetableStop(0, 7, 0, 0, 0), TimetableStop(2, 12, 0, 0, 300)))
        val terminates = record(1, listOf(TimetableStop(0, 8, 0, 0, 0), TimetableStop(1, 9, 0, 0, 100)))
        val starts = record(3, listOf(TimetableStop(1, 10, 0, 0, 0), TimetableStop(2, 11, 0, 0, 100)))
        val e = engine(
            mapOf(0 to through, 1 to terminates, 3 to starts),
            // 候选里塞一趟根本不停乙站的车（id=0 的站点是 甲/丙）
            mapOf(1 to intArrayOf(0, 1, 3)),
        )
        val board = e.board("乙站", "2026-03-01")
        assertFalse("不停本站的候选必须被剔除", board.any { it.trainCode == "G1" })
        // 终到乙站 → 只进到达屏
        assertEquals(listOf(BoardDirection.Arrival), board.filter { it.trainCode == "G9" }.map { it.direction })
        // 始发乙站 → 只进出发屏
        assertEquals(listOf(BoardDirection.Departure), board.filter { it.trainCode == "G1956/G1957" }.map { it.direction })
    }

    @Test
    fun `日期过期与周期规则的车不进板面`() = runBlocking {
        val expired = record(0, listOf(TimetableStop(0, 7, 0, 0, 0), TimetableStop(1, 9, 0, 0, 100)), end = 20261124)
        val weekendOnly = record(
            1,
            listOf(TimetableStop(0, 8, 0, 0, 0), TimetableStop(1, 10, 0, 0, 100)),
            start = 20261101,
            rule = 1,
        )
        val e = engine(
            mapOf(0 to expired, 1 to weekendOnly),
            mapOf(1 to intArrayOf(0, 1)),
            db(rules = listOf(RunningRule(1, 1), RunningRule(7, 0b11))),
        )
        // 11-25 已过 1461 类的截止日
        assertEquals(0, e.board("乙站", "2026-11-25").count { it.trainCode == "G1" })
        // 周期 7、只点亮前两格：距始发 3 天的那天不开
        assertEquals(1, e.board("乙站", "2026-11-01").count { it.trainCode == "G9" })
        assertEquals(0, e.board("乙站", "2026-11-04").count { it.trainCode == "G9" })
    }

    /**
     * 车站页的「查询日」是**到站日**，可开行判定只认始发日 —— 偏移几天就得把日期往回折几天。
     * 旧实现直接拿查询日当始发日判，表里演的是「今天始发的车」：带跨天的那班其实明天才到站，
     * 而今天到站的那班（昨天始发）一条都不在。
     */
    @Test
    fun `跨零点到达的车按到站日进表按始发日判开行`() = runBlocking {
        // 22:00 始发，次日 06:10 到乙站、06:20 再开：到与发都是 +1
        val lateNight = record(
            2,
            listOf(TimetableStop(0, 22, 0, 0, 0), TimetableStop(1, 6, 10, 10, 500), TimetableStop(2, 9, 0, 0, 700)),
            start = 20261101,
            rule = 1,
        )
        val e = engine(
            mapOf(2 to lateNight),
            mapOf(1 to intArrayOf(2)),
            db(rules = listOf(RunningRule(1, 1), RunningRule(7, 0b11))),
        )
        // 位图点亮 11-01、11-02 两格 → 这两天始发的班次才存在，到站日各顺延一天
        assertEquals(listOf("06:10", "06:20"), e.board("乙站", "2026-11-02").map { it.time }.sorted())
        assertEquals(2, e.board("乙站", "2026-11-03").size)
        // 11-04 要的是 11-03 始发的那班，位图没点亮 → 这天没它（旧口径会把 11-04 始发的车演上来）
        assertEquals(0, e.board("乙站", "2026-11-04").size)
        assertTrue("到站日就是查询日，不许再挂跨天", e.board("乙站", "2026-11-03").all { it.dayDiff == 0 })
    }

    /** 经停车跨零点停靠（到 +0／发 +1）：两张屏各按各的偏移折日期，不能共用一次判定。 */
    @Test
    fun `跨零点停靠的到达与出发行各折各的始发日`() = runBlocking {
        // 18:00 始发，23:55 到乙站、停 20 分后 00:15 开 → 到达 +0、出发 +1
        val straddle = record(
            2,
            listOf(TimetableStop(0, 18, 0, 0, 0), TimetableStop(1, 23, 55, 20, 500), TimetableStop(2, 5, 0, 0, 700)),
            start = 20261101,
            rule = 1,
        )
        val e = engine(
            mapOf(2 to straddle),
            mapOf(1 to intArrayOf(2)),
            db(rules = listOf(RunningRule(1, 1), RunningRule(7, 0b11))),
        )
        // 11-02：到达行要 11-02 始发（第 1 格，开）、出发行要 11-01 始发（第 0 格，开）→ 两行都在。
        // 它们是同一车次两个物理班次：00:15 开的是昨天 18:00 那班（跨零点停站后清早再走），
        // 23:55 到的是今天 18:00 那班。按车站时钟排，凌晨那条自然在表头。
        assertEquals(
            listOf(BoardDirection.Departure to "00:15", BoardDirection.Arrival to "23:55"),
            e.board("乙站", "2026-11-02").map { it.direction to it.time },
        )
        // 11-03：到达行要的 11-03 始发没开，出发行要的 11-02 始发开了 → 只剩凌晨那班出发
        assertEquals(
            listOf(BoardDirection.Departure to "00:15"),
            e.board("乙站", "2026-11-03").map { it.direction to it.time },
        )
    }

    /** 模糊同城扩站：两侧多站命中同一车次时只出一条，取「先停的出发站 → 最后停的到达站」整段。 */
    @Test
    fun `模糊扩站按多站取乘车段`() = runBlocking {
        val stops = listOf(
            TimetableStop(0, 7, 0, 0, 0),
            TimetableStop(1, 8, 0, 0, 50),
            TimetableStop(2, 9, 0, 0, 100),
            TimetableStop(3, 10, 0, 0, 200),
        )
        val e = engine(
            mapOf(0 to record(0, stops)),
            mapOf(0 to intArrayOf(0), 1 to intArrayOf(0), 2 to intArrayOf(0), 3 to intArrayOf(0)),
        )
        val wide = e.tripsBetween(listOf("甲站", "乙站"), listOf("丙站", "丁站"), "2026-03-01")
        assertEquals("同一趟车两侧各停两站也只该出一条", 1, wide.size)
        val t = wide.single()
        assertEquals("甲站", t.fromName)
        assertEquals("丁站", t.toName)
        assertEquals("07:00", t.departTime)
        assertEquals("10:00", t.arriveTime)
        assertEquals(200, t.distanceKm)

        // 收窄到中间两站，乘车段与里程就跟着变
        val narrow = e.tripsBetween(listOf("乙站"), listOf("丙站"), "2026-03-01")
        assertEquals("乙站", narrow.single().fromName)
        assertEquals("08:00", narrow.single().departTime)
        assertEquals(50, narrow.single().distanceKm)
    }

    /** 站到站要按运行方向：同两站反着坐不算直达，历时与区间里程都要对。 */
    @Test
    fun `站到站按方向取站并算历时与里程`() = runBlocking {
        val stops = listOf(
            TimetableStop(0, 7, 0, 0, 0),
            TimetableStop(1, 9, 10, 3, 100),
            TimetableStop(2, 12, 0, 0, 300),
        )
        val e = engine(mapOf(0 to record(0, stops)), mapOf(0 to intArrayOf(0), 2 to intArrayOf(0)))
        val trips = e.trips("甲站", "丙站", "2026-03-01")
        assertEquals(1, trips.size)
        val t = trips.first()
        assertEquals("07:00", t.departTime)
        assertEquals("12:00", t.arriveTime)
        assertEquals("5小时0分", t.durationText)
        assertEquals(300, t.distanceKm)
        assertTrue("离线没有余票与票价", t.seats.isEmpty())
        assertFalse("无席别数据不该算售罄", t.soldOut)
        // 反向不该出现
        assertTrue(e.trips("丙站", "甲站", "2026-03-01").isEmpty())
    }

    /** 库里把同车底多车次写成一格，用户只输其中一段也要能查到。 */
    @Test
    fun `合并车号的分段也能命中库内 ID`() {
        val db = db()
        assertEquals(3, db.trainIdOf("G1956/G1957"))
        assertEquals(3, db.trainIdOf("G1957"))
        // 大小写、空白都由 trainIdOf 归一，调用方不该自己记着转
        assertEquals(3, db.trainIdOf("  g1956 "))
        assertEquals(0, db.trainIdOf("G1"))
        assertEquals(null, db.trainIdOf("G99998"))
    }

    /**
     * 库里把「同一车底中途换号」存成一格（真库例：南宁东的 `G3741/G3744`）。
     * 板面要**照原样显示**才是事实，但点击查的必须是斜杠前的单个号 ——
     * 拿整串去查一定报错，这就是本次的问题本体。
     */
    @Test
    fun `合并车次号显示照原样但查询取前一段`() = runBlocking {
        val stops = listOf(TimetableStop(0, 7, 0, 0, 0), TimetableStop(1, 9, 0, 0, 120))
        val e = OfflineQueryEngine(
            db = db().copy(trainCodes = listOf("G3741/G3744")),
            readTrain = { id ->
                if (id == 0) OfflineTimetable(id, 6, 0, 20260101, 20501231, 0, stops) else null
            },
            readCandidates = { id -> if (id == 1) intArrayOf(0) else IntArray(0) },
            stationOf = { null },
        )
        val board = e.board("乙站", "2026-03-01")
        assertEquals("板面该显示合并号", "G3741/G3744", board.first().trainCode)
        assertEquals(
            "点击要退化成单个号", "G3741",
            com.crinfo.app.data.model.primaryTrainCode(board.first().trainCode),
        )
        val out = e.train("G3741", "2026-03-01")
        assertTrue("斜杠前的号应当查得到该车", out != null)
        assertEquals("详情页仍显示完整换号", "G3741/G3744", out!!.detail.trainCode)
    }
}
