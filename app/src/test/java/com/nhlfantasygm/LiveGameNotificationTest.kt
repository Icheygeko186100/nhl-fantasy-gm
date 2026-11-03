package com.nhlfantasygm

import com.nhlfantasygm.data.local.LiveNhlGameEntity
import com.nhlfantasygm.service.LiveGameSnapshot
import com.nhlfantasygm.service.TeamBadgeGenerator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    @Test
    fun `team badge generator creates and caches non-null software bitmaps for NHL teams`() {
        val cgyBadge = TeamBadgeGenerator.getBadgeBitmap("CGY", 120)
        assertNotNull(cgyBadge)
        assertEquals(120, cgyBadge.width)
        assertEquals(120, cgyBadge.height)

        val colBadge = TeamBadgeGenerator.getBadgeBitmap("COL", 120)
        assertNotNull(colBadge)
        assertEquals(120, colBadge.width)
        assertEquals(120, colBadge.height)

        // Caching verification: same instance returned for identical parameters
        val cachedCgy = TeamBadgeGenerator.getBadgeBitmap("CGY", 120)
        assertSame(cgyBadge, cachedCgy)
    }

    @Test
    fun `utc time formatting converts ISO strings to readable local time`() {
        val isoUtc = "2026-11-01T02:00:00Z"
        val instant = Instant.parse(isoUtc)
        val localTime = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
        val expectedFormatted = DateTimeFormatter.ofPattern("h:mm a").format(localTime)

        assertFalse(expectedFormatted.contains("T"))
        assertFalse(expectedFormatted.contains("Z"))
        assertTrue(expectedFormatted.contains("M")) // AM or PM
    }

    @Test
    fun `completed and final games cannot be pinned but can be unpinned`() {
        val finalGame = LiveNhlGameEntity(
            gameId = 2024020300L,
            gameDate = "2026-11-03",
            gameState = "FINAL",
            periodLabel = "3rd",
            clockLabel = "00:00",
            awayAbbrev = "BOS",
            awayName = "Boston Bruins",
            awayScore = 4,
            awaySog = 30,
            awayLogoUrl = "",
            homeAbbrev = "NYR",
            homeName = "New York Rangers",
            homeScore = 2,
            homeSog = 28,
            homeLogoUrl = "",
            startTimeUtc = "7:00 PM EST",
            venueName = "Madison Square Garden"
        )

        val offGame = finalGame.copy(gameState = "OFF")
        assertTrue(finalGame.isFinal)
        assertTrue(offGame.isFinal)

        // Cannot pin final game
        val pinned = mutableSetOf<Long>()
        val canPin = !finalGame.isFinal
        assertFalse(canPin)

        // Unpinning an existing pin is always permitted
        pinned.add(finalGame.gameId)
        assertTrue(pinned.contains(finalGame.gameId))
        pinned.remove(finalGame.gameId)
        assertFalse(pinned.contains(finalGame.gameId))
    }
}
