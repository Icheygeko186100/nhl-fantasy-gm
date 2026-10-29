/**
 * NHL Fantasy GM — Companion Zero-Knowledge Multiplayer WebSocket Relay Server
 *
 * By default, the Android app connects zero-setup over TLS WebSockets using
 * end-to-end AES-256-GCM encrypted room topics. If you prefer to self-host your
 * own dedicated relay server (LAN, VPS, Cloud Run, Fly.io, Docker), run:
 *
 *   npm install && PORT=8787 npm start
 *
 * Then set `ws://<host>:8787/ws` (or `wss://...` behind TLS) in:
 *   League -> Settings -> Multiplayer & Custom Relay Server.
 *
 * Security Note:
 * - Room topics are SHA-256 hashes of the 6-character invite code.
 * - Payloads are GZIP + AES-256-GCM encrypted on-device before transmission.
 * - The relay server never sees room codes or plaintext game data.
 */

const http = require("http");
const { WebSocketServer, WebSocket } = require("ws");

const PORT = Number(process.env.PORT || 8787);
const RETAIN_TTL_MS = 24 * 60 * 60 * 1000; // 24 hours
const MAX_PAYLOAD_BYTES = 512 * 1024; // 512 KB max frame

/** @type {Map<string, Set<WebSocket>>} */
const subscribersByTopic = new Map();

/** @type {Map<string, { payload: string, updatedAt: number }>} */
const retainedByTopic = new Map();

const server = http.createServer((req, res) => {
  if (req.url === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        status: "ok",
        service: "nhl-fantasy-gm-relay",
        version: "1.1.0",
        activeRooms: subscribersByTopic.size,
        retainedSnapshots: retainedByTopic.size,
      })
    );
    return;
  }
  res.writeHead(200, { "Content-Type": "text/plain" });
  res.end("NHL Fantasy GM Encrypted Multiplayer Relay Server (v1.1.0)\n");
});

const wss = new WebSocketServer({
  server,
  maxPayload: MAX_PAYLOAD_BYTES,
});

function removeSocketFromAllTopics(ws) {
  for (const [topic, set] of subscribersByTopic.entries()) {
    set.delete(ws);
    if (set.size === 0) {
      subscribersByTopic.delete(topic);
    }
  }
}

wss.on("connection", (ws) => {
  ws.isAlive = true;
  ws.on("pong", () => {
    ws.isAlive = true;
  });

  ws.on("message", (raw) => {
    let frame;
    try {
      frame = JSON.parse(raw.toString("utf8"));
    } catch {
      return;
    }
    if (!frame || typeof frame.action !== "string" || typeof frame.topic !== "string") {
      return;
    }
    const topic = frame.topic.trim();
    if (!topic || topic.length > 160) return;

    if (frame.action === "subscribe") {
      let set = subscribersByTopic.get(topic);
      if (!set) {
        set = new Set();
        subscribersByTopic.set(topic, set);
      }
      set.add(ws);

      ws.send(JSON.stringify({ action: "subscribed", topic }));

      const retained = retainedByTopic.get(topic);
      if (retained && Date.now() - retained.updatedAt < RETAIN_TTL_MS) {
        ws.send(
          JSON.stringify({
            action: "message",
            topic,
            payload: retained.payload,
            retain: true,
          })
        );
      }
    } else if (frame.action === "publish") {
      const payload = typeof frame.payload === "string" ? frame.payload : "";
      if (!payload) return;

      const retain = Boolean(frame.retain);
      if (retain) {
        retainedByTopic.set(topic, { payload, updatedAt: Date.now() });
      }

      const outbound = JSON.stringify({
        action: "message",
        topic,
        payload,
        retain,
      });

      const subscribers = subscribersByTopic.get(topic);
      if (subscribers) {
        for (const client of subscribers) {
          if (client.readyState === WebSocket.OPEN) {
            client.send(outbound);
          }
        }
      }
    }
  });

  ws.on("close", () => removeSocketFromAllTopics(ws));
  ws.on("error", () => removeSocketFromAllTopics(ws));
});

// Periodic keepalive & expired snapshot cleanup
const interval = setInterval(() => {
  const now = Date.now();
  for (const [topic, entry] of retainedByTopic.entries()) {
    if (now - entry.updatedAt >= RETAIN_TTL_MS) {
      retainedByTopic.delete(topic);
    }
  }
  for (const ws of wss.clients) {
    if (ws.isAlive === false) {
      removeSocketFromAllTopics(ws);
      ws.terminate();
      continue;
    }
    ws.isAlive = false;
    ws.ping();
  }
}, 30_000);

wss.on("close", () => clearInterval(interval));

server.listen(PORT, () => {
  console.log(`[NHL Fantasy GM Relay] Listening on port ${PORT} (ws://0.0.0.0:${PORT}/ws)`);
});
