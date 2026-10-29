package com.nhlfantasygm.data.repository

import androidx.room.withTransaction
import com.nhlfantasygm.data.local.*
import com.nhlfantasygm.data.remote.*
import com.nhlfantasygm.domain.*
import com.nhlfantasygm.model.*
import com.nhlfantasygm.multiplayer.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

class NhlFantasyRepository(
    private val database: NhlFantasyDatabase,
    private val dao: NhlFantasyDao,
    private val remote: NhlRemoteDataSource,
    val settingsStore: SettingsDataStore,
    private val credentialStore: SecureCredentialStore
) {
    private val syncMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val nhlTeamsFlow: Flow<List<NhlTeamEntity>> = dao.observeNhlTeams()
    val allPlayersFlow: Flow<List<PlayerEntity>> = dao.observeAllPlayers()
    val fantasyTeamsFlow: Flow<List<FantasyTeamEntity>> = dao.observeFantasyTeams()
    val settingsFlow: Flow<UserAppSettings> = settingsStore.settingsFlow

    val controlledTeamIdFlow: Flow<String> = settingsFlow
        .map { it.controlledTeamId.ifBlank { USER_TEAM_ID } }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val humanTeamFlow: Flow<FantasyTeamEntity?> = controlledTeamIdFlow
        .flatMapLatest { controlledId ->
            dao.observeFantasyTeamById(controlledId)
        }

    val transactionsFlow: Flow<List<TransactionEntity>> = dao.observeTransactions()
    val draftSessionFlow: Flow<DraftSessionEntity?> = dao.observeDraftSession()
    val draftPicksFlow: Flow<List<DraftPickEntity>> = dao.observeDraftPicks()
    val tradeProposalsFlow: Flow<List<TradeProposalEntity>> = dao.observeTradeProposals()
    val matchupsFlow: Flow<List<FantasyMatchupEntity>> = dao.observeMatchups()
    val seasonArchivesFlow: Flow<List<SeasonArchiveEntity>> = dao.observeSeasonArchives()
    val liveGamesFlow: Flow<List<LiveNhlGameEntity>> = dao.observeLiveNhlGames()
    val newsHeadlinesFlow: Flow<List<NewsHeadlineEntity>> = dao.observeNewsHeadlines()
    val syncStatusFlow: Flow<SyncStatusEntity?> = dao.observeSyncStatus()

    fun observeTeamRoster(teamId: String): Flow<List<PlayerEntity>> = dao.observeTeamRoster(teamId)

    suspend fun getActiveControlledTeamId(): String =
        settingsFlow.first().controlledTeamId.ifBlank { USER_TEAM_ID }

    /**
     * Ensures the user's franchise, default 8-team league, and initial schedule exist on first launch.
     */
    suspend fun ensureFranchiseInitialized() {
        val existingTeams = dao.getFantasyTeamsOnce()
        if (existingTeams.any { it.isHuman }) {
            val matchups = dao.getMatchupsOnce()
            if (matchups.isEmpty() && existingTeams.size >= 2) {
                val settings = settingsFlow.first()
                val schedule = SeasonSimulationEngine.generateRegularSeasonSchedule(
                    seasonNumber = settings.currentSeasonNumber,
                    teams = existingTeams
                )
                dao.insertMatchups(schedule)
            }
            return
        }

        val settings = settingsFlow.first()
        val budget = settings.economyConfig.startingBudgetCad
        val now = System.currentTimeMillis()

        val userTeam = FantasyTeamEntity(
            teamId = USER_TEAM_ID,
            teamName = "Northern Ice HC",
            abbreviation = "NIC",
            emblemId = TeamEmblem.MAPLE_SHIELD.id,
            primaryColorHex = 0xFF00C2FF,
            secondaryColorHex = 0xFF0B192C,
            isHuman = true,
            cashBalanceCad = budget,
            startingBudgetCad = budget
        )

        val cpuTeams = createDefaultCpuTeams(count = 7, startingBudgetCad = budget)
        val allTeams = listOf(userTeam) + cpuTeams

        database.withTransaction {
            dao.upsertFantasyTeams(allTeams)
            dao.insertTransaction(
                TransactionEntity(
                    type = "SETTINGS",
                    teamId = USER_TEAM_ID,
                    counterpartyLabel = "League Commissioner",
                    playerIdsCsv = "",
                    title = "Initial Franchise Treasury Allocation",
                    details = "Starting virtual CAD GM budget initialized (${CadFormatter.formatFull(budget)}).",
                    cashDeltaCad = budget,
                    balanceAfterCad = budget,
                    timestampEpochMs = now
                )
            )
            val schedule = SeasonSimulationEngine.generateRegularSeasonSchedule(
                seasonNumber = settings.currentSeasonNumber,
                teams = allTeams
            )
            dao.insertMatchups(schedule)
        }
    }

    /**
     * Full live NHL synchronization across official NHL Web API, NHL Stats REST API,
     * and public ESPN NHL Injuries & News endpoints. Preserves all user ownership, draft,
     * lineup, and watchlist progress.
     */
    suspend fun syncAllNhlData(includeProspects: Boolean = true): Result<Int> = syncMutex.withLock {
        val now = System.currentTimeMillis()
        val prevStatus = dao.getSyncStatusOnce() ?: SyncStatusEntity()
        val settings = settingsFlow.first()

        return runCatching {
            val standingsDto = remote.fetchStandingsNow().getOrThrow()
            if (standingsDto.standings.isEmpty()) {
                throw IllegalStateException("NHL standings endpoint returned empty team list")
            }

            val detectedSeasonId = standingsDto.standings.firstOrNull { it.seasonId > 20000000L }?.seasonId
                ?: 20262027L
            val startYear = detectedSeasonId / 10000L
            val prevSeasonId = (startYear - 1L) * 10000L + startYear
            val totalStandingsGames = standingsDto.standings.sumOf { it.gamesPlayed }
            val isOffseason = totalStandingsGames == 0

            val nhlTeams = standingsDto.standings.map { dto ->
                val abbrev = dto.teamAbbrev.default.trim()
                NhlTeamEntity(
                    abbrev = abbrev,
                    fullName = dto.teamName.default.ifBlank { abbrev },
                    commonName = dto.teamCommonName?.default ?: dto.teamName.default,
                    placeName = dto.placeName?.default ?: "",
                    conferenceName = dto.conferenceName,
                    divisionName = dto.divisionName,
                    logoLightUrl = dto.teamLogo,
                    logoDarkUrl = dto.teamLogoDark.ifBlank { dto.teamLogo },
                    gamesPlayed = dto.gamesPlayed,
                    wins = dto.wins,
                    losses = dto.losses,
                    otLosses = dto.otLosses,
                    points = dto.points,
                    goalsFor = dto.goalFor,
                    goalsAgainst = dto.goalAgainst,
                    streakCode = dto.streakCode,
                    streakCount = dto.streakCount,
                    leagueSequence = dto.leagueSequence
                )
            }

            val teamNameByAbbrev = nhlTeams.associate { it.abbrev to it.fullName }
            val teamAbbrevs = nhlTeams.map { it.abbrev }

            // Fetch rosters, prospects, bulk current/prior stats, scoreboard, injuries, and news concurrently
            val syncBundle = coroutineScope {
                val rostersDeferred = async { remote.fetchAllTeamRosters(teamAbbrevs) }
                val prospectsDeferred = async {
                    if (includeProspects) remote.fetchAllTeamProspects(teamAbbrevs) else emptyMap()
                }
                val curSkaterSumDeferred = async { remote.fetchSkaterSummaryBulk(detectedSeasonId).getOrNull() }
                val curSkaterRtDeferred = async { remote.fetchSkaterRealtimeBulk(detectedSeasonId).getOrNull() }
                val curGoalieSumDeferred = async { remote.fetchGoalieSummaryBulk(detectedSeasonId).getOrNull() }

                val prevSkaterSumDeferred = async { remote.fetchSkaterSummaryBulk(prevSeasonId).getOrNull() }
                val prevSkaterRtDeferred = async { remote.fetchSkaterRealtimeBulk(prevSeasonId).getOrNull() }
                val prevGoalieSumDeferred = async { remote.fetchGoalieSummaryBulk(prevSeasonId).getOrNull() }

                val scoreboardDeferred = async { remote.fetchScoreboardNow().getOrNull() }
                val injuriesDeferred = async { remote.fetchEspnInjuries().getOrNull() }
                val newsDeferred = async { remote.fetchEspnNews().getOrNull() }

                SyncBundle(
                    rosters = rostersDeferred.await(),
                    prospects = prospectsDeferred.await(),
                    curSkaterSum = curSkaterSumDeferred.await()?.data.orEmpty(),
                    curSkaterRt = curSkaterRtDeferred.await()?.data.orEmpty(),
                    curGoalieSum = curGoalieSumDeferred.await()?.data.orEmpty(),
                    prevSkaterSum = prevSkaterSumDeferred.await()?.data.orEmpty(),
                    prevSkaterRt = prevSkaterRtDeferred.await()?.data.orEmpty(),
                    prevGoalieSum = prevGoalieSumDeferred.await()?.data.orEmpty(),
                    scoreboard = scoreboardDeferred.await(),
                    injuries = injuriesDeferred.await(),
                    news = newsDeferred.await()
                )
            }

            // Fetch live/completed boxscores for today's games
            val activeOrCompletedGameIds = syncBundle.scoreboard?.games
                ?.filter { it.gameState != "FUT" && it.gameState != "PRE" }
                ?.map { it.id }
                .orEmpty()
            val boxscores = if (activeOrCompletedGameIds.isNotEmpty()) {
                remote.fetchActiveBoxscores(activeOrCompletedGameIds)
            } else emptyList()

            val todayBoxMap = buildTodayPlayerBoxscoreMap(boxscores)

            val curSkaterById = syncBundle.curSkaterSum.associateBy { it.playerId }
            val curRtById = syncBundle.curSkaterRt.associateBy { it.playerId }
            val curGoalieById = syncBundle.curGoalieSum.associateBy { it.playerId }

            val prevSkaterById = syncBundle.prevSkaterSum.associateBy { it.playerId }
            val prevRtById = syncBundle.prevSkaterRt.associateBy { it.playerId }
            val prevGoalieById = syncBundle.prevGoalieSum.associateBy { it.playerId }

            // Map ESPN Injuries by normalized player full name
            val injuryLookup = buildInjuryLookup(syncBundle.injuries)

            val existingPlayersById = dao.getAllPlayersOnce().associateBy { it.playerId }
            val mergedPlayers = LinkedHashMap<Long, PlayerEntity>(1600)

            fun buildEntityFromRosterDto(
                dto: RosterPlayerDto,
                teamAbbrev: String,
                forcedPos: String?,
                isProspectEntry: Boolean
            ) {
                if (dto.id <= 0L) return
                // Active roster takes precedence over prospect entry if player appears in both
                if (isProspectEntry && mergedPlayers.containsKey(dto.id)) return

                val firstName = dto.firstName.default.trim()
                val lastName = dto.lastName.default.trim()
                val fullName = "$firstName $lastName".trim()
                if (fullName.isBlank()) return

                val normalizedName = TextNormalizer.normalizeForMatch(fullName)
                val posCode = Position.fromCode(forcedPos ?: dto.positionCode).apiCode
                val age = TextNormalizer.computeAge(dto.birthDate)

                val curSk = curSkaterById[dto.id]
                val curRt = curRtById[dto.id]
                val curG = curGoalieById[dto.id]
                val prevSk = prevSkaterById[dto.id]
                val prevRt = prevRtById[dto.id]
                val prevG = prevGoalieById[dto.id]

                val curGp = curSk?.gamesPlayed ?: curG?.gamesPlayed ?: 0
                val prevGp = prevSk?.gamesPlayed ?: prevG?.gamesPlayed ?: 0
                val isRookie = !isProspectEntry && age <= 25 && prevGp < 25 && curGp > 0

                val injury = injuryLookup[normalizedName]
                val todayBox = todayBoxMap[dto.id]
                val existing = existingPlayersById[dto.id]

                val draftEntity = PlayerEntity(
                    playerId = dto.id,
                    firstName = firstName,
                    lastName = lastName,
                    fullName = fullName,
                    normalizedSearchName = normalizedName,
                    teamAbbrev = teamAbbrev,
                    teamFullName = teamNameByAbbrev[teamAbbrev] ?: teamAbbrev,
                    positionCode = posCode,
                    sweaterNumber = dto.sweaterNumber ?: 0,
                    age = age,
                    birthDate = dto.birthDate ?: "",
                    birthCountry = dto.birthCountry ?: "CAN",
                    shootsCatches = dto.shootsCatches ?: curSk?.shootsCatches ?: curG?.shootsCatches ?: "L",
                    heightInches = dto.heightInInches ?: 73,
                    weightPounds = dto.weightInPounds ?: 198,
                    headshotUrl = dto.headshot.ifBlank {
                        "https://assets.nhle.com/mugs/nhl/$detectedSeasonId/$teamAbbrev/${dto.id}.png"
                    },
                    isProspect = isProspectEntry && curGp == 0,
                    isRookie = isRookie,
                    seasonId = detectedSeasonId,
                    gamesPlayed = curGp,
                    goals = curSk?.goals ?: 0,
                    assists = curSk?.assists ?: 0,
                    points = curSk?.points ?: 0,
                    plusMinus = curSk?.plusMinus ?: 0,
                    penaltyMinutes = curSk?.penaltyMinutes ?: 0,
                    powerPlayGoals = curSk?.ppGoals ?: 0,
                    powerPlayPoints = curSk?.ppPoints ?: 0,
                    shorthandedPoints = curSk?.shPoints ?: 0,
                    shots = curSk?.shots ?: 0,
                    hits = curRt?.hits ?: 0,
                    blockedShots = curRt?.blockedShots ?: 0,
                    avgToiSeconds = curSk?.timeOnIcePerGame?.roundToInt() ?: 0,
                    faceoffWinPct = curSk?.faceoffWinPct ?: 0.0,
                    gamesStarted = curG?.gamesStarted ?: 0,
                    wins = curG?.wins ?: 0,
                    losses = curG?.losses ?: 0,
                    otLosses = curG?.otLosses ?: 0,
                    saves = curG?.saves ?: 0,
                    shotsAgainst = curG?.shotsAgainst ?: 0,
                    goalsAgainst = curG?.goalsAgainst ?: 0,
                    savePct = curG?.savePct ?: 0.0,
                    goalsAgainstAverage = curG?.goalsAgainstAverage ?: 0.0,
                    shutouts = curG?.shutouts ?: 0,
                    prevSeasonId = prevSeasonId,
                    prevGamesPlayed = prevGp,
                    prevGoals = prevSk?.goals ?: 0,
                    prevAssists = prevSk?.assists ?: 0,
                    prevPoints = prevSk?.points ?: 0,
                    prevPpPoints = prevSk?.ppPoints ?: 0,
                    prevShots = prevSk?.shots ?: 0,
                    prevHits = prevRt?.hits ?: 0,
                    prevBlockedShots = prevRt?.blockedShots ?: 0,
                    prevWins = prevG?.wins ?: 0,
                    prevSaves = prevG?.saves ?: 0,
                    prevSavePct = prevG?.savePct ?: 0.0,
                    prevGaa = prevG?.goalsAgainstAverage ?: 0.0,
                    prevShutouts = prevG?.shutouts ?: 0,
                    todayGameId = todayBox?.gameId ?: 0L,
                    todayGameIsLive = todayBox?.isLive ?: false,
                    todayOpponentLabel = todayBox?.opponentLabel ?: "",
                    todayGoals = todayBox?.goals ?: 0,
                    todayAssists = todayBox?.assists ?: 0,
                    todayPpPoints = todayBox?.ppPoints ?: 0,
                    todayShots = todayBox?.shots ?: 0,
                    todayHits = todayBox?.hits ?: 0,
                    todayBlocks = todayBox?.blocks ?: 0,
                    todayGoalieWin = todayBox?.goalieWin ?: 0,
                    todayGoalieSaves = todayBox?.goalieSaves ?: 0,
                    todayGoalieShutout = todayBox?.goalieShutout ?: 0,
                    injuryStatus = injury?.status ?: "Healthy",
                    injuryType = injury?.type ?: "",
                    injuryNote = injury?.note ?: "",
                    injuryReturnDate = injury?.returnDate ?: "",
                    ownerFantasyTeamId = existing?.ownerFantasyTeamId,
                    acquisitionPriceCad = existing?.acquisitionPriceCad ?: 0L,
                    lineupSlotCode = existing?.lineupSlotCode ?: LineupSlot.UNASSIGNED.code,
                    isWatchlisted = existing?.isWatchlisted ?: false,
                    lastViewedEpochMs = existing?.lastViewedEpochMs ?: 0L,
                    lastSyncedEpochMs = now
                )

                val valuation = PlayerValuationEngine.valuatePlayer(
                    player = draftEntity,
                    economyConfig = settings.economyConfig,
                    previousSavedPriceCad = existing?.purchasePriceCad
                )

                mergedPlayers[dto.id] = draftEntity.copy(
                    overallRating = valuation.overallRating,
                    purchasePriceCad = valuation.purchasePriceCad,
                    sellPriceCad = valuation.sellPriceCad,
                    capHitCad = valuation.simulatedCapHitCad,
                    contractYears = valuation.contractYears,
                    previousPriceCad = existing?.purchasePriceCad ?: (valuation.purchasePriceCad - valuation.priceDeltaCad),
                    priceDeltaCad = valuation.priceDeltaCad,
                    priceChangeReason = valuation.priceChangeExplanation
                )
            }

            // 1. Process all 32 NHL active rosters
            syncBundle.rosters.forEach { (abbrev, rosterDto) ->
                rosterDto.forwards.forEach { buildEntityFromRosterDto(it, abbrev, null, false) }
                rosterDto.defensemen.forEach { buildEntityFromRosterDto(it, abbrev, "D", false) }
                rosterDto.goalies.forEach { buildEntityFromRosterDto(it, abbrev, "G", false) }
            }

            // 2. Also include any skater/goalie with current-season NHL games who wasn't on today's 23-man active list (e.g. IR or recent call-up)
            syncBundle.curSkaterSum.forEach { sk ->
                if (!mergedPlayers.containsKey(sk.playerId) && sk.playerId > 0L) {
                    val full = sk.skaterFullName?.trim().orEmpty()
                    val parts = full.split(" ", limit = 2)
                    val first = parts.firstOrNull().orEmpty()
                    val last = parts.getOrNull(1) ?: sk.lastName.orEmpty()
                    val abbrev = sk.teamAbbrevs?.split(",")?.lastOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: "NHL"
                    buildEntityFromRosterDto(
                        dto = RosterPlayerDto(
                            id = sk.playerId,
                            headshot = "https://assets.nhle.com/mugs/nhl/$detectedSeasonId/$abbrev/${sk.playerId}.png",
                            firstName = LocalizedStringDto(first),
                            lastName = LocalizedStringDto(last),
                            positionCode = sk.positionCode ?: "C",
                            shootsCatches = sk.shootsCatches
                        ),
                        teamAbbrev = abbrev,
                        forcedPos = sk.positionCode,
                        isProspectEntry = false
                    )
                }
            }
            syncBundle.curGoalieSum.forEach { g ->
                if (!mergedPlayers.containsKey(g.playerId) && g.playerId > 0L) {
                    val full = g.goalieFullName?.trim().orEmpty()
                    val parts = full.split(" ", limit = 2)
                    val first = parts.firstOrNull().orEmpty()
                    val last = parts.getOrNull(1) ?: g.lastName.orEmpty()
                    val abbrev = g.teamAbbrevs?.split(",")?.lastOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: "NHL"
                    buildEntityFromRosterDto(
                        dto = RosterPlayerDto(
                            id = g.playerId,
                            headshot = "https://assets.nhle.com/mugs/nhl/$detectedSeasonId/$abbrev/${g.playerId}.png",
                            firstName = LocalizedStringDto(first),
                            lastName = LocalizedStringDto(last),
                            positionCode = "G",
                            shootsCatches = g.shootsCatches
                        ),
                        teamAbbrev = abbrev,
                        forcedPos = "G",
                        isProspectEntry = false
                    )
                }
            }

            // 3. Process NHL prospects across all 32 teams
            syncBundle.prospects.forEach { (abbrev, prosDto) ->
                prosDto.forwards.take(8).forEach { buildEntityFromRosterDto(it, abbrev, null, true) }
                prosDto.defensemen.take(5).forEach { buildEntityFromRosterDto(it, abbrev, "D", true) }
                prosDto.goalies.take(2).forEach { buildEntityFromRosterDto(it, abbrev, "G", true) }
            }

            // 4. Preserve any owned/drafted player who might not have returned in a transient team endpoint glitch
            existingPlayersById.values
                .filter { it.ownerFantasyTeamId != null && !mergedPlayers.containsKey(it.playerId) }
                .forEach { mergedPlayers[it.playerId] = it }

            val liveGames = mapScoreboardGames(syncBundle.scoreboard)
            val newsArticles = mapNewsArticles(syncBundle.news, now)

            val finalPlayersList = mergedPlayers.values.toList()

            database.withTransaction {
                dao.upsertNhlTeams(nhlTeams)
                if (finalPlayersList.isNotEmpty()) {
                    dao.upsertPlayers(finalPlayersList)
                }
                if (syncBundle.scoreboard != null) {
                    dao.replaceLiveGamesAtomically(liveGames)
                }
                if (syncBundle.news != null && newsArticles.isNotEmpty()) {
                    dao.replaceNewsAtomically(newsArticles)
                }
                dao.upsertSyncStatus(
                    SyncStatusEntity(
                        id = 1,
                        lastFullSyncEpochMs = now,
                        lastScoreSyncEpochMs = if (syncBundle.scoreboard != null) now else prevStatus.lastScoreSyncEpochMs,
                        lastNewsSyncEpochMs = if (syncBundle.news != null) now else prevStatus.lastNewsSyncEpochMs,
                        activeSeasonId = detectedSeasonId,
                        prevSeasonId = prevSeasonId,
                        isOffseason = isOffseason,
                        lastSyncSucceeded = true,
                        lastErrorMessage = "",
                        syncedPlayerCount = finalPlayersList.size,
                        syncedTeamCount = nhlTeams.size,
                        injuriesAvailable = syncBundle.injuries != null,
                        newsAvailable = syncBundle.news != null && newsArticles.isNotEmpty(),
                        liveGamesCount = liveGames.count { it.isLive }
                    )
                )
            }

            finalPlayersList.size
        }.onFailure { err ->
            val currentCount = dao.getPlayerCount()
            dao.upsertSyncStatus(
                prevStatus.copy(
                    lastSyncSucceeded = false,
                    lastErrorMessage = err.message ?: "Unable to reach NHL data service",
                    syncedPlayerCount = currentCount
                )
            )
        }
    }

    /**
     * Quick refresh of today's live NHL scoreboard & in-game boxscores.
     */
    suspend fun refreshLiveScoresOnly(): Result<Int> = syncMutex.withLock {
        val now = System.currentTimeMillis()
        val prevStatus = dao.getSyncStatusOnce() ?: SyncStatusEntity()
        return runCatching {
            val scoreboard = remote.fetchScoreboardNow().getOrThrow()
            val liveGames = mapScoreboardGames(scoreboard)
            val activeIds = scoreboard.games
                .filter { it.gameState != "FUT" && it.gameState != "PRE" }
                .map { it.id }
            val boxscores = if (activeIds.isNotEmpty()) remote.fetchActiveBoxscores(activeIds) else emptyList()
            val todayBoxMap = buildTodayPlayerBoxscoreMap(boxscores)

            val allPlayers = dao.getAllPlayersOnce()
            val updatedPlayers = allPlayers.mapNotNull { p ->
                val box = todayBoxMap[p.playerId]
                if (box != null) {
                    p.copy(
                        todayGameId = box.gameId,
                        todayGameIsLive = box.isLive,
                        todayOpponentLabel = box.opponentLabel,
                        todayGoals = box.goals,
                        todayAssists = box.assists,
                        todayPpPoints = box.ppPoints,
                        todayShots = box.shots,
                        todayHits = box.hits,
                        todayBlocks = box.blocks,
                        todayGoalieWin = box.goalieWin,
                        todayGoalieSaves = box.goalieSaves,
                        todayGoalieShutout = box.goalieShutout
                    )
                } else if (p.todayGameId != 0L) {
                    p.copy(
                        todayGameId = 0L,
                        todayGameIsLive = false,
                        todayGoals = 0,
                        todayAssists = 0,
                        todayPpPoints = 0,
                        todayShots = 0,
                        todayHits = 0,
                        todayBlocks = 0,
                        todayGoalieWin = 0,
                        todayGoalieSaves = 0,
                        todayGoalieShutout = 0
                    )
                } else null
            }

            database.withTransaction {
                dao.replaceLiveGamesAtomically(liveGames)
                if (updatedPlayers.isNotEmpty()) {
                    dao.updatePlayers(updatedPlayers)
                }
                dao.upsertSyncStatus(
                    prevStatus.copy(
                        lastScoreSyncEpochMs = now,
                        lastSyncSucceeded = true,
                        lastErrorMessage = "",
                        liveGamesCount = liveGames.count { it.isLive }
                    )
                )
            }
            liveGames.size
        }
    }

    /**
     * Fetches and caches a player's career historical stats (`seasonTotals`) & `last5Games` game log.
     */
    suspend fun getOrFetchPlayerProfileDetails(playerId: Long): Pair<PlayerProfileCacheEntity?, Boolean> {
        dao.markPlayerViewed(playerId, System.currentTimeMillis())
        val cached = dao.getPlayerProfileCache(playerId)
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.cachedAtEpochMs < 20 * 60 * 1000L) {
            return cached to false
        }

        val remoteLanding = remote.fetchPlayerLanding(playerId).getOrNull()
        if (remoteLanding != null) {
            val career = remoteLanding.featuredStats?.regularSeason?.career
            val draftInfo = remoteLanding.draftDetails?.let { d ->
                if (d.year != null && d.overallPick != null) {
                    "${d.year} NHL Draft • Rd ${d.round ?: "?"}, Pick #${d.overallPick} (${d.teamAbbrev ?: "NHL"})"
                } else "Undrafted / Entry Signee"
            } ?: "Undrafted / Entry Signee"

            val nhlSeasons = remoteLanding.seasonTotals
                .filter { it.gameTypeId == 2 && (it.leagueAbbrev == "NHL" || it.leagueAbbrev == "AHL") }
                .takeLast(8)
                .map { s ->
                    val sStr = s.season.toString()
                    val label = if (sStr.length == 8) "${sStr.substring(2, 4)}-${sStr.substring(6, 8)}" else sStr
                    HistoricalSeasonRow(
                        seasonLabel = label,
                        teamName = s.teamName?.default ?: "",
                        league = s.leagueAbbrev,
                        gamesPlayed = s.gamesPlayed,
                        goals = s.goals,
                        assists = s.assists,
                        points = s.points,
                        plusMinus = s.plusMinus,
                        shots = s.shots,
                        ppPoints = s.powerPlayPoints,
                        wins = s.wins,
                        savePct = s.savePctg,
                        gaa = s.goalsAgainstAvg,
                        shutouts = s.shutouts
                    )
                }

            val recentGames = remoteLanding.last5Games.map { g ->
                RecentGameLogRow(
                    gameDate = g.gameDate,
                    opponentAbbrev = g.opponentAbbrev,
                    homeRoad = g.homeRoadFlag,
                    goals = g.goals,
                    assists = g.assists,
                    points = g.points,
                    shots = g.shots,
                    plusMinus = g.plusMinus,
                    pim = g.pim,
                    toi = g.toi,
                    decision = g.decision,
                    savePct = g.savePctg,
                    shotsAgainst = g.shotsAgainst,
                    goalsAgainst = g.goalsAgainst
                )
            }

            val entity = PlayerProfileCacheEntity(
                playerId = playerId,
                careerGamesPlayed = career?.gamesPlayed ?: 0,
                careerGoals = career?.goals ?: 0,
                careerAssists = career?.assists ?: 0,
                careerPoints = career?.points ?: 0,
                careerWins = career?.wins ?: 0,
                careerSavePct = career?.savePctg ?: 0.0,
                careerGaa = career?.goalsAgainstAvg ?: 0.0,
                careerShutouts = career?.shutouts ?: 0,
                draftSummary = draftInfo,
                seasonHistoryJson = json.encodeToString(nhlSeasons),
                recentGamesJson = json.encodeToString(recentGames),
                cachedAtEpochMs = now
            )
            dao.upsertPlayerProfileCache(entity)
            return entity to true
        }

        return cached to false
    }

    fun decodeHistoricalSeasons(cache: PlayerProfileCacheEntity?): List<HistoricalSeasonRow> {
        if (cache == null || cache.seasonHistoryJson.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<HistoricalSeasonRow>>(cache.seasonHistoryJson) }
            .getOrDefault(emptyList())
    }

    fun decodeRecentGames(cache: PlayerProfileCacheEntity?): List<RecentGameLogRow> {
        if (cache == null || cache.recentGamesJson.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<RecentGameLogRow>>(cache.recentGamesJson) }
            .getOrDefault(emptyList())
    }

    // --- Marketplace Transactions ---

    suspend fun toggleWatchlist(playerId: Long) {
        val player = dao.getPlayerById(playerId) ?: return
        dao.setWatchlisted(playerId, !player.isWatchlisted)
    }

    suspend fun buyPlayerFromMarketplace(playerId: Long, actingTeamId: String? = null): Result<String> {
        val settings = settingsFlow.first()
        val teamId = actingTeamId ?: getActiveControlledTeamId()
        val buyerTeam = dao.getFantasyTeamById(teamId)
            ?: return Result.failure(IllegalStateException("Franchise not initialized"))
        val roster = dao.getTeamRosterOnce(teamId)
        val player = dao.getPlayerById(playerId)
            ?: return Result.failure(IllegalArgumentException("Player not found"))

        val validation = GameTransactionEngine.validatePlayerPurchase(
            buyerTeam = buyerTeam,
            currentRoster = roster,
            targetPlayer = player,
            economyConfig = settings.economyConfig
        )
        if (validation is TransactionValidationResult.Invalid) {
            return Result.failure(IllegalStateException(validation.reason))
        }

        val newBalance = buyerTeam.cashBalanceCad - player.purchasePriceCad
        val updatedRoster = LineupAnalyticsEngine.generateRecommendedLineup(
            roster + player.copy(
                ownerFantasyTeamId = teamId,
                acquisitionPriceCad = player.purchasePriceCad
            )
        )

        database.withTransaction {
            dao.upsertFantasyTeam(buyerTeam.copy(cashBalanceCad = newBalance))
            dao.updatePlayers(updatedRoster)
            dao.insertTransaction(
                TransactionEntity(
                    type = "BUY",
                    teamId = teamId,
                    counterpartyLabel = "NHL Marketplace",
                    playerIdsCsv = player.playerId.toString(),
                    title = "Signed ${player.fullName} (${player.position.uiShort})",
                    details = "${player.teamAbbrev} • ${player.overallRating} OVR • Cap Hit ${CadFormatter.formatCompact(player.capHitCad)}",
                    cashDeltaCad = -player.purchasePriceCad,
                    balanceAfterCad = newBalance,
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
        }
        return Result.success("Signed ${player.fullName} for ${CadFormatter.formatCompact(player.purchasePriceCad)}!")
    }

    suspend fun sellPlayerToMarketplace(playerId: Long, actingTeamId: String? = null): Result<String> {
        val teamId = actingTeamId ?: getActiveControlledTeamId()
        val sellerTeam = dao.getFantasyTeamById(teamId)
            ?: return Result.failure(IllegalStateException("Franchise not initialized"))
        val player = dao.getPlayerById(playerId)
            ?: return Result.failure(IllegalArgumentException("Player not found"))

        val validation = GameTransactionEngine.validatePlayerSale(sellerTeam, player)
        if (validation is TransactionValidationResult.Invalid) {
            return Result.failure(IllegalStateException(validation.reason))
        }

        val costBasis = if (player.acquisitionPriceCad > 0L) player.acquisitionPriceCad else player.purchasePriceCad
        val realizedPnL = player.sellPriceCad - costBasis
        val newBalance = sellerTeam.cashBalanceCad + player.sellPriceCad

        database.withTransaction {
            dao.upsertFantasyTeam(sellerTeam.copy(cashBalanceCad = newBalance))
            dao.updatePlayer(
                player.copy(
                    ownerFantasyTeamId = null,
                    acquisitionPriceCad = 0L,
                    lineupSlotCode = LineupSlot.UNASSIGNED.code
                )
            )
            // Re-optimize remaining starters
            val remainingRoster = dao.getTeamRosterOnce(teamId)
            if (remainingRoster.isNotEmpty()) {
                dao.updatePlayers(LineupAnalyticsEngine.generateRecommendedLineup(remainingRoster))
            }
            dao.insertTransaction(
                TransactionEntity(
                    type = "SELL",
                    teamId = teamId,
                    counterpartyLabel = "NHL Marketplace",
                    playerIdsCsv = player.playerId.toString(),
                    title = "Sold ${player.fullName} (${player.position.uiShort})",
                    details = "Sale price ${CadFormatter.formatCompact(player.sellPriceCad)} • Realized P&L: ${CadFormatter.formatCompact(realizedPnL, showPlusSign = true)}",
                    cashDeltaCad = player.sellPriceCad,
                    balanceAfterCad = newBalance,
                    realizedProfitLossCad = realizedPnL,
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
        }
        return Result.success("Sold ${player.fullName} for ${CadFormatter.formatCompact(player.sellPriceCad)} (${CadFormatter.formatCompact(realizedPnL, showPlusSign = true)} P&L).")
    }

    // --- Draft Mode Operations ---

    suspend fun startNewDraft(config: DraftConfig): Result<Unit> {
        val settings = settingsFlow.first()
        val budget = settings.economyConfig.startingBudgetCad
        val existingUser = dao.getFantasyTeamById(USER_TEAM_ID)

        val userTeam = (existingUser ?: FantasyTeamEntity(
            teamId = USER_TEAM_ID,
            teamName = "Northern Ice HC",
            abbreviation = "NIC",
            emblemId = TeamEmblem.MAPLE_SHIELD.id,
            primaryColorHex = 0xFF00C2FF,
            secondaryColorHex = 0xFF0B192C,
            isHuman = true,
            ownerType = "HUMAN_GM",
            gmDisplayName = settings.gmDisplayName,
            cashBalanceCad = budget,
            startingBudgetCad = budget
        )).copy(
            isHuman = true,
            ownerType = "HUMAN_GM",
            gmDisplayName = settings.gmDisplayName,
            cashBalanceCad = budget,
            startingBudgetCad = budget,
            wins = 0,
            losses = 0,
            ties = 0,
            pointsFor = 0.0,
            pointsAgainst = 0.0
        )

        val cpuCount = (config.teamCount - 1).coerceIn(1, 15)
        val cpuTeams = createDefaultCpuTeams(cpuCount, budget)

        // Place user team at requested 1-based draft slot
        val slotIndex = (config.userDraftPosition - 1).coerceIn(0, cpuTeams.size)
        val orderedTeams = cpuTeams.toMutableList().apply { add(slotIndex, userTeam) }
        val finalConfig = config.copy(teamCount = orderedTeams.size)
        val teamOrderCsv = orderedTeams.joinToString(",") { it.teamId }

        database.withTransaction {
            dao.releaseAllPlayerOwnership()
            dao.deleteAllFantasyTeams()
            dao.upsertFantasyTeams(orderedTeams)
            dao.clearDraftPicks()
            dao.clearTradeProposals()
            dao.clearMatchups()
            dao.upsertDraftSession(
                DraftSessionEntity(
                    sessionId = 1,
                    status = "IN_PROGRESS",
                    configJson = json.encodeToString(finalConfig),
                    teamOrderCsv = teamOrderCsv,
                    currentOverallPick = 1,
                    startedAtEpochMs = System.currentTimeMillis()
                )
            )
            val schedule = SeasonSimulationEngine.generateRegularSeasonSchedule(
                seasonNumber = settings.currentSeasonNumber,
                teams = orderedTeams
            )
            dao.insertMatchups(schedule)
            dao.insertTransaction(
                TransactionEntity(
                    type = "DRAFT",
                    teamId = USER_TEAM_ID,
                    counterpartyLabel = "Fantasy Draft Room",
                    playerIdsCsv = "",
                    title = "Started ${finalConfig.teamCount}-Team ${if (finalConfig.isSnakeDraft) "Snake" else "Standard"} Draft",
                    details = "${finalConfig.totalRounds} rounds • Draft Slot #${slotIndex + 1} • Treasury reset to ${CadFormatter.formatCompact(budget)}",
                    cashDeltaCad = 0L,
                    balanceAfterCad = budget,
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
        }

        // Sync economy salary cap setting if configured in draft
        settingsStore.updateEconomyConfig(
            settings.economyConfig.copy(
                enforceSalaryCap = finalConfig.enforceSalaryCap,
                salaryCapCad = finalConfig.salaryCapCad
            )
        )

        return Result.success(Unit)
    }

    /**
     * Authoritative Host initializer for a Multiplayer Draft with real invited friends,
     * optional local pass-and-play friends, and optional CPU GMs filling remaining slots.
     */
    suspend fun startMultiplayerDraft(
        config: DraftConfig,
        orderedParticipants: List<LobbyParticipant>,
        fillEmptyWithCpu: Boolean
    ): Result<List<FantasyTeamEntity>> {
        if (orderedParticipants.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least 1 human GM must be in the lobby"))
        }
        val settings = settingsFlow.first()
        val budget = settings.economyConfig.startingBudgetCad

        val humanTeams = orderedParticipants.map { p ->
            val ownerType = when {
                p.isHost -> "HUMAN_GM"
                p.isLocalPassAndPlay -> "LOCAL_HUMAN_GM"
                else -> "REMOTE_HUMAN_GM"
            }
            FantasyTeamEntity(
                teamId = p.assignedTeamId,
                teamName = p.teamName.trim().ifBlank { "${p.gmName}'s HC" }.take(28),
                abbreviation = p.abbreviation.trim().uppercase().take(3).ifBlank { "NHL" },
                emblemId = p.emblemId,
                primaryColorHex = p.primaryColorHex,
                secondaryColorHex = p.secondaryColorHex,
                isHuman = true,
                ownerType = ownerType,
                gmDisplayName = p.gmName,
                remoteClientId = p.clientId,
                cashBalanceCad = budget,
                startingBudgetCad = budget
            )
        }

        val targetTotalTeams = if (fillEmptyWithCpu) {
            maxOf(config.teamCount, humanTeams.size).coerceIn(2, 16)
        } else {
            humanTeams.size.coerceIn(2, 16)
        }

        if (!fillEmptyWithCpu && humanTeams.size < 2) {
            return Result.failure(
                IllegalStateException("Invite at least 1 friend (2+ human GMs) or enable 'Fill Open Slots with CPU GMs'.")
            )
        }

        val cpuNeeded = (targetTotalTeams - humanTeams.size).coerceAtLeast(0)
        val cpuTeams = createDefaultCpuTeams(cpuNeeded, budget)
        val orderedTeams = humanTeams + cpuTeams
        val hostSlot = (orderedTeams.indexOfFirst { it.teamId == USER_TEAM_ID } + 1).coerceAtLeast(1)
        val finalConfig = config.copy(
            teamCount = orderedTeams.size,
            userDraftPosition = hostSlot
        )
        val teamOrderCsv = orderedTeams.joinToString(",") { it.teamId }

        database.withTransaction {
            dao.releaseAllPlayerOwnership()
            dao.deleteAllFantasyTeams()
            dao.upsertFantasyTeams(orderedTeams)
            dao.clearDraftPicks()
            dao.clearTradeProposals()
            dao.clearMatchups()
            dao.upsertDraftSession(
                DraftSessionEntity(
                    sessionId = 1,
                    status = "IN_PROGRESS",
                    configJson = json.encodeToString(finalConfig),
                    teamOrderCsv = teamOrderCsv,
                    currentOverallPick = 1,
                    startedAtEpochMs = System.currentTimeMillis()
                )
            )
            val schedule = SeasonSimulationEngine.generateRegularSeasonSchedule(
                seasonNumber = settings.currentSeasonNumber,
                teams = orderedTeams
            )
            dao.insertMatchups(schedule)
            dao.insertTransaction(
                TransactionEntity(
                    type = "DRAFT",
                    teamId = USER_TEAM_ID,
                    counterpartyLabel = "Multiplayer Draft Room",
                    playerIdsCsv = "",
                    title = "Launched Multiplayer Draft (${humanTeams.size} Human GMs, ${cpuTeams.size} CPU GMs)",
                    details = "${finalConfig.totalRounds} rounds • ${if (finalConfig.isSnakeDraft) "Snake" else "Standard"} order",
                    cashDeltaCad = 0L,
                    balanceAfterCad = budget,
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
        }

        settingsStore.updateEconomyConfig(
            settings.economyConfig.copy(
                enforceSalaryCap = finalConfig.enforceSalaryCap,
                salaryCapCad = finalConfig.salaryCapCad
            )
        )

        return Result.success(orderedTeams)
    }

    fun decodeDraftConfig(session: DraftSessionEntity?): DraftConfig {
        if (session == null || session.configJson.isBlank()) return DraftConfig()
        return runCatching { json.decodeFromString<DraftConfig>(session.configJson) }
            .getOrDefault(DraftConfig())
    }

    suspend fun makeHumanDraftPick(playerId: Long, actingTeamId: String? = null): Result<String> {
        val session = dao.getDraftSessionOnce()
            ?: return Result.failure(IllegalStateException("No active draft session"))
        if (session.status != "IN_PROGRESS") {
            return Result.failure(IllegalStateException("Draft is already completed"))
        }

        val config = decodeDraftConfig(session)
        val teamOrder = session.teamOrderCsv.split(",").filter { it.isNotBlank() }
        val (round, pickInRound, onClockTeamId) = DraftEngine.getPickMetadata(
            session.currentOverallPick,
            teamOrder,
            config.isSnakeDraft
        )

        val onClockTeam = dao.getFantasyTeamById(onClockTeamId)
            ?: return Result.failure(IllegalStateException("On-clock team missing"))

        val controlledId = getActiveControlledTeamId()
        val permittedTeamId = when {
            actingTeamId != null -> actingTeamId
            onClockTeamId == controlledId -> controlledId
            onClockTeam.ownerType == "LOCAL_HUMAN_GM" && controlledId == USER_TEAM_ID -> onClockTeamId
            else -> null
        }

        if (permittedTeamId == null || permittedTeamId != onClockTeamId) {
            val whoLabel = when (onClockTeam.ownerType) {
                "REMOTE_HUMAN_GM" -> "${onClockTeam.teamName} (${onClockTeam.gmDisplayName.ifBlank { "Online Friend" }})"
                "HUMAN_GM" -> "${onClockTeam.teamName} (Host)"
                else -> "CPU GM (${onClockTeam.teamName})"
            }
            return Result.failure(IllegalStateException("Waiting on $whoLabel — it is not your turn on the clock."))
        }

        val currentRoster = dao.getTeamRosterOnce(onClockTeam.teamId)
        val player = dao.getPlayerById(playerId)
            ?: return Result.failure(IllegalArgumentException("Player not found"))

        val err = DraftEngine.validateDraftPick(onClockTeam, currentRoster, player, config)
        if (err != null) {
            return Result.failure(IllegalStateException(err))
        }

        val gmTag = onClockTeam.gmDisplayName.takeIf { it.isNotBlank() }?.let { "GM $it" } ?: "GM"
        executeSingleDraftPickAtomically(
            session = session,
            config = config,
            round = round,
            pickInRound = pickInRound,
            team = onClockTeam,
            currentRoster = currentRoster,
            player = player,
            rationale = "$gmTag selection (Rd $round, Pick #$pickInRound)"
        )
        return Result.success("${onClockTeam.abbreviation} drafted ${player.fullName} (${player.position.uiShort}, ${player.overallRating} OVR)!")
    }

    suspend fun autoPickForHuman(actingTeamId: String? = null): Result<String> {
        val session = dao.getDraftSessionOnce()
            ?: return Result.failure(IllegalStateException("No active draft"))
        val config = decodeDraftConfig(session)
        val teamOrder = session.teamOrderCsv.split(",").filter { it.isNotBlank() }
        val (_, _, onClockTeamId) = DraftEngine.getPickMetadata(
            session.currentOverallPick,
            teamOrder,
            config.isSnakeDraft
        )
        val onClockTeam = dao.getFantasyTeamById(onClockTeamId)
            ?: return Result.failure(IllegalStateException("On-clock team missing"))

        val controlledId = getActiveControlledTeamId()
        val targetTeamId = actingTeamId ?: when {
            onClockTeamId == controlledId -> controlledId
            onClockTeam.ownerType == "LOCAL_HUMAN_GM" && controlledId == USER_TEAM_ID -> onClockTeamId
            else -> controlledId
        }

        val settings = settingsFlow.first()
        val team = dao.getFantasyTeamById(targetTeamId)
            ?: return Result.failure(IllegalStateException("Team missing"))
        val roster = dao.getTeamRosterOnce(targetTeamId)
        val available = dao.getAllPlayersOnce().filter { it.ownerFantasyTeamId == null }

        val choice = DraftEngine.selectBestCpuPick(team, roster, available, config, settings.scoringWeights)
            ?: return Result.failure(IllegalStateException("No valid player available for auto-pick"))

        return makeHumanDraftPick(choice.player.playerId, actingTeamId = targetTeamId)
    }

    /**
     * Advances ONLY `CPU_GM` picks automatically, stopping immediately whenever any human GM
     * (`HUMAN_GM`, `REMOTE_HUMAN_GM`, or `LOCAL_HUMAN_GM`) is on the clock or the draft finishes.
     */
    suspend fun advanceCpuDraftPicks(maxSteps: Int = 32): Result<Int> {
        var picksMade = 0
        val settings = settingsFlow.first()

        while (picksMade < maxSteps) {
            val session = dao.getDraftSessionOnce() ?: break
            if (session.status != "IN_PROGRESS") break
            val config = decodeDraftConfig(session)
            if (session.currentOverallPick > config.totalPicks) {
                dao.upsertDraftSession(session.copy(status = "COMPLETED", completedAtEpochMs = System.currentTimeMillis()))
                break
            }

            val teamOrder = session.teamOrderCsv.split(",").filter { it.isNotBlank() }
            val (round, pickInRound, onClockTeamId) = DraftEngine.getPickMetadata(
                session.currentOverallPick,
                teamOrder,
                config.isSnakeDraft
            )

            val onClockTeam = dao.getFantasyTeamById(onClockTeamId) ?: break
            if (!onClockTeam.isCpuGm) {
                break // A real human GM (Host, Online Invited Friend, or Local Friend) is on the clock!
            }

            val cpuRoster = dao.getTeamRosterOnce(onClockTeamId)
            val availablePool = dao.getAllPlayersOnce().filter { it.ownerFantasyTeamId == null }

            val choice = DraftEngine.selectBestCpuPick(
                cpuTeam = onClockTeam,
                cpuCurrentRoster = cpuRoster,
                availablePool = availablePool,
                config = config,
                scoringWeights = settings.scoringWeights
            ) ?: break

            executeSingleDraftPickAtomically(
                session = session,
                config = config,
                round = round,
                pickInRound = pickInRound,
                team = onClockTeam,
                currentRoster = cpuRoster,
                player = choice.player,
                rationale = choice.rationale
            )
            picksMade++
        }
        return Result.success(picksMade)
    }

    private suspend fun executeSingleDraftPickAtomically(
        session: DraftSessionEntity,
        config: DraftConfig,
        round: Int,
        pickInRound: Int,
        team: FantasyTeamEntity,
        currentRoster: List<PlayerEntity>,
        player: PlayerEntity,
        rationale: String
    ) {
        val nextOverall = session.currentOverallPick + 1
        val isNowComplete = nextOverall > config.totalPicks
        val now = System.currentTimeMillis()
        val newCash = (team.cashBalanceCad - player.purchasePriceCad).coerceAtLeast(0L)

        val updatedRoster = LineupAnalyticsEngine.generateRecommendedLineup(
            currentRoster + player.copy(
                ownerFantasyTeamId = team.teamId,
                acquisitionPriceCad = player.purchasePriceCad
            )
        )

        database.withTransaction {
            dao.upsertFantasyTeam(team.copy(cashBalanceCad = newCash))
            dao.updatePlayers(updatedRoster)
            dao.insertDraftPick(
                DraftPickEntity(
                    overallPick = session.currentOverallPick,
                    roundNumber = round,
                    pickInRound = pickInRound,
                    fantasyTeamId = team.teamId,
                    fantasyTeamName = team.teamName,
                    isHumanPick = team.isHuman,
                    playerId = player.playerId,
                    playerName = player.fullName,
                    positionCode = player.positionCode,
                    nhlTeamAbbrev = player.teamAbbrev,
                    overallRating = player.overallRating,
                    capHitCad = player.capHitCad,
                    pickCostCad = player.purchasePriceCad,
                    rationale = rationale,
                    pickedAtEpochMs = now
                )
            )
            dao.upsertDraftSession(
                session.copy(
                    currentOverallPick = nextOverall,
                    status = if (isNowComplete) "COMPLETED" else "IN_PROGRESS",
                    completedAtEpochMs = if (isNowComplete) now else 0L
                )
            )
            if (team.isHuman) {
                dao.insertTransaction(
                    TransactionEntity(
                        type = "DRAFT",
                        teamId = team.teamId,
                        counterpartyLabel = "Round $round • Pick #${session.currentOverallPick}",
                        playerIdsCsv = player.playerId.toString(),
                        title = "Drafted ${player.fullName} (${player.position.uiShort})",
                        details = "${team.teamName} • ${player.teamAbbrev} • ${player.overallRating} OVR • Cap Hit ${CadFormatter.formatCompact(player.capHitCad)}",
                        cashDeltaCad = -player.purchasePriceCad,
                        balanceAfterCad = newCash,
                        timestampEpochMs = now
                    )
                )
            }
        }
    }

    /**
     * Undoes the user's most recent local draft pick and any subsequent CPU picks cleanly.
     */
    suspend fun undoLastHumanDraftPick(): Result<String> {
        val allTeams = dao.getFantasyTeamsOnce()
        if (allTeams.any { it.isRemoteHumanGm }) {
            return Result.failure(IllegalStateException("Undo is disabled in live multi-device online drafts."))
        }
        val session = dao.getDraftSessionOnce()
            ?: return Result.failure(IllegalStateException("No draft session to undo"))
        val picks = dao.getDraftPicksOnce()
        val targetOverall = DraftEngine.findUndoTargetOverallPick(picks)
            ?: return Result.failure(IllegalStateException("No user draft pick to undo yet"))

        val picksToRollback = picks.filter { it.overallPick >= targetOverall }
        val teamsById = allTeams.associateBy { it.teamId }.toMutableMap()
        val playersToRelease = mutableListOf<PlayerEntity>()

        for (pick in picksToRollback) {
            val team = teamsById[pick.fantasyTeamId]
            if (team != null) {
                teamsById[pick.fantasyTeamId] = team.copy(
                    cashBalanceCad = team.cashBalanceCad + pick.pickCostCad
                )
            }
            val p = dao.getPlayerById(pick.playerId)
            if (p != null) {
                playersToRelease.add(
                    p.copy(
                        ownerFantasyTeamId = null,
                        acquisitionPriceCad = 0L,
                        lineupSlotCode = LineupSlot.UNASSIGNED.code
                    )
                )
            }
        }

        val undoneHumanPick = picksToRollback.first()

        database.withTransaction {
            dao.upsertFantasyTeams(teamsById.values.toList())
            dao.updatePlayers(playersToRelease)
            dao.deleteDraftPicksFrom(targetOverall)
            dao.upsertDraftSession(
                session.copy(
                    status = "IN_PROGRESS",
                    currentOverallPick = targetOverall,
                    completedAtEpochMs = 0L
                )
            )
            // Re-optimize human lineup for remaining players
            val userRemaining = dao.getTeamRosterOnce(undoneHumanPick.fantasyTeamId)
            if (userRemaining.isNotEmpty()) {
                dao.updatePlayers(LineupAnalyticsEngine.generateRecommendedLineup(userRemaining))
            }
            dao.insertTransaction(
                TransactionEntity(
                    type = "DRAFT",
                    teamId = undoneHumanPick.fantasyTeamId,
                    counterpartyLabel = "Draft Undo",
                    playerIdsCsv = undoneHumanPick.playerId.toString(),
                    title = "Undid Pick #$targetOverall (${undoneHumanPick.playerName})",
                    details = "Refunded ${CadFormatter.formatCompact(undoneHumanPick.pickCostCad)} and rolled back ${picksToRollback.size} pick(s).",
                    cashDeltaCad = undoneHumanPick.pickCostCad,
                    balanceAfterCad = teamsById[undoneHumanPick.fantasyTeamId]?.cashBalanceCad ?: 0L,
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
        }

        return Result.success("Rolled back Pick #$targetOverall (${undoneHumanPick.playerName}). Back on the clock!")
    }

    // --- My Team & Lineup Management ---

    suspend fun customizeUserFranchise(
        teamName: String,
        abbreviation: String,
        emblem: TeamEmblem,
        primaryColorHex: Long,
        secondaryColorHex: Long,
        actingTeamId: String? = null
    ): Result<Unit> {
        val teamId = actingTeamId ?: getActiveControlledTeamId()
        val cleanName = teamName.trim().ifBlank { "Northern Ice HC" }.take(28)
        val cleanAbbrev = abbreviation.trim().uppercase().filter { it.isLetterOrDigit() }.ifBlank { "NIC" }.take(3)
        val userTeam = dao.getFantasyTeamById(teamId) ?: return Result.failure(IllegalStateException("Team missing"))
        dao.upsertFantasyTeam(
            userTeam.copy(
                teamName = cleanName,
                abbreviation = cleanAbbrev,
                emblemId = emblem.id,
                primaryColorHex = primaryColorHex,
                secondaryColorHex = secondaryColorHex
            )
        )
        return Result.success(Unit)
    }

    suspend fun applyRecommendedUserLineup(actingTeamId: String? = null): Result<String> {
        val teamId = actingTeamId ?: getActiveControlledTeamId()
        val roster = dao.getTeamRosterOnce(teamId)
        if (roster.isEmpty()) {
            return Result.failure(IllegalStateException("Your roster is empty — buy or draft players first."))
        }
        val optimized = LineupAnalyticsEngine.generateRecommendedLineup(roster)
        dao.updatePlayers(optimized)
        return Result.success("Optimal starting lineup set across Forward Lines, Defense Pairings, and Goalies!")
    }

    suspend fun assignPlayerToSlot(
        playerId: Long,
        targetSlot: LineupSlot,
        actingTeamId: String? = null
    ): Result<String> {
        val teamId = actingTeamId ?: getActiveControlledTeamId()
        val roster = dao.getTeamRosterOnce(teamId)
        val player = roster.firstOrNull { it.playerId == playerId }
            ?: return Result.failure(IllegalArgumentException("Player is not on your roster"))
        val occupant = if (targetSlot != LineupSlot.UNASSIGNED) {
            roster.firstOrNull { it.lineupSlot == targetSlot && it.playerId != playerId }
        } else null

        val err = LineupAnalyticsEngine.validateSlotAssignment(player, targetSlot, occupant)
        if (err != null) {
            return Result.failure(IllegalStateException(err))
        }

        val updates = mutableListOf<PlayerEntity>()
        updates.add(player.copy(lineupSlotCode = targetSlot.code))
        if (occupant != null) {
            updates.add(occupant.copy(lineupSlotCode = player.lineupSlotCode))
        }
        dao.updatePlayers(updates)
        return Result.success(
            if (occupant != null) "Swapped ${player.lastName} and ${occupant.lastName} (${targetSlot.code})."
            else "Assigned ${player.fullName} to ${targetSlot.label}."
        )
    }

    suspend fun applyRemoteTeamLineupSlots(
        teamId: String,
        slots: List<LineupSlotSyncEntry>
    ): Result<Unit> {
        val roster = dao.getTeamRosterOnce(teamId)
        if (roster.isEmpty()) return Result.success(Unit)
        val slotMap = slots.associate { it.playerId to LineupSlot.fromCode(it.lineupSlotCode).code }
        val updated = roster.map { p ->
            val newCode = slotMap[p.playerId]
            if (newCode != null) p.copy(lineupSlotCode = newCode) else p
        }
        dao.updatePlayers(updated)
        return Result.success(Unit)
    }

    // --- Player Trading System (CPU GMs & Real Multiplayer Friends) ---

    /**
     * Populates CPU teams with balanced free-agent rosters if the user chose Marketplace play without running a Draft,
     * leaving all user-owned, watchlisted, and plenty of top free agents untouched.
     */
    suspend fun ensureCpuTeamsHaveRostersIfNeeded() {
        val allTeams = dao.getFantasyTeamsOnce()
        val cpuTeams = allTeams.filter { it.isCpuGm }
        if (cpuTeams.isEmpty()) return

        val allPlayers = dao.getAllPlayersOnce()
        val anyTeamDrafted = (dao.getDraftPicksOnce().isNotEmpty()) ||
            cpuTeams.any { cpu -> allPlayers.count { it.ownerFantasyTeamId == cpu.teamId } >= 6 }
        if (anyTeamDrafted) return

        val settings = settingsFlow.first()
        val config = DraftConfig(
            teamCount = allTeams.size,
            enforceSalaryCap = settings.economyConfig.enforceSalaryCap,
            salaryCapCad = settings.economyConfig.salaryCapCad
        )

        val unowned = allPlayers
            .filter { it.ownerFantasyTeamId == null && !it.isWatchlisted }
            .sortedByDescending { it.overallRating }

        if (unowned.size < cpuTeams.size * 16) return
        val pool = unowned.drop(15).toMutableList()
        val updatedTeams = mutableListOf<FantasyTeamEntity>()
        val updatedPlayers = mutableListOf<PlayerEntity>()

        for (cpu in cpuTeams) {
            var workingCpu = cpu
            val cpuRoster = mutableListOf<PlayerEntity>()
            repeat(18) {
                val choice = DraftEngine.selectBestCpuPick(
                    cpuTeam = workingCpu,
                    cpuCurrentRoster = cpuRoster,
                    availablePool = pool,
                    config = config,
                    scoringWeights = settings.scoringWeights
                ) ?: return@repeat
                pool.removeAll { it.playerId == choice.player.playerId }
                val signed = choice.player.copy(
                    ownerFantasyTeamId = workingCpu.teamId,
                    acquisitionPriceCad = choice.player.purchasePriceCad
                )
                cpuRoster.add(signed)
                workingCpu = workingCpu.copy(
                    cashBalanceCad = (workingCpu.cashBalanceCad - signed.purchasePriceCad).coerceAtLeast(0L)
                )
            }
            updatedTeams.add(workingCpu)
            updatedPlayers.addAll(LineupAnalyticsEngine.generateRecommendedLineup(cpuRoster))
        }

        database.withTransaction {
            dao.upsertFantasyTeams(updatedTeams)
            dao.updatePlayers(updatedPlayers)
        }
    }

    suspend fun proposeTradeToCpu(
        cpuTeamId: String,
        offeredPlayerIds: List<Long>,
        offeredCashCad: Long,
        requestedPlayerIds: List<Long>,
        requestedCashCad: Long
    ): Result<TradeEvaluationVerdict> = proposeTradeToTeam(
        targetTeamId = cpuTeamId,
        offeredPlayerIds = offeredPlayerIds,
        offeredCashCad = offeredCashCad,
        requestedPlayerIds = requestedPlayerIds,
        requestedCashCad = requestedCashCad
    )

    /**
     * Proposes a trade to either a CPU GM (evaluated immediately by AI) OR a real human friend
     * in a multiplayer league (saved & broadcast as `PENDING` so the friend can Accept or Reject on their device!).
     */
    suspend fun proposeTradeToTeam(
        targetTeamId: String,
        offeredPlayerIds: List<Long>,
        offeredCashCad: Long,
        requestedPlayerIds: List<Long>,
        requestedCashCad: Long,
        proposerTeamIdOverride: String? = null
    ): Result<TradeEvaluationVerdict> {
        ensureCpuTeamsHaveRostersIfNeeded()
        val settings = settingsFlow.first()
        val proposerId = proposerTeamIdOverride ?: getActiveControlledTeamId()
        if (proposerId == targetTeamId) {
            return Result.failure(IllegalArgumentException("Cannot propose a trade to your own team"))
        }

        val proposerTeam = dao.getFantasyTeamById(proposerId)
            ?: return Result.failure(IllegalStateException("Proposing team missing"))
        val targetTeam = dao.getFantasyTeamById(targetTeamId)
            ?: return Result.failure(IllegalArgumentException("Target team not found"))

        val proposerRoster = dao.getTeamRosterOnce(proposerId)
        val targetRoster = dao.getTeamRosterOnce(targetTeamId)

        val offeredPlayers = proposerRoster.filter { it.playerId in offeredPlayerIds }
        val requestedPlayers = targetRoster.filter { it.playerId in requestedPlayerIds }

        val verdict = TradeEvaluationEngine.evaluateTradeForCpu(
            proposerTeam = proposerTeam,
            proposerRoster = proposerRoster,
            receiverCpuTeam = targetTeam,
            receiverRoster = targetRoster,
            offeredPlayers = offeredPlayers,
            offeredCashCad = offeredCashCad,
            requestedPlayers = requestedPlayers,
            requestedCashCad = requestedCashCad,
            economyConfig = settings.economyConfig,
            scoringWeights = settings.scoringWeights
        )

        if (!verdict.isRuleValid) {
            return Result.failure(IllegalStateException(verdict.gmReasoning))
        }

        val now = System.currentTimeMillis()
        val offeredSummary = offeredPlayers.joinToString(", ") { "${it.fullName} (${it.overallRating})" }.ifBlank { "Cash Only" }
        val requestedSummary = requestedPlayers.joinToString(", ") { "${it.fullName} (${it.overallRating})" }.ifBlank { "Cash Only" }

        if (!targetTeam.isCpuGm) {
            val friendLabel = targetTeam.gmDisplayName.ifBlank { targetTeam.teamName }
            val humanPitch = "${proposerTeam.teamName} offers $offeredSummary" +
                (if (offeredCashCad > 0) " + ${CadFormatter.formatCompact(offeredCashCad)}" else "") +
                " for $requestedSummary" +
                (if (requestedCashCad > 0) " + ${CadFormatter.formatCompact(requestedCashCad)}" else "") +
                " — Pending response from $friendLabel."

            val pendingProposal = TradeProposalEntity(
                proposerTeamId = proposerTeam.teamId,
                proposerTeamName = proposerTeam.teamName,
                receiverTeamId = targetTeam.teamId,
                receiverTeamName = targetTeam.teamName,
                offeredPlayerIdsCsv = offeredPlayers.joinToString(",") { it.playerId.toString() },
                offeredPlayersSummary = offeredSummary,
                offeredCashCad = offeredCashCad,
                requestedPlayerIdsCsv = requestedPlayers.joinToString(",") { it.playerId.toString() },
                requestedPlayersSummary = requestedSummary,
                requestedCashCad = requestedCashCad,
                status = "PENDING",
                fairnessScore = verdict.fairnessScore,
                gmEvaluationReason = humanPitch,
                createdAtEpochMs = now
            )
            dao.insertTradeProposal(pendingProposal)
            return Result.success(
                verdict.copy(
                    isRuleValid = true,
                    acceptedByCpu = false,
                    gmReasoning = "Live trade offer sent to ${targetTeam.teamName} ($friendLabel)!"
                )
            )
        }

        val proposal = TradeProposalEntity(
            proposerTeamId = proposerTeam.teamId,
            proposerTeamName = proposerTeam.teamName,
            receiverTeamId = targetTeam.teamId,
            receiverTeamName = targetTeam.teamName,
            offeredPlayerIdsCsv = offeredPlayers.joinToString(",") { it.playerId.toString() },
            offeredPlayersSummary = offeredSummary,
            offeredCashCad = offeredCashCad,
            requestedPlayerIdsCsv = requestedPlayers.joinToString(",") { it.playerId.toString() },
            requestedPlayersSummary = requestedSummary,
            requestedCashCad = requestedCashCad,
            status = if (verdict.acceptedByCpu) "ACCEPTED" else "REJECTED",
            fairnessScore = verdict.fairnessScore,
            gmEvaluationReason = verdict.gmReasoning,
            createdAtEpochMs = now,
            resolvedAtEpochMs = now
        )

        if (verdict.acceptedByCpu) {
            executeAcceptedTradeAtomically(
                proposal = proposal,
                teamA = proposerTeam,
                teamARoster = proposerRoster,
                teamAOutgoing = offeredPlayers,
                teamACashOut = offeredCashCad,
                teamB = targetTeam,
                teamBRoster = targetRoster,
                teamBOutgoing = requestedPlayers,
                teamBCashOut = requestedCashCad
            )
        } else {
            dao.insertTradeProposal(proposal)
        }

        return Result.success(verdict)
    }

    suspend fun solicitIncomingCpuOffer(): Result<TradeProposalEntity> {
        ensureCpuTeamsHaveRostersIfNeeded()
        val settings = settingsFlow.first()
        val controlledId = getActiveControlledTeamId()
        val allTeams = dao.getFantasyTeamsOnce()
        val userTeam = allTeams.firstOrNull { it.teamId == controlledId }
            ?: return Result.failure(IllegalStateException("User team missing"))
        val cpuTeams = allTeams.filter { it.isCpuGm }
        if (cpuTeams.isEmpty()) {
            return Result.failure(IllegalStateException("This league has only human GMs — propose a trade directly to a friend!"))
        }
        val allPlayers = dao.getAllPlayersOnce()
        val userRoster = allPlayers.filter { it.ownerFantasyTeamId == controlledId }
        if (userRoster.size < 3) {
            return Result.failure(IllegalStateException("Acquire at least 3 players on your roster to solicit CPU trade offers."))
        }

        val rostersByTeam = cpuTeams.associate { cpu ->
            cpu.teamId to allPlayers.filter { it.ownerFantasyTeamId == cpu.teamId }
        }

        val generated = TradeEvaluationEngine.generateIncomingCpuProposal(
            userTeam = userTeam,
            userRoster = userRoster,
            cpuTeams = cpuTeams,
            rostersByTeamId = rostersByTeam,
            economyConfig = settings.economyConfig
        ) ?: return Result.failure(IllegalStateException("No cap-compliant trade proposals found right now."))

        val entity = TradeProposalEntity(
            proposerTeamId = generated.cpuTeam.teamId,
            proposerTeamName = generated.cpuTeam.teamName,
            receiverTeamId = controlledId,
            receiverTeamName = userTeam.teamName,
            offeredPlayerIdsCsv = generated.cpuOfferedPlayers.joinToString(",") { it.playerId.toString() },
            offeredPlayersSummary = generated.cpuOfferedPlayers.joinToString(", ") { "${it.fullName} (${it.overallRating} OVR)" },
            offeredCashCad = generated.cpuOfferedCashCad,
            requestedPlayerIdsCsv = generated.userRequestedPlayers.joinToString(",") { it.playerId.toString() },
            requestedPlayersSummary = generated.userRequestedPlayers.joinToString(", ") { "${it.fullName} (${it.overallRating} OVR)" },
            requestedCashCad = generated.userRequestedCashCad,
            status = "PENDING",
            fairnessScore = generated.fairnessScore,
            gmEvaluationReason = generated.pitchMessage,
            createdAtEpochMs = System.currentTimeMillis()
        )
        val id = dao.insertTradeProposal(entity)
        return Result.success(entity.copy(tradeId = id))
    }

    suspend fun respondToIncomingProposal(
        tradeId: Long,
        accept: Boolean,
        responderTeamIdOverride: String? = null
    ): Result<String> {
        val proposal = dao.getTradeProposalById(tradeId)
            ?: return Result.failure(IllegalArgumentException("Trade proposal not found"))
        if (proposal.status != "PENDING") {
            return Result.failure(IllegalStateException("This proposal was already ${proposal.status.lowercase()}."))
        }

        val expectedResponderId = responderTeamIdOverride ?: getActiveControlledTeamId()
        val receiverTeam = dao.getFantasyTeamById(proposal.receiverTeamId)
            ?: return Result.failure(IllegalStateException("Receiving team no longer exists"))
        val canRespond = proposal.receiverTeamId == expectedResponderId ||
            (receiverTeam.isLocalPassAndPlayGm && expectedResponderId == USER_TEAM_ID)
        if (!canRespond) {
            return Result.failure(IllegalStateException("Only ${receiverTeam.teamName} can respond to this trade offer."))
        }

        val now = System.currentTimeMillis()
        if (!accept) {
            dao.updateTradeProposal(
                proposal.copy(
                    status = "REJECTED",
                    gmEvaluationReason = "Declined by ${receiverTeam.teamName}.",
                    resolvedAtEpochMs = now
                )
            )
            return Result.success("Declined trade offer from ${proposal.proposerTeamName}.")
        }

        val settings = settingsFlow.first()
        val proposerTeam = dao.getFantasyTeamById(proposal.proposerTeamId)
            ?: return Result.failure(IllegalStateException("Proposing team no longer exists"))

        val proposerRoster = dao.getTeamRosterOnce(proposerTeam.teamId)
        val receiverRoster = dao.getTeamRosterOnce(receiverTeam.teamId)

        val offeredIds = proposal.offeredPlayerIdsCsv.split(",").mapNotNull { it.trim().toLongOrNull() }
        val reqIds = proposal.requestedPlayerIdsCsv.split(",").mapNotNull { it.trim().toLongOrNull() }

        val proposerOutgoing = proposerRoster.filter { it.playerId in offeredIds }
        val receiverOutgoing = receiverRoster.filter { it.playerId in reqIds }

        if (proposerOutgoing.size != offeredIds.size || receiverOutgoing.size != reqIds.size) {
            dao.updateTradeProposal(proposal.copy(status = "REJECTED", gmEvaluationReason = "Voided: involved players changed ownership.", resolvedAtEpochMs = now))
            return Result.failure(IllegalStateException("One or more players in this proposal are no longer owned by their original team."))
        }

        val receiverCashAfter = receiverTeam.cashBalanceCad + proposal.offeredCashCad - proposal.requestedCashCad
        val proposerCashAfter = proposerTeam.cashBalanceCad - proposal.offeredCashCad + proposal.requestedCashCad
        if (receiverCashAfter < 0L || proposerCashAfter < 0L) {
            return Result.failure(IllegalStateException("Trade cannot complete: insufficient virtual CAD balance."))
        }

        if (settings.economyConfig.enforceSalaryCap) {
            val receiverCapAfter = receiverRoster.sumOf { it.capHitCad } - receiverOutgoing.sumOf { it.capHitCad } + proposerOutgoing.sumOf { it.capHitCad }
            val proposerCapAfter = proposerRoster.sumOf { it.capHitCad } - proposerOutgoing.sumOf { it.capHitCad } + receiverOutgoing.sumOf { it.capHitCad }
            if (receiverCapAfter > settings.economyConfig.salaryCapCad || proposerCapAfter > settings.economyConfig.salaryCapCad) {
                return Result.failure(IllegalStateException("Accepting this trade would exceed the Salary Cap."))
            }
        }

        executeAcceptedTradeAtomically(
            proposal = proposal.copy(
                status = "ACCEPTED",
                gmEvaluationReason = "Accepted by ${receiverTeam.teamName}! Trade completed.",
                resolvedAtEpochMs = now
            ),
            teamA = proposerTeam,
            teamARoster = proposerRoster,
            teamAOutgoing = proposerOutgoing,
            teamACashOut = proposal.offeredCashCad,
            teamB = receiverTeam,
            teamBRoster = receiverRoster,
            teamBOutgoing = receiverOutgoing,
            teamBCashOut = proposal.requestedCashCad,
            isExistingProposal = true
        )

        return Result.success("Trade completed between ${proposerTeam.teamName} and ${receiverTeam.teamName}!")
    }

    private suspend fun executeAcceptedTradeAtomically(
        proposal: TradeProposalEntity,
        teamA: FantasyTeamEntity,
        teamARoster: List<PlayerEntity>,
        teamAOutgoing: List<PlayerEntity>,
        teamACashOut: Long,
        teamB: FantasyTeamEntity,
        teamBRoster: List<PlayerEntity>,
        teamBOutgoing: List<PlayerEntity>,
        teamBCashOut: Long,
        isExistingProposal: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val teamANewCash = teamA.cashBalanceCad - teamACashOut + teamBCashOut
        val teamBNewCash = teamB.cashBalanceCad - teamBCashOut + teamACashOut

        val teamANewRoster = LineupAnalyticsEngine.generateRecommendedLineup(
            teamARoster.filterNot { p -> teamAOutgoing.any { it.playerId == p.playerId } } +
                teamBOutgoing.map { it.copy(ownerFantasyTeamId = teamA.teamId, acquisitionPriceCad = it.purchasePriceCad) }
        )

        val teamBNewRoster = LineupAnalyticsEngine.generateRecommendedLineup(
            teamBRoster.filterNot { p -> teamBOutgoing.any { it.playerId == p.playerId } } +
                teamAOutgoing.map { it.copy(ownerFantasyTeamId = teamB.teamId, acquisitionPriceCad = it.purchasePriceCad) }
        )

        database.withTransaction {
            dao.upsertFantasyTeams(
                listOf(
                    teamA.copy(cashBalanceCad = teamANewCash),
                    teamB.copy(cashBalanceCad = teamBNewCash)
                )
            )
            dao.updatePlayers(teamANewRoster + teamBNewRoster)
            if (isExistingProposal) {
                dao.updateTradeProposal(proposal)
            } else {
                dao.insertTradeProposal(proposal)
            }
            if (teamA.isHuman) {
                dao.insertTransaction(
                    TransactionEntity(
                        type = "TRADE",
                        teamId = teamA.teamId,
                        counterpartyLabel = teamB.teamName,
                        playerIdsCsv = (teamBOutgoing + teamAOutgoing).joinToString(",") { it.playerId.toString() },
                        title = "Trade with ${teamB.teamName}",
                        details = "Acquired: ${teamBOutgoing.joinToString { it.lastName }.ifBlank { "Cash" }} • Sent: ${teamAOutgoing.joinToString { it.lastName }.ifBlank { "Cash" }}",
                        cashDeltaCad = teamBCashOut - teamACashOut,
                        balanceAfterCad = teamANewCash,
                        timestampEpochMs = now
                    )
                )
            }
            if (teamB.isHuman) {
                dao.insertTransaction(
                    TransactionEntity(
                        type = "TRADE",
                        teamId = teamB.teamId,
                        counterpartyLabel = teamA.teamName,
                        playerIdsCsv = (teamAOutgoing + teamBOutgoing).joinToString(",") { it.playerId.toString() },
                        title = "Trade with ${teamA.teamName}",
                        details = "Acquired: ${teamAOutgoing.joinToString { it.lastName }.ifBlank { "Cash" }} • Sent: ${teamBOutgoing.joinToString { it.lastName }.ifBlank { "Cash" }}",
                        cashDeltaCad = teamACashOut - teamBCashOut,
                        balanceAfterCad = teamBNewCash,
                        timestampEpochMs = now
                    )
                )
            }
        }
    }

    // --- Fantasy Season Simulation ---

    suspend fun simulateNextSeasonWeek(): Result<String> {
        ensureCpuTeamsHaveRostersIfNeeded()
        val settings = settingsFlow.first()
        val controlledId = settings.controlledTeamId.ifBlank { USER_TEAM_ID }
        val syncStatus = dao.getSyncStatusOnce()
        val matchups = dao.getMatchupsOnce()
        val nextWeek = matchups.filter { !it.isCompleted }.minOfOrNull { it.weekNumber }
            ?: return Result.failure(IllegalStateException("Current fantasy season is complete! Start Next Season to continue."))

        val weekMatchups = matchups.filter { it.weekNumber == nextWeek && !it.isCompleted }
        val teamsById = dao.getFantasyTeamsOnce().associateBy { it.teamId }.toMutableMap()
        val allPlayers = dao.getAllPlayersOnce()

        val hasRealData = (syncStatus?.syncedPlayerCount ?: 0) > 0 && allPlayers.any { it.gamesPlayed > 0 || it.prevGamesPlayed > 0 }
        if (!hasRealData && !settings.allowOfflineSeasonSim) {
            return Result.failure(IllegalStateException("Live NHL statistics unavailable and Offline Simulation Mode is disabled in Settings."))
        }
        val forceOffline = !hasRealData

        val now = System.currentTimeMillis()
        val completedWeekMatchups = mutableListOf<FantasyMatchupEntity>()
        val humanRewardTransactions = mutableListOf<TransactionEntity>()
        var userVictoryBonusNote = ""

        for (m in weekMatchups) {
            val homeRoster = allPlayers.filter { it.ownerFantasyTeamId == m.homeTeamId }
            val awayRoster = allPlayers.filter { it.ownerFantasyTeamId == m.awayTeamId }

            val homeRes = SeasonSimulationEngine.calculateTeamWeeklyMatchupScore(
                teamId = m.homeTeamId,
                weekNumber = nextWeek,
                roster = homeRoster,
                weights = settings.scoringWeights,
                forceOfflineSim = forceOffline
            )
            val awayRes = SeasonSimulationEngine.calculateTeamWeeklyMatchupScore(
                teamId = m.awayTeamId,
                weekNumber = nextWeek,
                roster = awayRoster,
                weights = settings.scoringWeights,
                forceOfflineSim = forceOffline
            )

            // Playoff games cannot end in a tie: higher regular-season pointsFor breaks exact tie by 0.1
            val finalHomeScore = if (m.stage != "REGULAR_SEASON" && homeRes.score == awayRes.score) {
                homeRes.score + 0.1
            } else homeRes.score
            val finalAwayScore = awayRes.score

            val completed = m.copy(
                homeScore = finalHomeScore,
                awayScore = finalAwayScore,
                isCompleted = true,
                scoringModeLabel = if (homeRes.usedRealNhlStats || awayRes.usedRealNhlStats) "REAL_NHL_STATS" else "OFFLINE_SIM",
                highlightSummary = "${m.homeTeamName}: ${homeRes.topPerformerSummary} • ${m.awayTeamName}: ${awayRes.topPerformerSummary}",
                completedAtEpochMs = now
            )
            completedWeekMatchups.add(completed)

            // Update regular-season standings records
            if (m.stage == "REGULAR_SEASON") {
                val homeTeam = teamsById[m.homeTeamId]
                val awayTeam = teamsById[m.awayTeamId]
                if (homeTeam != null && awayTeam != null) {
                    val homeWon = finalHomeScore > finalAwayScore
                    val awayWon = finalAwayScore > finalHomeScore
                    val tied = !homeWon && !awayWon

                    teamsById[m.homeTeamId] = homeTeam.copy(
                        wins = homeTeam.wins + if (homeWon) 1 else 0,
                        losses = homeTeam.losses + if (awayWon) 1 else 0,
                        ties = homeTeam.ties + if (tied) 1 else 0,
                        pointsFor = homeTeam.pointsFor + finalHomeScore,
                        pointsAgainst = homeTeam.pointsAgainst + finalAwayScore
                    )
                    teamsById[m.awayTeamId] = awayTeam.copy(
                        wins = awayTeam.wins + if (awayWon) 1 else 0,
                        losses = awayTeam.losses + if (homeWon) 1 else 0,
                        ties = awayTeam.ties + if (tied) 1 else 0,
                        pointsFor = awayTeam.pointsFor + finalAwayScore,
                        pointsAgainst = awayTeam.pointsAgainst + finalHomeScore
                    )
                }
            }

            // Award virtual CAD matchup victory bonus to any winning human GM in the league
            val winnerTeamId = when {
                finalHomeScore > finalAwayScore -> m.homeTeamId
                finalAwayScore > finalHomeScore -> m.awayTeamId
                else -> null
            }
            if (winnerTeamId != null) {
                val winningTeam = teamsById[winnerTeamId]
                if (winningTeam != null && winningTeam.isHuman) {
                    val isTitle = m.stage == "CHAMPIONSHIP_FINAL"
                    val bonusCad = if (isTitle) 2_500_000L else 250_000L
                    val updatedWinner = winningTeam.copy(
                        cashBalanceCad = winningTeam.cashBalanceCad + bonusCad,
                        championshipsWon = winningTeam.championshipsWon + if (isTitle) 1 else 0
                    )
                    teamsById[winnerTeamId] = updatedWinner
                    if (winnerTeamId == controlledId) {
                        userVictoryBonusNote = " (+${CadFormatter.formatCompact(bonusCad)} virtual victory bonus)"
                    }
                    humanRewardTransactions.add(
                        TransactionEntity(
                            type = "REWARD",
                            teamId = winnerTeamId,
                            counterpartyLabel = "Fantasy League Office",
                            playerIdsCsv = "",
                            title = if (isTitle) "Fantasy Championship Prize Bonus" else "Week $nextWeek Matchup Victory Bonus",
                            details = "Virtual CAD performance prize credited to ${updatedWinner.teamName}.",
                            cashDeltaCad = bonusCad,
                            balanceAfterCad = updatedWinner.cashBalanceCad,
                            timestampEpochMs = now
                        )
                    )
                }
            }
        }

        // Check stage transitions (End of Week 14 -> Generate Semifinals; End of Week 15 -> Generate Championship Final; End of Week 16 -> Archive Season)
        var newlyGeneratedBracket = emptyList<FantasyMatchupEntity>()
        var seasonArchiveToSave: SeasonArchiveEntity? = null

        val allAfterThisWeek = matchups.map { old ->
            completedWeekMatchups.firstOrNull { it.matchupId == old.matchupId } ?: old
        }

        if (nextWeek == SeasonSimulationEngine.REGULAR_SEASON_WEEKS &&
            allAfterThisWeek.none { it.stage == "SEMIFINAL" || it.stage == "CHAMPIONSHIP_FINAL" }
        ) {
            val ranked = SeasonSimulationEngine.rankStandings(teamsById.values.toList())
            newlyGeneratedBracket = SeasonSimulationEngine.generatePlayoffSemifinals(
                seasonNumber = settings.currentSeasonNumber,
                rankedStandings = ranked
            )
        } else if (nextWeek == SeasonSimulationEngine.SEMIFINAL_WEEK &&
            allAfterThisWeek.none { it.stage == "CHAMPIONSHIP_FINAL" }
        ) {
            val semis = completedWeekMatchups.filter { it.stage == "SEMIFINAL" }
            val finalMatchup = SeasonSimulationEngine.generateChampionshipFinal(
                seasonNumber = settings.currentSeasonNumber,
                completedSemifinals = semis,
                teamsById = teamsById
            )
            if (finalMatchup != null) {
                newlyGeneratedBracket = listOf(finalMatchup)
            }
        } else if (completedWeekMatchups.any { it.stage == "CHAMPIONSHIP_FINAL" }) {
            val finalMatch = completedWeekMatchups.first { it.stage == "CHAMPIONSHIP_FINAL" }
            seasonArchiveToSave = SeasonSimulationEngine.buildSeasonArchive(
                seasonNumber = settings.currentSeasonNumber,
                activeSeasonId = syncStatus?.activeSeasonId ?: 20262027L,
                finalMatchup = finalMatch,
                teams = teamsById.values.toList(),
                allPlayers = allPlayers,
                weights = settings.scoringWeights
            )
        }

        database.withTransaction {
            dao.updateMatchups(completedWeekMatchups)
            dao.upsertFantasyTeams(teamsById.values.toList())
            if (newlyGeneratedBracket.isNotEmpty()) {
                dao.insertMatchups(newlyGeneratedBracket)
            }
            humanRewardTransactions.forEach { dao.insertTransaction(it) }
            if (seasonArchiveToSave != null) {
                dao.insertSeasonArchive(seasonArchiveToSave)
            }
        }

        val label = when {
            seasonArchiveToSave != null ->
                "Championship Final complete! ${seasonArchiveToSave.championTeamName} won the Season ${settings.currentSeasonNumber} Cup!$userVictoryBonusNote"
            newlyGeneratedBracket.any { it.stage == "SEMIFINAL" } ->
                "Week $nextWeek complete — Playoff Semifinal Bracket set!$userVictoryBonusNote"
            newlyGeneratedBracket.any { it.stage == "CHAMPIONSHIP_FINAL" } ->
                "Playoff Semifinals complete — Championship Final matchup set!$userVictoryBonusNote"
            else ->
                "Simulated Fantasy Week $nextWeek (${if (forceOffline) "Offline Sim Mode" else "Real NHL Stats"})$userVictoryBonusNote"
        }

        return Result.success(label)
    }

    // --- Multiplayer Authoritative Snapshot Export & Atomic Import ---

    suspend fun exportMultiplayerRoomSnapshot(
        roomCode: String,
        revision: Long,
        hostClientId: String,
        hostGmName: String,
        phase: MultiplayerRoomPhase,
        fillEmptySlotsWithCpu: Boolean,
        lobbyDraftConfig: DraftConfig,
        participants: List<LobbyParticipant>,
        chatHistory: List<MultiplayerChatMessage>
    ): MultiplayerRoomSnapshot {
        val settings = settingsFlow.first()
        val teams = dao.getFantasyTeamsOnce()
        val draftSession = dao.getDraftSessionOnce()
        val draftPicks = dao.getDraftPicksOnce()
        val ownedPlayers = dao.getAllPlayersOnce()
            .filter { it.ownerFantasyTeamId != null }
            .map { p ->
                SyncedPlayerOwnership(
                    playerId = p.playerId,
                    ownerTeamId = p.ownerFantasyTeamId!!,
                    acquisitionPriceCad = p.acquisitionPriceCad,
                    lineupSlotCode = p.lineupSlotCode,
                    fullName = p.fullName,
                    positionCode = p.positionCode,
                    nhlTeamAbbrev = p.teamAbbrev,
                    overallRating = p.overallRating,
                    purchasePriceCad = p.purchasePriceCad,
                    sellPriceCad = p.sellPriceCad,
                    capHitCad = p.capHitCad
                )
            }
        val trades = dao.getTradeProposalsOnce()
        val matchups = dao.getMatchupsOnce()

        val activeConfig = if (phase != MultiplayerRoomPhase.LOBBY && draftSession != null) {
            decodeDraftConfig(draftSession)
        } else {
            lobbyDraftConfig
        }

        return MultiplayerRoomSnapshot(
            roomCode = roomCode,
            revision = revision,
            hostClientId = hostClientId,
            hostGmName = hostGmName,
            phase = phase,
            fillEmptySlotsWithCpu = fillEmptySlotsWithCpu,
            draftConfig = activeConfig,
            economyConfig = settings.economyConfig,
            scoringWeights = settings.scoringWeights,
            currentSeasonNumber = settings.currentSeasonNumber,
            participants = participants,
            orderedSlotTeamIds = participants.map { it.assignedTeamId },
            teams = teams.map { SyncedFantasyTeam.fromEntity(it) },
            draftSession = draftSession?.let { SyncedDraftSession.fromEntity(it) },
            draftPicks = draftPicks.map { SyncedDraftPick.fromEntity(it) },
            ownedPlayers = ownedPlayers,
            tradeProposals = trades.map { SyncedTradeProposal.fromEntity(it) },
            matchups = matchups.map { SyncedMatchup.fromEntity(it) },
            chatHistory = chatHistory.takeLast(60),
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    /**
     * Atomically applies an authoritative MultiplayerRoomSnapshot on a Guest device.
     */
    suspend fun applyMultiplayerRoomSnapshot(
        snapshot: MultiplayerRoomSnapshot,
        localControlledTeamId: String
    ) {
        settingsStore.updateEconomyConfig(snapshot.economyConfig)
        settingsStore.updateScoringWeights(snapshot.scoringWeights)
        settingsStore.setSeasonNumber(snapshot.currentSeasonNumber)
        settingsStore.updateMultiplayerSessionState(
            roomCode = snapshot.roomCode,
            role = RoomRole.GUEST.name,
            controlledTeamId = localControlledTeamId
        )

        if (snapshot.phase == MultiplayerRoomPhase.LOBBY) {
            return
        }

        val existingPlayersById = dao.getAllPlayersOnce().associateBy { it.playerId }
        val playersToUpsert = mutableListOf<PlayerEntity>()
        val playersToUpdate = mutableListOf<PlayerEntity>()

        for (owned in snapshot.ownedPlayers) {
            val local = existingPlayersById[owned.playerId]
            if (local != null) {
                playersToUpdate.add(
                    local.copy(
                        ownerFantasyTeamId = owned.ownerTeamId,
                        acquisitionPriceCad = owned.acquisitionPriceCad,
                        lineupSlotCode = owned.lineupSlotCode
                    )
                )
            } else {
                val nameParts = owned.fullName.ifBlank { "NHL Player #${owned.playerId}" }.split(" ", limit = 2)
                val first = nameParts.firstOrNull().orEmpty()
                val last = nameParts.getOrNull(1).orEmpty()
                playersToUpsert.add(
                    PlayerEntity(
                        playerId = owned.playerId,
                        firstName = first,
                        lastName = last,
                        fullName = owned.fullName.ifBlank { "NHL Player #${owned.playerId}" },
                        normalizedSearchName = TextNormalizer.normalizeForMatch(owned.fullName),
                        teamAbbrev = owned.nhlTeamAbbrev,
                        teamFullName = owned.nhlTeamAbbrev,
                        positionCode = owned.positionCode,
                        sweaterNumber = 0,
                        age = 25,
                        birthDate = "",
                        birthCountry = "CAN",
                        shootsCatches = "L",
                        heightInches = 73,
                        weightPounds = 198,
                        headshotUrl = "https://assets.nhle.com/mugs/nhl/20252026/${owned.nhlTeamAbbrev}/${owned.playerId}.png",
                        isProspect = false,
                        isRookie = false,
                        seasonId = 20252026L,
                        gamesPlayed = 0,
                        goals = 0,
                        assists = 0,
                        points = 0,
                        plusMinus = 0,
                        penaltyMinutes = 0,
                        powerPlayGoals = 0,
                        powerPlayPoints = 0,
                        shorthandedPoints = 0,
                        shots = 0,
                        hits = 0,
                        blockedShots = 0,
                        avgToiSeconds = 0,
                        faceoffWinPct = 0.0,
                        gamesStarted = 0,
                        wins = 0,
                        losses = 0,
                        otLosses = 0,
                        saves = 0,
                        shotsAgainst = 0,
                        goalsAgainst = 0,
                        savePct = 0.0,
                        goalsAgainstAverage = 0.0,
                        shutouts = 0,
                        prevSeasonId = 20242025L,
                        prevGamesPlayed = 0,
                        prevGoals = 0,
                        prevAssists = 0,
                        prevPoints = 0,
                        prevPpPoints = 0,
                        prevShots = 0,
                        prevHits = 0,
                        prevBlockedShots = 0,
                        prevWins = 0,
                        prevSaves = 0,
                        prevSavePct = 0.0,
                        prevGaa = 0.0,
                        prevShutouts = 0,
                        overallRating = owned.overallRating,
                        purchasePriceCad = owned.purchasePriceCad,
                        sellPriceCad = owned.sellPriceCad,
                        capHitCad = owned.capHitCad,
                        ownerFantasyTeamId = owned.ownerTeamId,
                        acquisitionPriceCad = owned.acquisitionPriceCad,
                        lineupSlotCode = owned.lineupSlotCode
                    )
                )
            }
        }

        database.withTransaction {
            dao.releaseAllPlayerOwnership()
            if (snapshot.teams.isNotEmpty()) {
                dao.deleteAllFantasyTeams()
                dao.upsertFantasyTeams(snapshot.teams.map { it.toEntity() })
            }
            if (playersToUpdate.isNotEmpty()) {
                dao.updatePlayers(playersToUpdate)
            }
            if (playersToUpsert.isNotEmpty()) {
                dao.upsertPlayers(playersToUpsert)
            }
            snapshot.draftSession?.let { dao.upsertDraftSession(it.toEntity()) }
            dao.clearDraftPicks()
            snapshot.draftPicks.forEach { dao.insertDraftPick(it.toEntity()) }
            dao.clearTradeProposals()
            snapshot.tradeProposals.forEach { dao.insertTradeProposal(it.toEntity()) }
            dao.clearMatchups()
            if (snapshot.matchups.isNotEmpty()) {
                dao.insertMatchups(snapshot.matchups.map { it.toEntity() })
            }
        }
    }

    suspend fun leaveMultiplayerAndRestoreSoloIfNeeded() {
        settingsStore.updateMultiplayerSessionState(
            roomCode = "",
            role = RoomRole.NONE.name,
            controlledTeamId = USER_TEAM_ID
        )
        val userTeam = dao.getFantasyTeamById(USER_TEAM_ID)
        if (userTeam == null) {
            dao.deleteAllFantasyTeams()
            ensureFranchiseInitialized()
        } else if (userTeam.ownerType != "HUMAN_GM") {
            dao.upsertFantasyTeam(userTeam.copy(isHuman = true, ownerType = "HUMAN_GM"))
        }
    }

    suspend fun startNextFantasySeason(): Result<String> {
        val nextSeasonNum = settingsStore.incrementSeasonNumber()
        val teams = dao.getFantasyTeamsOnce().map {
            it.copy(wins = 0, losses = 0, ties = 0, pointsFor = 0.0, pointsAgainst = 0.0)
        }
        val newSchedule = SeasonSimulationEngine.generateRegularSeasonSchedule(nextSeasonNum, teams)
        database.withTransaction {
            dao.upsertFantasyTeams(teams)
            dao.clearMatchups()
            dao.insertMatchups(newSchedule)
        }
        return Result.success("Started Fantasy Season $nextSeasonNum with a fresh 14-week schedule (rosters & treasury preserved)!")
    }

    // --- Settings & Economy Configuration ---

    suspend fun updateThemeMode(mode: AppThemeMode) = settingsStore.updateThemeMode(mode)

    suspend fun updateScoringWeights(weights: ScoringWeights) = settingsStore.updateScoringWeights(weights)

    suspend fun updateOfflineSimToggle(enabled: Boolean) = settingsStore.setOfflineSeasonSim(enabled)

    suspend fun updateEconomySettings(
        newConfig: EconomyConfig,
        applyStartingBudgetToCurrentTreasury: Boolean
    ): Result<String> {
        settingsStore.updateEconomyConfig(newConfig)
        if (applyStartingBudgetToCurrentTreasury) {
            val userTeam = dao.getFantasyTeamById(USER_TEAM_ID)
            if (userTeam != null) {
                val rosterSpent = dao.getTeamRosterOnce(USER_TEAM_ID).sumOf {
                    if (it.acquisitionPriceCad > 0L) it.acquisitionPriceCad else it.purchasePriceCad
                }
                val recalculatedCash = (newConfig.startingBudgetCad - rosterSpent).coerceAtLeast(0L)
                val delta = recalculatedCash - userTeam.cashBalanceCad
                database.withTransaction {
                    dao.upsertFantasyTeam(
                        userTeam.copy(
                            startingBudgetCad = newConfig.startingBudgetCad,
                            cashBalanceCad = recalculatedCash
                        )
                    )
                    dao.insertTransaction(
                        TransactionEntity(
                            type = "SETTINGS",
                            teamId = USER_TEAM_ID,
                            counterpartyLabel = "Economy Settings",
                            playerIdsCsv = "",
                            title = "Updated Starting Budget & Salary Cap",
                            details = "Starting Budget: ${CadFormatter.formatCompact(newConfig.startingBudgetCad)} • Salary Cap: ${CadFormatter.formatCompact(newConfig.salaryCapCad)}",
                            cashDeltaCad = delta,
                            balanceAfterCad = recalculatedCash,
                            timestampEpochMs = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        // Re-evaluate sell prices if sell fee changed
        val allPlayers = dao.getAllPlayersOnce()
        if (allPlayers.isNotEmpty()) {
            val revalued = allPlayers.map { p ->
                val v = PlayerValuationEngine.valuatePlayer(p, newConfig, p.previousPriceCad)
                p.copy(sellPriceCad = v.sellPriceCad)
            }
            dao.updatePlayers(revalued)
        }
        return Result.success("Economy & Salary Cap settings saved.")
    }

    fun getOptionalExternalApiKey(): String = credentialStore.getOptionalExternalApiKey()
    fun saveOptionalExternalApiKey(key: String) = credentialStore.saveOptionalExternalApiKey(key)

    // --- Internal Helpers ---

    private fun createDefaultCpuTeams(count: Int, startingBudgetCad: Long): List<FantasyTeamEntity> {
        val blueprints = listOf(
            Triple("Montréal Voyageurs", "MTV", TeamEmblem.ROYAL_CROWN to (0xFFEF4444L to 0xFF1E1B4BL)),
            Triple("Toronto St. Pats", "TSP", TeamEmblem.NORTH_STAR to (0xFF3B82F6L to 0xFF0F172AL)),
            Triple("Edmonton Oil Barons", "EOB", TeamEmblem.BLAZING_PUCK to (0xFFF97316L to 0xFF172554L)),
            Triple("Vancouver Pacifics", "VCP", TeamEmblem.TRIDENT_DEEP to (0xFF10B981L to 0xFF062C22L)),
            Triple("Calgary Rockies", "CGR", TeamEmblem.MOUNTAIN_PEAK to (0xFFF59E0BL to 0xFF1C1917L)),
            Triple("Winnipeg Falcons", "WPF", TeamEmblem.FALCON_WING to (0xFF38BDF8L to 0xFF0F172AL)),
            Triple("Ottawa Senators GM", "OSG", TeamEmblem.FORTRESS_CREST to (0xFFDC2626L to 0xFF18181BL)),
            Triple("Boston Bears HC", "BBH", TeamEmblem.GRIZZLY_CLAW to (0xFFEAB308L to 0xFF1C1917L)),
            Triple("Colorado Summit", "CSM", TeamEmblem.ARCTIC_WOLF to (0xFF8B5CF6L to 0xFF1E1B4BL)),
            Triple("New York Empire", "NYE", TeamEmblem.CROSSED_STICKS to (0xFF2563EBL to 0xFF0F172AL)),
            Triple("Detroit Motor HC", "DMH", TeamEmblem.ICE_STORM to (0xFFEF4444L to 0xFF1F2937L)),
            Triple("Chicago Wind HC", "CWH", TeamEmblem.FALCON_WING to (0xFFF43F5EL to 0xFF111827L)),
            Triple("Seattle Deep HC", "SDH", TeamEmblem.TRIDENT_DEEP to (0xFF14B8A6L to 0xFF0F172AL)),
            Triple("Tampa Storm HC", "TSH", TeamEmblem.ICE_STORM to (0xFF0EA5E9L to 0xFF0F172AL)),
            Triple("Vegas Aces GM", "VAG", TeamEmblem.ROYAL_CROWN to (0xFFD97706L to 0xFF18181BL))
        )

        return (0 until count).map { idx ->
            val (name, abbrev, style) = blueprints[idx % blueprints.size]
            FantasyTeamEntity(
                teamId = "CPU_${idx + 1}",
                teamName = name,
                abbreviation = abbrev,
                emblemId = style.first.id,
                primaryColorHex = style.second.first,
                secondaryColorHex = style.second.second,
                isHuman = false,
                cashBalanceCad = startingBudgetCad,
                startingBudgetCad = startingBudgetCad
            )
        }
    }

    private data class SyncBundle(
        val rosters: Map<String, TeamRosterResponseDto>,
        val prospects: Map<String, TeamRosterResponseDto>,
        val curSkaterSum: List<StatsRestSkaterSummaryDto>,
        val curSkaterRt: List<StatsRestSkaterRealtimeDto>,
        val curGoalieSum: List<StatsRestGoalieSummaryDto>,
        val prevSkaterSum: List<StatsRestSkaterSummaryDto>,
        val prevSkaterRt: List<StatsRestSkaterRealtimeDto>,
        val prevGoalieSum: List<StatsRestGoalieSummaryDto>,
        val scoreboard: ScoreboardResponseDto?,
        val injuries: EspnInjuriesResponseDto?,
        val news: EspnNewsResponseDto?
    )

    private data class ParsedInjury(
        val status: String,
        val type: String,
        val note: String,
        val returnDate: String
    )

    private data class TodayPlayerBox(
        val gameId: Long,
        val isLive: Boolean,
        val opponentLabel: String,
        val goals: Int = 0,
        val assists: Int = 0,
        val ppPoints: Int = 0,
        val shots: Int = 0,
        val hits: Int = 0,
        val blocks: Int = 0,
        val goalieWin: Int = 0,
        val goalieSaves: Int = 0,
        val goalieShutout: Int = 0
    )

    private fun buildInjuryLookup(dto: EspnInjuriesResponseDto?): Map<String, ParsedInjury> {
        if (dto == null) return emptyMap()
        val map = HashMap<String, ParsedInjury>()
        dto.injuries.forEach { teamBlock ->
            teamBlock.injuries.forEach { inj ->
                val rawName = inj.athlete?.displayName?.ifBlank {
                    "${inj.athlete.firstName} ${inj.athlete.lastName}"
                } ?: ""
                val norm = TextNormalizer.normalizeForMatch(rawName)
                if (norm.isNotBlank()) {
                    map[norm] = ParsedInjury(
                        status = inj.status.ifBlank { "Day-to-Day" },
                        type = inj.details?.type ?: "",
                        note = inj.shortComment,
                        returnDate = inj.details?.returnDate ?: ""
                    )
                }
            }
        }
        return map
    }

    private fun buildTodayPlayerBoxscoreMap(boxscores: List<GameBoxscoreResponseDto>): Map<Long, TodayPlayerBox> {
        val result = HashMap<Long, TodayPlayerBox>()
        for (box in boxscores) {
            val isLive = box.gameState == "LIVE" || box.gameState == "CRIT"
            val isFinal = box.gameState == "FINAL" || box.gameState == "OFF"
            val awayScore = box.awayTeam.score ?: 0
            val homeScore = box.homeTeam.score ?: 0
            val awayOpp = "@ ${box.homeTeam.abbrev}"
            val homeOpp = "vs ${box.awayTeam.abbrev}"

            fun addSkaters(list: List<BoxscoreSkaterDto>, opp: String) {
                for (sk in list) {
                    if (sk.playerId <= 0L) continue
                    result[sk.playerId] = TodayPlayerBox(
                        gameId = box.id,
                        isLive = isLive,
                        opponentLabel = opp,
                        goals = sk.goals,
                        assists = sk.assists,
                        ppPoints = sk.powerPlayGoals,
                        shots = sk.sog,
                        hits = sk.hits,
                        blocks = sk.blockedShots
                    )
                }
            }

            fun addGoalies(list: List<BoxscoreGoalieDto>, opp: String, teamWonFinal: Boolean) {
                for (g in list) {
                    if (g.playerId <= 0L) continue
                    val earnedWin = if (g.decision == "W") {
                        1
                    } else if (isFinal && teamWonFinal && (g.starter == true || g.saves >= 10)) {
                        1
                    } else 0
                    val earnedShutout = if (isFinal && earnedWin == 1 && g.goalsAgainst == 0 && g.saves >= 12) 1 else 0
                    result[g.playerId] = TodayPlayerBox(
                        gameId = box.id,
                        isLive = isLive,
                        opponentLabel = opp,
                        goalieWin = earnedWin,
                        goalieSaves = g.saves,
                        goalieShutout = earnedShutout
                    )
                }
            }

            box.playerByGameStats?.awayTeam?.let { away ->
                addSkaters(away.forwards, awayOpp)
                addSkaters(away.defense, awayOpp)
                addGoalies(away.goalies, awayOpp, awayScore > homeScore)
            }
            box.playerByGameStats?.homeTeam?.let { home ->
                addSkaters(home.forwards, homeOpp)
                addSkaters(home.defense, homeOpp)
                addGoalies(home.goalies, homeOpp, homeScore > awayScore)
            }
        }
        return result
    }

    private fun mapScoreboardGames(dto: ScoreboardResponseDto?): List<LiveNhlGameEntity> {
        if (dto == null) return emptyList()
        return dto.games.map { g ->
            val pNum = g.periodDescriptor?.number ?: 1
            val pType = g.periodDescriptor?.periodType ?: "REG"
            val periodLabel = when {
                g.gameState == "FUT" || g.gameState == "PRE" -> "Scheduled"
                g.gameState == "FINAL" || g.gameState == "OFF" ->
                    if (pType == "OT") "FINAL/OT" else if (pType == "SO") "FINAL/SO" else "FINAL"
                pType == "OT" -> "OT"
                pType == "SO" -> "SO"
                else -> "P$pNum"
            }
            val clockLabel = when {
                g.gameState == "LIVE" || g.gameState == "CRIT" ->
                    if (g.clock?.inIntermission == true) "INT" else g.clock?.timeRemaining ?: ""
                else -> ""
            }
            LiveNhlGameEntity(
                gameId = g.id,
                gameDate = g.gameDate.ifBlank { dto.currentDate },
                gameState = g.gameState,
                periodLabel = periodLabel,
                clockLabel = clockLabel,
                awayAbbrev = g.awayTeam.abbrev,
                awayName = g.awayTeam.name?.default ?: g.awayTeam.commonName?.default ?: g.awayTeam.abbrev,
                awayScore = g.awayTeam.score ?: 0,
                awaySog = g.awayTeam.sog ?: 0,
                awayLogoUrl = g.awayTeam.logo,
                homeAbbrev = g.homeTeam.abbrev,
                homeName = g.homeTeam.name?.default ?: g.homeTeam.commonName?.default ?: g.homeTeam.abbrev,
                homeScore = g.homeTeam.score ?: 0,
                homeSog = g.homeTeam.sog ?: 0,
                homeLogoUrl = g.homeTeam.logo,
                startTimeUtc = g.startTimeUTC,
                venueName = g.venue?.default ?: ""
            )
        }
    }

    private fun mapNewsArticles(dto: EspnNewsResponseDto?, now: Long): List<NewsHeadlineEntity> {
        if (dto == null) return emptyList()
        return dto.articles
            .filter { it.headline.isNotBlank() }
            .mapIndexed { idx, a ->
                NewsHeadlineEntity(
                    articleId = (a.id ?: (a.headline.hashCode().toLong() + idx)).toString(),
                    headline = a.headline.trim(),
                    description = a.description.trim(),
                    publishedAt = a.published,
                    webUrl = a.links?.web?.href ?: "",
                    sourceLabel = "ESPN NHL News",
                    fetchedAtEpochMs = now
                )
            }
    }

    companion object {
        const val USER_TEAM_ID = "USER"
    }
}
