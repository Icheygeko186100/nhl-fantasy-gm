package com.nhlfantasygm.model

enum class LineupGroup(val title: String) {
    FORWARD_LINE_1("Forward Line 1"),
    FORWARD_LINE_2("Forward Line 2"),
    FORWARD_LINE_3("Forward Line 3"),
    FORWARD_LINE_4("Forward Line 4"),
    DEFENSE_PAIR_1("Defense Pairing 1"),
    DEFENSE_PAIR_2("Defense Pairing 2"),
    DEFENSE_PAIR_3("Defense Pairing 3"),
    GOALTENDING("Goaltending Depth"),
    BENCH("Bench / Healthy Scratches"),
    INJURED_RESERVE("Injured Reserve"),
    UNASSIGNED("Unassigned")
}

enum class LineupSlot(
    val code: String,
    val label: String,
    val group: LineupGroup,
    val isActiveStarter: Boolean
) {
    LW1("LW1", "1st Line LW", LineupGroup.FORWARD_LINE_1, true),
    C1("C1", "1st Line C", LineupGroup.FORWARD_LINE_1, true),
    RW1("RW1", "1st Line RW", LineupGroup.FORWARD_LINE_1, true),

    LW2("LW2", "2nd Line LW", LineupGroup.FORWARD_LINE_2, true),
    C2("C2", "2nd Line C", LineupGroup.FORWARD_LINE_2, true),
    RW2("RW2", "2nd Line RW", LineupGroup.FORWARD_LINE_2, true),

    LW3("LW3", "3rd Line LW", LineupGroup.FORWARD_LINE_3, true),
    C3("C3", "3rd Line C", LineupGroup.FORWARD_LINE_3, true),
    RW3("RW3", "3rd Line RW", LineupGroup.FORWARD_LINE_3, true),

    LW4("LW4", "4th Line LW", LineupGroup.FORWARD_LINE_4, true),
    C4("C4", "4th Line C", LineupGroup.FORWARD_LINE_4, true),
    RW4("RW4", "4th Line RW", LineupGroup.FORWARD_LINE_4, true),

    LD1("LD1", "1st Pair LD", LineupGroup.DEFENSE_PAIR_1, true),
    RD1("RD1", "1st Pair RD", LineupGroup.DEFENSE_PAIR_1, true),

    LD2("LD2", "2nd Pair LD", LineupGroup.DEFENSE_PAIR_2, true),
    RD2("RD2", "2nd Pair RD", LineupGroup.DEFENSE_PAIR_2, true),

    LD3("LD3", "3rd Pair LD", LineupGroup.DEFENSE_PAIR_3, true),
    RD3("RD3", "3rd Pair RD", LineupGroup.DEFENSE_PAIR_3, true),

    G1("G1", "Starting Goalie", LineupGroup.GOALTENDING, true),
    G2("G2", "Backup Goalie", LineupGroup.GOALTENDING, true),

    BN1("BN1", "Bench 1", LineupGroup.BENCH, false),
    BN2("BN2", "Bench 2", LineupGroup.BENCH, false),
    BN3("BN3", "Bench 3", LineupGroup.BENCH, false),

    IR1("IR1", "IR Slot 1", LineupGroup.INJURED_RESERVE, false),
    IR2("IR2", "IR Slot 2", LineupGroup.INJURED_RESERVE, false),

    UNASSIGNED("UNASSIGNED", "Unassigned", LineupGroup.UNASSIGNED, false);

    fun accepts(position: Position, injurySeverity: InjurySeverity = InjurySeverity.HEALTHY): Boolean {
        return when (this) {
            C1, C2, C3, C4 -> position == Position.CENTER
            LW1, LW2, LW3, LW4 -> position == Position.LEFT_WING
            RW1, RW2, RW3, RW4 -> position == Position.RIGHT_WING
            LD1, RD1, LD2, RD2, LD3, RD3 -> position == Position.DEFENSE
            G1, G2 -> position == Position.GOALIE
            BN1, BN2, BN3, UNASSIGNED -> true
            IR1, IR2 -> injurySeverity.isUnavailableToPlay || injurySeverity == InjurySeverity.DAY_TO_DAY
        }
    }

    /** Allows flexible wing/forward slotting if exact wing isn't available only when allowFlexForwards=true */
    fun acceptsFlexibleForward(position: Position): Boolean {
        return when (this) {
            C1, C2, C3, C4, LW1, LW2, LW3, LW4, RW1, RW2, RW3, RW4 -> position.isForward
            else -> accepts(position)
        }
    }

    companion object {
        val activeStarterSlots: List<LineupSlot> = entries.filter { it.isActiveStarter }
        val benchSlots: List<LineupSlot> = listOf(BN1, BN2, BN3)
        val irSlots: List<LineupSlot> = listOf(IR1, IR2)
        val allManageableSlots: List<LineupSlot> = entries.filter { it != UNASSIGNED }

        fun fromCode(code: String?): LineupSlot {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: UNASSIGNED
        }
    }
}
