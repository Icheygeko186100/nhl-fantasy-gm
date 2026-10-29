package com.nhlfantasygm.model

import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@Serializable
data class ScoringWeights(
    val goals: Double = 3.0,
    val assists: Double = 2.0,
    val powerPlayPoints: Double = 1.0,
    val shotsOnGoal: Double = 0.4,
    val hits: Double = 0.3,
    val blockedShots: Double = 0.5,
    val penaltyMinutes: Double = 0.0,
    val plusMinus: Double = 0.0,
    val goalieWins: Double = 4.0,
    val goalieSaves: Double = 0.2,
    val goalieShutouts: Double = 3.0,
    val goalieGoalsAgainst: Double = 0.0
)

@Serializable
data class EconomyConfig(
    val startingBudgetCad: Long = 100_000_000L,
    val salaryCapCad: Long = 95_500_000L,
    val enforceSalaryCap: Boolean = true,
    val expensiveTransactionThresholdCad: Long = 5_000_000L,
    val marketplaceSellFeePercent: Int = 4,
    val maxRosterSize: Int = 23
)

@Serializable
data class DraftConfig(
    val isSnakeDraft: Boolean = true,
    val teamCount: Int = 8,
    val userDraftPosition: Int = 1, // 1-based index
    val requiredCenters: Int = 3,
    val requiredLeftWings: Int = 3,
    val requiredRightWings: Int = 3,
    val requiredDefensemen: Int = 5,
    val requiredGoalies: Int = 2,
    val benchSlots: Int = 3,
    val enforceSalaryCap: Boolean = true,
    val salaryCapCad: Long = 95_500_000L,
    val pickTimerSeconds: Int = 0 // 0 = disabled, else 30, 60, 90, 120
) {
    val totalRounds: Int
        get() = requiredCenters + requiredLeftWings + requiredRightWings +
            requiredDefensemen + requiredGoalies + benchSlots

    val totalPicks: Int
        get() = totalRounds * teamCount
}

enum class AppThemeMode(val label: String) {
    DARK("Dark Rink (Default)"),
    LIGHT("Ice Arena Light"),
    SYSTEM("Follow System")
}

enum class DataFreshnessState(val badgeText: String, val detailPrefix: String) {
    LIVE("LIVE NHL DATA", "Synced live from NHL API"),
    SYNCING("SYNCING...", "Updating live NHL rosters & stats"),
    CACHED("CACHED DATA", "Offline / Cached local snapshot"),
    UNAVAILABLE("DATA UNAVAILABLE", "No connection & no cached data yet")
}

enum class PerformanceTrend(val label: String, val symbol: String) {
    HOT("Hot Streak", "▲▲"),
    RISING("Trending Up", "▲"),
    STEADY("Steady", "●"),
    COOLING("Cooling Off", "▼")
}

enum class TeamEmblem(val id: String, val displayName: String) {
    MAPLE_SHIELD("MAPLE_SHIELD", "Northern Shield"),
    CROSSED_STICKS("CROSSED_STICKS", "Crossed Blades"),
    ARCTIC_WOLF("ARCTIC_WOLF", "Arctic Wolf"),
    ROYAL_CROWN("ROYAL_CROWN", "Royal Crown"),
    ICE_STORM("ICE_STORM", "Ice Lightning"),
    MOUNTAIN_PEAK("MOUNTAIN_PEAK", "Rockies Peak"),
    NORTH_STAR("NORTH_STAR", "Polaris Star"),
    BLAZING_PUCK("BLAZING_PUCK", "Blazing Puck"),
    GRIZZLY_CLAW("GRIZZLY_CLAW", "Grizzly Crest"),
    FALCON_WING("FALCON_WING", "Winter Falcon"),
    TRIDENT_DEEP("TRIDENT_DEEP", "Deep Trident"),
    FORTRESS_CREST("FORTRESS_CREST", "Iron Fortress");

    companion object {
        fun fromId(id: String?): TeamEmblem =
            entries.firstOrNull { it.id == id } ?: MAPLE_SHIELD
    }
}

data class TeamColorPreset(
    val id: String,
    val name: String,
    val primaryHex: Long,
    val secondaryHex: Long
) {
    companion object {
        val presets = listOf(
            TeamColorPreset("ICE_CYAN", "Glacier Cyan & Navy", 0xFF00C2FF, 0xFF0B192C),
            TeamColorPreset("MAPLE_RED", "True North Crimson", 0xFFEF4444, 0xFF1E1B4B),
            TeamColorPreset("ROYAL_BLUE", "Atlantic Royal Blue", 0xFF3B82F6, 0xFF0F172A),
            TeamColorPreset("EMERALD_ICE", "Aurora Emerald", 0xFF10B981, 0xFF062C22),
            TeamColorPreset("GOLD_STEEL", "Championship Gold", 0xFFF59E0B, 0xFF1C1917),
            TeamColorPreset("OIL_ORANGE", "Prairie Copper & Blue", 0xFFF97316, 0xFF172554),
            TeamColorPreset("VIOLET_FROST", "Midnight Violet", 0xFF8B5CF6, 0xFF1E1B4B),
            TeamColorPreset("TEAL_SHARK", "Pacific Teal", 0xFF14B8A6, 0xFF0F172A)
        )
    }
}

data class ScoringBreakdownItem(
    val category: String,
    val statCount: Double,
    val weight: Double,
    val fantasyPoints: Double
)

data class ValuationComponent(
    val factorName: String,
    val impactDescription: String,
    val cadContribution: Long
)

data class ValuationResult(
    val overallRating: Int,
    val purchasePriceCad: Long,
    val sellPriceCad: Long,
    val simulatedCapHitCad: Long,
    val contractYears: Int,
    val priceDeltaCad: Long,
    val priceChangeExplanation: String,
    val components: List<ValuationComponent>
)

object CadFormatter {
    fun formatFull(amountCad: Long, includeCadSuffix: Boolean = true): String {
        val sign = if (amountCad < 0) "-" else ""
        val formatted = NumberFormat.getIntegerInstance(Locale.CANADA).format(abs(amountCad))
        return if (includeCadSuffix) "${sign}$$formatted CAD" else "${sign}$$formatted"
    }

    fun formatCompact(amountCad: Long, showPlusSign: Boolean = false): String {
        val sign = when {
            amountCad < 0 -> "-"
            amountCad > 0 && showPlusSign -> "+"
            else -> ""
        }
        val absVal = abs(amountCad)
        return when {
            absVal >= 1_000_000L -> String.format(Locale.CANADA, "%s$%.2fM CAD", sign, absVal / 1_000_000.0)
            absVal >= 1_000L -> String.format(Locale.CANADA, "%s$%.0fK CAD", sign, absVal / 1_000.0)
            else -> "${sign}$$absVal CAD"
        }
    }

    fun formatShortNoSuffix(amountCad: Long, showPlusSign: Boolean = false): String {
        val sign = when {
            amountCad < 0 -> "-"
            amountCad > 0 && showPlusSign -> "+"
            else -> ""
        }
        val absVal = abs(amountCad)
        return when {
            absVal >= 1_000_000L -> String.format(Locale.CANADA, "%s$%.2fM", sign, absVal / 1_000_000.0)
            absVal >= 1_000L -> String.format(Locale.CANADA, "%s$%.0fK", sign, absVal / 1_000.0)
            else -> "${sign}$$absVal"
        }
    }
}
