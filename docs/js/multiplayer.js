/**
 * NHL Fantasy GM — Cross-Platform Multiplayer Engine (iOS / Web <-> Android)
 * Wire-compatible implementation of RoomCrypto (SHA-256 + PBKDF2-HMAC-SHA256 + GZIP + AES-256-GCM)
 * and MqttPacketCodec (MQTT 3.1.1 over TLS WebSockets wss://broker.emqx.io:8084/mqtt).
 */

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const GCM_IV_BYTES = 12;
const PBKDF2_ITERATIONS = 4096;
const DEFAULT_WSS_BROKER = 'wss://broker.emqx.io:8084/mqtt';
const encoder = new TextEncoder();
const decoder = new TextDecoder();

export const RoomCrypto = {
  generateInviteCode(length = 6) {
    const rand = new Uint8Array(length);
    crypto.getRandomValues(rand);
    let out = '';
    for (let i = 0; i < length; i++) {
      out += CODE_ALPHABET[rand[i] % CODE_ALPHABET.length];
    }
    return out;
  },

  normalizeInviteCode(raw) {
    if (!raw) return '';
    let cleaned = String(raw).trim().toUpperCase();
    if (cleaned.startsWith('NHL-')) cleaned = cleaned.slice(4);
    cleaned = cleaned.replace(/[^A-Z0-9]/g, '');
    return cleaned.slice(0, 10);
  },

  formatInviteCodeForDisplay(code) {
    const norm = this.normalizeInviteCode(code);
    return norm.length === 6 ? `${norm.slice(0, 3)}-${norm.slice(3, 6)}` : norm;
  },

  async deriveRoomTopic(inviteCode) {
    const norm = this.normalizeInviteCode(inviteCode);
    const data = encoder.encode(`nhl-fantasy-gm-v1-topic:${norm}`);
    const hashBuf = await crypto.subtle.digest('SHA-256', data);
    const bytes = new Uint8Array(hashBuf).slice(0, 16);
    const hex = Array.from(bytes).map((b) => b.toString(16).padStart(2, '0')).join('');
    return `nhlfantasygm/v1/rooms/${hex}`;
  },

  async deriveAesKey(inviteCode) {
    const norm = this.normalizeInviteCode(inviteCode);
    const saltInput = encoder.encode(`nhl-fantasy-gm-key-salt:${norm}`);
    const saltHash = await crypto.subtle.digest('SHA-256', saltInput);
    const salt = new Uint8Array(saltHash).slice(0, 16);

    const baseKey = await crypto.subtle.importKey(
      'raw',
      encoder.encode(norm),
      { name: 'PBKDF2' },
      false,
      ['deriveKey']
    );

    return crypto.subtle.deriveKey(
      {
        name: 'PBKDF2',
        salt,
        iterations: PBKDF2_ITERATIONS,
        hash: 'SHA-256'
      },
      baseKey,
      { name: 'AES-GCM', length: 256 },
      false,
      ['encrypt', 'decrypt']
    );
  },

  async gzipCompress(uint8Input) {
    if (typeof CompressionStream === 'undefined') {
      throw new Error('GZIP CompressionStream not supported in this browser');
    }
    const cs = new CompressionStream('gzip');
    const writer = cs.writable.getWriter();
    writer.write(uint8Input);
    writer.close();
    const res = new Response(cs.readable);
    const buf = await res.arrayBuffer();
    return new Uint8Array(buf);
  },

  async gzipDecompress(uint8Compressed) {
    if (typeof DecompressionStream === 'undefined') {
      throw new Error('GZIP DecompressionStream not supported in this browser');
    }
    const ds = new DecompressionStream('gzip');
    const writer = ds.writable.getWriter();
    writer.write(uint8Compressed);
    writer.close();
    const res = new Response(ds.readable);
    const buf = await res.arrayBuffer();
    return new Uint8Array(buf);
  },

  uint8ToBase64(bytes) {
    let binary = '';
    const len = bytes.byteLength;
    for (let i = 0; i < len; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  },

  base64ToUint8(b64) {
    const binary = atob(b64.trim());
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  },

  async encryptRoomPayload(inviteCode, plainJson) {
    const key = await this.deriveAesKey(inviteCode);
    const compressed = await this.gzipCompress(encoder.encode(plainJson));
    const iv = new Uint8Array(GCM_IV_BYTES);
    crypto.getRandomValues(iv);
    const aad = encoder.encode('nhl-fantasy-gm-aad-v1');

    const cipherBuf = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv, additionalData: aad, tagLength: 128 },
      key,
      compressed
    );
    const cipherBytes = new Uint8Array(cipherBuf);
    const combined = new Uint8Array(iv.length + cipherBytes.length);
    combined.set(iv, 0);
    combined.set(cipherBytes, iv.length);
    return this.uint8ToBase64(combined);
  },

  async decryptRoomPayload(inviteCode, encodedPayload) {
    const combined = this.base64ToUint8(encodedPayload);
    if (combined.length <= GCM_IV_BYTES + 16) {
      throw new Error('Encrypted payload too short');
    }
    const iv = combined.slice(0, GCM_IV_BYTES);
    const cipherBytes = combined.slice(GCM_IV_BYTES);
    const key = await this.deriveAesKey(inviteCode);
    const aad = encoder.encode('nhl-fantasy-gm-aad-v1');

    const decryptedBuf = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv, additionalData: aad, tagLength: 128 },
      key,
      cipherBytes
    );
    const decompressed = await this.gzipDecompress(new Uint8Array(decryptedBuf));
    return decoder.decode(decompressed);
  }
};

export const MqttPacketCodec = {
  encodeRemainingLength(length) {
    const out = [];
    let x = length;
    do {
      let encodedByte = x % 128;
      x = Math.floor(x / 128);
      if (x > 0) encodedByte |= 0x80;
      out.push(encodedByte);
    } while (x > 0);
    return new Uint8Array(out);
  },

  decodeRemainingLength(bytes, startOffset = 1) {
    let multiplier = 1;
    let value = 0;
    let idx = startOffset;
    while (idx < bytes.length) {
      const encodedByte = bytes[idx] & 0xFF;
      value += (encodedByte & 127) * multiplier;
      idx++;
      if ((encodedByte & 128) === 0) {
        return [value, idx];
      }
      multiplier *= 128;
      if (multiplier > 128 * 128 * 128) return null;
    }
    return null;
  },

  wrapFixedHeader(headerByte, bodyBytes) {
    const remBytes = this.encodeRemainingLength(bodyBytes.length);
    const out = new Uint8Array(1 + remBytes.length + bodyBytes.length);
    out[0] = headerByte & 0xFF;
    out.set(remBytes, 1);
    out.set(bodyBytes, 1 + remBytes.length);
    return out;
  },

  encodeUtf8Field(str) {
    const strBytes = encoder.encode(str);
    const out = new Uint8Array(2 + strBytes.length);
    out[0] = (strBytes.length >> 8) & 0xFF;
    out[1] = strBytes.length & 0xFF;
    out.set(strBytes, 2);
    return out;
  },

  concatUint8(...arrays) {
    const total = arrays.reduce((acc, a) => acc + a.length, 0);
    const out = new Uint8Array(total);
    let offset = 0;
    for (const a of arrays) {
      out.set(a, offset);
      offset += a.length;
    }
    return out;
  },

  buildConnectPacket(clientId, keepAliveSec = 45) {
    const protoName = this.encodeUtf8Field('MQTT');
    const meta = new Uint8Array([
      0x04, // MQTT 3.1.1
      0x02, // Clean Session
      (keepAliveSec >> 8) & 0xFF,
      keepAliveSec & 0xFF
    ]);
    const cid = this.encodeUtf8Field(clientId);
    return this.wrapFixedHeader(0x10, this.concatUint8(protoName, meta, cid));
  },

  buildSubscribePacket(packetId, topic, qos = 1) {
    const pid = new Uint8Array([(packetId >> 8) & 0xFF, packetId & 0xFF]);
    const top = this.encodeUtf8Field(topic);
    const q = new Uint8Array([qos & 0x03]);
    return this.wrapFixedHeader(0x82, this.concatUint8(pid, top, q));
  },

  buildPublishPacket(topic, payloadUtf8, qos = 1, packetId = 1, retain = false) {
    const top = this.encodeUtf8Field(topic);
    const pid = qos > 0 ? new Uint8Array([(packetId >> 8) & 0xFF, packetId & 0xFF]) : new Uint8Array(0);
    const pay = encoder.encode(payloadUtf8);
    let headerByte = 0x30;
    if (qos === 1) headerByte |= 0x02;
    if (retain) headerByte |= 0x01;
    return this.wrapFixedHeader(headerByte, this.concatUint8(top, pid, pay));
  },

  buildPubAckPacket(packetId) {
    return this.wrapFixedHeader(0x40, new Uint8Array([(packetId >> 8) & 0xFF, packetId & 0xFF]));
  },

  buildPingReqPacket() {
    return new Uint8Array([0xC0, 0x00]);
  },

  buildDisconnectPacket() {
    return new Uint8Array([0xE0, 0x00]);
  },

  decodeFramePackets(frameBytes) {
    const packets = [];
    let cursor = 0;
    while (cursor < frameBytes.length) {
      const firstByte = frameBytes[cursor] & 0xFF;
      const packetType = (firstByte >> 4) & 0x0F;
      const flags = firstByte & 0x0F;
      const remPair = this.decodeRemainingLength(frameBytes, cursor + 1);
      if (!remPair) break;
      const [remLen, bodyStart] = remPair;
      const bodyEnd = bodyStart + remLen;
      if (bodyEnd > frameBytes.length) break;

      if (packetType === 2) {
        const rc = remLen >= 2 ? (frameBytes[bodyStart + 1] & 0xFF) : -1;
        packets.push({ type: 'CONNACK', returnCode: rc });
      } else if (packetType === 3) {
        const qos = (flags >> 1) & 0x03;
        const retain = (flags & 0x01) !== 0;
        if (bodyStart + 2 <= bodyEnd) {
          const topicLen = ((frameBytes[bodyStart] & 0xFF) << 8) | (frameBytes[bodyStart + 1] & 0xFF);
          const topicStart = bodyStart + 2;
          const topicEnd = topicStart + topicLen;
          if (topicEnd <= bodyEnd) {
            const topic = decoder.decode(frameBytes.slice(topicStart, topicEnd));
            let payloadStart = topicEnd;
            let packetId = null;
            if (qos > 0 && payloadStart + 2 <= bodyEnd) {
              packetId = ((frameBytes[payloadStart] & 0xFF) << 8) | (frameBytes[payloadStart + 1] & 0xFF);
              payloadStart += 2;
            }
            if (payloadStart <= bodyEnd) {
              const payloadUtf8 = decoder.decode(frameBytes.slice(payloadStart, bodyEnd));
              packets.push({ type: 'PUBLISH', topic, payloadUtf8, qos, packetId, retain });
            }
          }
        }
      } else if (packetType === 9) {
        packets.push({ type: 'SUBACK' });
      } else if (packetType === 13) {
        packets.push({ type: 'PINGRESP' });
      }
      cursor = bodyEnd;
    }
    return packets;
  }
};

export class MultiplayerRoomClient {
  constructor({ onStateChange, onEnvelope }) {
    this.onStateChange = onStateChange || (() => {});
    this.onEnvelope = onEnvelope || (() => {});
    this.ws = null;
    this.roomCode = '';
    this.roomTopic = '';
    this.clientId = 'ios_' + Math.random().toString(36).slice(2, 10);
    this.connectionState = 'DISCONNECTED';
    this.packetId = 10;
    this.pingTimer = null;
  }

  setState(state, error = '') {
    this.connectionState = state;
    this.onStateChange(state, error);
  }

  nextPacketId() {
    this.packetId = (this.packetId + 1) % 60000 || 10;
    return this.packetId;
  }

  async connect(roomCode) {
    this.disconnect();
    this.roomCode = RoomCrypto.normalizeInviteCode(roomCode);
    if (this.roomCode.length < 4) {
      this.setState('ERROR', 'Invite code must be at least 4 characters');
      return;
    }
    this.roomTopic = await RoomCrypto.deriveRoomTopic(this.roomCode);
    this.setState('CONNECTING');

    const mqttClientId = `nhlgm_${this.clientId}`;
    try {
      const ws = new WebSocket(DEFAULT_WSS_BROKER, 'mqtt');
      ws.binaryType = 'arraybuffer';
      this.ws = ws;

      ws.onopen = () => {
        const pkt = MqttPacketCodec.buildConnectPacket(mqttClientId, 45);
        ws.send(pkt);
      };

      ws.onmessage = async (event) => {
        const bytes = new Uint8Array(event.data);
        const decoded = MqttPacketCodec.decodeFramePackets(bytes);
        for (const pkt of decoded) {
          if (pkt.type === 'CONNACK') {
            if (pkt.returnCode === 0) {
              const subPkt = MqttPacketCodec.buildSubscribePacket(this.nextPacketId(), this.roomTopic, 1);
              ws.send(subPkt);
            } else {
              this.setState('ERROR', `MQTT handshake rejected (${pkt.returnCode})`);
            }
          } else if (pkt.type === 'SUBACK') {
            this.setState('CONNECTED');
            this.startKeepAlive();
          } else if (pkt.type === 'PUBLISH') {
            if (pkt.qos === 1 && pkt.packetId != null) {
              ws.send(MqttPacketCodec.buildPubAckPacket(pkt.packetId));
            }
            if (pkt.topic === this.roomTopic && pkt.payloadUtf8) {
              try {
                const jsonStr = await RoomCrypto.decryptRoomPayload(this.roomCode, pkt.payloadUtf8);
                const env = JSON.parse(jsonStr);
                this.onEnvelope(env);
              } catch (err) {
                console.warn('Dropped undecryptable room packet:', err);
              }
            }
          }
        }
      };

      ws.onerror = () => {
        this.setState('ERROR', 'Relay connection error');
      };

      ws.onclose = () => {
        this.stopKeepAlive();
        if (this.connectionState !== 'DISCONNECTED') {
          this.setState('DISCONNECTED');
        }
      };
    } catch (e) {
      this.setState('ERROR', e.message || 'WebSocket connection failed');
    }
  }

  startKeepAlive() {
    this.stopKeepAlive();
    this.pingTimer = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(MqttPacketCodec.buildPingReqPacket());
      }
    }, 20000);
  }

  stopKeepAlive() {
    if (this.pingTimer) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
  }

  async publishEnvelope(envelopeObj, retain = false) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN || this.connectionState !== 'CONNECTED') {
      return false;
    }
    const jsonStr = JSON.stringify(envelopeObj);
    const encrypted = await RoomCrypto.encryptRoomPayload(this.roomCode, jsonStr);
    const pkt = MqttPacketCodec.buildPublishPacket(
      this.roomTopic,
      encrypted,
      1,
      this.nextPacketId(),
      retain
    );
    this.ws.send(pkt);
    return true;
  }

  disconnect() {
    this.stopKeepAlive();
    if (this.ws) {
      try {
        if (this.ws.readyState === WebSocket.OPEN) {
          this.ws.send(MqttPacketCodec.buildDisconnectPacket());
        }
        this.ws.close(1000, 'Leave room');
      } catch (_) {}
      this.ws = null;
    }
    this.setState('DISCONNECTED');
  }
}
