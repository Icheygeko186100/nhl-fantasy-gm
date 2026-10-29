package com.nhlfantasygm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nhlfantasygm.data.local.HistoricalSeasonRow
import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.data.local.RecentGameLogRow
import com.nhlfantasygm.model.Position
import com.nhlfantasygm.ui.theme.IceCyan
import com.nhlfantasygm.ui.theme.ProfitEmerald
import com.nhlfantasygm.ui.theme.RoyalBlue
import com.nhlfantasygm.ui.theme.TrophyGold
import kotlin.math.max

@Composable
fun RecentGamesBarChart(
    games: List<RecentGameLogRow>,
    isGoalie: Boolean,
    modifier: Modifier = Modifier
) {
    if (games.isEmpty()) return
    val ordered = games.reversed() // chronological left-to-right
    val values = ordered.map { g ->
        if (isGoalie) (g.savePct * 100.0).coerceIn(75.0, 100.0)
        else (g.goals * 3.0 + g.assists * 2.0 + g.shots * 0.4).coerceAtLeast(0.2)
    }
    val maxVal = max(if (isGoalie) 100.0 else 6.0, values.maxOrNull() ?: 6.0)
    val barColor = if (isGoalie) TrophyGold else IceCyan

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            val count = ordered.size
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.48f).coerceAtMost(38.dp.toPx())

            // Horizontal guide lines
            for (i in 1..3) {
                val y = size.height * (i / 4f)
                drawLine(
                    color = Color.Gray.copy(alpha = 0.18f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            values.forEachIndexed { idx, v ->
                val ratio = if (isGoalie) {
                    ((v - 75.0) / 25.0).toFloat().coerceIn(0.08f, 1f)
                } else {
                    (v / maxVal).toFloat().coerceIn(0.08f, 1f)
                }
                val barHeight = size.height * ratio
                val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                val top = size.height - barHeight

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(barColor, barColor.copy(alpha = 0.35f)),
                        startY = top,
                        endY = size.height
                    ),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ordered.forEach { g ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isGoalie) {
                            "${(g.savePct * 1000).toInt() / 10.0}%"
                        } else {
                            "${g.goals}G ${g.assists}A"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = g.opponentAbbrev,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun HistoricalSeasonsLineChart(
    seasons: List<HistoricalSeasonRow>,
    isGoalie: Boolean,
    modifier: Modifier = Modifier
) {
    if (seasons.size < 2) return
    val values = seasons.map { s ->
        if (isGoalie) (s.savePct * 100.0).coerceIn(82.0, 96.0) else s.points.toDouble()
    }
    val minV = if (isGoalie) 84.0 else 0.0
    val maxV = max(if (isGoalie) 95.0 else 30.0, values.maxOrNull() ?: 30.0)
    val lineColor = ProfitEmerald

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
            val points = values.mapIndexed { idx, v ->
                val norm = ((v - minV) / max(1.0, maxV - minV)).toFloat().coerceIn(0f, 1f)
                Offset(idx * stepX, size.height * (1f - norm))
            }

            val areaPath = Path().apply {
                moveTo(points.first().x, size.height)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, size.height)
                close()
            }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.35f), Color.Transparent)
                )
            )

            val strokePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = strokePath,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx())
            )

            points.forEach { pt ->
                drawCircle(color = lineColor, radius = 4.5.dp.toPx(), center = pt)
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            seasons.forEach { s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isGoalie) "${s.wins}W" else "${s.points}P",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = s.seasonLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerAttributeBars(player: PlayerEntity) {
    val gp = max(1, player.gamesPlayed.takeIf { it > 0 } ?: player.prevGamesPlayed.coerceAtLeast(1))
    val useCur = player.gamesPlayed > 0

    val metrics: List<Triple<String, Float, Color>> = if (player.position == Position.GOALIE) {
        val sv = if (useCur) player.savePct else player.prevSavePct
        val wins = if (useCur) player.wins else player.prevWins
        val gaa = if (useCur) player.goalsAgainstAverage else player.prevGaa
        listOf(
            Triple("Save Efficiency", (((sv - 0.880) / 0.055).toFloat()).coerceIn(0.15f, 1f), IceCyan),
            Triple("Win Conversion", ((wins.toFloat() / gp) / 0.72f).coerceIn(0.15f, 1f), ProfitEmerald),
            Triple("GAA Prevention", (((3.8 - gaa) / 2.0).toFloat()).coerceIn(0.15f, 1f), RoyalBlue),
            Triple("Overall Rating", (player.overallRating / 99f).coerceIn(0.2f, 1f), TrophyGold)
        )
    } else {
        val goals = if (useCur) player.goals else player.prevGoals
        val assists = if (useCur) player.assists else player.prevAssists
        val shots = if (useCur) player.shots else player.prevShots
        val phys = if (useCur) (player.hits + player.blockedShots) else (player.prevHits + player.prevBlockedShots)
        listOf(
            Triple("Goal Scoring", ((goals.toFloat() / gp) / 0.65f).coerceIn(0.12f, 1f), IceCyan),
            Triple("Playmaking", ((assists.toFloat() / gp) / 0.90f).coerceIn(0.12f, 1f), RoyalBlue),
            Triple("Shot Volume", ((shots.toFloat() / gp) / 4.2f).coerceIn(0.12f, 1f), ProfitEmerald),
            Triple("Hits & Blocks", ((phys.toFloat() / gp) / 3.8f).coerceIn(0.12f, 1f), TrophyGold)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.forEach { (label, progress, tint) ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${(progress * 99).toInt()}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tint
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(tint)
                    )
                }
            }
        }
    }
}
