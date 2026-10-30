package com.nhlfantasygm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nhlfantasygm.model.CadFormatter
import com.nhlfantasygm.model.Position
import com.nhlfantasygm.ui.ScoredPlayerItem
import com.nhlfantasygm.ui.theme.*
import java.util.Locale

enum class QuickSearchCategory(val label: String) {
    ALL("All"),
    AVAILABLE("Available ⚡"),
    MY_TEAM("My Team"),
    WATCHLIST("Watchlist ⭐"),
    CENTERS("C"),
    WINGS("Wings"),
    DEFENSE("D"),
    GOALIES("G")
}

@Composable
fun OmniPlayerSearchDialog(
    allPlayers: List<ScoredPlayerItem>,
    controlledTeamId: String,
    onDismiss: () -> Unit,
    onPlayerClick: (Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(QuickSearchCategory.ALL) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val filteredList = remember(query, selectedCategory, allPlayers, controlledTeamId) {
        val q = query.trim().lowercase(Locale.ROOT)
        allPlayers.filter { item ->
            val p = item.player
            val matchesQuery = q.isEmpty() ||
                p.fullName.lowercase(Locale.ROOT).contains(q) ||
                p.normalizedSearchName.contains(q) ||
                p.teamAbbrev.lowercase(Locale.ROOT).contains(q) ||
                p.positionCode.lowercase(Locale.ROOT).contains(q)

            if (!matchesQuery) return@filter false

            when (selectedCategory) {
                QuickSearchCategory.ALL -> true
                QuickSearchCategory.AVAILABLE -> p.ownerFantasyTeamId == null
                QuickSearchCategory.MY_TEAM -> p.ownerFantasyTeamId == controlledTeamId
                QuickSearchCategory.WATCHLIST -> p.isWatchlisted
                QuickSearchCategory.CENTERS -> p.position == Position.CENTER
                QuickSearchCategory.WINGS -> p.position == Position.LEFT_WING || p.position == Position.RIGHT_WING
                QuickSearchCategory.DEFENSE -> p.position == Position.DEFENSE
                QuickSearchCategory.GOALIES -> p.position == Position.GOALIE
            }
        }.take(30)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Search Bar Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        placeholder = { Text("Search player, team (e.g. McDavid, EDM, C)...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = IceCyan)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Search")
                    }
                }

                // Quick Filter Category Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickSearchCategory.entries.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Text(
                    text = "${filteredList.size} Results • Tap player to scout, buy, or trade",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Search Results List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList, key = { it.player.playerId }) { item ->
                        val p = item.player
                        val isMine = p.ownerFantasyTeamId == controlledTeamId
                        val isAvailable = p.ownerFantasyTeamId == null

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPlayerClick(p.playerId)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    PlayerHeadshot(
                                        headshotUrl = p.headshotUrl,
                                        fullName = p.fullName,
                                        teamAbbrev = p.teamAbbrev,
                                        sweaterNumber = p.sweaterNumber,
                                        size = 42.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = p.fullName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            PositionPill(position = p.position)
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${p.teamAbbrev} • #${p.sweaterNumber} • ${p.age}y",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            val ownershipLabel = when {
                                                isMine -> "My Team"
                                                isAvailable -> "Free Agent"
                                                else -> "Rival Team"
                                            }
                                            val ownershipColor = when {
                                                isMine -> ProfitEmerald
                                                isAvailable -> IceCyan
                                                else -> TrophyGold
                                            }
                                            Text(
                                                text = "• $ownershipLabel",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = ownershipColor
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${p.overallRating} OVR",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (p.overallRating >= 90) TrophyGold else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${String.format(Locale.CANADA, "%.1f", item.fantasy.seasonPoints)} FP",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ProfitEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = CadFormatter.formatCompact(p.purchasePriceCad),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (filteredList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                                    Text(
                                        text = "No players found matching \"$query\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
