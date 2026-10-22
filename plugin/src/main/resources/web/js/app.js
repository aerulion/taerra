import {accountManager} from './account.js';
import {copyText} from './clipboard.js';
import {iconEditor} from './iconeditor.js';
import {iconChooser, iconStore} from './icons.js';
import {iconManager} from './iconmanager.js';
import {waypointManager} from './manager.js';
import {defaultLanguage, hashLanguage, language, LANGUAGES, numbers, setLanguage, t, translatePage} from './i18n.js';
import {mapManager} from './map.js';
import {navigationManager} from './navigation.js';
import {setupOffline} from './offline.js';
import {markerManager, privateIcon} from './markers.js';
import {playerManager} from './players.js';
import {regionManager} from './regions.js';
import {searchPalette} from './search.js';
import {settingsDialog} from './settings.js';
import {skyManager} from './sky.js';
import {chunkSelection} from './selection.js';
import {waypointEditor} from './waypoints.js';
import {regionEditor, regionList} from './regioneditor.js';
import {armed, element, modal} from './ui.js';
import {wsManager} from './websocket.js';

let serverName = document.getElementById('server-name').textContent;

const $ = (id) => document.getElementById(id);

let worlds = [];
let currentWorld = null;

async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`HTTP ${response.status} for ${url}`);
    }
    return response.json();
}

function worldLabel(world) {
    return world.displayName ?? world.name;
}

function worldTitle(world) {
    const name = element('span', 'world-tab__name', worldLabel(world));
    if (world.private) {
        name.append(privateIcon());
    }
    return name;
}

function worldEnv(world) {
    return element('span', 'label', t(`env.${world.environment}`));
}

function worldButton(world, className, ...content) {
    const button = element('button', className);
    button.type = 'button';
    button.setAttribute('aria-current', String(world.name === currentWorld?.name));
    button.append(...content);
    button.addEventListener('click', () => {
        $('world-menu').hidePopover();
        void selectWorld(world.name);
    });
    return button;
}

function renderWorlds() {
    const shown = currentWorld ?? worlds[0];
    const label = element('span', 'world-switch__label');
    label.append(worldEnv(shown), worldTitle(shown));
    const toggle = element('button', 'world-tab world-switch');
    toggle.type = 'button';
    toggle.setAttribute('popovertarget', 'world-menu');
    toggle.append(label);
    toggle.insertAdjacentHTML('beforeend', `<svg aria-hidden="true" class="world-switch__chevron" height="6" shape-rendering="crispEdges" viewBox="0 0 5 3" width="10">
<path d="M0 0h5v1H0zM1 1h3v1H1zM2 2h1v1H2z" fill="currentColor"/></svg>`);

    const nav = $('world-list');
    nav.replaceChildren(toggle, ...worlds.map(world => worldButton(world, 'world-tab', worldEnv(world), worldTitle(world))));
    $('world-menu').replaceChildren(...Object.values(Object.groupBy(worlds, world => world.environment)).map(group => {
        const section = element('div', 'menu__group');
        section.append(element('p', 'label menu__label', t(`env.${group[0].environment}`)),
            ...group.map(world => worldButton(world, 'menu__item', worldTitle(world))));
        return section;
    }));
    fitWorlds();
    $('player-scope').hidden = worlds.length < 2;
}

function fitWorlds() {
    const nav = $('world-list');
    const crowded = () => nav.scrollWidth > nav.clientWidth;
    nav.classList.remove('is-compact');
    $('server-name').classList.remove('is-crowded');
    if (crowded()) {
        nav.classList.add('is-compact');
    }
    if (crowded()) {
        $('server-name').classList.add('is-crowded');
    }
}

new ResizeObserver(fitWorlds).observe($('world-list'));
document.fonts.addEventListener('loadingdone', fitWorlds);

$('world-menu').addEventListener('beforetoggle', (event) => {
    if (event.newState === 'open') {
        const left = document.querySelector('.world-switch').getBoundingClientRect().left;
        event.target.style.setProperty('--switch-left', `${left}px`);
    }
});

async function selectWorld(name) {
    const world = worlds.find(w => w.name === name);
    if (!world || world === currentWorld) {
        return;
    }
    currentWorld = world;
    renderWorlds();
    updateTitle();

    chunkSelection.stop();
    mapManager.setWorld(world);
    playerManager.setWorld(world.name);
    skyManager.setWorld(world.name);
    navigationManager.setWorld(world.name);
    wsManager.subscribe(world.name);
    void markerManager.load(world.name);
    void regionManager.load(world.name);
}

async function loadPlayers() {
    try {
        playerManager.updatePlayers(await fetchJson('api/players'));
    } catch (error) {
        console.error('Failed to load players:', error);
    }
}

async function loadSky() {
    try {
        skyManager.update(await fetchJson('api/sky'));
    } catch (error) {
        console.error('Failed to load the sky:', error);
    }
}

async function loadWorlds() {
    try {
        worlds = await fetchJson('api/worlds');
    } catch (error) {
        console.error('Failed to load worlds:', error);
        $('world-list').textContent = t('worlds.error');
        return;
    }
    if (worlds.length === 0) {
        $('world-list').textContent = t('worlds.none');
        return;
    }
    if (!await applyHash()) {
        await selectWorld(worlds[0].name);
    }
}

function updateTitle() {
    if (currentWorld) {
        document.title = `${worldLabel(currentWorld)} – ${serverName || 'TAERRA'}`;
    }
}

function debugInfo() {
    return [
        'TAERRA debug',
        `Plugin: ${$('about-version-text').textContent || 'unknown'}`,
        `Browser: ${navigator.userAgent}`,
        `Screen: ${screen.width}x${screen.height} at ${devicePixelRatio}x, window ${innerWidth}x${innerHeight}`,
        `Language: ${navigator.language}, map in ${language}`
    ].join('\n');
}

function showVersion(version) {
    const build = version.match(/^(?:pr\d+-)?(\d+)-\p{Hex_Digit}+$/u)?.[1];
    $('about-build').textContent = build ? t('about.build', {number: build}) : t('about.dev');
    const commit = version.match(/(?<=^|-)\p{Hex_Digit}{7,}(?=(?:-dirty)?(?:-dev)?$)/u);
    if (commit) {
        const link = element('a', 'about__commit', commit[0]);
        link.href = `https://github.com/aerulion/taerra/commit/${commit[0]}`;
        link.target = '_blank';
        $('about-version-text').replaceChildren(version.slice(0, commit.index), link,
            version.slice(commit.index + commit[0].length));
    } else {
        $('about-version-text').textContent = version;
    }
    $('about-report').href = `https://github.com/aerulion/taerra/issues/new?template=bug-plugin.yml&debug=${
        encodeURIComponent(debugInfo())}`;
}

async function refreshServerName() {
    try {
        const config = await fetchJson('api/config');
        showVersion(config.version ?? '');
        const name = config.name ?? '';
        if (name === serverName) {
            return;
        }
        serverName = name;
        $('server-name').textContent = name;
        $('server-name').hidden = !name;
        updateTitle();
    } catch (error) {
        console.error('Failed to load the server name:', error);
    }
}

async function refreshWorlds() {
    try {
        worlds = await fetchJson('api/worlds');
    } catch (error) {
        console.error('Failed to load worlds:', error);
        return;
    }
    currentWorld = worlds.find(world => world.name === currentWorld?.name) ?? null;
    mapManager.setDepth(currentWorld?.depth);
    mapManager.setBounds(currentWorld?.bounds);
    if (worlds.length === 0) {
        $('world-list').textContent = t('worlds.none');
        return;
    }
    renderWorlds();
    updateTitle();
    if (!currentWorld) {
        await selectWorld(worlds[0].name);
    }
    await Promise.all([loadPlayers(), loadSky()]);
}

function placeParams(block) {
    const params = new URLSearchParams({
        world: currentWorld.name,
        x: block.x,
        z: block.z,
        zoom: mapManager.getMap().getZoom()
    });
    if (Number.isFinite(block.y)) {
        params.set('y', block.y);
    }
    if (mapManager.isIsometric()) {
        params.set('view', 'iso');
        params.set('r', mapManager.getRotation());
    }
    return params;
}

function writeHash() {
    if (!currentWorld) {
        return;
    }
    const params = placeParams(mapManager.latLngToBlock(mapManager.getMap().getCenter()));
    if (panelCollapsed() !== smallScreen.matches) {
        params.set('panel', panelCollapsed() ? 'hidden' : 'shown');
    }
    if (mapStyle() !== 'color') {
        params.set('style', mapStyle());
    }
    if (language !== defaultLanguage) {
        params.set('lang', language);
    }
    markerManager.writeVisibility(params);
    history.replaceState(null, '', `#${params}`);
}

async function applyHash() {
    const params = new URLSearchParams(location.hash.slice(1));
    if (hashLanguage(params) !== language) {
        location.reload();
        return true;
    }
    applyPanelHash(params);
    markerManager.readVisibility(params);
    setMapStyle(params.get('style'));
    const world = worlds.find(w => w.name === params.get('world'));
    if (!world) {
        return false;
    }
    await selectWorld(world.name);
    mapManager.setViewMode(params.get('view') === 'iso' ? 'iso' : 'top');
    if (params.has('r')) {
        mapManager.setRotation(Number(params.get('r')));
    }
    syncViewControls();
    const [x, z, zoom] = ['x', 'z', 'zoom'].map(key => Number(params.get(key)));
    if (Number.isFinite(x) && Number.isFinite(z)) {
        const y = params.has('y') ? Number(params.get('y')) : undefined;
        mapManager.centerOnBlock(x, z, Number.isFinite(zoom) ? zoom : undefined, y);
    }
    return true;
}

const MAP_STYLES = ['color', 'gray'];

function mapStyle() {
    return $('map').dataset.style;
}

function setMapStyle(style) {
    $('map').dataset.style = MAP_STYLES.includes(style) ? style : 'color';
    markerManager.setMapStyle(mapStyle());
    for (const button of $('map-style-buttons').querySelectorAll('button')) {
        button.setAttribute('aria-pressed', String(button.dataset.style === mapStyle()));
    }
}

function setupMapStyle() {
    $('map-style-buttons').addEventListener('click', (event) => {
        const button = event.target.closest('button');
        setMapStyle(button ? button.dataset.style : MAP_STYLES[(MAP_STYLES.indexOf(mapStyle()) + 1) % MAP_STYLES.length]);
        writeHash();
    });
}

function setupViewControls() {
    $('world-spawn-button').addEventListener('click', () => {
        const spawn = currentWorld?.spawn;
        if (spawn) {
            mapManager.centerOnBlock(spawn.x, spawn.z, Math.max(mapManager.getMap().getZoom(), 2));
        }
    });

    for (const button of $('view-mode-buttons').querySelectorAll('button')) {
        button.addEventListener('click', () => {
            mapManager.setViewMode(button.dataset.view);
            syncViewControls();
        });
    }
    const rotate = (turn) => {
        mapManager.setRotation(mapManager.getRotation() + turn);
        syncViewControls();
    };
    for (const button of $('rotation-controls').querySelectorAll('button')) {
        button.addEventListener('click', () => rotate(Number(button.dataset.turn)));
    }
    $('compass').addEventListener('click', () => rotate(-1));
    syncViewControls();
}

function syncViewControls() {
    for (const button of $('view-mode-buttons').querySelectorAll('button')) {
        button.setAttribute('aria-pressed', String((button.dataset.view === 'iso') === mapManager.isIsometric()));
    }
    $('rotation-controls').hidden = !mapManager.isIsometric();
    const compassLabel = mapManager.isIsometric() ? t('compass.rotate') : t('compass');
    $('compass').disabled = !mapManager.isIsometric();
    $('compass').title = compassLabel;
    $('compass').setAttribute('aria-label', compassLabel);
    const direction = t(`direction.${mapManager.getRotation()}`);
    $('rotation-readout').textContent = direction;
    $('rotation-readout').title = t('view.facing', {direction});
    $('rotation-readout').setAttribute('aria-label', t('view.facing', {direction}));
    playerManager.reposition();
    markerManager.redraw();
    regionManager.redraw();
    navigationManager.redraw();
    chunkSelection.redraw();
}

function setupLanguage() {
    const select = $('language-select');
    select.replaceChildren(...Object.entries(LANGUAGES).map(([code, name]) => new Option(name, code)));
    select.value = language;
    select.addEventListener('change', () => setLanguage(select.value));
}

function setupDialogs() {
    document.querySelectorAll('dialog').forEach(modal);
    for (const name of ['shortcuts', 'about']) {
        $(`${name}-open`).addEventListener('click', () => {
            if ($('account-menu').matches(':popover-open')) {
                $('account-menu').hidePopover();
            }
            $(name).showModal();
        });
    }
    $('about-open').addEventListener('click', () => void refreshServerName());
    let copiedTimer;
    $('about-copy').addEventListener('click', async () => {
        await copyText($('about-version-text').textContent);
        clearTimeout(copiedTimer);
        $('about-copy').dataset.icon = 'check';
        copiedTimer = setTimeout(() => $('about-copy').dataset.icon = 'copy', 1500);
    });
    let debugTimer;
    $('about-debug').addEventListener('click', async () => {
        await copyText(debugInfo());
        clearTimeout(debugTimer);
        $('about-debug').textContent = t('menu.copied');
        debugTimer = setTimeout(() => $('about-debug').textContent = t('about.debug'), 1500);
    });
}

function setupLayers() {
    $('layer-list').addEventListener('change', writeHash);
}

const smallScreen = matchMedia('(max-width: 720px)');

function panelCollapsed() {
    return $('panel').classList.contains('is-collapsed');
}

function setPanelCollapsed(collapsed) {
    $('panel').classList.toggle('is-collapsed', collapsed);
    $('panel-toggle').setAttribute('aria-expanded', String(!collapsed));
}

function applyPanelHash(params) {
    setPanelCollapsed(params.has('panel') ? params.get('panel') === 'hidden' : smallScreen.matches);
}

function setupPanel() {
    $('panel-toggle').addEventListener('click', () => {
        setPanelCollapsed(!panelCollapsed());
        writeHash();
    });
}

function setupShortcuts() {
    document.addEventListener('keydown', (event) => {
        if (event.ctrlKey || event.metaKey || event.altKey || event.repeat || event.key.length !== 1 ||
            event.target.closest('input, select, textarea') || document.querySelector('dialog[open]')) {
            return;
        }
        const combo = `${event.shiftKey ? 'Shift+' : ''}${event.key.toUpperCase()}`;
        const button = document.querySelector(`[aria-keyshortcuts="${combo}"]`);
        if (button) {
            event.preventDefault();
            button.click();
        }
    });
}

function formatDuration(millis) {
    const minutes = Math.max(1, Math.round(millis / 60000));
    const unit = (value, unit) => new Intl.NumberFormat(language, {style: 'unit', unit}).format(value);
    return minutes < 60 ? unit(minutes, 'minute') : `${unit(Math.floor(minutes / 60), 'hour')} ${unit(minutes % 60, 'minute')}`;
}

function worldName(name) {
    const world = worlds.find(w => w.name === name);
    return world ? worldLabel(world) : name;
}

function renderProgress({render, queued = 0, inFlight = 0}) {
    renderingWorld = render?.world ?? null;
    const pending = queued + inFlight;
    $('render-section').hidden = !render && pending === 0;
    const bar = $('render-bar').parentElement;

    if (render) {
        const scanning = render.phase === 'scanning';
        const percent = Math.max(0, Math.min(100, render.percent ?? 0));
        $('render-title').textContent = t('render.title');
        $('render-what').hidden = false;
        $('render-what').textContent = t(render.kind === 'area' ? 'render.area' : 'render.full', {world: worldName(render.world)});
        $('render-percent').hidden = false;
        $('render-percent').textContent = `${Math.floor(percent)}%`;
        bar.classList.remove('is-indeterminate');
        $('render-bar').style.width = `${percent}%`;
        let detail;
        if (scanning) {
            detail = render.totalRegions > 0
                ? t('render.scanning', {
                    done: numbers.format(render.scannedRegions),
                    total: numbers.format(render.totalRegions)
                })
                : t('render.starting');
        } else {
            detail = t('render.drawing', {
                done: numbers.format(render.completedTiles),
                total: numbers.format(render.totalTiles)
            });
            if (Number.isFinite(render.remainingMillis)) {
                detail += `\n${t('render.remaining', {time: formatDuration(render.remainingMillis)})}`;
            }
        }
        $('render-detail').textContent = detail;
    } else {
        $('render-title').textContent = t('render.updating');
        $('render-what').hidden = true;
        $('render-percent').hidden = true;
        bar.classList.add('is-indeterminate');
        $('render-bar').style.width = '';
        $('render-detail').textContent = t('render.changes', {count: numbers.format(pending)});
    }
}

let renderingWorld = null;

async function renderButton(button, action) {
    button.disabled = true;
    let response;
    try {
        response = await fetch('api/render', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({action, world: currentWorld.name})
        });
    } catch {
        response = null;
    }
    button.disabled = false;
    button.classList.remove('is-armed');
    const done = response?.ok === true;
    const full = action === 'full';
    button.textContent = t(done ? (full ? 'menu.renderStarted' : 'menu.renderCanceled')
        : response?.status === 409 ? (full ? 'menu.renderBusy' : 'menu.renderIdle') : 'selection.failed');
    button.classList.add(done ? 'is-done' : 'is-failed');
    return done;
}

async function teleportButton(button, {world, x, y, z}) {
    button.disabled = true;
    let response;
    try {
        response = await fetch('api/teleport', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({world, x, y: Number.isFinite(y) ? y : null, z})
        });
    } catch {
        response = null;
    }
    button.disabled = false;
    const done = response?.ok === true;
    button.textContent = t(done ? 'menu.teleported' : response?.status === 409 ? 'menu.offline' : 'menu.failed');
    button.classList.add(done ? 'is-done' : 'is-failed');
    return done;
}

function placeLink(block) {
    return `${location.origin}${location.pathname}#${placeParams(block)}`;
}

function setupContextMenu() {
    const menu = $('context-menu');
    let block = null;
    let regionTarget = null;
    let closeTimer = 0;

    const close = () => {
        clearTimeout(closeTimer);
        const focused = menu.contains(document.activeElement);
        menu.hidden = true;
        block = null;
        if (focused) {
            mapManager.getMap().getContainer().focus({preventScroll: true});
        }
    };

    mapManager.getMap().on('contextmenu', (event) => {
        if (!currentWorld) {
            return;
        }
        block = mapManager.latLngToBlock(event.latlng);
        const exact = Number.isFinite(block.y);
        $('context-menu-title').textContent = exact ? `${block.x}, ${block.y}, ${block.z}` : `${block.x}, ${block.z}`;
        for (const button of menu.querySelectorAll('button')) {
            button.classList.remove('is-done', 'is-failed', 'is-armed');
            button.textContent = t(button.dataset.i18n);
        }
        menu.querySelector('[data-action="navigate"]').hidden = !accountManager.usesMod();
        menu.querySelector('[data-action="teleport"]').hidden = !accountManager.canTeleport();
        const placing = accountManager.canEditMarkers() || accountManager.canKeepPersonal();
        menu.querySelector('[data-action="waypoint"]').hidden = !placing;
        menu.querySelector('[data-action="select"]').hidden = !accountManager.canRenderArea();
        const owner = regionManager.at(Math.floor(block.x / 16), Math.floor(block.z / 16));
        const region = menu.querySelector('[data-action="region"]');
        region.hidden = !accountManager.canEditRegions();
        region.dataset.i18n = !owner ? 'menu.drawRegion' : owner.name ? 'menu.editRegion' : 'menu.editUnnamedRegion';
        region.textContent = t(region.dataset.i18n, {name: owner?.name});
        regionTarget = owner;
        const rendering = renderingWorld === currentWorld.name;
        menu.querySelector('[data-action="render-world"]').hidden = !accountManager.canRenderWorld() || rendering;
        menu.querySelector('[data-action="cancel-render"]').hidden = !accountManager.canCancelRender() || !rendering;
        menu.querySelector('[data-action="area"]').hidden = !placing;
        menu.querySelector('[data-action="line"]').hidden = !placing;
        menu.hidden = false;
        const stage = menu.parentElement.getBoundingClientRect();
        const x = Math.min(event.containerPoint.x, stage.width - menu.offsetWidth - 8);
        const y = Math.min(event.containerPoint.y, stage.height - menu.offsetHeight - 8);
        menu.style.transform = `translate(${Math.max(8, x)}px, ${Math.max(8, y)}px)`;
        menu.querySelector('button').focus();
    });

    menu.addEventListener('click', async (event) => {
        const button = event.target.closest('button');
        if (!button || !block) {
            return;
        }
        const exact = Number.isFinite(block.y);
        const action = button.dataset.action;
        if (action === 'center') {
            mapManager.centerOnBlock(block.x, block.z, undefined, block.y);
            close();
            return;
        }
        if (action === 'teleport') {
            await teleport(button, block);
            return;
        }
        if (action === 'render-world' || action === 'cancel-render') {
            if (!armed(button, t(action === 'render-world' ? 'menu.renderConfirm' : 'menu.cancelConfirm',
                {world: worldLabel(currentWorld)}))) {
                return;
            }
            const done = await renderButton(button, action === 'render-world' ? 'full' : 'cancel');
            closeTimer = setTimeout(close, done ? 1200 : 2500);
            return;
        }
        if (action === 'navigate') {
            const {x, z} = block;
            const y = exact ? block.y + 1 : null;
            close();
            void navigationManager.navigate({
                label: exact ? `${x}, ${y}, ${z}` : `${x}, ${z}`, world: currentWorld.name,
                x: x + .5, y, z: z + .5, color: '#ffffff', player: null
            });
            return;
        }
        if (action === 'region') {
            close();
            chunkSelection.start(currentWorld.name, 'region', regionTarget);
            return;
        }
        if (action === 'select' || action === 'area' || action === 'line') {
            close();
            chunkSelection.start(currentWorld.name, action === 'select' ? 'chunks' : action);
            return;
        }
        if (action === 'waypoint') {
            const {x, z} = block;
            const y = exact ? block.y + 1 : undefined;
            close();
            void waypointEditor.open({world: currentWorld.name, x, y, z});
            return;
        }
        await copyText(action === 'coordinates' ? (exact ? `${block.x} ${block.y} ${block.z}` : `${block.x} ${block.z}`)
            : placeLink(block));
        button.textContent = t('menu.copied');
        button.classList.add('is-done');
        closeTimer = setTimeout(close, 700);
    });

    async function teleport(button, target) {
        const y = Number.isFinite(target.y) ? target.y + 1 : null;
        const done = await teleportButton(button, {world: currentWorld.name, x: target.x, y, z: target.z});
        closeTimer = setTimeout(close, done ? 700 : 2000);
    }

    document.addEventListener('pointerdown', (event) => {
        if (!menu.hidden && !menu.contains(event.target)) {
            close();
        }
    });
    document.addEventListener('keydown', (event) => {
        if (menu.hidden) {
            return;
        }
        if (event.key === 'Escape' || event.key === 'Tab') {
            event.preventDefault();
            close();
        } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            const items = [...menu.querySelectorAll('button:not([hidden])')];
            const step = event.key === 'ArrowDown' ? 1 : -1;
            items.at((items.indexOf(document.activeElement) + step) % items.length).focus();
        }
    });
    mapManager.getMap().on('movestart zoomstart', close);
}

wsManager.on('progress', renderProgress);
wsManager.on('disconnect', () => $('render-section').hidden = true);

wsManager.on('players', (message) => playerManager.updatePlayers(message.data ?? []));

wsManager.on('sky', (message) => skyManager.update(message.data));

wsManager.on('tile_updated', (message) => {
    if (message.world && message.world !== currentWorld?.name) {
        return;
    }
    if (message.bulk) {
        mapManager.refreshAllTiles();
    } else {
        mapManager.refreshTiles(message.tiles);
    }
});

wsManager.on('markers', (message) => {
    if (message.world === currentWorld?.name) {
        void markerManager.load(message.world);
    }
});

wsManager.on('worlds', () => void refreshWorlds());

const discovery = $('discovery');
let discoveryTimer;
discovery.addEventListener('click', () => discovery.hidden = true);

function celebrate(region) {
    $('discovery-name').textContent = region;
    $('discovery-burst').replaceChildren(...Array.from({length: 28}, (_, index) => {
        const spark = document.createElement('i');
        const angle = index / 28 * 2 * Math.PI + Math.random() * .4;
        const reach = 90 + Math.random() * 110;
        spark.style.setProperty('--x', `${Math.round(Math.cos(angle) * reach)}px`);
        spark.style.setProperty('--y', `${Math.round(Math.sin(angle) * reach * .55)}px`);
        spark.style.setProperty('--delay', `${Math.round(Math.random() * 150)}ms`);
        return spark;
    }));
    discovery.hidden = false;
    discovery.classList.remove('is-shown');
    void discovery.offsetWidth;
    discovery.classList.add('is-shown');
    clearTimeout(discoveryTimer);
    discoveryTimer = setTimeout(() => discovery.hidden = true, 5000);
}

wsManager.on('regions', (message) => {
    if (message.discovered) {
        celebrate(message.discovered);
    }
    if (currentWorld) {
        void regionManager.load(currentWorld.name).then(() => regionList.refresh());
        void markerManager.load(currentWorld.name);
    }
    void loadPlayers();
});

let connectedBefore = false;
wsManager.on('config', () => void refreshServerName());
wsManager.on('connect', () => {
    if (connectedBefore) {
        void refreshServerName();
        void refreshWorlds();
        mapManager.refreshAllTiles();
        if (currentWorld) {
            void markerManager.load(currentWorld.name);
            void regionManager.load(currentWorld.name);
        }
        void loadPlayers();
    }
    connectedBefore = true;
});

translatePage();
const accountReady = accountManager.initialize().catch(error => console.error('Failed to log in:', error));
await mapManager.initialize();
void iconStore.initialize();
markerManager.initialize();
regionManager.initialize();
skyManager.initialize({
    worlds: () => worlds,
    worldName,
    selectWorld: (name) => void selectWorld(name)
});
navigationManager.initialize();
navigationManager.onShow = (target) => {
    void selectWorld(target.world);
    if (target.player) {
        playerManager.follow(target.player);
    } else {
        mapManager.centerOnBlock(Math.floor(target.x), Math.floor(target.z), Math.max(mapManager.getMap().getZoom(), 2),
            Number.isFinite(target.y) ? Math.floor(target.y) : undefined);
    }
};
waypointEditor.initialize();
regionEditor.initialize();
regionList.initialize({
    create: () => currentWorld && chunkSelection.start(currentWorld.name, 'region'),
    edit: (region) => currentWorld && chunkSelection.start(currentWorld.name, 'region', region)
});
settingsDialog.initialize();
iconChooser.initialize();
iconEditor.initialize();
iconManager.initialize();
chunkSelection.initialize();
searchPalette.initialize({
    worlds: () => worlds,
    current: () => currentWorld?.name,
    worldName,
    selectWorld: (name) => void selectWorld(name)
});
waypointManager.initialize({
    worlds: () => worlds,
    current: () => currentWorld?.name,
    worldName,
    selectWorld: (name) => void selectWorld(name)
});
playerManager.worldLabel = worldName;
playerManager.onWorld = selectWorld;
markerManager.onPlayers = () => playerManager.showAll();
markerManager.onLayers = () => regionManager.redraw();
markerManager.onWaypoint = async (action, waypoint, button) => {
    if (action === 'navigate') {
        mapManager.getMap().closePopup();
        void navigationManager.navigate({
            label: waypoint.name, world: waypoint.world, x: waypoint.x + .5, y: waypoint.y, z: waypoint.z + .5,
            color: waypoint.color, player: null
        });
        return;
    }
    if (action === 'link') {
        await copyText(placeLink(waypoint));
        button.textContent = t('menu.copied');
        button.classList.add('is-done');
        return;
    }
    if (action === 'teleport') {
        if (await teleportButton(button, waypoint)) {
            setTimeout(() => mapManager.getMap().closePopup(), 700);
        }
        return;
    }
    mapManager.getMap().closePopup();
    if (action === 'move') {
        waypointEditor.move(waypoint);
    } else if (waypoint.type) {
        chunkSelection.start(waypoint.world, waypoint.type, waypoint);
    } else {
        void waypointEditor.open(waypoint);
    }
};
setupLanguage();
setupDialogs();
setupPanel();
setupOffline();
setupViewControls();
setupContextMenu();
setupShortcuts();
setupMapStyle();
setupLayers();
await accountReady;
wsManager.connect();
await loadWorlds();
await Promise.all([loadPlayers(), loadSky()]);
let hashTimer = 0;
mapManager.getMap().on('moveend', () => {
    clearTimeout(hashTimer);
    hashTimer = setTimeout(writeHash, 300);
});
mapManager.getMap().on('dragstart', () => playerManager.unfollow());
writeHash();
addEventListener('hashchange', applyHash);
