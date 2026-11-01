package com.nhlfantasygm

import com.nhlfantasygm.data.local.LiveNhlGameEntity
import com.nhlfantasygm.service.LiveGameSnapshot
import org.junit.Assert.*
import org.junit.Test

class LiveGameNotificationTest {

    @Test
    fun `live game entity formats live, final and scheduled status correctly`() {
        val liveGame = LiveNhlGameEntity(
            gameId = 2024020100L,
            gameDate = "2026-10-31",
            gameState = "LIVE",
            periodLabel = "2nd",
            clockLabel = "14:22",
            awayAbbrev = "EDM",
            awayName = "Edmonton Oilers",
            awayScore = 3,
            awaySog = 24,
            awayLogoUrl = "",
            homeAbbrev = "CGY",
            homeName = "Calgary Flames",
            homeScore = 2,
            homeSog = 18,
            homeLogoUrl = "",
            startTimeUtc = "7:00 PM EST",
            venueName = "Scotiabank Saddledome"
        )

        assertTrue(liveGame.isLive)
        assertFalse(liveGame.isFinal)
        assertEquals("EDM", liveGame.awayAbbrev)
        assertEquals(3, liveGame.awayScore)
        assertEquals(24, liveGame.awaySog)

        val finalGame = liveGame.copy(gameState = "FINAL")
        assertFalse(finalGame.isLive)
        assertTrue(finalGame.isFinal)

        val scheduledGame = liveGame.copy(gameState = "FUT")
        assertFalse(scheduledGame.isLive)
        assertFalse(scheduledGame.isFinal)
    }

    @Test
    fun `pinned game IDs set operations manage single and multi-game pinning`() {
        val pinned = mutableSetOf<Long>()
        val game1 = 101L
        val game2 = 102L

        // Pin game 1
        pinned.add(game1)
        assertTrue(pinned.contains(game1))
        assertFalse(pinned.contains(game2))
        assertEquals(1, pinned.size)

        // Pin game 2
        pinned.add(game2)
        assertTrue(pinned.contains(game1))
        assertTrue(pinned.contains(game2))
        assertEquals(2, pinned.size)

        // Unpin game 1
        pinned.remove(game1)
        assertFalse(pinned.contains(game1))
        assertTrue(pinned.contains(game2))
        assertEquals(1, pinned.size)

        // Unpin all
        pinned.clear()
        assertTrue(pinned.isEmpty())
    }

    @Test
    fun `snapshot equality deduplication prevents re-notifying unchanged game states`() {
        val game = LiveNhlGameEntity(
            gameId = 2024020200L,
            gameDate = "2026-10-31",
            gameState = "LIVE",
            periodLabel = "1st",
            clockLabel = "10:00",
            awayAbbrev = "TOR",
            awayName = "Toronto Maple Leafs",
            awayScore = 1,
            awaySog = 10,
            awayLogoUrl = "",
            homeAbbrev = "MTL",
            homeName = "Montreal Canadiens",
            homeScore = 0,
            homeSog = 8,
            homeLogoUrl = "",
            startTimeUtc = "7:00 PM EST",
            venueName = "Bell Centre"
        )

        val initialSnapshot = LiveGameSnapshot.from(game)
        val sameDataSnapshot = LiveGameSnapshot.from(game)

        // Identical data produces equal snapshots -> skips notify()
        assertEquals(initialSnapshot, sameDataSnapshot)

        // Score change produces different snapshot -> triggers live update
        val scoreUpdateSnapshot = LiveGameSnapshot.from(game.copy(awayScore = 2))
        assertNotEquals(initialSnapshot, scoreUpdateSnapshot)

        // Period/clock update produces different snapshot
        val clockUpdateSnapshot = LiveGameSnapshot.from(game.copy(clockLabel = "09:30"))
        assertNotEquals(initialSnapshot, clockUpdateSnapshot)

        // SOG update produces different snapshot
        val sogUpdateSnapshot = LiveGameSnapshot.from(game.copy(awaySog = 11))
        assertNotEquals(initialSnapshot, sogUpdateSnapshot)

        // Game final produces different snapshot
        val finalSnapshot = LiveGameSnapshot.from(game.copy(gameState = "FINAL"))
        assertNotEquals(initialSnapshot, finalSnapshot)
    }
}
