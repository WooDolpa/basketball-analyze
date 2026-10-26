import { icon, pageHref, teamBadge } from './ui.js';
import { escapeHtml } from '../utils/format.js';

/** 대시보드의 이동 링크 또는 일정 페이지의 경기 선택 카드를 만듭니다. */
export function matchCard(match, { selected = false, href = null } = {}) {
    const attributes = href
        ? `href="${escapeHtml(href)}"`
        : `type="button" data-match="${match.id}" aria-pressed="${selected}"`;
    const tag = href ? 'a' : 'button';
    return `<${tag} ${attributes} class="match-card ${selected ? 'is-selected' : ''}"
                aria-label="${escapeHtml(match.home.fullName)} 대 ${escapeHtml(match.away.fullName)} 분석 보기">
        <span class="match-card-top">
            <span class="match-status ${match.status === 'finished' ? 'finished' : ''}">${match.status === 'finished' ? '경기 종료' : '경기 예정'}</span>
            <span>${match.time}</span>
        </span>
        <span class="match-card-team">${teamBadge(match.home)}
            <span>${match.home.city} <strong>${match.home.name}</strong></span>
            <b>${match.status === 'finished' ? match.homeScore : `${match.homeProbability}%`}</b>
        </span>
        <span class="match-card-team">${teamBadge(match.away)}
            <span>${match.away.city} <strong>${match.away.name}</strong></span>
            <b>${match.status === 'finished' ? match.awayScore : `${100 - match.homeProbability}%`}</b>
        </span>
        <span class="match-card-footer"><span>${match.status === 'finished' ? '경기 분석 보기' : '예상 승리 확률'}</span>${icon('arrow-up-right')}</span>
    </${tag}>`;
}

/** 선택 경기의 확률 프리뷰와 독립 팀관리 페이지 링크를 표시합니다. */
export function renderMatchPreview(container, match, seasonId) {
    const probability = match.homeProbability;
    const teamHref = pageHref('teamManagement', { team: match.home.id, season: seasonId });
    container.innerHTML = `
        <div class="preview-heading">
            <div><span class="eyebrow">MATCH PREVIEW</span><h2>선택 경기 프리뷰</h2></div>
            <span class="preview-tag">예측 예시 ${icon('spark')}</span>
        </div>
        <div class="match-showdown">
            <div class="showdown-team">${teamBadge(match.home, 'large')}<span class="home-away">HOME</span>
                <h3>${match.home.city} ${match.home.name}</h3><span>${match.home.wins}승 ${match.home.losses}패</span></div>
            <div class="showdown-center"><span class="versus">VS</span>
                <span class="tipoff">${match.status === 'finished' ? 'FINAL' : 'TIP-OFF'} <strong>${match.time}</strong></span>
                <span class="court-date">${match.date.getMonth() + 1}월 ${match.date.getDate()}일 · 정규시즌</span></div>
            <div class="showdown-team">${teamBadge(match.away, 'large')}<span class="home-away">AWAY</span>
                <h3>${match.away.city} ${match.away.name}</h3><span>${match.away.wins}승 ${match.away.losses}패</span></div>
        </div>
        <div class="probability-heading"><span>예상 승리 확률 <span class="inline-demo">DEMO</span></span>
            <span class="confidence-label"><i></i>확신도 ${match.confidence}</span></div>
        <div class="probability-labels">
            <span class="${probability >= 50 ? 'accent-text' : ''}">${match.home.name} <strong>${probability}<small>%</small></strong></span>
            <span class="${probability < 50 ? 'accent-text' : ''}"><strong>${100 - probability}<small>%</small></strong> ${match.away.name}</span>
        </div>
        <div class="probability-bar" role="img" aria-label="예시 승리 확률 ${match.home.name} ${probability}퍼센트, ${match.away.name} ${100 - probability}퍼센트">
            <span style="width:${probability}%"></span><i></i></div>
        <div class="preview-bottom"><span>${icon('location')}${escapeHtml(match.home.stadium)}</span>
            <a href="${escapeHtml(teamHref)}" class="text-button">홈팀 정보 보기 ${icon('arrow-right')}</a></div>`;
}

/** 경기 예측의 예시 근거를 재사용 가능한 요약 목록으로 표시합니다. */
export function renderMatchInsights(container, match) {
    const favorite = match.homeProbability >= 50 ? match.home : match.away;
    container.innerHTML = `
        <div class="insight-item"><span class="insight-number">01</span>
            <div><p>${favorite.name}, 근소한 우세</p><span>예시 모델의 예상 승리 확률</span></div>
            <strong class="accent-text">${Math.max(match.homeProbability, 100 - match.homeProbability)}<small>%</small></strong></div>
        <div class="insight-item"><span class="insight-number">02</span>
            <div><p>최근 경기의 상승 흐름</p><span>${favorite.name} 최근 5경기 예시 성적</span></div>
            <strong>${[...favorite.form].filter((result) => result === 'W').length}<small>승</small></strong></div>
        <div class="insight-item"><span class="insight-number">03</span>
            <div><p>홈코트 어드밴티지</p><span>${match.home.name} 홈 경기 승률 예시</span></div>
            <strong>66.7<small>%</small></strong></div>`;
}
