import { SEASONS, TEAMS, getStandings } from '../data/demo-data.js';
import { mountAppShell } from '../components/app-shell.js';
import { renderMetrics } from '../components/metrics.js';
import { renderStandingsTable } from '../components/standings-table.js';
import { readPageState, persistPageState } from '../state/preferences.js';

const LIVE_SEASON_ID = '2026-2027';
const STANDINGS_URL = '/api/kbl/seasons/S49G01/standings';
const state = readPageState();
const teamAppearance = new Map(TEAMS.map((team) => [team.code, team]));
const metrics = document.getElementById('standings-metrics');
const standingsBody = document.getElementById('standings-body');
const standingsResults = document.getElementById('standings-results');
const standingsStatus = document.getElementById('standings-status');
const statusMessage = document.getElementById('standings-status-message');
const retry = document.getElementById('retry-standings');
let request;

/** 조회 진행 상태나 안내 문구를 표시하고 필요한 경우 재조회 버튼을 보입니다. */
function showStatus(message, canRetry = false) {
    standingsStatus.hidden = !message;
    statusMessage.textContent = message;
    retry.hidden = !canRetry;
}

/** DB 팀명의 표기를 유지하면서 팀 코드에 해당하는 배지와 팀관리 링크를 사용합니다. */
function toLiveStanding(standing) {
    const { team, recentResults, winRate } = standing;
    const appearance = teamAppearance.get(team.code) ?? { mark: team.code, color: 'var(--wood)' };
    return {
        ...standing,
        team: { id: appearance.id, name: team.name, mark: appearance.mark, color: appearance.color },
        winRate: winRate.toFixed(2),
        form: recentResults.toReversed().map((result) => result === 'WIN' ? 'W' : 'L').join(''),
    };
}

/** 이전 시즌의 기존 예시 순위와 팀 정보를 표에 맞추어 구성합니다. */
function getDemoStandings(seasonId) {
    return getStandings(seasonId).map((team, index) => {
        const games = team.wins + team.losses;
        return {
            rank: index + 1, team: { ...team, name: `${team.city} ${team.name}` }, games,
            wins: team.wins, losses: team.losses, winRate: (team.wins / games).toFixed(3), form: team.form,
        };
    });
}

/** 리그 선두, 종료 경기 수, 참가 팀 수와 순위표를 함께 표시합니다. */
function renderStandings(standings, completedGames, isLive) {
    if (!standings.length) {
        showStatus('표시할 팀 순위가 없습니다.');
        return;
    }
    const [leader] = standings;
    renderMetrics(metrics, [
        { label: '리그 선두', value: leader.team.name, detail: `${leader.wins}승 ${leader.losses}패`, icon: 'trophy', accent: true },
        { label: '시즌 종료 경기', value: completedGames, unit: '경기', detail: isLive ? '순위 집계 기준' : '정규시즌 예시 집계', icon: 'calendar' },
        { label: '참가 팀', value: standings.length, unit: '팀', detail: '남자 KBL', icon: 'users' },
    ]);
    renderStandingsTable(standingsBody, standings, state.season);
    metrics.hidden = false;
    standingsResults.hidden = false;
    showStatus('');
}

/** 선택한 시즌을 반영하고 늦게 도착한 이전 요청이 화면을 덮지 않게 합니다. */
async function loadStandings() {
    request?.abort();
    const seasonId = state.season;
    const season = SEASONS[seasonId];
    const isLive = seasonId === LIVE_SEASON_ID;
    document.getElementById('season-caption').textContent = `${season.label} 정규시즌`;
    document.getElementById('standings-season').textContent = `${season.label} · ${isLive ? 'KBL 적재 기록' : '화면 확인용 예시 기록'}`;
    persistPageState(state);
    metrics.hidden = true;
    standingsResults.hidden = true;

    if (!isLive) {
        renderStandings(getDemoStandings(seasonId), season.completedGames, false);
        return;
    }

    const controller = new AbortController();
    request = controller;
    showStatus('리그 순위를 불러오는 중입니다.');
    try {
        const response = await fetch(STANDINGS_URL, { headers: { Accept: 'application/json' }, signal: controller.signal });
        if (!response.ok) {
            const problem = response.status === 409 ? await response.json() : null;
            throw new Error(problem?.detail ?? '리그 순위를 불러오지 못했습니다.');
        }
        const { completedGames, standings } = await response.json();
        if (!controller.signal.aborted) renderStandings(standings.map(toLiveStanding), completedGames, true);
    } catch (error) {
        if (!controller.signal.aborted) {
            const message = error instanceof TypeError || error instanceof SyntaxError
                ? '리그 순위를 불러오지 못했습니다.' : error.message;
            showStatus(message, true);
        }
    } finally {
        if (request === controller) request = undefined;
    }
}

/** 연결된 시즌 선택과 새로고침 이후의 화면 상태를 갱신합니다. */
function handleSeasonChange(seasonId) {
    state.season = seasonId;
    loadStandings();
}

mountAppShell('standings', state, handleSeasonChange);
retry.addEventListener('click', loadStandings);
loadStandings();
