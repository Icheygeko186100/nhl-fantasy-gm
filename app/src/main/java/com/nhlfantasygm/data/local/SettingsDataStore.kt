package com.nhlfantasygm.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nhlfantasygm.model.AppThemeMode
import com.nhlfantasygm.model.EconomyConfig
import com.nhlfantasygm.model.ScoringWeights
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class UserAppSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val economyConfig: EconomyConfig = EconomyConfig(),
    val scoringWeights: ScoringWeights = ScoringWeights(),
    val allowOfflineSeasonSim: Boolean = true,
    val autoBackgroundSync: Boolean = true,
    val currentSeasonNumber: Int = 1,
    val localGmClientId: String = "",
    val gmDisplayName: String = "GM Captain",
    val controlledTeamId: String = "USER",
    val activeRoomCode: String = "",
    val activeRoomRole: String = "NONE",
    val customMultiplayerRelayUrl: String = ""
)

class SettingsDataStore(
    private val context: Context,
    private val storeName: String = DEFAULT_STORE_NAME
) {
    private val dataStore: DataStore<Preferences> = getOrCreateStore(context.applicationContext, storeName)
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

        // Multiplayer Identity & Active Room State
        val LOCAL_GM_CLIENT_ID = stringPreferencesKey("local_gm_client_id")
        val GM_DISPLAY_NAME = stringPreferencesKey("gm_display_name")
        val CONTROLLED_TEAM_ID = stringPreferencesKey("controlled_team_id")
        val ACTIVE_ROOM_CODE = stringPreferencesKey("active_room_code")
        val ACTIVE_ROOM_ROLE = stringPreferencesKey("active_room_role")
        val CUSTOM_MP_RELAY_URL = stringPreferencesKey("custom_multiplayer_relay_url")
    }

    val settingsFlow: Flow<UserAppSettings> = dataStore.data.map { prefs ->
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
            currentSeasonNumber = prefs[Keys.CURRENT_SEASON_NUM] ?: 1,
            localGmClientId = prefs[Keys.LOCAL_GM_CLIENT_ID].orEmpty(),
            gmDisplayName = prefs[Keys.GM_DISPLAY_NAME]?.ifBlank { "GM Captain" } ?: "GM Captain",
            controlledTeamId = prefs[Keys.CONTROLLED_TEAM_ID]?.ifBlank { "USER" } ?: "USER",
            activeRoomCode = prefs[Keys.ACTIVE_ROOM_CODE].orEmpty(),
            activeRoomRole = prefs[Keys.ACTIVE_ROOM_ROLE]?.ifBlank { "NONE" } ?: "NONE",
            customMultiplayerRelayUrl = prefs[Keys.CUSTOM_MP_RELAY_URL].orEmpty()
        )
    }

    suspend fun getOrCreateLocalClientId(): String {
        val current = settingsFlow.first().localGmClientId
        if (current.isNotBlank()) return current
        var created = ""
        dataStore.edit { prefs ->
            val existing = prefs[Keys.LOCAL_GM_CLIENT_ID]
            if (!existing.isNullOrBlank()) {
                created = existing
            } else {
                created = UUID.randomUUID().toString().replace("-", "").take(16)
                prefs[Keys.LOCAL_GM_CLIENT_ID] = created
            }
        }
        return created
    }

    suspend fun updateGmDisplayName(name: String) {
        val clean = name.trim().ifBlank { "GM Captain" }.take(24)
        dataStore.edit { it[Keys.GM_DISPLAY_NAME] = clean }
    }

    suspend fun updateMultiplayerSessionState(
        roomCode: String,
        role: String,
        controlledTeamId: String
    ) {
        dataStore.edit {
            it[Keys.ACTIVE_ROOM_CODE] = roomCode
            it[Keys.ACTIVE_ROOM_ROLE] = role
            it[Keys.CONTROLLED_TEAM_ID] = controlledTeamId.ifBlank { "USER" }
        }
    }

    suspend fun updateCustomMultiplayerRelayUrl(url: String) {
        dataStore.edit { it[Keys.CUSTOM_MP_RELAY_URL] = url.trim() }
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateEconomyConfig(config: EconomyConfig) {
        dataStore.edit {
            it[Keys.STARTING_BUDGET] = config.startingBudgetCad
            it[Keys.SALARY_CAP] = config.salaryCapCad
            it[Keys.ENFORCE_CAP] = config.enforceSalaryCap
            it[Keys.EXPENSIVE_TX_THRESHOLD] = config.expensiveTransactionThresholdCad
            it[Keys.SELL_FEE_PERCENT] = config.marketplaceSellFeePercent
            it[Keys.MAX_ROSTER_SIZE] = config.maxRosterSize
        }
    }

    suspend fun updateScoringWeights(weights: ScoringWeights) {
        dataStore.edit {
            it[Keys.SCORING_JSON] = json.encodeToString(weights)
        }
    }

    suspend fun setOfflineSeasonSim(enabled: Boolean) {
        dataStore.edit { it[Keys.ALLOW_OFFLINE_SIM] = enabled }
    }

    suspend fun setAutoBackgroundSync(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_BG_SYNC] = enabled }
    }

    suspend fun setSeasonNumber(seasonNumber: Int) {
        dataStore.edit { it[Keys.CURRENT_SEASON_NUM] = seasonNumber.coerceAtLeast(1) }
    }

    suspend fun incrementSeasonNumber(): Int {
        var next = 2
        dataStore.edit {
            val current = it[Keys.CURRENT_SEASON_NUM] ?: 1
            next = current + 1
            it[Keys.CURRENT_SEASON_NUM] = next
        }
        return next
    }

    companion object {
        const val DEFAULT_STORE_NAME = "nhl_fantasy_gm_prefs"
        private val storeRegistry = ConcurrentHashMap<String, DataStore<Preferences>>()

        private fun getOrCreateStore(appContext: Context, name: String): DataStore<Preferences> {
            return storeRegistry.getOrPut(name) {
                PreferenceDataStoreFactory.create(
                    produceFile = { appContext.preferencesDataStoreFile(name) }
                )
            }
        }
    }
}
