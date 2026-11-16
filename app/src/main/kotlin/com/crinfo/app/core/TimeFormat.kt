package com.crinfo.app.core

/** 12306 各接口时间格式不统一：`0718` / `07:18` / `--` / `----` / 空串都出现过。 */
fun normalizeTime(raw: String?): String? {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty() || t.all { it == '-' }) return null
    t.takeIf { it.length == 5 && it[2] == ':' }?.let { return it }
    val digits = t.filter { it.isDigit() }
    return when (digits.length) {
        4 -> "${digits.take(2)}:${digits.takeLast(2)}"
        3 -> "0${digits.take(1)}:${digits.takeLast(2)}"
        else -> t
    }
}

fun blankToNull(raw: String?): String? {
    val t = raw?.trim().orEmpty()
    return if (t.isEmpty() || t.all { it == '-' }) null else t
}

/** 大屏里 "12小时19分" / "32分" 这类文本换算成分钟，失败返回 -1。 */
fun chineseDurationToMinutes(text: String?): Int {
    val t = text?.trim().orEmpty()
    if (t.isEmpty()) return -1
    val hour = Regex("(\\d+)\\s*小时").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val minute = Regex("(\\d+)\\s*分").find(t)?.groupValues?.get(1)?.toIntOrNull()
        ?: t.toIntOrNull() ?: return -1
    return hour * 60 + minute
}

/** 12306 的日期列有 `20261102` 与 `2026-11-02` 两种写法，统一成 ISO 供上层使用。 */
fun normalizeDate(raw: String): String {
    val t = raw.trim()
    return if (t.length == 8 && t.all { it.isDigit() }) "${t.take(4)}-${t.substring(4, 6)}-${t.takeLast(2)}" else t
}
