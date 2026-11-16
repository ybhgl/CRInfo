package com.crinfo.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinfo.app.data.Station
import com.crinfo.app.data.model.RelayLeg
import com.crinfo.app.data.model.SaleStart
import com.crinfo.app.data.model.StationProfile
import com.crinfo.app.data.model.StationServiceGroup
import com.crinfo.app.data.model.dayNightSummary
import com.crinfo.app.data.model.groupRelaysByMinutes
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 车站信息弹窗：标题是车站全名、底部按钮钉住、中间内容自己滚（与筛选弹窗同一套三段式）。
 *
 * **点开必亮**：`show` 只认用户点没点、不认数据到没到——VM 先用本页已有字段同步建一份档案，
 * 远程五项（起售/换乘/天气/简介/服务）后补，未就位时那五项写「加载中…」。
 * 同城站点与站内服务条目都是**顿号流式文本**（chip 的内边距在小屏几乎一行一个，故不用），
 * 当前站靠主题色+字重凸显。层级两档：小节标题 15sp 粗（与字段标签同灰）+ 正文与条目 13sp；
 * 缺失项一律灰字「暂无…」。离线态拿不到的项整块不显示，不留「需联网」的骨架行。
 */
@Composable
fun StationInfoDialog(
    show: Boolean,
    profile: StationProfile?,
    offline: Boolean,
    onDismiss: () -> Unit,
    onPickStation: (Station) -> Unit,
) {
    // 弹窗常驻组合树，每次打开都回到「站点信息」；离线态简介整块没有，初值也不能是简介页
    var introTab by remember(show) { mutableStateOf(false) }
    val context = LocalContext.current
    val activeIntro = introTab && !offline

    OverlayDialog(show = show, title = profile?.displayName.orEmpty(), onDismissRequest = onDismiss) {
        val data = profile
        Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            // fill = false：内容装得下时按内容贴合，不把弹窗顶到高度上限
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
            ) {
                when {
                    data == null -> LoadingLine()
                    activeIntro -> IntroSection(data)
                    else -> InfoSection(data, offline, onPickStation)
                }
            }
            if (data != null) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    if (!offline) {
                        Button(
                            onClick = { introTab = !introTab },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                        ) {
                            Text(
                                text = if (activeIntro) "站点信息" else "车站简介",
                                fontSize = 15.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Button(
                        onClick = { openStationMap(context, data) },
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = MiuixTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(
                            text = "查看地图",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/* ---------------- 站点信息 ---------------- */

@Composable
private fun InfoSection(data: StationProfile, offline: Boolean, onPickStation: (Station) -> Unit) {
    val colors = MiuixTheme.colorScheme
    InfoRow("电报码", data.telecode)
    val bureau = listOf(data.bureau, data.stationSegment).filter { it.isNotBlank() }.distinct().joinToString(" · ")
    InfoRow("隶属路局", bureau)
    InfoRow("地址", data.displayAddress)

    Spacer(modifier = Modifier.height(12.dp))
    BigTitle("同城站点")
    if (data.saleTimes.isEmpty()) {
        Text(text = "暂无同城站点数据", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
    } else {
        CityStationFlow(data = data, offline = offline, onPick = onPickStation)
    }

    if (!offline) {
        Spacer(modifier = Modifier.height(12.dp))
        BigTitle("起售时间")
        if (!data.remoteReady) {
            LoadingLine()
        } else {
            val sale = data.ownSaleTime
            Text(
                text = sale?.let { "$it 起售" } ?: "暂无起售时间数据",
                fontSize = 13.sp,
                color = if (sale != null) colors.onSurface else colors.onSurfaceVariantSummary,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        BigTitle("换乘预留")
        if (!data.remoteReady) LoadingLine() else RelaySection(data.relays)
    }
}

@Composable
private fun LoadingLine() {
    Text(text = "加载中…", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

/**
 * 同城站点＝顿号分隔的流式文本。每段站名各自可点、直接切到该站；
 * 当前站换成主题色加粗——凸显用颜色和字重，不用 chip 底色（小屏一行塞不下几个）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CityStationFlow(data: StationProfile, offline: Boolean, onPick: (Station) -> Unit) {
    val colors = MiuixTheme.colorScheme
    FlowRow(
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        data.saleTimes.forEachIndexed { index, row ->
            // 电报码查不到的行（接口多给、本地表也没收录）点了没意义，不给点击语义
            val station = row.code.takeIf { it.isNotBlank() }?.let { Station(row.name, it, "", "", data.city) }
            Text(
                text = cityLabel(row, offline),
                fontSize = 13.sp,
                fontWeight = if (row.name == data.stationName) FontWeight.Medium else FontWeight.Normal,
                color = if (row.name == data.stationName) colors.primary else colors.onSurface,
                modifier = if (station == null) Modifier
                else Modifier.clickable { onPick(station) }.padding(horizontal = 1.dp, vertical = 2.dp),
            )
            if (index < data.saleTimes.lastIndex) {
                Text(text = "、", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
            }
        }
    }
}

/** 「北京南 VNP 12:45起售」。起售时刻随远程五项一起到，未到之前这半句不占位。 */
private fun cityLabel(row: SaleStart, offline: Boolean): String = buildString {
    append(row.name)
    if (row.code.isNotBlank()) append(" ${row.code}")
    if (!offline && row.time.isNotBlank()) append(" ${row.time}起售")
}

/** 换乘分钟按档分组：同城各站预留值高度重复（北京几乎全是 180），逐站列十几行不如并成一行。 */
@Composable
private fun RelaySection(relays: List<RelayLeg>) {
    val colors = MiuixTheme.colorScheme
    val groups = remember(relays) { groupRelaysByMinutes(relays) }
    if (groups.isEmpty()) {
        Text(text = "暂无换乘信息", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
        return
    }
    groups.forEach { (minutes, legs) ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(
                text = "≥ $minutes 分钟",
                fontSize = 13.sp,
                color = colors.primary,
                modifier = Modifier.width(84.dp),
            )
            Text(text = legs.joinToString("、") { it.toName }, fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
        }
    }
    Text(
        text = "同城车站间换乘建议预留以上时间",
        fontSize = 11.sp,
        color = colors.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/* ---------------- 车站简介 ---------------- */

@Composable
private fun IntroSection(data: StationProfile) {
    val colors = MiuixTheme.colorScheme
    BigTitle("车站简介")
    if (!data.remoteReady) {
        LoadingLine()
    } else {
        val intro = data.intro
        if (intro == null || intro.isBlank) {
            Text(text = "暂无简介数据", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
        } else {
            intro.paragraphs.forEach { paragraph ->
                Text(
                    text = paragraph,
                    fontSize = 13.sp,
                    color = colors.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            // 简介图与车底图同一个 CDN，基址沿用 CarImages，别在界面里拼裸 URL
            intro.pictureNames.forEach { name ->
                RemotePicture(
                    url = CarImages.urlOf(name),
                    contentDescription = "${data.stationName}简介图",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    BigTitle("天气")
    if (!data.remoteReady) {
        LoadingLine()
    } else {
        val weather = data.weather
        if (weather == null) {
            Text(text = "暂无天气数据", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
        } else {
            val main = listOf(
                weather.phenomena.takeIf { it.isNotBlank() },
                weather.temperature.takeIf { it.isNotBlank() }?.let { "${it}℃" },
            ).filterNotNull().joinToString(" ")
            InfoRow("实况", main)
            // 概况一行装完（用户定稿）：白天转夜间 + 高低温、AQI、风、湿度；生活指数整块不显示
            val summary = listOfNotNull(
                weather.dayNightSummary().takeIf { it.isNotBlank() },
                weather.aqi.takeIf { it.isNotBlank() }?.let { "AQI $it" },
                weather.windDirection.takeIf { it.isNotBlank() }?.let { "$it${weather.windPower}级" },
                weather.humidity.takeIf { it.isNotBlank() }?.let { "湿度 $it%" },
            ).joinToString("  ")
            InfoRow("概况", summary)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    BigTitle("站内服务")
    if (!data.remoteReady) LoadingLine() else ServiceSection(data.services)
}

/** 类别名与「电报码」同款（13sp 灰），条目用顿号连成流式文本——不可点的内容不该长得像按钮。 */
@Composable
private fun ServiceSection(groups: List<StationServiceGroup>) {
    val colors = MiuixTheme.colorScheme
    if (groups.isEmpty()) {
        Text(text = "暂无站内服务数据", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
        return
    }
    groups.forEach { group ->
        FieldLabel(group.title)
        Text(text = group.items.joinToString("、"), fontSize = 13.sp, color = colors.onSurface)
    }
}

/* ---------------- 标题与通用行 ---------------- */

/**
 * 小节标题：颜色与「电报码」这类字段标签同灰，字号大 2sp（15sp），加粗。
 * 用在 同城站点 / 起售时间 / 换乘预留 / 车站简介 / 天气 / 站内服务 六处，全版一致。
 */
@Composable
private fun BigTitle(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** 分类名＝与「电报码」完全同款（13sp 灰），站内服务的类别用它。 */
@Composable
private fun FieldLabel(label: String) {
    Text(
        text = label,
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/** 一条信息：标签 13sp 灰定宽，值 13sp 正文色；值缺失时整行灰字，不画深色「暂无」。 */
@Composable
private fun InfoRow(label: String, value: String) {
    val colors = MiuixTheme.colorScheme
    val blank = value.isBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = if (blank) "暂无${label}数据" else value,
            fontSize = 13.sp,
            color = if (blank) colors.onSurfaceVariantSummary else colors.onSurface,
        )
    }
}

/* ---------------- 查看地图 ---------------- */

/**
 * 用系统 `geo:` intent 唤起地图。**只传名称、不传坐标**——接口那对 GCJ-02 实测偏差明显
 * （南宁东落到别处），交给地图 App 自己检索反而准。名称用 `StationProfile.displayName`
 * （接口全名优先，缺「站」字才补，南宁东 → 南宁东站）。
 * 没装 `geo:` 处理程序时退高德搜索；每段参数**先 Uri.encode 再拼进 URI**，
 * 整条串不编码会让中文站名原样漏过，部分地图 App 解析失败。
 */
private fun openStationMap(context: Context, data: StationProfile) {
    val name = data.displayName
    if (context.startActivitySafe(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(name)}")))) return
    // 兜底同样只给关键词：坐标不准，拿它打点会把 marker 钉在错的地方
    val fallback = "https://uri.amap.com/search?keyword=${Uri.encode(name)}" +
        "&city=${Uri.encode(data.city)}&src=crinfo&callnative=1"
    context.startActivitySafe(Intent(Intent.ACTION_VIEW, Uri.parse(fallback)))
}

private fun Context.startActivitySafe(intent: Intent): Boolean =
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
