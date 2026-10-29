package com.nhlfantasygm.domain

import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.model.InjurySeverity
import com.nhlfantasygm.model.LineupSlot
import com.nhlfantasygm.model.Position
import kotlin.math.roundToInt

data class TeamRosterAnalytics(
    val overallTeamRating: Int,
    val forwardsRating: Int,
    val defenseRating: Int,
    val goaltendingRating: Int,
    val activeStartersCount: Int,
    val totalRosterCount: Int,
    val vacanciesByPosition: Map<Position, Int>,
    val totalVacancies: Int,
    val bestPlayers: List<PlayerEntity>,
    val injuredPlayers: List<PlayerEntity>,
    val weakestPositionLabel: String,
    val strengths: List<String>,
    val weaknesses: List<String>
)

object LineupAnalyticsEngine {

    private val centerSlots = listOf(LineupSlot.C1, LineupSlot.C2, LineupSlot.C3, LineupSlot.C4)
    private val lwSlots = listOf(LineupSlot.LW1, LineupSlot.LW2, LineupSlot.LW3, LineupSlot.LW4)
    private val rwSlots = listOf(LineupSlot.RW1, LineupSlot.RW2, LineupSlot.RW3, LineupSlot.RW4)
    private val defSlots = listOf(
        LineupSlot.LD1, LineupSlot.RD1,
        LineupSlot.LD2, LineupSlot.RD2,
        LineupSlot.LD3, LineupSlot.RD3
    )
    private val goalieSlots = listOf(LineupSlot.G1, LineupSlot.G2)

    fun analyzeRoster(roster: List<PlayerEntity>): TeamRosterAnalytics {
        val centers = roster.filter { it.position == Position.CENTER }.sortedByDescending { it.overallRating }
        val leftWings = roster.filter { it.position == Position.LEFT_WING }.sortedByDescending { it.overallRating }
        val rightWings = roster.filter { it.position == Position.RIGHT_WING }.sortedByDescending { it.overallRating }
        val defensemen = roster.filter { it.position == Position.DEFENSE }.sortedByDescending { it.overallRating }
        val goalies = roster.filter { it.position == Position.GOALIE }.sortedByDescending { it.overallRating }

        val forwards = (centers + leftWings + rightWings).sortedByDescending { it.overallRating }

        val fwdRating = averageTopN(forwards, 12, emptySlotPenaltyFloor = 55)
        val defRating = averageTopN(defensemen, 6, emptySlotPenaltyFloor = 55)
        val gRating = if (goalies.isEmpty()) {
            0
        } else {
            val starter = goalies.first().overallRating
            val backup = goalies.getOrNull(1)?.overallRating ?: 60
            (starter * 0.72 + backup * 0.28).roundToInt()
        }

        val overall = if (roster.isEmpty()) {
            0
        } else {
            (fwdRating * 0.50 + defRating * 0.32 + gRating * 0.18).roundToInt().coerceIn(0, 99)
        }

        val vacancies = mapOf(
            Position.CENTER to maxOf(0, 4 - centers.size),
            Position.LEFT_WING to maxOf(0, 4 - leftWings.size),
            Position.RIGHT_WING to maxOf(0, 4 - rightWings.size),
            Position.DEFENSE to maxOf(0, 6 - defensemen.size),
            Position.GOALIE to maxOf(0, 2 - goalies.size)
        )

        val posAverages = mapOf(
            Position.CENTER to averageTopN(centers, 4, 52),
            Position.LEFT_WING to averageTopN(leftWings, 4, 52),
            Position.RIGHT_WING to averageTopN(rightWings, 4, 52),
            Position.DEFENSE to averageTopN(defensemen, 6, 52),
            Position.GOALIE to averageTopN(goalies, 2, 52)
        )

        val weakestEntry = posAverages.minByOrNull { it.value }
        val weakestLabel = if (roster.isEmpty()) {
            "All Positions Open (Empty Roster)"
        } else {
            "${weakestEntry?.key?.displayName ?: "Depth"} (${weakestEntry?.value ?: 0} Avg Rating)"
        }

        val injured = roster.filter { it.injurySeverity != InjurySeverity.HEALTHY }
        val best = roster.sortedByDescending { it.overallRating }.take(4)

        val strengths = buildList {
            if (centers.take(2).map { it.overallRating }.average() >= 86.0) {
                add("Elite Top-Six Center Depth (${centers.take(2).joinToString { it.lastName }})")
            }
            if (forwards.size >= 12 && fwdRating >= 82) {
                add("High-Octane Forward Group ($fwdRating Fwd OVR across 4 lines)")
            }
            if (defensemen.take(2).map { it.overallRating }.average() >= 85.0) {
                add("Shutdown 1st Defense Pairing (${defensemen.take(2).joinToString("/") { it.lastName }})")
            }
            if (goalies.firstOrNull()?.overallRating ?: 0 >= 86) {
                add("Franchise Starting Goaltender (${goalies.first().fullName}, ${goalies.first().overallRating} OVR)")
            }
            val rookiesCount = roster.count { it.isRookie || it.isProspect || it.age <= 23 }
            if (rookiesCount >= 4) {
                add("Strong Young Core ($rookiesCount players aged 23 or under on cost-controlled deals)")
            }
            if (isEmpty() && roster.isNotEmpty()) {
                add("Balanced salary distribution with room to upgrade key positions")
            }
        }

        val weaknesses = buildList {
            vacancies.forEach { (pos, missing) ->
                if (missing > 0) {
                    add("Missing $missing ${pos.displayName}${if (missing > 1) "s" else ""} for a complete 20-man NHL lineup")
                }
            }
            if (goalies.isEmpty()) {
                add("Critical: No goaltender on roster — sign or draft a #1 goalie immediately")
            } else if (goalies.first().overallRating < 79) {
                add("Below-average starting goaltending (${goalies.first().overallRating} OVR)")
            }
            if (defensemen.size >= 4 && defRating < 77) {
                add("Blue-line depth needs reinforcement ($defRating Def OVR)")
            }
            if (injured.isNotEmpty()) {
                add("${injured.size} roster player(s) currently injured or day-to-day (${injured.joinToString { it.lastName }})")
            }
            if (isEmpty() && roster.isNotEmpty()) {
                add("No major structural holes — monitor injury list and matchup trends")
            }
        }

        val activeStarters = roster.count { it.lineupSlot.isActiveStarter }

        return TeamRosterAnalytics(
            overallTeamRating = overall,
            forwardsRating = if (forwards.isEmpty()) 0 else fwdRating,
            defenseRating = if (defensemen.isEmpty()) 0 else defRating,
            goaltendingRating = gRating,
            activeStartersCount = activeStarters,
            totalRosterCount = roster.size,
            vacanciesByPosition = vacancies,
            totalVacancies = vacancies.values.sum(),
            bestPlayers = best,
            injuredPlayers = injured,
            weakestPositionLabel = weakestLabel,
            strengths = strengths,
            weaknesses = weaknesses
        )
    }

    /**
     * Generates a 100% legal recommended starting lineup:
     * - Healthy natural-position players fill C1-C4, LW1-LW4, RW1-RW4, LD1-RD3, G1-G2 by OVR.
     * - Out / IR players are placed into IR1-IR2 first so healthy skaters start.
     * - Remaining healthy extras fill BN1-BN3.
     */
    fun generateRecommendedLineup(roster: List<PlayerEntity>): List<PlayerEntity> {
        if (roster.isEmpty()) return emptyList()

        val assigned = mutableMapOf<Long, LineupSlot>()
        val usedSlots = mutableSetOf<LineupSlot>()

        // 1. Place OUT / IR players into IR slots first
        val unavailable = roster
            .filter { it.injurySeverity.isUnavailableToPlay }
            .sortedByDescending { it.overallRating }

        for (irSlot in LineupSlot.irSlots) {
            val candidate = unavailable.firstOrNull { !assigned.containsKey(it.playerId) } ?: break
            assigned[candidate.playerId] = irSlot
            usedSlots.add(irSlot)
        }

        // Healthy & Day-to-Day eligible starters sorted by OVR (healthy prioritized over DTD on ties)
        val eligiblePool = roster
            .filter { !assigned.containsKey(it.playerId) }
            .sortedWith(
                compareBy<PlayerEntity> { it.injurySeverity.isUnavailableToPlay }
                    .thenByDescending { it.overallRating }
                    .thenByDescending { it.points + it.wins * 4 }
            )

        fun assignPositionSlots(pos: Position, slots: List<LineupSlot>) {
            val candidates = eligiblePool.filter { it.position == pos && !assigned.containsKey(it.playerId) }
            slots.zip(candidates).forEach { (slot, player) ->
                assigned[player.playerId] = slot
                usedSlots.add(slot)
            }
        }

        assignPositionSlots(Position.CENTER, centerSlots)
        assignPositionSlots(Position.LEFT_WING, lwSlots)
        assignPositionSlots(Position.RIGHT_WING, rwSlots)
        assignPositionSlots(Position.DEFENSE, defSlots)
        assignPositionSlots(Position.GOALIE, goalieSlots)

        // Place remaining players on Bench slots BN1..BN3, or UNASSIGNED if >23
        val remaining = roster
            .filter { !assigned.containsKey(it.playerId) }
            .sortedByDescending { it.overallRating }

        for (player in remaining) {
            val openBench = LineupSlot.benchSlots.firstOrNull { !usedSlots.contains(it) }
            if (openBench != null) {
                assigned[player.playerId] = openBench
                usedSlots.add(openBench)
            } else {
                assigned[player.playerId] = LineupSlot.UNASSIGNED
            }
        }

        return roster.map { player ->
            player.copy(lineupSlotCode = (assigned[player.playerId] ?: LineupSlot.UNASSIGNED).code)
        }
    }

    /**
     * Validates assigning [player] into [targetSlot] (optionally swapping with current occupant).
     * Returns null if valid, or a clear error message explaining why the lineup move is illegal.
     */
    fun validateSlotAssignment(
        player: PlayerEntity,
        targetSlot: LineupSlot,
        currentOccupant: PlayerEntity?
    ): String? {
        if (!targetSlot.accepts(player.position, player.injurySeverity)) {
            return when (targetSlot) {
                LineupSlot.IR1, LineupSlot.IR2 ->
                    "${player.fullName} is Healthy and cannot be placed on Injured Reserve."
                else ->
                    "Invalid position: ${player.fullName} (${player.position.uiShort}) cannot play ${targetSlot.label}."
            }
        }

        if (player.injurySeverity.isUnavailableToPlay && targetSlot.isActiveStarter) {
            return "${player.fullName} is currently ${player.injuryStatus} and cannot be placed in the starting lineup."
        }

        // If swapping with an existing occupant, verify the occupant can legally move to player's old slot
        val originSlot = player.lineupSlot
        if (currentOccupant != null && originSlot != LineupSlot.UNASSIGNED) {
            if (!originSlot.accepts(currentOccupant.position, currentOccupant.injurySeverity)) {
                return "Cannot swap: ${currentOccupant.fullName} (${currentOccupant.position.uiShort}) is not eligible for ${originSlot.label}."
            }
            if (currentOccupant.injurySeverity.isUnavailableToPlay && originSlot.isActiveStarter) {
                return "Cannot move injured player ${currentOccupant.fullName} (${currentOccupant.injuryStatus}) into active slot ${originSlot.label}."
            }
        }

        return null
    }

    private fun averageTopN(players: List<PlayerEntity>, requiredCount: Int, emptySlotPenaltyFloor: Int): Int {
        if (players.isEmpty()) return 0
        val ratings = (0 until requiredCount).map { idx ->
            players.getOrNull(idx)?.overallRating ?: emptySlotPenaltyFloor
        }
        return ratings.average().roundToInt()
    }
}
