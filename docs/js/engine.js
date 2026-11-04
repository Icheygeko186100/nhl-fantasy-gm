/**
 * NHL Fantasy GM — Core Domain Engines (iOS / Web Edition)
 * 1:1 port of Android Kotlin FantasyScoringEngine, PlayerValuationEngine,
 * GameTransactionEngine, LineupAnalyticsEngine, DraftEngine, SeasonSimulationEngine,
 * and TradeEvaluationEngine.
 */

export const DEFAULT_SCORING_WEIGHTS = {
  goals: 3.0,
  assists: 2.0,
  powerPlayPoints: 1.0,
  shotsOnGoal: 0.4,
  hits: 0.3,
  blockedShots: 0.5,
  penaltyMinutes: 0.0,
  plusMinus: 0.0,
  goalieWins: 4.0,
  goalieSaves: 0.2,
  goalieShutouts: 3.0,
  goalieGoalsAgainst: 0.0
};

export const DEFAULT_ECONOMY_CONFIG = {
  startingBudgetCad: 100_000_000,
  salaryCapCad: 95_500_000,
  enforceSalaryCap: true,
  expensiveTransactionThresholdCad: 5_000_000,
  marketplaceSellFeePercent: 4,
  maxRosterSize: 23
};

export const DEFAULT_DRAFT_CONFIG = {
  isSnakeDraft: true,
  teamCount: 8,
  userDraftPosition: 1,
  requiredCenters: 3,
  requiredLeftWings: 3,
  requiredRightWings: 3,
  requiredDefensemen: 5,
  requiredGoalies: 2,
  benchSlots: 3,
  enforceSalaryCap: true,
  salaryCapCad: 95_500_000,
  pickTimerSeconds: 0
};

export const LINEUP_GROUPS = [
  { id: 'FORWARD_LINE_1', title: 'Forward Line 1', slots: ['LW1', 'C1', 'RW1'] },
  { id: 'FORWARD_LINE_2', title: 'Forward Line 2', slots: ['LW2', 'C2', 'RW2'] },
  { id: 'FORWARD_LINE_3', title: 'Forward Line 3', slots: ['LW3', 'C3', 'RW3'] },
  { id: 'FORWARD_LINE_4', title: 'Forward Line 4', slots: ['LW4', 'C4', 'RW4'] },
  { id: 'DEFENSE_PAIR_1', title: 'Defense Pairing 1', slots: ['LD1', 'RD1'] },
  { id: 'DEFENSE_PAIR_2', title: 'Defense Pairing 2', slots: ['LD2', 'RD2'] },
  { id: 'DEFENSE_PAIR_3', title: 'Defense Pairing 3', slots: ['LD3', 'RD3'] },
  { id: 'GOALTENDING', title: 'Goaltending Depth', slots: ['G1', 'G2'] },
  { id: 'BENCH', title: 'Bench / Healthy Scratches', slots: ['BN1', 'BN2', 'BN3'] },
  { id: 'INJURED_RESERVE', title: 'Injured Reserve', slots: ['IR1', 'IR2'] }
];

export const LINEUP_SLOTS = {
  LW1: { code: 'LW1', label: '1st Line LW', pos: 'LW', isActiveStarter: true },
  C1:  { code: 'C1',  label: '1st Line C',  pos: 'C',  isActiveStarter: true },
  RW1: { code: 'RW1', label: '1st Line RW', pos: 'RW', isActiveStarter: true },
  LW2: { code: 'LW2', label: '2nd Line LW', pos: 'LW', isActiveStarter: true },
  C2:  { code: 'C2',  label: '2nd Line C',  pos: 'C',  isActiveStarter: true },
  RW2: { code: 'RW2', label: '2nd Line RW', pos: 'RW', isActiveStarter: true },
  LW3: { code: 'LW3', label: '3rd Line LW', pos: 'LW', isActiveStarter: true },
  C3:  { code: 'C3',  label: '3rd Line C',  pos: 'C',  isActiveStarter: true },
  RW3: { code: 'RW3', label: '3rd Line RW', pos: 'RW', isActiveStarter: true },
  LW4: { code: 'LW4', label: '4th Line LW', pos: 'LW', isActiveStarter: true },
  C4:  { code: 'C4',  label: '4th Line C',  pos: 'C',  isActiveStarter: true },
  RW4: { code: 'RW4', label: '4th Line RW', pos: 'RW', isActiveStarter: true },
  LD1: { code: 'LD1', label: '1st Pair LD', pos: 'D',  isActiveStarter: true },
  RD1: { code: 'RD1', label: '1st Pair RD', pos: 'D',  isActiveStarter: true },
  LD2: { code: 'LD2', label: '2nd Pair LD', pos: 'D',  isActiveStarter: true },
  RD2: { code: 'RD2', label: '2nd Pair RD', pos: 'D',  isActiveStarter: true },
  LD3: { code: 'LD3', label: '3rd Pair LD', pos: 'D',  isActiveStarter: true },
  RD3: { code: 'RD3', label: '3rd Pair RD', pos: 'D',  isActiveStarter: true },
  G1:  { code: 'G1',  label: 'Starting Goalie', pos: 'G', isActiveStarter: true },
  G2:  { code: 'G2',  label: 'Backup Goalie',   pos: 'G', isActiveStarter: true },
  BN1: { code: 'BN1', label: 'Bench 1', pos: 'ANY', isActiveStarter: false },
  BN2: { code: 'BN2', label: 'Bench 2', pos: 'ANY', isActiveStarter: false },
  BN3: { code: 'BN3', label: 'Bench 3', pos: 'ANY', isActiveStarter: false },
  IR1: { code: 'IR1', label: 'IR Slot 1', pos: 'IR', isActiveStarter: false },
  IR2: { code: 'IR2', label: 'IR Slot 2', pos: 'IR', isActiveStarter: false },
  UNASSIGNED: { code: 'UNASSIGNED', label: 'Unassigned', pos: 'ANY', isActiveStarter: false }
};

export const TEAM_EMBLEMS = [
  { id: 'MAPLE_SHIELD', name: 'Northern Shield', icon: '🛡️' },
  { id: 'CROSSED_STICKS', name: 'Crossed Blades', icon: '🏒' },
  { id: 'ARCTIC_WOLF', name: 'Arctic Wolf', icon: '🐺' },
  { id: 'ROYAL_CROWN', name: 'Royal Crown', icon: '👑' },
  { id: 'ICE_STORM', name: 'Ice Lightning', icon: '⚡' },
  { id: 'MOUNTAIN_PEAK', name: 'Rockies Peak', icon: '🏔️' },
  { id: 'NORTH_STAR', name: 'Polaris Star', icon: '⭐' },
  { id: 'BLAZING_PUCK', name: 'Blazing Puck', icon: '🔥' },
  { id: 'GRIZZLY_CLAW', name: 'Grizzly Crest', icon: '🐻' },
  { id: 'FALCON_WING', name: 'Winter Falcon', icon: '🦅' },
  { id: 'TRIDENT_DEEP', name: 'Deep Trident', icon: '🔱' },
  { id: 'FORTRESS_CREST', name: 'Iron Fortress', icon: '🏰' }
];

export const TEAM_COLOR_PRESETS = [
  { id: 'ICE_CYAN', name: 'Glacier Cyan & Navy', primaryHex: 0xFF00C2FF, secondaryHex: 0xFF0B192C, css: '#00C2FF' },
  { id: 'MAPLE_RED', name: 'True North Crimson', primaryHex: 0xFFEF4444, secondaryHex: 0xFF1E1B4B, css: '#EF4444' },
  { id: 'ROYAL_BLUE', name: 'Atlantic Royal Blue', primaryHex: 0xFF3B82F6, secondaryHex: 0xFF0F172A, css: '#3B82F6' },
  { id: 'EMERALD_ICE', name: 'Aurora Emerald', primaryHex: 0xFF10B981, secondaryHex: 0xFF062C22, css: '#10B981' },
  { id: 'GOLD_STEEL', name: 'Championship Gold', primaryHex: 0xFFF59E0B, secondaryHex: 0xFF1C1917, css: '#F59E0B' },
  { id: 'OIL_ORANGE', name: 'Prairie Copper & Blue', primaryHex: 0xFFF97316, secondaryHex: 0xFF172554, css: '#F97316' },
  { id: 'VIOLET_FROST', name: 'Midnight Violet', primaryHex: 0xFF8B5CF6, secondaryHex: 0xFF1E1B4B, css: '#8B5CF6' },
  { id: 'TEAL_SHARK', name: 'Pacific Teal', primaryHex: 0xFF14B8A6, secondaryHex: 0xFF0F172A, css: '#14B8A6' }
];

export const CPU_TEAM_BLUEPRINTS = [
  { teamName: 'Montréal Voyageurs', abbreviation: 'MTV', emblemId: 'ROYAL_CROWN', primaryColorHex: 0xFFEF4444, secondaryColorHex: 0xFF1E1B4B },
  { teamName: 'Toronto St. Pats', abbreviation: 'TSP', emblemId: 'NORTH_STAR', primaryColorHex: 0xFF3B82F6, secondaryColorHex: 0xFF0F172A },
  { teamName: 'Edmonton Oil Barons', abbreviation: 'EOB', emblemId: 'BLAZING_PUCK', primaryColorHex: 0xFFF97316, secondaryColorHex: 0xFF172554 },
  { teamName: 'Vancouver Pacifics', abbreviation: 'VCP', emblemId: 'TRIDENT_DEEP', primaryColorHex: 0xFF10B981, secondaryColorHex: 0xFF062C22 },
  { teamName: 'Calgary Rockies', abbreviation: 'CGR', emblemId: 'MOUNTAIN_PEAK', primaryColorHex: 0xFFF59E0B, secondaryColorHex: 0xFF1C1917 },
  { teamName: 'Winnipeg Falcons', abbreviation: 'WPF', emblemId: 'FALCON_WING', primaryColorHex: 0xFF38BDF8, secondaryColorHex: 0xFF0F172A },
  { teamName: 'Ottawa Senators GM', abbreviation: 'OSG', emblemId: 'FORTRESS_CREST', primaryColorHex: 0xFFDC2626, secondaryColorHex: 0xFF18181B }
];

export function hexLongToCss(hexLong) {
  if (!hexLong && hexLong !== 0) return '#00C2FF';
  const rgb = Number(BigInt(hexLong) & 0xFFFFFFn);
  return '#' + rgb.toString(16).padStart(6, '0').toUpperCase();
}

export function getEmblemIcon(emblemId) {
  const found = TEAM_EMBLEMS.find((e) => e.id === emblemId);
  return found ? found.icon : '🛡️';
}

export const CadFormatter = {
  formatFull(amountCad, includeCadSuffix = true) {
    const sign = amountCad < 0 ? '-' : '';
    const formatted = Math.abs(Math.round(amountCad)).toLocaleString('en-CA');
    return includeCadSuffix ? `${sign}$${formatted} CAD` : `${sign}$${formatted}`;
  },
  formatCompact(amountCad, showPlusSign = false) {
    const sign = amountCad < 0 ? '-' : (amountCad > 0 && showPlusSign ? '+' : '');
    const absVal = Math.abs(amountCad);
    if (absVal >= 1_000_000) return `${sign}$${(absVal / 1_000_000).toFixed(2)}M CAD`;
    if (absVal >= 1_000) return `${sign}$${Math.round(absVal / 1_000)}K CAD`;
    return `${sign}$${Math.round(absVal)} CAD`;
  },
  formatShortNoSuffix(amountCad, showPlusSign = false) {
    const sign = amountCad < 0 ? '-' : (amountCad > 0 && showPlusSign ? '+' : '');
    const absVal = Math.abs(amountCad);
    if (absVal >= 1_000_000) return `${sign}$${(absVal / 1_000_000).toFixed(2)}M`;
    if (absVal >= 1_000) return `${sign}$${Math.round(absVal / 1_000)}K`;
    return `${sign}$${Math.round(absVal)}`;
  }
};

function roundToNearest25k(val) {
  return Math.round(val / 25_000) * 25_000;
}

function clamp(val, min, max) {
  return Math.max(min, Math.min(max, val));
}

function round1(val) {
  return Math.round(val * 10) / 10;
}

function round2(val) {
  return Math.round(val * 100) / 100;
}

export const PlayerValuationEngine = {
  valuatePlayer(p, economy = DEFAULT_ECONOMY_CONFIG, previousSavedPriceCad = null) {
    if (p.position === 'G') {
      return this.valuateGoalie(p, economy, previousSavedPriceCad);
    }
    return this.valuateSkater(p, economy, previousSavedPriceCad);
  },

  valuateSkater(p, economy, previousSavedPriceCad) {
    const curGp = p.gamesPlayed || 0;
    const prevGp = p.prevGamesPlayed || 0;

    const curPpg = curGp > 0 ? p.points / curGp : 0.0;
    const prevPpg = prevGp > 0 ? p.prevPoints / prevGp : (p.isProspect ? 0.18 : 0.32);

    const curGpg = curGp > 0 ? p.goals / curGp : 0.0;
    const prevGpg = prevGp > 0 ? p.prevGoals / prevGp : 0.12;

    const curShotsPg = curGp > 0 ? p.shots / curGp : 0.0;
    const prevShotsPg = prevGp > 0 ? p.prevShots / prevGp : 1.4;

    const curPhysPg = curGp > 0 ? (p.hits + p.blockedShots * 1.2) / curGp : 1.5;

    const currentWeight = curGp >= 25 ? 0.80 : curGp >= 10 ? 0.62 : curGp >= 4 ? 0.45 : curGp >= 1 ? 0.25 : 0.0;
    const blendedPpg = prevGp > 0
      ? curPpg * currentWeight + prevPpg * (1.0 - currentWeight)
      : curGp > 0 ? curPpg * 0.85 + 0.10 : (p.isProspect ? 0.15 : 0.28);

    const blendedGpg = prevGp > 0 ? curGpg * currentWeight + prevGpg * (1.0 - currentWeight) : curGpg;
    const blendedShotsPg = prevGp > 0 ? curShotsPg * currentWeight + prevShotsPg * (1.0 - currentWeight) : curShotsPg;

    const isDefense = p.position === 'D';
    const offensiveRatingScore = isDefense
      ? blendedPpg * 34.0 + blendedGpg * 14.0 + blendedShotsPg * 2.2 + curPhysPg * 1.8
      : blendedPpg * 24.5 + blendedGpg * 11.5 + blendedShotsPg * 1.9 + curPhysPg * 1.0;

    const baseFloor = (p.isProspect && curGp === 0 && prevGp === 0) ? 63.0 : (curGp === 0 && prevGp === 0) ? 68.0 : 67.0;
    const rawOvr = Math.round(baseFloor + offensiveRatingScore);
    const ovr = (p.isProspect && curGp < 5 && prevGp < 10) ? clamp(rawOvr, 60, 77) : clamp(rawOvr, 65, 98);

    let baseValueCad;
    if (ovr >= 95) baseValueCad = 13_500_000 + (ovr - 95) * 950_000;
    else if (ovr >= 90) baseValueCad = 9_800_000 + (ovr - 90) * 740_000;
    else if (ovr >= 85) baseValueCad = 6_500_000 + (ovr - 85) * 660_000;
    else if (ovr >= 80) baseValueCad = 3_900_000 + (ovr - 80) * 520_000;
    else if (ovr >= 75) baseValueCad = 2_150_000 + (ovr - 75) * 350_000;
    else if (ovr >= 70) baseValueCad = 1_250_000 + (ovr - 70) * 180_000;
    else baseValueCad = 800_000 + Math.max(0, ovr - 60) * 45_000;

    const ageMultiplier = p.age <= 21 ? 1.12 : p.age <= 26 ? 1.08 : p.age <= 30 ? 1.00 : p.age <= 33 ? 0.93 : p.age <= 36 ? 0.85 : 0.78;
    const ageImpactCad = Math.round((ageMultiplier - 1.0) * baseValueCad);

    const posMultiplier = p.position === 'C' ? 1.04 : p.position === 'D' ? 1.03 : 1.00;
    const posImpactCad = Math.round((posMultiplier - 1.0) * baseValueCad);

    let formDeltaRatio = 0.0;
    if (curGp >= 4 && prevGp >= 10) {
      formDeltaRatio = clamp((curPpg - prevPpg) / Math.max(0.35, prevPpg), -0.18, 0.22);
    } else if (curGp >= 4 && curPpg >= 0.85) {
      formDeltaRatio = 0.10;
    }
    const formImpactCad = Math.round(baseValueCad * formDeltaRatio * 0.55);

    const injuryDiscountRatio = p.injurySeverity === 'INJURED_RESERVE' ? -0.14
      : p.injurySeverity === 'OUT' ? -0.09
      : p.injurySeverity === 'DAY_TO_DAY' ? -0.04 : 0.0;
    const injuryImpactCad = Math.round(baseValueCad * injuryDiscountRatio);

    const rawPurchase = baseValueCad + ageImpactCad + posImpactCad + formImpactCad + injuryImpactCad;
    const purchasePriceCad = roundToNearest25k(clamp(rawPurchase, 750_000, 18_500_000));
    const sellPriceCad = roundToNearest25k(Math.max(650_000, Math.round(purchasePriceCad * (100 - economy.marketplaceSellFeePercent) / 100.0)));

    let simulatedCapHitCad;
    if (p.isProspect || (p.isRookie && p.age <= 22)) simulatedCapHitCad = 925_000;
    else if (ovr >= 94) simulatedCapHitCad = roundToNearest25k(Math.min(14_000_000, 11_200_000 + (ovr - 94) * 650_000));
    else if (ovr >= 88) simulatedCapHitCad = roundToNearest25k(7_400_000 + (ovr - 88) * 620_000);
    else if (ovr >= 82) simulatedCapHitCad = roundToNearest25k(4_200_000 + (ovr - 82) * 520_000);
    else if (ovr >= 76) simulatedCapHitCad = roundToNearest25k(2_000_000 + (ovr - 76) * 350_000);
    else simulatedCapHitCad = roundToNearest25k(950_000 + Math.max(0, ovr - 68) * 125_000);

    const contractYears = (p.isProspect || p.isRookie) ? 3 : p.age >= 35 ? 1 : (ovr >= 88 && p.age <= 29) ? 6 : ovr >= 82 ? 4 : 2;
    const baselinePreFormPrice = roundToNearest25k(clamp(baseValueCad + ageImpactCad + posImpactCad, 750_000, 18_500_000));
    const referencePrice = (previousSavedPriceCad && previousSavedPriceCad > 0 && previousSavedPriceCad !== purchasePriceCad)
      ? previousSavedPriceCad : baselinePreFormPrice;
    const priceDeltaCad = purchasePriceCad - referencePrice;

    let explanation = 'Valuation anchored to multi-season NHL scoring & two-way metrics';
    if (injuryImpactCad < 0) {
      explanation = `Discounted due to ${p.injurySeverity.replace('_', ' ')} status (${p.injuryNote || 'Injury report'})`;
    } else if (formImpactCad > 100_000) {
      explanation = `Surging form: ${round2(curPpg)} PTS/GP in ${curGp} GP vs ${round2(prevPpg)} prior pace`;
    } else if (formImpactCad < -100_000) {
      explanation = `Cold start adjustment (${round2(curPpg)} PTS/GP across ${curGp} GP)`;
    }

    const components = [
      {
        factorName: `Statistical Production (${ovr} OVR)`,
        impactDescription: curGp > 0 ? `${p.goals}G, ${p.assists}A, ${p.points}PTS in ${curGp} GP` : prevGp > 0 ? `Prior season: ${p.prevPoints} PTS in ${prevGp} GP` : 'Depth baseline projection',
        cadContribution: baseValueCad
      }
    ];
    if (ageImpactCad !== 0) {
      components.push({
        factorName: `Age Curve (Age ${p.age})`,
        impactDescription: ageImpactCad > 0 ? 'Youth / Prime upside premium' : 'Veteran age adjustment',
        cadContribution: ageImpactCad
      });
    }
    if (posImpactCad !== 0) {
      components.push({
        factorName: `Positional Scarcity (${p.position})`,
        impactDescription: `${p.position} market demand`,
        cadContribution: posImpactCad
      });
    }
    if (formImpactCad !== 0) {
      components.push({
        factorName: 'Current Season Form',
        impactDescription: formImpactCad > 0 ? 'Outperforming prior-season scoring rate' : 'Below prior-season scoring rate',
        cadContribution: formImpactCad
      });
    }
    if (injuryImpactCad !== 0) {
      components.push({
        factorName: 'Availability / Injury Report',
        impactDescription: p.injuryNote || p.injurySeverity,
        cadContribution: injuryImpactCad
      });
    }

    return {
      overallRating: ovr,
      purchasePriceCad,
      sellPriceCad,
      simulatedCapHitCad,
      contractYears,
      priceDeltaCad,
      priceChangeExplanation: explanation,
      components
    };
  },

  valuateGoalie(p, economy, previousSavedPriceCad) {
    const curGp = p.gamesPlayed || 0;
    const prevGp = p.prevGamesPlayed || 0;

    const curSvPct = curGp > 0 && p.savePct > 0.5 ? p.savePct : 0.902;
    const prevSvPct = prevGp > 0 && p.prevSavePct > 0.5 ? p.prevSavePct : 0.903;

    const weight = curGp >= 15 ? 0.75 : curGp >= 6 ? 0.55 : curGp >= 2 ? 0.35 : 0.0;
    const blendedSv = prevGp > 0 ? curSvPct * weight + prevSvPct * (1.0 - weight) : curSvPct;
    const winRatio = curGp > 0 ? p.wins / curGp : (prevGp > 0 ? p.prevWins / prevGp : 0.45);
    const workloadScore = Math.min(10.0, (curGp * 2.2 + prevGp * 0.35) * 0.22);

    const svScore = clamp((blendedSv - 0.885) * 620.0, 0.0, 22.0);
    const winScore = clamp(winRatio * 10.5, 0.0, 8.5);
    const rawOvr = Math.round(67.0 + svScore + winScore + workloadScore * 0.45);
    const ovr = clamp(rawOvr, 66, 97);

    let baseValueCad;
    if (ovr >= 94) baseValueCad = 12_800_000 + (ovr - 94) * 850_000;
    else if (ovr >= 89) baseValueCad = 8_600_000 + (ovr - 89) * 720_000;
    else if (ovr >= 84) baseValueCad = 5_400_000 + (ovr - 84) * 580_000;
    else if (ovr >= 78) baseValueCad = 2_800_000 + (ovr - 78) * 410_000;
    else baseValueCad = 1_100_000 + Math.max(0, ovr - 68) * 160_000;

    const ageMultiplier = p.age <= 25 ? 1.08 : p.age <= 31 ? 1.02 : p.age <= 34 ? 0.93 : 0.84;
    const ageImpactCad = Math.round((ageMultiplier - 1.0) * baseValueCad);

    const formDelta = curGp >= 3 ? clamp((curSvPct - prevSvPct) * 6.5, -0.15, 0.18) : 0.0;
    const formImpactCad = Math.round(baseValueCad * formDelta);

    const injuryDiscountRatio = p.injurySeverity === 'INJURED_RESERVE' ? -0.14
      : p.injurySeverity === 'OUT' ? -0.09
      : p.injurySeverity === 'DAY_TO_DAY' ? -0.04 : 0.0;
    const injuryImpactCad = Math.round(baseValueCad * injuryDiscountRatio);

    const purchasePriceCad = roundToNearest25k(clamp(baseValueCad + ageImpactCad + formImpactCad + injuryImpactCad, 750_000, 16_500_000));
    const sellPriceCad = roundToNearest25k(Math.max(650_000, Math.round(purchasePriceCad * (100 - economy.marketplaceSellFeePercent) / 100.0)));
    const simulatedCapHitCad = (p.isProspect || (p.isRookie && p.age <= 23))
      ? 925_000
      : roundToNearest25k(clamp(Math.round(purchasePriceCad * 0.72), 950_000, 11_500_000));
    const contractYears = p.age >= 34 ? 1 : ovr >= 88 ? 5 : 3;

    const baselinePrice = roundToNearest25k(clamp(baseValueCad + ageImpactCad, 750_000, 16_500_000));
    const refPrice = (previousSavedPriceCad && previousSavedPriceCad > 0 && previousSavedPriceCad !== purchasePriceCad)
      ? previousSavedPriceCad : baselinePrice;
    const priceDeltaCad = purchasePriceCad - refPrice;

    const explanation = injuryImpactCad < 0
      ? `Discounted due to ${p.injurySeverity.replace('_', ' ')} (${p.injuryNote})`
      : curGp > 0
      ? `Goaltending valuation: ${p.wins}W, ${(curSvPct * 100).toFixed(1)}% SV% across ${curGp} GP`
      : `Prior season baseline: ${p.prevWins}W in ${prevGp} GP`;

    const components = [
      {
        factorName: `Goaltending Rating (${ovr} OVR)`,
        impactDescription: `${p.wins}W, ${p.shutouts} SO, ${(curSvPct * 100).toFixed(1)}% SV%`,
        cadContribution: baseValueCad
      }
    ];
    if (ageImpactCad !== 0) components.push({ factorName: `Age Curve (Age ${p.age})`, impactDescription: 'Crease longevity factor', cadContribution: ageImpactCad });
    if (formImpactCad !== 0) components.push({ factorName: 'Save % Form Trend', impactDescription: 'Current season SV% vs career baseline', cadContribution: formImpactCad });
    if (injuryImpactCad !== 0) components.push({ factorName: 'Injury Discount', impactDescription: p.injuryNote || p.injurySeverity, cadContribution: injuryImpactCad });

    return {
      overallRating: ovr,
      purchasePriceCad,
      sellPriceCad,
      simulatedCapHitCad,
      contractYears,
      priceDeltaCad,
      priceChangeExplanation: explanation,
      components
    };
  }
};

export const FantasyScoringEngine = {
  calculateSeasonBreakdown(p, weights = DEFAULT_SCORING_WEIGHTS) {
    const usePrior = p.gamesPlayed === 0 && p.prevGamesPlayed > 0 && !p.isProspect;
    const item = (category, statCount, weight) => ({
      category,
      statCount,
      weight,
      fantasyPoints: round1(statCount * weight)
    });

    if (p.position === 'G') {
      const w = usePrior ? p.prevWins : p.wins;
      const sv = usePrior ? p.prevSaves : p.saves;
      const so = usePrior ? p.prevShutouts : p.shutouts;
      const ga = usePrior ? 0 : (p.goalsAgainst || 0);
      const list = [
        item('Goalie Wins', w, weights.goalieWins),
        item('Saves', sv, weights.goalieSaves),
        item('Shutouts', so, weights.goalieShutouts)
      ];
      if (weights.goalieGoalsAgainst !== 0) list.push(item('Goals Against', ga, weights.goalieGoalsAgainst));
      return list;
    } else {
      const g = usePrior ? p.prevGoals : p.goals;
      const a = usePrior ? p.prevAssists : p.assists;
      const ppp = usePrior ? p.prevPpPoints : p.powerPlayPoints;
      const sog = usePrior ? p.prevShots : p.shots;
      const hit = usePrior ? p.prevHits : p.hits;
      const blk = usePrior ? p.prevBlockedShots : p.blockedShots;
      const list = [
        item('Goals', g, weights.goals),
        item('Assists', a, weights.assists),
        item('Power-Play Points', ppp, weights.powerPlayPoints),
        item('Shots on Goal', sog, weights.shotsOnGoal),
        item('Hits', hit, weights.hits),
        item('Blocked Shots', blk, weights.blockedShots)
      ];
      if (weights.penaltyMinutes !== 0 && !usePrior) list.push(item('Penalty Minutes', p.penaltyMinutes || 0, weights.penaltyMinutes));
      if (weights.plusMinus !== 0 && !usePrior) list.push(item('Plus/Minus', p.plusMinus || 0, weights.plusMinus));
      return list;
    }
  },

  calculateSeasonPoints(p, weights = DEFAULT_SCORING_WEIGHTS) {
    if (!p.gamesPlayed) return 0.0;
    const raw = p.position === 'G'
      ? p.wins * weights.goalieWins + p.saves * weights.goalieSaves + p.shutouts * weights.goalieShutouts + (p.goalsAgainst || 0) * weights.goalieGoalsAgainst
      : p.goals * weights.goals + p.assists * weights.assists + p.powerPlayPoints * weights.powerPlayPoints + p.shots * weights.shotsOnGoal + p.hits * weights.hits + p.blockedShots * weights.blockedShots + (p.penaltyMinutes || 0) * weights.penaltyMinutes + (p.plusMinus || 0) * weights.plusMinus;
    return round1(raw);
  },

  calculatePriorSeasonPoints(p, weights = DEFAULT_SCORING_WEIGHTS) {
    if (!p.prevGamesPlayed) return 0.0;
    const raw = p.position === 'G'
      ? p.prevWins * weights.goalieWins + p.prevSaves * weights.goalieSaves + p.prevShutouts * weights.goalieShutouts
      : p.prevGoals * weights.goals + p.prevAssists * weights.assists + p.prevPpPoints * weights.powerPlayPoints + p.prevShots * weights.shotsOnGoal + p.prevHits * weights.hits + p.prevBlockedShots * weights.blockedShots;
    return round1(raw);
  },

  calculateDailyLivePoints(p, weights = DEFAULT_SCORING_WEIGHTS) {
    if (!p.todayGameId) return 0.0;
    const raw = p.position === 'G'
      ? (p.todayGoalieWin || 0) * weights.goalieWins + (p.todayGoalieSaves || 0) * weights.goalieSaves + (p.todayGoalieShutout || 0) * weights.goalieShutouts
      : (p.todayGoals || 0) * weights.goals + (p.todayAssists || 0) * weights.assists + (p.todayPpPoints || 0) * weights.powerPlayPoints + (p.todayShots || 0) * weights.shotsOnGoal + (p.todayHits || 0) * weights.hits + (p.todayBlocks || 0) * weights.blockedShots;
    return round1(raw);
  },

  summarizePlayer(p, weights = DEFAULT_SCORING_WEIGHTS) {
    const currentSeasonPts = this.calculateSeasonPoints(p, weights);
    const priorSeasonPts = this.calculatePriorSeasonPoints(p, weights);
    const effectiveSeasonPts = p.gamesPlayed > 0 ? currentSeasonPts : priorSeasonPts;
    const effectiveGp = p.gamesPlayed > 0 ? p.gamesPlayed : (p.prevGamesPlayed > 0 ? p.prevGamesPlayed : 0);

    const fppg = effectiveGp > 0 ? round1(effectiveSeasonPts / effectiveGp) : 0.0;
    const dailyPts = this.calculateDailyLivePoints(p, weights);

    const expectedWeeklyGames = p.position === 'G' ? 2.1 : 3.2;
    const baseWeekly = p.gamesPlayed > 0 ? fppg * Math.min(expectedWeeklyGames, p.gamesPlayed) : fppg * expectedWeeklyGames * 0.75;
    const weeklyPts = round1(Math.max(dailyPts, baseWeekly));

    const priceMillions = Math.max(0.75, (p.purchasePriceCad || 1_000_000) / 1_000_000.0);
    const valuePerMillion = round1(effectiveSeasonPts / priceMillions);

    const priorFppg = p.prevGamesPlayed >= 5 ? priorSeasonPts / p.prevGamesPlayed : fppg;
    const currentFppg = p.gamesPlayed > 0 ? currentSeasonPts / p.gamesPlayed : 0.0;

    let trend = 'STEADY';
    if (dailyPts >= 6.0 || (p.gamesPlayed >= 3 && currentFppg >= priorFppg * 1.25 && currentFppg >= 4.0)) trend = 'HOT';
    else if (p.gamesPlayed >= 3 && currentFppg > priorFppg * 1.08) trend = 'RISING';
    else if (p.gamesPlayed >= 4 && priorFppg > 2.5 && currentFppg < priorFppg * 0.78) trend = 'COOLING';

    let isBreakoutCandidate = false;
    let breakoutReason = '';
    if (p.age <= 24) {
      if (p.position === 'G') {
        if (p.gamesPlayed >= 3 && p.savePct >= 0.912) {
          isBreakoutCandidate = true;
          breakoutReason = `Age ${p.age} goalie posting ${(p.savePct * 100).toFixed(1)}% SV% across ${p.gamesPlayed} GP`;
        }
      } else {
        const currentPpg = p.gamesPlayed > 0 ? p.points / p.gamesPlayed : 0.0;
        const priorPpg = p.prevGamesPlayed >= 10 ? p.prevPoints / p.prevGamesPlayed : 0.35;
        if (p.isRookie && p.gamesPlayed >= 3 && currentPpg >= 0.60) {
          isBreakoutCandidate = true;
          breakoutReason = `Rookie age ${p.age} averaging ${round2(currentPpg)} PTS/GP (${p.points} PTS in ${p.gamesPlayed} GP)`;
        } else if (p.gamesPlayed >= 4 && currentPpg >= 0.70 && currentPpg >= priorPpg * 1.22) {
          isBreakoutCandidate = true;
          breakoutReason = `Age ${p.age} surge: ${round2(currentPpg)} PTS/GP (+${Math.round((currentPpg / Math.max(0.2, priorPpg) - 1.0) * 100)}% vs prior season)`;
        } else if (p.gamesPlayed >= 4 && currentFppg >= 4.2 && currentFppg >= priorFppg * 1.20) {
          isBreakoutCandidate = true;
          breakoutReason = `Age ${p.age} multi-category breakout (${round1(currentFppg)} FP/GP with ${p.shots} SOG)`;
        }
      }
    }

    return {
      playerId: p.playerId,
      dailyPoints: dailyPts,
      weeklyPoints: weeklyPts,
      seasonPoints: effectiveSeasonPts,
      pointsPerGame: fppg,
      valuePerMillionCad: valuePerMillion,
      trend,
      isBreakoutCandidate,
      breakoutReason,
      breakdown: this.calculateSeasonBreakdown(p, weights)
    };
  }
};

export function slotAcceptsPlayer(slotCode, player) {
  const slot = LINEUP_SLOTS[slotCode];
  if (!slot) return false;
  if (slot.pos === 'ANY') return true;
  if (slot.pos === 'IR') return player.injurySeverity && player.injurySeverity !== 'HEALTHY';
  return slot.pos === player.position;
}

export function autoOptimizeLineup(rosterPlayers, weights = DEFAULT_SCORING_WEIGHTS) {
  // Sort by healthy first, then OVR and weekly fantasy points
  const scored = rosterPlayers.map((p) => ({
    player: p,
    summary: FantasyScoringEngine.summarizePlayer(p, weights),
    isInjured: p.injurySeverity === 'INJURED_RESERVE' || p.injurySeverity === 'OUT'
  })).sort((a, b) => {
    if (a.isInjured !== b.isInjured) return a.isInjured ? 1 : -1;
    if (b.player.overallRating !== a.player.overallRating) return b.player.overallRating - a.player.overallRating;
    return b.summary.weeklyPoints - a.summary.weeklyPoints;
  });

  const assigned = new Set();
  const slotAssignments = {};

  // 1. Place IR players in IR1, IR2 if injured
  for (const irCode of ['IR1', 'IR2']) {
    const candidate = scored.find((s) => !assigned.has(s.player.playerId) && s.isInjured);
    if (candidate) {
      assigned.add(candidate.player.playerId);
      slotAssignments[candidate.player.playerId] = irCode;
    }
  }

  // 2. Fill starter slots
  const starterOrder = [
    'C1', 'LW1', 'RW1', 'C2', 'LW2', 'RW2', 'C3', 'LW3', 'RW3', 'C4', 'LW4', 'RW4',
    'LD1', 'RD1', 'LD2', 'RD2', 'LD3', 'RD3',
    'G1', 'G2'
  ];

  for (const code of starterOrder) {
    const slot = LINEUP_SLOTS[code];
    const candidate = scored.find((s) => !assigned.has(s.player.playerId) && !s.isInjured && s.player.position === slot.pos);
    if (candidate) {
      assigned.add(candidate.player.playerId);
      slotAssignments[candidate.player.playerId] = code;
    }
  }

  // 3. Fill Bench slots BN1, BN2, BN3
  for (const bnCode of ['BN1', 'BN2', 'BN3']) {
    const candidate = scored.find((s) => !assigned.has(s.player.playerId));
    if (candidate) {
      assigned.add(candidate.player.playerId);
      slotAssignments[candidate.player.playerId] = bnCode;
    }
  }

  // 4. Remaining unassigned
  for (const s of scored) {
    if (!assigned.has(s.player.playerId)) {
      slotAssignments[s.player.playerId] = 'UNASSIGNED';
    }
  }

  return slotAssignments;
}

export function calculateTeamFinancials(team, rosterPlayers, economy = DEFAULT_ECONOMY_CONFIG) {
  const activeCapPlayers = rosterPlayers.filter((p) => p.lineupSlot !== 'IR1' && p.lineupSlot !== 'IR2');
  const totalCapHitCad = activeCapPlayers.reduce((sum, p) => sum + (p.capHitCad || 0), 0);
  const totalRosterMarketValueCad = rosterPlayers.reduce((sum, p) => sum + (p.purchasePriceCad || 0), 0);
  const capSpaceRemainingCad = economy.salaryCapCad - totalCapHitCad;
  return {
    cashBalanceCad: team.cashBalanceCad,
    totalCapHitCad,
    salaryCapCad: economy.salaryCapCad,
    capSpaceRemainingCad,
    totalRosterMarketValueCad,
    franchiseNetWorthCad: team.cashBalanceCad + totalRosterMarketValueCad,
    rosterCount: rosterPlayers.length,
    maxRosterSize: economy.maxRosterSize
  };
}

export function buildSnakeDraftOrder(teamIds, totalRounds, isSnake = true) {
  const order = [];
  for (let round = 1; round <= totalRounds; round++) {
    const roundTeams = (isSnake && round % 2 === 0) ? [...teamIds].reverse() : [...teamIds];
    roundTeams.forEach((tid, idx) => {
      order.push({
        overallPick: order.length + 1,
        roundNumber: round,
        pickInRound: idx + 1,
        teamId: tid
      });
    });
  }
  return order;
}

export function pickBestAvailableForCpu(team, teamRoster, availablePlayers, draftConfig = DEFAULT_DRAFT_CONFIG) {
  const counts = { C: 0, LW: 0, RW: 0, D: 0, G: 0 };
  let currentCap = 0;
  teamRoster.forEach((p) => {
    counts[p.position] = (counts[p.position] || 0) + 1;
    currentCap += (p.capHitCad || 0);
  });
  const capRemaining = draftConfig.salaryCapCad - currentCap;

  const needs = {
    C: counts.C < draftConfig.requiredCenters,
    LW: counts.LW < draftConfig.requiredLeftWings,
    RW: counts.RW < draftConfig.requiredRightWings,
    D: counts.D < draftConfig.requiredDefensemen,
    G: counts.G < draftConfig.requiredGoalies
  };
  const anyRequiredLeft = Object.values(needs).some(Boolean);

  const candidates = availablePlayers.filter((p) => {
    if (draftConfig.enforceSalaryCap && p.capHitCad > capRemaining) return false;
    if (anyRequiredLeft) return needs[p.position];
    return true;
  });

  const pool = candidates.length > 0 ? candidates : availablePlayers;
  let best = pool[0];
  let bestScore = -1e9;

  for (const p of pool) {
    const posBonus = needs[p.position] ? 12 : 0;
    const score = p.overallRating * 3.2 + (p.fantasy?.seasonPoints || 0) * 0.35 + posBonus - (p.capHitCad / 2_500_000);
    if (score > bestScore) {
      bestScore = score;
      best = p;
    }
  }
  return best;
}

export function generateRegularSeasonSchedule(seasonNumber, teams, totalWeeks = 14) {
  const matchups = [];
  const teamIds = teams.map((t) => t.teamId);
  const teamMap = Object.fromEntries(teams.map((t) => [t.teamId, t]));
  const n = teamIds.length;
  if (n < 2) return matchups;

  const rot = [...teamIds];
  let idCounter = 1;

  for (let week = 1; week <= totalWeeks; week++) {
    for (let i = 0; i < Math.floor(n / 2); i++) {
      const t1 = rot[i];
      const t2 = rot[n - 1 - i];
      const homeId = week % 2 === 1 ? t1 : t2;
      const awayId = week % 2 === 1 ? t2 : t1;
      matchups.push({
        matchupId: idCounter++,
        seasonNumber,
        weekNumber: week,
        stage: 'REGULAR_SEASON',
        homeTeamId: homeId,
        homeTeamName: teamMap[homeId]?.teamName || homeId,
        awayTeamId: awayId,
        awayTeamName: teamMap[awayId]?.teamName || awayId,
        homeScore: 0.0,
        awayScore: 0.0,
        isCompleted: false,
        scoringModeLabel: 'SCHEDULED',
        highlightSummary: 'Scheduled Head-to-Head Showdown',
        completedAtEpochMs: 0
      });
    }
    // Rotate all except index 0
    const last = rot.pop();
    rot.splice(1, 0, last);
  }
  return matchups;
}
