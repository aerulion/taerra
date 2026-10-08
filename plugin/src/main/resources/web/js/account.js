import {t} from './i18n.js';
import {wsManager} from './websocket.js';

const $ = (id) => document.getElementById(id);

const tabs = new BroadcastChannel('taerra-account');

let current = null;

function show(account) {
    current = account;
    $('account-in').hidden = !account;
    $('account-out').hidden = Boolean(account);
    $('account-name').textContent = account?.name ?? '';
    const label = account ? `${t('account.as')} ${account.name}` : t('account.login');
    $('account-button').setAttribute('aria-label', label);
    $('account-button').title = label;
    $('account-head').hidden = true;
    if (account) {
        $('account-head').src = `/api/account/avatar.png?uuid=${account.uuid}`;
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

async function login(code) {
    const response = await fetch('/api/login', {method: 'POST', body: code});
    if (response.ok) {
        show(await response.json());
        tabs.postMessage('changed');
        wsManager.reconnect();
        return;
    }
    await refresh();
    $('login-error').hidden = false;
    $('account-menu').showPopover();
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
    $('account-head').addEventListener('load', () => $('account-head').hidden = false);
    $('account-menu').addEventListener('toggle', (event) => {
        if (event.newState === 'closed') {
            $('login-error').hidden = true;
        }
    });
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

function canHide() {
    return current?.hide === true;
}

export const accountManager = {
    initialize, loggedIn, usesMod, ownUuid, canTeleport, canEditMarkers, canKeepPersonal, canCreatePrivateGroups, canRender,
    canHide
};
