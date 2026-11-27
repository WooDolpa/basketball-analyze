import { SEASONS, TEAMS, getStandings, getTeamTrend } from '../data/demo-data.js';
import { mountAppShell } from '../components/app-shell.js';
import { formDots, pageHref, populateTeamSelect, teamBadge, updateSegmentedControl } from '../components/ui.js';
import { renderTeamTrend, teamResultBadge } from '../components/team-trend.js';
import { readPageState, persistPageState } from '../state/preferences.js';
import { escapeHtml, formatDateKey, formatKoreanDateKey } from '../utils/format.js';

const ACTIVE_SEASON = '2026-2027';
const TEAM_API = '/api/kbl/seasons/S49G01/teams';
const state = readPageState();
const teamSelect = document.getElementById('team-select');
const teamIdentity = document.getElementById('team-identity');
const teamSeasonPanel = document.getElementById('team-season-panel');
const teamTrendContent = document.getElementById('team-trend-content');
const recentGamesPanel = document.getElementById('recent-games-panel');
const statusPanel = document.getElementById('team-status');
const statusMessage = document.getElementById('team-status-message');
const retryButton = document.getElementById('retry-team');
let request;

/** 조회 중에는 이전 팀의 기록을 접고, 팀과 경기 수 선택은 계속 받습니다. */
function showStatus(message, canRetry = false) {
    statusPanel.hidden = !message;
    statusMessage.textContent = message;
    retryButton.hidden = !canRetry;
    for (const panel of [teamIdentity, teamSeasonPanel, teamTrendContent, recentGamesPanel]) {
        panel.hidden = Boolean(message);
    }
}

/** 이미 지원하던 예시 시즌의 화면 데이터를 구성합니다. */
function getDemoTeamData() {
    const teams = getStandings(state.season);
    const index = teams.findIndex((team) => team.id === state.teamId);
    const team = teams[index];
    const games = team.wins + team.losses;
    return {
        team: { ...team, name: team.fullName },
        standing: { rank: index + 1, games, wins: team.wins, losses: team.losses, winRate: team.wins / games },
        form: team.form,
        records: getTeamTrend(team.id, state.period, state.season).map((game) => ({
            ...game, date: formatDateKey(game.date), won: game.scored > game.conceded,
        })),
    };
}

/** API의 최근 경기 자료를 차트에서 읽는 과거순·한국시간 날짜로 변환합니다. */
function toTeamData(data, appearance) {
    const records = data.recentGames.toReversed().map((game) => ({
        date: formatKoreanDateKey(new Date(game.startsAt)),
        opponent: game.opponent.name,
        scored: game.scored,
        conceded: game.conceded,
        won: game.result === 'WIN',
    }));
    return {
        team: { ...data.team, mark: appearance.mark, color: appearance.color, stadium: appearance.stadium },
        standing: data.standing,
        records,
        form: records.slice(-5).map((game) => game.won ? 'W' : 'L').join(''),
    };
}

/** 팀 정보와 시즌 성적을 표시하고 같은 최근 경기 목록을 차트와 표에 전달합니다. */
function renderTeamManagement({ team, standing, records, form }, isLive) {
    const source = isLive ? 'KBL 적재 기록' : '예시 기록';
    teamIdentity.innerHTML = `
        <div class="team-profile">${teamBadge(team, 'large')}
            <div><span class="eyebrow">${SEASONS[state.season].label} · ${source}</span><h2>${escapeHtml(team.name)}</h2>${team.stadium ? `<p>홈구장 · ${escapeHtml(team.stadium)}</p>` : ''}</div></div>
        <div class="team-profile-form"><span>최근 ${form.length || 5}경기</span>${form ? formDots(form) : '<span>—</span>'}</div>
        <a class="button button-secondary" href="${escapeHtml(pageHref('standings', { season: state.season }))}">리그 순위 보기 ↗</a>`;
    document.getElementById('team-season-summary').innerHTML = `
        <div class="team-season-rank"><span>리그 순위</span><strong>${escapeHtml(standing.rank)}<small>위</small></strong></div>
        <dl class="team-season-stats">
            <div><dt>시즌 성적</dt><dd>${escapeHtml(standing.wins)}승 ${escapeHtml(standing.losses)}패</dd></div>
            <div><dt>경기 수</dt><dd>${escapeHtml(standing.games)}경기</dd></div>
            <div><dt>승률</dt><dd class="accent-text">${(standing.winRate * 100).toFixed(1)}%</dd></div>
        </dl><p class="team-season-note">${isLive ? '동기화한 리그 순위 집계 기준입니다.' : '시즌 성적과 최근 경기 차트는 화면 검토용 예시입니다.'}</p>`;
    document.getElementById('recent-games-title').textContent = records.length ? `최근 ${records.length}경기 기록` : '최근 경기 기록';
    document.getElementById('recent-games-source').textContent = `최신 경기부터 표시 · ${source}`;
    showStatus('');
    renderTeamTrend(records);
    renderRecentGames(records);
}

/** 최신 경기부터 표시하고 점수가 비어 있는 행의 득실 마진은 대시로 표시합니다. */
function renderRecentGames(records) {
    document.getElementById('recent-games-body').innerHTML = records.length ? records.toReversed().map((record) => {
        const margin = record.scored == null || record.conceded == null ? null : record.scored - record.conceded;
        const marginLabel = margin == null ? '—' : `${margin > 0 ? '+' : ''}${margin}`;
        return `<tr><td>${escapeHtml(record.date)}</td><th scope="row">${escapeHtml(record.opponent)}</th>
            <td>${escapeHtml(record.scored ?? '—')}</td><td>${escapeHtml(record.conceded ?? '—')}</td>
            <td>${marginLabel}</td><td>${teamResultBadge(record.won)}</td></tr>`;
    }).join('') : '<tr><td colspan="6">표시할 종료 경기 기록이 없습니다.</td></tr>';
}

/** 선택값에 맞는 팀 정보를 조회하고 중단된 이전 응답은 화면에 반영하지 않습니다. */
async function loadTeamManagement() {
    request?.abort();
    persistPageState(state, { team: state.teamId, period: state.period });
    updateSegmentedControl('[data-period]', state.period);
    if (state.season !== ACTIVE_SEASON) {
        renderTeamManagement(getDemoTeamData(), false);
        return;
    }

    const team = TEAMS.find((entry) => entry.id === state.teamId);
    const controller = new AbortController();
    request = controller;
    showStatus('팀 성적과 경기 기록을 불러오는 중입니다.');
    try {
        const response = await fetch(`${TEAM_API}/${team.code}?limit=${state.period}`, {
            headers: { Accept: 'application/json' }, signal: controller.signal,
        });
        if (!response.ok) {
            const problem = await response.json().catch(() => null);
            if (!controller.signal.aborted) showStatus(problem?.detail ?? '팀 기록을 불러오지 못했습니다.', true);
            return;
        }
        const data = await response.json();
        if (!controller.signal.aborted) renderTeamManagement(toTeamData(data, team), true);
    } catch {
        if (!controller.signal.aborted) showStatus('팀 기록을 불러오지 못했습니다.', true);
    } finally {
        if (request === controller) request = undefined;
    }
}

/** 시즌 선택을 바꾸고 팀 정보와 최근 경기 목록을 다시 표시합니다. */
function handleSeasonChange(season) {
    state.season = season;
    loadTeamManagement();
}

/** 팀과 표시할 경기 수의 변경 및 오류 후 다시 조회를 연결합니다. */
function bindTeamEvents() {
    teamSelect.addEventListener('change', (event) => {
        state.teamId = event.target.value;
        loadTeamManagement();
    });
    document.querySelector('.trend-panel').addEventListener('click', (event) => {
        const button = event.target.closest('[data-period]');
        if (!button) return;
        state.period = Number(button.dataset.period);
        loadTeamManagement();
    });
    retryButton.addEventListener('click', loadTeamManagement);
}

mountAppShell('teamManagement', state, handleSeasonChange);
populateTeamSelect(teamSelect, TEAMS, state.teamId);
bindTeamEvents();
loadTeamManagement();
