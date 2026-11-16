package com.crinfo.app.data.model

fun timeToMinutes(text: String): Int {
    val parts = text.split(":")
    if (parts.size != 2) return -1
    val h = parts[0].toIntOrNull() ?: return -1
    val m = parts[1].toIntOrNull() ?: return -1
    return h * 60 + m
}

/**
 * 车型标签。CR+数字才是复兴号（CR200/300/400/450），CRH 前缀是和谐号；
 * CR400AF-Z / CR400BF-AZ / CR400AF-BZ 为智能配置型。车票查询与车站大屏共用这一份判定。
 *
 * ⚠️ 别改去吃库里 extra.dat 表3/4/5 那三份「官方名单」。真库 20261108 实测：
 * 表2(333)、表3(795) 完全包含于表1(4085)（并集仍等于表1），表1 里能对上 jlb 车型的
 * 3536 趟有 3532 趟车型以 CR 开头 —— 「表1 ≈ 复兴号」这个方向成立；
 * 但反向差得远：全库车型以 CR 开头的车有 **10568** 趟，名单只收 4085 趟。
 * 换成名单口径会让六千多趟 CR 车丢「复」字，而三档到底分的是什么（智能型？编组等级？）
 * 库里和官方代码都没说。所以名单只是「存在这份数据」的记录，不作界面判定依据。
 */
fun trainTags(trainStyle: String, hasSleeper: Boolean = false): List<TrainTag> = buildList {
    if (Regex("^CR\\d").containsMatchIn(trainStyle)) add(TrainTag.FUXING)
    if (Regex("-[AB]?Z(_|$)").containsMatchIn(trainStyle)) add(TrainTag.SMART)
    if (hasSleeper) add(TrainTag.SLEEPER)
}

data class SeatOffer(
    val seat: Seat,
    val status: SeatStatus,
    val price: Double?,
)

/** 板面捞来的补充信息（车型与区间里程），拿不到时为空白。 */
data class TrainFacts(
    val style: String = "",
    val distanceKm: Int = 0,
)

/** 一次车票查询结果里的一行。 */
data class Trip(
    val trainCode: String,
    val trainNo: String,
    val date: String,
    val fromCode: String,
    val toCode: String,
    val fromName: String,
    val toName: String,
    val startCode: String,
    val endCode: String,
    val startName: String,
    val endName: String,
    val departTime: String,
    val arriveTime: String,
    val durationText: String,
    val canWait: Boolean,
    val seats: List<SeatOffer>,
    /** 车型（来自板面 `train_style`），余票接口不提供，取不到时为空串。 */
    val trainStyle: String = "",
    /** 区间里程（公里），0 表示未知。余票接口无里程列，由两站板面里程相减得到。 */
    val distanceKm: Int = 0,
) {
    val kind: TrainKind get() = TrainKind.of(trainCode)
    val departMinutes: Int get() = timeToMinutes(departTime)
    val durationMinutes: Int get() = timeToMinutes(durationText)

    /** 响应里没有跨天列，只能用「出发 + 历时」推算。 */
    val arrivesNextDay: Boolean
        get() = departMinutes >= 0 && durationMinutes >= 0 && departMinutes + durationMinutes >= 1440

    /**
     * 起售价只算**当前有票**的席别：某车次二等/无座售罄时，卡片不该再拿它们的票价标「起」。
     * 全部售罄时返回 null，界面改显「售罄」。
     */
    val lowestAvailablePrice: Double?
        get() = seats.filter { it.status.hasTicket }.mapNotNull { it.price }.minOrNull()

    /**
     * 席别数据里有没有任何「票量」信息。离线态只有席别种类（`SeatStatus.Unknown`），
     * 这时既不该说「售罄」，也不该被「只看有票」整列杀掉 —— 库压根没告诉我有没有票。
     */
    val ticketKnown: Boolean get() = seats.any { it.status != SeatStatus.Unknown }

    /** 有席别数据、且确实一个都买不到。板面模式无席别数据，不算售罄。 */
    val soldOut: Boolean get() = ticketKnown && lowestAvailablePrice == null

    val tags: List<TrainTag> get() = trainTags(trainStyle, seats.any { it.seat.isSleeper })

    val hasAnyTicket: Boolean get() = seats.any { it.status.hasTicket }

    fun seat(seat: Seat): SeatOffer? = seats.firstOrNull { it.seat == seat }
}
