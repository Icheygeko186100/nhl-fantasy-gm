package com.nhlfantasygm

import com.nhlfantasygm.data.remote.NhlRemoteDataSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LiveNhlDataSourceVerificationTest {

    @Test
    fun `live NHL and ESPN endpoints return valid 32-team rosters stats scoreboard injuries and news`() = runBlocking {
        val remote = NhlRemoteDataSource()

        val standingsRes = remote.fetchStandingsNow()
        assertTrue("Standings endpoint must succeed: ${standingsRes.exceptionOrNull()}", standingsRes.isSuccess)
        val standings = standingsRes.getOrThrow().standings
        assertEquals("NHL must have all 32 franchises", 32, standings.size)

        val seasonId = standings.first().seasonId
        assertTrue("Detected seasonId must be valid 8-digit season", seasonId >= 20242025L)

        // Verify roster & prospects parsing
        val sampleTeam = standings.first().teamAbbrev.default
        val roster = remote.fetchTeamRoster(sampleTeam).getOrThrow()
        assertTrue("Active NHL team roster must have forwards, defensemen, and goalies",
            roster.forwards.isNotEmpty() && roster.defensemen.isNotEmpty() && roster.goalies.isNotEmpty())

        // Verify bulk skater & goalie statistics
        val skaters = remote.fetchSkaterSummaryBulk(seasonId).getOrThrow()
        assertTrue("Bulk skater summary must return active NHL skaters", skaters.data.size > 100)

        val realtime = remote.fetchSkaterRealtimeBulk(seasonId).getOrThrow()
        assertTrue("Bulk skater realtime (hits/blocks) must return active skaters", realtime.data.size > 100)

        val goalies = remote.fetchGoalieSummaryBulk(seasonId).getOrThrow()
        assertTrue("Bulk goalie summary must return active NHL goalies", goalies.data.size > 20)

        // Verify player landing historical stats & game log
        val firstPlayerId = roster.forwards.first().id
        val landing = remote.fetchPlayerLanding(firstPlayerId).getOrThrow()
        assertEquals(firstPlayerId, landing.playerId)

        // Verify scoreboard, injuries, and news feeds
        assertTrue(remote.fetchScoreboardNow().isSuccess)
        assertTrue(remote.fetchEspnInjuries().isSuccess)
        assertTrue(remote.fetchEspnNews().isSuccess)
    }
}
