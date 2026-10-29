package com.nhlfantasygm.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nhlfantasygm.data.local.UserAppSettings
import com.nhlfantasygm.data.local.SyncStatusEntity
import com.nhlfantasygm.model.*
import com.nhlfantasygm.multiplayer.MultiplayerUiState
import com.nhlfantasygm.multiplayer.RoomCrypto
import com.nhlfantasygm.ui.components.DataFreshnessBanner
import com.nhlfantasygm.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: UserAppSettings,
    mpState: MultiplayerUiState = MultiplayerUiState(),
    savedApiKey: String = "",
    syncStatus: SyncStatusEntity? = null,
    freshnessState: DataFreshnessState = DataFreshnessState.CACHED,
    isSyncing: Boolean = false,
    onRefreshData: () -> Unit = {},
    onSaveTheme: (AppThemeMode) -> Unit,
    onSaveEconomy: (EconomyConfig, Boolean) -> Unit,
    onSaveScoring: (ScoringWeights) -> Unit,
    onToggleOfflineSim: (Boolean) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onSaveCustomRelayUrl: (String) -> Unit,
    onLeaveMultiplayerRoom: () -> Unit
) {
    var budgetM by remember(settings.economyConfig) {
        mutableFloatStateOf(settings.economyConfig.startingBudgetCad / 1_000_000f)
    }
    var capM by remember(settings.economyConfig) {
        mutableFloatStateOf(settings.economyConfig.salaryCapCad / 1_000_000f)
    }
    var enforceCap by remember(settings.economyConfig) {
        mutableStateOf(settings.economyConfig.enforceSalaryCap)
    }
    var recalcTreasuryNow by remember { mutableStateOf(false) }

    var wGoals by remember(settings.scoringWeights) { mutableFloatStateOf(settings.scoringWeights.goals.toFloat()) }
    var wAssists by remember(settings.scoringWeights) { mutableFloatStateOf(settings.scoringWeights.assists.toFloat()) }
    var wPpp by remember(settings.scoringWeights) { mutableFloatStateOf(settings.scoringWeights.powerPlayPoints.toFloat()) }
    var wShots by remember(settings.scoringWeights) { mutableFloatStateOf(settings.scoringWeights.shotsOnGoal.toFloat()) }
    var wHits by remember(settings.scoringWeights) { mutableFloatStateOf(settings.scoringWeights.hits.toFloat()) }
    var wBlocks by remember(settings.scoringWeights) { mutableFloatStateOf(settings.scoringWeights.blockedShots.toFloat()) }
    var wGoalieW by remember(settings.scoringWeights) { mutableFloatStateOf(settings.scoringWeights.goalieWins.toFloat()) }
    var wSaves by remember(settings.scoringWeights) { mutableFloatStateOf(settings.scoringWeights.goalieSaves.toFloat()) }
    var wShutouts by remember(settings.scoringWeights) { mutableFloatStateOf(settings.scoringWeights.goalieShutouts.toFloat()) }

    var apiKeyInput by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var customRelayInput by remember(mpState.customRelayUrl) { mutableStateOf(mpState.customRelayUrl) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.padding(8.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column {
                                Text(
                                    text = "Settings & Configuration",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Game rules, economy, scoring, & feeds",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    DataFreshnessBanner(
                        freshness = freshnessState,
                        lastSyncEpochMs = syncStatus?.lastFullSyncEpochMs ?: 0L,
                        playerCount = syncStatus?.syncedPlayerCount ?: 0,
                        errorMessage = syncStatus?.lastErrorMessage.orEmpty(),
                        isSyncing = isSyncing,
                        onRefreshClick = onRefreshData
                    )
                }
            }
        }

        // 2. Appearance & Display
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = IceCyan)
                        Text(
                            text = "Appearance & Theme",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onSaveTheme(mode) },
                                label = { Text(mode.label) },
                                leadingIcon = {
                                    if (settings.themeMode == mode) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Offline Simulation Fallback",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Allow simulated matchups offline when live NHL servers are unreachable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.allowOfflineSeasonSim,
                            onCheckedChange = onToggleOfflineSim
                        )
                    }
                }
            }
        }

        // 3. Virtual CAD Budget & Salary Cap Settings
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = TrophyGold)
                        Text(
                            text = "Virtual CAD Budget & Salary Cap",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Starting Virtual CAD Budget: $${budgetM.roundToInt()},000,000 CAD",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = ProfitEmerald
                    )
                    Slider(
                        value = budgetM,
                        onValueChange = { budgetM = it },
                        valueRange = 50f..250f,
                        steps = 39
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enforce NHL Salary Cap",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Limit active roster cap hit to configured salary limit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enforceCap, onCheckedChange = { enforceCap = it })
                    }

                    Text(
                        text = "Salary Cap Limit: $${String.format(Locale.CANADA, "%.1f", capM)}M CAD",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = IceCyan
                    )
                    Slider(
                        value = capM,
                        onValueChange = { capM = it },
                        valueRange = 75f..130f,
                        steps = 54
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = recalcTreasuryNow,
                            onCheckedChange = { recalcTreasuryNow = it }
                        )
                        Text(
                            text = "Recalculate active franchise cash balance from new starting budget",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = {
                            onSaveEconomy(
                                settings.economyConfig.copy(
                                    startingBudgetCad = budgetM.roundToInt() * 1_000_000L,
                                    salaryCapCad = (capM * 1_000_000L).toLong(),
                                    enforceSalaryCap = enforceCap
                                ),
                                recalcTreasuryNow
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Budget & Cap Rules", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Fantasy Scoring Weights Formula
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.QueryStats, contentDescription = null, tint = IceCyan)
                            Text(
                                "Fantasy Scoring Weights",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = { onSaveScoring(ScoringWeights()) }) {
                            Text("Reset Defaults")
                        }
                    }

                    Text(
                        text = "Points awarded per statistic when evaluating real NHL player performance:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SettingsWeightSlider("Goals", wGoals, 0f..8f) { wGoals = it }
                    SettingsWeightSlider("Assists", wAssists, 0f..6f) { wAssists = it }
                    SettingsWeightSlider("Power-Play Points", wPpp, 0f..4f) { wPpp = it }
                    SettingsWeightSlider("Shots on Goal", wShots, 0f..2f) { wShots = it }
                    SettingsWeightSlider("Hits", wHits, 0f..2f) { wHits = it }
                    SettingsWeightSlider("Blocked Shots", wBlocks, 0f..2f) { wBlocks = it }
                    SettingsWeightSlider("Goalie Wins", wGoalieW, 0f..8f) { wGoalieW = it }
                    SettingsWeightSlider("Goalie Saves", wSaves, 0f..1f) { wSaves = it }
                    SettingsWeightSlider("Goalie Shutouts", wShutouts, 0f..8f) { wShutouts = it }

                    Button(
                        onClick = {
                            fun r1(f: Float) = (f * 10f).roundToInt() / 10.0
                            onSaveScoring(
                                settings.scoringWeights.copy(
                                    goals = r1(wGoals),
                                    assists = r1(wAssists),
                                    powerPlayPoints = r1(wPpp),
                                    shotsOnGoal = r1(wShots),
                                    hits = r1(wHits),
                                    blockedShots = r1(wBlocks),
                                    goalieWins = r1(wGoalieW),
                                    goalieSaves = r1(wSaves),
                                    goalieShutouts = r1(wShutouts)
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Apply Custom Scoring Formula", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Multiplayer & Custom Relay Server
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, IceCyan.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = IceCyan)
                        Text(
                            "Multiplayer & Custom Relay Server",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Multiplayer drafts & leagues connect via TLS WebSockets with end-to-end AES-256-GCM encryption derived from your 6-character Invite Code. You can optionally connect to your own self-hosted relay server (e.g., node backend/relay-server.js).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = customRelayInput,
                        onValueChange = { customRelayInput = it },
                        label = { Text("Optional Custom Relay URL") },
                        placeholder = { Text("wss://your-relay.example.com/ws") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSaveCustomRelayUrl(customRelayInput) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Relay Server")
                        }
                        if (customRelayInput.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    customRelayInput = ""
                                    onSaveCustomRelayUrl("")
                                }
                            ) {
                                Text("Use Default")
                            }
                        }
                    }

                    if (mpState.isMultiplayerActive) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Room: ${RoomCrypto.formatInviteCodeForDisplay(mpState.roomCode)} (${mpState.role.name})",
                                style = MaterialTheme.typography.labelLarge,
                                color = ProfitEmerald,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedButton(onClick = onLeaveMultiplayerRoom) {
                                Text("Leave Room", color = AlertRed)
                            }
                        }
                    }
                }
            }
        }

        // 6. Data Sources & Secure API Key Storage
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Data Sources & Security",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "NHL Fantasy GM automatically pulls official NHL Web/Stats REST feeds (api-web.nhle.com & api.nhle.com) and ESPN injury/headline feeds with zero configuration. An optional commercial provider key can be stored encrypted via Android Keystore below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Optional External Provider Key (Keystore Encrypted)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = { onSaveApiKey(apiKeyInput) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Encrypted Key")
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = IceCyan, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Simulation Policy: All virtual currency ($ CAD), player contracts, and fantasy transactions are fictional game currency with strictly zero real-money gambling or payments.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 7. App & Version Information
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NHL Fantasy GM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = IceCyan
                    )
                    Text(
                        text = "Version 1.1.1 (Multiplayer & Solo GM Simulator)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Built for Android 16 / One UI 8 • 32 NHL Teams • Real-time Stats Engine",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsWeightSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    val clean = (value * 10f).roundToInt() / 10f
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("$clean FP", style = MaterialTheme.typography.labelLarge, color = IceCyan, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
