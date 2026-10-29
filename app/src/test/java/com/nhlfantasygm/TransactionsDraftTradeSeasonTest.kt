package com.nhlfantasygm

import com.nhlfantasygm.data.local.DraftPickEntity
import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.domain.*
import com.nhlfantasygm.model.*
import org.junit.Assert.*
import org.junit.Test

class TransactionsDraftTradeSeasonTest {

    private fun makeTeam(
        id: String = "USER",
        name: String = "Northern Ice HC",
        isHuman: Boolean = true,
        cash: Long = 100_000_000L
    ) = FantasyTeamEntity(
        teamId = id,
        teamName = name,
        abbreviation = id.take(3),
        emblemId = TeamEmblem.MAPLE_SHIELD.id,
        primaryColorHex = 0xFF00C2FF,
        secondaryColorHex = 0xFF0B192C,
        isHuman = isHuman,
        cashBalanceCad = cash,
        startingBudgetCad = 100_000_000L
    )

    private fun makePlayer(
        id: Long,
        name: String,
        pos: String,
        ovr: Int = 85,
        price: Long = 6_000_000L,
        capHit: Long = 5_500_000L,
        ownerId: String? = null,
        injury: String = "Healthy"
    ): PlayerEntity {
        val parts = name.split(" ")
        return PlayerEntity(
            playerId = id,
            firstName = parts.first(),
            lastName = parts.last(),
            fullName = name,
            normalizedSearchName = TextNormalizer.normalizeForMatch(name),
            teamAbbrev = "EDM",
            teamFullName = "Edmonton Oilers",
            positionCode = pos,
            sweaterNumber = 97,
            age = 26,
            birthDate = "1999-01-13",
            birthCountry = "CAN",
            shootsCatches = "L",
            heightInches = 73,
            weightPounds = 195,
            headshotUrl = "",
            isProspect = false,
            isRookie = false,
            seasonId = 20262027L,
            gamesPlayed = 14,
            goals = if (pos == "G") 0 else 9,
            assists = if (pos == "G") 0 else 12,
            points = if (pos == "G") 0 else 21,
            plusMinus = 6,
            penaltyMinutes = 4,
            powerPlayGoals = 3,
            powerPlayPoints = 7,
            shorthandedPoints = 0,
            shots = if (pos == "G") 0 else 48,
            hits = if (pos == "G") 0 else 12,
            blockedShots = if (pos == "G") 0 else 9,
            avgToiSeconds = 1250,
            faceoffWinPct = 0.52,
            gamesStarted = if (pos == "G") 10 else 0,
            wins = if (pos == "G") 7 else 0,
            losses = if (pos == "G") 2 else 0,
            otLosses = if (pos == "G") 1 else 0,
            saves = if (pos == "G") 270 else 0,
            shotsAgainst = if (pos == "G") 295 else 0,
            goalsAgainst = if (pos == "G") 25 else 0,
            savePct = if (pos == "G") 0.915 else 0.0,
            goalsAgainstAverage = if (pos == "G") 2.45 else 0.0,
            shutouts = if (pos == "G") 1 else 0,
            prevSeasonId = 20252026L,
            prevGamesPlayed = 78,
            prevGoals = 35,
            prevAssists = 55,
            prevPoints = 90,
            prevPpPoints = 28,
            prevShots = 250,
            prevHits = 45,
            prevBlockedShots = 40,
            prevWins = if (pos == "G") 32 else 0,
            prevSaves = if (pos == "G") 1550 else 0,
            prevSavePct = if (pos == "G") 0.914 else 0.0,
            prevGaa = if (pos == "G") 2.50 else 0.0,
            prevShutouts = if (pos == "G") 3 else 0,
            injuryStatus = injury,
            overallRating = ovr,
            purchasePriceCad = price,
            sellPriceCad = (price * 0.96).toLong(),
            capHitCad = capHit,
            ownerFantasyTeamId = ownerId
        )
    }

    @Test
    fun `transaction engine prevents overspending duplicate ownership and salary cap violations`() {
        val economy = EconomyConfig(
            startingBudgetCad = 100_000_000L,
            salaryCapCad = 95_500_000L,
            enforceSalaryCap = true,
            expensiveTransactionThresholdCad = 5_000_000L
        )
        val poorTeam = makeTeam(cash = 3_000_000L)
        val star = makePlayer(1L, "Connor McDavid", "C", ovr = 97, price = 16_000_000L, capHit = 13_500_000L)

        // 1. Prevent overspending
        val overspendRes = GameTransactionEngine.validatePlayerPurchase(poorTeam, emptyList(), star, economy)
        assertTrue(overspendRes is TransactionValidationResult.Invalid)

        // 2. Prevent duplicate ownership (already owned by user or CPU)
        val richTeam = makeTeam(cash = 100_000_000L)
        val alreadyUserOwned = star.copy(ownerFantasyTeamId = "USER")
        assertTrue(GameTransactionEngine.validatePlayerPurchase(richTeam, listOf(alreadyUserOwned), alreadyUserOwned, economy) is TransactionValidationResult.Invalid)

        val cpuOwned = star.copy(ownerFantasyTeamId = "CPU_1")
        assertTrue(GameTransactionEngine.validatePlayerPurchase(richTeam, emptyList(), cpuOwned, economy) is TransactionValidationResult.Invalid)

        // 3. Prevent salary cap violation
        val highPayrollRoster = (1..10).map {
            makePlayer(100L + it, "Cap Skater$it", "C", price = 5_000_000L, capHit = 9_000_000L, ownerId = "USER")
        } // $90M payroll
        val capExceedRes = GameTransactionEngine.validatePlayerPurchase(richTeam, highPayrollRoster, star, economy)
        assertTrue("Purchase exceeding $95.5M cap must be blocked", capExceedRes is TransactionValidationResult.Invalid)

        // 4. Valid high-value purchase triggers confirmation flag
        val validRes = GameTransactionEngine.validatePlayerPurchase(richTeam, emptyList(), star, economy)
        assertTrue(validRes is TransactionValidationResult.Valid)
        assertTrue((validRes as TransactionValidationResult.Valid).requiresHighValueConfirmation)
    }

    @Test
    fun `draft engine handles snake order mandatory positions cpu picks and undo target`() {
        val teams = listOf("CPU_1", "USER", "CPU_2", "CPU_3")
        // Round 1 Pick 2 -> USER; Round 2 Snake Pick 7 (3rd in Rd 2) -> USER
        val (r1, p1, t1) = DraftEngine.getPickMetadata(2, teams, isSnakeDraft = true)
        assertEquals(1, r1)
        assertEquals(2, p1)
        assertEquals("USER", t1)

        val (r2, p2, t2) = DraftEngine.getPickMetadata(7, teams, isSnakeDraft = true)
        assertEquals(2, r2)
        assertEquals(3, p2)
        assertEquals("USER", t2)

        // CPU GM picks highest-need legal player
        val cfg = DraftConfig(
            teamCount = 4,
            requiredCenters = 1,
            requiredLeftWings = 1,
            requiredRightWings = 1,
            requiredDefensemen = 1,
            requiredGoalies = 1,
            benchSlots = 0
        ) // 5 total rounds, 1 of each position mandatory!

        val cpuTeam = makeTeam(id = "CPU_1", isHuman = false)
        val existingRoster = listOf(
            makePlayer(10L, "Center One", "C", ownerId = "CPU_1"),
            makePlayer(11L, "Left Wing", "L", ownerId = "CPU_1"),
            makePlayer(12L, "Right Wing", "R", ownerId = "CPU_1"),
            makePlayer(13L, "Defense One", "D", ownerId = "CPU_1")
        )
        // Only 1 pick left and CPU still needs 1 mandatory Goalie!
        val extraCenter = makePlayer(20L, "Extra Center", "C", ovr = 95)
        val neededGoalie = makePlayer(21L, "Needed Goalie", "G", ovr = 84)

        // Validating extraCenter must fail because 1 pick left and Goalie is still unmet!
        assertNotNull(DraftEngine.validateDraftPick(cpuTeam, existingRoster, extraCenter, cfg))
        assertNull(DraftEngine.validateDraftPick(cpuTeam, existingRoster, neededGoalie, cfg))

        val cpuChoice = DraftEngine.selectBestCpuPick(cpuTeam, existingRoster, listOf(extraCenter, neededGoalie), cfg)
        assertNotNull(cpuChoice)
        assertEquals("CPU must draft mandatory Goalie need", 21L, cpuChoice!!.player.playerId)

        // Undo target rolls back to last human pick
        val history = listOf(
            DraftPickEntity(1, 1, 1, "CPU_1", "CPU 1", false, 1L, "P1", "C", "EDM", 90, 8_000_000L, 9_000_000L, "", 1L),
            DraftPickEntity(2, 1, 2, "USER", "User", true, 2L, "P2", "C", "TOR", 89, 7_500_000L, 8_500_000L, "", 2L),
            DraftPickEntity(3, 1, 3, "CPU_2", "CPU 2", false, 3L, "P3", "D", "COL", 88, 7_000_000L, 8_000_000L, "", 3L)
        )
        assertEquals(2, DraftEngine.findUndoTargetOverallPick(history))
    }

    @Test
    fun `lineup analytics prevents invalid slot assignments`() {
        val goalie = makePlayer(50L, "Connor Hellebuyck", "G", ovr = 93, ownerId = "USER")
        val injuredCenter = makePlayer(51L, "Injured Center", "C", ovr = 86, ownerId = "USER", injury = "Injured Reserve")
        val healthyCenter = makePlayer(52L, "Healthy Center", "C", ovr = 84, ownerId = "USER")

        // Goalie cannot play 1st Line Center
        assertNotNull(LineupAnalyticsEngine.validateSlotAssignment(goalie, LineupSlot.C1, null))
        // Goalie CAN play Starting Goalie G1
        assertNull(LineupAnalyticsEngine.validateSlotAssignment(goalie, LineupSlot.G1, null))
        // Injured Reserve player cannot be placed in active starting lineup C1
        assertNotNull(LineupAnalyticsEngine.validateSlotAssignment(injuredCenter, LineupSlot.C1, null))
        // Injured Reserve player CAN be placed in IR1
        assertNull(LineupAnalyticsEngine.validateSlotAssignment(injuredCenter, LineupSlot.IR1, null))
        // Healthy player CANNOT be placed in IR1
        assertNotNull(LineupAnalyticsEngine.validateSlotAssignment(healthyCenter, LineupSlot.IR1, null))
    }

    @Test
    fun `trade evaluator enforces budget cap and fairness rules`() {
        val economy = EconomyConfig()
        val user = makeTeam("USER", cash = 20_000_000L)
        val cpu = makeTeam("CPU_1", "Montréal Voyageurs", isHuman = false, cash = 20_000_000L)

        val userPlayer = makePlayer(60L, "Star Wing", "L", ovr = 87, price = 7_500_000L, capHit = 6_500_000L, ownerId = "USER")
        val cpuPlayer = makePlayer(61L, "Star Defense", "D", ovr = 87, price = 7_500_000L, capHit = 6_500_000L, ownerId = "CPU_1")
        val cpuGoalie1 = makePlayer(62L, "CPU Starter", "G", ovr = 84, ownerId = "CPU_1")
        val cpuGoalie2 = makePlayer(63L, "CPU Backup", "G", ovr = 79, ownerId = "CPU_1")

        // Fair 1-for-1 + modest cash boot should be accepted
        val fairVerdict = TradeEvaluationEngine.evaluateTradeForCpu(
            proposerTeam = user,
            proposerRoster = listOf(userPlayer),
            receiverCpuTeam = cpu,
            receiverRoster = listOf(cpuPlayer, cpuGoalie1, cpuGoalie2),
            offeredPlayers = listOf(userPlayer),
            offeredCashCad = 500_000L,
            requestedPlayers = listOf(cpuPlayer),
            requestedCashCad = 0L,
            economyConfig = economy
        )
        assertTrue(fairVerdict.isRuleValid)
        assertTrue("Fair trade addressing CPU defense/wing balance should be accepted", fairVerdict.acceptedByCpu)

        // Offering more cash than user has must be rejected as rule-invalid
        val negativeCashVerdict = TradeEvaluationEngine.evaluateTradeForCpu(
            proposerTeam = user,
            proposerRoster = listOf(userPlayer),
            receiverCpuTeam = cpu,
            receiverRoster = listOf(cpuPlayer, cpuGoalie1),
            offeredPlayers = listOf(userPlayer),
            offeredCashCad = 50_000_000L,
            requestedPlayers = listOf(cpuPlayer),
            requestedCashCad = 0L,
            economyConfig = economy
        )
        assertFalse(negativeCashVerdict.isRuleValid)
    }

    @Test
    fun `season simulation generates schedule calculates real-stat matchups and playoff bracket`() {
        val teams = listOf(
            makeTeam("USER", "User HC", true),
            makeTeam("CPU_1", "CPU 1", false),
            makeTeam("CPU_2", "CPU 2", false),
            makeTeam("CPU_3", "CPU 3", false)
        )
        val schedule = SeasonSimulationEngine.generateRegularSeasonSchedule(1, teams)
        // 4 teams = 2 games per week * 14 weeks = 28 matchups
        assertEquals(28, schedule.size)

        val roster = listOf(
            makePlayer(70L, "Top Center", "C", ovr = 90, ownerId = "USER"),
            makePlayer(71L, "Top Defense", "D", ovr = 88, ownerId = "USER"),
            makePlayer(72L, "Top Goalie", "G", ovr = 87, ownerId = "USER")
        )
        val realScore = SeasonSimulationEngine.calculateTeamWeeklyMatchupScore(
            teamId = "USER",
            weekNumber = 1,
            roster = roster,
            weights = ScoringWeights(),
            forceOfflineSim = false
        )
        assertTrue(realScore.usedRealNhlStats)
        assertTrue("Active NHL starters must generate positive weekly fantasy score", realScore.score > 20.0)

        val semis = SeasonSimulationEngine.generatePlayoffSemifinals(1, teams)
        assertEquals(2, semis.size)
        assertEquals("SEMIFINAL", semis.first().stage)
    }
}
