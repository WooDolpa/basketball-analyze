import { formDots, pageHref, teamBadge } from './ui.js';
import { getStandings } from '../data/demo-data.js';
import { escapeHtml } from '../utils/format.js';

/** 순위표를 표시하고 각 팀을 해당 팀관리 페이지로 연결합니다. */
export function renderStandingsTable(container, seasonId) {
    container.innerHTML = getStandings(seasonId).map((team, index) => `
        <tr class="${index === 5 ? 'playoff-cutoff' : ''}">
            <td><span class="rank-number ${index < 3 ? 'top-rank' : ''}">${index + 1}</span></td>
            <th scope="row"><a class="standings-team" href="${escapeHtml(pageHref('teamManagement', { team: team.id, season: seasonId }))}"
                aria-label="${team.city} ${team.name} 팀관리 보기">
                ${teamBadge(team, 'small')}<span>${team.city} <strong>${team.name}</strong></span></a></th>
            <td>${team.wins + team.losses}</td><td>${team.wins}</td><td>${team.losses}</td>
            <td class="win-rate">${(team.wins / (team.wins + team.losses)).toFixed(3)}</td>
            <td>${formDots(team.form)}</td>
        </tr>`).join('');
}
