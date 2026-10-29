package com.nhlfantasygm.model

import kotlinx.serialization.Serializable

@Serializable
enum class Position(
    val apiCode: String,
    val uiShort: String,
    val displayName: String,
    val isForward: Boolean,
    val isSkater: Boolean
) {
    CENTER("C", "C", "Center", isForward = true, isSkater = true),
    LEFT_WING("L", "LW", "Left Wing", isForward = true, isSkater = true),
    RIGHT_WING("R", "RW", "Right Wing", isForward = true, isSkater = true),
    DEFENSE("D", "D", "Defenseman", isForward = false, isSkater = true),
    GOALIE("G", "G", "Goaltender", isForward = false, isSkater = false);

    companion object {
        fun fromCode(raw: String?): Position {
            return when (raw?.trim()?.uppercase()) {
                "C" -> CENTER
                "L", "LW" -> LEFT_WING
                "R", "RW" -> RIGHT_WING
                "D", "LD", "RD" -> DEFENSE
                "G" -> GOALIE
                else -> CENTER
            }
        }
    }
}

enum class InjurySeverity(val label: String, val isUnavailableToPlay: Boolean) {
    HEALTHY("Healthy", false),
    DAY_TO_DAY("Day-to-Day", false),
    OUT("Out", true),
    INJURED_RESERVE("Injured Reserve", true);

    companion object {
        fun fromStatusText(status: String?): InjurySeverity {
            if (status.isNullOrBlank()) return HEALTHY
            val lower = status.lowercase()
            return when {
                lower.contains("injured reserve") || lower == "ir" || lower.contains("ltir") -> INJURED_RESERVE
                lower.contains("out") || lower.contains("suspended") -> OUT
                lower.contains("day-to-day") || lower.contains("dtd") || lower.contains("questionable") || lower.contains("probable") -> DAY_TO_DAY
                lower.contains("healthy") || lower.contains("active") -> HEALTHY
                else -> DAY_TO_DAY
            }
        }
    }
}
