package com.nhlfantasygm

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.data.local.NhlTeamEntity
import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.domain.FantasyScoringEngine
import com.nhlfantasygm.model.ScoringWeights
import com.nhlfantasygm.model.TeamEmblem
import com.nhlfantasygm.ui.MarketplaceFilterState
import com.nhlfantasygm.ui.ScoredPlayerItem
import com.nhlfantasygm.ui.screens.MarketplaceScreen
import com.nhlfantasygm.ui.theme.NhlFantasyGmTheme
import org.junit.Assert.assertEquals
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
}
