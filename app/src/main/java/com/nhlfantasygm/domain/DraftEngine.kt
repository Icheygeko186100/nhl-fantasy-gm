package com.nhlfantasygm.domain

import com.nhlfantasygm.data.local.DraftPickEntity
import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.model.CadFormatter
import com.nhlfantasygm.model.DraftConfig
import com.nhlfantasygm.model.Position
import com.nhlfantasygm.model.ScoringWeights
import kotlin.math.max
import kotlin.math.roundToInt

data class CpuDraftChoice(
    val player: PlayerEntity,
    val rationale: String
)

data class TeamDraftReport(
    val teamId: String,
    val teamName: String,
    val isHuman: Boolean,
    val ownerType: String = if (isHuman) "HUMAN_GM" else "CPU_GM",
    val gmDisplayName: String = "",
    val picksCount: Int,
    val averageOvr: Int,
    val totalPayrollCad: Long,
    val bestPickName: String,
    val bestValueStealName: String,
    val letterGrade: String
)

object DraftEngine {

    /**
     * Computes which team (from ordered list `teamOrderIds`) owns 1-based `overallPick`.
     */
    fun getPickMetadata(
        overallPick: Int,
        teamOrderIds: List<String>,
        isSnakeDraft: Boolean
    ): Triple<Int, Int, String> {
        require(teamOrderIds.isNotEmpty()) { "Team order cannot be empty" }
        val teamCount = teamOrderIds.size
        val zeroBased = (overallPick - 1).coerceAtLeast(0)
        val roundNumber = (zeroBased / teamCount) + 1
        val pickInRound = (zeroBased % teamCount) + 1

        val indexInOrder = if (isSnakeDraft && roundNumber % 2 == 0) {
            teamCount - pickInRound
        } else {
            pickInRound - 1
        }
        return Triple(roundNumber, pickInRound, teamOrderIds[indexInOrder])
    }

    /**
     * Validates whether [team] can legally draft [candidate] under [config].
     * Ensures:
     * 1. Player is undrafted/unowned.
     * 2. Roster size limit is not exceeded.
     * 3. Mandatory positional requirements remain satisfiable with remaining draft slots.
     * 4. Salary cap and virtual budget are respected (leaving minimum $925K/slot for remaining picks!).
     */
    fun validateDraftPick(
        team: FantasyTeamEntity,
        teamCurrentRoster: List<PlayerEntity>,
        candidate: PlayerEntity,
        config: DraftConfig
    ): String? {
        if (candidate.ownerFantasyTeamId != null) {
            return "${candidate.fullName} has already been drafted."
        }
        val maxDraftRoster = config.totalRounds
        if (teamCurrentRoster.size >= maxDraftRoster) {
            return "${team.teamName} has already completed all $maxDraftRoster draft picks."
        }

        val remainingPicksAfterThis = (maxDraftRoster - teamCurrentRoster.size - 1).coerceAtLeast(0)
        val minReservePerRemainingPick = 925_000L * remainingPicksAfterThis

        // Virtual cash budget check
        if (candidate.purchasePriceCad + minReservePerRemainingPick > team.cashBalanceCad) {
            return "Budget exceeded: drafting ${candidate.fullName} (${CadFormatter.formatCompact(candidate.purchasePriceCad)}) leaves insufficient virtual CAD for remaining $remainingPicksAfterThis pick(s)."
        }

        // Salary cap check
        if (config.enforceSalaryCap) {
            val currentPayroll = teamCurrentRoster.sumOf { it.capHitCad }
            if (currentPayroll + candidate.capHitCad + minReservePerRemainingPick > config.salaryCapCad) {
                return "Salary cap exceeded: ${candidate.fullName} (${CadFormatter.formatCompact(candidate.capHitCad)} cap hit) leaves insufficient cap space for remaining $remainingPicksAfterThis pick(s)."
            }
        }

        // Positional requirement feasibility check
        val counts = mutableMapOf(
            Position.CENTER to teamCurrentRoster.count { it.position == Position.CENTER },
            Position.LEFT_WING to teamCurrentRoster.count { it.position == Position.LEFT_WING },
            Position.RIGHT_WING to teamCurrentRoster.count { it.position == Position.RIGHT_WING },
            Position.DEFENSE to teamCurrentRoster.count { it.position == Position.DEFENSE },
            Position.GOALIE to teamCurrentRoster.count { it.position == Position.GOALIE }
        )
        counts[candidate.position] = (counts[candidate.position] ?: 0) + 1

        val unmetMandatorySlotsAfterPick =
            max(0, config.requiredCenters - (counts[Position.CENTER] ?: 0)) +
                max(0, config.requiredLeftWings - (counts[Position.LEFT_WING] ?: 0)) +
                max(0, config.requiredRightWings - (counts[Position.RIGHT_WING] ?: 0)) +
                max(0, config.requiredDefensemen - (counts[Position.DEFENSE] ?: 0)) +
                max(0, config.requiredGoalies - (counts[Position.GOALIE] ?: 0))

        if (unmetMandatorySlotsAfterPick > remainingPicksAfterThis) {
            val neededPositions = buildNeededPositionsLabel(teamCurrentRoster, config)
            return "Roster requirement conflict: you only have ${remainingPicksAfterThis + 1} pick(s) left and must fill mandatory positions ($neededPositions)."
        }

        return null
    }

    /**
     * Smart CPU GM drafting AI: selects the highest-utility legal pick based on
     * player rating, real fantasy points, positional scarcity, roster needs, and budget/cap efficiency.
     */
    fun selectBestCpuPick(
        cpuTeam: FantasyTeamEntity,
        cpuCurrentRoster: List<PlayerEntity>,
        availablePool: List<PlayerEntity>,
        config: DraftConfig,
        scoringWeights: ScoringWeights = ScoringWeights()
    ): CpuDraftChoice? {
        val legalCandidates = availablePool.filter { candidate ->
            validateDraftPick(cpuTeam, cpuCurrentRoster, candidate, config) == null
        }

        val poolToEvaluate = legalCandidates.ifEmpty {
            // Emergency fallback if custom ultra-low cap/budget constrained every player: cheapest valid-position player
            availablePool.filter { it.ownerFantasyTeamId == null }.sortedBy { it.purchasePriceCad }.take(15)
        }
        if (poolToEvaluate.isEmpty()) return null

        val cCount = cpuCurrentRoster.count { it.position == Position.CENTER }
        val lwCount = cpuCurrentRoster.count { it.position == Position.LEFT_WING }
        val rwCount = cpuCurrentRoster.count { it.position == Position.RIGHT_WING }
        val dCount = cpuCurrentRoster.count { it.position == Position.DEFENSE }
        val gCount = cpuCurrentRoster.count { it.position == Position.GOALIE }

        // Scarcity: measure drop-off between #1 and #8 available at each position
        val scarcityByPos = Position.entries.associateWith { pos ->
            val topAtPos = poolToEvaluate.filter { it.position == pos }.sortedByDescending { it.overallRating }
            val first = topAtPos.firstOrNull()?.overallRating ?: 70
            val eighth = topAtPos.getOrNull(7)?.overallRating ?: 64
            (first - eighth).coerceIn(0, 12)
        }

        val remainingPicks = (config.totalRounds - cpuCurrentRoster.size).coerceAtLeast(1)
        val remainingCashPerSlot = cpuTeam.cashBalanceCad / remainingPicks

        var bestPlayer = poolToEvaluate.first()
        var bestScore = Double.NEGATIVE_INFINITY
        var bestReason = ""

        for (player in poolToEvaluate) {
            val needDeficit = when (player.position) {
                Position.CENTER -> config.requiredCenters - cCount
                Position.LEFT_WING -> config.requiredLeftWings - lwCount
                Position.RIGHT_WING -> config.requiredRightWings - rwCount
                Position.DEFENSE -> config.requiredDefensemen - dCount
                Position.GOALIE -> config.requiredGoalies - gCount
            }

            val needMultiplier = when {
                needDeficit >= 2 -> 1.16
                needDeficit == 1 -> 1.10
                player.position == Position.GOALIE && gCount >= config.requiredGoalies -> 0.72 // Don't hoard goalies
                else -> 0.96
            }

            val scarcityBonus = (scarcityByPos[player.position] ?: 2) * 0.65
            val fantasySummary = FantasyScoringEngine.summarizePlayer(player, scoringWeights)
            val fantasyBonus = fantasySummary.pointsPerGame * 2.4

            // Budget discipline: if remaining budget per pick is getting tight, prefer cost-efficient value
            val budgetFitBonus = if (remainingCashPerSlot < 3_500_000L) {
                max(-8.0, (3_500_000L - player.purchasePriceCad) / 450_000.0)
            } else 0.0

            val youthBonus = if (player.age <= 24) 1.5 else if (player.age >= 35) -1.2 else 0.0
            val injuryPenalty = if (player.injurySeverity.isUnavailableToPlay) -5.5 else 0.0

            val compositeScore = (player.overallRating * needMultiplier) +
                scarcityBonus + fantasyBonus + budgetFitBonus + youthBonus + injuryPenalty

            if (compositeScore > bestScore) {
                bestScore = compositeScore
                bestPlayer = player
                bestReason = when {
                    needDeficit > 0 && (scarcityByPos[player.position] ?: 0) >= 5 ->
                        "Targeted scarce ${player.position.uiShort} need (${player.overallRating} OVR)"
                    needDeficit > 0 ->
                        "Filled core ${player.position.displayName} slot (${player.overallRating} OVR, ${CadFormatter.formatShortNoSuffix(player.capHitCad)} cap)"
                    else ->
                        "Best player available for bench depth (${player.overallRating} OVR)"
                }
            }
        }

        return CpuDraftChoice(bestPlayer, bestReason)
    }

    /**
     * Determines which overallPick index to roll back to when the user presses "Undo".
     * Rolls back to the most recent human pick (undoing that human pick AND any subsequent auto-CPU picks).
     */
    fun findUndoTargetOverallPick(picks: List<DraftPickEntity>): Int? {
        return picks.lastOrNull { it.isHumanPick }?.overallPick
    }

    fun buildPostDraftSummary(
        teams: List<FantasyTeamEntity>,
        picks: List<DraftPickEntity>
    ): List<TeamDraftReport> {
        val reports = teams.map { team ->
            val teamPicks = picks.filter { it.fantasyTeamId == team.teamId }
            val avgOvr = if (teamPicks.isEmpty()) 0 else teamPicks.map { it.overallRating }.average().roundToInt()
            val payroll = teamPicks.sumOf { it.capHitCad }
            val bestPick = teamPicks.maxByOrNull { it.overallRating }
            val bestSteal = teamPicks.maxByOrNull { it.overallRating * 2 + it.roundNumber * 3 }

            Triple(team, avgOvr, TeamDraftReport(
                teamId = team.teamId,
                teamName = team.teamName,
                isHuman = team.isHuman,
                ownerType = team.ownerType,
                gmDisplayName = team.gmDisplayName,
                picksCount = teamPicks.size,
                averageOvr = avgOvr,
                totalPayrollCad = payroll,
                bestPickName = bestPick?.let { "${it.playerName} (${it.overallRating} OVR)" } ?: "—",
                bestValueStealName = bestSteal?.let { "${it.playerName} (Rd ${it.roundNumber})" } ?: "—",
                letterGrade = "B+"
            ))
        }

        val maxAvg = reports.maxOfOrNull { it.second } ?: 80
        return reports
            .map { (_, avg, report) ->
                val diff = maxAvg - avg
                val grade = when {
                    report.picksCount == 0 -> "N/A"
                    diff == 0 -> "A+"
                    diff <= 1 -> "A"
                    diff <= 2 -> "A-"
                    diff <= 3 -> "B+"
                    diff <= 5 -> "B"
                    else -> "B-"
                }
                report.copy(letterGrade = grade)
            }
            .sortedByDescending { it.averageOvr }
    }

    private fun buildNeededPositionsLabel(roster: List<PlayerEntity>, config: DraftConfig): String {
        val parts = mutableListOf<String>()
        val cNeed = config.requiredCenters - roster.count { it.position == Position.CENTER }
        val lwNeed = config.requiredLeftWings - roster.count { it.position == Position.LEFT_WING }
        val rwNeed = config.requiredRightWings - roster.count { it.position == Position.RIGHT_WING }
        val dNeed = config.requiredDefensemen - roster.count { it.position == Position.DEFENSE }
        val gNeed = config.requiredGoalies - roster.count { it.position == Position.GOALIE }
        if (cNeed > 0) parts.add("${cNeed}C")
        if (lwNeed > 0) parts.add("${lwNeed}LW")
        if (rwNeed > 0) parts.add("${rwNeed}RW")
        if (dNeed > 0) parts.add("${dNeed}D")
        if (gNeed > 0) parts.add("${gNeed}G")
        return parts.joinToString(", ")
    }
}
