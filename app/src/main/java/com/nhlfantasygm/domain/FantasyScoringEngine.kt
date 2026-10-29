package com.nhlfantasygm.domain

import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.model.PerformanceTrend
import com.nhlfantasygm.model.Position
import com.nhlfantasygm.model.ScoringBreakdownItem
import com.nhlfantasygm.model.ScoringWeights
import kotlin.math.max
import kotlin.math.round

data class PlayerFantasyScoreSummary(
    val playerId: Long,
    val dailyPoints: Double,
    val weeklyPoints: Double,
    val seasonPoints: Double,
    val pointsPerGame: Double,
    val valuePerMillionCad: Double,
    val trend: PerformanceTrend,
    val isBreakoutCandidate: Boolean,
    val breakoutReason: String,
    val breakdown: List<ScoringBreakdownItem>
)

object FantasyScoringEngine {

    fun calculateSeasonBreakdown(
        player: PlayerEntity,
        weights: ScoringWeights
    ): List<ScoringBreakdownItem> {
        val usePriorSeasonFallback = player.gamesPlayed == 0 && player.prevGamesPlayed > 0 && !player.isProspect
        return if (player.position == Position.GOALIE) {
            val w = if (usePriorSeasonFallback) player.prevWins else player.wins
            val sv = if (usePriorSeasonFallback) player.prevSaves else player.saves
            val so = if (usePriorSeasonFallback) player.prevShutouts else player.shutouts
            val ga = if (usePriorSeasonFallback) 0 else player.goalsAgainst
            buildList {
                add(item("Goalie Wins", w.toDouble(), weights.goalieWins))
                add(item("Saves", sv.toDouble(), weights.goalieSaves))
                add(item("Shutouts", so.toDouble(), weights.goalieShutouts))
                if (weights.goalieGoalsAgainst != 0.0) {
                    add(item("Goals Against", ga.toDouble(), weights.goalieGoalsAgainst))
                }
            }
        } else {
            val g = if (usePriorSeasonFallback) player.prevGoals else player.goals
            val a = if (usePriorSeasonFallback) player.prevAssists else player.assists
            val ppp = if (usePriorSeasonFallback) player.prevPpPoints else player.powerPlayPoints
            val sog = if (usePriorSeasonFallback) player.prevShots else player.shots
            val hit = if (usePriorSeasonFallback) player.prevHits else player.hits
            val blk = if (usePriorSeasonFallback) player.prevBlockedShots else player.blockedShots
            buildList {
                add(item("Goals", g.toDouble(), weights.goals))
                add(item("Assists", a.toDouble(), weights.assists))
                add(item("Power-Play Points", ppp.toDouble(), weights.powerPlayPoints))
                add(item("Shots on Goal", sog.toDouble(), weights.shotsOnGoal))
                add(item("Hits", hit.toDouble(), weights.hits))
                add(item("Blocked Shots", blk.toDouble(), weights.blockedShots))
                if (weights.penaltyMinutes != 0.0 && !usePriorSeasonFallback) {
                    add(item("Penalty Minutes", player.penaltyMinutes.toDouble(), weights.penaltyMinutes))
                }
                if (weights.plusMinus != 0.0 && !usePriorSeasonFallback) {
                    add(item("Plus/Minus", player.plusMinus.toDouble(), weights.plusMinus))
                }
            }
        }
    }

    fun calculateSeasonPoints(player: PlayerEntity, weights: ScoringWeights): Double {
        if (player.gamesPlayed == 0) return 0.0
        val raw = if (player.position == Position.GOALIE) {
            player.wins * weights.goalieWins +
                player.saves * weights.goalieSaves +
                player.shutouts * weights.goalieShutouts +
                player.goalsAgainst * weights.goalieGoalsAgainst
        } else {
            player.goals * weights.goals +
                player.assists * weights.assists +
                player.powerPlayPoints * weights.powerPlayPoints +
                player.shots * weights.shotsOnGoal +
                player.hits * weights.hits +
                player.blockedShots * weights.blockedShots +
                player.penaltyMinutes * weights.penaltyMinutes +
                player.plusMinus * weights.plusMinus
        }
        return round1(raw)
    }

    fun calculatePriorSeasonPoints(player: PlayerEntity, weights: ScoringWeights): Double {
        if (player.prevGamesPlayed == 0) return 0.0
        val raw = if (player.position == Position.GOALIE) {
            player.prevWins * weights.goalieWins +
                player.prevSaves * weights.goalieSaves +
                player.prevShutouts * weights.goalieShutouts
        } else {
            player.prevGoals * weights.goals +
                player.prevAssists * weights.assists +
                player.prevPpPoints * weights.powerPlayPoints +
                player.prevShots * weights.shotsOnGoal +
                player.prevHits * weights.hits +
                player.prevBlockedShots * weights.blockedShots
        }
        return round1(raw)
    }

    fun calculateDailyLivePoints(player: PlayerEntity, weights: ScoringWeights): Double {
        if (player.todayGameId == 0L) return 0.0
        val raw = if (player.position == Position.GOALIE) {
            player.todayGoalieWin * weights.goalieWins +
                player.todayGoalieSaves * weights.goalieSaves +
                player.todayGoalieShutout * weights.goalieShutouts
        } else {
            player.todayGoals * weights.goals +
                player.todayAssists * weights.assists +
                player.todayPpPoints * weights.powerPlayPoints +
                player.todayShots * weights.shotsOnGoal +
                player.todayHits * weights.hits +
                player.todayBlocks * weights.blockedShots
        }
        return round1(raw)
    }

    fun summarizePlayer(player: PlayerEntity, weights: ScoringWeights): PlayerFantasyScoreSummary {
        val currentSeasonPts = calculateSeasonPoints(player, weights)
        val priorSeasonPts = calculatePriorSeasonPoints(player, weights)
        val effectiveSeasonPts = if (player.gamesPlayed > 0) currentSeasonPts else priorSeasonPts
        val effectiveGp = when {
            player.gamesPlayed > 0 -> player.gamesPlayed
            player.prevGamesPlayed > 0 -> player.prevGamesPlayed
            else -> 0
        }

        val fppg = if (effectiveGp > 0) round1(effectiveSeasonPts / effectiveGp.toDouble()) else 0.0
        val dailyPts = calculateDailyLivePoints(player, weights)

        // Typical NHL team plays ~3.2 games per week (goalies start ~2.1 games/week)
        val expectedWeeklyGames = if (player.position == Position.GOALIE) 2.1 else 3.2
        val baseWeekly = if (player.gamesPlayed > 0) fppg * minOf(expectedWeeklyGames, player.gamesPlayed.toDouble()) else fppg * expectedWeeklyGames * 0.75
        val weeklyPts = round1(max(dailyPts, baseWeekly))

        val priceMillions = max(0.75, player.purchasePriceCad / 1_000_000.0)
        val valuePerMillion = round1(effectiveSeasonPts / priceMillions)

        val priorFppg = if (player.prevGamesPlayed >= 5) priorSeasonPts / player.prevGamesPlayed.toDouble() else fppg
        val currentFppg = if (player.gamesPlayed > 0) currentSeasonPts / player.gamesPlayed.toDouble() else 0.0

        val trend = when {
            dailyPts >= 6.0 || (player.gamesPlayed >= 3 && currentFppg >= priorFppg * 1.25 && currentFppg >= 4.0) -> PerformanceTrend.HOT
            player.gamesPlayed >= 3 && currentFppg > priorFppg * 1.08 -> PerformanceTrend.RISING
            player.gamesPlayed >= 4 && priorFppg > 2.5 && currentFppg < priorFppg * 0.78 -> PerformanceTrend.COOLING
            else -> PerformanceTrend.STEADY
        }

        val (isBreakout, breakoutReason) = evaluateBreakout(player, currentFppg, priorFppg)
        val breakdown = calculateSeasonBreakdown(player, weights)

        return PlayerFantasyScoreSummary(
            playerId = player.playerId,
            dailyPoints = dailyPts,
            weeklyPoints = weeklyPts,
            seasonPoints = currentSeasonPts,
            pointsPerGame = fppg,
            valuePerMillionCad = valuePerMillion,
            trend = trend,
            isBreakoutCandidate = isBreakout,
            breakoutReason = breakoutReason,
            breakdown = breakdown
        )
    }

    private fun evaluateBreakout(
        player: PlayerEntity,
        currentFppg: Double,
        priorFppg: Double
    ): Pair<Boolean, String> {
        if (player.age > 24) return false to ""
        if (player.position == Position.GOALIE) {
            if (player.gamesPlayed >= 3 && player.savePct >= 0.912) {
                return true to "Age ${player.age} goalie posting ${(player.savePct * 1000).toInt() / 10.0}% SV% across ${player.gamesPlayed} GP"
            }
            return false to ""
        }
        val currentPpg = if (player.gamesPlayed > 0) player.points.toDouble() / player.gamesPlayed else 0.0
        val priorPpg = if (player.prevGamesPlayed >= 10) player.prevPoints.toDouble() / player.prevGamesPlayed else 0.35

        return when {
            player.isRookie && player.gamesPlayed >= 3 && currentPpg >= 0.60 ->
                true to "Rookie age ${player.age} averaging ${round2(currentPpg)} PTS/GP (${player.points} PTS in ${player.gamesPlayed} GP)"
            player.gamesPlayed >= 4 && currentPpg >= 0.70 && currentPpg >= priorPpg * 1.22 ->
                true to "Age ${player.age} surge: ${round2(currentPpg)} PTS/GP (+${((currentPpg / max(0.2, priorPpg) - 1.0) * 100).toInt()}% vs prior season)"
            player.gamesPlayed >= 4 && currentFppg >= 4.2 && currentFppg >= priorFppg * 1.20 ->
                true to "Age ${player.age} multi-category breakout (${round1(currentFppg)} FP/GP with ${player.shots} SOG)"
            else -> false to ""
        }
    }

    private fun item(label: String, count: Double, weight: Double): ScoringBreakdownItem {
        return ScoringBreakdownItem(
            category = label,
            statCount = count,
            weight = weight,
            fantasyPoints = round1(count * weight)
        )
    }

    private fun round1(v: Double): Double = round(v * 10.0) / 10.0
    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
}
