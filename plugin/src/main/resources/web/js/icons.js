import {t} from './i18n.js';
import {pixelPath} from './map.js';
import {element} from './ui.js';
import {wsManager} from './websocket.js';

const OUTLINE = pixelPath([[1, 0, 10, 1], [0, 1, 12, 10], [1, 11, 10, 1], [3, 12, 6, 1], [4, 13, 4, 1], [5, 14, 2, 1]]);
const FILL = pixelPath([[1, 1, 10, 10], [3, 11, 6, 1], [4, 12, 4, 1], [5, 13, 2, 1]]);
const SHINE = pixelPath([[1, 1, 2, 1], [1, 2, 1, 1]]);
const RIM = pixelPath([[2, 0, 10, 1], [1, 1, 12, 1], [0, 2, 14, 10], [1, 12, 12, 1], [2, 13, 10, 1], [4, 14, 6, 1],
    [5, 15, 4, 1], [6, 16, 2, 1]]);
export const MARKER = 'taerra:marker';
export const GEM = 'taerra:gem';
export const NOT_NAME = /[^\p{L}\p{N} _.,:'&()#!?+-]/gu;
export const fold = (text) => text.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase();
const MIN_CONTRAST = 3;

const linear = (value) => (value /= 255) <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4;

function lowContrast(hex) {
    const [r, g, b] = [1, 3, 5].map(i => linear(parseInt(hex.slice(i, i + 2), 16) || 0));
    return 1.05 / (.2126 * r + .7152 * g + .0722 * b + .05) < MIN_CONTRAST;
}

let grids = 0;

export function tintFilter(tint, color) {
    return tint ? ` filter="url(#icon-${lowContrast(color) ? 'dark' : 'light'})"` : '';
}

function badgeSvg(href, tint, color, personal = false) {
    return badgeFrame(href ? `<image href="${href}" x="2" y="2" width="8" height="8"${tintFilter(tint, color)}/>` : '',
        personal);
}

export function badgeFrame(glyph, personal = false) {
    const [width, height] = personal ? [14, 17] : [12, 15];
    return `<svg class="waypoint__badge" viewBox="0 0 ${width} ${height}" width="${width * 2}" height="${height * 2}" aria-hidden="true">
${personal ? `<path d="${RIM}" style="fill: var(--gold)"/><g transform="translate(1 1)">` : '<g>'}
<path d="${OUTLINE}" fill="#111"/><path d="${FILL}" fill="currentColor"/><path d="${SHINE}" fill="#fff" fill-opacity=".35"/>
${glyph}</g></svg>`;
}

class IconStore {
    constructor() {
        this.icons = new Map();
        this.listeners = [];
    }

    initialize() {
        wsManager.on('icons', () => void this.load());
        return this.load();
    }

    async load() {
        try {
            const response = await fetch('api/icons');
            if (!response.ok) {
                return;
            }
            this.icons = new Map((await response.json()).map(icon => [icon.id, icon]));
            this.listeners.forEach(listener => listener());
        } catch (error) {
            console.error('Failed to load the icons:', error);
        }
    }

    onChange(listener) {
        this.listeners.push(listener);
    }

    get(id) {
        return id ? this.icons.get(id) : undefined;
    }

    list() {
        return [...this.icons.values()];
    }

    url(id) {
        const icon = this.get(id);
        return icon ? `api/icons/${icon.id.replace(':', '/')}.png?v=${icon.version}` : null;
    }

    label(id) {
        const icon = this.get(id);
        if (!icon) {
            return '';
        }
        const [namespace, key] = icon.id.split(':');
        if (!icon.label) {
            return t(`icon.${key}`);
        }
        return icon.source === 'plugin' ? `${icon.label} (${namespace})` : icon.label;
    }

    resolve(own, fallback, personal) {
        return this.get(own) ? own : this.get(fallback) ? fallback : personal ? GEM : MARKER;
    }

    badge(id, color, personal = false) {
        const icon = this.get(id);
        return badgeSvg(icon ? this.url(id) : null, Boolean(icon?.tint), color, personal);
    }

    image(id) {
        const image = element('img', this.get(id)?.tint ? 'icon-image icon-image--tint' : 'icon-image');
        image.src = this.url(id) ?? '';
        image.alt = '';
        image.width = 16;
        image.height = 16;
        return image;
    }
}

export const iconStore = new IconStore();

class IconGrid {
    constructor(root, onPick) {
        this.root = root;
        this.onPick = onPick;
        this.name = `icons-${++grids}`;
        this.value = '';
        this.query = '';
        this.fallback = null;
        this.fallbackLabel = '';
        root.classList.add('icon-grid');
        root.setAttribute('role', 'radiogroup');
        root.addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                this.onPick(this.value);
            }
        });
        iconStore.onChange(() => this.render());
    }

    set(value, fallback = null, fallbackLabel = t('icons.default')) {
        this.value = iconStore.get(value) ? value : '';
        this.fallback = fallback;
        this.fallbackLabel = fallbackLabel;
        this.render();
    }

    render() {
        if (this.value && !iconStore.get(this.value)) {
            this.value = '';
        }
        const tile = (id, label, content) => {
            const wrapper = element('label', 'icon-tile');
            wrapper.title = label;
            const radio = document.createElement('input');
            radio.type = 'radio';
            radio.name = this.name;
            radio.value = id;
            radio.checked = id === this.value;
            radio.setAttribute('aria-label', label);
            radio.addEventListener('change', () => this.value = id);
            wrapper.addEventListener('click', (event) => {
                if (event.detail > 0) {
                    this.onPick(id);
                }
            });
            wrapper.append(radio, content);
            return wrapper;
        };
        const query = fold(this.query.trim());
        this.root.replaceChildren(...(query ? [] : [tile('', this.fallbackLabel, iconStore.image(this.fallback))]),
            ...iconStore.list().filter(icon => fold(iconStore.label(icon.id)).includes(query))
                .map(icon => tile(icon.id, iconStore.label(icon.id), iconStore.image(icon.id))));
    }

    focus() {
        (this.root.querySelector('input:checked') ?? this.root.querySelector('input'))?.focus();
    }
}

class IconChooser {
    constructor() {
        this.dialog = null;
        this.grid = null;
        this.resolve = null;
    }

    initialize() {
        this.dialog = document.getElementById('icon-chooser');
        this.filter = document.getElementById('icon-chooser-filter');
        this.filter.placeholder = t('manager.filter');
        this.filter.addEventListener('input', () => {
            this.grid.query = this.filter.value;
            this.grid.render();
        });
        this.filter.addEventListener('keydown', (event) => {
            const only = this.grid.root.querySelectorAll('input');
            if (event.key === 'Enter' && only.length === 1) {
                event.preventDefault();
                this.finish(only[0].value);
                this.dialog.close();
            }
        });
        this.grid = new IconGrid(document.getElementById('icon-chooser-grid'), (id) => {
            this.finish(id);
            this.dialog.close();
        });
        this.dialog.addEventListener('close', () => this.finish(null));
    }

    choose(title, current, fallback, fallbackLabel) {
        this.finish(null);
        document.getElementById('icon-chooser-title').textContent = title;
        this.filter.value = '';
        this.grid.query = '';
        this.grid.set(current, fallback, fallbackLabel);
        this.dialog.showModal();
        this.grid.focus();
        return new Promise(resolve => this.resolve = resolve);
    }

    finish(id) {
        this.resolve?.(id);
        this.resolve = null;
    }
}

export const iconChooser = new IconChooser();
