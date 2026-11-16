package com.crinfo.app.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 列车此刻在时刻表上的位置。
 *
 * @param reachedIndex 已完成服务（到发均已过）的最后一站的行下标
 * @param atStation 车是否正停在该站；false 表示处在两站之间的区间里
 */
data class TrainProgress(
    val reachedIndex: Int,
    val atStation: Boolean,
)

/**
 * 一律折算成「epoch 天数 ×1440 + 当日分钟」这类绝对分钟再比较：
 * 始发站的 `dayDiff` 恒为 0，前一日夜发的车跨零点后若只比时钟会算出负数。
 * 注意不能用 `LocalDate.until()` 取天数——它返回日历 Period，`.days` 只是零头天数。
 */
fun trainProgress(
    rows: List<ScheduleRow>,
    date: String,
    now: LocalDateTime = LocalDateTime.now(),
): TrainProgress? {
    val base = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    if (rows.isEmpty()) return null
    val nowMinutes = absoluteMinutes(base, now)
    val origin = servedMinutes(rows.first(), base) ?: return null
    // 还没到始发时刻：算「未出发」，界面不画进度
    if (nowMinutes < origin) return null

    val last = rows.lastIndex
    var reached = 0
    var atStation = false
    for (i in 0..last) {
        // 到站判据用「到达时刻」，不是发车时刻：否则停站期间会被算成还没到这一站
        val arrivedAt = arriveMinutes(rows[i], base) ?: servedMinutes(rows[i], base) ?: continue
        if (nowMinutes < arrivedAt) continue
        reached = i
        // 停站中 = 已到但未走；终到站只有到达，到点即为终点
        val depart = departMinutes(rows[i], base)
        atStation = depart == null || nowMinutes < depart
    }
    return TrainProgress(reached, atStation)
}

/** 完成服务的时刻：有出发取出发，只有到达（终到站）取到达。 */
private fun servedMinutes(row: ScheduleRow, base: LocalDate): Int? {
    val stop = row.stop
    val depart = departMinutes(row, base)
    if (depart != null) return depart
    return arriveMinutes(row, base)
}

private fun departMinutes(row: ScheduleRow, base: LocalDate): Int? {
    val time = row.stop.departTime ?: return null
    val clock = parseClock(time) ?: return null
    return absoluteMinutes(base, row.stop.dayDiff, clock) + (row.live?.departDelayMinutes ?: 0)
}

private fun arriveMinutes(row: ScheduleRow, base: LocalDate): Int? {
    val time = row.stop.arriveTime ?: return null
    val clock = parseClock(time) ?: return null
    return absoluteMinutes(base, row.stop.dayDiff, clock) + (row.live?.arriveDelayMinutes ?: 0)
}

private fun parseClock(time: String): LocalTime? {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime.of(hour, minute)
}

private fun absoluteMinutes(base: LocalDate, stamp: LocalDateTime): Int =
    stamp.toLocalDate().toEpochDay().toInt() * 1440 + stamp.hour * 60 + stamp.minute

private fun absoluteMinutes(base: LocalDate, dayDiff: Int, clock: LocalTime): Int =
    base.plusDays(dayDiff.toLong()).toEpochDay().toInt() * 1440 + clock.hour * 60 + clock.minute
