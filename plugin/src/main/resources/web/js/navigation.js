import {accountManager} from './account.js';
import {t} from './i18n.js';
import {mapManager, pixelPath} from './map.js';
import {wsManager} from './websocket.js';

const $ = (id) => document.getElementById(id);

const X_OUTLINE = [[0, 0, 3, 1], [4, 0, 3, 1], [0, 1, 1, 1], [3, 1, 1, 1], [6, 1, 1, 1], [0, 2, 1, 1], [6, 2, 1, 1],
    [1, 3, 1, 1], [5, 3, 1, 1], [0, 4, 1, 1], [6, 4, 1, 1], [0, 5, 1, 1], [3, 5, 1, 1], [6, 5, 1, 1], [0, 6, 3, 1], [4, 6, 3, 1]];
const X_BODY = [[1, 1, 2, 1], [4, 1, 2, 1], [1, 2, 5, 1], [2, 3, 3, 1], [1, 4, 5, 1], [1, 5, 2, 1], [4, 5, 2, 1]];

export const TARGET_SVG = `<svg viewBox="0 0 7 7" width="21" height="21" aria-hidden="true" shape-rendering="crispEdges">
<path d="${pixelPath(X_OUTLINE)}" fill="#111"/><path d="${pixelPath(X_BODY)}" fill="currentColor"/></svg>`;

class NavigationManager {
    constructor() {
        this.target = null;
        this.world = null;
        this.marker = null;
        this.onShow = null;
    }

    initialize() {
        $('navigation-icon').innerHTML = TARGET_SVG;
        $('navigation-show').addEventListener('click', () => this.target && this.onShow?.(this.target));
        $('navigation-stop').addEventListener('click', () => void this.navigate(null));
        wsManager.on('navigation', (message) => this.apply(message.target ?? null));
        wsManager.on('connect', () => void this.load());
    }

    async load() {
        if (!accountManager.loggedIn()) {
            this.apply(null);
            return;
        }
        try {
            const response = await fetch('/api/navigation');
            this.apply(response.status === 200 ? await response.json() : null);
        } catch (error) {
            console.error('Failed to load the navigation:', error);
        }
    }

    async navigate(target) {
        try {
            const response = await fetch('/api/navigation', target
                ? {method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(target)}
                : {method: 'DELETE'});
            if (response.ok) {
                this.apply(target);
            }
            return response.ok;
        } catch {
            return false;
        }
    }

    apply(target) {
        this.target = target;
        $('navigation').hidden = !target;
        if (target) {
            $('navigation').style.setProperty('--marker', target.color);
            $('navigation-text').textContent = t('navigation.to', {name: target.label});
        }
        this.redraw();
    }

    setWorld(world) {
        this.world = world;
        this.redraw();
    }

    redraw() {
        this.marker?.remove();
        this.marker = null;
        const target = this.target;
        if (!target || target.player || target.world !== this.world) {
            return;
        }
        const body = document.createElement('div');
        body.className = 'navigation-target';
        body.style.color = target.color;
        body.innerHTML = TARGET_SVG;
        this.marker = L.marker(mapManager.blockToLatLng(target.x, target.z, target.y ?? undefined), {
            pane: 'waypoints',
            interactive: false,
            icon: L.divIcon({className: 'navigation-target-icon', html: body, iconSize: [21, 21], iconAnchor: [10, 10]})
        }).addTo(mapManager.getMap());
    }
}

export const navigationManager = new NavigationManager();
