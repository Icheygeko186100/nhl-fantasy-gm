package com.nhlfantasygm.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class NhlRemoteDataSource(
    val okHttpClient: OkHttpClient = defaultOkHttpClient()
) {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val requestSemaphore = Semaphore(permits = 8)

    suspend fun fetchStandingsNow(): Result<StandingsResponseDto> =
        getJson("$NHL_WEB_BASE/standings/now")

    suspend fun fetchTeamRoster(teamAbbrev: String): Result<TeamRosterResponseDto> =
        getJson("$NHL_WEB_BASE/roster/$teamAbbrev/current")

    suspend fun fetchTeamProspects(teamAbbrev: String): Result<TeamRosterResponseDto> =
        getJson("$NHL_WEB_BASE/prospects/$teamAbbrev")

    suspend fun fetchAllTeamRosters(teamAbbrevs: List<String>): Map<String, TeamRosterResponseDto> =
        coroutineScope {
            teamAbbrevs.map { abbrev ->
                async {
                    requestSemaphore.withPermit {
                        abbrev to fetchTeamRoster(abbrev).getOrNull()
                    }
                }
            }.awaitAll()
                .mapNotNull { (abbrev, dto) -> dto?.let { abbrev to it } }
                .toMap()
        }

    suspend fun fetchAllTeamProspects(teamAbbrevs: List<String>): Map<String, TeamRosterResponseDto> =
        coroutineScope {
            teamAbbrevs.map { abbrev ->
                async {
                    requestSemaphore.withPermit {
                        abbrev to fetchTeamProspects(abbrev).getOrNull()
                    }
                }
            }.awaitAll()
                .mapNotNull { (abbrev, dto) -> dto?.let { abbrev to it } }
                .toMap()
        }

    suspend fun fetchSkaterSummaryBulk(seasonId: Long): Result<StatsRestEnvelopeDto<StatsRestSkaterSummaryDto>> =
        getJson("$NHL_STATS_REST_BASE/skater/summary?limit=-1&cayenneExp=seasonId=$seasonId%20and%20gameTypeId=2")

    suspend fun fetchSkaterRealtimeBulk(seasonId: Long): Result<StatsRestEnvelopeDto<StatsRestSkaterRealtimeDto>> =
        getJson("$NHL_STATS_REST_BASE/skater/realtime?limit=-1&cayenneExp=seasonId=$seasonId%20and%20gameTypeId=2")

    suspend fun fetchGoalieSummaryBulk(seasonId: Long): Result<StatsRestEnvelopeDto<StatsRestGoalieSummaryDto>> =
        getJson("$NHL_STATS_REST_BASE/goalie/summary?limit=-1&cayenneExp=seasonId=$seasonId%20and%20gameTypeId=2")

    suspend fun fetchScoreboardNow(): Result<ScoreboardResponseDto> =
        getJson("$NHL_WEB_BASE/score/now")

    suspend fun fetchScoreboardForDate(dateStr: String): Result<ScoreboardResponseDto> =
        getJson("$NHL_WEB_BASE/score/$dateStr")

    /**
     * Resolves the smart scoreboard for live display:
     * - If [targetDate] is explicitly provided, loads that specific date.
     * - Otherwise determines today's Eastern Time date (official NHL schedule date).
     * - Inspects /score/now: if any game is currently LIVE or CRIT, uses now.
     * - If no game is live and /score/now is pointing to a past date (typical morning/early afternoon NHL API behavior),
     *   loads today's calendar date so games playing today are shown immediately.
     */
    suspend fun fetchSmartScoreboard(targetDate: String? = null): Result<ScoreboardResponseDto> {
        if (!targetDate.isNullOrBlank()) {
            return fetchScoreboardForDate(targetDate)
        }
        val todayEastern = try {
            java.time.LocalDate.now(java.time.ZoneId.of("America/New_York")).toString()
        } catch (_: Exception) {
            ""
        }
        val nowRes = fetchScoreboardNow()
        val nowDto = nowRes.getOrNull()
        if (nowDto != null) {
            val hasActiveLiveGame = nowDto.games.any {
                it.gameState.equals("LIVE", ignoreCase = true) || it.gameState.equals("CRIT", ignoreCase = true)
            }
            if (hasActiveLiveGame || (todayEastern.isNotBlank() && nowDto.currentDate >= todayEastern)) {
                return nowRes
            }
        }
        if (todayEastern.isNotBlank()) {
            val todayRes = fetchScoreboardForDate(todayEastern)
            val todayDto = todayRes.getOrNull()
            if (todayDto != null && (todayDto.games.isNotEmpty() || nowDto == null)) {
                return todayRes
            }
        }
        val todayLocal = try {
            java.time.LocalDate.now().toString()
        } catch (_: Exception) {
            ""
        }
        if (todayLocal.isNotBlank() && todayLocal != todayEastern) {
            val localRes = fetchScoreboardForDate(todayLocal)
            val localDto = localRes.getOrNull()
            if (localDto != null && localDto.games.isNotEmpty()) {
                return localRes
            }
        }
        return if (nowRes.isSuccess) nowRes else fetchScoreboardForDate(todayEastern)
    }

    suspend fun fetchGameBoxscore(gameId: Long): Result<GameBoxscoreResponseDto> =
        getJson("$NHL_WEB_BASE/gamecenter/$gameId/boxscore")

    suspend fun fetchActiveBoxscores(gameIds: List<Long>): List<GameBoxscoreResponseDto> =
        coroutineScope {
            gameIds.take(16).map { gameId ->
                async {
                    requestSemaphore.withPermit {
                        fetchGameBoxscore(gameId).getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }

    suspend fun fetchPlayerLanding(playerId: Long): Result<PlayerLandingResponseDto> =
        getJson("$NHL_WEB_BASE/player/$playerId/landing")

    suspend fun fetchEspnInjuries(): Result<EspnInjuriesResponseDto> =
        getJson("$ESPN_NHL_BASE/injuries")

    suspend fun fetchEspnNews(): Result<EspnNewsResponseDto> =
        getJson("$ESPN_NHL_BASE/news")

    private suspend inline fun <reified T> getJson(url: String): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "NHLFantasyGM-Android/1.0")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} from $url")
                    }
                    val bodyString = response.body?.string()
                        ?: throw IOException("Empty response body from $url")
                    json.decodeFromString<T>(bodyString)
                }
            }
        }

    companion object {
        const val NHL_WEB_BASE = "https://api-web.nhle.com/v1"
        const val NHL_STATS_REST_BASE = "https://api.nhle.com/stats/rest/en"
        const val ESPN_NHL_BASE = "https://site.api.espn.com/apis/site/v2/sports/hockey/nhl"

        fun defaultOkHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(28, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
    }
}
