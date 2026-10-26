import { renderTrendChart } from './charts.js';
import { escapeHtml } from '../utils/format.js';
import { getTeamTrend } from '../data/demo-data.js';
import { updateSegmentedControl } from './ui.js';

/** 최근 경기의 평균과 승패를 계산합니다. */
export function summarizeTrend(records) {
    return {
        scored: records.reduce((sum, record) => sum + record.scored, 0) / records.length,
        conceded: records.reduce((sum, record) => sum + record.conceded, 0) / records.length,
        wins: records.filter((record) => record.scored > record.conceded).length,
    };
}

/** 팀관리 화면의 차트와 요약을 함께 갱신합니다. */
export function renderTeamTrend(state) {
    const records = getTeamTrend(state.teamId, state.period, state.season);
    const summary = summarizeTrend(records);
    renderTrendChart(document.getElementById('trend-chart'), records, renderChartDetail);
    const margin = summary.scored - summary.conceded;
    document.getElementById('trend-summary').innerHTML = `
        <div><span>평균 득점</span><strong>${summary.scored.toFixed(1)}<small>점</small></strong></div>
        <div><span>평균 실점</span><strong>${summary.conceded.toFixed(1)}<small>점</small></strong></div>
        <div><span>평균 득실 마진</span><strong class="accent-text">${margin >= 0 ? '+' : ''}${margin.toFixed(1)}<small>점</small></strong></div>
        <div><span>최근 ${state.period}경기</span><strong>${summary.wins}<small>승</small> ${records.length - summary.wins}<small>패</small></strong></div>`;
    updateSegmentedControl('[data-period]', state.period);
    return records;
}

/** 차트에서 선택한 경기의 상대와 점수를 설명합니다. */
function renderChartDetail(record) {
    document.getElementById('chart-detail').innerHTML = `
        <span>${record.date.getMonth() + 1}.${String(record.date.getDate()).padStart(2, '0')} <strong>vs ${escapeHtml(record.opponent)}</strong></span>
        <span><i class="legend-dot scored"></i>${record.scored} <span class="detail-divider">:</span> ${record.conceded}
            <span class="result-badge ${record.scored > record.conceded ? 'win' : 'loss'}">${record.scored > record.conceded ? '승' : '패'}</span></span>`;
}
