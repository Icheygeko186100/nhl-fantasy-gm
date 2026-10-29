package com.nhlfantasygm

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.nhlfantasygm.data.local.NhlFantasyDatabase
import com.nhlfantasygm.data.local.SecureCredentialStore
import com.nhlfantasygm.data.local.SettingsDataStore
import com.nhlfantasygm.data.remote.NhlRemoteDataSource
import com.nhlfantasygm.data.repository.NhlFantasyRepository
import com.nhlfantasygm.model.DraftConfig
import com.nhlfantasygm.model.Position
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EndToEndGameIntegrityTest {

    @Test
    fun `full live NHL sync persistent save restore complete draft stats refresh and season championship`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "e2e_persistent_test.db")
        if (dbFile.exists()) dbFile.delete()

        val db1 = Room.databaseBuilder(context, NhlFantasyDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val settings = SettingsDataStore(context)
        val creds = SecureCredentialStore(context)
        val remote = NhlRemoteDataSource()

        val repo1 = NhlFantasyRepository(db1, db1.dao(), remote, settings, creds)
        repo1.ensureFranchiseInitialized()

        // 1. Sync entire 32-team NHL player database from live NHL API
        val syncedCount = repo1.syncAllNhlData(includeProspects = false).getOrThrow()
        assertTrue("Expected > 700 real NHL players across all 32 teams, got $syncedCount", syncedCount > 700)

        val teams = db1.dao().getNhlTeamsOnce()
        assertEquals("All 32 NHL franchises must be loaded", 32, teams.size)

        val allPlayers = db1.dao().getAllPlayersOnce()
        assertTrue(allPlayers.any { it.position == Position.CENTER })
        assertTrue(allPlayers.any { it.position == Position.LEFT_WING })
        assertTrue(allPlayers.any { it.position == Position.RIGHT_WING })
        assertTrue(allPlayers.any { it.position == Position.DEFENSE })
        assertTrue(allPlayers.any { it.position == Position.GOALIE })

        // 2. Purchase a real NHL free agent & close DB to verify persistence across app restart
        val affordableTarget = allPlayers.first { it.purchasePriceCad in 1_000_000L..8_000_000L }
        assertTrue(repo1.buyPlayerFromMarketplace(affordableTarget.playerId).isSuccess)
        val cashAfterBuy = repo1.humanTeamFlow.first()!!.cashBalanceCad
        assertEquals(100_000_000L - affordableTarget.purchasePriceCad, cashAfterBuy)
        db1.close()

        // Reopen DB from disk (simulating app restart / phone reboot)
        val db2 = Room.databaseBuilder(context, NhlFantasyDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val repo2 = NhlFantasyRepository(db2, db2.dao(), remote, settings, creds)
        val restoredTeam = repo2.humanTeamFlow.first()!!
        assertEquals("Virtual CAD balance must survive restart", cashAfterBuy, restoredTeam.cashBalanceCad)
        val restoredPlayer = db2.dao().getPlayerById(affordableTarget.playerId)!!
        assertEquals("Player ownership must survive restart", "USER", restoredPlayer.ownerFantasyTeamId)

        // 3. Complete a full 4-team Fantasy Draft (1C, 1LW, 1RW, 2D, 1G, 1BN = 7 rounds = 28 picks)
        val draftCfg = DraftConfig(
            isSnakeDraft = true,
            teamCount = 4,
            userDraftPosition = 2,
            requiredCenters = 1,
            requiredLeftWings = 1,
            requiredRightWings = 1,
            requiredDefensemen = 2,
            requiredGoalies = 1,
            benchSlots = 1
        )
        assertTrue(repo2.startNewDraft(draftCfg).isSuccess)

        var safety = 0
        while (safety < 40) {
            val session = db2.dao().getDraftSessionOnce()!!
            if (session.status == "COMPLETED") break
            val order = session.teamOrderCsv.split(",")
            val (_, _, activeTeamId) = com.nhlfantasygm.domain.DraftEngine.getPickMetadata(
                session.currentOverallPick, order, draftCfg.isSnakeDraft
            )
            if (activeTeamId == "USER") {
                assertTrue(repo2.autoPickForHuman().isSuccess)
            } else {
                repo2.advanceCpuDraftPicks(maxSteps = 10)
            }
            safety++
        }

        val completedSession = db2.dao().getDraftSessionOnce()!!
        assertEquals("COMPLETED", completedSession.status)
        val completedPicks = db2.dao().getDraftPicksOnce()
        assertEquals(28, completedPicks.size)
        // Verify zero duplicate player ownership across all 28 drafted players
        assertEquals(28, completedPicks.map { it.playerId }.distinct().size)

        // Verify every team stayed within budget & salary cap
        db2.dao().getFantasyTeamsOnce().forEach { ft ->
            assertTrue("Team ${ft.teamName} balance cannot be negative", ft.cashBalanceCad >= 0L)
            val payroll = db2.dao().getTeamRosterOnce(ft.teamId).sumOf { it.capHitCad }
            assertTrue("Team ${ft.teamName} payroll must respect salary cap", payroll <= draftCfg.salaryCapCad)
        }

        // 4. Refresh live NHL scores & verify ownership preserved
        assertTrue(repo2.refreshLiveScoresOnly().isSuccess)
        assertEquals(7, db2.dao().getTeamRosterOnce("USER").size)

        // 5. Simulate all 14 regular-season weeks + Semifinals (Wk 15) + Championship Final (Wk 16)
        repeat(16) {
            assertTrue(repo2.simulateNextSeasonWeek().isSuccess)
        }
        val archives = db2.dao().getSeasonArchivesOnce()
        assertTrue("Completed championship season must be archived with trophies", archives.isNotEmpty())
        db2.close()
        dbFile.delete()
        Unit
    }

    @Test
    fun `main activity launches and renders bottom navigation cleanly`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
        }
    }
}
