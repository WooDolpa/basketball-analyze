import { SEASONS, getStandings } from '../data/demo-data.js';
import { mountAppShell } from '../components/app-shell.js';
import { renderMetrics } from '../components/metrics.js';
import { renderStandingsTable } from '../components/standings-table.js';
import { readPageState, persistPageState } from '../state/preferences.js';

const state = readPageState();

/** 선택 시즌의 리그 요약과 전체 팀 순위를 표시합니다. */
function renderStandings() {
    const season = SEASONS[state.season];
    const teams = getStandings(state.season);
    const leader = teams[0];
    document.getElementById('season-caption').textContent = `${season.label} 정규시즌`;
    document.getElementById('standings-season').textContent = `${season.label} · 화면 확인용 예시 기록`;
    renderMetrics(document.getElementById('standings-metrics'), [
        { label: '리그 선두', value: `${leader.city} ${leader.name}`, detail: `${leader.wins}승 ${leader.losses}패 · 예시`, icon: 'trophy', accent: true },
        { label: '시즌 진행 경기', value: season.completedGames, unit: '경기', detail: '정규시즌 예시 집계', icon: 'calendar' },
        { label: '참가 팀', value: teams.length, unit: '팀', detail: '남자 KBL', icon: 'users' },
    ]);
    renderStandingsTable(document.getElementById('standings-body'), state.season);
    persistPageState(state);
}

/** 시즌 변경을 순위표와 공유 설정에 반영합니다. */
function handleSeasonChange(season) {
    state.season = season;
    renderStandings();
}

mountAppShell('standings', state, handleSeasonChange);
renderStandings();
