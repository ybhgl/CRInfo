package com.crinfo.app.data.offline

import com.crinfo.app.data.offline.OfflineBinary.be4
import com.crinfo.app.data.offline.OfflineBinary.id255
import com.crinfo.app.data.offline.OfflineBinary.u8
import com.crinfo.app.data.offline.OfflineBinary.utf8

/**
 * sp.dat 表1：车次的**担当企业**（带生效日期与开行规则）。
 * `ruleIndex` 指向 t.rule，日期是 `YYYYMMDD` 整数 —— 官方 `SpDataMgr.getQiYe` 就是
 * 「按车次号取全部记录，逐条用 `Rule.outOfDate(起, 止, 车号, ruleIndex)` 过滤，第一条命中的那条」。
 */
data class TrainDuty(
    val trainId: Int,
    val startDate: Int,
    val endDate: Int,
    val ruleIndex: Int,
    val enterpriseCode: Int,
)

/** sp.dat 表4：车站归属（担当企业代码 + 省 ID）。 */
data class StationBelong(val enterpriseCode: Int, val provinceId: Int)

/**
 * sp.dat 的六张顺序表。表头宽度不统一（表1/2/4/5/6 是 4 字节大端条数，表3 是 2 字节 255 进制），
 * 这是照 `SpDataMgr.OooOoO0()` 逐行抄的，别统一成一种。
 *
 * [trainNos] 是本轮逆向里最值钱的一张：**下标即车次 ID，值就是 12306 的 12 位 trainNo**
 * （真库 14889 条、长度分布只有 12 这一种）。`routes.dat` §2 那 14766 个键 100% 落在它里面，
 * 所以官方径路从此可以按车次 ID 精确命中，不再靠「显示车次号后恰好剩 2 个字符」的锚定猜测。
 */
data class SpData(
    val declaredDuties: Int,
    val duties: List<TrainDuty>,
    val trainNos: List<String>,
    val provinceNames: Map<Int, String>,
    val belongs: Map<Int, StationBelong>,
    val locationById: Map<Int, String>,
    val locationByName: Map<String, String>,
    /** 走完六张表剩下的字节数。验收用：真库实测为 0，不为 0 说明走步错了。 */
    val residual: Int,
) {
    /** 车次 ID → 12 位 trainNo（越界给空串，调用方不必再判空）。 */
    fun trainNoOf(trainId: Int): String? = trainNos.getOrNull(trainId)?.takeIf { it.isNotBlank() }

    /**
     * 车次 ID → 担当企业代码（按查询日过滤）。库里这张表只有零星几十条（临客/调整），
     * **常态担当在 extra.dat 表2**，所以解不出就返回 null，别当成「无担当」。
     */
    fun dutyCode(trainId: Int, date: Int): Int? = duties.firstOrNull {
        it.trainId == trainId && date in it.startDate..it.endDate
    }?.enterpriseCode

    companion object {
        /** 库里没装 sp.dat 时的空值（各表都空、residual 0），别让调用方各自拼参数。 */
        val EMPTY = SpData(0, emptyList(), emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), 0)

        /**
         * 走步一次解码。任何一张表结构对不上就**停在当前表**并把已解出的部分带出去 ——
         * 六张表是顺序关系，后面错位不能把前面已经对上的数据一起废掉（与 an.db 其他表同一口径）。
         *
         * 所有循环都按 `repeat(声明条数)` 走，**绝不用 `map.size < count` 当条件**：
         * 真库里这些表的键会重复，size 不涨就会让循环一路读穿到下一张表
         * （sp.dat 因此一度残留 13 万字节，sn.dat 残留 368 字节）。
         */
        fun parse(b: ByteArray): SpData {
            var p = 0
            val n1 = be4(b, p)
            if (n1 !in 0..200000) return SpData(0, emptyList(), emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), b.size)
            p += 4
            val duties = ArrayList<TrainDuty>(n1)
            while (duties.size < n1 && p + 16 <= b.size) {
                duties.add(
                    TrainDuty(
                        trainId = be4(b, p),
                        startDate = be4(b, p + 4),
                        endDate = be4(b, p + 8),
                        ruleIndex = id255(b, p + 12),
                        enterpriseCode = id255(b, p + 14),
                    ),
                )
                p += 16
            }
            val n2 = be4(b, p)
            if (n2 !in 0..200000) return SpData(n1, duties, emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), b.size - p)
            p += 4
            val trainNos = ArrayList<String>(if (n2 in 1..60000) n2 else 0)
            var i2 = 0
            while (i2 < n2 && p + 1 <= b.size) {
                val len = u8(b, p)
                if (len !in 1..40 || p + 1 + len > b.size) break
                trainNos.add(utf8(b, p + 1, len).orEmpty())
                p += 1 + len
                i2++
            }
            val n3 = id255(b, p)
            if (n3 !in 0..2000) return SpData(n1, duties, trainNos, emptyMap(), emptyMap(), emptyMap(), emptyMap(), b.size - p)
            p += 2
            val provinces = HashMap<Int, String>(if (n3 > 0) n3 else 1)
            var i3 = 0
            while (i3 < n3 && p + 3 <= b.size) {
                val id = id255(b, p)
                val len = u8(b, p + 2)
                if (len !in 1..80 || p + 3 + len > b.size) break
                provinces[id] = utf8(b, p + 3, len).orEmpty()
                p += 3 + len
                i3++
            }
            val n4 = be4(b, p)
            if (n4 !in 0..200000) return SpData(n1, duties, trainNos, provinces, emptyMap(), emptyMap(), emptyMap(), b.size - p)
            p += 4
            val belongs = HashMap<Int, StationBelong>(if (n4 in 1..60000) n4 else 0)
            var i4 = 0
            while (i4 < n4 && p + 8 <= b.size) {
                // 站 ID 会重复，所以 putIfAbsent + 计数器：既不让 map.size 卡住走步，也不覆盖先解出的那条
                belongs.putIfAbsent(be4(b, p), StationBelong(id255(b, p + 4), id255(b, p + 6)))
                p += 8
                i4++
            }
            val n5 = be4(b, p)
            if (n5 !in 0..200000) return SpData(n1, duties, trainNos, provinces, belongs, emptyMap(), emptyMap(), b.size - p)
            p += 4
            val byId = HashMap<Int, String>(if (n5 in 1..60000) n5 else 0)
            var i5 = 0
            while (i5 < n5 && p + 3 <= b.size) {
                val id = id255(b, p)
                val len = u8(b, p + 2)
                // 地址串里有「新疆维吾尔自治区 巴音郭楞…」这类长文本，上限给到 200 字节
                if (len !in 1..200 || p + 3 + len > b.size) break
                byId.putIfAbsent(id, utf8(b, p + 3, len).orEmpty())
                p += 3 + len
                i5++
            }
            val n6 = be4(b, p)
            if (n6 !in 0..200000) return SpData(n1, duties, trainNos, provinces, belongs, byId, emptyMap(), b.size - p)
            p += 4
            val byName = HashMap<String, String>(if (n6 in 1..60000) n6 else 0)
            var i6 = 0
            while (i6 < n6 && p + 1 <= b.size) {
                val len = u8(b, p)
                if (len !in 1..200 || p + 1 + len > b.size) break
                val raw = utf8(b, p + 1, len).orEmpty()
                p += 1 + len
                val split = raw.indexOf(',')
                if (split > 0) byName.putIfAbsent(raw.substring(0, split).trim(), raw.substring(split + 1).trim())
                i6++
            }
            return SpData(n1, duties, trainNos, provinces, belongs, byId, byName, (b.size - p).coerceAtLeast(0))
        }
    }
}
