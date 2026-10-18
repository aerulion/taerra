import qrcode from '../vendor/qrcode/qrcode.js';
import {copyText} from './clipboard.js';
import {language, t} from './i18n.js';
import {pixelPath} from './map.js';
import {wsManager} from './websocket.js';

const $ = (id) => document.getElementById(id);

export const FALLBACK_AVATAR = 'data:image/svg+xml,' + encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 8 8" shape-rendering="crispEdges">
<rect width="8" height="8" fill="#7a7a7a"/><rect width="8" height="2" fill="#4a4a4a"/><rect y="2" width="1" height="1" fill="#4a4a4a"/><rect x="7" y="2" width="1" height="1" fill="#4a4a4a"/>
<rect x="1" y="4" width="1" height="1" fill="#e0e0e0"/><rect x="2" y="4" width="1" height="1" fill="#333"/><rect x="5" y="4" width="1" height="1" fill="#333"/><rect x="6" y="4" width="1" height="1" fill="#e0e0e0"/>
<rect x="3" y="6" width="2" height="1" fill="#5a5a5a"/></svg>`);

const tabs = new BroadcastChannel('taerra-account');
const POLL_MILLIS = 2000;
const QUIET_ZONE = 4;
const AGO = [['day', 86400000], ['hour', 3600000], ['minute', 60000]];
const relative = new Intl.RelativeTimeFormat(language, {numeric: 'auto'});

let current = null;
let pairing = null;
let pollTimer = 0;
let pairTimer = 0;
let linkTimer = 0;
let copiedTimer = 0;

function show(account) {
    current = account;
    const open = $('account-menu').matches(':popover-open');
    if (account) {
        stopPairing();
        if (open) {
            void loadDevices();
        }
    } else if (!pairing) {
        showPairing('', '');
        if (open) {
            void startPairing();
        }
    }
    $('account-in').hidden = !account;
    $('account-out').hidden = Boolean(account);
    $('account-name').textContent = account?.name ?? '';
    $('manager-open').hidden = account?.personal !== true;
    $('settings-open').hidden = account?.config !== true;
    $('regions-open').hidden = account?.regions !== true;
    const label = account ? `${t('account.as')} ${account.name}` : t('account.login');
    $('account-button').setAttribute('aria-label', label);
    $('account-button').title = label;
    $('account-head').hidden = true;
    $('account-avatar').hidden = true;
    if (account) {
        $('account-head').src = `/api/account/avatar.png?uuid=${account.uuid}`;
        $('account-avatar').src = $('account-head').src;
    }
}

async function refresh() {
    try {
        const response = await fetch('/api/account');
        show(response.status === 200 ? await response.json() : null);
    } catch (error) {
        console.error('Failed to load the account:', error);
        show(null);
    }
}

async function changed() {
    await refresh();
    wsManager.reconnect();
}

function loggedInHere(account) {
    show(account);
    tabs.postMessage('changed');
    wsManager.reconnect();
}

async function login(code) {
    const response = await fetch('/api/login', {method: 'POST', body: code});
    if (response.ok) {
        loggedInHere(await response.json());
        return;
    }
    await refresh();
    $('login-error').hidden = false;
    $('account-menu').showPopover();
}

function clock(millis) {
    const seconds = Math.max(0, Math.ceil(millis / 1000));
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

function showPairing(code, status, expired = false) {
    $('pair-code').textContent = code;
    $('pair-command').hidden = !code;
    $('pair-command').disabled = expired;
    $('pair-command').classList.toggle('is-expired', expired);
    $('pair-status').textContent = status;
    $('pair-status').classList.toggle('is-expired', expired);
    $('pair-new').hidden = !expired;
}

function stopPairing() {
    clearTimeout(pollTimer);
    clearInterval(pairTimer);
    pairing = null;
}

function tickPairing() {
    if (!pairing?.until) {
        return;
    }
    const left = pairing.until - Date.now();
    if (left <= 0) {
        expirePairing();
    } else {
        $('pair-status').textContent = t('account.pairTime', {time: clock(left)});
    }
}

function expirePairing() {
    const code = pairing?.code ?? '';
    stopPairing();
    showPairing(code, t('account.expired'), true);
}

async function startPairing() {
    stopPairing();
    showPairing('', '');
    const started = {};
    pairing = started;
    let response = null;
    try {
        response = await fetch('/api/login/pair', {method: 'POST'});
    } catch {
    }
    if (pairing !== started) {
        return;
    }
    if (!response?.ok) {
        pairing = null;
        showPairing('', t('account.pairFailed'), true);
        return;
    }
    const {code, claim, seconds} = await response.json();
    Object.assign(started, {code, claim, until: Date.now() + seconds * 1000});
    showPairing(code, '');
    tickPairing();
    pairTimer = setInterval(tickPairing, 1000);
    pollTimer = setTimeout(pollPairing, POLL_MILLIS);
}

async function pollPairing() {
    const waiting = pairing;
    if (!waiting?.claim) {
        return;
    }
    if (Date.now() >= waiting.until) {
        expirePairing();
        return;
    }
    let response = null;
    try {
        response = await fetch('/api/login/claim', {method: 'POST', body: waiting.claim});
    } catch {
    }
    if (pairing !== waiting) {
        return;
    }
    if (response?.status === 200) {
        stopPairing();
        loggedInHere(await response.json());
    } else if (response?.status === 410) {
        expirePairing();
    } else {
        pollTimer = setTimeout(pollPairing, POLL_MILLIS);
    }
}

function qrSvg(text) {
    const qr = qrcode(0, 'M');
    qr.addData(text);
    qr.make();
    const count = qr.getModuleCount();
    const size = count + QUIET_ZONE * 2;
    const dark = [];
    for (let row = 0; row < count; row++) {
        for (let col = 0; col < count; col++) {
            if (qr.isDark(row, col)) {
                dark.push([col + QUIET_ZONE, row + QUIET_ZONE, 1, 1]);
            }
        }
    }
    return `<svg viewBox="0 0 ${size} ${size}" shape-rendering="crispEdges" role="img" aria-label="${t('account.device')}">
<rect width="${size}" height="${size}" fill="#fff"/><path d="${pixelPath(dark)}" fill="#000"/></svg>`;
}

async function showDeviceLink() {
    clearInterval(linkTimer);
    const panel = $('device-qr');
    let response = null;
    try {
        response = await fetch('/api/login/link', {method: 'POST'});
    } catch {
    }
    panel.hidden = false;
    if (!response?.ok) {
        panel.classList.add('is-expired');
        $('device-qr-code').replaceChildren();
        $('device-qr-status').textContent = t('account.linkFailed');
        $('device-qr-status').classList.add('is-expired');
        return;
    }
    const {code, base, seconds} = await response.json();
    const page = base || `${location.origin}${location.pathname.replace(/\/$/, '')}`;
    $('device-qr-code').innerHTML = qrSvg(`${page}/#login=${code}`);
    panel.classList.remove('is-expired');
    $('device-qr-status').classList.remove('is-expired');
    $('device-button').textContent = t('account.newCode');
    const until = Date.now() + seconds * 1000;
    const tick = () => {
        const left = until - Date.now();
        if (left > 0) {
            $('device-qr-status').textContent = t('account.scan', {time: clock(left)});
            return;
        }
        clearInterval(linkTimer);
        panel.classList.add('is-expired');
        $('device-qr-status').textContent = t('account.expired');
        $('device-qr-status').classList.add('is-expired');
    };
    tick();
    linkTimer = setInterval(tick, 1000);
}

function hideDeviceLink() {
    clearInterval(linkTimer);
    $('device-qr').hidden = true;
    $('device-qr-code').replaceChildren();
    $('device-button').textContent = t('account.device');
}

function deviceName(device) {
    if (device.game) {
        return t('account.game');
    }
    const browser = device.browser ?? t('account.browser');
    return device.os ? t('account.on', {browser, os: device.os}) : browser;
}

function deviceIcon(device) {
    if (device.game) {
        return 'game';
    }
    return device.os === 'iPad' ? 'tablet' : device.os === 'iPhone' || device.os === 'Android' ? 'phone' : 'desktop';
}

function ago(time) {
    const since = Date.now() - time;
    if (since < 60000) {
        return t('account.justNow');
    }
    const [unit, size] = AGO.find(([, size]) => since >= size);
    return relative.format(-Math.floor(since / size), unit);
}

async function loadDevices() {
    let devices = [];
    try {
        const response = await fetch('/api/account/sessions');
        devices = response.ok ? await response.json() : [];
    } catch {
    }
    $('device-list').replaceChildren(...devices.map(device => {
        const row = document.createElement('li');
        row.className = 'device';
        row.dataset.icon = deviceIcon(device);
        row.classList.toggle('is-current', device.current || device.game);
        const text = document.createElement('span');
        text.className = 'device__text';
        const name = document.createElement('span');
        name.className = 'device__name';
        name.textContent = deviceName(device);
        const meta = document.createElement('span');
        meta.className = 'device__meta';
        meta.textContent = device.current ? t('account.thisDevice')
            : device.game ? t('account.inGame') : ago(device.seen);
        text.append(name, meta);
        row.append(text);
        if (!device.current && !device.game) {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'device__logout';
            button.setAttribute('aria-label', t('account.endDeviceLabel', {device: name.textContent}));
            button.title = button.getAttribute('aria-label');
            button.addEventListener('click', async () => {
                button.disabled = true;
                try {
                    await fetch(`/api/account/sessions/${encodeURIComponent(device.id)}`, {method: 'DELETE'});
                } catch {
                }
                await loadDevices();
            });
            row.append(button);
        }
        return row;
    }));
}

function takeHashCode() {
    const params = new URLSearchParams(location.hash.slice(1));
    const code = params.get('login');
    if (code !== null) {
        params.delete('login');
        history.replaceState(null, '', `#${params}`);
    }
    return code;
}

async function initialize() {
    const code = takeHashCode();
    addEventListener('hashchange', () => {
        const code = takeHashCode();
        if (code !== null) {
            void login(code);
        }
    });
    for (const head of [$('account-head'), $('account-avatar')]) {
        head.addEventListener('load', () => head.hidden = false);
        head.addEventListener('error', () => {
            if (current && head.getAttribute('src') !== FALLBACK_AVATAR) {
                head.src = FALLBACK_AVATAR;
            }
        });
    }
    $('account-menu').addEventListener('toggle', (event) => {
        if (event.newState === 'closed') {
            $('login-error').hidden = true;
            hideDeviceLink();
        } else if (!current && !pairing) {
            void startPairing();
        } else if (current) {
            void loadDevices();
        }
    });
    $('pair-new').addEventListener('click', () => void startPairing());
    $('pair-command').addEventListener('click', async () => {
        await copyText(`/taerra login ${$('pair-code').textContent}`);
        clearTimeout(copiedTimer);
        $('pair-command').classList.add('is-copied');
        copiedTimer = setTimeout(() => $('pair-command').classList.remove('is-copied'), 1500);
    });
    $('device-button').addEventListener('click', () => void showDeviceLink());
    $('logout-button').addEventListener('click', async () => {
        await fetch('/api/logout', {method: 'POST'});
        $('account-menu').hidePopover();
        show(null);
    });
    tabs.addEventListener('message', changed);
    wsManager.on('account', changed);
    await (code === null ? refresh() : login(code));
}

function canTeleport() {
    return current?.teleport === true;
}

function canEditMarkers() {
    return current?.markers === true;
}

function loggedIn() {
    return current !== null;
}

function usesMod() {
    return current?.mod === true;
}

function ownUuid() {
    return current?.uuid ?? null;
}

function canKeepPersonal() {
    return current?.personal === true;
}

function canCreatePrivateGroups() {
    return current?.privateGroups === true;
}

function canRender() {
    return current?.render === true;
}

function canEditRegions() {
    return current?.regions === true;
}

function canConfigure() {
    return current?.config === true;
}

export const accountManager = {
    initialize, loggedIn, usesMod, ownUuid, canTeleport, canEditMarkers, canKeepPersonal, canCreatePrivateGroups, canRender,
    canEditRegions, canConfigure
};
