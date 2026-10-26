import { formatCsvCell, formatDateKey } from './format.js';

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
    const csv = '\uFEFF' + [headers, ...rows].map((row) => row.map(formatCsvCell).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `courtside-demo-${formatDateKey(matches[0].date)}.csv`;
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}
