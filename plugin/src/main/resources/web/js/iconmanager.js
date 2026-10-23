import {accountManager} from './account.js';
import {language, numbers, plurals, t} from './i18n.js';
import {iconEditor} from './iconeditor.js';
import {fold, iconStore, NOT_NAME} from './icons.js';
import {armed, element, iconButton} from './ui.js';

const $ = (id) => document.getElementById(id);

const MAX = 256;
const MAX_BYTES = 16 * 1024;
const SIZE = 8;
const STATUS_MILLIS = 6000;
const ERRORS = {400: 'icons.badName', 409: 'icons.taken', 415: 'icons.wrongSize', 422: 'icons.full'};

function nameOf(file) {
    const name = file.name.replace(/\.png$/i, '').replace(/[_-]+/g, ' ').normalize('NFC').replace(NOT_NAME, '')
        .trim().slice(0, 32).trim();
    return /[\p{L}\p{N}]/u.test(name) ? name : t('icons.unnamed');
}

async function read(file) {
    if (file.type !== 'image/png' && !/\.png$/i.test(file.name)) {
        return {problem: t('icons.notPng', {file: file.name})};
    }
    if (file.size > MAX_BYTES) {
        return {problem: t('icons.tooBig', {file: file.name})};
    }
    let bitmap;
    try {
        bitmap = await createImageBitmap(file);
    } catch {
        return {problem: t('icons.notPng', {file: file.name})};
    }
    const {width, height} = bitmap;
    bitmap.close();
    if (width !== SIZE || height !== SIZE) {
        return {problem: t('icons.size', {file: file.name, width, height})};
    }
    const bytes = new Uint8Array(await file.arrayBuffer());
    let binary = '';
    bytes.forEach(byte => binary += String.fromCharCode(byte));
    return {png: btoa(binary)};
}

class IconManager {
    constructor() {
        this.dialog = $('icons');
        this.replacing = null;
        this.statusTimer = 0;
    }

    initialize() {
        $('icons-filter').placeholder = t('manager.filter');
        $('icons-open').addEventListener('click', () => this.open());
        $('icons-filter').addEventListener('input', () => this.render());
        $('icons-upload').addEventListener('click', () => $('icons-file').click());
        $('icons-draw').addEventListener('click', () => void iconEditor.open({save: (name, png) => this.drawn(name, png)}));
        $('icons-file').addEventListener('change', () => {
            const files = [...$('icons-file').files];
            $('icons-file').value = '';
            void this.upload(files);
        });
        $('icons-replace-file').addEventListener('change', () => {
            const file = $('icons-replace-file').files?.[0];
            $('icons-replace-file').value = '';
            if (file && this.replacing) {
                void this.replace(this.replacing, file);
            }
        });
        this.dialog.addEventListener('dragover', (event) => event.preventDefault());
        this.dialog.addEventListener('drop', (event) => {
            event.preventDefault();
            void this.upload([...event.dataTransfer?.files ?? []]);
        });
        iconStore.onChange(() => this.dialog.open && this.render());
    }

    open() {
        if (!accountManager.canEditIcons()) {
            return;
        }
        $('account-menu').hidePopover?.();
        $('icons-filter').value = '';
        this.show('');
        this.render();
        this.dialog.showModal();
        void iconStore.load();
    }

    uploads() {
        return iconStore.list().filter(icon => icon.source === 'upload');
    }

    render() {
        const uploads = this.uploads();
        const query = fold($('icons-filter').value.trim());
        const shown = uploads.filter(icon => !query || fold(icon.label).includes(query))
            .sort((a, b) => a.label.localeCompare(b.label, language));
        $('icons-count').textContent = `${numbers.format(uploads.length)} / ${numbers.format(MAX)}`;
        $('icons-upload').disabled = uploads.length >= MAX;
        $('icons-draw').disabled = uploads.length >= MAX;
        const focused = document.activeElement?.dataset?.key;
        $('icons-list').replaceChildren(...shown.map(icon => this.row(icon)));
        if (focused) {
            this.dialog.querySelector(`[data-key="${CSS.escape(focused)}"]`)?.focus();
        }
        $('icons-empty').hidden = shown.length > 0;
        $('icons-empty').textContent = t(uploads.length === 0 ? 'icons.none' : 'icons.noMatch');
    }

    row(icon) {
        const row = element('li', 'row manager__row icons__row');
        const preview = element('span', 'icons__preview');
        preview.append(iconStore.image(icon.id));
        const text = element('div', 'row__text icons__text');
        const name = element('input', 'input input--small icons__name');
        name.value = icon.label;
        name.maxLength = 32;
        name.autocomplete = 'off';
        name.spellcheck = false;
        name.dataset.key = icon.id;
        name.setAttribute('aria-label', t('icons.rename', {name: icon.label}));
        name.addEventListener('input', () => {
            const clean = name.value.normalize('NFC').replace(NOT_NAME, '');
            if (clean !== name.value) {
                name.value = clean;
            }
        });
        name.addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                name.blur();
            } else if (event.key === 'Escape' && name.value !== icon.label) {
                event.preventDefault();
                name.value = icon.label;
                name.blur();
            }
        });
        name.addEventListener('change', () => void this.rename(icon, name));
        const uses = icon.uses ?? 0;
        text.append(name, element('span', 'row__meta',
            uses === 0 ? t('icons.unused') : t(`icons.uses.${plurals.select(uses)}`, {count: numbers.format(uses)})));
        const actions = element('span', 'manager__actions');
        const draw = iconButton('edit', t('icons.draw.edit'), () => void iconEditor.open({
            icon, url: iconStore.url(icon.id), save: (_, png) => this.redrawn(icon, png)
        }));
        draw.setAttribute('aria-label', `${t('icons.draw.edit')}: ${icon.label}`);
        const replace = iconButton('refresh', t('icons.replace'), () => {
            this.replacing = icon;
            $('icons-replace-file').click();
        });
        replace.setAttribute('aria-label', `${t('icons.replace')}: ${icon.label}`);
        const remove = iconButton('delete', t('waypoint.delete'), (button) => void this.delete(icon, button));
        remove.setAttribute('aria-label', `${t('waypoint.delete')}: ${icon.label}`);
        remove.classList.add('icon-btn--danger');
        actions.append(draw, replace, remove);
        row.append(preview, text, actions);
        return row;
    }

    async send(url, method, body) {
        try {
            return await fetch(url, {
                method,
                headers: body ? {'Content-Type': 'application/json'} : {},
                body: body ? JSON.stringify(body) : undefined
            });
        } catch {
            return null;
        }
    }

    problem(response, params) {
        return t(ERRORS[response?.status] ?? 'icons.failed', {max: numbers.format(MAX), ...params});
    }

    async upload(files) {
        if (files.length === 0) {
            return;
        }
        let added = 0;
        const problems = [];
        for (const file of files) {
            const {png, problem} = await read(file);
            if (problem) {
                problems.push(problem);
                continue;
            }
            const base = nameOf(file);
            let response = null;
            for (let attempt = 1; attempt <= 9; attempt++) {
                const name = attempt === 1 ? base : `${base.slice(0, 29).trim()} ${attempt}`;
                response = await this.send('api/icons', 'POST', {name, png});
                if (response?.status !== 409) {
                    break;
                }
            }
            if (response?.ok) {
                added++;
            } else {
                problems.push(this.problem(response, {name: base, file: file.name, width: '?', height: '?'}));
                if (response?.status === 422) {
                    break;
                }
            }
        }
        await iconStore.load();
        this.show([added > 0 ? t(`icons.uploaded.${plurals.select(added)}`, {count: numbers.format(added)}) : '',
            ...problems].filter(Boolean).join(' '), problems.length > 0);
    }

    async drawn(name, png) {
        const response = await this.send('api/icons', 'POST', {name, png});
        if (!response?.ok) {
            return this.problem(response, {name});
        }
        await iconStore.load();
        this.show(t('icons.uploaded.one', {count: numbers.format(1)}));
        return null;
    }

    async redrawn(icon, png) {
        const response = await this.send(this.url(icon), 'PUT', {png});
        if (!response?.ok) {
            return this.problem(response, {name: icon.label});
        }
        await iconStore.load();
        this.show(t('icons.replaced', {name: icon.label}));
        return null;
    }

    async replace(icon, file) {
        const {png, problem} = await read(file);
        const response = problem ? null : await this.send(this.url(icon), 'PUT', {png});
        if (!response?.ok) {
            this.show(problem ?? this.problem(response, {name: icon.label}), true);
            return;
        }
        await iconStore.load();
        this.show(t('icons.replaced', {name: icon.label}));
    }

    async rename(icon, input) {
        const name = input.value.trim();
        if (name === icon.label) {
            return;
        }
        const response = /[\p{L}\p{N}]/u.test(name) ? await this.send(this.url(icon), 'PUT', {name}) : null;
        if (!response?.ok) {
            input.value = icon.label;
            this.show(response ? this.problem(response, {name}) : t('icons.badName'), true);
            return;
        }
        await iconStore.load();
    }

    async delete(icon, button) {
        if (!armed(button, t('waypoint.confirmDelete'))) {
            return;
        }
        const response = await this.send(this.url(icon), 'DELETE');
        if (!response?.ok) {
            this.show(t('icons.failed'), true);
        }
        await iconStore.load();
    }

    url(icon) {
        return `api/icons/${icon.id.replace(':', '/')}`;
    }

    show(message, failed = false) {
        clearTimeout(this.statusTimer);
        $('icons-status').textContent = message;
        $('icons-status').classList.toggle('is-failed', Boolean(message) && failed);
        $('icons-status').classList.toggle('is-done', Boolean(message) && !failed);
        if (message) {
            this.statusTimer = setTimeout(() => this.show(''), STATUS_MILLIS);
        }
    }
}

export const iconManager = new IconManager();
