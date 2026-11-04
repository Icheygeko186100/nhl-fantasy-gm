/**
 * NHL Fantasy GM — Main Application Controller & UI Renderer (iOS / Web PWA Edition)
 */

import {
  DEFAULT_SCORING_WEIGHTS,
  DEFAULT_ECONOMY_CONFIG,
  DEFAULT_DRAFT_CONFIG,
  LINEUP_GROUPS,
  LINEUP_SLOTS,
  TEAM_EMBLEMS,
  TEAM_COLOR_PRESETS,
  CPU_TEAM_BLUEPRINTS,
  CadFormatter,
  PlayerValuationEngine,
  FantasyScoringEngine,
  slotAcceptsPlayer,
  autoOptimizeLineup,
  calculateTeamFinancials,
  buildSnakeDraftOrder,
  pickBestAvailableForCpu,
  generateRegularSeasonSchedule,
  hexLongToCss,
  getEmblemIcon
} from './engine.js';

import {
  getTodayEasternDateStr,
  buildSevenDayWeek,
  loadInitialNhlDatabase,
  fetchLiveScoreboardForDate,
  fetchLiveBoxscoresForGames,
  fetchLiveNhlNews
} from './live-api.js';

import { RoomCrypto, MultiplayerRoomClient } from './multiplayer.js';

const STORAGE_KEY = 'nhl_fantasy_gm_ios_state_v155';

const state = {
  // Navigation
  bottomNav: 'FANTASY', // 'HOME' | 'FANTASY' | 'SCORES' | 'PROFILE'
  fantasySubTab: 'MATCH', // 'MATCH' | 'TEAM' | 'PLAYERS' | 'LEAGUE'
  leagueSubTab: 'DRAFTS', // 'DRAFTS' | 'STANDINGS' | 'TRANSACTIONS' | 'NHL_STATS' | 'NEWS'

  // Live NHL Data
  isSyncing: false,
  nhlTeams: [],
  allPlayers: [],
  liveGames: [],
  customDateGames: null,
  todayDate: getTodayEasternDateStr(),
  selectedScoreDate: null,
  gameWeek: [],
  newsHeadlines: [],
  pinnedGameIds: [],

  // Marketplace Filters
  marketCategory: 'ALL', // 'ALL' | 'BEST_VALUE' | 'BREAKOUTS' | 'WATCHLIST' | 'FREE_AGENTS'
  marketPosition: 'ALL', // 'ALL' | 'C' | 'LW' | 'RW' | 'D' | 'G'
  marketSort: 'OVR', // 'OVR' | 'FP' | 'PRICE_DESC' | 'PRICE_ASC' | 'FPPG'
  marketSearch: '',

  // Persisted Franchise & League State
  settings: {
    scoringWeights: { ...DEFAULT_SCORING_WEIGHTS },
    economyConfig: { ...DEFAULT_ECONOMY_CONFIG },
    draftConfig: { ...DEFAULT_DRAFT_CONFIG },
    currentSeasonNumber: 1,
    currentWeekNumber: 1
  },
  fantasyTeams: [],
  ownership: {}, // { [playerId]: { ownerTeamId, acquisitionPriceCad, lineupSlot } }
  watchlist: [],
  transactions: [],
  matchups: [],
  selectedMatchupWeek: 1,

  // Draft State
  draftSession: null, // { status: 'IN_PROGRESS'|'COMPLETED', order: [], currentPickIndex: 0 }
  draftPicks: [],

  // Multiplayer State
  mp: {
    role: 'NONE', // 'NONE' | 'HOST' | 'GUEST'
    roomCode: '',
    connectionState: 'DISCONNECTED',
    localGmName: 'GM Captain',
    participants: [],
    chatMessages: [],
    lastError: ''
  },

  // Active Modal
  modal: null // { type: 'PLAYER_DETAIL'|'SETTINGS'|'CUSTOMIZE_TEAM'|'IOS_INSTALL'|'SLOT_PICKER', data: any }
};

let mpClient = null;

function showToast(msg) {
  const container = document.getElementById('toast-container');
  if (!container) return;
  const el = document.createElement('div');
  el.className = 'toast-msg';
  el.textContent = msg;
  container.innerHTML = '';
  container.appendChild(el);
  setTimeout(() => {
    if (container.contains(el)) container.removeChild(el);
  }, 3200);
}

function savePersistedState() {
  try {
    const payload = {
      settings: state.settings,
      fantasyTeams: state.fantasyTeams,
      ownership: state.ownership,
      watchlist: state.watchlist,
      transactions: state.transactions,
      matchups: state.matchups,
      selectedMatchupWeek: state.selectedMatchupWeek,
      draftSession: state.draftSession,
      draftPicks: state.draftPicks,
      pinnedGameIds: state.pinnedGameIds,
      localGmName: state.mp.localGmName
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  } catch (e) {
    console.warn('Failed to persist state:', e);
  }
}

function loadPersistedState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return false;
    const parsed = JSON.parse(raw);
    if (parsed.settings) state.settings = { ...state.settings, ...parsed.settings };
    if (Array.isArray(parsed.fantasyTeams) && parsed.fantasyTeams.length > 0) {
      state.fantasyTeams = parsed.fantasyTeams;
    }
    if (parsed.ownership) state.ownership = parsed.ownership;
    if (Array.isArray(parsed.watchlist)) state.watchlist = parsed.watchlist;
    if (Array.isArray(parsed.transactions)) state.transactions = parsed.transactions;
    if (Array.isArray(parsed.matchups) && parsed.matchups.length > 0) state.matchups = parsed.matchups;
    if (parsed.selectedMatchupWeek) state.selectedMatchupWeek = parsed.selectedMatchupWeek;
    if (parsed.draftSession) state.draftSession = parsed.draftSession;
    if (Array.isArray(parsed.draftPicks)) state.draftPicks = parsed.draftPicks;
    if (Array.isArray(parsed.pinnedGameIds)) state.pinnedGameIds = parsed.pinnedGameIds;
    if (parsed.localGmName) state.mp.localGmName = parsed.localGmName;
    return state.fantasyTeams.length > 0;
  } catch (_) {
    return false;
  }
}

function initializeDefaultLeague() {
  const budget = state.settings.economyConfig.startingBudgetCad;
  const userTeam = {
    teamId: 'USER',
    teamName: 'Northern Ice HC',
    abbreviation: 'NIC',
    emblemId: 'MAPLE_SHIELD',
    primaryColorHex: 0xFF00C2FF,
    secondaryColorHex: 0xFF0B192C,
    isHuman: true,
    ownerType: 'HUMAN_LOCAL',
    gmDisplayName: state.mp.localGmName,
    cashBalanceCad: budget,
    startingBudgetCad: budget,
    wins: 0,
    losses: 0,
    ties: 0,
    pointsFor: 0.0,
    pointsAgainst: 0.0,
    championshipsWon: 0
  };

  const cpuTeams = CPU_TEAM_BLUEPRINTS.map((bp, idx) => ({
    teamId: `CPU_${idx + 1}`,
    teamName: bp.teamName,
    abbreviation: bp.abbreviation,
    emblemId: bp.emblemId,
    primaryColorHex: bp.primaryColorHex,
    secondaryColorHex: bp.secondaryColorHex,
    isHuman: false,
    ownerType: 'CPU',
    gmDisplayName: 'CPU AI',
    cashBalanceCad: budget,
    startingBudgetCad: budget,
    wins: 0,
    losses: 0,
    ties: 0,
    pointsFor: 0.0,
    pointsAgainst: 0.0,
    championshipsWon: 0
  }));

  state.fantasyTeams = [userTeam, ...cpuTeams];
  state.ownership = {};
  state.transactions = [
    {
      id: Date.now(),
      type: 'SETTINGS',
      teamId: 'USER',
      title: 'Initial Franchise Treasury Allocation',
      details: `Starting virtual CAD GM budget initialized (${CadFormatter.formatFull(budget)}).`,
      cashDeltaCad: budget,
      balanceAfterCad: budget,
      timestampEpochMs: Date.now()
    }
  ];
  state.matchups = generateRegularSeasonSchedule(state.settings.currentSeasonNumber, state.fantasyTeams);
  state.selectedMatchupWeek = 1;
  savePersistedState();
}

function enrichAllPlayers() {
  const weights = state.settings.scoringWeights;
  const economy = state.settings.economyConfig;

  state.allPlayers.forEach((p) => {
    const val = PlayerValuationEngine.valuatePlayer(p, economy, p.previousPriceCad);
    p.overallRating = val.overallRating;
    p.purchasePriceCad = val.purchasePriceCad;
    p.sellPriceCad = val.sellPriceCad;
    p.capHitCad = val.simulatedCapHitCad;
    p.contractYears = val.contractYears;
    p.priceDeltaCad = val.priceDeltaCad;
    p.priceChangeExplanation = val.priceChangeExplanation;
    p.valuationComponents = val.components;

    const own = state.ownership[p.playerId];
    p.ownerTeamId = own ? own.ownerTeamId : null;
    p.acquisitionPriceCad = own ? own.acquisitionPriceCad : 0;
    p.lineupSlot = own ? own.lineupSlot : 'UNASSIGNED';
    p.isWatchlisted = state.watchlist.includes(p.playerId);

    p.fantasy = FantasyScoringEngine.summarizePlayer(p, weights);
  });
}

function getHumanTeam() {
  return state.fantasyTeams.find((t) => t.teamId === 'USER') || state.fantasyTeams[0];
}

function getTeamRoster(teamId) {
  return state.allPlayers.filter((p) => p.ownerTeamId === teamId);
}

// --- Live Scoreboard & Boxscore Sync ---
async function refreshLiveScores(targetDate = null) {
  if (state.isSyncing) return;
  state.isSyncing = true;
  renderApp();

  const dateToFetch = targetDate || state.selectedScoreDate || getTodayEasternDateStr();
  try {
    const sb = await fetchLiveScoreboardForDate(dateToFetch);
    const isToday = !state.selectedScoreDate || state.selectedScoreDate === state.todayDate;

    if (isToday) {
      state.liveGames = sb.games;
      state.customDateGames = null;

      // Update gameWeek count for today
      state.gameWeek = buildSevenDayWeek(state.todayDate, state.gameWeek);
      const todayEntry = state.gameWeek.find((d) => d.date === dateToFetch);
      if (todayEntry) todayEntry.numberOfGames = sb.games.length;

      // Fetch live player boxscores for today's active/completed games
      const boxMap = await fetchLiveBoxscoresForGames(sb.games);
      state.allPlayers.forEach((p) => {
        const key = p.fullName.trim().toLowerCase();
        const box = boxMap[key];
        if (box) {
          p.todayGameId = box.gameId;
          p.todayGameIsLive = box.isLive;
          p.todayOpponentLabel = box.opponentLabel;
          p.todayGoals = box.goals;
          p.todayAssists = box.assists;
          p.todayPpPoints = box.ppPoints;
          p.todayShots = box.shots;
          p.todayHits = box.hits;
          p.todayBlocks = box.blocks;
          p.todayGoalieWin = box.goalieWin;
          p.todayGoalieSaves = box.goalieSaves;
          p.todayGoalieShutout = box.goalieShutout;
        } else {
          p.todayGameId = 0;
          p.todayGameIsLive = false;
          p.todayGoals = 0;
          p.todayAssists = 0;
          p.todayShots = 0;
          p.todayHits = 0;
          p.todayBlocks = 0;
          p.todayGoalieWin = 0;
          p.todayGoalieSaves = 0;
          p.todayGoalieShutout = 0;
        }
      });
      enrichAllPlayers();
      showToast(`Refreshed ${sb.games.length} NHL games & live player boxscores.`);
    } else {
      state.customDateGames = sb.games;
      const dayEntry = state.gameWeek.find((d) => d.date === dateToFetch);
      if (dayEntry) dayEntry.numberOfGames = sb.games.length;
      showToast(`Loaded ${sb.games.length} NHL games for ${dateToFetch}.`);
    }
  } catch (err) {
    showToast(`Live scoreboard sync offline: using cached schedule.`);
  } finally {
    state.isSyncing = false;
    renderApp();
  }
}

async function selectScoreDate(dateStr) {
  if (!dateStr || dateStr === state.todayDate) {
    state.selectedScoreDate = null;
    state.customDateGames = null;
    renderApp();
    return;
  }
  state.selectedScoreDate = dateStr;
  await refreshLiveScores(dateStr);
}

// --- Marketplace Buy & Sell Actions ---
function buyPlayer(playerId) {
  const player = state.allPlayers.find((p) => p.playerId === playerId);
  if (!player) return;
  if (player.ownerTeamId) {
    showToast(`${player.fullName} is already under contract.`);
    return;
  }

  const team = getHumanTeam();
  const roster = getTeamRoster(team.teamId);
  const fin = calculateTeamFinancials(team, roster, state.settings.economyConfig);

  if (roster.length >= state.settings.economyConfig.maxRosterSize) {
    showToast(`Roster full (${fin.rosterCount}/${fin.maxRosterSize}). Release or sell a player first.`);
    return;
  }
  if (team.cashBalanceCad < player.purchasePriceCad) {
    showToast(`Insufficient GM Cash (${CadFormatter.formatCompact(team.cashBalanceCad)} available).`);
    return;
  }
  if (state.settings.economyConfig.enforceSalaryCap && (fin.totalCapHitCad + player.capHitCad > fin.salaryCapCad)) {
    showToast(`Exceeds $95.50M CAD Salary Cap (${CadFormatter.formatCompact(fin.capSpaceRemainingCad)} space left).`);
    return;
  }

  team.cashBalanceCad -= player.purchasePriceCad;
  state.ownership[player.playerId] = {
    ownerTeamId: team.teamId,
    acquisitionPriceCad: player.purchasePriceCad,
    lineupSlot: 'UNASSIGNED'
  };

  // Auto-slot into open starter or bench slot
  enrichAllPlayers();
  const updatedRoster = getTeamRoster(team.teamId);
  const optimized = autoOptimizeLineup(updatedRoster, state.settings.scoringWeights);
  Object.entries(optimized).forEach(([pid, slotCode]) => {
    if (state.ownership[pid]) state.ownership[pid].lineupSlot = slotCode;
  });

  state.transactions.unshift({
    id: Date.now(),
    type: 'BUY',
    teamId: team.teamId,
    title: `Signed ${player.fullName} (${player.position} • ${player.nhlTeamAbbrev})`,
    details: `Acquired for ${CadFormatter.formatCompact(player.purchasePriceCad)} • Cap Hit ${CadFormatter.formatCompact(player.capHitCad)}`,
    cashDeltaCad: -player.purchasePriceCad,
    balanceAfterCad: team.cashBalanceCad,
    timestampEpochMs: Date.now()
  });

  enrichAllPlayers();
  savePersistedState();
  showToast(`Signed ${player.fullName} for ${CadFormatter.formatCompact(player.purchasePriceCad)}!`);
  renderApp();
}

function sellPlayer(playerId) {
  const player = state.allPlayers.find((p) => p.playerId === playerId);
  if (!player || player.ownerTeamId !== 'USER') return;

  const team = getHumanTeam();
  team.cashBalanceCad += player.sellPriceCad;
  delete state.ownership[player.playerId];

  state.transactions.unshift({
    id: Date.now(),
    type: 'SELL',
    teamId: team.teamId,
    title: `Released / Sold ${player.fullName} (${player.position})`,
    details: `Recouped ${CadFormatter.formatCompact(player.sellPriceCad)} (after ${state.settings.economyConfig.marketplaceSellFeePercent}% market fee)`,
    cashDeltaCad: player.sellPriceCad,
    balanceAfterCad: team.cashBalanceCad,
    timestampEpochMs: Date.now()
  });

  enrichAllPlayers();
  savePersistedState();
  showToast(`Sold ${player.fullName} for ${CadFormatter.formatCompact(player.sellPriceCad)}.`);
  if (state.modal?.type === 'PLAYER_DETAIL') state.modal = null;
  renderApp();
}

function toggleWatchlist(playerId) {
  const idx = state.watchlist.indexOf(playerId);
  if (idx >= 0) {
    state.watchlist.splice(idx, 1);
    showToast('Removed from Watchlist.');
  } else {
    state.watchlist.push(playerId);
    showToast('Added to Watchlist ★');
  }
  enrichAllPlayers();
  savePersistedState();
  renderApp();
}

// --- Matchup Simulation ---
function simulateNextWeek() {
  const week = state.settings.currentWeekNumber;
  const weekMatchups = state.matchups.filter((m) => m.weekNumber === week && !m.isCompleted);
  if (weekMatchups.length === 0) {
    if (week < 14) {
      state.settings.currentWeekNumber++;
      state.selectedMatchupWeek = state.settings.currentWeekNumber;
      savePersistedState();
      renderApp();
      showToast(`Advanced to Week ${state.settings.currentWeekNumber}.`);
    } else {
      showToast('All 14 regular season weeks completed!');
    }
    return;
  }

  // Ensure CPU teams have rosters if they haven't drafted yet
  seedCpuRostersIfEmpty();

  weekMatchups.forEach((m) => {
    const homeScore = computeTeamWeeklyScore(m.homeTeamId);
    const awayScore = computeTeamWeeklyScore(m.awayTeamId);
    m.homeScore = homeScore;
    m.awayScore = awayScore;
    m.isCompleted = true;
    m.scoringModeLabel = 'FINAL';
    m.completedAtEpochMs = Date.now();

    const homeTeam = state.fantasyTeams.find((t) => t.teamId === m.homeTeamId);
    const awayTeam = state.fantasyTeams.find((t) => t.teamId === m.awayTeamId);

    if (homeTeam && awayTeam) {
      homeTeam.pointsFor = Math.round((homeTeam.pointsFor + homeScore) * 10) / 10;
      homeTeam.pointsAgainst = Math.round((homeTeam.pointsAgainst + awayScore) * 10) / 10;
      awayTeam.pointsFor = Math.round((awayTeam.pointsFor + awayScore) * 10) / 10;
      awayTeam.pointsAgainst = Math.round((awayTeam.pointsAgainst + homeScore) * 10) / 10;

      if (homeScore > awayScore) {
        homeTeam.wins++;
        awayTeam.losses++;
        m.highlightSummary = `${homeTeam.teamName} defeated ${awayTeam.teamName} (${homeScore} - ${awayScore} FP)`;
      } else if (awayScore > homeScore) {
        awayTeam.wins++;
        homeTeam.losses++;
        m.highlightSummary = `${awayTeam.teamName} defeated ${homeTeam.teamName} (${awayScore} - ${homeScore} FP)`;
      } else {
        homeTeam.ties++;
        awayTeam.ties++;
        m.highlightSummary = `Draw (${homeScore} - ${awayScore} FP)`;
      }
    }
  });

  state.selectedMatchupWeek = week;
  if (state.settings.currentWeekNumber < 14) {
    state.settings.currentWeekNumber++;
  }
  savePersistedState();
  showToast(`Simulated Week ${week} matchups!`);
  renderApp();
}

function computeTeamWeeklyScore(teamId) {
  const roster = getTeamRoster(teamId);
  const starters = roster.filter((p) => {
    const slot = LINEUP_SLOTS[p.lineupSlot];
    return slot ? slot.isActiveStarter : true;
  });
  const activePool = starters.length > 0 ? starters : roster.slice(0, 18);
  const baseTotal = activePool.reduce((acc, p) => acc + (p.fantasy?.weeklyPoints || 3.5), 0);
  // Slight variance for realistic weekly game flow
  const jitter = (Math.sin(teamId.length * 17 + Date.now() % 97) * 4.5);
  return Math.max(12.0, Math.round((baseTotal + jitter) * 10) / 10);
}

function seedCpuRostersIfEmpty() {
  const cpuTeams = state.fantasyTeams.filter((t) => !t.isHuman);
  const anyCpuHasPlayers = cpuTeams.some((t) => getTeamRoster(t.teamId).length > 0);
  if (anyCpuHasPlayers) return;

  const available = [...state.allPlayers]
    .filter((p) => !p.ownerTeamId)
    .sort((a, b) => b.overallRating - a.overallRating);

  let cursor = 0;
  for (let round = 0; round < 16; round++) {
    for (const cpu of cpuTeams) {
      while (cursor < available.length && state.ownership[available[cursor].playerId]) {
        cursor++;
      }
      const pick = available[cursor++];
      if (pick) {
        state.ownership[pick.playerId] = {
          ownerTeamId: cpu.teamId,
          acquisitionPriceCad: pick.purchasePriceCad,
          lineupSlot: 'UNASSIGNED'
        };
      }
    }
  }
  enrichAllPlayers();
}

// --- Snake Draft Room Engine ---
function startNewSnakeDraft() {
  const cfg = state.settings.draftConfig;
  const totalRounds = cfg.requiredCenters + cfg.requiredLeftWings + cfg.requiredRightWings + cfg.requiredDefensemen + cfg.requiredGoalies + cfg.benchSlots;
  const teamIds = state.fantasyTeams.map((t) => t.teamId);
  const order = buildSnakeDraftOrder(teamIds, totalRounds, cfg.isSnakeDraft);

  state.ownership = {};
  state.fantasyTeams.forEach((t) => {
    t.cashBalanceCad = state.settings.economyConfig.startingBudgetCad;
  });

  state.draftSession = {
    status: 'IN_PROGRESS',
    totalRounds,
    order,
    currentPickIndex: 0,
    startedAtEpochMs: Date.now()
  };
  state.draftPicks = [];
  enrichAllPlayers();
  advanceCpuDraftPicks();
  savePersistedState();
  showToast('Started 8-Team Snake Draft! You are on the clock.');
  renderApp();
}

function advanceCpuDraftPicks() {
  if (!state.draftSession || state.draftSession.status !== 'IN_PROGRESS') return;

  while (state.draftSession.currentPickIndex < state.draftSession.order.length) {
    const slot = state.draftSession.order[state.draftSession.currentPickIndex];
    const team = state.fantasyTeams.find((t) => t.teamId === slot.teamId);
    if (!team || team.isHuman) break;

    const available = state.allPlayers.filter((p) => !state.ownership[p.playerId]);
    const teamRoster = state.allPlayers.filter((p) => state.ownership[p.playerId]?.ownerTeamId === team.teamId);
    const chosen = pickBestAvailableForCpu(team, teamRoster, available, state.settings.draftConfig);
    if (!chosen) break;

    recordDraftPickInternal(slot, team, chosen);
  }

  if (state.draftSession.currentPickIndex >= state.draftSession.order.length) {
    finalizeDraftSession();
  }
  enrichAllPlayers();
}

function recordDraftPickInternal(slot, team, player) {
  state.ownership[player.playerId] = {
    ownerTeamId: team.teamId,
    acquisitionPriceCad: player.purchasePriceCad,
    lineupSlot: 'UNASSIGNED'
  };
  team.cashBalanceCad = Math.max(0, team.cashBalanceCad - player.purchasePriceCad);

  state.draftPicks.unshift({
    overallPick: slot.overallPick,
    roundNumber: slot.roundNumber,
    pickInRound: slot.pickInRound,
    fantasyTeamId: team.teamId,
    fantasyTeamName: team.teamName,
    isHumanPick: team.isHuman,
    playerId: player.playerId,
    playerName: player.fullName,
    positionCode: player.position,
    nhlTeamAbbrev: player.nhlTeamAbbrev,
    overallRating: player.overallRating,
    capHitCad: player.capHitCad,
    pickCostCad: player.purchasePriceCad,
    pickedAtEpochMs: Date.now()
  });

  state.draftSession.currentPickIndex++;
}

function makeUserDraftPick(playerId) {
  if (!state.draftSession || state.draftSession.status !== 'IN_PROGRESS') return;
  const slot = state.draftSession.order[state.draftSession.currentPickIndex];
  if (!slot || slot.teamId !== 'USER') {
    showToast('Wait for your turn on the draft clock.');
    return;
  }
  const player = state.allPlayers.find((p) => p.playerId === playerId);
  if (!player || state.ownership[player.playerId]) return;

  const userTeam = getHumanTeam();
  recordDraftPickInternal(slot, userTeam, player);
  advanceCpuDraftPicks();

  // Auto-optimize user lineup slots
  const userRoster = getTeamRoster('USER');
  const opt = autoOptimizeLineup(userRoster, state.settings.scoringWeights);
  Object.entries(opt).forEach(([pid, sc]) => {
    if (state.ownership[pid]) state.ownership[pid].lineupSlot = sc;
  });

  enrichAllPlayers();
  savePersistedState();
  showToast(`Drafted ${player.fullName} (#${slot.overallPick} overall)!`);
  if (state.mp.role === 'HOST') broadcastMultiplayerSnapshot();
  renderApp();
}

function autoPickUserTurn() {
  if (!state.draftSession || state.draftSession.status !== 'IN_PROGRESS') return;
  const slot = state.draftSession.order[state.draftSession.currentPickIndex];
  if (!slot) return;
  const team = state.fantasyTeams.find((t) => t.teamId === slot.teamId);
  const available = state.allPlayers.filter((p) => !state.ownership[p.playerId]);
  const roster = getTeamRoster(team.teamId);
  const best = pickBestAvailableForCpu(team, roster, available, state.settings.draftConfig);
  if (best) makeUserDraftPick(best.playerId);
}

function finalizeDraftSession() {
  if (!state.draftSession) return;
  state.draftSession.status = 'COMPLETED';
  state.draftSession.completedAtEpochMs = Date.now();

  state.fantasyTeams.forEach((t) => {
    const r = getTeamRoster(t.teamId);
    const opt = autoOptimizeLineup(r, state.settings.scoringWeights);
    Object.entries(opt).forEach(([pid, sc]) => {
      if (state.ownership[pid]) state.ownership[pid].lineupSlot = sc;
    });
  });
  showToast('Snake Draft Completed! All rosters & starting lineups optimized.');
}

// --- Cross-Platform Multiplayer Room (iOS / Web <-> Android) ---
function initMultiplayerClient() {
  if (mpClient) return mpClient;
  mpClient = new MultiplayerRoomClient({
    onStateChange(connState, err) {
      state.mp.connectionState = connState;
      state.mp.lastError = err || '';
      if (connState === 'CONNECTED') {
        if (state.mp.role === 'HOST') {
          broadcastMultiplayerSnapshot();
        } else if (state.mp.role === 'GUEST') {
          const userTeam = getHumanTeam();
          mpClient.publishEnvelope({
            messageId: 'msg_' + Date.now(),
            type: 'JOIN_LOBBY',
            senderClientId: mpClient.clientId,
            timestampEpochMs: Date.now(),
            participantUpdate: {
              gmName: state.mp.localGmName,
              teamName: userTeam.teamName,
              abbreviation: userTeam.abbreviation,
              emblemId: userTeam.emblemId,
              primaryColorHex: userTeam.primaryColorHex,
              secondaryColorHex: userTeam.secondaryColorHex,
              isReady: true
            }
          });
        }
      }
      renderApp();
    },
    onEnvelope(env) {
      handleIncomingMultiplayerEnvelope(env);
    }
  });
  return mpClient;
}

async function hostMultiplayerRoom() {
  const client = initMultiplayerClient();
  const code = RoomCrypto.generateInviteCode(6);
  const userTeam = getHumanTeam();
  state.mp.role = 'HOST';
  state.mp.roomCode = code;
  state.mp.participants = [
    {
      clientId: client.clientId,
      gmName: state.mp.localGmName,
      assignedTeamId: 'USER',
      teamName: userTeam.teamName,
      abbreviation: userTeam.abbreviation,
      emblemId: userTeam.emblemId,
      primaryColorHex: userTeam.primaryColorHex,
      secondaryColorHex: userTeam.secondaryColorHex,
      isHost: true,
      isLocalPassAndPlay: false,
      isReady: true,
      joinedAtEpochMs: Date.now(),
      lastSeenEpochMs: Date.now()
    }
  ];
  state.mp.chatMessages = [
    {
      messageId: 'sys_' + Date.now(),
      senderClientId: 'SYSTEM',
      senderGmName: 'Room Relay',
      senderTeamAbbrev: 'NHL',
      text: `Encrypted Room ${RoomCrypto.formatInviteCodeForDisplay(code)} hosted on iOS/Web. Share code with Android & iPhone GMs!`,
      isSystemEvent: true,
      timestampEpochMs: Date.now()
    }
  ];
  await client.connect(code);
  showToast(`Hosting Room ${RoomCrypto.formatInviteCodeForDisplay(code)}!`);
  renderApp();
}

async function joinMultiplayerRoom(rawCode) {
  const norm = RoomCrypto.normalizeInviteCode(rawCode);
  if (norm.length < 4) {
    showToast('Please enter a valid 6-character Room Invite Code.');
    return;
  }
  const client = initMultiplayerClient();
  state.mp.role = 'GUEST';
  state.mp.roomCode = norm;
  await client.connect(norm);
  showToast(`Connecting to Room ${RoomCrypto.formatInviteCodeForDisplay(norm)}...`);
  renderApp();
}

function leaveMultiplayerRoom() {
  if (mpClient) mpClient.disconnect();
  state.mp.role = 'NONE';
  state.mp.roomCode = '';
  state.mp.participants = [];
  state.mp.chatMessages = [];
  showToast('Left multiplayer room.');
  renderApp();
}

async function broadcastMultiplayerSnapshot() {
  if (!mpClient || state.mp.role !== 'HOST' || state.mp.connectionState !== 'CONNECTED') return;
  const snapshot = {
    roomCode: state.mp.roomCode,
    revision: Date.now(),
    hostClientId: mpClient.clientId,
    hostGmName: state.mp.localGmName,
    phase: state.draftSession?.status === 'IN_PROGRESS' ? 'DRAFT_IN_PROGRESS' : 'LOBBY',
    fillEmptySlotsWithCpu: true,
    draftConfig: state.settings.draftConfig,
    economyConfig: state.settings.economyConfig,
    scoringWeights: state.settings.scoringWeights,
    currentSeasonNumber: state.settings.currentSeasonNumber,
    participants: state.mp.participants,
    orderedSlotTeamIds: state.fantasyTeams.map((t) => t.teamId),
    teams: state.fantasyTeams,
    draftPicks: state.draftPicks,
    chatHistory: state.mp.chatMessages.slice(-40),
    updatedAtEpochMs: Date.now()
  };

  await mpClient.publishEnvelope(
    {
      messageId: 'snap_' + Date.now(),
      type: 'SNAPSHOT',
      senderClientId: mpClient.clientId,
      timestampEpochMs: Date.now(),
      snapshot
    },
    true
  );
}

function handleIncomingMultiplayerEnvelope(env) {
  if (!env || !env.type) return;

  if (env.type === 'SNAPSHOT' && env.snapshot) {
    const snap = env.snapshot;
    if (Array.isArray(snap.participants)) state.mp.participants = snap.participants;
    if (Array.isArray(snap.chatHistory)) state.mp.chatMessages = snap.chatHistory;
    if (Array.isArray(snap.draftPicks) && snap.draftPicks.length > 0) {
      state.draftPicks = snap.draftPicks;
    }
    renderApp();
  } else if (env.type === 'JOIN_LOBBY' && state.mp.role === 'HOST' && env.participantUpdate) {
    const p = env.participantUpdate;
    const existingIdx = state.mp.participants.findIndex((x) => x.clientId === env.senderClientId);
    const entry = {
      clientId: env.senderClientId,
      gmName: p.gmName || 'Remote GM',
      assignedTeamId: existingIdx >= 0 ? state.mp.participants[existingIdx].assignedTeamId : `CPU_${state.mp.participants.length}`,
      teamName: p.teamName || 'Remote HC',
      abbreviation: p.abbreviation || 'RMT',
      emblemId: p.emblemId || 'MAPLE_SHIELD',
      primaryColorHex: p.primaryColorHex || 0xFF00C2FF,
      secondaryColorHex: p.secondaryColorHex || 0xFF0B192C,
      isHost: false,
      isLocalPassAndPlay: false,
      isReady: p.isReady !== false,
      joinedAtEpochMs: Date.now(),
      lastSeenEpochMs: Date.now()
    };
    if (existingIdx >= 0) state.mp.participants[existingIdx] = entry;
    else state.mp.participants.push(entry);

    state.mp.chatMessages.push({
      messageId: 'join_' + Date.now(),
      senderClientId: 'SYSTEM',
      senderGmName: 'Lobby',
      senderTeamAbbrev: entry.abbreviation,
      text: `${entry.gmName} (${entry.teamName}) joined the room!`,
      isSystemEvent: true,
      timestampEpochMs: Date.now()
    });
    broadcastMultiplayerSnapshot();
    renderApp();
  } else if (env.type === 'CHAT' && env.chatMessage) {
    const exists = state.mp.chatMessages.some((m) => m.messageId === env.chatMessage.messageId);
    if (!exists) {
      state.mp.chatMessages.push(env.chatMessage);
      if (state.mp.role === 'HOST') broadcastMultiplayerSnapshot();
      renderApp();
    }
  }
}

async function sendMultiplayerChat(text) {
  if (!text || !text.trim() || !mpClient) return;
  const userTeam = getHumanTeam();
  const msg = {
    messageId: 'chat_' + Date.now() + '_' + Math.random().toString(36).slice(2, 6),
    senderClientId: mpClient.clientId,
    senderGmName: state.mp.localGmName,
    senderTeamAbbrev: userTeam.abbreviation,
    text: text.trim(),
    isSystemEvent: false,
    timestampEpochMs: Date.now()
  };
  state.mp.chatMessages.push(msg);
  await mpClient.publishEnvelope({
    messageId: msg.messageId,
    type: 'CHAT',
    senderClientId: mpClient.clientId,
    timestampEpochMs: Date.now(),
    chatMessage: msg
  });
  if (state.mp.role === 'HOST') broadcastMultiplayerSnapshot();
  renderApp();
}

// --- Formatting Helpers ---
function formatStartTimeLocal(isoUtc) {
  if (!isoUtc) return 'SCHEDULED';
  try {
    const d = new Date(isoUtc);
    if (isNaN(d.getTime())) return 'SCHEDULED';
    return d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  } catch (_) {
    return 'SCHEDULED';
  }
}

function formatShortMonthDay(dateStr) {
  if (!dateStr) return '';
  try {
    const d = new Date(`${dateStr}T12:00:00Z`);
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' });
  } catch (_) {
    return dateStr.slice(-5);
  }
}

function formatFullDateHeader(dateStr) {
  if (!dateStr) return '';
  try {
    const d = new Date(`${dateStr}T12:00:00Z`);
    return d.toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric', timeZone: 'UTC' });
  } catch (_) {
    return dateStr;
  }
}

// --- UI Rendering ---
export function renderApp() {
  renderTopHeader();
  renderSubTabBar();
  renderMainContent();
  renderBottomNav();
  renderModal();
}

function renderTopHeader() {
  const el = document.getElementById('top-header-container');
  if (!el) return;
  const team = getHumanTeam();
  const emblem = getEmblemIcon(team.emblemId);
  const borderColor = hexLongToCss(team.primaryColorHex);

  let title = 'NHL Fantasy GM';
  let subtitle = `${team.teamName} • ${team.wins}W - ${team.losses}L`;

  if (state.bottomNav === 'SCORES') {
    title = 'NHL Live Scores';
    subtitle = 'Real-time NHL game stats & multi-day schedule';
  } else if (state.bottomNav === 'PROFILE') {
    title = 'Franchise GM Office';
    subtitle = `${team.teamName} • Treasury & iOS PWA`;
  }

  el.innerHTML = `
    <div class="top-header-row">
      <div class="header-brand">
        <div class="team-emblem-badge" style="border-color: ${borderColor}">
          ${emblem}
        </div>
        <div class="header-titles">
          <h1 class="header-title">${title}</h1>
          <p class="header-subtitle">${subtitle}</p>
        </div>
      </div>
      <div class="header-actions">
        <button class="icon-btn" id="btn-ios-install" title="Add to iPhone Home Screen">📲</button>
        <button class="icon-btn" id="btn-open-settings" title="League Settings & Rules">⚙️</button>
      </div>
    </div>
  `;

  document.getElementById('btn-ios-install')?.addEventListener('click', () => {
    state.modal = { type: 'IOS_INSTALL' };
    renderModal();
  });
  document.getElementById('btn-open-settings')?.addEventListener('click', () => {
    state.modal = { type: 'SETTINGS' };
    renderModal();
  });
}

function renderSubTabBar() {
  const el = document.getElementById('sub-tab-container');
  if (!el) return;

  if (state.bottomNav !== 'FANTASY') {
    el.innerHTML = '';
    return;
  }

  const tabs = [
    { id: 'MATCH', label: 'MATCH' },
    { id: 'TEAM', label: 'TEAM' },
    { id: 'PLAYERS', label: 'PLAYERS' },
    { id: 'LEAGUE', label: 'LEAGUE' }
  ];

  el.innerHTML = `
    <div class="sub-tab-bar">
      ${tabs.map((t) => `
        <button class="sub-tab-btn ${state.fantasySubTab === t.id ? 'active' : ''}" data-subtab="${t.id}">
          ${t.label}
        </button>
      `).join('')}
    </div>
  `;

  el.querySelectorAll('.sub-tab-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.fantasySubTab = btn.getAttribute('data-subtab');
      renderApp();
    });
  });
}

function renderBottomNav() {
  const el = document.getElementById('bottom-nav-container');
  if (!el) return;

  const items = [
    { id: 'HOME', icon: '🏠', label: 'HOME' },
    { id: 'FANTASY', icon: '🛡️', label: 'FANTASY' },
    { id: 'SCORES', icon: '📅', label: 'SCORES' },
    { id: 'PROFILE', icon: '👤', label: 'PROFILE' }
  ];

  el.innerHTML = items.map((item) => `
    <button class="bottom-nav-item ${state.bottomNav === item.id ? 'active' : ''}" data-nav="${item.id}">
      <div class="bottom-nav-pill">${item.icon}</div>
      <span class="bottom-nav-label">${item.label}</span>
    </button>
  `).join('');

  el.querySelectorAll('.bottom-nav-item').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.bottomNav = btn.getAttribute('data-nav');
      renderApp();
    });
  });
}

function renderMainContent() {
  const container = document.getElementById('main-content');
  if (!container) return;

  if (state.bottomNav === 'HOME') {
    renderHomeScreen(container);
  } else if (state.bottomNav === 'SCORES') {
    renderScoresScreen(container);
  } else if (state.bottomNav === 'PROFILE') {
    renderProfileScreen(container);
  } else {
    if (state.fantasySubTab === 'MATCH') renderMatchupScreen(container);
    else if (state.fantasySubTab === 'TEAM') renderMyTeamScreen(container);
    else if (state.fantasySubTab === 'PLAYERS') renderMarketplaceScreen(container);
    else renderLeagueScreen(container);
  }
}

// --- 1. HOME SCREEN ---
function renderHomeScreen(container) {
  const userTeam = getHumanTeam();
  const roster = getTeamRoster('USER');
  const fin = calculateTeamFinancials(userTeam, roster, state.settings.economyConfig);
  const curWeek = state.settings.currentWeekNumber;
  const userMatchup = state.matchups.find(
    (m) => m.weekNumber === curWeek && (m.homeTeamId === 'USER' || m.awayTeamId === 'USER')
  ) || state.matchups[0];

  const trending = [...state.allPlayers]
    .sort((a, b) => b.overallRating - a.overallRating || (b.fantasy?.seasonPoints || 0) - (a.fantasy?.seasonPoints || 0))
    .slice(0, 10);

  const movers = [...state.allPlayers]
    .filter((p) => Math.abs(p.priceDeltaCad || 0) >= 200_000)
    .sort((a, b) => Math.abs(b.priceDeltaCad) - Math.abs(a.priceDeltaCad))
    .slice(0, 6);

  const sortedStandings = [...state.fantasyTeams].sort(
    (a, b) => (b.wins * 2 + b.ties) - (a.wins * 2 + a.ties) || b.pointsFor - a.pointsFor
  );

  container.innerHTML = `
    <div class="screen-fade" style="display:flex;flex-direction:column;gap:12px;">
      <!-- Weekly Matchup Hero Card -->
      <div class="hero-card">
        <div class="row-between" style="margin-bottom:6px;">
          <span class="badge badge-cyan">WEEK ${curWeek} MATCHUP</span>
          <span style="font-size:11px;color:var(--text-secondary);font-weight:700;">REGULAR SEASON</span>
        </div>
        <div class="row-between">
          <div>
            <div style="font-size:15px;font-weight:800;">
              ${userMatchup ? `${userMatchup.awayTeamName} vs ${userMatchup.homeTeamName}` : 'Northern Ice HC Showdown'}
            </div>
            <div style="font-size:12px;color:var(--text-secondary);margin-top:2px;">
              ${userMatchup?.isCompleted ? `Final: ${userMatchup.awayScore} - ${userMatchup.homeScore} FP` : 'Scheduled Head-to-Head Showdown'}
            </div>
          </div>
          <div class="row-center">
            <button class="btn btn-outline" id="home-btn-matchup">Matchup</button>
            <button class="btn btn-primary" id="home-btn-sim">Sim</button>
          </div>
        </div>
      </div>

      <!-- Multiplayer Draft Room Hero Entry (Core Feature Discoverability Invariant) -->
      <div class="card" style="border-color:rgba(16,185,129,0.45);background:linear-gradient(135deg,#0B2624 0%,#111C30 100%);">
        <div class="row-between">
          <div>
            <div class="row-center" style="margin-bottom:3px;">
              <span class="badge badge-emerald">CROSS-PLATFORM LIVE</span>
              <span style="font-size:11px;color:var(--text-secondary);font-weight:700;">iOS & Android</span>
            </div>
            <div style="font-size:14px;font-weight:800;">Multiplayer Draft Room & Lobby</div>
            <div style="font-size:11px;color:var(--text-secondary);">Host or join 6-char room codes with friends on iPhone or Android</div>
          </div>
          <button class="btn btn-emerald" id="home-btn-mp-draft">Draft Lobby →</button>
        </div>
      </div>

      <!-- Today's NHL Games Carousel -->
      <div>
        <div class="row-between" style="margin-bottom:8px;">
          <span style="font-size:14px;font-weight:800;">Today's NHL Games (${state.todayDate})</span>
          <span style="font-size:12px;color:var(--ice-cyan);font-weight:700;cursor:pointer;" id="home-btn-all-scores">Live NHL Feed →</span>
        </div>
        ${state.liveGames.length === 0 ? `
          <div class="card" style="text-align:center;color:var(--text-secondary);font-size:12px;">
            ${state.isSyncing ? 'Loading live NHL schedule...' : 'No NHL games scheduled today.'}
          </div>
        ` : `
          <div class="h-scroll">
            ${state.liveGames.map((g) => `
              <div class="card card-clickable home-game-card" style="min-width:190px;padding:12px;border-color:${g.isLive ? 'var(--profit-emerald)' : 'var(--border-card)'};">
                <div class="row-between" style="margin-bottom:8px;">
                  <div class="row-center" style="gap:5px;">
                    ${g.isLive ? '<span class="live-dot"></span>' : ''}
                    <span style="font-size:11px;font-weight:800;color:${g.isLive ? 'var(--profit-emerald)' : g.isFinal ? 'var(--text-secondary)' : 'var(--ice-cyan)'};">
                      ${g.isLive ? `LIVE ${g.periodLabel} ${g.clockLabel}` : g.isFinal ? g.periodLabel : formatStartTimeLocal(g.startTimeUtc)}
                    </span>
                  </div>
                  <span style="font-size:11px;color:var(--text-muted);">${g.gameDate.slice(-5)}</span>
                </div>
                <div class="row-between" style="margin-bottom:6px;">
                  <div class="row-center">
                    <img src="${g.awayLogoUrl}" class="team-logo-sm" alt="${g.awayAbbrev}" onerror="this.style.display='none'"/>
                    <span style="font-size:13px;font-weight:700;">${g.awayAbbrev} (${g.awaySog} SOG)</span>
                  </div>
                  <span style="font-size:15px;font-weight:900;">${g.awayScore}</span>
                </div>
                <div class="row-between">
                  <div class="row-center">
                    <img src="${g.homeLogoUrl}" class="team-logo-sm" alt="${g.homeAbbrev}" onerror="this.style.display='none'"/>
                    <span style="font-size:13px;font-weight:700;">${g.homeAbbrev} (${g.homeSog} SOG)</span>
                  </div>
                  <span style="font-size:15px;font-weight:900;">${g.homeScore}</span>
                </div>
              </div>
            `).join('')}
          </div>
        `}
      </div>

      <!-- League Standings Preview -->
      <div class="card card-clickable" id="home-standings-card">
        <div class="row-between" style="margin-bottom:8px;">
          <span style="font-size:14px;font-weight:800;">League Standings</span>
          <span style="font-size:12px;color:var(--ice-cyan);font-weight:700;">Full Table →</span>
        </div>
        ${sortedStandings.slice(0, 4).map((t, idx) => `
          <div class="row-between" style="padding:5px 0;font-size:13px;">
            <span style="font-weight:${t.teamId === 'USER' ? '800' : '600'};color:${t.teamId === 'USER' ? 'var(--profit-emerald)' : 'var(--text-primary)'};">
              ${idx + 1}. ${t.teamName} ${t.teamId === 'USER' ? '(You)' : ''}
            </span>
            <span style="color:var(--text-secondary);font-size:12px;">
              ${t.wins}-${t.losses}-${t.ties} • ${t.pointsFor.toFixed(1)} PF
            </span>
          </div>
        `).join('')}
      </div>

      <!-- Trending Performers -->
      <div>
        <div style="font-size:14px;font-weight:800;margin-bottom:8px;">Trending Performers</div>
        <div class="h-scroll">
          ${trending.map((p) => `
            <div class="card card-clickable home-player-card" data-pid="${p.playerId}" style="min-width:165px;padding:12px;">
              <div class="row-between" style="margin-bottom:8px;">
                <img src="${p.headshotUrl}" class="player-avatar" alt="${p.fullName}" onerror="this.src='icon.svg'"/>
                <span class="ovr-pill">${p.overallRating}</span>
              </div>
              <div style="font-size:13px;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${p.fullName}</div>
              <div style="font-size:11px;color:var(--text-secondary);margin-top:2px;">
                ${p.nhlTeamAbbrev} • ${p.position} • ${CadFormatter.formatShortNoSuffix(p.purchasePriceCad)}
              </div>
              <div style="font-size:12px;color:var(--profit-emerald);font-weight:800;margin-top:6px;">
                ${(p.fantasy?.seasonPoints || 0).toFixed(1)} Season FP
              </div>
            </div>
          `).join('')}
        </div>
      </div>

      <!-- Franchise Cap & Cash Bar -->
      <div class="card card-clickable" id="home-roster-bar">
        <div class="row-between">
          <div>
            <div style="font-size:11px;color:var(--text-secondary);font-weight:700;">Franchise Cap & Cash</div>
            <div style="font-size:13px;font-weight:800;margin-top:2px;">
              ${CadFormatter.formatCompact(fin.cashBalanceCad)} Cash • ${CadFormatter.formatCompact(fin.capSpaceRemainingCad)} Cap Room
            </div>
          </div>
          <span style="font-size:12px;color:var(--ice-cyan);font-weight:800;">Roster →</span>
        </div>
      </div>

      <!-- Market Valuation Movers -->
      <div>
        <div style="font-size:14px;font-weight:800;">Market Valuation Movers</div>
        <div style="font-size:11px;color:var(--text-secondary);margin-bottom:8px;">Transparent price adjustments from NHL performance & availability</div>
        <div class="card" style="display:flex;flex-direction:column;gap:10px;">
          ${movers.map((p) => `
            <div class="row-between card-clickable home-player-card" data-pid="${p.playerId}">
              <div class="row-center" style="min-width:0;flex:1;">
                <img src="${p.headshotUrl}" class="player-avatar" style="width:38px;height:38px;" alt="${p.fullName}" onerror="this.src='icon.svg'"/>
                <div style="min-width:0;flex:1;">
                  <div style="font-size:13px;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">
                    ${p.fullName} (${p.nhlTeamAbbrev} • ${p.position})
                  </div>
                  <div style="font-size:11px;color:var(--text-secondary);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">
                    ${p.priceChangeExplanation}
                  </div>
                </div>
              </div>
              <div style="text-align:right;flex-shrink:0;">
                <div style="font-size:13px;font-weight:800;">${CadFormatter.formatShortNoSuffix(p.purchasePriceCad)}</div>
                <div style="font-size:11px;font-weight:800;color:${p.priceDeltaCad >= 0 ? 'var(--profit-emerald)' : 'var(--alert-red)'};">
                  ${CadFormatter.formatShortNoSuffix(p.priceDeltaCad, true)}
                </div>
              </div>
            </div>
          `).join('')}
        </div>
      </div>
    </div>
  `;

  document.getElementById('home-btn-matchup')?.addEventListener('click', () => {
    state.bottomNav = 'FANTASY';
    state.fantasySubTab = 'MATCH';
    renderApp();
  });
  document.getElementById('home-btn-sim')?.addEventListener('click', () => simulateNextWeek());
  document.getElementById('home-btn-mp-draft')?.addEventListener('click', () => {
    state.bottomNav = 'FANTASY';
    state.fantasySubTab = 'LEAGUE';
    state.leagueSubTab = 'DRAFTS';
    renderApp();
  });
  document.getElementById('home-btn-all-scores')?.addEventListener('click', () => {
    state.bottomNav = 'SCORES';
    renderApp();
  });
  document.querySelectorAll('.home-game-card').forEach((c) => {
    c.addEventListener('click', () => {
      state.bottomNav = 'SCORES';
      renderApp();
    });
  });
  document.getElementById('home-standings-card')?.addEventListener('click', () => {
    state.bottomNav = 'FANTASY';
    state.fantasySubTab = 'LEAGUE';
    state.leagueSubTab = 'STANDINGS';
    renderApp();
  });
  document.getElementById('home-roster-bar')?.addEventListener('click', () => {
    state.bottomNav = 'FANTASY';
    state.fantasySubTab = 'TEAM';
    renderApp();
  });
  document.querySelectorAll('.home-player-card').forEach((c) => {
    c.addEventListener('click', () => {
      const pid = Number(c.getAttribute('data-pid'));
      openPlayerDetail(pid);
    });
  });
}

// --- 2. SCORES SCREEN (With v1.5.5 Multi-Day Date Strip) ---
function renderScoresScreen(container) {
  const isViewingToday = !state.selectedScoreDate || state.selectedScoreDate === state.todayDate;
  const activeDate = state.selectedScoreDate || state.todayDate;
  const displayGames = isViewingToday ? state.liveGames : (state.customDateGames || []);
  const currentIndex = state.gameWeek.findIndex((d) => d.date === activeDate);

  container.innerHTML = `
    <div class="screen-fade" style="display:flex;flex-direction:column;gap:10px;">
      <!-- Scoreboard Header -->
      <div class="row-between" style="padding:2px 4px;">
        <div>
          <div style="font-size:16px;font-weight:900;">
            ${isViewingToday ? "Today's NHL Scoreboard" : `NHL Scoreboard • ${formatFullDateHeader(activeDate)}`}
          </div>
          <div style="font-size:11px;color:${isViewingToday ? 'var(--text-secondary)' : 'var(--ice-cyan)'};">
            ${isViewingToday ? 'Live real-time NHL game scores & shots on goal' : 'Viewing past/upcoming schedule • Tap TODAY to return'}
          </div>
        </div>
        <button class="icon-btn" id="btn-refresh-scores" ${state.isSyncing ? 'disabled' : ''}>
          ${state.isSyncing ? '⏳' : '🔄'}
        </button>
      </div>

      <!-- Multi-Day Date Navigator Strip ([ < ] [ TUE Nov 3 (11) ] [ ● TODAY Nov 4 (4) ] [ THU Nov 5 (11) ] [ > ]) -->
      <div class="row-center" style="gap:6px;">
        <button class="icon-btn" id="btn-score-prev-day" style="width:34px;height:34px;">◀</button>
        <div class="h-scroll" style="flex:1;">
          ${state.gameWeek.map((day) => {
            const isSelected = day.date === activeDate;
            const isToday = day.date === state.todayDate;
            return `
              <button class="chip ${isSelected ? 'active' : ''}" data-score-date="${day.date}">
                ${isToday ? '<span style="width:6px;height:6px;border-radius:50%;background:var(--ice-cyan);display:inline-block;"></span>' : ''}
                <span style="font-weight:900;">${isToday ? 'TODAY' : day.dayAbbrev}</span>
                <span style="opacity:0.85;">${formatShortMonthDay(day.date)}</span>
                ${day.numberOfGames > 0 ? `<span style="background:rgba(0,0,0,0.35);padding:1px 5px;border-radius:4px;font-size:10px;">${day.numberOfGames}</span>` : ''}
              </button>
            `;
          }).join('')}
        </div>
        <button class="icon-btn" id="btn-score-next-day" style="width:34px;height:34px;">▶</button>
      </div>

      <!-- Games List -->
      ${displayGames.length === 0 ? `
        <div class="card" style="text-align:center;padding:28px 16px;">
          <div style="font-size:34px;margin-bottom:8px;">🏒</div>
          <div style="font-size:15px;font-weight:800;margin-bottom:4px;">
            ${isViewingToday ? 'No Games Scheduled Today' : `No Games Scheduled (${activeDate})`}
          </div>
          <div style="font-size:12px;color:var(--text-secondary);margin-bottom:14px;">
            Tap refresh or choose another date in the strip above to view NHL schedules and final scores.
          </div>
          <button class="btn btn-primary" id="btn-empty-sync-scores">Sync Live Scores</button>
        </div>
      ` : displayGames.map((game) => {
        const isPinned = state.pinnedGameIds.includes(game.gameId);
        return `
          <div class="card" style="border-color:${isPinned || game.isLive ? 'var(--profit-emerald)' : 'var(--border-card)'};">
            <div class="row-between" style="margin-bottom:10px;">
              <div class="row-center" style="gap:6px;">
                ${game.isLive ? '<span class="live-dot"></span>' : ''}
                <span style="font-size:11px;font-weight:900;color:${game.isLive ? 'var(--profit-emerald)' : game.isFinal ? 'var(--text-secondary)' : 'var(--ice-cyan)'};">
                  ${game.isLive ? `LIVE • ${game.periodLabel} ${game.clockLabel}` : game.isFinal ? game.periodLabel : formatStartTimeLocal(game.startTimeUtc)}
                </span>
              </div>
              <span style="font-size:11px;color:var(--text-muted);">${game.gameDate}</span>
            </div>

            <div class="row-between" style="margin-bottom:10px;">
              <div class="row-center" style="flex:1;">
                <img src="${game.awayLogoUrl}" class="team-logo-sm" style="width:34px;height:34px;" alt="${game.awayAbbrev}" onerror="this.style.display='none'"/>
                <div>
                  <div style="font-size:16px;font-weight:800;">${game.awayAbbrev}</div>
                  <div style="font-size:11px;color:var(--text-secondary);">${game.awaySog} SOG</div>
                </div>
              </div>

              <div style="font-size:24px;font-weight:900;letter-spacing:1px;">
                ${game.awayScore}  -  ${game.homeScore}
              </div>

              <div class="row-center" style="flex:1;justify-content:flex-end;">
                <div style="text-align:right;">
                  <div style="font-size:16px;font-weight:800;">${game.homeAbbrev}</div>
                  <div style="font-size:11px;color:var(--text-secondary);">${game.homeSog} SOG</div>
                </div>
                <img src="${game.homeLogoUrl}" class="team-logo-sm" style="width:34px;height:34px;" alt="${game.homeAbbrev}" onerror="this.style.display='none'"/>
              </div>
            </div>

            <div class="divider"></div>

            <div class="row-between">
              <span style="font-size:11px;color:var(--text-muted);">${game.venueName}</span>
              ${game.isFinal && !isPinned ? `
                <span class="badge" style="background:rgba(30,41,59,0.6);color:var(--text-muted);border:1px solid #334155;">Final • Over</span>
              ` : `
                <button class="chip ${isPinned ? 'active' : ''} btn-pin-game" data-gid="${game.gameId}" style="padding:4px 9px;font-size:11px;">
                  📌 ${isPinned ? 'Pinned • Live' : 'Pin Live Score'}
                </button>
              `}
            </div>
          </div>
        `;
      }).join('')}
    </div>
  `;

  document.getElementById('btn-refresh-scores')?.addEventListener('click', () => refreshLiveScores());
  document.getElementById('btn-empty-sync-scores')?.addEventListener('click', () => refreshLiveScores());

  document.getElementById('btn-score-prev-day')?.addEventListener('click', () => {
    if (currentIndex > 0) {
      selectScoreDate(state.gameWeek[currentIndex - 1].date);
    } else {
      const dt = new Date(`${activeDate}T12:00:00Z`);
      dt.setUTCDate(dt.getUTCDate() - 1);
      const prevIso = dt.toISOString().slice(0, 10);
      state.gameWeek = buildSevenDayWeek(prevIso, state.gameWeek);
      selectScoreDate(prevIso);
    }
  });

  document.getElementById('btn-score-next-day')?.addEventListener('click', () => {
    if (currentIndex >= 0 && currentIndex < state.gameWeek.length - 1) {
      selectScoreDate(state.gameWeek[currentIndex + 1].date);
    } else {
      const dt = new Date(`${activeDate}T12:00:00Z`);
      dt.setUTCDate(dt.getUTCDate() + 1);
      const nextIso = dt.toISOString().slice(0, 10);
      state.gameWeek = buildSevenDayWeek(nextIso, state.gameWeek);
      selectScoreDate(nextIso);
    }
  });

  container.querySelectorAll('[data-score-date]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const d = btn.getAttribute('data-score-date');
      selectScoreDate(d === state.todayDate ? null : d);
    });
  });

  container.querySelectorAll('.btn-pin-game').forEach((btn) => {
    btn.addEventListener('click', () => {
      const gid = Number(btn.getAttribute('data-gid'));
      const idx = state.pinnedGameIds.indexOf(gid);
      if (idx >= 0) {
        state.pinnedGameIds.splice(idx, 1);
        showToast('Unpinned game.');
      } else {
        state.pinnedGameIds.push(gid);
        showToast('Pinned game to Live Watch!');
      }
      savePersistedState();
      renderApp();
    });
  });
}

// --- 3. FANTASY -> MATCHUP SCREEN ---
function renderMatchupScreen(container) {
  const week = state.selectedMatchupWeek;
  const weekMatchups = state.matchups.filter((m) => m.weekNumber === week);
  const userMatchup = weekMatchups.find((m) => m.homeTeamId === 'USER' || m.awayTeamId === 'USER') || weekMatchups[0];

  const userRoster = getTeamRoster('USER');
  const oppId = userMatchup ? (userMatchup.homeTeamId === 'USER' ? userMatchup.awayTeamId : userMatchup.homeTeamId) : 'CPU_1';
  const oppTeam = state.fantasyTeams.find((t) => t.teamId === oppId) || state.fantasyTeams[1];
  const oppRoster = getTeamRoster(oppId);

  const userProj = computeTeamWeeklyScore('USER');
  const oppProj = computeTeamWeeklyScore(oppId);

  container.innerHTML = `
    <div class="screen-fade" style="display:flex;flex-direction:column;gap:12px;">
      <!-- Week Selector Strip -->
      <div class="h-scroll">
        ${Array.from({ length: 14 }, (_, i) => i + 1).map((w) => `
          <button class="chip ${w === week ? 'active' : ''}" data-match-week="${w}">
            Week ${w} ${w === state.settings.currentWeekNumber ? '•' : ''}
          </button>
        `).join('')}
      </div>

      <!-- Main Head-to-Head Matchup Card -->
      ${userMatchup ? `
        <div class="hero-card">
          <div class="row-between" style="margin-bottom:10px;">
            <span class="badge ${userMatchup.isCompleted ? 'badge-emerald' : 'badge-cyan'}">
              ${userMatchup.isCompleted ? 'FINAL SCORE' : `WEEK ${week} HEAD-TO-HEAD`}
            </span>
            <button class="btn btn-primary" id="btn-sim-week" style="padding:6px 12px;font-size:12px;">
              ⚡ Simulate Week ${state.settings.currentWeekNumber}
            </button>
          </div>

          <div class="row-between" style="padding:8px 0;">
            <div>
              <div style="font-size:15px;font-weight:900;color:var(--ice-cyan);">${getHumanTeam().teamName}</div>
              <div style="font-size:11px;color:var(--text-secondary);">You • ${userRoster.length} Rostered</div>
            </div>
            <div style="font-size:26px;font-weight:900;">
              ${userMatchup.isCompleted
                ? (userMatchup.homeTeamId === 'USER' ? userMatchup.homeScore : userMatchup.awayScore).toFixed(1)
                : userProj.toFixed(1)}
              <span style="font-size:13px;color:var(--text-secondary);">vs</span>
              ${userMatchup.isCompleted
                ? (userMatchup.homeTeamId === 'USER' ? userMatchup.awayScore : userMatchup.homeScore).toFixed(1)
                : oppProj.toFixed(1)}
            </div>
            <div style="text-align:right;">
              <div style="font-size:15px;font-weight:900;">${oppTeam?.teamName || 'Opponent'}</div>
              <div style="font-size:11px;color:var(--text-secondary);">CPU GM • ${oppRoster.length} Rostered</div>
            </div>
          </div>

          <div style="font-size:11px;color:var(--text-secondary);text-align:center;margin-top:6px;">
            ${userMatchup.highlightSummary}
          </div>
        </div>
      ` : ''}

      <!-- Quick Roster Readiness -->
      ${userRoster.length === 0 ? `
        <div class="card" style="border-color:var(--gold-amber);text-align:center;">
          <div style="font-size:14px;font-weight:800;margin-bottom:4px;">Your Franchise Roster is Empty!</div>
          <div style="font-size:12px;color:var(--text-secondary);margin-bottom:10px;">
            Run an 8-Team Snake Draft or sign NHL players from the Marketplace to compete in weekly matchups.
          </div>
          <div class="row-center" style="justify-content:center;">
            <button class="btn btn-primary" id="match-btn-start-draft">Start Snake Draft</button>
            <button class="btn btn-outline" id="match-btn-market">Browse Players</button>
          </div>
        </div>
      ` : ''}

      <!-- League-Wide Week Matchups -->
      <div>
        <div style="font-size:14px;font-weight:800;margin-bottom:8px;">Week ${week} League Scoreboard</div>
        <div style="display:flex;flex-direction:column;gap:8px;">
          ${weekMatchups.map((m) => `
            <div class="card">
              <div class="row-between">
                <div>
                  <div style="font-size:13px;font-weight:800;color:${m.awayTeamId === 'USER' ? 'var(--ice-cyan)' : 'var(--text-primary)'};">
                    ${m.awayTeamName}
                  </div>
                  <div style="font-size:13px;font-weight:800;margin-top:4px;color:${m.homeTeamId === 'USER' ? 'var(--ice-cyan)' : 'var(--text-primary)'};">
                    @ ${m.homeTeamName}
                  </div>
                </div>
                <div style="text-align:right;">
                  <div style="font-size:14px;font-weight:900;">${m.isCompleted ? m.awayScore.toFixed(1) : '--'}</div>
                  <div style="font-size:14px;font-weight:900;margin-top:4px;">${m.isCompleted ? m.homeScore.toFixed(1) : '--'}</div>
                </div>
              </div>
            </div>
          `).join('')}
        </div>
      </div>
    </div>
  `;

  container.querySelectorAll('[data-match-week]').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.selectedMatchupWeek = Number(btn.getAttribute('data-match-week'));
      renderApp();
    });
  });

  document.getElementById('btn-sim-week')?.addEventListener('click', () => simulateNextWeek());
  document.getElementById('match-btn-start-draft')?.addEventListener('click', () => {
    state.fantasySubTab = 'LEAGUE';
    state.leagueSubTab = 'DRAFTS';
    renderApp();
  });
  document.getElementById('match-btn-market')?.addEventListener('click', () => {
    state.fantasySubTab = 'PLAYERS';
    renderApp();
  });
}

// --- 4. FANTASY -> TEAM (MY ROSTER & LINEUP SLOTS) ---
function renderMyTeamScreen(container) {
  const team = getHumanTeam();
  const roster = getTeamRoster('USER');
  const fin = calculateTeamFinancials(team, roster, state.settings.economyConfig);

  const slotMap = {};
  roster.forEach((p) => {
    if (p.lineupSlot && p.lineupSlot !== 'UNASSIGNED') {
      slotMap[p.lineupSlot] = p;
    }
  });
  const unassigned = roster.filter((p) => !p.lineupSlot || p.lineupSlot === 'UNASSIGNED');

  container.innerHTML = `
    <div class="screen-fade" style="display:flex;flex-direction:column;gap:12px;">
      <!-- Franchise Treasury & Cap Summary Card -->
      <div class="card">
        <div class="row-between" style="margin-bottom:10px;">
          <div>
            <div style="font-size:15px;font-weight:900;">${team.teamName} (${team.abbreviation})</div>
            <div style="font-size:11px;color:var(--text-secondary);">Active Roster: ${fin.rosterCount}/${fin.maxRosterSize} Players</div>
          </div>
          <div class="row-center">
            <button class="btn btn-outline" id="btn-customize-team" style="padding:6px 10px;font-size:11px;">🎨 Identity</button>
            <button class="btn btn-primary" id="btn-auto-lineup" style="padding:6px 10px;font-size:11px;">✨ Auto-Lines</button>
          </div>
        </div>
        <div class="grid-3">
          <div style="background:#0B1220;padding:8px;border-radius:9px;">
            <div style="font-size:10px;color:var(--text-secondary);">GM CASH</div>
            <div style="font-size:13px;font-weight:800;color:var(--profit-emerald);">${CadFormatter.formatShortNoSuffix(fin.cashBalanceCad)}</div>
          </div>
          <div style="background:#0B1220;padding:8px;border-radius:9px;">
            <div style="font-size:10px;color:var(--text-secondary);">CAP HIT</div>
            <div style="font-size:13px;font-weight:800;color:var(--ice-cyan);">${CadFormatter.formatShortNoSuffix(fin.totalCapHitCad)}</div>
          </div>
          <div style="background:#0B1220;padding:8px;border-radius:9px;">
            <div style="font-size:10px;color:var(--text-secondary);">CAP SPACE</div>
            <div style="font-size:13px;font-weight:800;">${CadFormatter.formatShortNoSuffix(fin.capSpaceRemainingCad)}</div>
          </div>
        </div>
      </div>

      ${roster.length === 0 ? `
        <div class="card" style="text-align:center;padding:24px;">
          <div style="font-size:32px;margin-bottom:8px;">🛡️</div>
          <div style="font-size:15px;font-weight:800;margin-bottom:4px;">No Players Under Contract</div>
          <div style="font-size:12px;color:var(--text-secondary);margin-bottom:14px;">
            Sign free agents from the NHL Player Marketplace or launch a Snake Draft to build your lines.
          </div>
          <div class="row-center" style="justify-content:center;">
            <button class="btn btn-primary" id="team-btn-go-players">Open Player Market</button>
            <button class="btn btn-outline" id="team-btn-go-draft">Snake Draft Room</button>
          </div>
        </div>
      ` : ''}

      <!-- Lineup Groups (Forward Lines 1-4, Defense Pairs 1-3, Goalies, Bench, IR) -->
      ${LINEUP_GROUPS.map((grp) => `
        <div class="card" style="padding:12px;">
          <div style="font-size:12px;font-weight:900;color:var(--ice-cyan);margin-bottom:8px;letter-spacing:0.5px;">
            ${grp.title.toUpperCase()}
          </div>
          <div style="display:flex;flex-direction:column;gap:6px;">
            ${grp.slots.map((slotCode) => {
              const slotMeta = LINEUP_SLOTS[slotCode];
              const p = slotMap[slotCode];
              return `
                <div class="row-between card-clickable slot-row" data-slot="${slotCode}" data-pid="${p ? p.playerId : ''}" style="background:#0E1628;padding:8px 10px;border-radius:10px;border:1px solid var(--border-subtle);">
                  <div class="row-center" style="min-width:0;flex:1;">
                    <span class="badge badge-cyan" style="min-width:40px;justify-content:center;">${slotCode}</span>
                    ${p ? `
                      <img src="${p.headshotUrl}" class="player-avatar" style="width:32px;height:32px;" alt="${p.fullName}" onerror="this.src='icon.svg'"/>
                      <div style="min-width:0;flex:1;">
                        <div style="font-size:13px;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">
                          ${p.fullName}
                          ${p.injurySeverity !== 'HEALTHY' ? `<span class="badge badge-red" style="font-size:9px;padding:1px 4px;">${p.injurySeverity}</span>` : ''}
                        </div>
                        <div style="font-size:11px;color:var(--text-secondary);">
                          ${p.nhlTeamAbbrev} • ${p.position} • Cap ${CadFormatter.formatShortNoSuffix(p.capHitCad)}
                        </div>
                      </div>
                    ` : `
                      <span style="font-size:12px;color:var(--text-muted);font-style:italic;">Empty ${slotMeta.label} — Tap to assign</span>
                    `}
                  </div>
                  ${p ? `
                    <div style="text-align:right;flex-shrink:0;">
                      <span class="ovr-pill">${p.overallRating}</span>
                      <div style="font-size:11px;color:var(--profit-emerald);font-weight:800;margin-top:2px;">
                        ${(p.fantasy?.seasonPoints || 0).toFixed(1)} FP
                      </div>
                    </div>
                  ` : ''}
                </div>
              `;
            }).join('')}
          </div>
        </div>
      `).join('')}

      ${unassigned.length > 0 ? `
        <div class="card" style="padding:12px;">
          <div style="font-size:12px;font-weight:900;color:var(--gold-amber);margin-bottom:8px;">UNASSIGNED PLAYERS (${unassigned.length})</div>
          <div style="display:flex;flex-direction:column;gap:6px;">
            ${unassigned.map((p) => `
              <div class="row-between card-clickable unassigned-row" data-pid="${p.playerId}" style="background:#0E1628;padding:8px 10px;border-radius:10px;">
                <div class="row-center">
                  <img src="${p.headshotUrl}" class="player-avatar" style="width:32px;height:32px;" alt="${p.fullName}" onerror="this.src='icon.svg'"/>
                  <div>
                    <div style="font-size:13px;font-weight:800;">${p.fullName} (${p.position} • ${p.nhlTeamAbbrev})</div>
                    <div style="font-size:11px;color:var(--text-secondary);">Tap to assign slot or view contract</div>
                  </div>
                </div>
                <span class="ovr-pill">${p.overallRating}</span>
              </div>
            `).join('')}
          </div>
        </div>
      ` : ''}
    </div>
  `;

  document.getElementById('btn-auto-lineup')?.addEventListener('click', () => {
    const opt = autoOptimizeLineup(roster, state.settings.scoringWeights);
    Object.entries(opt).forEach(([pid, sc]) => {
      if (state.ownership[pid]) state.ownership[pid].lineupSlot = sc;
    });
    enrichAllPlayers();
    savePersistedState();
    showToast('Optimized starting lines, defense pairs, goalies & IR!');
    renderApp();
  });

  document.getElementById('btn-customize-team')?.addEventListener('click', () => {
    state.modal = { type: 'CUSTOMIZE_TEAM' };
    renderModal();
  });

  document.getElementById('team-btn-go-players')?.addEventListener('click', () => {
    state.fantasySubTab = 'PLAYERS';
    renderApp();
  });
  document.getElementById('team-btn-go-draft')?.addEventListener('click', () => {
    state.fantasySubTab = 'LEAGUE';
    state.leagueSubTab = 'DRAFTS';
    renderApp();
  });

  container.querySelectorAll('.slot-row').forEach((row) => {
    row.addEventListener('click', () => {
      const pid = Number(row.getAttribute('data-pid'));
      if (pid) {
        openPlayerDetail(pid);
      } else {
        state.fantasySubTab = 'PLAYERS';
        renderApp();
      }
    });
  });

  container.querySelectorAll('.unassigned-row').forEach((row) => {
    row.addEventListener('click', () => {
      const pid = Number(row.getAttribute('data-pid'));
      if (pid) openPlayerDetail(pid);
    });
  });
}

// --- 5. FANTASY -> PLAYERS (NHL MARKETPLACE) ---
function renderMarketplaceScreen(container) {
  const query = state.marketSearch.trim().toLowerCase();
  let list = state.allPlayers.filter((p) => {
    if (state.marketPosition !== 'ALL' && p.position !== state.marketPosition) return false;
    if (state.marketCategory === 'WATCHLIST' && !p.isWatchlisted) return false;
    if (state.marketCategory === 'BREAKOUTS' && !p.fantasy?.isBreakoutCandidate) return false;
    if (state.marketCategory === 'FREE_AGENTS' && p.ownerTeamId) return false;
    if (query) {
      return p.fullName.toLowerCase().includes(query) || p.nhlTeamAbbrev.toLowerCase().includes(query);
    }
    return true;
  });

  if (state.marketCategory === 'BEST_VALUE') {
    list.sort((a, b) => (b.fantasy?.valuePerMillionCad || 0) - (a.fantasy?.valuePerMillionCad || 0));
  } else if (state.marketSort === 'FP') {
    list.sort((a, b) => (b.fantasy?.seasonPoints || 0) - (a.fantasy?.seasonPoints || 0));
  } else if (state.marketSort === 'PRICE_DESC') {
    list.sort((a, b) => b.purchasePriceCad - a.purchasePriceCad);
  } else if (state.marketSort === 'PRICE_ASC') {
    list.sort((a, b) => a.purchasePriceCad - b.purchasePriceCad);
  } else if (state.marketSort === 'FPPG') {
    list.sort((a, b) => (b.fantasy?.pointsPerGame || 0) - (a.fantasy?.pointsPerGame || 0));
  } else {
    list.sort((a, b) => b.overallRating - a.overallRating || (b.fantasy?.seasonPoints || 0) - (a.fantasy?.seasonPoints || 0));
  }

  const visiblePlayers = list.slice(0, 75);

  container.innerHTML = `
    <div class="screen-fade" style="display:flex;flex-direction:column;gap:10px;">
      <!-- Search Bar -->
      <div class="row-center">
        <input type="text" id="market-search-input" class="input-field" placeholder="Search 790+ NHL players or team (e.g. McDavid, EDM)..." value="${state.marketSearch}"/>
        ${state.marketSearch ? `<button class="icon-btn" id="market-clear-search">✕</button>` : ''}
      </div>

      <!-- Category Tabs -->
      <div class="h-scroll">
        ${[
          { id: 'ALL', label: 'All Players' },
          { id: 'FREE_AGENTS', label: 'Available' },
          { id: 'BEST_VALUE', label: '💎 Best Value' },
          { id: 'BREAKOUTS', label: '🚀 Breakouts' },
          { id: 'WATCHLIST', label: `★ Watchlist (${state.watchlist.length})` }
        ].map((c) => `
          <button class="chip ${state.marketCategory === c.id ? 'active' : ''}" data-mcat="${c.id}">${c.label}</button>
        `).join('')}
      </div>

      <!-- Position & Sort Row -->
      <div class="row-between">
        <div class="h-scroll">
          ${['ALL', 'C', 'LW', 'RW', 'D', 'G'].map((pos) => `
            <button class="chip ${state.marketPosition === pos ? 'active' : ''}" data-mpos="${pos}" style="padding:5px 10px;">${pos}</button>
          `).join('')}
        </div>
        <select id="market-sort-select" class="input-field" style="width:auto;padding:5px 8px;font-size:12px;">
          <option value="OVR" ${state.marketSort === 'OVR' ? 'selected' : ''}>Sort: OVR</option>
          <option value="FP" ${state.marketSort === 'FP' ? 'selected' : ''}>Sort: Season FP</option>
          <option value="FPPG" ${state.marketSort === 'FPPG' ? 'selected' : ''}>Sort: FP/GP</option>
          <option value="PRICE_DESC" ${state.marketSort === 'PRICE_DESC' ? 'selected' : ''}>Price: High</option>
          <option value="PRICE_ASC" ${state.marketSort === 'PRICE_ASC' ? 'selected' : ''}>Price: Low</option>
        </select>
      </div>

      <div style="font-size:11px;color:var(--text-secondary);">
        Showing ${visiblePlayers.length} of ${list.length} matching NHL players
      </div>

      <!-- Player Rows -->
      <div style="display:flex;flex-direction:column;gap:8px;">
        ${visiblePlayers.map((p) => {
          const isOwnedByUser = p.ownerTeamId === 'USER';
          const isOwnedByOther = p.ownerTeamId && p.ownerTeamId !== 'USER';
          return `
            <div class="player-row card-clickable market-player-row" data-pid="${p.playerId}">
              <div class="row-center" style="min-width:0;flex:1;">
                <img src="${p.headshotUrl}" class="player-avatar" alt="${p.fullName}" loading="lazy" onerror="this.src='icon.svg'"/>
                <div style="min-width:0;flex:1;">
                  <div class="row-center" style="gap:5px;">
                    <span class="ovr-pill">${p.overallRating}</span>
                    <span style="font-size:14px;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">
                      ${p.fullName}
                    </span>
                    ${p.isWatchlisted ? '<span style="color:var(--gold-amber);">★</span>' : ''}
                  </div>
                  <div style="font-size:11px;color:var(--text-secondary);margin-top:2px;">
                    ${p.nhlTeamAbbrev} • ${p.position} • Age ${p.age} • Cap ${CadFormatter.formatShortNoSuffix(p.capHitCad)}
                    ${p.injurySeverity !== 'HEALTHY' ? `<span class="badge badge-red" style="font-size:9px;padding:1px 4px;margin-left:4px;">${p.injurySeverity}</span>` : ''}
                  </div>
                  <div style="font-size:11px;color:var(--profit-emerald);font-weight:700;margin-top:2px;">
                    ${(p.fantasy?.seasonPoints || 0).toFixed(1)} FP (${(p.fantasy?.pointsPerGame || 0).toFixed(1)}/GP)
                    ${p.position === 'G' ? `• ${p.wins}W ${(p.savePct * 100).toFixed(1)}% SV%` : `• ${p.goals}G ${p.assists}A ${p.shots}SOG`}
                  </div>
                </div>
              </div>

              <div style="text-align:right;flex-shrink:0;">
                <div style="font-size:13px;font-weight:900;">${CadFormatter.formatShortNoSuffix(p.purchasePriceCad)}</div>
                ${isOwnedByUser ? `
                  <span class="badge badge-emerald" style="margin-top:4px;">On Roster</span>
                ` : isOwnedByOther ? `
                  <span class="badge" style="background:#1E293B;color:var(--text-muted);margin-top:4px;">Signed</span>
                ` : `
                  <button class="btn btn-primary btn-quick-buy" data-buy-pid="${p.playerId}" style="padding:4px 10px;font-size:11px;margin-top:4px;">
                    + Sign
                  </button>
                `}
              </div>
            </div>
          `;
        }).join('')}
      </div>
    </div>
  `;

  const searchInput = document.getElementById('market-search-input');
  searchInput?.addEventListener('input', (e) => {
    state.marketSearch = e.target.value;
    renderMainContent();
    const newInput = document.getElementById('market-search-input');
    if (newInput) {
      newInput.focus();
      newInput.setSelectionRange(newInput.value.length, newInput.value.length);
    }
  });

  document.getElementById('market-clear-search')?.addEventListener('click', () => {
    state.marketSearch = '';
    renderApp();
  });

  container.querySelectorAll('[data-mcat]').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.marketCategory = btn.getAttribute('data-mcat');
      renderApp();
    });
  });

  container.querySelectorAll('[data-mpos]').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.marketPosition = btn.getAttribute('data-mpos');
      renderApp();
    });
  });

  document.getElementById('market-sort-select')?.addEventListener('change', (e) => {
    state.marketSort = e.target.value;
    renderApp();
  });

  container.querySelectorAll('.btn-quick-buy').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const pid = Number(btn.getAttribute('data-buy-pid'));
      buyPlayer(pid);
    });
  });

  container.querySelectorAll('.market-player-row').forEach((row) => {
    row.addEventListener('click', () => {
      const pid = Number(row.getAttribute('data-pid'));
      openPlayerDetail(pid);
    });
  });
}

// --- 6. FANTASY -> LEAGUE HUB (DRAFTS, STANDINGS, TRANSACTIONS, NHL STATS, NEWS) ---
function renderLeagueScreen(container) {
  const subTabs = [
    { id: 'DRAFTS', label: '🎯 DRAFTS & LOBBY' },
    { id: 'STANDINGS', label: '🏆 STANDINGS' },
    { id: 'TRANSACTIONS', label: '💸 LEDGER' },
    { id: 'NHL_STATS', label: '🏒 NHL 32 TEAMS' },
    { id: 'NEWS', label: '📰 NHL NEWS' }
  ];

  container.innerHTML = `
    <div class="screen-fade" style="display:flex;flex-direction:column;gap:12px;">
      <div class="h-scroll">
        ${subTabs.map((t) => `
          <button class="chip ${state.leagueSubTab === t.id ? 'active' : ''}" data-lsub="${t.id}">
            ${t.label}
          </button>
        `).join('')}
      </div>
      <div id="league-sub-body" style="display:flex;flex-direction:column;gap:12px;"></div>
    </div>
  `;

  container.querySelectorAll('[data-lsub]').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.leagueSubTab = btn.getAttribute('data-lsub');
      renderApp();
    });
  });

  const body = document.getElementById('league-sub-body');
  if (!body) return;

  if (state.leagueSubTab === 'DRAFTS') renderDraftsAndMultiplayerSubTab(body);
  else if (state.leagueSubTab === 'STANDINGS') renderStandingsSubTab(body);
  else if (state.leagueSubTab === 'TRANSACTIONS') renderTransactionsSubTab(body);
  else if (state.leagueSubTab === 'NHL_STATS') renderNhlTeamsSubTab(body);
  else renderNewsSubTab(body);
}

function renderDraftsAndMultiplayerSubTab(body) {
  const mp = state.mp;
  const isMpActive = mp.role !== 'NONE' && mp.roomCode;
  const ds = state.draftSession;
  const isDraftActive = ds && ds.status === 'IN_PROGRESS';
  const currentSlot = isDraftActive ? ds.order[ds.currentPickIndex] : null;
  const availableDraftPlayers = [...state.allPlayers]
    .filter((p) => !state.ownership[p.playerId])
    .sort((a, b) => b.overallRating - a.overallRating)
    .slice(0, 35);

  body.innerHTML = `
    <!-- Cross-Platform Multiplayer Room Card -->
    <div class="hero-card">
      <div class="row-between" style="margin-bottom:8px;">
        <span class="badge badge-emerald">ENCRYPTED MQTT-OVER-WSS</span>
        <span style="font-size:11px;color:var(--text-secondary);font-weight:700;">
          ${mp.connectionState}
        </span>
      </div>
      <div style="font-size:16px;font-weight:900;margin-bottom:4px;">
        Cross-Platform Multiplayer Draft & League Room
      </div>
      <div style="font-size:12px;color:var(--text-secondary);margin-bottom:12px;">
        Compatible with NHL Fantasy GM on Android & iOS. Uses end-to-end AES-256-GCM encryption keyed from your 6-character Room Code.
      </div>

      ${!isMpActive ? `
        <div class="grid-2" style="margin-bottom:10px;">
          <input type="text" id="mp-gm-name-input" class="input-field" placeholder="Your GM Name" value="${mp.localGmName}"/>
          <button class="btn btn-emerald" id="btn-host-mp-room">🌐 Host New Room</button>
        </div>
        <div class="row-center">
          <input type="text" id="mp-join-code-input" class="input-field" placeholder="Enter 6-char Room Code (e.g. K9M-4P2)" maxlength="12" style="text-transform:uppercase;"/>
          <button class="btn btn-primary" id="btn-join-mp-room">Join Room</button>
        </div>
      ` : `
        <div class="card" style="background:#0B1220;margin-bottom:10px;">
          <div class="row-between">
            <div>
              <div style="font-size:11px;color:var(--text-secondary);">ACTIVE ROOM INVITE CODE (${mp.role})</div>
              <div style="font-size:22px;font-weight:900;color:var(--ice-cyan);letter-spacing:2px;">
                ${RoomCrypto.formatInviteCodeForDisplay(mp.roomCode)}
              </div>
            </div>
            <div class="row-center">
              <button class="btn btn-outline" id="btn-copy-room-code" style="padding:6px 10px;font-size:11px;">📋 Copy Code</button>
              <button class="btn btn-danger" id="btn-leave-mp-room" style="padding:6px 10px;font-size:11px;">Leave</button>
            </div>
          </div>

          <div class="divider"></div>
          <div style="font-size:12px;font-weight:800;margin-bottom:6px;">
            Lobby GMs (${mp.participants.length}/8 Humans • Remaining filled by CPU)
          </div>
          ${mp.participants.map((p) => `
            <div class="row-between" style="font-size:12px;padding:3px 0;">
              <span>${p.isHost ? '👑' : '🎮'} <b>${p.gmName}</b> — ${p.teamName} (${p.abbreviation})</span>
              <span class="badge badge-emerald">READY</span>
            </div>
          `).join('')}

          <div class="divider"></div>
          <div style="max-height:110px;overflow-y:auto;font-size:11px;display:flex;flex-direction:column;gap:4px;margin-bottom:8px;">
            ${mp.chatMessages.map((m) => `
              <div style="color:${m.isSystemEvent ? 'var(--ice-cyan)' : 'var(--text-primary)'};">
                <b>[${m.senderGmName}]:</b> ${m.text}
              </div>
            `).join('')}
          </div>
          <div class="row-center">
            <input type="text" id="mp-chat-input" class="input-field" placeholder="Send message to lobby..." style="padding:7px 10px;font-size:12px;"/>
            <button class="btn btn-primary" id="btn-send-mp-chat" style="padding:7px 12px;font-size:12px;">Send</button>
          </div>
        </div>
      `}
    </div>

    <!-- Snake Draft Room -->
    <div class="card">
      <div class="row-between" style="margin-bottom:8px;">
        <div>
          <div style="font-size:15px;font-weight:900;">8-Team Snake Draft Room</div>
          <div style="font-size:11px;color:var(--text-secondary);">
            ${isDraftActive
              ? `Round ${currentSlot?.roundNumber} • Pick #${currentSlot?.overallPick} (${currentSlot?.teamId === 'USER' ? 'YOUR PICK' : currentSlot?.teamId})`
              : ds?.status === 'COMPLETED'
              ? 'Draft Completed • All rosters assigned'
              : 'Draft 19 rounds (3C, 3LW, 3RW, 5D, 2G, 3 Bench) under $95.50M Cap'}
          </div>
        </div>
        <div class="row-center">
          ${isDraftActive ? `
            <button class="btn btn-primary" id="btn-draft-autopick" style="padding:6px 10px;font-size:11px;">⚡ Auto-Pick</button>
          ` : ''}
          <button class="btn btn-outline" id="btn-start-snake-draft" style="padding:6px 10px;font-size:11px;">
            ${isDraftActive ? 'Restart Draft' : 'Start Snake Draft'}
          </button>
        </div>
      </div>

      ${isDraftActive ? `
        <div style="font-size:12px;font-weight:800;color:var(--ice-cyan);margin:10px 0 6px 0;">
          Best Available Players on Draft Board (Tap to Draft)
        </div>
        <div style="display:flex;flex-direction:column;gap:6px;max-height:320px;overflow-y:auto;">
          ${availableDraftPlayers.map((p) => `
            <div class="row-between card-clickable draft-pick-row" data-draft-pid="${p.playerId}" style="background:#0B1220;padding:8px 10px;border-radius:10px;border:1px solid var(--border-subtle);">
              <div class="row-center">
                <span class="ovr-pill">${p.overallRating}</span>
                <img src="${p.headshotUrl}" class="player-avatar" style="width:32px;height:32px;" alt="${p.fullName}" onerror="this.src='icon.svg'"/>
                <div>
                  <div style="font-size:13px;font-weight:800;">${p.fullName} (${p.position} • ${p.nhlTeamAbbrev})</div>
                  <div style="font-size:11px;color:var(--text-secondary);">
                    ${(p.fantasy?.seasonPoints || 0).toFixed(1)} FP • Cap ${CadFormatter.formatShortNoSuffix(p.capHitCad)}
                  </div>
                </div>
              </div>
              <button class="btn btn-primary" style="padding:5px 10px;font-size:11px;">Draft</button>
            </div>
          `).join('')}
        </div>
      ` : ''}

      ${state.draftPicks.length > 0 ? `
        <div class="divider"></div>
        <div style="font-size:12px;font-weight:800;margin-bottom:6px;">Recent Draft Picks (${state.draftPicks.length})</div>
        <div style="display:flex;flex-direction:column;gap:5px;max-height:220px;overflow-y:auto;">
          ${state.draftPicks.slice(0, 24).map((pk) => `
            <div class="row-between" style="font-size:12px;padding:4px 6px;background:#0B1220;border-radius:7px;">
              <span>
                <b style="color:var(--ice-cyan);">#${pk.overallPick} (R${pk.roundNumber})</b>
                ${pk.playerName} (${pk.positionCode} • ${pk.nhlTeamAbbrev})
              </span>
              <span style="color:${pk.isHumanPick ? 'var(--profit-emerald)' : 'var(--text-secondary)'};font-weight:700;">
                ${pk.fantasyTeamName}
              </span>
            </div>
          `).join('')}
        </div>
      ` : ''}
    </div>
  `;

  document.getElementById('btn-host-mp-room')?.addEventListener('click', () => {
    const gmVal = document.getElementById('mp-gm-name-input')?.value;
    if (gmVal) state.mp.localGmName = gmVal.trim();
    savePersistedState();
    hostMultiplayerRoom();
  });

  document.getElementById('btn-join-mp-room')?.addEventListener('click', () => {
    const gmVal = document.getElementById('mp-gm-name-input')?.value;
    if (gmVal) state.mp.localGmName = gmVal.trim();
    const codeVal = document.getElementById('mp-join-code-input')?.value || '';
    savePersistedState();
    joinMultiplayerRoom(codeVal);
  });

  document.getElementById('btn-copy-room-code')?.addEventListener('click', () => {
    const formatted = RoomCrypto.formatInviteCodeForDisplay(state.mp.roomCode);
    navigator.clipboard?.writeText(formatted);
    showToast(`Copied Room Code ${formatted} to clipboard!`);
  });

  document.getElementById('btn-leave-mp-room')?.addEventListener('click', () => leaveMultiplayerRoom());

  document.getElementById('btn-send-mp-chat')?.addEventListener('click', () => {
    const inp = document.getElementById('mp-chat-input');
    if (inp && inp.value) {
      sendMultiplayerChat(inp.value);
      inp.value = '';
    }
  });

  document.getElementById('btn-start-snake-draft')?.addEventListener('click', () => startNewSnakeDraft());
  document.getElementById('btn-draft-autopick')?.addEventListener('click', () => autoPickUserTurn());

  body.querySelectorAll('.draft-pick-row').forEach((row) => {
    row.addEventListener('click', () => {
      const pid = Number(row.getAttribute('data-draft-pid'));
      makeUserDraftPick(pid);
    });
  });
}

function renderStandingsSubTab(body) {
  const sorted = [...state.fantasyTeams].sort(
    (a, b) => (b.wins * 2 + b.ties) - (a.wins * 2 + a.ties) || b.pointsFor - a.pointsFor
  );

  body.innerHTML = `
    <div class="card">
      <div style="font-size:15px;font-weight:900;margin-bottom:10px;">Fantasy League Standings (Season ${state.settings.currentSeasonNumber})</div>
      <div style="display:flex;flex-direction:column;gap:8px;">
        ${sorted.map((t, idx) => {
          const r = getTeamRoster(t.teamId);
          const fin = calculateTeamFinancials(t, r, state.settings.economyConfig);
          return `
            <div class="row-between" style="padding:10px;background:#0B1220;border-radius:10px;border:1px solid ${t.teamId === 'USER' ? 'var(--ice-cyan)' : 'var(--border-subtle)'};">
              <div class="row-center">
                <span style="font-size:14px;font-weight:900;width:20px;">${idx + 1}</span>
                <div class="team-emblem-badge" style="width:30px;height:30px;font-size:15px;border-color:${hexLongToCss(t.primaryColorHex)};">
                  ${getEmblemIcon(t.emblemId)}
                </div>
                <div>
                  <div style="font-size:13px;font-weight:800;color:${t.teamId === 'USER' ? 'var(--ice-cyan)' : 'var(--text-primary)'};">
                    ${t.teamName} (${t.abbreviation})
                  </div>
                  <div style="font-size:11px;color:var(--text-secondary);">
                    Net Worth ${CadFormatter.formatShortNoSuffix(fin.franchiseNetWorthCad)} • ${r.length} Players
                  </div>
                </div>
              </div>
              <div style="text-align:right;">
                <div style="font-size:13px;font-weight:900;">${t.wins}-${t.losses}-${t.ties}</div>
                <div style="font-size:11px;color:var(--profit-emerald);">${t.pointsFor.toFixed(1)} PF</div>
              </div>
            </div>
          `;
        }).join('')}
      </div>
    </div>
  `;
}

function renderTransactionsSubTab(body) {
  body.innerHTML = `
    <div class="card">
      <div style="font-size:15px;font-weight:900;margin-bottom:10px;">Franchise Financial Ledger</div>
      <div style="display:flex;flex-direction:column;gap:8px;">
        ${state.transactions.map((tx) => `
          <div class="row-between" style="padding:9px;background:#0B1220;border-radius:10px;">
            <div style="min-width:0;flex:1;">
              <div style="font-size:13px;font-weight:800;">${tx.title}</div>
              <div style="font-size:11px;color:var(--text-secondary);margin-top:2px;">${tx.details}</div>
            </div>
            <div style="text-align:right;flex-shrink:0;">
              <div style="font-size:13px;font-weight:900;color:${tx.cashDeltaCad >= 0 ? 'var(--profit-emerald)' : 'var(--alert-red)'};">
                ${CadFormatter.formatShortNoSuffix(tx.cashDeltaCad, true)}
              </div>
              <div style="font-size:10px;color:var(--text-muted);">
                Bal: ${CadFormatter.formatShortNoSuffix(tx.balanceAfterCad)}
              </div>
            </div>
          </div>
        `).join('')}
      </div>
    </div>
  `;
}

function renderNhlTeamsSubTab(body) {
  const sorted = [...state.nhlTeams].sort((a, b) => b.points - a.points || b.wins - a.wins);
  body.innerHTML = `
    <div class="card">
      <div style="font-size:15px;font-weight:900;margin-bottom:10px;">Official 32-Team NHL Standings</div>
      <div style="display:flex;flex-direction:column;gap:6px;">
        ${sorted.map((t, idx) => `
          <div class="row-between" style="padding:8px 10px;background:#0B1220;border-radius:9px;">
            <div class="row-center">
              <span style="font-size:12px;color:var(--text-muted);width:18px;">${idx + 1}</span>
              <img src="${t.logoLightUrl}" class="team-logo-sm" alt="${t.abbrev}" onerror="this.style.display='none'"/>
              <div>
                <div style="font-size:13px;font-weight:800;">${t.fullName}</div>
                <div style="font-size:10px;color:var(--text-secondary);">${t.divisionName} • GP ${t.gamesPlayed}</div>
              </div>
            </div>
            <div style="text-align:right;">
              <div style="font-size:13px;font-weight:900;color:var(--ice-cyan);">${t.points} PTS</div>
              <div style="font-size:11px;color:var(--text-secondary);">${t.wins}-${t.losses}-${t.otLosses}</div>
            </div>
          </div>
        `).join('')}
      </div>
    </div>
  `;
}

function renderNewsSubTab(body) {
  body.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:8px;">
      ${state.newsHeadlines.length === 0 ? `
        <div class="card" style="text-align:center;color:var(--text-secondary);">Loading live ESPN NHL headlines...</div>
      ` : state.newsHeadlines.map((n) => `
        <a href="${n.webUrl}" target="_blank" rel="noopener" class="card card-clickable" style="text-decoration:none;color:inherit;">
          <div class="row-between" style="margin-bottom:4px;">
            <span class="badge badge-cyan">${n.sourceLabel}</span>
            <span style="font-size:10px;color:var(--text-muted);">${n.publishedAt ? n.publishedAt.slice(0, 10) : ''}</span>
          </div>
          <div style="font-size:14px;font-weight:800;margin-bottom:4px;">${n.headline}</div>
          <div style="font-size:12px;color:var(--text-secondary);">${n.description}</div>
        </a>
      `).join('')}
    </div>
  `;
}

// --- 7. PROFILE SCREEN ---
function renderProfileScreen(container) {
  const team = getHumanTeam();
  const roster = getTeamRoster('USER');
  const fin = calculateTeamFinancials(team, roster, state.settings.economyConfig);

  container.innerHTML = `
    <div class="screen-fade" style="display:flex;flex-direction:column;gap:12px;">
      <!-- GM Franchise Card -->
      <div class="hero-card">
        <div class="row-center" style="gap:12px;margin-bottom:12px;">
          <div class="team-emblem-badge" style="width:52px;height:52px;font-size:26px;border-color:${hexLongToCss(team.primaryColorHex)};">
            ${getEmblemIcon(team.emblemId)}
          </div>
          <div>
            <div style="font-size:18px;font-weight:900;">${team.teamName} (${team.abbreviation})</div>
            <div style="font-size:12px;color:var(--text-secondary);">
              GM ${state.mp.localGmName} • Record: ${team.wins}-${team.losses}-${team.ties} (${team.pointsFor.toFixed(1)} PF)
            </div>
          </div>
        </div>
        <div class="grid-2">
          <div style="background:rgba(11,18,32,0.75);padding:10px;border-radius:10px;">
            <div style="font-size:10px;color:var(--text-secondary);">FRANCHISE NET WORTH</div>
            <div style="font-size:15px;font-weight:900;color:var(--profit-emerald);">${CadFormatter.formatCompact(fin.franchiseNetWorthCad)}</div>
          </div>
          <div style="background:rgba(11,18,32,0.75);padding:10px;border-radius:10px;">
            <div style="font-size:10px;color:var(--text-secondary);">AVAILABLE TREASURY</div>
            <div style="font-size:15px;font-weight:900;color:var(--ice-cyan);">${CadFormatter.formatCompact(fin.cashBalanceCad)}</div>
          </div>
        </div>
      </div>

      <!-- iOS / iPhone Install Card -->
      <div class="card" style="border-color:var(--ice-cyan);">
        <div class="row-between">
          <div>
            <span class="badge badge-cyan" style="margin-bottom:4px;">iOS / iPHONE READY</span>
            <div style="font-size:15px;font-weight:800;">Install NHL Fantasy GM on iPhone</div>
            <div style="font-size:12px;color:var(--text-secondary);margin-top:2px;">
              No sideloading or App Store required — add directly to your iOS Home Screen for full-screen native app mode.
            </div>
          </div>
          <button class="btn btn-primary" id="profile-btn-ios-guide">How to Install</button>
        </div>
      </div>

      <!-- Quick Actions -->
      <div class="card" style="display:flex;flex-direction:column;gap:8px;">
        <button class="btn btn-outline" id="profile-btn-customize" style="justify-content:space-between;width:100%;">
          <span>🎨 Customize Team Name, Emblem & Colors</span>
          <span>→</span>
        </button>
        <button class="btn btn-outline" id="profile-btn-settings" style="justify-content:space-between;width:100%;">
          <span>⚙️ League Settings, Salary Cap & Scoring Weights</span>
          <span>→</span>
        </button>
        <button class="btn btn-outline" id="profile-btn-sync" style="justify-content:space-between;width:100%;">
          <span>🔄 Sync Live NHL Scoreboard & Boxscores</span>
          <span>→</span>
        </button>
      </div>
    </div>
  `;

  document.getElementById('profile-btn-ios-guide')?.addEventListener('click', () => {
    state.modal = { type: 'IOS_INSTALL' };
    renderModal();
  });
  document.getElementById('profile-btn-customize')?.addEventListener('click', () => {
    state.modal = { type: 'CUSTOMIZE_TEAM' };
    renderModal();
  });
  document.getElementById('profile-btn-settings')?.addEventListener('click', () => {
    state.modal = { type: 'SETTINGS' };
    renderModal();
  });
  document.getElementById('profile-btn-sync')?.addEventListener('click', () => refreshLiveScores());
}

// --- MODALS (PLAYER DETAIL, SETTINGS, CUSTOMIZE TEAM, iOS INSTALL) ---
function openPlayerDetail(playerId) {
  state.modal = { type: 'PLAYER_DETAIL', playerId };
  renderModal();
}

function renderModal() {
  const root = document.getElementById('modal-root');
  if (!root) return;
  if (!state.modal) {
    root.innerHTML = '';
    return;
  }

  if (state.modal.type === 'PLAYER_DETAIL') {
    const p = state.allPlayers.find((x) => x.playerId === state.modal.playerId);
    if (!p) {
      state.modal = null;
      root.innerHTML = '';
      return;
    }
    const isUserOwned = p.ownerTeamId === 'USER';
    const compatibleSlots = Object.values(LINEUP_SLOTS).filter(
      (s) => s.code !== 'UNASSIGNED' && slotAcceptsPlayer(s.code, p)
    );

    root.innerHTML = `
      <div class="modal-backdrop" id="modal-backdrop-close">
        <div class="modal-sheet" onclick="event.stopPropagation()">
          <div class="row-between" style="margin-bottom:12px;">
            <div class="row-center" style="gap:10px;">
              <img src="${p.headshotUrl}" class="player-avatar" style="width:56px;height:56px;" alt="${p.fullName}" onerror="this.src='icon.svg'"/>
              <div>
                <div class="row-center" style="gap:6px;">
                  <span class="ovr-pill">${p.overallRating} OVR</span>
                  <span style="font-size:17px;font-weight:900;">${p.fullName}</span>
                </div>
                <div style="font-size:12px;color:var(--text-secondary);margin-top:2px;">
                  ${p.nhlTeamAbbrev} • #${p.sweaterNumber} • ${p.position} • Age ${p.age} • ${p.shootsCatches}
                </div>
              </div>
            </div>
            <button class="icon-btn" id="btn-close-modal">✕</button>
          </div>

          <!-- Action Buttons -->
          <div class="row-between" style="margin-bottom:12px;">
            <button class="btn btn-outline" id="modal-btn-watchlist">
              ${p.isWatchlisted ? '★ Watchlisted' : '☆ Add Watchlist'}
            </button>
            ${isUserOwned ? `
              <button class="btn btn-danger" id="modal-btn-sell">
                Sell (${CadFormatter.formatShortNoSuffix(p.sellPriceCad)})
              </button>
            ` : !p.ownerTeamId ? `
              <button class="btn btn-primary" id="modal-btn-buy">
                Sign (${CadFormatter.formatShortNoSuffix(p.purchasePriceCad)})
              </button>
            ` : `
              <span class="badge">Owned by ${p.ownerTeamId}</span>
            `}
          </div>

          ${isUserOwned ? `
            <div class="card" style="background:#0B1220;margin-bottom:12px;padding:10px;">
              <div style="font-size:11px;font-weight:800;color:var(--ice-cyan);margin-bottom:6px;">
                ASSIGN LINEUP SLOT (Current: ${p.lineupSlot})
              </div>
              <div class="h-scroll">
                ${compatibleSlots.map((s) => `
                  <button class="chip ${p.lineupSlot === s.code ? 'active' : ''}" data-assign-slot="${s.code}">
                    ${s.code}
                  </button>
                `).join('')}
              </div>
            </div>
          ` : ''}

          <!-- Financial & Contract Grid -->
          <div class="grid-3" style="margin-bottom:12px;">
            <div style="background:#0B1220;padding:8px;border-radius:8px;">
              <div style="font-size:10px;color:var(--text-secondary);">BUY PRICE</div>
              <div style="font-size:13px;font-weight:800;">${CadFormatter.formatShortNoSuffix(p.purchasePriceCad)}</div>
            </div>
            <div style="background:#0B1220;padding:8px;border-radius:8px;">
              <div style="font-size:10px;color:var(--text-secondary);">CAP HIT</div>
              <div style="font-size:13px;font-weight:800;color:var(--ice-cyan);">${CadFormatter.formatShortNoSuffix(p.capHitCad)}</div>
            </div>
            <div style="background:#0B1220;padding:8px;border-radius:8px;">
              <div style="font-size:10px;color:var(--text-secondary);">SEASON FP</div>
              <div style="font-size:13px;font-weight:800;color:var(--profit-emerald);">${(p.fantasy?.seasonPoints || 0).toFixed(1)} FP</div>
            </div>
          </div>

          <!-- Transparent Valuation Breakdown -->
          <div class="card" style="background:#0B1220;margin-bottom:12px;padding:11px;">
            <div style="font-size:12px;font-weight:800;margin-bottom:4px;">Transparent Valuation Model</div>
            <div style="font-size:11px;color:var(--text-secondary);margin-bottom:8px;">${p.priceChangeExplanation}</div>
            ${(p.valuationComponents || []).map((c) => `
              <div class="row-between" style="font-size:12px;padding:3px 0;">
                <div>
                  <div style="font-weight:700;">${c.factorName}</div>
                  <div style="font-size:10px;color:var(--text-secondary);">${c.impactDescription}</div>
                </div>
                <span style="font-weight:800;color:${c.cadContribution >= 0 ? 'var(--profit-emerald)' : 'var(--alert-red)'};">
                  ${CadFormatter.formatShortNoSuffix(c.cadContribution, true)}
                </span>
              </div>
            `).join('')}
          </div>

          <!-- Fantasy Scoring Breakdown -->
          <div class="card" style="background:#0B1220;padding:11px;">
            <div style="font-size:12px;font-weight:800;margin-bottom:6px;">Fantasy Scoring Breakdown (${p.gamesPlayed} GP)</div>
            ${(p.fantasy?.breakdown || []).map((b) => `
              <div class="row-between" style="font-size:12px;padding:3px 0;">
                <span>${b.category} (${b.statCount} × ${b.weight})</span>
                <span style="font-weight:800;color:var(--ice-cyan);">${b.fantasyPoints.toFixed(1)} FP</span>
              </div>
            `).join('')}
          </div>
        </div>
      </div>
    `;

    document.getElementById('modal-backdrop-close')?.addEventListener('click', () => {
      state.modal = null;
      renderModal();
    });
    document.getElementById('btn-close-modal')?.addEventListener('click', () => {
      state.modal = null;
      renderModal();
    });
    document.getElementById('modal-btn-watchlist')?.addEventListener('click', () => toggleWatchlist(p.playerId));
    document.getElementById('modal-btn-buy')?.addEventListener('click', () => {
      buyPlayer(p.playerId);
      renderModal();
    });
    document.getElementById('modal-btn-sell')?.addEventListener('click', () => sellPlayer(p.playerId));

    root.querySelectorAll('[data-assign-slot]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const targetSlot = btn.getAttribute('data-assign-slot');
        // Swap if occupied
        Object.values(state.ownership).forEach((own) => {
          if (own.ownerTeamId === 'USER' && own.lineupSlot === targetSlot) {
            own.lineupSlot = p.lineupSlot || 'UNASSIGNED';
          }
        });
        if (state.ownership[p.playerId]) {
          state.ownership[p.playerId].lineupSlot = targetSlot;
        }
        enrichAllPlayers();
        savePersistedState();
        showToast(`Moved ${p.fullName} to ${targetSlot}`);
        renderApp();
      });
    });
  } else if (state.modal.type === 'IOS_INSTALL') {
    root.innerHTML = `
      <div class="modal-backdrop" id="modal-backdrop-close">
        <div class="modal-sheet" onclick="event.stopPropagation()">
          <div class="row-between" style="margin-bottom:12px;">
            <div style="font-size:17px;font-weight:900;">📲 Install on iPhone / iOS</div>
            <button class="icon-btn" id="btn-close-modal">✕</button>
          </div>
          <div style="font-size:13px;color:var(--text-secondary);margin-bottom:14px;">
            Because Apple blocks APK sideloading on iPhones, NHL Fantasy GM runs as a full-screen <b>Progressive Web App (PWA)</b> on iOS:
          </div>
          <div class="card" style="background:#0B1220;display:flex;flex-direction:column;gap:10px;margin-bottom:14px;">
            <div class="row-center">
              <span class="badge badge-cyan">STEP 1</span>
              <span style="font-size:13px;">Open this link in <b>Safari</b> on your iPhone.</span>
            </div>
            <div class="row-center">
              <span class="badge badge-cyan">STEP 2</span>
              <span style="font-size:13px;">Tap the <b>Share</b> button (square with arrow pointing up) at the bottom of Safari.</span>
            </div>
            <div class="row-center">
              <span class="badge badge-cyan">STEP 3</span>
              <span style="font-size:13px;">Scroll down and tap <b>"Add to Home Screen"</b>.</span>
            </div>
            <div class="row-center">
              <span class="badge badge-emerald">DONE!</span>
              <span style="font-size:13px;">Launch <b>NHL GM</b> from your iPhone Home Screen in full-screen native mode!</span>
            </div>
          </div>
          <button class="btn btn-primary" id="btn-got-it-ios" style="width:100%;">Got It</button>
        </div>
      </div>
    `;
    const close = () => { state.modal = null; renderModal(); };
    document.getElementById('modal-backdrop-close')?.addEventListener('click', close);
    document.getElementById('btn-close-modal')?.addEventListener('click', close);
    document.getElementById('btn-got-it-ios')?.addEventListener('click', close);
  } else if (state.modal.type === 'CUSTOMIZE_TEAM') {
    const team = getHumanTeam();
    root.innerHTML = `
      <div class="modal-backdrop" id="modal-backdrop-close">
        <div class="modal-sheet" onclick="event.stopPropagation()">
          <div class="row-between" style="margin-bottom:12px;">
            <div style="font-size:17px;font-weight:900;">🎨 Customize Franchise Identity</div>
            <button class="icon-btn" id="btn-close-modal">✕</button>
          </div>
          <div style="display:flex;flex-direction:column;gap:10px;">
            <div>
              <label style="font-size:11px;color:var(--text-secondary);">FRANCHISE NAME</label>
              <input type="text" id="cust-team-name" class="input-field" value="${team.teamName}"/>
            </div>
            <div>
              <label style="font-size:11px;color:var(--text-secondary);">3-LETTER ABBREVIATION</label>
              <input type="text" id="cust-team-abbrev" class="input-field" maxlength="3" value="${team.abbreviation}" style="text-transform:uppercase;"/>
            </div>
            <div>
              <label style="font-size:11px;color:var(--text-secondary);">EMBLEM</label>
              <div class="h-scroll" style="margin-top:4px;">
                ${TEAM_EMBLEMS.map((e) => `
                  <button class="chip ${team.emblemId === e.id ? 'active' : ''}" data-emblem="${e.id}">
                    ${e.icon} ${e.name}
                  </button>
                `).join('')}
              </div>
            </div>
            <div>
              <label style="font-size:11px;color:var(--text-secondary);">COLOR PALETTE</label>
              <div class="h-scroll" style="margin-top:4px;">
                ${TEAM_COLOR_PRESETS.map((c) => `
                  <button class="chip ${team.primaryColorHex === c.primaryHex ? 'active' : ''}" data-color-preset="${c.id}">
                    <span style="width:10px;height:10px;border-radius:50%;background:${c.css};display:inline-block;"></span>
                    ${c.name}
                  </button>
                `).join('')}
              </div>
            </div>
            <button class="btn btn-primary" id="btn-save-team-identity" style="margin-top:6px;">Save Franchise Identity</button>
          </div>
        </div>
      </div>
    `;
    const close = () => { state.modal = null; renderModal(); };
    document.getElementById('modal-backdrop-close')?.addEventListener('click', close);
    document.getElementById('btn-close-modal')?.addEventListener('click', close);

    root.querySelectorAll('[data-emblem]').forEach((btn) => {
      btn.addEventListener('click', () => {
        team.emblemId = btn.getAttribute('data-emblem');
        renderModal();
      });
    });
    root.querySelectorAll('[data-color-preset]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const preset = TEAM_COLOR_PRESETS.find((x) => x.id === btn.getAttribute('data-color-preset'));
        if (preset) {
          team.primaryColorHex = preset.primaryHex;
          team.secondaryColorHex = preset.secondaryHex;
          renderModal();
        }
      });
    });
    document.getElementById('btn-save-team-identity')?.addEventListener('click', () => {
      const n = document.getElementById('cust-team-name')?.value?.trim();
      const a = document.getElementById('cust-team-abbrev')?.value?.trim()?.toUpperCase();
      if (n) team.teamName = n;
      if (a) team.abbreviation = a.slice(0, 3);
      savePersistedState();
      state.modal = null;
      showToast('Updated Franchise Identity!');
      renderApp();
    });
  } else if (state.modal.type === 'SETTINGS') {
    const w = state.settings.scoringWeights;
    const ec = state.settings.economyConfig;
    root.innerHTML = `
      <div class="modal-backdrop" id="modal-backdrop-close">
        <div class="modal-sheet" onclick="event.stopPropagation()">
          <div class="row-between" style="margin-bottom:12px;">
            <div style="font-size:17px;font-weight:900;">⚙️ League Settings & Scoring Rules</div>
            <button class="icon-btn" id="btn-close-modal">✕</button>
          </div>

          <div style="display:flex;flex-direction:column;gap:10px;">
            <div class="card" style="background:#0B1220;">
              <div class="row-between">
                <div>
                  <div style="font-size:13px;font-weight:800;">Enforce $95.50M CAD Salary Cap</div>
                  <div style="font-size:11px;color:var(--text-secondary);">Official NHL Upper Limit Cap Compliance</div>
                </div>
                <input type="checkbox" id="set-enforce-cap" ${ec.enforceSalaryCap ? 'checked' : ''} style="transform:scale(1.3);"/>
              </div>
            </div>

            <div class="card" style="background:#0B1220;">
              <div style="font-size:12px;font-weight:800;color:var(--ice-cyan);margin-bottom:8px;">FANTASY SCORING WEIGHTS</div>
              <div class="grid-2">
                <div>
                  <label style="font-size:10px;color:var(--text-secondary);">Goals (G)</label>
                  <input type="number" step="0.1" id="sw-goals" class="input-field" value="${w.goals}"/>
                </div>
                <div>
                  <label style="font-size:10px;color:var(--text-secondary);">Assists (A)</label>
                  <input type="number" step="0.1" id="sw-assists" class="input-field" value="${w.assists}"/>
                </div>
                <div>
                  <label style="font-size:10px;color:var(--text-secondary);">Power-Play Pts (PPP)</label>
                  <input type="number" step="0.1" id="sw-ppp" class="input-field" value="${w.powerPlayPoints}"/>
                </div>
                <div>
                  <label style="font-size:10px;color:var(--text-secondary);">Shots on Goal (SOG)</label>
                  <input type="number" step="0.1" id="sw-sog" class="input-field" value="${w.shotsOnGoal}"/>
                </div>
                <div>
                  <label style="font-size:10px;color:var(--text-secondary);">Hits</label>
                  <input type="number" step="0.1" id="sw-hits" class="input-field" value="${w.hits}"/>
                </div>
                <div>
                  <label style="font-size:10px;color:var(--text-secondary);">Blocked Shots</label>
                  <input type="number" step="0.1" id="sw-blocks" class="input-field" value="${w.blockedShots}"/>
                </div>
                <div>
                  <label style="font-size:10px;color:var(--text-secondary);">Goalie Wins (W)</label>
                  <input type="number" step="0.1" id="sw-gwins" class="input-field" value="${w.goalieWins}"/>
                </div>
                <div>
                  <label style="font-size:10px;color:var(--text-secondary);">Goalie Saves (SV)</label>
                  <input type="number" step="0.05" id="sw-gsaves" class="input-field" value="${w.goalieSaves}"/>
                </div>
              </div>
            </div>

            <div class="row-between">
              <button class="btn btn-danger" id="btn-reset-league">Reset Franchise</button>
              <button class="btn btn-primary" id="btn-save-settings">Save Rules & Recalculate</button>
            </div>
          </div>
        </div>
      </div>
    `;
    const close = () => { state.modal = null; renderModal(); };
    document.getElementById('modal-backdrop-close')?.addEventListener('click', close);
    document.getElementById('btn-close-modal')?.addEventListener('click', close);

    document.getElementById('btn-save-settings')?.addEventListener('click', () => {
      ec.enforceSalaryCap = Boolean(document.getElementById('set-enforce-cap')?.checked);
      w.goals = parseFloat(document.getElementById('sw-goals')?.value) || 3.0;
      w.assists = parseFloat(document.getElementById('sw-assists')?.value) || 2.0;
      w.powerPlayPoints = parseFloat(document.getElementById('sw-ppp')?.value) || 1.0;
      w.shotsOnGoal = parseFloat(document.getElementById('sw-sog')?.value) || 0.4;
      w.hits = parseFloat(document.getElementById('sw-hits')?.value) || 0.3;
      w.blockedShots = parseFloat(document.getElementById('sw-blocks')?.value) || 0.5;
      w.goalieWins = parseFloat(document.getElementById('sw-gwins')?.value) || 4.0;
      w.goalieSaves = parseFloat(document.getElementById('sw-gsaves')?.value) || 0.2;

      enrichAllPlayers();
      savePersistedState();
      state.modal = null;
      showToast('Saved scoring weights & recalculated all 794 NHL players!');
      renderApp();
    });

    document.getElementById('btn-reset-league')?.addEventListener('click', () => {
      localStorage.removeItem(STORAGE_KEY);
      initializeDefaultLeague();
      enrichAllPlayers();
      state.modal = null;
      showToast('Reset franchise to fresh $100M CAD treasury.');
      renderApp();
    });
  }
}

// --- Bootstrap Application ---
async function bootstrap() {
  const hasSaved = loadPersistedState();
  if (!hasSaved) {
    initializeDefaultLeague();
  }

  state.todayDate = getTodayEasternDateStr();
  state.gameWeek = buildSevenDayWeek(state.todayDate, []);
  renderApp();

  try {
    const db = await loadInitialNhlDatabase();
    state.nhlTeams = db.nhlTeams || [];
    state.allPlayers = db.players || [];
    if (Array.isArray(db.gameWeek) && db.gameWeek.length > 0) {
      state.gameWeek = buildSevenDayWeek(state.todayDate, db.gameWeek);
    }
    enrichAllPlayers();
    renderApp();
  } catch (err) {
    console.error('Database load error:', err);
  }

  // Fetch live today's scoreboard, boxscores & news
  refreshLiveScores();
  fetchLiveNhlNews().then((articles) => {
    state.newsHeadlines = articles;
    renderApp();
  });

  // Register Service Worker for iOS/Web PWA
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  }
}

document.addEventListener('DOMContentLoaded', bootstrap);
