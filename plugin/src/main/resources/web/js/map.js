import {t} from './i18n.js';

export const pixelPath = (list) => list.map(([x, y, w, h]) => `M${x} ${y}h${w}v${h}h${-w}z`).join('');

const NEEDLE_SVG = `<svg class="compass__needle" viewBox="0 0 7 15" width="14" height="30" aria-hidden="true">
<path d="${pixelPath([[3, 0, 1, 2], [2, 2, 2, 2], [1, 4, 3, 3]])}" fill="#e34a4a"/>
<path d="${pixelPath([[4, 2, 1, 2], [4, 4, 2, 3]])}" fill="#a32c2c"/>
<path d="${pixelPath([[1, 7, 3, 4], [2, 11, 2, 2], [3, 13, 1, 2]])}" fill="#d0d1d4"/>
<path d="${pixelPath([[4, 7, 2, 4], [4, 11, 1, 2]])}" fill="#8c8d90"/>
<path d="${pixelPath([[3, 7, 1, 1]])}" fill="#1e1e1f"/></svg>`;

class MapManager {
    static LEAFLET_TILE_SIZE = 256;
    static DEFAULT_TILE_BLOCK_SIZE = 64;
    static DEFAULT_MAX_ZOOM = 4;
    static ROTATIONS = 4;
    static ISO_REFERENCE_Y = 64;
    static PICK_CACHE_LIMIT = 48;

    constructor() {
        this.map = null;
        this.currentWorld = null;
        this.tileLayer = null;
        this.tileBlockSize = MapManager.DEFAULT_TILE_BLOCK_SIZE;
        this.maxZoom = MapManager.DEFAULT_MAX_ZOOM;
        this.mapUnitsPerBlock = this.computeMapUnitsPerBlock();
        this.viewMode = 'top';
        this.isometric = {
            maxZoom: MapManager.DEFAULT_MAX_ZOOM,
            blockWidth: 8,
            tileSize: 256
        };
        this.rotation = 0;
        this.depth = {top_down: MapManager.DEFAULT_MAX_ZOOM, isometric: []};
        this.bounds = null;
        this.placing = false;
        this.pickCache = new Map();
        this.pickPending = new Map();
        this.epoch = Date.now();
        this.tileVersions = new Map();
        this.revealed = new Set();
    }

    static rotateCell(rotation, x, z) {
        switch (rotation) {
            case 1:
                return [z, -x - 1];
            case 2:
                return [-x - 1, -z - 1];
            case 3:
                return [-z - 1, x];
            default:
                return [x, z];
        }
    }

    static unrotateCell(rotation, rotatedX, rotatedZ) {
        switch (rotation) {
            case 1:
                return [-rotatedZ - 1, rotatedX];
            case 2:
                return [-rotatedX - 1, -rotatedZ - 1];
            case 3:
                return [rotatedZ, -rotatedX - 1];
            default:
                return [rotatedX, rotatedZ];
        }
    }

    static versionKey(view, rotation, zoom, x, y) {
        return `${view}:${view === 'isometric' ? rotation : 0}:${zoom}:${x}:${y}`;
    }

    static async decodePick(raw) {
        if (raw.length < 18 || raw[0] !== 0x50 || raw[1] !== 0x4D || raw[2] !== 0x50 || raw[3] !== 0x4B ||
            raw[4] !== 3) {
            return null;
        }
        const header = new DataView(raw.buffer, raw.byteOffset, 18);
        const size = header.getUint16(5);
        const scale = raw[15];
        const depthPerHeight = raw[16];
        const width = raw[17];
        const stored = size / scale;
        const cells = stored * stored;
        const maskBytes = Math.ceil(cells / 8);
        const stream = new Blob([raw.subarray(18)]).stream().pipeThrough(new DecompressionStream('deflate'));
        const body = new Uint8Array(await new Response(stream).arrayBuffer());
        if (body.length < maskBytes + cells * width * 2) {
            return null;
        }
        const read = offset => width === 1 ? body[offset] : body[offset] << 8 | body[offset + 1];
        const wrap = width === 1 ? 0xFF : 0xFFFF;
        const originDepth = header.getInt32(7);
        const originHeight = header.getInt32(11);
        const depths = new Int32Array(cells);
        const heights = new Int32Array(cells);
        const empty = new Uint8Array(cells);
        let height = 0;
        for (let cell = 0; cell < cells; cell++) {
            depths[cell] = ((cell >= stored ? depths[cell - stored] : 0) + read(maskBytes + cell * width)) & wrap;
            height = ((cell % stored > 0 ? height : 0) + read(maskBytes + (cells + cell) * width)) & wrap;
            heights[cell] = height;
        }
        for (let cell = 0; cell < cells; cell++) {
            heights[cell] += originHeight;
            depths[cell] += originDepth + depthPerHeight * heights[cell];
            empty[cell] = body[cell >> 3] & (0x80 >>> (cell & 7));
        }
        return {size, scale, stored, depths, heights, empty};
    }

    static pickCell(buffer, localX, localY) {
        const index = Math.floor(localY / buffer.scale) * buffer.stored + Math.floor(localX / buffer.scale);
        if (buffer.empty[index]) {
            return null;
        }
        return {depth: buffer.depths[index], height: buffer.heights[index]};
    }

    screenBearing(yaw) {
        const radians = yaw * Math.PI / 180;
        const dx = -Math.sin(radians);
        const dz = Math.cos(radians);
        let screenX = dx;
        let screenY = dz;
        if (this.isIsometric()) {
            const [rx, rz] = [[dx, dz], [dz, -dx], [-dx, -dz], [-dz, dx]][this.rotation] ?? [dx, dz];
            screenX = rx - rz;
            screenY = (rx + rz) / 2;
        }
        return Math.atan2(screenX, -screenY) * 180 / Math.PI;
    }

    computeMapUnitsPerBlock() {
        return (MapManager.LEAFLET_TILE_SIZE / Math.pow(2, this.maxZoom)) / this.tileBlockSize;
    }

    async loadGeometry() {
        try {
            const response = await fetch('/api/config');
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }
            const geometry = await response.json();
            if (Number.isInteger(geometry.tileSize) && Number.isInteger(geometry.maxZoom)) {
                this.tileBlockSize = geometry.tileSize;
                this.maxZoom = geometry.maxZoom;
            }
            const iso = geometry.isometric;
            if (iso && Number.isInteger(iso.maxZoom) && Number.isInteger(iso.blockWidth)) {
                this.isometric = {
                    maxZoom: iso.maxZoom,
                    blockWidth: iso.blockWidth,
                    tileSize: Number.isInteger(iso.tileSize) ? iso.tileSize : 256
                };
            }
        } catch (error) {
            console.warn('Falling back to default tile geometry:', error);
        }
        this.mapUnitsPerBlock = this.computeMapUnitsPerBlock();
    }

    async initialize() {
        await this.loadGeometry();

        this.map = L.map('map', {
            renderer: L.canvas({tolerance: 6}),
            crs: L.CRS.Simple,
            minZoom: 0,
            maxZoom: Math.max(this.maxZoom, this.isometric.maxZoom),
            zoom: 2,
            center: [0, 0],
            zoomControl: false,
            attributionControl: false,
            zoomSnap: 1
        });
        L.control.zoom({
            position: 'topright',
            zoomInTitle: t('map.zoomIn'),
            zoomOutTitle: t('map.zoomOut')
        }).addTo(this.map);
        const compass = L.DomUtil.create('button', 'compass');
        compass.type = 'button';
        compass.id = 'compass';
        compass.innerHTML = NEEDLE_SVG;
        L.DomEvent.disableClickPropagation(compass);
        this.needle = compass.firstChild;
        new (L.Control.extend({onAdd: () => compass}))({position: 'topright'}).addTo(this.map);
        this.updateCompass();

        let pending = null;
        this.map.on('mousemove', (e) => {
            if (pending === null) {
                requestAnimationFrame(() => {
                    this.updatePointer(pending);
                    pending = null;
                });
            }
            pending = e;
        });
        this.map.on('mouseout', () => this.forgetPointer());
        this.map.on('moveend resize', () => this.keepInBounds());

    }

    isIsometric() {
        return this.viewMode === 'iso';
    }

    activeMaxZoom() {
        return this.isIsometric() ? this.isometric.maxZoom : this.maxZoom;
    }

    minZoomFor(mode) {
        return mode === 'iso'
            ? this.isometric.maxZoom - (this.depth.isometric[this.rotation] ?? 0)
            : this.maxZoom - this.depth.top_down;
    }

    activeMinZoom() {
        return this.minZoomFor(this.viewMode);
    }

    setDepth(depth) {
        if (!depth || JSON.stringify(depth) === JSON.stringify(this.depth)) {
            return;
        }
        this.depth = depth;
        if (this.tileLayer) {
            this.tileLayer.options.minZoom = this.activeMinZoom();
            this.map.setMinZoom(this.activeMinZoom());
        }
    }

    setBounds(bounds) {
        this.bounds = bounds ?? null;
        this.keepInBounds();
    }

    keepInBounds() {
        if (!this.bounds || this.placing) {
            return;
        }
        const {minX, minZ, maxX, maxZ} = this.bounds;
        const zoom = this.map.getZoom();
        const area = L.bounds([[minX, minZ], [minX, maxZ + 1], [maxX + 1, minZ], [maxX + 1, maxZ + 1]]
            .map(([x, z]) => this.map.project(this.blockToLatLng(x, z), zoom)));
        const half = this.map.getSize().divideBy(2);
        this.map.panInsideBounds(L.latLngBounds(
            this.map.unproject(area.min.subtract(half), zoom),
            this.map.unproject(area.max.add(half), zoom)));
    }

    viewZoomOffset() {
        if (this.mapUnitsPerBlock <= 0) {
            return 0;
        }
        const isoBlockUnits = this.isometric.blockWidth * this.isoMapUnitsPerPixel();
        return Math.round(Math.log2(isoBlockUnits / this.mapUnitsPerBlock));
    }

    matchingZoom(mode) {
        if (!this.map) {
            return undefined;
        }
        const offset = this.viewZoomOffset();
        const target = this.map.getZoom() + (mode === 'iso' ? -offset : offset);
        return Math.max(this.minZoomFor(mode), Math.min(mode === 'iso' ? this.isometric.maxZoom : this.maxZoom, target));
    }

    getRotation() {
        return this.rotation;
    }

    tileVersion(zoom, x, y) {
        const view = this.isIsometric() ? 'isometric' : 'top_down';
        return this.tileVersions.get(MapManager.versionKey(view, this.rotation, zoom, x, y)) ?? this.epoch;
    }

    newEpoch() {
        this.epoch = Date.now();
        this.tileVersions.clear();
    }

    tileUrl(zoom, x, y) {
        const world = encodeURIComponent(this.currentWorld);
        const suffix = `?v=${this.tileVersion(zoom, x, y)}`;
        if (this.isIsometric()) {
            return `/api/iso/${world}/${this.rotation}/${zoom}/${x}/${y}.png${suffix}`;
        }
        return `/api/tiles/${world}/${zoom}/${x}/${y}.png${suffix}`;
    }

    rebuildTileLayer() {
        if (!this.currentWorld) {
            return;
        }

        if (this.tileLayer) {
            this.map.removeLayer(this.tileLayer);
        }

        const manager = this;
        const Layered = L.GridLayer.extend({
            createTile(coords, done) {
                const tile = document.createElement('div');
                void manager.composeTile(tile, coords.z, coords.x, coords.y).then(() => done(null, tile), error => done(error, tile));
                return tile;
            }
        });
        this.tileLayer = new Layered({
            tileSize: MapManager.LEAFLET_TILE_SIZE,
            minZoom: this.activeMinZoom(),
            maxZoom: this.activeMaxZoom(),
            noWrap: true
        });

        this.tileLayer.addTo(this.map);
        this.map.setMinZoom(this.activeMinZoom());
    }

    setViewMode(mode) {
        const next = mode === 'iso' ? 'iso' : 'top';
        if (next === this.viewMode) {
            return;
        }

        const anchor = this.map ? this.latLngToBlock(this.map.getCenter()) : null;
        const zoom = this.matchingZoom(next);
        this.viewMode = next;
        this.rebuildTileLayer();
        if (anchor) {
            this.centerOnBlock(anchor.x, anchor.z, zoom, anchor.y);
        }
        this.forgetPointer();
        this.updateCompass();
        return this.viewMode;
    }

    setRotation(rotation) {
        const next = ((rotation % MapManager.ROTATIONS) + MapManager.ROTATIONS) % MapManager.ROTATIONS;
        if (!this.isIsometric() || next === this.rotation) {
            return this.rotation;
        }

        const anchor = this.map ? this.latLngToBlock(this.map.getCenter()) : null;
        this.rotation = next;
        this.pickCache.clear();
        this.rebuildTileLayer();
        if (anchor) {
            this.centerOnBlock(anchor.x, anchor.z, undefined, anchor.y);
        }
        this.forgetPointer();
        this.updateCompass();
        return this.rotation;
    }

    updateCompass() {
        this.needle.style.rotate = `${this.screenBearing(180)}deg`;
    }

    setWorld(world) {
        this.currentWorld = world.name;
        this.pickCache.clear();
        this.newEpoch();
        this.map.getContainer().dataset.environment = world.environment ?? '';
        this.depth = world.depth ?? this.depth;
        this.bounds = world.bounds ?? null;

        this.rebuildTileLayer();

        if (world.spawn && world.spawn.x !== undefined && world.spawn.z !== undefined) {
            this.centerOnBlock(world.spawn.x, world.spawn.z, Math.max(this.map.getZoom(), 2));
        }
    }

    setRevealed(ids) {
        const next = new Set(ids);
        if (next.size === this.revealed.size && [...next].every(id => this.revealed.has(id))) {
            return;
        }
        this.revealed = next;
        this.refreshAllTiles();
    }

    wantedLayers(header) {
        return (header ?? '').split(',').filter(id => {
            const region = Number(id.slice(1));
            return id[0] === 'b' ? this.revealed.has(region) : id[0] === 's' && !this.revealed.has(region);
        }).sort((a, b) => a[0] === b[0] ? 0 : a[0] === 'b' ? -1 : 1);
    }

    async composeTile(tile, zoom, x, y) {
        const url = this.tileUrl(zoom, x, y);
        const response = await fetch(url);
        if (!response.ok) {
            tile.replaceChildren();
            return;
        }
        const layers = this.wantedLayers(response.headers.get('X-Taerra-Layers'));
        const images = [url, ...layers.map(id => `${url}&layer=${id}`)].map(src => {
            const image = new Image();
            image.alt = '';
            image.src = src;
            return image;
        });
        await Promise.all(images.map(image => image.decode().catch(() => image.remove())));
        tile.replaceChildren(...images.filter(image => image.complete && image.naturalWidth > 0));
    }

    recompose(tile) {
        const {x, y, z} = tile.coords;
        const next = document.createElement('div');
        void this.composeTile(next, z, x, y).then(() => tile.el.replaceChildren(...next.childNodes)).catch(() => {
        });
    }

    refreshTiles(tiles) {
        if (!this.tileLayer || !Array.isArray(tiles) || tiles.length === 0) {
            return;
        }

        const version = Date.now();
        const loaded = this.tileLayer._tiles;
        for (const entry of tiles) {
            if (Array.isArray(entry) && entry.length >= 3) {
                const [zoom, x, z, view, rotation] = entry;
                this.tileVersions.set(MapManager.versionKey(view ?? 'top_down', rotation ?? 0, zoom, x, z), version);
            }
        }

        const activeView = this.isIsometric() ? 'isometric' : 'top_down';

        for (const entry of tiles) {
            if (!Array.isArray(entry) || entry.length < 3) {
                continue;
            }

            const [zoom, x, z, view, rotation] = entry;
            if ((view ?? 'top_down') !== activeView) {
                continue;
            }
            if (this.isIsometric() && (rotation ?? 0) !== this.rotation) {
                continue;
            }
            if (this.isIsometric() && zoom === this.isometric.maxZoom) {
                this.pickCache.delete(this.pickKey(x, z));
            }

            const tile = loaded[`${x}:${z}:${zoom}`];
            if (!tile || !tile.el || !tile.coords) {
                continue;
            }

            this.recompose(tile);
        }
    }

    refreshAllTiles() {
        if (!this.tileLayer) {
            return;
        }

        this.pickCache.clear();
        this.newEpoch();

        for (const key of Object.keys(this.tileLayer._tiles)) {
            const tile = this.tileLayer._tiles[key];
            if (!tile || !tile.el || !tile.coords) {
                continue;
            }

            this.recompose(tile);
        }
    }

    isoMapUnitsPerPixel() {
        return 1 / Math.pow(2, this.isometric.maxZoom);
    }

    pickKey(tileX, tileY) {
        return `iso:${this.currentWorld}:${this.rotation}:${tileX}:${tileY}`;
    }

    cachedPick(key, url) {
        const buffer = this.pickCache.get(key);
        if (buffer === undefined) {
            void this.requestPick(key, url);
        }
        return buffer ?? null;
    }

    topPick(x, z) {
        const size = this.tileBlockSize;
        const tileX = Math.floor(x / size);
        const tileZ = Math.floor(z / size);
        return {
            key: `top:${this.currentWorld}:${tileX}:${tileZ}`,
            url: `/api/tiles/${encodeURIComponent(this.currentWorld)}/${tileX}/${tileZ}.pick`,
            height: buffer => buffer ? MapManager.pickCell(buffer, x - tileX * size, z - tileZ * size)?.height : undefined
        };
    }

    surfaceHeightAt(x, z) {
        const pick = this.topPick(x, z);
        return pick.height(this.cachedPick(pick.key, pick.url));
    }

    async loadPick(pick) {
        return this.pickCache.has(pick.key) ? this.pickCache.get(pick.key) : this.requestPick(pick.key, pick.url);
    }

    rememberPick(key, buffer) {
        if (this.pickCache.size >= MapManager.PICK_CACHE_LIMIT) {
            this.pickCache.delete(this.pickCache.keys().next().value);
        }
        this.pickCache.set(key, buffer);
    }

    requestPick(key, url) {
        let pending = this.pickPending.get(key);
        if (!pending) {
            pending = this.fetchPick(url)
                .catch(() => null)
                .then(buffer => {
                    this.pickPending.delete(key);
                    this.rememberPick(key, buffer);
                    if (this.lastPointer) {
                        this.updatePointer(this.lastPointer);
                    }
                    return buffer;
                });
            this.pickPending.set(key, pending);
        }
        return pending;
    }

    async fetchPick(url) {
        const response = await fetch(url);
        if (!response.ok) {
            return null;
        }
        const base = await MapManager.decodePick(new Uint8Array(await response.arrayBuffer()));
        const ids = this.wantedLayers(response.headers.get('X-Taerra-Layers')).filter(id => id[0] === 'b');
        const bodies = await Promise.all(ids.map(async id => {
            const layer = await fetch(`${url}?layer=${id}`);
            return layer.ok ? MapManager.decodePick(new Uint8Array(await layer.arrayBuffer())) : null;
        }));
        for (const body of bodies) {
            if (base && body && body.stored === base.stored) {
                for (let cell = 0; cell < base.empty.length; cell++) {
                    if (!body.empty[cell]) {
                        base.empty[cell] = 0;
                        base.depths[cell] = body.depths[cell];
                        base.heights[cell] = body.heights[cell];
                    }
                }
            }
        }
        return base;
    }

    pickAt(screenX, screenY) {
        if (!this.currentWorld) {
            return null;
        }
        const size = this.isometric.tileSize;
        const tileX = Math.floor(screenX / size);
        const tileY = Math.floor(screenY / size);
        const buffer = this.cachedPick(this.pickKey(tileX, tileY),
            `/api/iso/${encodeURIComponent(this.currentWorld)}/${this.rotation}/${tileX}/${tileY}.pick`);
        const cell = buffer && MapManager.pickCell(buffer, screenX - tileX * size, screenY - tileY * size);
        if (!cell) {
            return null;
        }

        const depth = cell.depth;
        const half = this.isometric.blockWidth / 2;
        const higher = Math.floor((screenX + half) / half);
        const difference = ((higher ^ depth) & 1) === 0 ? higher : higher - 1;
        const [x, z] = MapManager.unrotateCell(this.rotation, (depth + difference) / 2, (depth - difference) / 2);
        return {x, y: cell.height, z};
    }

    blockToLatLng(x, z, y) {
        if (this.isIsometric()) {
            const half = this.isometric.blockWidth / 2;
            const quarter = this.isometric.blockWidth / 4;
            const height = Number.isFinite(y) ? y : MapManager.ISO_REFERENCE_Y;
            const units = this.isoMapUnitsPerPixel();
            const [rotatedX, rotatedZ] = MapManager.rotateCell(this.rotation, x, z);
            const screenX = (rotatedX - rotatedZ) * half;
            const screenY = (rotatedX + rotatedZ) * quarter - height * half;
            return L.latLng(-screenY * units, screenX * units);
        }

        const scale = this.mapUnitsPerBlock;
        return L.latLng(-z * scale, x * scale);
    }

    project(x, y, z) {
        if (!this.isIsometric()) {
            return L.latLng(-z * this.mapUnitsPerBlock, x * this.mapUnitsPerBlock);
        }
        let across;
        let down;
        switch (this.rotation) {
            case 1:
                across = z + x;
                down = z - x;
                break;
            case 2:
                across = z - x;
                down = -x - z;
                break;
            case 3:
                across = -z - x;
                down = x - z;
                break;
            default:
                across = x - z;
                down = x + z;
        }
        const half = this.isometric.blockWidth / 2;
        const units = this.isoMapUnitsPerPixel();
        return L.latLng(-(down * half / 2 - (y - 1) * half) * units, across * half * units);
    }

    latLngToBlock(latLng) {
        if (this.isIsometric()) {
            const half = this.isometric.blockWidth / 2;
            const quarter = this.isometric.blockWidth / 4;
            const units = this.isoMapUnitsPerPixel();
            const screenX = latLng.lng / units;
            const screenY = -latLng.lat / units;

            const picked = this.pickAt(Math.floor(screenX), Math.floor(screenY));
            if (picked) {
                return picked;
            }

            const difference = screenX / half;
            const sum = (screenY + MapManager.ISO_REFERENCE_Y * half) / quarter;
            const [x, z] = MapManager.unrotateCell(this.rotation, Math.round((sum + difference) / 2),
                Math.round((sum - difference) / 2));
            return {x, z};
        }

        const scale = 1 / this.mapUnitsPerBlock;
        const x = Math.floor(latLng.lng * scale);
        const z = Math.floor(-latLng.lat * scale);
        return {x, y: this.currentWorld ? this.surfaceHeightAt(x, z) : undefined, z};
    }

    centerOnBlock(x, z, zoom, y) {
        this.placing = true;
        this.map.setView(this.blockToLatLng(x, z, y), zoom ?? this.map.getZoom(), {animate: false});
        this.placing = false;
    }

    updatePointer(event) {
        this.lastPointer = event;
        const block = this.latLngToBlock(event.latlng);
        document.getElementById('coord-x').textContent = block.x;
        document.getElementById('coord-z').textContent = block.z;
        const exact = Number.isFinite(block.y);
        document.getElementById('coord-y').textContent = exact ? block.y : '—';
    }

    forgetPointer() {
        this.lastPointer = null;
    }

    getMap() {
        return this.map;
    }
}

export const mapManager = new MapManager();
