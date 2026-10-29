package com.nhlfantasygm.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class LocalizedStringDto(
    val default: String = ""
)

// --- Standings (https://api-web.nhle.com/v1/standings/now) ---
@Serializable
data class StandingsResponseDto(
    val standings: List<StandingsTeamDto> = emptyList()
)

@Serializable
data class StandingsTeamDto(
    val teamAbbrev: LocalizedStringDto = LocalizedStringDto(),
    val teamName: LocalizedStringDto = LocalizedStringDto(),
    val teamCommonName: LocalizedStringDto? = null,
    val placeName: LocalizedStringDto? = null,
    val conferenceName: String = "",
    val divisionName: String = "",
    val teamLogo: String = "",
    val teamLogoDark: String = "",
    val seasonId: Long = 20262027L,
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val otLosses: Int = 0,
    val points: Int = 0,
    val goalFor: Int = 0,
    val goalAgainst: Int = 0,
    val streakCode: String = "",
    val streakCount: Int = 0,
    val leagueSequence: Int = 99
)

// --- Roster & Prospects (https://api-web.nhle.com/v1/roster/{team}/current & prospects/{team}) ---
@Serializable
data class TeamRosterResponseDto(
    val forwards: List<RosterPlayerDto> = emptyList(),
    val defensemen: List<RosterPlayerDto> = emptyList(),
    val goalies: List<RosterPlayerDto> = emptyList()
)

@Serializable
data class RosterPlayerDto(
    val id: Long = 0L,
    val headshot: String = "",
    val firstName: LocalizedStringDto = LocalizedStringDto(),
    val lastName: LocalizedStringDto = LocalizedStringDto(),
    val sweaterNumber: Int? = null,
    val positionCode: String = "C",
    val shootsCatches: String? = null,
    val heightInInches: Int? = null,
    val weightInPounds: Int? = null,
    val birthDate: String? = null,
    val birthCountry: String? = null
)

// --- Stats REST Bulk Endpoints (https://api.nhle.com/stats/rest/en/...) ---
@Serializable
data class StatsRestEnvelopeDto<T>(
    val total: Int = 0,
    val data: List<T> = emptyList()
)

@Serializable
data class StatsRestSkaterSummaryDto(
    val playerId: Long = 0L,
    val skaterFullName: String? = null,
    val lastName: String? = null,
    val teamAbbrevs: String? = null,
    val positionCode: String? = null,
    val shootsCatches: String? = null,
    val gamesPlayed: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val points: Int = 0,
    val plusMinus: Int? = 0,
    val penaltyMinutes: Int? = 0,
    val ppGoals: Int? = 0,
    val ppPoints: Int? = 0,
    val shPoints: Int? = 0,
    val shots: Int? = 0,
    val timeOnIcePerGame: Double? = 0.0,
    val faceoffWinPct: Double? = null
)

@Serializable
data class StatsRestSkaterRealtimeDto(
    val playerId: Long = 0L,
    val hits: Int? = 0,
    val blockedShots: Int? = 0,
    val takeaways: Int? = 0
)

@Serializable
data class StatsRestGoalieSummaryDto(
    val playerId: Long = 0L,
    val goalieFullName: String? = null,
    val lastName: String? = null,
    val teamAbbrevs: String? = null,
    val shootsCatches: String? = null,
    val gamesPlayed: Int = 0,
    val gamesStarted: Int? = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val otLosses: Int? = 0,
    val saves: Int? = 0,
    val shotsAgainst: Int? = 0,
    val goalsAgainst: Int? = 0,
    val savePct: Double? = 0.0,
    val goalsAgainstAverage: Double? = 0.0,
    val shutouts: Int? = 0
)

// --- Scoreboard & Live Game Boxscore ---
@Serializable
data class ScoreboardResponseDto(
    val currentDate: String = "",
    val games: List<ScoreboardGameDto> = emptyList()
)

@Serializable
data class ScoreboardGameDto(
    val id: Long = 0L,
    val season: Long = 20262027L,
    val gameType: Int = 2,
    val gameDate: String = "",
    val gameState: String = "FUT",
    val startTimeUTC: String = "",
    val venue: LocalizedStringDto? = null,
    val periodDescriptor: PeriodDescriptorDto? = null,
    val clock: GameClockDto? = null,
    val awayTeam: ScoreTeamDto = ScoreTeamDto(),
    val homeTeam: ScoreTeamDto = ScoreTeamDto()
)

@Serializable
data class PeriodDescriptorDto(
    val number: Int = 1,
    val periodType: String = "REG"
)

@Serializable
data class GameClockDto(
    val timeRemaining: String = "20:00",
    val inIntermission: Boolean = false
)

@Serializable
data class ScoreTeamDto(
    val id: Int = 0,
    val abbrev: String = "",
    val name: LocalizedStringDto? = null,
    val commonName: LocalizedStringDto? = null,
    val score: Int? = 0,
    val sog: Int? = 0,
    val logo: String = ""
)

@Serializable
data class GameBoxscoreResponseDto(
    val id: Long = 0L,
    val gameState: String = "",
    val awayTeam: ScoreTeamDto = ScoreTeamDto(),
    val homeTeam: ScoreTeamDto = ScoreTeamDto(),
    val playerByGameStats: PlayerByGameStatsDto? = null
)

@Serializable
data class PlayerByGameStatsDto(
    val awayTeam: BoxscoreTeamPlayersDto? = null,
    val homeTeam: BoxscoreTeamPlayersDto? = null
)

@Serializable
data class BoxscoreTeamPlayersDto(
    val forwards: List<BoxscoreSkaterDto> = emptyList(),
    val defense: List<BoxscoreSkaterDto> = emptyList(),
    val goalies: List<BoxscoreGoalieDto> = emptyList()
)

@Serializable
data class BoxscoreSkaterDto(
    val playerId: Long = 0L,
    val goals: Int = 0,
    val assists: Int = 0,
    val points: Int = 0,
    val powerPlayGoals: Int = 0,
    val sog: Int = 0,
    val hits: Int = 0,
    val blockedShots: Int = 0,
    val pim: Int = 0,
    val plusMinus: Int = 0,
    val toi: String = ""
)

@Serializable
data class BoxscoreGoalieDto(
    val playerId: Long = 0L,
    val decision: String? = null,
    val starter: Boolean? = false,
    val saves: Int = 0,
    val shotsAgainst: Int = 0,
    val goalsAgainst: Int = 0,
    val savePctg: Double? = null,
    val toi: String = "00:00"
)

// --- Player Landing (Historical Career Stats & Last 5 Games) ---
@Serializable
data class PlayerLandingResponseDto(
    val playerId: Long = 0L,
    val isActive: Boolean = true,
    val draftDetails: DraftDetailsDto? = null,
    val featuredStats: FeaturedStatsDto? = null,
    val seasonTotals: List<LandingSeasonTotalDto> = emptyList(),
    val last5Games: List<LandingGameLogDto> = emptyList()
)

@Serializable
data class DraftDetailsDto(
    val year: Int? = null,
    val teamAbbrev: String? = null,
    val round: Int? = null,
    val overallPick: Int? = null
)

@Serializable
data class FeaturedStatsDto(
    val regularSeason: FeaturedRegularSeasonDto? = null
)

@Serializable
data class FeaturedRegularSeasonDto(
    val career: LandingCareerStatsDto? = null
)

@Serializable
data class LandingCareerStatsDto(
    val gamesPlayed: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val points: Int = 0,
    val wins: Int = 0,
    val savePctg: Double = 0.0,
    val goalsAgainstAvg: Double = 0.0,
    val shutouts: Int = 0
)

@Serializable
data class LandingSeasonTotalDto(
    val season: Long = 0L,
    val gameTypeId: Int = 2,
    val leagueAbbrev: String = "",
    val teamName: LocalizedStringDto? = null,
    val gamesPlayed: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val points: Int = 0,
    val plusMinus: Int = 0,
    val shots: Int = 0,
    val powerPlayPoints: Int = 0,
    val wins: Int = 0,
    val savePctg: Double = 0.0,
    val goalsAgainstAvg: Double = 0.0,
    val shutouts: Int = 0
)

@Serializable
data class LandingGameLogDto(
    val gameDate: String = "",
    val opponentAbbrev: String = "",
    val homeRoadFlag: String = "",
    val goals: Int = 0,
    val assists: Int = 0,
    val points: Int = 0,
    val shots: Int = 0,
    val plusMinus: Int = 0,
    val pim: Int = 0,
    val toi: String = "",
    val decision: String = "",
    val savePctg: Double = 0.0,
    val shotsAgainst: Int = 0,
    val goalsAgainst: Int = 0
)

// --- Public ESPN NHL Injuries & News Headlines ---
@Serializable
data class EspnInjuriesResponseDto(
    val injuries: List<EspnTeamInjuriesDto> = emptyList()
)

@Serializable
data class EspnTeamInjuriesDto(
    val displayName: String = "",
    val injuries: List<EspnPlayerInjuryDto> = emptyList()
)

@Serializable
data class EspnPlayerInjuryDto(
    val status: String = "",
    val shortComment: String = "",
    val date: String = "",
    val athlete: EspnAthleteDto? = null,
    val details: EspnInjuryDetailsDto? = null
)

@Serializable
data class EspnAthleteDto(
    val displayName: String = "",
    val firstName: String = "",
    val lastName: String = ""
)

@Serializable
data class EspnInjuryDetailsDto(
    val type: String = "",
    val returnDate: String = ""
)

@Serializable
data class EspnNewsResponseDto(
    val articles: List<EspnNewsArticleDto> = emptyList()
)

@Serializable
data class EspnNewsArticleDto(
    val id: Long? = null,
    val headline: String = "",
    val description: String = "",
    val published: String = "",
    val links: EspnArticleLinksDto? = null
)

@Serializable
data class EspnArticleLinksDto(
    val web: EspnWebLinkDto? = null
)

@Serializable
data class EspnWebLinkDto(
    val href: String = ""
)
