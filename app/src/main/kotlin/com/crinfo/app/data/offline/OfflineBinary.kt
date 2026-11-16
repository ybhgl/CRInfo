package com.crinfo.app.data.offline

/**
 * an.db 里反复出现的三种自定进制与两种整数宽度。
 * 每一个都对着路路通自己的解码函数抄（`DataMgr.OooOOo0/OooOOOO/OooOOo0`、`Schedule`、`ExtraDataMgr`），
 * 不是「看起来合理」的写法 —— 这张库里**同一张表的不同字段会用不同进制**，猜一个就整体错位。
 */
internal object OfflineBinary {

    /** `[b0][b1]` 大端，Java `readShort()` 的宽度（这里按无符号用，负值只出现在 sn.dat）。 */
    fun be(b: ByteArray, at: Int, len: Int): Int {
        if (at < 0 || len !in 1..4 || at + len > b.size) return -1
        var v = 0
        repeat(len) { i -> v = (v shl 8) or (b[at + i].toInt() and 0xFF) }
        return v
    }

    /** 大端 4 字节整数（plat.dat 的记录数与两个 ID、sp.dat 各表的记录数都用它）。 */
    fun be4(b: ByteArray, at: Int): Int = be(b, at, 4)

    /** 2 字节 **255 进制**：`b0*255 + b1`。库里绝大多数 ID 是这一种。 */
    fun id255(b: ByteArray, at: Int): Int {
        if (at < 0 || at + 2 > b.size) return -1
        return (b[at].toInt() and 0xFF) * 255 + (b[at + 1].toInt() and 0xFF)
    }

    /** 2 字节 **128 进制**：`b0*128 + b1`。只有票价族的两个站键用它。 */
    fun id128(b: ByteArray, at: Int): Int {
        if (at < 0 || at + 2 > b.size) return -1
        return (b[at].toInt() and 0xFF) * 128 + (b[at + 1].toInt() and 0xFF)
    }

    /**
     * 4 字节 **128 进制小端**，解出来是 `YYYYMMDD` 整数。
     * 已在 `t*.dat` 的 startDate/endDate 上全量验过（14889 条 100% 合法），日期字段一律走这里。
     */
    fun date128(b: ByteArray, at: Int): Int {
        if (at < 0 || at + 4 > b.size) return -1
        return (b[at].toInt() and 0xFF) +
            (b[at + 1].toInt() and 0xFF) * 128 +
            (b[at + 2].toInt() and 0xFF) * 16384 +
            (b[at + 3].toInt() and 0xFF) * 2097152
    }

    /** Java `DataInputStream.readShort()`：带符号，sn.dat 的组键与成员 ID 按它读。 */
    fun signedShort(b: ByteArray, at: Int): Int {
        val v = be(b, at, 2)
        return if (v >= 0x8000) v - 0x10000 else v
    }

    fun u8(b: ByteArray, at: Int): Int = if (at < 0 || at >= b.size) -1 else b[at].toInt() and 0xFF

    fun utf8(b: ByteArray, at: Int, len: Int): String? {
        if (at < 0 || len < 0 || at + len > b.size) return null
        return runCatching { String(b, at, len, Charsets.UTF_8) }.getOrNull()
    }

    /**
     * 席别位图占几个 2 字节值 —— 路路通 `DataMgr.OooO00o` 的权重表，
     * bit0=1 bit1=1 bit2=3 bit3=2 bit4=1 bit5=1（bit2 是硬卧上中下、bit3 是软卧上下，所以不是 1）。
     * 位含义同 `Consts.XW_TYPE_*`：硬座1 软座2 硬卧4 软卧8 一等16 二等32（bit6=64 动卧不在本权重内）。
     */
    fun seatSlots(flag: Int): Int {
        var v = 0
        if (flag and 1 != 0) v += 1
        if (flag and 2 != 0) v += 1
        if (flag and 4 != 0) v += 3
        if (flag and 8 != 0) v += 2
        if (flag and 16 != 0) v += 1
        if (flag and 32 != 0) v += 1
        return v
    }
}
