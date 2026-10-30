package com.nhlfantasygm.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhlfantasygm.data.local.FantasyMatchupEntity
import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.model.LineupSlot
import com.nhlfantasygm.model.TeamEmblem
import com.nhlfantasygm.multiplayer.MultiplayerUiState
import com.nhlfantasygm.multiplayer.RoomRole
import com.nhlfantasygm.ui.ScoredPlayerItem
import com.nhlfantasygm.ui.components.FranchiseCrestBadge
import com.nhlfantasygm.ui.theme.*
import java.util.Locale

@Composable
fun MatchupScreen(
    matchups: List<FantasyMatchupEntity>,
    fantasyTeams: List<FantasyTeamEntity>,
    humanTeam: FantasyTeamEntity?,
    controlledTeamId: String,
    allScoredPlayers: List<ScoredPlayerItem>,
    seasonNumber: Int,
    mpState: MultiplayerUiState = MultiplayerUiState(),
    onPlayerClick: (Long) -> Unit,
    onSimulateNextWeek: () -> Unit,
    onStartNextSeason: () -> Unit,
    onGoToTeam: () -> Unit
) {
    val nextUncompletedWeek = matchups.filter { !it.isCompleted }.minOfOrNull { it.weekNumber }
    val latestAvailableWeek = nextUncompletedWeek ?: (matchups.maxOfOrNull { it.weekNumber } ?: 1)
    var selectedWeek by remember(latestAvailableWeek) { mutableIntStateOf(latestAvailableWeek) }

    val availableWeeks = remember(matchups) {
        matchups.map { it.weekNumber }.distinct().sorted()
    }

    val currentWeekMatchups = remember(matchups, selectedWeek) {
        matchups.filter { it.weekNumber == selectedWeek }
    }

    val userMatchup = remember(currentWeekMatchups, controlledTeamId) {
        currentWeekMatchups.firstOrNull { it.homeTeamId == controlledTeamId || it.awayTeamId == controlledTeamId }
            ?: currentWeekMatchups.firstOrNull()
    }

    val opponentTeamId = remember(userMatchup, controlledTeamId) {
        if (userMatchup == null) null
        else if (userMatchup.homeTeamId == controlledTeamId) userMatchup.awayTeamId
        else userMatchup.homeTeamId
    }

    val userTeamObj = remember(fantasyTeams, controlledTeamId) {
        fantasyTeams.firstOrNull { it.teamId == controlledTeamId } ?: humanTeam
    }
    val opponentTeamObj = remember(fantasyTeams, opponentTeamId) {
        fantasyTeams.firstOrNull { it.teamId == opponentTeamId }
    }

    val isUserHome = userMatchup?.homeTeamId == controlledTeamId
    val userScore = if (userMatchup == null) 0.0 else if (isUserHome) userMatchup.homeScore else userMatchup.awayScore
    val oppScore = if (userMatchup == null) 0.0 else if (isUserHome) userMatchup.awayScore else userMatchup.homeScore

    // Players assigned to User's starters vs bench
    val userPlayers = remember(allScoredPlayers, controlledTeamId) {
        allScoredPlayers.filter { it.player.ownerFantasyTeamId == controlledTeamId }
    }
    val userStarters = remember(userPlayers) {
        userPlayers.filter { it.player.lineupSlot.isActiveStarter }
            .sortedBy { it.player.lineupSlot.ordinal }
    }
    val userBench = remember(userPlayers) {
        userPlayers.filter { !it.player.lineupSlot.isActiveStarter }
            .sortedByDescending { it.fantasy.seasonPoints }
    }

    // Players assigned to Opponent
    val opponentPlayers = remember(allScoredPlayers, opponentTeamId) {
        if (opponentTeamId == null) emptyList()
        else allScoredPlayers.filter { it.player.ownerFantasyTeamId == opponentTeamId }
    }
    val opponentStarters = remember(opponentPlayers) {
        opponentPlayers.filter { it.player.lineupSlot.isActiveStarter }
            .sortedBy { it.player.lineupSlot.ordinal }
    }
    val opponentBench = remember(opponentPlayers) {
        opponentPlayers.filter { !it.player.lineupSlot.isActiveStarter }
            .sortedByDescending { it.fantasy.seasonPoints }
    }

    val otherMatchups = remember(currentWeekMatchups, userMatchup) {
        currentWeekMatchups.filter { it.matchupId != userMatchup?.matchupId }
    }

    val isSeasonFinished = remember(matchups) {
        matchups.isNotEmpty() && matchups.all { it.isCompleted } &&
            matchups.any { it.stage == "CHAMPIONSHIP_FINAL" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Clean Week Navigation Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val prevWeek = availableWeeks.lastOrNull { it < selectedWeek }
                val nextWeek = availableWeeks.firstOrNull { it > selectedWeek }

                IconButton(
                    onClick = { if (prevWeek != null) selectedWeek = prevWeek },
                    enabled = prevWeek != null
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Week")
                }

                val stageLabel = when (selectedWeek) {
                    15 -> "Playoff Semifinals"
                    16 -> "Stanley Fantasy Final 🏆"
                    else -> "Week $selectedWeek Matchup"
                }
                Text(
                    text = stageLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (selectedWeek >= 15) TrophyGold else IceCyan
                )

                IconButton(
                    onClick = { if (nextWeek != null) selectedWeek = nextWeek },
                    enabled = nextWeek != null
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Week")
                }
            }
        }

        // 2. Sleeper Clean Scoreboard Hero Card
        if (userMatchup != null && userTeamObj != null && opponentTeamObj != null) {
            item {
                CleanSleeperScoreboard(
                    matchup = userMatchup,
                    userTeam = userTeamObj,
                    oppTeam = opponentTeamObj,
                    userScore = userScore,
                    oppScore = oppScore,
                    isSeasonFinished = isSeasonFinished,
                    canSimulate = mpState.role != RoomRole.GUEST,
                    onSimulateWeek = onSimulateNextWeek,
                    onStartNextSeason = onStartNextSeason,
                    onGoToTeam = onGoToTeam
                )
            }
        }

        // 3. Section Header for Lineup Comparison
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Starters Head-to-Head",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = onGoToTeam,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Edit Lineup", style = MaterialTheme.typography.labelMedium, color = IceCyan)
                }
            }
        }

        // 4. Clean Sleeper Slot-by-Slot Comparison Table
        val starterSlots = listOf(
            LineupSlot.C1, LineupSlot.C2, LineupSlot.C3,
            LineupSlot.LW1, LineupSlot.LW2, LineupSlot.LW3,
            LineupSlot.RW1, LineupSlot.RW2, LineupSlot.RW3,
            LineupSlot.LD1, LineupSlot.RD1,
            LineupSlot.LD2, LineupSlot.RD2,
            LineupSlot.LD3, LineupSlot.RD3,
            LineupSlot.G1, LineupSlot.G2
        )

        items(starterSlots) { slot ->
            val myPlayer = userStarters.firstOrNull { it.player.lineupSlot == slot }
            val oppPlayer = opponentStarters.firstOrNull { it.player.lineupSlot == slot }
            SleeperSlotRow(
                slot = slot,
                myPlayer = myPlayer,
                oppPlayer = oppPlayer,
                onPlayerClick = onPlayerClick
            )
        }

        // 5. Bench Reserves Section
        if (userBench.isNotEmpty() || opponentBench.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Bench Reserves",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val maxBench = maxOf(userBench.size, opponentBench.size)
            items(maxBench) { idx ->
                val myBench = userBench.getOrNull(idx)
                val oppBench = opponentBench.getOrNull(idx)
                SleeperBenchRow(
                    slotLabel = "BN${idx + 1}",
                    myPlayer = myBench,
                    oppPlayer = oppBench,
                    onPlayerClick = onPlayerClick
                )
            }
        }

        // 6. Other Matchups Around the League
        if (otherMatchups.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "League Scoreboard • Week $selectedWeek",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(otherMatchups, key = { it.matchupId }) { om ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${om.awayTeamName} vs ${om.homeTeamName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (om.isCompleted) "Completed" else "Scheduled",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = if (om.isCompleted) {
                                "${String.format(Locale.CANADA, "%.1f", om.awayScore)} - ${String.format(Locale.CANADA, "%.1f", om.homeScore)}"
                            } else "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (om.isCompleted) ProfitEmerald else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CleanSleeperScoreboard(
    matchup: FantasyMatchupEntity,
    userTeam: FantasyTeamEntity,
    oppTeam: FantasyTeamEntity,
    userScore: Double,
    oppScore: Double,
    isSeasonFinished: Boolean,
    canSimulate: Boolean,
    onSimulateWeek: () -> Unit,
    onStartNextSeason: () -> Unit,
    onGoToTeam: () -> Unit
) {
    val totalScore = (userScore + oppScore).coerceAtLeast(1.0)
    val userProgress = (userScore / totalScore).toFloat().coerceIn(0.05f, 0.95f)
    val animatedProgress by animateFloatAsState(
        targetValue = userProgress,
        animationSpec = tween(durationMillis = 500),
        label = "progress"
    )

    val isWinning = userScore > oppScore
    val diff = userScore - oppScore

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (matchup.isCompleted) (if (isWinning) ProfitEmerald.copy(alpha = 0.6f) else IceCyan.copy(alpha = 0.4f)) else MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Team vs Team Clean Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Side
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FranchiseCrestBadge(
                        emblem = TeamEmblem.fromId(userTeam.emblemId),
                        primaryColorHex = userTeam.primaryColorHex,
                        secondaryColorHex = userTeam.secondaryColorHex,
                        abbreviation = userTeam.abbreviation,
                        size = 36.dp
                    )
                    Column {
                        Text(
                            text = userTeam.teamName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "You (${userTeam.wins}-${userTeam.losses})",
                            style = MaterialTheme.typography.labelSmall,
                            color = ProfitEmerald
                        )
                    }
                }

                // Opponent Side
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = oppTeam.teamName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = "${if (oppTeam.isCpuGm) "CPU" else "Opponent"} (${oppTeam.wins}-${oppTeam.losses})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FranchiseCrestBadge(
                        emblem = TeamEmblem.fromId(oppTeam.emblemId),
                        primaryColorHex = oppTeam.primaryColorHex,
                        secondaryColorHex = oppTeam.secondaryColorHex,
                        abbreviation = oppTeam.abbreviation,
                        size = 36.dp
                    )
                }
            }

            // Big Numbers & Lead Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.CANADA, "%.1f", userScore),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isWinning) ProfitEmerald else MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (diff >= 0) "+${String.format(Locale.CANADA, "%.1f", diff)}" else String.format(Locale.CANADA, "%.1f", diff),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (diff >= 0) ProfitEmerald else AlertRed
                    )
                }

                Text(
                    text = String.format(Locale.CANADA, "%.1f", oppScore),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (!isWinning && diff != 0.0) AlertRed else MaterialTheme.colorScheme.onSurface
                )
            }

            // Clean Momentum Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isWinning) ProfitEmerald else IceCyan,
                trackColor = AlertRed.copy(alpha = 0.5f)
            )

            // One-Tap Action Button
            if (isSeasonFinished) {
                Button(
                    onClick = onStartNextSeason,
                    enabled = canSimulate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TrophyGold, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Launch Next Fantasy Season", fontWeight = FontWeight.Bold)
                }
            } else if (!matchup.isCompleted) {
                Button(
                    onClick = onSimulateWeek,
                    enabled = canSimulate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Simulate Week ${matchup.weekNumber}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SleeperSlotRow(
    slot: LineupSlot,
    myPlayer: ScoredPlayerItem?,
    oppPlayer: ScoredPlayerItem?,
    onPlayerClick: (Long) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position Pill
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.width(34.dp)
            ) {
                Text(
                    text = slot.code,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = IceCyan,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            // My Player Info & Score
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = myPlayer != null) { myPlayer?.let { onPlayerClick(it.player.playerId) } },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (myPlayer != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = myPlayer.player.lastName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${myPlayer.player.teamAbbrev} • ${if (myPlayer.player.todayOpponentLabel.isNotBlank()) myPlayer.player.todayOpponentLabel else "Next"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = String.format(Locale.CANADA, "%.1f", myPlayer.fantasy.seasonPoints),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = ProfitEmerald
                    )
                } else {
                    Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("0.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Divider
            VerticalDivider(
                modifier = Modifier
                    .height(26.dp)
                    .padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )

            // Opponent Player Info & Score
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = oppPlayer != null) { oppPlayer?.let { onPlayerClick(it.player.playerId) } },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (oppPlayer != null) {
                    Text(
                        text = String.format(Locale.CANADA, "%.1f", oppPlayer.fantasy.seasonPoints),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(
                            text = oppPlayer.player.lastName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = "${oppPlayer.player.teamAbbrev} • ${if (oppPlayer.player.todayOpponentLabel.isNotBlank()) oppPlayer.player.todayOpponentLabel else "Next"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    }
                } else {
                    Text("0.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SleeperBenchRow(
    slotLabel: String,
    myPlayer: ScoredPlayerItem?,
    oppPlayer: ScoredPlayerItem?,
    onPlayerClick: (Long) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = slotLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(34.dp),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.width(8.dp))

            // My Bench
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = myPlayer != null) { myPlayer?.let { onPlayerClick(it.player.playerId) } },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = myPlayer?.player?.lastName ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (myPlayer != null) String.format(Locale.CANADA, "%.1f", myPlayer.fantasy.seasonPoints) else "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            VerticalDivider(modifier = Modifier.height(18.dp).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Opponent Bench
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = oppPlayer != null) { oppPlayer?.let { onPlayerClick(it.player.playerId) } },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (oppPlayer != null) String.format(Locale.CANADA, "%.1f", oppPlayer.fantasy.seasonPoints) else "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = oppPlayer?.player?.lastName ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
