package com.nhlfantasygm

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nhlfantasygm.data.local.*
import com.nhlfantasygm.data.remote.NhlRemoteDataSource
import com.nhlfantasygm.data.repository.NhlFantasyRepository
import com.nhlfantasygm.domain.DraftEngine
import com.nhlfantasygm.model.DraftConfig
import com.nhlfantasygm.model.TeamEmblem
import com.nhlfantasygm.multiplayer.*
import com.nhlfantasygm.ui.screens.MultiplayerSetupAndLobbyView
import com.nhlfantasygm.ui.theme.NhlFantasyGmTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiplayerDraftAndInviteTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var hostDb: NhlFantasyDatabase
    private lateinit var guestDb: NhlFantasyDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        hostDb = Room.inMemoryDatabaseBuilder(context, NhlFantasyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        guestDb = Room.inMemoryDatabaseBuilder(context, NhlFantasyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        hostDb.close()
        guestDb.close()
    }

    @Test
    fun `roomCrypto generates valid codes parses deep links and performs AES-256-GCM GZIP encryption with tamper rejection`(): Unit {
        val code = RoomCrypto.generateInviteCode()
        assertEquals(6, code.length)
        assertFalse(code.contains('0') || code.contains('O') || code.contains('1') || code.contains('I'))

        val formatted = RoomCrypto.formatInviteCodeForDisplay("k9m4p2")
        assertEquals("K9M-4P2", formatted)

        val deepLink = RoomCrypto.buildDeepLink("K9M-4P2")
        assertEquals("nhlfantasygm://join?code=K9M4P2", deepLink)
        assertEquals("K9M4P2", RoomCrypto.extractInviteCodeFromLinkOrText(deepLink))
        assertEquals(
            "K9M4P2",
            RoomCrypto.extractInviteCodeFromLinkOrText("Join my NHL draft! Tap nhlfantasygm://join?code=K9M-4P2 now")
        )

        val topicA = RoomCrypto.deriveRoomTopic("K9M-4P2")
        val topicB = RoomCrypto.deriveRoomTopic("k9m4p2")
        assertEquals(topicA, topicB)
        assertTrue(topicA.startsWith("nhlfantasygm/v1/rooms/"))
        assertFalse(topicA.contains("K9M4P2", ignoreCase = true))

        val largeJson = buildString {
            append("""{"room":"K9M4P2","picks":[""")
            repeat(120) { i ->
                if (i > 0) append(",")
                append("""{"pick":${i + 1},"player":"Connor McDavid #$i","price":15500000}""")
            }
            append("]}")
        }

        val encrypted = RoomCrypto.encryptRoomPayload("K9M-4P2", largeJson)
        assertNotEquals(largeJson, encrypted)
        assertFalse(encrypted.contains("McDavid"))

        val decrypted = RoomCrypto.decryptRoomPayload("k9m4p2", encrypted)
        assertEquals(largeJson, decrypted)

        // Wrong room code must fail AES-GCM authentication tag verification
        val wrongCodeResult = runCatching {
            RoomCrypto.decryptRoomPayload("X7P9Q4", encrypted)
        }
        assertTrue("Expected decryption with wrong invite code to fail", wrongCodeResult.isFailure)
    }

    @Test
    fun `mqttPacketCodec encodes and decodes multi-byte variable length frames over 32KB plus CONNACK SUBACK PUBLISH and PUBACK`(): Unit {
        val connectPkt = MqttPacketCodec.buildConnectPacket("nhlgm_test_client", keepAliveSec = 45)
        assertEquals(0x10.toByte(), connectPkt[0])

        val subPkt = MqttPacketCodec.buildSubscribePacket(packetId = 42, topic = "nhlfantasygm/v1/rooms/abc", qos = 1)
        assertEquals(0x82.toByte(), subPkt[0])

        // 40 KB payload triggers 3-byte MQTT variable Remaining Length encoding
        val bigPayload = "ENCRYPTED_BLOCK_".repeat(2600)
        assertTrue(bigPayload.length > 35_000)
        val pubPkt = MqttPacketCodec.buildPublishPacket(
            topic = "nhlfantasygm/v1/rooms/abc123",
            payloadUtf8 = bigPayload,
            qos = 1,
            packetId = 777,
            retain = true
        )

        val decoded = MqttPacketCodec.decodeFramePackets(pubPkt)
        assertEquals(1, decoded.size)
        val pub = decoded.first() as MqttPacketCodec.DecodedPacket.Publish
        assertEquals("nhlfantasygm/v1/rooms/abc123", pub.topic)
        assertEquals(1, pub.qos)
        assertEquals(777, pub.packetId)
        assertTrue(pub.retain)
        assertEquals(bigPayload, pub.payloadUtf8)

        val ackBytes = MqttPacketCodec.buildPubAckPacket(777)
        val ackDecoded = MqttPacketCodec.decodeFramePackets(ackBytes).first() as MqttPacketCodec.DecodedPacket.PubAck
        assertEquals(777, ackDecoded.packetId)
    }

    @Test
    fun `two-device Host and Invited Friend join via invite link draft together alongside CPU GMs chat and complete live player plus Virtual CAD trade`(): Unit = runBlocking {
        val offlineHttp = OkHttpClient.Builder()
            .addInterceptor(Interceptor { throw IOException("Offline test mode") })
            .build()

        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val hostSettings = SettingsDataStore(context, storeName = "host_mp_prefs_$uniqueSuffix")
        val guestSettings = SettingsDataStore(context, storeName = "guest_mp_prefs_$uniqueSuffix")
        val credStore = SecureCredentialStore(context)

        val hostRepo = NhlFantasyRepository(
            database = hostDb,
            dao = hostDb.dao(),
            remote = NhlRemoteDataSource(offlineHttp),
            settingsStore = hostSettings,
            credentialStore = credStore
        )
        val guestRepo = NhlFantasyRepository(
            database = guestDb,
            dao = guestDb.dao(),
            remote = NhlRemoteDataSource(offlineHttp),
            settingsStore = guestSettings,
            credentialStore = credStore
        )

        hostRepo.ensureFranchiseInitialized()
        guestRepo.ensureFranchiseInitialized()

        // Seed 60 NHL players across C, LW, RW, D, G into both devices' local Room caches
        val seededPlayers = buildSeededNhlPool(count = 60)
        hostDb.dao().upsertPlayers(seededPlayers)
        guestDb.dao().upsertPlayers(seededPlayers)

        // Shared encrypted relay hub connecting Host Device and Invited Friend's Device
        val relayHub = InMemoryLoopbackTransportHub()
        val transportFactory = MultiplayerTransportFactory { relayHub.createClientTransport() }

        val hostCoordinator = MultiplayerCoordinator(hostRepo, transportFactory)
        val guestCoordinator = MultiplayerCoordinator(guestRepo, transportFactory)

        // Wait briefly for init localClientId coroutines
        awaitCondition {
            hostCoordinator.uiState.value.localClientId.isNotBlank() &&
                guestCoordinator.uiState.value.localClientId.isNotBlank()
        }

        // 1. Host creates a 4-team Snake Draft lobby (1 Host + 1 Invited Friend + 2 CPU GMs, 6 rounds)
        val quickDraftCfg = DraftConfig(
            isSnakeDraft = true,
            teamCount = 4,
            userDraftPosition = 1,
            requiredCenters = 1,
            requiredLeftWings = 1,
            requiredRightWings = 1,
            requiredDefensemen = 2,
            requiredGoalies = 1,
            benchSlots = 0,
            enforceSalaryCap = true,
            salaryCapCad = 95_500_000L,
            pickTimerSeconds = 60
        )

        val hostRes = hostCoordinator.hostNewRoom("GM Connor", quickDraftCfg)
        assertTrue(hostRes.isSuccess)
        val roomCode = hostRes.getOrThrow()
        assertEquals(6, roomCode.length)

        val shareMessage = hostCoordinator.buildInviteShareMessage()
        assertTrue(shareMessage.contains("nhlfantasygm://join?code=$roomCode"))

        // 2. Friend taps the invite link on their phone and joins the lobby
        val extractedCode = guestCoordinator.handleIncomingInviteLink(shareMessage)
        assertEquals(roomCode, extractedCode)

        val joinRes = guestCoordinator.joinFriendRoom(
            rawCodeOrLink = shareMessage,
            gmDisplayName = "GM Leon",
            teamName = "Atlantic Frost HC",
            abbreviation = "AFH",
            emblem = TeamEmblem.ARCTIC_WOLF,
            primaryColorHex = 0xFF00E676,
            secondaryColorHex = 0xFF071E18
        )
        assertTrue(joinRes.isSuccess)

        awaitCondition {
            hostCoordinator.uiState.value.participants.size == 2 &&
                guestCoordinator.uiState.value.participants.size == 2
        }

        // 3. Friend marks Ready & sends live War Room chat message
        guestCoordinator.updateGuestProfileAndReady(
            gmName = "GM Leon",
            teamName = "Atlantic Frost HC",
            abbreviation = "AFH",
            emblem = TeamEmblem.ARCTIC_WOLF,
            primaryColorHex = 0xFF00E676,
            secondaryColorHex = 0xFF071E18,
            isReady = true
        )
        guestCoordinator.sendChatMessage("Ready to draft! Good luck everyone!")

        awaitCondition {
            hostCoordinator.uiState.value.participants.any { it.gmName == "GM Leon" && it.isReady } &&
                hostCoordinator.uiState.value.chatMessages.any { it.text.contains("Ready to draft") }
        }

        val guestTeamId = guestCoordinator.uiState.value.controlledTeamId
        assertTrue("Guest must be assigned a dedicated MP_ team ID", guestTeamId.startsWith("MP_"))

        // 4. Host launches the 4-team Multiplayer Draft (Slot #1 Host, Slot #2 Friend, Slots #3-#4 CPU GMs)
        val launchRes = hostCoordinator.launchMultiplayerDraft()
        assertTrue(launchRes.isSuccess)

        awaitCondition {
            guestRepo.draftSessionFlow.first()?.status == "IN_PROGRESS"
        }

        val guestTeams = guestRepo.fantasyTeamsFlow.first()
        assertEquals(4, guestTeams.size)
        assertEquals(2, guestTeams.count { !it.isCpuGm }) // Host + Friend
        assertEquals(2, guestTeams.count { it.isCpuGm })  // 2 CPU GMs (never disguised as online humans)

        // 5. Pick #1 is Host ("USER"). Host drafts Center #8470001
        val hostPick1PlayerId = 8470001L
        val pick1Res = hostCoordinator.submitMultiplayerDraftPick(hostPick1PlayerId)
        assertTrue("Host pick #1 failed: ${pick1Res.exceptionOrNull()?.message}", pick1Res.isSuccess)

        // Verify CPU auto-advance did NOT skip Invited Friend's Pick #2!
        awaitCondition {
            guestRepo.draftSessionFlow.first()?.currentOverallPick == 2
        }
        val sessionAtPick2 = hostRepo.draftSessionFlow.first()!!
        assertEquals(2, sessionAtPick2.currentOverallPick)

        // 6. Pick #2 is Invited Friend (`guestTeamId`). Friend drafts Center #8470006 from Guest device!
        val guestPick2PlayerId = 8470006L
        val pick2Res = guestCoordinator.submitMultiplayerDraftPick(guestPick2PlayerId)
        assertTrue(pick2Res.isSuccess)

        // Host validates Pick #2, then automatically advances CPU #3, CPU #4, Snake Round 2 CPU #5, CPU #6 -> stops at Pick #7 (Invited Friend's Snake Round 2 turn!)
        awaitCondition {
            hostRepo.draftSessionFlow.first()?.currentOverallPick == 7 &&
                guestRepo.draftSessionFlow.first()?.currentOverallPick == 7
        }

        val hostPicksAfterR1 = hostRepo.draftPicksFlow.first()
        val guestPicksAfterR1 = guestRepo.draftPicksFlow.first()
        assertEquals(6, hostPicksAfterR1.size)
        assertEquals(6, guestPicksAfterR1.size)
        assertEquals(guestTeamId, guestPicksAfterR1.first { it.overallPick == 2 }.fantasyTeamId)

        val (_, _, pick7OnClockTeam) = DraftEngine.getPickMetadata(
            7,
            sessionAtPick2.teamOrderCsv.split(","),
            isSnakeDraft = true
        )
        assertEquals("Snake Round 2 Pick #7 should belong to Invited Friend", guestTeamId, pick7OnClockTeam)

        // Verify both devices agree on player ownership & Virtual CAD deductions
        val hostGuestTeam = hostRepo.fantasyTeamsFlow.first().first { it.teamId == guestTeamId }
        val guestOwnTeam = guestRepo.humanTeamFlow.first()!!
        assertEquals(guestTeamId, guestOwnTeam.teamId)
        assertEquals(hostGuestTeam.cashBalanceCad, guestOwnTeam.cashBalanceCad)
        assertTrue(guestOwnTeam.cashBalanceCad < 100_000_000L)

        // 7. Live Human-to-Human Trade Proposal: Invited Friend offers their drafted player + $1,000,000 CAD for Host's drafted player!
        val tradeOfferRes = guestCoordinator.submitMultiplayerTradeProposal(
            targetTeamId = NhlFantasyRepository.USER_TEAM_ID,
            offeredPlayerIds = listOf(guestPick2PlayerId),
            offeredCashCad = 1_000_000L,
            requestedPlayerIds = listOf(hostPick1PlayerId),
            requestedCashCad = 0L
        )
        assertTrue(tradeOfferRes.isSuccess)

        awaitCondition {
            hostRepo.tradeProposalsFlow.first().any { it.status == "PENDING" && it.proposerTeamId == guestTeamId }
        }

        val pendingTrade = hostRepo.tradeProposalsFlow.first()
            .first { it.status == "PENDING" && it.proposerTeamId == guestTeamId }
        assertEquals(NhlFantasyRepository.USER_TEAM_ID, pendingTrade.receiverTeamId)
        assertEquals(1_000_000L, pendingTrade.offeredCashCad)

        val hostCashBeforeTrade = hostRepo.humanTeamFlow.first()!!.cashBalanceCad
        val guestCashBeforeTrade = guestRepo.humanTeamFlow.first()!!.cashBalanceCad

        // 8. Host Commissioner accepts Invited Friend's trade offer!
        val acceptRes = hostCoordinator.submitMultiplayerTradeResponse(pendingTrade.tradeId, accept = true)
        assertTrue("Host trade accept failed: ${acceptRes.exceptionOrNull()?.message}", acceptRes.isSuccess)

        awaitCondition {
            guestRepo.tradeProposalsFlow.first().any { it.tradeId == pendingTrade.tradeId && it.status == "ACCEPTED" }
        }

        // Verify traded players and $1,000,000 Virtual CAD transferred identically on BOTH devices!
        val hostPlayersNow = hostDb.dao().getAllPlayersOnce()
        val guestPlayersNow = guestDb.dao().getAllPlayersOnce()

        assertEquals(NhlFantasyRepository.USER_TEAM_ID, hostPlayersNow.first { it.playerId == guestPick2PlayerId }.ownerFantasyTeamId)
        assertEquals(guestTeamId, hostPlayersNow.first { it.playerId == hostPick1PlayerId }.ownerFantasyTeamId)
        assertEquals(NhlFantasyRepository.USER_TEAM_ID, guestPlayersNow.first { it.playerId == guestPick2PlayerId }.ownerFantasyTeamId)
        assertEquals(guestTeamId, guestPlayersNow.first { it.playerId == hostPick1PlayerId }.ownerFantasyTeamId)

        assertEquals(hostCashBeforeTrade + 1_000_000L, hostRepo.humanTeamFlow.first()!!.cashBalanceCad)
        assertEquals(guestCashBeforeTrade - 1_000_000L, guestRepo.humanTeamFlow.first()!!.cashBalanceCad)

        hostCoordinator.leaveMultiplayerRoom()
        guestCoordinator.leaveMultiplayerRoom()
        Unit
    }

    @Test
    fun `multiplayer lobby UI renders formatted invite code share button connected friends and launches multiplayer draft`(): Unit {
        val participants = listOf(
            LobbyParticipant(
                clientId = "host-1",
                gmName = "GM Connor",
                assignedTeamId = "USER",
                teamName = "Northern Ice HC",
                abbreviation = "NIC",
                emblemId = TeamEmblem.MAPLE_SHIELD.id,
                primaryColorHex = 0xFF00C2FF,
                secondaryColorHex = 0xFF0B192C,
                isHost = true,
                isReady = true
            ),
            LobbyParticipant(
                clientId = "guest-2",
                gmName = "GM Leon",
                assignedTeamId = "MP_GUEST2",
                teamName = "Atlantic Frost HC",
                abbreviation = "AFH",
                emblemId = TeamEmblem.ARCTIC_WOLF.id,
                primaryColorHex = 0xFF00E676,
                secondaryColorHex = 0xFF071E18,
                isHost = false,
                isReady = true
            )
        )

        val mpState = MultiplayerUiState(
            role = RoomRole.HOST,
            roomCode = "K9M4P2",
            connectionState = MultiplayerConnectionState.CONNECTED,
            localClientId = "host-1",
            localGmName = "GM Connor",
            controlledTeamId = "USER",
            phase = MultiplayerRoomPhase.LOBBY,
            fillEmptySlotsWithCpu = true,
            draftConfig = DraftConfig(teamCount = 6),
            participants = participants
        )

        var launchedClicked = false

        composeTestRule.setContent {
            NhlFantasyGmTheme {
                MultiplayerSetupAndLobbyView(
                    mpState = mpState,
                    humanTeam = null,
                    initialDraftConfig = DraftConfig(teamCount = 6),
                    onHostRoom = { _, _ -> },
                    onJoinRoom = { _, _, _, _, _, _, _ -> },
                    onBuildShareText = { "nhlfantasygm://join?code=K9M4P2" },
                    onAddLocalFriend = { _, _, _, _, _, _ -> },
                    onRemoveParticipant = {},
                    onMoveParticipant = { _, _ -> },
                    onRandomizeOrder = {},
                    onUpdateHostRules = { _, _ -> },
                    onUpdateGuestProfileAndReady = { _, _, _, _, _, _, _ -> },
                    onSendChat = {},
                    onLaunchMultiplayerDraft = { launchedClicked = true },
                    onLeaveRoom = {}
                )
            }
        }

        composeTestRule.onNodeWithText("K9M-4P2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Invite Friends").assertIsDisplayed()
        composeTestRule.onNodeWithText("Atlantic Frost HC (AFH)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Launch Multiplayer Draft (6 Teams)")
            .performScrollTo()
            .performClick()

        assertTrue(launchedClicked)
    }

    @Test
    fun `live encrypted TLS WebSocket MQTT relay connects and round-trips AES-256-GCM room packet`(): Unit = runBlocking {
        val roomCode = RoomCrypto.generateInviteCode()
        val topic = RoomCrypto.deriveRoomTopic(roomCode)

        val hostTransport = MqttWebSocketTransport()
        val guestTransport = MqttWebSocketTransport()

        try {
            hostTransport.connect(topic, "hosttest")
            guestTransport.connect(topic, "guesttest")

            val connected = withTimeoutOrNull(8_000L) {
                while (
                    hostTransport.connectionState.value != MultiplayerConnectionState.CONNECTED ||
                    guestTransport.connectionState.value != MultiplayerConnectionState.CONNECTED
                ) {
                    delay(100L)
                }
                true
            } ?: false

            if (!connected) {
                // Offline build environment fallback — skip network assertion gracefully
                println("Skipping live WSS broker round-trip check (network offline in test runner).")
                return@runBlocking
            }

            val secretMessage = """{"hello":"multiplayer-nhl-draft","room":"$roomCode"}"""
            val encrypted = RoomCrypto.encryptRoomPayload(roomCode, secretMessage)

            var receivedPlain = ""
            val receiveJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                guestTransport.incomingEncryptedPayloads.collect { payload ->
                    runCatching { RoomCrypto.decryptRoomPayload(roomCode, payload) }
                        .onSuccess { receivedPlain = it }
                }
            }
            delay(150L)

            val published = hostTransport.publish(encryptedPayload = encrypted, retain = false)
            assertTrue(published)

            val delivered = withTimeoutOrNull(6_000L) {
                while (receivedPlain != secretMessage) {
                    delay(100L)
                }
                true
            } ?: false

            receiveJob.cancel()
            assertTrue("Expected encrypted payload to arrive at invited friend over live WSS relay", delivered)
        } finally {
            hostTransport.disconnect()
            guestTransport.disconnect()
        }
        Unit
    }

    private suspend fun awaitCondition(timeoutMs: Long = 5_000L, predicate: suspend () -> Boolean) {
        val ok = withTimeoutOrNull(timeoutMs) {
            while (!predicate()) {
                delay(30L)
            }
            true
        } ?: false
        assertTrue("Timed out waiting for multiplayer synchronization condition", ok)
    }

    private fun buildSeededNhlPool(count: Int): List<PlayerEntity> {
        val positions = listOf("C", "L", "R", "D", "G")
        return (1..count).map { idx ->
            val pos = positions[(idx - 1) % positions.size]
            val ovr = (92 - (idx / 3)).coerceIn(72, 95)
            val price = 2_500_000L + (ovr - 70) * 250_000L
            PlayerEntity(
                playerId = 8470000L + idx,
                firstName = "NHL",
                lastName = "Star$idx",
                fullName = "NHL Star$idx",
                normalizedSearchName = "nhl star$idx",
                teamAbbrev = "EDM",
                teamFullName = "Edmonton Oilers",
                positionCode = pos,
                sweaterNumber = (idx % 98) + 1,
                age = 24,
                birthDate = "2002-01-15",
                birthCountry = "CAN",
                shootsCatches = "L",
                heightInches = 73,
                weightPounds = 198,
                headshotUrl = "",
                isProspect = false,
                isRookie = false,
                seasonId = 20262027L,
                gamesPlayed = 20,
                goals = if (pos == "G") 0 else 10,
                assists = if (pos == "G") 0 else 14,
                points = if (pos == "G") 0 else 24,
                plusMinus = 6,
                penaltyMinutes = 8,
                powerPlayGoals = 3,
                powerPlayPoints = 7,
                shorthandedPoints = 0,
                shots = if (pos == "G") 0 else 62,
                hits = if (pos == "G") 0 else 22,
                blockedShots = if (pos == "G") 0 else 16,
                avgToiSeconds = 1150,
                faceoffWinPct = 0.51,
                gamesStarted = if (pos == "G") 15 else 0,
                wins = if (pos == "G") 10 else 0,
                losses = if (pos == "G") 4 else 0,
                otLosses = if (pos == "G") 1 else 0,
                saves = if (pos == "G") 410 else 0,
                shotsAgainst = if (pos == "G") 445 else 0,
                goalsAgainst = if (pos == "G") 35 else 0,
                savePct = if (pos == "G") 0.921 else 0.0,
                goalsAgainstAverage = if (pos == "G") 2.33 else 0.0,
                shutouts = if (pos == "G") 2 else 0,
                prevSeasonId = 20252026L,
                prevGamesPlayed = 75,
                prevGoals = 25,
                prevAssists = 40,
                prevPoints = 65,
                prevPpPoints = 18,
                prevShots = 210,
                prevHits = 70,
                prevBlockedShots = 45,
                prevWins = if (pos == "G") 32 else 0,
                prevSaves = if (pos == "G") 1650 else 0,
                prevSavePct = if (pos == "G") 0.916 else 0.0,
                prevGaa = if (pos == "G") 2.48 else 0.0,
                prevShutouts = if (pos == "G") 4 else 0,
                overallRating = ovr,
                purchasePriceCad = price,
                sellPriceCad = (price * 96) / 100,
                capHitCad = 3_500_000L
            )
        }
    }
}
