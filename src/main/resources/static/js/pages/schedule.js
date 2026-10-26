import { getMatches, getWeekDates } from '../data/demo-data.js';
import { mountAppShell, showToast } from '../components/app-shell.js';
import { matchCard, renderMatchPreview, renderMatchInsights } from '../components/match-preview.js';
import { readPageState, persistPageState } from '../state/preferences.js';
import { formatDateKey, formatKoreanDate } from '../utils/format.js';
import { exportMatchReport } from '../utils/export.js';

const state = readPageState();

/** 날짜 탭과 경기 목록, 선택 경기 프리뷰를 함께 표시합니다. */
function renderSchedule() {
    const dates = getWeekDates(state.season);
    const date = dates[state.dayIndex];
    const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
    document.getElementById('day-tabs').innerHTML = dates.map((item, index) => `
        <button type="button" class="day-tab ${index === state.dayIndex ? 'is-active' : ''}" data-day="${index}"
                aria-pressed="${index === state.dayIndex}" aria-label="${formatKoreanDate(item)}">
            <span>${weekdays[item.getDay()]}</span><strong>${item.getDate()}</strong><i aria-hidden="true"></i>
        </button>`).join('');
    document.getElementById('selected-date-label').textContent = formatDateKey(date).replaceAll('-', '. ');
    document.getElementById('match-date-caption').textContent = formatKoreanDate(date);
    document.getElementById('previous-day').disabled = state.dayIndex === 0;
    document.getElementById('next-day').disabled = state.dayIndex === 6;
    renderMatches();
}

/** 현재 날짜의 경기 카드를 그리고 선택된 경기를 주소에 보존합니다. */
function renderMatches() {
    const matches = getMatches(state.dayIndex, state.season);
    const selected = matches.find((match) => match.id === state.selectedMatchId) ?? matches[0];
    state.selectedMatchId = selected.id;
    document.getElementById('match-count').textContent = String(matches.length).padStart(2, '0');
    document.getElementById('match-cards').innerHTML = matches.map((match) => matchCard(match, {
        selected: match.id === state.selectedMatchId,
    })).join('');
    renderMatchPreview(document.getElementById('featured-match'), selected, state.season);
    renderMatchInsights(document.getElementById('match-insights'), selected);
    persistPageState(state, { day: state.dayIndex, match: state.selectedMatchId });
}

/** 예시 데이터의 일주일 범위 안에서 날짜를 변경합니다. */
function selectDay(dayIndex) {
    if (!Number.isInteger(dayIndex) || dayIndex < 0 || dayIndex > 6) return;
    state.dayIndex = dayIndex;
    state.selectedMatchId = null;
    renderSchedule();
}

/** 날짜와 경기 선택 및 CSV 저장 동작을 연결합니다. */
function bindScheduleEvents() {
    document.getElementById('day-tabs').addEventListener('click', (event) => {
        const button = event.target.closest('[data-day]');
        if (!button) return;
        selectDay(Number(button.dataset.day));
        document.querySelector(`[data-day="${state.dayIndex}"]`).focus({ preventScroll: true });
    });
    document.getElementById('previous-day').addEventListener('click', () => selectDay(state.dayIndex - 1));
    document.getElementById('next-day').addEventListener('click', () => selectDay(state.dayIndex + 1));
    document.getElementById('match-cards').addEventListener('click', (event) => {
        const button = event.target.closest('[data-match]');
        if (!button) return;
        state.selectedMatchId = button.dataset.match;
        renderMatches();
        document.querySelector(`[data-match="${state.selectedMatchId}"]`).focus({ preventScroll: true });
    });
    document.getElementById('export-button').addEventListener('click', () => {
        exportMatchReport(getMatches(state.dayIndex, state.season));
        showToast('선택 날짜의 예시 경기 리포트를 저장했습니다.');
    });
}

/** 시즌 변경 후 경기 ID를 초기화하고 해당 시즌 일정을 표시합니다. */
function handleSeasonChange(season) {
    state.season = season;
    state.selectedMatchId = null;
    renderSchedule();
}

mountAppShell('schedule', state, handleSeasonChange);
renderSchedule();
bindScheduleEvents();
