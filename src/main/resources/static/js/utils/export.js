import { formatCsvCell, formatDateKey, formatKoreanTime, formatMatchStatus } from './format.js';

/** 표의 머리글과 행을 한글을 지원하는 UTF-8 CSV 파일로 저장합니다. */
function downloadCsv(headers, rows, filename) {
    const csv = '\uFEFF' + [headers, ...rows].map((row) => row.map(formatCsvCell).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** 현재 날짜의 예시 경기 기록과 확률을 UTF-8 CSV로 저장합니다. */
export function exportMatchReport(matches) {
    if (!matches.length) return;
    const headers = ['데이터 구분', '시즌', '날짜', '시작 시간', '홈 팀', '원정 팀', '상태', '홈 점수', '원정 점수', '홈 승리 확률(%)', '원정 승리 확률(%)'];
    const rows = matches.map((match) => [
        'UI 예시 데이터 - 실제 기록 아님', match.season,
        formatDateKey(match.date), match.time, match.home.fullName, match.away.fullName,
        match.status === 'finished' ? '종료' : '예정',
        match.status === 'finished' ? match.homeScore : '',
        match.status === 'finished' ? match.awayScore : '',
        match.homeProbability, 100 - match.homeProbability,
    ]);
    downloadCsv(headers, rows, `courtside-demo-${formatDateKey(matches[0].date)}.csv`);
}

/** 스프레드시트에서 외부 팀 이름을 수식으로 해석하지 않도록 텍스트로 표시합니다. */
function formatTeamNameForCsv(name) {
    return /^(?:[\t\r\n]|\s*[=+@-])/.test(name) ? `'${name}` : name;
}

/** 선택 날짜의 실제 경기 일정과 쿼터·합계 점수를 CSV로 저장합니다. */
export function exportScheduleReport(matches, seasonId, date) {
    if (!matches.length) return;
    const headers = ['시즌', '날짜', '시작 시간(KST)', '경기 코드', '홈 팀', '원정 팀', '상태', '홈 합계', '원정 합계',
        '홈 1Q', '원정 1Q', '홈 2Q', '원정 2Q', '홈 3Q', '원정 3Q', '홈 4Q', '원정 4Q'];
    const rows = matches.map((match) => [
        seasonId, date, formatKoreanTime(match.startsAt), match.matchCode,
        formatTeamNameForCsv(match.homeTeam.name), formatTeamNameForCsv(match.awayTeam.name),
        formatMatchStatus(match.status), match.homeScore, match.awayScore,
        ...match.quarters.flatMap((quarter) => [quarter.homeScore, quarter.awayScore]),
    ]);
    downloadCsv(headers, rows, `courtside-schedule-${date}.csv`);
}
