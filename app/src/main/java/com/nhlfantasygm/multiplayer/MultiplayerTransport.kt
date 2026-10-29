package com.nhlfantasygm.multiplayer

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

interface MultiplayerTransport {
    val connectionState: StateFlow<MultiplayerConnectionState>
    val incomingEncryptedPayloads: SharedFlow<String>
    val lastError: StateFlow<String>

    fun connect(roomTopic: String, clientTag: String)
    fun publish(encryptedPayload: String, retain: Boolean = false): Boolean
    fun disconnect()
}

/**
 * Pure Kotlin binary MQTT 3.1.1 packet encoder & decoder over WebSocket binary frames.
 * Zero external AAR dependencies; Android 16 / One UI 8 safe.
 */
object MqttPacketCodec {

    sealed class DecodedPacket {
        data class ConnAck(val returnCode: Int) : DecodedPacket()
        data class SubAck(val packetId: Int) : DecodedPacket()
        data class Publish(
            val topic: String,
            val payloadUtf8: String,
            val qos: Int,
            val packetId: Int?,
            val retain: Boolean
        ) : DecodedPacket()
        data class PubAck(val packetId: Int) : DecodedPacket()
        data object PingResp : DecodedPacket()
        data class Other(val packetType: Int) : DecodedPacket()
    }

    fun encodeRemainingLength(length: Int): ByteArray {
        require(length >= 0) { "Remaining length cannot be negative" }
        val out = ByteArrayOutputStream(4)
        var x = length
        do {
            var encodedByte = x % 128
            x /= 128
            if (x > 0) {
                encodedByte = encodedByte or 0x80
            }
            out.write(encodedByte)
        } while (x > 0)
        return out.toByteArray()
    }

    /**
     * Returns `(decodedLength, nextReadIndex)` or `null` if malformed/truncated.
     */
    fun decodeRemainingLength(bytes: ByteArray, startOffset: Int = 1): Pair<Int, Int>? {
        var multiplier = 1
        var value = 0
        var idx = startOffset
        while (idx < bytes.size) {
            val encodedByte = bytes[idx].toInt() and 0xFF
            value += (encodedByte and 127) * multiplier
            idx++
            if ((encodedByte and 128) == 0) {
                return value to idx
            }
            multiplier *= 128
            if (multiplier > 128 * 128 * 128) return null
        }
        return null
    }

    fun buildConnectPacket(clientId: String, keepAliveSec: Int = 45): ByteArray {
        val body = ByteArrayOutputStream()
        writeUtf8String(body, "MQTT")
        body.write(0x04) // Protocol Level 4 (MQTT 3.1.1)
        body.write(0x02) // Connect Flags: Clean Session = 1
        body.write((keepAliveSec shr 8) and 0xFF)
        body.write(keepAliveSec and 0xFF)
        writeUtf8String(body, clientId)
        return wrapFixedHeader(0x10, body.toByteArray())
    }

    fun buildSubscribePacket(packetId: Int, topic: String, qos: Int = 1): ByteArray {
        val body = ByteArrayOutputStream()
        body.write((packetId shr 8) and 0xFF)
        body.write(packetId and 0xFF)
        writeUtf8String(body, topic)
        body.write(qos and 0x03)
        return wrapFixedHeader(0x82, body.toByteArray())
    }

    fun buildPublishPacket(
        topic: String,
        payloadUtf8: String,
        qos: Int = 1,
        packetId: Int = 1,
        retain: Boolean = false
    ): ByteArray {
        val body = ByteArrayOutputStream()
        writeUtf8String(body, topic)
        if (qos > 0) {
            body.write((packetId shr 8) and 0xFF)
            body.write(packetId and 0xFF)
        }
        val payloadBytes = payloadUtf8.toByteArray(StandardCharsets.UTF_8)
        body.write(payloadBytes)

        var headerByte = 0x30
        if (qos == 1) headerByte = headerByte or 0x02
        if (retain) headerByte = headerByte or 0x01
        return wrapFixedHeader(headerByte, body.toByteArray())
    }

    fun buildPubAckPacket(packetId: Int): ByteArray {
        val body = byteArrayOf(
            ((packetId shr 8) and 0xFF).toByte(),
            (packetId and 0xFF).toByte()
        )
        return wrapFixedHeader(0x40, body)
    }

    fun buildPingReqPacket(): ByteArray = byteArrayOf(0xC0.toByte(), 0x00.toByte())

    fun buildDisconnectPacket(): ByteArray = byteArrayOf(0xE0.toByte(), 0x00.toByte())

    /**
     * Decodes one or more concatenated MQTT packets from a single WebSocket binary frame.
     */
    fun decodeFramePackets(frameBytes: ByteArray): List<DecodedPacket> {
        val packets = mutableListOf<DecodedPacket>()
        var cursor = 0
        while (cursor < frameBytes.size) {
            val firstByte = frameBytes[cursor].toInt() and 0xFF
            val packetType = (firstByte shr 4) and 0x0F
            val flags = firstByte and 0x0F
            val remPair = decodeRemainingLength(frameBytes, cursor + 1) ?: break
            val (remLen, bodyStart) = remPair
            val bodyEnd = bodyStart + remLen
            if (bodyEnd > frameBytes.size) break

            when (packetType) {
                2 -> { // CONNACK
                    val rc = if (remLen >= 2) frameBytes[bodyStart + 1].toInt() and 0xFF else -1
                    packets.add(DecodedPacket.ConnAck(rc))
                }
                3 -> { // PUBLISH
                    val qos = (flags shr 1) and 0x03
                    val retain = (flags and 0x01) != 0
                    if (bodyStart + 2 <= bodyEnd) {
                        val topicLen = ((frameBytes[bodyStart].toInt() and 0xFF) shl 8) or
                            (frameBytes[bodyStart + 1].toInt() and 0xFF)
                        val topicStart = bodyStart + 2
                        val topicEnd = topicStart + topicLen
                        if (topicEnd <= bodyEnd) {
                            val topic = String(frameBytes, topicStart, topicLen, StandardCharsets.UTF_8)
                            var payloadStart = topicEnd
                            var packetId: Int? = null
                            if (qos > 0 && payloadStart + 2 <= bodyEnd) {
                                packetId = ((frameBytes[payloadStart].toInt() and 0xFF) shl 8) or
                                    (frameBytes[payloadStart + 1].toInt() and 0xFF)
                                payloadStart += 2
                            }
                            if (payloadStart <= bodyEnd) {
                                val payload = String(
                                    frameBytes,
                                    payloadStart,
                                    bodyEnd - payloadStart,
                                    StandardCharsets.UTF_8
                                )
                                packets.add(DecodedPacket.Publish(topic, payload, qos, packetId, retain))
                            }
                        }
                    }
                }
                4 -> { // PUBACK
                    val pid = if (remLen >= 2) {
                        ((frameBytes[bodyStart].toInt() and 0xFF) shl 8) or
                            (frameBytes[bodyStart + 1].toInt() and 0xFF)
                    } else 0
                    packets.add(DecodedPacket.PubAck(pid))
                }
                9 -> { // SUBACK
                    val pid = if (remLen >= 2) {
                        ((frameBytes[bodyStart].toInt() and 0xFF) shl 8) or
                            (frameBytes[bodyStart + 1].toInt() and 0xFF)
                    } else 0
                    packets.add(DecodedPacket.SubAck(pid))
                }
                13 -> packets.add(DecodedPacket.PingResp)
                else -> packets.add(DecodedPacket.Other(packetType))
            }
            cursor = bodyEnd
        }
        return packets
    }

    private fun wrapFixedHeader(headerByte: Int, bodyBytes: ByteArray): ByteArray {
        val remBytes = encodeRemainingLength(bodyBytes.size)
        val out = ByteArray(1 + remBytes.size + bodyBytes.size)
        out[0] = headerByte.toByte()
        System.arraycopy(remBytes, 0, out, 1, remBytes.size)
        System.arraycopy(bodyBytes, 0, out, 1 + remBytes.size, bodyBytes.size)
        return out
    }

    private fun writeUtf8String(out: ByteArrayOutputStream, str: String) {
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        out.write((bytes.size shr 8) and 0xFF)
        out.write(bytes.size and 0xFF)
        out.write(bytes)
    }
}

/**
 * Default zero-setup internet multiplayer transport using encrypted MQTT 3.1.1 over TLS WebSockets (`wss://`).
 */
class MqttWebSocketTransport(
    private val brokerWsUrl: String = DEFAULT_PUBLIC_WSS_BROKER,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : MultiplayerTransport {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Streaming WebSocket
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val _connectionState = MutableStateFlow(MultiplayerConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<MultiplayerConnectionState> = _connectionState.asStateFlow()

    private val _incomingEncryptedPayloads = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incomingEncryptedPayloads: SharedFlow<String> = _incomingEncryptedPayloads.asSharedFlow()

    private val _lastError = MutableStateFlow("")
    override val lastError: StateFlow<String> = _lastError.asStateFlow()

    private val packetIdCounter = AtomicInteger(10)

    @Volatile
    private var activeSocket: WebSocket? = null

    @Volatile
    private var activeTopic: String = ""

    @Volatile
    private var activeClientTag: String = ""

    @Volatile
    private var userRequestedDisconnect: Boolean = false

    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt: Int = 0

    override fun connect(roomTopic: String, clientTag: String) {
        userRequestedDisconnect = false
        reconnectJob?.cancel()
        pingJob?.cancel()
        runCatching { activeSocket?.close(1000, "Switching room") }
        activeSocket = null

        activeTopic = roomTopic
        activeClientTag = clientTag
        reconnectAttempt = 0
        openWebSocketInternal(isReconnect = false)
    }

    private fun openWebSocketInternal(isReconnect: Boolean) {
        val topic = activeTopic
        if (topic.isBlank() || userRequestedDisconnect) return

        _connectionState.value = if (isReconnect) {
            MultiplayerConnectionState.RECONNECTING
        } else {
            MultiplayerConnectionState.CONNECTING
        }

        val mqttClientId = "nhlgm_${activeClientTag.take(8)}_${UUID.randomUUID().toString().take(6)}"
        val request = Request.Builder()
            .url(brokerWsUrl)
            .header("Sec-WebSocket-Protocol", "mqtt")
            .build()

        activeSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val connectBytes = MqttPacketCodec.buildConnectPacket(mqttClientId, keepAliveSec = 45)
                webSocket.send(connectBytes.toByteString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val decodedList = MqttPacketCodec.decodeFramePackets(bytes.toByteArray())
                for (pkt in decodedList) {
                    when (pkt) {
                        is MqttPacketCodec.DecodedPacket.ConnAck -> {
                            if (pkt.returnCode == 0) {
                                val subId = nextPacketId()
                                webSocket.send(
                                    MqttPacketCodec.buildSubscribePacket(subId, topic, qos = 1).toByteString()
                                )
                            } else {
                                _lastError.value = "Relay rejected MQTT handshake (code ${pkt.returnCode})"
                                _connectionState.value = MultiplayerConnectionState.ERROR
                            }
                        }
                        is MqttPacketCodec.DecodedPacket.SubAck -> {
                            reconnectAttempt = 0
                            _lastError.value = ""
                            _connectionState.value = MultiplayerConnectionState.CONNECTED
                            startKeepAliveLoop(webSocket)
                        }
                        is MqttPacketCodec.DecodedPacket.Publish -> {
                            if (pkt.qos == 1 && pkt.packetId != null) {
                                webSocket.send(MqttPacketCodec.buildPubAckPacket(pkt.packetId).toByteString())
                            }
                            if (pkt.topic == topic && pkt.payloadUtf8.isNotBlank()) {
                                _incomingEncryptedPayloads.tryEmit(pkt.payloadUtf8)
                            }
                        }
                        else -> Unit
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                pingJob?.cancel()
                if (!userRequestedDisconnect) {
                    scheduleReconnect("Connection closed ($reason)")
                } else {
                    _connectionState.value = MultiplayerConnectionState.DISCONNECTED
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                pingJob?.cancel()
                if (!userRequestedDisconnect) {
                    scheduleReconnect(t.message ?: "Network unreachable")
                }
            }
        })
    }

    private fun startKeepAliveLoop(ws: WebSocket) {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && !userRequestedDisconnect) {
                delay(20_000L)
                ws.send(MqttPacketCodec.buildPingReqPacket().toByteString())
            }
        }
    }

    private fun scheduleReconnect(errorMsg: String) {
        if (userRequestedDisconnect) return
        _lastError.value = errorMsg
        _connectionState.value = MultiplayerConnectionState.RECONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val backoffMs = (1500L * (1 shl reconnectAttempt.coerceAtMost(4))).coerceAtMost(15_000L)
            reconnectAttempt++
            delay(backoffMs)
            if (!userRequestedDisconnect && activeTopic.isNotBlank()) {
                openWebSocketInternal(isReconnect = true)
            }
        }
    }

    override fun publish(encryptedPayload: String, retain: Boolean): Boolean {
        val ws = activeSocket ?: return false
        val topic = activeTopic
        if (topic.isBlank() || _connectionState.value != MultiplayerConnectionState.CONNECTED) return false

        val pkt = MqttPacketCodec.buildPublishPacket(
            topic = topic,
            payloadUtf8 = encryptedPayload,
            qos = 1,
            packetId = nextPacketId(),
            retain = retain
        )
        return ws.send(pkt.toByteString())
    }

    override fun disconnect() {
        userRequestedDisconnect = true
        reconnectJob?.cancel()
        pingJob?.cancel()
        val ws = activeSocket
        activeSocket = null
        activeTopic = ""
        runCatching {
            ws?.send(MqttPacketCodec.buildDisconnectPacket().toByteString())
            ws?.close(1000, "User left multiplayer room")
        }
        _connectionState.value = MultiplayerConnectionState.DISCONNECTED
    }

    private fun nextPacketId(): Int {
        val next = packetIdCounter.incrementAndGet()
        if (next > 60_000) packetIdCounter.set(10)
        return (next and 0xFFFF).coerceAtLeast(1)
    }

    companion object {
        const val DEFAULT_PUBLIC_WSS_BROKER = "wss://broker.emqx.io:8084/mqtt"
    }
}

/**
 * Companion JSON WebSocket transport for users running the self-hosted `backend/relay-server.js` server.
 */
class CustomRelayWebSocketTransport(
    private val relayWsUrl: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : MultiplayerTransport {

    @Serializable
    private data class RelayFrame(
        val action: String, // "subscribe", "publish", "message", "subscribed"
        val topic: String = "",
        val payload: String = "",
        val retain: Boolean = false
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val _connectionState = MutableStateFlow(MultiplayerConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<MultiplayerConnectionState> = _connectionState.asStateFlow()

    private val _incomingEncryptedPayloads = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incomingEncryptedPayloads: SharedFlow<String> = _incomingEncryptedPayloads.asSharedFlow()

    private val _lastError = MutableStateFlow("")
    override val lastError: StateFlow<String> = _lastError.asStateFlow()

    @Volatile
    private var activeSocket: WebSocket? = null
    @Volatile
    private var activeTopic: String = ""
    @Volatile
    private var userRequestedDisconnect = false
    private var reconnectJob: Job? = null

    override fun connect(roomTopic: String, clientTag: String) {
        userRequestedDisconnect = false
        reconnectJob?.cancel()
        runCatching { activeSocket?.close(1000, "Switching room") }
        activeTopic = roomTopic
        openInternal(isReconnect = false)
    }

    private fun openInternal(isReconnect: Boolean) {
        val topic = activeTopic
        if (topic.isBlank() || userRequestedDisconnect) return
        _connectionState.value = if (isReconnect) MultiplayerConnectionState.RECONNECTING else MultiplayerConnectionState.CONNECTING

        val req = Request.Builder().url(relayWsUrl).build()
        activeSocket = okHttpClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(json.encodeToString(RelayFrame(action = "subscribe", topic = topic)))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = runCatching { json.decodeFromString<RelayFrame>(text) }.getOrNull() ?: return
                when (frame.action) {
                    "subscribed" -> {
                        _lastError.value = ""
                        _connectionState.value = MultiplayerConnectionState.CONNECTED
                    }
                    "message" -> {
                        if (frame.topic == topic && frame.payload.isNotBlank()) {
                            _incomingEncryptedPayloads.tryEmit(frame.payload)
                        }
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!userRequestedDisconnect) scheduleReconnect(reason)
                else _connectionState.value = MultiplayerConnectionState.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!userRequestedDisconnect) scheduleReconnect(t.message ?: "Relay unreachable")
            }
        })
    }

    private fun scheduleReconnect(err: String) {
        _lastError.value = err
        _connectionState.value = MultiplayerConnectionState.RECONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(2500L)
            if (!userRequestedDisconnect && activeTopic.isNotBlank()) openInternal(isReconnect = true)
        }
    }

    override fun publish(encryptedPayload: String, retain: Boolean): Boolean {
        val ws = activeSocket ?: return false
        if (_connectionState.value != MultiplayerConnectionState.CONNECTED) return false
        return ws.send(
            json.encodeToString(
                RelayFrame(action = "publish", topic = activeTopic, payload = encryptedPayload, retain = retain)
            )
        )
    }

    override fun disconnect() {
        userRequestedDisconnect = true
        reconnectJob?.cancel()
        runCatching { activeSocket?.close(1000, "Leave") }
        activeSocket = null
        activeTopic = ""
        _connectionState.value = MultiplayerConnectionState.DISCONNECTED
    }
}

/**
 * Factory that chooses between the default zero-setup encrypted MQTT-over-WSS relay
 * and an optional custom WebSocket server URL configured by the user.
 */
fun interface MultiplayerTransportFactory {
    fun create(customRelayUrl: String): MultiplayerTransport

    companion object {
        val Default = MultiplayerTransportFactory { customUrl ->
            val trimmed = customUrl.trim()
            when {
                trimmed.isBlank() -> MqttWebSocketTransport()
                trimmed.endsWith("/mqtt", ignoreCase = true) -> MqttWebSocketTransport(brokerWsUrl = trimmed)
                else -> CustomRelayWebSocketTransport(relayWsUrl = trimmed)
            }
        }
    }
}

/**
 * Deterministic in-memory multi-client relay hub supporting Retained messages for multi-device unit & integration tests.
 */
class InMemoryLoopbackTransportHub {
    private val retainedByTopic = ConcurrentHashMap<String, String>()
    private val subscribersByTopic = ConcurrentHashMap<String, CopyOnWriteClientSet>()

    private class CopyOnWriteClientSet {
        val clients = java.util.concurrent.CopyOnWriteArraySet<ClientEndpoint>()
    }

    fun createClientTransport(): MultiplayerTransport = ClientEndpoint()

    private inner class ClientEndpoint : MultiplayerTransport {
        private val _state = MutableStateFlow(MultiplayerConnectionState.DISCONNECTED)
        override val connectionState: StateFlow<MultiplayerConnectionState> = _state.asStateFlow()

        private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 128)
        override val incomingEncryptedPayloads: SharedFlow<String> = _incoming.asSharedFlow()

        private val _err = MutableStateFlow("")
        override val lastError: StateFlow<String> = _err.asStateFlow()

        @Volatile
        private var currentTopic: String = ""

        override fun connect(roomTopic: String, clientTag: String) {
            disconnect()
            currentTopic = roomTopic
            val bucket = subscribersByTopic.getOrPut(roomTopic) { CopyOnWriteClientSet() }
            bucket.clients.add(this)
            _state.value = MultiplayerConnectionState.CONNECTED

            // Immediately deliver retained room snapshot just like an MQTT broker
            retainedByTopic[roomTopic]?.let { retained ->
                _incoming.tryEmit(retained)
            }
        }

        override fun publish(encryptedPayload: String, retain: Boolean): Boolean {
            val topic = currentTopic
            if (topic.isBlank() || _state.value != MultiplayerConnectionState.CONNECTED) return false
            if (retain) {
                retainedByTopic[topic] = encryptedPayload
            }
            subscribersByTopic[topic]?.clients?.forEach { subscriber ->
                subscriber.deliver(encryptedPayload)
            }
            return true
        }

        fun deliver(payload: String) {
            _incoming.tryEmit(payload)
        }

        override fun disconnect() {
            val oldTopic = currentTopic
            if (oldTopic.isNotBlank()) {
                subscribersByTopic[oldTopic]?.clients?.remove(this)
            }
            currentTopic = ""
            _state.value = MultiplayerConnectionState.DISCONNECTED
        }
    }
}
