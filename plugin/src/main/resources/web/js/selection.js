import {accountManager} from './account.js';
import {numbers, t} from './i18n.js';
import {mapManager} from './map.js';
import {cell, freshColor, regionManager} from './regions.js';
import {regionEditor} from './regioneditor.js';
import {groundNow, markerManager, spline} from './markers.js';
import {armed, disarm, element} from './ui.js';
import {DEFAULT_COLOR, waypointEditor, waypointUrl} from './waypoints.js';

const $ = (id) => document.getElementById(id);

const CHUNK = 16;
const MAX_CHUNKS = 16384;
const MAX_REGION_CHUNKS = 65536;
const STEP_PIXELS = 4;
const STATUS_MILLIS = 4000;
const UNKNOWN_GROUND = 63;
const LINE = '#ffffff';
const ERASE = '#ff5555';
const HANDLE_PIXELS = 8;
const HISTORY = 200;
const SIZES = [1, 3, 5];
const TOUCH = matchMedia('(hover: none)');
const SMALL = matchMedia('(max-width: 720px)');
const TOOLS = {chunks: ['brush', 'rectangle', 'lasso'], region: ['brush', 'rectangle', 'lasso'], area: ['brush', 'rectangle', 'lasso', 'points'], line: ['points']};

const SIDES = [[0, -1], [1, 0], [0, 1], [-1, 0]];
const CORNERS = [[0, 0], [1, 0], [1, 1], [0, 1]];

function trace(chunks) {
    const edges = new Map();
    for (const [x, z] of chunks.values()) {
        SIDES.forEach(([dx, dz], side) => {
            if (!chunks.has(cell(x + dx, z + dz))) {
                const [fromX, fromZ] = CORNERS[side];
                const [toX, toZ] = CORNERS[(side + 1) % CORNERS.length];
                const from = cell(x + fromX, z + fromZ);
                let next = edges.get(from);
                if (!next) {
                    next = {at: [x + fromX, z + fromZ], to: []};
                    edges.set(from, next);
                }
                next.to.push([x + toX, z + toZ]);
            }
        });
    }
    const rings = [];
    while (edges.size > 0) {
        const start = edges.keys().next().value;
        const ring = [];
        let at = start;
        do {
            const edge = edges.get(at);
            const [toX, toZ] = edge.to.pop();
            if (edge.to.length === 0) {
                edges.delete(at);
            }
            ring.push([edge.at[0] * CHUNK, edge.at[1] * CHUNK]);
            at = cell(toX, toZ);
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

function detached(chunks) {
    const left = new Map(chunks);
    const pieces = [];
    while (left.size > 0) {
        const [[key, first]] = left;
        left.delete(key);
        const piece = [first];
        for (let i = 0; i < piece.length; i++) {
            const [x, z] = piece[i];
            for (const [dx, dz] of SIDES) {
                const next = cell(x + dx, z + dz);
                const chunk = left.get(next);
                if (chunk) {
                    left.delete(next);
                    piece.push(chunk);
                }
            }
        }
        pieces.push(piece);
    }
    pieces.sort((a, b) => b.length - a.length);
    return pieces.slice(1).flat();
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
        this.mode = 'paint';
        this.size = 1;
        this.chunks = new Map();
        this.points = [];
        this.smooth = false;
        this.color = DEFAULT_COLOR;
        this.editing = null;
        this.version = 0;
        this.rings = null;
        this.split = null;
        this.loose = [];
        this.history = [];
        this.future = [];
        this.grounds = new Map();
        this.levels = new Map();
        this.stroke = null;
        this.hover = null;
        this.pan = null;
        this.status = null;
        this.failed = false;
        this.sending = false;
        this.drawn = new Map();
        this.view = null;
        this.missing = false;
        this.frame = 0;
        this.retry = 0;
    }

    initialize() {
        const map = mapManager.getMap();
        const container = map.getContainer();
        const ignored = (event) => event.target.closest('.leaflet-control, .leaflet-popup');
        container.addEventListener('pointerdown', (event) => {
            if (this.world && !event.isPrimary && this.stroke) {
                this.cancelStroke();
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
            if (!event.isPrimary || !this.world) {
                return;
            }
            const point = map.mouseEventToContainerPoint(event);
            if (this.pan) {
                map.panBy(this.pan.subtract(point), {animate: false});
                this.pan = point;
            } else if (this.stroke) {
                this.move(point);
            } else if (event.pointerType === 'mouse') {
                this.hoverAt(point, event.shiftKey);
            }
        });
        container.addEventListener('pointerleave', () => {
            if (this.hover) {
                this.hover = null;
                this.redraw();
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
            const command = event.ctrlKey || event.metaKey;
            if (command && event.key.toLowerCase() === 'z') {
                event.preventDefault();
                event.shiftKey ? this.redo() : this.undo();
            } else if (command && event.key.toLowerCase() === 'y') {
                event.preventDefault();
                this.redo();
            } else if (event.key === 'Escape') {
                this.stop();
            } else if (event.key === 'Enter' && this.kind !== 'chunks' && !event.target.closest('button')) {
                event.preventDefault();
                this.save();
            } else if (event.key === 'Backspace' && this.tool === 'points' && this.points.length > 0) {
                event.preventDefault();
                this.remember({points: this.points.map(point => [...point])});
                this.points.pop();
                this.changed();
            } else if ((event.key === '[' || event.key === ']') && !command) {
                const at = SIZES.indexOf(this.size) + (event.key === ']' ? 1 : -1);
                this.setSize(SIZES[Math.max(0, Math.min(SIZES.length - 1, at))]);
            } else if (event.key === 'Shift' && this.hover) {
                this.hover.shift = true;
                this.redraw();
            }
        });
        document.addEventListener('keyup', (event) => {
            if (event.key === 'Shift' && this.hover) {
                this.hover.shift = false;
                this.redraw();
            }
        });
        for (const button of $('selection').querySelectorAll('[data-tool]')) {
            button.addEventListener('click', () => this.setTool(button.dataset.tool));
        }
        for (const button of $('selection').querySelectorAll('[data-action]')) {
            button.addEventListener('click', () => void this.act(button.dataset.action));
        }
        for (const button of $('selection-mode').querySelectorAll('[data-mode]')) {
            button.addEventListener('click', () => this.setMode(button.dataset.mode));
        }
        for (const button of $('selection-size').querySelectorAll('[data-size]')) {
            button.addEventListener('click', () => this.setSize(Number(button.dataset.size)));
        }
        $('selection-undo').addEventListener('click', () => this.undo());
        $('selection-redo').addEventListener('click', () => this.redo());
        $('selection-delete').addEventListener('click', () => void this.remove());
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
        const region = kind === 'region';
        if (region) {
            for (const [x, z] of editing?.chunks ?? []) {
                this.chunks.set(cell(x, z), [x, z]);
            }
            regionManager.hide('all');
        }
        this.points = region ? [] : editing?.points.map(([x, , z]) => [x, z]) ?? [];
        this.smooth = editing?.smooth ?? false;
        this.color = editing?.color ?? (region ? freshColor() : DEFAULT_COLOR);
        this.history = [];
        this.future = [];
        this.hover = null;
        this.touched();
        this.grounds.clear();
        this.levels.clear();
        map.closePopup();
        map.dragging.disable();
        map.boxZoom.disable();
        const chunks = kind === 'chunks';
        this.label($('selection-title'), chunks ? 'selection.title'
            : region && editing ? 'selection.title.regionEdit' : `selection.title.${kind}`);
        this.label($('selection-done'), chunks ? 'selection.done' : 'selection.cancel');
        disarm($('selection-delete'));
        this.label($('selection-delete'), 'waypoint.delete');
        $('selection-delete').hidden = !editing;
        markerManager.hide(region ? null : editing?.id ?? null);
        $('selection-render').hidden = !chunks || !accountManager.canRenderArea();
        $('selection-smooth').hidden = kind !== 'line';
        $('selection-save').hidden = chunks;
        this.label($('selection-save'), 'selection.save');
        $('selection-tools').hidden = kind === 'line';
        for (const button of $('selection').querySelectorAll('[data-tool]')) {
            button.hidden = !TOOLS[kind].includes(button.dataset.tool);
        }
        $('selection').hidden = false;
        if (SMALL.matches && $('panel-toggle').getAttribute('aria-expanded') === 'true') {
            $('panel-toggle').click();
        }
        this.tool = null;
        this.setTool(editing && kind !== 'region' || kind === 'line' ? 'points' : this.lastTool);
        this.changed();
    }

    stop() {
        const map = mapManager.getMap();
        this.world = null;
        this.stroke = null;
        this.hover = null;
        this.pan = null;
        this.chunks.clear();
        this.points = [];
        this.editing = null;
        this.history = [];
        this.future = [];
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
                this.touched();
            } else if (tool !== 'points' && this.points.length >= 3) {
                this.fill(this.points, false);
                this.points = [];
                this.touched();
            }
            this.history = [];
            this.future = [];
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

    setMode(mode) {
        if (this.world && (mode === 'paint' || mode === 'erase')) {
            this.mode = mode;
            this.changed();
        }
    }

    setSize(size) {
        if (this.world && SIZES.includes(size)) {
            this.size = size;
            this.changed();
        }
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

    erasing(shift) {
        return (this.mode === 'erase') !== Boolean(shift);
    }

    hoverAt(point, shift) {
        const block = this.tool === 'points' ? null : this.blockAt(point);
        const x = block ? chunkOf(block.x) : null;
        const z = block ? chunkOf(block.z) : null;
        if (this.hover?.x !== x || this.hover?.z !== z || this.hover?.shift !== shift) {
            this.hover = block ? {x, z, shift} : null;
            this.redraw();
        }
    }

    begin(point, shift) {
        const block = this.blockAt(point);
        if (!block) {
            return;
        }
        this.hover = null;
        if (this.tool === 'points') {
            const before = this.points.map(entry => [...entry]);
            let index = this.pointAt(point);
            if (shift) {
                if (index >= 0) {
                    this.remember({points: before});
                    this.points.splice(index, 1);
                }
            } else {
                const existing = index >= 0;
                if (!existing) {
                    const segment = this.segmentAt(point);
                    index = segment < 0 ? this.points.length : segment + 1;
                    this.points.splice(index, 0, [block.x + .5, block.z + .5]);
                }
                this.stroke = {index, existing, moved: false, before};
            }
            this.changed();
            return;
        }
        this.stroke = {
            erase: this.erasing(shift), anchor: block, reach: block, last: point,
            lasso: [[block.x + .5, block.z + .5]], added: new Map(), removed: new Map()
        };
        if (this.tool === 'brush') {
            this.brush(chunkOf(block.x), chunkOf(block.z));
        }
        this.redraw();
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
                this.brush(chunkOf(block.x), chunkOf(block.z));
            } else if (this.tool === 'lasso') {
                const [lastX, lastZ] = stroke.lasso.at(-1);
                if (Math.abs(lastX - block.x - .5) + Math.abs(lastZ - block.z - .5) >= 1) {
                    stroke.lasso.push([block.x + .5, block.z + .5]);
                }
            }
        }
        stroke.last = point;
        this.redraw();
    }

    finish() {
        const stroke = this.stroke;
        if (!stroke) {
            return;
        }
        this.stroke = null;
        if (stroke.index !== undefined) {
            if (stroke.existing && !stroke.moved) {
                this.points.splice(stroke.index, 1);
            }
            if (JSON.stringify(stroke.before) !== JSON.stringify(this.points)) {
                this.remember({points: stroke.before});
            }
            this.changed();
            return;
        }
        if (this.tool === 'rectangle') {
            const [minX, minZ, maxX, maxZ] = this.box(stroke);
            for (let x = minX; x <= maxX; x++) {
                for (let z = minZ; z <= maxZ; z++) {
                    this.apply(x, z, stroke);
                }
            }
        } else if (this.tool === 'lasso' && stroke.lasso.length >= 3) {
            this.fill(stroke.lasso, stroke.erase, stroke);
        }
        if (stroke.added.size > 0 || stroke.removed.size > 0) {
            this.remember({added: [...stroke.added.values()], removed: [...stroke.removed.values()]});
            this.touched();
        }
        this.changed();
    }

    cancelStroke() {
        const stroke = this.stroke;
        this.stroke = null;
        if (stroke?.added) {
            for (const key of stroke.added.keys()) {
                this.chunks.delete(key);
            }
            for (const [key, chunk] of stroke.removed) {
                this.chunks.set(key, chunk);
            }
        } else if (stroke?.before) {
            this.points = stroke.before;
        }
        this.changed();
    }

    remember(entry) {
        this.history.push(entry);
        if (this.history.length > HISTORY) {
            this.history.shift();
        }
        this.future = [];
    }

    undo() {
        this.step(this.history, this.future);
    }

    redo() {
        this.step(this.future, this.history);
    }

    step(from, to) {
        if (!this.world || this.stroke || from.length === 0) {
            return;
        }
        const entry = from.pop();
        if (entry.points) {
            to.push({points: this.points});
            this.points = entry.points;
        } else {
            for (const [x, z] of entry.added) {
                this.chunks.delete(cell(x, z));
            }
            for (const [x, z] of entry.removed) {
                this.chunks.set(cell(x, z), [x, z]);
            }
            to.push({added: entry.removed, removed: entry.added});
            this.touched();
        }
        this.changed();
    }

    fill(outline, erase, stroke = {erase, added: new Map(), removed: new Map()}) {
        const xs = outline.map(([x]) => x);
        const zs = outline.map(([, z]) => z);
        for (let x = chunkOf(Math.min(...xs)); x <= Math.max(...xs) / CHUNK; x++) {
            for (let z = chunkOf(Math.min(...zs)); z <= Math.max(...zs) / CHUNK; z++) {
                if (inside(outline, x * CHUNK + CHUNK / 2, z * CHUNK + CHUNK / 2)) {
                    this.apply(x, z, stroke);
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

    footprint(x, z) {
        const size = this.tool === 'brush' ? this.size : 1;
        const from = -Math.floor(size / 2);
        const chunks = [];
        for (let dx = from; dx < from + size; dx++) {
            for (let dz = from; dz < from + size; dz++) {
                chunks.push([x + dx, z + dz]);
            }
        }
        return chunks;
    }

    brush(x, z) {
        for (const [chunkX, chunkZ] of this.footprint(x, z)) {
            this.apply(chunkX, chunkZ, this.stroke);
        }
    }

    apply(chunkX, chunkZ, stroke) {
        const key = cell(chunkX, chunkZ);
        if (stroke.erase) {
            if (this.chunks.delete(key)) {
                if (!stroke.added.delete(key)) {
                    stroke.removed.set(key, [chunkX, chunkZ]);
                }
            }
        } else if (this.chunks.size < this.limit() && !this.chunks.has(key) && !this.taken(chunkX, chunkZ)) {
            this.chunks.set(key, [chunkX, chunkZ]);
            if (!stroke.removed.delete(key)) {
                stroke.added.set(key, [chunkX, chunkZ]);
            }
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

    touched() {
        this.version++;
        this.rings = null;
        this.split = null;
        this.loose = [];
    }

    outlines() {
        if (!this.rings) {
            this.rings = trace(this.chunks);
        }
        return this.rings;
    }

    isSplit() {
        if (this.split === null) {
            this.loose = this.kind === 'region' || this.kind === 'area' && this.tool !== 'points'
                ? detached(this.chunks) : [];
            this.split = this.loose.length > 0 || this.kind === 'area' && this.tool !== 'points' && this.outlines().length > 1;
        }
        return this.split;
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
        const key = cell(blockX, blockZ);
        let y = this.grounds.get(key);
        if (y === undefined) {
            const surface = mapManager.surfaceHeightAt(blockX, blockZ);
            if (Number.isFinite(surface)) {
                this.grounds.set(key, surface);
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
            .filter(chunkZ => !selected || selected.has(cell(chunkX, chunkZ)))
            .map(chunkZ => this.chunkLevel(chunkX, chunkZ)));
        return mapManager.project(x, levels.reduce((sum, y) => sum + y, 0) / levels.length + 1, z);
    }

    chunkLevel(chunkX, chunkZ) {
        const key = cell(chunkX, chunkZ);
        const known = this.levels.get(key);
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
        this.levels.set(key, y);
        return y;
    }

    square(chunkX, chunkZ) {
        const [x0, z0, x1, z1] = [chunkX * CHUNK, chunkZ * CHUNK, (chunkX + 1) * CHUNK, (chunkZ + 1) * CHUNK];
        if (!mapManager.isIsometric()) {
            return [[x0, z0], [x1, z0], [x1, z1], [x0, z1]].map(([x, z]) => mapManager.project(x, 0, z));
        }
        const y = this.chunkLevel(chunkX, chunkZ) + 1;
        return [[x0, z0], [x1, z0], [x1, z1], [x0, z1]].map(([x, z]) => mapManager.project(x, y, z));
    }

    squares(chunks, style) {
        return chunks.length === 0 ? null
            : L.polygon(chunks.map(([x, z]) => [this.square(x, z)]), {interactive: false, ...style});
    }

    paintColor() {
        return this.kind === 'chunks' ? LINE : this.color;
    }

    redraw() {
        cancelAnimationFrame(this.frame);
        this.frame = requestAnimationFrame(() => this.draw());
    }

    layer(name, key, build) {
        const drawn = this.drawn.get(name);
        if (drawn && drawn.key === key && key !== null) {
            return;
        }
        drawn?.layer?.remove();
        const missing = this.missing;
        this.missing = false;
        const layer = this.world ? build() : null;
        const incomplete = this.missing;
        this.missing = missing || incomplete;
        layer?.addTo(mapManager.getMap());
        this.drawn.set(name, {key: incomplete ? null : key, layer});
    }

    othersLayer() {
        const group = L.layerGroup();
        for (const other of regionManager.regions) {
            if (other.id === this.editing?.id || !other.chunks) {
                continue;
            }
            const owned = new Map(other.chunks.map(([x, z]) => [cell(x, z), [x, z]]));
            const color = other.color ?? LINE;
            group.addLayer(L.polygon(trace(owned).map(ring => corners(ring).map(([x, z]) => this.level(x, z, owned))), {
                color, opacity: .9, weight: 1.5, fillColor: color, fillOpacity: .22, interactive: false
            }));
            if (other.label) {
                const [x, z] = other.label;
                group.addLayer(L.marker(this.level(x, z, owned), {
                    interactive: false, keyboard: false,
                    icon: L.divIcon({
                        className: 'region-name-icon',
                        html: element('span', 'region-name', other.name || t('regions.unnamed')),
                        iconSize: null
                    })
                }));
            }
        }
        return group;
    }

    baseLayer() {
        if (this.chunks.size === 0 && !this.stroke) {
            return null;
        }
        const chunks = this.stroke ? this.strokeBase() : this.chunks;
        if (chunks.size === 0) {
            return null;
        }
        const color = this.paintColor();
        const marker = this.kind === 'area' || this.kind === 'line';
        const rings = this.stroke ? trace(chunks) : this.outlines();
        return L.polygon(rings.map(ring => this.draped(corners(ring))?.flat() ?? ring.map(([x, z]) => this.level(x, z, chunks))), {
            color, opacity: marker ? 1 : .9, weight: 2, fillColor: color, fillOpacity: .3, interactive: false
        });
    }

    strokeBase() {
        if (!this.stroke.base) {
            const base = new Map(this.chunks);
            for (const key of this.stroke.added.keys()) {
                base.delete(key);
            }
            for (const [key, chunk] of this.stroke.removed) {
                base.set(key, chunk);
            }
            this.stroke.base = base;
        }
        return this.stroke.base;
    }

    strokeLayer() {
        const stroke = this.stroke;
        if (!stroke?.added) {
            return null;
        }
        const color = this.paintColor();
        return L.layerGroup([
            this.squares([...stroke.added.values()], {stroke: false, fillColor: color, fillOpacity: .45}),
            this.squares([...stroke.removed.values()], {color: ERASE, weight: 1, fillColor: '#000', fillOpacity: .45})
        ].filter(Boolean));
    }

    previewLayer() {
        const outline = this.outline();
        if (outline && outline.length > 1) {
            return L.polygon(outline.map(([x, z]) => this.level(x, z)), {
                color: this.stroke.erase ? ERASE : LINE, weight: 2, fill: false, interactive: false
            });
        }
        const hover = this.hover;
        if (!hover || this.stroke || this.tool === 'points') {
            return null;
        }
        const erase = this.erasing(hover.shift);
        const footprint = this.footprint(hover.x, hover.z);
        const taken = erase ? [] : footprint.filter(([x, z]) => this.taken(x, z));
        const free = erase ? footprint : footprint.filter(([x, z]) => !this.taken(x, z));
        return L.layerGroup([
            this.squares(free, {color: erase ? ERASE : LINE, weight: 2, fill: erase, fillColor: '#000', fillOpacity: .25}),
            this.squares(taken, {color: ERASE, weight: 1, fillColor: ERASE, fillOpacity: .35, dashArray: '3 3'})
        ].filter(Boolean));
    }

    pointsLayer() {
        if (this.points.length === 0) {
            return null;
        }
        const color = this.paintColor();
        const group = L.layerGroup();
        if (this.kind === 'line') {
            const path = this.smooth && this.points.length > 2 ? spline(this.points) : this.points;
            group.addLayer(L.polyline(path.map(([x, z]) => this.ground(x, z)), {color, weight: 3, interactive: false}));
        } else {
            group.addLayer(L.polygon(this.draped(this.points)?.flat() ?? this.points.map(([x, z]) => this.ground(x, z)), {
                color, weight: 2, fillColor: color, fillOpacity: .3, interactive: false
            }));
        }
        for (const handle of this.handles()) {
            group.addLayer(L.circleMarker(handle, {
                radius: 4, color: '#000', weight: 1, fillColor: LINE, fillOpacity: 1, interactive: false
            }));
        }
        return group;
    }

    draw() {
        const view = `${mapManager.isIsometric()}|${mapManager.rotation}`;
        if (view !== this.view || !this.world) {
            this.view = view;
            for (const {layer} of this.drawn.values()) {
                layer?.remove();
            }
            this.drawn.clear();
        }
        this.missing = false;
        if (!this.world) {
            return;
        }
        const region = this.kind === 'region';
        const strokeKey = this.stroke?.added ? `${this.version}|${this.stroke.added.size}|${this.stroke.removed.size}` : null;
        this.layer('others', region ? `${regionManager.version}|${this.editing?.id}` : 'none',
            () => region ? this.othersLayer() : null);
        this.layer('base', `${this.version}|${this.color}|${Boolean(this.stroke?.added)}`, () => this.baseLayer());
        this.layer('stroke', strokeKey ?? 'none', () => this.strokeLayer());
        const loose = !this.stroke && this.chunks.size > 0 && this.isSplit() ? this.loose : [];
        this.layer('loose', `${this.version}|${loose.length}`,
            () => this.squares(loose, {color: ERASE, weight: 2, fillColor: ERASE, fillOpacity: .5}));
        this.layer('preview', Symbol(), () => this.previewLayer());
        this.layer('points', Symbol(), () => this.pointsLayer());
        if (this.missing) {
            clearTimeout(this.retry);
            this.retry = setTimeout(() => this.redraw(), 300);
        }
        this.panel();
    }

    panel() {
        const region = this.kind === 'region';
        const points = this.tool === 'points';
        const split = !this.stroke && !points && this.chunks.size > 0 && this.isSplit();
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
        $('selection-brush').hidden = this.kind === 'line';
        $('selection-mode').hidden = points;
        $('selection-size').hidden = this.tool !== 'brush';
        for (const button of $('selection-mode').querySelectorAll('[data-mode]')) {
            button.setAttribute('aria-pressed', String(button.dataset.mode === this.mode));
        }
        for (const button of $('selection-size').querySelectorAll('[data-size]')) {
            button.setAttribute('aria-pressed', String(Number(button.dataset.size) === this.size));
        }
        $('selection-undo').disabled = this.history.length === 0;
        $('selection-redo').disabled = this.future.length === 0;
        $('selection-smooth').setAttribute('aria-pressed', String(this.smooth));
        $('selection-smooth').textContent = t(this.smooth ? 'selection.smooth.on' : 'selection.smooth.off');
        $('selection-save').disabled = this.sending || (region ? this.chunks.size === 0 || split : !this.shape());
        $('selection').classList.toggle('is-failed', split || full || (this.status !== null && this.failed));
        $('selection').classList.toggle('is-done', !split && !full && this.status !== null && !this.failed);
    }

    hints(points) {
        const erase = this.mode === 'erase';
        const pairs = points
            ? TOUCH.matches ? [['tap', 'add'], ['drag', 'move'], ['tapPoint', 'remove'], ['twoFingers', 'panZoom']]
                : [['click', 'add'], ['drag', 'move'], ['clickPoint', 'remove'], ['backspace', 'undo'], ['rightDrag', 'pan']]
            : TOUCH.matches ? [['drag', erase ? 'erase' : 'paint'], ['twoFingers', 'panZoom']]
                : [['drag', erase ? 'erase' : 'paint'], ['shift', erase ? 'paint' : 'erase'], ['rightDrag', 'pan']];
        if (!TOUCH.matches) {
            pairs.push(['undoKey', 'history']);
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
            this.history = [];
            this.future = [];
            this.touched();
        }
        this.show(t(response?.status === 202 ? `selection.started.${action}`
            : response?.status === 409 ? 'selection.busy' : 'selection.failed'), response?.status !== 202);
    }

    openRegion() {
        if (this.chunks.size === 0 || this.isSplit()) {
            return;
        }
        regionEditor.open({
            region: this.editing, color: this.color, size: this.chunks.size,
            onColor: (color) => {
                this.color = color;
                this.redraw();
            },
            onSave: (fields) => this.saveRegion(fields),
            onDelete: () => this.deleteRegion()
        });
    }

    async saveRegion(fields) {
        if (this.sending || this.chunks.size === 0 || this.isSplit()) {
            return 'failed';
        }
        const world = this.world;
        this.setSending(true);
        let response = null;
        try {
            response = await fetch('/api/regions', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({world, id: this.editing?.id ?? null, ...fields, chunks: [...this.chunks.values()]})
            });
        } catch {
        }
        this.setSending(false);
        if (world !== this.world) {
            return 'failed';
        }
        if (response?.ok) {
            const {id} = await response.json();
            this.editing = {...this.editing, ...fields, id};
            this.color = fields.color;
            $('selection-delete').hidden = false;
            this.label($('selection-title'), 'selection.title.regionEdit');
            this.show(t(response.status === 202 ? 'region.started' : 'region.saved'), false);
            return null;
        }
        return response ? (await response.json().catch(() => null))?.problem ?? 'failed' : 'failed';
    }

    async deleteRegion() {
        let response = null;
        try {
            response = await fetch(`/api/regions/${this.editing.id}`, {method: 'DELETE'});
        } catch {
        }
        if (response?.ok) {
            this.stop();
            return null;
        }
        return response ? (await response.json().catch(() => null))?.problem ?? 'failed' : 'failed';
    }

    save() {
        if (this.kind === 'region') {
            this.openRegion();
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
        if (!armed(button, t('waypoint.confirmDelete'))) {
            return;
        }
        const world = this.world;
        if (this.kind === 'region') {
            const problem = await this.deleteRegion();
            if (problem) {
                disarm(button);
                this.show(t(`region.problem.${problem}`), true);
            }
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
        disarm(button);
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
