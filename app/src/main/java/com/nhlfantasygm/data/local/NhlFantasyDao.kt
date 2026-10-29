package com.nhlfantasygm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NhlFantasyDao {

    // --- NHL Teams ---
    @Query("SELECT * FROM nhl_teams ORDER BY leagueSequence ASC, abbrev ASC")
    fun observeNhlTeams(): Flow<List<NhlTeamEntity>>

    @Query("SELECT * FROM nhl_teams ORDER BY abbrev ASC")
    suspend fun getNhlTeamsOnce(): List<NhlTeamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNhlTeams(teams: List<NhlTeamEntity>)

    // --- Players ---
    @Query("SELECT * FROM players ORDER BY overallRating DESC, points DESC, wins DESC")
    fun observeAllPlayers(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players ORDER BY overallRating DESC")
    suspend fun getAllPlayersOnce(): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE playerId = :playerId LIMIT 1")
    suspend fun getPlayerById(playerId: Long): PlayerEntity?

    @Query("SELECT * FROM players WHERE playerId = :playerId LIMIT 1")
    fun observePlayerById(playerId: Long): Flow<PlayerEntity?>

    @Query("SELECT * FROM players WHERE ownerFantasyTeamId = :teamId ORDER BY overallRating DESC")
    fun observeTeamRoster(teamId: String): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE ownerFantasyTeamId = :teamId ORDER BY overallRating DESC")
    suspend fun getTeamRosterOnce(teamId: String): List<PlayerEntity>

    @Query("SELECT COUNT(*) FROM players")
    suspend fun getPlayerCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayers(players: List<PlayerEntity>)

    @Update
    suspend fun updatePlayer(player: PlayerEntity)

    @Update
    suspend fun updatePlayers(players: List<PlayerEntity>)

    @Query("UPDATE players SET isWatchlisted = :watchlisted WHERE playerId = :playerId")
    suspend fun setWatchlisted(playerId: Long, watchlisted: Boolean)

    @Query("UPDATE players SET lastViewedEpochMs = :timestamp WHERE playerId = :playerId")
    suspend fun markPlayerViewed(playerId: Long, timestamp: Long)

    @Query("UPDATE players SET ownerFantasyTeamId = NULL, acquisitionPriceCad = 0, lineupSlotCode = 'UNASSIGNED'")
    suspend fun releaseAllPlayerOwnership()

    // --- Player Profile Cache ---
    @Query("SELECT * FROM player_profile_cache WHERE playerId = :playerId LIMIT 1")
    suspend fun getPlayerProfileCache(playerId: Long): PlayerProfileCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayerProfileCache(cache: PlayerProfileCacheEntity)

    // --- Fantasy Teams ---
    @Query("SELECT * FROM fantasy_teams ORDER BY isHuman DESC, wins DESC, pointsFor DESC")
    fun observeFantasyTeams(): Flow<List<FantasyTeamEntity>>

    @Query("SELECT * FROM fantasy_teams")
    suspend fun getFantasyTeamsOnce(): List<FantasyTeamEntity>

    @Query("SELECT * FROM fantasy_teams WHERE teamId = :teamId LIMIT 1")
    suspend fun getFantasyTeamById(teamId: String): FantasyTeamEntity?

    @Query("SELECT * FROM fantasy_teams WHERE teamId = :teamId LIMIT 1")
    fun observeFantasyTeamById(teamId: String): Flow<FantasyTeamEntity?>

    @Query("SELECT * FROM fantasy_teams WHERE isHuman = 1 LIMIT 1")
    fun observeHumanTeam(): Flow<FantasyTeamEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFantasyTeam(team: FantasyTeamEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFantasyTeams(teams: List<FantasyTeamEntity>)

    @Query("DELETE FROM fantasy_teams WHERE isHuman = 0")
    suspend fun deleteCpuTeams()

    @Query("DELETE FROM fantasy_teams")
    suspend fun deleteAllFantasyTeams()

    @Query("SELECT * FROM trade_proposals ORDER BY createdAtEpochMs DESC")
    suspend fun getTradeProposalsOnce(): List<TradeProposalEntity>

    // --- Transactions ---
    @Query("SELECT * FROM transactions ORDER BY timestampEpochMs DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestampEpochMs DESC")
    suspend fun getTransactionsOnce(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    // --- Draft ---
    @Query("SELECT * FROM draft_sessions WHERE sessionId = 1 LIMIT 1")
    fun observeDraftSession(): Flow<DraftSessionEntity?>

    @Query("SELECT * FROM draft_sessions WHERE sessionId = 1 LIMIT 1")
    suspend fun getDraftSessionOnce(): DraftSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraftSession(session: DraftSessionEntity)

    @Query("SELECT * FROM draft_picks ORDER BY overallPick ASC")
    fun observeDraftPicks(): Flow<List<DraftPickEntity>>

    @Query("SELECT * FROM draft_picks ORDER BY overallPick ASC")
    suspend fun getDraftPicksOnce(): List<DraftPickEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraftPick(pick: DraftPickEntity)

    @Query("DELETE FROM draft_picks WHERE overallPick >= :fromOverallPick")
    suspend fun deleteDraftPicksFrom(fromOverallPick: Int)

    @Query("DELETE FROM draft_picks")
    suspend fun clearDraftPicks()

    // --- Trades ---
    @Query("SELECT * FROM trade_proposals ORDER BY createdAtEpochMs DESC")
    fun observeTradeProposals(): Flow<List<TradeProposalEntity>>

    @Query("SELECT * FROM trade_proposals WHERE tradeId = :tradeId LIMIT 1")
    suspend fun getTradeProposalById(tradeId: Long): TradeProposalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTradeProposal(proposal: TradeProposalEntity): Long

    @Update
    suspend fun updateTradeProposal(proposal: TradeProposalEntity)

    @Query("DELETE FROM trade_proposals")
    suspend fun clearTradeProposals()

    // --- Fantasy Season Matchups & Archives ---
    @Query("SELECT * FROM fantasy_matchups ORDER BY weekNumber ASC, matchupId ASC")
    fun observeMatchups(): Flow<List<FantasyMatchupEntity>>

    @Query("SELECT * FROM fantasy_matchups ORDER BY weekNumber ASC, matchupId ASC")
    suspend fun getMatchupsOnce(): List<FantasyMatchupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatchups(matchups: List<FantasyMatchupEntity>)

    @Update
    suspend fun updateMatchups(matchups: List<FantasyMatchupEntity>)

    @Query("DELETE FROM fantasy_matchups")
    suspend fun clearMatchups()

    @Query("SELECT * FROM season_archives ORDER BY completedAtEpochMs DESC")
    fun observeSeasonArchives(): Flow<List<SeasonArchiveEntity>>

    @Query("SELECT * FROM season_archives ORDER BY seasonNumber DESC")
    suspend fun getSeasonArchivesOnce(): List<SeasonArchiveEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeasonArchive(archive: SeasonArchiveEntity)

    // --- Live NHL Games & News ---
    @Query("SELECT * FROM live_nhl_games ORDER BY startTimeUtc ASC")
    fun observeLiveNhlGames(): Flow<List<LiveNhlGameEntity>>

    @Query("DELETE FROM live_nhl_games")
    suspend fun clearLiveNhlGames()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiveNhlGames(games: List<LiveNhlGameEntity>)

    @Query("SELECT * FROM news_headlines ORDER BY publishedAt DESC")
    fun observeNewsHeadlines(): Flow<List<NewsHeadlineEntity>>

    @Query("DELETE FROM news_headlines")
    suspend fun clearNewsHeadlines()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNewsHeadlines(articles: List<NewsHeadlineEntity>)

    // --- Sync Status ---
    @Query("SELECT * FROM sync_status WHERE id = 1 LIMIT 1")
    fun observeSyncStatus(): Flow<SyncStatusEntity?>

    @Query("SELECT * FROM sync_status WHERE id = 1 LIMIT 1")
    suspend fun getSyncStatusOnce(): SyncStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncStatus(status: SyncStatusEntity)

    // --- Atomic Composite Operations ---
    @Transaction
    suspend fun replaceLiveGamesAtomically(games: List<LiveNhlGameEntity>) {
        clearLiveNhlGames()
        if (games.isNotEmpty()) {
            insertLiveNhlGames(games)
        }
    }

    @Transaction
    suspend fun replaceNewsAtomically(articles: List<NewsHeadlineEntity>) {
        clearNewsHeadlines()
        if (articles.isNotEmpty()) {
            insertNewsHeadlines(articles)
        }
    }
}
