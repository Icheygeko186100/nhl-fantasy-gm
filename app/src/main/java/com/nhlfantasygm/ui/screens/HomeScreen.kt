package com.nhlfantasygm.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nhlfantasygm.data.local.*
import com.nhlfantasygm.domain.SeasonSimulationEngine
import com.nhlfantasygm.domain.TeamFinancialSummary
import com.nhlfantasygm.domain.TeamRosterAnalytics
import com.nhlfantasygm.model.CadFormatter
import com.nhlfantasygm.model.DataFreshnessState
import com.nhlfantasygm.model.TeamEmblem
import com.nhlfantasygm.multiplayer.MultiplayerUiState
import com.nhlfantasygm.multiplayer.RoomCrypto
import com.nhlfantasygm.multiplayer.RoomRole
import com.nhlfantasygm.ui.LeagueSectionTab
import com.nhlfantasygm.ui.ScoredPlayerItem
import com.nhlfantasygm.ui.components.*
import com.nhlfantasygm.ui.theme.*
import java.util.Locale
import kotlin.math.abs

@Composable
fun HomeScreen(
    humanTeam: FantasyTeamEntity?,
    financialSummary: TeamFinancialSummary?,
    rosterAnalytics: TeamRosterAnalytics,
    freshnessState: DataFreshnessState,
    syncStatus: SyncStatusEntity?,
    isSyncing: Boolean,
    liveGames: List<LiveNhlGameEntity>,
    allScoredPlayers: List<ScoredPlayerItem>,
    fantasyTeams: List<FantasyTeamEntity>,
    matchups: List<FantasyMatchupEntity>,
    newsHeadlines: List<NewsHeadlineEntity>,
    mpState: MultiplayerUiState = MultiplayerUiState(),
    onRefreshData: () -> Unit,
    onPlayerClick: (Long) -> Unit,
    onNavigateMainTab: (Int) -> Unit,
    onNavigateLeagueSubTab: (LeagueSectionTab) -> Unit,
    onSimulateNextWeek: () -> Unit
) {
    val context = LocalContext.current
    val topPerformers = allScoredPlayers
        .sortedWith(
            compareByDescending<ScoredPlayerItem> { it.fantasy.dailyPoints > 0 }
                .thenByDescending { it.fantasy.dailyPoints }
                .thenByDescending { it.fantasy.seasonPoints }
        )
        .take(8)

    val priceMovers = allScoredPlayers
        .filter { it.player.priceDeltaCad != 0L }
        .sortedByDescending { abs(it.player.priceDeltaCad) }
        .take(6)

    val nextUserMatchup = matchups
        .filter { !it.isCompleted && (it.homeTeamId == humanTeam?.teamId || it.awayTeamId == humanTeam?.teamId) }
        .minByOrNull { it.weekNumber }

    val rankedStandings = SeasonSimulationEngine.rankStandings(fantasyTeams).take(4)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Sleeper Matchup Spotlight (Top Hero Card)
        if (nextUserMatchup != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.2.dp, IceCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateMainTab(1) }
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "WEEK ${nextUserMatchup.weekNumber} MATCHUP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = IceCyan
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = nextUserMatchup.stage.replace("_", " "),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${nextUserMatchup.awayTeamName} vs ${nextUserMatchup.homeTeamName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (nextUserMatchup.isCompleted) {
                                        "Final: ${String.format(Locale.CANADA, "%.1f", nextUserMatchup.awayScore)} - ${String.format(Locale.CANADA, "%.1f", nextUserMatchup.homeScore)}"
                                    } else "Scheduled Showdown • Head-to-Head",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (nextUserMatchup.isCompleted) ProfitEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onNavigateMainTab(1) },
                                    colors = ButtonDefaults.buttonColors(containerColor = IceCyan, contentColor = Color(0xFF001F29))
                                ) {
                                    Text("Matchup", fontWeight = FontWeight.Bold)
                                }
                                if (!nextUserMatchup.isCompleted) {
                                    FilledTonalButton(onClick = onSimulateNextWeek) {
                                        Text("Sim")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Active Multiplayer Room Pill (if hosting or joined)
        if (mpState.isMultiplayerActive) {
            item {
                val formattedCode = RoomCrypto.formatInviteCodeForDisplay(mpState.roomCode)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateMainTab(5) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, ProfitEmerald)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🟢 ROOM $formattedCode • ${mpState.totalHumanParticipantsCount} Human GMs",
                            style = MaterialTheme.typography.labelMedium,
                            color = ProfitEmerald,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Draft Board →",
                            style = MaterialTheme.typography.labelSmall,
                            color = IceCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }


        // 4. Real NHL Scoreboard Strip (Today's Real Games)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's NHL Games",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (syncStatus?.isOffseason == true) "Offseason" else if (isSyncing) "Updating..." else "Live NHL Feed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSyncing) IceCyan else ProfitEmerald
                )
            }
            if (liveGames.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (isSyncing) "Loading live NHL schedule..."
                        else "No NHL games scheduled today.",
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(liveGames, key = { it.gameId }) { game ->
                        LiveNhlGameCard(
                            game = game,
                            onClick = {
                                onNavigateLeagueSubTab(LeagueSectionTab.NHL_STATS)
                                onNavigateMainTab(4)
                            }
                        )
                    }
                }
            }
        }

        // 5. League Standings Preview
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.clickable { onNavigateMainTab(4) }
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "League Standings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Full Table →",
                            style = MaterialTheme.typography.labelSmall,
                            color = IceCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    rankedStandings.forEachIndexed { idx, t ->
                        val isMe = t.teamId == humanTeam?.teamId
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}. ${t.teamName}${if (isMe) " (You)" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isMe) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isMe) ProfitEmerald else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${t.wins}-${t.losses}-${t.ties} • ${String.format(Locale.CANADA, "%.1f", t.pointsFor)} PF",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        // 6. Top Fantasy Performers Strip
        item {
            Text(
                text = "Trending Performers",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(topPerformers, key = { it.player.playerId }) { item ->
                    TopPerformerCompactCard(
                        item = item,
                        onClick = { onPlayerClick(item.player.playerId) }
                    )
                }
            }
        }

        // 7. Franchise Treasury Snapshot (Clean, compact card)
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateMainTab(2) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Franchise Cap & Cash",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${CadFormatter.formatCompact(humanTeam?.cashBalanceCad ?: 100_000_000L)} Cash • ${CadFormatter.formatCompact(financialSummary?.remainingCapSpaceCad ?: 7_300_000L)} Cap Room",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Roster →",
                        style = MaterialTheme.typography.labelSmall,
                        color = IceCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 7. Recent Player Market Price Changes
        if (priceMovers.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Market Valuation Movers",
                    subtitle = "Transparent price adjustments from NHL performance & availability"
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        priceMovers.forEach { item ->
                            val p = item.player
                            val isUp = p.priceDeltaCad > 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayerClick(p.playerId) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PlayerHeadshot(p.headshotUrl, p.fullName, p.teamAbbrev, p.sweaterNumber, size = 38.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${p.fullName} (${p.teamAbbrev} • ${p.position.uiShort})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = p.priceChangeReason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = CadFormatter.formatShortNoSuffix(p.purchasePriceCad),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = CadFormatter.formatShortNoSuffix(p.priceDeltaCad, showPlusSign = true),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isUp) ProfitEmerald else AlertRed,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8. Real NHL News Headlines
        item {
            SectionHeader(
                title = "NHL News Headlines",
                subtitle = if (newsHeadlines.isNotEmpty()) "Live hockey headlines via public ESPN NHL desk"
                else "Headlines unavailable offline"
            )
            if (newsHeadlines.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "News feed currently unavailable. Refresh while online to load real NHL headlines.",
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    newsHeadlines.take(5).forEach { article ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = article.webUrl.isNotBlank()) {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.webUrl)))
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = article.headline,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (article.description.isNotBlank() && article.description != article.headline) {
                                    Text(
                                        text = article.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${article.sourceLabel} • ${article.publishedAt.take(10)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FranchiseHeroDashboardCard(
    team: FantasyTeamEntity?,
    financials: TeamFinancialSummary?,
    analytics: TeamRosterAnalytics,
    onMyTeamClick: () -> Unit
) {
    val cash = financials?.cashBalanceCad ?: team?.cashBalanceCad ?: 100_000_000L
    val rosterVal = financials?.totalRosterMarketValueCad ?: 0L
    val payroll = financials?.currentPayrollCad ?: 0L
    val cap = financials?.salaryCapCad ?: 95_500_000L
    val capProgress = if (cap > 0) (payroll.toFloat() / cap.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMyTeamClick() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FranchiseCrestBadge(
                    emblem = TeamEmblem.fromId(team?.emblemId),
                    primaryColorHex = team?.primaryColorHex ?: 0xFF00C2FF,
                    secondaryColorHex = team?.secondaryColorHex ?: 0xFF0B192C,
                    abbreviation = team?.abbreviation ?: "NIC",
                    size = 54.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = team?.teamName ?: "Northern Ice HC",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "GM Franchise • Record ${team?.wins ?: 0}-${team?.losses ?: 0}-${team?.ties ?: 0} • ${analytics.totalRosterCount} Players",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OvrBadge(ovr = analytics.overallTeamRating)
                    Text("TEAM OVR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Virtual Cash Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CadFormatter.formatFull(cash),
                        style = MaterialTheme.typography.titleLarge,
                        color = ProfitEmerald,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Roster Value", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CadFormatter.formatCompact(rosterVal),
                        style = MaterialTheme.typography.titleMedium,
                        color = IceCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Team Payroll: ${CadFormatter.formatCompact(payroll)} / ${CadFormatter.formatCompact(cap)} Cap",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val pnl = (financials?.realizedProfitLossCad ?: 0L) + (financials?.unrealizedProfitLossCad ?: 0L)
                    Text(
                        text = "Net P&L: ${CadFormatter.formatShortNoSuffix(pnl, showPlusSign = true)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pnl >= 0) ProfitEmerald else AlertRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                LinearProgressIndicator(
                    progress = { capProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (capProgress > 0.94f) WarningAmber else IceCyan,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickNavButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ElevatedAssistChip(
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Bold) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
}

@Composable
private fun LiveNhlGameCard(
    game: LiveNhlGameEntity,
    onClick: () -> Unit
) {
    val borderColor = if (game.isLive) ProfitEmerald else MaterialTheme.colorScheme.outline
    Surface(
        modifier = Modifier
            .width(195.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (game.isLive) "● LIVE ${game.periodLabel} ${game.clockLabel}" else game.periodLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (game.isLive) ProfitEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = game.gameDate.takeLast(5),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NhlTeamBadge(game.awayAbbrev, game.awayLogoUrl, size = 26.dp)
                    Text("${game.awayAbbrev} (${game.awaySog} SOG)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Text("${game.awayScore}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NhlTeamBadge(game.homeAbbrev, game.homeLogoUrl, size = 26.dp)
                    Text("${game.homeAbbrev} (${game.homeSog} SOG)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Text("${game.homeScore}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun TopPerformerCompactCard(
    item: ScoredPlayerItem,
    onClick: () -> Unit
) {
    val p = item.player
    val f = item.fantasy
    Surface(
        modifier = Modifier
            .width(168.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerHeadshot(p.headshotUrl, p.fullName, p.teamAbbrev, p.sweaterNumber, size = 40.dp)
                OvrBadge(p.overallRating)
            }
            Text(
                text = p.fullName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${p.teamAbbrev} • ${p.position.uiShort} • ${CadFormatter.formatShortNoSuffix(p.purchasePriceCad)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${f.seasonPoints} Season FP",
                    style = MaterialTheme.typography.labelMedium,
                    color = ProfitEmerald,
                    fontWeight = FontWeight.ExtraBold
                )
                if (f.dailyPoints > 0) {
                    Text(
                        text = "+${f.dailyPoints} Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = IceCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
