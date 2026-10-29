package com.nhlfantasygm.multiplayer

import com.nhlfantasygm.data.repository.NhlFantasyRepository
import com.nhlfantasygm.domain.DraftEngine
import com.nhlfantasygm.model.DraftConfig
import com.nhlfantasygm.model.LineupSlot
import com.nhlfantasygm.model.TeamColorPreset
import com.nhlfantasygm.model.TeamEmblem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates real-time multiplayer lobbies, friend invites (room codes & deep links),
 * authoritative conflict-free draft synchronization, live chat, and human-to-human trading.
 */
class MultiplayerCoordinator(
    private val repository: NhlFantasyRepository,
    private val transportFactory: MultiplayerTransportFactory = MultiplayerTransportFactory.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val hostActionMutex = Mutex()
    private val revisionCounter = AtomicLong(1L)

    // Deduplicate recently processed message IDs (since broker echoes published messages to sender)
    private val processedMessageIds: MutableSet<String> =
        Collections.synchronizedSet(object : LinkedHashSet<String>() {
            override fun add(element: String): Boolean {
                if (size > 256) {
                    val it = iterator()
                    if (it.hasNext()) {
                        it.next()
                        it.remove()
                    }
                }
                return super.add(element)
            }
        })

    private val _uiState = MutableStateFlow(MultiplayerUiState())
    val uiState: StateFlow<MultiplayerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var activeTransport: MultiplayerTransport? = null
    private var transportJobs: Job? = null
    private var lastAppliedGuestRevision: Long = -1L

    init {
        scope.launch {
            val clientId = repository.settingsStore.getOrCreateLocalClientId()
            val initialSettings = repository.settingsFlow.first()
            _uiState.update {
                it.copy(
                    localClientId = clientId,
                    localGmName = initialSettings.gmDisplayName,
                    controlledTeamId = initialSettings.controlledTeamId.ifBlank { NhlFantasyRepository.USER_TEAM_ID },
                    customRelayUrl = initialSettings.customMultiplayerRelayUrl
                )
            }

            // Keep settings-backed fields synchronized
            repository.settingsFlow.collect { s ->
                _uiState.update { cur ->
                    cur.copy(
                        localGmName = s.gmDisplayName,
                        controlledTeamId = s.controlledTeamId.ifBlank { NhlFantasyRepository.USER_TEAM_ID },
                        customRelayUrl = s.customMultiplayerRelayUrl
                    )
                }
            }
        }
    }

    /**
     * Handles an incoming deep link such as `nhlfantasygm://join?code=K9M4P2`
     * or pasted invite link text.
     */
    fun handleIncomingInviteLink(rawUriOrText: String): String? {
        val code = RoomCrypto.extractInviteCodeFromLinkOrText(rawUriOrText) ?: return null
        _uiState.update { it.copy(pendingDeepLinkCode = code) }
        _events.tryEmit("Invite code ${RoomCrypto.formatInviteCodeForDisplay(code)} loaded — tap Join Lobby!")
        return code
    }

    fun clearPendingDeepLinkCode() {
        _uiState.update { it.copy(pendingDeepLinkCode = "") }
    }

    fun markChatRead() {
        _uiState.update { it.copy(unreadChatCount = 0) }
    }

    fun buildInviteShareMessage(roomCode: String = _uiState.value.roomCode): String {
        val formattedCode = RoomCrypto.formatInviteCodeForDisplay(roomCode)
        val deepLink = RoomCrypto.buildDeepLink(roomCode)
        val hostName = _uiState.value.localGmName
        val cfg = _uiState.value.draftConfig
        return buildString {
            appendLine("🏒 Join my NHL Fantasy GM Multiplayer Draft!")
            appendLine("Commissioner: $hostName • ${cfg.teamCount}-Team ${if (cfg.isSnakeDraft) "Snake" else "Standard"} Draft (${cfg.totalRounds} Rounds)")
            appendLine()
            appendLine("🔑 Room Invite Code: $formattedCode")
            appendLine("📲 Tap to Join in App: $deepLink")
            appendLine("⬇️ Download APK (Android): https://github.com/Icheygeko186100/nhl-fantasy-gm/releases/latest")
        }.trim()
    }

    // =========================================================================
    // HOST (COMMISSIONER) LIFECYCLE & ACTIONS
    // =========================================================================

    suspend fun hostNewRoom(
        gmDisplayName: String,
        initialConfig: DraftConfig = DraftConfig()
    ): Result<String> {
        val clientId = repository.settingsStore.getOrCreateLocalClientId()
        val cleanGm = gmDisplayName.trim().ifBlank { "Commissioner" }.take(22)
        repository.settingsStore.updateGmDisplayName(cleanGm)

        val roomCode = RoomCrypto.generateInviteCode()
        repository.settingsStore.updateMultiplayerSessionState(
            roomCode = roomCode,
            role = RoomRole.HOST.name,
            controlledTeamId = NhlFantasyRepository.USER_TEAM_ID
        )

        repository.ensureFranchiseInitialized()
        val hostTeam = repository.fantasyTeamsFlow.first()
            .firstOrNull { it.teamId == NhlFantasyRepository.USER_TEAM_ID }

        val now = System.currentTimeMillis()
        val hostParticipant = LobbyParticipant(
            clientId = clientId,
            gmName = cleanGm,
            assignedTeamId = NhlFantasyRepository.USER_TEAM_ID,
            teamName = hostTeam?.teamName ?: "$cleanGm's HC",
            abbreviation = hostTeam?.abbreviation ?: "NIC",
            emblemId = hostTeam?.emblemId ?: TeamEmblem.MAPLE_SHIELD.id,
            primaryColorHex = hostTeam?.primaryColorHex ?: 0xFF00C2FFL,
            secondaryColorHex = hostTeam?.secondaryColorHex ?: 0xFF0B192CL,
            isHost = true,
            isLocalPassAndPlay = false,
            isReady = true,
            joinedAtEpochMs = now,
            lastSeenEpochMs = now
        )

        val welcomeMsg = MultiplayerChatMessage(
            messageId = UUID.randomUUID().toString(),
            senderClientId = clientId,
            senderGmName = "League Office",
            senderTeamAbbrev = "NHL",
            text = "Lobby ${RoomCrypto.formatInviteCodeForDisplay(roomCode)} opened by $cleanGm. Share the code to invite friends!",
            isSystemEvent = true,
            timestampEpochMs = now
        )

        revisionCounter.set(1L)
        _uiState.update {
            it.copy(
                role = RoomRole.HOST,
                roomCode = roomCode,
                pendingDeepLinkCode = "",
                localClientId = clientId,
                localGmName = cleanGm,
                controlledTeamId = NhlFantasyRepository.USER_TEAM_ID,
                phase = MultiplayerRoomPhase.LOBBY,
                fillEmptySlotsWithCpu = true,
                draftConfig = initialConfig,
                participants = listOf(hostParticipant),
                chatMessages = listOf(welcomeMsg),
                unreadChatCount = 0,
                lastError = ""
            )
        }

        bindAndConnectTransport(roomCode, clientId) {
            broadcastAuthoritativeSnapshot()
        }

        return Result.success(roomCode)
    }

    suspend fun addLocalPassAndPlayFriend(
        friendGmName: String,
        teamName: String,
        abbreviation: String,
        emblem: TeamEmblem,
        primaryHex: Long,
        secondaryHex: Long
    ): Result<Unit> = hostActionMutex.withLock {
        val state = _uiState.value
        if (state.role != RoomRole.HOST || state.phase != MultiplayerRoomPhase.LOBBY) {
            return Result.failure(IllegalStateException("Can only add local friends in the Host Lobby"))
        }
        if (state.participants.size >= 16) {
            return Result.failure(IllegalStateException("Maximum 16 teams reached"))
        }

        val shortId = UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
        val cleanGm = friendGmName.trim().ifBlank { "Friend ${state.participants.size}" }.take(22)
        val cleanTeam = teamName.trim().ifBlank { "$cleanGm's HC" }.take(28)
        val cleanAbbrev = abbreviation.trim().uppercase().filter { it.isLetterOrDigit() }.take(3).ifBlank { "FRD" }
        val now = System.currentTimeMillis()

        val localParticipant = LobbyParticipant(
            clientId = "LOCAL_CLIENT_$shortId",
            gmName = cleanGm,
            assignedTeamId = "LOCAL_$shortId",
            teamName = cleanTeam,
            abbreviation = cleanAbbrev,
            emblemId = emblem.id,
            primaryColorHex = primaryHex,
            secondaryColorHex = secondaryHex,
            isHost = false,
            isLocalPassAndPlay = true,
            isReady = true,
            joinedAtEpochMs = now,
            lastSeenEpochMs = now
        )

        val updatedList = state.participants + localParticipant
        val bumpedTeamCount = maxOf(state.draftConfig.teamCount, updatedList.size)
        val sysMsg = buildSystemChat("$cleanGm ($cleanTeam) added as a Local Pass-and-Play GM.")

        _uiState.update {
            it.copy(
                participants = updatedList,
                draftConfig = it.draftConfig.copy(teamCount = bumpedTeamCount),
                chatMessages = (it.chatMessages + sysMsg).takeLast(60)
            )
        }
        broadcastAuthoritativeSnapshot()
        return Result.success(Unit)
    }

    suspend fun removeParticipantFromLobby(targetClientId: String) = hostActionMutex.withLock {
        val state = _uiState.value
        if (state.role != RoomRole.HOST || state.phase != MultiplayerRoomPhase.LOBBY) return@withLock
        val target = state.participants.firstOrNull { it.clientId == targetClientId && !it.isHost } ?: return@withLock

        val updated = state.participants.filterNot { it.clientId == targetClientId }
        val sysMsg = buildSystemChat("${target.gmName} (${target.teamName}) was removed from the lobby.")
        _uiState.update {
            it.copy(
                participants = updated,
                chatMessages = (it.chatMessages + sysMsg).takeLast(60)
            )
        }
        publishEnvelope(
            MultiplayerEnvelope(
                messageId = UUID.randomUUID().toString(),
                type = EnvelopeMessageType.KICK_PARTICIPANT,
                senderClientId = state.localClientId,
                timestampEpochMs = System.currentTimeMillis(),
                targetClientId = targetClientId
            ),
            retain = false
        )
        broadcastAuthoritativeSnapshot()
    }

    suspend fun updateHostDraftRules(
        newConfig: DraftConfig,
        fillEmptySlotsWithCpu: Boolean
    ) = hostActionMutex.withLock {
        val state = _uiState.value
        if (state.role != RoomRole.HOST || state.phase != MultiplayerRoomPhase.LOBBY) return@withLock
        val clampedCount = maxOf(newConfig.teamCount, state.participants.size).coerceIn(2, 16)
        _uiState.update {
            it.copy(
                draftConfig = newConfig.copy(teamCount = clampedCount),
                fillEmptySlotsWithCpu = fillEmptySlotsWithCpu
            )
        }
        broadcastAuthoritativeSnapshot()
    }

    suspend fun randomizeLobbyDraftOrder() = hostActionMutex.withLock {
        val state = _uiState.value
        if (state.role != RoomRole.HOST || state.phase != MultiplayerRoomPhase.LOBBY) return@withLock
        val shuffled = state.participants.shuffled()
        val sysMsg = buildSystemChat("Commissioner randomized the human GM draft slot order!")
        _uiState.update {
            it.copy(
                participants = shuffled,
                chatMessages = (it.chatMessages + sysMsg).takeLast(60)
            )
        }
        broadcastAuthoritativeSnapshot()
    }

    suspend fun moveParticipantSlot(clientId: String, moveUp: Boolean) = hostActionMutex.withLock {
        val state = _uiState.value
        if (state.role != RoomRole.HOST || state.phase != MultiplayerRoomPhase.LOBBY) return@withLock
        val idx = state.participants.indexOfFirst { it.clientId == clientId }
        if (idx < 0) return@withLock
        val swapIdx = if (moveUp) idx - 1 else idx + 1
        if (swapIdx !in state.participants.indices) return@withLock

        val mutable = state.participants.toMutableList()
        val tmp = mutable[idx]
        mutable[idx] = mutable[swapIdx]
        mutable[swapIdx] = tmp
        _uiState.update { it.copy(participants = mutable) }
        broadcastAuthoritativeSnapshot()
    }

    suspend fun launchMultiplayerDraft(): Result<String> = hostActionMutex.withLock {
        val state = _uiState.value
        if (state.role != RoomRole.HOST) {
            return Result.failure(IllegalStateException("Only the Host Commissioner can start the multiplayer draft"))
        }

        val res = repository.startMultiplayerDraft(
            config = state.draftConfig,
            orderedParticipants = state.participants,
            fillEmptyWithCpu = state.fillEmptySlotsWithCpu
        )
        if (res.isFailure) {
            return Result.failure(res.exceptionOrNull() ?: IllegalStateException("Could not start multiplayer draft"))
        }

        // Advance any initial CPU picks if Pick #1 belongs to a CPU slot
        repository.advanceCpuDraftPicks(maxSteps = 20)

        val session = repository.draftSessionFlow.first()
        val newPhase = if (session?.status == "COMPLETED") {
            MultiplayerRoomPhase.LEAGUE_ACTIVE
        } else {
            MultiplayerRoomPhase.DRAFT_IN_PROGRESS
        }
        val orderedTeams = res.getOrThrow()
        val humanCount = orderedTeams.count { !it.isCpuGm }
        val cpuCount = orderedTeams.count { it.isCpuGm }
        val launchChat = buildSystemChat(
            "🚀 Multiplayer Draft started! ${orderedTeams.size} teams ($humanCount Real GMs, $cpuCount CPU GMs) • ${state.draftConfig.totalRounds} rounds."
        )

        _uiState.update {
            it.copy(
                phase = newPhase,
                draftConfig = repository.decodeDraftConfig(session),
                chatMessages = (it.chatMessages + launchChat).takeLast(60)
            )
        }
        broadcastAuthoritativeSnapshot()
        return Result.success("Multiplayer Draft live with $humanCount human GM(s)!")
    }

    /**
     * Broadcasts the Host's current Room DB state as a Retained encrypted snapshot to all room participants.
     */
    suspend fun broadcastAuthoritativeSnapshot() {
        val state = _uiState.value
        if (state.role != RoomRole.HOST || state.roomCode.isBlank()) return

        val currentSession = repository.draftSessionFlow.first()
        val effectivePhase = when {
            state.phase == MultiplayerRoomPhase.LOBBY -> MultiplayerRoomPhase.LOBBY
            currentSession?.status == "COMPLETED" -> MultiplayerRoomPhase.LEAGUE_ACTIVE
            else -> MultiplayerRoomPhase.DRAFT_IN_PROGRESS
        }
        if (effectivePhase != state.phase) {
            _uiState.update { it.copy(phase = effectivePhase) }
        }

        val rev = revisionCounter.incrementAndGet()
        val snapshot = repository.exportMultiplayerRoomSnapshot(
            roomCode = state.roomCode,
            revision = rev,
            hostClientId = state.localClientId,
            hostGmName = state.localGmName,
            phase = effectivePhase,
            fillEmptySlotsWithCpu = state.fillEmptySlotsWithCpu,
            lobbyDraftConfig = state.draftConfig,
            participants = _uiState.value.participants,
            chatHistory = _uiState.value.chatMessages
        )

        publishEnvelope(
            MultiplayerEnvelope(
                messageId = UUID.randomUUID().toString(),
                type = EnvelopeMessageType.SNAPSHOT,
                senderClientId = state.localClientId,
                timestampEpochMs = System.currentTimeMillis(),
                snapshot = snapshot
            ),
            retain = true
        )
    }

    // =========================================================================
    // GUEST (INVITED FRIEND) LIFECYCLE & ACTIONS
    // =========================================================================

    suspend fun joinFriendRoom(
        rawCodeOrLink: String,
        gmDisplayName: String,
        teamName: String,
        abbreviation: String,
        emblem: TeamEmblem,
        primaryColorHex: Long,
        secondaryColorHex: Long
    ): Result<String> {
        val roomCode = RoomCrypto.extractInviteCodeFromLinkOrText(rawCodeOrLink)
            ?: return Result.failure(IllegalArgumentException("Enter a valid 6-character Room Invite Code"))

        val clientId = repository.settingsStore.getOrCreateLocalClientId()
        val cleanGm = gmDisplayName.trim().ifBlank { "GM Friend" }.take(22)
        val cleanTeam = teamName.trim().ifBlank { "$cleanGm's HC" }.take(28)
        val cleanAbbrev = abbreviation.trim().uppercase().filter { it.isLetterOrDigit() }.take(3).ifBlank { "FRD" }
        val assignedTeamId = buildRemoteTeamId(clientId)

        repository.settingsStore.updateGmDisplayName(cleanGm)
        repository.settingsStore.updateMultiplayerSessionState(
            roomCode = roomCode,
            role = RoomRole.GUEST.name,
            controlledTeamId = assignedTeamId
        )

        lastAppliedGuestRevision = -1L
        val selfPlaceholder = LobbyParticipant(
            clientId = clientId,
            gmName = cleanGm,
            assignedTeamId = assignedTeamId,
            teamName = cleanTeam,
            abbreviation = cleanAbbrev,
            emblemId = emblem.id,
            primaryColorHex = primaryColorHex,
            secondaryColorHex = secondaryColorHex,
            isHost = false,
            isLocalPassAndPlay = false,
            isReady = true,
            joinedAtEpochMs = System.currentTimeMillis(),
            lastSeenEpochMs = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(
                role = RoomRole.GUEST,
                roomCode = roomCode,
                pendingDeepLinkCode = "",
                localClientId = clientId,
                localGmName = cleanGm,
                controlledTeamId = assignedTeamId,
                phase = MultiplayerRoomPhase.LOBBY,
                participants = listOf(selfPlaceholder),
                chatMessages = emptyList(),
                unreadChatCount = 0,
                lastError = ""
            )
        }

        val joinPayload = JoinOrUpdateParticipantPayload(
            gmName = cleanGm,
            teamName = cleanTeam,
            abbreviation = cleanAbbrev,
            emblemId = emblem.id,
            primaryColorHex = primaryColorHex,
            secondaryColorHex = secondaryColorHex,
            isReady = true
        )

        bindAndConnectTransport(roomCode, clientId) {
            publishEnvelope(
                MultiplayerEnvelope(
                    messageId = UUID.randomUUID().toString(),
                    type = EnvelopeMessageType.JOIN_LOBBY,
                    senderClientId = clientId,
                    timestampEpochMs = System.currentTimeMillis(),
                    participantUpdate = joinPayload
                ),
                retain = false
            )
            publishEnvelope(
                MultiplayerEnvelope(
                    messageId = UUID.randomUUID().toString(),
                    type = EnvelopeMessageType.REQUEST_SNAPSHOT,
                    senderClientId = clientId,
                    timestampEpochMs = System.currentTimeMillis()
                ),
                retain = false
            )
        }

        return Result.success(roomCode)
    }

    suspend fun updateGuestProfileAndReady(
        gmName: String,
        teamName: String,
        abbreviation: String,
        emblem: TeamEmblem,
        primaryColorHex: Long,
        secondaryColorHex: Long,
        isReady: Boolean
    ) {
        val state = _uiState.value
        if (state.role != RoomRole.GUEST) return
        val cleanGm = gmName.trim().ifBlank { state.localGmName }.take(22)
        val cleanTeam = teamName.trim().ifBlank { "$cleanGm's HC" }.take(28)
        val cleanAbbrev = abbreviation.trim().uppercase().filter { it.isLetterOrDigit() }.take(3).ifBlank { "FRD" }
        repository.settingsStore.updateGmDisplayName(cleanGm)

        publishEnvelope(
            MultiplayerEnvelope(
                messageId = UUID.randomUUID().toString(),
                type = EnvelopeMessageType.UPDATE_PARTICIPANT,
                senderClientId = state.localClientId,
                timestampEpochMs = System.currentTimeMillis(),
                participantUpdate = JoinOrUpdateParticipantPayload(
                    gmName = cleanGm,
                    teamName = cleanTeam,
                    abbreviation = cleanAbbrev,
                    emblemId = emblem.id,
                    primaryColorHex = primaryColorHex,
                    secondaryColorHex = secondaryColorHex,
                    isReady = isReady
                )
            ),
            retain = false
        )
    }

    // =========================================================================
    // UNIFIED MULTIPLAYER GAMEPLAY ACTIONS (HOST & GUEST)
    // =========================================================================

    suspend fun submitMultiplayerDraftPick(playerId: Long): Result<String> {
        val state = _uiState.value
        return when (state.role) {
            RoomRole.NONE -> repository.makeHumanDraftPick(playerId)
            RoomRole.HOST -> hostActionMutex.withLock {
                val res = repository.makeHumanDraftPick(playerId)
                if (res.isSuccess) {
                    repository.advanceCpuDraftPicks(maxSteps = 24)
                    broadcastAuthoritativeSnapshot()
                }
                res
            }
            RoomRole.GUEST -> {
                val session = repository.draftSessionFlow.first()
                    ?: return Result.failure(IllegalStateException("Waiting for Host to start draft"))
                val sent = publishEnvelope(
                    MultiplayerEnvelope(
                        messageId = UUID.randomUUID().toString(),
                        type = EnvelopeMessageType.DRAFT_PICK_INTENT,
                        senderClientId = state.localClientId,
                        timestampEpochMs = System.currentTimeMillis(),
                        draftPickIntent = DraftPickIntentPayload(
                            teamId = state.controlledTeamId,
                            expectedOverallPick = session.currentOverallPick,
                            playerId = playerId
                        )
                    ),
                    retain = false
                )
                if (sent) Result.success("Submitting draft pick to Commissioner...")
                else Result.failure(IllegalStateException("Reconnecting to multiplayer room — try again in a moment"))
            }
        }
    }

    suspend fun submitMultiplayerAutoPick(): Result<String> {
        val state = _uiState.value
        return when (state.role) {
            RoomRole.NONE -> repository.autoPickForHuman()
            RoomRole.HOST -> hostActionMutex.withLock {
                val res = repository.autoPickForHuman()
                if (res.isSuccess) {
                    repository.advanceCpuDraftPicks(maxSteps = 24)
                    broadcastAuthoritativeSnapshot()
                }
                res
            }
            RoomRole.GUEST -> {
                val sent = publishEnvelope(
                    MultiplayerEnvelope(
                        messageId = UUID.randomUUID().toString(),
                        type = EnvelopeMessageType.AUTO_PICK_INTENT,
                        senderClientId = state.localClientId,
                        timestampEpochMs = System.currentTimeMillis()
                    ),
                    retain = false
                )
                if (sent) Result.success("Requesting auto-pick...")
                else Result.failure(IllegalStateException("Not connected to multiplayer relay"))
            }
        }
    }

    suspend fun syncLineupAfterLocalChange() {
        val state = _uiState.value
        when (state.role) {
            RoomRole.NONE -> Unit
            RoomRole.HOST -> hostActionMutex.withLock { broadcastAuthoritativeSnapshot() }
            RoomRole.GUEST -> {
                val roster = repository.observeTeamRoster(state.controlledTeamId).first()
                val entries = roster.map { LineupSlotSyncEntry(it.playerId, it.lineupSlotCode) }
                publishEnvelope(
                    MultiplayerEnvelope(
                        messageId = UUID.randomUUID().toString(),
                        type = EnvelopeMessageType.LINEUP_UPDATE_INTENT,
                        senderClientId = state.localClientId,
                        timestampEpochMs = System.currentTimeMillis(),
                        lineupUpdateIntent = LineupUpdateIntentPayload(
                            teamId = state.controlledTeamId,
                            slots = entries
                        )
                    ),
                    retain = false
                )
            }
        }
    }

    suspend fun submitMultiplayerMarketBuy(playerId: Long): Result<String> {
        val state = _uiState.value
        return when (state.role) {
            RoomRole.NONE -> repository.buyPlayerFromMarketplace(playerId)
            RoomRole.HOST -> hostActionMutex.withLock {
                val res = repository.buyPlayerFromMarketplace(playerId)
                if (res.isSuccess) broadcastAuthoritativeSnapshot()
                res
            }
            RoomRole.GUEST -> {
                val sent = publishEnvelope(
                    MultiplayerEnvelope(
                        messageId = UUID.randomUUID().toString(),
                        type = EnvelopeMessageType.MARKET_BUY_INTENT,
                        senderClientId = state.localClientId,
                        timestampEpochMs = System.currentTimeMillis(),
                        marketIntent = MarketActionIntentPayload(state.controlledTeamId, playerId)
                    ),
                    retain = false
                )
                if (sent) Result.success("Signing player in multiplayer league...")
                else Result.failure(IllegalStateException("Not connected to multiplayer relay"))
            }
        }
    }

    suspend fun submitMultiplayerMarketSell(playerId: Long): Result<String> {
        val state = _uiState.value
        return when (state.role) {
            RoomRole.NONE -> repository.sellPlayerToMarketplace(playerId)
            RoomRole.HOST -> hostActionMutex.withLock {
                val res = repository.sellPlayerToMarketplace(playerId)
                if (res.isSuccess) broadcastAuthoritativeSnapshot()
                res
            }
            RoomRole.GUEST -> {
                val sent = publishEnvelope(
                    MultiplayerEnvelope(
                        messageId = UUID.randomUUID().toString(),
                        type = EnvelopeMessageType.MARKET_SELL_INTENT,
                        senderClientId = state.localClientId,
                        timestampEpochMs = System.currentTimeMillis(),
                        marketIntent = MarketActionIntentPayload(state.controlledTeamId, playerId)
                    ),
                    retain = false
                )
                if (sent) Result.success("Selling player to marketplace...")
                else Result.failure(IllegalStateException("Not connected to multiplayer relay"))
            }
        }
    }

    suspend fun submitMultiplayerTradeProposal(
        targetTeamId: String,
        offeredPlayerIds: List<Long>,
        offeredCashCad: Long,
        requestedPlayerIds: List<Long>,
        requestedCashCad: Long
    ): Result<String> {
        val state = _uiState.value
        return when (state.role) {
            RoomRole.NONE -> repository.proposeTradeToTeam(
                targetTeamId, offeredPlayerIds, offeredCashCad, requestedPlayerIds, requestedCashCad
            ).map { it.gmReasoning }
            RoomRole.HOST -> hostActionMutex.withLock {
                val res = repository.proposeTradeToTeam(
                    targetTeamId, offeredPlayerIds, offeredCashCad, requestedPlayerIds, requestedCashCad
                )
                if (res.isSuccess) broadcastAuthoritativeSnapshot()
                res.map { it.gmReasoning }
            }
            RoomRole.GUEST -> {
                val sent = publishEnvelope(
                    MultiplayerEnvelope(
                        messageId = UUID.randomUUID().toString(),
                        type = EnvelopeMessageType.TRADE_PROPOSE_INTENT,
                        senderClientId = state.localClientId,
                        timestampEpochMs = System.currentTimeMillis(),
                        tradeProposeIntent = TradeProposeIntentPayload(
                            proposerTeamId = state.controlledTeamId,
                            receiverTeamId = targetTeamId,
                            offeredPlayerIds = offeredPlayerIds,
                            offeredCashCad = offeredCashCad,
                            requestedPlayerIds = requestedPlayerIds,
                            requestedCashCad = requestedCashCad
                        )
                    ),
                    retain = false
                )
                if (sent) Result.success("Sent trade proposal to league office...")
                else Result.failure(IllegalStateException("Not connected to multiplayer relay"))
            }
        }
    }

    suspend fun submitMultiplayerTradeResponse(tradeId: Long, accept: Boolean): Result<String> {
        val state = _uiState.value
        return when (state.role) {
            RoomRole.NONE -> repository.respondToIncomingProposal(tradeId, accept)
            RoomRole.HOST -> hostActionMutex.withLock {
                val res = repository.respondToIncomingProposal(tradeId, accept)
                if (res.isSuccess) broadcastAuthoritativeSnapshot()
                res
            }
            RoomRole.GUEST -> {
                val sent = publishEnvelope(
                    MultiplayerEnvelope(
                        messageId = UUID.randomUUID().toString(),
                        type = EnvelopeMessageType.TRADE_RESPOND_INTENT,
                        senderClientId = state.localClientId,
                        timestampEpochMs = System.currentTimeMillis(),
                        tradeRespondIntent = TradeRespondIntentPayload(
                            responderTeamId = state.controlledTeamId,
                            tradeId = tradeId,
                            accept = accept
                        )
                    ),
                    retain = false
                )
                if (sent) Result.success(if (accept) "Accepting trade offer..." else "Declining trade offer...")
                else Result.failure(IllegalStateException("Not connected to multiplayer relay"))
            }
        }
    }

    suspend fun sendChatMessage(rawText: String) {
        val clean = rawText.trim().take(220)
        if (clean.isBlank()) return
        val state = _uiState.value
        if (!state.isMultiplayerActive) return

        val myParticipant = state.participants.firstOrNull { it.clientId == state.localClientId }
        val msg = MultiplayerChatMessage(
            messageId = UUID.randomUUID().toString(),
            senderClientId = state.localClientId,
            senderGmName = state.localGmName,
            senderTeamAbbrev = myParticipant?.abbreviation ?: "GM",
            text = clean,
            isSystemEvent = false,
            timestampEpochMs = System.currentTimeMillis()
        )

        if (state.role == RoomRole.HOST) {
            hostActionMutex.withLock {
                _uiState.update { it.copy(chatMessages = (it.chatMessages + msg).takeLast(60)) }
                publishEnvelope(
                    MultiplayerEnvelope(
                        messageId = UUID.randomUUID().toString(),
                        type = EnvelopeMessageType.CHAT,
                        senderClientId = state.localClientId,
                        timestampEpochMs = msg.timestampEpochMs,
                        chatMessage = msg
                    ),
                    retain = false
                )
                broadcastAuthoritativeSnapshot()
            }
        } else {
            _uiState.update { it.copy(chatMessages = (it.chatMessages + msg).takeLast(60)) }
            publishEnvelope(
                MultiplayerEnvelope(
                    messageId = UUID.randomUUID().toString(),
                    type = EnvelopeMessageType.CHAT,
                    senderClientId = state.localClientId,
                    timestampEpochMs = msg.timestampEpochMs,
                    chatMessage = msg
                ),
                retain = false
            )
        }
    }

    suspend fun leaveMultiplayerRoom() {
        val state = _uiState.value
        if (state.role == RoomRole.GUEST && state.roomCode.isNotBlank()) {
            publishEnvelope(
                MultiplayerEnvelope(
                    messageId = UUID.randomUUID().toString(),
                    type = EnvelopeMessageType.LEAVE_LOBBY,
                    senderClientId = state.localClientId,
                    timestampEpochMs = System.currentTimeMillis()
                ),
                retain = false
            )
        }
        transportJobs?.cancel()
        activeTransport?.disconnect()
        activeTransport = null
        lastAppliedGuestRevision = -1L

        repository.leaveMultiplayerAndRestoreSoloIfNeeded()
        _uiState.update {
            it.copy(
                role = RoomRole.NONE,
                roomCode = "",
                connectionState = MultiplayerConnectionState.DISCONNECTED,
                controlledTeamId = NhlFantasyRepository.USER_TEAM_ID,
                phase = MultiplayerRoomPhase.LOBBY,
                participants = emptyList(),
                chatMessages = emptyList(),
                unreadChatCount = 0,
                lastError = ""
            )
        }
        _events.tryEmit("Left multiplayer room. Solo franchise active.")
    }

    suspend fun saveCustomRelayServerUrl(url: String) {
        repository.settingsStore.updateCustomMultiplayerRelayUrl(url.trim())
        _events.tryEmit(
            if (url.isBlank()) "Using default encrypted internet multiplayer relay."
            else "Saved custom multiplayer WebSocket relay URL."
        )
    }

    // =========================================================================
    // INTERNAL TRANSPORT WIRING & ENVELOPE DISPATCH
    // =========================================================================

    private fun bindAndConnectTransport(
        roomCode: String,
        clientId: String,
        onConnectedCallback: suspend () -> Unit
    ) {
        transportJobs?.cancel()
        activeTransport?.disconnect()

        val customUrl = _uiState.value.customRelayUrl
        val transport = transportFactory.create(customUrl)
        activeTransport = transport
        val topic = RoomCrypto.deriveRoomTopic(roomCode)

        transportJobs = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                transport.connectionState.collect { connState ->
                    _uiState.update { it.copy(connectionState = connState) }
                    if (connState == MultiplayerConnectionState.CONNECTED) {
                        onConnectedCallback()
                    }
                }
            }
            launch(start = CoroutineStart.UNDISPATCHED) {
                transport.lastError.collect { err ->
                    if (err.isNotBlank()) {
                        _uiState.update { it.copy(lastError = err) }
                    }
                }
            }
            launch(start = CoroutineStart.UNDISPATCHED) {
                transport.incomingEncryptedPayloads.collect { encrypted ->
                    val plain = runCatching {
                        RoomCrypto.decryptRoomPayload(roomCode, encrypted)
                    }.getOrNull() ?: return@collect

                    val envelope = runCatching {
                        json.decodeFromString<MultiplayerEnvelope>(plain)
                    }.getOrNull() ?: return@collect

                    if (!processedMessageIds.add(envelope.messageId)) {
                        return@collect // Already processed
                    }
                    if (envelope.senderClientId == _uiState.value.localClientId) {
                        return@collect // Ignore own echoed packet
                    }

                    when (_uiState.value.role) {
                        RoomRole.HOST -> handleIncomingAsHost(envelope)
                        RoomRole.GUEST -> handleIncomingAsGuest(envelope)
                        RoomRole.NONE -> Unit
                    }
                }
            }

            transport.connect(roomTopic = topic, clientTag = clientId)
        }
    }

    private suspend fun handleIncomingAsHost(envelope: MultiplayerEnvelope) = hostActionMutex.withLock {
        val state = _uiState.value
        if (state.role != RoomRole.HOST) return@withLock
        val now = System.currentTimeMillis()

        when (envelope.type) {
            EnvelopeMessageType.REQUEST_SNAPSHOT -> {
                broadcastAuthoritativeSnapshot()
            }

            EnvelopeMessageType.JOIN_LOBBY,
            EnvelopeMessageType.UPDATE_PARTICIPANT -> {
                val pInfo = envelope.participantUpdate ?: return@withLock
                val existingIdx = state.participants.indexOfFirst { it.clientId == envelope.senderClientId }
                val palette = TeamColorPreset.presets[state.participants.size % TeamColorPreset.presets.size]
                val assignedId = if (existingIdx >= 0) {
                    state.participants[existingIdx].assignedTeamId
                } else {
                    buildRemoteTeamId(envelope.senderClientId)
                }

                val updatedParticipant = LobbyParticipant(
                    clientId = envelope.senderClientId,
                    gmName = pInfo.gmName.trim().ifBlank { "Friend GM" }.take(22),
                    assignedTeamId = assignedId,
                    teamName = pInfo.teamName.trim().ifBlank { "${pInfo.gmName}'s HC" }.take(28),
                    abbreviation = pInfo.abbreviation.trim().uppercase().take(3).ifBlank { "FRD" },
                    emblemId = pInfo.emblemId,
                    primaryColorHex = if (pInfo.primaryColorHex != 0L) pInfo.primaryColorHex else palette.primaryHex,
                    secondaryColorHex = if (pInfo.secondaryColorHex != 0L) pInfo.secondaryColorHex else palette.secondaryHex,
                    isHost = false,
                    isLocalPassAndPlay = false,
                    isReady = pInfo.isReady,
                    joinedAtEpochMs = if (existingIdx >= 0) state.participants[existingIdx].joinedAtEpochMs else now,
                    lastSeenEpochMs = now
                )

                if (state.phase != MultiplayerRoomPhase.LOBBY) {
                    // If friend is reconnecting mid-draft or mid-season, update lastSeen & re-sync snapshot
                    if (existingIdx >= 0) {
                        val list = state.participants.toMutableList().apply { set(existingIdx, updatedParticipant) }
                        _uiState.update { it.copy(participants = list) }
                        repository.customizeUserFranchise(
                            teamName = updatedParticipant.teamName,
                            abbreviation = updatedParticipant.abbreviation,
                            emblem = TeamEmblem.fromId(updatedParticipant.emblemId),
                            primaryColorHex = updatedParticipant.primaryColorHex,
                            secondaryColorHex = updatedParticipant.secondaryColorHex,
                            actingTeamId = assignedId
                        )
                    } else {
                        sendRejectionToClient(
                            envelope.senderClientId,
                            "This multiplayer draft has already started (${state.participants.size} GMs locked)."
                        )
                    }
                    broadcastAuthoritativeSnapshot()
                    return@withLock
                }

                if (existingIdx < 0 && state.participants.size >= 16) {
                    sendRejectionToClient(envelope.senderClientId, "Lobby is full (16/16 teams).")
                    return@withLock
                }

                val updatedParticipants = if (existingIdx >= 0) {
                    state.participants.toMutableList().apply { set(existingIdx, updatedParticipant) }
                } else {
                    state.participants + updatedParticipant
                }

                val newChat = if (existingIdx < 0) {
                    val joinNotice = buildSystemChat(
                        "👋 ${updatedParticipant.gmName} joined with ${updatedParticipant.teamName} (${updatedParticipant.abbreviation})!"
                    )
                    _events.tryEmit("${updatedParticipant.gmName} joined your Draft Lobby!")
                    (state.chatMessages + joinNotice).takeLast(60)
                } else {
                    state.chatMessages
                }

                val adjustedTeamCount = maxOf(state.draftConfig.teamCount, updatedParticipants.size).coerceIn(2, 16)
                _uiState.update {
                    it.copy(
                        participants = updatedParticipants,
                        draftConfig = it.draftConfig.copy(teamCount = adjustedTeamCount),
                        chatMessages = newChat
                    )
                }
                broadcastAuthoritativeSnapshot()
            }

            EnvelopeMessageType.LEAVE_LOBBY -> {
                val leaving = state.participants.firstOrNull { it.clientId == envelope.senderClientId } ?: return@withLock
                if (state.phase == MultiplayerRoomPhase.LOBBY) {
                    val leaveChat = buildSystemChat("${leaving.gmName} left the lobby.")
                    _uiState.update {
                        it.copy(
                            participants = it.participants.filterNot { p -> p.clientId == envelope.senderClientId },
                            chatMessages = (it.chatMessages + leaveChat).takeLast(60)
                        )
                    }
                    broadcastAuthoritativeSnapshot()
                }
            }

            EnvelopeMessageType.CHAT -> {
                val msg = envelope.chatMessage ?: return@withLock
                _uiState.update {
                    it.copy(
                        chatMessages = (it.chatMessages + msg).takeLast(60),
                        unreadChatCount = it.unreadChatCount + 1
                    )
                }
                broadcastAuthoritativeSnapshot()
            }

            EnvelopeMessageType.DRAFT_PICK_INTENT -> {
                val intent = envelope.draftPickIntent ?: return@withLock
                val participant = state.participants.firstOrNull { it.clientId == envelope.senderClientId }
                if (participant == null || participant.assignedTeamId != intent.teamId) {
                    sendRejectionToClient(envelope.senderClientId, "Team authorization mismatch.")
                    return@withLock
                }

                val res = repository.makeHumanDraftPick(
                    playerId = intent.playerId,
                    actingTeamId = participant.assignedTeamId
                )
                res.onSuccess { summary ->
                    _events.tryEmit(summary)
                    repository.advanceCpuDraftPicks(maxSteps = 24)
                    broadcastAuthoritativeSnapshot()
                }.onFailure { err ->
                    sendRejectionToClient(envelope.senderClientId, err.message ?: "Invalid draft pick")
                }
            }

            EnvelopeMessageType.AUTO_PICK_INTENT -> {
                val participant = state.participants.firstOrNull { it.clientId == envelope.senderClientId } ?: return@withLock
                val res = repository.autoPickForHuman(actingTeamId = participant.assignedTeamId)
                res.onSuccess { summary ->
                    _events.tryEmit(summary)
                    repository.advanceCpuDraftPicks(maxSteps = 24)
                    broadcastAuthoritativeSnapshot()
                }.onFailure { err ->
                    sendRejectionToClient(envelope.senderClientId, err.message ?: "Auto-pick failed")
                }
            }

            EnvelopeMessageType.LINEUP_UPDATE_INTENT -> {
                val intent = envelope.lineupUpdateIntent ?: return@withLock
                val participant = state.participants.firstOrNull { it.clientId == envelope.senderClientId } ?: return@withLock
                if (participant.assignedTeamId == intent.teamId) {
                    repository.applyRemoteTeamLineupSlots(intent.teamId, intent.slots)
                    broadcastAuthoritativeSnapshot()
                }
            }

            EnvelopeMessageType.MARKET_BUY_INTENT -> {
                val intent = envelope.marketIntent ?: return@withLock
                val participant = state.participants.firstOrNull { it.clientId == envelope.senderClientId } ?: return@withLock
                if (participant.assignedTeamId != intent.teamId) return@withLock
                repository.buyPlayerFromMarketplace(intent.playerId, actingTeamId = intent.teamId)
                    .onSuccess {
                        _events.tryEmit("${participant.teamName}: $it")
                        broadcastAuthoritativeSnapshot()
                    }
                    .onFailure { sendRejectionToClient(envelope.senderClientId, it.message ?: "Purchase invalid") }
            }

            EnvelopeMessageType.MARKET_SELL_INTENT -> {
                val intent = envelope.marketIntent ?: return@withLock
                val participant = state.participants.firstOrNull { it.clientId == envelope.senderClientId } ?: return@withLock
                if (participant.assignedTeamId != intent.teamId) return@withLock
                repository.sellPlayerToMarketplace(intent.playerId, actingTeamId = intent.teamId)
                    .onSuccess {
                        broadcastAuthoritativeSnapshot()
                    }
                    .onFailure { sendRejectionToClient(envelope.senderClientId, it.message ?: "Sale invalid") }
            }

            EnvelopeMessageType.TRADE_PROPOSE_INTENT -> {
                val intent = envelope.tradeProposeIntent ?: return@withLock
                val participant = state.participants.firstOrNull { it.clientId == envelope.senderClientId } ?: return@withLock
                if (participant.assignedTeamId != intent.proposerTeamId) return@withLock
                repository.proposeTradeToTeam(
                    targetTeamId = intent.receiverTeamId,
                    offeredPlayerIds = intent.offeredPlayerIds,
                    offeredCashCad = intent.offeredCashCad,
                    requestedPlayerIds = intent.requestedPlayerIds,
                    requestedCashCad = intent.requestedCashCad,
                    proposerTeamIdOverride = intent.proposerTeamId
                ).onSuccess {
                    if (intent.receiverTeamId == NhlFantasyRepository.USER_TEAM_ID) {
                        _events.tryEmit("Incoming trade proposal from ${participant.teamName}!")
                    }
                    broadcastAuthoritativeSnapshot()
                }.onFailure { err ->
                    sendRejectionToClient(envelope.senderClientId, err.message ?: "Trade proposal invalid")
                }
            }

            EnvelopeMessageType.TRADE_RESPOND_INTENT -> {
                val intent = envelope.tradeRespondIntent ?: return@withLock
                val participant = state.participants.firstOrNull { it.clientId == envelope.senderClientId } ?: return@withLock
                if (participant.assignedTeamId != intent.responderTeamId) return@withLock
                repository.respondToIncomingProposal(
                    tradeId = intent.tradeId,
                    accept = intent.accept,
                    responderTeamIdOverride = intent.responderTeamId
                ).onSuccess { msg ->
                    _events.tryEmit(msg)
                    broadcastAuthoritativeSnapshot()
                }.onFailure { err ->
                    sendRejectionToClient(envelope.senderClientId, err.message ?: "Trade response failed")
                }
            }

            else -> Unit
        }
    }

    private suspend fun handleIncomingAsGuest(envelope: MultiplayerEnvelope) {
        val state = _uiState.value
        if (state.role != RoomRole.GUEST) return

        when (envelope.type) {
            EnvelopeMessageType.SNAPSHOT -> {
                val snap = envelope.snapshot ?: return
                if (snap.revision <= lastAppliedGuestRevision) return
                lastAppliedGuestRevision = snap.revision

                val meInSnap = snap.participants.firstOrNull { it.clientId == state.localClientId }
                val myAssignedTeamId = meInSnap?.assignedTeamId ?: buildRemoteTeamId(state.localClientId)

                // If Host's snapshot doesn't include us yet (because it was an older retained snapshot before our JOIN_LOBBY arrived), re-send JOIN_LOBBY!
                if (meInSnap == null && snap.phase == MultiplayerRoomPhase.LOBBY) {
                    val localMe = state.participants.firstOrNull { it.clientId == state.localClientId }
                    if (localMe != null) {
                        publishEnvelope(
                            MultiplayerEnvelope(
                                messageId = UUID.randomUUID().toString(),
                                type = EnvelopeMessageType.JOIN_LOBBY,
                                senderClientId = state.localClientId,
                                timestampEpochMs = System.currentTimeMillis(),
                                participantUpdate = JoinOrUpdateParticipantPayload(
                                    gmName = localMe.gmName,
                                    teamName = localMe.teamName,
                                    abbreviation = localMe.abbreviation,
                                    emblemId = localMe.emblemId,
                                    primaryColorHex = localMe.primaryColorHex,
                                    secondaryColorHex = localMe.secondaryColorHex,
                                    isReady = localMe.isReady
                                )
                            ),
                            retain = false
                        )
                    }
                }

                val previousPhase = state.phase
                repository.applyMultiplayerRoomSnapshot(
                    snapshot = snap,
                    localControlledTeamId = myAssignedTeamId
                )

                val prevChatSize = state.chatMessages.size
                val addedChat = (snap.chatHistory.size - prevChatSize).coerceAtLeast(0)

                _uiState.update {
                    it.copy(
                        controlledTeamId = myAssignedTeamId,
                        phase = snap.phase,
                        fillEmptySlotsWithCpu = snap.fillEmptySlotsWithCpu,
                        draftConfig = snap.draftConfig,
                        participants = snap.participants,
                        chatMessages = snap.chatHistory,
                        unreadChatCount = it.unreadChatCount + addedChat
                    )
                }

                if (previousPhase == MultiplayerRoomPhase.LOBBY && snap.phase == MultiplayerRoomPhase.DRAFT_IN_PROGRESS) {
                    _events.tryEmit("🏒 Commissioner ${snap.hostGmName} launched the Multiplayer Draft!")
                }

                // Notify Guest if it just became their turn on the clock
                val ds = snap.draftSession
                if (ds != null && ds.status == "IN_PROGRESS") {
                    val order = ds.teamOrderCsv.split(",").filter { it.isNotBlank() }
                    if (order.isNotEmpty()) {
                        val (_, _, onClockId) = DraftEngine.getPickMetadata(
                            ds.currentOverallPick,
                            order,
                            snap.draftConfig.isSnakeDraft
                        )
                        if (onClockId == myAssignedTeamId) {
                            _events.tryEmit("⚡ YOU ARE ON THE CLOCK! Make Pick #${ds.currentOverallPick}.")
                        }
                    }
                }
            }

            EnvelopeMessageType.CHAT -> {
                val msg = envelope.chatMessage ?: return
                if (state.chatMessages.none { it.messageId == msg.messageId }) {
                    _uiState.update {
                        it.copy(
                            chatMessages = (it.chatMessages + msg).takeLast(60),
                            unreadChatCount = it.unreadChatCount + 1
                        )
                    }
                }
            }

            EnvelopeMessageType.ACTION_REJECTED -> {
                val rej = envelope.actionRejected ?: return
                if (rej.targetClientId == state.localClientId) {
                    _events.tryEmit(rej.reason)
                }
            }

            EnvelopeMessageType.KICK_PARTICIPANT -> {
                if (envelope.targetClientId == state.localClientId) {
                    leaveMultiplayerRoom()
                    _events.tryEmit("You were removed from the lobby by the Commissioner.")
                }
            }

            else -> Unit
        }
    }

    private fun sendRejectionToClient(targetClientId: String, reason: String) {
        publishEnvelope(
            MultiplayerEnvelope(
                messageId = UUID.randomUUID().toString(),
                type = EnvelopeMessageType.ACTION_REJECTED,
                senderClientId = _uiState.value.localClientId,
                timestampEpochMs = System.currentTimeMillis(),
                actionRejected = ActionRejectedPayload(targetClientId = targetClientId, reason = reason)
            ),
            retain = false
        )
    }

    private fun publishEnvelope(envelope: MultiplayerEnvelope, retain: Boolean): Boolean {
        val roomCode = _uiState.value.roomCode
        val transport = activeTransport ?: return false
        if (roomCode.isBlank()) return false
        processedMessageIds.add(envelope.messageId)

        return runCatching {
            val plain = json.encodeToString(envelope)
            val encrypted = RoomCrypto.encryptRoomPayload(roomCode, plain)
            transport.publish(encrypted, retain = retain)
        }.getOrDefault(false)
    }

    private fun buildSystemChat(text: String): MultiplayerChatMessage = MultiplayerChatMessage(
        messageId = UUID.randomUUID().toString(),
        senderClientId = _uiState.value.localClientId,
        senderGmName = "League Office",
        senderTeamAbbrev = "NHL",
        text = text,
        isSystemEvent = true,
        timestampEpochMs = System.currentTimeMillis()
    )

    companion object {
        fun buildRemoteTeamId(clientId: String): String =
            "MP_${clientId.filter { it.isLetterOrDigit() }.take(8).uppercase()}"
    }
}
