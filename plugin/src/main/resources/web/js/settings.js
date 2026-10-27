import {accountManager, FALLBACK_AVATAR} from './account.js';
import {language, numbers, plurals, t} from './i18n.js';
import {iconChooser, iconStore, MARKER} from './icons.js';
import {mapManager} from './map.js';
import {markerManager, privateIcon} from './markers.js';
import {armed, disarm, element, iconButton, slide, switchInput} from './ui.js';
import {
    addsView,
    badLimit,
    isLimit,
    isStorage,
    isViews,
    layerField,
    layerIconField,
    layerScaleField,
    limit,
    ORDER,
    outOfRange,
    parseScale,
    rebase,
    reshape,
    same,
    sameOrder,
    scaleText,
    settingField,
    SHAPES,
    STORAGE_TYPE,
    VIEWS,
    viewsOf,
    worldField
} from './settingvalues.js';
import {wsManager} from './websocket.js';

const $ = (id) => document.getElementById(id);

const NOT_NAME = /[^\p{L}\p{N} _.,:'&()#!?+-]/gu;
const STATUS_MILLIS = 6000;
const FLASH_MILLIS = 1800;
const SECTIONS = [
    ['worlds', []],
    ['limits', []],
    ['views', []],
    ['map', ['web.name', 'web.public-url', 'join-hint.interval-days']],
    [
        'layers',
        [
            'markers.spawn.enabled',
            'markers.spawn.shown-by-default',
            'markers.worldborder.enabled',
            'markers.worldborder.shown-by-default',
            'markers.worldguard.enabled',
            'markers.worldguard.shown-by-default',
            'markers.towny.enabled',
            'markers.towny.shown-by-default',
            'markers.griefprevention.enabled',
            'markers.griefprevention.shown-by-default',
            'markers.plotsquared.enabled',
            'markers.plotsquared.shown-by-default',
            'markers.loaded-chunks.enabled',
            'markers.loaded-chunks.shown-by-default'
        ]
    ],
    ['rendering', ['rendering.update-interval', 'rendering.render-threads', 'cache.max-tiles', 'cache.max-chunks']],
    [
        'storage',
        [
            'storage.type',
            'storage.database.host',
            'storage.database.port',
            'storage.database.name',
            'storage.database.user',
            'storage.database.password'
        ]
    ],
    ['web', ['web.port', 'web.host']]
];
const ago = new Intl.RelativeTimeFormat(language, {numeric: 'auto'});

const coordinate = (text) => (/^-?\d+$/.test(text.trim()) ? Number(text) : null);

function translated(key, fallback) {
    const text = t(key);
    return text === key ? fallback : text;
}

function cleanName(input) {
    const clean = input.value.normalize('NFC').replace(NOT_NAME, '');
    if (clean !== input.value) {
        input.value = clean;
    }
    return clean;
}

function since(at) {
    const minutes = Math.round((at - Date.now()) / 60000);
    if (Math.abs(minutes) < 60) {
        return ago.format(minutes, 'minute');
    }
    const hours = Math.round(minutes / 60);
    return Math.abs(hours) < 24 ? ago.format(hours, 'hour') : ago.format(Math.round(hours / 24), 'day');
}

function flag(field, checked, content, onChange) {
    const label = element('label', 'settings__flag');
    const text = element('span', 'settings__flag-text');
    text.append(...content);
    const {wrap} = switchInput(field, checked, text.textContent, onChange);
    label.append(wrap, text);
    return label;
}

class SettingsDialog {
    constructor() {
        this.dialog = $('settings');
        this.view = null;
        this.base = null;
        this.draft = null;
        this.conflicts = new Map();
        this.display = [];
        this.flashing = new Set();
        this.editors = [];
        this.statusTimer = 0;
        this.flashTimer = 0;
        this.highlight = null;
        this.loading = null;
        this.reload = false;
        this.worlds = () => [];
        this.scaleOpen = null;
        this.scaleZoom = 0;
    }

    initialize(worlds) {
        this.worlds = worlds;
        $('settings-open').addEventListener('click', () => void this.open());
        iconStore.onChange(() => this.dialog.open && this.draft && this.render());
        this.dialog.addEventListener('cancel', (event) => {
            if (this.mustConfirm()) {
                event.preventDefault();
            }
        });
        this.dialog.addEventListener('close', () => {
            this.closeScale();
            wsManager.send({type: 'settings', open: false});
            this.disarm();
            this.view = null;
            this.base = null;
            this.draft = null;
            this.conflicts.clear();
        });
        $('settings-form').addEventListener('submit', (event) => {
            event.preventDefault();
            void this.save();
        });
        $('settings-body').addEventListener('scroll', () => this.highlight?.(), {passive: true});
        wsManager.on('settings', (message) => {
            if (this.dialog.open && this.view && message.revision > this.view.revision) {
                void this.refresh();
            }
        });
        wsManager.on('settings_editors', (message) => {
            this.editors = message.editors ?? [];
            this.renderEditors();
        });
        wsManager.on('connect', () => {
            if (this.dialog.open) {
                this.announce();
                void this.refresh();
            }
        });
    }

    mustConfirm() {
        if (!this.draft || !Object.keys(this.changes()).length || armed($('settings-cancel'), t('settings.discard'))) {
            return false;
        }
        $('settings-cancel').focus();
        return true;
    }

    disarm() {
        disarm($('settings-cancel'));
    }

    announce() {
        wsManager.send({type: 'settings', open: true, client: 'web'});
    }

    async open() {
        if (!accountManager.canConfigure()) {
            return;
        }
        $('account-menu').hidePopover?.();
        this.showStatus(null);
        $('settings-nav').replaceChildren();
        $('settings-editors').replaceChildren();
        $('settings-body').replaceChildren(element('p', 'empty', t('settings.loading')));
        $('settings-save').disabled = true;
        if (!this.dialog.open) {
            this.dialog.showModal();
        }
        this.announce();
        const next = await this.fetchView();
        if (!next) {
            $('settings-body').replaceChildren(element('p', 'empty', t('settings.unavailable')));
            return;
        }
        this.accept(next);
        this.base = structuredClone(next.values);
        this.draft = structuredClone(next.values);
        this.conflicts.clear();
        this.display = [];
        this.arrange(this.draft[ORDER]);
        this.render();
        $('settings-body').scrollTop = 0;
        this.updateStatus();
    }

    async fetchView() {
        try {
            const response = await fetch('api/settings', {cache: 'no-store'});
            return response.ok ? await response.json() : null;
        } catch {
            return null;
        }
    }

    async refresh() {
        if (this.loading) {
            this.reload = true;
            return;
        }
        this.loading = this.fetchView();
        const next = await this.loading;
        this.loading = null;
        if (next && this.draft && this.dialog.open) {
            this.rebase(next);
        }
        if (this.reload) {
            this.reload = false;
            void this.refresh();
        }
    }

    accept(next) {
        this.view = next;
        this.editors = next.editors ?? [];
    }

    rebase(next) {
        const now = next.values;
        const changed = rebase(this, next);
        this.accept(next);
        this.arrange(this.draft[ORDER]);
        this.fixOrder();
        this.base = structuredClone(now);
        this.render();
        this.flash(changed);
        if (changed.size) {
            this.announceChanges(changed, next);
        } else {
            this.updateStatus();
        }
    }

    arrange(order) {
        const known = new Set(this.view.worlds.map((world) => world.id));
        const wanted = (order ?? []).filter((id) => known.has(id));
        const queue = [...wanted];
        const placed = this.display
            .filter((id) => known.has(id))
            .map((id) => (wanted.includes(id) ? queue.shift() : id));
        const rest = [...queue, ...this.view.worlds.map((world) => world.id)].filter((id) => !placed.includes(id));
        this.display = [...placed, ...new Set(rest)];
    }

    fixOrder() {
        this.draft[ORDER] = this.display.filter((id) => this.draft[worldField(id, 'enabled')] === true);
    }

    announceChanges(fields, next) {
        const names = [...new Set([...fields].map((field) => next.authors?.[field]?.name).filter(Boolean))];
        const count = fields.size;
        const who = names.length === 1 ? names[0] : t('settings.someone');
        this.showStatus(
            t(`settings.liveChanged.${plurals.select(count)}`, {who, count: numbers.format(count)}),
            'is-live'
        );
    }

    flash(fields) {
        if (!fields.size) {
            return;
        }
        fields.forEach((field) => this.flashing.add(field));
        this.markFlashing();
        clearTimeout(this.flashTimer);
        this.flashTimer = setTimeout(() => {
            this.flashing.clear();
            this.markFlashing();
        }, FLASH_MILLIS);
    }

    markFlashing() {
        for (const row of document.querySelectorAll('#settings [data-fields]')) {
            row.classList.toggle(
                'is-updated',
                row.dataset.fields.split(' ').some((field) => this.flashing.has(field))
            );
        }
    }

    setting(key) {
        return this.view.settings.find((setting) => setting.key === key);
    }

    world(id) {
        return this.view.worlds.find((world) => world.id === id);
    }

    label(field) {
        if (field === ORDER) {
            return t('settings.order');
        }
        if (field.startsWith('world:')) {
            const id = field.slice('world:'.length, field.lastIndexOf(':'));
            const world = this.world(id);
            const name = this.draft?.[worldField(id, 'name')] || world?.defaultName || id;
            return `${name}: ${t(`settings.world.${field.slice(field.lastIndexOf(':') + 1)}`)}`;
        }
        if (field.startsWith('layer:')) {
            const id = field.slice('layer:'.length);
            return translated(`layer.${id}`, this.view.layers.find((layer) => layer.id === id)?.label ?? id);
        }
        if (field.startsWith('layer-icon:')) {
            return `${this.label(layerField(field.slice('layer-icon:'.length)))}: ${t('settings.layerIcon')}`;
        }
        if (field.startsWith('layer-scale:')) {
            return `${this.label(layerField(field.slice('layer-scale:'.length)))}: ${t('settings.layerScale')}`;
        }
        const key = field.startsWith('setting:') ? field.slice('setting:'.length) : field;
        return translated(`settings.${key}`, key);
    }

    format(field, value) {
        if (field === ORDER) {
            return (value ?? [])
                .map((id) => this.draft[worldField(id, 'name')] || this.world(id)?.defaultName || id)
                .join(' → ');
        }
        if (isLimit(field)) {
            return this.describeLimit(value);
        }
        if (isViews(field)) {
            return t(`settings.views.${value in VIEWS ? value : 'both'}`);
        }
        if (typeof value === 'boolean') {
            return t(value ? 'settings.on' : 'settings.off');
        }
        if (field.startsWith('layer-icon:')) {
            return value ? iconStore.label(value) || value : t('icons.default');
        }
        if (field.startsWith('layer-scale:')) {
            return this.describeScale(parseScale(value));
        }
        if (value === '' || value === null || value === undefined) {
            return t('settings.default');
        }
        return typeof value === 'number' ? numbers.format(value) : `“${value}”`;
    }

    describeLimit(value) {
        if (!value || value.shape === 'none') {
            return t('settings.limit.none');
        }
        if (value.shape === 'rectangle') {
            return t('settings.limit.box', {
                fromX: numbers.format(value.minX),
                fromZ: numbers.format(value.minZ),
                toX: numbers.format(value.maxX),
                toZ: numbers.format(value.maxZ)
            });
        }
        return t('settings.limit.round', {
            shape: t(`settings.limit.${value.shape}`),
            radius: numbers.format((value.maxX - value.minX) / 2),
            x: numbers.format(Math.floor((value.minX + value.maxX) / 2)),
            z: numbers.format(Math.floor((value.minZ + value.maxZ) / 2))
        });
    }

    changedBy(field) {
        const author = this.view.authors?.[field];
        return author ? t('settings.changedBy', {name: author.name, time: since(author.at)}) : '';
    }

    render() {
        const body = $('settings-body');
        const scroll = body.scrollTop;
        const active = document.activeElement;
        const focused = this.dialog.contains(active) ? active.dataset?.field : null;
        const selection =
            focused && typeof active.selectionStart === 'number' ? [active.selectionStart, active.selectionEnd] : null;
        const sections = SECTIONS.map(([name, keys]) => {
            if (name === 'limits' || name === 'views') {
                return this.section(
                    name,
                    this.display
                        .filter((id) => this.draft[worldField(id, 'enabled')] === true)
                        .map((id) => this.world(id))
                        .filter(Boolean)
                        .flatMap((world) => (name === 'limits' ? this.limitRow(world) : this.viewsRow(world)))
                );
            }
            if (name === 'worlds') {
                return this.section(name, [this.conflict(ORDER), this.worldList()].filter(Boolean));
            }
            const rows = keys
                .map((key) => this.setting(key))
                .filter(Boolean)
                .flatMap((setting) => this.field(setting));
            if (name === 'layers' && this.view.layers.length) {
                rows.push(
                    element('h4', 'settings__subheading', t('settings.privateLayers')),
                    ...this.view.layers.flatMap((layer) => this.layerRow(layer))
                );
            }
            return this.section(name, rows);
        });
        body.replaceChildren(...sections);
        body.scrollTop = scroll;
        this.renderNav(sections);
        this.renderEditors();
        this.markFlashing();
        if (focused) {
            const target = this.dialog.querySelector(`[data-field="${CSS.escape(focused)}"]`);
            target?.focus({preventScroll: true});
            if (selection && target?.setSelectionRange) {
                target.setSelectionRange(...selection);
            }
        }
    }

    renderEditors() {
        const box = $('settings-editors');
        const me = accountManager.ownUuid();
        const others = this.editors.filter((editor) => !(editor.uuid === me && editor.client === 'web'));
        box.replaceChildren(
            ...others.map((editor) => {
                const item = element('span', 'settings__editor');
                const where = t(editor.client === 'mod' ? 'settings.inGame' : 'settings.inBrowser');
                const name = editor.uuid === me ? t('settings.you') : editor.name;
                item.title = t('settings.editing', {name, where});
                const head = document.createElement('img');
                head.className = 'settings__editor-head';
                head.alt = '';
                head.src = `api/avatars/${editor.uuid}.png`;
                head.addEventListener('error', () => (head.src = FALLBACK_AVATAR), {once: true});
                item.append(head, element('span', 'settings__editor-name', name));
                item.dataset.client = editor.client;
                return item;
            })
        );
        box.hidden = others.length === 0;
    }

    renderNav(sections) {
        const nav = $('settings-nav');
        const links = sections.map((section) => {
            const link = element('button', 'settings__link', section.dataset.title);
            link.type = 'button';
            link.dataset.icon = section.dataset.section;
            link.addEventListener('click', () => section.scrollIntoView({behavior: 'smooth', block: 'start'}));
            return link;
        });
        nav.replaceChildren(...links);
        const body = $('settings-body');
        this.highlight = () => {
            const atEnd = body.scrollTop + body.clientHeight >= body.scrollHeight - 4;
            const top = body.getBoundingClientRect().top + 32;
            const current = atEnd
                ? sections.at(-1)
                : (sections.findLast((section) => section.getBoundingClientRect().top <= top) ?? sections[0]);
            links.forEach((link, index) => {
                const now = sections[index] === current;
                if (now && link.getAttribute('aria-current') !== 'true') {
                    const box = link.getBoundingClientRect();
                    const left = nav.scrollLeft + box.left - nav.getBoundingClientRect().left;
                    nav.scrollTo({left: left - (nav.clientWidth - box.width) / 2, behavior: 'smooth'});
                }
                link.setAttribute('aria-current', String(now));
            });
            slide(nav, links[sections.indexOf(current)]);
        };
        this.highlight();
    }

    section(name, rows) {
        const section = element('section', 'settings__section');
        section.dataset.section = name;
        section.dataset.title = t(`settings.section.${name}`);
        const header = element('header', 'settings__section-header');
        header.append(element('h3', 'title settings__heading', section.dataset.title));
        const hint = translated(`settings.section.${name}.hint`, null);
        if (hint) {
            header.append(element('p', 'settings__intro', hint));
        }
        section.append(header, ...rows);
        return section;
    }

    set(field, value) {
        this.draft[field] = value;
        this.conflicts.delete(field);
        this.disarm();
        this.updateStatus();
    }

    conflict(...fields) {
        const found = fields.filter((field) => this.conflicts.has(field));
        if (!found.length) {
            return null;
        }
        const box = element('div', 'callout');
        box.setAttribute('role', 'alert');
        for (const field of found) {
            const {theirs, by} = this.conflicts.get(field);
            const line = element('div', 'settings__conflict-line');
            const named = field.startsWith('world:') || field === ORDER;
            const text = element(
                'p',
                'settings__conflict-text',
                t(named ? 'settings.conflictNamed' : 'settings.conflict', {
                    who: by ?? t('settings.someone'),
                    what: this.label(field),
                    value: this.format(field, theirs)
                })
            );
            const theirsButton = element('button', 'btn btn--small', t('settings.useTheirs'));
            theirsButton.type = 'button';
            theirsButton.addEventListener('click', () => {
                this.draft[field] = structuredClone(theirs);
                this.conflicts.delete(field);
                if (field === ORDER) {
                    this.arrange(theirs);
                }
                if (field.endsWith(':enabled') || field === ORDER) {
                    this.fixOrder();
                }
                this.render();
                this.updateStatus();
            });
            const mineButton = element('button', 'btn btn--small btn--primary', t('settings.keepMine'));
            mineButton.type = 'button';
            mineButton.addEventListener('click', () => {
                this.conflicts.delete(field);
                this.render();
                this.updateStatus();
            });
            const buttons = element('div', 'settings__conflict-buttons');
            buttons.append(theirsButton, mineButton);
            line.append(text, buttons);
            box.append(line);
        }
        return box;
    }

    field(setting) {
        const field = settingField(setting.key);
        const value = this.draft[field];
        const database = setting.key.startsWith('storage.database.');
        const row = element(
            setting.kind === 'toggle' ? 'label' : 'div',
            setting.kind === 'toggle' ? 'row row--action settings__row' : 'row settings__row'
        );
        row.dataset.fields = field;
        row.title = this.changedBy(field);
        row.classList.toggle('is-locked', !setting.editable);
        row.classList.toggle('is-conflict', this.conflicts.has(field));
        row.classList.toggle('is-changed', setting.editable && !same(value, this.base[field]));
        row.classList.toggle(
            'is-invalid',
            setting.editable && !same(value, this.base[field]) && outOfRange(setting, value)
        );
        const id = `setting-${setting.key.replace(/\W/g, '-')}`;
        const text = element('span', 'row__text');
        const title = element(setting.kind === 'toggle' ? 'span' : 'label', 'settings__label', this.label(field));
        if (setting.kind !== 'toggle') {
            title.htmlFor = id;
        }
        text.append(title);
        const hint = setting.editable
            ? translated(`settings.${setting.key}.hint`, null)
            : (translated(`settings.${setting.key}.locked`, null)?.replace(
                  '{command}',
                  `/taerra config ${setting.key}`
              ) ?? t('settings.readOnly', {command: `/taerra config ${setting.key}`}));
        if (hint) {
            text.append(element('span', 'hint', hint));
        }
        row.append(text);
        const rows = [row];
        const conflict = this.conflict(field);
        if (setting.kind === 'toggle') {
            const parent = setting.key.endsWith('.shown-by-default')
                ? settingField(setting.key.replace(/shown-by-default$/, 'enabled'))
                : null;
            const {wrap, input} = switchInput(field, value === true, this.label(field), (checked) => {
                this.set(field, checked);
                this.render();
            });
            input.disabled = !setting.editable;
            if (parent) {
                const off = this.draft[parent] !== true;
                row.classList.add('settings__row--sub');
                row.classList.toggle('is-inactive', off);
                input.disabled ||= off;
            }
            row.append(wrap);
        } else if (setting.kind === 'choice' && setting.editable) {
            const group = element('div', 'segmented settings__segmented');
            group.setAttribute('role', 'group');
            group.setAttribute('aria-label', this.label(field));
            for (const option of setting.options ?? []) {
                const button = element(
                    'button',
                    'btn btn--small',
                    translated(`settings.${setting.key}.${option}`, option)
                );
                button.type = 'button';
                button.dataset.field = `${field}:${option}`;
                button.setAttribute('aria-pressed', String(option === value));
                button.addEventListener('click', () => {
                    this.set(field, option);
                    this.render();
                    this.dialog.querySelector(`[data-field="${CSS.escape(`${field}:${option}`)}"]`)?.focus();
                });
                group.append(button);
            }
            row.append(group);
        } else if (!setting.editable) {
            const shown = String(value ?? '');
            const box = element('code', 'settings__value', shown || t('settings.notSet'));
            box.classList.toggle('is-empty', !shown);
            box.title = shown;
            row.append(box);
        } else {
            const input = document.createElement('input');
            input.id = id;
            input.dataset.field = field;
            input.className = 'input settings__input';
            input.autocomplete = 'off';
            input.spellcheck = false;
            if (setting.kind === 'number') {
                const box = element('span', 'settings__number');
                input.type = 'number';
                input.inputMode = 'numeric';
                input.min = String(setting.min);
                input.max = String(setting.max);
                input.step = '1';
                input.value = String(value ?? '');
                input.addEventListener('input', () => {
                    this.set(field, input.value === '' ? null : Number(input.value));
                    const changed = !same(this.draft[field], this.base[field]);
                    row.classList.toggle('is-changed', changed);
                    row.classList.toggle('is-invalid', changed && outOfRange(setting, this.draft[field]));
                });
                box.append(input);
                const unit = translated(`settings.${setting.key}.unit`, null);
                if (unit) {
                    box.append(element('span', 'settings__unit', unit));
                }
                row.append(box);
            } else if (setting.kind === 'secret') {
                input.type = 'password';
                input.autocomplete = 'new-password';
                input.classList.add('settings__input--wide');
                input.placeholder = this.base[field] ? '••••••••' : t('settings.notSet');
                input.value = same(value, this.base[field]) ? '' : (value ?? '');
                input.addEventListener('input', () => {
                    this.set(field, input.value === '' ? this.base[field] : input.value);
                    row.classList.toggle('is-changed', !same(this.draft[field], this.base[field]));
                });
                row.append(input);
            } else {
                input.type = setting.key === 'web.public-url' ? 'url' : 'text';
                input.classList.add('settings__input--wide');
                if (setting.key === 'web.name') {
                    input.maxLength = 32;
                    input.placeholder = 'TAERRA';
                }
                if (setting.key === 'web.public-url') {
                    input.placeholder = 'https://map.example.com';
                }
                input.value = value ?? '';
                input.addEventListener('input', () => {
                    this.set(field, setting.key === 'web.name' ? cleanName(input) : input.value);
                    row.classList.toggle('is-changed', !same(this.draft[field], this.base[field]));
                });
                row.append(input);
            }
        }
        if (database) {
            const off = this.draft[STORAGE_TYPE] === 'files';
            row.classList.add('settings__row--sub');
            row.classList.toggle('is-inactive', off);
            row.querySelectorAll('input').forEach((input) => (input.disabled ||= off));
        }
        if (conflict) {
            rows.push(conflict);
        }
        return rows;
    }

    worldList() {
        const list = element('ol', 'settings__worlds');
        let position = 0;
        this.display
            .map((id) => this.world(id))
            .filter(Boolean)
            .forEach((world) => {
                const on = this.draft[worldField(world.id, 'enabled')] === true;
                const fields = ['enabled', 'private', 'players', 'name'].map((part) => worldField(world.id, part));
                const row = element('li', 'row settings__world');
                row.dataset.env = world.environment ?? 'NONE';
                row.dataset.fields = fields.join(' ');
                row.title = fields.map((field) => this.changedBy(field)).filter(Boolean)[0] ?? '';
                row.classList.toggle('is-off', !on);
                row.classList.toggle(
                    'is-conflict',
                    fields.some((field) => this.conflicts.has(field))
                );
                row.classList.toggle(
                    'is-changed',
                    fields.some((field) => !same(this.draft[field], this.base[field]))
                );

                const rank = element('span', 'settings__rank', on ? String(++position) : '–');
                rank.setAttribute('aria-hidden', 'true');

                const nameField = worldField(world.id, 'name');
                const name = document.createElement('input');
                name.className = 'input settings__world-input';
                name.maxLength = 32;
                name.autocomplete = 'off';
                name.spellcheck = false;
                name.placeholder = world.defaultName;
                name.value = this.draft[nameField] ?? '';
                name.dataset.field = nameField;
                name.setAttribute('aria-label', t('settings.worldName', {world: world.defaultName}));
                name.addEventListener('input', () => {
                    this.set(nameField, cleanName(name));
                    row.classList.toggle(
                        'is-changed',
                        fields.some((field) => !same(this.draft[field], this.base[field]))
                    );
                });

                const about = element('span', 'settings__world-id');
                about.append(
                    element(
                        'span',
                        'settings__env',
                        world.environment ? t(`env.${world.environment}`) : t('settings.notLoaded')
                    ),
                    element('code', '', world.id)
                );
                const naming = element('div', 'settings__world-name');
                naming.append(name, about);

                const privateField = worldField(world.id, 'private');
                const playersField = worldField(world.id, 'players');
                const players = flag(
                    playersField,
                    this.draft[playersField] === true,
                    [privateIcon(), element('span', '', t('settings.privatePlayers'))],
                    (checked) => {
                        this.set(playersField, checked);
                        this.render();
                    }
                );
                players.title = t('settings.privatePlayersHint', {
                    permission: `taerra.players.${world.id.replaceAll(':', '.')}`
                });
                const flags = element('div', 'settings__flags');
                flags.append(
                    flag(worldField(world.id, 'enabled'), on, [t('settings.onMap')], (checked) => {
                        this.set(worldField(world.id, 'enabled'), checked);
                        this.fixOrder();
                        this.render();
                    }),
                    flag(
                        privateField,
                        this.draft[privateField] === true,
                        [privateIcon(), element('span', '', t('settings.private'))],
                        (checked) => {
                            this.set(privateField, checked);
                            this.render();
                        }
                    ),
                    players
                );

                const moves = element('div', 'settings__moves');
                moves.append(this.move(world.id, -1, 'up'), this.move(world.id, 1, 'down'));
                row.append(rank, naming, flags, moves);
                const conflict = this.conflict(...fields);
                if (conflict) {
                    row.append(conflict);
                }
                list.append(row);
            });
        return list;
    }

    limitRow(world) {
        const field = worldField(world.id, 'limit');
        const value = this.draft[field] ?? {shape: 'none'};
        const changed = () => !same(this.draft[field], this.base[field]);
        const row = element('div', 'row settings__row settings__limit');
        row.dataset.fields = field;
        row.title = this.changedBy(field);
        row.classList.toggle('is-conflict', this.conflicts.has(field));
        row.classList.toggle('is-changed', changed());
        row.classList.toggle('is-invalid', changed() && badLimit(value));
        const name = this.draft[worldField(world.id, 'name')] || world.defaultName;
        const text = element('span', 'row__text');
        text.append(element('span', 'settings__label', name), element('code', 'hint', world.id));
        const labelled = (label, control) => {
            const box = element('label', 'field');
            box.append(element('span', 'label', label), control);
            return box;
        };
        const shape = element('select', 'input input--small settings__shape');
        shape.dataset.field = `${field}:shape`;
        shape.setAttribute('aria-label', `${name}: ${t('settings.limit.shape')}`);
        for (const option of SHAPES) {
            const item = element('option', '', t(`settings.limit.${option}`));
            item.value = option;
            item.selected = value.shape === option;
            shape.append(item);
        }
        shape.addEventListener('change', () => {
            this.set(field, reshape(this.draft[field] ?? value, shape.value));
            this.render();
        });
        const line = element('div', 'settings__limit-line');
        line.append(labelled(t('settings.limit.shape'), shape));
        row.append(text, line);
        if (value.shape !== 'none') {
            const round = value.shape !== 'rectangle';
            const parts = round
                ? [
                      ['x', Math.floor((value.minX + value.maxX) / 2)],
                      ['z', Math.floor((value.minZ + value.maxZ) / 2)],
                      ['radius', (value.maxX - value.minX) / 2]
                  ]
                : [
                      ['fromX', value.minX],
                      ['fromZ', value.minZ],
                      ['toX', value.maxX],
                      ['toZ', value.maxZ]
                  ];
            const inputs = parts.map(([part, number]) => {
                const input = element('input', 'input input--small');
                input.inputMode = 'numeric';
                input.autocomplete = 'off';
                input.dataset.field = `${field}:${part}`;
                input.value = Number.isFinite(number) ? String(number) : '';
                line.append(labelled(t(`settings.limit.${part}`), input));
                return input;
            });
            const pick = (a, b, op) => (a === null || b === null ? null : op(a, b));
            inputs.forEach((input) =>
                input.addEventListener('input', () => {
                    const [a, b, c, d] = inputs.map((box) => coordinate(box.value));
                    const next = round
                        ? limit(
                              value.shape,
                              pick(a, c, (x, r) => x - r),
                              pick(b, c, (z, r) => z - r),
                              pick(a, c, (x, r) => x + r),
                              pick(b, c, (z, r) => z + r)
                          )
                        : limit(
                              value.shape,
                              pick(a, c, Math.min),
                              pick(b, d, Math.min),
                              pick(a, c, Math.max),
                              pick(b, d, Math.max)
                          );
                    this.set(field, next);
                    row.classList.toggle('is-changed', changed());
                    row.classList.toggle('is-invalid', changed() && badLimit(next));
                })
            );
        }
        const conflict = this.conflict(field);
        return conflict ? [row, conflict] : [row];
    }

    viewsRow(world) {
        const field = worldField(world.id, 'views');
        const value = this.draft[field] ?? 'both';
        const before = this.base[field] ?? 'both';
        const row = element('div', 'row settings__row settings__views');
        row.dataset.fields = field;
        row.title = this.changedBy(field);
        row.classList.toggle('is-conflict', this.conflicts.has(field));
        row.classList.toggle('is-changed', value !== before);
        const name = this.draft[worldField(world.id, 'name')] || world.defaultName;
        const text = element('span', 'row__text');
        text.append(element('span', 'settings__label', name), element('code', 'hint', world.id));
        const effects = [
            ...viewsOf(before)
                .filter((view) => !viewsOf(value).includes(view))
                .map((view) => t(`settings.views.drops.${view}`)),
            ...(addsView(before, value) ? [t('settings.views.renders')] : [])
        ];
        if (effects.length) {
            text.append(element('span', 'hint settings__effect', effects.join(' ')));
        }
        const group = element('div', 'segmented settings__segmented');
        group.dataset.views = field;
        group.setAttribute('role', 'group');
        group.setAttribute('aria-label', `${name}: ${t('settings.world.views')}`);
        for (const option of Object.keys(VIEWS)) {
            const button = element('button', 'btn btn--small', t(`settings.views.${option}`));
            button.type = 'button';
            button.dataset.field = `${field}:${option}`;
            button.setAttribute('aria-pressed', String(option === value));
            button.addEventListener('click', () => {
                const from = Object.keys(VIEWS).indexOf(this.draft[field] ?? 'both');
                this.set(field, option);
                this.render();
                const next = this.dialog.querySelector(`[data-views="${CSS.escape(field)}"]`);
                if (next && from >= 0) {
                    next.classList.add('is-instant');
                    next.style.setProperty('--at', String(from));
                    void next.offsetWidth;
                    next.classList.remove('is-instant');
                    next.style.removeProperty('--at');
                }
            });
            group.append(button);
        }
        row.append(text, group);
        const conflict = this.conflict(field);
        return conflict ? [row, conflict] : [row];
    }

    move(id, by, direction) {
        const index = this.display.indexOf(id);
        const target = index + by;
        const button = iconButton(direction, t(`settings.move.${direction}`), () => {
            [this.display[index], this.display[target]] = [this.display[target], this.display[index]];
            this.fixOrder();
            this.conflicts.delete(ORDER);
            this.updateStatus();
            this.render();
            const again = this.dialog.querySelector(`[data-field="${CSS.escape(`${ORDER}:${id}:${direction}`)}"]`);
            (again && !again.disabled
                ? again
                : this.dialog.querySelector(`[data-field^="${CSS.escape(`${ORDER}:${id}:`)}"]:not(:disabled)`)
            )?.focus();
        });
        button.dataset.field = `${ORDER}:${id}:${direction}`;
        button.disabled = target < 0 || target >= this.display.length;
        return button;
    }

    layerRow(layer) {
        const field = layerField(layer.id);
        const iconField = layerIconField(layer.id);
        const scaleField = layerScaleField(layer.id);
        const fields = [field, iconField, scaleField];
        const row = element('label', 'row row--action settings__row settings__layer');
        row.dataset.fields = fields.join(' ');
        row.title = fields.map((each) => this.changedBy(each)).filter(Boolean)[0] ?? '';
        row.classList.toggle(
            'is-conflict',
            fields.some((each) => this.conflicts.has(each))
        );
        row.classList.toggle(
            'is-changed',
            fields.some((each) => !same(this.draft[each], this.base[each]))
        );
        const text = element('span', 'row__text');
        const title = element('span', 'settings__label', this.label(field));
        text.append(
            title,
            element(
                'span',
                'hint',
                layer.present
                    ? t('settings.layerPermission', {
                          permission: `taerra.layer.${layer.id.replaceAll(':', '.')}`
                      })
                    : t('settings.layerGone')
            )
        );
        const {wrap} = switchInput(
            field,
            this.draft[field] === true,
            t('settings.privateLayer', {layer: title.textContent}),
            (checked) => {
                this.set(field, checked);
                this.render();
            }
        );
        const icon = this.draft[iconField] ?? '';
        const choose = element('button', 'icon-choice');
        choose.type = 'button';
        choose.dataset.field = iconField;
        const label = `${t('settings.layerIcon')}: ${this.format(iconField, icon)}`;
        choose.title = label;
        choose.setAttribute('aria-label', `${title.textContent}, ${label}`);
        choose.append(iconStore.image(iconStore.get(icon) ? icon : MARKER));
        choose.classList.toggle('is-default', !iconStore.get(icon));
        choose.addEventListener('click', async (event) => {
            event.preventDefault();
            const chosen = await iconChooser.choose(
                t('settings.layerIconTitle', {layer: title.textContent}),
                this.draft[iconField] ?? '',
                MARKER,
                t('icons.default')
            );
            if (chosen !== null) {
                this.set(iconField, chosen);
                this.render();
            }
            this.dialog.querySelector(`[data-field="${CSS.escape(iconField)}"]`)?.focus();
        });
        const scale = element('button', 'settings__scale-button', this.format(scaleField, this.draft[scaleField]));
        scale.type = 'button';
        scale.dataset.field = `${scaleField}:open`;
        scale.title = t('settings.layerScale');
        scale.setAttribute('aria-label', `${title.textContent}, ${t('settings.layerScale')}: ${scale.textContent}`);
        scale.setAttribute('aria-expanded', String(this.scaleOpen === layer.id));
        scale.addEventListener('click', (event) => {
            event.preventDefault();
            this.toggleScale(layer.id);
            this.dialog.querySelector(`[data-field="${CSS.escape(`${scaleField}:open`)}"]`)?.focus();
        });
        row.append(text, wrap, scale, choose);
        const rows = [row];
        if (this.scaleOpen === layer.id) {
            rows.push(this.scalePanel(layer, scaleField, title.textContent, row, scale));
        }
        const conflict = this.conflict(...fields);
        return conflict ? [...rows, conflict] : rows;
    }

    describeScale(scale) {
        if (scale === null) {
            return t('settings.layerScale.always');
        }
        return scale >= 0
            ? t('settings.layerScale.pixels', {pixels: numbers.format(2 ** scale)})
            : t('settings.layerScale.blocks', {blocks: numbers.format(2 ** -scale)});
    }

    scaleStops(value) {
        const {far, close} = mapManager.scaleRange(this.worlds());
        const current = parseScale(value);
        const from = Math.min(far + 1, close, current ?? Infinity);
        const to = Math.max(close, current ?? -Infinity);
        return [null, ...Array.from({length: to - from + 1}, (_, index) => from + index)];
    }

    toggleScale(id) {
        const open = this.scaleOpen !== id;
        this.closeScale();
        if (open) {
            this.scaleOpen = id;
            this.scaleZoom = mapManager.getMap().getZoom();
        }
        this.render();
    }

    closeScale() {
        if (this.scaleOpen === null) {
            return;
        }
        this.scaleOpen = null;
        this.dialog.classList.remove('is-peeking');
        markerManager.previewScale(null);
        mapManager.getMap().setZoom(this.scaleZoom, {animate: false});
    }

    peekScale(id, scale) {
        markerManager.previewScale(id, scale);
        mapManager
            .getMap()
            .setZoom(scale === null ? mapManager.activeMinZoom() : mapManager.zoomForScale(scale), {animate: false});
    }

    scalePanel(layer, field, title, row, button) {
        const stops = this.scaleStops(this.draft[field]);
        const panel = element('div', 'row settings__row settings__row--sub settings__scale');
        panel.dataset.fields = field;
        const slider = document.createElement('input');
        slider.type = 'range';
        slider.className = 'settings__slider';
        slider.dataset.field = field;
        slider.min = '0';
        slider.max = String(stops.length - 1);
        slider.step = '1';
        slider.value = String(Math.max(0, stops.indexOf(parseScale(this.draft[field]))));
        slider.setAttribute('aria-label', t('settings.layerScaleTitle', {layer: title}));
        slider.style.setProperty('--steps', String(stops.length - 1));
        const fill = () => slider.style.setProperty('--at', String(Number(slider.value) / (stops.length - 1)));
        fill();
        const shown = element('output', 'settings__scale-value');
        const choose = (scale) => {
            fill();
            const text = this.describeScale(scale);
            shown.textContent = text;
            button.textContent = text;
            slider.setAttribute('aria-valuetext', text);
            this.set(field, scaleText(scale));
            row.classList.toggle(
                'is-changed',
                [layerField(layer.id), layerIconField(layer.id), field].some(
                    (each) => !same(this.draft[each], this.base[each])
                )
            );
            this.peekScale(layer.id, scale);
        };
        shown.textContent = this.describeScale(stops[Number(slider.value)]);
        slider.setAttribute('aria-valuetext', shown.textContent);
        slider.addEventListener('input', () => choose(stops[Number(slider.value)]));
        slider.addEventListener('pointerdown', () => {
            this.dialog.classList.add('is-peeking');
            const done = () => {
                this.dialog.classList.remove('is-peeking');
                removeEventListener('pointerup', done);
                removeEventListener('pointercancel', done);
            };
            addEventListener('pointerup', done);
            addEventListener('pointercancel', done);
        });
        slider.addEventListener('keydown', (event) => event.key !== 'Tab' && this.dialog.classList.add('is-peeking'));
        for (const type of ['keyup', 'blur']) {
            slider.addEventListener(type, () => this.dialog.classList.remove('is-peeking'));
        }
        const ends = element('span', 'settings__scale-ends');
        ends.append(
            element('span', '', t('settings.layerScale.always')),
            element('span', '', t('settings.layerScale.closest'))
        );
        const track = element('span', 'settings__scale-track');
        track.append(slider, ends);
        const here = element('button', 'btn btn--small', t('settings.layerScale.useMap'));
        here.type = 'button';
        here.addEventListener('click', () => {
            const scale = Math.floor(mapManager.scaleAt(this.scaleZoom));
            const index = scale < stops[1] ? 0 : Math.min(stops.length - 1, stops.indexOf(scale));
            slider.value = String(index < 0 ? stops.length - 1 : index);
            choose(stops[Number(slider.value)]);
        });
        const head = element('div', 'settings__scale-head');
        const label = element('span', 'settings__scale-label', t('settings.layerScale'));
        label.append(shown);
        head.append(label, track);
        panel.append(head, element('span', 'hint', t('settings.layerScale.hint')), here);
        return panel;
    }

    changes() {
        const changes = {};
        for (const [field, value] of Object.entries(this.draft)) {
            if (field === ORDER) {
                if (!same(value, this.base[ORDER])) {
                    changes[ORDER] = value;
                }
            } else if (!same(value, this.base[field])) {
                const setting = field.startsWith('setting:') ? this.setting(field.slice('setting:'.length)) : null;
                if (!setting || setting.editable) {
                    changes[field] = value;
                }
            }
        }
        return changes;
    }

    problem(changes) {
        const fields = Object.keys(changes);
        const number = fields.find(
            (field) =>
                field.startsWith('setting:') && outOfRange(this.setting(field.slice('setting:'.length)), changes[field])
        );
        if (number) {
            const setting = this.setting(number.slice('setting:'.length));
            return t('settings.range', {
                name: this.label(number),
                min: numbers.format(setting.min),
                max: numbers.format(setting.max)
            });
        }
        const limits = fields.filter(isLimit);
        const bad = limits.find((field) => badLimit(changes[field]));
        if (bad) {
            const id = bad.slice('world:'.length, bad.lastIndexOf(':'));
            return t('settings.limitInvalid', {
                world: this.draft[worldField(id, 'name')] || this.world(id)?.defaultName || id
            });
        }
        const renders =
            limits.length +
            fields.filter(
                (field) =>
                    isViews(field) &&
                    this.draft[worldField(field.slice('world:'.length, field.lastIndexOf(':')), 'enabled')] === true &&
                    addsView(this.base[field], changes[field])
            ).length;
        return renders > 1 ? t('settings.limitOne') : null;
    }

    updateStatus() {
        if (!this.draft) {
            return;
        }
        const changes = this.changes();
        const count =
            Object.keys(changes).length - (ORDER in changes && sameOrder(changes[ORDER], this.base[ORDER]) ? 1 : 0);
        const conflicts = this.conflicts.size;
        const problem = this.problem(changes);
        $('settings-save').disabled = count === 0 || conflicts > 0 || Boolean(problem);
        const status = $('settings-status');
        if (conflicts) {
            this.showStatus(
                t(`settings.conflicts.${plurals.select(conflicts)}`, {count: numbers.format(conflicts)}),
                'is-conflict'
            );
        } else if (problem) {
            this.showStatus(problem, 'is-conflict');
        } else if (count) {
            this.showStatus(t(`settings.unsaved.${plurals.select(count)}`, {count: numbers.format(count)}), 'is-dirty');
        } else if (!['is-live', 'is-done', 'is-failed'].some((state) => status.classList.contains(state))) {
            this.showStatus(null);
        }
    }

    async save() {
        if (!this.draft || this.conflicts.size) {
            return;
        }
        const changes = this.changes();
        if (!Object.keys(changes).length || this.problem(changes)) {
            return;
        }
        this.disarm();
        $('settings-save').disabled = true;
        $('settings-save').classList.add('is-busy');
        let response = null;
        try {
            response = await fetch('api/settings', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({base: this.view.revision, changes})
            });
        } catch {}
        $('settings-save').classList.remove('is-busy');
        const body =
            response && (response.status === 200 || response.status === 409)
                ? await response.json().catch(() => null)
                : null;
        if (response?.status === 200 && body) {
            const before = this.base;
            this.base = {...this.base, ...structuredClone(changes)};
            this.rebase(body);
            const views = Object.keys(changes).some(
                (field) => isViews(field) && addsView(before[field], changes[field])
            );
            this.showStatus(
                t(
                    Object.keys(changes).some(isLimit)
                        ? 'settings.savedLimit'
                        : views
                          ? 'settings.savedViews'
                          : Object.keys(changes).some(isStorage)
                            ? 'settings.savedStorage'
                            : 'settings.saved'
                ),
                'is-done'
            );
            return;
        }
        if (response?.status === 409 && body) {
            this.rebase(body);
            if (body.busy) {
                this.showStatus(t(isStorage(body.busy) ? 'settings.storageBusy' : 'settings.limitBusy'), 'is-failed');
            } else {
                this.updateStatus();
            }
            return;
        }
        if (response?.status === 400) {
            const invalid = await response.json().catch(() => null);
            this.showStatus(
                invalid?.unreachable
                    ? t('settings.storageFailed', {reason: invalid.unreachable})
                    : t('settings.invalid', {name: this.label(invalid?.invalid ?? '')}),
                'is-failed'
            );
        } else {
            this.showStatus(
                t(response?.status === 401 || response?.status === 403 ? 'settings.denied' : 'settings.failed'),
                'is-failed'
            );
        }
        $('settings-save').disabled = false;
    }

    showStatus(message, state) {
        clearTimeout(this.statusTimer);
        const status = $('settings-status');
        status.className = `note${state ? ` ${state}` : ''}`;
        status.textContent = message ?? '';
        if (message && (state === 'is-done' || state === 'is-live')) {
            this.statusTimer = setTimeout(() => {
                status.className = 'note';
                status.textContent = '';
                this.updateStatus();
            }, STATUS_MILLIS);
        }
    }
}

export const settingsDialog = new SettingsDialog();
