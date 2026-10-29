# NHL Fantasy GM — Ultimate Fantasy Hockey Simulator (Android)

**NHL Fantasy GM** (`v1.1.0`) is a native Kotlin & Jetpack Compose Android hockey general-manager simulator built for **Android 16 / One UI 8 (API 36)** and optimized for high-density displays such as the **Samsung Galaxy S22 Ultra** — featuring both **Single-Player vs Smart CPU GMs** and **Real-Time Multiplayer Drafts, Invites & Leagues with Friends**.

> **Simulation & No-Gambling Notice:** All financial balances, player prices, contracts, and matchup rewards use **fictional Virtual Canadian Dollars (CAD)** (`$100,000,000 CAD` default starting treasury). There is **zero** real-money gambling, wagering, deposits, withdrawals, or paid player purchases.

---

## 1. Real-Time Multiplayer Drafts, Friend Invites & Leagues (`v1.1.0`)

Real players can invite friends to join their draft and play together across Android devices — with **zero account signup** and **zero embedded private API keys**:

- **Host & Invite Real Players:**
  - Open **Draft → Multiplayer Friends → Create Lobby & Get Invite Code** to generate a 6-character Room Invite Code (e.g. `K9M-4P2`) and a one-tap deep link (`nhlfantasygm://join?code=K9M4P2`).
  - Tap **Invite Friends** to share the invite code and deep link via SMS, WhatsApp, Discord, Messenger, or Email using Android's native Share Sheet.
- **One-Tap Deep Link or Code Join:**
  - Friends tap the `nhlfantasygm://join?code=...` link (or enter `K9M-4P2` under **Join Friend's Draft**), customize their own Franchise Name, 3-Letter Abbreviation, Crest Emblem, and Team Colors, and mark **Ready to Draft ✓**.
- **Flexible Human + Optional CPU League Slots (Never Fake Bots):**
  - Supports **2 to 16 teams**: pure Human-vs-Human leagues (`0 CPU GMs`), Online Friends + Local **Pass-and-Play** Friends on the same phone, or Human Friends + explicitly labeled **CPU GMs** filling remaining slots. CPU GMs are never disguised as fake online users.
- **Authoritative Conflict-Free Draft & Live War Room:**
  - Real-time synchronized draft board, turn clock, positional/cap validation, **Live Draft & Lobby Chat**, synchronized lineups, Marketplace signings/sales, **live Human-to-Human Player + Virtual CAD Trade Proposals** (`PENDING` offers that friends can Accept or Reject on their phones), and Commissioner week simulation broadcasts.
- **End-to-End Encrypted (`AES-256-GCM`) Zero-Setup Relay + Self-Hosted Option:**
  - All room payloads are compressed (`GZIP`) and encrypted on-device with **`AES-256-GCM`** (`PBKDF2-HMAC-SHA256` derived from the 6-character invite code) over TLS WebSockets (`MQTT 3.1.1 over WSS` out-of-the-box).
  - Includes an optional zero-knowledge Node.js WebSocket relay server in [`backend/relay-server.js`](backend/relay-server.js) (`Dockerfile` included) configurable under **League → Settings → Multiplayer & Custom Relay Server**.

---

## 2. Architecture & Technology Stack

- **Language & UI:** 100% Kotlin (`2.0.21`) + Jetpack Compose (Material 3, custom `Canvas` hockey crests and statistical charts, edge-to-edge One UI 8 layout).
- **Target / Compile SDK:** `compileSdk = 36`, `targetSdk = 36` (Android 16), `minSdk = 28`.
- **Architecture:** Clean separation across:
  - `multiplayer` — `RoomCrypto` (`AES-256-GCM` + `GZIP` + `nhlfantasygm://join` deep links), `MqttPacketCodec` & `MqttWebSocketTransport` / `CustomRelayWebSocketTransport`, and `MultiplayerCoordinator` (authoritative Host & Guest state synchronization).
  - `data/remote` — OkHttp + Kotlinx Serialization clients for official NHL and public hockey feeds with concurrency throttling and error recovery.
  - `data/local` — **Room SQLite Database (`v2` with lossless `MIGRATION_1_2`)** (`nhl_fantasy_gm.db`) persisting all 32 NHL teams, 1,200+ active players & prospects, multi-GM franchise ownership, lineups, draft sessions, trades, season schedules, historical banners, and transaction ledgers; **DataStore Preferences** for user & room settings; **Android Keystore `EncryptedSharedPreferences`** for optional external API credentials.
  - `domain` — Pure, unit-tested game engines:
    - `FantasyScoringEngine` (configurable fantasy weights, daily/weekly/season points, bargains, breakouts, transparent breakdowns)
    - `PlayerValuationEngine` (transparent OVR rating, simulated NHL salary cap hit, purchase/sell prices, deterministic price-change explanations)
    - `GameTransactionEngine` (budget, salary cap, duplicate-ownership, roster-size, and high-value confirmation validation + P&L statements)
    - `DraftEngine` (Snake & Standard drafts, positional requirement feasibility checks, smart CPU GM AI, local Undo rollback, post-draft grades)
    - `LineupAnalyticsEngine` (Forward Lines 1–4, Defense Pairings 1–3, Goalies 1–2, Bench, IR, optimal lineup generator, strict position/injury slot validation)
    - `TradeEvaluationEngine` (player-for-player and player + virtual CAD trades with both **Real Friends** and **CPU GMs**)
    - `SeasonSimulationEngine` (14-week round-robin schedule supporting 2–16 teams, real-NHL-stats matchup scoring + optional offline simulation fallback, Semifinal & Championship Final bracket, Season Awards)
  - `worker` — `WorkManager` (`NhlDataSyncWorker`) periodic background statistics synchronization.
  - `ui` — 5-tab bottom navigation (`Home`, `Draft`, `Marketplace`, `My Team`, `League`) with Dark Rink theme by default and optional Ice Arena Light theme.

---

## 3. Real NHL Data Sources Used

No API keys are embedded in the APK, and **no paid subscription or API key is required** for full live functionality:

1. **Official NHL Web API (`https://api-web.nhle.com/v1`)**
   - `/standings/now` — All 32 active NHL franchises, conference/division alignment, active season ID, and real-time NHL standings.
   - `/roster/{teamAbbrev}/current` — Active NHL rosters across all 32 teams (Forwards `C`, `LW`, `RW`, Defensemen `D`, Goaltenders `G`, jersey numbers, birthdates, nationalities, height/weight, shoots/catches, official headshot URLs).
   - `/prospects/{teamAbbrev}` — Team rookies and prospects across NHL organizations.
   - `/score/now` & `/gamecenter/{gameId}/boxscore` — Today's NHL games, live scores, period/clock status, shots on goal, and live in-game player boxscores.
   - `/player/{playerId}/landing` — Career totals, historical year-by-year NHL statistics, draft pedigree, and last-5-games performance logs.
2. **Official NHL Stats REST API (`https://api.nhle.com/stats/rest/en`)**
   - `/skater/summary` — Bulk current-season and prior-season goals, assists, points, `+/-`, PIM, power-play points, shorthanded points, shots, TOI, and faceoff %.
   - `/skater/realtime` — Bulk current-season and prior-season **hits** and **blocked shots**.
   - `/goalie/summary` — Bulk current-season and prior-season wins, losses, OTL, saves, shots against, save percentage, GAA, and shutouts.
3. **Public ESPN NHL Endpoints (`https://site.api.espn.com/apis/site/v2/sports/hockey/nhl`)**
   - `/injuries` — Active NHL injury statuses (`Healthy`, `Day-to-Day`, `Out`, `Injured Reserve`), injury types, notes, and estimated return dates.
   - `/news` — Real NHL news headlines and article links.

---

## 4. Installing the APK on Samsung Galaxy S22 Ultra (Android 16 / One UI 8)

### APK Output Paths
- Release Output Directory: `release/NHLFantasyGM-v1.1.0-release.apk`
- Gradle Build Output: `app/build/outputs/apk/release/app-release.apk`

### Option A — Direct Phone Install (USB Copy / GitHub Release)
1. Download `NHLFantasyGM-v1.1.0-release.apk` (signed with the same release certificate as `v1.0.0`, so it upgrades seamlessly in-place while preserving your data).
2. Open Samsung **My Files** → **Downloads** and tap `NHLFantasyGM-v1.1.0-release.apk`.
3. If prompted by One UI 8 / Android 16 security:
   - Tap **Settings** → enable **Allow from this source** for *My Files* (if **Auto Blocker** is enabled under *Settings → Security and privacy → Auto Blocker*, temporarily toggle Auto Blocker off during sideloading, then re-enable it afterward).
4. Tap **Install** (or **Update**), then tap **Open**.

### Option B — Install via ADB over USB
```powershell
"$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "release\NHLFantasyGM-v1.1.0-release.apk"
```

---

## 5. Optional Self-Hosted Multiplayer Relay Server (`backend/`)

Multiplayer works **out of the box** without running any server. If you want to host a private LAN or cloud relay server:

```bash
cd backend
npm install
PORT=8787 npm start
```

Then enter `ws://<your-ip>:8787/ws` (or `wss://your-domain.example.com/ws` behind TLS) under **League → Settings → Multiplayer & Custom Relay Server** on each participating device.

---

## 6. Rebuilding the APK & Running Tests

Ensure **JDK 17 or JDK 21** (`JAVA_HOME`) and **Android SDK Platform 36** (`ANDROID_HOME` or `local.properties`) are configured, then run from the project root:

```powershell
# Run all Unit Tests, Multiplayer E2E Tests, Engine Tests, and Robolectric Compose UI Tests
.\gradlew.bat testDebugUnitTest

# Build the Release APK
.\gradlew.bat assembleRelease
```
