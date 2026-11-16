package com.crinfo.app.data.remote

import com.crinfo.app.core.normalizeDate
import com.crinfo.app.data.model.Seat
import com.crinfo.app.data.model.SeatOffer
import com.crinfo.app.data.model.SeatStatus
import com.crinfo.app.data.model.Trip

/**
 * leftTicket 的 result 是位置型 `|` 分隔字符串，没有字段名。列号见下方常量，
 * 全部由真实响应比对得出；12306 若调整列序，由 LeftTicketParserTest 的 fixture 第一时间抓到。
 */
object LeftTicketParser {
    private const val COL_TRAIN_NO = 2
    private const val COL_TRAIN_CODE = 3
    private const val COL_START_CODE = 4
    private const val COL_END_CODE = 5
    private const val COL_FROM_CODE = 6
    private const val COL_TO_CODE = 7
    private const val COL_DEPART = 8
    private const val COL_ARRIVE = 9
    private const val COL_DURATION = 10
    private const val COL_CAN_WAIT = 11
    private const val COL_DATE = 13
    private const val COL_SEAT_TYPES = 34
    private const val COL_YP_INFO = 39
    private const val MIN_COLUMNS = 40

    fun parse(result: List<String>, nameOf: (String) -> String?): List<Trip> =
        result.mapNotNull { row -> parseRow(row.split("|"), nameOf) }

    internal fun parseRow(f: List<String>, nameOf: (String) -> String?): Trip? {
        if (f.size < MIN_COLUMNS) return null
        val trainCode = f[COL_TRAIN_CODE].trim()
        if (trainCode.isEmpty()) return null

        val seatTypes = f[COL_SEAT_TYPES]
        val prices = zipPrices(seatTypes, f[COL_YP_INFO])
        val seats = Seat.entries
            .filter { seat -> seatTypes.isEmpty() || seatTypes.contains(seat.priceCode) }
            .map { seat ->
                SeatOffer(
                    seat = seat,
                    status = SeatStatus.parse(f.getOrElse(seat.field) { "" }),
                    price = prices[seat.priceCode],
                )
            }

        return Trip(
            trainCode = trainCode,
            trainNo = f[COL_TRAIN_NO],
            date = normalizeDate(f[COL_DATE]),
            fromCode = f[COL_FROM_CODE],
            toCode = f[COL_TO_CODE],
            fromName = nameOf(f[COL_FROM_CODE]) ?: f[COL_FROM_CODE],
            toName = nameOf(f[COL_TO_CODE]) ?: f[COL_TO_CODE],
            startCode = f[COL_START_CODE],
            endCode = f[COL_END_CODE],
            startName = nameOf(f[COL_START_CODE]) ?: f[COL_START_CODE],
            endName = nameOf(f[COL_END_CODE]) ?: f[COL_END_CODE],
            departTime = f[COL_DEPART],
            arriveTime = f[COL_ARRIVE],
            durationText = f[COL_DURATION],
            canWait = f[COL_CAN_WAIT] == "Y",
            seats = seats,
        )
    }

    /**
     * seat_types 形如 `90M0O0W0`（席别码 + 1 位标志，成对）；
     * yp_info 形如 `9174400005M083600021...`（10 字符一组：席别码 + 5 位「票价×10」+ 4 位控制）。
     * 两者按位置一一对应，所以无座票价取它在 seat_types 中对应的那一组，而非组内自带的码——
     * 实测 G531 无座 498 = 二等座 498，1461 无座 156.5 = 硬座 156.5。
     */
    internal fun zipPrices(seatTypes: String, ypInfo: String): Map<Char, Double> {
        val codes = ArrayList<Char>()
        var i = 0
        while (i < seatTypes.length) {
            codes.add(seatTypes[i])
            i += 2
        }
        val out = LinkedHashMap<Char, Double>()
        var group = 0
        while (group + 10 <= ypInfo.length) {
            val chunk = ypInfo.substring(group, group + 10)
            val code = codes.getOrNull(group / 10) ?: chunk[0]
            val price = chunk.substring(1, 6).toIntOrNull()
            if (price != null && price > 0) out[code] = price / 10.0
            group += 10
        }
        return out
    }
}
