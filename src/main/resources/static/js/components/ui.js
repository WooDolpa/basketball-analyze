import { escapeHtml } from '../utils/format.js';

export const PAGES = {
    dashboard: { href: '/', label: '대시보드', icon: 'grid' },
    schedule: { href: '/pages/schedule.html', label: '경기 일정', icon: 'calendar' },
    teamManagement: { href: '/pages/team-management.html', label: '팀관리', icon: 'chart', parentLabel: '팀' },
    standings: { href: '/pages/standings.html', label: '리그 순위', icon: 'trophy' },
    players: { href: '/pages/player-records.html', label: '선수 기록', icon: 'users' },
};

export const NAVIGATION = [
    'dashboard',
    'schedule',
    { id: 'teams', label: '팀', icon: 'users', children: ['teamManagement'] },
    'standings',
    'players',
];

/** 허용된 페이지 경로와 선택값으로 내부 링크를 만듭니다. */
export function pageHref(pageId, parameters = {}) {
    const query = new URLSearchParams(parameters);
    return `${PAGES[pageId].href}${query.size ? `?${query}` : ''}`;
}

/** 로컬 SVG 스프라이트의 아이콘을 표시합니다. */
export function icon(name, className = '') {
    return `<svg class="icon ${className}" aria-hidden="true"><use href="/assets/icons/sprite.svg#${name}"/></svg>`;
}

/** 공식 로고 대신 팀 식별용 이니셜 배지를 표시합니다. */
export function teamBadge(team, size = '') {
    return `<span class="team-badge ${size}" style="--team-color:${team.color}" aria-hidden="true">${escapeHtml(team.mark)}</span>`;
}

/** 최근 경기의 승패를 색상과 접근 가능한 설명으로 표시합니다. */
export function formDots(form) {
    const description = [...form].map((result) => result === 'W' ? '승' : '패').join(' ');
    return `<span class="form-dots" aria-label="최근 5경기 ${description}">
        ${[...form].map((result) => `<i class="${result === 'W' ? 'win' : 'loss'}"></i>`).join('')}
    </span>`;
}

/** 버튼 그룹의 선택 상태를 시각적 표시와 보조 기술에 반영합니다. */
export function updateSegmentedControl(selector, value) {
    document.querySelectorAll(selector).forEach((button) => {
        const selected = String(Object.values(button.dataset)[0]) === String(value);
        button.classList.toggle('is-active', selected);
        button.setAttribute('aria-pressed', String(selected));
    });
}

/** 선택 옵션을 만들고 현재 값을 적용합니다. */
export function populateTeamSelect(select, teams, selectedId, includeAll = false) {
    select.innerHTML = `${includeAll ? '<option value="all">전체 팀</option>' : ''}${teams.map((team) =>
        `<option value="${team.id}">${escapeHtml(team.city)} ${escapeHtml(team.name)}</option>`).join('')}`;
    select.value = selectedId;
}
