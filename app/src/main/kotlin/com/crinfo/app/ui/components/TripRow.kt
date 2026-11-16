package com.crinfo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinfo.app.data.model.SeatOffer
import com.crinfo.app.data.model.SeatStatus
import com.crinfo.app.data.model.TrainTag
import com.crinfo.app.data.model.Trip
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun formatPrice(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)

@Composable
fun TripRow(trip: Trip, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        // Miuix 卡片默认 insideMargin 为 0，必须自己留白，否则文字压在圆角上
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
        // 整卡绘制按压层，而不是只在文字那一小块出现反馈
        showIndication = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = trip.trainCode, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            trip.tags.forEach { badge -> TagBadge(badge) }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = trip.kind.label,
                fontSize = 11.sp,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.weight(1f))
            when {
                trip.soldOut -> Text(text = "售罄", fontSize = 12.sp, color = colors.error)
                trip.lowestAvailablePrice != null -> Text(
                    text = "¥${formatPrice(trip.lowestAvailablePrice!!)} 起",
                    fontSize = 12.sp,
                    color = colors.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(82.dp), horizontalAlignment = Alignment.End) {
                Text(text = trip.departTime, fontSize = 21.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = trip.fromName + if (trip.fromCode == trip.startCode) " 始" else "",
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = trip.durationText,
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariantSummary,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                        .height(1.dp)
                        .background(colors.onSurfaceVariantSummary.copy(alpha = 0.35f)),
                )
                Text(
                    text = if (trip.distanceKm > 0) "${trip.distanceKm}km" else "",
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            Column(modifier = Modifier.width(82.dp), horizontalAlignment = Alignment.Start) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(text = trip.arriveTime, fontSize = 21.sp, fontWeight = FontWeight.Medium)
                    // 跨天标成右上角小字，与车次详情页/板面行的「+1」同一套读法
                    if (trip.arrivesNextDay) {
                        Text(
                            text = "+1",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.primary,
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                        )
                    }
                }
                Text(
                    text = trip.toName + if (trip.toCode == trip.endCode) " 终" else "",
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }

        if (trip.seats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            SeatGrid(trip.seats)
        }
    }
}

/** 车次标签：圆形底 + 单字（复/智/铺/静）。 */
@Composable
fun TagBadge(tag: TrainTag) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .size(17.dp)
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = tag.label, fontSize = 10.sp, color = colors.primary)
    }
}

/** 席别+余票+票价的小方块组，车票查询卡片与车次详情票价表共用。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeatGrid(seats: List<SeatOffer>) {
    val colors = MiuixTheme.colorScheme
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        seats.forEach { offer ->
            val has = offer.status.hasTicket
            // 离线态只有席别种类、没有票量：只出席别名。
            // 一堆「二等座 无 / 一等座 无」会让人读成「全卖光了」，比不显示更误导（用户定稿）。
            val label = if (offer.status == SeatStatus.Unknown) {
                offer.seat.label
            } else {
                buildString {
                    append(offer.seat.label)
                    append(' ')
                    append(offer.status.display())
                    offer.price?.let { append(" ¥"); append(formatPrice(it)) }
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = if (has) colors.primary else colors.onSurfaceVariantSummary,
                )
            }
        }
    }
}
