import {accountManager} from './account.js';
import {numbers, t} from './i18n.js';
import {mapManager} from './map.js';
import {regionManager} from './regions.js';
import {element, groundNow, markerManager, spline} from './markers.js';
import {DEFAULT_COLOR, waypointEditor, waypointUrl} from './waypoints.js';

const $ = (id) => document.getElementById(id);

const CHUNK = 16;
const MAX_CHUNKS = 16384;
const MAX_REGION_CHUNKS = 65536;
const OTHER_REGION = '#9aa0a6';
const STEP_PIXELS = 4;
const STATUS_MILLIS = 4000;
const UNKNOWN_GROUND = 63;
const LINE = '#ffffff';
const ERASE_LINE = '#ff5555';
const HANDLE_PIXELS = 8;
const TOUCH = matchMedia('(hover: none)');
const SMALL = matchMedia('(max-width: 720px)');
const HINTS = {
    chunks: [['drag', 'paint'], ['shift', 'erase'], ['rightDrag', 'pan']],
    points: [['click', 'add'], ['drag', 'move'], ['clickPoint', 'remove'], ['backspace', 'undo'], ['rightDrag', 'pan']],
    chunksTouch: [['drag', 'paint'], ['startSelected', 'erase'], ['twoFingers', 'panZoom']],
    pointsTouch: [['tap', 'add'], ['drag', 'move'], ['tapPoint', 'remove'], ['twoFingers', 'panZoom']]
};
const TOOLS = {chunks: ['brush', 'rectangle', 'lasso'], region: ['brush', 'rectangle', 'lasso'], area: ['brush', 'rectangle', 'lasso', 'points'], line: ['points']};

const key = (chunkX, chunkZ) => `${chunkX},${chunkZ}`;
const SIDES = [[0, -1], [1, 0], [0, 1], [-1, 0]];
const CORNERS = [[0, 0], [1, 0], [1, 1], [0, 1]];

function trace(chunks) {
    const edges = new Map();
    for (const [x, z] of chunks.values()) {
        SIDES.forEach(([dx, dz], side) => {
            if (!chunks.has(key(x + dx, z + dz))) {
                const [fromX, fromZ] = CORNERS[side];
                const [toX, toZ] = CORNERS[(side + 1) % CORNERS.length];
                const from = key(x + fromX, z + fromZ);
                if (!edges.has(from)) {
                    edges.set(from, []);
                }
                edges.get(from).push([x + toX, z + toZ]);
            }
        });
    }
    const rings = [];
    while (edges.size > 0) {
        const start = edges.keys().next().value;
        const ring = [];
        let at = start;
        do {
            const next = edges.get(at);
            const [toX, toZ] = next.pop();
            if (next.length === 0) {
                edges.delete(at);
            }
            const [atX, atZ] = at.split(',').map(Number);
            ring.push([atX * CHUNK, atZ * CHUNK]);
            at = key(toX, toZ);
        } while (at !== start);
        rings.push(ring);
    }
    return rings;
}

function corners(ring) {
    return ring.filter(([x, z], i) => {
        const [px, pz] = ring.at(i - 1);
        const [nx, nz] = ring[(i + 1) % ring.length];
        return (x - px) * (nz - z) !== (z - pz) * (nx - x);
    });
}

function connected(chunks) {
    const left = new Map(chunks);
    const open = [left.keys().next().value];
    left.delete(open[0]);
    while (open.length > 0) {
        const [x, z] = open.pop().split(',').map(Number);
        for (const [dx, dz] of SIDES) {
            const next = key(x + dx, z + dz);
            if (left.delete(next)) {
                open.push(next);
            }
        }
    }
    return left.size === 0;
}

const chunkOf = (block) => Math.floor(block / CHUNK);
const touching = (at) => at % CHUNK === 0 ? [at / CHUNK - 1, at / CHUNK] : [chunkOf(at)];

function inside(outline, x, z) {
    let crossings = 0;
    for (let i = 0, j = outline.length - 1; i < outline.length; j = i++) {
        const [ax, az] = outline[i];
        const [bx, bz] = outline[j];
        if ((az > z) !== (bz > z) && x < (bx - ax) * (z - az) / (bz - az) + ax) {
            crossings++;
        }
    }
    return crossings % 2 === 1;
}

class ChunkSelection {
    constructor() {
        this.world = null;
        this.kind = 'chunks';
        this.tool = 'brush';
        this.lastTool = 'brush';
        this.chunks = new Map();
        this.points = [];
        this.smooth = false;
        this.color = DEFAULT_COLOR;
        this.editing = null;
        this.rings = [];
        this.traced = false;
        this.grounds = new Map();
        this.levels = new Map();
        this.stroke = null;
        this.pan = null;
        this.status = null;
        this.failed = false;
        this.sending = false;
        this.layers = [];
        this.missing = false;
        this.frame = 0;
        this.retry = 0;
        this.fog = 'hide';
        this.discoverable = false;
    }

    initialize() {
        const map = mapManager.getMap();
        const container = map.getContainer();
        const ignored = (event) => event.target.closest('.leaflet-control, .leaflet-popup');
        container.addEventListener('pointerdown', (event) => {
            if (this.world && !event.isPrimary && this.stroke) {
                this.stroke = null;
                this.changed();
            }
            if (!this.world || !event.isPrimary || ignored(event)) {
                return;
            }
            const point = map.mouseEventToContainerPoint(event);
            if (event.button === 2) {
                this.pan = point;
            } else if (event.button === 0) {
                this.begin(point, event.shiftKey);
            }
        });
        container.addEventListener('pointermove', (event) => {
            if (!event.isPrimary) {
                return;
            }
            const point = map.mouseEventToContainerPoint(event);
            if (this.pan) {
                map.panBy(this.pan.subtract(point), {animate: false});
                this.pan = point;
            } else if (this.stroke) {
                this.move(point);
            }
        });
        const end = () => {
            this.pan = null;
            this.finish();
        };
        addEventListener('pointerup', end);
        addEventListener('pointercancel', end);
        for (const type of ['click', 'contextmenu']) {
            container.addEventListener(type, (event) => {
                if (this.world && !ignored(event)) {
                    event.preventDefault();
                    event.stopPropagation();
                }
            }, true);
        }
        document.addEventListener('keydown', (event) => {
            if (!this.world || event.target.closest('input, select, textarea, dialog')) {
                return;
            }
            if (event.key === 'Escape') {
                this.stop();
            } else if (event.key === 'Enter' && this.kind !== 'chunks' && !event.target.closest('button')) {
                event.preventDefault();
                this.save();
            } else if (event.key === 'Backspace' && this.tool === 'points' && this.points.length > 0) {
                event.preventDefault();
                this.points.pop();
                this.changed();
            }
        });
        for (const button of $('selection').querySelectorAll('[data-tool]')) {
            button.addEventListener('click', () => this.setTool(button.dataset.tool));
        }
        for (const button of $('selection').querySelectorAll('[data-action]')) {
            button.addEventListener('click', () => void this.act(button.dataset.action));
        }
        $('selection-delete').addEventListener('click', () => void this.remove());
        $('region-reset').addEventListener('click', () => void this.resetDiscoveries());
        for (const button of $('region-fog').querySelectorAll('[data-fog]')) {
            button.addEventListener('click', () => {
                this.fog = button.dataset.fog;
                this.changed();
            });
        }
        $('region-discoverable').addEventListener('click', () => {
            this.discoverable = !this.discoverable;
            this.changed();
        });
        $('selection-smooth').addEventListener('click', () => {
            this.smooth = !this.smooth;
            this.changed();
        });
        map.on('zoomend', () => this.redraw());
    }

    start(world, kind, editing = null) {
        const map = mapManager.getMap();
        this.world = world;
        this.kind = kind;
        this.editing = editing;
        this.chunks.clear();
        this.traced = false;
        const region = kind === 'region';
        if (region) {
            for (const [x, z] of editing?.chunks ?? []) {
                this.chunks.set(key(x, z), [x, z]);
            }
            this.fog = editing?.fog ?? 'hide';
            this.discoverable = editing?.discoverable ?? true;
            $('region-name').value = editing?.name ?? '';
            $('region-name').placeholder = t('region.name');
            this.label($('region-reset'), 'region.reset');
            $('region-reset').classList.remove('is-armed');
            regionManager.hide(editing?.id ?? null);
        }
        this.points = region ? [] : editing?.points.map(([x, , z]) => [x, z]) ?? [];
        this.smooth = editing?.smooth ?? false;
        this.color = editing?.color ?? DEFAULT_COLOR;
        this.grounds.clear();
        this.levels.clear();
        map.closePopup();
        map.dragging.disable();
        map.boxZoom.disable();
        const chunks = kind === 'chunks';
        this.label($('selection-title'), chunks ? 'selection.title'
            : region && editing ? 'selection.title.regionEdit' : `selection.title.${kind}`);
        this.label($('selection-done'), chunks ? 'selection.done' : 'selection.cancel');
        this.label($('selection-delete'), 'waypoint.delete');
        $('selection-delete').classList.remove('is-armed');
        $('selection-delete').hidden = !editing;
        markerManager.hide(region ? null : editing?.id ?? null);
        $('selection-render').hidden = !chunks || !accountManager.canRender();
        $('selection-region').hidden = !region;
        $('region-reset').hidden = !region || !editing;
        $('selection-smooth').hidden = kind !== 'line';
        $('selection-save').hidden = chunks;
        this.label($('selection-save'), region ? 'region.save' : 'selection.save');
        $('selection-tools').hidden = kind === 'line';
        for (const button of $('selection').querySelectorAll('[data-tool]')) {
            button.hidden = !TOOLS[kind].includes(button.dataset.tool);
        }
        $('selection').hidden = false;
        if (SMALL.matches && $('panel-toggle').getAttribute('aria-expanded') === 'true') {
            $('panel-toggle').click();
        }
        this.tool = null;
        this.setTool(editing || kind === 'line' ? 'points' : this.lastTool);
        this.changed();
    }

    stop() {
        const map = mapManager.getMap();
        this.world = null;
        this.stroke = null;
        this.pan = null;
        this.chunks.clear();
        this.points = [];
        this.editing = null;
        markerManager.hide(null);
        regionManager.hide(null);
        map.dragging.enable();
        map.boxZoom.enable();
        $('selection').hidden = true;
        this.redraw();
    }

    setTool(tool) {
        if (!this.world || !TOOLS[this.kind].includes(tool) || tool === this.tool) {
            return;
        }
        if (this.kind === 'area' && this.tool && (tool === 'points') !== (this.tool === 'points')) {
            if (tool === 'points' && this.chunks.size > 0) {
                const rings = this.outlines();
                if (rings.length !== 1) {
                    this.show(t('selection.split'), true);
                    return;
                }
                this.points = corners(rings[0]);
                this.chunks.clear();
                this.traced = false;
            } else if (tool !== 'points' && this.points.length >= 3) {
                this.fill(this.points, false);
                this.points = [];
            }
        }
        this.tool = tool;
        if (tool !== 'points') {
            this.lastTool = tool;
        }
        for (const button of $('selection').querySelectorAll('[data-tool]')) {
            button.setAttribute('aria-pressed', String(button.dataset.tool === tool));
        }
        this.changed();
    }

    label(element, key) {
        element.dataset.i18n = key;
        element.textContent = t(key);
    }

    blockAt(point) {
        const block = mapManager.latLngToBlock(mapManager.getMap().containerPointToLatLng(point));
        return Number.isFinite(block.x) && Number.isFinite(block.z) ? block : null;
    }

    pointAt(point) {
        const map = mapManager.getMap();
        let nearest = -1;
        let best = HANDLE_PIXELS;
        const handles = this.handles();
        this.points.forEach((_, i) => {
            const distance = map.latLngToContainerPoint(handles[i]).distanceTo(point);
            if (distance <= best) {
                best = distance;
                nearest = i;
            }
        });
        return nearest;
    }

    segmentAt(point) {
        const map = mapManager.getMap();
        const screen = this.handles().map(handle => map.latLngToContainerPoint(handle));
        const closed = this.kind === 'area' && screen.length >= 3;
        for (let i = 0; i < screen.length - (closed ? 0 : 1); i++) {
            const a = screen[i];
            const b = screen[(i + 1) % screen.length];
            if (L.LineUtil.pointToSegmentDistance(point, a, b) <= HANDLE_PIXELS / 2) {
                return i;
            }
        }
        return -1;
    }

    begin(point, shift) {
        const block = this.blockAt(point);
        if (!block) {
            return;
        }
        if (this.tool === 'points') {
            let index = this.pointAt(point);
            if (shift) {
                if (index >= 0) {
                    this.points.splice(index, 1);
                }
            } else {
                const existing = index >= 0;
                if (!existing) {
                    const segment = this.segmentAt(point);
                    index = segment < 0 ? this.points.length : segment + 1;
                    this.points.splice(index, 0, [block.x + .5, block.z + .5]);
                }
                this.stroke = {index, existing, moved: false};
            }
            this.changed();
            return;
        }
        const erase = shift || this.chunks.has(key(chunkOf(block.x), chunkOf(block.z)));
        this.stroke = {erase, anchor: block, reach: block, last: point, lasso: [[block.x + .5, block.z + .5]]};
        if (this.tool === 'brush') {
            this.apply(chunkOf(block.x), chunkOf(block.z), erase);
        }
        this.changed();
    }

    move(point) {
        const stroke = this.stroke;
        if (stroke.index !== undefined) {
            const block = this.blockAt(point);
            const [x, z] = this.points[stroke.index];
            if (block && (block.x + .5 !== x || block.z + .5 !== z)) {
                this.points[stroke.index] = [block.x + .5, block.z + .5];
                stroke.moved = true;
                this.changed();
            }
            return;
        }
        const steps = this.tool === 'brush' ? Math.ceil(point.distanceTo(stroke.last) / STEP_PIXELS) : 1;
        for (let step = 1; step <= steps; step++) {
            const block = this.blockAt(stroke.last.add(point.subtract(stroke.last).multiplyBy(step / steps)));
            if (!block) {
                continue;
            }
            stroke.reach = block;
            if (this.tool === 'brush') {
                this.apply(chunkOf(block.x), chunkOf(block.z), stroke.erase);
            } else if (this.tool === 'lasso') {
                const [lastX, lastZ] = stroke.lasso.at(-1);
                if (Math.abs(lastX - block.x - .5) + Math.abs(lastZ - block.z - .5) >= 1) {
                    stroke.lasso.push([block.x + .5, block.z + .5]);
                }
            }
        }
        stroke.last = point;
        this.changed();
    }

    finish() {
        const stroke = this.stroke;
        if (!stroke) {
            return;
        }
        this.stroke = null;
        if (stroke.existing && !stroke.moved) {
            this.points.splice(stroke.index, 1);
        } else if (this.tool === 'rectangle') {
            const [minX, minZ, maxX, maxZ] = this.box(stroke);
            for (let x = minX; x <= maxX; x++) {
                for (let z = minZ; z <= maxZ; z++) {
                    this.apply(x, z, stroke.erase);
                }
            }
        } else if (this.tool === 'lasso' && stroke.lasso.length >= 3) {
            this.fill(stroke.lasso, stroke.erase);
        }
        this.changed();
    }

    fill(outline, erase) {
        const xs = outline.map(([x]) => x);
        const zs = outline.map(([, z]) => z);
        for (let x = chunkOf(Math.min(...xs)); x <= Math.max(...xs) / CHUNK; x++) {
            for (let z = chunkOf(Math.min(...zs)); z <= Math.max(...zs) / CHUNK; z++) {
                if (inside(outline, x * CHUNK + CHUNK / 2, z * CHUNK + CHUNK / 2)) {
                    this.apply(x, z, erase);
                }
            }
        }
    }

    box(stroke) {
        const {anchor, reach} = stroke;
        return [
            chunkOf(Math.min(anchor.x, reach.x)), chunkOf(Math.min(anchor.z, reach.z)),
            chunkOf(Math.max(anchor.x, reach.x)), chunkOf(Math.max(anchor.z, reach.z))
        ];
    }

    apply(chunkX, chunkZ, erase) {
        if (erase) {
            if (this.chunks.delete(key(chunkX, chunkZ))) {
                this.traced = false;
            }
        } else if (this.chunks.size < this.limit() && !this.chunks.has(key(chunkX, chunkZ))
            && !this.taken(chunkX, chunkZ)) {
            this.chunks.set(key(chunkX, chunkZ), [chunkX, chunkZ]);
            this.traced = false;
        }
    }

    limit() {
        return this.kind === 'region' ? MAX_REGION_CHUNKS : MAX_CHUNKS;
    }

    taken(chunkX, chunkZ) {
        if (this.kind !== 'region') {
            return false;
        }
        const owner = regionManager.at(chunkX, chunkZ);
        return owner !== null && owner.id !== this.editing?.id;
    }

    outlines() {
        if (!this.traced) {
            this.rings = trace(this.chunks);
            this.traced = true;
        }
        return this.rings;
    }

    changed() {
        this.status = null;
        this.failed = false;
        this.redraw();
    }

    outline() {
        const stroke = this.stroke;
        if (!stroke || this.tool === 'brush' || this.tool === 'points') {
            return null;
        }
        if (this.tool === 'lasso') {
            return stroke.lasso;
        }
        const [minX, minZ, maxX, maxZ] = this.box(stroke);
        const [x0, z0, x1, z1] = [minX * CHUNK, minZ * CHUNK, (maxX + 1) * CHUNK, (maxZ + 1) * CHUNK];
        return [[x0, z0], [x1, z0], [x1, z1], [x0, z1]];
    }

    shape() {
        if (this.kind === 'line') {
            return this.points.length >= 2 ? this.points : null;
        }
        if (this.tool === 'points') {
            return this.points.length >= 3 ? this.points : null;
        }
        const rings = this.outlines();
        return rings.length === 1 ? corners(rings[0]) : null;
    }

    ground(x, z) {
        if (!mapManager.isIsometric()) {
            return mapManager.project(x, 0, z);
        }
        const blockX = Math.floor(x);
        const blockZ = Math.floor(z);
        const cell = key(blockX, blockZ);
        let y = this.grounds.get(cell);
        if (y === undefined) {
            const surface = mapManager.surfaceHeightAt(blockX, blockZ);
            if (Number.isFinite(surface)) {
                this.grounds.set(cell, surface);
                y = surface;
            } else {
                this.missing = true;
                y = UNKNOWN_GROUND;
            }
        }
        return mapManager.project(x, y + 1, z);
    }

    draped(points) {
        if (this.kind !== 'area' || !mapManager.isIsometric() || points.length < 3) {
            return null;
        }
        return groundNow(points).map(side => side.map(([x, y, z]) => {
            if (y === undefined) {
                this.missing = true;
            }
            return mapManager.project(x, y ?? UNKNOWN_GROUND + 1, z);
        }));
    }

    handles() {
        return this.draped(this.points)?.map(side => side[0]) ?? this.points.map(([x, z]) => this.ground(x, z));
    }

    level(x, z, selected = null) {
        if (!mapManager.isIsometric()) {
            return mapManager.project(x, 0, z);
        }
        const levels = touching(x).flatMap(chunkX => touching(z)
            .filter(chunkZ => !selected || selected.has(key(chunkX, chunkZ)))
            .map(chunkZ => this.chunkLevel(chunkX, chunkZ)));
        return mapManager.project(x, levels.reduce((sum, y) => sum + y, 0) / levels.length + 1, z);
    }

    chunkLevel(chunkX, chunkZ) {
        const cell = key(chunkX, chunkZ);
        const known = this.levels.get(cell);
        if (known !== undefined) {
            return known;
        }
        const heights = [];
        for (let x = 2; x < CHUNK; x += 4) {
            for (let z = 2; z < CHUNK; z += 4) {
                const surface = mapManager.surfaceHeightAt(chunkX * CHUNK + x, chunkZ * CHUNK + z);
                if (Number.isFinite(surface)) {
                    heights.push(surface);
                }
            }
        }
        if (heights.length === 0) {
            this.missing = true;
            return UNKNOWN_GROUND;
        }
        heights.sort((a, b) => a - b);
        const y = heights[heights.length >> 1];
        this.levels.set(cell, y);
        return y;
    }

    redraw() {
        cancelAnimationFrame(this.frame);
        this.frame = requestAnimationFrame(() => this.draw());
    }

    add(layer) {
        this.layers.push(layer.addTo(mapManager.getMap()));
    }

    draw() {
        for (const layer of this.layers) {
            layer.remove();
        }
        this.layers = [];
        this.missing = false;
        if (!this.world) {
            return;
        }
        const region = this.kind === 'region';
        const marker = this.kind !== 'chunks' && !region;
        const color = marker ? this.color : LINE;
        if (region) {
            for (const other of regionManager.regions) {
                if (other.id === this.editing?.id || !other.chunks) {
                    continue;
                }
                const owned = new Map(other.chunks.map(([x, z]) => [key(x, z), [x, z]]));
                this.add(L.polygon(trace(owned).map(ring => corners(ring).map(([x, z]) => this.level(x, z, owned))), {
                    color: OTHER_REGION, opacity: .8, weight: 1, dashArray: '4 4',
                    fillColor: OTHER_REGION, fillOpacity: .15, interactive: false
                }));
            }
        }
        if (this.chunks.size > 0) {
            this.add(L.polygon(this.outlines().map(ring => this.draped(corners(ring))?.flat() ?? ring.map(([x, z]) => this.level(x, z, this.chunks))), {
                color, opacity: marker ? 1 : .75, weight: marker ? 2 : 1,
                fillColor: color, fillOpacity: marker ? .3 : .25, interactive: false
            }));
        }
        const outline = this.outline();
        if (outline && outline.length > 1) {
            this.add(L.polygon(outline.map(([x, z]) => this.level(x, z)), {
                color: this.stroke.erase ? ERASE_LINE : LINE, weight: 2, fill: false, interactive: false
            }));
        }
        if (this.points.length > 0) {
            if (this.kind === 'line') {
                const path = this.smooth && this.points.length > 2 ? spline(this.points) : this.points;
                this.add(L.polyline(path.map(([x, z]) => this.ground(x, z)), {color, weight: 3, interactive: false}));
            } else {
                this.add(L.polygon(this.draped(this.points)?.flat() ?? this.points.map(([x, z]) => this.ground(x, z)), {
                    color, weight: 2, fillColor: color, fillOpacity: .3, interactive: false
                }));
            }
            for (const handle of this.handles()) {
                this.add(L.circleMarker(handle, {
                    radius: 4, color: '#000', weight: 1, fillColor: LINE, fillOpacity: 1, interactive: false
                }));
            }
        }
        if (this.missing) {
            clearTimeout(this.retry);
            this.retry = setTimeout(() => this.redraw(), 300);
        }
        const points = this.tool === 'points';
        const split = (this.kind === 'area' && !points && this.outlines().length > 1)
            || (region && this.chunks.size > 0 && !connected(this.chunks));
        $('selection-count').textContent = numbers.format(points ? this.points.length : this.chunks.size);
        const full = !points && this.chunks.size >= this.limit();
        const message = this.status ?? (split ? t(region ? 'region.split' : 'selection.split')
            : full ? t('selection.full', {count: numbers.format(this.limit())}) : null);
        $('selection-text').classList.toggle('selection__message--keys', !message);
        if (message) {
            $('selection-text').textContent = message;
        } else {
            $('selection-text').replaceChildren(...this.hints(points));
        }
        $('selection-smooth').setAttribute('aria-pressed', String(this.smooth));
        $('selection-smooth').textContent = t(this.smooth ? 'selection.smooth.on' : 'selection.smooth.off');
        $('selection-save').disabled = this.sending || (region ? this.chunks.size === 0 || split : !this.shape());
        for (const button of $('region-fog').querySelectorAll('[data-fog]')) {
            button.setAttribute('aria-pressed', String(button.dataset.fog === this.fog));
        }
        $('region-discoverable').setAttribute('aria-pressed', String(this.discoverable));
        $('region-discoverable').textContent = t(this.discoverable ? 'region.discoverable.on' : 'region.discoverable.off');
        $('selection').classList.toggle('is-failed', split || full || (this.status !== null && this.failed));
        $('selection').classList.toggle('is-done', !split && !full && this.status !== null && !this.failed);
    }

    hints(points) {
        const pairs = [...HINTS[`${points ? 'points' : 'chunks'}${TOUCH.matches ? 'Touch' : ''}`]];
        if (!TOUCH.matches) {
            pairs.push(['esc', this.kind === 'chunks' ? 'exit' : 'cancel']);
        }
        return pairs.flatMap(([key, action]) => {
            const keycap = element('span', 'selection__key');
            keycap.append(element('kbd', '', t(`hint.key.${key}`)));
            return [keycap, element('span', 'selection__action', t(`hint.do.${action}`))];
        });
    }

    setSending(sending) {
        this.sending = sending;
        for (const button of $('selection').querySelectorAll('[data-action]:not([data-action="done"])')) {
            button.disabled = sending;
        }
    }

    async act(action) {
        if (action === 'done') {
            this.stop();
            return;
        }
        if (this.sending || !this.world) {
            return;
        }
        if (action === 'save') {
            this.save();
            return;
        }
        if (this.chunks.size === 0) {
            this.show(t('selection.empty'), true);
            return;
        }
        const world = this.world;
        this.setSending(true);
        let response = null;
        try {
            response = await fetch('/api/area', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({action, world, chunks: [...this.chunks.values()]})
            });
        } catch {
        }
        this.setSending(false);
        if (world !== this.world) {
            return;
        }
        if (response?.status === 202) {
            this.chunks.clear();
        }
        this.show(t(response?.status === 202 ? `selection.started.${action}`
            : response?.status === 409 ? 'selection.busy' : 'selection.failed'), response?.status !== 202);
    }

    async saveRegion() {
        if (this.sending || this.chunks.size === 0 || !connected(this.chunks)) {
            return;
        }
        const world = this.world;
        this.setSending(true);
        let response = null;
        try {
            response = await fetch('/api/regions', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    world, id: this.editing?.id ?? null, name: $('region-name').value, fog: this.fog,
                    discoverable: this.discoverable, chunks: [...this.chunks.values()]
                })
            });
        } catch {
        }
        this.setSending(false);
        if (world !== this.world) {
            return;
        }
        if (response?.ok) {
            const {id} = await response.json();
            this.editing = {...this.editing, id};
            $('selection-delete').hidden = false;
            $('region-reset').hidden = false;
            this.label($('selection-title'), 'selection.title.regionEdit');
            regionManager.hide(id);
            this.show(t(response.status === 202 ? 'region.started' : 'region.saved'), false);
            return;
        }
        const problem = response ? (await response.json().catch(() => null))?.problem : null;
        this.show(t(`region.problem.${problem ?? 'failed'}`), true);
    }

    async resetDiscoveries() {
        const button = $('region-reset');
        if (!button.classList.contains('is-armed')) {
            button.classList.add('is-armed');
            this.label(button, 'region.resetConfirm');
            return;
        }
        let response = null;
        try {
            response = await fetch(`/api/regions/${this.editing.id}/reset`, {method: 'POST'});
        } catch {
        }
        button.classList.remove('is-armed');
        this.label(button, 'region.reset');
        this.show(t(response?.ok ? 'region.resetDone' : 'region.problem.failed'), !response?.ok);
    }

    save() {
        if (this.kind === 'region') {
            void this.saveRegion();
            return;
        }
        const shape = this.shape();
        if (!shape) {
            return;
        }
        const heights = new Map(this.editing?.points.map(([x, y, z]) => [`${x},${z}`, y]));
        void waypointEditor.open({
            ...this.editing, world: this.world, type: this.kind, smooth: this.kind === 'line' && this.smooth,
            color: this.color, points: shape.map(([x, z]) => [x, heights.get(`${x},${z}`) ?? null, z]),
            onSaved: () => this.stop(),
            onColor: (color) => {
                this.color = color;
                this.redraw();
            }
        });
    }

    async remove() {
        const button = $('selection-delete');
        if (!button.classList.contains('is-armed')) {
            button.classList.add('is-armed');
            this.label(button, 'waypoint.confirmDelete');
            return;
        }
        const world = this.world;
        if (this.kind === 'region') {
            let response = null;
            try {
                response = await fetch(`/api/regions/${this.editing.id}`, {method: 'DELETE'});
            } catch {
            }
            if (response?.ok) {
                this.stop();
                return;
            }
            button.classList.remove('is-armed');
            this.label(button, 'waypoint.delete');
            const problem = response ? (await response.json().catch(() => null))?.problem : null;
            this.show(t(`region.problem.${problem ?? 'failed'}`), true);
            return;
        }
        const previous = this.editing.previous;
        let response = null;
        try {
            response = await fetch(waypointUrl(previous, this.editing.personal), {method: 'DELETE'});
        } catch {
        }
        if (response?.ok) {
            this.stop();
            void markerManager.load(world);
            return;
        }
        button.classList.remove('is-armed');
        this.label(button, 'waypoint.delete');
        this.show(t('waypoint.failed', {name: previous}), true);
    }

    show(status, failed) {
        this.status = status;
        this.failed = failed;
        this.redraw();
        clearTimeout(this.statusTimer);
        this.statusTimer = setTimeout(() => {
            if (this.status === status) {
                this.status = null;
                this.redraw();
            }
        }, STATUS_MILLIS);
    }
}

export const chunkSelection = new ChunkSelection();
