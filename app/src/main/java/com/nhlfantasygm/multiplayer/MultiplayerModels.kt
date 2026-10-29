package com.nhlfantasygm.multiplayer

import com.nhlfantasygm.data.local.*
import com.nhlfantasygm.model.DraftConfig
import com.nhlfantasygm.model.EconomyConfig
import com.nhlfantasygm.model.ScoringWeights
import kotlinx.serialization.Serializable

enum class RoomRole {
    NONE,
    HOST,
    GUEST;

    companion object {
        fun fromString(raw: String?): RoomRole =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class MultiplayerConnectionState(val badgeLabel: String) {
    DISCONNECTED("Offline"),
    CONNECTING("Connecting..."),
    CONNECTED("Live Connected"),
    RECONNECTING("Reconnecting..."),
    ERROR("Connection Error")
}

@Serializable
enum class MultiplayerRoomPhase {
    LOBBY,
    DRAFT_IN_PROGRESS,
    LEAGUE_ACTIVE
}

@Serializable
data class LobbyParticipant(
    val clientId: String,
    val gmName: String,
    val assignedTeamId: String,
    val teamName: String,
    val abbreviation: String,
    val emblemId: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val isHost: Boolean = false,
    val isLocalPassAndPlay: Boolean = false,
    val isReady: Boolean = false,
    val joinedAtEpochMs: Long = 0L,
    val lastSeenEpochMs: Long = 0L
)

@Serializable
data class MultiplayerChatMessage(
    val messageId: String,
    val senderClientId: String,
    val senderGmName: String,
    val senderTeamAbbrev: String,
    val text: String,
    val isSystemEvent: Boolean = false,
    val timestampEpochMs: Long
)

@Serializable
data class SyncedFantasyTeam(
    val teamId: String,
    val teamName: String,
    val abbreviation: String,
    val emblemId: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val isHuman: Boolean,
    val ownerType: String,
    val gmDisplayName: String = "",
    val remoteClientId: String = "",
    val cashBalanceCad: Long,
    val startingBudgetCad: Long,
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
    val pointsFor: Double = 0.0,
    val pointsAgainst: Double = 0.0,
    val championshipsWon: Int = 0
) {
    fun toEntity(): FantasyTeamEntity = FantasyTeamEntity(
        teamId = teamId,
        teamName = teamName,
        abbreviation = abbreviation,
        emblemId = emblemId,
        primaryColorHex = primaryColorHex,
        secondaryColorHex = secondaryColorHex,
        isHuman = isHuman,
        ownerType = ownerType,
        gmDisplayName = gmDisplayName,
        remoteClientId = remoteClientId,
        cashBalanceCad = cashBalanceCad,
        startingBudgetCad = startingBudgetCad,
        wins = wins,
        losses = losses,
        ties = ties,
        pointsFor = pointsFor,
        pointsAgainst = pointsAgainst,
        championshipsWon = championshipsWon
    )

    companion object {
        fun fromEntity(e: FantasyTeamEntity): SyncedFantasyTeam = SyncedFantasyTeam(
            teamId = e.teamId,
            teamName = e.teamName,
            abbreviation = e.abbreviation,
            emblemId = e.emblemId,
            primaryColorHex = e.primaryColorHex,
            secondaryColorHex = e.secondaryColorHex,
            isHuman = e.isHuman,
            ownerType = e.ownerType,
            gmDisplayName = e.gmDisplayName,
            remoteClientId = e.remoteClientId,
            cashBalanceCad = e.cashBalanceCad,
            startingBudgetCad = e.startingBudgetCad,
            wins = e.wins,
            losses = e.losses,
            ties = e.ties,
            pointsFor = e.pointsFor,
            pointsAgainst = e.pointsAgainst,
            championshipsWon = e.championshipsWon
        )
    }
}

@Serializable
data class SyncedDraftSession(
    val status: String,
    val configJson: String,
    val teamOrderCsv: String,
    val currentOverallPick: Int,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long = 0L
) {
    fun toEntity(): DraftSessionEntity = DraftSessionEntity(
        sessionId = 1,
        status = status,
        configJson = configJson,
        teamOrderCsv = teamOrderCsv,
        currentOverallPick = currentOverallPick,
        startedAtEpochMs = startedAtEpochMs,
        completedAtEpochMs = completedAtEpochMs
    )

    companion object {
        fun fromEntity(e: DraftSessionEntity): SyncedDraftSession = SyncedDraftSession(
            status = e.status,
            configJson = e.configJson,
            teamOrderCsv = e.teamOrderCsv,
            currentOverallPick = e.currentOverallPick,
            startedAtEpochMs = e.startedAtEpochMs,
            completedAtEpochMs = e.completedAtEpochMs
        )
    }
}

@Serializable
data class SyncedDraftPick(
    val overallPick: Int,
    val roundNumber: Int,
    val pickInRound: Int,
    val fantasyTeamId: String,
    val fantasyTeamName: String,
    val isHumanPick: Boolean,
    val playerId: Long,
    val playerName: String,
    val positionCode: String,
    val nhlTeamAbbrev: String,
    val overallRating: Int,
    val capHitCad: Long,
    val pickCostCad: Long,
    val rationale: String,
    val pickedAtEpochMs: Long
) {
    fun toEntity(): DraftPickEntity = DraftPickEntity(
        overallPick = overallPick,
        roundNumber = roundNumber,
        pickInRound = pickInRound,
        fantasyTeamId = fantasyTeamId,
        fantasyTeamName = fantasyTeamName,
        isHumanPick = isHumanPick,
        playerId = playerId,
        playerName = playerName,
        positionCode = positionCode,
        nhlTeamAbbrev = nhlTeamAbbrev,
        overallRating = overallRating,
        capHitCad = capHitCad,
        pickCostCad = pickCostCad,
        rationale = rationale,
        pickedAtEpochMs = pickedAtEpochMs
    )

    companion object {
        fun fromEntity(e: DraftPickEntity): SyncedDraftPick = SyncedDraftPick(
            overallPick = e.overallPick,
            roundNumber = e.roundNumber,
            pickInRound = e.pickInRound,
            fantasyTeamId = e.fantasyTeamId,
            fantasyTeamName = e.fantasyTeamName,
            isHumanPick = e.isHumanPick,
            playerId = e.playerId,
            playerName = e.playerName,
            positionCode = e.positionCode,
            nhlTeamAbbrev = e.nhlTeamAbbrev,
            overallRating = e.overallRating,
            capHitCad = e.capHitCad,
            pickCostCad = e.pickCostCad,
            rationale = e.rationale,
            pickedAtEpochMs = e.pickedAtEpochMs
        )
    }
}

@Serializable
data class SyncedPlayerOwnership(
    val playerId: Long,
    val ownerTeamId: String,
    val acquisitionPriceCad: Long,
    val lineupSlotCode: String,
    val fullName: String = "",
    val positionCode: String = "C",
    val nhlTeamAbbrev: String = "NHL",
    val overallRating: Int = 75,
    val purchasePriceCad: Long = 1_500_000L,
    val sellPriceCad: Long = 1_440_000L,
    val capHitCad: Long = 1_200_000L
)

@Serializable
data class SyncedTradeProposal(
    val tradeId: Long,
    val proposerTeamId: String,
    val proposerTeamName: String,
    val receiverTeamId: String,
    val receiverTeamName: String,
    val offeredPlayerIdsCsv: String,
    val offeredPlayersSummary: String,
    val offeredCashCad: Long,
    val requestedPlayerIdsCsv: String,
    val requestedPlayersSummary: String,
    val requestedCashCad: Long,
    val status: String,
    val fairnessScore: Int,
    val gmEvaluationReason: String,
    val createdAtEpochMs: Long,
    val resolvedAtEpochMs: Long = 0L
) {
    fun toEntity(): TradeProposalEntity = TradeProposalEntity(
        tradeId = tradeId,
        proposerTeamId = proposerTeamId,
        proposerTeamName = proposerTeamName,
        receiverTeamId = receiverTeamId,
        receiverTeamName = receiverTeamName,
        offeredPlayerIdsCsv = offeredPlayerIdsCsv,
        offeredPlayersSummary = offeredPlayersSummary,
        offeredCashCad = offeredCashCad,
        requestedPlayerIdsCsv = requestedPlayerIdsCsv,
        requestedPlayersSummary = requestedPlayersSummary,
        requestedCashCad = requestedCashCad,
        status = status,
        fairnessScore = fairnessScore,
        gmEvaluationReason = gmEvaluationReason,
        createdAtEpochMs = createdAtEpochMs,
        resolvedAtEpochMs = resolvedAtEpochMs
    )

    companion object {
        fun fromEntity(e: TradeProposalEntity): SyncedTradeProposal = SyncedTradeProposal(
            tradeId = e.tradeId,
            proposerTeamId = e.proposerTeamId,
            proposerTeamName = e.proposerTeamName,
            receiverTeamId = e.receiverTeamId,
            receiverTeamName = e.receiverTeamName,
            offeredPlayerIdsCsv = e.offeredPlayerIdsCsv,
            offeredPlayersSummary = e.offeredPlayersSummary,
            offeredCashCad = e.offeredCashCad,
            requestedPlayerIdsCsv = e.requestedPlayerIdsCsv,
            requestedPlayersSummary = e.requestedPlayersSummary,
            requestedCashCad = e.requestedCashCad,
            status = e.status,
            fairnessScore = e.fairnessScore,
            gmEvaluationReason = e.gmEvaluationReason,
            createdAtEpochMs = e.createdAtEpochMs,
            resolvedAtEpochMs = e.resolvedAtEpochMs
        )
    }
}

@Serializable
data class SyncedMatchup(
    val matchupId: Long,
    val seasonNumber: Int,
    val weekNumber: Int,
    val stage: String,
    val homeTeamId: String,
    val homeTeamName: String,
    val awayTeamId: String,
    val awayTeamName: String,
    val homeScore: Double = 0.0,
    val awayScore: Double = 0.0,
    val isCompleted: Boolean = false,
    val scoringModeLabel: String = "PENDING",
    val highlightSummary: String = "",
    val completedAtEpochMs: Long = 0L
) {
    fun toEntity(): FantasyMatchupEntity = FantasyMatchupEntity(
        matchupId = matchupId,
        seasonNumber = seasonNumber,
        weekNumber = weekNumber,
        stage = stage,
        homeTeamId = homeTeamId,
        homeTeamName = homeTeamName,
        awayTeamId = awayTeamId,
        awayTeamName = awayTeamName,
        homeScore = homeScore,
        awayScore = awayScore,
        isCompleted = isCompleted,
        scoringModeLabel = scoringModeLabel,
        highlightSummary = highlightSummary,
        completedAtEpochMs = completedAtEpochMs
    )

    companion object {
        fun fromEntity(e: FantasyMatchupEntity): SyncedMatchup = SyncedMatchup(
            matchupId = e.matchupId,
            seasonNumber = e.seasonNumber,
            weekNumber = e.weekNumber,
            stage = e.stage,
            homeTeamId = e.homeTeamId,
            homeTeamName = e.homeTeamName,
            awayTeamId = e.awayTeamId,
            awayTeamName = e.awayTeamName,
            homeScore = e.homeScore,
            awayScore = e.awayScore,
            isCompleted = e.isCompleted,
            scoringModeLabel = e.scoringModeLabel,
            highlightSummary = e.highlightSummary,
            completedAtEpochMs = e.completedAtEpochMs
        )
    }
}

@Serializable
data class MultiplayerRoomSnapshot(
    val roomCode: String,
    val revision: Long,
    val hostClientId: String,
    val hostGmName: String,
    val phase: MultiplayerRoomPhase,
    val fillEmptySlotsWithCpu: Boolean = true,
    val draftConfig: DraftConfig = DraftConfig(),
    val economyConfig: EconomyConfig = EconomyConfig(),
    val scoringWeights: ScoringWeights = ScoringWeights(),
    val currentSeasonNumber: Int = 1,
    val participants: List<LobbyParticipant> = emptyList(),
    val orderedSlotTeamIds: List<String> = emptyList(),
    val teams: List<SyncedFantasyTeam> = emptyList(),
    val draftSession: SyncedDraftSession? = null,
    val draftPicks: List<SyncedDraftPick> = emptyList(),
    val ownedPlayers: List<SyncedPlayerOwnership> = emptyList(),
    val tradeProposals: List<SyncedTradeProposal> = emptyList(),
    val matchups: List<SyncedMatchup> = emptyList(),
    val chatHistory: List<MultiplayerChatMessage> = emptyList(),
    val updatedAtEpochMs: Long = 0L
)

@Serializable
enum class EnvelopeMessageType {
    SNAPSHOT,
    REQUEST_SNAPSHOT,
    JOIN_LOBBY,
    UPDATE_PARTICIPANT,
    LEAVE_LOBBY,
    KICK_PARTICIPANT,
    CHAT,
    DRAFT_PICK_INTENT,
    AUTO_PICK_INTENT,
    LINEUP_UPDATE_INTENT,
    MARKET_BUY_INTENT,
    MARKET_SELL_INTENT,
    TRADE_PROPOSE_INTENT,
    TRADE_RESPOND_INTENT,
    ACTION_REJECTED,
    HEARTBEAT
}

@Serializable
data class JoinOrUpdateParticipantPayload(
    val gmName: String,
    val teamName: String,
    val abbreviation: String,
    val emblemId: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val isReady: Boolean
)

@Serializable
data class DraftPickIntentPayload(
    val teamId: String,
    val expectedOverallPick: Int,
    val playerId: Long
)

@Serializable
data class LineupSlotSyncEntry(
    val playerId: Long,
    val lineupSlotCode: String
)

@Serializable
data class LineupUpdateIntentPayload(
    val teamId: String,
    val slots: List<LineupSlotSyncEntry>
)

@Serializable
data class MarketActionIntentPayload(
    val teamId: String,
    val playerId: Long
)

@Serializable
data class TradeProposeIntentPayload(
    val proposerTeamId: String,
    val receiverTeamId: String,
    val offeredPlayerIds: List<Long>,
    val offeredCashCad: Long,
    val requestedPlayerIds: List<Long>,
    val requestedCashCad: Long
)

@Serializable
data class TradeRespondIntentPayload(
    val responderTeamId: String,
    val tradeId: Long,
    val accept: Boolean
)

@Serializable
data class ActionRejectedPayload(
    val targetClientId: String,
    val reason: String
)

@Serializable
data class MultiplayerEnvelope(
    val messageId: String,
    val type: EnvelopeMessageType,
    val senderClientId: String,
    val timestampEpochMs: Long,
    val snapshot: MultiplayerRoomSnapshot? = null,
    val participantUpdate: JoinOrUpdateParticipantPayload? = null,
    val targetClientId: String? = null,
    val chatMessage: MultiplayerChatMessage? = null,
    val draftPickIntent: DraftPickIntentPayload? = null,
    val lineupUpdateIntent: LineupUpdateIntentPayload? = null,
    val marketIntent: MarketActionIntentPayload? = null,
    val tradeProposeIntent: TradeProposeIntentPayload? = null,
    val tradeRespondIntent: TradeRespondIntentPayload? = null,
    val actionRejected: ActionRejectedPayload? = null
)

data class MultiplayerUiState(
    val role: RoomRole = RoomRole.NONE,
    val roomCode: String = "",
    val pendingDeepLinkCode: String = "",
    val connectionState: MultiplayerConnectionState = MultiplayerConnectionState.DISCONNECTED,
    val localClientId: String = "",
    val localGmName: String = "GM Captain",
    val controlledTeamId: String = "USER",
    val phase: MultiplayerRoomPhase = MultiplayerRoomPhase.LOBBY,
    val fillEmptySlotsWithCpu: Boolean = true,
    val draftConfig: DraftConfig = DraftConfig(),
    val participants: List<LobbyParticipant> = emptyList(),
    val chatMessages: List<MultiplayerChatMessage> = emptyList(),
    val unreadChatCount: Int = 0,
    val lastError: String = "",
    val customRelayUrl: String = ""
) {
    val isMultiplayerActive: Boolean
        get() = role != RoomRole.NONE && roomCode.isNotBlank()

    val onlineHumanCount: Int
        get() = participants.count { !it.isLocalPassAndPlay }

    val totalHumanParticipantsCount: Int
        get() = participants.size
}
