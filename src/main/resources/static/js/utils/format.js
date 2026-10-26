/** HTML 문자열 안에 표시할 텍스트의 특수 문자를 이스케이프합니다. */
export function escapeHtml(value) {
    const entities = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
    return String(value).replace(/[&<>"']/g, (character) => entities[character]);
}

/** 날짜를 한국어 월·일·요일로 표시합니다. */
export function formatKoreanDate(date) {
    return new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' }).format(date);
}

/** 날짜를 정렬과 파일명에 사용할 수 있는 형식으로 표시합니다. */
export function formatDateKey(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

/** CSV 셀의 구분 기호와 따옴표를 처리합니다. */
export function formatCsvCell(value) {
    return `"${String(value ?? '').replaceAll('"', '""')}"`;
}
