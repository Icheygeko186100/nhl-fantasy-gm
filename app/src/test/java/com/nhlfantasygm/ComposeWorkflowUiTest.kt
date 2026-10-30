package com.nhlfantasygm

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nhlfantasygm.data.local.DraftSessionEntity
import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.data.local.NhlTeamEntity
import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.data.local.UserAppSettings
import com.nhlfantasygm.domain.FantasyScoringEngine
import com.nhlfantasygm.model.DraftConfig
import com.nhlfantasygm.model.ScoringWeights
import com.nhlfantasygm.model.TeamEmblem
import com.nhlfantasygm.multiplayer.MultiplayerConnectionState
import com.nhlfantasygm.multiplayer.MultiplayerRoomPhase
import com.nhlfantasygm.multiplayer.MultiplayerUiState
import com.nhlfantasygm.multiplayer.RoomCrypto
import com.nhlfantasygm.multiplayer.RoomRole
import com.nhlfantasygm.ui.LeagueSectionTab
import com.nhlfantasygm.ui.MarketplaceFilterState
import com.nhlfantasygm.ui.ScoredPlayerItem
import com.nhlfantasygm.ui.screens.DraftScreen
import com.nhlfantasygm.ui.screens.LeagueScreen
import com.nhlfantasygm.ui.screens.MarketplaceScreen
import com.nhlfantasygm.ui.screens.MultiplayerDraftHeroCard
import com.nhlfantasygm.ui.theme.NhlFantasyGmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposeWorkflowUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `marketplace screen renders real player data handles search input and triggers buy callback`() {
        val mcdavid = PlayerEntity(
            playerId = 8478402L,
            firstName = "Connor",
            lastName = "McDavid",
            fullName = "Connor McDavid",
            normalizedSearchName = "connor mcdavid",
            teamAbbrev = "EDM",
            teamFullName = "Edmonton Oilers",
            positionCode = "C",
            sweaterNumber = 97,
            age = 29,
            birthDate = "1997-01-13",
            birthCountry = "CAN",
            shootsCatches = "L",
            heightInches = 73,
            weightPounds = 194,
            headshotUrl = "",
            isProspect = false,
            isRookie = false,
            seasonId = 20262027L,
            gamesPlayed = 14,
            goals = 10,
            assists = 16,
            points = 26,
            plusMinus = 8,
            penaltyMinutes = 6,
            powerPlayGoals = 4,
            powerPlayPoints = 10,
            shorthandedPoints = 0,
            shots = 52,
            hits = 12,
            blockedShots = 7,
            avgToiSeconds = 1320,
            faceoffWinPct = 0.53,
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
            prevSeasonId = 20252026L,
            prevGamesPlayed = 76,
            prevGoals = 38,
            prevAssists = 92,
            prevPoints = 130,
            prevPpPoints = 44,
            prevShots = 265,
            prevHits = 45,
            prevBlockedShots = 30,
            prevWins = 0,
            prevSaves = 0,
            prevSavePct = 0.0,
            prevGaa = 0.0,
            prevShutouts = 0,
            overallRating = 97,
            purchasePriceCad = 16_500_000L,
            sellPriceCad = 15_840_000L,
            capHitCad = 13_500_000L
        )
        val scored = ScoredPlayerItem(
            player = mcdavid,
            fantasy = FantasyScoringEngine.summarizePlayer(mcdavid, ScoringWeights())
        )
        val userTeam = FantasyTeamEntity(
            teamId = "USER",
            teamName = "Northern Ice HC",
            abbreviation = "NIC",
            emblemId = TeamEmblem.MAPLE_SHIELD.id,
            primaryColorHex = 0xFF00C2FF,
            secondaryColorHex = 0xFF0B192C,
            isHuman = true,
            cashBalanceCad = 100_000_000L,
            startingBudgetCad = 100_000_000L
        )

        var boughtPlayerId = 0L

        composeTestRule.setContent {
            NhlFantasyGmTheme {
                MarketplaceScreen(
                    players = listOf(scored),
                    nhlTeams = listOf(
                        NhlTeamEntity("EDM", "Edmonton Oilers", "Oilers", "Edmonton", "Western", "Pacific", "", "", 14, 9, 4, 1, 19, 50, 38, "W", 3, 4)
                    ),
                    humanTeam = userTeam,
                    filterState = MarketplaceFilterState(),
                    isSyncing = false,
                    onUpdateFilter = {},
                    onPlayerClick = {},
                    onBuyClick = { boughtPlayerId = it.playerId },
                    onSellClick = {},
                    onWatchlistToggle = {}
                )
            }
        }

        composeTestRule.onNodeWithText("NHL Player Marketplace").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connor McDavid").assertIsDisplayed()
        composeTestRule.onNodeWithText("Buy").performClick()
        assertEquals(8478402L, boughtPlayerId)
    }

    @Test
    fun `multiplayer draft entry banner renders prominently in league screen and clicking navigates directly into multiplayer draft`() {
        var openMultiplayerDraftCalled = false

        composeTestRule.setContent {
            NhlFantasyGmTheme {
                LeagueScreen(
                    activeTab = LeagueSectionTab.MATCHUPS,
                    onSelectTab = {},
                    fantasyTeams = emptyList(),
                    humanTeam = null,
                    mpState = MultiplayerUiState(),
                    matchups = emptyList(),
                    seasonArchives = emptyList(),
                    tradeProposals = emptyList(),
                    allScoredPlayers = emptyList(),
                    nhlTeams = emptyList(),
                    liveGames = emptyList(),
                    settings = UserAppSettings(),
                    isSyncing = false,
                    savedOptionalApiKey = "",
                    onSimulateNextWeek = {},
                    onStartNextSeason = {},
                    onProposeTrade = { _, _, _, _, _ -> },
                    onSolicitCpuOffer = {},
                    onRespondToTrade = { _, _ -> },
                    onRefreshLiveScores = {},
                    onPlayerClick = {},
                    onSaveTheme = {},
                    onSaveEconomy = { _, _ -> },
                    onSaveScoring = {},
                    onToggleOfflineSim = {},
                    onSaveOptionalApiKey = {},
                    onOpenMultiplayerDraft = { openMultiplayerDraftCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithText("MULTIPLAYER DRAFT & LOBBY").assertIsDisplayed()
        composeTestRule.onNodeWithText("Play with Friends & Family").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter").performClick()
        assertTrue(openMultiplayerDraftCalled)
    }

    @Test
    fun `drafts tab in league screen displays multiplayer creation options and solo draft status`() {
        var openMultiplayerCalled = false
        var openSoloCalled = false

        composeTestRule.setContent {
            NhlFantasyGmTheme {
                LeagueScreen(
                    activeTab = LeagueSectionTab.DRAFTS,
                    onSelectTab = {},
                    fantasyTeams = emptyList(),
                    humanTeam = null,
                    mpState = MultiplayerUiState(),
                    matchups = emptyList(),
                    seasonArchives = emptyList(),
                    tradeProposals = emptyList(),
                    allScoredPlayers = emptyList(),
                    nhlTeams = emptyList(),
                    liveGames = emptyList(),
                    settings = UserAppSettings(),
                    isSyncing = false,
                    savedOptionalApiKey = "",
                    onSimulateNextWeek = {},
                    onStartNextSeason = {},
                    onProposeTrade = { _, _, _, _, _ -> },
                    onSolicitCpuOffer = {},
                    onRespondToTrade = { _, _ -> },
                    onRefreshLiveScores = {},
                    onPlayerClick = {},
                    onSaveTheme = {},
                    onSaveEconomy = { _, _ -> },
                    onSaveScoring = {},
                    onToggleOfflineSim = {},
                    onSaveOptionalApiKey = {},
                    onOpenMultiplayerDraft = { openMultiplayerCalled = true },
                    onOpenSoloDraft = { openSoloCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Create Draft").performClick()
        assertTrue(openMultiplayerCalled)

        composeTestRule.onNodeWithText("Start Solo Draft").performScrollTo().performClick()
        assertTrue(openSoloCalled)
    }

    @Test
    fun `active multiplayer room renders room code and lobby entry button in leagues section`() {
        var openLobbyCalled = false
        val participants = listOf(
            com.nhlfantasygm.multiplayer.LobbyParticipant(
                clientId = "host-1",
                gmName = "GM Connor",
                assignedTeamId = "USER",
                teamName = "Northern Ice HC",
                abbreviation = "NIC",
                emblemId = TeamEmblem.MAPLE_SHIELD.id,
                primaryColorHex = 0xFF00C2FF,
                secondaryColorHex = 0xFF0B192C,
                isHost = true
            ),
            com.nhlfantasygm.multiplayer.LobbyParticipant(
                clientId = "guest-2",
                gmName = "GM Leon",
                assignedTeamId = "MP_GUEST2",
                teamName = "Atlantic Frost HC",
                abbreviation = "AFH",
                emblemId = TeamEmblem.ARCTIC_WOLF.id,
                primaryColorHex = 0xFF00E676,
                secondaryColorHex = 0xFF071E18,
                isHost = false
            )
        )
        val activeMp = MultiplayerUiState(
            role = RoomRole.HOST,
            roomCode = "K9M4P2",
            connectionState = MultiplayerConnectionState.CONNECTED,
            participants = participants,
            phase = MultiplayerRoomPhase.LOBBY
        )

        composeTestRule.setContent {
            NhlFantasyGmTheme {
                MultiplayerDraftHeroCard(
                    mpState = activeMp,
                    onOpenMultiplayerDraft = { openLobbyCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithText("ROOM K9M-4P2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Host Commissioner • 2 Human GM(s)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lobby").performClick()
        assertTrue(openLobbyCalled)
    }

    @Test
    fun `draft screen with initialMultiplayerMode opens directly to multiplayer setup and lobby flow`() {
        composeTestRule.setContent {
            NhlFantasyGmTheme {
                DraftScreen(
                    draftSession = null,
                    draftConfig = DraftConfig(),
                    draftPicks = emptyList(),
                    fantasyTeams = emptyList(),
                    humanTeam = null,
                    mpState = MultiplayerUiState(),
                    allScoredPlayers = emptyList(),
                    timerRemainingSeconds = 60,
                    initialMultiplayerMode = true,
                    onStartDraft = {},
                    onMakeHumanPick = {},
                    onAutoPickHuman = {},
                    onUndoLastPick = {},
                    onSimRestOfDraft = {},
                    onPlayerClick = {},
                    onGoToMyTeam = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Multiplayer Friends").assertIsDisplayed()
        composeTestRule.onNodeWithText("Multiplayer Draft & League").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create Lobby & Get Invite Code")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `drafts tab in league screen clicking Enter Code triggers join mode callback`() {
        var requestedJoinMode: Boolean? = null

        composeTestRule.setContent {
            NhlFantasyGmTheme {
                LeagueScreen(
                    activeTab = LeagueSectionTab.DRAFTS,
                    onSelectTab = {},
                    fantasyTeams = emptyList(),
                    humanTeam = null,
                    mpState = MultiplayerUiState(),
                    matchups = emptyList(),
                    seasonArchives = emptyList(),
                    tradeProposals = emptyList(),
                    allScoredPlayers = emptyList(),
                    nhlTeams = emptyList(),
                    liveGames = emptyList(),
                    settings = UserAppSettings(),
                    isSyncing = false,
                    savedOptionalApiKey = "",
                    onSimulateNextWeek = {},
                    onStartNextSeason = {},
                    onProposeTrade = { _, _, _, _, _ -> },
                    onSolicitCpuOffer = {},
                    onRespondToTrade = { _, _ -> },
                    onRefreshLiveScores = {},
                    onPlayerClick = {},
                    onSaveTheme = {},
                    onSaveEconomy = { _, _ -> },
                    onSaveScoring = {},
                    onToggleOfflineSim = {},
                    onSaveOptionalApiKey = {},
                    onOpenMultiplayerDraft = { isJoin -> requestedJoinMode = isJoin }
                )
            }
        }

        composeTestRule.onNodeWithText("Enter Code").performClick()
        assertEquals(true, requestedJoinMode)
    }

    @Test
    fun `draft screen with initialJoinMode true opens directly to join room code input tab`() {
        composeTestRule.setContent {
            NhlFantasyGmTheme {
                DraftScreen(
                    draftSession = null,
                    draftConfig = DraftConfig(),
                    draftPicks = emptyList(),
                    fantasyTeams = emptyList(),
                    humanTeam = null,
                    mpState = MultiplayerUiState(),
                    allScoredPlayers = emptyList(),
                    timerRemainingSeconds = 60,
                    initialMultiplayerMode = true,
                    initialJoinMode = true,
                    onStartDraft = {},
                    onMakeHumanPick = {},
                    onAutoPickHuman = {},
                    onUndoLastPick = {},
                    onSimRestOfDraft = {},
                    onPlayerClick = {},
                    onGoToMyTeam = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Join Friend's Draft").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter Friend's Room Code or Invite Link").assertIsDisplayed()
    }

    @Test
    fun `draft screen with existing solo draft in progress and pending deep link opens multiplayer join view directly without flicker`() {
        val inProgressSoloDraft = DraftSessionEntity(
            sessionId = 1,
            status = "IN_PROGRESS",
            configJson = "{}",
            teamOrderCsv = "USER,CPU_1,CPU_2,CPU_3",
            currentOverallPick = 5,
            startedAtEpochMs = 1700000000000L
        )
        val deepLinkMpState = MultiplayerUiState(
            pendingDeepLinkCode = "K9M4P2"
        )

        composeTestRule.setContent {
            NhlFantasyGmTheme {
                DraftScreen(
                    draftSession = inProgressSoloDraft,
                    draftConfig = DraftConfig(),
                    draftPicks = emptyList(),
                    fantasyTeams = emptyList(),
                    humanTeam = null,
                    mpState = deepLinkMpState,
                    allScoredPlayers = emptyList(),
                    timerRemainingSeconds = 60,
                    initialMultiplayerMode = false,
                    onStartDraft = {},
                    onMakeHumanPick = {},
                    onAutoPickHuman = {},
                    onUndoLastPick = {},
                    onSimRestOfDraft = {},
                    onPlayerClick = {},
                    onGoToMyTeam = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Multiplayer Friends").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter Friend's Room Code or Invite Link").assertIsDisplayed()
    }

    @Test
    fun `active multiplayer room during live draft displays Draft Room button and navigates to active draft`() {
        val activeDraftMp = MultiplayerUiState(
            role = RoomRole.HOST,
            roomCode = "K9M4P2",
            connectionState = MultiplayerConnectionState.CONNECTED,
            phase = MultiplayerRoomPhase.DRAFT_IN_PROGRESS
        )

        composeTestRule.setContent {
            NhlFantasyGmTheme {
                MultiplayerDraftHeroCard(
                    mpState = activeDraftMp,
                    onOpenMultiplayerDraft = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Draft Room").assertIsDisplayed()
    }

    @Test
    fun `multiplayer draft hero card with pending invite displays invite room code and Join button and clicking triggers join mode callback`() {
        var requestedJoinMode: Boolean? = null
        val inviteMp = MultiplayerUiState(
            pendingDeepLinkCode = "K9M4P2"
        )

        composeTestRule.setContent {
            NhlFantasyGmTheme {
                LeagueScreen(
                    activeTab = LeagueSectionTab.MATCHUPS,
                    onSelectTab = {},
                    fantasyTeams = emptyList(),
                    humanTeam = null,
                    mpState = inviteMp,
                    matchups = emptyList(),
                    seasonArchives = emptyList(),
                    tradeProposals = emptyList(),
                    allScoredPlayers = emptyList(),
                    nhlTeams = emptyList(),
                    liveGames = emptyList(),
                    settings = UserAppSettings(),
                    isSyncing = false,
                    savedOptionalApiKey = "",
                    onSimulateNextWeek = {},
                    onStartNextSeason = {},
                    onProposeTrade = { _, _, _, _, _ -> },
                    onSolicitCpuOffer = {},
                    onRespondToTrade = { _, _ -> },
                    onRefreshLiveScores = {},
                    onPlayerClick = {},
                    onSaveTheme = {},
                    onSaveEconomy = { _, _ -> },
                    onSaveScoring = {},
                    onToggleOfflineSim = {},
                    onSaveOptionalApiKey = {},
                    onOpenMultiplayerDraft = { isJoin -> requestedJoinMode = isJoin }
                )
            }
        }

        composeTestRule.onNodeWithText("Invite: ROOM K9M-4P2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Join").performClick()
        assertEquals(true, requestedJoinMode)
    }

    @Test
    fun `draft screen with initialJoinMode false and pending deep link code respects explicit Create Draft action and displays Host tab`() {
        val pendingMpState = MultiplayerUiState(
            pendingDeepLinkCode = "K9M4P2"
        )

        composeTestRule.setContent {
            NhlFantasyGmTheme {
                DraftScreen(
                    draftSession = null,
                    draftConfig = DraftConfig(),
                    draftPicks = emptyList(),
                    fantasyTeams = emptyList(),
                    humanTeam = null,
                    mpState = pendingMpState,
                    allScoredPlayers = emptyList(),
                    timerRemainingSeconds = 60,
                    initialMultiplayerMode = true,
                    initialJoinMode = false,
                    onStartDraft = {},
                    onMakeHumanPick = {},
                    onAutoPickHuman = {},
                    onUndoLastPick = {},
                    onSimRestOfDraft = {},
                    onPlayerClick = {},
                    onGoToMyTeam = {}
                )
            }
        }

        // Must display Host & Invite Friends setup controls, not the Join room code input
        composeTestRule.onNodeWithText("Multiplayer Friends").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create Lobby & Get Invite Code")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `drafts section displays pending invite card when pending deep link code exists and clicking it triggers join mode`() {
        var requestedJoinMode: Boolean? = null
        val inviteMp = MultiplayerUiState(
            pendingDeepLinkCode = "K9M4P2"
        )

        composeTestRule.setContent {
            NhlFantasyGmTheme {
                LeagueScreen(
                    activeTab = LeagueSectionTab.DRAFTS,
                    onSelectTab = {},
                    fantasyTeams = emptyList(),
                    humanTeam = null,
                    mpState = inviteMp,
                    matchups = emptyList(),
                    seasonArchives = emptyList(),
                    tradeProposals = emptyList(),
                    allScoredPlayers = emptyList(),
                    nhlTeams = emptyList(),
                    liveGames = emptyList(),
                    settings = UserAppSettings(),
                    isSyncing = false,
                    savedOptionalApiKey = "",
                    onSimulateNextWeek = {},
                    onStartNextSeason = {},
                    onProposeTrade = { _, _, _, _, _ -> },
                    onSolicitCpuOffer = {},
                    onRespondToTrade = { _, _ -> },
                    onRefreshLiveScores = {},
                    onPlayerClick = {},
                    onSaveTheme = {},
                    onSaveEconomy = { _, _ -> },
                    onSaveScoring = {},
                    onToggleOfflineSim = {},
                    onSaveOptionalApiKey = {},
                    onOpenMultiplayerDraft = { isJoin -> requestedJoinMode = isJoin }
                )
            }
        }

        // Inside LeagueDraftsSection, the pending invite card should be displayed and clickable
        composeTestRule.onNodeWithText("Tap to join friends in multiplayer draft")
            .assertIsDisplayed()
            .performClick()
        assertEquals(true, requestedJoinMode)
    }

    @Test
    fun `hero card with active multiplayer room gives precedence to active room even if pending invite code exists`() {
        var requestedJoinMode: Boolean? = null
        val activeMpWithPendingLink = MultiplayerUiState(
            role = RoomRole.HOST,
            roomCode = "ACT123",
            connectionState = MultiplayerConnectionState.CONNECTED,
            phase = MultiplayerRoomPhase.LOBBY,
            pendingDeepLinkCode = "PEN789"
        )

        composeTestRule.setContent {
            NhlFantasyGmTheme {
                LeagueScreen(
                    activeTab = LeagueSectionTab.MATCHUPS,
                    onSelectTab = {},
                    fantasyTeams = emptyList(),
                    humanTeam = null,
                    mpState = activeMpWithPendingLink,
                    matchups = emptyList(),
                    seasonArchives = emptyList(),
                    tradeProposals = emptyList(),
                    allScoredPlayers = emptyList(),
                    nhlTeams = emptyList(),
                    liveGames = emptyList(),
                    settings = UserAppSettings(),
                    isSyncing = false,
                    savedOptionalApiKey = "",
                    onSimulateNextWeek = {},
                    onStartNextSeason = {},
                    onProposeTrade = { _, _, _, _, _ -> },
                    onSolicitCpuOffer = {},
                    onRespondToTrade = { _, _ -> },
                    onRefreshLiveScores = {},
                    onPlayerClick = {},
                    onSaveTheme = {},
                    onSaveEconomy = { _, _ -> },
                    onSaveScoring = {},
                    onToggleOfflineSim = {},
                    onSaveOptionalApiKey = {},
                    onOpenMultiplayerDraft = { isJoin -> requestedJoinMode = isJoin }
                )
            }
        }

        // Card should display active room, not the pending invite
        composeTestRule.onNodeWithText("ROOM ACT-123").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lobby").performClick()
        // Must open active room (isJoin = false), NOT join mode for PEN789
        assertEquals(false, requestedJoinMode)
    }
}

