import {t} from './i18n.js';
import {mapManager, pixelPath} from './map.js';
import {element} from './ui.js';

const PALETTE = {
    s: '#ffcf3f', r: '#e0a82e', o: '#ff8a3d', p: '#ffb15c', h: '#b35a1f', w: '#8a4518', m: '#e3e4e7', d: '#8c8d90',
    c: '#a0a1a4', u: '#6e6f72', b: '#5b9bd5', t: '#7d7e82', l: '#58585a'
};
const ART = {
    sun: ['....r....', '.r.....r.', '...sss...', '..sssss..', 'r.sssss.r', '..sssss..', '...sss...', '.r.....r.', '....r....'],
    sunset: ['.........', '....p....', '.p.....p.', '...ooo...', 'p.ooooo.p', '..ooooo..', 'hhhhhhhhh', '..wwwww..', '.........'],
    moon: ['...mmd...', '..mmd....', '.mmd.....', '.mmd.....', '.mmd.....', '.mmmd....', '.mmmmd...', '..mmmmmd.', '...mmmm..'],
    rain: ['.........', '...ccc...', '.ccccccc.', 'ccccccccc', '.uuuuuuu.', '.........', '.b..b..b.', 'b..b..b..', '.........'],
    thunder: ['...ttt...', '.ttttttt.', 'ttttttttt', '.lllllll.', '....ss...', '...ss....', '..ssss...', '...ss....', '...s.....']
};
const GLYPHS = Object.fromEntries(Object.entries(ART).map(([name, rows]) => {
    const rects = {};
    rows.forEach((row, y) => {
        for (const {0: run, index: x} of row.matchAll(/([^.])\1*/g)) {
            (rects[run[0]] ??= []).push([x, y, run.length, 1]);
        }
    });
    return [name, Object.entries(rects).map(([key, list]) => [PALETTE[key], list])];
}));

function glyphName(sky) {
    if (sky.weather !== 'clear') {
        return sky.weather;
    }
    return {day: 'sun', night: 'moon'}[sky.phase] ?? 'sunset';
}

function glyph(sky, size) {
    const paths = GLYPHS[glyphName(sky)].map(([fill, rects]) => `<path d="${pixelPath(rects)}" fill="${fill}"/>`);
    return `<svg aria-hidden="true" class="sky-glyph" height="${size}" shape-rendering="crispEdges" viewBox="0 0 9 9" width="${size}">${paths.join('')}</svg>`;
}

function clock(ticks) {
    const time = (ticks + 6000) % 24000;
    return `${String(Math.floor(time / 1000)).padStart(2, '0')}:${String(Math.floor(time % 1000 * 60 / 1000)).padStart(2, '0')}`;
}

function describe(sky, withClock = true) {
    return [t(`sky.${sky.phase}`), sky.weather === 'clear' ? null : t(`sky.${sky.weather}`), withClock ? clock(sky.time) : null,
        t('sky.count', {day: sky.day}), sky.advancing ? null : t('sky.stopped')].filter(Boolean).join(', ');
}

function showProgress(bar, sky) {
    bar.dataset.phase = sky.advancing ? sky.phase : 'stopped';
    bar.style.setProperty('--progress', sky.progress);
}

class SkyManager {
    skies = {};
    world = null;
    rows = new Map();

    initialize({worlds, worldName, selectWorld}) {
        this.worlds = worlds;
        this.worldName = worldName;
        this.selectWorld = selectWorld;
        this.menu = element('div', 'menu sky-menu');
        this.menu.id = 'sky-menu';
        this.menu.popover = 'auto';
        this.menu.setAttribute('aria-label', t('sky.title'));
        document.body.append(this.menu);
        this.button = L.DomUtil.create('button', 'map-btn sky');
        this.button.type = 'button';
        this.button.hidden = true;
        this.button.setAttribute('popovertarget', 'sky-menu');
        L.DomEvent.disableClickPropagation(this.button);
        new (L.Control.extend({onAdd: () => this.button}))({position: 'topright'}).addTo(mapManager.getMap());
        this.menu.addEventListener('beforetoggle', (event) => {
            if (event.newState === 'open') {
                this.menu.style.visibility = 'hidden';
                this.renderMenu();
            }
        });
        this.menu.addEventListener('toggle', (event) => {
            if (event.newState === 'open') {
                this.place();
                this.menu.style.visibility = '';
            }
        });
        addEventListener('resize', () => this.menu.matches(':popover-open') && this.place());
    }

    place() {
        const {top, left} = this.button.getBoundingClientRect();
        const highest = document.querySelector('.topbar').getBoundingClientRect().bottom + 8;
        const height = this.menu.scrollHeight + 4;
        this.menu.style.setProperty('--sky-top', `${Math.max(highest, Math.min(top, innerHeight - height - 16))}px`);
        this.menu.style.setProperty('--sky-right', `${innerWidth - left + 8}px`);
    }

    update(skies) {
        this.skies = skies ?? {};
        this.render();
    }

    setWorld(name) {
        this.world = name;
        this.render();
    }

    render() {
        const sky = this.skies[this.world];
        this.button.hidden = !sky;
        if (sky) {
            const bar = element('span', 'sky__bar');
            showProgress(bar, sky);
            this.button.innerHTML = glyph(sky, 18);
            this.button.append(bar);
            this.button.title = describe(sky);
            this.button.setAttribute('aria-label', describe(sky));
        }
        if (this.menu.matches(':popover-open')) {
            const shown = Object.keys(this.skies);
            if (shown.length === this.rows.size && shown.every(name => this.rows.has(name))) {
                this.updateMenu();
            } else {
                this.renderMenu();
                this.place();
            }
        }
    }

    renderMenu() {
        this.rows = new Map(this.worlds().filter(world => this.skies[world.name]).map(world => {
            const row = element('button', 'menu__item sky-menu__item');
            row.type = 'button';
            row.setAttribute('aria-current', String(world.name === this.world));
            const icon = element('span', 'sky-menu__glyph');
            const detail = element('span', 'sky-menu__detail');
            const time = element('span', 'sky-menu__time');
            const bar = element('span', 'sky__bar sky-menu__bar');
            const text = element('span', 'sky-menu__text');
            text.append(element('span', 'sky-menu__name', this.worldName(world.name)), time, detail, bar);
            row.append(icon, text);
            row.addEventListener('click', () => {
                this.menu.hidePopover();
                this.selectWorld(world.name);
            });
            return [world.name, {row, icon, detail, time, bar}];
        }));
        this.menu.replaceChildren(element('p', 'label menu__label', t('sky.title')),
            ...[...this.rows.values()].map(({row}) => row));
        this.updateMenu();
    }

    updateMenu() {
        for (const [name, {icon, detail, time, bar}] of this.rows) {
            const sky = this.skies[name];
            icon.innerHTML = glyph(sky, 36);
            detail.textContent = describe(sky, false);
            time.textContent = clock(sky.time);
            showProgress(bar, sky);
        }
    }
}

export const skyManager = new SkyManager();
