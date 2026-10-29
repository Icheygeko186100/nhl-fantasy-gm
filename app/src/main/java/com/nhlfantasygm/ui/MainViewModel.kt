package com.nhlfantasygm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nhlfantasygm.data.local.*
import com.nhlfantasygm.data.repository.NhlFantasyRepository
import com.nhlfantasygm.domain.*
import com.nhlfantasygm.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ScoredPlayerItem(
    val player: PlayerEntity,
    val fantasy: PlayerFantasyScoreSummary
)

enum class MarketplaceCategoryTab(val label: String) {
    ALL("All Players"),
    BEST_VALUE("Best Value"),
    BREAKOUTS("Breakouts"),
    WATCHLIST("Watchlist"),
    RECENT("Recent")
}

enum class PlayerSortOption(val label: String) {
    OVR_DESC("Highest Rating"),
    VALUE_DESC("Highest Price"),
    VALUE_ASC("Lowest Price"),
    FANTASY_SEASON("Fantasy Points"),
    BARGAIN_VALUE("FP per $1M"),
    GOALS("Goals"),
    ASSISTS("Assists"),
    POINTS("NHL Points"),
    SHOTS("Shots"),
    HITS("Hits"),
    BLOCKS("Blocks"),
    SAVE_PCT("Save %"),
    WINS("Goalie Wins")
}

enum class PositionFilter(val label: String) {
    ALL("All"),
    FORWARDS("Forwards"),
    CENTER("C"),
    LEFT_WING("LW"),
    RIGHT_WING("RW"),
    DEFENSE("D"),
    GOALIE("G"),
    ROOKIES_PROSPECTS("Rookies & Prospects")
}

enum class LeagueSectionTab(val label: String) {
    MATCHUPS("Matchups"),
    STANDINGS("Standings"),
    TRADES("Trades"),
    NHL_STATS("NHL Live"),
    HISTORY("Awards"),
    SETTINGS("Settings")
}

data class MarketplaceFilterState(
    val searchQuery: String = "",
    val categoryTab: MarketplaceCategoryTab = MarketplaceCategoryTab.ALL,
    val teamAbbrev: String = "ALL",
    val positionFilter: PositionFilter = PositionFilter.ALL,
    val sortOption: PlayerSortOption = PlayerSortOption.OVR_DESC,
    val maxPriceMillions: Float = 20f,
    val minAge: Int = 17,
    val maxAge: Int = 43,
    val minOvr: Int = 55,
    val availableOnly: Boolean = false,
    val healthyOnly: Boolean = false
)

data class PendingConfirmationTx(
    val player: PlayerEntity,
    val warningText: String,
    val isSelling: Boolean
)

data class PlayerDetailDialogState(
    val player: PlayerEntity,
    val fantasy: PlayerFantasyScoreSummary,
    val valuation: ValuationResult,
    val profileCache: PlayerProfileCacheEntity?,
    val historicalSeasons: List<HistoricalSeasonRow>,
    val recentGames: List<RecentGameLogRow>,
    val isLoadingCareer: Boolean,
    val isLiveCareerFetch: Boolean
)

class MainViewModel(
    val repository: NhlFantasyRepository
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _hasSyncedLiveThisSession = MutableStateFlow(false)

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _marketFilter = MutableStateFlow(MarketplaceFilterState())
    val marketFilter: StateFlow<MarketplaceFilterState> = _marketFilter.asStateFlow()

    private val _leagueTab = MutableStateFlow(LeagueSectionTab.MATCHUPS)
    val leagueTab: StateFlow<LeagueSectionTab> = _leagueTab.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmationTx?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmationTx?> = _pendingConfirmation.asStateFlow()

    private val _selectedPlayerDetail = MutableStateFlow<PlayerDetailDialogState?>(null)
    val selectedPlayerDetail: StateFlow<PlayerDetailDialogState?> = _selectedPlayerDetail.asStateFlow()

    private val _draftTimerRemainingSec = MutableStateFlow(0)
    val draftTimerRemainingSec: StateFlow<Int> = _draftTimerRemainingSec.asStateFlow()
    private var draftTimerJob: Job? = null

    val settingsState: StateFlow<UserAppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserAppSettings())

    val nhlTeamsState: StateFlow<List<NhlTeamEntity>> = repository.nhlTeamsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fantasyTeamsState: StateFlow<List<FantasyTeamEntity>> = repository.fantasyTeamsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val humanTeamState: StateFlow<FantasyTeamEntity?> = repository.humanTeamFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val transactionsState: StateFlow<List<TransactionEntity>> = repository.transactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val draftSessionState: StateFlow<DraftSessionEntity?> = repository.draftSessionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val draftPicksState: StateFlow<List<DraftPickEntity>> = repository.draftPicksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tradeProposalsState: StateFlow<List<TradeProposalEntity>> = repository.tradeProposalsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val matchupsState: StateFlow<List<FantasyMatchupEntity>> = repository.matchupsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seasonArchivesState: StateFlow<List<SeasonArchiveEntity>> = repository.seasonArchivesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val liveGamesState: StateFlow<List<LiveNhlGameEntity>> = repository.liveGamesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val newsHeadlinesState: StateFlow<List<NewsHeadlineEntity>> = repository.newsHeadlinesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncStatusState: StateFlow<SyncStatusEntity?> = repository.syncStatusFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dataFreshnessState: StateFlow<DataFreshnessState> = combine(
        _isSyncing,
        _hasSyncedLiveThisSession,
        syncStatusState
    ) { syncing, liveThisSession, status ->
        when {
            syncing -> DataFreshnessState.SYNCING
            status != null && status.lastSyncSucceeded &&
                (liveThisSession || (System.currentTimeMillis() - status.lastFullSyncEpochMs < 20 * 60 * 1000L)) &&
                status.syncedPlayerCount > 0 -> DataFreshnessState.LIVE
            status != null && status.syncedPlayerCount > 0 -> DataFreshnessState.CACHED
            else -> DataFreshnessState.UNAVAILABLE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataFreshnessState.SYNCING)

    val allScoredPlayersState: StateFlow<List<ScoredPlayerItem>> = combine(
        repository.allPlayersFlow,
        settingsState
    ) { players, settings ->
        players.map { p ->
            ScoredPlayerItem(
                player = p,
                fantasy = FantasyScoringEngine.summarizePlayer(p, settings.scoringWeights)
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredMarketplacePlayersState: StateFlow<List<ScoredPlayerItem>> = combine(
        allScoredPlayersState,
        _marketFilter
    ) { allItems, filter ->
        val normQuery = TextNormalizer.normalizeForMatch(filter.searchQuery)
        val maxPriceCad = (filter.maxPriceMillions * 1_000_000f).toLong()

        val filtered = allItems.filter { item ->
            val p = item.player
            val matchesQuery = normQuery.isBlank() ||
                p.normalizedSearchName.contains(normQuery) ||
                p.teamAbbrev.lowercase().contains(normQuery) ||
                p.teamFullName.lowercase().contains(normQuery)

            val matchesTab = when (filter.categoryTab) {
                MarketplaceCategoryTab.ALL -> true
                MarketplaceCategoryTab.BEST_VALUE -> (p.gamesPlayed >= 2 || p.prevGamesPlayed >= 10) && item.fantasy.valuePerMillionCad > 1.0
                MarketplaceCategoryTab.BREAKOUTS -> item.fantasy.isBreakoutCandidate
                MarketplaceCategoryTab.WATCHLIST -> p.isWatchlisted
                MarketplaceCategoryTab.RECENT -> p.lastViewedEpochMs > 0L
            }

            val matchesTeam = filter.teamAbbrev == "ALL" || p.teamAbbrev == filter.teamAbbrev
            val matchesPos = when (filter.positionFilter) {
                PositionFilter.ALL -> true
                PositionFilter.FORWARDS -> p.position.isForward
                PositionFilter.CENTER -> p.position == Position.CENTER
                PositionFilter.LEFT_WING -> p.position == Position.LEFT_WING
                PositionFilter.RIGHT_WING -> p.position == Position.RIGHT_WING
                PositionFilter.DEFENSE -> p.position == Position.DEFENSE
                PositionFilter.GOALIE -> p.position == Position.GOALIE
                PositionFilter.ROOKIES_PROSPECTS -> p.isRookie || p.isProspect
            }

            val matchesPrice = filter.maxPriceMillions >= 19.9f || p.purchasePriceCad <= maxPriceCad
            val matchesAge = p.age in filter.minAge..filter.maxAge
            val matchesOvr = p.overallRating >= filter.minOvr
            val matchesAvail = !filter.availableOnly || p.ownerFantasyTeamId == null
            val matchesHealthy = !filter.healthyOnly || p.injurySeverity == InjurySeverity.HEALTHY

            matchesQuery && matchesTab && matchesTeam && matchesPos &&
                matchesPrice && matchesAge && matchesOvr && matchesAvail && matchesHealthy
        }

        when {
            filter.categoryTab == MarketplaceCategoryTab.RECENT ->
                filtered.sortedByDescending { it.player.lastViewedEpochMs }
            filter.categoryTab == MarketplaceCategoryTab.BEST_VALUE && filter.sortOption == PlayerSortOption.OVR_DESC ->
                filtered.sortedByDescending { it.fantasy.valuePerMillionCad }
            else -> when (filter.sortOption) {
                PlayerSortOption.OVR_DESC -> filtered.sortedWith(
                    compareByDescending<ScoredPlayerItem> { it.player.overallRating }
                        .thenByDescending { it.fantasy.seasonPoints }
                )
                PlayerSortOption.VALUE_DESC -> filtered.sortedByDescending { it.player.purchasePriceCad }
                PlayerSortOption.VALUE_ASC -> filtered.sortedBy { it.player.purchasePriceCad }
                PlayerSortOption.FANTASY_SEASON -> filtered.sortedByDescending { it.fantasy.seasonPoints }
                PlayerSortOption.BARGAIN_VALUE -> filtered.sortedByDescending { it.fantasy.valuePerMillionCad }
                PlayerSortOption.GOALS -> filtered.sortedByDescending { it.player.goals }
                PlayerSortOption.ASSISTS -> filtered.sortedByDescending { it.player.assists }
                PlayerSortOption.POINTS -> filtered.sortedByDescending { it.player.points }
                PlayerSortOption.SHOTS -> filtered.sortedByDescending { it.player.shots }
                PlayerSortOption.HITS -> filtered.sortedByDescending { it.player.hits }
                PlayerSortOption.BLOCKS -> filtered.sortedByDescending { it.player.blockedShots }
                PlayerSortOption.SAVE_PCT -> filtered.sortedWith(
                    compareByDescending<ScoredPlayerItem> { it.player.position == Position.GOALIE && it.player.gamesPlayed > 0 }
                        .thenByDescending { it.player.savePct }
                )
                PlayerSortOption.WINS -> filtered.sortedByDescending { it.player.wins }
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userRosterScoredState: StateFlow<List<ScoredPlayerItem>> = allScoredPlayersState
        .map { list -> list.filter { it.player.ownerFantasyTeamId == NhlFantasyRepository.USER_TEAM_ID } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userRosterAnalyticsState: StateFlow<TeamRosterAnalytics> = userRosterScoredState
        .map { list -> LineupAnalyticsEngine.analyzeRoster(list.map { it.player }) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            LineupAnalyticsEngine.analyzeRoster(emptyList())
        )

    val userFinancialSummaryState: StateFlow<TeamFinancialSummary?> = combine(
        humanTeamState,
        userRosterScoredState,
        transactionsState,
        settingsState
    ) { team, rosterItems, txs, settings ->
        if (team == null) null
        else GameTransactionEngine.buildFinancialSummary(
            team = team,
            roster = rosterItems.map { it.player },
            transactions = txs,
            economyConfig = settings.economyConfig
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            repository.ensureFranchiseInitialized()
            triggerFullSync(silentIfCached = true)
        }
        observeDraftSessionForTimerAndCpu()
    }

    fun triggerFullSync(silentIfCached: Boolean = false) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.syncAllNhlData(includeProspects = true)
            _isSyncing.value = false
            res.onSuccess { count ->
                _hasSyncedLiveThisSession.value = true
                if (!silentIfCached) {
                    _snackbarEvents.tryEmit("Live NHL Sync complete: $count players across all 32 teams.")
                }
            }.onFailure { err ->
                if (!silentIfCached) {
                    _snackbarEvents.tryEmit("NHL Sync failed (${err.message ?: "Offline"}). Using local cached data.")
                }
            }
        }
    }

    fun refreshLiveScores() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.refreshLiveScoresOnly()
            _isSyncing.value = false
            res.onSuccess { games ->
                _hasSyncedLiveThisSession.value = true
                _snackbarEvents.tryEmit("Refreshed $games NHL games & live player boxscores.")
            }.onFailure { err ->
                _snackbarEvents.tryEmit("Scoreboard refresh failed: ${err.message ?: "Offline"}")
            }
        }
    }

    // --- Marketplace & Player Detail ---

    fun updateMarketFilter(transform: (MarketplaceFilterState) -> MarketplaceFilterState) {
        _marketFilter.update(transform)
    }

    fun setLeagueTab(tab: LeagueSectionTab) {
        _leagueTab.value = tab
    }

    fun openPlayerDetail(playerId: Long) {
        val scoredItem = allScoredPlayersState.value.firstOrNull { it.player.playerId == playerId } ?: return
        val settings = settingsState.value
        val valuation = PlayerValuationEngine.valuatePlayer(
            scoredItem.player,
            settings.economyConfig,
            scoredItem.player.previousPriceCad
        )

        _selectedPlayerDetail.value = PlayerDetailDialogState(
            player = scoredItem.player,
            fantasy = scoredItem.fantasy,
            valuation = valuation,
            profileCache = null,
            historicalSeasons = emptyList(),
            recentGames = emptyList(),
            isLoadingCareer = true,
            isLiveCareerFetch = false
        )

        viewModelScope.launch {
            val (cache, isLive) = repository.getOrFetchPlayerProfileDetails(playerId)
            val seasons = repository.decodeHistoricalSeasons(cache)
            val recent = repository.decodeRecentGames(cache)
            _selectedPlayerDetail.update { cur ->
                if (cur?.player?.playerId != playerId) cur
                else cur.copy(
                    profileCache = cache,
                    historicalSeasons = seasons,
                    recentGames = recent,
                    isLoadingCareer = false,
                    isLiveCareerFetch = isLive
                )
            }
        }
    }

    fun closePlayerDetail() {
        _selectedPlayerDetail.value = null
    }

    fun toggleWatchlist(playerId: Long) {
        viewModelScope.launch {
            repository.toggleWatchlist(playerId)
            refreshOpenPlayerDetailSnapshot(playerId)
        }
    }

    fun requestBuyPlayer(player: PlayerEntity) {
        val team = humanTeamState.value ?: return
        val roster = userRosterScoredState.value.map { it.player }
        val validation = GameTransactionEngine.validatePlayerPurchase(
            buyerTeam = team,
            currentRoster = roster,
            targetPlayer = player,
            economyConfig = settingsState.value.economyConfig
        )
        when (validation) {
            is TransactionValidationResult.Invalid -> {
                _snackbarEvents.tryEmit(validation.reason)
            }
            is TransactionValidationResult.Valid -> {
                if (validation.requiresHighValueConfirmation) {
                    _pendingConfirmation.value = PendingConfirmationTx(
                        player = player,
                        warningText = validation.confirmationWarning,
                        isSelling = false
                    )
                } else {
                    executeConfirmedBuy(player.playerId)
                }
            }
        }
    }

    fun requestSellPlayer(player: PlayerEntity) {
        val team = humanTeamState.value ?: return
        val validation = GameTransactionEngine.validatePlayerSale(team, player)
        when (validation) {
            is TransactionValidationResult.Invalid -> {
                _snackbarEvents.tryEmit(validation.reason)
            }
            is TransactionValidationResult.Valid -> {
                if (validation.requiresHighValueConfirmation) {
                    _pendingConfirmation.value = PendingConfirmationTx(
                        player = player,
                        warningText = validation.confirmationWarning,
                        isSelling = true
                    )
                } else {
                    executeConfirmedSell(player.playerId)
                }
            }
        }
    }

    fun confirmPendingTransaction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        if (pending.isSelling) {
            executeConfirmedSell(pending.player.playerId)
        } else {
            executeConfirmedBuy(pending.player.playerId)
        }
    }

    fun dismissPendingTransaction() {
        _pendingConfirmation.value = null
    }

    private fun executeConfirmedBuy(playerId: Long) {
        viewModelScope.launch {
            val res = repository.buyPlayerFromMarketplace(playerId)
            res.onSuccess { msg ->
                _snackbarEvents.tryEmit(msg)
                refreshOpenPlayerDetailSnapshot(playerId)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Purchase failed")
            }
        }
    }

    private fun executeConfirmedSell(playerId: Long) {
        viewModelScope.launch {
            val res = repository.sellPlayerToMarketplace(playerId)
            res.onSuccess { msg ->
                _snackbarEvents.tryEmit(msg)
                refreshOpenPlayerDetailSnapshot(playerId)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Sale failed")
            }
        }
    }

    private suspend fun refreshOpenPlayerDetailSnapshot(playerId: Long) {
        delay(80)
        val latest = allScoredPlayersState.value.firstOrNull { it.player.playerId == playerId } ?: return
        val valRes = PlayerValuationEngine.valuatePlayer(
            latest.player,
            settingsState.value.economyConfig,
            latest.player.previousPriceCad
        )
        _selectedPlayerDetail.update { cur ->
            if (cur?.player?.playerId != playerId) cur
            else cur.copy(player = latest.player, fantasy = latest.fantasy, valuation = valRes)
        }
    }

    // --- Draft Mode ---

    private fun observeDraftSessionForTimerAndCpu() {
        viewModelScope.launch {
            draftSessionState.collectLatest { session ->
                draftTimerJob?.cancel()
                if (session == null || session.status != "IN_PROGRESS") {
                    _draftTimerRemainingSec.value = 0
                    return@collectLatest
                }
                val config = repository.decodeDraftConfig(session)
                val teamOrder = session.teamOrderCsv.split(",").filter { it.isNotBlank() }
                if (teamOrder.isEmpty()) return@collectLatest

                val (_, _, activeTeamId) = DraftEngine.getPickMetadata(
                    session.currentOverallPick,
                    teamOrder,
                    config.isSnakeDraft
                )

                if (activeTeamId != NhlFantasyRepository.USER_TEAM_ID) {
                    _draftTimerRemainingSec.value = 0
                    delay(180)
                    repository.advanceCpuDraftPicks(maxSteps = 20)
                } else if (config.pickTimerSeconds > 0) {
                    draftTimerJob = launch {
                        var left = config.pickTimerSeconds
                        _draftTimerRemainingSec.value = left
                        while (left > 0) {
                            delay(1000L)
                            left--
                            _draftTimerRemainingSec.value = left
                        }
                        val autoRes = repository.autoPickForHuman()
                        autoRes.onSuccess { msg ->
                            _snackbarEvents.tryEmit("Timer expired — Auto-Draft: $msg")
                        }
                    }
                } else {
                    _draftTimerRemainingSec.value = 0
                }
            }
        }
    }

    fun startDraft(config: DraftConfig) {
        viewModelScope.launch {
            repository.startNewDraft(config)
                .onSuccess {
                    _snackbarEvents.tryEmit("Draft Room live! ${config.teamCount} teams • ${config.totalRounds} rounds.")
                }
                .onFailure {
                    _snackbarEvents.tryEmit(it.message ?: "Could not start draft")
                }
        }
    }

    fun draftPlayerForHuman(playerId: Long) {
        viewModelScope.launch {
            repository.makeHumanDraftPick(playerId)
                .onSuccess { msg -> _snackbarEvents.tryEmit(msg) }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Invalid draft pick") }
        }
    }

    fun autoPickHumanTurn() {
        viewModelScope.launch {
            repository.autoPickForHuman()
                .onSuccess { msg -> _snackbarEvents.tryEmit(msg) }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Auto-pick failed") }
        }
    }

    fun undoLastDraftPick() {
        viewModelScope.launch {
            repository.undoLastHumanDraftPick()
                .onSuccess { msg -> _snackbarEvents.tryEmit(msg) }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Nothing to undo") }
        }
    }

    fun simRemainingDraftToCompletion() {
        viewModelScope.launch {
            var safety = 0
            while (safety < 350) {
                val session = draftSessionState.value
                if (session == null || session.status != "IN_PROGRESS") break
                val config = repository.decodeDraftConfig(session)
                val order = session.teamOrderCsv.split(",").filter { it.isNotBlank() }
                val (_, _, activeTeam) = DraftEngine.getPickMetadata(session.currentOverallPick, order, config.isSnakeDraft)
                if (activeTeam == NhlFantasyRepository.USER_TEAM_ID) {
                    val r = repository.autoPickForHuman()
                    if (r.isFailure) break
                } else {
                    val r = repository.advanceCpuDraftPicks(maxSteps = 16)
                    if (r.getOrDefault(0) == 0) break
                }
                delay(40)
                safety++
            }
            _snackbarEvents.tryEmit("Draft completed! Review your Draft Report Card.")
        }
    }

    // --- My Team ---

    fun saveFranchiseCustomization(
        name: String,
        abbrev: String,
        emblem: TeamEmblem,
        primaryHex: Long,
        secondaryHex: Long
    ) {
        viewModelScope.launch {
            repository.customizeUserFranchise(name, abbrev, emblem, primaryHex, secondaryHex)
                .onSuccess { _snackbarEvents.tryEmit("Updated franchise identity to $name ($abbrev).") }
        }
    }

    fun autoSetOptimalLineup() {
        viewModelScope.launch {
            repository.applyRecommendedUserLineup()
                .onSuccess { _snackbarEvents.tryEmit(it) }
                .onFailure { _snackbarEvents.tryEmit(it.message ?: "Could not set lineup") }
        }
    }

    fun assignLineupSlot(playerId: Long, targetSlot: LineupSlot) {
        viewModelScope.launch {
            repository.assignPlayerToSlot(playerId, targetSlot)
                .onSuccess { _snackbarEvents.tryEmit(it) }
                .onFailure { _snackbarEvents.tryEmit(it.message ?: "Invalid lineup move") }
        }
    }

    // --- Trades & Season ---

    fun submitTradeProposal(
        cpuTeamId: String,
        offeredPlayerIds: List<Long>,
        offeredCashCad: Long,
        requestedPlayerIds: List<Long>,
        requestedCashCad: Long
    ) {
        viewModelScope.launch {
            repository.proposeTradeToCpu(
                cpuTeamId = cpuTeamId,
                offeredPlayerIds = offeredPlayerIds,
                offeredCashCad = offeredCashCad,
                requestedPlayerIds = requestedPlayerIds,
                requestedCashCad = requestedCashCad
            ).onSuccess { verdict ->
                _snackbarEvents.tryEmit(verdict.gmReasoning)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Trade invalid")
            }
        }
    }

    fun solicitCpuTradeOffer() {
        viewModelScope.launch {
            repository.solicitIncomingCpuOffer()
                .onSuccess { prop ->
                    _snackbarEvents.tryEmit("New trade offer received from ${prop.proposerTeamName}!")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "No incoming trade offers available")
                }
        }
    }

    fun respondToTradeOffer(tradeId: Long, accept: Boolean) {
        viewModelScope.launch {
            repository.respondToIncomingProposal(tradeId, accept)
                .onSuccess { _snackbarEvents.tryEmit(it) }
                .onFailure { _snackbarEvents.tryEmit(it.message ?: "Trade failed") }
        }
    }

    fun simulateNextWeek() {
        viewModelScope.launch {
            repository.simulateNextSeasonWeek()
                .onSuccess { _snackbarEvents.tryEmit(it) }
                .onFailure { _snackbarEvents.tryEmit(it.message ?: "Could not simulate week") }
        }
    }

    fun launchNextSeason() {
        viewModelScope.launch {
            repository.startNextFantasySeason()
                .onSuccess { _snackbarEvents.tryEmit(it) }
        }
    }

    // --- Settings ---

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.updateThemeMode(mode) }
    }

    fun saveScoringWeights(weights: ScoringWeights) {
        viewModelScope.launch {
            repository.updateScoringWeights(weights)
            _snackbarEvents.tryEmit("Fantasy scoring weights updated across all players.")
        }
    }

    fun saveEconomySettings(config: EconomyConfig, recalculateCurrentTreasury: Boolean) {
        viewModelScope.launch {
            repository.updateEconomySettings(config, recalculateCurrentTreasury)
                .onSuccess { _snackbarEvents.tryEmit(it) }
        }
    }

    fun setOfflineSimEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateOfflineSimToggle(enabled)
        }
    }

    fun getSavedOptionalApiKey(): String = repository.getOptionalExternalApiKey()

    fun saveOptionalApiKey(key: String) {
        repository.saveOptionalExternalApiKey(key)
        _snackbarEvents.tryEmit(
            if (key.isBlank()) "Cleared optional external API key."
            else "Saved API key in Android Keystore EncryptedSharedPreferences."
        )
    }

    companion object {
        fun factory(repository: NhlFantasyRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(repository) as T
                }
            }
    }
}
