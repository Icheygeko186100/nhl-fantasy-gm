# NHL Fantasy GM — Ultimate Fantasy Hockey Simulator (Android)

**NHL Fantasy GM** is a native Kotlin & Jetpack Compose Android hockey general-manager simulator built for **Android 16 / One UI 8 (API 36)** and optimized for high-density displays such as the **Samsung Galaxy S22 Ultra**.

> **Simulation & No-Gambling Notice:** All financial balances, player prices, contracts, and matchup rewards use **fictional Virtual Canadian Dollars (CAD)** (`$100,000,000 CAD` default starting treasury). There is **zero** real-money gambling, wagering, deposits, withdrawals, or paid player purchases.

---

## 1. Architecture & Technology Stack

- **Language & UI:** 100% Kotlin (`2.0.21`) + Jetpack Compose (Material 3, custom `Canvas` hockey crests and statistical charts, edge-to-edge One UI 8 layout).
- **Target / Compile SDK:** `compileSdk = 36`, `targetSdk = 36` (Android 16), `minSdk = 28`.
- **Architecture:** Clean separation across:
  - `data/remote` — OkHttp + Kotlinx Serialization clients for official NHL and public hockey feeds with concurrency throttling and error recovery.
  - `data/local` — **Room SQLite Database** (`nhl_fantasy_gm.db`) persisting all 32 NHL teams, 1,200+ active players & prospects, franchise ownership, lineups, draft sessions, trades, season schedules, historical banners, and transaction ledgers; **DataStore Preferences** for user settings; **Android Keystore `EncryptedSharedPreferences`** for optional external API credentials.
  - `domain` — Pure, unit-tested game engines:
    - `FantasyScoringEngine` (configurable fantasy weights, daily/weekly/season points, bargains, breakouts, transparent breakdowns)
    - `PlayerValuationEngine` (transparent OVR rating, simulated NHL salary cap hit, purchase/sell prices, deterministic price-change explanations)
    - `GameTransactionEngine` (budget, salary cap, duplicate-ownership, roster-size, and high-value confirmation validation + P&L statements)
    - `DraftEngine` (Snake & Standard drafts, positional requirement feasibility checks, smart CPU GM AI, local Undo rollback, post-draft grades)
    - `LineupAnalyticsEngine` (Forward Lines 1–4, Defense Pairings 1–3, Goalies 1–2, Bench, IR, optimal lineup generator, strict position/injury slot validation)
    - `TradeEvaluationEngine` (player-for-player and player + virtual CAD trades with CPU GMs + incoming CPU trade offers)
    - `SeasonSimulationEngine` (14-week round-robin schedule, real-NHL-stats matchup scoring + optional offline simulation fallback, Semifinal & Championship Final bracket, Season Awards)
  - `worker` — `WorkManager` (`NhlDataSyncWorker`) periodic background statistics synchronization.
  - `ui` — 5-tab bottom navigation (`Home`, `Draft`, `Marketplace`, `My Team`, `League`) with Dark Rink theme by default and optional Ice Arena Light theme.

---

## 2. Real NHL Data Sources Used

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

## 3. Installing the APK on Samsung Galaxy S22 Ultra (Android 16 / One UI 8)

### APK Output Paths
- Release Output Directory: `release/NHLFantasyGM-v1.0.0-release.apk`
- Gradle Build Output: `app/build/outputs/apk/release/app-release.apk`

### Option A — Direct Phone Install (USB Copy / Cloud Drive)
1. Copy `NHLFantasyGM-v1.0.0-release.apk` to your Samsung Galaxy S22 Ultra (via USB file transfer into `Downloads`).
2. Open Samsung **My Files** → **Downloads** and tap `NHLFantasyGM-v1.0.0-release.apk`.
3. If prompted by One UI 8 / Android 16 security:
   - Tap **Settings** → enable **Allow from this source** for *My Files* (if **Auto Blocker** is enabled under *Settings → Security and privacy → Auto Blocker*, temporarily toggle Auto Blocker off during sideloading, then re-enable it afterward).
4. Tap **Install**, then tap **Open**.

### Option B — Install via ADB over USB
1. On your S22 Ultra, open **Settings → About phone → Software information** and tap **Build number** 7 times to enable **Developer options**.
2. Open **Settings → Developer options** and enable **USB debugging** (ensure *Auto Blocker* is off if USB debugging is greyed out).
3. Connect the phone to your PC via USB-C and run:
   ```powershell
   "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "release\NHLFantasyGM-v1.0.0-release.apk"
   ```

---

## 4. Configuring Optional API Keys

- **Out of the Box:** No API key configuration is needed. All NHL rosters, prospects, live stats, scores, injuries, and news sync automatically over HTTPS.
- **Optional External Provider Key:** Under **League → Settings → Data Sources & Secure API Key Storage**, you may optionally enter an external commercial sports-data key. It is encrypted locally on-device using hardware-backed **Android Keystore (`EncryptedSharedPreferences`)** and is never hardcoded into source code or the APK.

---

## 5. Online vs. Offline Capabilities

| Feature | Requires Internet? | Behavior When Offline |
| :--- | :--- | :--- |
| Live NHL Roster, Stats & Injury Sync | **Yes** | Automatically falls back to cached Room database (`CACHED DATA` badge + exact last-synced timestamp). |
| Today's Live NHL Game Scores & Boxscores | **Yes** | Displays last cached scoreboard or clear empty-state notice without fake live badges. |
| NHL News Headlines | **Yes** | Displays cached headlines; tapping external article URLs requires internet. |
| Player Career Charts (`/player/{id}/landing`) | **First view only** | Cached in Room after viewing so charts remain viewable offline. |
| Virtual CAD Economy, Marketplace Buy/Sell, Watchlist | **No** (once synced) | 100% usable offline with full persistence across restarts/reboots. |
| Fantasy Draft Mode (Snake/Linear, CPU GMs, Undo) | **No** (once synced) | 100% playable offline. |
| My Team Branding, Lineup Builder & Trades | **No** (once synced) | 100% playable offline. |
| Fantasy Season Matchups, Playoffs & Awards | **No** | Uses cached real NHL stats, or optional clearly-labeled **Offline Sim Mode** if no stats have been synced yet. |

---

## 6. Rebuilding the APK & Running Tests

Ensure **JDK 17 or JDK 21** (`JAVA_HOME`) and **Android SDK Platform 36** (`ANDROID_HOME` or `local.properties`) are configured, then run from the project root:

```powershell
# Run all Unit Tests, Valuation/Scoring/Draft/Trade Engine Tests, and Robolectric Compose UI Tests
.\gradlew.bat testDebugUnitTest

# Build the Release APK
.\gradlew.bat assembleRelease
```

*(Note: If `keystore.properties` is present locally, `assembleRelease` signs with your release keystore; otherwise `app/build.gradle.kts` cleanly falls back to standard signing so fresh clones still produce an installable APK immediately.)*
