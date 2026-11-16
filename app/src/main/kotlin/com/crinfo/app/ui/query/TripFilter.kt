package com.crinfo.app.ui.query

import com.crinfo.app.data.model.Seat
import com.crinfo.app.data.model.TrainKind
import com.crinfo.app.data.model.Trip

enum class TimeBucket(val label: String, val from: Int, val to: Int) {
    DAWN("0:00-6:00", 0, 360),
    MORNING("6:00-12:00", 360, 720),
    AFTERNOON("12:00-18:00", 720, 1080),
    EVENING("18:00-24:00", 1080, 1441),
}

enum class TripSort(val label: String) {
    DEPART("出发最早"),
    ARRIVE("到达最早"),
    DURATION("耗时最短"),
    PRICE("票价最低"),
}

/** 车票查询的筛选与排序条件。空集合语义为「不限」。 */
data class TripFilter(
    val kinds: Set<TrainKind> = emptySet(),
    val buckets: Set<TimeBucket> = emptySet(),
    /** 模糊查询扩出的出发站电报码，空=不限出发站。 */
    val fromStations: Set<String> = emptySet(),
    /** 模糊查询扩出的到达站电报码，空=不限到达站。 */
    val toStations: Set<String> = emptySet(),
    val onlyOrigin: Boolean = false,
    val onlyTerminal: Boolean = false,
    val onlyAvailable: Boolean = false,
    val seat: Seat? = null,
    val sort: TripSort = TripSort.DEPART,
) {
    val activeCount: Int
        get() = listOf(
            kinds.isNotEmpty(),
            buckets.isNotEmpty(),
            fromStations.isNotEmpty(),
            toStations.isNotEmpty(),
            onlyOrigin,
            onlyTerminal,
            onlyAvailable,
            seat != null,
        ).count { it }

    val isDefault: Boolean get() = activeCount == 0 && sort == TripSort.DEPART

    fun apply(trips: List<Trip>): List<Trip> {
        val filtered = trips.filter { trip ->
            if (kinds.isNotEmpty() && trip.kind !in kinds) return@filter false
            if (buckets.isNotEmpty() && buckets.none { trip.departMinutes in it.from until it.to }) return@filter false
            if (fromStations.isNotEmpty() && trip.fromCode !in fromStations) return@filter false
            if (toStations.isNotEmpty() && trip.toCode !in toStations) return@filter false
            if (onlyOrigin && trip.fromCode != trip.startCode) return@filter false
            if (onlyTerminal && trip.toCode != trip.endCode) return@filter false
            // 席别筛选两种口径都成立：联网看「该席别有票」，离线（只有席别种类、不知道票量）看「该席别存在」。
            // 「只看有票」则只在有票量数据时才参与过滤 —— 离线态它会在筛选弹窗里禁用，
            // 这里再挡一道：否则一开就把离线结果整屏杀空，像是这两站之间没车。
            if (seat != null) {
                if (if (trip.ticketKnown) trip.seat(seat)?.status?.hasTicket != true else trip.seat(seat) == null) {
                    return@filter false
                }
            } else if (onlyAvailable && trip.ticketKnown && !trip.hasAnyTicket) {
                return@filter false
            }
            true
        }
        return when (sort) {
            TripSort.DEPART -> filtered.sortedBy { it.departMinutes }
            TripSort.ARRIVE -> filtered.sortedWith(
                compareBy<Trip> { it.arriveDayDiff() }.thenBy { it.arriveMinutes() },
            )
            TripSort.DURATION -> filtered.sortedBy { it.durationMinutes }
            TripSort.PRICE -> filtered.sortedWith(
                // 与卡片角标同口径：按「有票席别的最低价」排，全无票的沉底
                compareBy<Trip> { it.lowestAvailablePrice ?: Double.MAX_VALUE }.thenBy { it.departMinutes },
            )
        }
    }

    companion object {
        val AllKinds = TrainKind.entries.toList()
    }
}

private fun Trip.arriveDayDiff(): Int = if (arrivesNextDay) 1 else 0

private fun Trip.arriveMinutes(): Int = com.crinfo.app.data.model.timeToMinutes(arriveTime)
