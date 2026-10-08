import {accountManager} from './account.js';
import {numbers, t} from './i18n.js';
import {mapManager, pixelPath} from './map.js';
import {element, markerManager, PLAYER_LAYER} from './markers.js';
import {navigationManager} from './navigation.js';

const FALLBACK_AVATAR = 'data:image/svg+xml,' + encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 8 8" shape-rendering="crispEdges">
<rect width="8" height="8" fill="#7a7a7a"/><rect width="8" height="2" fill="#4a4a4a"/><rect y="2" width="1" height="1" fill="#4a4a4a"/><rect x="7" y="2" width="1" height="1" fill="#4a4a4a"/>
<rect x="1" y="4" width="1" height="1" fill="#e0e0e0"/><rect x="2" y="4" width="1" height="1" fill="#333"/><rect x="5" y="4" width="1" height="1" fill="#333"/><rect x="6" y="4" width="1" height="1" fill="#e0e0e0"/>
<rect x="3" y="6" width="2" height="1" fill="#5a5a5a"/></svg>`);

const GLIDE_MILLIS = 1000;
const TELEPORT_BLOCKS = 48;
const ALL_WORLDS_KEY = 'taerra.players.allWorlds';

const FACING_SVG = `<svg viewBox="0 0 24 24" width="48" height="48" aria-hidden="true">
<g class="player-marker__edge"><path d="${pixelPath([[11, 0, 2, 1], [10, 1, 4, 1], [9, 2, 6, 1], [8, 3, 8, 1], [9, 4, 6, 1]])}" fill="#111"/>
<path d="${pixelPath([[11, 1, 2, 1], [10, 2, 4, 1], [9, 3, 6, 1]])}" fill="currentColor"/></g>
<g class="player-marker__corner"><path d="${pixelPath([[19, 0, 4, 1], [18, 1, 6, 1], [19, 2, 5, 1], [20, 3, 4, 1], [21, 4, 3, 1], [22, 5, 1, 1]])}" fill="#111"/>
<path d="${pixelPath([[19, 1, 4, 1], [20, 2, 3, 1], [21, 3, 2, 1], [22, 4, 1, 1]])}" fill="currentColor"/></g></svg>`;

export function avatar(player, size) {
    const img = new Image(size, size);
    img.alt = '';
    img.src = player.anonymous ? FALLBACK_AVATAR : `/api/avatars/${player.uuid}.png`;
    img.addEventListener('error', () => img.src = FALLBACK_AVATAR, {once: true});
    return img;
}

class PlayerManager {
    constructor() {
        this.entries = new Map();
        this.followed = null;
        this.frame = 0;
        this.world = null;
        this.worldLabel = name => name;
        this.onWorld = () => {
        };
        this.list = document.getElementById('player-list');
        this.count = document.getElementById('player-count');
        this.empty = document.getElementById('player-empty');
        this.scope = document.getElementById('player-scope');
        try {
            this.allWorlds = localStorage.getItem(ALL_WORLDS_KEY) === 'true';
        } catch {
            this.allWorlds = false;
        }
        this.scope.addEventListener('click', (event) => {
            const button = event.target.closest('button');
            if (button) {
                this.setAllWorlds(button.dataset.scope === 'all');
            }
        });
        this.setAllWorlds(this.allWorlds);
    }

    setAllWorlds(all) {
        this.allWorlds = all;
        try {
            localStorage.setItem(ALL_WORLDS_KEY, String(all));
        } catch {
        }
        for (const button of this.scope.querySelectorAll('button')) {
            button.setAttribute('aria-pressed', String((button.dataset.scope === 'all') === all));
        }
        this.render();
    }

    setWorld(name) {
        this.world = name;
        const followed = this.entries.get(this.followed);
        if (followed && followed.player.world !== name) {
            this.unfollow();
        }
        for (const entry of this.entries.values()) {
            this.show(entry);
        }
        if (followed && followed.player.world === name) {
            this.place(followed);
        }
        this.render();
    }

    updatePlayers(players) {
        const seen = new Set();
        for (const player of players) {
            if (player.anonymous) {
                player.name = t('players.anonymous');
            }
            seen.add(player.uuid);
            const existing = this.entries.get(player.uuid);
            const entry = existing ?? this.create(player);
            const previous = entry.player;
            entry.player = player;
            const jump = Math.abs(player.x - previous.x) + Math.abs(player.y - previous.y) +
                Math.abs(player.z - previous.z);
            if (this.followed === player.uuid && player.world !== this.world) {
                this.onWorld(player.world);
                if (player.world !== this.world) {
                    this.unfollow();
                }
            }
            this.show(entry);
            if (existing && player.world === previous.world && jump <= TELEPORT_BLOCKS && !document.hidden) {
                this.glide(entry);
            } else {
                this.place(entry);
            }
            this.aim(entry);
            entry.body.style.setProperty('--player-color', player.color);
            entry.button.style.setProperty('--player-color', player.color);
            entry.body.classList.toggle('is-hidden', player.hidden === true);
            entry.button.classList.toggle('is-hidden', player.hidden === true);
            entry.tag.hidden = player.hidden !== true;
            entry.coords.textContent = `${player.x}, ${player.y}, ${player.z}`;
            if (entry.marker.isPopupOpen()) {
                entry.marker.getPopup().update();
            }
        }
        for (const [uuid, entry] of this.entries) {
            if (!seen.has(uuid)) {
                if (this.followed === uuid) {
                    this.unfollow();
                }
                entry.marker.remove();
                this.entries.delete(uuid);
            }
        }

        this.render();
    }

    render() {
        const elsewhere = entry => entry.player.world !== this.world;
        const entries = [...this.entries.values()]
            .filter(entry => this.allWorlds || !elsewhere(entry))
            .sort((a, b) => elsewhere(a) - elsewhere(b) ||
                this.worldLabel(a.player.world).localeCompare(this.worldLabel(b.player.world)) ||
                a.player.name.localeCompare(b.player.name));
        for (const entry of entries) {
            const away = elsewhere(entry);
            const world = this.worldLabel(entry.player.world);
            entry.button.classList.toggle('is-elsewhere', away);
            entry.world.hidden = !away;
            entry.world.textContent = world;
            entry.button.title = t(away ? 'players.followElsewhere' : 'players.follow', {
                name: entry.player.name,
                world
            });
        }
        const rows = entries.map(entry => entry.row);
        if (rows.length !== this.list.children.length || rows.some((row, i) => this.list.children[i] !== row)) {
            this.list.replaceChildren(...rows);
        }
        this.count.textContent = numbers.format(rows.length);
        this.empty.hidden = rows.length > 0;
    }

    show(entry) {
        const map = mapManager.getMap();
        if (entry.player.world !== this.world || !markerManager.isShown(PLAYER_LAYER)) {
            entry.marker.remove();
        } else if (!map.hasLayer(entry.marker)) {
            entry.marker.addTo(map);
            entry.marker.getElement().classList.toggle('is-followed', this.followed === entry.player.uuid);
        }
    }

    showAll() {
        this.entries.forEach(entry => this.show(entry));
    }

    create(player) {
        const entry = {player, glide: null};
        entry.body = element('div', 'player-marker__body');
        entry.facing = element('span', 'player-marker__facing');
        entry.facing.innerHTML = FACING_SVG;
        entry.body.append(entry.facing, avatar(player, 24));
        const icon = L.divIcon({
            className: 'player-marker',
            iconSize: [28, 28],
            popupAnchor: [0, -18],
            html: entry.body
        });
        entry.marker = L.marker(this.latLng(player), {icon, title: player.name})
            .bindPopup(() => this.popup(entry.player), {className: 'marker-popup-frame'});

        entry.button = element('button', 'player');
        entry.button.type = 'button';
        entry.button.setAttribute('aria-pressed', 'false');
        entry.button.dataset.followingLabel = t('players.following');
        entry.coords = element('span', 'player__coords');
        entry.tag = element('span', 'player__tag', t('players.hidden'));
        entry.world = element('span', 'player__world');
        const details = element('span', 'player__details');
        details.append(entry.coords, entry.tag);
        entry.button.append(avatar(player, 32), element('span', 'player__name', player.name), details, entry.world);
        entry.button.addEventListener('click', () => {
            if (this.followed === player.uuid) {
                this.unfollow();
            } else {
                this.onWorld(entry.player.world);
                this.follow(player.uuid);
            }
        });
        entry.row = document.createElement('li');
        entry.row.append(entry.button);

        this.entries.set(player.uuid, entry);
        return entry;
    }

    latLng(player) {
        return mapManager.blockToLatLng(player.x, player.z, player.y);
    }

    popup(player) {
        const content = element('div', 'marker-popup marker-popup--player');
        content.style.setProperty('--marker', player.color);
        content.append(element('span', 'marker-popup__layer', t(player.hidden ? 'players.hiddenPlayer' : 'players.title')),
            element('strong', 'marker-popup__title', player.name),
            element('span', 'marker-popup__facts', `${player.x}, ${player.y}, ${player.z}`));
        if (accountManager.usesMod() && !player.anonymous && player.uuid !== accountManager.ownUuid()) {
            const actions = element('div', 'marker-popup__actions');
            const navigate = element('button', 'marker-popup__action', t('waypoint.navigate'));
            navigate.type = 'button';
            navigate.addEventListener('click', () => {
                mapManager.getMap().closePopup();
                void navigationManager.navigate({
                    label: player.name, world: player.world, x: player.x, y: player.y, z: player.z,
                    color: player.color, player: player.uuid
                });
            });
            actions.append(navigate);
            content.append(actions);
        }
        return content;
    }

    aim(entry) {
        const octant = (Math.round(mapManager.screenBearing(entry.player.yaw ?? 0) / 45) % 8 + 8) % 8;
        entry.facing.classList.toggle('is-diagonal', octant % 2 === 1);
        entry.facing.style.rotate = `${Math.floor(octant / 2) * 90}deg`;
    }

    place(entry) {
        entry.glide = null;
        const latLng = this.latLng(entry.player);
        entry.marker.setLatLng(latLng);
        if (this.followed === entry.player.uuid) {
            mapManager.getMap().panTo(latLng, {animate: false});
        }
    }

    glide(entry) {
        entry.glide = {from: entry.marker.getLatLng(), to: this.latLng(entry.player), start: performance.now()};
        if (!this.frame) {
            this.frame = requestAnimationFrame(now => this.tick(now));
        }
    }

    tick(now) {
        this.frame = 0;
        let moving = false;
        for (const entry of this.entries.values()) {
            const glide = entry.glide;
            if (!glide) {
                continue;
            }
            const progress = Math.min(1, Math.max(0, (now - glide.start) / GLIDE_MILLIS));
            const latLng = L.latLng(glide.from.lat + (glide.to.lat - glide.from.lat) * progress,
                glide.from.lng + (glide.to.lng - glide.from.lng) * progress);
            entry.marker.setLatLng(latLng);
            if (this.followed === entry.player.uuid) {
                mapManager.getMap().panTo(latLng, {animate: false});
            }
            if (progress < 1) {
                moving = true;
            } else {
                entry.glide = null;
            }
        }
        if (moving) {
            this.frame = requestAnimationFrame(next => this.tick(next));
        }
    }

    follow(uuid) {
        const entry = this.entries.get(uuid);
        if (!entry) {
            return;
        }
        this.unfollow();
        this.followed = uuid;
        entry.button.setAttribute('aria-pressed', 'true');
        entry.marker.getElement()?.classList.add('is-followed');
        const {x, y, z} = entry.player;
        mapManager.centerOnBlock(x, z, Math.max(mapManager.getMap().getZoom(), 2), y);
    }

    unfollow() {
        const entry = this.entries.get(this.followed);
        if (entry) {
            entry.button.setAttribute('aria-pressed', 'false');
            entry.marker.getElement()?.classList.remove('is-followed');
        }
        this.followed = null;
    }

    reposition() {
        for (const entry of this.entries.values()) {
            this.place(entry);
            this.aim(entry);
        }
    }
}

export const playerManager = new PlayerManager();
