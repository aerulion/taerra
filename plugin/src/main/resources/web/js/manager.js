import {accountManager} from './account.js';
import {language, numbers, t} from './i18n.js';
import {mapManager} from './map.js';
import {markerIcon, markerManager, PERSONAL_LAYER} from './markers.js';
import {playerManager} from './players.js';
import {armed, element, iconButton} from './ui.js';
import {waypointUrl} from './waypoints.js';

const $ = (id) => document.getElementById(id);

const FORMAT = 'taerra-waypoints';
const VERSION = 1;
const MAX = 100;
const ALL = '';
const STATUS_MILLIS = 6000;
const meters = new Intl.NumberFormat(language, {style: 'unit', unit: 'meter', maximumFractionDigits: 0});
const kilometers = new Intl.NumberFormat(language, {style: 'unit', unit: 'kilometer', maximumFractionDigits: 1});

const fold = (text) => text.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase();
const key = (name) => name.toLowerCase();

function center(waypoint) {
    if (waypoint.type === 'point') {
        return [waypoint.x + .5, waypoint.y, waypoint.z + .5];
    }
    const xs = waypoint.points.map(point => point[0]);
    const zs = waypoint.points.map(point => point.at(-1));
    return [(Math.min(...xs) + Math.max(...xs)) / 2, undefined, (Math.min(...zs) + Math.max(...zs)) / 2];
}

function distanceText(blocks) {
    return blocks < 1000 ? meters.format(blocks) : kilometers.format(blocks / 1000);
}

function today() {
    return new Date().toISOString().slice(0, 10);
}

class WaypointManager {
    constructor() {
        this.dialog = $('manager');
        this.waypoints = [];
        this.selected = new Set();
        this.incoming = null;
        this.statusTimer = 0;
        this.app = null;
    }

    initialize(app) {
        this.app = app;
        $('manager-filter').placeholder = t('manager.filter');
        $('manager-open').addEventListener('click', () => void this.open());
        for (const id of ['manager-filter', 'manager-world', 'manager-sort']) {
            $(id).addEventListener('input', () => this.render());
        }
        $('manager-all').addEventListener('change', (event) => {
            for (const waypoint of this.visible()) {
                event.target.checked ? this.selected.add(key(waypoint.name)) : this.selected.delete(key(waypoint.name));
            }
            this.render();
        });
        $('manager-export').addEventListener('click', () => this.export());
        $('manager-delete').addEventListener('click', () => void this.deleteSelected());
        $('manager-import').addEventListener('click', () => $('manager-file').click());
        $('manager-file').addEventListener('change', () => void this.read());
        $('manager-import-cancel').addEventListener('click', () => this.showImport(null));
        $('manager-import-add').addEventListener('click', () => void this.import('add'));
        $('manager-import-replace').addEventListener('click', () => void this.import('replace'));
        this.dialog.addEventListener('dragover', (event) => event.preventDefault());
        this.dialog.addEventListener('drop', (event) => {
            event.preventDefault();
            const file = event.dataTransfer?.files?.[0];
            if (file) {
                void this.read(file);
            }
        });
    }

    async open() {
        if (!accountManager.canKeepPersonal()) {
            return;
        }
        $('account-menu').hidePopover?.();
        this.showImport(null);
        this.show('');
        this.fillWorlds();
        if (!this.dialog.open) {
            this.dialog.showModal();
        }
        await this.load();
    }

    fillWorlds() {
        const select = $('manager-world');
        const chosen = select.value;
        select.replaceChildren(new Option(t('manager.allWorlds'), ALL),
            ...this.app.worlds().map(world => new Option(this.app.worldName(world.name), world.name)));
        select.value = [...select.options].some(option => option.value === chosen) ? chosen : ALL;
    }

    async load() {
        try {
            const response = await fetch('api/waypoints/personal');
            this.waypoints = response.ok ? await response.json() : [];
        } catch {
            this.waypoints = [];
        }
        const names = new Set(this.waypoints.map(waypoint => key(waypoint.name)));
        for (const name of this.selected) {
            if (!names.has(name)) {
                this.selected.delete(name);
            }
        }
        this.render();
    }

    origin(world) {
        const own = playerManager.entries.get(accountManager.ownUuid())?.player;
        if (own && own.world === world) {
            return [own.x, own.z];
        }
        if (world === this.app.current()) {
            const block = mapManager.latLngToBlock(mapManager.getMap().getCenter());
            return [block.x, block.z];
        }
        return null;
    }

    distance(waypoint) {
        const from = this.origin(waypoint.world);
        if (!from) {
            return null;
        }
        const [x, , z] = center(waypoint);
        return Math.hypot(x - from[0], z - from[1]);
    }

    visible() {
        const world = $('manager-world').value;
        const query = fold($('manager-filter').value.trim());
        const shown = this.waypoints
            .filter(waypoint => world === ALL || waypoint.world === world)
            .filter(waypoint => !query || fold(waypoint.name).includes(query)
                || fold(waypoint.description ?? '').includes(query))
            .map(waypoint => ({waypoint, distance: this.distance(waypoint)}));
        if ($('manager-sort').value === 'distance') {
            shown.sort((a, b) => (a.distance ?? Infinity) - (b.distance ?? Infinity)
                || a.waypoint.name.localeCompare(b.waypoint.name, language));
        } else {
            shown.sort((a, b) => a.waypoint.name.localeCompare(b.waypoint.name, language));
        }
        return shown.map(({waypoint, distance}) => Object.assign(waypoint, {distance}));
    }

    render() {
        const visible = this.visible();
        $('manager-count').textContent = `${numbers.format(this.waypoints.length)} / ${numbers.format(MAX)}`;
        $('manager-list').replaceChildren(...visible.map(waypoint => this.row(waypoint)));
        const empty = this.waypoints.length === 0;
        $('manager-empty').hidden = visible.length > 0;
        $('manager-empty').textContent = t(empty ? 'manager.none' : 'manager.noMatch');
        const picked = visible.filter(waypoint => this.selected.has(key(waypoint.name))).length;
        $('manager-all').checked = picked > 0 && picked === visible.length;
        $('manager-all').indeterminate = picked > 0 && picked < visible.length;
        $('manager-all').closest('label').hidden = visible.length === 0;
        const count = this.selected.size;
        $('manager-export').textContent = count > 0
            ? t('manager.exportSome', {count: numbers.format(count)}) : t('manager.exportAll');
        $('manager-export').disabled = empty;
        $('manager-delete').hidden = count === 0;
        $('manager-delete').classList.remove('is-armed');
        $('manager-delete').textContent = t('manager.deleteSome', {count: numbers.format(count)});
    }

    row(waypoint) {
        const name = key(waypoint.name);
        const row = element('li', 'row manager__row');
        row.classList.toggle('is-hidden', Boolean(waypoint.hidden));
        const check = document.createElement('input');
        check.type = 'checkbox';
        check.className = 'checkbox';
        check.checked = this.selected.has(name);
        check.setAttribute('aria-label', t('manager.select', {name: waypoint.name}));
        check.addEventListener('change', () => {
            check.checked ? this.selected.add(name) : this.selected.delete(name);
            this.render();
        });
        const icon = element('span', `marker-icon marker-icon--${waypoint.type} marker-icon--personal`);
        icon.style.setProperty('--marker', waypoint.color);
        if (waypoint.type === 'point') {
            icon.innerHTML = markerIcon({personal: true});
        }
        const text = element('button', 'row__text manager__text');
        text.type = 'button';
        text.title = t('manager.showOnMap');
        const where = waypoint.type === 'point' ? `${waypoint.x}, ${waypoint.y}, ${waypoint.z}`
            : t(`manager.type.${waypoint.type}`);
        const meta = element('span', 'row__meta');
        meta.append(...[this.app.worldName(waypoint.world), where, waypoint.description]
            .filter(Boolean).map(part => element('span', 'manager__part', part)));
        text.append(element('span', 'row__name', waypoint.name), meta);
        text.addEventListener('click', () => this.showOnMap(waypoint));
        const distance = element('span', 'mono manager__distance',
            waypoint.distance === null ? '' : distanceText(waypoint.distance));
        const actions = element('span', 'manager__actions');
        const action = (name, label, run) => {
            const button = iconButton(name, t(label), (button) => void run(button));
            button.setAttribute('aria-label', `${t(label)}: ${waypoint.name}`);
            button.classList.toggle('icon-btn--danger', name === 'delete');
            actions.append(button);
        };
        action(waypoint.hidden ? 'hide' : 'show', waypoint.hidden ? 'manager.hidden' : 'manager.shown',
            () => this.setHidden([waypoint.name], !waypoint.hidden));
        if (waypoint.type === 'point' && accountManager.usesMod()) {
            action('navigate', 'waypoint.navigate', () => this.hand('navigate', waypoint));
        }
        if (waypoint.type === 'point' && accountManager.canTeleport()) {
            action('teleport', 'waypoint.teleport', (button) => this.teleport(waypoint, button));
        }
        action('edit', 'waypoint.editShort', () => {
            this.dialog.close();
            this.hand('edit', waypoint);
        });
        action('delete', 'waypoint.delete', (button) => this.delete(waypoint, button));
        row.append(check, icon, text, distance, actions);
        return row;
    }

    editable(waypoint) {
        return {
            world: waypoint.world, x: waypoint.x, y: waypoint.y, z: waypoint.z, name: waypoint.name,
            color: waypoint.color, personal: true, group: '', previous: waypoint.name,
            id: `${PERSONAL_LAYER}:${key(waypoint.name)}`, description: waypoint.description ?? '',
            ...(waypoint.type === 'point' ? {} : {
                type: waypoint.type, smooth: Boolean(waypoint.smooth),
                points: waypoint.type === 'area' ? waypoint.points.map(([x, z]) => [x, null, z]) : waypoint.points
            })
        };
    }

    hand(action, waypoint) {
        markerManager.onWaypoint?.(action, this.editable(waypoint));
    }

    showOnMap(waypoint) {
        this.dialog.close();
        this.app.selectWorld(waypoint.world);
        const [x, y, z] = center(waypoint);
        mapManager.centerOnBlock(Math.floor(x), Math.floor(z), Math.max(mapManager.getMap().getZoom(), 2),
            Number.isFinite(y) ? Math.floor(y) : undefined);
        if (!waypoint.hidden) {
            markerManager.reveal(PERSONAL_LAYER, `${PERSONAL_LAYER}:${key(waypoint.name)}`);
        }
    }

    async teleport(waypoint, button) {
        let response = null;
        try {
            response = await fetch('api/teleport', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({world: waypoint.world, x: waypoint.x, y: waypoint.y, z: waypoint.z})
            });
        } catch {
        }
        const done = response?.ok === true;
        button.classList.toggle('is-done', done);
        this.show(t(done ? 'menu.teleported' : response?.status === 409 ? 'menu.offline' : 'menu.failed'), !done);
    }

    async setHidden(names, hidden) {
        let response = null;
        try {
            response = await fetch('api/waypoints/personal/hidden', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({names, hidden})
            });
        } catch {
        }
        if (!response?.ok) {
            this.show(t('manager.failed'), true);
            return;
        }
        const keys = new Set(names.map(key));
        for (const waypoint of this.waypoints) {
            if (keys.has(key(waypoint.name))) {
                waypoint.hidden = hidden;
            }
        }
        this.render();
    }

    async delete(waypoint, button) {
        if (armed(button, t('waypoint.confirmDelete'))) {
            await this.remove([waypoint]);
        }
    }

    async deleteSelected() {
        if (!armed($('manager-delete'), t('waypoint.confirmDelete'))) {
            return;
        }
        await this.remove(this.waypoints.filter(waypoint => this.selected.has(key(waypoint.name))));
    }

    async remove(waypoints) {
        let failed = 0;
        for (const waypoint of waypoints) {
            try {
                const response = await fetch(waypointUrl(waypoint.name, true), {method: 'DELETE'});
                if (response.ok) {
                    this.selected.delete(key(waypoint.name));
                } else {
                    failed++;
                }
            } catch {
                failed++;
            }
        }
        if (failed > 0) {
            this.show(t('manager.failed'), true);
        }
        await this.load();
    }

    export() {
        const chosen = this.selected.size > 0
            ? this.waypoints.filter(waypoint => this.selected.has(key(waypoint.name))) : this.waypoints;
        const waypoints = chosen.map(({distance, ...waypoint}) => waypoint);
        const file = new Blob([JSON.stringify({format: FORMAT, version: VERSION, waypoints}, null, 2)],
            {type: 'application/json'});
        const link = document.createElement('a');
        link.href = URL.createObjectURL(file);
        link.download = `taerra-waypoints-${today()}.json`;
        document.body.append(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(link.href), 0);
        this.show(t('manager.exported', {count: numbers.format(waypoints.length)}));
    }

    async read(file = $('manager-file').files?.[0]) {
        $('manager-file').value = '';
        if (!file) {
            return;
        }
        let waypoints = null;
        try {
            const data = JSON.parse(await file.text());
            waypoints = Array.isArray(data) ? data : data?.waypoints;
        } catch {
        }
        if (!Array.isArray(waypoints) || waypoints.length === 0) {
            this.showImport(null);
            this.show(t('manager.notAFile', {file: file.name}), true);
            return;
        }
        this.showImport({file: file.name, waypoints});
    }

    showImport(incoming) {
        this.incoming = incoming;
        $('manager-importing').hidden = !incoming;
        if (!incoming) {
            return;
        }
        this.show('');
        $('manager-import-text').textContent = t('manager.importing',
            {count: numbers.format(incoming.waypoints.length), file: incoming.file});
        $('manager-import-replace').textContent = t('manager.replace',
            {count: numbers.format(this.waypoints.length)});
        $('manager-import-replace').hidden = this.waypoints.length === 0;
        $('manager-import-add').focus();
    }

    async import(mode) {
        const incoming = this.incoming;
        if (!incoming) {
            return;
        }
        for (const id of ['manager-import-add', 'manager-import-replace']) {
            $(id).disabled = true;
        }
        let response = null;
        try {
            response = await fetch('api/waypoints/personal/import', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({mode, waypoints: incoming.waypoints})
            });
        } catch {
        }
        for (const id of ['manager-import-add', 'manager-import-replace']) {
            $(id).disabled = false;
        }
        if (!response?.ok) {
            this.show(t(response?.status === 400 ? 'manager.notAFile' : 'manager.failed', {file: incoming.file}), true);
            return;
        }
        const {added, taken, full, invalid} = await response.json();
        const skipped = [
            taken > 0 && t('manager.skipped.taken', {count: numbers.format(taken)}),
            full > 0 && t('manager.skipped.full', {count: numbers.format(full), max: numbers.format(MAX)}),
            invalid > 0 && t('manager.skipped.invalid', {count: numbers.format(invalid)})
        ].filter(Boolean);
        this.showImport(null);
        this.show([added > 0 ? t('manager.imported', {count: numbers.format(added)}) : t('manager.importedNone'),
            ...skipped].join(' '),
            added === 0 && skipped.length > 0);
        await this.load();
    }

    show(message, failed = false) {
        clearTimeout(this.statusTimer);
        $('manager-status').textContent = message;
        $('manager-status').classList.toggle('is-failed', Boolean(message) && failed);
        $('manager-status').classList.toggle('is-done', Boolean(message) && !failed);
        if (message) {
            this.statusTimer = setTimeout(() => this.show(''), STATUS_MILLIS);
        }
    }
}

export const waypointManager = new WaypointManager();
