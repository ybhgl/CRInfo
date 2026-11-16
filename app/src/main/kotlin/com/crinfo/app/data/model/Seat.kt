package com.crinfo.app.data.model

/**
 * 席别。[field] 是 leftTicket 结果行里余票所在的下标，[priceCode] 是 seat_types / yp_info 里的席别码。
 * 两者由 2026-10-31 真实响应逐列比对得出（北京南→上海虹桥 54 趟，含高铁/动车卧铺/普速）。
 * 注意 20、23、28 三列被多个席别共用，必须靠 seat_types 里的席别码消歧。
 */
enum class Seat(val label: String, val field: Int, val priceCode: Char) {
    BUSINESS("商务座", 32, '9'),
    SPECIAL_FIRST("特等座", 20, 'P'),
    PREMIUM_FIRST("优选一等", 20, 'D'),
    FIRST("一等座", 31, 'M'),
    SECOND("二等座", 30, 'O'),
    PREMIUM_SOFT_SLEEPER("高级软卧", 21, 'A'),
    FIRST_SLEEPER("一等卧", 23, 'I'),
    SOFT_SLEEPER("软卧", 23, '4'),
    MOVING_SLEEPER("动卧", 33, 'F'),
    SECOND_SLEEPER("二等卧", 28, 'J'),
    HARD_SLEEPER("硬卧", 28, '3'),
    SOFT_SEAT("软座", 24, '6'),
    HARD_SEAT("硬座", 29, '1'),
    NO_SEAT("无座", 26, 'W'),
    ;

    /** 卧铺席别，用于卡片上的「铺」标签。 */
    val isSleeper: Boolean
        get() = this == PREMIUM_SOFT_SLEEPER || this == FIRST_SLEEPER || this == SOFT_SLEEPER ||
            this == MOVING_SLEEPER || this == SECOND_SLEEPER || this == HARD_SLEEPER
}

/**
 * 车次特殊标签。复/智 来自板面 `train_style`（实测取值如 CR400AF-Z_578、CR400BF-AZ、CR200J、CRH380B），
 * 铺 来自席别数据。静（静音车厢）目前无可验证数据源：leftTicket 无车型列，
 * 而 `train_flag` 在京沪静音试点车 G1 上为空，语义自相矛盾，故不下结论。
 */
enum class TrainTag(val label: String) {
    FUXING("复"),
    SMART("智"),
    SLEEPER("铺"),
    SILENT("静"),
}

sealed interface SeatStatus {
    data object SoldOut : SeatStatus

    /**
     * 库里只说了这趟车**有**这一席别，没给余票数量（离线态 `xw.dat` 席别位图）。
     * 界面按无票的非高亮样式显示「无」，但 [Trip.soldOut] 与「只看有票」都不许把它当成「卖完了」——
     * 「不知道」和「没票」是两件事，混成一件事会让离线列表看起来像全站售罄。
     */
    data object Unknown : SeatStatus
    data object Plenty : SeatStatus
    data class Count(val remaining: Int) : SeatStatus

    val hasTicket: Boolean
        get() = this == Plenty || (this is Count && remaining > 0)

    fun display(): String = when (this) {
        SoldOut -> "无"
        Unknown -> "无"
        Plenty -> "有"
        is Count -> remaining.toString()
    }

    companion object {
        fun parse(raw: String): SeatStatus = when {
            // 必须先排空串："".all { isDigit } 恒为 true，会让 "".toInt() 抛 NumberFormatException
            raw.isEmpty() || raw == "*" || raw == "-" -> SoldOut
            raw == "有" -> Plenty
            // 0 张与售罄同义，归一后界面统一显示「无」而不是突兀的「0」
            raw == "0" -> SoldOut
            raw.all { it.isDigit() } -> Count(raw.toInt())
            else -> SoldOut
        }
    }
}

enum class TrainKind(val label: String, val code: String) {
    G("高铁", "G"), D("动车", "D"), C("城际", "C"), Z("直达特快", "Z"), T("特快", "T"), K("快速", "K"),
    L("临客", "L"), Y("旅游", "Y"), S("市郊", "S"), OTH("普快/其他", "");

    /** 芯片文案：OTH 没有可展示的前缀字母，只显示中文名。 */
    val chipLabel: String get() = if (code.isEmpty()) label else "$label $code"

    companion object {
        fun of(trainCode: String): TrainKind {
            val c = trainCode.firstOrNull()?.uppercaseChar() ?: return OTH
            return entries.firstOrNull { it.name[0] == c } ?: OTH
        }
    }
}
