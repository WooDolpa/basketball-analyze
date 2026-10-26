import { DEFAULT_PREFERENCES, SEASONS, TEAMS } from '../data/demo-data.js';

const STORAGE_KEY = 'courtside.dashboard.preferences.v1';

/** 저장소와 페이지 주소의 선택값을 검증해 초기 화면 상태를 만듭니다. */
export function readPageState() {
    let saved = {};
    try {
        saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}') ?? {};
    } catch {
        // 저장소가 차단되어도 URL과 기본값으로 화면을 표시합니다.
    }
    const query = new URLSearchParams(location.search);
    const season = query.get('season') ?? saved.season;
    const teamId = query.get('team') ?? saved.teamId;
    const dayIndex = query.has('day') ? Number(query.get('day')) : saved.dayIndex;
    const period = query.has('period') ? Number(query.get('period')) : saved.period;
    const leaderMetric = query.get('metric') ?? saved.leaderMetric;
    return {
        season: Object.hasOwn(SEASONS, season) ? season : DEFAULT_PREFERENCES.season,
        teamId: TEAMS.some((team) => team.id === teamId) ? teamId : DEFAULT_PREFERENCES.teamId,
        dayIndex: Number.isInteger(dayIndex) && dayIndex >= 0 && dayIndex < 7 ? dayIndex : DEFAULT_PREFERENCES.dayIndex,
        period: [5, 10].includes(period) ? period : DEFAULT_PREFERENCES.period,
        leaderMetric: ['points', 'rebounds', 'assists'].includes(leaderMetric) ? leaderMetric : DEFAULT_PREFERENCES.leaderMetric,
        selectedMatchId: query.get('match'),
    };
}

/** 페이지 간 공유 설정과 새로고침 가능한 현재 페이지 주소를 저장합니다. */
export function persistPageState(state, pageQuery = {}) {
    const preferences = {};
    for (const key of Object.keys(DEFAULT_PREFERENCES)) preferences[key] = state[key];
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences));
    } catch {
        // 저장소 없이도 현재 페이지와 주소의 선택값은 유지합니다.
    }
    const query = new URLSearchParams({ season: state.season });
    for (const [key, value] of Object.entries(pageQuery)) {
        if (value !== null && value !== undefined) query.set(key, String(value));
    }
    history.replaceState(null, '', `${location.pathname}?${query}${location.hash}`);
}
