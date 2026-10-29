package com.nhlfantasygm.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.data.local.TransactionEntity
import com.nhlfantasygm.domain.LineupAnalyticsEngine
import com.nhlfantasygm.domain.TeamFinancialSummary
import com.nhlfantasygm.domain.TeamRosterAnalytics
import com.nhlfantasygm.model.*
import com.nhlfantasygm.ui.ScoredPlayerItem
import com.nhlfantasygm.ui.components.*
import com.nhlfantasygm.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyTeamScreen(
    humanTeam: FantasyTeamEntity?,
    rosterScored: List<ScoredPlayerItem>,
    analytics: TeamRosterAnalytics,
    financials: TeamFinancialSummary?,
    transactions: List<TransactionEntity>,
    onPlayerClick: (Long) -> Unit,
    onSellPlayer: (PlayerEntity) -> Unit,
    onAutoSetLineup: () -> Unit,
    onAssignSlot: (Long, LineupSlot) -> Unit,
    onSaveBranding: (String, String, TeamEmblem, Long, Long) -> Unit,
    onGoToMarketplace: () -> Unit
) {
    var activeSubTab by remember { mutableIntStateOf(0) } // 0=Lineup, 1=Analytics, 2=Finances & Ledger
    var showBrandingDialog by remember { mutableStateOf(false) }
    var playerForSlotModal by remember { mutableStateOf<PlayerEntity?>(null) }

    val rosterPlayers = remember(rosterScored) { rosterScored.map { it.player } }
    val teamFantasyPoints = remember(rosterScored) {
        val total = rosterScored
            .filter { it.player.lineupSlot.isActiveStarter }
            .sumOf { it.fantasy.seasonPoints }
        kotlin.math.round(total * 10.0) / 10.0
    }

    if (showBrandingDialog && humanTeam != null) {
        FranchiseBrandingDialog(
            team = humanTeam,
            onDismiss = { showBrandingDialog = false },
            onSave = { name, abbrev, emblem, pHex, sHex ->
                onSaveBranding(name, abbrev, emblem, pHex, sHex)
                showBrandingDialog = false
            }
        )
    }

    playerForSlotModal?.let { targetPlayer ->
        SlotAssignmentDialog(
            player = targetPlayer,
            fullRoster = rosterPlayers,
            onDismiss = { playerForSlotModal = null },
            onSelectSlot = { slot ->
                onAssignSlot(targetPlayer.playerId, slot)
                playerForSlotModal = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Franchise Header Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FranchiseCrestBadge(
                        emblem = TeamEmblem.fromId(humanTeam?.emblemId),
                        primaryColorHex = humanTeam?.primaryColorHex ?: 0xFF00C2FF,
                        secondaryColorHex = humanTeam?.secondaryColorHex ?: 0xFF0B192C,
                        abbreviation = humanTeam?.abbreviation ?: "NIC",
                        size = 50.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = humanTeam?.teamName ?: "Northern Ice HC",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "OVR ${analytics.overallTeamRating} (Fwd ${analytics.forwardsRating} • Def ${analytics.defenseRating} • G ${analytics.goaltendingRating}) • $teamFantasyPoints Active FP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalIconButton(onClick = { showBrandingDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Customize Franchise")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Remaining Budget", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = CadFormatter.formatCompact(financials?.cashBalanceCad ?: 100_000_000L),
                            style = MaterialTheme.typography.titleSmall,
                            color = ProfitEmerald,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column {
                        Text("Team Payroll", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = CadFormatter.formatCompact(financials?.currentPayrollCad ?: 0L),
                            style = MaterialTheme.typography.titleSmall,
                            color = IceCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Cap Space", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val capLeft = financials?.remainingCapSpaceCad ?: 95_500_000L
                        Text(
                            text = CadFormatter.formatCompact(capLeft),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (capLeft < 3_000_000L) WarningAmber else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                financials?.budgetWarnings?.firstOrNull()?.let { warn ->
                    Text(
                        text = "⚠ $warn",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber
                    )
                }
            }
        }

        TabRow(selectedTabIndex = activeSubTab) {
            Tab(selected = activeSubTab == 0, onClick = { activeSubTab = 0 }, text = { Text("Lineup (${rosterPlayers.size})") })
            Tab(selected = activeSubTab == 1, onClick = { activeSubTab = 1 }, text = { Text("Scouting") })
            Tab(selected = activeSubTab == 2, onClick = { activeSubTab = 2 }, text = { Text("Finances") })
        }

        when (activeSubTab) {
            0 -> LineupBuilderTab(
                rosterScored = rosterScored,
                onAutoSetLineup = onAutoSetLineup,
                onGoToMarketplace = onGoToMarketplace,
                onPlayerClick = onPlayerClick,
                onMoveSlotClick = { playerForSlotModal = it },
                onSellClick = onSellPlayer
            )
            1 -> RosterAnalyticsTab(
                analytics = analytics,
                onPlayerClick = onPlayerClick
            )
            2 -> FranchiseFinancesTab(
                financials = financials,
                transactions = transactions
            )
        }
    }
}

@Composable
private fun LineupBuilderTab(
    rosterScored: List<ScoredPlayerItem>,
    onAutoSetLineup: () -> Unit,
    onGoToMarketplace: () -> Unit,
    onPlayerClick: (Long) -> Unit,
    onMoveSlotClick: (PlayerEntity) -> Unit,
    onSellClick: (PlayerEntity) -> Unit
) {
    if (rosterScored.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.SportsHockey, contentDescription = null, modifier = Modifier.size(52.dp), tint = IceCyan)
                Text("Your NHL Franchise Roster is Empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Sign real NHL players in the Marketplace or launch a Fantasy Draft to build your team.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onGoToMarketplace) {
                    Text("Browse Player Marketplace", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val bySlot = remember(rosterScored) {
        rosterScored.associateBy { it.player.lineupSlot }
    }
    val unassigned = remember(rosterScored) {
        rosterScored.filter { it.player.lineupSlot == LineupSlot.UNASSIGNED }
    }

    val managedGroups = listOf(
        LineupGroup.FORWARD_LINE_1 to listOf(LineupSlot.LW1, LineupSlot.C1, LineupSlot.RW1),
        LineupGroup.FORWARD_LINE_2 to listOf(LineupSlot.LW2, LineupSlot.C2, LineupSlot.RW2),
        LineupGroup.FORWARD_LINE_3 to listOf(LineupSlot.LW3, LineupSlot.C3, LineupSlot.RW3),
        LineupGroup.FORWARD_LINE_4 to listOf(LineupSlot.LW4, LineupSlot.C4, LineupSlot.RW4),
        LineupGroup.DEFENSE_PAIR_1 to listOf(LineupSlot.LD1, LineupSlot.RD1),
        LineupGroup.DEFENSE_PAIR_2 to listOf(LineupSlot.LD2, LineupSlot.RD2),
        LineupGroup.DEFENSE_PAIR_3 to listOf(LineupSlot.LD3, LineupSlot.RD3),
        LineupGroup.GOALTENDING to listOf(LineupSlot.G1, LineupSlot.G2),
        LineupGroup.BENCH to LineupSlot.benchSlots,
        LineupGroup.INJURED_RESERVE to LineupSlot.irSlots
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAutoSetLineup,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Recommended Starting Lineup", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onGoToMarketplace) {
                    Text("+ Sign")
                }
            }
        }

        items(managedGroups, key = { it.first.name }) { (group, slots) ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = IceCyan,
                        fontWeight = FontWeight.ExtraBold
                    )
                    slots.forEach { slot ->
                        val occupant = bySlot[slot]
                        LineupSlotRow(
                            slot = slot,
                            occupant = occupant,
                            onPlayerClick = onPlayerClick,
                            onMoveClick = onMoveSlotClick,
                            onSellClick = onSellClick
                        )
                    }
                }
            }
        }

        if (unassigned.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Unassigned Reserve Players", style = MaterialTheme.typography.titleSmall, color = WarningAmber, fontWeight = FontWeight.ExtraBold)
                        unassigned.forEach { item ->
                            LineupSlotRow(
                                slot = LineupSlot.UNASSIGNED,
                                occupant = item,
                                onPlayerClick = onPlayerClick,
                                onMoveClick = onMoveSlotClick,
                                onSellClick = onSellClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineupSlotRow(
    slot: LineupSlot,
    occupant: ScoredPlayerItem?,
    onPlayerClick: (Long) -> Unit,
    onMoveClick: (PlayerEntity) -> Unit,
    onSellClick: (PlayerEntity) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (occupant != null) Modifier.clickable { onPlayerClick(occupant.player.playerId) }
                else Modifier
            )
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.width(44.dp)
        ) {
            Text(
                text = slot.code.take(4),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (occupant == null) {
            Text(
                text = "Open ${slot.label} Vacancy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        } else {
            val p = occupant.player
            PlayerHeadshot(p.headshotUrl, p.fullName, p.teamAbbrev, p.sweaterNumber, size = 34.dp)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = p.fullName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    InjuryBadge(p.injurySeverity, p.injuryStatus)
                }
                Text(
                    text = "${p.teamAbbrev} • ${p.position.uiShort} • ${occupant.fantasy.seasonPoints} FP • ${CadFormatter.formatShortNoSuffix(p.capHitCad)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OvrBadge(p.overallRating)
            IconButton(
                onClick = { onMoveClick(p) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.SwapVert, contentDescription = "Move Slot", tint = IceCyan, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onSellClick(p) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.Sell, contentDescription = "Sell Player", tint = WarningAmber, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun RosterAnalyticsTab(
    analytics: TeamRosterAnalytics,
    onPlayerClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Positional Vacancies Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Positional Depth & Vacancies (20-Starter Standard)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        analytics.vacanciesByPosition.forEach { (pos, missing) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PositionPill(pos)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (missing == 0) "Full" else "-$missing",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (missing == 0) ProfitEmerald else WarningAmber,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                    Text(
                        text = "Weakest Unit: ${analytics.weakestPositionLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber
                    )
                }
            }
        }

        // Strengths & Weaknesses
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Franchise Strengths", style = MaterialTheme.typography.titleMedium, color = ProfitEmerald, fontWeight = FontWeight.Bold)
                    analytics.strengths.forEach { s ->
                        Text("✓ $s", style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Text("Areas to Address", style = MaterialTheme.typography.titleMedium, color = WarningAmber, fontWeight = FontWeight.Bold)
                    analytics.weaknesses.forEach { w ->
                        Text("• $w", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Best Players
        if (analytics.bestPlayers.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Franchise Cornerstone Players", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        analytics.bestPlayers.forEach { p ->
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
                                    Text("${p.fullName} (${p.position.uiShort})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("${p.teamAbbrev} • Value ${CadFormatter.formatCompact(p.purchasePriceCad)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OvrBadge(p.overallRating)
                            }
                        }
                    }
                }
            }
        }

        // Injured / Unavailable Players
        if (analytics.injuredPlayers.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Injured / Day-to-Day Report", style = MaterialTheme.typography.titleMedium, color = AlertRed, fontWeight = FontWeight.Bold)
                        analytics.injuredPlayers.forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayerClick(p.playerId) }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${p.fullName} (${p.position.uiShort})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("${p.injuryStatus} ${p.injuryType}", style = MaterialTheme.typography.bodySmall, color = AlertRed)
                                }
                                InjuryBadge(p.injurySeverity, p.injuryStatus)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FranchiseFinancesTab(
    financials: TeamFinancialSummary?,
    transactions: List<TransactionEntity>
) {
    val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.CANADA) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (financials != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Franchise Financial Statement (Virtual CAD)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FinanceLine("Current Cash Balance", CadFormatter.formatFull(financials.cashBalanceCad), ProfitEmerald)
                        FinanceLine("Roster Market Value", CadFormatter.formatFull(financials.totalRosterMarketValueCad), IceCyan)
                        FinanceLine("Net Franchise Worth", CadFormatter.formatFull(financials.netFranchiseWorthCad), TrophyGold)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        FinanceLine("Total Spent on Signings/Draft", CadFormatter.formatFull(financials.totalSpentOnPurchasesCad), MaterialTheme.colorScheme.onSurface)
                        FinanceLine("Total Marketplace Sales Revenue", CadFormatter.formatFull(financials.totalEarnedFromSalesCad), MaterialTheme.colorScheme.onSurface)
                        FinanceLine(
                            "Realized Trading P&L",
                            CadFormatter.formatFull(financials.realizedProfitLossCad),
                            if (financials.realizedProfitLossCad >= 0) ProfitEmerald else AlertRed
                        )
                        FinanceLine(
                            "Unrealized Roster Value Delta",
                            CadFormatter.formatFull(financials.unrealizedProfitLossCad),
                            if (financials.unrealizedProfitLossCad >= 0) ProfitEmerald else AlertRed
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        FinanceLine("Active Payroll / Salary Cap", "${CadFormatter.formatCompact(financials.currentPayrollCad)} / ${CadFormatter.formatCompact(financials.salaryCapCad)}", IceCyan)
                    }
                }
            }
        }

        item {
            Text("Transaction History (${transactions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(transactions, key = { it.id }) { tx ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tx.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(tx.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${tx.type} • ${tx.counterpartyLabel} • ${fmt.format(Date(tx.timestampEpochMs))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (tx.cashDeltaCad != 0L) {
                            Text(
                                text = CadFormatter.formatShortNoSuffix(tx.cashDeltaCad, showPlusSign = true),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (tx.cashDeltaCad > 0) ProfitEmerald else AlertRed,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = "Bal: ${CadFormatter.formatShortNoSuffix(tx.balanceAfterCad)}",
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
private fun FinanceLine(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun SlotAssignmentDialog(
    player: PlayerEntity,
    fullRoster: List<PlayerEntity>,
    onDismiss: () -> Unit,
    onSelectSlot: (LineupSlot) -> Unit
) {
    val occupantBySlot = remember(fullRoster) {
        fullRoster.filter { it.lineupSlot != LineupSlot.UNASSIGNED }.associateBy { it.lineupSlot }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Assign Slot: ${player.fullName} (${player.position.uiShort})", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LineupSlot.allManageableSlots.forEach { slot ->
                    val occ = occupantBySlot[slot]?.takeIf { it.playerId != player.playerId }
                    val validationErr = LineupAnalyticsEngine.validateSlotAssignment(player, slot, occ)
                    val isLegal = validationErr == null

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isLegal) { onSelectSlot(slot) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (player.lineupSlot == slot) IceCyan.copy(alpha = 0.2f)
                        else if (isLegal) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${slot.code} — ${slot.label}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isLegal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = occ?.let { "Swap with ${it.fullName} (${it.overallRating} OVR)" }
                                        ?: (validationErr ?: "Open Slot"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isLegal) MaterialTheme.colorScheme.onSurfaceVariant else AlertRed
                                )
                            }
                            if (isLegal) {
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = IceCyan)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun FranchiseBrandingDialog(
    team: FantasyTeamEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, TeamEmblem, Long, Long) -> Unit
) {
    var name by remember { mutableStateOf(team.teamName) }
    var abbrev by remember { mutableStateOf(team.abbreviation) }
    var emblem by remember { mutableStateOf(TeamEmblem.fromId(team.emblemId)) }
    var primaryHex by remember { mutableLongStateOf(team.primaryColorHex) }
    var secondaryHex by remember { mutableLongStateOf(team.secondaryColorHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Franchise Identity", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FranchiseCrestBadge(
                        emblem = emblem,
                        primaryColorHex = primaryHex,
                        secondaryColorHex = secondaryHex,
                        abbreviation = abbrev.ifBlank { "NIC" },
                        size = 72.dp
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(28) },
                    label = { Text("Team Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = abbrev,
                    onValueChange = { abbrev = it.uppercase().take(3) },
                    label = { Text("3-Letter Abbreviation") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Select Franchise Crest", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TeamEmblem.entries.forEach { emb ->
                        FilterChip(
                            selected = emblem == emb,
                            onClick = { emblem = emb },
                            label = { Text(emb.displayName) }
                        )
                    }
                }

                Text("Team Color Palette", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TeamColorPreset.presets.forEach { preset ->
                        val selected = primaryHex == preset.primaryHex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(preset.primaryHex))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) Color.White else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    primaryHex = preset.primaryHex
                                    secondaryHex = preset.secondaryHex
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, abbrev, emblem, primaryHex, secondaryHex) }) {
                Text("Save Identity")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
