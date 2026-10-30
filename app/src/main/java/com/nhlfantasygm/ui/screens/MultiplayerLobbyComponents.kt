package com.nhlfantasygm.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.model.*
import com.nhlfantasygm.multiplayer.*
import com.nhlfantasygm.ui.components.FranchiseCrestBadge
import com.nhlfantasygm.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun DraftModeSelectorBanner(
    isMultiplayerTabSelected: Boolean,
    mpState: MultiplayerUiState,
    onSelectMultiplayerTab: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (mpState.isMultiplayerActive) ProfitEmerald.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !isMultiplayerTabSelected,
                    onClick = { onSelectMultiplayerTab(false) },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(17.dp))
                    },
                    label = { Text("Solo vs CPU GMs", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = isMultiplayerTabSelected,
                    onClick = { onSelectMultiplayerTab(true) },
                    leadingIcon = {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(17.dp))
                    },
                    label = { Text("Multiplayer Friends", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (mpState.isMultiplayerActive) {
                val formattedCode = RoomCrypto.formatInviteCodeForDisplay(mpState.roomCode)
                val connColor = when (mpState.connectionState) {
                    MultiplayerConnectionState.CONNECTED -> ProfitEmerald
                    MultiplayerConnectionState.CONNECTING, MultiplayerConnectionState.RECONNECTING -> IceCyan
                    MultiplayerConnectionState.ERROR -> AlertRed
                    MultiplayerConnectionState.DISCONNECTED -> WarningAmber
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(connColor)
                        )
                        Text(
                            text = "Room $formattedCode • ${if (mpState.role == RoomRole.HOST) "Host Commissioner" else "Joined Friend"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = connColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = "${mpState.totalHumanParticipantsCount} Human GM${if (mpState.totalHumanParticipantsCount == 1) "" else "s"} • ${mpState.connectionState.badgeLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MultiplayerSetupAndLobbyView(
    mpState: MultiplayerUiState,
    humanTeam: FantasyTeamEntity?,
    initialDraftConfig: DraftConfig,
    initialJoinMode: Boolean? = null,
    onHostRoom: (String, DraftConfig) -> Unit,
    onJoinRoom: (String, String, String, String, TeamEmblem, Long, Long) -> Unit,
    onBuildShareText: () -> String,
    onAddLocalFriend: (String, String, String, TeamEmblem, Long, Long) -> Unit,
    onRemoveParticipant: (String) -> Unit,
    onMoveParticipant: (String, Boolean) -> Unit,
    onRandomizeOrder: () -> Unit,
    onUpdateHostRules: (DraftConfig, Boolean) -> Unit,
    onUpdateGuestProfileAndReady: (String, String, String, TeamEmblem, Long, Long, Boolean) -> Unit,
    onSendChat: (String) -> Unit,
    onLaunchMultiplayerDraft: () -> Unit,
    onLeaveRoom: () -> Unit
) {
    when (mpState.role) {
        RoomRole.NONE -> MultiplayerCreateOrJoinSetup(
            mpState = mpState,
            humanTeam = humanTeam,
            initialDraftConfig = initialDraftConfig,
            initialJoinMode = initialJoinMode,
            onHostRoom = onHostRoom,
            onJoinRoom = onJoinRoom
        )
        RoomRole.HOST -> MultiplayerHostLobbyScreen(
            mpState = mpState,
            onBuildShareText = onBuildShareText,
            onAddLocalFriend = onAddLocalFriend,
            onRemoveParticipant = onRemoveParticipant,
            onMoveParticipant = onMoveParticipant,
            onRandomizeOrder = onRandomizeOrder,
            onUpdateHostRules = onUpdateHostRules,
            onSendChat = onSendChat,
            onLaunchMultiplayerDraft = onLaunchMultiplayerDraft,
            onCloseRoom = onLeaveRoom
        )
        RoomRole.GUEST -> MultiplayerGuestLobbyScreen(
            mpState = mpState,
            onBuildShareText = onBuildShareText,
            onUpdateGuestProfileAndReady = onUpdateGuestProfileAndReady,
            onSendChat = onSendChat,
            onLeaveRoom = onLeaveRoom
        )
    }
}

@Composable
private fun MultiplayerCreateOrJoinSetup(
    mpState: MultiplayerUiState,
    humanTeam: FantasyTeamEntity?,
    initialDraftConfig: DraftConfig,
    initialJoinMode: Boolean? = null,
    onHostRoom: (String, DraftConfig) -> Unit,
    onJoinRoom: (String, String, String, String, TeamEmblem, Long, Long) -> Unit
) {
    var subMode by remember(mpState.pendingDeepLinkCode, initialJoinMode) {
        mutableIntStateOf(
            when (initialJoinMode) {
                true -> 1
                false -> 0
                null -> if (mpState.pendingDeepLinkCode.isNotBlank()) 1 else 0
            }
        )
    }
    var hostGmName by remember(mpState.localGmName) { mutableStateOf(mpState.localGmName) }

    var inviteInput by remember(mpState.pendingDeepLinkCode) {
        mutableStateOf(
            if (mpState.pendingDeepLinkCode.isNotBlank()) {
                RoomCrypto.formatInviteCodeForDisplay(mpState.pendingDeepLinkCode)
            } else ""
        )
    }
    var guestGmName by remember(mpState.localGmName) { mutableStateOf(mpState.localGmName) }
    var guestTeamName by remember { mutableStateOf("Atlantic Frost HC") }
    var guestAbbrev by remember { mutableStateOf("AFH") }
    var guestEmblem by remember { mutableStateOf(TeamEmblem.ARCTIC_WOLF) }
    val secondPreset = TeamColorPreset.presets.getOrNull(1) ?: TeamColorPreset.presets.first()
    var guestPrimaryHex by remember { mutableLongStateOf(secondPreset.primaryHex) }
    var guestSecondaryHex by remember { mutableLongStateOf(secondPreset.secondaryHex) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Multiplayer Draft & League",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Invite real friends across Android phones via encrypted Room Code or one-tap invite link — or add local Pass-and-Play friends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TabRow(selectedTabIndex = subMode) {
            Tab(
                selected = subMode == 0,
                onClick = { subMode = 0 },
                text = { Text("Host & Invite Friends", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = subMode == 1,
                onClick = { subMode = 1 },
                text = { Text("Join Friend's Draft", fontWeight = FontWeight.Bold) }
            )
        }

        if (subMode == 0) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, IceCyan.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FranchiseCrestBadge(
                            emblem = TeamEmblem.fromId(humanTeam?.emblemId),
                            primaryColorHex = humanTeam?.primaryColorHex ?: 0xFF00C2FF,
                            secondaryColorHex = humanTeam?.secondaryColorHex ?: 0xFF0B192C,
                            abbreviation = humanTeam?.abbreviation ?: "NIC",
                            size = 56.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = humanTeam?.teamName ?: "Northern Ice HC",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Host Commissioner Franchise • Virtual CAD League",
                                style = MaterialTheme.typography.bodySmall,
                                color = IceCyan
                            )
                        }
                    }

                    OutlinedTextField(
                        value = hostGmName,
                        onValueChange = { hostGmName = it.take(24) },
                        label = { Text("Your GM Display Name") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "How Multiplayer Works",
                                style = MaterialTheme.typography.labelLarge,
                                color = ProfitEmerald,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "• Generates a 6-character Room Code (e.g. K9M-4P2) & shareable nhlfantasygm://join invite link.\n" +
                                    "• End-to-end encrypted (AES-256-GCM) live draft picks, chat, lineups & player trades.\n" +
                                    "• Real friends control their own teams; optional CPU GMs only fill explicitly open slots.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { onHostRoom(hostGmName.ifBlank { "Commissioner" }, initialDraftConfig) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Create Lobby & Get Invite Code",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ProfitEmerald.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter Friend's Room Code or Invite Link",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )

                    OutlinedTextField(
                        value = inviteInput,
                        onValueChange = { inviteInput = it },
                        label = { Text("6-Char Invite Code (e.g. K9M-4P2)") },
                        placeholder = { Text("Paste K9M-4P2 or nhlfantasygm://join?code=...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = ProfitEmerald) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = guestGmName,
                        onValueChange = { guestGmName = it.take(24) },
                        label = { Text("Your GM Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    Text(
                        text = "Your Multiplayer Franchise Identity",
                        style = MaterialTheme.typography.labelLarge,
                        color = IceCyan,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FranchiseCrestBadge(
                            emblem = guestEmblem,
                            primaryColorHex = guestPrimaryHex,
                            secondaryColorHex = guestSecondaryHex,
                            abbreviation = guestAbbrev.ifBlank { "AFH" },
                            size = 56.dp
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = guestTeamName,
                                onValueChange = { guestTeamName = it.take(26) },
                                label = { Text("Your Team Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = guestAbbrev,
                                onValueChange = { guestAbbrev = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(3) },
                                label = { Text("3-Letter Abbrev") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TeamEmblem.entries.forEach { emb ->
                            FilterChip(
                                selected = guestEmblem == emb,
                                onClick = { guestEmblem = emb },
                                label = { Text(emb.displayName) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TeamColorPreset.presets.forEach { preset ->
                            val selected = guestPrimaryHex == preset.primaryHex
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset.primaryHex))
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) Color.White else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        guestPrimaryHex = preset.primaryHex
                                        guestSecondaryHex = preset.secondaryHex
                                    }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onJoinRoom(
                                inviteInput,
                                guestGmName.ifBlank { "GM Friend" },
                                guestTeamName.ifBlank { "Atlantic Frost HC" },
                                guestAbbrev.ifBlank { "AFH" },
                                guestEmblem,
                                guestPrimaryHex,
                                guestSecondaryHex
                            )
                        },
                        enabled = inviteInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ProfitEmerald, contentColor = Color(0xFF04131E)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Join Friend's Draft Lobby",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiplayerHostLobbyScreen(
    mpState: MultiplayerUiState,
    onBuildShareText: () -> String,
    onAddLocalFriend: (String, String, String, TeamEmblem, Long, Long) -> Unit,
    onRemoveParticipant: (String) -> Unit,
    onMoveParticipant: (String, Boolean) -> Unit,
    onRandomizeOrder: () -> Unit,
    onUpdateHostRules: (DraftConfig, Boolean) -> Unit,
    onSendChat: (String) -> Unit,
    onLaunchMultiplayerDraft: () -> Unit,
    onCloseRoom: () -> Unit
) {
    val context = LocalContext.current
    val formattedCode = RoomCrypto.formatInviteCodeForDisplay(mpState.roomCode)
    var showAddLocalFriendDialog by remember { mutableStateOf(false) }
    var copiedFeedback by remember { mutableStateOf(false) }

    val cfg = mpState.draftConfig
    val humanCount = mpState.participants.size
    val totalTeams = if (mpState.fillEmptySlotsWithCpu) {
        max(cfg.teamCount, humanCount).coerceIn(2, 16)
    } else {
        humanCount
    }
    val cpuCount = (totalTeams - humanCount).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Invite Friends Hero Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, ProfitEmerald)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INVITE REAL PLAYERS TO YOUR DRAFT",
                        style = MaterialTheme.typography.labelMedium,
                        color = ProfitEmerald,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = mpState.connectionState.badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = IceCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = formattedCode,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = IceCyan,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = RoomCrypto.buildDeepLink(mpState.roomCode),
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
                            text = "AES-256 E2EE",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = ProfitEmerald,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val shareText = onBuildShareText()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Join my NHL Fantasy GM Multiplayer Draft ($formattedCode)")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Invite Friends to NHL Draft"))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ProfitEmerald, contentColor = Color(0xFF04131E))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Invite Friends", fontWeight = FontWeight.ExtraBold)
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("NHL Draft Room Code", formattedCode))
                            copiedFeedback = true
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (copiedFeedback) "Copied!" else "Copy Code", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Connected Friends & Draft Slot Order Card
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Joined GMs & Draft Order ($humanCount Human)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (mpState.fillEmptySlotsWithCpu) {
                                "$humanCount Real Human GM${if (humanCount == 1) "" else "s"} + $cpuCount CPU GM${if (cpuCount == 1) "" else "s"} = $totalTeams Total Teams"
                            } else {
                                "$humanCount Real Human GM${if (humanCount == 1) "" else "s"} • 0 CPU GMs (Pure Human League)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = IceCyan
                        )
                    }
                    TextButton(onClick = onRandomizeOrder, enabled = humanCount >= 2) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle")
                    }
                }

                mpState.participants.forEachIndexed { idx, p ->
                    LobbyParticipantRow(
                        slotNumber = idx + 1,
                        participant = p,
                        isHostControlsEnabled = true,
                        canMoveUp = idx > 0,
                        canMoveDown = idx < mpState.participants.lastIndex,
                        onMoveUp = { onMoveParticipant(p.clientId, true) },
                        onMoveDown = { onMoveParticipant(p.clientId, false) },
                        onRemove = { onRemoveParticipant(p.clientId) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { showAddLocalFriendDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Pass & Play Friend (Same Phone)")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fill Open Slots with CPU GMs",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Turn off for a pure human-vs-human draft with only your invited friends.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = mpState.fillEmptySlotsWithCpu,
                        onCheckedChange = { fill -> onUpdateHostRules(cfg, fill) }
                    )
                }
            }
        }

        // 3. Host Draft Rules Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Multiplayer Draft Rules (${cfg.totalRounds} Rounds)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = cfg.isSnakeDraft,
                        onClick = { onUpdateHostRules(cfg.copy(isSnakeDraft = true), mpState.fillEmptySlotsWithCpu) },
                        label = { Text("Snake Draft (1-N, N-1)") }
                    )
                    FilterChip(
                        selected = !cfg.isSnakeDraft,
                        onClick = { onUpdateHostRules(cfg.copy(isSnakeDraft = false), mpState.fillEmptySlotsWithCpu) },
                        label = { Text("Linear Draft") }
                    )
                }

                if (mpState.fillEmptySlotsWithCpu) {
                    Text("Total League Size (Humans + CPU GMs): $totalTeams Teams", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(2, 4, 6, 8, 10, 12, 16).forEach { count ->
                            FilterChip(
                                selected = cfg.teamCount == count,
                                enabled = count >= humanCount,
                                onClick = {
                                    onUpdateHostRules(cfg.copy(teamCount = count), mpState.fillEmptySlotsWithCpu)
                                },
                                label = { Text("$count Teams") }
                            )
                        }
                    }
                }

                Text("Pick Timer Clock", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0 to "No Timer", 30 to "30s", 60 to "60s", 90 to "90s", 120 to "120s").forEach { (sec, label) ->
                        FilterChip(
                            selected = cfg.pickTimerSeconds == sec,
                            onClick = {
                                onUpdateHostRules(cfg.copy(pickTimerSeconds = sec), mpState.fillEmptySlotsWithCpu)
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enforce NHL Salary Cap (${CadFormatter.formatCompact(cfg.salaryCapCad)})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = cfg.enforceSalaryCap,
                        onCheckedChange = { enf ->
                            onUpdateHostRules(cfg.copy(enforceSalaryCap = enf), mpState.fillEmptySlotsWithCpu)
                        }
                    )
                }

                MultiplayerCompactRosterRow(
                    cfg = cfg,
                    onChange = { updated -> onUpdateHostRules(updated, mpState.fillEmptySlotsWithCpu) }
                )
            }
        }

        // 4. Inline Live Lobby Chat
        LobbyInlineChatCard(
            messages = mpState.chatMessages,
            localClientId = mpState.localClientId,
            onSendChat = onSendChat
        )

        // 5. Launch or Close Lobby Actions
        val canLaunch = totalTeams >= 2
        if (!canLaunch) {
            Text(
                text = "Invite at least 1 friend (or enable CPU GMs) so the draft has at least 2 teams.",
                style = MaterialTheme.typography.bodySmall,
                color = WarningAmber
            )
        }

        Button(
            onClick = onLaunchMultiplayerDraft,
            enabled = canLaunch,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Launch Multiplayer Draft ($totalTeams Teams)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }

        OutlinedButton(
            onClick = onCloseRoom,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Close Multiplayer Lobby", color = AlertRed)
        }
    }

    if (showAddLocalFriendDialog) {
        AddOrEditParticipantDialog(
            dialogTitle = "Add Local Pass-and-Play Friend",
            initialGmName = "Friend ${humanCount + 1}",
            initialTeamName = "Pacific Blades HC",
            initialAbbrev = "PBL",
            initialEmblem = TeamEmblem.CROSSED_STICKS,
            initialPrimaryHex = TeamColorPreset.presets.getOrNull(humanCount % TeamColorPreset.presets.size)?.primaryHex ?: 0xFFFFB800,
            initialSecondaryHex = TeamColorPreset.presets.getOrNull(humanCount % TeamColorPreset.presets.size)?.secondaryHex ?: 0xFF1F1600,
            confirmLabel = "Add Friend to Draft",
            onDismiss = { showAddLocalFriendDialog = false },
            onConfirm = { gm, tName, abbr, emb, pHex, sHex ->
                onAddLocalFriend(gm, tName, abbr, emb, pHex, sHex)
                showAddLocalFriendDialog = false
            }
        )
    }
}

@Composable
private fun MultiplayerGuestLobbyScreen(
    mpState: MultiplayerUiState,
    onBuildShareText: () -> String,
    onUpdateGuestProfileAndReady: (String, String, String, TeamEmblem, Long, Long, Boolean) -> Unit,
    onSendChat: (String) -> Unit,
    onLeaveRoom: () -> Unit
) {
    val context = LocalContext.current
    val formattedCode = RoomCrypto.formatInviteCodeForDisplay(mpState.roomCode)
    val myParticipant = mpState.participants.firstOrNull { it.clientId == mpState.localClientId }
    var showEditIdentityDialog by remember { mutableStateOf(false) }

    val cfg = mpState.draftConfig
    val humanCount = mpState.participants.size
    val totalTeams = if (mpState.fillEmptySlotsWithCpu) max(cfg.teamCount, humanCount) else humanCount
    val cpuCount = (totalTeams - humanCount).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, IceCyan)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "JOINED FRIEND'S LOBBY • $formattedCode",
                            style = MaterialTheme.typography.labelMedium,
                            color = IceCyan,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (myParticipant != null) {
                                "Connected! Waiting for Host Commissioner to launch draft..."
                            } else {
                                "Handshaking with Host Commissioner (${mpState.connectionState.badgeLabel})..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            val shareText = onBuildShareText()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Room Invite"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Invite", tint = IceCyan)
                    }
                }

                if (myParticipant != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FranchiseCrestBadge(
                            emblem = TeamEmblem.fromId(myParticipant.emblemId),
                            primaryColorHex = myParticipant.primaryColorHex,
                            secondaryColorHex = myParticipant.secondaryColorHex,
                            abbreviation = myParticipant.abbreviation,
                            size = 48.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(myParticipant.teamName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            Text("GM ${myParticipant.gmName} • Your Franchise", style = MaterialTheme.typography.bodySmall, color = ProfitEmerald)
                        }
                        OutlinedButton(onClick = { showEditIdentityDialog = true }) {
                            Text("Edit Team")
                        }
                    }

                    val isReady = myParticipant.isReady
                    Button(
                        onClick = {
                            onUpdateGuestProfileAndReady(
                                myParticipant.gmName,
                                myParticipant.teamName,
                                myParticipant.abbreviation,
                                TeamEmblem.fromId(myParticipant.emblemId),
                                myParticipant.primaryColorHex,
                                myParticipant.secondaryColorHex,
                                !isReady
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isReady) ProfitEmerald else IceCyan,
                            contentColor = Color(0xFF04131E)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isReady) "READY TO DRAFT ✓ (Tap to Unready)" else "Tap When Ready to Draft",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Lobby Roster & Rules Summary
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Draft Order & Rules ($totalTeams Teams • ${cfg.totalRounds} Rounds)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${if (cfg.isSnakeDraft) "Snake Draft" else "Linear Draft"} • " +
                        "$humanCount Real Human GMs + $cpuCount CPU GMs • " +
                        (if (cfg.pickTimerSeconds > 0) "${cfg.pickTimerSeconds}s Clock" else "No Timer") +
                        (if (cfg.enforceSalaryCap) " • ${CadFormatter.formatCompact(cfg.salaryCapCad)} Cap" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = IceCyan
                )

                mpState.participants.forEachIndexed { idx, p ->
                    LobbyParticipantRow(
                        slotNumber = idx + 1,
                        participant = p,
                        isHostControlsEnabled = false,
                        canMoveUp = false,
                        canMoveDown = false,
                        onMoveUp = {},
                        onMoveDown = {},
                        onRemove = {}
                    )
                }
            }
        }

        LobbyInlineChatCard(
            messages = mpState.chatMessages,
            localClientId = mpState.localClientId,
            onSendChat = onSendChat
        )

        OutlinedButton(
            onClick = onLeaveRoom,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Leave Friend's Lobby", color = AlertRed)
        }
    }

    if (showEditIdentityDialog && myParticipant != null) {
        AddOrEditParticipantDialog(
            dialogTitle = "Customize Your Franchise",
            initialGmName = myParticipant.gmName,
            initialTeamName = myParticipant.teamName,
            initialAbbrev = myParticipant.abbreviation,
            initialEmblem = TeamEmblem.fromId(myParticipant.emblemId),
            initialPrimaryHex = myParticipant.primaryColorHex,
            initialSecondaryHex = myParticipant.secondaryColorHex,
            confirmLabel = "Save Identity",
            onDismiss = { showEditIdentityDialog = false },
            onConfirm = { gm, tName, abbr, emb, pHex, sHex ->
                onUpdateGuestProfileAndReady(gm, tName, abbr, emb, pHex, sHex, myParticipant.isReady)
                showEditIdentityDialog = false
            }
        )
    }
}

@Composable
private fun LobbyParticipantRow(
    slotNumber: Int,
    participant: LobbyParticipant,
    isHostControlsEnabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val badgeText = when {
        participant.isHost -> "Host Commissioner"
        participant.isLocalPassAndPlay -> "Local Friend • Pass & Play"
        participant.isReady -> "Online Friend • Ready ✓"
        else -> "Online Friend • Joined"
    }
    val badgeTone = when {
        participant.isHost -> IceCyan
        participant.isLocalPassAndPlay -> TrophyGold
        participant.isReady -> ProfitEmerald
        else -> WarningAmber
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, badgeTone.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "#$slotNumber",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = IceCyan
            )
            FranchiseCrestBadge(
                emblem = TeamEmblem.fromId(participant.emblemId),
                primaryColorHex = participant.primaryColorHex,
                secondaryColorHex = participant.secondaryColorHex,
                abbreviation = participant.abbreviation,
                size = 38.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${participant.teamName} (${participant.abbreviation})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "GM ${participant.gmName} • $badgeText",
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeTone,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isHostControlsEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                    }
                    if (!participant.isHost) {
                        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = AlertRed, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiplayerCompactRosterRow(
    cfg: DraftConfig,
    onChange: (DraftConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Roster Slots: ${cfg.requiredCenters}C • ${cfg.requiredLeftWings}LW • ${cfg.requiredRightWings}RW • ${cfg.requiredDefensemen}D • ${cfg.requiredGoalies}G • ${cfg.benchSlots} Bench",
            style = MaterialTheme.typography.labelLarge
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                onClick = {
                    onChange(
                        cfg.copy(
                            requiredCenters = 2,
                            requiredLeftWings = 2,
                            requiredRightWings = 2,
                            requiredDefensemen = 4,
                            requiredGoalies = 2,
                            benchSlots = 3
                        )
                    )
                },
                label = { Text("Standard 15-Round") }
            )
            AssistChip(
                onClick = {
                    onChange(
                        cfg.copy(
                            requiredCenters = 1,
                            requiredLeftWings = 1,
                            requiredRightWings = 1,
                            requiredDefensemen = 2,
                            requiredGoalies = 1,
                            benchSlots = 2
                        )
                    )
                },
                label = { Text("Quick 8-Round") }
            )
            AssistChip(
                onClick = {
                    onChange(
                        cfg.copy(
                            requiredCenters = 4,
                            requiredLeftWings = 4,
                            requiredRightWings = 4,
                            requiredDefensemen = 6,
                            requiredGoalies = 2,
                            benchSlots = 3
                        )
                    )
                },
                label = { Text("Deep 23-Round NHL") }
            )
        }
    }
}

@Composable
fun LobbyInlineChatCard(
    messages: List<MultiplayerChatMessage>,
    localClientId: String,
    onSendChat: (String) -> Unit
) {
    var draftText by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.CANADA) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Live Draft Room Chat (${messages.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 70.dp, max = 180.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (messages.isEmpty()) {
                    Text(
                        text = "No messages yet. Say hello to your fellow GMs!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    messages.takeLast(30).forEach { m ->
                        if (m.isSystemEvent) {
                            Text(
                                text = "★ ${m.text}",
                                style = MaterialTheme.typography.labelSmall,
                                color = IceCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            val isMe = m.senderClientId == localClientId
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${m.senderGmName} (${m.senderTeamAbbrev}): ${m.text}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isMe) ProfitEmerald else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = fmt.format(Date(m.timestampEpochMs)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it.take(240) },
                    placeholder = { Text("Send message or trade banter...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                FilledIconButton(
                    onClick = {
                        if (draftText.isNotBlank()) {
                            onSendChat(draftText)
                            draftText = ""
                        }
                    },
                    enabled = draftText.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun MultiplayerChatSheetDialog(
    mpState: MultiplayerUiState,
    onBuildShareText: () -> String,
    onSendChat: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val formattedCode = RoomCrypto.formatInviteCodeForDisplay(mpState.roomCode)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.2.dp, IceCyan)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Room $formattedCode • Live War Room",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${mpState.totalHumanParticipantsCount} Human GMs • ${mpState.connectionState.badgeLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ProfitEmerald
                        )
                    }
                    Row {
                        IconButton(
                            onClick = {
                                val shareText = onBuildShareText()
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Room Invite"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share Invite", tint = IceCyan)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                LobbyInlineChatCard(
                    messages = mpState.chatMessages,
                    localClientId = mpState.localClientId,
                    onSendChat = onSendChat
                )
            }
        }
    }
}

@Composable
private fun AddOrEditParticipantDialog(
    dialogTitle: String,
    initialGmName: String,
    initialTeamName: String,
    initialAbbrev: String,
    initialEmblem: TeamEmblem,
    initialPrimaryHex: Long,
    initialSecondaryHex: Long,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, TeamEmblem, Long, Long) -> Unit
) {
    var gmName by remember { mutableStateOf(initialGmName) }
    var teamName by remember { mutableStateOf(initialTeamName) }
    var abbrev by remember { mutableStateOf(initialAbbrev) }
    var emblem by remember { mutableStateOf(initialEmblem) }
    var primaryHex by remember { mutableLongStateOf(initialPrimaryHex) }
    var secondaryHex by remember { mutableLongStateOf(initialSecondaryHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle, fontWeight = FontWeight.Bold) },
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
                        abbreviation = abbrev.ifBlank { "NHL" },
                        size = 64.dp
                    )
                }
                OutlinedTextField(
                    value = gmName,
                    onValueChange = { gmName = it.take(24) },
                    label = { Text("GM Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teamName,
                    onValueChange = { teamName = it.take(26) },
                    label = { Text("Franchise Team Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = abbrev,
                    onValueChange = { abbrev = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(3) },
                    label = { Text("3-Letter Abbreviation") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TeamColorPreset.presets.forEach { preset ->
                        val selected = primaryHex == preset.primaryHex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
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
            Button(
                onClick = {
                    onConfirm(
                        gmName.ifBlank { "Friend GM" },
                        teamName.ifBlank { "Pacific Blades HC" },
                        abbrev.ifBlank { "PBL" },
                        emblem,
                        primaryHex,
                        secondaryHex
                    )
                }
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
