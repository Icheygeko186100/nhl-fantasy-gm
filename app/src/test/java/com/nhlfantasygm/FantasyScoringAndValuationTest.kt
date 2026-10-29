package com.nhlfantasygm

import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.domain.FantasyScoringEngine
import com.nhlfantasygm.domain.PlayerValuationEngine
import com.nhlfantasygm.domain.TextNormalizer
import com.nhlfantasygm.model.EconomyConfig
import com.nhlfantasygm.model.ScoringWeights
import org.junit.Assert.*
import org.junit.Test

class FantasyScoringAndValuationTest {

    private fun sampleSkater(
        id: Long = 8478427L,
        name: String = "Sebastian Aho",
        pos: String = "C",
        age: Int = 28,
        gp: Int = 14,
        goals: Int = 8,
        assists: Int = 11,
        ppp: Int = 6,
        shots: Int = 43,
        hits: Int = 10,
        blocks: Int = 8,
        injuryStatus: String = "Healthy",
        isRookie: Boolean = false
    ): PlayerEntity {
        val parts = name.split(" ")
        return PlayerEntity(
            playerId = id,
            firstName = parts.first(),
            lastName = parts.last(),
            fullName = name,
            normalizedSearchName = TextNormalizer.normalizeForMatch(name),
            teamAbbrev = "CAR",
            teamFullName = "Carolina Hurricanes",
            positionCode = pos,
            sweaterNumber = 20,
            age = age,
            birthDate = "1997-07-26",
            birthCountry = "FIN",
            shootsCatches = "L",
            heightInches = 72,
            weightPounds = 189,
            headshotUrl = "",
            isProspect = false,
            isRookie = isRookie,
            seasonId = 20262027L,
            gamesPlayed = gp,
            goals = goals,
            assists = assists,
            points = goals + assists,
            plusMinus = 7,
            penaltyMinutes = 14,
            powerPlayGoals = 3,
            powerPlayPoints = ppp,
            shorthandedPoints = 2,
            shots = shots,
            hits = hits,
            blockedShots = blocks,
            avgToiSeconds = 1198,
            faceoffWinPct = 0.56,
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
            prevGamesPlayed = 79,
            prevGoals = 27,
            prevAssists = 53,
            prevPoints = 80,
            prevPpPoints = 27,
            prevShots = 195,
            prevHits = 45,
            prevBlockedShots = 32,
            prevWins = 0,
            prevSaves = 0,
            prevSavePct = 0.0,
            prevGaa = 0.0,
            prevShutouts = 0,
            todayGameId = 2026020189L,
            todayGameIsLive = true,
            todayOpponentLabel = "vs BOS",
            todayGoals = 1,
            todayAssists = 1,
            todayPpPoints = 1,
            todayShots = 5,
            todayHits = 2,
            todayBlocks = 1,
            injuryStatus = injuryStatus
        )
    }

    private fun sampleGoalie(
        wins: Int = 8,
        saves: Int = 146,
        shutouts: Int = 1,
        savePct: Double = 0.918
    ): PlayerEntity {
        return sampleSkater(
            id = 8481611L,
            name = "Pyotr Kochetkov",
            pos = "G",
            age = 26,
            gp = 8,
            goals = 0,
            assists = 0,
            ppp = 0,
            shots = 0,
            hits = 0,
            blocks = 0
        ).copy(
            gamesStarted = 8,
            wins = wins,
            saves = saves,
            shotsAgainst = 159,
            goalsAgainst = 13,
            savePct = savePct,
            goalsAgainstAverage = 1.62,
            shutouts = shutouts,
            todayGoalieWin = 1,
            todayGoalieSaves = 28,
            todayGoalieShutout = 0
        )
    }

    @Test
    fun `skater season and daily fantasy points match transparent scoring formula`() {
        val weights = ScoringWeights() // G=3, A=2, PPP=1, SOG=0.4, HIT=0.3, BLK=0.5
        val aho = sampleSkater()
        // Expected season: 8*3 + 11*2 + 6*1 + 43*0.4 + 10*0.3 + 8*0.5 = 24 + 22 + 6 + 17.2 + 3.0 + 4.0 = 76.2
        val seasonPts = FantasyScoringEngine.calculateSeasonPoints(aho, weights)
        assertEquals(76.2, seasonPts, 0.05)

        // Expected daily: 1*3 + 1*2 + 1*1 + 5*0.4 + 2*0.3 + 1*0.5 = 3 + 2 + 1 + 2.0 + 0.6 + 0.5 = 9.1
        val dailyPts = FantasyScoringEngine.calculateDailyLivePoints(aho, weights)
        assertEquals(9.1, dailyPts, 0.05)

        val summary = FantasyScoringEngine.summarizePlayer(aho, weights)
        assertEquals(6, summary.breakdown.size)
        assertEquals(76.2, summary.breakdown.sumOf { it.fantasyPoints }, 0.05)
    }

    @Test
    fun `custom user scoring weights immediately change fantasy points`() {
        val aho = sampleSkater()
        val defaultPts = FantasyScoringEngine.calculateSeasonPoints(aho, ScoringWeights())
        val heavyGoalWeights = ScoringWeights(goals = 5.0, assists = 3.0, hits = 1.0)
        val customPts = FantasyScoringEngine.calculateSeasonPoints(aho, heavyGoalWeights)
        assertTrue("Custom scoring weights must increase skater total", customPts > defaultPts + 25.0)
    }

    @Test
    fun `goalie fantasy scoring awards wins saves and shutouts accurately`() {
        val goalie = sampleGoalie(wins = 8, saves = 146, shutouts = 1)
        val weights = ScoringWeights() // W=4.0, SV=0.2, SO=3.0 -> 32 + 29.2 + 3.0 = 64.2
        val pts = FantasyScoringEngine.calculateSeasonPoints(goalie, weights)
        assertEquals(64.2, pts, 0.05)
    }

    @Test
    fun `valuation engine calculates higher value for healthy star vs injured veteran and explains reason`() {
        val economy = EconomyConfig()
        val healthyStar = sampleSkater()
        val injuredStar = healthyStar.copy(injuryStatus = "Injured Reserve", injuryType = "Lower Body")

        val healthyVal = PlayerValuationEngine.valuatePlayer(healthyStar, economy)
        val injuredVal = PlayerValuationEngine.valuatePlayer(injuredStar, economy)

        assertTrue("Elite center should rate >= 86 OVR", healthyVal.overallRating >= 86)
        assertTrue("Injured Reserve status must apply transparent market discount", injuredVal.purchasePriceCad < healthyVal.purchasePriceCad)
        assertTrue("Price explanation must cite Injured Reserve", injuredVal.priceChangeExplanation.contains("Injured Reserve", ignoreCase = true))
        assertTrue("Sell price must respect marketplace spread", healthyVal.sellPriceCad < healthyVal.purchasePriceCad)
    }

    @Test
    fun `diacritic normalization matches accented NHL names for search and injury feeds`() {
        assertEquals("montreal canadiens", TextNormalizer.normalizeForMatch("Montréal Canadiens"))
        assertEquals("tim stutzle", TextNormalizer.normalizeForMatch("Tim Stützle"))
    }
}
