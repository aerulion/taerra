import {numbers, plurals, t} from './i18n.js';
import {accountManager} from './account.js';
import {mapManager, pixelPath} from './map.js';


const SPLINE_BLOCKS_PER_STEP = 2;
const DETAIL_ZOOM_RANGE = 2;
const VIEW_MARGIN = .25;
const DRAW_BUDGET_MS = 12;
const LABEL_GAP = 3;
const FALLBACK_GROUND = 64;
const MAX_EDGE_SAMPLES = 512;

const LOOKS = {
    color: {halo: true, weight: 0, fill: .12, hoverFill: .34, ground: .08, wall: .16, wallLine: .35},
    gray: {halo: false, weight: 1, fill: .22, hoverFill: .45, ground: .16, wall: .28, wallLine: .6}
};

const PIN_OUTLINE = [[2, 0, 5, 1], [1, 1, 7, 1], [0, 2, 9, 5], [1, 7, 7, 1], [2, 8, 5, 1], [3, 9, 3, 1], [4, 10, 1, 1]];
const PIN_BODY = [[2, 1, 5, 1], [1, 2, 7, 5], [2, 7, 5, 1], [3, 8, 3, 1], [4, 9, 1, 1]];
const PIN_SHINE = [[2, 2, 2, 1], [2, 3, 1, 1]];
const PIN_EYE = [[3, 3, 3, 3]];

export const PIN_SVG = `<svg class="waypoint__pin" viewBox="0 0 9 11" width="18" height="22" aria-hidden="true">
<path d="${pixelPath(PIN_OUTLINE)}" fill="#111"/><path d="${pixelPath(PIN_BODY)}" fill="currentColor"/>
<path d="${pixelPath(PIN_SHINE)}" fill="#fff" fill-opacity=".45"/><path d="${pixelPath(PIN_EYE)}" fill="#fff"/></svg>`;

export const PLAYER_LAYER = {id: 'players', shown: true, markers: []};

export function privateIcon() {
    const icon = element('span', 'private-icon');
    icon.title = t('private.hint');
    icon.setAttribute('role', 'img');
    icon.setAttribute('aria-label', t('private.label'));
    icon.innerHTML = `<svg viewBox="0 0 5 6" width="10" height="12" aria-hidden="true" shape-rendering="crispEdges">
<path d="${pixelPath([[1, 0, 3, 1], [0, 1, 1, 2], [4, 1, 1, 2], [0, 3, 5, 1], [0, 4, 2, 1], [3, 4, 2, 1], [0, 5, 5, 1]])}" fill="currentColor"/></svg>`;
    return icon;
}

export function element(tag, className, text) {
    const node = document.createElement(tag);
    node.className = className;
    if (text !== undefined) {
        node.textContent = text;
    }
    return node;
}

export function spline(points) {
    const out = [points[0]];
    for (let i = 0; i < points.length - 1; i++) {
        const p0 = points[Math.max(0, i - 1)];
        const p1 = points[i];
        const p2 = points[i + 1];
        const p3 = points[Math.min(points.length - 1, i + 2)];
        const steps = Math.min(32, Math.max(1, Math.ceil(Math.hypot(...p2.map((v, k) => v - p1[k])) / SPLINE_BLOCKS_PER_STEP)));
        for (let step = 1; step <= steps; step++) {
            const s = step / steps;
            out.push(p1.map((_, k) => 0.5 * (2 * p1[k] + (p2[k] - p0[k]) * s
                + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * s * s
                + (3 * p1[k] - p0[k] - 3 * p2[k] + p3[k]) * s * s * s)));
        }
    }
    return out;
}

function edgePoints(outline) {
    return outline.map(([ax, az], i) => {
        const [bx, bz] = outline[(i + 1) % outline.length];
        const steps = Math.min(MAX_EDGE_SAMPLES, Math.max(1, Math.ceil(Math.hypot(bx - ax, bz - az))));
        return Array.from({length: steps + 1}, (_, k) => [ax + (bx - ax) * k / steps, az + (bz - az) * k / steps]);
    });
}

function groundKey(marker) {
    return marker.groundKey ??= `${marker.id} ${marker.points}`;
}

async function groundUnder(marker) {
    const [cx, cz] = marker.points.reduce(([sx, sz], [x, z]) => [sx + x, sz + z], [0, 0])
        .map(sum => sum / marker.points.length);
    const sides = edgePoints(marker.points).map(side => side.map(([x, z]) => ({
        x, z, pick: mapManager.topPick(Math.floor(x + Math.sign(cx - x) * .5), Math.floor(z + Math.sign(cz - z) * .5))
    })));
    const buffers = new Map();
    const picks = new Map(sides.flat().map(({pick}) => [pick.key, pick]));
    await Promise.all([...picks.values()].map(async pick => buffers.set(pick.key, await mapManager.loadPick(pick))));
    return sides.map(side => {
        const ground = smoothed(side.map(({pick}) => pick.height(buffers.get(pick.key))));
        return level(side.map(({x, z}, i) => [x, Number.isFinite(ground[i]) ? ground[i] + 1 : undefined, z]));
    });
}

function level(points) {
    return points.filter((point, i) => i === 0 || i === points.length - 1 ||
        point[1] !== points[i - 1][1] || point[1] !== points[i + 1][1]);
}

function smoothed(heights) {
    return heights.map((height, i) => {
        const window = heights.slice(Math.max(0, i - 2), i + 3).filter(Number.isFinite).sort((a, b) => a - b);
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
    return '#' + [16, 8, 0].map(bits => Math.round(((value >> bits) & 255) * factor).toString(16).padStart(2, '0')).join('');
}

function isWaypointLayer(layer) {
    return layer.id === 'waypoints' || layer.id.startsWith('waypoints.');
}

function facts(marker) {
    const round = (value) => numbers.format(Math.round(value));
    switch (marker.type) {
        case 'point':
            return marker.position.map(v => round(Math.floor(v))).join(', ');
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
            return [Math.max(...xs) - Math.min(...xs), marker.maxY - marker.minY, Math.max(...zs) - Math.min(...zs)]
                .map(round).join(' × ') + ` · Y ${round(marker.minY)} – ${round(topBlock(marker.maxY))}`;
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
        this.onWaypoint = null;
        this.hidden = null;
        this.pending = null;
        this.onPlayers = null;
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
        });
        map.on('moveend', () => this.cull());
    }

    placeLabels() {
        const map = mapManager.getMap();
        const size = map.getSize();
        const candidates = this.labels.filter(label => map.hasLayer(label.marker))
            .map(label => ({label, box: label.box(map)}));
        const placed = [];
        for (const {label, box} of candidates) {
            const show = box.right > 0 && box.left < size.x && box.bottom > 0 && box.top < size.y &&
                !placed.some(other => box.left < other.right + LABEL_GAP && other.left < box.right + LABEL_GAP &&
                    box.top < other.bottom + LABEL_GAP && other.top < box.bottom + LABEL_GAP);
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
            const response = await fetch(`/api/markers/${encodeURIComponent(world)}`);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }
            const layers = await response.json();
            if (this.world === world) {
                this.setLayers(layers);
            }
        } catch (error) {
            console.error('Failed to load markers:', error);
        }
    }

    setLayers(layers) {
        this.layers = layers;
        this.showPending();
        this.redraw();
        this.renderControls();
    }

    isShown(layer) {
        return this.visibility[layer.id] ?? layer.shown;
    }

    writeVisibility(params) {
        for (const [id, shown] of Object.entries(this.visibility)) {
            params.append(shown ? 'show' : 'hide', id);
        }
    }

    readVisibility(params) {
        this.visibility = {};
        params.getAll('show').forEach(id => this.visibility[id] = true);
        params.getAll('hide').forEach(id => this.visibility[id] = false);
        this.onPlayers?.();
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
        this.cull();
    }

    reveal(layerId, id) {
        this.pending = {layerId, id};
        this.showPending();
        this.cull();
    }

    showPending() {
        const layer = this.pending && this.layers.find(candidate => candidate.id === this.pending.layerId);
        if (layer && !this.isShown(layer)) {
            this.setShown(layer, true);
            this.renderControls();
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

    redraw() {
        this.groups.forEach(group => group.remove());
        this.groups.clear();
        this.labels = [];
        this.entries = [];
        for (const layer of this.layers) {
            const group = L.layerGroup();
            this.groups.set(layer.id, group);
            if (this.isShown(layer)) {
                group.addTo(mapManager.getMap());
            }
            for (const marker of layer.markers) {
                if (marker.id === this.hidden) {
                    continue;
                }
                this.entries.push({
                    layer,
                    marker,
                    reach: this.reach(marker),
                    rough: !this.grounds.has(groundKey(marker)),
                    drawn: null
                });
            }
        }
        this.cull();
    }

    cull() {
        clearTimeout(this.cullTimer);
        const map = mapManager.getMap();
        const view = map.getBounds().pad(VIEW_MARGIN);
        const started = performance.now();
        let unfinished = false;
        for (const entry of this.entries) {
            const group = this.groups.get(entry.layer.id);
            if (entry.rough && this.grounds.has(groundKey(entry.marker))) {
                entry.reach = this.reach(entry.marker);
                entry.rough = false;
            }
            const inView = this.isShown(entry.layer) && view.intersects(entry.reach);
            if (inView && !entry.drawn && performance.now() - started > DRAW_BUDGET_MS) {
                unfinished = true;
            } else if (inView && !entry.drawn) {
                entry.drawn = this.draw(entry.marker);
                entry.drawn?.bindPopup(() => this.popup(entry.layer, entry.marker), {className: 'marker-popup-frame'});
                entry.drawn?.off('click', entry.drawn._openPopup, entry.drawn).on('click', togglePopup);
            }
            if (entry.drawn && inView !== group.hasLayer(entry.drawn)) {
                if (inView) {
                    group.addLayer(entry.drawn);
                } else {
                    group.removeLayer(entry.drawn);
                }
            }
            if (inView && entry.drawn && entry.layer.id === this.pending?.layerId
                && entry.marker.id === this.pending.id) {
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
        const ground = this.grounds.get(groundKey(marker))?.flat().map(([, y]) => y).filter(Number.isFinite) ?? [];
        const low = ground.length > 0 ? Math.max(marker.minY, Math.min(...ground)) : marker.minY;
        const high = ground.length > 0 && marker.fullHeight ? Math.max(...ground) : marker.maxY;
        const points = marker.type === 'area'
            ? marker.points.flatMap(([x, z]) => [[x, low, z], [x, high, z]])
            : marker.type === 'line' ? marker.points : [marker.position];
        return L.latLngBounds(points.map(([x, y, z]) => mapManager.project(x, y, z)));
    }

    draw(marker) {
        const color = marker.color;
        switch (marker.type) {
            case 'point': {
                const [x, y, z] = marker.position;
                const position = mapManager.project(x, y, z);
                const pin = L.marker(position, {pane: 'waypoints', riseOnHover: true});
                if (marker.label) {
                    pin.on('add', () => pin.getElement()?.setAttribute('aria-label', marker.label));
                }
                return pin.setIcon(this.pin(marker, pin));
            }
            case 'line': {
                const points = (marker.smooth ? spline(marker.points) : marker.points)
                    .map(([x, y, z]) => mapManager.project(x, y, z));
                const {halo, weight} = this.look;
                return this.labeled(marker, L.featureGroup([
                    ...(halo ? [L.polyline(points, {color: '#000', opacity: .45, weight: 7, interactive: false})] : []),
                    hoverable(L.polyline(points, {
                        color,
                        weight: 3 + weight
                    }), {weight: 5 + weight}, {weight: 3 + weight})
                ]));
            }
            case 'area':
                return this.area(marker);
            default:
                return null;
        }
    }

    area(marker) {
        const {color, minY, maxY} = marker;
        if (!mapManager.isIsometric()) {
            return this.region(marker, marker.points.map(([x, z]) => mapManager.project(x, 0, z)));
        }

        const sides = this.grounds.get(groundKey(marker));
        if (!sides) {
            if (!this.sampling.has(groundKey(marker))) {
                this.sampling.add(groundKey(marker));
                const world = this.world;
                void groundUnder(marker).then(ground => {
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
        const base = sides.map(side => side.map(([x, ground, z]) => mapManager.project(x, bottom(ground), z)));
        if (this.far || fullHeight) {
            return this.region(marker, base.flat());
        }
        const flat = marker.points.map(([x, z]) => mapManager.project(x, 0, z));
        const winding = Math.sign(signedArea(flat));

        const walls = [];
        sides.forEach((side, i) => {
            const next = (i + 1) % flat.length;
            const [x0, , z0] = side[0];
            const [x1, , z1] = side.at(-1);
            const slice = [flat[i], flat[next], mapManager.project(x1, 1, z1), mapManager.project(x0, 1, z0)];
            if (Math.sign(signedArea(slice)) !== winding) {
                return;
            }
            const facesLeft = (flat[next].lat - flat[i].lat) * (flat[next].lng - flat[i].lng) < 0;
            const crest = side.map(([x, , z]) => mapManager.project(x, maxY, z)).reverse();
            walls.push(L.polygon([...base[i], ...crest], {
                color, weight: 1, opacity: this.look.wallLine, fillColor: shade(color, facesLeft ? .55 : .8),
                fillOpacity: this.look.wall, interactive: false
            }));
        });

        const groundLine = base.flat();
        return this.region(marker, marker.points.map(([x, z]) => mapManager.project(x, maxY, z)), [
            ...(this.look.halo ? [L.polygon(groundLine, {
                color: '#000',
                opacity: .4,
                weight: 4,
                fill: false,
                interactive: false
            })] : []),
            L.polygon(groundLine, {
                color, weight: 2 + this.look.weight, opacity: .9, fill: !marker.hollow, fillOpacity: this.look.ground,
                interactive: false
            }),
            ...walls
        ]);
    }

    region(marker, face, below = []) {
        const color = marker.color;
        const {halo, weight, fill, hoverFill} = this.look;
        const parts = [...below,
            ...(this.far || !halo ? [] : [L.polygon(face, {
                color: '#000',
                opacity: .4,
                weight: 5,
                fill: false,
                interactive: false
            })])];
        const stroke = (this.far ? 1 : 2) + weight;
        const outline = {color, weight: stroke, opacity: this.far ? .7 : 1};
        const surface = marker.hollow
            ? hoverable(L.polyline([...face, face[0]], outline),
                {weight: stroke + 2}, {weight: stroke})
            : hoverable(L.polygon(face, {...outline, fillColor: color, fillOpacity: fill}),
                {fillOpacity: hoverFill}, {fillOpacity: fill});
        parts.push(surface);
        if (marker.label) {
            const top = face.reduce((best, point) => point.lat > best.lat + 1e-9 ||
            (Math.abs(point.lat - best.lat) <= 1e-9 && point.lng < best.lng) ? point : best);
            let shown = null;
            const hide = () => {
                shown?.remove();
                shown = null;
            };
            surface.on('mouseover', () => {
                const flag = element('span', 'region-flag', marker.label);
                flag.style.setProperty('--marker', color);
                shown = L.marker(top, {
                    pane: 'waypoints', interactive: false,
                    icon: L.divIcon({className: 'region-flag-icon', html: flag, iconSize: null, iconAnchor: [1, 0]})
                }).addTo(mapManager.getMap());
            }).on('mouseout remove', hide);
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

    pin(marker, pin) {
        const body = element('div', 'waypoint');
        body.style.color = marker.color;
        body.innerHTML = PIN_SVG;
        if (marker.label) {
            const label = element('span', 'waypoint__label', marker.label);
            body.prepend(label);
            this.labels.push({
                marker: pin,
                box: (map) => {
                    const tip = map.latLngToContainerPoint(pin.getLatLng());
                    label.measured ||= label.offsetWidth;
                    label.tall ||= label.offsetHeight;
                    return {
                        left: tip.x - label.measured / 2,
                        right: tip.x + label.measured / 2,
                        top: tip.y - 26 - label.tall,
                        bottom: tip.y
                    };
                },
                show: (shown) => label.classList.toggle('is-hidden', !shown)
            });
        }
        return L.divIcon({
            className: 'waypoint-icon',
            html: body,
            iconSize: [18, 22],
            iconAnchor: [9, 22],
            popupAnchor: [0, -24]
        });
    }

    popup(layer, marker) {
        const content = element('div', 'marker-popup');
        content.style.setProperty('--marker', marker.color);
        content.append(element('span', 'marker-popup__layer', this.layerLabel(layer)),
            element('strong', 'marker-popup__title', marker.label ?? marker.id),
            element('span', 'marker-popup__facts', facts(marker)));
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
        const actions = this.waypointActions(layer, marker);
        if (actions.childElementCount > 0) {
            content.append(actions);
        }
        return content;
    }

    waypointActions(layer, marker) {
        const actions = element('div', 'marker-popup__actions');
        if (!isWaypointLayer(layer)) {
            return actions;
        }
        const point = marker.type === 'point';
        const [x, y, z] = point ? marker.position.map(Math.floor) : [];
        const waypoint = {
            world: this.world, x, y, z, name: marker.label, color: marker.color,
            group: layer.id === 'waypoints' ? '' : layer.label, previous: marker.label, id: marker.id,
            description: marker.description ?? '',
            ...(point ? {} : {
                type: marker.type, smooth: marker.smooth,
                points: marker.type === 'area' ? marker.points.map(([px, pz]) => [px, null, pz]) : marker.points
            })
        };
        const action = (name, label) => {
            const button = element('button', 'marker-popup__action', t(label));
            button.type = 'button';
            button.addEventListener('click', () => this.onWaypoint?.(name, waypoint, button));
            actions.append(button);
        };
        if (point && accountManager.canTeleport()) {
            action('teleport', 'waypoint.teleport');
        }
        if (marker.id.startsWith('waypoint:') && accountManager.canEditMarkers()) {
            if (point) {
                action('move', 'waypoint.move');
            }
            action('edit', 'waypoint.editShort');
        }
        return actions;
    }

    layerLabel(layer) {
        const key = `layer.${layer.id}`;
        const translated = t(key);
        return translated === key ? layer.label : translated;
    }

    renderControls() {
        this.list.replaceChildren(...[PLAYER_LAYER, ...this.layers].map(layer => {
            const toggle = document.createElement('input');
            toggle.type = 'checkbox';
            toggle.checked = this.isShown(layer);
            toggle.addEventListener('change', () => this.setShown(layer, toggle.checked));
            const row = element('label', 'layer');
            const name = element('span', 'layer__name', this.layerLabel(layer));
            if (layer.private) {
                name.append(privateIcon());
            }
            row.append(toggle, name);
            if (layer !== PLAYER_LAYER) {
                row.append(element('span', 'badge', numbers.format(layer.markers.length)));
            }
            const item = document.createElement('li');
            item.append(row);
            return item;
        }));
    }
}

export const markerManager = new MarkerManager();
