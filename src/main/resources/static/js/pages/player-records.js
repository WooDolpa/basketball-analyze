import { SEASONS, TEAMS, getPlayerRecords, getTeam } from '../data/demo-data.js';
import { mountAppShell } from '../components/app-shell.js';
import { formatPlayerAverage, renderPlayerLeaders } from '../components/player-leaders.js';
import { pageHref, teamBadge, updateSegmentedControl } from '../components/ui.js';
import { readPageState, persistPageState } from '../state/preferences.js';
import { escapeHtml } from '../utils/format.js';

const LIVE_SEASON_ID = '2026-2027';
const PLAYER_RECORDS_URL = '/api/kbl/seasons/S49G01/player-records';
const METRIC_LABELS = { points: '득점', rebounds: '리바운드', assists: '어시스트' };
const state = readPageState();
const teamAppearance = new Map(TEAMS.map((team) => [team.code, team]));
const leaders = document.getElementById('player-leader-list');
const records = document.getElementById('player-records-results');
const body = document.getElementById('player-records-body');
const status = document.getElementById('player-records-status');
const statusMessage = document.getElementById('player-records-status-message');
const retry = document.getElementById('retry-player-records');
const appearanceNotice = document.getElementById('player-appearance-notice');
let request;
let players = [];
let recordedGames = 0;

/** 조회 상태를 표시하고 실패했을 때 같은 시즌을 다시 요청할 수 있게 합니다. */
function showStatus(message, canRetry = false) {
    status.hidden = !message;
    statusMessage.textContent = message;
    retry.hidden = !canRetry;
}

/** DB의 선수·구단 이름과 원래 경기당 평균을 화면에 사용할 형태로 맞춥니다. */
function toLivePlayer(player) {
    const teams = player.teams.map((team) => {
        const appearance = teamAppearance.get(team.code);
        return { ...team, id: appearance?.id, mark: appearance?.mark ?? team.code, color: appearance?.color ?? 'var(--wood)' };
    });
    return {
        ...player,
        teams,
        ...Object.fromEntries(Object.keys(METRIC_LABELS).map((metric) => [metric,
            player.totals[metric] === null ? null : player.totals[metric] / player.games])),
    };
}

/** 이전 시즌의 예시 기록을 공통 선수 카드와 표 형식에 맞춥니다. */
function toDemoPlayer(player) {
    const team = getTeam(player.teamId);
    return { ...player, teams: [{ ...team, name: `${team.city} ${team.name}` }] };
}

/** 지원하는 팀은 팀관리 화면으로 연결하고 모든 출전 팀을 표시합니다. */
function renderTeams(teams) {
    return teams.map((team) => {
        const label = `${teamBadge(team, 'small')}<strong>${escapeHtml(team.name)}</strong>`;
        if (!team.id) return `<span class="standings-team">${label}</span>`;
        const link = pageHref('teamManagement', { team: team.id, season: state.season });
        return `<a class="standings-team" href="${escapeHtml(link)}">${label}</a>`;
    }).join('');
}

/** 선택 지표 순으로 선수 리더와 전체 기록을 표시합니다. */
function renderPlayerRecords() {
    const metric = state.leaderMetric;
    updateSegmentedControl('[data-leader]', metric);
    persistPageState(state, { metric });
    if (!players.length) return;

    const isLive = state.season === LIVE_SEASON_ID;
    const sorted = [...players].sort((first, second) => (second[metric] ?? -1) - (first[metric] ?? -1)
        || (second.games ?? 0) - (first.games ?? 0) || first.name.localeCompare(second.name, 'ko'));
    const scope = isLive ? `적재 경기 ${recordedGames}경기` : '예시';
    document.getElementById('records-sort-caption').textContent = `${METRIC_LABELS[metric]} 높은 순 · ${players.length}명 · ${scope}`;
    renderPlayerLeaders(leaders, sorted.filter((player) => player[metric] !== null), metric);
    document.querySelectorAll('[data-stat]').forEach((heading) => {
        const selected = heading.dataset.stat === metric;
        heading.classList.toggle('is-sorted', selected);
        heading.setAttribute('aria-sort', selected ? 'descending' : 'none');
    });
    body.innerHTML = sorted.map((player, index) => `<tr><td>${player[metric] === null ? '—' : index + 1}</td>
            <th scope="row">${escapeHtml(player.name)}</th>
            <td><div class="player-team-links">${renderTeams(player.teams)}</div></td><td>${player.games ?? '—'}</td>
            ${Object.keys(METRIC_LABELS).map((key) => `<td class="${key === metric ? 'accent-text' : ''}">${formatPlayerAverage(player[key])}</td>`).join('')}
        </tr>`).join('');
    records.hidden = false;
    const hasLeaders = sorted.some((player) => player[metric] !== null);
    leaders.hidden = !hasLeaders;
    showStatus(hasLeaders ? '' : `확인된 ${METRIC_LABELS[metric]} 기록이 없습니다.`);
}

/** 시즌을 선택하고 늦게 도착한 이전 시즌의 응답이 현재 화면을 덮지 않게 합니다. */
async function loadPlayerRecords() {
    request?.abort();
    const seasonId = state.season;
    const isLive = seasonId === LIVE_SEASON_ID;
    document.getElementById('season-caption').textContent = `${SEASONS[seasonId].label} 정규시즌`;
    document.getElementById('player-leader-caption').textContent = isLive ? '경기당 평균 · 적재 경기 기준' : '경기당 평균 · 예시';
    players = [];
    leaders.hidden = true;
    records.hidden = true;
    appearanceNotice.hidden = true;
    renderPlayerRecords();

    if (!isLive) {
        players = getPlayerRecords(seasonId).map(toDemoPlayer);
        renderPlayerRecords();
        return;
    }

    const controller = new AbortController();
    request = controller;
    showStatus('선수 기록을 불러오는 중입니다.');
    try {
        const response = await fetch(PLAYER_RECORDS_URL, { headers: { Accept: 'application/json' }, signal: controller.signal });
        if (!response.ok) throw new Error('선수 기록을 불러오지 못했습니다.');
        const result = await response.json();
        if (controller.signal.aborted) return;
        recordedGames = result.recordedGames;
        players = result.players.map(toLivePlayer);
        appearanceNotice.hidden = result.unverifiedAppearances === 0;
        appearanceNotice.textContent = `출전 여부 미확인 ${result.unverifiedAppearances}건은 경기당 평균 집계에서 제외했습니다.`;
        if (!players.length) {
            showStatus(recordedGames ? '확인된 출전 기록이 없습니다.' : '아직 적재된 선수 기록이 없습니다.');
            return;
        }
        renderPlayerRecords();
    } catch {
        if (!controller.signal.aborted) showStatus('선수 기록을 불러오지 못했습니다.', true);
    } finally {
        if (request === controller) request = undefined;
    }
}

/** 시즌 선택을 조회에 반영합니다. */
function handleSeasonChange(seasonId) {
    state.season = seasonId;
    loadPlayerRecords();
}

/** 리더 순위 버튼의 선택을 기록 정렬에 반영합니다. */
function handleMetricSelection(event) {
    const button = event.target.closest('[data-leader]');
    if (!button) return;
    state.leaderMetric = button.dataset.leader;
    renderPlayerRecords();
}

mountAppShell('players', state, handleSeasonChange);
document.querySelector('.leaders-panel').addEventListener('click', handleMetricSelection);
retry.addEventListener('click', loadPlayerRecords);
loadPlayerRecords();
