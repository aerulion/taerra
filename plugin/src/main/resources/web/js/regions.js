import {element, markerManager} from './markers.js';
import {mapManager} from './map.js';

const LABEL_Y = 64;

class RegionManager {
    constructor() {
        this.world = null;
        this.regions = [];
        this.editable = false;
        this.owners = new Map();
        this.labels = null;
        this.hidden = null;
    }

    initialize() {
        this.labels = L.layerGroup().addTo(mapManager.getMap());
    }

    async load(world) {
        this.world = world;
        let data = null;
        try {
            const response = await fetch(`/api/regions/${encodeURIComponent(world)}`);
            data = response.ok ? await response.json() : null;
        } catch {
        }
        if (world !== this.world) {
            return;
        }
        this.regions = data?.regions ?? [];
        this.editable = data?.editable === true;
        this.owners = new Map();
        for (const region of this.regions) {
            for (const [x, z] of region.chunks ?? []) {
                this.owners.set(`${x},${z}`, region);
            }
        }
        mapManager.setRevealed(data?.revealed ?? []);
        if (data) {
            mapManager.setLayered(data.layered === true);
        }
        this.redraw();
    }

    at(chunkX, chunkZ) {
        return this.owners.get(`${chunkX},${chunkZ}`) ?? null;
    }

    hide(id) {
        this.hidden = id;
        this.redraw();
    }

    redraw() {
        if (!this.labels) {
            return;
        }
        this.labels.clearLayers();
        if (!markerManager.shownLayer('regions')) {
            return;
        }
        for (const region of this.regions) {
            if (!region.name || region.id === this.hidden) {
                continue;
            }
            const [x, z] = region.label;
            L.marker(mapManager.project(x, LABEL_Y, z), {
                interactive: false,
                keyboard: false,
                icon: L.divIcon({className: 'region-name-icon', html: element('span', 'region-name', region.name), iconSize: null})
            }).addTo(this.labels);
        }
    }
}

export const regionManager = new RegionManager();
