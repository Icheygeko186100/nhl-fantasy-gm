package com.nhlfantasygm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nhlfantasygm.ui.LeagueSectionTab
import com.nhlfantasygm.ui.MainViewModel
import com.nhlfantasygm.ui.components.ExpensiveTransactionDialog
import com.nhlfantasygm.ui.screens.*
import com.nhlfantasygm.ui.theme.NhlFantasyGmTheme
import kotlinx.coroutines.flow.MutableStateFlow

enum class BottomNavDestination(val index: Int, val label: String, val icon: ImageVector) {
    HOME(0, "Home", Icons.Default.Dashboard),
    DRAFT(1, "Draft", Icons.Default.HowToVote),
    MARKETPLACE(2, "Marketplace", Icons.Default.Storefront),
    MY_TEAM(3, "My Team", Icons.Default.Groups),
    LEAGUE(4, "League", Icons.Default.EmojiEvents)
}

class MainActivity : ComponentActivity() {

    private val incomingDeepLinkUriFlow = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        intent?.dataString?.takeIf { it.isNotBlank() }?.let {
            incomingDeepLinkUriFlow.value = it
        }

        val app = application as NhlFantasyApplication

        setContent {
            val vm: MainViewModel = viewModel(
                factory = MainViewModel.factory(app.repository, app.multiplayerCoordinator)
            )
            val pendingDeepLink by incomingDeepLinkUriFlow.collectAsStateWithLifecycle()

            LaunchedEffect(pendingDeepLink) {
                val uri = pendingDeepLink
                if (!uri.isNullOrBlank()) {
                    vm.handleIncomingInviteDeepLink(uri)
                    incomingDeepLinkUriFlow.value = null
                }
            }

            NhlFantasyGmAppRoot(vm)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.takeIf { it.isNotBlank() }?.let {
            incomingDeepLinkUriFlow.value = it
        }
    }
}

@Composable
fun NhlFantasyGmAppRoot(vm: MainViewModel) {
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val humanTeam by vm.humanTeamState.collectAsStateWithLifecycle()
    val controlledTeamId by vm.controlledTeamIdState.collectAsStateWithLifecycle()
    val mpState by vm.multiplayerState.collectAsStateWithLifecycle()
    val fantasyTeams by vm.fantasyTeamsState.collectAsStateWithLifecycle()
    val nhlTeams by vm.nhlTeamsState.collectAsStateWithLifecycle()
    val allScoredPlayers by vm.allScoredPlayersState.collectAsStateWithLifecycle()
    val filteredMarketPlayers by vm.filteredMarketplacePlayersState.collectAsStateWithLifecycle()
    val marketFilter by vm.marketFilter.collectAsStateWithLifecycle()
    val userRosterScored by vm.userRosterScoredState.collectAsStateWithLifecycle()
    val rosterAnalytics by vm.userRosterAnalyticsState.collectAsStateWithLifecycle()
    val financials by vm.userFinancialSummaryState.collectAsStateWithLifecycle()
    val transactions by vm.transactionsState.collectAsStateWithLifecycle()
    val draftSession by vm.draftSessionState.collectAsStateWithLifecycle()
    val draftPicks by vm.draftPicksState.collectAsStateWithLifecycle()
    val draftTimerSec by vm.draftTimerRemainingSec.collectAsStateWithLifecycle()
    val tradeProposals by vm.tradeProposalsState.collectAsStateWithLifecycle()
    val matchups by vm.matchupsState.collectAsStateWithLifecycle()
    val seasonArchives by vm.seasonArchivesState.collectAsStateWithLifecycle()
    val liveGames by vm.liveGamesState.collectAsStateWithLifecycle()
    val newsHeadlines by vm.newsHeadlinesState.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatusState.collectAsStateWithLifecycle()
    val freshness by vm.dataFreshnessState.collectAsStateWithLifecycle()
    val isSyncing by vm.isSyncing.collectAsStateWithLifecycle()
    val leagueTab by vm.leagueTab.collectAsStateWithLifecycle()
    val pendingConfirm by vm.pendingConfirmation.collectAsStateWithLifecycle()
    val selectedPlayerDetail by vm.selectedPlayerDetail.collectAsStateWithLifecycle()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        }
    }

    LaunchedEffect(vm) {
        vm.navigationRequests.collect { tabIdx ->
            selectedTabIndex = tabIdx
        }
    }

    NhlFantasyGmTheme(themeMode = settings.themeMode) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    BottomNavDestination.entries.forEach { dest ->
                        val selected = selectedTabIndex == dest.index
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedTabIndex = dest.index },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = {
                                Text(
                                    text = dest.label,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTabIndex) {
                    0 -> HomeScreen(
                        humanTeam = humanTeam,
                        financialSummary = financials,
                        rosterAnalytics = rosterAnalytics,
                        freshnessState = freshness,
                        syncStatus = syncStatus,
                        isSyncing = isSyncing,
                        liveGames = liveGames,
                        allScoredPlayers = allScoredPlayers,
                        fantasyTeams = fantasyTeams,
                        matchups = matchups,
                        newsHeadlines = newsHeadlines,
                        mpState = mpState,
                        onRefreshData = { vm.triggerFullSync(silentIfCached = false) },
                        onPlayerClick = { vm.openPlayerDetail(it) },
                        onNavigateMainTab = { selectedTabIndex = it },
                        onNavigateLeagueSubTab = { vm.setLeagueTab(it) },
                        onSimulateNextWeek = { vm.simulateNextWeek() }
                    )
                    1 -> DraftScreen(
                        draftSession = draftSession,
                        draftConfig = vm.repository.decodeDraftConfig(draftSession),
                        draftPicks = draftPicks,
                        fantasyTeams = fantasyTeams,
                        humanTeam = humanTeam,
                        controlledTeamId = controlledTeamId,
                        mpState = mpState,
                        allScoredPlayers = allScoredPlayers,
                        timerRemainingSeconds = draftTimerSec,
                        onStartDraft = { vm.startDraft(it) },
                        onMakeHumanPick = { vm.draftPlayerForHuman(it) },
                        onAutoPickHuman = { vm.autoPickHumanTurn() },
                        onUndoLastPick = { vm.undoLastDraftPick() },
                        onSimRestOfDraft = { vm.simRemainingDraftToCompletion() },
                        onPlayerClick = { vm.openPlayerDetail(it) },
                        onGoToMyTeam = { selectedTabIndex = 3 },
                        onHostMultiplayerRoom = { gmName, cfg -> vm.hostMultiplayerLobby(gmName, cfg) },
                        onJoinMultiplayerRoom = { code, gm, team, abbr, emb, pHex, sHex ->
                            vm.joinMultiplayerLobby(code, gm, team, abbr, emb, pHex, sHex)
                        },
                        onBuildMultiplayerShareText = { vm.buildMultiplayerInviteShareText() },
                        onAddLocalPassAndPlayFriend = { gm, team, abbr, emb, pHex, sHex ->
                            vm.addLocalPassAndPlayFriendToLobby(gm, team, abbr, emb, pHex, sHex)
                        },
                        onRemoveLobbyParticipant = { vm.removeLobbyParticipant(it) },
                        onMoveLobbyParticipant = { id, up -> vm.moveMultiplayerParticipantOrder(id, up) },
                        onRandomizeLobbyOrder = { vm.randomizeMultiplayerLobbyOrder() },
                        onUpdateHostLobbyRules = { cfg, fillCpu -> vm.updateMultiplayerLobbyRules(cfg, fillCpu) },
                        onUpdateGuestLobbyProfile = { gm, team, abbr, emb, pHex, sHex, ready ->
                            vm.updateGuestLobbyProfileAndReady(gm, team, abbr, emb, pHex, sHex, ready)
                        },
                        onSendMultiplayerChat = { vm.sendMultiplayerChatMessage(it) },
                        onMarkMultiplayerChatRead = { vm.markMultiplayerChatRead() },
                        onLaunchMultiplayerDraft = { vm.launchMultiplayerDraftFromLobby() },
                        onLeaveMultiplayerRoom = { vm.leaveMultiplayerRoom() }
                    )
                    2 -> MarketplaceScreen(
                        players = filteredMarketPlayers,
                        nhlTeams = nhlTeams,
                        humanTeam = humanTeam,
                        filterState = marketFilter,
                        isSyncing = isSyncing,
                        onUpdateFilter = { vm.updateMarketFilter(it) },
                        onPlayerClick = { vm.openPlayerDetail(it) },
                        onBuyClick = { vm.requestBuyPlayer(it) },
                        onSellClick = { vm.requestSellPlayer(it) },
                        onWatchlistToggle = { vm.toggleWatchlist(it) }
                    )
                    3 -> MyTeamScreen(
                        humanTeam = humanTeam,
                        rosterScored = userRosterScored,
                        analytics = rosterAnalytics,
                        financials = financials,
                        transactions = transactions,
                        onPlayerClick = { vm.openPlayerDetail(it) },
                        onSellPlayer = { vm.requestSellPlayer(it) },
                        onAutoSetLineup = { vm.autoSetOptimalLineup() },
                        onAssignSlot = { pid, slot -> vm.assignLineupSlot(pid, slot) },
                        onSaveBranding = { n, a, e, p, s -> vm.saveFranchiseCustomization(n, a, e, p, s) },
                        onGoToMarketplace = { selectedTabIndex = 2 }
                    )
                    4 -> LeagueScreen(
                        activeTab = leagueTab,
                        onSelectTab = { vm.setLeagueTab(it) },
                        fantasyTeams = fantasyTeams,
                        humanTeam = humanTeam,
                        controlledTeamId = controlledTeamId,
                        mpState = mpState,
                        matchups = matchups,
                        seasonArchives = seasonArchives,
                        tradeProposals = tradeProposals,
                        allScoredPlayers = allScoredPlayers,
                        nhlTeams = nhlTeams,
                        liveGames = liveGames,
                        settings = settings,
                        isSyncing = isSyncing,
                        savedOptionalApiKey = vm.getSavedOptionalApiKey(),
                        onSimulateNextWeek = { vm.simulateNextWeek() },
                        onStartNextSeason = { vm.launchNextSeason() },
                        onProposeTrade = { targetTeamId, off, offC, req, reqC ->
                            vm.submitTradeProposal(targetTeamId, off, offC, req, reqC)
                        },
                        onSolicitCpuOffer = { vm.solicitCpuTradeOffer() },
                        onRespondToTrade = { id, acc -> vm.respondToTradeOffer(id, acc) },
                        onRefreshLiveScores = { vm.refreshLiveScores() },
                        onPlayerClick = { vm.openPlayerDetail(it) },
                        onSaveTheme = { vm.setThemeMode(it) },
                        onSaveEconomy = { cfg, recalc -> vm.saveEconomySettings(cfg, recalc) },
                        onSaveScoring = { vm.saveScoringWeights(it) },
                        onToggleOfflineSim = { vm.setOfflineSimEnabled(it) },
                        onSaveOptionalApiKey = { vm.saveOptionalApiKey(it) },
                        onSaveCustomRelayUrl = { vm.saveCustomMultiplayerRelayUrl(it) },
                        onLeaveMultiplayerRoom = { vm.leaveMultiplayerRoom() }
                    )
                }
            }
        }

        // High-Value Transaction Confirmation Dialog
        pendingConfirm?.let { pending ->
            ExpensiveTransactionDialog(
                player = pending.player,
                warningText = pending.warningText,
                isSelling = pending.isSelling,
                onConfirm = { vm.confirmPendingTransaction() },
                onDismiss = { vm.dismissPendingTransaction() }
            )
        }

        // Full Player Scouting & Stats Profile Dialog
        selectedPlayerDetail?.let { detail ->
            PlayerDetailDialog(
                detail = detail,
                controlledTeamId = controlledTeamId,
                onDismiss = { vm.closePlayerDetail() },
                onBuyClick = { vm.requestBuyPlayer(detail.player) },
                onSellClick = { vm.requestSellPlayer(detail.player) },
                onToggleWatchlist = { vm.toggleWatchlist(detail.player.playerId) },
                onJumpToTradeWithOwner = {
                    vm.closePlayerDetail()
                    vm.setLeagueTab(LeagueSectionTab.TRADES)
                    selectedTabIndex = 4
                }
            )
        }
    }
}
