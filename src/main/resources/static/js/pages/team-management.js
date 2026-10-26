import { SEASONS, TEAMS, getStandings } from '../data/demo-data.js';
import { mountAppShell } from '../components/app-shell.js';
import { formDots, pageHref, populateTeamSelect, teamBadge } from '../components/ui.js';
import { renderTeamTrend } from '../components/team-trend.js';
import { readPageState, persistPageState } from '../state/preferences.js';
import { escapeHtml, formatDateKey } from '../utils/format.js';

const state = readPageState();

/** 선택한 팀의 시즌 정보, 추세와 최근 경기 기록을 표시합니다. */
function renderTeamManagement() {
    const standings = getStandings(state.season);
    const rank = standings.findIndex((team) => team.id === state.teamId) + 1;
    const team = standings[rank - 1];
    document.getElementById('team-select').value = team.id;
    document.getElementById('team-identity').innerHTML = `
        <div class="team-profile">${teamBadge(team, 'large')}
            <div><span class="eyebrow">${SEASONS[state.season].label} · 예시 기록</span><h2>${team.fullName}</h2><p>${team.stadium}</p></div></div>
        <div class="team-profile-form"><span>최근 5경기</span>${formDots(team.form)}</div>
        <a class="button button-secondary" href="${escapeHtml(pageHref('standings', { season: state.season }))}">리그 순위 보기 ↗</a>`;
    const records = renderTeamTrend(state);
    document.getElementById('team-season-summary').innerHTML = `
        <div class="team-season-rank"><span>리그 순위</span><strong>${rank}<small>위</small></strong></div>
        <dl class="team-season-stats">
            <div><dt>시즌 성적</dt><dd>${team.wins}승 ${team.losses}패</dd></div>
            <div><dt>경기 수</dt><dd>${team.wins + team.losses}경기</dd></div>
            <div><dt>승률</dt><dd class="accent-text">${(team.wins / (team.wins + team.losses) * 100).toFixed(1)}%</dd></div>
        </dl><p class="team-season-note">시즌 성적과 최근 경기 차트는 서로 다른 예시 데이터입니다.</p>`;
    document.getElementById('recent-games-title').textContent = `최근 ${state.period}경기 기록`;
    document.getElementById('recent-games-body').innerHTML = [...records].reverse().map((record) => `
        <tr><td>${formatDateKey(record.date)}</td><th scope="row">${escapeHtml(record.opponent)}</th>
            <td>${record.scored}</td><td>${record.conceded}</td><td>${record.scored > record.conceded ? '+' : ''}${record.scored - record.conceded}</td>
            <td><span class="result-badge ${record.scored > record.conceded ? 'win' : 'loss'}">${record.scored > record.conceded ? '승' : '패'}</span></td></tr>`).join('');
    persistPageState(state, { team: state.teamId, period: state.period });
}

/** 시즌 선택을 유지한 채 팀의 분석 내용을 갱신합니다. */
function handleSeasonChange(season) {
    state.season = season;
    renderTeamManagement();
}

/** 팀 선택과 최근 경기 수 선택을 화면 상태에 연결합니다. */
function bindTeamEvents() {
    document.getElementById('team-select').addEventListener('change', (event) => {
        state.teamId = event.target.value;
        renderTeamManagement();
    });
    document.querySelector('.trend-panel').addEventListener('click', (event) => {
        const button = event.target.closest('[data-period]');
        if (!button) return;
        state.period = Number(button.dataset.period);
        renderTeamManagement();
    });
}

mountAppShell('teamManagement', state, handleSeasonChange);
populateTeamSelect(document.getElementById('team-select'), TEAMS, state.teamId);
renderTeamManagement();
bindTeamEvents();
