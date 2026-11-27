import { renderTrendChart } from './charts.js';
import { escapeHtml, formatMonthDay } from '../utils/format.js';

/** 경기 결과에 해당하는 공통 승패 배지를 만듭니다. */
export function teamResultBadge(won) {
    return `<span class="result-badge ${won ? 'win' : 'loss'}">${won ? '승' : '패'}</span>`;
}

/** 최근 경기의 평균과 승패를 계산합니다. */
function summarizeTrend(records) {
    return {
        scored: records.reduce((sum, record) => sum + record.scored, 0) / records.length,
        conceded: records.reduce((sum, record) => sum + record.conceded, 0) / records.length,
        wins: records.filter((record) => record.won).length,
    };
}

/** 팀관리 화면의 차트와 요약을 함께 갱신합니다. */
export function renderTeamTrend(records) {
    const chart = document.getElementById('trend-chart');
    const detail = document.getElementById('chart-detail');
    const summaryPanel = document.getElementById('trend-summary');
    const scoreNotice = document.getElementById('trend-score-notice');
    const scoredGames = records.filter((record) => record.scored != null && record.conceded != null);
    scoreNotice.hidden = scoredGames.length === 0 || scoredGames.length === records.length;
    scoreNotice.textContent = `득점과 실점이 확인된 ${scoredGames.length}경기를 차트에 표시합니다.`;
    detail.hidden = scoredGames.length === 0;
    summaryPanel.hidden = scoredGames.length === 0;
    if (!scoredGames.length) {
        chart.textContent = records.length ? '확인된 경기 점수가 없습니다.' : '표시할 종료 경기 기록이 없습니다.';
        return;
    }

    const summary = summarizeTrend(scoredGames);
    renderTrendChart(chart, scoredGames, renderChartDetail);
    const margin = summary.scored - summary.conceded;
    summaryPanel.innerHTML = `
        <div><span>평균 득점</span><strong>${summary.scored.toFixed(1)}<small>점</small></strong></div>
        <div><span>평균 실점</span><strong>${summary.conceded.toFixed(1)}<small>점</small></strong></div>
        <div><span>평균 득실 마진</span><strong class="accent-text">${margin >= 0 ? '+' : ''}${margin.toFixed(1)}<small>점</small></strong></div>
        <div><span>최근 ${scoredGames.length}경기</span><strong>${summary.wins}<small>승</small> ${scoredGames.length - summary.wins}<small>패</small></strong></div>`;
}

/** 차트에서 선택한 경기의 상대와 점수를 설명합니다. */
function renderChartDetail(record) {
    document.getElementById('chart-detail').innerHTML = `
        <span>${formatMonthDay(record.date)} <strong>vs ${escapeHtml(record.opponent)}</strong></span>
        <span><i class="legend-dot scored"></i>${escapeHtml(record.scored)} <span class="detail-divider">:</span> ${escapeHtml(record.conceded)}
            ${teamResultBadge(record.won)}</span>`;
}
