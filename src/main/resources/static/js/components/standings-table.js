import { formDots, pageHref, teamBadge } from './ui.js';
import { escapeHtml } from '../utils/format.js';

/** 순위 행의 팀명을 표시하고 팀관리 화면에 등록된 팀을 연결합니다. */
function renderTeamLink(team, seasonId) {
    const name = escapeHtml(team.name);
    const label = `${teamBadge(team, 'small')}<strong>${name}</strong>`;
    if (!team.id) return `<span class="standings-team">${label}</span>`;

    const href = escapeHtml(pageHref('teamManagement', { team: team.id, season: seasonId }));
    return `<a class="standings-team" href="${href}" aria-label="${name} 팀관리 보기">${label}</a>`;
}

/** 반환된 순위를 유지하고, 최근 경기 결과와 플레이오프 순위 기준선을 표시합니다. */
export function renderStandingsTable(container, standings, seasonId) {
    container.innerHTML = standings.map((standing, index) => {
        const { rank, team, games, wins, losses, winRate, form } = standing;
        const nextRank = standings[index + 1]?.rank;
        return `<tr class="${rank <= 6 && nextRank > 6 ? 'playoff-cutoff' : ''}">
            <td><span class="rank-number ${rank <= 3 ? 'top-rank' : ''}">${escapeHtml(rank)}</span></td>
            <th scope="row">${renderTeamLink(team, seasonId)}</th>
            <td>${escapeHtml(games)}</td><td>${escapeHtml(wins)}</td><td>${escapeHtml(losses)}</td>
            <td class="win-rate">${escapeHtml(winRate)}</td>
            <td>${form ? formDots(form) : '—'}</td>
        </tr>`;
    }).join('');
}
