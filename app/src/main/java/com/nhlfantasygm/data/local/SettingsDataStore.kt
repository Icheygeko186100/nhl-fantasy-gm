package com.nhlfantasygm.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nhlfantasygm.model.AppThemeMode
import com.nhlfantasygm.model.EconomyConfig
import com.nhlfantasygm.model.ScoringWeights
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "nhl_fantasy_gm_prefs")

data class UserAppSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val economyConfig: EconomyConfig = EconomyConfig(),
    val scoringWeights: ScoringWeights = ScoringWeights(),
    val allowOfflineSeasonSim: Boolean = true,
    val autoBackgroundSync: Boolean = true,
    val currentSeasonNumber: Int = 1
)

class SettingsDataStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val STARTING_BUDGET = longPreferencesKey("starting_budget_cad")
        val SALARY_CAP = longPreferencesKey("salary_cap_cad")
        val ENFORCE_CAP = booleanPreferencesKey("enforce_salary_cap")
        val EXPENSIVE_TX_THRESHOLD = longPreferencesKey("expensive_tx_threshold_cad")
        val SELL_FEE_PERCENT = intPreferencesKey("sell_fee_percent")
        val MAX_ROSTER_SIZE = intPreferencesKey("max_roster_size")
        val SCORING_JSON = stringPreferencesKey("scoring_weights_json")
        val ALLOW_OFFLINE_SIM = booleanPreferencesKey("allow_offline_season_sim")
        val AUTO_BG_SYNC = booleanPreferencesKey("auto_background_sync")
        val CURRENT_SEASON_NUM = intPreferencesKey("current_season_number")
    }

    val settingsFlow: Flow<UserAppSettings> = context.dataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]?.let {
            runCatching { AppThemeMode.valueOf(it) }.getOrNull()
        } ?: AppThemeMode.DARK

        val economy = EconomyConfig(
            startingBudgetCad = prefs[Keys.STARTING_BUDGET] ?: 100_000_000L,
            salaryCapCad = prefs[Keys.SALARY_CAP] ?: 95_500_000L,
            enforceSalaryCap = prefs[Keys.ENFORCE_CAP] ?: true,
            expensiveTransactionThresholdCad = prefs[Keys.EXPENSIVE_TX_THRESHOLD] ?: 5_000_000L,
            marketplaceSellFeePercent = prefs[Keys.SELL_FEE_PERCENT] ?: 4,
            maxRosterSize = prefs[Keys.MAX_ROSTER_SIZE] ?: 23
        )

        val scoring = prefs[Keys.SCORING_JSON]?.let { raw ->
            runCatching { json.decodeFromString<ScoringWeights>(raw) }.getOrNull()
        } ?: ScoringWeights()

        UserAppSettings(
            themeMode = theme,
            economyConfig = economy,
            scoringWeights = scoring,
            allowOfflineSeasonSim = prefs[Keys.ALLOW_OFFLINE_SIM] ?: true,
            autoBackgroundSync = prefs[Keys.AUTO_BG_SYNC] ?: true,
            currentSeasonNumber = prefs[Keys.CURRENT_SEASON_NUM] ?: 1
        )
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateEconomyConfig(config: EconomyConfig) {
        context.dataStore.edit {
            it[Keys.STARTING_BUDGET] = config.startingBudgetCad
            it[Keys.SALARY_CAP] = config.salaryCapCad
            it[Keys.ENFORCE_CAP] = config.enforceSalaryCap
            it[Keys.EXPENSIVE_TX_THRESHOLD] = config.expensiveTransactionThresholdCad
            it[Keys.SELL_FEE_PERCENT] = config.marketplaceSellFeePercent
            it[Keys.MAX_ROSTER_SIZE] = config.maxRosterSize
        }
    }

    suspend fun updateScoringWeights(weights: ScoringWeights) {
        context.dataStore.edit {
            it[Keys.SCORING_JSON] = json.encodeToString(weights)
        }
    }

    suspend fun setOfflineSeasonSim(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_OFFLINE_SIM] = enabled }
    }

    suspend fun setAutoBackgroundSync(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_BG_SYNC] = enabled }
    }

    suspend fun incrementSeasonNumber(): Int {
        var next = 2
        context.dataStore.edit {
            val current = it[Keys.CURRENT_SEASON_NUM] ?: 1
            next = current + 1
            it[Keys.CURRENT_SEASON_NUM] = next
        }
        return next
    }
}
