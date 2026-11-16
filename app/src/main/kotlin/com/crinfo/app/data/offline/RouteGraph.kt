package com.crinfo.app.data.offline

/** 一趟车某一站的离线径路结果：相对始发站的累计里程，以及到这一站所经过的线路。 */
data class RouteStop(val km: Int, val lines: List<String>)

/**
 * 用离线线路表把一趟车的停靠站串成径路。**这是自建兜底，不是库的语义**。
 *
 * 库里的里程都**相对本线路起点**，跨线时必须按「走了哪些线、在哪进出」累加，所以把线路看成图：
 * 相邻节点连一条边、权重取里程差，代价用 `(换线次数, 里程)` 字典序。
 * 纯按最短距离会挑出「更短但车不走」的别线（实测 曲阜东→南京南 最短 432km，板面却是 488km），
 * 先压换线次数才能贴住图定径路。
 *
 * 2026-11-02 解通 `t*.dat` 之后，这里的定位要摆正：
 * - **逐站里程不再归它管** —— 时刻表记录本身就带每站的累计里程（255 进制，实测与 12306 板面逐字一致），
 *   那是权威值；图搜索算出来的只在「时刻表没给」时才用。
 * - **路路通自己没有图搜索兜底**：车不在 §2 官方径路时它直接放弃（日志 `No train route found`）。
 *   所以这里的产物只能当「推断」，界面文案别说成库里的经由。
 */
class RouteGraph(lines: List<RouteLine>) {
    private data class Edge(val to: String, val km: Int, val line: String)

    private data class Cost(val swaps: Int, val km: Int) : Comparable<Cost> {
        override fun compareTo(other: Cost): Int =
            if (swaps != other.swaps) swaps - other.swaps else km - other.km
    }

    private data class NodeState(
        val cost: Cost,
        val line: String?,
        val route: List<String>,
        val prev: String? = null,
    )

    /** 经由板面上的一个节点：营业停站与线路所都在这里，`line` 是到达该节点所走的线路。 */
    data class RouteNode(
        val telecode: String,
        val name: String,
        val km: Int,
        val line: String,
    )

    private val adj: Map<String, List<Edge>>
    private val nodeNames: Map<String, String>

    init {
        val builder = HashMap<String, MutableList<Edge>>()
        val names = HashMap<String, String>()
        for (line in lines) {
            val stops = line.stops
            for (stop in stops) names.putIfAbsent(stop.telecode, stop.name)
            for (i in 0 until stops.size - 1) {
                val a = stops[i]
                val b = stops[i + 1]
                val w = b.km - a.km
                // 权重 0 是同名里程的并行线路所（济南西 JGK 与 EII 都是 406），丢了路就断
                if (w < 0) continue
                builder.getOrPut(a.telecode) { mutableListOf() }.add(Edge(b.telecode, w, line.name))
                builder.getOrPut(b.telecode) { mutableListOf() }.add(Edge(a.telecode, w, line.name))
            }
        }
        adj = builder
        nodeNames = names
    }

    /**
     * 停靠站电报码序列 → 每站的累计里程与经由线路。
     * 任一站不可达就整体返回 null，由调用方回落到车站板面，绝不给半截错误里程。
     */
    fun route(telecodes: List<String>): List<RouteStop>? {
        if (telecodes.size < 2) return null
        if (telecodes.any { !adj.containsKey(it) }) return null
        val state = singleSource(telecodes.first())
        return telecodes.map { s -> state[s] ?: return null }
            .map { RouteStop(it.cost.km, it.route) }
    }

    /**
     * 经由的完整节点序列（含两站之间的线路所/通过站），每项带累计里程与所属线路。
     * 板面上的「北京南 -（京沪高速线）- 廊坊（60km） - …」就是这份列表。
     */
    fun fullRoute(telecodes: List<String>): List<List<RouteNode>>? {
        if (telecodes.size < 2) return null
        if (telecodes.any { !adj.containsKey(it) }) return null
        val state = singleSource(telecodes.first())
        return (0 until telecodes.lastIndex).map { i ->
            pathNodes(state, telecodes[i], telecodes[i + 1]) ?: return null
        }
    }

    /** 两站之间的实际节点链；`cost.km` 已是相对始发站的累计值，直接取用。 */
    private fun pathNodes(state: Map<String, NodeState>, from: String, to: String): List<RouteNode>? {
        val chain = ArrayList<String>()
        var cur: String? = to
        var guard = 0
        while (cur != null && guard++ <= MAX_PATH) {
            chain.add(cur)
            if (cur == from) break
            cur = state[cur]?.prev
        }
        if (chain.lastOrNull() != from) return null
        chain.reverse()
        return chain.map { tele ->
            val s = state[tele] ?: return null
            RouteNode(tele, nodeNames[tele] ?: tele, s.cost.km, s.line ?: "")
        }
    }

    private companion object {
        const val MAX_PATH = 600
    }

    private fun singleSource(origin: String): Map<String, NodeState> {
        val best = HashMap<String, NodeState>()
        val pending = ArrayList<String>()
        best[origin] = NodeState(Cost(0, 0), null, emptyList())
        pending.add(origin)
        while (pending.isNotEmpty()) {
            var idx = 0
            for (k in 1 until pending.size) {
                if (best.getValue(pending[k]).cost < best.getValue(pending[idx]).cost) idx = k
            }
            val node = pending.removeAt(idx)
            val cur = best.getValue(node)
            for (edge in adj[node].orEmpty()) {
                val swaps = cur.cost.swaps +
                    if (cur.line == null || cur.line == edge.line) 0 else 1
                val candidate = NodeState(
                    cost = Cost(swaps, cur.cost.km + edge.km),
                    line = edge.line,
                    route = (cur.route + edge.line).dedupeRuns(),
                    prev = node,
                )
                val old = best[edge.to]
                if (old == null || candidate.cost < old.cost) {
                    best[edge.to] = candidate
                    pending.add(edge.to)
                }
            }
        }
        return best
    }
}

/** 去掉连续的重复项；同一条线被走多次时仍会保留两段。 */
private fun List<String>.dedupeRuns(): List<String> {
    if (size < 2) return this
    val out = ArrayList<String>(size)
    for (item in this) {
        if (out.isEmpty() || out.last() != item) out.add(item)
    }
    return out
}
