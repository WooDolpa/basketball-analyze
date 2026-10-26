import { createSparkline } from './charts.js';
import { icon } from './ui.js';
import { escapeHtml } from '../utils/format.js';

/** 재사용 가능한 지표 카드와 선택적 미니 추세선을 표시합니다. */
export function renderMetrics(container, metrics) {
    container.innerHTML = metrics.map((metric) => `
        <article class="metric-card">
            <div class="metric-top"><span>${escapeHtml(metric.label)}</span>${icon(metric.icon)}</div>
            <div class="metric-value ${metric.accent ? 'accent-text' : ''}">${escapeHtml(metric.value)}<span>${escapeHtml(metric.unit ?? '')}</span></div>
            <div class="metric-bottom"><span>${escapeHtml(metric.detail)}</span><span class="sparkline-slot"></span></div>
        </article>`).join('');
    metrics.forEach((metric, index) => {
        if (metric.values) container.querySelectorAll('.sparkline-slot')[index].append(
            createSparkline(metric.values, metric.accent ? 'var(--accent)' : 'var(--muted-line)'));
    });
}
