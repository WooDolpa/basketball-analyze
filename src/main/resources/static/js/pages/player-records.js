import { SEASONS, getPlayerRecords, getTeam } from '../data/demo-data.js';
import { mountAppShell } from '../components/app-shell.js';
import { renderPlayerLeaders } from '../components/player-leaders.js';
import { pageHref, teamBadge, updateSegmentedControl } from '../components/ui.js';
import { readPageState, persistPageState } from '../state/preferences.js';
import { escapeHtml } from '../utils/format.js';

const state = readPageState();
const METRIC_LABELS = { points: '득점', rebounds: '리바운드', assists: '어시스트' };

/** 선택 지표 순으로 선수 리더와 전체 예시 선수 기록을 표시합니다. */
function renderPlayerRecords() {
    const metric = state.leaderMetric;
    const players = getPlayerRecords(state.season).sort((first, second) => second[metric] - first[metric]);
    document.getElementById('season-caption').textContent = `${SEASONS[state.season].label} 정규시즌`;
    document.getElementById('records-sort-caption').textContent = `${METRIC_LABELS[metric]} 높은 순 · ${players.length}명 · 예시`;
    renderPlayerLeaders(document.getElementById('player-leader-list'), players, metric);
    updateSegmentedControl('[data-leader]', metric);
    document.querySelectorAll('[data-stat]').forEach((heading) => {
        const selected = heading.dataset.stat === metric;
        heading.classList.toggle('is-sorted', selected);
        heading.setAttribute('aria-sort', selected ? 'descending' : 'none');
    });
    document.getElementById('player-records-body').innerHTML = players.map((player, index) => {
        const team = getTeam(player.teamId);
        return `<tr><td>${index + 1}</td><th scope="row">${escapeHtml(player.name)}</th>
            <td><a class="standings-team" href="${escapeHtml(pageHref('teamManagement', { team: team.id, season: state.season }))}">
                ${teamBadge(team, 'small')}<span>${team.city} <strong>${team.name}</strong></span></a></td>
            ${['points', 'rebounds', 'assists'].map((key) => `<td class="${key === metric ? 'accent-text' : ''}">${player[key].toFixed(1)}</td>`).join('')}
        </tr>`;
    }).join('');
    persistPageState(state, { metric });
}

/** 시즌 변경을 선수별 예시 기록과 공유 설정에 반영합니다. */
function handleSeasonChange(season) {
    state.season = season;
    renderPlayerRecords();
}

/** 리더 순위 버튼의 선택을 기록 정렬에 반영합니다. */
function handleMetricSelection(event) {
    const button = event.target.closest('[data-leader]');
    if (!button) return;
    state.leaderMetric = button.dataset.leader;
    renderPlayerRecords();
}

mountAppShell('players', state, handleSeasonChange);
renderPlayerRecords();
document.querySelector('.leaders-panel').addEventListener('click', handleMetricSelection);
