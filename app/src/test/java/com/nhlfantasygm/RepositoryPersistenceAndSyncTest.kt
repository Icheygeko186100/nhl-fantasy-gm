package com.nhlfantasygm

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nhlfantasygm.data.local.*
import com.nhlfantasygm.data.remote.NhlRemoteDataSource
import com.nhlfantasygm.data.repository.NhlFantasyRepository
import com.nhlfantasygm.model.DraftConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryPersistenceAndSyncTest {

    private lateinit var context: Context
    private lateinit var db: NhlFantasyDatabase
    private lateinit var settingsStore: SettingsDataStore
    private lateinit var credStore: SecureCredentialStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NhlFantasyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsStore = SettingsDataStore(context)
        credStore = SecureCredentialStore(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `repository initializes $100M CAD treasury buys sells drafts undoes and survives offline failure`() = runBlocking {
        // Offline client that simulates network disconnect cleanly
        val offlineClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { throw IOException("Simulated Airplane Mode Offline") })
            .build()

        val repo = NhlFantasyRepository(
            database = db,
            dao = db.dao(),
            remote = NhlRemoteDataSource(offlineClient),
            settingsStore = settingsStore,
            credentialStore = credStore
        )

        repo.ensureFranchiseInitialized()
        val humanTeam = repo.humanTeamFlow.first()
        assertNotNull(humanTeam)
        assertEquals(100_000_000L, humanTeam!!.cashBalanceCad)

        // Seed 24 real-schema NHL players into Room cache
        val seededPlayers = (1..24).map { idx ->
            val pos = when (idx % 5) {
                0 -> "C"
                1 -> "L"
                2 -> "R"
                3 -> "D"
                else -> "G"
            }
            PlayerEntity(
                playerId = 8470000L + idx,
                firstName = "Player",
                lastName = "Number$idx",
                fullName = "Player Number$idx",
                normalizedSearchName = "player number$idx",
                teamAbbrev = "TOR",
                teamFullName = "Toronto Maple Leafs",
                positionCode = pos,
                sweaterNumber = idx,
                age = 24,
                birthDate = "2002-04-10",
                birthCountry = "CAN",
                shootsCatches = "L",
                heightInches = 73,
                weightPounds = 198,
                headshotUrl = "",
                isProspect = false,
                isRookie = idx <= 4,
                seasonId = 20262027L,
                gamesPlayed = 12,
                goals = 5,
                assists = 7,
                points = 12,
                plusMinus = 3,
                penaltyMinutes = 4,
                powerPlayGoals = 2,
                powerPlayPoints = 4,
                shorthandedPoints = 0,
                shots = 32,
                hits = 14,
                blockedShots = 10,
                avgToiSeconds = 1100,
                faceoffWinPct = 0.51,
                gamesStarted = if (pos == "G") 8 else 0,
                wins = if (pos == "G") 5 else 0,
                losses = 2,
                otLosses = 1,
                saves = if (pos == "G") 210 else 0,
                shotsAgainst = if (pos == "G") 230 else 0,
                goalsAgainst = 20,
                savePct = if (pos == "G") 0.913 else 0.0,
                goalsAgainstAverage = if (pos == "G") 2.48 else 0.0,
                shutouts = 1,
                prevSeasonId = 20252026L,
                prevGamesPlayed = 70,
                prevGoals = 22,
                prevAssists = 35,
                prevPoints = 57,
                prevPpPoints = 15,
                prevShots = 180,
                prevHits = 50,
                prevBlockedShots = 40,
                prevWins = 25,
                prevSaves = 1400,
                prevSavePct = 0.912,
                prevGaa = 2.55,
                prevShutouts = 2,
                overallRating = 80 + (idx % 10),
                purchasePriceCad = 4_000_000L,
                sellPriceCad = 3_840_000L,
                capHitCad = 3_500_000L
            )
        }
        db.dao().upsertPlayers(seededPlayers)

        // 1. Buy player on Marketplace
        val buyRes = repo.buyPlayerFromMarketplace(8470001L)
        assertTrue(buyRes.isSuccess)
        assertEquals(96_000_000L, repo.humanTeamFlow.first()!!.cashBalanceCad)

        // Duplicate purchase must fail
        val dupRes = repo.buyPlayerFromMarketplace(8470001L)
        assertTrue(dupRes.isFailure)

        // 2. Offline API failure must NOT wipe cached players or user ownership
        val offlineSyncRes = repo.syncAllNhlData()
        assertTrue(offlineSyncRes.isFailure)
        val preservedPlayer = db.dao().getPlayerById(8470001L)
        assertEquals("USER", preservedPlayer?.ownerFantasyTeamId)
        val syncStatus = db.dao().getSyncStatusOnce()
        assertNotNull(syncStatus)
        assertFalse(syncStatus!!.lastSyncSucceeded)
        assertEquals(24, syncStatus.syncedPlayerCount)

        // 3. Sell player back to Marketplace
        val sellRes = repo.sellPlayerToMarketplace(8470001L)
        assertTrue(sellRes.isSuccess)
        assertEquals(99_840_000L, repo.humanTeamFlow.first()!!.cashBalanceCad)
        assertNull(db.dao().getPlayerById(8470001L)?.ownerFantasyTeamId)

        // 4. Start 4-team mini Draft (1C, 1LW, 1RW, 1D, 1G = 5 rounds x 4 teams = 20 picks), make human pick, advance CPU, and Undo!
        val miniDraft = DraftConfig(
            isSnakeDraft = true,
            teamCount = 4,
            userDraftPosition = 1,
            requiredCenters = 1,
            requiredLeftWings = 1,
            requiredRightWings = 1,
            requiredDefensemen = 1,
            requiredGoalies = 1,
            benchSlots = 0
        )
        assertTrue(repo.startNewDraft(miniDraft).isSuccess)
        assertTrue(repo.makeHumanDraftPick(8470005L).isSuccess) // Pick #1 (Center)
        val cpuAdvanced = repo.advanceCpuDraftPicks().getOrThrow()
        assertTrue("CPU GMs should automatically make picks until user's snake turn", cpuAdvanced >= 6)

        // Undo rolls back human pick #1 AND all subsequent CPU picks!
        val undoRes = repo.undoLastHumanDraftPick()
        assertTrue(undoRes.isSuccess)
        assertEquals(1, db.dao().getDraftSessionOnce()?.currentOverallPick)
        assertEquals(100_000_000L, repo.humanTeamFlow.first()!!.cashBalanceCad)
    }
}
