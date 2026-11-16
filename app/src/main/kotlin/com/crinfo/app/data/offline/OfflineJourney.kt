package com.crinfo.app.data.offline

import com.crinfo.app.data.model.JourneyNode
import com.crinfo.app.data.model.TrainStop

/** 逐站离线里程（供停站展开行用）。 */
data class OfflineMileage(val km: Int, val lines: List<String>)

/** 官方径路算出来的结果：整条经由序列 + 命中停站的里程与所在线路。 */
data class OfficialJourney(
    val nodes: List<JourneyNode>,
    val mileages: Map<String, OfflineMileage>,
)

/**
 * 用 routes.dat §2 的**每车官方径路**（竞品显示的经由就是它）生成经由序列与逐站里程。
 *
 * 径路表只给换线点和线路ID、不给里程，所以里程靠 §1 本线节点累计：
 * **一个区间的两端都能在本线上定位时才累加**，定位不到就这段不给里程 ——
 * 拿别的线上同名节点硬凑会算出倒退的累计值（G3739 走图搜索时出现过 资阳北 741 → 重庆西 738、
 * 柳州 2088 → 南宁东 1829）。线路名不受此限制：走哪条线是径路表直接给的，里程缺经由也不缺。
 *
 * 换线点按**站名**在本线里定位：§1 节点与 §2 站名同源，且线路所（半边山所、长田湾所）根本不在 12306 站表里，
 * 只能靠名字对上。
 */
object OfflineJourney {

    fun build(route: TrainRoute, linesById: Map<Int, RouteLine>, stops: List<TrainStop>): OfficialJourney? {
        if (route.legs.isEmpty() || stops.isEmpty()) return null
        val origin = stops.first()
        val lineNames = route.legs.mapNotNull { linesById[it.lineId]?.name }
        if (lineNames.size != route.legs.size) return null

        var cum = 0
        // 某段的换线点在本线里找不到时，累计里程失去基准：只标到那一段为止，不永久拉黑后面的站
        var cumValid = true
        var cursor = origin.name
        val chain = ArrayList<RouteNode>()
        chain.add(RouteNode(origin.telecode, origin.name, 0, ""))
        route.legs.forEachIndexed { index, leg ->
            val line = linesById.getValue(leg.lineId)
            val next = route.legs.getOrNull(index + 1)?.station ?: route.terminus
            val a = line.stops.firstOrNull { it.name == cursor }
            val b = line.stops.firstOrNull { it.name == next }
            if (a != null && b != null) {
                val base = if (cumValid) cum else -1
                // 本线里程轴的朝向不保证与行车方向一致（G1958 走石太客专线是 232→0 逆着走的），
                // 所以跨度取绝对值 —— 「倒序区间」是正常的，不是解析失败
                if (cumValid) cum += kotlin.math.abs(b.km - a.km)
                // 区间起点是上一段已经落好的换线点，线路名归上一段
                if (index > 0) {
                    chain.lastOrNull { it.name == a.name }?.let { it.line = line.name }
                }
                appendAlong(line, a, b, line.name, chain, base)
            } else {
                appendNamed(line, next, line.name, chain)
                cumValid = false
            }
            cursor = next
        }
        // 同一站可能在两线上各出现一次（成都东 658 在西成客专线与成渝高速线都有条目），
        // 也可能作为「上一区间末节点 + 下一区间首节点」重复；按（站名, 里程）只留第一次，
        // 经由板面就不会出现两行一模一样的站。
        val seen = HashSet<String>()
        chain.removeAll { !seen.add(it.name + "@" + it.km) }

        // 停站与线路节点都优先按**站名**对上：不同来源的电报码不保证一致
        // （12306 车站代码和 12306 电报码本身就不是同一套编码），而库里只有站名是准的。
        val byName = stops.associateBy { it.name }
        fun match(node: RouteNode): TrainStop? = byName[node.name]
        val nodes = chain.map { n ->
            val stop = match(n)
            JourneyNode(
                name = n.name,
                km = n.km.coerceAtLeast(0),
                line = n.line,
                arriveTime = stop?.arriveTime,
                departTime = stop?.departTime,
                telecode = stop?.telecode ?: n.telecode,
                arriveDayDiff = stop?.dayDiff ?: 0,
                departDayDiff = stop?.dayDiff ?: 0,
                isStop = stop != null,
            )
        }
        val mileages = HashMap<String, OfflineMileage>()
        for (i in chain.indices) {
            val node = chain[i]
            if (node.km < 0) continue
            val stop = match(node) ?: continue
            mileages[stop.telecode] = OfflineMileage(node.km, linesUpTo(chain, i))
        }
        return OfficialJourney(nodes, mileages)
    }

    /**
     * 两换线点之间的实际节点链（含线路所）。
     * 里程一律是「区间起点累计 + 距区间起点的距离」：区间可能是逆着线路里程轴走的
     * （G1958 的石太客专线 232→0），这时链上的站要从 b 向 a 排、距离按相对 **a** 算，
     * 否则每个站的里程都会整体少算一个跨度、区间末尾甚至退化成 0。
     */
    private fun appendAlong(
        line: RouteLine,
        from: LineStop,
        to: LineStop,
        lineName: String,
        chain: MutableList<RouteNode>,
        base: Int,
    ) {
        val i = line.stops.indexOfFirst { it.telecode == from.telecode && it.name == from.name }
        val j = line.stops.indexOfFirst { it.telecode == to.telecode && it.name == to.name }
        if (i < 0 || j < 0) return
        val segment = if (j >= i) line.stops.subList(i, j + 1) else line.stops.subList(j, i + 1).reversed()
        for (node in segment.drop(1)) {
            // 换线点在两条线上各出现一次，链上只留第一次（里程与线路名都取上一区间落好的）
            if (chain.any { it.name == node.name }) continue
            chain.add(RouteNode(node.telecode, node.name, if (base < 0) -1 else base + kotlin.math.abs(node.km - from.km), lineName))
        }
    }

    /** 换线点落不到本线节点上：只补这一站与线路名，不编造它前面的站序与里程。 */
    private fun appendNamed(line: RouteLine, name: String, lineName: String, chain: MutableList<RouteNode>) {
        if (chain.last().name == name) return
        val node = line.stops.firstOrNull { it.name == name } ?: return
        chain.add(RouteNode(node.telecode, node.name, -1, lineName))
    }

    /** 到这一站为止依次经过的线路（连续同名只留一条）。 */
    private fun linesUpTo(chain: List<RouteNode>, index: Int): List<String> {
        val out = ArrayList<String>()
        for (n in chain.take(index + 1)) {
            if (n.line.isNotEmpty() && out.lastOrNull() != n.line) out.add(n.line)
        }
        return out
    }

    private data class RouteNode(val telecode: String, val name: String, val km: Int, var line: String)
}
