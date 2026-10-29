package com.nhlfantasygm.domain

import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.model.EconomyConfig
import com.nhlfantasygm.model.InjurySeverity
import com.nhlfantasygm.model.Position
import com.nhlfantasygm.model.ValuationComponent
import com.nhlfantasygm.model.ValuationResult
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Transparent, deterministic NHL player valuation model using real current-season and
 * historical prior-season NHL statistics, position, age curve, rookie/prospect contract
 * status, and injury availability.
 */
object PlayerValuationEngine {

    fun valuatePlayer(
        player: PlayerEntity,
        economyConfig: EconomyConfig = EconomyConfig(),
        previousSavedPriceCad: Long? = null
    ): ValuationResult {
        return if (player.position == Position.GOALIE) {
            valuateGoalie(player, economyConfig, previousSavedPriceCad)
        } else {
            valuateSkater(player, economyConfig, previousSavedPriceCad)
        }
    }

    private fun valuateSkater(
        p: PlayerEntity,
        economy: EconomyConfig,
        previousSavedPriceCad: Long?
    ): ValuationResult {
        val curGp = p.gamesPlayed
        val prevGp = p.prevGamesPlayed

        val curPpg = if (curGp > 0) p.points.toDouble() / curGp else 0.0
        val prevPpg = if (prevGp > 0) p.prevPoints.toDouble() / prevGp else if (p.isProspect) 0.18 else 0.32

        val curGpg = if (curGp > 0) p.goals.toDouble() / curGp else 0.0
        val prevGpg = if (prevGp > 0) p.prevGoals.toDouble() / prevGp else 0.12

        val curShotsPg = if (curGp > 0) p.shots.toDouble() / curGp else 0.0
        val prevShotsPg = if (prevGp > 0) p.prevShots.toDouble() / prevGp else 1.4

        val curPhysPg = if (curGp > 0) (p.hits + p.blockedShots * 1.2) / curGp else 1.5

        // Sample-size stabilized blend between current 2026-27 pace and 2025-26 baseline
        val currentWeight = when {
            curGp >= 25 -> 0.80
            curGp >= 10 -> 0.62
            curGp >= 4 -> 0.45
            curGp >= 1 -> 0.25
            else -> 0.0
        }
        val blendedPpg = if (prevGp > 0) {
            curPpg * currentWeight + prevPpg * (1.0 - currentWeight)
        } else if (curGp > 0) {
            curPpg * 0.85 + 0.10
        } else {
            if (p.isProspect) 0.15 else 0.28
        }

        val blendedGpg = if (prevGp > 0) {
            curGpg * currentWeight + prevGpg * (1.0 - currentWeight)
        } else curGpg

        val blendedShotsPg = if (prevGp > 0) {
            curShotsPg * currentWeight + prevShotsPg * (1.0 - currentWeight)
        } else curShotsPg

        val isDefense = p.position == Position.DEFENSE
        // Defensemen score fewer raw points per game than forwards for equivalent NHL impact
        val offensiveRatingScore = if (isDefense) {
            blendedPpg * 34.0 + blendedGpg * 14.0 + blendedShotsPg * 2.2 + curPhysPg * 1.8
        } else {
            blendedPpg * 24.5 + blendedGpg * 11.5 + blendedShotsPg * 1.9 + curPhysPg * 1.0
        }

        val baseFloor = when {
            p.isProspect && curGp == 0 && prevGp == 0 -> 63.0
            curGp == 0 && prevGp == 0 -> 68.0
            else -> 67.0
        }

        val rawOvr = (baseFloor + offensiveRatingScore).roundToInt()
        val ovr = if (p.isProspect && curGp < 5 && prevGp < 10) {
            rawOvr.coerceIn(60, 77)
        } else {
            rawOvr.coerceIn(65, 98)
        }

        // Base statistical value in Virtual CAD
        val baseValueCad = when {
            ovr >= 95 -> 13_500_000L + (ovr - 95) * 950_000L
            ovr >= 90 -> 9_800_000L + (ovr - 90) * 740_000L
            ovr >= 85 -> 6_500_000L + (ovr - 85) * 660_000L
            ovr >= 80 -> 3_900_000L + (ovr - 80) * 520_000L
            ovr >= 75 -> 2_150_000L + (ovr - 75) * 350_000L
            ovr >= 70 -> 1_250_000L + (ovr - 70) * 180_000L
            else -> 800_000L + max(0, ovr - 60) * 45_000L
        }

        val ageMultiplier = when {
            p.age <= 21 -> 1.12 // High dynasty/youth upside
            p.age in 22..26 -> 1.08 // Entering prime
            p.age in 27..30 -> 1.00 // Prime
            p.age in 31..33 -> 0.93 // Veteran curve
            p.age in 34..36 -> 0.85
            else -> 0.78
        }
        val ageImpactCad = ((ageMultiplier - 1.0) * baseValueCad).roundToLong()

        val posMultiplier = when (p.position) {
            Position.CENTER -> 1.04
            Position.DEFENSE -> 1.03
            Position.LEFT_WING, Position.RIGHT_WING -> 1.00
            Position.GOALIE -> 1.02
        }
        val posImpactCad = ((posMultiplier - 1.0) * baseValueCad).roundToLong()

        val formDeltaRatio = if (curGp >= 4 && prevGp >= 10) {
            ((curPpg - prevPpg) / max(0.35, prevPpg)).coerceIn(-0.18, 0.22)
        } else if (curGp >= 4 && curPpg >= 0.85) {
            0.10
        } else {
            0.0
        }
        val formImpactCad = (baseValueCad * formDeltaRatio * 0.55).roundToLong()

        val injuryDiscountRatio = when (p.injurySeverity) {
            InjurySeverity.HEALTHY -> 0.0
            InjurySeverity.DAY_TO_DAY -> -0.04
            InjurySeverity.OUT -> -0.09
            InjurySeverity.INJURED_RESERVE -> -0.14
        }
        val injuryImpactCad = (baseValueCad * injuryDiscountRatio).roundToLong()

        val rawPurchase = baseValueCad + ageImpactCad + posImpactCad + formImpactCad + injuryImpactCad
        val purchasePriceCad = roundToNearest25k(rawPurchase.coerceIn(750_000L, 18_500_000L))
        val sellPriceCad = roundToNearest25k(
            (purchasePriceCad * (100 - economy.marketplaceSellFeePercent) / 100.0).roundToLong()
                .coerceAtLeast(650_000L)
        )

        // Simulated NHL Cap Hit (Entry-level rookies/prospects capped realistically at ~$925K-$950K)
        val simulatedCapHitCad = when {
            p.isProspect || (p.isRookie && p.age <= 22) -> 925_000L
            ovr >= 94 -> roundToNearest25k((11_200_000L + (ovr - 94) * 650_000L).coerceAtMost(14_000_000L))
            ovr >= 88 -> roundToNearest25k(7_400_000L + (ovr - 88) * 620_000L)
            ovr >= 82 -> roundToNearest25k(4_200_000L + (ovr - 82) * 520_000L)
            ovr >= 76 -> roundToNearest25k(2_000_000L + (ovr - 76) * 350_000L)
            else -> roundToNearest25k(950_000L + max(0, ovr - 68) * 125_000L)
        }

        val contractYears = when {
            p.isProspect || p.isRookie -> 3
            p.age >= 35 -> 1
            ovr >= 88 && p.age <= 29 -> 6
            ovr >= 82 -> 4
            else -> 2
        }

        // Baseline price before current-season form & injury shifts (for transparent price delta)
        val baselinePreFormPrice = roundToNearest25k((baseValueCad + ageImpactCad + posImpactCad).coerceIn(750_000L, 18_500_000L))
        val referencePrice = if (previousSavedPriceCad != null && previousSavedPriceCad > 0L && previousSavedPriceCad != purchasePriceCad) {
            previousSavedPriceCad
        } else {
            baselinePreFormPrice
        }
        val priceDeltaCad = purchasePriceCad - referencePrice

        val explanation = buildExplanation(
            p = p,
            curGp = curGp,
            curPpg = curPpg,
            prevPpg = prevPpg,
            formImpactCad = formImpactCad,
            injuryImpactCad = injuryImpactCad
        )

        val components = buildList {
            add(
                ValuationComponent(
                    "Statistical Production (${ovr} OVR)",
                    if (curGp > 0) "${p.goals}G, ${p.assists}A, ${p.points}PTS in ${curGp} GP"
                    else if (prevGp > 0) "Prior season: ${p.prevPoints} PTS in ${prevGp} GP"
                    else "Prospect / Depth baseline projection",
                    baseValueCad
                )
            )
            if (ageImpactCad != 0L) {
                add(ValuationComponent("Age Curve (Age ${p.age})", if (ageImpactCad > 0) "Youth / Prime upside premium" else "Veteran age adjustment", ageImpactCad))
            }
            if (posImpactCad != 0L) {
                add(ValuationComponent("Positional Scarcity (${p.position.uiShort})", "${p.position.displayName} market demand", posImpactCad))
            }
            if (formImpactCad != 0L) {
                add(ValuationComponent("Current Season Form", if (formImpactCad > 0) "Outperforming historical PPG pace" else "Below historical PPG pace", formImpactCad))
            }
            if (injuryImpactCad != 0L) {
                add(ValuationComponent("Availability (${p.injuryStatus})", p.injuryType.ifBlank { "Active injury discount" }, injuryImpactCad))
            }
        }

        return ValuationResult(
            overallRating = ovr,
            purchasePriceCad = purchasePriceCad,
            sellPriceCad = sellPriceCad,
            simulatedCapHitCad = simulatedCapHitCad,
            contractYears = contractYears,
            priceDeltaCad = priceDeltaCad,
            priceChangeExplanation = explanation,
            components = components
        )
    }

    private fun valuateGoalie(
        p: PlayerEntity,
        economy: EconomyConfig,
        previousSavedPriceCad: Long?
    ): ValuationResult {
        val curGp = p.gamesPlayed
        val prevGp = p.prevGamesPlayed

        val curSvPct = if (curGp > 0 && p.savePct > 0.5) p.savePct else 0.903
        val prevSvPct = if (prevGp > 0 && p.prevSavePct > 0.5) p.prevSavePct else 0.903
        val weight = when {
            curGp >= 15 -> 0.75
            curGp >= 6 -> 0.55
            curGp >= 2 -> 0.35
            else -> 0.0
        }
        val blendedSvPct = if (prevGp > 0) curSvPct * weight + prevSvPct * (1.0 - weight) else curSvPct
        val winRate = when {
            curGp >= 3 -> p.wins.toDouble() / curGp
            prevGp >= 5 -> p.prevWins.toDouble() / prevGp
            else -> 0.45
        }
        val workloadBonus = min(8.0, (curGp * 0.45) + (prevGp * 0.12))
        val svScore = ((blendedSvPct - 0.885) * 480.0).coerceIn(-6.0, 22.0)
        val winScore = (winRate - 0.40) * 18.0

        val rawOvr = (69.0 + svScore + winScore + workloadBonus).roundToInt()
        val ovr = if (p.isProspect && curGp < 3 && prevGp < 5) {
            rawOvr.coerceIn(60, 76)
        } else {
            rawOvr.coerceIn(66, 96)
        }

        val baseValueCad = when {
            ovr >= 92 -> 10_200_000L + (ovr - 92) * 850_000L
            ovr >= 87 -> 6_900_000L + (ovr - 87) * 660_000L
            ovr >= 82 -> 4_200_000L + (ovr - 82) * 540_000L
            ovr >= 76 -> 2_100_000L + (ovr - 76) * 350_000L
            else -> 950_000L + max(0, ovr - 66) * 115_000L
        }

        val ageMultiplier = when {
            p.age <= 24 -> 1.08
            p.age in 25..31 -> 1.02
            p.age in 32..35 -> 0.94
            else -> 0.84
        }
        val ageImpactCad = ((ageMultiplier - 1.0) * baseValueCad).roundToLong()

        val formRatio = if (curGp >= 3) {
            ((curSvPct - 0.905) * 4.5).coerceIn(-0.15, 0.18)
        } else 0.0
        val formImpactCad = (baseValueCad * formRatio).roundToLong()

        val injuryDiscountRatio = when (p.injurySeverity) {
            InjurySeverity.HEALTHY -> 0.0
            InjurySeverity.DAY_TO_DAY -> -0.04
            InjurySeverity.OUT -> -0.10
            InjurySeverity.INJURED_RESERVE -> -0.15
        }
        val injuryImpactCad = (baseValueCad * injuryDiscountRatio).roundToLong()

        val purchasePriceCad = roundToNearest25k(
            (baseValueCad + ageImpactCad + formImpactCad + injuryImpactCad).coerceIn(800_000L, 14_500_000L)
        )
        val sellPriceCad = roundToNearest25k(
            (purchasePriceCad * (100 - economy.marketplaceSellFeePercent) / 100.0).roundToLong()
                .coerceAtLeast(700_000L)
        )

        val simulatedCapHitCad = when {
            p.isProspect || (p.isRookie && p.age <= 23) -> 925_000L
            ovr >= 91 -> roundToNearest25k(8_500_000L + (ovr - 91) * 550_000L)
            ovr >= 85 -> roundToNearest25k(5_200_000L + (ovr - 85) * 520_000L)
            ovr >= 79 -> roundToNearest25k(2_500_000L + (ovr - 79) * 420_000L)
            else -> roundToNearest25k(1_050_000L + max(0, ovr - 70) * 140_000L)
        }

        val baselinePreFormPrice = roundToNearest25k((baseValueCad + ageImpactCad).coerceIn(800_000L, 14_500_000L))
        val referencePrice = if (previousSavedPriceCad != null && previousSavedPriceCad > 0L && previousSavedPriceCad != purchasePriceCad) {
            previousSavedPriceCad
        } else {
            baselinePreFormPrice
        }
        val priceDeltaCad = purchasePriceCad - referencePrice

        val explanation = when {
            injuryImpactCad < 0L -> "Adjusted for ${p.injuryStatus} availability (${p.injuryType.ifBlank { "medical status" }})"
            curGp >= 3 && curSvPct >= 0.915 -> "Value boosted by ${(curSvPct * 1000).roundToInt() / 10.0}% SV% and ${p.wins}W in ${curGp} GP"
            curGp >= 3 && curSvPct < 0.892 -> "Value softened by early ${(curSvPct * 1000).roundToInt() / 10.0}% SV% across ${curGp} GP"
            p.isProspect -> "Simulated prospect valuation (ELC cap structure)"
            else -> "Simulated goalie valuation from NHL SV%, GAA, and workload"
        }

        val components = buildList {
            add(
                ValuationComponent(
                    "Goaltending Rating (${ovr} OVR)",
                    if (curGp > 0) "${p.wins}W, ${String.format("%.3f", p.savePct)} SV%, ${String.format("%.2f", p.goalsAgainstAverage)} GAA"
                    else "Historical / Prospect goalie baseline",
                    baseValueCad
                )
            )
            if (ageImpactCad != 0L) {
                add(ValuationComponent("Age Curve (Age ${p.age})", "Goalie career window factor", ageImpactCad))
            }
            if (formImpactCad != 0L) {
                add(ValuationComponent("Current Save % Form", if (formImpactCad > 0) "Above .905 league SV% benchmark" else "Below .905 league SV% benchmark", formImpactCad))
            }
            if (injuryImpactCad != 0L) {
                add(ValuationComponent("Availability (${p.injuryStatus})", p.injuryType.ifBlank { "Injury adjustment" }, injuryImpactCad))
            }
        }

        return ValuationResult(
            overallRating = ovr,
            purchasePriceCad = purchasePriceCad,
            sellPriceCad = sellPriceCad,
            simulatedCapHitCad = simulatedCapHitCad,
            contractYears = if (p.isProspect) 3 else if (p.age >= 34) 1 else 4,
            priceDeltaCad = priceDeltaCad,
            priceChangeExplanation = explanation,
            components = components
        )
    }

    private fun buildExplanation(
        p: PlayerEntity,
        curGp: Int,
        curPpg: Double,
        prevPpg: Double,
        formImpactCad: Long,
        injuryImpactCad: Long
    ): String {
        return when {
            injuryImpactCad < 0L ->
                "Discounted due to ${p.injuryStatus} status${if (p.injuryType.isNotBlank()) " (${p.injuryType})" else ""}"
            formImpactCad >= 200_000L && curGp > 0 ->
                "Trending up: ${String.format("%.2f", curPpg)} PTS/GP in ${curGp} GP vs ${String.format("%.2f", prevPpg)} prior pace"
            formImpactCad <= -200_000L && curGp > 0 ->
                "Adjusted down: ${String.format("%.2f", curPpg)} PTS/GP in ${curGp} GP vs ${String.format("%.2f", prevPpg)} prior pace"
            p.isProspect ->
                "Simulated prospect valuation (Age ${p.age}, entry-level cap hit)"
            curGp > 0 ->
                "Valued from ${p.points} PTS in ${curGp} GP (${p.position.uiShort}, Age ${p.age})"
            else ->
                "Valued from historical NHL production & positional curve"
        }
    }

    private fun roundToNearest25k(amount: Long): Long {
        return ((amount + 12_500L) / 25_000L) * 25_000L
    }
}
