package com.nhlfantasygm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.domain.TeamFinancialSummary
import com.nhlfantasygm.model.CadFormatter
import com.nhlfantasygm.model.DataFreshnessState
import com.nhlfantasygm.model.TeamEmblem
import com.nhlfantasygm.multiplayer.MultiplayerUiState
import com.nhlfantasygm.ui.theme.*

@Composable
fun SleeperTopBar(
    team: FantasyTeamEntity?,
    financials: TeamFinancialSummary?,
    mpState: MultiplayerUiState,
    freshnessState: DataFreshnessState,
    isDraftActive: Boolean,
    onOpenSearch: () -> Unit,
    onOpenDraft: () -> Unit,
    onOpenSettings: () -> Unit,
    onTeamClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 3.dp,
        border = BorderStroke(0.dp, Color.Transparent)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Team Identity & Cap Room Badge
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onTeamClick),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (team != null) {
                        FranchiseCrestBadge(
                            emblem = TeamEmblem.fromId(team.emblemId),
                            primaryColorHex = team.primaryColorHex,
                            secondaryColorHex = team.secondaryColorHex,
                            abbreviation = team.abbreviation,
                            size = 38.dp
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = team?.teamName ?: "My GM Franchise",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (team != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${team.wins}-${team.losses}-${team.ties}",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (team.wins >= team.losses) ProfitEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Cap Space / Treasury pill
                        val capRoom = financials?.remainingCapSpaceCad ?: 7_300_000L
                        Text(
                            text = "${CadFormatter.formatCompact(capRoom)} Cap Space • ${CadFormatter.formatCompact(team?.cashBalanceCad ?: 100_000_000L)} Cash",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Right: Action Icons (Search, Draft Room, Settings)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Draft Room Icon / Pill (if active or multiplayer)
                    if (isDraftActive || mpState.isMultiplayerActive) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (mpState.isMultiplayerActive) ProfitEmerald.copy(alpha = 0.2f) else IceCyan.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (mpState.isMultiplayerActive) ProfitEmerald else IceCyan),
                            modifier = Modifier.clickable(onClick = onOpenDraft)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HowToVote,
                                    contentDescription = "Draft Room",
                                    tint = if (mpState.isMultiplayerActive) ProfitEmerald else IceCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (mpState.isMultiplayerActive) "LIVE" else "DRAFT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (mpState.isMultiplayerActive) ProfitEmerald else IceCyan
                                )
                            }
                        }
                    }

                    // Instant Player Search Icon
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Players",
                            tint = IceCyan
                        )
                    }

                    // Settings Cog Icon
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                thickness = 1.dp
            )
        }
    }
}
