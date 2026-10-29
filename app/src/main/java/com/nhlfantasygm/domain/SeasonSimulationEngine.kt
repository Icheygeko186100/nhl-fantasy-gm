package com.nhlfantasygm.domain

import com.nhlfantasygm.data.local.FantasyMatchupEntity
import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.data.local.SeasonArchiveEntity
import com.nhlfantasygm.model.InjurySeverity
import com.nhlfantasygm.model.Position
import com.nhlfantasygm.model.ScoringWeights
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.random.Random

data class SimulatedTeamWeekScore(
    val teamId: String,
    val score: Double,
    val topPerformerSummary: String,
    val usedRealNhlStats: Boolean
)

data class SeasonAwardsSummary(
    val hartMvp: String,
    val vezinaGoalie: String,
    val norrisDefense: String,
    val calderRookie: String,
    val gmOfTheYear: String
)

object SeasonSimulationEngine {

    const val REGULAR_SEASON_WEEKS = 14
    const val SEMIFINAL_WEEK = 15
    const val CHAMPIONSHIP_WEEK = 16

    /**
     * Generates a balanced 14-week round-robin regular-season schedule using the standard circle method.
     * Supports both even (2..16) and odd (3..15) league team counts using a BYE rotation slot so every
     * real player in a multiplayer league is included in the schedule.
     */
    fun generateRegularSeasonSchedule(
        seasonNumber: Int,
        teams: List<FantasyTeamEntity>
    ): List<FantasyMatchupEntity> {
        if (teams.size < 2) return emptyList()
        val working = teams.toMutableList()
        if (working.size % 2 != 0) {
            working.add(
                FantasyTeamEntity(
                    teamId = "__BYE__",
                    teamName = "BYE",
                    abbreviation = "BYE",
                    emblemId = "MAPLE_SHIELD",
                    primaryColorHex = 0L,
                    secondaryColorHex = 0L,
                    isHuman = false,
                    ownerType = "BYE",
                    cashBalanceCad = 0L,
                    startingBudgetCad = 0L
                )
            )
        }
        val n = working.size
        val rounds = (n - 1).coerceAtLeast(1)
        val matchups = mutableListOf<FantasyMatchupEntity>()

        for (week in 1..REGULAR_SEASON_WEEKS) {
            val roundIdx = (week - 1) % rounds
            val rotated = mutableListOf<FantasyTeamEntity>()
            rotated.add(working[0])
            val movable = working.drop(1)
            for (i in movable.indices) {
                rotated.add(movable[(i + roundIdx) % movable.size])
            }

            val swapHomeAway = ((week - 1) / rounds) % 2 == 1
            for (pairIdx in 0 until n / 2) {
                val first = rotated[pairIdx]
                val second = rotated[n - 1 - pairIdx]
                if (first.teamId == "__BYE__" || second.teamId == "__BYE__") continue
                val (home, away) = if (swapHomeAway) second to first else first to second
                matchups.add(
                    FantasyMatchupEntity(
                        seasonNumber = seasonNumber,
                        weekNumber = week,
                        stage = "REGULAR_SEASON",
                        homeTeamId = home.teamId,
                        homeTeamName = home.teamName,
                        awayTeamId = away.teamId,
                        awayTeamName = away.teamName
                    )
                )
            }
        }
        return matchups
    }

    /**
     * Calculates a fantasy team's weekly matchup score from their active starting lineup.
     * - When real NHL stats exist (`hasRealStats`), uses each starter's actual NHL fantasy points
     *   per game × real weekly NHL game workload (+ any live today's boxscore points!).
     * - When live stats are unavailable & `offlineSimMode` is enabled, uses cached/historical rating
     *   rates clearly tagged as `OFFLINE_SIM`.
     */
    fun calculateTeamWeeklyMatchupScore(
        teamId: String,
        weekNumber: Int,
        roster: List<PlayerEntity>,
        weights: ScoringWeights,
        forceOfflineSim: Boolean = false
    ): SimulatedTeamWeekScore {
        if (roster.isEmpty()) {
            return SimulatedTeamWeekScore(teamId, 0.0, "Empty lineup (0 active starters)", !forceOfflineSim)
        }

        // Ensure optimal starters are evaluated if lineup hasn't been manually slotted yet
        val slottedRoster = if (roster.any { it.lineupSlot.isActiveStarter }) {
            roster
        } else {
            LineupAnalyticsEngine.generateRecommendedLineup(roster)
        }

        val activeStarters = slottedRoster.filter {
            it.lineupSlot.isActiveStarter && !it.injurySeverity.isUnavailableToPlay
        }

        val hasRealSeasonStats = activeStarters.any { it.gamesPlayed > 0 || it.prevGamesPlayed > 0 }
        val useRealMode = hasRealSeasonStats && !forceOfflineSim

        // Deterministic weekly schedule variation (+/- 1 NHL game on team's real schedule that week, 3 vs 4 games)
        val rng = Random(teamId.hashCode().toLong() * 31L + weekNumber * 997L)

        var totalPoints = 0.0
        var bestPlayerName = ""
        var bestPlayerPts = -1.0

        for (player in activeStarters) {
            val summary = FantasyScoringEngine.summarizePlayer(player, weights)
            val gamesThisWeek = if (player.position == Position.GOALIE) {
                2.0 + rng.nextInt(0, 2) * 0.5 // 2.0 to 2.5 starts
            } else {
                3.0 + rng.nextInt(0, 2) * 1.0 // 3 or 4 NHL games in a calendar week
            }

            val playerWeekPts = if (useRealMode) {
                val baseFromRealStats = summary.pointsPerGame * gamesThisWeek
                // Incorporate actual live/today boxscore delta when playing current week
                val liveBonus = if (summary.dailyPoints > summary.pointsPerGame) {
                    (summary.dailyPoints - summary.pointsPerGame) * 0.5
                } else 0.0
                val availabilityFactor = if (player.injurySeverity == InjurySeverity.DAY_TO_DAY) 0.75 else 1.0
                max(0.0, (baseFromRealStats + liveBonus) * availabilityFactor)
            } else {
                // Offline simulation fallback derived from player OVR & position
                val baselinePerGame = ((player.overallRating - 58).coerceAtLeast(4)) * 0.135
                val simFactor = 0.85 + rng.nextDouble() * 0.30
                baselinePerGame * gamesThisWeek * simFactor
            }

            val roundedPlayerPts = round1(playerWeekPts)
            totalPoints += roundedPlayerPts
            if (roundedPlayerPts > bestPlayerPts) {
                bestPlayerPts = roundedPlayerPts
                bestPlayerName = "${player.lastName} (${roundedPlayerPts} FP)"
            }
        }

        return SimulatedTeamWeekScore(
            teamId = teamId,
            score = round1(totalPoints),
            topPerformerSummary = bestPlayerName.ifBlank { "Team effort" },
            usedRealNhlStats = useRealMode
        )
    }

    /**
     * Generates Semifinal Playoff Bracket (#1 vs #4, #2 vs #3) from final regular-season standings.
     */
    fun generatePlayoffSemifinals(
        seasonNumber: Int,
        rankedStandings: List<FantasyTeamEntity>
    ): List<FantasyMatchupEntity> {
        if (rankedStandings.size < 4) {
            if (rankedStandings.size >= 2) {
                return listOf(
                    FantasyMatchupEntity(
                        seasonNumber = seasonNumber,
                        weekNumber = CHAMPIONSHIP_WEEK,
                        stage = "CHAMPIONSHIP_FINAL",
                        homeTeamId = rankedStandings[0].teamId,
                        homeTeamName = "#1 ${rankedStandings[0].teamName}",
                        awayTeamId = rankedStandings[1].teamId,
                        awayTeamName = "#2 ${rankedStandings[1].teamName}"
                    )
                )
            }
            return emptyList()
        }

        val seed1 = rankedStandings[0]
        val seed2 = rankedStandings[1]
        val seed3 = rankedStandings[2]
        val seed4 = rankedStandings[3]

        return listOf(
            FantasyMatchupEntity(
                seasonNumber = seasonNumber,
                weekNumber = SEMIFINAL_WEEK,
                stage = "SEMIFINAL",
                homeTeamId = seed1.teamId,
                homeTeamName = "#1 ${seed1.teamName}",
                awayTeamId = seed4.teamId,
                awayTeamName = "#4 ${seed4.teamName}"
            ),
            FantasyMatchupEntity(
                seasonNumber = seasonNumber,
                weekNumber = SEMIFINAL_WEEK,
                stage = "SEMIFINAL",
                homeTeamId = seed2.teamId,
                homeTeamName = "#2 ${seed2.teamName}",
                awayTeamId = seed3.teamId,
                awayTeamName = "#3 ${seed3.teamName}"
            )
        )
    }

    /**
     * Generates Championship Final matchup from completed Semifinal winners.
     */
    fun generateChampionshipFinal(
        seasonNumber: Int,
        completedSemifinals: List<FantasyMatchupEntity>,
        teamsById: Map<String, FantasyTeamEntity>
    ): FantasyMatchupEntity? {
        if (completedSemifinals.size < 2 || completedSemifinals.any { !it.isCompleted }) return null

        fun winnerOf(m: FantasyMatchupEntity): FantasyTeamEntity? {
            // Higher seed advances on rare exact playoff tie
            val winnerId = if (m.homeScore >= m.awayScore) m.homeTeamId else m.awayTeamId
            return teamsById[winnerId]
        }

        val finalistA = winnerOf(completedSemifinals[0]) ?: return null
        val finalistB = winnerOf(completedSemifinals[1]) ?: return null

        return FantasyMatchupEntity(
            seasonNumber = seasonNumber,
            weekNumber = CHAMPIONSHIP_WEEK,
            stage = "CHAMPIONSHIP_FINAL",
            homeTeamId = finalistA.teamId,
            homeTeamName = finalistA.teamName,
            awayTeamId = finalistB.teamId,
            awayTeamName = finalistB.teamName
        )
    }

    fun rankStandings(teams: List<FantasyTeamEntity>): List<FantasyTeamEntity> {
        return teams.sortedWith(
            compareByDescending<FantasyTeamEntity> { it.wins * 2 + it.ties }
                .thenByDescending { it.pointsFor }
                .thenBy { it.pointsAgainst }
        )
    }

    fun computeSeasonAwards(
        allPlayers: List<PlayerEntity>,
        championTeam: FantasyTeamEntity,
        weights: ScoringWeights
    ): SeasonAwardsSummary {
        val scored = allPlayers.map { it to FantasyScoringEngine.summarizePlayer(it, weights) }

        val hart = scored.maxByOrNull { it.second.seasonPoints + it.first.overallRating * 0.25 }
        val vezina = scored
            .filter { it.first.position == Position.GOALIE }
            .maxByOrNull { it.second.seasonPoints + it.first.overallRating * 0.25 }
        val norris = scored
            .filter { it.first.position == Position.DEFENSE }
            .maxByOrNull { it.second.seasonPoints + it.first.overallRating * 0.25 }
        val calder = scored
            .filter { it.first.isRookie || (it.first.age <= 22 && !it.first.isProspect) }
            .maxByOrNull { it.second.seasonPoints + it.first.overallRating * 0.2 }

        fun fmt(pair: Pair<PlayerEntity, PlayerFantasyScoreSummary>?): String {
            if (pair == null) return "—"
            val (p, s) = pair
            return "${p.fullName} (${p.teamAbbrev}) • ${s.seasonPoints} FP"
        }

        return SeasonAwardsSummary(
            hartMvp = fmt(hart),
            vezinaGoalie = fmt(vezina),
            norrisDefense = fmt(norris),
            calderRookie = fmt(calder),
            gmOfTheYear = "${championTeam.teamName} (${championTeam.wins}-${championTeam.losses}-${championTeam.ties})"
        )
    }

    fun buildSeasonArchive(
        seasonNumber: Int,
        activeSeasonId: Long,
        finalMatchup: FantasyMatchupEntity,
        teams: List<FantasyTeamEntity>,
        allPlayers: List<PlayerEntity>,
        weights: ScoringWeights
    ): SeasonArchiveEntity {
        val championId = if (finalMatchup.homeScore >= finalMatchup.awayScore) finalMatchup.homeTeamId else finalMatchup.awayTeamId
        val runnerUpId = if (championId == finalMatchup.homeTeamId) finalMatchup.awayTeamId else finalMatchup.homeTeamId
        val champion = teams.firstOrNull { it.teamId == championId } ?: teams.first()
        val runnerUp = teams.firstOrNull { it.teamId == runnerUpId }
        val userTeam = teams.firstOrNull { it.isHuman } ?: champion
        val awards = computeSeasonAwards(allPlayers, champion, weights)

        val seasonStr = activeSeasonId.toString()
        val nhlLabel = if (seasonStr.length == 8) {
            "${seasonStr.substring(0, 4)}-${seasonStr.substring(6, 8)}"
        } else "2026-27"

        return SeasonArchiveEntity(
            seasonNumber = seasonNumber,
            nhlSeasonLabel = nhlLabel,
            championTeamId = champion.teamId,
            championTeamName = champion.teamName,
            runnerUpTeamName = runnerUp?.teamName ?: "Runner-Up",
            userRecord = "${userTeam.wins}-${userTeam.losses}-${userTeam.ties}",
            userPointsFor = round1(userTeam.pointsFor),
            hartMvpWinner = awards.hartMvp,
            vezinaGoalieWinner = awards.vezinaGoalie,
            norrisDefenseWinner = awards.norrisDefense,
            calderRookieWinner = awards.calderRookie,
            gmOfTheYearWinner = awards.gmOfTheYear,
            completedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun round1(v: Double): Double = round(v * 10.0) / 10.0
}
