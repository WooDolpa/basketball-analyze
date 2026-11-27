const KOREA_TIME_ZONE = 'Asia/Seoul';
const kstTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: KOREA_TIME_ZONE,
});
const kstDateFormatter = new Intl.DateTimeFormat('en-CA', {
    year: 'numeric', month: '2-digit', day: '2-digit', timeZone: KOREA_TIME_ZONE,
});
const MATCH_STATUS_LABELS = {
    SCHEDULED: '경기 예정',
    LIVE: '경기 진행 중',
    FINISHED: '경기 종료',
    POSTPONED: '경기 연기',
    CANCELLED: '경기 취소',
};

/** HTML 문자열 안에 표시할 텍스트의 특수 문자를 이스케이프합니다. */
export function escapeHtml(value) {
    const entities = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
    return String(value).replace(/[&<>"']/g, (character) => entities[character]);
}

/** 날짜를 한국어 월·일·요일로 표시합니다. */
export function formatKoreanDate(date, timeZone) {
    return new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'long', timeZone }).format(date);
}

/** 날짜와 시간을 한국시간 기준 YYYY-MM-DD로 표시합니다. */
export function formatKoreanDateKey(date) {
    const parts = Object.fromEntries(kstDateFormatter.formatToParts(date).map(({ type, value }) => [type, value]));
    return `${parts.year}-${parts.month}-${parts.day}`;
}

/** API의 시작 일시를 한국시간 기준 HH:mm으로 표시합니다. */
export function formatKoreanTime(dateTime) {
    return kstTimeFormatter.format(new Date(dateTime));
}

/** API의 경기 상태를 화면과 CSV에서 사용할 한국어로 표시합니다. */
export function formatMatchStatus(status) {
    return Object.hasOwn(MATCH_STATUS_LABELS, status) ? MATCH_STATUS_LABELS[status] : '경기 상태 미확인';
}

/** 날짜를 정렬과 파일명에 사용할 수 있는 형식으로 표시합니다. */
export function formatDateKey(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

/** 날짜 키에서 차트에 표시할 월과 일을 만듭니다. */
export function formatMonthDay(dateKey) {
    const [, month, day] = dateKey.split('-');
    return `${Number(month)}.${day}`;
}

/** CSV 셀의 구분 기호와 따옴표를 처리합니다. */
export function formatCsvCell(value) {
    return `"${String(value ?? '').replaceAll('"', '""')}"`;
}
