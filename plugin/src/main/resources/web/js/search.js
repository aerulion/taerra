import {t} from './i18n.js';
import {mapManager} from './map.js';
import {markerIcon, markerManager} from './markers.js';
import {element} from './ui.js';
import {avatar, located, playerManager} from './players.js';

const $ = (id) => document.getElementById(id);

const LIMIT = 30;
const KINDS = ['place', 'player', 'marker', 'world'];
const COORDINATES = /^(-?\d+(?:\.\d+)?)(?:[\s,]+(-?\d+(?:\.\d+)?))?[\s,]+(-?\d+(?:\.\d+)?)$/;
const MAC = /Mac|iPhone|iPad/.test(navigator.platform);

const fold = (text) => text.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase();

function score(label, query) {
    const text = fold(label);
    if (text.startsWith(query)) {
        return 0;
    }
    if (text.split(/[\s_.,:'&()#!?+-]+/).some(word => word.startsWith(query))) {
        return 1;
    }
    return text.includes(query) ? 2 : -1;
}

function center(marker) {
    if (marker.type === 'point') {
        return marker.position;
    }
    const xs = marker.points.map(point => point[0]);
    const zs = marker.points.map(point => point.at(-1));
    return [(Math.min(...xs) + Math.max(...xs)) / 2, undefined, (Math.min(...zs) + Math.max(...zs)) / 2];
}

class SearchPalette {
    constructor() {
        this.dialog = $('search');
        this.input = $('search-input');
        this.list = $('search-results');
        this.results = [];
        this.active = 0;
        this.markers = [];
        this.app = null;
    }

    initialize(app) {
        this.app = app;
        this.input.placeholder = t('search.placeholder');
        $('search-button').addEventListener('click', () => this.open());
        document.addEventListener('keydown', (event) => {
            const typing = event.target.closest('input, select, textarea, [contenteditable]');
            if ((event.key === 'k' || event.key === 'K') && (MAC ? event.metaKey : event.ctrlKey) && !event.altKey) {
                event.preventDefault();
                this.dialog.open ? this.dialog.close() : this.open();
            } else if (event.key === '/' && !typing && !this.dialog.open && !document.querySelector('dialog[open]')) {
                event.preventDefault();
                this.open();
            }
        });
        this.input.addEventListener('input', () => this.update());
        this.input.addEventListener('keydown', (event) => {
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                this.select(this.active + (event.key === 'ArrowDown' ? 1 : -1));
            } else if (event.key === 'Enter') {
                event.preventDefault();
                this.run(this.results[this.active]);
            }
        });
    }

    async open() {
        if (this.dialog.open) {
            return;
        }
        this.input.value = '';
        this.markers = [];
        this.dialog.showModal();
        this.input.focus();
        this.update();
        const worlds = this.app.worlds();
        const loaded = await Promise.all(worlds.map(async world => {
            try {
                const response = await fetch(`/api/markers/${encodeURIComponent(world.name)}`);
                return response.ok ? (await response.json()).map(layer => ({world, layer})) : [];
            } catch {
                return [];
            }
        }));
        this.markers = loaded.flat().flatMap(({world, layer}) => layer.markers
            .filter(marker => marker.label)
            .map(marker => ({world, layer, marker})));
        if (this.dialog.open) {
            this.update();
        }
    }

    elsewhere(world) {
        return world === this.app.current() ? null : this.app.worldName(world);
    }

    candidates() {
        const players = [...playerManager.entries.values()]
            .map(entry => entry.player)
            .filter(player => !player.anonymous && located(player))
            .map(player => ({
                kind: 'player',
                label: player.name,
                detail: `${player.x}, ${player.y}, ${player.z}`,
                tag: this.elsewhere(player.world),
                icon: () => avatar(player, 20),
                run: () => {
                    this.app.selectWorld(player.world);
                    playerManager.follow(player.uuid);
                }
            }));
        const here = this.app.current();
        const markers = this.markers.toSorted((a, b) => (a.world.name !== here) - (b.world.name !== here))
            .map(({world, layer, marker}) => ({
            kind: 'marker',
            label: marker.label,
            detail: markerManager.layerLabel(layer),
            tag: this.elsewhere(world.name),
            description: marker.description,
            icon: () => {
                const icon = element('span', `marker-icon marker-icon--${marker.type}`);
                icon.classList.toggle('marker-icon--personal', Boolean(layer.personal));
                icon.style.setProperty('--marker', marker.color);
                if (marker.type === 'point') {
                    icon.innerHTML = markerIcon(layer);
                }
                return icon;
            },
            run: () => {
                this.app.selectWorld(world.name);
                const [x, y, z] = center(marker);
                mapManager.centerOnBlock(Math.floor(x), Math.floor(z), Math.max(mapManager.getMap().getZoom(), 2),
                    Number.isFinite(y) ? Math.floor(y) : undefined);
                markerManager.reveal(layer.id, marker.id);
            }
        }));
        const worlds = this.app.worlds().map(world => ({
            kind: 'world',
            label: this.app.worldName(world.name),
            icon: () => element('span', 'marker-icon marker-icon--world'),
            run: () => this.app.selectWorld(world.name)
        }));
        return [...players, ...markers, ...worlds];
    }

    update() {
        const query = fold(this.input.value.trim());
        const place = COORDINATES.exec(this.input.value.trim());
        let results = this.candidates()
            .map((result, order) => {
                let rank = query ? score(result.label, query) : 0;
                if (rank < 0 && result.description && fold(result.description).includes(query)) {
                    rank = 3;
                }
                return {result, rank, order};
            })
            .filter(({rank}) => rank >= 0)
            .sort((a, b) => a.rank - b.rank || a.order - b.order)
            .slice(0, LIMIT)
            .map(({result}) => result);
        if (place) {
            const [x, y, z] = place.slice(1).map(value => value === undefined ? undefined : Math.floor(Number(value)));
            results.unshift({
                kind: 'place',
                label: t('search.goTo', {place: [x, y, z].filter(value => value !== undefined).join(', ')}),
                run: () => mapManager.centerOnBlock(x, z, undefined, y)
            });
        }
        results = KINDS.flatMap(kind => results.filter(result => result.kind === kind));
        this.results = results;
        const rows = [];
        results.forEach((result, index) => {
            if (result.kind !== results[index - 1]?.kind) {
                const heading = element('li', 'label search__group', t(`search.group.${result.kind}`));
                heading.setAttribute('role', 'presentation');
                rows.push(heading);
            }
            const row = element('li', 'search__result');
            row.id = `search-result-${index}`;
            row.setAttribute('role', 'option');
            row.append(result.icon?.() ?? element('span', 'marker-icon marker-icon--place'));
            const text = element('span', 'row__text');
            text.append(element('span', 'row__name', result.label));
            if (result.detail) {
                text.append(element('span', 'row__meta', result.detail));
            }
            row.append(text);
            if (result.tag) {
                row.append(element('span', 'tag', result.tag));
            }
            row.addEventListener('pointermove', () => this.select(index, false));
            row.addEventListener('click', () => this.run(result));
            rows.push(row);
        });
        this.list.replaceChildren(...rows);
        $('search-empty').hidden = results.length > 0;
        this.select(0);
    }

    select(index, scroll = true) {
        if (this.results.length === 0) {
            this.input.removeAttribute('aria-activedescendant');
            return;
        }
        this.active = (index + this.results.length) % this.results.length;
        const options = this.list.querySelectorAll('[role="option"]');
        options.forEach((row, i) => row.setAttribute('aria-selected', String(i === this.active)));
        const row = options[this.active];
        this.input.setAttribute('aria-activedescendant', row.id);
        if (scroll) {
            row.scrollIntoView({block: 'nearest'});
        }
    }

    run(result) {
        if (!result) {
            return;
        }
        this.dialog.close();
        playerManager.unfollow();
        result.run();
    }
}

export const searchPalette = new SearchPalette();
