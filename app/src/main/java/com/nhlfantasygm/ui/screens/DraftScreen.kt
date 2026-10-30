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
import com.nhlfantasygm.model.TeamEmblem
import com.nhlfantasygm.multiplayer.MultiplayerRoomPhase
import com.nhlfantasygm.multiplayer.MultiplayerUiState
import com.nhlfantasygm.multiplayer.RoomCrypto
import com.nhlfantasygm.multiplayer.RoomRole
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
    controlledTeamId: String = NhlFantasyRepository.USER_TEAM_ID,
    mpState: MultiplayerUiState = MultiplayerUiState(),
    allScoredPlayers: List<ScoredPlayerItem>,
    timerRemainingSeconds: Int,
    initialMultiplayerMode: Boolean = false,
    initialJoinMode: Boolean? = null,
    onStartDraft: (DraftConfig) -> Unit,
    onMakeHumanPick: (Long) -> Unit,
    onAutoPickHuman: () -> Unit,
    onUndoLastPick: () -> Unit,
    onSimRestOfDraft: () -> Unit,
    onPlayerClick: (Long) -> Unit,
    onGoToMyTeam: () -> Unit,
    onHostMultiplayerRoom: (String, DraftConfig) -> Unit = { _, _ -> },
    onJoinMultiplayerRoom: (String, String, String, String, TeamEmblem, Long, Long) -> Unit = { _, _, _, _, _, _, _ -> },
    onBuildMultiplayerShareText: () -> String = { "" },
    onAddLocalPassAndPlayFriend: (String, String, String, TeamEmblem, Long, Long) -> Unit = { _, _, _, _, _, _ -> },
    onRemoveLobbyParticipant: (String) -> Unit = {},
    onMoveLobbyParticipant: (String, Boolean) -> Unit = { _, _ -> },
    onRandomizeLobbyOrder: () -> Unit = {},
    onUpdateHostLobbyRules: (DraftConfig, Boolean) -> Unit = { _, _ -> },
    onUpdateGuestLobbyProfile: (String, String, String, TeamEmblem, Long, Long, Boolean) -> Unit = { _, _, _, _, _, _, _ -> },
    onSendMultiplayerChat: (String) -> Unit = {},
    onMarkMultiplayerChatRead: () -> Unit = {},
    onLaunchMultiplayerDraft: () -> Unit = {},
    onLeaveMultiplayerRoom: () -> Unit = {}
) {
    val shouldForceMultiplayerSetup = (initialMultiplayerMode || (initialJoinMode == true) || mpState.pendingDeepLinkCode.isNotBlank()) &&
        !mpState.isMultiplayerActive &&
        (draftSession?.status == "IN_PROGRESS" || draftSession?.status == "COMPLETED")
    var forceSetupView by remember(initialMultiplayerMode, initialJoinMode, mpState.pendingDeepLinkCode) {
        mutableStateOf(shouldForceMultiplayerSetup)
    }
    var isMultiplayerTabSelected by remember(initialMultiplayerMode, initialJoinMode, mpState.isMultiplayerActive, mpState.pendingDeepLinkCode) {
        mutableStateOf(initialMultiplayerMode || (initialJoinMode == true) || mpState.isMultiplayerActive || mpState.pendingDeepLinkCode.isNotBlank())
    }
    var showChatDialog by remember { mutableStateOf(false) }

    LaunchedEffect(mpState.pendingDeepLinkCode, mpState.role, mpState.phase) {
        if (mpState.pendingDeepLinkCode.isNotBlank() ||
            (mpState.isMultiplayerActive && mpState.phase == MultiplayerRoomPhase.LOBBY)
        ) {
            isMultiplayerTabSelected = true
            forceSetupView = true
        } else if (mpState.isMultiplayerActive && mpState.phase == MultiplayerRoomPhase.DRAFT_IN_PROGRESS) {
            forceSetupView = false
        }
    }

    val isInMultiplayerLobby = mpState.isMultiplayerActive && mpState.phase == MultiplayerRoomPhase.LOBBY
    val showSetupOrLobby = draftSession == null ||
        draftSession.status == "NOT_STARTED" ||
        forceSetupView ||
        isInMultiplayerLobby

    when {
        showSetupOrLobby -> {
            Column(modifier = Modifier.fillMaxSize()) {
                DraftModeSelectorBanner(
                    isMultiplayerTabSelected = isMultiplayerTabSelected,
                    mpState = mpState,
                    onSelectMultiplayerTab = { selectedMp ->
                        isMultiplayerTabSelected = selectedMp
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )

                if (isMultiplayerTabSelected) {
                    Box(modifier = Modifier.weight(1f)) {
                        MultiplayerSetupAndLobbyView(
                            mpState = mpState,
                            humanTeam = humanTeam,
                            initialDraftConfig = draftConfig,
                            initialJoinMode = initialJoinMode,
                            onHostRoom = onHostMultiplayerRoom,
                            onJoinRoom = onJoinMultiplayerRoom,
                            onBuildShareText = onBuildMultiplayerShareText,
                            onAddLocalFriend = onAddLocalPassAndPlayFriend,
                            onRemoveParticipant = onRemoveLobbyParticipant,
                            onMoveParticipant = onMoveLobbyParticipant,
                            onRandomizeOrder = onRandomizeLobbyOrder,
                            onUpdateHostRules = onUpdateHostLobbyRules,
                            onUpdateGuestProfileAndReady = onUpdateGuestLobbyProfile,
                            onSendChat = onSendMultiplayerChat,
                            onLaunchMultiplayerDraft = {
                                forceSetupView = false
                                onLaunchMultiplayerDraft()
                            },
                            onLeaveRoom = onLeaveMultiplayerRoom
                        )
                    }
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        DraftSetupConfigurator(
                            initialConfig = draftConfig,
                            hasExistingSession = draftSession != null && draftSession.status != "NOT_STARTED" && !isInMultiplayerLobby,
                            onCancelReconfigure = { forceSetupView = false },
                            onLaunchDraft = { cfg ->
                                forceSetupView = false
                                onStartDraft(cfg)
                            }
                        )
                    }
                }
            }
        }
        draftSession.status == "IN_PROGRESS" -> {
            ActiveDraftRoomView(
                session = draftSession,
                config = draftConfig,
                picks = draftPicks,
                fantasyTeams = fantasyTeams,
                humanTeam = humanTeam,
                controlledTeamId = controlledTeamId,
                mpState = mpState,
                allScoredPlayers = allScoredPlayers,
                timerSecondsLeft = timerRemainingSeconds,
                onPickPlayer = onMakeHumanPick,
                onAutoPick = onAutoPickHuman,
                onUndo = onUndoLastPick,
                onSimToEnd = onSimRestOfDraft,
                onReconfigure = {
                    isMultiplayerTabSelected = mpState.isMultiplayerActive
                    forceSetupView = true
                },
                onOpenMultiplayerInvite = {
                    isMultiplayerTabSelected = true
                    forceSetupView = true
                },
                onOpenChat = {
                    onMarkMultiplayerChatRead()
                    showChatDialog = true
                },
                onPlayerClick = onPlayerClick
            )
        }
        else -> {
            DraftCompletedSummaryView(
                picks = draftPicks,
                fantasyTeams = fantasyTeams,
                controlledTeamId = controlledTeamId,
                mpState = mpState,
                canUndo = mpState.role != RoomRole.GUEST && draftPicks.any { it.isHumanPick },
                onUndoLastPick = onUndoLastPick,
                onStartNewDraft = {
                    isMultiplayerTabSelected = mpState.isMultiplayerActive
                    forceSetupView = true
                },
                onInviteFriendsDraft = {
                    isMultiplayerTabSelected = true
                    forceSetupView = true
                },
                onOpenChat = {
                    onMarkMultiplayerChatRead()
                    showChatDialog = true
                },
                onManageRoster = onGoToMyTeam
            )
        }
    }

    if (showChatDialog && mpState.isMultiplayerActive) {
        MultiplayerChatSheetDialog(
            mpState = mpState,
            onBuildShareText = onBuildMultiplayerShareText,
            onSendChat = onSendMultiplayerChat,
            onDismiss = {
                onMarkMultiplayerChatRead()
                showChatDialog = false
            }
        )
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
            Text("Start Solo Fantasy Draft (${previewConfig.teamCount} Teams)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
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
    controlledTeamId: String,
    mpState: MultiplayerUiState,
    allScoredPlayers: List<ScoredPlayerItem>,
    timerSecondsLeft: Int,
    onPickPlayer: (Long) -> Unit,
    onAutoPick: () -> Unit,
    onUndo: () -> Unit,
    onSimToEnd: () -> Unit,
    onReconfigure: () -> Unit,
    onOpenMultiplayerInvite: () -> Unit,
    onOpenChat: () -> Unit,
    onPlayerClick: (Long) -> Unit
) {
    var activeSubTab by remember { mutableIntStateOf(0) } // 0=Available, 1=Draft Log, 2=My Picks
    var search by remember { mutableStateOf("") }
    var posFilter by remember { mutableStateOf(PositionFilter.ALL) }

    val teamOrder = session.teamOrderCsv.split(",").filter { it.isNotBlank() }
    val (round, pickInRound, onClockTeamId) = if (teamOrder.isNotEmpty()) {
        DraftEngine.getPickMetadata(session.currentOverallPick, teamOrder, config.isSnakeDraft)
    } else Triple(1, 1, controlledTeamId)

    val onClockTeam = fantasyTeams.firstOrNull { it.teamId == onClockTeamId }
    val isMyControlledTurn = onClockTeamId == controlledTeamId
    val isLocalPassAndPlayTurn = mpState.role != RoomRole.GUEST && onClockTeam?.isLocalPassAndPlayGm == true
    val canActOnClock = isMyControlledTurn || isLocalPassAndPlayTurn
    val isRemoteFriendTurn = !canActOnClock && onClockTeam != null && !onClockTeam.isCpuGm

    val actingTeamId = if (isLocalPassAndPlayTurn) onClockTeamId else controlledTeamId
    val actingTeam = fantasyTeams.firstOrNull { it.teamId == actingTeamId } ?: humanTeam

    val myControlledRoster = allScoredPlayers.filter { it.player.ownerFantasyTeamId == controlledTeamId }
    val actingRoster = allScoredPlayers.filter { it.player.ownerFantasyTeamId == actingTeamId }
    val actingPayroll = actingRoster.sumOf { it.player.capHitCad }

    val neededText = buildList {
        val cLeft = config.requiredCenters - actingRoster.count { it.player.position == Position.CENTER }
        val lwLeft = config.requiredLeftWings - actingRoster.count { it.player.position == Position.LEFT_WING }
        val rwLeft = config.requiredRightWings - actingRoster.count { it.player.position == Position.RIGHT_WING }
        val dLeft = config.requiredDefensemen - actingRoster.count { it.player.position == Position.DEFENSE }
        val gLeft = config.requiredGoalies - actingRoster.count { it.player.position == Position.GOALIE }
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

    val bannerTone = when {
        isMyControlledTurn -> ProfitEmerald
        isLocalPassAndPlayTurn -> TrophyGold
        isRemoteFriendTurn -> IceCyan
        else -> RoyalBlue
    }

    val turnHeadline = when {
        isMyControlledTurn -> "YOU ARE ON THE CLOCK! (${humanTeam?.teamName ?: "Your Team"})"
        isLocalPassAndPlayTurn -> "PASS PHONE TO ${onClockTeam?.gmDisplayName?.ifBlank { onClockTeam.teamName }} (${onClockTeam?.teamName})!"
        isRemoteFriendTurn -> {
            val gmTag = onClockTeam?.gmDisplayName?.takeIf { it.isNotBlank() }?.let { " (GM $it)" }.orEmpty()
            "WAITING ON FRIEND: ${onClockTeam?.teamName ?: onClockTeamId}$gmTag..."
        }
        else -> "CPU GM Picking: ${onClockTeam?.teamName ?: onClockTeamId}..."
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
            border = BorderStroke(1.5.dp, bannerTone)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val mpPrefix = if (mpState.isMultiplayerActive) {
                            "LIVE ROOM ${RoomCrypto.formatInviteCodeForDisplay(mpState.roomCode)} • "
                        } else ""
                        Text(
                            text = "${mpPrefix}ROUND $round OF ${config.totalRounds} • PICK #${session.currentOverallPick} (Rd Pick $pickInRound)",
                            style = MaterialTheme.typography.labelMedium,
                            color = bannerTone,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = turnHeadline,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    if (config.pickTimerSeconds > 0 && timerSecondsLeft > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (timerSecondsLeft <= 10) AlertRed.copy(alpha = 0.2f) else bannerTone.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${timerSecondsLeft}s",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (timerSecondsLeft <= 10) AlertRed else bannerTone,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Text(
                    text = "${actingTeam?.abbreviation ?: "GM"} Needs: $neededText • Cash: ${CadFormatter.formatCompact(actingTeam?.cashBalanceCad ?: 0L)}" +
                        (if (config.enforceSalaryCap) " • Cap Left: ${CadFormatter.formatCompact(config.salaryCapCad - actingPayroll)}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Draft Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onAutoPick,
                        enabled = canActOnClock
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Auto-Pick")
                    }

                    if (mpState.isMultiplayerActive) {
                        OutlinedButton(onClick = onOpenChat) {
                            Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            val unread = if (mpState.unreadChatCount > 0) " (${mpState.unreadChatCount})" else ""
                            Text("Chat$unread")
                        }
                    } else {
                        OutlinedButton(onClick = onOpenMultiplayerInvite) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Invite Friends")
                        }
                    }

                    if (mpState.role != RoomRole.GUEST) {
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
                    }

                    TextButton(onClick = onReconfigure) {
                        Text(if (mpState.isMultiplayerActive) "Lobby" else "New Draft")
                    }
                }
            }
        }

        // Sub-Tabs: Draft Pool | Pick History | My Roster
        TabRow(selectedTabIndex = activeSubTab) {
            Tab(selected = activeSubTab == 0, onClick = { activeSubTab = 0 }, text = { Text("Players (${availablePool.size})") })
            Tab(selected = activeSubTab == 1, onClick = { activeSubTab = 1 }, text = { Text("History (${picks.size})") })
            Tab(selected = activeSubTab == 2, onClick = { activeSubTab = 2 }, text = { Text("My Picks (${myControlledRoster.size})") })
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
                                    enabled = canActOnClock,
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
                        val isMyPick = pick.fantasyTeamId == controlledTeamId
                        val borderColor = when {
                            isMyPick -> ProfitEmerald.copy(alpha = 0.65f)
                            pick.isHumanPick -> IceCyan.copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.outline
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, borderColor)
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
                                    color = if (isMyPick) ProfitEmerald else IceCyan,
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
                    items(myControlledRoster.sortedByDescending { it.player.overallRating }, key = { it.player.playerId }) { item ->
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
    controlledTeamId: String,
    mpState: MultiplayerUiState,
    canUndo: Boolean,
    onUndoLastPick: () -> Unit,
    onStartNewDraft: () -> Unit,
    onInviteFriendsDraft: () -> Unit,
    onOpenChat: () -> Unit,
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
            val subtitle = if (mpState.isMultiplayerActive) {
                "Room ${RoomCrypto.formatInviteCodeForDisplay(mpState.roomCode)} • ${picks.size} picks synced across all GMs!"
            } else {
                "${picks.size} total picks completed • Rosters synced to My Team & Season Matchups"
            }
            Text("Fantasy Draft Summary & Grades", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onManageRoster) {
                    Text("Manage My Lineup", fontWeight = FontWeight.Bold)
                }
                if (mpState.isMultiplayerActive) {
                    OutlinedButton(onClick = onOpenChat) {
                        val unread = if (mpState.unreadChatCount > 0) " (${mpState.unreadChatCount})" else ""
                        Text("Room Chat$unread")
                    }
                } else {
                    OutlinedButton(onClick = onInviteFriendsDraft) {
                        Text("Multiplayer Draft")
                    }
                }
                if (canUndo) {
                    OutlinedButton(onClick = onUndoLastPick) {
                        Text("Undo Pick")
                    }
                }
                if (mpState.role != RoomRole.GUEST) {
                    OutlinedButton(onClick = onStartNewDraft) {
                        Text("New Draft")
                    }
                }
            }
        }

        items(reports, key = { it.teamId }) { rep ->
            val isMe = rep.teamId == controlledTeamId
            val isFriendHuman = !isMe && rep.isHuman && rep.ownerType != "CPU_GM"
            val ownerTag = when {
                isMe -> " (Your Team)"
                rep.ownerType == "REMOTE_HUMAN_GM" -> " (Online Friend${if (rep.gmDisplayName.isNotBlank()) ": GM ${rep.gmDisplayName}" else ""})"
                rep.ownerType == "LOCAL_HUMAN_GM" -> " (Local Friend${if (rep.gmDisplayName.isNotBlank()) ": GM ${rep.gmDisplayName}" else ""})"
                isFriendHuman -> " (Friend${if (rep.gmDisplayName.isNotBlank()) ": GM ${rep.gmDisplayName}" else ""})"
                else -> " (CPU GM)"
            }
            val highlightTone = when {
                isMe -> ProfitEmerald
                isFriendHuman -> IceCyan
                else -> MaterialTheme.colorScheme.outline
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (isMe || isFriendHuman) 1.6.dp else 1.dp,
                    color = highlightTone
                )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${rep.teamName}$ownerTag",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isMe) ProfitEmerald else if (isFriendHuman) IceCyan else MaterialTheme.colorScheme.onSurface
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
