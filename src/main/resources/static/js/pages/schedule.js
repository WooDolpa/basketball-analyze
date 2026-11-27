import { mountAppShell, showToast } from '../components/app-shell.js';
import { renderScheduleMatchDetail, scheduleMatchCard } from '../components/match-preview.js';
import { formatKoreanDate, formatKoreanDateKey } from '../utils/format.js';
import { exportScheduleReport } from '../utils/export.js';

const dateInput = document.getElementById('schedule-date-input');
const dayTabs = document.getElementById('day-tabs');
const previousDay = document.getElementById('previous-day');
const nextDay = document.getElementById('next-day');
const dateCaption = document.getElementById('match-date-caption');
const matchCount = document.getElementById('match-count');
const matchCards = document.getElementById('match-cards');
const featuredMatch = document.getElementById('featured-match');
const scheduleStatus = document.getElementById('schedule-status');
const statusMessage = document.getElementById('schedule-status-message');
const retryButton = document.getElementById('retry-schedule');
const exportButton = document.getElementById('export-button');
const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
const query = new URLSearchParams(location.search);
const requestedDate = query.get('date');
const state = {
    date: isValidScheduleDate(requestedDate) ? requestedDate : formatKoreanDateKey(new Date()),
    matchCode: query.get('match'),
    weekStart: null,
    seasonId: '',
    matches: [],
    status: 'loading',
    request: null,
};

/** 한국시간 달력 날짜를 DST에 영향받지 않는 UTC 기준값으로 변환합니다. */
function parseCalendarDate(date) {
    return new Date(`${date}T00:00:00Z`);
}

/** YYYY-MM-DD 형식과 실제 날짜, API가 지원하는 날짜 범위를 확인합니다. */
function isValidScheduleDate(date) {
    if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(date ?? '') || date < dateInput.min || date > dateInput.max) return false;
    const parsed = parseCalendarDate(date);
    return Number.isFinite(parsed.getTime()) && parsed.toISOString().startsWith(`${date}T`);
}

/** 달력에서 지정한 일수만큼 이동한 날짜를 반환합니다. */
function shiftDate(date, days) {
    const shifted = parseCalendarDate(date);
    shifted.setUTCDate(shifted.getUTCDate() + days);
    return shifted.toISOString().split('T')[0];
}

/** 선택 날짜가 포함된 월요일부터 일요일까지의 조회 일자를 구합니다. */
function getWeekDates(date) {
    const daysSinceMonday = (parseCalendarDate(date).getUTCDay() + 6) % 7;
    return Array.from({ length: 7 }, (_, index) => shiftDate(date, index - daysSinceMonday)).filter(isValidScheduleDate);
}

/** 날짜 입력과 이동 버튼에 선택한 날짜를 반영하고 주간 날짜 탭을 표시합니다. */
function renderDateControls(weekDates) {
    dateInput.value = state.date;
    previousDay.disabled = state.date === dateInput.min;
    nextDay.disabled = state.date === dateInput.max;
    dayTabs.innerHTML = weekDates.map((date) => {
        const parsed = parseCalendarDate(date);
        const selected = date === state.date;
        return `<button type="button" class="day-tab${selected ? ' is-active' : ''}" data-date="${date}"
                    aria-pressed="${selected}" aria-label="${formatKoreanDate(parsed, 'UTC')}">
            <span>${weekdays[parsed.getUTCDay()]}</span><strong>${parsed.getUTCDate()}</strong>
        </button>`;
    }).join('');
}

/** 선택 날짜와 경기 코드를 주소에 기록해 직접 열거나 새로고침할 수 있게 합니다. */
function updateScheduleUrl() {
    const parameters = new URLSearchParams({ date: state.date });
    if (state.matchCode) parameters.set('match', state.matchCode);
    history.replaceState(null, '', `${location.pathname}?${parameters}${location.hash}`);
}

/** API의 한국시간 경기 일시에서 선택 날짜에 해당하는 경기를 골라냅니다. */
function getMatchesForSelectedDate() {
    return state.matches.filter((match) => match.startsAt.startsWith(`${state.date}T`));
}

/** 선택 경기의 상세와 버튼 상태를 갱신합니다. */
function selectMatch(match) {
    state.matchCode = match.matchCode;
    matchCards.querySelectorAll('[data-match]').forEach((card) => {
        const selected = card.dataset.match === state.matchCode;
        card.classList.toggle('is-selected', selected);
        card.setAttribute('aria-pressed', String(selected));
    });
    renderScheduleMatchDetail(featuredMatch, match);
    updateScheduleUrl();
}

/** 선택 날짜의 경기 목록과 로딩·빈 일정·조회 실패 상태를 표시합니다. */
function renderMatches() {
    const ready = state.status === 'ready';
    const matches = ready ? getMatchesForSelectedDate() : [];
    const selected = matches.find((match) => match.matchCode === state.matchCode) ?? matches[0];
    const dateLabel = formatKoreanDate(parseCalendarDate(state.date), 'UTC');
    dateCaption.textContent = state.seasonId ? `${state.seasonId.replace('-', '–')} 정규시즌 · ${dateLabel}` : dateLabel;
    matchCount.textContent = `${matches.length}경기`;
    matchCount.hidden = !ready;
    matchCards.hidden = !selected;
    featuredMatch.hidden = !selected;
    exportButton.disabled = !selected;
    scheduleStatus.hidden = Boolean(selected);
    retryButton.hidden = state.status !== 'error';
    statusMessage.textContent = {
        loading: '경기 일정을 불러오는 중입니다.',
        error: '경기 일정을 불러오지 못했습니다.',
        ready: '선택한 날짜에 등록된 경기가 없습니다.',
    }[state.status];

    matchCards.innerHTML = matches.map((match) => scheduleMatchCard(match, match === selected)).join('');
    if (ready) state.matchCode = selected?.matchCode ?? null;
    if (selected) renderScheduleMatchDetail(featuredMatch, selected);
    else featuredMatch.innerHTML = '';
    updateScheduleUrl();
}

/** 선택한 주의 기간을 조회하고 날짜 이동으로 이전 요청이 취소되면 응답을 건너뜁니다. */
async function loadWeek(weekDates) {
    state.request?.abort();
    const request = new AbortController();
    state.request = request;
    state.weekStart = weekDates[0];
    state.matches = [];
    state.status = 'loading';
    renderMatches();

    try {
        const params = new URLSearchParams({ fromDate: weekDates[0], toDate: weekDates.at(-1) });
        const response = await fetch(`/api/kbl/matches?${params}`, {
            headers: { Accept: 'application/json' },
            signal: request.signal,
        });
        if (!response.ok) throw new Error(`경기 일정 조회 실패 (${response.status})`);
        const data = await response.json();
        if (request.signal.aborted) return;
        if (!Array.isArray(data.matches) || typeof data.seasonId !== 'string') throw new Error('경기 일정 응답 형식 오류');

        state.matches = data.matches;
        state.seasonId = data.seasonId;
        state.status = 'ready';
        renderMatches();
    } catch {
        if (request.signal.aborted) return;
        state.status = 'error';
        renderMatches();
    }
}

/** 새 날짜를 선택하고 같은 주에서는 받아둔 경기 데이터를 사용합니다. */
function selectDate(date) {
    if (!isValidScheduleDate(date)) {
        dateInput.value = state.date;
        return;
    }
    if (date !== state.date) state.matchCode = null;
    state.date = date;
    const weekDates = getWeekDates(date);
    renderDateControls(weekDates);
    if (weekDates[0] === state.weekStart) renderMatches();
    else loadWeek(weekDates);
}

/** 일정의 날짜 이동, 경기 선택, 재조회와 CSV 저장 동작을 연결합니다. */
function bindScheduleEvents() {
    dayTabs.addEventListener('click', (event) => {
        const tab = event.target.closest('[data-date]');
        if (!tab) return;
        selectDate(tab.dataset.date);
        dayTabs.querySelector('[aria-pressed="true"]')?.focus({ preventScroll: true });
    });
    dateInput.addEventListener('change', () => selectDate(dateInput.value));
    previousDay.addEventListener('click', () => selectDate(shiftDate(state.date, -1)));
    nextDay.addEventListener('click', () => selectDate(shiftDate(state.date, 1)));
    matchCards.addEventListener('click', (event) => {
        const card = event.target.closest('[data-match]');
        if (!card) return;
        const match = getMatchesForSelectedDate().find((item) => item.matchCode === card.dataset.match);
        if (match) selectMatch(match);
    });
    retryButton.addEventListener('click', () => loadWeek(getWeekDates(state.date)));
    exportButton.addEventListener('click', () => {
        exportScheduleReport(getMatchesForSelectedDate(), state.seasonId, state.date);
        showToast('선택 날짜의 경기 일정 CSV를 저장했습니다.');
    });
}

mountAppShell('schedule');
bindScheduleEvents();
selectDate(state.date);
