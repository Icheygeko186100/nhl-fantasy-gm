package com.nhlfantasygm.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.data.local.NhlTeamEntity
import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.data.repository.NhlFantasyRepository
import com.nhlfantasygm.model.CadFormatter
import com.nhlfantasygm.model.Position
import com.nhlfantasygm.ui.*
import com.nhlfantasygm.ui.components.*
import com.nhlfantasygm.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    players: List<ScoredPlayerItem>,
    nhlTeams: List<NhlTeamEntity>,
    humanTeam: FantasyTeamEntity?,
    filterState: MarketplaceFilterState,
    isSyncing: Boolean,
    onUpdateFilter: ((MarketplaceFilterState) -> MarketplaceFilterState) -> Unit,
    onPlayerClick: (Long) -> Unit,
    onBuyClick: (PlayerEntity) -> Unit,
    onSellClick: (PlayerEntity) -> Unit,
    onWatchlistToggle: (Long) -> Unit
) {
    var showAdvancedFilters by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showTeamMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Header with Treasury Balance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NHL Player Marketplace",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${players.size} matching players • Simulated Virtual CAD Valuation",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ProfitEmerald.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, ProfitEmerald.copy(alpha = 0.45f))
            ) {
                Text(
                    text = CadFormatter.formatCompact(humanTeam?.cashBalanceCad ?: 100_000_000L),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = ProfitEmerald,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Search Bar + Filter / Sort Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q) } },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search NHL player or team...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (filterState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onUpdateFilter { it.copy(searchQuery = "") } }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp)
            )

            FilledTonalIconButton(onClick = { showAdvancedFilters = !showAdvancedFilters }) {
                Icon(Icons.Default.FilterList, contentDescription = "Filters")
            }

            Box {
                FilledTonalIconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    PlayerSortOption.entries.forEach { opt ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = opt.label,
                                    fontWeight = if (filterState.sortOption == opt) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (filterState.sortOption == opt) IceCyan else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onUpdateFilter { it.copy(sortOption = opt) }
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Category Tabs (All, Best Value, Breakouts, Watchlist, Recent)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MarketplaceCategoryTab.entries.forEach { tab ->
                FilterChip(
                    selected = filterState.categoryTab == tab,
                    onClick = { onUpdateFilter { it.copy(categoryTab = tab) } },
                    label = { Text(tab.label, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        // Quick Position Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PositionFilter.entries.forEach { pos ->
                FilterChip(
                    selected = filterState.positionFilter == pos,
                    onClick = { onUpdateFilter { it.copy(positionFilter = pos) } },
                    label = { Text(pos.label) }
                )
            }
        }

        // Expandable Advanced Filters Drawer
        AnimatedVisibility(visible = showAdvancedFilters) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                        Box {
                            OutlinedButton(onClick = { showTeamMenu = true }) {
                                Text("NHL Team: ${filterState.teamAbbrev}")
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = showTeamMenu,
                                onDismissRequest = { showTeamMenu = false },
                                modifier = Modifier.heightIn(max = 320.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("ALL (32 NHL Teams)", fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        onUpdateFilter { it.copy(teamAbbrev = "ALL") }
                                        showTeamMenu = false
                                    }
                                )
                                nhlTeams.sortedBy { it.abbrev }.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text("${t.abbrev} — ${t.fullName}") },
                                        onClick = {
                                            onUpdateFilter { it.copy(teamAbbrev = t.abbrev) }
                                            showTeamMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        TextButton(onClick = { onUpdateFilter { MarketplaceFilterState() } }) {
                            Text("Reset All")
                        }
                    }

                    Text(
                        text = if (filterState.maxPriceMillions >= 19.9f) "Max Price: Any"
                        else "Max Price: $${String.format(Locale.CANADA, "%.1f", filterState.maxPriceMillions)}M CAD",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = filterState.maxPriceMillions,
                        onValueChange = { v -> onUpdateFilter { it.copy(maxPriceMillions = v) } },
                        valueRange = 1f..20f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Min Rating: ${filterState.minOvr} OVR", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = filterState.minOvr.toFloat(),
                                onValueChange = { v -> onUpdateFilter { it.copy(minOvr = v.roundToInt()) } },
                                valueRange = 55f..94f
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Max Age: ${filterState.maxAge}", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = filterState.maxAge.toFloat(),
                                onValueChange = { v -> onUpdateFilter { it.copy(maxAge = v.roundToInt()) } },
                                valueRange = 19f..43f
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = filterState.availableOnly,
                            onClick = { onUpdateFilter { it.copy(availableOnly = !it.availableOnly) } },
                            label = { Text("Free Agents Only") }
                        )
                        FilterChip(
                            selected = filterState.healthyOnly,
                            onClick = { onUpdateFilter { it.copy(healthyOnly = !it.healthyOnly) } },
                            label = { Text("Healthy Only") }
                        )
                    }
                }
            }
        }

        // Player List
        if (players.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(color = IceCyan)
                        Text("Loading real NHL players & statistics...", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Icon(
                            imageVector = Icons.Default.PersonSearch,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when (filterState.categoryTab) {
                                MarketplaceCategoryTab.WATCHLIST -> "Your Watchlist is empty. Tap the star icon on any player."
                                MarketplaceCategoryTab.RECENT -> "No recently viewed players yet. Tap any player card to inspect their profile."
                                else -> "No NHL players match your current filters."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(players, key = { it.player.playerId }) { item ->
                    MarketplacePlayerCard(
                        item = item,
                        showBreakoutNote = filterState.categoryTab == MarketplaceCategoryTab.BREAKOUTS || item.fantasy.isBreakoutCandidate,
                        onCardClick = { onPlayerClick(item.player.playerId) },
                        onBuyClick = { onBuyClick(item.player) },
                        onSellClick = { onSellClick(item.player) },
                        onWatchlistClick = { onWatchlistToggle(item.player.playerId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketplacePlayerCard(
    item: ScoredPlayerItem,
    showBreakoutNote: Boolean,
    onCardClick: () -> Unit,
    onBuyClick: () -> Unit,
    onSellClick: () -> Unit,
    onWatchlistClick: () -> Unit
) {
    val p = item.player
    val f = item.fantasy
    val isUserOwned = p.ownerFantasyTeamId == NhlFantasyRepository.USER_TEAM_ID
    val isCpuOwned = p.ownerFantasyTeamId != null && !isUserOwned

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isUserOwned) ProfitEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlayerHeadshot(p.headshotUrl, p.fullName, p.teamAbbrev, p.sweaterNumber, size = 48.dp)

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PositionPill(p.position)
                        Text(
                            text = p.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        InjuryBadge(p.injurySeverity, p.injuryStatus)
                    }
                    Text(
                        text = "${p.teamAbbrev} • Age ${p.age}" +
                            (if (p.isProspect) " • Prospect" else if (p.isRookie) " • Rookie" else "") +
                            " • Cap ${CadFormatter.formatShortNoSuffix(p.capHitCad)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val statLine = if (p.position == Position.GOALIE) {
                        if (p.gamesPlayed > 0) "${p.gamesPlayed} GP • ${p.wins}W • ${String.format(Locale.CANADA, "%.3f", p.savePct)} SV% • ${String.format(Locale.CANADA, "%.2f", p.goalsAgainstAverage)} GAA"
                        else if (p.prevGamesPlayed > 0) "Prior: ${p.prevWins}W • ${String.format(Locale.CANADA, "%.3f", p.prevSavePct)} SV%"
                        else "0 NHL GP this season"
                    } else {
                        if (p.gamesPlayed > 0) "${p.gamesPlayed} GP • ${p.goals}G ${p.assists}A ${p.points}P • ${p.shots} SOG • ${p.hits}H ${p.blockedShots}B"
                        else if (p.prevGamesPlayed > 0) "Prior: ${p.prevGamesPlayed} GP • ${p.prevGoals}G ${p.prevAssists}A ${p.prevPoints}P"
                        else "0 NHL GP this season"
                    }
                    Text(
                        text = statLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onWatchlistClick,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (p.isWatchlisted) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Watchlist",
                                tint = if (p.isWatchlisted) TrophyGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        OvrBadge(p.overallRating)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${f.seasonPoints} FP",
                        style = MaterialTheme.typography.labelLarge,
                        color = ProfitEmerald,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${f.valuePerMillionCad} FP/$1M",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrophyGold
                    )
                }
            }

            if (showBreakoutNote && f.breakoutReason.isNotBlank()) {
                Text(
                    text = "★ Breakout Signal: ${f.breakoutReason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = IceCyan
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = CadFormatter.formatCompact(if (isUserOwned) p.sellPriceCad else p.purchasePriceCad),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isUserOwned) TrophyGold else IceCyan
                        )
                        if (p.priceDeltaCad != 0L) {
                            val up = p.priceDeltaCad > 0
                            Text(
                                text = "${if (up) "▲" else "▼"} ${CadFormatter.formatShortNoSuffix(p.priceDeltaCad, showPlusSign = true)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (up) ProfitEmerald else AlertRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (f.dailyPoints > 0) {
                        Text(
                            text = "Today Live: +${f.dailyPoints} FP (${p.todayOpponentLabel})",
                            style = MaterialTheme.typography.labelSmall,
                            color = ProfitEmerald
                        )
                    }
                }

                when {
                    isUserOwned -> {
                        OutlinedButton(
                            onClick = onSellClick,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text("Sell", fontWeight = FontWeight.Bold, color = WarningAmber)
                        }
                    }
                    isCpuOwned -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RoyalBlue.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "League Owned",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = RoyalBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    else -> {
                        Button(
                            onClick = onBuyClick,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text("Buy", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
