import { teamBadge } from './ui.js';
import { escapeHtml, formatKoreanTime, formatMatchStatus } from '../utils/format.js';

/** 일정의 시작 시간과 상태를 카드와 상세에서 같은 형식으로 표시합니다. */
function scheduleMatchTime(match) {
    const statusClass = match.status === 'FINISHED' ? ' finished' : '';
    return `<span class="match-status${statusClass}">${formatMatchStatus(match.status)}</span>
        <time datetime="${escapeHtml(match.startsAt)}">${formatKoreanTime(match.startsAt)}</time>`;
}

/** 일정 페이지에서 홈·원정팀, 합계 점수, 시작 시간을 표시하는 경기 카드를 만듭니다. */
export function scheduleMatchCard(match, selected) {
    const { homeTeam, awayTeam, homeScore, awayScore } = match;
    return `<button type="button" class="match-card schedule-match-card${selected ? ' is-selected' : ''}"
            data-match="${escapeHtml(match.matchCode)}" aria-pressed="${selected}" aria-controls="featured-match">
        <span class="match-card-top">${scheduleMatchTime(match)}</span>
        <span class="match-card-team">${teamBadge({ mark: homeTeam.code, color: 'var(--accent)' })}
            <span><strong>${escapeHtml(homeTeam.name)}</strong></span><b>${homeScore ?? '—'}</b>
        </span>
        <span class="match-card-team">${teamBadge({ mark: awayTeam.code, color: 'var(--text-secondary)' })}
            <span><strong>${escapeHtml(awayTeam.name)}</strong></span><b>${awayScore ?? '—'}</b>
        </span>
    </button>`;
}

/** 선택 경기의 1~4쿼터 점수와 양 팀 합계를 표로 표시합니다. */
export function renderScheduleMatchDetail(container, match) {
    const teams = [
        { name: match.homeTeam.name, side: '홈', score: 'homeScore' },
        { name: match.awayTeam.name, side: '원정', score: 'awayScore' },
    ];
    const quarterHeadings = match.quarters.map(({ quarter }) => `<th scope="col">${quarter}Q</th>`).join('');
    const teamRows = teams.map(({ name, side, score }) => `<tr>
        <th scope="row"><span class="schedule-team-side">${side}</span>${escapeHtml(name)}</th>
        ${match.quarters.map((quarter) => `<td>${quarter[score] ?? '—'}</td>`).join('')}
        <td class="schedule-total-score">${match[score] ?? '—'}</td>
    </tr>`).join('');
    container.innerHTML = `
        <div class="schedule-detail-heading">
            <h2 id="schedule-detail-title">${escapeHtml(match.homeTeam.name)} <span>vs</span> ${escapeHtml(match.awayTeam.name)}</h2>
            <div class="schedule-detail-meta">${scheduleMatchTime(match)}</div>
        </div>
        <div class="schedule-score-wrap">
            <table class="schedule-score-table">
                <caption class="sr-only">홈·원정팀 쿼터별 점수</caption>
                <thead><tr><th scope="col">팀</th>${quarterHeadings}<th scope="col">합계</th></tr></thead>
                <tbody>${teamRows}</tbody>
            </table>
        </div>`;
}

/** Elo와 승률을 소수점 첫째 자리까지 표시하고 비어 있는 값은 대시로 표시합니다. */
function formatPredictionNumber(value, suffix = '') {
    return Number.isFinite(value) ? `${value.toFixed(1)}${suffix}` : '—';
}

/** 오늘 예정 경기와 API가 반환한 양 팀의 승률로 선택 버튼을 구성합니다. */
export function predictionMatchCard(match, index) {
    const { homeTeam, awayTeam, startsAt, prediction } = match;
    const tipoff = formatKoreanTime(startsAt);
    return `<button type="button" class="match-card prediction-card" data-prediction-index="${index}" aria-pressed="false">
        <span class="match-card-top">
            <span class="match-status">경기 예정</span>
            <time datetime="${escapeHtml(startsAt)}">${tipoff}</time>
        </span>
        <span class="match-card-team">
            ${teamBadge({ mark: homeTeam.code, color: 'var(--prediction-home-mark)' })}
            <span><strong>${escapeHtml(homeTeam.name)}</strong></span>
            <b>${formatPredictionNumber(prediction.homeWinPercentage, '%')}</b>
        </span>
        <span class="match-card-team">
            ${teamBadge({ mark: awayTeam.code, color: 'var(--prediction-away-mark)' })}
            <span><strong>${escapeHtml(awayTeam.name)}</strong></span>
            <b>${formatPredictionNumber(prediction.awayWinPercentage, '%')}</b>
        </span>
    </button>`;
}

/** 선택 경기의 예상 승률과 API가 반환한 Elo 및 예측 근거를 표시합니다. */
export function renderPredictionDetail(preview, insights, match) {
    const { homeTeam, awayTeam, startsAt, prediction } = match;
    const { homeWinPercentage, awayWinPercentage, homeElo, awayElo, reason } = prediction;
    const homeName = escapeHtml(homeTeam.name);
    const awayName = escapeHtml(awayTeam.name);
    const tipoff = formatKoreanTime(startsAt);
    const homeWin = formatPredictionNumber(homeWinPercentage, '%');
    const awayWin = formatPredictionNumber(awayWinPercentage, '%');
    const homeLeading = homeWinPercentage > awayWinPercentage;
    const awayLeading = awayWinPercentage > homeWinPercentage;
    const forecast = prediction.status === 'AVAILABLE' ? `
        <p class="prediction-label">예상 승리 확률</p>
        <div class="prediction-probabilities">
            <div><span>홈 · ${homeName}</span><strong class="${homeLeading ? 'accent-text' : ''}">${homeWin}</strong></div>
            <div><span>원정 · ${awayName}</span><strong class="${awayLeading ? 'accent-text' : ''}">${awayWin}</strong></div>
        </div>
        <div class="prediction-bar${homeLeading ? ' home-leading' : ''}${awayLeading ? ' away-leading' : ''}" role="img"
             aria-label="${homeName} ${homeWin}, ${awayName} ${awayWin}">
            <span style="width:${homeWin}"></span>
        </div>` : '<p class="prediction-label">승률을 계산할 경기 기록이 부족합니다.</p>';

    preview.innerHTML = `
        <div class="prediction-heading">
            <h3 id="featured-match-title">${homeName} <span>vs</span> ${awayName}</h3>
            <span><time datetime="${escapeHtml(startsAt)}">${tipoff}</time> 시작</span>
        </div>
        ${forecast}`;
    insights.innerHTML = `
        <dl class="prediction-ratings">
            <div><dt>홈 Elo</dt><dd>${formatPredictionNumber(homeElo)}</dd></div>
            <div><dt>원정 Elo</dt><dd>${formatPredictionNumber(awayElo)}</dd></div>
        </dl>
        <p class="prediction-reason">${escapeHtml(reason)}</p>`;
}
