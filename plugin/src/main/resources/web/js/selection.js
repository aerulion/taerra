import {accountManager} from './account.js';
import {numbers, t} from './i18n.js';
import {mapManager} from './map.js';

const $ = (id) => document.getElementById(id);

const CHUNK = 16;
const MAX_CHUNKS = 16384;
const STEP_PIXELS = 4;
const STATUS_MILLIS = 4000;
const UNKNOWN_GROUND = 63;
const LINE = '#ffffff';
const ERASE_LINE = '#ff5555';

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
const chunkOf = (block) => Math.floor(block / CHUNK);

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
        this.tool = 'brush';
        this.chunks = new Map();
        this.rings = [];
        this.traced = false;
        this.grounds = new Map();
        this.stroke = null;
        this.pan = null;
        this.status = null;
        this.failed = false;
        this.sending = false;
        this.layers = [];
        this.missing = false;
        this.frame = 0;
        this.retry = 0;
    }

    initialize() {
        const map = mapManager.getMap();
        const container = map.getContainer();
        const ignored = (event) => event.target.closest('.leaflet-control, .leaflet-popup');
        container.addEventListener('pointerdown', (event) => {
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
            if (event.key === 'Escape' && this.world) {
                this.stop();
            }
        });
        for (const button of $('selection').querySelectorAll('[data-tool]')) {
            button.addEventListener('click', () => this.setTool(button.dataset.tool));
        }
        for (const button of $('selection').querySelectorAll('[data-action]')) {
            button.addEventListener('click', () => void this.act(button.dataset.action));
        }
        map.on('zoomend', () => this.redraw());
    }

    start(world, block) {
        const map = mapManager.getMap();
        this.world = world;
        this.chunks.clear();
        this.grounds.clear();
        map.dragging.disable();
        map.boxZoom.disable();
        $('selection-render').hidden = !accountManager.canRender();
        $('selection-hide').hidden = !accountManager.canHide();
        $('selection-reveal').hidden = !accountManager.canHide();
        $('selection').hidden = false;
        this.apply(chunkOf(block.x), chunkOf(block.z), false);
        this.setTool(this.tool);
        this.changed();
    }

    stop() {
        const map = mapManager.getMap();
        this.world = null;
        this.stroke = null;
        this.pan = null;
        this.chunks.clear();
        map.dragging.enable();
        map.boxZoom.enable();
        $('selection').hidden = true;
        this.redraw();
    }

    setTool(tool) {
        this.tool = tool;
        for (const button of $('selection').querySelectorAll('[data-tool]')) {
            button.setAttribute('aria-pressed', String(button.dataset.tool === tool));
        }
    }

    blockAt(point) {
        const block = mapManager.latLngToBlock(mapManager.getMap().containerPointToLatLng(point));
        return Number.isFinite(block.x) && Number.isFinite(block.z) ? block : null;
    }

    begin(point, shift) {
        const block = this.blockAt(point);
        if (!block) {
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
        if (this.tool === 'rectangle') {
            const [minX, minZ, maxX, maxZ] = this.box(stroke);
            for (let x = minX; x <= maxX; x++) {
                for (let z = minZ; z <= maxZ; z++) {
                    this.apply(x, z, stroke.erase);
                }
            }
        } else if (this.tool === 'lasso' && stroke.lasso.length >= 3) {
            const xs = stroke.lasso.map(([x]) => x);
            const zs = stroke.lasso.map(([, z]) => z);
            for (let x = chunkOf(Math.min(...xs)); x <= Math.max(...xs) / CHUNK; x++) {
                for (let z = chunkOf(Math.min(...zs)); z <= Math.max(...zs) / CHUNK; z++) {
                    if (inside(stroke.lasso, x * CHUNK + CHUNK / 2, z * CHUNK + CHUNK / 2)) {
                        this.apply(x, z, stroke.erase);
                    }
                }
            }
        }
        this.changed();
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
        } else if (this.chunks.size < MAX_CHUNKS && !this.chunks.has(key(chunkX, chunkZ))) {
            this.chunks.set(key(chunkX, chunkZ), [chunkX, chunkZ]);
            this.traced = false;
        }
    }

    changed() {
        this.status = null;
        this.failed = false;
        this.redraw();
    }

    outline() {
        const stroke = this.stroke;
        if (!stroke || this.tool === 'brush') {
            return null;
        }
        if (this.tool === 'lasso') {
            return stroke.lasso;
        }
        const [minX, minZ, maxX, maxZ] = this.box(stroke);
        const [x0, z0, x1, z1] = [minX * CHUNK, minZ * CHUNK, (maxX + 1) * CHUNK, (maxZ + 1) * CHUNK];
        return [[x0, z0], [x1, z0], [x1, z1], [x0, z1]];
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

    redraw() {
        cancelAnimationFrame(this.frame);
        this.frame = requestAnimationFrame(() => this.draw());
    }

    draw() {
        const map = mapManager.getMap();
        for (const layer of this.layers) {
            layer.remove();
        }
        this.layers = [];
        this.missing = false;
        if (this.chunks.size > 0) {
            if (!this.traced) {
                this.rings = trace(this.chunks);
                this.traced = true;
            }
            this.layers.push(L.polygon(this.rings.map(ring => ring.map(([x, z]) => this.ground(x, z))), {
                color: LINE, opacity: .75, weight: 1, fillColor: LINE, fillOpacity: .25, interactive: false
            }).addTo(map));
        }
        const outline = this.outline();
        if (outline && outline.length > 1) {
            this.layers.push(L.polygon(outline.map(([x, z]) => this.ground(x, z)), {
                color: this.stroke.erase ? ERASE_LINE : LINE, weight: 2, fill: false, interactive: false
            }).addTo(map));
        }
        if (this.missing) {
            clearTimeout(this.retry);
            this.retry = setTimeout(() => this.redraw(), 300);
        }
        $('selection-count').textContent = numbers.format(this.chunks.size);
        $('selection-text').textContent = this.status ?? t('selection.hint');
        $('selection').classList.toggle('is-failed', this.status !== null && this.failed);
        $('selection').classList.toggle('is-done', this.status !== null && !this.failed);
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
