package com.crinfo.app.data.offline

import com.crinfo.app.data.offline.OfflineBinary.be
import com.crinfo.app.data.offline.OfflineBinary.signedShort
import com.crinfo.app.data.offline.OfflineBinary.u8
import com.crinfo.app.data.offline.OfflineBinary.utf8

/**
 * sn.dat（路路通 `FuzzyStationMgr.init`）+ ime.dat + cdn.dat/zcdn.dat 三份「站名周边与调度」文件。
 *
 * sn.dat 是官方自己的**同城站分组表**，正好是 `s*.dat` 板面候选被城市主站污染的那一层：
 * 真库实测 658 组，其中 69 组的键是 `10000+组名下标`（键本身不是车站，如「安图」→「安图西」），
 * 其余 589 组的键直接就是车站 ID（如北京 165 → 北京南/北京西/…/亦庄 共 10 站）。
 * 所以「候选=同城合集」不再靠猜，按这张表就能把同城站拆开或并回去。
 */
data class StationGroupData(
    /** 表1：组名，**组键 = 10000 + 下标**（这些组名本身多半不在 s.i 里）。 */
    val groupNames: List<String>,
    /** 表2：组键 → 组内车站 ID，**官方口径主站在首位**（把小于键的成员轮转到尾部）。 */
    val groups: Map<Int, IntArray>,
    /** 表3：站名 → 电报码补充表（真库 364 条，`叆河-AH` 这类 s.i 里带不到的略号）。 */
    val telecodes: Map<String, String>,
    /** 车站 ID → 组键。查不到就是这站自己成组（官方 `getCity` 找不到组时返回入参）。 */
    val groupKeyOfStation: Map<Int, Int>,
    val residual: Int,
) {
    /**
     * 某站的同城站集合，**请求站置顶**、其余按 ID 升序 —— 与 `FuzzyStationMgr.OooO0Oo(id, true)` 同形，
     * 板面/站站查询可以拿它当「这些站算同一城市」的官方依据。
     */
    fun cityMembers(stationId: Int): IntArray {
        val key = groupKeyOfStation[stationId] ?: return intArrayOf(stationId)
        val members = groups[key] ?: return intArrayOf(stationId)
        if (members.isEmpty() || members[0] == stationId) return members
        return intArrayOf(stationId) + members.filter { it != stationId }.toIntArray()
    }

    companion object {
        val EMPTY = StationGroupData(emptyList(), emptyMap(), emptyMap(), emptyMap(), 0)

        fun parseSn(b: ByteArray): StationGroupData {
            var p = 0
            val n1 = signedShort(b, p)
            if (n1 !in 0..20000) return StationGroupData(emptyList(), emptyMap(), emptyMap(), emptyMap(), b.size)
            p += 2
            val names = ArrayList<String>(if (n1 > 0) n1 else 0)
            while (names.size < n1 && p + 2 <= b.size) {
                val len = be(b, p, 2)
                if (len !in 1..60 || p + 2 + len > b.size) break
                names.add(utf8(b, p + 2, len).orEmpty())
                p += 2 + len
            }
            val n2 = signedShort(b, p)
            if (n2 !in 0..20000) return StationGroupData(names, emptyMap(), emptyMap(), emptyMap(), b.size - p)
            p += 2
            val groups = LinkedHashMap<Int, IntArray>(if (n2 > 0) n2 else 0)
            var i2 = 0
            while (i2 < n2 && p + 3 <= b.size) {
                val key = signedShort(b, p)
                val count = u8(b, p + 2)
                p += 3
                if (count < 0 || p + count * 2 > b.size) break
                // readByte 是有符号的，但 n 只到 127；成员按官方那样先升序再把「首元素 ≥ key」轮转到最前
                val members = IntArray(count) { signedShort(b, p + it * 2) }.sortedArray()
                p += count * 2
                groups.putIfAbsent(key, rotateToFirstNotLessThan(members, key))
                i2++
            }
            val n3 = signedShort(b, p)
            if (n3 !in 0..20000) {
                return StationGroupData(names, groups, emptyMap(), invertKeys(groups), (b.size - p).coerceAtLeast(0))
            }
            p += 2
            val tele = HashMap<String, String>(if (n3 > 0) n3 else 1)
            var i3 = 0
            while (i3 < n3 && p + 1 <= b.size) {
                val len = u8(b, p)
                if (len !in 1..60 || p + 1 + len > b.size) break
                val raw = utf8(b, p + 1, len).orEmpty()
                p += 1 + len
                i3++
                val split = raw.indexOf('-')
                if (split > 0) tele.putIfAbsent(raw.substring(0, split).trim(), raw.substring(split + 1).trim().uppercase())
            }
            return StationGroupData(names, groups, tele, invertKeys(groups), (b.size - p).coerceAtLeast(0))
        }

        /** 官方 `initSx` 之外没人用键的方向，这里只把「成员 → 组键」反建一份给查询用。 */
        private fun invertKeys(groups: Map<Int, IntArray>): Map<Int, Int> = buildMap {
            groups.forEach { (key, members) ->
                if (key < CITY_KEY_BASE) put(key, key)
                members.forEach { put(it, key) }
            }
        }

        private fun rotateToFirstNotLessThan(sorted: IntArray, key: Int): IntArray {
            if (sorted.isEmpty()) return sorted
            val at = sorted.indexOfFirst { it >= key }
            return if (at <= 0) sorted else sorted.copyOfRange(at, sorted.size) + sorted.copyOfRange(0, at)
        }

        /** 表1 的组名从 10000 开始编号（官方 `init` 里的 `int i = 10000`）。 */
        const val CITY_KEY_BASE = 10000

        /**
         * ime.dat：`[2B 大端条数]` + 每条 `[2B 大端字节长][「九宫格数字码 空格 站名连排」]`（真库 550 条、残留 0）。
         * **路路通全 APK 零引用**，属于输入法遗留索引；站名之间没有分隔符，所以只能原样存，不假装能切词。
         */
        fun parseIme(b: ByteArray): Map<String, String> {
            var p = 0
            val count = be(b, p, 2)
            if (count !in 1..20000) return emptyMap()
            p += 2
            val out = LinkedHashMap<String, String>(count)
            while (out.size < count && p + 2 <= b.size) {
                val len = be(b, p, 2)
                if (len !in 1..4000 || p + 2 + len > b.size) break
                val raw = utf8(b, p + 2, len).orEmpty()
                p += 2 + len
                val split = raw.indexOf(' ')
                if (split > 0) out[raw.substring(0, split)] = raw.substring(split + 1)
            }
            return out
        }

        /**
         * cdn.dat / zcdn.dat：一行一个 15 字符块 ID（真库 200 行 / 50 行）。
         * 它是 `LLTDns`/`UpgradeModel` 用的**在线调度主机列表**，与图定数据无关，只按行原样解出来。
         */
        fun parseBlockIds(b: ByteArray): List<String> =
            String(b, Charsets.ISO_8859_1).split('\n').map { it.trim('\r', ' ', '\n') }.filter { it.isNotEmpty() }
    }
}
