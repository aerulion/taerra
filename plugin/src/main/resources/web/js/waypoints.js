import {accountManager} from './account.js';
import {numbers, t} from './i18n.js';
import {mapManager} from './map.js';
import {element, GEM_SVG, markerManager, personalIcon, PIN_SVG, privateIcon} from './markers.js';

const $ = (id) => document.getElementById(id);

const DYES = [
    ['white', '#f9fffe'], ['light_gray', '#9d9d97'], ['gray', '#474f52'], ['black', '#1d1d21'],
    ['brown', '#835432'], ['red', '#b02e26'], ['orange', '#f9801d'], ['yellow', '#fed83d'],
    ['lime', '#80c71f'], ['green', '#5e7c16'], ['cyan', '#169c9c'], ['light_blue', '#3ab3da'],
    ['blue', '#3c44aa'], ['purple', '#8932b8'], ['magenta', '#c74ebd'], ['pink', '#f38baa']
];
export const DEFAULT_COLOR = '#3ab3da';
const NEW_GROUP = '\u0000new';
const PERSONAL = '\u0000personal';
const SHADE_STEP = .02;
const NOT_NAME = /[^\p{L}\p{N} _.,:'&()#!?+-]/gu;

const SAVE_ERRORS = {409: 'waypoint.taken', 422: 'waypoint.full'};

const cleanName = (text) => text.normalize('NFC').replace(NOT_NAME, '');

export const waypointUrl = (name, personal) =>
    `/api/waypoints/${encodeURIComponent(name)}${personal ? '?personal=true' : ''}`;

function hsvToHex(h, s, v) {
    const channel = (n) => {
        const k = (n + h / 60) % 6;
        return v - v * s * Math.max(0, Math.min(k, 4 - k, 1));
    };
    return `#${[5, 3, 1].map(n => Math.round(channel(n) * 255).toString(16).padStart(2, '0')).join('')}`;
}

function hexToHsv(hex) {
    const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255);
    const max = Math.max(r, g, b);
    const range = max - Math.min(r, g, b);
    const h = range === 0 ? 0
        : max === r ? ((g - b) / range + 6) % 6
            : max === g ? (b - r) / range + 2
                : (r - g) / range + 4;
    return {h: h * 60, s: max === 0 ? 0 : range / max, v: max};
}

const clamp = (value) => Math.min(1, Math.max(0, value));

class WaypointEditor {
    constructor() {
        this.dialog = $('waypoint-editor');
        this.target = null;
        this.hsv = hexToHsv(DEFAULT_COLOR);
        this.hex = DEFAULT_COLOR;
        this.moving = null;
        this.placingTimer = 0;
    }

    initialize() {
        $('waypoint-group-name').placeholder = t('waypoint.groupName');
        $('waypoint-description').placeholder = t('waypoint.descriptionHint');
        $('waypoint-private-title').append(privateIcon());
        $('placing-cancel').addEventListener('click', () => this.stopMoving());
        mapManager.getMap().on('click', (event) => void this.place(event.latlng));
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape' && this.moving) {
                this.stopMoving();
            }
        });
        $('waypoint-swatches').replaceChildren(...DYES.map(([dye, hex]) => {
            const swatch = element('label', 'swatch');
            swatch.title = t(`dye.${dye}`);
            swatch.style.setProperty('--swatch', hex);
            const radio = document.createElement('input');
            radio.type = 'radio';
            radio.name = 'waypoint-dye';
            radio.value = hex;
            radio.setAttribute('aria-label', t(`dye.${dye}`));
            radio.addEventListener('change', () => this.setColor(hex));
            swatch.append(radio);
            return swatch;
        }));

        this.setupShade();
        $('waypoint-hue').addEventListener('input', (event) => {
            this.hsv.h = Number(event.target.value);
            this.setColor(hsvToHex(this.hsv.h, this.hsv.s, this.hsv.v), false);
        });
        $('waypoint-hex').addEventListener('input', (event) => {
            const value = event.target.value.replace(/[^0-9a-f]/gi, '');
            if (value.length === 6) {
                this.setColor(`#${value.toLowerCase()}`, true, false);
            }
        });
        $('waypoint-hex').addEventListener('blur', () => $('waypoint-hex').value = this.hex.slice(1));

        $('waypoint-groups').addEventListener('change', () => {
            this.showPreview();
            const creating = this.selectedGroup() === NEW_GROUP;
            $('waypoint-new-group').hidden = !creating;
            if (creating) {
                $('waypoint-group-name').focus();
            }
        });
        for (const id of ['waypoint-name', 'waypoint-group-name']) {
            $(id).addEventListener('input', (event) => {
                const clean = cleanName(event.target.value);
                if (clean !== event.target.value) {
                    event.target.value = clean;
                }
            });
        }
        $('waypoint-cancel').addEventListener('click', () => this.dialog.close());
        $('waypoint-delete').addEventListener('click', () => this.remove());
        $('waypoint-form').addEventListener('input', () => this.showError(null));
        $('waypoint-form').addEventListener('submit', (event) => {
            event.preventDefault();
            void this.save();
        });
        this.dialog.addEventListener('click', (event) => {
            if (event.target === this.dialog) {
                this.dialog.close();
            }
        });
    }

    setupShade() {
        const field = $('waypoint-shade');
        const pick = (event) => {
            const box = field.getBoundingClientRect();
            this.hsv.s = clamp((event.clientX - box.left) / box.width);
            this.hsv.v = clamp(1 - (event.clientY - box.top) / box.height);
            this.setColor(hsvToHex(this.hsv.h, this.hsv.s, this.hsv.v), false);
        };
        field.addEventListener('pointerdown', (event) => {
            field.setPointerCapture(event.pointerId);
            field.focus();
            pick(event);
        });
        field.addEventListener('pointermove', (event) => {
            if (field.hasPointerCapture(event.pointerId)) {
                pick(event);
            }
        });
        field.addEventListener('keydown', (event) => {
            const step = SHADE_STEP * (event.shiftKey ? 5 : 1);
            const moves = {ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, step], ArrowDown: [0, -step]};
            const move = moves[event.key];
            if (move) {
                event.preventDefault();
                this.hsv.s = clamp(this.hsv.s + move[0]);
                this.hsv.v = clamp(this.hsv.v + move[1]);
                this.setColor(hsvToHex(this.hsv.h, this.hsv.s, this.hsv.v), false);
            }
        });
    }

    setColor(hex, fromHex = true, writeHex = true) {
        this.hex = hex;
        this.target?.onColor?.(hex);
        if (fromHex) {
            const hsv = hexToHsv(hex);
            this.hsv = {h: hsv.s === 0 ? this.hsv.h : hsv.h, s: hsv.s, v: hsv.v};
        }
        this.dialog.style.setProperty('--waypoint', hex);
        this.dialog.style.setProperty('--hue', `hsl(${this.hsv.h} 100% 50%)`);
        $('waypoint-hue').value = String(Math.round(this.hsv.h));
        const thumb = $('waypoint-shade-thumb');
        thumb.style.left = `${this.hsv.s * 100}%`;
        thumb.style.top = `${(1 - this.hsv.v) * 100}%`;
        $('waypoint-shade').setAttribute('aria-valuetext', hex);
        if (writeHex) {
            $('waypoint-hex').value = hex.slice(1);
        }
        for (const radio of $('waypoint-swatches').querySelectorAll('input')) {
            radio.checked = radio.value === hex;
        }
    }

    selectedGroup() {
        return $('waypoint-groups').querySelector('input:checked')?.value ?? '';
    }

    showPreview() {
        $('waypoint-preview').innerHTML = this.selectedGroup() === PERSONAL ? GEM_SVG : PIN_SVG;
    }

    renderGroups(groups, current) {
        const {personal, previous} = this.target;
        const chip = (value, label, isPrivate) => {
            const wrapper = element('label', value === NEW_GROUP ? 'chip chip--new' : 'chip');
            const radio = document.createElement('input');
            radio.type = 'radio';
            radio.name = 'waypoint-group';
            radio.value = value;
            radio.checked = value === current;
            const text = element('span', 'chip__label', label);
            if (isPrivate) {
                text.append(privateIcon());
            }
            if (value === PERSONAL) {
                text.append(personalIcon());
            }
            wrapper.append(radio, text);
            return wrapper;
        };
        const ownChip = (personal || !previous) && accountManager.canKeepPersonal();
        const serverChips = !personal && accountManager.canEditMarkers();
        $('waypoint-groups').replaceChildren(
            ...(ownChip ? [chip(PERSONAL, t('waypoint.personal'), false)] : []),
            ...(serverChips ? [
                chip('', t('waypoint.noGroup'), false),
                ...groups.map(group => chip(group.label, group.label, group.private)),
                chip(NEW_GROUP, t('waypoint.newGroup'), false)
            ] : []));
        if (!$('waypoint-groups').querySelector('input:checked')) {
            $('waypoint-groups').querySelector('input').checked = true;
        }
        $('waypoint-new-group').hidden = true;
        $('waypoint-group-name').value = '';
        $('waypoint-private').checked = false;
        $('waypoint-private-option').hidden = !accountManager.canCreatePrivateGroups();
        this.showPreview();
    }

    async open({
                   world, x, y, z, type = 'point', points = null, smooth = false,
                   name = '', description = '', color = DEFAULT_COLOR, group = '', previous = null,
                   personal = false, onSaved = null, onColor = null
               }) {
        this.target = {world, x, y, z, type, points, smooth, previous, personal, onSaved, onColor};
        const kind = type === 'point' ? 'waypoint' : type;
        $('waypoint-title').textContent = t(previous ? `${kind}.edit` : `${kind}.new`);
        $('waypoint-preview').hidden = Boolean(points);
        $('waypoint-where').textContent = points
            ? t('waypoint.points', {count: numbers.format(points.length)})
            : `${x}, ${Number.isFinite(y) ? y : '—'}, ${z}`;
        $('waypoint-name').value = cleanName(name);
        $('waypoint-description').value = description;
        $('waypoint-delete').hidden = !previous;
        this.disarm();
        this.showError(null);
        this.setColor(/^#[0-9a-f]{6}$/i.test(color) ? color.toLowerCase() : DEFAULT_COLOR);
        const current = personal || (!previous && accountManager.canKeepPersonal()) ? PERSONAL : group;
        this.renderGroups([], current);
        this.dialog.showModal();
        $('waypoint-name').focus();
        if (personal || !accountManager.canEditMarkers()) {
            return;
        }
        try {
            const response = await fetch('/api/waypoints/groups');
            if (response.ok && this.dialog.open) {
                this.renderGroups(await response.json(), current);
            }
        } catch (error) {
            console.error('Failed to load the waypoint groups:', error);
        }
    }

    async save() {
        const name = $('waypoint-name').value.trim();
        const creating = this.selectedGroup() === NEW_GROUP;
        const personal = this.selectedGroup() === PERSONAL;
        const group = creating ? $('waypoint-group-name').value.trim() : personal ? '' : this.selectedGroup();
        if (!/[\p{L}\p{N}]/u.test(name)) {
            this.showError(t('waypoint.noName'));
            $('waypoint-name').focus();
            return;
        }
        if (creating && !/[\p{L}\p{N}]/u.test(group)) {
            this.showError(t('waypoint.noGroupName'));
            $('waypoint-group-name').focus();
            return;
        }
        const {world, x, y, z, type, points, smooth, previous, onSaved} = this.target;
        const response = await this.send('/api/waypoints', {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                name, color: this.hex, group: group || null, description: $('waypoint-description').value,
                privateGroup: creating && $('waypoint-private').checked,
                world, previous, personal,
                ...(points ? {type, points, smooth} : {x, y: Number.isFinite(y) ? y : null, z})
            })
        });
        if (response?.ok) {
            this.dialog.close();
            onSaved?.();
            void markerManager.load(world);
        } else {
            this.showError(t(SAVE_ERRORS[response?.status] ?? 'waypoint.failed', {name}));
        }
    }

    async remove() {
        const button = $('waypoint-delete');
        if (!button.classList.contains('is-armed')) {
            button.classList.add('is-armed');
            button.textContent = t('waypoint.confirmDelete');
            return;
        }
        const {world, previous, personal, onSaved} = this.target;
        const response = await this.send(waypointUrl(previous, personal), {method: 'DELETE'});
        if (response?.ok) {
            this.dialog.close();
            onSaved?.();
            void markerManager.load(world);
        } else {
            this.disarm();
            this.showError(t('waypoint.failed', {name: previous}));
        }
    }

    move(waypoint) {
        this.moving = waypoint;
        clearTimeout(this.placingTimer);
        $('placing').classList.remove('is-failed');
        $('placing-text').textContent = t('waypoint.moveHint', {name: waypoint.name});
        $('placing').hidden = false;
    }

    stopMoving() {
        this.moving = null;
        $('placing').hidden = true;
    }

    async place(latLng) {
        const waypoint = this.moving;
        const world = markerManager.world;
        if (!waypoint || !world) {
            return;
        }
        const {x, y, z} = mapManager.latLngToBlock(latLng);
        this.stopMoving();
        const response = await this.send('/api/waypoints', {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                name: waypoint.name, color: waypoint.color, group: waypoint.group || null,
                description: waypoint.description, personal: waypoint.personal,
                world, x, y: Number.isFinite(y) ? y + 1 : null, z, previous: waypoint.previous
            })
        });
        if (response?.ok) {
            void markerManager.load(world);
            return;
        }
        $('placing').classList.add('is-failed');
        $('placing-text').textContent = t('waypoint.failed', {name: waypoint.name});
        $('placing').hidden = false;
        this.placingTimer = setTimeout(() => $('placing').hidden = true, 3000);
    }

    disarm() {
        $('waypoint-delete').classList.remove('is-armed');
        $('waypoint-delete').textContent = t('waypoint.delete');
    }

    async send(url, options) {
        try {
            return await fetch(url, options);
        } catch {
            return null;
        }
    }

    showError(message) {
        $('waypoint-error').hidden = !message;
        $('waypoint-error').textContent = message ?? '';
    }
}

export const waypointEditor = new WaypointEditor();
