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
import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.domain.SeasonSimulationEngine
import com.nhlfantasygm.model.LineupSlot
import com.nhlfantasygm.model.Position
import com.nhlfantasygm.model.TeamEmblem
import com.nhlfantasygm.multiplayer.MultiplayerUiState
import com.nhlfantasygm.multiplayer.RoomRole
import com.nhlfantasygm.ui.ScoredPlayerItem
import com.nhlfantasygm.ui.components.FranchiseCrestBadge
import com.nhlfantasygm.ui.components.PlayerHeadshot
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

    // Find the user's matchup for the selected week
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
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Sleeper-Style Week Selector Header
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
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

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        Text(
                            text = "Season $seasonNumber • Real NHL Player Scoring",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { if (nextWeek != null) selectedWeek = nextWeek },
                        enabled = nextWeek != null
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Week")
                    }
                }
            }
        }

        // 2. Horizontal Quick Week Pills
        if (availableWeeks.size > 1) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableWeeks.forEach { wk ->
                        val pillLabel = when (wk) {
                            15 -> "Semis"
                            16 -> "Final 🏆"
                            else -> "W$wk"
                        }
                        val isSelected = selectedWeek == wk
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedWeek = wk },
                            label = { Text(pillLabel, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }

        // 3. Iconic Sleeper Head-to-Head Scoreboard Card
        if (userMatchup != null && userTeamObj != null && opponentTeamObj != null) {
            item {
                SleeperMatchupHeroCard(
                    matchup = userMatchup,
                    userTeam = userTeamObj,
                    oppTeam = opponentTeamObj,
                    userScore = userScore,
                    oppScore = oppScore,
                    isSeasonFinished = isSeasonFinished,
                    canSimulate = mpState.role != RoomRole.GUEST,
                    onSimulateWeek = onSimulateNextWeek,
                    onStartNextSeason = onStartNextSeason
                )
            }
        }

        // 4. Lineup Optimization / Quick Alert
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = IceCyan, modifier = Modifier.size(18.dp))
                        Text(
                            text = "${userStarters.size} Active Starters • ${userBench.size} Bench",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(onClick = onGoToTeam) {
                        Text("Edit Lineup", fontWeight = FontWeight.Bold, color = IceCyan)
                    }
                }
            }
        }

        // 5. Starters Comparison Section (Sleeper Side-by-Side Slot Comparison)
        item {
            Text(
                text = "Starting Lineup Showdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Render matched starting positions
        val maxStarterSlots = maxOf(userStarters.size, opponentStarters.size)
        if (maxStarterSlots == 0) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No starters assigned for this matchup yet. Open Team to optimize your starting lines.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(maxStarterSlots) { idx ->
                val myItem = userStarters.getOrNull(idx)
                val oppItem = opponentStarters.getOrNull(idx)
                SleeperSlotDuelRow(
                    myPlayer = myItem,
                    oppPlayer = oppItem,
                    onPlayerClick = onPlayerClick
                )
            }
        }

        // 6. Bench Section
        if (userBench.isNotEmpty() || opponentBench.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Bench Reserves",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val maxBench = maxOf(userBench.size, opponentBench.size)
            items(maxBench) { idx ->
                val myBench = userBench.getOrNull(idx)
                val oppBench = opponentBench.getOrNull(idx)
                SleeperBenchDuelRow(
                    myPlayer = myBench,
                    oppPlayer = oppBench,
                    onPlayerClick = onPlayerClick
                )
            }
        }

        // 7. Other League Matchups This Week
        if (otherMatchups.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Other League Matchups • Week $selectedWeek",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(otherMatchups, key = { it.matchupId }) { om ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
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
                                text = if (om.isCompleted) "Completed (${om.scoringModeLabel})" else "Scheduled",
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
private fun SleeperMatchupHeroCard(
    matchup: FantasyMatchupEntity,
    userTeam: FantasyTeamEntity,
    oppTeam: FantasyTeamEntity,
    userScore: Double,
    oppScore: Double,
    isSeasonFinished: Boolean,
    canSimulate: Boolean,
    onSimulateWeek: () -> Unit,
    onStartNextSeason: () -> Unit
) {
    val totalScore = (userScore + oppScore).coerceAtLeast(1.0)
    val userProgress = (userScore / totalScore).toFloat().coerceIn(0.05f, 0.95f)
    val animatedProgress by animateFloatAsState(
        targetValue = userProgress,
        animationSpec = tween(durationMillis = 600),
        label = "userProgress"
    )

    val isUserWinning = userScore > oppScore
    val isTied = userScore == oppScore

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.5.dp,
            if (matchup.isCompleted) (if (isUserWinning) ProfitEmerald else IceCyan) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Status Pill & Scoring Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (matchup.isCompleted) ProfitEmerald.copy(alpha = 0.15f) else IceCyan.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (matchup.isCompleted) "FINAL RESULT" else "IN PROGRESS / SCHEDULED",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (matchup.isCompleted) ProfitEmerald else IceCyan
                    )
                }

                Text(
                    text = if (matchup.scoringModeLabel == "REAL_NHL_STATS") "NHL STATS ENGINE" else "OFFLINE SIM",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Head-to-Head Banner: User vs Opponent
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Team Side
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
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
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "You • ${userTeam.wins}-${userTeam.losses}-${userTeam.ties}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ProfitEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = String.format(Locale.CANADA, "%.1f", userScore),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isUserWinning) ProfitEmerald else MaterialTheme.colorScheme.onSurface
                    )
                    Text("Fantasy Points", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // VS Pill
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "VS",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = TrophyGold
                    )
                }

                // Opponent Team Side
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = oppTeam.teamName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = "${if (oppTeam.isCpuGm) "CPU GM" else "Friend"} • ${oppTeam.wins}-${oppTeam.losses}-${oppTeam.ties}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FranchiseCrestBadge(
                            emblem = TeamEmblem.fromId(oppTeam.emblemId),
                            primaryColorHex = oppTeam.primaryColorHex,
                            secondaryColorHex = oppTeam.secondaryColorHex,
                            abbreviation = oppTeam.abbreviation,
                            size = 36.dp
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = String.format(Locale.CANADA, "%.1f", oppScore),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (!isUserWinning && !isTied) AlertRed else MaterialTheme.colorScheme.onSurface
                    )
                    Text("Fantasy Points", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Sleeper-Style Live Momentum Meter
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isUserWinning) ProfitEmerald else IceCyan,
                    trackColor = AlertRed.copy(alpha = 0.6f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val diff = userScore - oppScore
                    val diffText = if (diff >= 0) "+${String.format(Locale.CANADA, "%.1f", diff)} Lead" else "${String.format(Locale.CANADA, "%.1f", diff)} Deficit"
                    Text(
                        text = diffText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (diff >= 0) ProfitEmerald else AlertRed
                    )
                    Text(
                        text = if (isUserWinning) "Projected Winner" else if (isTied) "Even Match" else "Trailing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Matchup Highlight or Storyline
            if (matchup.highlightSummary.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Text(
                    text = "Key Performers: ${matchup.highlightSummary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Simulation Action Button
            if (isSeasonFinished) {
                Button(
                    onClick = onStartNextSeason,
                    enabled = canSimulate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TrophyGold, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Launch Fantasy Season Final / Next Season", fontWeight = FontWeight.Bold)
                }
            } else if (!matchup.isCompleted) {
                Button(
                    onClick = onSimulateWeek,
                    enabled = canSimulate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Simulate Week ${matchup.weekNumber} Matchup", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SleeperSlotDuelRow(
    myPlayer: ScoredPlayerItem?,
    oppPlayer: ScoredPlayerItem?,
    onPlayerClick: (Long) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // My Player Half
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = myPlayer != null) { myPlayer?.let { onPlayerClick(it.player.playerId) } }
            ) {
                if (myPlayer != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerHeadshot(
                            headshotUrl = myPlayer.player.headshotUrl,
                            fullName = myPlayer.player.fullName,
                            teamAbbrev = myPlayer.player.teamAbbrev,
                            sweaterNumber = myPlayer.player.sweaterNumber,
                            size = 38.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = myPlayer.player.lastName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = myPlayer.player.lineupSlot.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = IceCyan
                                )
                            }
                            Text(
                                text = "${myPlayer.player.teamAbbrev} • ${if (myPlayer.player.todayOpponentLabel.isNotBlank()) myPlayer.player.todayOpponentLabel else "Next Game"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = String.format(Locale.CANADA, "%.1f", myPlayer.fantasy.seasonPoints),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = ProfitEmerald
                        )
                    }
                } else {
                    Text(
                        text = "Empty Slot",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Center Divider Bar
            VerticalDivider(
                modifier = Modifier
                    .height(36.dp)
                    .padding(horizontal = 6.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )

            // Opponent Player Half
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = oppPlayer != null) { oppPlayer?.let { onPlayerClick(it.player.playerId) } }
            ) {
                if (oppPlayer != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(Locale.CANADA, "%.1f", oppPlayer.fantasy.seasonPoints),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = oppPlayer.player.lineupSlot.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TrophyGold
                                )
                                Text(
                                    text = oppPlayer.player.lastName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${oppPlayer.player.teamAbbrev} • ${if (oppPlayer.player.todayOpponentLabel.isNotBlank()) oppPlayer.player.todayOpponentLabel else "Next Game"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        PlayerHeadshot(
                            headshotUrl = oppPlayer.player.headshotUrl,
                            fullName = oppPlayer.player.fullName,
                            teamAbbrev = oppPlayer.player.teamAbbrev,
                            sweaterNumber = oppPlayer.player.sweaterNumber,
                            size = 38.dp
                        )
                    }
                } else {
                    Text(
                        text = "Empty Slot",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun SleeperBenchDuelRow(
    myPlayer: ScoredPlayerItem?,
    oppPlayer: ScoredPlayerItem?,
    onPlayerClick: (Long) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = myPlayer != null) { myPlayer?.let { onPlayerClick(it.player.playerId) } }
            ) {
                if (myPlayer != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("BN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Text(myPlayer.player.lastName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Text(String.format(Locale.CANADA, "%.1f", myPlayer.fantasy.seasonPoints), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 6.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = oppPlayer != null) { oppPlayer?.let { onPlayerClick(it.player.playerId) } }
            ) {
                if (oppPlayer != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(String.format(Locale.CANADA, "%.1f", oppPlayer.fantasy.seasonPoints), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        Text(oppPlayer.player.lastName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("BN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
