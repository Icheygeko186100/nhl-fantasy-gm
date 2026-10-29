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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nhlfantasygm.data.local.DraftPickEntity
import com.nhlfantasygm.data.local.DraftSessionEntity
import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.data.repository.NhlFantasyRepository
import com.nhlfantasygm.domain.DraftEngine
import com.nhlfantasygm.domain.TextNormalizer
import com.nhlfantasygm.model.CadFormatter
import com.nhlfantasygm.model.DraftConfig
import com.nhlfantasygm.model.Position
import com.nhlfantasygm.ui.PositionFilter
import com.nhlfantasygm.ui.ScoredPlayerItem
import com.nhlfantasygm.ui.components.*
import com.nhlfantasygm.ui.theme.*
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun DraftScreen(
    draftSession: DraftSessionEntity?,
    draftConfig: DraftConfig,
    draftPicks: List<DraftPickEntity>,
    fantasyTeams: List<FantasyTeamEntity>,
    humanTeam: FantasyTeamEntity?,
    allScoredPlayers: List<ScoredPlayerItem>,
    timerRemainingSeconds: Int,
    onStartDraft: (DraftConfig) -> Unit,
    onMakeHumanPick: (Long) -> Unit,
    onAutoPickHuman: () -> Unit,
    onUndoLastPick: () -> Unit,
    onSimRestOfDraft: () -> Unit,
    onPlayerClick: (Long) -> Unit,
    onGoToMyTeam: () -> Unit
) {
    var forceSetupView by remember { mutableStateOf(false) }

    when {
        draftSession == null || draftSession.status == "NOT_STARTED" || forceSetupView -> {
            DraftSetupConfigurator(
                initialConfig = draftConfig,
                hasExistingSession = draftSession != null,
                onCancelReconfigure = { forceSetupView = false },
                onLaunchDraft = { cfg ->
                    forceSetupView = false
                    onStartDraft(cfg)
                }
            )
        }
        draftSession.status == "IN_PROGRESS" -> {
            ActiveDraftRoomView(
                session = draftSession,
                config = draftConfig,
                picks = draftPicks,
                fantasyTeams = fantasyTeams,
                humanTeam = humanTeam,
                allScoredPlayers = allScoredPlayers,
                timerSecondsLeft = timerRemainingSeconds,
                onPickPlayer = onMakeHumanPick,
                onAutoPick = onAutoPickHuman,
                onUndo = onUndoLastPick,
                onSimToEnd = onSimRestOfDraft,
                onReconfigure = { forceSetupView = true },
                onPlayerClick = onPlayerClick
            )
        }
        else -> {
            DraftCompletedSummaryView(
                picks = draftPicks,
                fantasyTeams = fantasyTeams,
                canUndo = draftPicks.any { it.isHumanPick },
                onUndoLastPick = onUndoLastPick,
                onStartNewDraft = { forceSetupView = true },
                onManageRoster = onGoToMyTeam
            )
        }
    }
}

@Composable
private fun DraftSetupConfigurator(
    initialConfig: DraftConfig,
    hasExistingSession: Boolean,
    onCancelReconfigure: () -> Unit,
    onLaunchDraft: (DraftConfig) -> Unit
) {
    var isSnake by remember { mutableStateOf(initialConfig.isSnakeDraft) }
    var teamCount by remember { mutableIntStateOf(initialConfig.teamCount) }
    var userSlot by remember { mutableIntStateOf(initialConfig.userDraftPosition) }
    var reqC by remember { mutableIntStateOf(initialConfig.requiredCenters) }
    var reqLW by remember { mutableIntStateOf(initialConfig.requiredLeftWings) }
    var reqRW by remember { mutableIntStateOf(initialConfig.requiredRightWings) }
    var reqD by remember { mutableIntStateOf(initialConfig.requiredDefensemen) }
    var reqG by remember { mutableIntStateOf(initialConfig.requiredGoalies) }
    var bench by remember { mutableIntStateOf(initialConfig.benchSlots) }
    var enforceCap by remember { mutableStateOf(initialConfig.enforceSalaryCap) }
    var capMillions by remember { mutableFloatStateOf(initialConfig.salaryCapCad / 1_000_000f) }
    var timerSec by remember { mutableIntStateOf(initialConfig.pickTimerSeconds) }

    val previewConfig = DraftConfig(
        isSnakeDraft = isSnake,
        teamCount = teamCount,
        userDraftPosition = userSlot.coerceIn(1, teamCount),
        requiredCenters = reqC,
        requiredLeftWings = reqLW,
        requiredRightWings = reqRW,
        requiredDefensemen = reqD,
        requiredGoalies = reqG,
        benchSlots = bench,
        enforceSalaryCap = enforceCap,
        salaryCapCad = (capMillions * 1_000_000L).toLong(),
        pickTimerSeconds = timerSec
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("NHL Fantasy Draft Room", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Single-Player vs Smart CPU General Managers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (hasExistingSession) {
                TextButton(onClick = onCancelReconfigure) {
                    Text("Back to Draft")
                }
            }
        }

        // Format & League Size
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Draft Format & Order", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isSnake,
                        onClick = { isSnake = true },
                        label = { Text("Snake Draft (1-N, N-1)") }
                    )
                    FilterChip(
                        selected = !isSnake,
                        onClick = { isSnake = false },
                        label = { Text("Standard Linear Draft") }
                    )
                }

                Text("Number of League Teams: $teamCount", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(4, 6, 8, 10, 12, 16).forEach { count ->
                        FilterChip(
                            selected = teamCount == count,
                            onClick = {
                                teamCount = count
                                if (userSlot > count) userSlot = count
                            },
                            label = { Text("$count Teams") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Your Draft Order Slot: #${userSlot.coerceIn(1, teamCount)}", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { userSlot = Random.nextInt(1, teamCount + 1) }) {
                        Text("Randomize Pick")
                    }
                }
                Slider(
                    value = userSlot.coerceIn(1, teamCount).toFloat(),
                    onValueChange = { userSlot = it.roundToInt().coerceIn(1, teamCount) },
                    valueRange = 1f..teamCount.toFloat()
                )
            }
        }

        // Position Requirements & Roster Size
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Roster Requirements (${previewConfig.totalRounds} Rounds • ${previewConfig.totalPicks} Total Picks)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                PositionStepperRow("Centers (C)", reqC, 1..4) { reqC = it }
                PositionStepperRow("Left Wings (LW)", reqLW, 1..4) { reqLW = it }
                PositionStepperRow("Right Wings (RW)", reqRW, 1..4) { reqRW = it }
                PositionStepperRow("Defensemen (D)", reqD, 2..6) { reqD = it }
                PositionStepperRow("Goaltenders (G)", reqG, 1..3) { reqG = it }
                PositionStepperRow("Bench / Utility", bench, 0..4) { bench = it }
            }
        }

        // Salary Cap & Pick Timer
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enforce NHL Salary Cap", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Switch(checked = enforceCap, onCheckedChange = { enforceCap = it })
                }
                if (enforceCap) {
                    Text("Draft Salary Cap: $${String.format("%.1f", capMillions)}M CAD", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = capMillions,
                        onValueChange = { capMillions = it },
                        valueRange = 80f..120f
                    )
                }

                Text("Draft Pick Timer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0 to "Disabled", 30 to "30s", 60 to "60s", 90 to "90s", 120 to "120s").forEach { (sec, label) ->
                        FilterChip(
                            selected = timerSec == sec,
                            onClick = { timerSec = sec },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        Button(
            onClick = { onLaunchDraft(previewConfig) },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start Fantasy Draft (${previewConfig.teamCount} Teams)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun PositionStepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedIconButton(
                onClick = { if (value > range.first) onChange(value - 1) },
                enabled = value > range.first,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
            }
            Text(
                text = "$value",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.width(24.dp)
            )
            OutlinedIconButton(
                onClick = { if (value < range.last) onChange(value + 1) },
                enabled = value < range.last,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ActiveDraftRoomView(
    session: DraftSessionEntity,
    config: DraftConfig,
    picks: List<DraftPickEntity>,
    fantasyTeams: List<FantasyTeamEntity>,
    humanTeam: FantasyTeamEntity?,
    allScoredPlayers: List<ScoredPlayerItem>,
    timerSecondsLeft: Int,
    onPickPlayer: (Long) -> Unit,
    onAutoPick: () -> Unit,
    onUndo: () -> Unit,
    onSimToEnd: () -> Unit,
    onReconfigure: () -> Unit,
    onPlayerClick: (Long) -> Unit
) {
    var activeSubTab by remember { mutableIntStateOf(0) } // 0=Available, 1=Draft Log, 2=My Picks
    var search by remember { mutableStateOf("") }
    var posFilter by remember { mutableStateOf(PositionFilter.ALL) }

    val teamOrder = session.teamOrderCsv.split(",").filter { it.isNotBlank() }
    val (round, pickInRound, onClockTeamId) = if (teamOrder.isNotEmpty()) {
        DraftEngine.getPickMetadata(session.currentOverallPick, teamOrder, config.isSnakeDraft)
    } else Triple(1, 1, NhlFantasyRepository.USER_TEAM_ID)

    val onClockTeam = fantasyTeams.firstOrNull { it.teamId == onClockTeamId }
    val isUserTurn = onClockTeamId == NhlFantasyRepository.USER_TEAM_ID

    val userRoster = allScoredPlayers.filter { it.player.ownerFantasyTeamId == NhlFantasyRepository.USER_TEAM_ID }
    val userPayroll = userRoster.sumOf { it.player.capHitCad }

    val neededText = buildList {
        val cLeft = config.requiredCenters - userRoster.count { it.player.position == Position.CENTER }
        val lwLeft = config.requiredLeftWings - userRoster.count { it.player.position == Position.LEFT_WING }
        val rwLeft = config.requiredRightWings - userRoster.count { it.player.position == Position.RIGHT_WING }
        val dLeft = config.requiredDefensemen - userRoster.count { it.player.position == Position.DEFENSE }
        val gLeft = config.requiredGoalies - userRoster.count { it.player.position == Position.GOALIE }
        if (cLeft > 0) add("${cLeft}C")
        if (lwLeft > 0) add("${lwLeft}LW")
        if (rwLeft > 0) add("${rwLeft}RW")
        if (dLeft > 0) add("${dLeft}D")
        if (gLeft > 0) add("${gLeft}G")
    }.joinToString(", ").ifBlank { "Bench Depth" }

    val normSearch = TextNormalizer.normalizeForMatch(search)
    val availablePool = remember(allScoredPlayers, normSearch, posFilter) {
        allScoredPlayers
            .filter { item ->
                val p = item.player
                p.ownerFantasyTeamId == null &&
                    (normSearch.isBlank() || p.normalizedSearchName.contains(normSearch) || p.teamAbbrev.lowercase().contains(normSearch)) &&
                    when (posFilter) {
                        PositionFilter.ALL -> true
                        PositionFilter.FORWARDS -> p.position.isForward
                        PositionFilter.CENTER -> p.position == Position.CENTER
                        PositionFilter.LEFT_WING -> p.position == Position.LEFT_WING
                        PositionFilter.RIGHT_WING -> p.position == Position.RIGHT_WING
                        PositionFilter.DEFENSE -> p.position == Position.DEFENSE
                        PositionFilter.GOALIE -> p.position == Position.GOALIE
                        PositionFilter.ROOKIES_PROSPECTS -> p.isRookie || p.isProspect
                    }
            }
            .sortedByDescending { it.player.overallRating }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // On The Clock Banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, if (isUserTurn) ProfitEmerald else IceCyan)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ROUND $round OF ${config.totalRounds} • PICK #${session.currentOverallPick} (Rd Pick $pickInRound)",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isUserTurn) ProfitEmerald else IceCyan,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (isUserTurn) "YOU ARE ON THE CLOCK!" else "CPU Picking: ${onClockTeam?.teamName ?: onClockTeamId}...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    if (config.pickTimerSeconds > 0 && isUserTurn) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (timerSecondsLeft <= 10) AlertRed.copy(alpha = 0.2f) else IceCyan.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${timerSecondsLeft}s",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (timerSecondsLeft <= 10) AlertRed else IceCyan,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Text(
                    text = "Mandatory Needs: $neededText • Cash: ${CadFormatter.formatCompact(humanTeam?.cashBalanceCad ?: 0L)}" +
                        (if (config.enforceSalaryCap) " • Cap Left: ${CadFormatter.formatCompact(config.salaryCapCad - userPayroll)}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Draft Controls Row (Undo, Auto-Pick, Sim Draft, New Setup)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onAutoPick,
                        enabled = isUserTurn
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Auto-Pick")
                    }
                    OutlinedButton(
                        onClick = onUndo,
                        enabled = picks.any { it.isHumanPick }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Undo")
                    }
                    OutlinedButton(onClick = onSimToEnd) {
                        Text("Sim Rest")
                    }
                    TextButton(onClick = onReconfigure) {
                        Text("New Draft")
                    }
                }
            }
        }

        // Sub-Tabs: Draft Pool | Pick History | My Roster
        TabRow(selectedTabIndex = activeSubTab) {
            Tab(selected = activeSubTab == 0, onClick = { activeSubTab = 0 }, text = { Text("Players (${availablePool.size})") })
            Tab(selected = activeSubTab == 1, onClick = { activeSubTab = 1 }, text = { Text("History (${picks.size})") })
            Tab(selected = activeSubTab == 2, onClick = { activeSubTab = 2 }, text = { Text("My Picks (${userRoster.size})") })
        }

        when (activeSubTab) {
            0 -> {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search available draft pool...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PositionFilter.entries.forEach { pf ->
                        FilterChip(
                            selected = posFilter == pf,
                            onClick = { posFilter = pf },
                            label = { Text(pf.label) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 14.dp)
                ) {
                    items(availablePool, key = { it.player.playerId }) { item ->
                        val p = item.player
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayerClick(p.playerId) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PlayerHeadshot(p.headshotUrl, p.fullName, p.teamAbbrev, p.sweaterNumber, size = 44.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        PositionPill(p.position)
                                        Text(p.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        InjuryBadge(p.injurySeverity, p.injuryStatus)
                                    }
                                    Text(
                                        text = "${p.teamAbbrev} • ${item.fantasy.seasonPoints} FP • Cost ${CadFormatter.formatShortNoSuffix(p.purchasePriceCad)} • Cap ${CadFormatter.formatShortNoSuffix(p.capHitCad)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OvrBadge(p.overallRating)
                                Button(
                                    onClick = { onPickPlayer(p.playerId) },
                                    enabled = isUserTurn,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Draft", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(picks.reversed(), key = { it.overallPick }) { pick ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (pick.isHumanPick) ProfitEmerald.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayerClick(pick.playerId) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "#${pick.overallPick}\nR${pick.roundNumber}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (pick.isHumanPick) ProfitEmerald else IceCyan,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${pick.playerName} (${Position.fromCode(pick.positionCode).uiShort} • ${pick.nhlTeamAbbrev})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${pick.fantasyTeamName} — ${pick.rationale}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OvrBadge(pick.overallRating)
                            }
                        }
                    }
                }
            }
            2 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(userRoster.sortedByDescending { it.player.overallRating }, key = { it.player.playerId }) { item ->
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
                                PositionPill(p.position)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("${p.teamAbbrev} • Slot ${p.lineupSlot.code} • Cap ${CadFormatter.formatCompact(p.capHitCad)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OvrBadge(p.overallRating)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftCompletedSummaryView(
    picks: List<DraftPickEntity>,
    fantasyTeams: List<FantasyTeamEntity>,
    canUndo: Boolean,
    onUndoLastPick: () -> Unit,
    onStartNewDraft: () -> Unit,
    onManageRoster: () -> Unit
) {
    val reports = remember(fantasyTeams, picks) {
        DraftEngine.buildPostDraftSummary(fantasyTeams, picks)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Fantasy Draft Summary & Grades", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text("${picks.size} total picks completed • Rosters synced to My Team & Season Matchups", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onManageRoster, modifier = Modifier.weight(1f)) {
                    Text("Manage My Lineup", fontWeight = FontWeight.Bold)
                }
                if (canUndo) {
                    OutlinedButton(onClick = onUndoLastPick) {
                        Text("Undo Pick")
                    }
                }
                OutlinedButton(onClick = onStartNewDraft) {
                    Text("New Draft")
                }
            }
        }

        items(reports, key = { it.teamId }) { rep ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (rep.isHuman) 1.6.dp else 1.dp,
                    color = if (rep.isHuman) ProfitEmerald else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${rep.teamName}${if (rep.isHuman) " (Your Team)" else ""}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (rep.isHuman) ProfitEmerald else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${rep.picksCount} Picks • Avg ${rep.averageOvr} OVR • Payroll ${CadFormatter.formatCompact(rep.totalPayrollCad)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = TrophyGold.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, TrophyGold)
                        ) {
                            Text(
                                text = rep.letterGrade,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = TrophyGold
                            )
                        }
                    }
                    Text("Cornerstone Pick: ${rep.bestPickName}", style = MaterialTheme.typography.bodySmall)
                    Text("Best Value Steal: ${rep.bestValueStealName}", style = MaterialTheme.typography.bodySmall, color = IceCyan)
                }
            }
        }
    }
}
