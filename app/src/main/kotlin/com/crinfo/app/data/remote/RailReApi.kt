package com.crinfo.app.data.remote

import com.crinfo.app.core.net.Http
import com.crinfo.app.data.model.EmuFallback
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * rail.re 是 Arnie97 维护的第三方动车组交路服务，这里**只作兜底**：12306 的车底详情接口
 * 空响应时，至少把车底号补上。它的 `/img/<车次>.png` 是按车次生成的运用图静态资源，
 * 既不是这辆车的照片，也与当次解析出的车底号可能不一致，所以不再取图。
 * 第三方服务没有 SLA，失败一律由调用方静默降级，绝不让车次查询整页失败。
 */
object RailReApi {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class EmuDto(val train_no: String = "", val emu_no: String = "")

    /** 补连字符的三条规则照搬交路脚本，`CR400BFA5092` 这类写法人读不懂。 */
    private val emuNoRules = listOf(
        Regex("^(\\w+)(\\d{4})$"),
        Regex("^(\\w+A)(A-\\d{4})$"),
        Regex("^(\\w+F)(\\w+-\\d{4})$"),
    )

    suspend fun emuFor(trainCode: String): EmuFallback? {
        val body = Http.get("https://api.rail.re/train/,$trainCode")
        val info = json.decodeFromString<List<EmuDto>>(body)
            .firstOrNull { it.emu_no.isNotBlank() && it.train_no.equals(trainCode, ignoreCase = true) }
            ?: return null
        return EmuFallback(emuNo = formatEmuNo(info.emu_no))
    }

    private fun formatEmuNo(raw: String): String {
        emuNoRules.forEach { rule ->
            val match = rule.matchEntire(raw)
            if (match != null) return "${match.groupValues[1]}-${match.groupValues[2]}"
        }
        return raw
    }
}
