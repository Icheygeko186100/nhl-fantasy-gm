package com.nhlfantasygm.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nhlfantasygm.data.repository.NhlFantasyRepository
import com.nhlfantasygm.model.CadFormatter
import com.nhlfantasygm.model.InjurySeverity
import com.nhlfantasygm.model.Position
import com.nhlfantasygm.ui.PlayerDetailDialogState
import com.nhlfantasygm.ui.components.*
import com.nhlfantasygm.ui.theme.*
import java.util.Locale

@Composable
fun PlayerDetailDialog(
    detail: PlayerDetailDialogState,
    controlledTeamId: String = NhlFantasyRepository.USER_TEAM_ID,
    onDismiss: () -> Unit,
    onBuyClick: () -> Unit,
    onSellClick: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onJumpToTradeWithOwner: (String) -> Unit
) {
    val p = detail.player
    val f = detail.fantasy
    val v = detail.valuation
    val isOwnedByUser = p.ownerFantasyTeamId == controlledTeamId
    val isOwnedByCpu = p.ownerFantasyTeamId != null && !isOwnedByUser

    // Read system insets from host window to protect against 3-button navigation bar overlap
    val hostNavInsets = WindowInsets.navigationBars.asPaddingValues()
    val hostSystemInsets = WindowInsets.systemBars.asPaddingValues()
    val hostBottomInset = maxOf(hostNavInsets.calculateBottomPadding(), hostSystemInsets.calculateBottomPadding())
    val hostTopInset = maxOf(hostSystemInsets.calculateTopPadding(), WindowInsets.statusBars.asPaddingValues().calculateTopPadding())

    // Ensure ample clearance above Samsung 3-button nav bar (typically 48dp on Galaxy devices)
    val safeBottomPadding = maxOf(hostBottomInset, 48.dp) + 16.dp
    val safeTopPadding = maxOf(hostTopInset, 24.dp) + 8.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = safeTopPadding,
                    bottom = safeBottomPadding
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close Profile")
                        }
                        Text(
                            text = "NHL Player Scouting Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onToggleWatchlist) {
                        Icon(
                            imageVector = if (p.isWatchlisted) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Watchlist",
                            tint = if (p.isWatchlisted) TrophyGold else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Bio Hero Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                PlayerHeadshot(
                                    headshotUrl = p.headshotUrl,
                                    fullName = p.fullName,
                                    teamAbbrev = p.teamAbbrev,
                                    sweaterNumber = p.sweaterNumber,
                                    size = 70.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        PositionPill(p.position)
                                        Text(
                                            text = if (p.sweaterNumber > 0) "#${p.sweaterNumber} • ${p.teamAbbrev}" else p.teamAbbrev,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (p.isProspect) {
                                            Badge(containerColor = RoyalBlue) { Text("PROSPECT") }
                                        } else if (p.isRookie) {
                                            Badge(containerColor = ProfitEmerald) { Text("ROOKIE") }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = p.fullName,
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Text(
                                        text = "${p.teamFullName} • Age ${p.age} • ${p.birthCountry} • Shoots/Catches ${p.shootsCatches}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val feet = p.heightInches / 12
                                    val inches = p.heightInches % 12
                                    Text(
                                        text = "${feet}'${inches}\" • ${p.weightPounds} lbs" +
                                            (detail.profileCache?.draftSummary?.let { " • $it" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    OvrBadge(ovr = p.overallRating)
                                    Text(
                                        text = "OVR",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Injury Status Notice
                            if (p.injurySeverity != InjurySeverity.HEALTHY) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AlertRed.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.45f))
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text(
                                            text = "Medical Status: ${p.injuryStatus}" +
                                                (if (p.injuryType.isNotBlank()) " (${p.injuryType})" else "") +
                                                (if (p.injuryReturnDate.isNotBlank()) " • Est. Return: ${p.injuryReturnDate}" else ""),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = AlertRed
                                        )
                                        if (p.injuryNote.isNotBlank()) {
                                            Text(
                                                text = p.injuryNote,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // Today's Live / Final Game Strip
                            if (p.todayGameId > 0L) {
                                val badgeTone = if (p.todayGameIsLive) ProfitEmerald else IceCyan
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = badgeTone.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, badgeTone.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = (if (p.todayGameIsLive) "LIVE GAME TODAY " else "TODAY'S NHL GAME ") + p.todayOpponentLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = badgeTone,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            val line = if (p.position == Position.GOALIE) {
                                                "${p.todayGoalieSaves} SV • ${if (p.todayGoalieWin > 0) "WIN" else "Active"}"
                                            } else {
                                                "${p.todayGoals}G, ${p.todayAssists}A, ${p.todayShots} SOG, ${p.todayHits} HIT, ${p.todayBlocks} BLK"
                                            }
                                            Text(line, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(
                                            text = "+${f.dailyPoints} FP",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = badgeTone,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Market Price, Cap Hit & Fantasy Output Strip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricBox("Market Buy Price", CadFormatter.formatCompact(p.purchasePriceCad), IceCyan)
                                MetricBox("Market Sell Price", CadFormatter.formatCompact(p.sellPriceCad), TrophyGold)
                                MetricBox("Sim Cap Hit (${p.contractYears}yr)", CadFormatter.formatCompact(p.capHitCad), MaterialTheme.colorScheme.onSurface)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricBox("Season FP", "${f.seasonPoints}", ProfitEmerald)
                                MetricBox("Weekly FP Est.", "${f.weeklyPoints}", IceCyan)
                                MetricBox("Bargain FP/$1M", "${f.valuePerMillionCad}", TrophyGold)
                                MetricBox("Form Trend", "${f.trend.symbol} ${f.trend.label}", MaterialTheme.colorScheme.onSurface)
                            }
                            if (isOwnedByUser && p.acquisitionPriceCad > 0L) {
                                val unrl = p.purchasePriceCad - p.acquisitionPriceCad
                                Text(
                                    text = "Acquired for ${CadFormatter.formatCompact(p.acquisitionPriceCad)} • Unrealized Market Delta: ${CadFormatter.formatCompact(unrl, showPlusSign = true)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (unrl >= 0) ProfitEmerald else AlertRed
                                )
                            }
                        }
                    }

                    // Current Season NHL Stats Grid
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Current NHL Season Statistics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (p.position == Position.GOALIE) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    StatCell("GP", "${p.gamesPlayed}")
                                    StatCell("GS", "${p.gamesStarted}")
                                    StatCell("W-L-OT", "${p.wins}-${p.losses}-${p.otLosses}")
                                    StatCell("SV%", String.format(Locale.CANADA, "%.3f", p.savePct))
                                    StatCell("GAA", String.format(Locale.CANADA, "%.2f", p.goalsAgainstAverage))
                                    StatCell("SO", "${p.shutouts}")
                                }
                                if (p.prevGamesPlayed > 0) {
                                    Text(
                                        text = "Prior Season: ${p.prevGamesPlayed} GP • ${p.prevWins}W • ${String.format(Locale.CANADA, "%.3f", p.prevSavePct)} SV% • ${String.format(Locale.CANADA, "%.2f", p.prevGaa)} GAA • ${p.prevShutouts} SO",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    StatCell("GP", "${p.gamesPlayed}")
                                    StatCell("G", "${p.goals}")
                                    StatCell("A", "${p.assists}")
                                    StatCell("PTS", "${p.points}")
                                    StatCell("+/-", "${if (p.plusMinus > 0) "+" else ""}${p.plusMinus}")
                                }
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    StatCell("SOG", "${p.shots}")
                                    StatCell("PPP", "${p.powerPlayPoints}")
                                    StatCell("HIT", "${p.hits}")
                                    StatCell("BLK", "${p.blockedShots}")
                                    StatCell("PIM", "${p.penaltyMinutes}")
                                }
                                if (p.prevGamesPlayed > 0) {
                                    Text(
                                        text = "Prior Season: ${p.prevGamesPlayed} GP • ${p.prevGoals}G • ${p.prevAssists}A • ${p.prevPoints} PTS • ${p.prevShots} SOG • ${p.prevHits} HIT • ${p.prevBlockedShots} BLK",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Performance Attributes & Charts Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Scouting Metrics & Performance Charts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            PlayerAttributeBars(player = p)

                            if (detail.isLoadingCareer) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text("Loading historical NHL career & last 5 games...", style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                val sourceTag = if (detail.isLiveCareerFetch) "Live NHL Landing API" else if (detail.profileCache != null) "Cached NHL Profile" else "Unavailable Offline"
                                Text(
                                    text = "Game Log & Historical Source: $sourceTag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (detail.recentGames.isNotEmpty()) {
                                    Text("Last 5 NHL Games", style = MaterialTheme.typography.labelLarge)
                                    RecentGamesBarChart(
                                        games = detail.recentGames,
                                        isGoalie = p.position == Position.GOALIE
                                    )
                                }
                                if (detail.historicalSeasons.size >= 2) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = if (p.position == Position.GOALIE) "Historical Season Save % & Wins" else "Historical Season Points Progression",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    HistoricalSeasonsLineChart(
                                        seasons = detail.historicalSeasons,
                                        isGoalie = p.position == Position.GOALIE
                                    )
                                }
                            }
                        }
                    }

                    // Transparent Fantasy Scoring Breakdown
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Fantasy Scoring Formula Breakdown",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (p.gamesPlayed == 0 && p.prevGamesPlayed > 0) "Showing prior-season reference breakdown (0 GP so far this season)" else "Calculated directly from real NHL season statistics × configured weights",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            f.breakdown.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${row.category} (${row.statCount.toInt()} × ${row.weight})",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${if (row.fantasyPoints >= 0) "+" else ""}${row.fantasyPoints} FP",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (row.fantasyPoints > 0) ProfitEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Transparent Simulated Valuation Model Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Simulated Valuation Model (Virtual CAD)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (v.priceDeltaCad != 0L) {
                                    Text(
                                        text = CadFormatter.formatShortNoSuffix(v.priceDeltaCad, showPlusSign = true),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (v.priceDeltaCad > 0) ProfitEmerald else AlertRed
                                    )
                                }
                            }
                            Text(
                                text = v.priceChangeExplanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            v.components.forEach { comp ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(comp.factorName, style = MaterialTheme.typography.labelLarge)
                                        Text(comp.impactDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = CadFormatter.formatShortNoSuffix(comp.cadContribution, showPlusSign = comp.factorName != v.components.first().factorName),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (comp.cadContribution >= 0) MaterialTheme.colorScheme.onSurface else AlertRed
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Action Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            isOwnedByUser -> {
                                Button(
                                    onClick = onSellClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                                ) {
                                    Icon(Icons.Default.Sell, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Sell to Marketplace (${CadFormatter.formatCompact(p.sellPriceCad)})",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            isOwnedByCpu -> {
                                Button(
                                    onClick = { p.ownerFantasyTeamId?.let(onJumpToTradeWithOwner) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Owned by League Team • Propose Trade", fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> {
                                Button(
                                    onClick = onBuyClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 50.dp)
                                ) {
                                    Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Sign Player (${CadFormatter.formatCompact(p.purchasePriceCad)})",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
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
private fun MetricBox(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor
        )
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
