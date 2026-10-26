import { SEASONS, getMatches, getStandings, getTeam, getWeekDates, getPlayerLeaders } from '../data/demo-data.js';
import { mountAppShell, showToast } from '../components/app-shell.js';
import { renderMetrics } from '../components/metrics.js';
import { matchCard, renderMatchPreview, renderMatchInsights } from '../components/match-preview.js';
import { icon, pageHref } from '../components/ui.js';
import { readPageState, persistPageState } from '../state/preferences.js';
import { formatKoreanDate, escapeHtml } from '../utils/format.js';
import { exportMatchReport } from '../utils/export.js';

const state = readPageState();

/** 대시보드의 핵심 지표와 경기 요약을 표시합니다. */
function renderDashboard() {
    const season = SEASONS[state.season];
    const matches = getMatches(state.dayIndex, state.season);
    const date = getWeekDates(state.season)[state.dayIndex];
    renderMetrics(document.getElementById('summary-metrics'), [
        { label: '선택 날짜 경기', value: matches.length, unit: '경기', detail: formatKoreanDate(date), icon: 'calendar', values: [2, 3, 2, 4, 3, matches.length] },
        { label: '승부 예측 적중률', value: season.accuracy.toFixed(1), unit: '%', detail: '평가 결과 예시', icon: 'target', accent: true, values: [56, 59, 58, 64, 62, season.accuracy] },
        { label: '시즌 진행 경기', value: season.completedGames, unit: '/ 270', detail: '정규시즌 · 예시 집계', icon: 'ball', values: [8, 13, 17, 25, 30, season.completedGames] },
        { label: '팀 평균 득점', value: season.averageScore.toFixed(1), unit: '점', detail: '팀당 경기 평균 · 예시', icon: 'activity', values: [78, 82, 80, 84, 81, season.averageScore] },
    ]);
    document.getElementById('match-count').textContent = String(matches.length).padStart(2, '0');
    document.getElementById('match-date-caption').textContent = formatKoreanDate(date);
    document.getElementById('schedule-link').href = pageHref('schedule', { day: state.dayIndex, season: state.season });
    document.getElementById('match-cards').innerHTML = matches.map((match) => matchCard(match, {
        href: pageHref('schedule', { day: state.dayIndex, match: match.id, season: state.season }),
    })).join('');
    const featured = matches.find((match) => match.id === state.selectedMatchId) ?? matches[0];
    renderMatchPreview(document.getElementById('featured-match'), featured, state.season);
    renderMatchInsights(document.getElementById('match-insights'), featured);
    renderOverviewLinks();
    persistPageState(state);
}

/** 상세 정보를 독립 메뉴 페이지로 연결하는 요약 카드를 만듭니다. */
function renderOverviewLinks() {
    const team = getTeam(state.teamId);
    const firstTeam = getStandings(state.season)[0];
    const firstPlayer = getPlayerLeaders('points', state.season)[0];
    const cards = [
        { page: 'teamManagement', title: '팀관리', label: `${team.city} ${team.name}`, description: '팀 정보와 최근 경기의 득점·실점 흐름 살펴보기', icon: 'chart', parameters: { team: state.teamId, season: state.season } },
        { page: 'standings', title: '리그 순위', label: `1위 · ${firstTeam.city} ${firstTeam.name}`, description: '10개 팀의 순위와 승패 기록 한눈에', icon: 'trophy', parameters: { season: state.season } },
        { page: 'players', title: '선수 기록', label: `${firstPlayer.name} · ${firstPlayer.value.toFixed(1)} PTS`, description: '득점·리바운드·어시스트 리더 확인', icon: 'users', parameters: { season: state.season } },
    ];
    document.getElementById('overview-links').innerHTML = cards.map((card) => `
        <a class="overview-link panel" href="${escapeHtml(pageHref(card.page, card.parameters))}">
            <div class="overview-link-top"><span>${icon(card.icon)}${card.title}</span>${icon('arrow-up-right')}</div>
            <strong>${escapeHtml(card.label)}</strong><p>${card.description}</p>
        </a>`).join('');
}

/** 시즌 변경을 대시보드와 다른 페이지의 공유 설정에 반영합니다. */
function handleSeasonChange(season) {
    state.season = season;
    state.selectedMatchId = null;
    renderDashboard();
}

/** 선택 날짜의 예시 경기 요약을 CSV로 저장합니다. */
function handleExport() {
    exportMatchReport(getMatches(state.dayIndex, state.season));
    showToast('선택 날짜의 예시 경기 리포트를 저장했습니다.');
}

mountAppShell('dashboard', state, handleSeasonChange);
renderDashboard();
document.getElementById('export-button').addEventListener('click', handleExport);
