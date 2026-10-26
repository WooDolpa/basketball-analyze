const SVG_NAMESPACE = 'http://www.w3.org/2000/svg';

/** SVG 요소와 속성을 생성합니다. */
function createSvgElement(tag, attributes = {}, content) {
    const element = document.createElementNS(SVG_NAMESPACE, tag);
    for (const [name, value] of Object.entries(attributes)) {
        element.setAttribute(name, String(value));
    }
    if (content !== undefined) element.textContent = content;
    return element;
}

/** 요약 카드에 사용하는 장식용 미니 추세선을 생성합니다. */
export function createSparkline(values, color = 'var(--accent)') {
    const svg = createSvgElement('svg', { viewBox: '0 0 104 38', 'aria-hidden': 'true', class: 'sparkline' });
    const min = Math.min(...values) - 2;
    const max = Math.max(...values) + 2;
    const points = values.map((value, index) => `${index * 104 / (values.length - 1)},${34 - (value - min) / (max - min) * 29}`).join(' ');
    svg.append(createSvgElement('polyline', { points, fill: 'none', stroke: color, 'stroke-width': 2, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }));
    return svg;
}

/** 키보드와 포인터로 경기별 값을 살펴볼 수 있는 추세 차트를 그립니다. */
export function renderTrendChart(container, records, onSelect) {
    const width = 680;
    const height = 246;
    const bounds = { left: 34, right: 16, top: 18, bottom: 38 };
    const allValues = records.flatMap((record) => [record.scored, record.conceded]);
    const minimum = Math.floor((Math.min(...allValues) - 5) / 10) * 10;
    const maximum = Math.ceil((Math.max(...allValues) + 5) / 10) * 10;
    const plotWidth = width - bounds.left - bounds.right;
    const plotHeight = height - bounds.top - bounds.bottom;
    const svg = createSvgElement('svg', { viewBox: `0 0 ${width} ${height}`, role: 'group', 'aria-label': `최근 ${records.length}경기 득점과 실점. 각 지점을 선택하면 상세 기록이 표시됩니다.` });
    const points = records.map((record, index) => ({
        x: bounds.left + index * plotWidth / (records.length - 1),
        scoredY: bounds.top + (maximum - record.scored) / (maximum - minimum) * plotHeight,
        concededY: bounds.top + (maximum - record.conceded) / (maximum - minimum) * plotHeight,
    }));

    for (let value = minimum; value <= maximum; value += 10) {
        const y = bounds.top + (maximum - value) / (maximum - minimum) * plotHeight;
        svg.append(createSvgElement('line', { x1: bounds.left, y1: y, x2: width - bounds.right, y2: y, class: 'chart-gridline' }));
        svg.append(createSvgElement('text', { x: bounds.left - 12, y: y + 4, 'text-anchor': 'end', class: 'chart-axis-label' }, value));
    }

    const fillPoints = `${points[0].x},${height - bounds.bottom} ${points.map((point) => `${point.x},${point.scoredY}`).join(' ')} ${points.at(-1).x},${height - bounds.bottom}`;
    svg.append(createSvgElement('polygon', { points: fillPoints, class: 'chart-area' }));
    svg.append(createSvgElement('polyline', { points: points.map((point) => `${point.x},${point.concededY}`).join(' '), class: 'chart-line chart-line-conceded' }));
    svg.append(createSvgElement('polyline', { points: points.map((point) => `${point.x},${point.scoredY}`).join(' '), class: 'chart-line chart-line-scored' }));

    const selectionLine = createSvgElement('line', { y1: bounds.top, y2: height - bounds.bottom, class: 'chart-selection-line' });
    svg.append(selectionLine);

    /** 선택 지점의 강조 표시와 외부 상세 설명을 갱신합니다. */
    function selectPoint(index) {
        selectionLine.setAttribute('x1', points[index].x);
        selectionLine.setAttribute('x2', points[index].x);
        svg.querySelectorAll('.chart-point-group').forEach((group, groupIndex) => {
            group.classList.toggle('is-selected', groupIndex === index);
            group.setAttribute('aria-pressed', String(groupIndex === index));
        });
        onSelect(records[index], index);
    }

    for (const [index, point] of points.entries()) {
        const record = records[index];
        const dateLabel = `${record.date.getMonth() + 1}.${String(record.date.getDate()).padStart(2, '0')}`;
        svg.append(createSvgElement('text', { x: point.x, y: height - 10, 'text-anchor': 'middle', class: 'chart-axis-label' }, dateLabel));
        const group = createSvgElement('g', { class: 'chart-point-group', tabindex: 0, role: 'button', 'aria-pressed': false, 'aria-label': `${dateLabel} ${record.opponent}전, 득점 ${record.scored}, 실점 ${record.conceded}` });
        group.append(createSvgElement('rect', { x: point.x - 14, y: bounds.top, width: 28, height: plotHeight, fill: 'transparent' }));
        group.append(createSvgElement('circle', { cx: point.x, cy: point.concededY, r: 3.5, class: 'chart-dot-conceded' }));
        group.append(createSvgElement('circle', { cx: point.x, cy: point.scoredY, r: 4.5, class: 'chart-dot-scored' }));
        group.addEventListener('pointerenter', () => selectPoint(index));
        group.addEventListener('focus', () => selectPoint(index));
        group.addEventListener('click', () => selectPoint(index));
        group.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                selectPoint(index);
            }
        });
        svg.append(group);
    }

    container.replaceChildren(svg);
    selectPoint(records.length - 1);
}
