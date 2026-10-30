package com.nhlfantasygm.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
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
import com.nhlfantasygm.model.InjurySeverity
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

    var selectedMatchupIndex by remember(selectedWeek, controlledTeamId) {
        val userIdx = currentWeekMatchups.indexOfFirst {
            it.homeTeamId == controlledTeamId || it.awayTeamId == controlledTeamId
        }
        mutableIntStateOf(if (userIdx >= 0) userIdx else 0)
    }

    val activeMatchup = remember(currentWeekMatchups, selectedMatchupIndex) {
        currentWeekMatchups.getOrNull(selectedMatchupIndex) ?: currentWeekMatchups.firstOrNull()
    }

    val isUserInMatchup = activeMatchup != null &&
            (activeMatchup.homeTeamId == controlledTeamId || activeMatchup.awayTeamId == controlledTeamId)

    val (leftTeamId, rightTeamId) = remember(activeMatchup, controlledTeamId) {
        if (activeMatchup == null) "" to ""
        else if (activeMatchup.homeTeamId == controlledTeamId) {
            activeMatchup.homeTeamId to activeMatchup.awayTeamId
        } else if (activeMatchup.awayTeamId == controlledTeamId) {
            activeMatchup.awayTeamId to activeMatchup.homeTeamId
        } else {
            activeMatchup.awayTeamId to activeMatchup.homeTeamId
        }
    }

    val leftTeamObj = remember(fantasyTeams, leftTeamId) {
        fantasyTeams.firstOrNull { it.teamId == leftTeamId } ?: humanTeam
    }
    val rightTeamObj = remember(fantasyTeams, rightTeamId) {
        fantasyTeams.firstOrNull { it.teamId == rightTeamId }
    }

    val isLeftHome = activeMatchup?.homeTeamId == leftTeamId
    val leftScore = if (activeMatchup == null) 0.0 else if (isLeftHome) activeMatchup.homeScore else activeMatchup.awayScore
    val rightScore = if (activeMatchup == null) 0.0 else if (isLeftHome) activeMatchup.awayScore else activeMatchup.homeScore

    // Players assigned to Left Team
    val leftPlayers = remember(allScoredPlayers, leftTeamId) {
        allScoredPlayers.filter { it.player.ownerFantasyTeamId == leftTeamId }
    }
    val leftStarters = remember(leftPlayers) {
        leftPlayers.filter { it.player.lineupSlot.isActiveStarter }
            .sortedBy { it.player.lineupSlot.ordinal }
    }
    val leftBench = remember(leftPlayers) {
        leftPlayers.filter { !it.player.lineupSlot.isActiveStarter }
            .sortedByDescending { it.fantasy.seasonPoints }
    }

    // Players assigned to Right Team
    val rightPlayers = remember(allScoredPlayers, rightTeamId) {
        allScoredPlayers.filter { it.player.ownerFantasyTeamId == rightTeamId }
    }
    val rightStarters = remember(rightPlayers) {
        rightPlayers.filter { it.player.lineupSlot.isActiveStarter }
            .sortedBy { it.player.lineupSlot.ordinal }
    }
    val rightBench = remember(rightPlayers) {
        rightPlayers.filter { !it.player.lineupSlot.isActiveStarter }
            .sortedByDescending { it.fantasy.seasonPoints }
    }

    val isSeasonFinished = remember(matchups) {
        matchups.isNotEmpty() && matchups.all { it.isCompleted } &&
                matchups.any { it.stage == "CHAMPIONSHIP_FINAL" }
    }

    // Dynamic win probability calculation (Sleeper style)
    val (leftWinPct, rightWinPct) = remember(leftScore, rightScore, leftStarters, rightStarters, activeMatchup?.isCompleted) {
        if (activeMatchup?.isCompleted == true) {
            if (leftScore > rightScore) 100 to 0
            else if (rightScore > leftScore) 0 to 100
            else 50 to 50
        } else if (leftScore > 0 || rightScore > 0) {
            val total = (leftScore + rightScore).coerceAtLeast(1.0)
            val leftP = ((leftScore / total) * 100).toInt().coerceIn(15, 85)
            leftP to (100 - leftP)
        } else {
            val leftPj = leftStarters.sumOf { (it.fantasy.seasonPoints / maxOf(1, it.player.gamesPlayed)).coerceAtLeast(1.2) * 3.2 } + 70.0
            val rightPj = rightStarters.sumOf { (it.fantasy.seasonPoints / maxOf(1, it.player.gamesPlayed)).coerceAtLeast(1.2) * 3.2 } + 68.0
            val totalPj = (leftPj + rightPj).coerceAtLeast(1.0)
            val leftP = ((leftPj / totalPj) * 100).toInt().coerceIn(20, 80)
            leftP to (100 - leftP)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E17)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Sleeper Matchup Hero Card (matching reference screenshot)
        if (activeMatchup != null && leftTeamObj != null && rightTeamObj != null) {
            item {
                SleeperMatchupHeroCard(
                    matchup = activeMatchup,
                    leftTeam = leftTeamObj,
                    rightTeam = rightTeamObj,
                    leftScore = leftScore,
                    rightScore = rightScore,
                    leftWinPct = leftWinPct,
                    rightWinPct = rightWinPct,
                    isUserInMatchup = isUserInMatchup,
                    matchupCount = currentWeekMatchups.size,
                    currentMatchupIndex = selectedMatchupIndex,
                    onSelectMatchupIndex = { selectedMatchupIndex = it },
                    canSimulate = mpState.role != RoomRole.GUEST && !activeMatchup.isCompleted,
                    isSeasonFinished = isSeasonFinished,
                    onSimulateWeek = onSimulateNextWeek,
                    onStartNextSeason = onStartNextSeason
                )
            }
        }

        // 2. Starters Section Header (Left: "Starters", Right: "WEEK 8 >")
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Starters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        // Cycle to next available week
                        val nextW = availableWeeks.firstOrNull { it > selectedWeek } ?: availableWeeks.firstOrNull() ?: 1
                        selectedWeek = nextW
                    }
                ) {
                    Text(
                        text = "WEEK $selectedWeek",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E5FF)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next Week",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 3. Starters Slot-by-Slot Table (Sleeper layout: Left Player | Score | Center Badge | Score | Right Player)
        val starterSlots = listOf(
            LineupSlot.C1,
            LineupSlot.C2,
            LineupSlot.LW1,
            LineupSlot.LW2,
            LineupSlot.RW1,
            LineupSlot.RW2,
            LineupSlot.LD1,
            LineupSlot.RD1,
            LineupSlot.LD2,
            LineupSlot.RD2,
            LineupSlot.C3,
            LineupSlot.LW3,
            LineupSlot.RW3,
            LineupSlot.LD3,
            LineupSlot.RD3,
            LineupSlot.G1,
            LineupSlot.G2
        )

        items(starterSlots) { slot ->
            val leftPlayer = leftStarters.firstOrNull { it.player.lineupSlot == slot }
                ?: leftStarters.getOrNull(starterSlots.indexOf(slot))
            val rightPlayer = rightStarters.firstOrNull { it.player.lineupSlot == slot }
                ?: rightStarters.getOrNull(starterSlots.indexOf(slot))

            SleeperSlotRow(
                slot = slot,
                leftPlayer = leftPlayer,
                rightPlayer = rightPlayer,
                isCompleted = activeMatchup?.isCompleted == true,
                onPlayerClick = onPlayerClick
            )
        }

        // 4. Bench Section
        if (leftBench.isNotEmpty() || rightBench.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Bench Reserves",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            val maxBench = maxOf(leftBench.size, rightBench.size)
            items(maxBench) { idx ->
                val leftB = leftBench.getOrNull(idx)
                val rightB = rightBench.getOrNull(idx)
                SleeperBenchRow(
                    slotIndex = idx + 1,
                    leftPlayer = leftB,
                    rightPlayer = rightB,
                    isCompleted = activeMatchup?.isCompleted == true,
                    onPlayerClick = onPlayerClick
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Sleeper Hero Scoreboard Card.
 * Exact match to user's uploaded Sleeper reference screenshot:
 * - Left avatar with green WIN% pill
 * - Center score with hockey icon divider
 * - Right avatar with red WIN% pill
 * - Team names & handles underneath
 * - Carousel dots
 */
@Composable
private fun SleeperMatchupHeroCard(
    matchup: FantasyMatchupEntity,
    leftTeam: FantasyTeamEntity,
    rightTeam: FantasyTeamEntity,
    leftScore: Double,
    rightScore: Double,
    leftWinPct: Int,
    rightWinPct: Int,
    isUserInMatchup: Boolean,
    matchupCount: Int,
    currentMatchupIndex: Int,
    onSelectMatchupIndex: (Int) -> Unit,
    canSimulate: Boolean,
    isSeasonFinished: Boolean,
    onSimulateWeek: () -> Unit,
    onStartNextSeason: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF131D31),
        border = BorderStroke(1.dp, Color(0xFF223555)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Avatars, Win % Pills & Center Scores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Team Avatar & Win % Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FranchiseCrestBadge(
                        emblem = TeamEmblem.fromId(leftTeam.emblemId),
                        primaryColorHex = leftTeam.primaryColorHex,
                        secondaryColorHex = leftTeam.secondaryColorHex,
                        abbreviation = leftTeam.abbreviation,
                        size = 44.dp
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF064E3B).copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, ProfitEmerald.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "$leftWinPct% WIN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = ProfitEmerald,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Center Scores & Mascot / Puck Divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (matchup.isCompleted) "FINAL" else "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7E8B9F),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(Locale.CANADA, "%.2f", leftScore),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (leftScore >= rightScore && matchup.isCompleted) ProfitEmerald else Color.White
                        )
                    }

                    // Small center divider icon
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🏒", fontSize = 11.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (matchup.isCompleted) "FINAL" else "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7E8B9F),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(Locale.CANADA, "%.2f", rightScore),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (rightScore > leftScore && matchup.isCompleted) ProfitEmerald else Color.White
                        )
                    }
                }

                // Right Team Avatar & Win % Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF4C0519).copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "$rightWinPct% WIN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = AlertRed,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    FranchiseCrestBadge(
                        emblem = TeamEmblem.fromId(rightTeam.emblemId),
                        primaryColorHex = rightTeam.primaryColorHex,
                        secondaryColorHex = rightTeam.secondaryColorHex,
                        abbreviation = rightTeam.abbreviation,
                        size = 44.dp
                    )
                }
            }

            // Team Names and Handles / Records
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = leftTeam.teamName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "@${leftTeam.gmDisplayName.ifBlank { "gm" }} • ${leftTeam.wins}-${leftTeam.losses} (#${leftTeam.teamId.takeLast(1).toIntOrNull()?.plus(1) ?: 1})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7E8B9F)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = rightTeam.teamName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "@${rightTeam.gmDisplayName.ifBlank { "cpu" }} • ${rightTeam.wins}-${rightTeam.losses} (#${rightTeam.teamId.takeLast(1).toIntOrNull()?.plus(1) ?: 2})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7E8B9F),
                        textAlign = TextAlign.End
                    )
                }
            }

            // Carousel Dot Indicators (Sleeper style)
            if (matchupCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(matchupCount) { idx ->
                        val isCurrent = idx == currentMatchupIndex
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (isCurrent) 7.dp else 5.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent) Color(0xFF00E5FF) else Color(0xFF334155))
                                .clickable { onSelectMatchupIndex(idx) }
                        )
                    }
                }
            }

            // Action Button: Simulate Matchup or Championship Launch
            if (isSeasonFinished) {
                Button(
                    onClick = onStartNextSeason,
                    colors = ButtonDefaults.buttonColors(containerColor = TrophyGold, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Season Champion Crowned • Launch Next Season", fontWeight = FontWeight.Bold)
                }
            } else if (canSimulate) {
                Button(
                    onClick = onSimulateWeek,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF001F29)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Simulate Week ${matchup.weekNumber} Games", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Clean Slot Row matching Sleeper's exact table:
 * Left Player | Left Score | Center Position Badge | Right Score | Right Player
 */
@Composable
private fun SleeperSlotRow(
    slot: LineupSlot,
    leftPlayer: ScoredPlayerItem?,
    rightPlayer: ScoredPlayerItem?,
    isCompleted: Boolean,
    onPlayerClick: (Long) -> Unit
) {
    val (posCode, badgeColor) = when {
        slot.code.startsWith("C") -> "C" to Color(0xFF10B981) // Mint Teal
        slot.code.startsWith("LW") -> "LW" to Color(0xFFF97316) // Coral
        slot.code.startsWith("RW") -> "RW" to Color(0xFFEC4899) // Magenta Pink
        slot.code.startsWith("LD") || slot.code.startsWith("RD") || slot.code.startsWith("D") -> "D" to Color(0xFF8B5CF6) // Purple
        slot.code.startsWith("G") -> "G" to Color(0xFFF59E0B) // Amber
        else -> "BN" to Color(0xFF475569) // Slate
    }

    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT PLAYER
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = leftPlayer != null) {
                        leftPlayer?.let { onPlayerClick(it.player.playerId) }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                ) {
                    if (leftPlayer != null) {
                        Text(
                            text = "${leftPlayer.player.firstName.take(1)}. ${leftPlayer.player.lastName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = posCode,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = badgeColor
                            )
                            Text(
                                text = " • ${leftPlayer.player.teamAbbrev} (${leftPlayer.player.sweaterNumber})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7E8B9F)
                            )
                            if (leftPlayer.player.injurySeverity != InjurySeverity.HEALTHY) {
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = if (leftPlayer.player.injurySeverity == InjurySeverity.DAY_TO_DAY) "DTD" else "IR",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = if (leftPlayer.player.injurySeverity == InjurySeverity.DAY_TO_DAY) WarningAmber else AlertRed
                                )
                            }
                        }
                        Text(
                            text = if (leftPlayer.player.todayOpponentLabel.isNotBlank()) leftPlayer.player.todayOpponentLabel else "Next Game",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "Empty Slot",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Left score
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = if (isCompleted) "F" else "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = if (leftPlayer != null) String.format(Locale.CANADA, "%.2f", leftPlayer.fantasy.seasonPoints) else "0.00",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // DEAD CENTER POSITION BADGE
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f)),
                modifier = Modifier
                    .width(36.dp)
                    .height(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = posCode,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = badgeColor
                    )
                }
            }

            // RIGHT PLAYER
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = rightPlayer != null) {
                        rightPlayer?.let { onPlayerClick(it.player.playerId) }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Right score
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = if (isCompleted) "F" else "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = if (rightPlayer != null) String.format(Locale.CANADA, "%.2f", rightPlayer.fantasy.seasonPoints) else "0.00",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (rightPlayer != null) {
                        Text(
                            text = "${rightPlayer.player.firstName.take(1)}. ${rightPlayer.player.lastName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (rightPlayer.player.injurySeverity != InjurySeverity.HEALTHY) {
                                Text(
                                    text = if (rightPlayer.player.injurySeverity == InjurySeverity.DAY_TO_DAY) "DTD" else "IR",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = if (rightPlayer.player.injurySeverity == InjurySeverity.DAY_TO_DAY) WarningAmber else AlertRed
                                )
                                Spacer(Modifier.width(3.dp))
                            }
                            Text(
                                text = posCode,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = badgeColor
                            )
                            Text(
                                text = " • ${rightPlayer.player.teamAbbrev} (${rightPlayer.player.sweaterNumber})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7E8B9F)
                            )
                        }
                        Text(
                            text = if (rightPlayer.player.todayOpponentLabel.isNotBlank()) rightPlayer.player.todayOpponentLabel else "Next Game",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    } else {
                        Text(
                            text = "Empty Slot",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bench Row with BN center badge.
 */
@Composable
private fun SleeperBenchRow(
    slotIndex: Int,
    leftPlayer: ScoredPlayerItem?,
    rightPlayer: ScoredPlayerItem?,
    isCompleted: Boolean,
    onPlayerClick: (Long) -> Unit
) {
    val badgeColor = Color(0xFF475569) // Slate gray for bench

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Bench Player
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = leftPlayer != null) {
                    leftPlayer?.let { onPlayerClick(it.player.playerId) }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                if (leftPlayer != null) {
                    Text(
                        text = "${leftPlayer.player.firstName.take(1)}. ${leftPlayer.player.lastName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${leftPlayer.player.position.uiShort} • ${leftPlayer.player.teamAbbrev}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7E8B9F)
                    )
                } else {
                    Text("—", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                }
            }

            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 6.dp)) {
                Text(
                    text = if (leftPlayer != null) String.format(Locale.CANADA, "%.2f", leftPlayer.fantasy.seasonPoints) else "0.00",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Center Bench Badge
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = badgeColor.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f)),
            modifier = Modifier
                .width(36.dp)
                .height(26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "BN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // Right Bench Player
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = rightPlayer != null) {
                    rightPlayer?.let { onPlayerClick(it.player.playerId) }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.padding(start = 6.dp)) {
                Text(
                    text = if (rightPlayer != null) String.format(Locale.CANADA, "%.2f", rightPlayer.fantasy.seasonPoints) else "0.00",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (rightPlayer != null) {
                    Text(
                        text = "${rightPlayer.player.firstName.take(1)}. ${rightPlayer.player.lastName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = "${rightPlayer.player.position.uiShort} • ${rightPlayer.player.teamAbbrev}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7E8B9F),
                        textAlign = TextAlign.End
                    )
                } else {
                    Text("—", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B), textAlign = TextAlign.End)
                }
            }
        }
    }
}
