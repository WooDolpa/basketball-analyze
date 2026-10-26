import { SEASONS } from '../data/demo-data.js';
import { icon, NAVIGATION, PAGES } from './ui.js';

let toastTimeout;

/** 페이지 링크에 현재 메뉴 표시와 접근성 정보를 적용합니다. */
function renderNavigationLink(id, pageId, nested = false) {
    const page = PAGES[id];
    const active = id === pageId;
    return `<a href="${page.href}" class="nav-link ${nested ? 'nav-sublink' : ''} ${active ? 'is-active' : ''}"
               ${active ? 'aria-current="page"' : ''}>
                ${nested ? '<span class="nav-submarker" aria-hidden="true"></span>' : icon(page.icon)}${page.label}
            </a>`;
}

/** 공통 메뉴 정의로 일반 링크와 펼칠 수 있는 2Depth 메뉴를 구성합니다. */
function renderNavigation(pageId) {
    return NAVIGATION.map((entry) => {
        if (typeof entry === 'string') return renderNavigationLink(entry, pageId);
        const active = entry.children.includes(pageId);
        return `<div class="nav-group ${active ? 'is-current' : ''}">
                    <button type="button" class="nav-link nav-group-toggle" data-nav-toggle
                            aria-expanded="${active}" aria-controls="nav-${entry.id}">
                        ${icon(entry.icon)}${entry.label}${icon('chevron-down', 'nav-chevron')}
                    </button>
                    <div class="nav-submenu" id="nav-${entry.id}" ${active ? '' : 'hidden'}>
                        ${entry.children.map((id) => renderNavigationLink(id, pageId, true)).join('')}
                    </div>
                </div>`;
    }).join('');
}

/** 공통 메뉴·상단 바·시즌 선택·안내창을 현재 페이지에 구성합니다. */
export function mountAppShell(pageId, state, onSeasonChange) {
    const main = document.getElementById('main-content');
    const shell = document.createElement('div');
    shell.className = 'app-shell';
    shell.innerHTML = `
        <aside class="sidebar" id="sidebar" aria-label="주 메뉴">
            <a class="brand" href="/" aria-label="COURTSIDE 대시보드">
                <span class="brand-mark" aria-hidden="true"><svg viewBox="0 0 32 32"><rect x="3" y="5" width="26" height="22" rx="5"/><path d="M16 5v22M3 11h5v10H3m26-10h-5v10h5"/><circle cx="16" cy="16" r="5"/></svg></span>
                <span>COURTSIDE<span class="brand-caption">THE GAME, DECODED.</span></span>
            </a>
            <div class="workspace-label">나의 분석 워크스페이스 <span class="workspace-dot"></span></div>
            <p class="nav-label">OVERVIEW</p>
            <nav class="navigation" aria-label="분석 메뉴">
                ${renderNavigation(pageId)}
            </nav>
            <div class="sidebar-note">
                <span class="note-symbol">${icon('spark')}</span>
                <p>감각에 데이터를 더하다.</p>
                <span>코트 위의 흐름을 읽는<br>나만의 농구 분석 공간</span>
                <div class="tiny-court" aria-hidden="true"><i></i></div>
            </div>
            <div class="sidebar-bottom">
                <label class="nav-label" for="season-select">SEASON</label>
                <div class="select-wrap season-select-wrap">
                    <select id="season-select">${Object.entries(SEASONS).map(([id, season]) =>
                        `<option value="${id}">${season.label} 시즌</option>`).join('')}</select>
                    ${icon('chevron-down')}
                </div>
                <div class="local-status"><span class="status-dot"></span>로컬 워크스페이스<span>v0.1</span></div>
            </div>
        </aside>
        <div class="sidebar-backdrop" id="sidebar-backdrop" hidden></div>
        <div class="main-shell">
            <header class="topbar">
                <div class="topbar-left">
                    <button type="button" class="icon-button mobile-menu" id="menu-toggle" aria-label="메뉴 열기"
                            aria-expanded="false" aria-controls="sidebar">${icon('menu')}</button>
                    <span class="breadcrumb">워크스페이스 <span>/</span> ${PAGES[pageId].parentLabel ? `<span>${PAGES[pageId].parentLabel}</span><span>/</span>` : ''}<strong>${PAGES[pageId].label}</strong></span>
                </div>
                <div class="topbar-actions">
                    <span class="demo-badge"><span></span>예시 데이터</span><span class="topbar-divider"></span>
                    <button type="button" class="icon-button" id="guide-button" aria-label="화면 이용 안내">${icon('help')}</button>
                    <span class="user-avatar" aria-label="개인 워크스페이스">ME</span>
                </div>
            </header>
            <div id="page-outlet"></div>
        </div>`;
    document.body.insertBefore(shell, main);
    shell.querySelector('#page-outlet').replaceWith(main);
    main.insertAdjacentHTML('beforeend', `
        <footer class="page-footer">
            <span><span class="status-dot"></span>개인 분석을 위한 로컬 워크스페이스</span>
            <p>모든 경기·기록·예측은 UI 확인용 예시입니다. 실제 KBL 데이터 및 예측 모델은 연결되지 않았습니다.</p>
            <span class="footer-wordmark">COURTSIDE © 2026</span>
        </footer>`);
    document.body.insertAdjacentHTML('beforeend', `
        <div class="toast" id="toast" role="status" aria-live="polite" hidden></div>
        <dialog class="guide-dialog" id="guide-dialog" aria-labelledby="guide-title">
            <div class="dialog-heading"><span class="eyebrow">WELCOME TO COURTSIDE</span>
                <button type="button" class="icon-button" id="guide-close" aria-label="안내 닫기">${icon('close')}</button></div>
            <h2 id="guide-title">코트를 읽는 작은 가이드</h2>
            <p>현재 화면은 예시 데이터로 동작합니다. 예측 확률과 적중률은 실제 모델의 결과가 아닙니다.</p>
            <ol>
                <li><strong>메뉴별 독립 페이지</strong><span>경기 일정·리그 순위·선수 기록으로 이동하거나, 팀 메뉴를 펼쳐 팀관리를 선택하세요.</span></li>
                <li><strong>기록을 이어서 탐색</strong><span>순위표의 팀을 선택하면 해당 팀의 팀관리 화면으로 이동합니다. 시즌과 선택 설정은 페이지 간 유지됩니다.</span></li>
                <li><strong>예시 기록 저장</strong><span>경기 일정과 대시보드의 내보내기 버튼으로 선택 날짜의 CSV를 저장할 수 있습니다.</span></li>
            </ol>
            <div class="dialog-footer"><span class="status-dot"></span>화면 설정만 이 기기에 저장합니다.</div>
        </dialog>`);
    const seasonSelect = document.getElementById('season-select');
    seasonSelect.value = state.season;
    seasonSelect.addEventListener('change', (event) => onSeasonChange(event.target.value));
    bindShellEvents();
}

/** 하위 메뉴 펼침, 공통 도움말과 모바일 메뉴의 이벤트를 연결합니다. */
function bindShellEvents() {
    document.querySelectorAll('[data-nav-toggle]').forEach((button) => {
        button.addEventListener('click', () => toggleNavigationGroup(button));
    });
    document.getElementById('menu-toggle').addEventListener('click', () => setMobileMenu(!document.body.classList.contains('menu-open')));
    document.getElementById('sidebar-backdrop').addEventListener('click', () => setMobileMenu(false));
    const dialog = document.getElementById('guide-dialog');
    document.getElementById('guide-button').addEventListener('click', () => dialog.showModal());
    document.getElementById('guide-close').addEventListener('click', () => dialog.close());
    document.addEventListener('keydown', handleMenuKeyboard);
    matchMedia('(min-width: 1001px)').addEventListener('change', () => {
        setMobileMenu(false);
        synchronizeSidebarAccessibility();
    });
    synchronizeSidebarAccessibility();
}

/** 하위 메뉴의 표시 상태와 상위 버튼의 펼침 상태를 함께 변경합니다. */
function toggleNavigationGroup(button) {
    const expanded = button.getAttribute('aria-expanded') !== 'true';
    button.setAttribute('aria-expanded', String(expanded));
    document.getElementById(button.getAttribute('aria-controls')).hidden = !expanded;
}

/** 모바일 메뉴 노출 상태와 닫힌 뒤의 키보드 포커스를 관리합니다. */
function setMobileMenu(open) {
    const wasOpen = document.body.classList.contains('menu-open');
    document.body.classList.toggle('menu-open', open);
    document.getElementById('sidebar-backdrop').hidden = !open;
    const toggle = document.getElementById('menu-toggle');
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', open ? '메뉴 닫기' : '메뉴 열기');
    synchronizeSidebarAccessibility();
    if (open) {
        const activeLink = document.querySelector('.nav-link.is-active');
        const target = activeLink?.closest('[hidden]')
            ? document.querySelector('.nav-group.is-current .nav-group-toggle') : activeLink;
        target?.focus({ preventScroll: true });
    } else if (wasOpen && matchMedia('(max-width: 1000px)').matches) toggle.focus({ preventScroll: true });
}

/** 닫힌 모바일 메뉴를 키보드와 보조 기술의 탐색 대상에서 제외합니다. */
function synchronizeSidebarAccessibility() {
    const hidden = matchMedia('(max-width: 1000px)').matches && !document.body.classList.contains('menu-open');
    const sidebar = document.getElementById('sidebar');
    sidebar.inert = hidden;
    if (hidden) sidebar.setAttribute('aria-hidden', 'true');
    else sidebar.removeAttribute('aria-hidden');
}

/** 열린 모바일 메뉴의 Escape 닫기와 Tab 순환을 처리합니다. */
function handleMenuKeyboard(event) {
    if (!document.body.classList.contains('menu-open')) return;
    if (event.key === 'Escape') return setMobileMenu(false);
    if (event.key !== 'Tab') return;
    const focusable = [...document.getElementById('sidebar').querySelectorAll('a[href], button, select')]
        .filter((element) => !element.closest('[hidden]'));
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
    }
}

/** 공통 작업 결과 메시지를 잠시 표시합니다. */
export function showToast(message) {
    const toast = document.getElementById('toast');
    clearTimeout(toastTimeout);
    toast.textContent = message;
    toast.hidden = false;
    toastTimeout = setTimeout(() => { toast.hidden = true; }, 4200);
}
