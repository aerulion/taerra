import {dates, numbers, plurals, t} from './i18n.js';
import {accountManager} from './account.js';
import {mapManager, pixelPath} from './map.js';
import {iconStore} from './icons.js';
import {element, menuItem} from './ui.js';
import {wsManager} from './websocket.js';

const SPLINE_BLOCKS_PER_STEP = 2;
const DETAIL_ZOOM_RANGE = 2;
const VIEW_MARGIN = 0.25;
const DRAW_BUDGET_MS = 12;
const LABEL_GAP = 3;
const FALLBACK_GROUND = 64;
const MAX_EDGE_SAMPLES = 512;

const LOOKS = {
    color: {halo: true, weight: 0, fill: 0.12, hoverFill: 0.34, ground: 0.08, wall: 0.16, wallLine: 0.35},
    gray: {halo: false, weight: 1, fill: 0.22, hoverFill: 0.45, ground: 0.16, wall: 0.28, wallLine: 0.6}
};

const DASHES = '6 4';
const MUTED = 0.7;

export const PLAYER_LAYER = {id: 'players', shown: true, markers: []};
export const PERSONAL_LAYER = 'personal';

export function personalIcon() {
    const icon = element('span', 'personal-icon');
    icon.title = t('personal.hint');
    icon.setAttribute('role', 'img');
    icon.setAttribute('aria-label', t('personal.label'));
    icon.innerHTML = `<svg viewBox="0 0 5 7" width="10" height="14" aria-hidden="true" shape-rendering="crispEdges">
<path d="${pixelPath([
        [2, 0, 1, 1],
        [1, 1, 3, 1],
        [0, 2, 5, 2],
        [1, 4, 3, 1],
        [1, 5, 3, 1],
        [2, 6, 1, 1]
    ])}" fill="currentColor"/></svg>`;
    return icon;
}

export function privateIcon() {
    const icon = element('span', 'private-icon');
    icon.title = t('private.hint');
    icon.setAttribute('role', 'img');
    icon.setAttribute('aria-label', t('private.label'));
    icon.innerHTML = `<svg viewBox="0 0 5 6" width="10" height="12" aria-hidden="true" shape-rendering="crispEdges">
<path d="${pixelPath([
        [1, 0, 3, 1],
        [0, 1, 1, 2],
        [4, 1, 1, 2],
        [0, 3, 5, 1],
        [0, 4, 2, 1],
        [3, 4, 2, 1],
        [0, 5, 5, 1]
    ])}" fill="currentColor"/></svg>`;
    return icon;
}

export function spline(points) {
    const out = [points[0]];
    for (let i = 0; i < points.length - 1; i++) {
        const p0 = points[Math.max(0, i - 1)];
        const p1 = points[i];
        const p2 = points[i + 1];
        const p3 = points[Math.min(points.length - 1, i + 2)];
        const steps = Math.min(
            32,
            Math.max(1, Math.ceil(Math.hypot(...p2.map((v, k) => v - p1[k])) / SPLINE_BLOCKS_PER_STEP))
        );
        for (let step = 1; step <= steps; step++) {
            const s = step / steps;
            out.push(
                p1.map(
                    (_, k) =>
                        0.5 *
                        (2 * p1[k] +
                            (p2[k] - p0[k]) * s +
                            (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * s * s +
                            (3 * p1[k] - p0[k] - 3 * p2[k] + p3[k]) * s * s * s)
                )
            );
        }
    }
    return out;
}

function edgePoints(outline) {
    return outline.map(([ax, az], i) => {
        const [bx, bz] = outline[(i + 1) % outline.length];
        const steps = Math.min(MAX_EDGE_SAMPLES, Math.max(1, Math.ceil(Math.hypot(bx - ax, bz - az))));
        return Array.from({length: steps + 1}, (_, k) => [ax + ((bx - ax) * k) / steps, az + ((bz - az) * k) / steps]);
    });
}

function groundKey(marker) {
    return (marker.groundKey ??= `${marker.id} ${marker.points} ${marker.holes ?? ''}`);
}

function samples(points) {
    const [cx, cz] = points.reduce(([sx, sz], [x, z]) => [sx + x, sz + z], [0, 0]).map((sum) => sum / points.length);
    return edgePoints(points).map((side) =>
        side.map(([x, z]) => ({
            x,
            z,
            pick: mapManager.topPick(Math.floor(x + Math.sign(cx - x) * 0.5), Math.floor(z + Math.sign(cz - z) * 0.5))
        }))
    );
}

function drape(sides, buffers) {
    return sides.map((side) => {
        const ground = smoothed(side.map(({pick}) => pick.height(buffers.get(pick.key))));
        return level(side.map(({x, z}, i) => [x, Number.isFinite(ground[i]) ? ground[i] + 1 : undefined, z]));
    });
}

async function groundUnder(marker) {
    const rings = [marker.points, ...(marker.holes ?? [])].map(samples);
    const buffers = new Map();
    const picks = new Map(rings.flat(2).map(({pick}) => [pick.key, pick]));
    await Promise.all([...picks.values()].map(async (pick) => buffers.set(pick.key, await mapManager.loadPick(pick))));
    return rings.map((sides) => drape(sides, buffers));
}

export function groundNow(points) {
    const sides = samples(points);
    const buffers = new Map(sides.flat().map(({pick}) => [pick.key, mapManager.cachedPick(pick.key, pick.url)]));
    return drape(sides, buffers);
}

function level(points) {
    return points.filter(
        (point, i) =>
            i === 0 || i === points.length - 1 || point[1] !== points[i - 1][1] || point[1] !== points[i + 1][1]
    );
}

function smoothed(heights) {
    return heights.map((height, i) => {
        const window = heights
            .slice(Math.max(0, i - 2), i + 3)
            .filter(Number.isFinite)
            .sort((a, b) => a - b);
        return window.length > 0 ? window[window.length >> 1] : height;
    });
}

function togglePopup(event) {
    L.DomEvent.stop(event);
    if (this.isPopupOpen()) {
        this.closePopup();
    } else {
        this.openPopup(event.latlng);
    }
}

function hoverable(layer, over, out) {
    return layer.on('mouseover', () => layer.setStyle(over)).on('mouseout', () => layer.setStyle(out));
}

function signedArea(ring) {
    let sum = 0;
    ring.forEach((point, i) => {
        const next = ring[(i + 1) % ring.length];
        sum += point.lng * next.lat - next.lng * point.lat;
    });
    return sum;
}

function shade(hex, factor) {
    const value = parseInt(hex.slice(1), 16);
    return (
        '#' +
        [16, 8, 0]
            .map((bits) =>
                Math.round(((value >> bits) & 255) * factor)
                    .toString(16)
                    .padStart(2, '0')
            )
            .join('')
    );
}

function isWaypointLayer(layer) {
    return layer.personal || layer.id === 'waypoints' || layer.id === 'spawn' || layer.id.startsWith('waypoints.');
}

const CHUNK_LEVELS = ['entity_ticking', 'ticking', 'border'];

export function chunkCount(count) {
    return t(`chunks.count.${plurals.select(Number(count))}`, {count: numbers.format(count)});
}

function translatedOr(key, fallback) {
    const text = t(key);
    return text === key ? fallback : text;
}

function localize(layer) {
    switch (layer.id) {
        case 'chunks':
            return {...layer, markers: layer.markers.map(chunkMarker)};
        case 'worldborder':
            return {
                ...layer,
                markers: layer.markers.map((marker) => ({
                    ...marker,
                    label: t('marker.worldborder'),
                    details: localizedDetails(marker)
                }))
            };
        case 'worldguard':
        case 'towny':
        case 'griefprevention':
        case 'plotsquared':
            return {...layer, markers: layer.markers.map((marker) => ({...marker, details: localizedDetails(marker)}))};
        case 'spawn':
            return {...layer, markers: layer.markers.map((marker) => ({...marker, label: t('marker.spawn')}))};
        default:
            return layer;
    }
}

function localizedDetails(marker) {
    return marker.details?.map(([key, value]) => [
        translatedOr(`marker.detail.${key}`, key),
        key === 'shape' ? translatedOr(`marker.shape.${value}`, value) : value
    ]);
}

function chunkMarker(marker) {
    const facts = Object.fromEntries(marker.details ?? []);
    if (!CHUNK_LEVELS.includes(facts.level)) {
        return marker;
    }
    const split = CHUNK_LEVELS.filter((level) => level in facts).map((level) =>
        t(`chunks.split.${level}`, {count: numbers.format(facts[level])})
    );
    const players = [facts.players, facts.anonymous && t('chunks.anonymous', {count: numbers.format(facts.anonymous)})]
        .filter(Boolean)
        .join(', ');
    const rows = [
        ['level', t(`chunks.description.${facts.level}`)],
        ['chunks', numbers.format(facts.chunks) + (split.length > 1 ? ` (${split.join(', ')})` : '')],
        ['size', t('chunks.size', {width: numbers.format(facts.width), depth: numbers.format(facts.depth)})],
        [
            'blocks',
            t(
                'chunks.blocks',
                Object.fromEntries(['minX', 'maxX', 'minZ', 'maxZ'].map((name) => [name, numbers.format(facts[name])]))
            )
        ],
        ['players', players || t('chunks.nobody')],
        facts.forced && ['forced', chunkCount(facts.forced)],
        facts.tickets && ['tickets', facts.tickets],
        ['view', chunkCount(facts.view)],
        ['simulation', chunkCount(facts.simulation)]
    ].filter(Boolean);
    return {
        ...marker,
        label: `${t(`chunks.level.${facts.level}`)}, ${chunkCount(facts.chunks)}`,
        details: rows.map(([key, value]) => [t(`chunks.detail.${key}`), value])
    };
}

function drawKey(layer, marker) {
    return JSON.stringify([layer.id, layer.icon, layer.personal, {...marker, groundKey: undefined}]);
}

export function markerBadge(layer, marker) {
    return iconStore.badge(iconStore.resolve(marker.icon, layer.icon, layer.personal), marker.color, layer.personal);
}

function facts(marker, flat = false) {
    const round = (value) => numbers.format(Math.round(value));
    switch (marker.type) {
        case 'point':
            return marker.position.map((v) => round(Math.floor(v))).join(', ');
        case 'line': {
            const points = marker.smooth ? spline(marker.points) : marker.points;
            let length = 0;
            for (let i = 1; i < points.length; i++) {
                length += Math.hypot(...points[i].map((v, k) => v - points[i - 1][k]));
            }
            return t(`marker.blocks.${plurals.select(Math.round(length))}`, {count: round(length)});
        }
        case 'area': {
            const xs = marker.points.map(([x]) => x);
            const zs = marker.points.map(([, z]) => z);
            if (flat) {
                return [Math.max(...xs) - Math.min(...xs), Math.max(...zs) - Math.min(...zs)].map(round).join(' × ');
            }
            return (
                [Math.max(...xs) - Math.min(...xs), marker.maxY - marker.minY, Math.max(...zs) - Math.min(...zs)]
                    .map(round)
                    .join(' × ') + `\nY ${round(marker.minY)} – ${round(topBlock(marker.maxY))}`
            );
        }
        default:
            return '';
    }
}

function topBlock(y) {
    return Number.isInteger(y) ? y - 1 : Math.floor(y);
}

class MarkerManager {
    constructor() {
        this.world = null;
        this.look = LOOKS.color;
        this.layers = [];
        this.groups = new Map();
        this.entries = [];
        this.labels = [];
        this.grounds = new Map();
        this.sampling = new Set();
        this.cullTimer = 0;
        this.visibility = {};
        this.synced = false;
        this.onWaypoint = null;
        this.hidden = null;
        this.pending = null;
        this.onPlayers = null;
        this.preview = null;
        this.outOfScale = '';
        this.list = document.getElementById('layer-list');
    }

    initialize() {
        const map = mapManager.getMap();
        map.createPane('waypoints').style.zIndex = 550;
        this.far = mapManager.activeMaxZoom() - map.getZoom() >= DETAIL_ZOOM_RANGE;
        map.on('zoomend', () => {
            const far = mapManager.activeMaxZoom() - map.getZoom() >= DETAIL_ZOOM_RANGE;
            if (far !== this.far) {
                this.far = far;
                this.redraw();
            }
            this.renderScales();
        });
        map.on('moveend', () => this.cull());
        iconStore.onChange(() => this.redraw());
        wsManager.on('layers', (message) => this.applySaved(message.layers ?? {}));
        wsManager.on('connect', () => void this.loadSaved());
    }

    async loadSaved() {
        if (!accountManager.loggedIn()) {
            this.synced = false;
            return;
        }
        try {
            const response = await fetch('api/layers');
            if (response.status === 200) {
                this.applySaved(await response.json());
            }
        } catch (error) {
            console.error('Failed to load the layers:', error);
        }
    }

    applySaved(visibility) {
        this.synced = true;
        this.replaceVisibility({...visibility});
    }

    async save(layer, shown) {
        if (!this.synced) {
            return;
        }
        const change = {[shown === layer.shown ? 'reset' : shown ? 'show' : 'hide']: [layer.id]};
        try {
            await fetch('api/layers', {
                method: 'PATCH',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(change)
            });
        } catch (error) {
            console.error('Failed to save the layers:', error);
        }
    }

    placeLabels() {
        const map = mapManager.getMap();
        const size = map.getSize();
        const candidates = this.labels
            .filter((label) => map.hasLayer(label.marker))
            .map((label) => ({label, box: label.box(map)}));
        const placed = [];
        for (const {label, box} of candidates) {
            const show =
                box.right > 0 &&
                box.left < size.x &&
                box.bottom > 0 &&
                box.top < size.y &&
                !placed.some(
                    (other) =>
                        box.left < other.right + LABEL_GAP &&
                        other.left < box.right + LABEL_GAP &&
                        box.top < other.bottom + LABEL_GAP &&
                        other.top < box.bottom + LABEL_GAP
                );
            if (show) {
                placed.push(box);
            }
            label.show(show);
        }
    }

    async load(world) {
        if (world !== this.world) {
            this.world = world;
            this.grounds.clear();
            this.sampling.clear();
            this.setLayers([]);
        }
        try {
            const response = await fetch(`api/markers/${encodeURIComponent(world)}`);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }
            const layers = await response.json();
            if (this.world === world) {
                this.setLayers(layers.map(localize));
            }
        } catch (error) {
            console.error('Failed to load markers:', error);
        }
    }

    setLayers(layers) {
        this.layers = layers;
        this.showPending();
        this.redraw(true);
        this.renderControls();
        this.onLayers?.();
    }

    shownLayer(id) {
        const layer = this.layers.find((candidate) => candidate.id === id);
        return layer !== undefined && this.isShown(layer);
    }

    isShown(layer) {
        return this.visibility[layer.id] ?? layer.shown;
    }

    minScale(layer) {
        return this.preview?.id === layer.id ? this.preview.scale : layer.minScale;
    }

    reaches(layer, scale = mapManager.scale()) {
        const min = this.minScale(layer);
        return !Number.isInteger(min) || scale >= min;
    }

    revealZoom(layer) {
        return Number.isInteger(layer?.minScale) ? mapManager.zoomForScale(layer.minScale) : -Infinity;
    }

    previewScale(id, scale) {
        this.preview = id === null ? null : {id, scale};
        this.cull();
        this.renderScales();
    }

    renderScales() {
        const scale = mapManager.scale();
        const out = this.layers
            .filter((layer) => !this.reaches(layer, scale))
            .map((layer) => layer.id)
            .join('\n');
        if (out !== this.outOfScale) {
            this.outOfScale = out;
            this.renderControls();
        }
    }

    writeVisibility(params) {
        if (this.synced) {
            return;
        }
        for (const [id, shown] of Object.entries(this.visibility)) {
            params.append(shown ? 'show' : 'hide', id);
        }
    }

    readVisibility(params) {
        if (this.synced) {
            return;
        }
        const visibility = {};
        params.getAll('show').forEach((id) => (visibility[id] = true));
        params.getAll('hide').forEach((id) => (visibility[id] = false));
        this.replaceVisibility(visibility);
    }

    replaceVisibility(visibility) {
        this.visibility = visibility;
        this.onPlayers?.();
        this.onLayers?.();
        this.redraw();
        this.renderControls();
    }

    setShown(layer, shown) {
        if (shown === layer.shown) {
            delete this.visibility[layer.id];
        } else {
            this.visibility[layer.id] = shown;
        }
        const group = this.groups.get(layer.id);
        if (shown) {
            group?.addTo(mapManager.getMap());
        } else {
            group?.remove();
        }
        if (layer === PLAYER_LAYER) {
            this.onPlayers?.();
        }
        this.onLayers?.();
        this.cull();
    }

    reveal(layerId, id) {
        this.pending = {layerId, id};
        this.showPending();
        this.cull();
    }

    showPending() {
        const layer =
            this.pending &&
            !this.pending.shown &&
            this.layers.find((candidate) => candidate.id === this.pending.layerId);
        if (layer) {
            this.pending.shown = true;
            if (!this.isShown(layer)) {
                this.setShown(layer, true);
                this.renderControls();
            }
        }
    }

    hide(id) {
        if (id !== this.hidden) {
            this.hidden = id;
            this.redraw();
        }
    }

    setMapStyle(style) {
        this.look = LOOKS[style] ?? LOOKS.color;
        this.redraw();
    }

    redraw(keep = false) {
        const previous = this.entries;
        const kept = new Map(keep ? previous.filter((entry) => entry.drawn).map((entry) => [entry.key, entry]) : []);
        const groups = this.groups;
        this.groups = new Map();
        this.entries = [];
        for (const layer of this.layers) {
            const group = (keep && groups.get(layer.id)) || L.layerGroup();
            if (keep) {
                groups.delete(layer.id);
            }
            this.groups.set(layer.id, group);
            if (this.isShown(layer)) {
                group.addTo(mapManager.getMap());
            } else {
                group.remove();
            }
            for (const marker of layer.markers) {
                if (marker.id === this.hidden) {
                    continue;
                }
                const key = drawKey(layer, marker);
                const entry = kept.get(key);
                if (entry) {
                    kept.delete(key);
                    entry.layer = layer;
                    entry.marker = marker;
                    this.entries.push(entry);
                } else {
                    this.entries.push({
                        key,
                        layer,
                        marker,
                        reach: this.reach(marker),
                        rough: !this.grounds.has(groundKey(marker)),
                        drawn: null
                    });
                }
            }
        }
        groups.forEach((group) => group.remove());
        const current = new Set(this.entries);
        const drawn = new Set(this.entries.map((entry) => entry.drawn));
        previous
            .filter((entry) => entry.drawn && !current.has(entry))
            .forEach((entry) => this.groups.get(entry.layer.id)?.removeLayer(entry.drawn));
        this.labels = this.labels.filter((label) => drawn.has(label.marker));
        this.cull();
    }

    cull() {
        clearTimeout(this.cullTimer);
        const map = mapManager.getMap();
        const view = map.getBounds().pad(VIEW_MARGIN);
        const scale = mapManager.scale();
        const started = performance.now();
        let unfinished = false;
        for (const entry of this.entries) {
            const group = this.groups.get(entry.layer.id);
            if (entry.rough && this.grounds.has(groundKey(entry.marker))) {
                entry.reach = this.reach(entry.marker);
                entry.rough = false;
            }
            const inView =
                this.isShown(entry.layer) && this.reaches(entry.layer, scale) && view.intersects(entry.reach);
            if (inView && !entry.drawn && performance.now() - started > DRAW_BUDGET_MS) {
                unfinished = true;
            } else if (inView && !entry.drawn) {
                entry.drawn = this.draw(entry.marker, entry.layer);
                if (entry.layer.id !== 'regions' || entry.marker.label) {
                    entry.drawn?.bindPopup(() => this.popup(entry.layer, entry.marker), {
                        className: 'marker-popup-frame'
                    });
                    entry.drawn?.off('click', entry.drawn._openPopup, entry.drawn).on('click', togglePopup);
                }
            }
            if (entry.drawn && inView !== group.hasLayer(entry.drawn)) {
                if (inView) {
                    group.addLayer(entry.drawn);
                } else {
                    group.removeLayer(entry.drawn);
                }
            }
            if (
                inView &&
                entry.drawn &&
                entry.layer.id === this.pending?.layerId &&
                entry.marker.id === this.pending.id
            ) {
                this.pending = null;
                entry.drawn.openPopup();
            }
        }
        this.placeLabels();
        if (unfinished) {
            this.cullTimer = setTimeout(() => this.cull());
        }
    }

    cullSoon() {
        clearTimeout(this.cullTimer);
        this.cullTimer = setTimeout(() => this.cull(), 100);
    }

    reach(marker) {
        const ground =
            this.grounds
                .get(groundKey(marker))
                ?.flat(2)
                .map(([, y]) => y)
                .filter(Number.isFinite) ?? [];
        const low = ground.length > 0 ? Math.max(marker.minY, Math.min(...ground)) : marker.minY;
        const high = ground.length > 0 && marker.fullHeight ? Math.max(...ground) : marker.maxY;
        const points =
            marker.type === 'area'
                ? marker.points.flatMap(([x, z]) => [
                      [x, low, z],
                      [x, high, z]
                  ])
                : marker.type === 'line'
                  ? marker.points
                  : [marker.position];
        return L.latLngBounds(points.map(([x, y, z]) => mapManager.project(x, y, z)));
    }

    draw(marker, layer) {
        const personal = layer.personal;
        const color = marker.color;
        const dashArray = personal ? DASHES : null;
        switch (marker.type) {
            case 'point': {
                const [x, y, z] = marker.position;
                const position = mapManager.project(x, y, z);
                const point = L.marker(position, {pane: 'waypoints', riseOnHover: true});
                if (marker.label) {
                    point.on('add', () => point.getElement()?.setAttribute('aria-label', marker.label));
                }
                return point.setIcon(this.badgeIcon(marker, point, markerBadge(layer, marker), personal));
            }
            case 'line': {
                const points = (marker.smooth ? spline(marker.points) : marker.points).map(([x, y, z]) =>
                    mapManager.project(x, y, z)
                );
                const {halo, weight} = this.look;
                return this.labeled(
                    marker,
                    L.featureGroup([
                        ...(halo
                            ? [L.polyline(points, {color: '#000', opacity: 0.45, weight: 7, interactive: false})]
                            : []),
                        hoverable(
                            L.polyline(points, {
                                color,
                                weight: 3 + weight,
                                dashArray
                            }),
                            {weight: 5 + weight},
                            {weight: 3 + weight}
                        )
                    ])
                );
            }
            case 'area':
                return this.area(marker, dashArray);
            default:
                return null;
        }
    }

    area(marker, dashArray) {
        const {color, minY, maxY} = marker;
        const holes = marker.holes ?? [];
        if (!mapManager.isIsometric()) {
            return this.region(
                marker,
                marker.points.map(([x, z]) => mapManager.project(x, 0, z)),
                [],
                dashArray,
                holes.map((hole) => hole.map(([x, z]) => mapManager.project(x, 0, z)))
            );
        }

        const grounds = this.grounds.get(groundKey(marker));
        if (!grounds) {
            if (!this.sampling.has(groundKey(marker))) {
                this.sampling.add(groundKey(marker));
                const world = this.world;
                void groundUnder(marker).then((ground) => {
                    if (this.world === world) {
                        this.sampling.delete(groundKey(marker));
                        this.grounds.set(groundKey(marker), ground);
                        this.cullSoon();
                    }
                });
            }
            return null;
        }

        const {fullHeight} = marker;
        const bottom = (ground) => Math.min(maxY, Math.max(minY, ground ?? (fullHeight ? FALLBACK_GROUND : minY)));
        const [base, ...holeBases] = grounds.map((sides) =>
            sides.map((side) => side.map(([x, ground, z]) => mapManager.project(x, bottom(ground), z)))
        );
        const holeLines = holeBases.map((ring) => ring.flat());
        if (this.far || fullHeight) {
            return this.region(marker, base.flat(), [], dashArray, holeLines);
        }

        const walls = [];
        [marker.points, ...holes].forEach((points, ring) => {
            const flat = points.map(([x, z]) => mapManager.project(x, 0, z));
            const winding = Math.sign(signedArea(flat));
            grounds[ring].forEach((side, i) => {
                const next = (i + 1) % flat.length;
                const [x0, , z0] = side[0];
                const [x1, , z1] = side.at(-1);
                const slice = [flat[i], flat[next], mapManager.project(x1, 1, z1), mapManager.project(x0, 1, z0)];
                if ((Math.sign(signedArea(slice)) === winding) === ring > 0) {
                    return;
                }
                const facesLeft = (flat[next].lat - flat[i].lat) * (flat[next].lng - flat[i].lng) < 0;
                const crest = side.map(([x, , z]) => mapManager.project(x, maxY, z)).reverse();
                walls.push(
                    L.polygon([...(ring === 0 ? base : holeBases[ring - 1])[i], ...crest], {
                        color,
                        weight: 1,
                        opacity: this.look.wallLine,
                        fillColor: shade(color, facesLeft ? 0.55 : 0.8),
                        fillOpacity: this.look.wall,
                        interactive: false
                    })
                );
            });
        });

        const groundLines = [base.flat(), ...holeLines];
        return this.region(
            marker,
            marker.points.map(([x, z]) => mapManager.project(x, maxY, z)),
            [
                ...(this.look.halo
                    ? [
                          L.polygon(groundLines, {
                              color: '#000',
                              opacity: 0.4,
                              weight: 4,
                              fill: false,
                              interactive: false
                          })
                      ]
                    : []),
                L.polygon(groundLines, {
                    color,
                    weight: 2 + this.look.weight,
                    opacity: 0.9 * (marker.muted ? MUTED : 1),
                    fill: !marker.hollow,
                    fillOpacity: this.look.ground,
                    interactive: false,
                    dashArray
                }),
                ...walls
            ],
            dashArray,
            holes.map((hole) => hole.map(([x, z]) => mapManager.project(x, maxY, z)))
        );
    }

    region(marker, face, below = [], dashArray = null, holes = []) {
        const color = marker.color;
        const {halo, weight, fill, hoverFill} = this.look;
        const parts = [
            ...below,
            ...(this.far || !halo || marker.muted
                ? []
                : [
                      L.polygon([face, ...holes], {
                          color: '#000',
                          opacity: 0.4,
                          weight: 5,
                          fill: false,
                          interactive: false
                      })
                  ])
        ];
        const stroke = (this.far ? 1 : 2) + weight;
        const outline = {color, weight: stroke, opacity: (this.far ? 0.7 : 1) * (marker.muted ? MUTED : 1), dashArray};
        const surface = marker.hollow
            ? hoverable(
                  L.polyline(
                      [face, ...holes].map((ring) => [...ring, ring[0]]),
                      outline
                  ),
                  {weight: stroke + 2},
                  {weight: stroke}
              )
            : hoverable(
                  L.polygon([face, ...holes], {...outline, fillColor: color, fillOpacity: fill}),
                  {fillOpacity: hoverFill},
                  {fillOpacity: fill}
              );
        parts.push(surface);
        if (marker.label) {
            const top = face.reduce((best, point) =>
                point.lat > best.lat + 1e-9 || (Math.abs(point.lat - best.lat) <= 1e-9 && point.lng < best.lng)
                    ? point
                    : best
            );
            let shown = null;
            const hide = () => {
                shown?.remove();
                shown = null;
            };
            surface
                .on('mouseover', () => {
                    const flag = element('span', 'region-flag', marker.label);
                    flag.style.setProperty('--marker', color);
                    shown = L.marker(top, {
                        pane: 'waypoints',
                        interactive: false,
                        icon: L.divIcon({className: 'region-flag-icon', html: flag, iconSize: null, iconAnchor: [1, 0]})
                    }).addTo(mapManager.getMap());
                })
                .on('mouseout remove', hide);
        }
        return L.featureGroup(parts);
    }

    labeled(marker, layer) {
        if (marker.label) {
            layer.bindTooltip(element('span', '', marker.label), {
                sticky: true,
                direction: 'top',
                offset: [0, -8],
                className: 'marker-tooltip'
            });
        }
        return layer;
    }

    badgeIcon(marker, point, svg, personal) {
        const [width, height] = personal ? [28, 34] : [24, 30];
        const body = element('div', 'waypoint');
        body.style.color = marker.color;
        body.innerHTML = svg;
        if (marker.label) {
            const label = element('span', 'waypoint__label', marker.label);
            body.prepend(label);
            this.labels.push({
                marker: point,
                box: (map) => {
                    const tip = map.latLngToContainerPoint(point.getLatLng());
                    label.measured ||= label.offsetWidth;
                    label.tall ||= label.offsetHeight;
                    return {
                        left: tip.x - label.measured / 2,
                        right: tip.x + label.measured / 2,
                        top: tip.y - height - 4 - label.tall,
                        bottom: tip.y
                    };
                },
                show: (shown) => label.classList.toggle('is-hidden', !shown)
            });
        }
        return L.divIcon({
            className: 'waypoint-icon',
            html: body,
            iconSize: [width, height],
            iconAnchor: [width / 2, height],
            popupAnchor: [0, marker.label ? -height - 20 : -height - 2]
        });
    }

    popup(layer, marker) {
        const content = element('div', 'marker-popup');
        content.style.setProperty('--marker', marker.color);
        const layerLabel = element('span', 'label marker-popup__layer', this.layerLabel(layer));
        if (layer.personal) {
            layerLabel.prepend(personalIcon());
        }
        const heading = [
            layerLabel,
            element('strong', 'title marker-popup__title', marker.label ?? marker.id),
            element('span', 'mono marker-popup__facts', facts(marker, layer.id === 'regions'))
        ];
        if (marker.type === 'point') {
            const badge = element('span', 'marker-popup__badge');
            badge.innerHTML = markerBadge(layer, marker);
            const text = element('div', 'marker-popup__heading');
            text.append(...heading);
            const head = element('div', 'marker-popup__head');
            head.append(badge, text);
            content.append(head);
        } else {
            content.append(...heading);
        }
        if (marker.discovered) {
            content.append(
                element(
                    'span',
                    'marker-popup__discovered',
                    t('region.discovered', {date: dates.format(new Date(marker.discovered))})
                )
            );
        }
        if (marker.description) {
            content.append(element('p', 'marker-popup__description', marker.description));
        }
        if (marker.details?.length) {
            const rows = element('dl', 'marker-popup__details');
            for (const [key, value] of marker.details) {
                rows.append(element('dt', '', key), element('dd', '', value));
            }
            content.append(rows);
        }
        content.append(...this.waypointActions(layer, marker).filter((group) => group.childElementCount > 0));
        return content;
    }

    waypointActions(layer, marker) {
        const groups = [element('div', 'menu__group'), element('div', 'menu__group'), element('div', 'menu__group')];
        const point = marker.type === 'point';
        const [x, y, z] = point ? marker.position.map(Math.floor) : [];
        const personal = layer.id === PERSONAL_LAYER;
        const waypoint = {
            world: this.world,
            x,
            y,
            z,
            name: marker.label,
            color: marker.color,
            personal,
            group: layer.id === 'waypoints' || personal ? '' : layer.label,
            previous: marker.label,
            id: marker.id,
            description: marker.description ?? '',
            icon: marker.icon ?? '',
            ...(point
                ? {}
                : {
                      type: marker.type,
                      smooth: marker.smooth,
                      points: marker.type === 'area' ? marker.points.map(([px, pz]) => [px, null, pz]) : marker.points
                  })
        };
        const action = (group, name, label, icon = name) => {
            groups[group].append(menuItem(icon, t(label), (button) => this.onWaypoint?.(name, waypoint, button)));
        };
        if (point) {
            action(0, 'coordinates', 'menu.coordinates', 'copy');
            action(0, 'link', 'waypoint.link');
        }
        if (point && accountManager.usesMod()) {
            action(1, 'navigate', 'waypoint.navigate');
        }
        if (point && isWaypointLayer(layer) && accountManager.canTeleport()) {
            action(1, 'teleport', 'waypoint.teleport');
        }
        if (
            isWaypointLayer(layer) &&
            (personal
                ? accountManager.canKeepPersonal()
                : marker.id.startsWith('waypoint:') && accountManager.canEditMarkers())
        ) {
            if (point) {
                action(2, 'move', 'waypoint.move');
                if (this.me?.(this.world)) {
                    action(2, 'here', 'waypoint.here');
                }
            }
            action(2, 'edit', 'waypoint.editShort');
        }
        return groups;
    }

    layerLabel(layer) {
        return translatedOr(`layer.${layer.id}`, layer.label);
    }

    renderControls() {
        this.list.replaceChildren(
            ...[PLAYER_LAYER, ...this.layers].map((layer) => {
                const toggle = document.createElement('input');
                toggle.type = 'checkbox';
                toggle.className = 'checkbox';
                toggle.checked = this.isShown(layer);
                toggle.addEventListener('change', () => {
                    this.setShown(layer, toggle.checked);
                    void this.save(layer, toggle.checked);
                });
                const row = element('label', 'row row--action');
                const name = element('span', 'layer__name', this.layerLabel(layer));
                if (layer.private) {
                    name.append(privateIcon());
                }
                if (layer.personal) {
                    name.append(personalIcon());
                }
                if (layer !== PLAYER_LAYER && !this.reaches(layer)) {
                    const text = element('span', 'row__text');
                    text.append(name, element('span', 'hint', t('layers.zoomIn')));
                    row.classList.add('is-out-of-scale');
                    row.append(toggle, text);
                } else {
                    row.append(toggle, name);
                }
                if (layer !== PLAYER_LAYER) {
                    row.append(element('span', 'badge', numbers.format(layer.markers.length)));
                }
                const item = document.createElement('li');
                item.append(row);
                return item;
            })
        );
    }
}

export const markerManager = new MarkerManager();
