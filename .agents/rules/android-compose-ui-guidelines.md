---
trigger: always_on
description: Mandatory rules for Android Jetpack Compose edge-to-edge window insets, header simplicity, and core feature discoverability.
---

# Android Compose UI & Navigation Standards

## 1. Edge-to-Edge Window Insets Invariant
- Every root top bar, header surface, or custom navigation header MUST include `Modifier.statusBarsPadding()`.
- Bottom action bars, sticky buttons, and full-screen modals (`DialogProperties(decorFitsSystemWindows = false)`) MUST include `Modifier.navigationBarsPadding()` or safe bottom inset calculation to prevent obstruction by Android 3-button system bars (Back, Home, Recents).
- Never draw interactive controls directly beneath the camera hole-punch or status bar icons.

## 2. Header Simplicity (Single Responsibility)
- Top headers must follow a clean 1-row layout: `[<] Back` | `[Emblem/Icon]` | `[Screen Title]` | `[⚙️] Action`.
- Never cram secondary status badges, cap room pills, search inputs, and draft buttons into the top header row.
- Sub-navigation must be cleanly delegated to dedicated horizontal tab rows (e.g. `MATCH` | `TEAM` | `PLAYERS` | `LEAGUE`) or contextual hero cards.

## 3. Core Feature Discoverability Invariant
- Major gameplay modes (Multiplayer Draft Room, Room Join, Matchup Simulation) must always have high-visibility entry points within their parent section (e.g., `MultiplayerDraftHeroCard` and `DRAFTS` sub-tab in the League hub).
- UI simplifications or theme refactors must never remove or conceal multiplayer draft creation or lobby access.
