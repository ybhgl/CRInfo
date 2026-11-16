package com.crinfo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 钉住大屏拆屏规则与实时状态挂载：拆错方向会把终到车演成发车，属于最刺眼的错误。 */
class StationBoardTest {

    @Test
    fun `originating train only shows on the departure board`() {
        val board = buildBoard(listOf(train("G1", from = "VNP", to = "AOH", dep = "06:30", arr = null)), "VNP")
        assertEquals(listOf("G1"), board.departures.map { it.trainCode })
        assertTrue("终到站不是本站但没有到达时刻，不该进到达屏", board.arrivals.isEmpty())
    }

    @Test
    fun `terminating train only shows on the arrival board`() {
        val board = buildBoard(listOf(train("C2602", from = "TJP", to = "VNP", dep = "06:12", arr = "06:12")), "VNP")
        assertTrue("终到本站的车不能出现在出发屏", board.departures.isEmpty())
        assertEquals(listOf("C2602"), board.arrivals.map { it.trainCode })
        assertEquals("天津西", board.arrivals.single().otherEnd)
    }

    @Test
    fun `passing train shows on both boards with its own times`() {
        val board = buildBoard(listOf(train("G101", from = "VNP", to = "AOH", dep = "09:10", arr = "08:52")), "JNK")
        assertEquals("08:52", board.arrivals.single().time)
        assertEquals("09:10", board.departures.single().time)
        assertEquals(BoardDirection.Arrival, board.arrivals.single().direction)
        assertEquals("上海虹桥", board.departures.single().otherEnd)
    }

    @Test
    fun `board rows sort by clock time with no day offset`() {
        // 接口那两列 day_diff 是「距列车始发日」，不是「距本站查询日」：12306 已按到站日筛过开行，
        // 板面给的就是「这天在站的车」。拿它折排序键会把凌晨车甩到队尾（实测郑州 293 行里 127 行中招）
        val board = buildBoard(
            listOf(
                train("G900", from = "VNP", to = "AOH", dep = "01:20"),
                train("G800", from = "VNP", to = "AOH", dep = "22:40"),
                train("G700", from = "VNP", to = "AOH", dep = "06:05"),
            ),
            "VNP",
        )
        assertEquals(listOf("G900", "G700", "G800"), board.departures.map { it.trainCode })
        assertTrue("板面行不该带跨天徽标", board.departures.all { it.dayDiff == 0 })
    }

    @Test
    fun `live states attach by normalized train code`() {
        val trains = listOf(
            train("G1081", from = "VNP", to = "JNK", dep = "21:45"),
            train("G1083", from = "VNP", to = "JNK", dep = "21:52"),
        )
        val board = buildBoard(
            trains,
            "VNP",
            departures = listOf(LiveBoard(" g1081 ", "正在检票", wicket = "20检票口")),
        )
        val matched = board.departures.first { it.trainCode == "G1081" }
        assertEquals("正在检票", matched.stateText)
        assertEquals("20检票口", matched.live?.wicket)
        assertTrue(matched.live?.isHandling == true)
        assertNull(board.departures.first { it.trainCode == "G1083" }.live)
    }

    @Test
    fun `delay text is parsed out of the live state`() {
        assertEquals(13, LiveBoard("G48", "晚点13分").delayMinutes)
        assertTrue(LiveBoard("G48", "晚点13分").isDelayed)
        assertEquals(0, LiveBoard("G48", "正点").delayMinutes)
        assertFalse(LiveBoard("G48", "正点").isDelayed)
    }

    @Test
    fun `departure and arrival boards stay separate`() {
        val board = buildBoard(
            listOf(
                train("G1", from = "VNP", to = "AOH", dep = "06:30"),
                train("G2", from = "AOH", to = "VNP", arr = "05:59"),
            ),
            "VNP",
        )
        assertEquals(listOf("G1"), board.entries(BoardDirection.Departure).map { it.trainCode })
        assertEquals(listOf("G2"), board.entries(BoardDirection.Arrival).map { it.trainCode })
        assertEquals(2, board.total)
    }

    @Test
    fun `tags come from the consist on the board`() {
        val board = buildBoard(
            listOf(train("G1", from = "VNP", to = "AOH", dep = "06:30", style = "CR400BF-AZ")),
            "VNP",
        )
        assertEquals(listOf(TrainTag.FUXING, TrainTag.SMART), board.departures.single().tags)
    }

    /* ---------------- 大屏 ---------------- */

    @Test
    fun `live screen rows inherit consist from the all-day board`() {
        val board = buildBoard(listOf(train("G1081", from = "VNP", to = "JNK", dep = "21:45")), "VNP")
        val screen = buildScreen(
            liveDepartures = listOf(LiveBoard("G1081", "正在检票", time = "21:45", otherEnd = "济南东", wicket = "20检票口")),
            liveArrivals = emptyList(),
            board = board,
            nowMinutes = timeToMinutes("21:20"),
        )
        assertTrue("大屏只演实时源里有的那趟", screen.arrivals.isEmpty())
        val row = screen.departures.single()
        assertEquals("CR400AF-S", row.trainStyle)
        assertEquals("20检票口", row.live?.wicket)
        assertEquals("正在检票", row.stateText)
        assertTrue(screen.fromLiveSource)
    }

    @Test
    fun `screen keeps live rows the board does not know`() {
        val screen = buildScreen(
            liveDepartures = listOf(LiveBoard("S102", "正在候车", time = "21:58", otherEnd = "滨海港")),
            liveArrivals = emptyList(),
            board = StationBoard.EMPTY,
            nowMinutes = timeToMinutes("21:20"),
        )
        val row = screen.departures.single()
        assertEquals("S102", row.trainCode)
        assertEquals("滨海港", row.otherEnd)
        assertEquals("", row.trainStyle)
    }

    @Test
    fun `screen falls back to the time window without the live source`() {
        val board = buildBoard(
            listOf(
                train("G600", from = "VNP", to = "AOH", dep = "11:20"),
                train("G700", from = "VNP", to = "AOH", dep = "12:05"),
                train("G800", from = "VNP", to = "AOH", dep = "15:00"),
                train("G900", from = "VNP", to = "AOH", dep = "16:30"),
            ),
            "VNP",
        )
        val screen = buildScreen(emptyList(), emptyList(), board, timeToMinutes("12:00"))
        // 窗口 = 现在前 30 分到后 4 小时，即 11:30~16:00
        assertEquals(listOf("G700", "G800"), screen.departures.map { it.trainCode })
        assertFalse("退化模式没有实时状态", screen.fromLiveSource)
        assertTrue(screen.departures.all { it.live == null })
    }

    @Test
    fun `screen rows are ordered by clock time`() {
        val screen = buildScreen(
            liveDepartures = listOf(
                LiveBoard("G800", "正在候车", time = "22:40"),
                LiveBoard("G700", "正在检票", time = "21:30"),
            ),
            liveArrivals = emptyList(),
            board = StationBoard.EMPTY,
            nowMinutes = timeToMinutes("21:20"),
        )
        assertEquals(listOf("G700", "G800"), screen.departures.map { it.trainCode })
    }

    @Test
    fun `screen row takes the day marker from the live source only`() {
        val board = buildBoard(
            listOf(
                train("D1", from = "VNP", to = "JNK", dep = "00:05"),
                train("G800", from = "VNP", to = "JNK", dep = "22:40"),
            ),
            "VNP",
        )
        val screen = buildScreen(
            liveDepartures = listOf(
                LiveBoard("D1", "正在候车", time = "00:05", otherEnd = "滨海", dayDiff = 1),
                LiveBoard("G800", "正在候车", time = "22:40", otherEnd = "济南东"),
            ),
            liveArrivals = emptyList(),
            board = board,
            nowMinutes = timeToMinutes("13:20"),
        )
        assertEquals("只有实时源标了跨天的那条该排到最后", listOf("G800", "D1"), screen.departures.map { it.trainCode })
        assertEquals(1, screen.departures.last().dayDiff)
        // 板面不再供跨天数：实时源没标的行一律当日（曾经这里会借到列车自己的 +1/+2）
        assertEquals(0, screen.departures.first().dayDiff)
    }

    /** 携程不给跨天标记，只能按「时刻明显早于此刻 = 明天那张屏」推导。 */
    @Test
    fun `next-day rows are derived from how far behind the clock they are`() {
        // 实测 13:20 的郑州屏上只有 D1 00:05、K1159 00:17 比现在早，liechetime 恰好只给这两条标 1
        assertEquals(1, stationDayDiff("00:05", timeToMinutes("13:20")))
        assertEquals(1, stationDayDiff("00:17", timeToMinutes("13:20")))
        assertEquals(1, stationDayDiff("04:50", timeToMinutes("14:59")))
        // 已到站但仍留在屏上的车：实测最多早 53 分钟（兰州 C681 到 14:06，此刻 14:59，状态还挂「正点」）
        assertEquals("刚办完的车不许标成明天", 0, stationDayDiff("14:06", timeToMinutes("14:59")))
        assertEquals(0, stationDayDiff("12:43", timeToMinutes("12:44")))
        assertEquals(0, stationDayDiff("13:29", timeToMinutes("13:20")))
        assertEquals(0, stationDayDiff(null, timeToMinutes("13:20")))
        assertEquals(0, stationDayDiff("乱码", timeToMinutes("13:20")))
    }

    @Test
    fun `degraded screen keeps the early morning train`() {
        // 板面不再折跨天数，凌晨车就还在窗口里：曾经它被当成「后天 02:58」整条挤出大屏
        val board = buildBoard(listOf(train("Z166", from = "VNP", to = "AOH", dep = "02:58")), "VNP")
        val screen = buildScreen(emptyList(), emptyList(), board, timeToMinutes("00:30"))
        assertEquals(listOf("Z166"), screen.departures.map { it.trainCode })
        assertEquals(0, screen.departures.single().dayDiff)
    }

    /* ---------------- 检票口 ---------------- */

    @Test
    fun `gate codes are normalized across station writing styles`() {
        // 上海虹桥：连写且带尾部分隔符
        assertEquals("11A、11B", normalizeGate("11A11B#"))
        // 同一个口在 # 前后重复出现，只留一次
        assertEquals("11A、11B", normalizeGate("11A#11A11B#"))
        // 郑州东：# 分隔多个口
        assertEquals("12A、12B、1A", normalizeGate("12A#12B#1A#"))
        // 广州南：中文「或」分隔，且存在字母在前的写法
        assertEquals("10、A10", normalizeGate("10或A10#10或A10#"))
        // liechetime 自带字面「检票口」与中英文混用分隔符
        assertEquals("22A、23A、22B、23B", normalizeGate("22A、23A检票口,22B、23B检票口"))
        assertEquals("20", normalizeGate("20检票口"))
        // 抽不到任何编号时原样给，不猜
        assertEquals("商务候车区", normalizeGate("商务候车区"))
        assertNull(normalizeGate("  "))
        assertNull(normalizeGate(null))
    }

    @Test
    fun `ctrip checking port splits into waiting room and gate`() {
        // 携程把两段并成一串，按第一个斜杠拆回 liechetime 的两字段口径（实测六站逐条一致）
        assertEquals("二层候车大厅" to "12A、13A检票口", splitCheckingPort("二层候车大厅/12A、13A检票口"))
        assertEquals(
            "城际进站厅,京津候车区" to "18检票口,19检票口,快速进站4厅18、19检票口",
            splitCheckingPort("城际进站厅,京津候车区/18检票口,19检票口,快速进站4厅18、19检票口"),
        )
        // 广州南有整串不带斜杠的，候车室名已并进票口串里，只能整体当检票口
        assertEquals(null to "三层候车室A1", splitCheckingPort("三层候车室A1"))
        assertEquals(null to "8检票口", splitCheckingPort("/8检票口"))
        assertEquals(null to null, splitCheckingPort("  "))
        assertEquals(null to null, splitCheckingPort(null))
    }

    @Test
    fun `arrival rows do not borrow the departure gate code`() {
        val board = buildBoard(
            listOf(train("G2", from = "AOH", to = "VNP", arr = "05:59", platform = "12A12B#")),
            "VNP",
        )
        assertNull("板面那个票口是本站出发旅客走的，到达屏不该演", board.arrivals.single().gateText)
    }

    @Test
    fun `departure gate prefers the live string and falls back to the board`() {
        val trains = listOf(train("G1", from = "VNP", to = "AOH", dep = "06:30", platform = "20A20B#"))
        val withLive = buildBoard(
            trains,
            "VNP",
            departures = listOf(LiveBoard("G1", "正在检票", wicket = "22A、23A检票口")),
        )
        assertEquals("22A、23A", withLive.departures.single().gateText)
        assertEquals("20A、20B", buildBoard(trains, "VNP").departures.single().gateText)
    }

    /* ---------------- 车型兜底（凌晨当日板面空窗） ---------------- */

    @Test
    fun `neighbouring day board fills the blank style and gate`() {
        val sparse = buildScreen(
            liveDepartures = listOf(LiveBoard("G1", "正在候车", time = "06:30", otherEnd = "上海虹桥")),
            liveArrivals = emptyList(),
            board = buildBoard(emptyList(), "VNP"),
            nowMinutes = timeToMinutes("06:00"),
        )
        assertTrue("今日板面挂不上时车型是空的", sparse.departures.single().trainStyle.isBlank())
        val supplement = buildBoard(
            listOf(train("G1", from = "VNP", to = "AOH", dep = "06:30", style = "CR400BF-A", platform = "11A11B#")),
            "VNP",
        ).supplementByCode()
        val filled = sparse.filledFrom(supplement)
        assertEquals("CR400BF-A", filled.departures.single().trainStyle)
        assertEquals("11A、11B", filled.departures.single().gateText)
        assertEquals("06:30", filled.departures.single().time)
    }

    @Test
    fun `offline car type restores the badges when both boards miss`() {        val screen = buildScreen(
            liveDepartures = listOf(LiveBoard("G1", "正点", time = "06:30", otherEnd = "上海虹桥")),
            liveArrivals = emptyList(),
            board = StationBoard.EMPTY,
            nowMinutes = timeToMinutes("06:00"),
        )
        val filled = screen.filledByCarTypes { code -> if (code == "G1") "CR400AF-BZ" else null }
        assertEquals("CR400AF-BZ", filled.departures.single().trainStyle)
        assertEquals("徽标由车型派生，补上型号就该回来", listOf(TrainTag.FUXING, TrainTag.SMART), filled.departures.single().tags)
        val untouched = buildScreen(
            liveDepartures = listOf(LiveBoard("G9999", "正点", time = "06:30")),
            liveArrivals = emptyList(),
            board = StationBoard.EMPTY,
            nowMinutes = timeToMinutes("06:00"),
        ).filledByCarTypes { null }
        assertTrue("库里也没有的车次保持空白，不编型号", untouched.departures.single().trainStyle.isBlank())
    }

    @Test
    fun `trains view fills the missing style without overwriting the board value`() {
        val board = buildBoard(
            listOf(
                train("G1", from = "VNP", to = "AOH", dep = "06:30", style = "CR400AF-A"),
                train("G9998", from = "VNP", to = "AOH", dep = "07:00", style = ""),
            ),
            "VNP",
        )
        val filled = board.departures.filledByCarTypes { code -> if (code == "G9998") "CR400BF-A" else null }
        assertEquals("CR400BF-A", filled.first { it.trainCode == "G9998" }.trainStyle)
        assertEquals("板面已有的型号不被覆盖", "CR400AF-A", filled.first { it.trainCode == "G1" }.trainStyle)
    }

    private fun train(
        code: String,
        from: String,
        to: String,
        dep: String? = null,
        arr: String? = null,
        style: String = "CR400AF-S",
        platform: String? = null,
    ) = ScreenTrain(
        trainCode = code,
        trainNo = "240000$code",
        fromStation = if (from == "VNP") "北京南" else if (from == "TJP") "天津西" else "上海虹桥",
        toStation = if (to == "AOH") "上海虹桥" else if (to == "VNP") "北京南" else "济南西",
        fromTelecode = from,
        toTelecode = to,
        arriveTime = arr,
        departTime = dep,
        runningTimeText = "",
        distanceKm = 0,
        platform = platform,
        trainStyle = style,
        className = "高速",
        typeName = "",
        stationBureauCode = "P",
        corporationCode = "H00",
        stationCorporation = "",
        jiaoluCorporation = "",
        jiaoluDept = "",
        jiaoluStyle = "",
        stationOrder = "01",
    )
}
