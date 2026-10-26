/** 실제 기록과 분리한 화면 검토용 팀 데이터입니다. */
export const TEAMS = [
    { id: 'sk', city: '서울', name: 'SK', fullName: '서울 SK 나이츠', mark: 'SK', color: '#e96c62', wins: 7, losses: 2, form: 'WWLWW', stadium: '잠실학생체육관' },
    { id: 'lg', city: '창원', name: 'LG', fullName: '창원 LG 세이커스', mark: 'LG', color: '#c97393', wins: 6, losses: 2, form: 'WLWWW', stadium: '창원체육관' },
    { id: 'kt', city: '수원', name: 'KT', fullName: '수원 KT 소닉붐', mark: 'KT', color: '#e06c60', wins: 6, losses: 3, form: 'WWLWW', stadium: '수원 KT 소닉붐 아레나' },
    { id: 'db', city: '원주', name: 'DB', fullName: '원주 DB 프로미', mark: 'DB', color: '#71bca0', wins: 5, losses: 3, form: 'LWWLW', stadium: '원주DB프로미아레나' },
    { id: 'kgc', city: '안양', name: '정관장', fullName: '안양 정관장 레드부스터스', mark: 'JKJ', color: '#d28076', wins: 5, losses: 4, form: 'WLWLW', stadium: '안양 정관장 아레나' },
    { id: 'kcc', city: '부산', name: 'KCC', fullName: '부산 KCC 이지스', mark: 'KCC', color: '#7b9ac9', wins: 4, losses: 4, form: 'LWWLL', stadium: '부산사직체육관' },
    { id: 'hd', city: '울산', name: '현대모비스', fullName: '울산 현대모비스 피버스', mark: 'MOB', color: '#db9a6c', wins: 4, losses: 5, form: 'WLLWL', stadium: '울산동천체육관' },
    { id: 'sono', city: '고양', name: '소노', fullName: '고양 소노 스카이거너스', mark: 'SONO', color: '#86b6d5', wins: 3, losses: 5, form: 'LLWWL', stadium: '고양소노아레나' },
    { id: 'gas', city: '대구', name: '한국가스공사', fullName: '대구 한국가스공사 페가수스', mark: 'GAS', color: '#9ca0d1', wins: 2, losses: 6, form: 'LWLLL', stadium: '대구체육관' },
    { id: 'ss', city: '서울', name: '삼성', fullName: '서울 삼성 썬더스', mark: 'SS', color: '#719ed8', wins: 1, losses: 9, form: 'LLLWL', stadium: '잠실실내체육관' },
];

export const SEASONS = {
    '2026-2027': { label: '2026–2027', year: 2026, completedGames: 43, accuracy: 68.4, averageScore: 83.6 },
    '2025-2026': { label: '2025–2026', year: 2025, completedGames: 33, accuracy: 65.7, averageScore: 81.9 },
};

export const DEFAULT_PREFERENCES = { season: '2026-2027', dayIndex: 4, teamId: 'kt', period: 10, leaderMetric: 'points' };

const FIXTURE_PLANS = [
    [['sk', 'lg'], ['db', 'sono'], ['kt', 'ss']],
    [['kgc', 'gas'], ['hd', 'kcc']],
    [['lg', 'db'], ['ss', 'sk'], ['sono', 'kt']],
    [['kcc', 'kgc'], ['gas', 'hd']],
    [['kt', 'db'], ['sk', 'lg'], ['kgc', 'kcc'], ['sono', 'hd']],
    [['gas', 'ss'], ['lg', 'kt'], ['db', 'sk']],
    [['kcc', 'sono'], ['hd', 'kgc']],
];

const PLAYER_EXAMPLES = [
    { name: '패리스 배스', teamId: 'kt', number: '01', points: 27.8, rebounds: 10.2, assists: 4.8 },
    { name: '자밀 워니', teamId: 'sk', number: '34', points: 25.4, rebounds: 11.6, assists: 4.3 },
    { name: '아셈 마레이', teamId: 'lg', number: '50', points: 21.9, rebounds: 13.8, assists: 5.1 },
    { name: '헨리 엘런슨', teamId: 'db', number: '13', points: 24.6, rebounds: 9.4, assists: 2.9 },
    { name: '게이지 프림', teamId: 'hd', number: '44', points: 23.8, rebounds: 9.8, assists: 2.6 },
    { name: '허훈', teamId: 'kt', number: '02', points: 17.6, rebounds: 2.4, assists: 8.3 },
    { name: '이선 알바노', teamId: 'db', number: '17', points: 18.2, rebounds: 3.1, assists: 7.5 },
    { name: '변준형', teamId: 'kgc', number: '05', points: 15.9, rebounds: 3.2, assists: 6.8 },
    { name: '숀 롱', teamId: 'kcc', number: '21', points: 22.7, rebounds: 12.1, assists: 2.1 },
    { name: '이정현', teamId: 'sono', number: '06', points: 20.3, rebounds: 3.8, assists: 6.4 },
];

/** 식별자로 예시 팀 정보를 찾습니다. */
export function getTeam(teamId) {
    return TEAMS.find((team) => team.id === teamId) ?? TEAMS[2];
}

/** 선택 시즌의 일주일 날짜 목록을 생성합니다. */
export function getWeekDates(seasonId) {
    const year = SEASONS[seasonId].year;
    return Array.from({ length: 7 }, (_, index) => new Date(year, 9, 19 + index, 12));
}

/** 날짜별 경기와 확률을 생성하며 모든 값은 예시로만 사용합니다. */
export function getMatches(dayIndex, seasonId) {
    const date = getWeekDates(seasonId)[dayIndex];
    const teams = getStandings(seasonId);
    return FIXTURE_PLANS[dayIndex].map(([homeId, awayId], index) => ({
        id: `demo-${seasonId}-${dayIndex}-${index}`,
        season: seasonId,
        home: teams.find((team) => team.id === homeId),
        away: teams.find((team) => team.id === awayId),
        date,
        time: index % 2 === 0 ? '19:00' : '19:30',
        status: dayIndex < 4 ? 'finished' : 'scheduled',
        homeProbability: [58, 54, 62, 47][index],
        homeScore: 78 + ((dayIndex * 7 + index * 3) % 23),
        awayScore: 75 + ((dayIndex * 3 + index * 7) % 21),
        confidence: ['보통', '보통', '높음', '보통'][index],
    }));
}

/** 시즌별 예시 순위를 반환하고 원본 팀 목록을 보존합니다. */
export function getStandings(seasonId) {
    const offset = seasonId === '2025-2026' ? 1 : 0;
    return TEAMS.map((team) => ({
        ...team,
        wins: Math.max(0, team.wins - offset),
        losses: Math.max(0, team.losses - offset),
    })).sort(compareTeamRecords);
}

/** 승률과 승수로 예시 순위를 정렬합니다. */
function compareTeamRecords(first, second) {
    return second.wins / (second.wins + second.losses) - first.wins / (first.wins + first.losses)
        || second.wins - first.wins;
}

/** 선택 팀의 최근 경기 시계열 예시를 반환합니다. */
export function getTeamTrend(teamId, period, seasonId) {
    const teamIndex = TEAMS.findIndex((team) => team.id === teamId);
    const adjustment = teamIndex - 2 + (seasonId === '2025-2026' ? -2 : 0);
    const opponents = TEAMS.filter((team) => team.id !== teamId);
    const scored = [78, 86, 82, 91, 76, 88, 84, 95, 87, 92];
    const conceded = [83, 79, 88, 81, 85, 80, 78, 86, 82, 84];
    return scored.map((score, index) => ({
        date: new Date(SEASONS[seasonId].year, 8, 27 + index * 2, 12),
        opponent: opponents[(teamIndex + index) % opponents.length].name,
        scored: score + adjustment,
        conceded: conceded[index] - Math.round(adjustment / 2),
    })).slice(-period);
}

/** 선택 지표의 선수 상위 다섯 명을 반환합니다. */
export function getPlayerLeaders(metric, seasonId) {
    return getPlayerRecords(seasonId).map((player) => ({ ...player, value: player[metric] }))
        .sort((first, second) => second.value - first.value).slice(0, 5);
}

/** 선수 기록 페이지에서 사용할 전체 예시 기록을 반환합니다. */
export function getPlayerRecords(seasonId) {
    const adjustment = seasonId === '2025-2026' ? 0.6 : 0;
    return PLAYER_EXAMPLES.map((player) => ({
        ...player,
        points: player.points - adjustment,
        rebounds: player.rebounds - adjustment,
        assists: player.assists - adjustment,
    }));
}
