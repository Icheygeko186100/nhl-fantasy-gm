package com.nhlfantasygm.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.data.local.TransactionEntity
import com.nhlfantasygm.domain.TeamFinancialSummary
import com.nhlfantasygm.model.CadFormatter
import com.nhlfantasygm.model.DataFreshnessState
import com.nhlfantasygm.model.TeamEmblem
import com.nhlfantasygm.ui.components.FranchiseCrestBadge
import com.nhlfantasygm.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    humanTeam: FantasyTeamEntity?,
    financials: TeamFinancialSummary?,
    transactions: List<TransactionEntity>,
    freshnessState: DataFreshnessState,
    isSyncing: Boolean,
    onOpenSettings: () -> Unit,
    onRefreshData: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E17)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Franchise Profile Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131D31),
                border = BorderStroke(1.dp, Color(0xFF223555)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (humanTeam != null) {
                        FranchiseCrestBadge(
                            emblem = TeamEmblem.fromId(humanTeam.emblemId),
                            primaryColorHex = humanTeam.primaryColorHex,
                            secondaryColorHex = humanTeam.secondaryColorHex,
                            abbreviation = humanTeam.abbreviation,
                            size = 56.dp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = humanTeam?.teamName ?: "My GM Franchise",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "GM ${humanTeam?.gmDisplayName?.ifBlank { "General Manager" } ?: "General Manager"} • ${humanTeam?.wins ?: 0}W - ${humanTeam?.losses ?: 0}L",
                            style = MaterialTheme.typography.bodySmall,
                            color = ProfitEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                }
            }
        }

        // Virtual Economy Overview
        item {
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
                    Text(
                        text = "Virtual CAD Economy (Fictional Simulation)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        EconomyMetricItem(
                            label = "Virtual Cash Balance",
                            value = CadFormatter.formatFull(financials?.cashBalanceCad ?: 100_000_000L),
                            color = ProfitEmerald
                        )
                        EconomyMetricItem(
                            label = "Cap Space Available",
                            value = CadFormatter.formatCompact(financials?.remainingCapSpaceCad ?: 0L),
                            color = Color(0xFF00E5FF)
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        EconomyMetricItem(
                            label = "Current Payroll",
                            value = CadFormatter.formatCompact(financials?.currentPayrollCad ?: 0L),
                            color = Color.White
                        )
                        EconomyMetricItem(
                            label = "Roster Market Value",
                            value = CadFormatter.formatCompact(financials?.totalRosterMarketValueCad ?: 0L),
                            color = TrophyGold
                        )
                    }
                }
            }
        }

        // Settings & Management Shortcuts
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF131D31),
                border = BorderStroke(1.dp, Color(0xFF223555)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSettings)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF)
                        )
                        Column {
                            Text(
                                text = "League Settings & Scoring Rules",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Scoring weights, economy budget, cap settings",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7E8B9F)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF7E8B9F)
                    )
                }
            }
        }

        // Recent Transactions History
        item {
            Text(
                text = "Recent Transactions (${transactions.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp)
            )
        }

        if (transactions.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF131D31),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No player transactions yet. Buy or trade players in the Players tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            val recentList = transactions.take(8)
            items(recentList, key = { it.id }) { tx ->
                val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.CANADA)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF131D31),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = tx.title.ifBlank { tx.type },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${tx.type} • ${fmt.format(Date(tx.timestampEpochMs))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7E8B9F)
                            )
                        }
                        Text(
                            text = (if (tx.cashDeltaCad >= 0) "+ " else "- ") + CadFormatter.formatCompact(kotlin.math.abs(tx.cashDeltaCad)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black,
                            color = if (tx.cashDeltaCad >= 0) ProfitEmerald else AlertRed
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EconomyMetricItem(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF7E8B9F)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}
