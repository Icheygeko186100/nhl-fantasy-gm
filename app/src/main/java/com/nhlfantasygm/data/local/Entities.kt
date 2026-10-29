package com.nhlfantasygm.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nhlfantasygm.model.InjurySeverity
import com.nhlfantasygm.model.LineupSlot
import com.nhlfantasygm.model.Position
import kotlinx.serialization.Serializable

@Entity(tableName = "nhl_teams")
data class NhlTeamEntity(
    @PrimaryKey val abbrev: String,
    val fullName: String,
    val commonName: String,
    val placeName: String,
    val conferenceName: String,
    val divisionName: String,
    val logoLightUrl: String,
    val logoDarkUrl: String,
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val otLosses: Int,
    val points: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val streakCode: String,
    val streakCount: Int,
    val leagueSequence: Int
)

@Entity(
    tableName = "players",
    indices = [
        Index(value = ["teamAbbrev"]),
        Index(value = ["positionCode"]),
        Index(value = ["ownerFantasyTeamId"]),
        Index(value = ["normalizedSearchName"])
    ]
)
data class PlayerEntity(
    @PrimaryKey val playerId: Long,
    val firstName: String,
    val lastName: String,
    val fullName: String,
    val normalizedSearchName: String,
    val teamAbbrev: String,
    val teamFullName: String,
    val positionCode: String,
    val sweaterNumber: Int,
    val age: Int,
    val birthDate: String,
    val birthCountry: String,
    val shootsCatches: String,
    val heightInches: Int,
    val weightPounds: Int,
    val headshotUrl: String,
    val isProspect: Boolean,
    val isRookie: Boolean,

    // Current Season Stats
    val seasonId: Long,
    val gamesPlayed: Int,
    val goals: Int,
    val assists: Int,
    val points: Int,
    val plusMinus: Int,
    val penaltyMinutes: Int,
    val powerPlayGoals: Int,
    val powerPlayPoints: Int,
    val shorthandedPoints: Int,
    val shots: Int,
    val hits: Int,
    val blockedShots: Int,
    val avgToiSeconds: Int,
    val faceoffWinPct: Double,

    // Goalie Current Season Stats
    val gamesStarted: Int,
    val wins: Int,
    val losses: Int,
    val otLosses: Int,
    val saves: Int,
    val shotsAgainst: Int,
    val goalsAgainst: Int,
    val savePct: Double,
    val goalsAgainstAverage: Double,
    val shutouts: Int,

    // Prior Completed Season Historical Baseline Stats
    val prevSeasonId: Long,
    val prevGamesPlayed: Int,
    val prevGoals: Int,
    val prevAssists: Int,
    val prevPoints: Int,
    val prevPpPoints: Int,
    val prevShots: Int,
    val prevHits: Int,
    val prevBlockedShots: Int,
    val prevWins: Int,
    val prevSaves: Int,
    val prevSavePct: Double,
    val prevGaa: Double,
    val prevShutouts: Int,

    // Today's Real Live / Completed Boxscore Stats
    val todayGameId: Long = 0L,
    val todayGameIsLive: Boolean = false,
    val todayOpponentLabel: String = "",
    val todayGoals: Int = 0,
    val todayAssists: Int = 0,
    val todayPpPoints: Int = 0,
    val todayShots: Int = 0,
    val todayHits: Int = 0,
    val todayBlocks: Int = 0,
    val todayGoalieWin: Int = 0,
    val todayGoalieSaves: Int = 0,
    val todayGoalieShutout: Int = 0,

    // Injury & Availability
    val injuryStatus: String = "Healthy",
    val injuryType: String = "",
    val injuryNote: String = "",
    val injuryReturnDate: String = "",

    // Valuation & Market Pricing (Virtual CAD)
    val overallRating: Int = 72,
    val purchasePriceCad: Long = 1_500_000L,
    val sellPriceCad: Long = 1_440_000L,
    val capHitCad: Long = 1_200_000L,
    val contractYears: Int = 2,
    val previousPriceCad: Long = 1_500_000L,
    val priceDeltaCad: Long = 0L,
    val priceChangeReason: String = "Initial NHL statistical valuation",

    // Persistent Franchise / Ownership State
    val ownerFantasyTeamId: String? = null,
    val acquisitionPriceCad: Long = 0L,
    val lineupSlotCode: String = LineupSlot.UNASSIGNED.code,
    val isWatchlisted: Boolean = false,
    val lastViewedEpochMs: Long = 0L,
    val lastSyncedEpochMs: Long = 0L
) {
    val position: Position get() = Position.fromCode(positionCode)
    val injurySeverity: InjurySeverity get() = InjurySeverity.fromStatusText(injuryStatus)
    val lineupSlot: LineupSlot get() = LineupSlot.fromCode(lineupSlotCode)
}

@Serializable
data class HistoricalSeasonRow(
    val seasonLabel: String,
    val teamName: String,
    val league: String,
    val gamesPlayed: Int,
    val goals: Int = 0,
    val assists: Int = 0,
    val points: Int = 0,
    val plusMinus: Int = 0,
    val shots: Int = 0,
    val ppPoints: Int = 0,
    val wins: Int = 0,
    val savePct: Double = 0.0,
    val gaa: Double = 0.0,
    val shutouts: Int = 0
)

@Serializable
data class RecentGameLogRow(
    val gameDate: String,
    val opponentAbbrev: String,
    val homeRoad: String,
    val goals: Int = 0,
    val assists: Int = 0,
    val points: Int = 0,
    val shots: Int = 0,
    val plusMinus: Int = 0,
    val pim: Int = 0,
    val toi: String = "",
    val decision: String = "",
    val savePct: Double = 0.0,
    val shotsAgainst: Int = 0,
    val goalsAgainst: Int = 0
)

@Entity(tableName = "player_profile_cache")
data class PlayerProfileCacheEntity(
    @PrimaryKey val playerId: Long,
    val careerGamesPlayed: Int,
    val careerGoals: Int,
    val careerAssists: Int,
    val careerPoints: Int,
    val careerWins: Int,
    val careerSavePct: Double,
    val careerGaa: Double,
    val careerShutouts: Int,
    val draftSummary: String,
    val seasonHistoryJson: String,
    val recentGamesJson: String,
    val cachedAtEpochMs: Long
)

@Entity(tableName = "fantasy_teams")
data class FantasyTeamEntity(
    @PrimaryKey val teamId: String,
    val teamName: String,
    val abbreviation: String,
    val emblemId: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val isHuman: Boolean,
    val ownerType: String = if (isHuman) "HUMAN_GM" else "CPU_GM",
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
    val isCpuGm: Boolean get() = ownerType == "CPU_GM" || !isHuman
    val isRemoteHumanGm: Boolean get() = ownerType == "REMOTE_HUMAN_GM"
    val isLocalPassAndPlayGm: Boolean get() = ownerType == "LOCAL_HUMAN_GM"
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String, // BUY, SELL, DRAFT, TRADE, REWARD, SETTINGS
    val teamId: String,
    val counterpartyLabel: String,
    val playerIdsCsv: String,
    val title: String,
    val details: String,
    val cashDeltaCad: Long,
    val balanceAfterCad: Long,
    val realizedProfitLossCad: Long = 0L,
    val timestampEpochMs: Long
)

@Entity(tableName = "draft_sessions")
data class DraftSessionEntity(
    @PrimaryKey val sessionId: Int = 1,
    val status: String, // NOT_STARTED, IN_PROGRESS, COMPLETED
    val configJson: String,
    val teamOrderCsv: String,
    val currentOverallPick: Int, // 1-based; > totalPicks when completed
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long = 0L
)

@Entity(tableName = "draft_picks")
data class DraftPickEntity(
    @PrimaryKey val overallPick: Int,
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
)

@Entity(tableName = "trade_proposals")
data class TradeProposalEntity(
    @PrimaryKey(autoGenerate = true) val tradeId: Long = 0L,
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
    val status: String, // PENDING, ACCEPTED, REJECTED
    val fairnessScore: Int,
    val gmEvaluationReason: String,
    val createdAtEpochMs: Long,
    val resolvedAtEpochMs: Long = 0L
)

@Entity(tableName = "fantasy_matchups")
data class FantasyMatchupEntity(
    @PrimaryKey(autoGenerate = true) val matchupId: Long = 0L,
    val seasonNumber: Int,
    val weekNumber: Int,
    val stage: String, // REGULAR_SEASON, SEMIFINAL, CHAMPIONSHIP_FINAL
    val homeTeamId: String,
    val homeTeamName: String,
    val awayTeamId: String,
    val awayTeamName: String,
    val homeScore: Double = 0.0,
    val awayScore: Double = 0.0,
    val isCompleted: Boolean = false,
    val scoringModeLabel: String = "PENDING", // REAL_NHL_STATS or OFFLINE_SIM
    val highlightSummary: String = "",
    val completedAtEpochMs: Long = 0L
)

@Entity(tableName = "season_archives")
data class SeasonArchiveEntity(
    @PrimaryKey(autoGenerate = true) val archiveId: Long = 0L,
    val seasonNumber: Int,
    val nhlSeasonLabel: String,
    val championTeamId: String,
    val championTeamName: String,
    val runnerUpTeamName: String,
    val userRecord: String,
    val userPointsFor: Double,
    val hartMvpWinner: String,
    val vezinaGoalieWinner: String,
    val norrisDefenseWinner: String,
    val calderRookieWinner: String,
    val gmOfTheYearWinner: String,
    val completedAtEpochMs: Long
)

@Entity(tableName = "live_nhl_games")
data class LiveNhlGameEntity(
    @PrimaryKey val gameId: Long,
    val gameDate: String,
    val gameState: String, // FUT, PRE, LIVE, CRIT, FINAL, OFF
    val periodLabel: String,
    val clockLabel: String,
    val awayAbbrev: String,
    val awayName: String,
    val awayScore: Int,
    val awaySog: Int,
    val awayLogoUrl: String,
    val homeAbbrev: String,
    val homeName: String,
    val homeScore: Int,
    val homeSog: Int,
    val homeLogoUrl: String,
    val startTimeUtc: String,
    val venueName: String
) {
    val isLive: Boolean get() = gameState == "LIVE" || gameState == "CRIT"
    val isFinal: Boolean get() = gameState == "FINAL" || gameState == "OFF"
}

@Entity(tableName = "news_headlines")
data class NewsHeadlineEntity(
    @PrimaryKey val articleId: String,
    val headline: String,
    val description: String,
    val publishedAt: String,
    val webUrl: String,
    val sourceLabel: String,
    val fetchedAtEpochMs: Long
)

@Entity(tableName = "sync_status")
data class SyncStatusEntity(
    @PrimaryKey val id: Int = 1,
    val lastFullSyncEpochMs: Long = 0L,
    val lastScoreSyncEpochMs: Long = 0L,
    val lastNewsSyncEpochMs: Long = 0L,
    val activeSeasonId: Long = 20262027L,
    val prevSeasonId: Long = 20252026L,
    val isOffseason: Boolean = false,
    val lastSyncSucceeded: Boolean = false,
    val lastErrorMessage: String = "",
    val syncedPlayerCount: Int = 0,
    val syncedTeamCount: Int = 0,
    val injuriesAvailable: Boolean = false,
    val newsAvailable: Boolean = false,
    val liveGamesCount: Int = 0
)
