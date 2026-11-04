/**
 * NHL Fantasy GM — Real-Time Live NHL Data & Scoreboard Service (iOS / Web Edition)
 * Combines the 32-team 794-player official NHL database snapshot with CORS-native
 * real-time ESPN NHL Scoreboard, Live Player Boxscores, Injuries, and News feeds.
 */

const ESPN_NHL_BASE = 'https://site.api.espn.com/apis/site/v2/sports/hockey/nhl';

const ABBREV_NORM = {
  SJ: 'SJS',
  TB: 'TBL',
  LA: 'LAK',
  NJ: 'NJD',
  MON: 'MTL',
  WAS: 'WSH',
  CLB: 'CBJ',
  cal: 'CGY'
};

export function normalizeNhlAbbrev(raw) {
  if (!raw) return 'NHL';
  const up = String(raw).trim().toUpperCase();
  return ABBREV_NORM[up] || up;
}

export function getTodayEasternDateStr() {
  try {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'America/New_York',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).formatToParts(new Date());
    const y = parts.find((p) => p.type === 'year')?.value;
    const m = parts.find((p) => p.type === 'month')?.value;
    const d = parts.find((p) => p.type === 'day')?.value;
    if (y && m && d) return `${y}-${m}-${d}`;
  } catch (_) {}
  return new Date().toISOString().slice(0, 10);
}

export function buildSevenDayWeek(centerDateStr, existingGameWeek = []) {
  const countMap = {};
  (existingGameWeek || []).forEach((d) => {
    if (d && d.date) countMap[d.date] = d.numberOfGames;
  });

  const base = new Date(`${centerDateStr}T12:00:00Z`);
  if (isNaN(base.getTime())) return existingGameWeek;

  const days = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
  const week = [];
  for (let offset = -3; offset <= 3; offset++) {
    const dt = new Date(base.getTime() + offset * 86400000);
    const iso = dt.toISOString().slice(0, 10);
    week.push({
      date: iso,
      dayAbbrev: days[dt.getUTCDay()],
      numberOfGames: countMap[iso] !== undefined ? countMap[iso] : 0
    });
  }
  return week;
}

export async function loadInitialNhlDatabase() {
  const res = await fetch('./data/nhl-live-db.json');
  if (!res.ok) throw new Error(`Failed to load NHL database (${res.status})`);
  return res.json();
}

export async function fetchLiveScoreboardForDate(dateStr) {
  const targetDate = dateStr || getTodayEasternDateStr();
  const compactDate = targetDate.replace(/-/g, '');
  const url = `${ESPN_NHL_BASE}/scoreboard?dates=${compactDate}`;

  const res = await fetch(url);
  if (!res.ok) throw new Error(`Scoreboard HTTP ${res.status}`);
  const data = await res.json();

  const events = data.events || [];
  const games = events.map((ev) => {
    const comp = (ev.competitions && ev.competitions[0]) || {};
    const competitors = comp.competitors || [];
    const awayComp = competitors.find((c) => c.homeAway === 'away') || competitors[0] || {};
    const homeComp = competitors.find((c) => c.homeAway === 'home') || competitors[1] || {};

    const awayAbbrev = normalizeNhlAbbrev(awayComp.team?.abbreviation);
    const homeAbbrev = normalizeNhlAbbrev(homeComp.team?.abbreviation);

    const awayScore = parseInt(awayComp.score || '0', 10) || 0;
    const homeScore = parseInt(homeComp.score || '0', 10) || 0;

    const getStat = (compObj, statName) => {
      const st = (compObj.statistics || []).find((s) => s.name === statName);
      return st ? (parseInt(st.displayValue, 10) || 0) : 0;
    };

    const awaySaves = getStat(awayComp, 'saves');
    const homeSaves = getStat(homeComp, 'saves');

    // Team SOG = goals scored + opponent goalie saves
    const awaySog = (awaySaves > 0 || homeSaves > 0 || awayScore > 0 || homeScore > 0)
      ? (awayScore + homeSaves)
      : 0;
    const homeSog = (awaySaves > 0 || homeSaves > 0 || awayScore > 0 || homeScore > 0)
      ? (homeScore + awaySaves)
      : 0;

    const statusType = ev.status?.type || {};
    const stateRaw = statusType.state || 'pre'; // 'pre', 'in', 'post'
    const period = ev.status?.period || 1;
    const clock = ev.status?.displayClock || '';

    let gameState = 'FUT';
    let periodLabel = 'Scheduled';
    let clockLabel = '';

    if (stateRaw === 'in') {
      gameState = 'LIVE';
      periodLabel = period > 3 ? (period === 4 ? 'OT' : 'SO') : `P${period}`;
      clockLabel = clock;
    } else if (stateRaw === 'post') {
      gameState = 'FINAL';
      periodLabel = period > 3 ? (period === 4 ? 'FINAL/OT' : 'FINAL/SO') : 'FINAL';
    } else {
      gameState = 'FUT';
      periodLabel = 'Scheduled';
    }

    return {
      gameId: Number(ev.id),
      gameDate: targetDate,
      gameState,
      periodLabel,
      clockLabel,
      awayAbbrev,
      awayName: awayComp.team?.displayName || awayAbbrev,
      awayScore,
      awaySog,
      awayLogoUrl: `https://assets.nhle.com/logos/nhl/svg/${awayAbbrev}_light.svg`,
      homeAbbrev,
      homeName: homeComp.team?.displayName || homeAbbrev,
      homeScore,
      homeSog,
      homeLogoUrl: `https://assets.nhle.com/logos/nhl/svg/${homeAbbrev}_light.svg`,
      startTimeUtc: ev.date || '',
      venueName: comp.venue?.fullName || 'NHL Regular Season',
      isLive: gameState === 'LIVE',
      isFinal: gameState === 'FINAL'
    };
  });

  return {
    currentDate: targetDate,
    games
  };
}

export async function fetchLiveBoxscoresForGames(games) {
  const activeGames = (games || []).filter((g) => g.gameState === 'LIVE' || g.gameState === 'FINAL');
  if (activeGames.length === 0) return {};

  const playerBoxMap = {};

  await Promise.all(
    activeGames.slice(0, 14).map(async (g) => {
      try {
        const res = await fetch(`${ESPN_NHL_BASE}/summary?event=${g.gameId}`);
        if (!res.ok) return;
        const sum = await res.json();
        const teamBlocks = sum.boxscore?.players || [];

        teamBlocks.forEach((tb) => {
          const teamAbbrev = normalizeNhlAbbrev(tb.team?.abbreviation);
          const isAway = teamAbbrev === g.awayAbbrev;
          const oppLabel = isAway ? `@ ${g.homeAbbrev}` : `vs ${g.awayAbbrev}`;
          const teamWon = g.isFinal && ((isAway && g.awayScore > g.homeScore) || (!isAway && g.homeScore > g.awayScore));

          (tb.statistics || []).forEach((cat) => {
            const keys = cat.keys || [];
            const isGoalieCat = cat.name === 'goalies';

            (cat.athletes || []).forEach((athEntry) => {
              const name = athEntry.athlete?.displayName;
              if (!name) return;
              const normName = name.trim().toLowerCase();
              const stats = athEntry.stats || [];
              const statObj = {};
              keys.forEach((k, idx) => {
                statObj[k] = stats[idx];
              });

              if (isGoalieCat) {
                const saves = parseInt(statObj.saves || '0', 10) || 0;
                const ga = parseInt(statObj.goalsAgainst || '0', 10) || 0;
                const toi = statObj.timeOnIce || '0:00';
                if (saves > 0 || ga > 0 || toi !== '0:00') {
                  playerBoxMap[normName] = {
                    gameId: g.gameId,
                    isLive: g.isLive,
                    opponentLabel: oppLabel,
                    goals: 0,
                    assists: 0,
                    ppPoints: 0,
                    shots: 0,
                    hits: 0,
                    blocks: 0,
                    goalieWin: (teamWon && saves >= 10) ? 1 : 0,
                    goalieSaves: saves,
                    goalieShutout: (teamWon && ga === 0 && saves >= 15) ? 1 : 0
                  };
                }
              } else if (cat.name === 'forwards' || cat.name === 'defenses') {
                const goals = parseInt(statObj.goals || '0', 10) || 0;
                const assists = parseInt(statObj.assists || '0', 10) || 0;
                const shots = parseInt(statObj.shotsTotal || '0', 10) || 0;
                const hits = parseInt(statObj.hits || '0', 10) || 0;
                const blocks = parseInt(statObj.blockedShots || '0', 10) || 0;
                playerBoxMap[normName] = {
                  gameId: g.gameId,
                  isLive: g.isLive,
                  opponentLabel: oppLabel,
                  goals,
                  assists,
                  ppPoints: 0,
                  shots,
                  hits,
                  blocks,
                  goalieWin: 0,
                  goalieSaves: 0,
                  goalieShutout: 0
                };
              }
            });
          });
        });
      } catch (_) {}
    })
  );

  return playerBoxMap;
}

export async function fetchLiveNhlNews() {
  try {
    const res = await fetch(`${ESPN_NHL_BASE}/news`);
    if (!res.ok) return [];
    const data = await res.json();
    return (data.articles || []).map((a, idx) => ({
      articleId: String(a.id || idx),
      headline: a.headline || 'NHL Update',
      description: a.description || '',
      publishedAt: a.published || '',
      webUrl: a.links?.web?.href || 'https://www.nhl.com',
      sourceLabel: 'ESPN NHL News'
    }));
  } catch (_) {
    return [];
  }
}
