package com.nhlfantasygm.domain

import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.model.CadFormatter
import com.nhlfantasygm.model.EconomyConfig
import com.nhlfantasygm.model.Position
import com.nhlfantasygm.model.ScoringWeights
import kotlin.math.roundToInt

data class TradeEvaluationVerdict(
    val isRuleValid: Boolean,
    val acceptedByCpu: Boolean,
    val fairnessScore: Int, // 0..100 (50+ fair for CPU)
    val offeredPackageValueCad: Long,
    val requestedPackageValueCad: Long,
    val gmReasoning: String
)

data class GeneratedCpuOffer(
    val cpuTeam: FantasyTeamEntity,
    val cpuOfferedPlayers: List<PlayerEntity>,
    val cpuOfferedCashCad: Long,
    val userRequestedPlayers: List<PlayerEntity>,
    val userRequestedCashCad: Long,
    val fairnessScore: Int,
    val pitchMessage: String
)

object TradeEvaluationEngine {

    /**
     * Evaluates a proposed trade from [proposerTeam] to [receiverCpuTeam].
     * Enforces all financial, salary cap, roster limit, and ownership invariants first,
     * then models CPU GM valuation (quality, positional needs, age upside, budget).
     */
    fun evaluateTradeForCpu(
        proposerTeam: FantasyTeamEntity,
        proposerRoster: List<PlayerEntity>,
        receiverCpuTeam: FantasyTeamEntity,
        receiverRoster: List<PlayerEntity>,
        offeredPlayers: List<PlayerEntity>,
        offeredCashCad: Long,
        requestedPlayers: List<PlayerEntity>,
        requestedCashCad: Long,
        economyConfig: EconomyConfig,
        scoringWeights: ScoringWeights = ScoringWeights()
    ): TradeEvaluationVerdict {
        if (offeredPlayers.isEmpty() && requestedPlayers.isEmpty()) {
            return invalid("Select at least one NHL player to trade.")
        }
        if (offeredCashCad < 0L || requestedCashCad < 0L) {
            return invalid("Cash amounts in trades cannot be negative.")
        }
        if (offeredPlayers.any { it.ownerFantasyTeamId != proposerTeam.teamId }) {
            return invalid("One or more offered players are no longer owned by ${proposerTeam.teamName}.")
        }
        if (requestedPlayers.any { it.ownerFantasyTeamId != receiverCpuTeam.teamId }) {
            return invalid("One or more requested players are no longer owned by ${receiverCpuTeam.teamName}.")
        }
        if (offeredPlayers.map { it.playerId }.intersect(requestedPlayers.map { it.playerId }.toSet()).isNotEmpty()) {
            return invalid("Duplicate player reference detected in trade package.")
        }

        // Cash balance validation (no negative balances ever allowed)
        val proposerCashAfter = proposerTeam.cashBalanceCad - offeredCashCad + requestedCashCad
        if (proposerCashAfter < 0L) {
            return invalid("${proposerTeam.teamName} lacks sufficient virtual CAD for ${CadFormatter.formatCompact(offeredCashCad)} cash offer.")
        }
        val receiverCashAfter = receiverCpuTeam.cashBalanceCad - requestedCashCad + offeredCashCad
        if (receiverCashAfter < 0L) {
            return invalid("${receiverCpuTeam.teamName} only has ${CadFormatter.formatCompact(receiverCpuTeam.cashBalanceCad)} virtual CAD available.")
        }

        // Roster size limit validation
        val proposerCountAfter = proposerRoster.size - offeredPlayers.size + requestedPlayers.size
        if (proposerCountAfter > economyConfig.maxRosterSize) {
            return invalid("Trade would put ${proposerTeam.teamName} over the ${economyConfig.maxRosterSize}-man roster limit ($proposerCountAfter).")
        }
        val receiverCountAfter = receiverRoster.size - requestedPlayers.size + offeredPlayers.size
        if (receiverCountAfter > economyConfig.maxRosterSize) {
            return invalid("Trade would put ${receiverCpuTeam.teamName} over the ${economyConfig.maxRosterSize}-man roster limit ($receiverCountAfter).")
        }

        // Minimum goalie protection for CPU (won't trade away their last remaining goalie without getting one back)
        val cpuGoaliesBefore = receiverRoster.count { it.position == Position.GOALIE }
        val cpuGoaliesAfter = cpuGoaliesBefore -
            requestedPlayers.count { it.position == Position.GOALIE } +
            offeredPlayers.count { it.position == Position.GOALIE }
        if (cpuGoaliesBefore > 0 && cpuGoaliesAfter == 0) {
            return TradeEvaluationVerdict(
                isRuleValid = true,
                acceptedByCpu = false,
                fairnessScore = 18,
                offeredPackageValueCad = offeredPlayers.sumOf { it.purchasePriceCad } + offeredCashCad,
                requestedPackageValueCad = requestedPlayers.sumOf { it.purchasePriceCad } + requestedCashCad,
                gmReasoning = "${receiverCpuTeam.teamName} GM declined: \"Trading away our last goaltender leaves our crease empty. Include a goalie in return.\""
            )
        }

        // Salary Cap validation
        if (economyConfig.enforceSalaryCap) {
            val proposerCapAfter = proposerRoster.sumOf { it.capHitCad } -
                offeredPlayers.sumOf { it.capHitCad } + requestedPlayers.sumOf { it.capHitCad }
            if (proposerCapAfter > economyConfig.salaryCapCad) {
                val over = proposerCapAfter - economyConfig.salaryCapCad
                return invalid("Trade puts ${proposerTeam.teamName} over the Salary Cap by ${CadFormatter.formatCompact(over)}.")
            }

            val receiverCapAfter = receiverRoster.sumOf { it.capHitCad } -
                requestedPlayers.sumOf { it.capHitCad } + offeredPlayers.sumOf { it.capHitCad }
            if (receiverCapAfter > economyConfig.salaryCapCad) {
                val over = receiverCapAfter - economyConfig.salaryCapCad
                return invalid("Trade puts ${receiverCpuTeam.teamName} over the Salary Cap by ${CadFormatter.formatCompact(over)}.")
            }
        }

        // CPU GM Value Appraisal (Player quality + Positional Fit + Age + Fantasy Points + Cash)
        val cpuNeeds = computePositionalNeeds(receiverRoster)

        fun appraiseForCpu(player: PlayerEntity, isIncomingToCpu: Boolean): Long {
            val fantasy = FantasyScoringEngine.summarizePlayer(player, scoringWeights)
            val needMult = if (isIncomingToCpu) {
                when {
                    (cpuNeeds[player.position] ?: 0) >= 2 -> 1.14
                    (cpuNeeds[player.position] ?: 0) == 1 -> 1.07
                    else -> 0.97
                }
            } else {
                // Trading away from a thin position costs extra for the CPU
                val countAtPos = receiverRoster.count { it.position == player.position }
                if (countAtPos <= 2) 1.12 else 1.00
            }
            val ageMult = when {
                player.age <= 24 -> 1.07
                player.age >= 34 -> 0.92
                else -> 1.00
            }
            val starPremium = if (player.overallRating >= 90) 1.10 else if (player.overallRating >= 86) 1.04 else 1.00
            val fantasyBonusCad = (fantasy.pointsPerGame * 180_000.0).toLong()
            return ((player.purchasePriceCad + fantasyBonusCad) * needMult * ageMult * starPremium).toLong()
        }

        // Quantity dilution discount (trading three 74-OVR depth skaters for one 91-OVR superstar is penalized realistically!)
        val maxIncomingOvr = offeredPlayers.maxOfOrNull { it.overallRating } ?: 0
        val maxOutgoingOvr = requestedPlayers.maxOfOrNull { it.overallRating } ?: 0
        val starGapPenaltyFactor = if (offeredPlayers.size > requestedPlayers.size && maxOutgoingOvr - maxIncomingOvr >= 7) {
            0.84
        } else {
            1.00
        }

        val incomingValueToCpu = (offeredPlayers.sumOf { appraiseForCpu(it, true) } * starGapPenaltyFactor).toLong() + offeredCashCad
        val outgoingValueFromCpu = requestedPlayers.sumOf { appraiseForCpu(it, false) } + requestedCashCad

        val ratio = if (outgoingValueFromCpu <= 0L) 2.0 else incomingValueToCpu.toDouble() / outgoingValueFromCpu.toDouble()
        val fairnessScore = (ratio * 52.0).roundToInt().coerceIn(5, 99)
        val accepted = ratio >= 0.96

        val reasoning = when {
            accepted && ratio >= 1.12 ->
                "${receiverCpuTeam.teamName} GM accepted enthusiastically: strong upgrade in franchise asset value and positional fit."
            accepted ->
                "${receiverCpuTeam.teamName} GM accepted: fair value swap addressing roster balance under the salary cap."
            maxOutgoingOvr - maxIncomingOvr >= 7 && offeredPlayers.size > requestedPlayers.size ->
                "${receiverCpuTeam.teamName} GM declined: \"We won't move an elite ${maxOutgoingOvr}-OVR cornerstone for a multi-player depth package without higher-end talent or more virtual CAD.\""
            else -> {
                val gapCad = (outgoingValueFromCpu * 0.97 - incomingValueToCpu).toLong().coerceAtLeast(250_000L)
                "${receiverCpuTeam.teamName} GM declined: package falls short by ~${CadFormatter.formatCompact(gapCad)} in perceived GM value."
            }
        }

        return TradeEvaluationVerdict(
            isRuleValid = true,
            acceptedByCpu = accepted,
            fairnessScore = fairnessScore,
            offeredPackageValueCad = incomingValueToCpu,
            requestedPackageValueCad = outgoingValueFromCpu,
            gmReasoning = reasoning
        )
    }

    /**
     * Generates a sensible incoming trade offer from a CPU team looking to swap from its positional
     * surplus for a player on the user's roster of comparable value.
     */
    fun generateIncomingCpuProposal(
        userTeam: FantasyTeamEntity,
        userRoster: List<PlayerEntity>,
        cpuTeams: List<FantasyTeamEntity>,
        rostersByTeamId: Map<String, List<PlayerEntity>>,
        economyConfig: EconomyConfig
    ): GeneratedCpuOffer? {
        if (userRoster.size < 3 || cpuTeams.isEmpty()) return null

        val userNeeds = computePositionalNeeds(userRoster)
        val targetPositionForUser = userNeeds.maxByOrNull { it.value }?.key ?: Position.DEFENSE

        for (cpu in cpuTeams.shuffled()) {
            val cpuRoster = rostersByTeamId[cpu.teamId].orEmpty()
            if (cpuRoster.size < 4) continue

            val cpuCandidate = cpuRoster
                .filter { it.position == targetPositionForUser && it.overallRating in 76..89 }
                .ifEmpty { cpuRoster.filter { it.overallRating in 76..89 } }
                .randomOrNull() ?: continue

            val userTarget = userRoster
                .filter { it.position != cpuCandidate.position }
                .minByOrNull { kotlin.math.abs(it.purchasePriceCad - cpuCandidate.purchasePriceCad) }
                ?: continue

            val diffCad = userTarget.purchasePriceCad - cpuCandidate.purchasePriceCad
            val cpuCashBoot = if (diffCad > 250_000L && cpu.cashBalanceCad >= diffCad) {
                ((diffCad + 25_000L) / 50_000L) * 50_000L
            } else 0L

            val userCashBoot = if (diffCad < -350_000L && userTeam.cashBalanceCad >= -diffCad) {
                ((-diffCad) / 50_000L) * 50_000L
            } else 0L

            // Verify salary cap legality for both sides
            if (economyConfig.enforceSalaryCap) {
                val userCapAfter = userRoster.sumOf { it.capHitCad } - userTarget.capHitCad + cpuCandidate.capHitCad
                val cpuCapAfter = cpuRoster.sumOf { it.capHitCad } - cpuCandidate.capHitCad + userTarget.capHitCad
                if (userCapAfter > economyConfig.salaryCapCad || cpuCapAfter > economyConfig.salaryCapCad) {
                    continue
                }
            }

            val pitch = "${cpu.teamName} offers ${cpuCandidate.fullName} (${cpuCandidate.position.uiShort}, ${cpuCandidate.overallRating} OVR)" +
                (if (cpuCashBoot > 0) " + ${CadFormatter.formatCompact(cpuCashBoot)}" else "") +
                " for ${userTarget.fullName} (${userTarget.position.uiShort}, ${userTarget.overallRating} OVR)" +
                (if (userCashBoot > 0) " + ${CadFormatter.formatCompact(userCashBoot)}" else "") +
                " to balance positional depth."

            return GeneratedCpuOffer(
                cpuTeam = cpu,
                cpuOfferedPlayers = listOf(cpuCandidate),
                cpuOfferedCashCad = cpuCashBoot,
                userRequestedPlayers = listOf(userTarget),
                userRequestedCashCad = userCashBoot,
                fairnessScore = 54,
                pitchMessage = pitch
            )
        }
        return null
    }

    private fun computePositionalNeeds(roster: List<PlayerEntity>): Map<Position, Int> {
        return mapOf(
            Position.CENTER to maxOf(0, 4 - roster.count { it.position == Position.CENTER }),
            Position.LEFT_WING to maxOf(0, 4 - roster.count { it.position == Position.LEFT_WING }),
            Position.RIGHT_WING to maxOf(0, 4 - roster.count { it.position == Position.RIGHT_WING }),
            Position.DEFENSE to maxOf(0, 6 - roster.count { it.position == Position.DEFENSE }),
            Position.GOALIE to maxOf(0, 2 - roster.count { it.position == Position.GOALIE })
        )
    }

    private fun invalid(reason: String) = TradeEvaluationVerdict(
        isRuleValid = false,
        acceptedByCpu = false,
        fairnessScore = 0,
        offeredPackageValueCad = 0L,
        requestedPackageValueCad = 0L,
        gmReasoning = reason
    )
}
