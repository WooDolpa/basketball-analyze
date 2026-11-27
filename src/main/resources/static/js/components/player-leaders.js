import { escapeHtml } from '../utils/format.js';
import { teamBadge } from './ui.js';

const averageFormat = new Intl.NumberFormat('ko-KR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** 평균은 한 자리 소수로, 미확인 기록은 대시로 표시합니다. */
export function formatPlayerAverage(value) {
    return value == null ? '—' : averageFormat.format(value);
}

/** 선수 지표별 상위 다섯 명을 공통 리더 카드로 표시합니다. */
export function renderPlayerLeaders(container, players, metric) {
    const units = { points: 'PTS', rebounds: 'REB', assists: 'AST' };
    container.innerHTML = players.slice(0, 5).map((player, index) => {
        const [team] = player.teams;
        const teamNames = player.teams.map((entry) => entry.name).join(' · ');
        const playerBadge = player.number == null ? teamBadge(team, 'small')
            : `<div class="jersey-avatar" style="--team-color:${team.color}" aria-hidden="true"><span>${escapeHtml(player.number)}</span></div>`;
        return `<article class="player-card ${index === 0 ? 'is-leading' : ''}">
            <span class="player-rank">${String(index + 1).padStart(2, '0')}</span>
            <div class="player-identity">
                ${playerBadge}
                <div><h3>${escapeHtml(player.name)}</h3><p title="${escapeHtml(teamNames)}">${escapeHtml(teamNames)}</p></div>
            </div>
            <div class="player-value"><strong>${formatPlayerAverage(player[metric])}</strong><span>${units[metric]}</span></div>
        </article>`;
    }).join('');
}
