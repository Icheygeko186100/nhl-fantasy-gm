package com.nhlfantasygm.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhlfantasygm.data.local.LiveNhlGameEntity
import com.nhlfantasygm.model.DataFreshnessState
import com.nhlfantasygm.ui.components.NhlTeamBadge
import com.nhlfantasygm.ui.theme.*

@Composable
fun ScoresScreen(
    liveGames: List<LiveNhlGameEntity>,
    isSyncing: Boolean,
    freshness: DataFreshnessState,
    onRefreshScores: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E17)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header Info
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Today's NHL Scoreboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = "Official NHL live scores & statistics",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7E8B9F)
                    )
                }

                IconButton(
                    onClick = onRefreshScores,
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF00E5FF),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Scores",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                }
            }
        }

        if (liveGames.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF131D31),
                    border = BorderStroke(1.dp, Color(0xFF223555)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🏒", fontSize = 36.sp)
                        Text(
                            text = "No Games Scheduled Today",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap refresh to check for upcoming NHL game schedules and live updates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7E8B9F),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRefreshScores,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF001F29)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Sync Live Scores", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(liveGames, key = { it.gameId }) { game ->
                NhlGameScorecard(game = game)
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NhlGameScorecard(game: LiveNhlGameEntity) {
    val isLive = game.gameState.equals("LIVE", ignoreCase = true) || game.gameState.equals("CRIT", ignoreCase = true)
    val isFinal = game.gameState.equals("OFF", ignoreCase = true) || game.gameState.equals("FINAL", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF131D31),
        border = BorderStroke(
            1.dp,
            if (isLive) ProfitEmerald.copy(alpha = 0.5f) else Color(0xFF223555)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Game Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isLive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ProfitEmerald)
                        )
                    }
                    Text(
                        text = when {
                            isLive -> "LIVE • ${if (game.periodLabel.isNotBlank()) game.periodLabel else "In Progress"}"
                            isFinal -> "FINAL"
                            else -> game.startTimeUtc.takeIf { it.isNotBlank() } ?: "SCHEDULED"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            isLive -> ProfitEmerald
                            isFinal -> Color(0xFF94A3B8)
                            else -> Color(0xFF00E5FF)
                        }
                    )
                }

                Text(
                    text = game.gameDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B)
                )
            }

            // Teams & Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Away Team
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    NhlTeamBadge(abbrev = game.awayAbbrev, logoUrl = game.awayLogoUrl, size = 32.dp)
                    Column {
                        Text(
                            text = game.awayAbbrev,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${game.awaySog} SOG",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7E8B9F)
                        )
                    }
                }

                // Scores
                Text(
                    text = "${game.awayScore}  -  ${game.homeScore}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                // Home Team
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = game.homeAbbrev,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${game.homeSog} SOG",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7E8B9F)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    NhlTeamBadge(abbrev = game.homeAbbrev, logoUrl = game.homeLogoUrl, size = 32.dp)
                }
            }
        }
    }
}
