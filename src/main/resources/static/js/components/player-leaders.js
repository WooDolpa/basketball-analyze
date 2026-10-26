import { getTeam } from '../data/demo-data.js';
import { escapeHtml } from '../utils/format.js';

/** 선수 지표별 상위 다섯 명을 공통 리더 카드로 표시합니다. */
export function renderPlayerLeaders(container, players, metric) {
    const units = { points: 'PTS', rebounds: 'REB', assists: 'AST' };
    container.innerHTML = players.slice(0, 5).map((player, index) => {
        const team = getTeam(player.teamId);
        return `<article class="player-card ${index === 0 ? 'is-leading' : ''}">
            <span class="player-rank">${String(index + 1).padStart(2, '0')}</span>
            <div class="player-identity">
                <div class="jersey-avatar" style="--team-color:${team.color}" aria-hidden="true"><span>${player.number}</span></div>
                <div><h3>${escapeHtml(player.name)}</h3><p><i style="background:${team.color}"></i>${team.city} ${team.name}</p></div>
            </div>
            <div class="player-value"><strong>${player[metric].toFixed(1)}</strong><span>${units[metric]}</span></div>
        </article>`;
    }).join('');
}
