package com.nhlfantasygm.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nhlfantasygm.data.local.*
import com.nhlfantasygm.data.repository.NhlFantasyRepository
import com.nhlfantasygm.domain.SeasonSimulationEngine
import com.nhlfantasygm.model.*
import com.nhlfantasygm.multiplayer.MultiplayerUiState
import com.nhlfantasygm.multiplayer.RoomCrypto
import com.nhlfantasygm.multiplayer.RoomRole
import com.nhlfantasygm.ui.LeagueSectionTab
import com.nhlfantasygm.ui.ScoredPlayerItem
import com.nhlfantasygm.ui.components.*
import com.nhlfantasygm.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun LeagueScreen(
    activeTab: LeagueSectionTab,
    onSelectTab: (LeagueSectionTab) -> Unit,
    fantasyTeams: List<FantasyTeamEntity>,
    humanTeam: FantasyTeamEntity?,
    controlledTeamId: String = NhlFantasyRepository.USER_TEAM_ID,
    mpState: MultiplayerUiState = MultiplayerUiState(),
    matchups: List<FantasyMatchupEntity>,
    seasonArchives: List<SeasonArchiveEntity>,
    tradeProposals: List<TradeProposalEntity>,
    allScoredPlayers: List<ScoredPlayerItem>,
    nhlTeams: List<NhlTeamEntity>,
    liveGames: List<LiveNhlGameEntity>,
    settings: UserAppSettings,
    isSyncing: Boolean,
    savedOptionalApiKey: String,
    onSimulateNextWeek: () -> Unit,
    onStartNextSeason: () -> Unit,
    onProposeTrade: (String, List<Long>, Long, List<Long>, Long) -> Unit,
    onSolicitCpuOffer: () -> Unit,
    onRespondToTrade: (Long, Boolean) -> Unit,
    onRefreshLiveScores: () -> Unit,
    onPlayerClick: (Long) -> Unit,
    onSaveTheme: (AppThemeMode) -> Unit,
    onSaveEconomy: (EconomyConfig, Boolean) -> Unit,
    onSaveScoring: (ScoringWeights) -> Unit,
    onToggleOfflineSim: (Boolean) -> Unit,
    onSaveOptionalApiKey: (String) -> Unit,
    onSaveCustomRelayUrl: (String) -> Unit = {},
    onLeaveMultiplayerRoom: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Horizontal Sub-Navigation Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LeagueSectionTab.entries.forEach { tab ->
                FilterChip(
                    selected = activeTab == tab,
                    onClick = { onSelectTab(tab) },
                    label = { Text(tab.label, fontWeight = FontWeight.Bold) }
                )
            }
        }

        when (activeTab) {
            LeagueSectionTab.MATCHUPS -> MatchupsAndPlayoffsSection(
                seasonNumber = settings.currentSeasonNumber,
                controlledTeamId = controlledTeamId,
                mpState = mpState,
                matchups = matchups,
                onSimulateNextWeek = onSimulateNextWeek,
                onStartNextSeason = onStartNextSeason
            )
            LeagueSectionTab.STANDINGS -> FantasyStandingsSection(
                fantasyTeams = fantasyTeams,
                controlledTeamId = controlledTeamId
            )
            LeagueSectionTab.TRADES -> TradesCenterSection(
                fantasyTeams = fantasyTeams,
                humanTeam = humanTeam,
                controlledTeamId = controlledTeamId,
                mpState = mpState,
                allScoredPlayers = allScoredPlayers,
                tradeProposals = tradeProposals,
                onProposeTrade = onProposeTrade,
                onSolicitOffer = onSolicitCpuOffer,
                onRespondOffer = onRespondToTrade,
                onPlayerClick = onPlayerClick
            )
            LeagueSectionTab.NHL_STATS -> NhlLiveStatisticsSection(
                liveGames = liveGames,
                nhlTeams = nhlTeams,
                allScoredPlayers = allScoredPlayers,
                isSyncing = isSyncing,
                onRefreshScores = onRefreshLiveScores,
                onPlayerClick = onPlayerClick
            )
            LeagueSectionTab.HISTORY -> AwardsAndHistorySection(
                allScoredPlayers = allScoredPlayers,
                fantasyTeams = fantasyTeams,
                weights = settings.scoringWeights,
                archives = seasonArchives
            )
            LeagueSectionTab.SETTINGS -> SettingsScreen(
                settings = settings,
                mpState = mpState,
                savedApiKey = savedOptionalApiKey,
                isSyncing = isSyncing,
                onSaveTheme = onSaveTheme,
                onSaveEconomy = onSaveEconomy,
                onSaveScoring = onSaveScoring,
                onToggleOfflineSim = onToggleOfflineSim,
                onSaveApiKey = onSaveOptionalApiKey,
                onSaveCustomRelayUrl = onSaveCustomRelayUrl,
                onLeaveMultiplayerRoom = onLeaveMultiplayerRoom
            )
        }
    }
}

@Composable
private fun MatchupsAndPlayoffsSection(
    seasonNumber: Int,
    controlledTeamId: String,
    mpState: MultiplayerUiState,
    matchups: List<FantasyMatchupEntity>,
    onSimulateNextWeek: () -> Unit,
    onStartNextSeason: () -> Unit
) {
    val nextWeek = matchups.filter { !it.isCompleted }.minOfOrNull { it.weekNumber }
    val displayWeek = nextWeek ?: (matchups.maxOfOrNull { it.weekNumber } ?: 1)
    var selectedWeek by remember(displayWeek) { mutableIntStateOf(displayWeek) }

    val availableWeeks = matchups.map { it.weekNumber }.distinct().sorted()
    val weekMatchups = matchups.filter { it.weekNumber == selectedWeek }
    val playoffMatchups = matchups.filter { it.stage != "REGULAR_SEASON" }
    val seasonComplete = matchups.isNotEmpty() && matchups.all { it.isCompleted } &&
        matchups.any { it.stage == "CHAMPIONSHIP_FINAL" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, IceCyan.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fantasy Season $seasonNumber Simulator",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Simulated fantasy matchups calculated from real NHL player statistics (distinct from actual NHL games).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (seasonComplete) {
                        Button(
                            onClick = onStartNextSeason,
                            enabled = mpState.role != RoomRole.GUEST,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = TrophyGold, contentColor = androidx.compose.ui.graphics.Color.Black)
                        ) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (mpState.role == RoomRole.GUEST) "Waiting on Host for Season ${seasonNumber + 1}" else "Launch Fantasy Season ${seasonNumber + 1}",
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    } else if (nextWeek != null) {
                        val stageTitle = when (nextWeek) {
                            SeasonSimulationEngine.SEMIFINAL_WEEK -> "Playoff Semifinals (Week 15)"
                            SeasonSimulationEngine.CHAMPIONSHIP_WEEK -> "Stanley Fantasy Cup Final (Week 16)"
                            else -> "Regular Season Week $nextWeek of 14"
                        }
                        Button(
                            onClick = onSimulateNextWeek,
                            enabled = mpState.role != RoomRole.GUEST,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FastForward, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (mpState.role == RoomRole.GUEST) "Waiting on Host Commissioner ($stageTitle)" else "Simulate $stageTitle",
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Playoff Bracket Card once Week 15+ exists
        if (playoffMatchups.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.2.dp, TrophyGold.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Playoff Championship Bracket",
                            style = MaterialTheme.typography.titleMedium,
                            color = TrophyGold,
                            fontWeight = FontWeight.ExtraBold
                        )
                        playoffMatchups.forEach { pm ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${if (pm.stage == "CHAMPIONSHIP_FINAL") "🏆 FINAL" else "SEMIFINAL"}: ${pm.awayTeamName} vs ${pm.homeTeamName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (pm.isCompleted) "${pm.awayScore} - ${pm.homeScore}" else "Upcoming",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (pm.isCompleted) ProfitEmerald else IceCyan
                                )
                            }
                        }
                    }
                }
            }
        }

        // Week Picker Row
        if (availableWeeks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableWeeks.forEach { wk ->
                        val label = when (wk) {
                            15 -> "Semifinals"
                            16 -> "Final"
                            else -> "Wk $wk"
                        }
                        FilterChip(
                            selected = selectedWeek == wk,
                            onClick = { selectedWeek = wk },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        items(weekMatchups, key = { it.matchupId }) { m ->
            val involvesUser = m.homeTeamId == controlledTeamId || m.awayTeamId == controlledTeamId
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (involvesUser) ProfitEmerald.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "WEEK ${m.weekNumber} • ${m.stage.replace("_", " ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = IceCyan,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = when (m.scoringModeLabel) {
                                "REAL_NHL_STATS" -> "REAL NHL STATS"
                                "OFFLINE_SIM" -> "OFFLINE SIM MODE"
                                else -> "SCHEDULED"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (m.scoringModeLabel == "REAL_NHL_STATS") ProfitEmerald else TrophyGold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(m.awayTeamName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (m.isCompleted) String.format(Locale.CANADA, "%.1f", m.awayScore) else "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(m.homeTeamName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (m.isCompleted) String.format(Locale.CANADA, "%.1f", m.homeScore) else "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (m.highlightSummary.isNotBlank()) {
                        Text(
                            text = "Top Performers: ${m.highlightSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FantasyStandingsSection(
    fantasyTeams: List<FantasyTeamEntity>,
    controlledTeamId: String
) {
    val ranked = remember(fantasyTeams) { SeasonSimulationEngine.rankStandings(fantasyTeams) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text("Fantasy League Standings (Top 4 Qualify for Playoffs)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(ranked.indices.toList(), key = { ranked[it].teamId }) { idx ->
            val t = ranked[idx]
            val inPlayoffs = idx < 4
            val isMe = t.teamId == controlledTeamId
            val isFriend = !isMe && !t.isCpuGm
            val ownerTag = when {
                isMe -> " (You)"
                t.isRemoteHumanGm -> " (Online Friend${if (t.gmDisplayName.isNotBlank()) ": GM ${t.gmDisplayName}" else ""})"
                t.isLocalPassAndPlayGm -> " (Local Friend${if (t.gmDisplayName.isNotBlank()) ": GM ${t.gmDisplayName}" else ""})"
                isFriend -> " (Friend${if (t.gmDisplayName.isNotBlank()) ": GM ${t.gmDisplayName}" else ""})"
                else -> " (CPU GM)"
            }
            val borderColor = when {
                isMe -> ProfitEmerald
                isFriend -> IceCyan
                else -> MaterialTheme.colorScheme.outline
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (isMe || isFriend) 1.5.dp else 1.dp,
                    color = borderColor
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "#${idx + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (inPlayoffs) TrophyGold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FranchiseCrestBadge(
                        emblem = TeamEmblem.fromId(t.emblemId),
                        primaryColorHex = t.primaryColorHex,
                        secondaryColorHex = t.secondaryColorHex,
                        abbreviation = t.abbreviation,
                        size = 38.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${t.teamName}$ownerTag",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isMe) ProfitEmerald else if (isFriend) IceCyan else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "PF ${String.format(Locale.CANADA, "%.1f", t.pointsFor)} • PA ${String.format(Locale.CANADA, "%.1f", t.pointsAgainst)}" +
                                (if (inPlayoffs) " • Playoff Seed" else ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${t.wins}-${t.losses}-${t.ties}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${t.wins * 2 + t.ties} PTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = IceCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TradesCenterSection(
    fantasyTeams: List<FantasyTeamEntity>,
    humanTeam: FantasyTeamEntity?,
    controlledTeamId: String,
    mpState: MultiplayerUiState,
    allScoredPlayers: List<ScoredPlayerItem>,
    tradeProposals: List<TradeProposalEntity>,
    onProposeTrade: (String, List<Long>, Long, List<Long>, Long) -> Unit,
    onSolicitOffer: () -> Unit,
    onRespondOffer: (Long, Boolean) -> Unit,
    onPlayerClick: (Long) -> Unit
) {
    val opposingTeams = remember(fantasyTeams, controlledTeamId) {
        fantasyTeams.filter { it.teamId != controlledTeamId }
    }
    var selectedTargetId by remember(opposingTeams) { mutableStateOf(opposingTeams.firstOrNull()?.teamId ?: "") }
    val selectedTargetTeam = opposingTeams.firstOrNull { it.teamId == selectedTargetId }
    var offeredIds by remember(selectedTargetId) { mutableStateOf(setOf<Long>()) }
    var requestedIds by remember(selectedTargetId) { mutableStateOf(setOf<Long>()) }
    var offeredCashMillions by remember(selectedTargetId) { mutableFloatStateOf(0f) }
    var requestedCashMillions by remember(selectedTargetId) { mutableFloatStateOf(0f) }

    val userRoster = remember(allScoredPlayers, controlledTeamId) {
        allScoredPlayers.filter { it.player.ownerFantasyTeamId == controlledTeamId }
            .sortedByDescending { it.player.overallRating }
    }
    val targetRoster = remember(allScoredPlayers, selectedTargetId) {
        allScoredPlayers.filter { it.player.ownerFantasyTeamId == selectedTargetId }
            .sortedByDescending { it.player.overallRating }
    }

    val isHostOrSolo = mpState.role != RoomRole.GUEST
    val pendingIncoming = tradeProposals.filter { prop ->
        val receiver = fantasyTeams.firstOrNull { it.teamId == prop.receiverTeamId }
        prop.status == "PENDING" && (
            prop.receiverTeamId == controlledTeamId ||
                (isHostOrSolo && receiver?.isLocalPassAndPlayGm == true)
            )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("NHL GM Trade Desk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = if (mpState.isMultiplayerActive) "Live player & Virtual CAD trades with Real Friends & CPU GMs"
                        else "Player-for-player & Virtual CAD trades with CPU GMs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (mpState.role != RoomRole.GUEST && opposingTeams.any { it.isCpuGm }) {
                    FilledTonalButton(onClick = onSolicitOffer) {
                        Text("Solicit CPU Offer")
                    }
                }
            }
        }

        // Incoming Pending Trade Proposals
        if (pendingIncoming.isNotEmpty()) {
            item {
                Text("Incoming Trade Proposals (${pendingIncoming.size})", style = MaterialTheme.typography.titleMedium, color = TrophyGold, fontWeight = FontWeight.Bold)
            }
            items(pendingIncoming, key = { it.tradeId }) { prop ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.4.dp, TrophyGold)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${prop.proposerTeamName} → ${prop.receiverTeamName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        Text(
                            text = "Offers: ${prop.offeredPlayersSummary}" + (if (prop.offeredCashCad > 0) " + ${CadFormatter.formatCompact(prop.offeredCashCad)}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = ProfitEmerald
                        )
                        Text(
                            text = "Requests: ${prop.requestedPlayersSummary}" + (if (prop.requestedCashCad > 0) " + ${CadFormatter.formatCompact(prop.requestedCashCad)}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = IceCyan
                        )
                        Text(prop.gmEvaluationReason, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onRespondOffer(prop.tradeId, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = ProfitEmerald)
                            ) {
                                Text("Accept Trade", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = { onRespondOffer(prop.tradeId, false) }) {
                                Text("Reject")
                            }
                        }
                    }
                }
            }
        }

        // Interactive Trade Builder
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Propose New Trade", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Text("1. Target Franchise (Friends & CPU GMs)", style = MaterialTheme.typography.labelLarge, color = IceCyan)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        opposingTeams.forEach { target ->
                            val tag = if (!target.isCpuGm) "Friend" else "CPU"
                            FilterChip(
                                selected = selectedTargetId == target.teamId,
                                onClick = { selectedTargetId = target.teamId },
                                label = {
                                    Text("${target.teamName} • $tag (${CadFormatter.formatShortNoSuffix(target.cashBalanceCad)})")
                                }
                            )
                        }
                    }

                    Text("2. Players You Offer (${offeredIds.size} selected)", style = MaterialTheme.typography.labelLarge, color = ProfitEmerald)
                    if (userRoster.isEmpty()) {
                        Text("Your roster is empty. Sign or draft players first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            userRoster.forEach { item ->
                                val p = item.player
                                val isSel = p.playerId in offeredIds
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        offeredIds = if (isSel) offeredIds - p.playerId
                                        else if (offeredIds.size < 3) offeredIds + p.playerId else offeredIds
                                    },
                                    label = { Text("${p.lastName} (${p.position.uiShort} ${p.overallRating})") }
                                )
                            }
                        }
                    }

                    val maxUserCashM = ((humanTeam?.cashBalanceCad ?: 0L) / 1_000_000f).coerceAtMost(25f)
                    if (maxUserCashM >= 0.5f) {
                        Text("Include Virtual Cash Offered: $${String.format(Locale.CANADA, "%.1f", offeredCashMillions)}M CAD", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = offeredCashMillions,
                            onValueChange = { offeredCashMillions = (it * 2).roundToInt() / 2f },
                            valueRange = 0f..maxUserCashM
                        )
                    }

                    Text("3. Players You Request (${requestedIds.size} selected)", style = MaterialTheme.typography.labelLarge, color = TrophyGold)
                    if (targetRoster.isEmpty()) {
                        Text(
                            text = if (selectedTargetTeam?.isCpuGm == false) "This friend's roster is currently empty."
                            else "CPU team roster will auto-populate when you submit or solicit an offer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            targetRoster.forEach { item ->
                                val p = item.player
                                val isSel = p.playerId in requestedIds
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        requestedIds = if (isSel) requestedIds - p.playerId
                                        else if (requestedIds.size < 3) requestedIds + p.playerId else requestedIds
                                    },
                                    label = { Text("${p.lastName} (${p.position.uiShort} ${p.overallRating})") }
                                )
                            }
                        }
                    }

                    Text("Request Virtual Cash: $${String.format(Locale.CANADA, "%.1f", requestedCashMillions)}M CAD", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = requestedCashMillions,
                        onValueChange = { requestedCashMillions = (it * 2).roundToInt() / 2f },
                        valueRange = 0f..15f
                    )

                    val offeredValue = userRoster.filter { it.player.playerId in offeredIds }.sumOf { it.player.purchasePriceCad } +
                        (offeredCashMillions * 1_000_000L).toLong()
                    val reqValue = targetRoster.filter { it.player.playerId in requestedIds }.sumOf { it.player.purchasePriceCad } +
                        (requestedCashMillions * 1_000_000L).toLong()

                    Text(
                        text = "Est. Market Package: You Offer ${CadFormatter.formatCompact(offeredValue)} vs Request ${CadFormatter.formatCompact(reqValue)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val isTargetHumanFriend = selectedTargetTeam != null && !selectedTargetTeam.isCpuGm
                    Button(
                        onClick = {
                            onProposeTrade(
                                selectedTargetId,
                                offeredIds.toList(),
                                (offeredCashMillions * 1_000_000L).toLong(),
                                requestedIds.toList(),
                                (requestedCashMillions * 1_000_000L).toLong()
                            )
                            offeredIds = emptySet()
                            requestedIds = emptySet()
                            offeredCashMillions = 0f
                            requestedCashMillions = 0f
                        },
                        enabled = selectedTargetId.isNotBlank() && (offeredIds.isNotEmpty() || requestedIds.isNotEmpty()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isTargetHumanFriend) "Send Live Trade Offer to ${selectedTargetTeam?.teamName}"
                            else "Submit Trade Proposal to CPU GM",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Trade History
        if (tradeProposals.isNotEmpty()) {
            item {
                Text("Evaluated & Completed Trades (${tradeProposals.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(tradeProposals, key = { it.tradeId }) { tr ->
                val tone = when (tr.status) {
                    "ACCEPTED" -> ProfitEmerald
                    "REJECTED" -> AlertRed
                    else -> TrophyGold
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("${tr.proposerTeamName} ↔ ${tr.receiverTeamName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(tr.status, style = MaterialTheme.typography.labelMedium, color = tone, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(
                            text = "Offered: ${tr.offeredPlayersSummary}" + (if (tr.offeredCashCad > 0) " + ${CadFormatter.formatCompact(tr.offeredCashCad)}" else ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Requested: ${tr.requestedPlayersSummary}" + (if (tr.requestedCashCad > 0) " + ${CadFormatter.formatCompact(tr.requestedCashCad)}" else ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = tr.gmEvaluationReason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NhlLiveStatisticsSection(
    liveGames: List<LiveNhlGameEntity>,
    nhlTeams: List<NhlTeamEntity>,
    allScoredPlayers: List<ScoredPlayerItem>,
    isSyncing: Boolean,
    onRefreshScores: () -> Unit,
    onPlayerClick: (Long) -> Unit
) {
    var mode by remember { mutableIntStateOf(0) } // 0=Fantasy Leaders, 1=Today NHL Games, 2=32-Team NHL Standings

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("NHL Leaders") })
                FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Games (${liveGames.size})") })
                FilterChip(selected = mode == 2, onClick = { mode = 2 }, label = { Text("32 NHL Teams") })
            }
            IconButton(onClick = onRefreshScores, enabled = !isSyncing) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Live Scores", tint = IceCyan)
            }
        }

        when (mode) {
            0 -> {
                val leaders = remember(allScoredPlayers) {
                    allScoredPlayers.sortedByDescending { it.fantasy.seasonPoints }.take(40)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(leaders.indices.toList(), key = { leaders[it].player.playerId }) { idx ->
                        val item = leaders[idx]
                        val p = item.player
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayerClick(p.playerId) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("#${idx + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = IceCyan)
                                PlayerHeadshot(p.headshotUrl, p.fullName, p.teamAbbrev, p.sweaterNumber, size = 38.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${p.fullName} (${p.teamAbbrev} • ${p.position.uiShort})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    val stats = if (p.position == Position.GOALIE) {
                                        "${p.gamesPlayed} GP • ${p.wins}W • ${String.format(Locale.CANADA, "%.3f", p.savePct)} SV% • ${p.shutouts} SO"
                                    } else {
                                        "${p.gamesPlayed} GP • ${p.goals}G ${p.assists}A ${p.points}P • ${p.powerPlayPoints} PPP • ${p.shots} SOG"
                                    }
                                    Text(stats, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${item.fantasy.seasonPoints} FP", style = MaterialTheme.typography.titleSmall, color = ProfitEmerald, fontWeight = FontWeight.ExtraBold)
                                    if (item.fantasy.dailyPoints > 0) {
                                        Text("+${item.fantasy.dailyPoints} Today", style = MaterialTheme.typography.labelSmall, color = IceCyan)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (liveGames.isEmpty()) {
                        item {
                            Text("No NHL games on today's scoreboard.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(liveGames, key = { it.gameId }) { g ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (g.isLive) ProfitEmerald else MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text(
                                        text = if (g.isLive) "● LIVE — ${g.periodLabel} ${g.clockLabel}" else "${g.periodLabel} (${g.gameDate})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (g.isLive) ProfitEmerald else IceCyan,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(g.venueName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        NhlTeamBadge(g.awayAbbrev, g.awayLogoUrl, 30.dp)
                                        Text("${g.awayName} (${g.awaySog} SOG)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    }
                                    Text("${g.awayScore}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                                }
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        NhlTeamBadge(g.homeAbbrev, g.homeLogoUrl, 30.dp)
                                        Text("${g.homeName} (${g.homeSog} SOG)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    }
                                    Text("${g.homeScore}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(nhlTeams, key = { it.abbrev }) { team ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                NhlTeamBadge(team.abbrev, team.logoDarkUrl, size = 34.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("#${team.leagueSequence} ${team.fullName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("${team.conferenceName} • ${team.divisionName} • GF ${team.goalsFor} / GA ${team.goalsAgainst}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${team.wins}-${team.losses}-${team.otLosses}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text("${team.points} PTS (${team.streakCode}${team.streakCount})", style = MaterialTheme.typography.labelSmall, color = IceCyan)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AwardsAndHistorySection(
    allScoredPlayers: List<ScoredPlayerItem>,
    fantasyTeams: List<FantasyTeamEntity>,
    weights: ScoringWeights,
    archives: List<SeasonArchiveEntity>
) {
    val liveAwards = remember(allScoredPlayers, fantasyTeams, weights) {
        val leader = SeasonSimulationEngine.rankStandings(fantasyTeams).firstOrNull() ?: return@remember null
        SeasonSimulationEngine.computeSeasonAwards(
            allPlayers = allScoredPlayers.map { it.player },
            championTeam = leader,
            weights = weights
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (liveAwards != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, TrophyGold.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Current Season Trophy Race", style = MaterialTheme.typography.titleMedium, color = TrophyGold, fontWeight = FontWeight.ExtraBold)
                        AwardRow("Hart Memorial MVP", liveAwards.hartMvp)
                        AwardRow("Vezina Top Goaltender", liveAwards.vezinaGoalie)
                        AwardRow("James Norris Top Defenseman", liveAwards.norrisDefense)
                        AwardRow("Calder Memorial Top Rookie", liveAwards.calderRookie)
                        AwardRow("Leading Fantasy GM", liveAwards.gmOfTheYear)
                    }
                }
            }
        }

        item {
            Text("Historical Season Records (${archives.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (archives.isEmpty()) {
            item {
                Text(
                    text = "Complete Week 16 (Stanley Fantasy Cup Final) to permanently archive your season banner & trophy winners.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(archives, key = { it.archiveId }) { arch ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, TrophyGold)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "🏆 Season ${arch.seasonNumber} (${arch.nhlSeasonLabel}) — Champion: ${arch.championTeamName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TrophyGold,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text("Runner-Up: ${arch.runnerUpTeamName} • Your Record: ${arch.userRecord} (${arch.userPointsFor} PF)", style = MaterialTheme.typography.bodySmall)
                        AwardRow("Hart MVP", arch.hartMvpWinner)
                        AwardRow("Vezina", arch.vezinaGoalieWinner)
                        AwardRow("Norris", arch.norrisDefenseWinner)
                        AwardRow("Calder", arch.calderRookieWinner)
                    }
                }
            }
        }
    }
}

@Composable
private fun AwardRow(title: String, winner: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(winner, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}


