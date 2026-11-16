package com.crinfo.app.data.remote

import com.crinfo.app.core.net.ApiException
import com.crinfo.app.core.net.Http
import com.crinfo.app.data.model.Trip
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
private data class LeftTicketResponse(val data: LeftTicketData? = null)

@Serializable
private data class LeftTicketData(
    val result: List<String> = emptyList(),
    val map: Map<String, String> = emptyMap(),
)

object LeftTicketApi {
    private const val BASE = "https://kyfw.12306.cn/otn"
    private const val INIT_URL = "$BASE/leftTicket/init"
    private const val SESSION_TTL_MS = 25 * 60 * 1000L

    /** 查询用的 URL 后缀（queryG/queryA/…）会轮换，只能从 init 页面里取。 */
    private val URL_KEY = Regex("""CLeftTicketUrl\s*=\s*['"]([^'"]+)['"]""")

    private val json = Json { ignoreUnknownKeys = true }

    /** 12306 预售期口径会变，按当前 15 天做本地兜底校验。 */
    const val PRESALE_DAYS = 15

    @Volatile
    private var queryPath: String? = null

    @Volatile
    private var sessionAt: Long = 0

    /**
     * @param date yyyy-MM-dd
     * @param nameOf 电报码 → 站名的兜底解析（离线车站表）
     */
    suspend fun query(
        from: String,
        to: String,
        date: String,
        nameOf: (String) -> String?,
    ): List<Trip> {
        var lastError: Exception? = null
        // 302 有两种成因：会话过期（重握手可救）与超出预售期（重试无用），各试一次后按日期判定
        repeat(2) { attempt ->
            try {
                val path = session(date, from, to, force = attempt > 0)
                val raw = Http.get(
                    "$BASE/$path?leftTicketDTO.train_date=$date" +
                        "&leftTicketDTO.from_station=$from&leftTicketDTO.to_station=$to" +
                        "&purpose_codes=ADULT",
                    referer = INIT_URL,
                )
                if (raw.isBlank()) throw ApiException("余票查询返回空响应", 302)
                val dto = try {
                    json.decodeFromString(LeftTicketResponse.serializer(), raw)
                } catch (e: SerializationException) {
                    throw ApiException("余票查询响应格式异常", 0)
                }
                val data = dto.data ?: throw ApiException("余票查询无数据", 0)
                return LeftTicketParser.parse(data.result) { code -> data.map[code] ?: nameOf(code) }
            } catch (e: Exception) {
                lastError = e
                queryPath = null
                if (e is ApiException && e.httpCode != 302 && e.httpCode != 0) throw e
            }
        }
        if (isOutOfPresale(date)) throw ApiException("$date 超出预售期（约 $PRESALE_DAYS 天）", 0)
        throw lastError ?: ApiException("余票查询失败")
    }

    private suspend fun session(date: String, from: String, to: String, force: Boolean): String {
        val cached = queryPath
        if (!force && cached != null && System.currentTimeMillis() - sessionAt < SESSION_TTL_MS) return cached
        val page = Http.get(
            "$INIT_URL?linktypeid=dc&date=$date&fs=$from&ts=$to&flag=N,N,Y",
            referer = "https://www.12306.cn/",
        )
        val path = URL_KEY.find(page)?.groupValues?.get(1)?.trim('/')
            ?: throw ApiException("未能解析 CLeftTicketUrl", 0)
        queryPath = path
        sessionAt = System.currentTimeMillis()
        return path
    }

    private fun isOutOfPresale(date: String): Boolean {
        val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
        return day.isAfter(LocalDate.now().plusDays(PRESALE_DAYS.toLong()))
    }
}
