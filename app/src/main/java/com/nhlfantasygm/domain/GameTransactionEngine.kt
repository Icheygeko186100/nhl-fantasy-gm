package com.nhlfantasygm.domain

import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.data.local.TransactionEntity
import com.nhlfantasygm.model.CadFormatter
import com.nhlfantasygm.model.EconomyConfig

sealed class TransactionValidationResult {
    data class Valid(
        val requiresHighValueConfirmation: Boolean,
        val confirmationWarning: String = "",
        val projectedCashAfterCad: Long,
        val projectedPayrollAfterCad: Long
    ) : TransactionValidationResult()

    data class Invalid(val reason: String) : TransactionValidationResult()
}

data class TeamFinancialSummary(
    val cashBalanceCad: Long,
    val startingBudgetCad: Long,
    val totalRosterMarketValueCad: Long,
    val totalRosterSellValueCad: Long,
    val totalAcquisitionCostCad: Long,
    val unrealizedProfitLossCad: Long,
    val realizedProfitLossCad: Long,
    val totalSpentOnPurchasesCad: Long,
    val totalEarnedFromSalesCad: Long,
    val currentPayrollCad: Long,
    val salaryCapCad: Long,
    val remainingCapSpaceCad: Long,
    val netFranchiseWorthCad: Long,
    val budgetWarnings: List<String>
)

object GameTransactionEngine {

    fun validatePlayerPurchase(
        buyerTeam: FantasyTeamEntity,
        currentRoster: List<PlayerEntity>,
        targetPlayer: PlayerEntity,
        economyConfig: EconomyConfig
    ): TransactionValidationResult {
        if (targetPlayer.ownerFantasyTeamId == buyerTeam.teamId) {
            return TransactionValidationResult.Invalid("${targetPlayer.fullName} is already on your roster.")
        }
        if (targetPlayer.ownerFantasyTeamId != null) {
            return TransactionValidationResult.Invalid(
                "${targetPlayer.fullName} is currently owned by another league team. Propose a trade instead."
            )
        }
        if (currentRoster.size >= economyConfig.maxRosterSize) {
            return TransactionValidationResult.Invalid(
                "Roster full (${currentRoster.size}/${economyConfig.maxRosterSize}). Sell or trade a player first."
            )
        }
        if (targetPlayer.purchasePriceCad > buyerTeam.cashBalanceCad) {
            val shortfall = targetPlayer.purchasePriceCad - buyerTeam.cashBalanceCad
            return TransactionValidationResult.Invalid(
                "Insufficient virtual CAD: need ${CadFormatter.formatCompact(targetPlayer.purchasePriceCad)}, short by ${CadFormatter.formatCompact(shortfall)}."
            )
        }

        val currentPayroll = currentRoster.sumOf { it.capHitCad }
        val projectedPayroll = currentPayroll + targetPlayer.capHitCad
        if (economyConfig.enforceSalaryCap && projectedPayroll > economyConfig.salaryCapCad) {
            val capOver = projectedPayroll - economyConfig.salaryCapCad
            return TransactionValidationResult.Invalid(
                "Exceeds NHL Salary Cap (${CadFormatter.formatCompact(economyConfig.salaryCapCad)}) by ${CadFormatter.formatCompact(capOver)}."
            )
        }

        val projectedCash = buyerTeam.cashBalanceCad - targetPlayer.purchasePriceCad
        val usesOverQuarterRemaining = buyerTeam.cashBalanceCad > 0 &&
            targetPlayer.purchasePriceCad.toDouble() / buyerTeam.cashBalanceCad.toDouble() >= 0.25
        val isExpensive = targetPlayer.purchasePriceCad >= economyConfig.expensiveTransactionThresholdCad ||
            usesOverQuarterRemaining

        val warning = if (isExpensive) {
            "High-value signing: ${CadFormatter.formatFull(targetPlayer.purchasePriceCad)} virtual CAD " +
                "(Cap hit: ${CadFormatter.formatCompact(targetPlayer.capHitCad)}). " +
                "Remaining cash after signing: ${CadFormatter.formatCompact(projectedCash)}."
        } else ""

        return TransactionValidationResult.Valid(
            requiresHighValueConfirmation = isExpensive,
            confirmationWarning = warning,
            projectedCashAfterCad = projectedCash,
            projectedPayrollAfterCad = projectedPayroll
        )
    }

    fun validatePlayerSale(
        sellerTeam: FantasyTeamEntity,
        targetPlayer: PlayerEntity
    ): TransactionValidationResult {
        if (targetPlayer.ownerFantasyTeamId != sellerTeam.teamId) {
            return TransactionValidationResult.Invalid("You do not own ${targetPlayer.fullName}.")
        }
        val projectedCash = sellerTeam.cashBalanceCad + targetPlayer.sellPriceCad
        return TransactionValidationResult.Valid(
            requiresHighValueConfirmation = targetPlayer.sellPriceCad >= 5_000_000L,
            confirmationWarning = "Confirm selling ${targetPlayer.fullName} to the Marketplace for ${CadFormatter.formatFull(targetPlayer.sellPriceCad)}?",
            projectedCashAfterCad = projectedCash,
            projectedPayrollAfterCad = 0L
        )
    }

    fun buildFinancialSummary(
        team: FantasyTeamEntity,
        roster: List<PlayerEntity>,
        transactions: List<TransactionEntity>,
        economyConfig: EconomyConfig
    ): TeamFinancialSummary {
        val totalMarketValue = roster.sumOf { it.purchasePriceCad }
        val totalSellValue = roster.sumOf { it.sellPriceCad }
        val totalAcquisitionCost = roster.sumOf {
            if (it.acquisitionPriceCad > 0L) it.acquisitionPriceCad else it.purchasePriceCad
        }
        val unrealizedPnL = totalMarketValue - totalAcquisitionCost
        val realizedPnL = transactions.filter { it.teamId == team.teamId }.sumOf { it.realizedProfitLossCad }

        val totalSpent = transactions
            .filter { it.teamId == team.teamId && (it.type == "BUY" || it.type == "DRAFT") && it.cashDeltaCad < 0 }
            .sumOf { -it.cashDeltaCad }

        val totalEarnedSales = transactions
            .filter { it.teamId == team.teamId && it.type == "SELL" && it.cashDeltaCad > 0 }
            .sumOf { it.cashDeltaCad }

        val payroll = roster.sumOf { it.capHitCad }
        val remainingCap = economyConfig.salaryCapCad - payroll

        val warnings = buildList {
            if (team.cashBalanceCad < (economyConfig.startingBudgetCad * 0.12).toLong()) {
                add("Low Virtual Cash Warning: Only ${CadFormatter.formatCompact(team.cashBalanceCad)} remaining in treasury.")
            }
            if (economyConfig.enforceSalaryCap && remainingCap < 4_000_000L) {
                add("Salary Cap Warning: Only ${CadFormatter.formatCompact(remainingCap)} cap space left.")
            }
            if (roster.size < 20) {
                add("Roster Incomplete: ${roster.size}/20 active lineup players signed.")
            }
        }

        return TeamFinancialSummary(
            cashBalanceCad = team.cashBalanceCad,
            startingBudgetCad = team.startingBudgetCad,
            totalRosterMarketValueCad = totalMarketValue,
            totalRosterSellValueCad = totalSellValue,
            totalAcquisitionCostCad = totalAcquisitionCost,
            unrealizedProfitLossCad = unrealizedPnL,
            realizedProfitLossCad = realizedPnL,
            totalSpentOnPurchasesCad = totalSpent,
            totalEarnedFromSalesCad = totalEarnedSales,
            currentPayrollCad = payroll,
            salaryCapCad = economyConfig.salaryCapCad,
            remainingCapSpaceCad = remainingCap,
            netFranchiseWorthCad = team.cashBalanceCad + totalMarketValue,
            budgetWarnings = warnings
        )
    }
}
