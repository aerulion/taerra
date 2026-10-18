import {numbers, t} from './i18n.js';
import {mapManager} from './map.js';
import {ColorPicker} from './colors.js';
import {element} from './markers.js';
import {regionManager} from './regions.js';

const $ = (id) => document.getElementById(id);

const fold = (text) => text.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase();

const regionName = (region) => region.name || t('regions.unnamed');

function meta(region) {
    return [
        t(`region.fog.${region.fog ?? 'hide'}`),
        t('regions.size', {count: numbers.format(region.size ?? region.chunks?.length ?? 0)}),
        region.fog !== 'none' && region.discoverable
            ? t('regions.found', {count: numbers.format(region.discoveries ?? 0)}) : null
    ].filter(Boolean).join(', ');
}

function arm(button, key, confirmKey) {
    if (button.classList.contains('is-armed')) {
        return true;
    }
    button.classList.add('is-armed');
    button.textContent = t(confirmKey);
    button.dataset.i18n = confirmKey;
    button.dataset.idle = key;
    return false;
}

function disarm(button) {
    if (button.dataset.idle) {
        button.classList.remove('is-armed');
        button.textContent = t(button.dataset.idle);
        button.dataset.i18n = button.dataset.idle;
    }
}

class RegionEditor {
    constructor() {
        this.dialog = $('region-editor');
        this.target = null;
        this.fog = 'hide';
        this.color = null;
        this.saved = false;
    }

    initialize() {
        this.picker = new ColorPicker($('region-color'), (hex) => {
            this.color = hex;
            this.dialog.style.setProperty('--waypoint', hex);
            this.target?.onColor?.(hex);
        });
        for (const button of $('region-fog').querySelectorAll('[data-fog]')) {
            button.addEventListener('click', () => this.setFog(button.dataset.fog));
        }
        $('region-cancel').addEventListener('click', () => this.dialog.close());
        $('region-delete').addEventListener('click', () => void this.remove());
        $('region-form').addEventListener('input', () => this.showError(null));
        $('region-form').addEventListener('submit', (event) => {
            event.preventDefault();
            void this.save();
        });
        this.dialog.addEventListener('click', (event) => {
            if (event.target === this.dialog) {
                this.dialog.close();
            }
        });
        this.dialog.addEventListener('close', () => {
            if (!this.saved && this.target) {
                this.target.onColor?.(this.target.color);
            }
            this.target = null;
        });
    }

    open(target) {
        const region = target.region;
        this.target = target;
        this.saved = false;
        $('region-title').textContent = region?.id ? regionName(region) : t('regions.new');
        $('region-where').textContent = meta({...region, size: target.size});
        $('region-name').value = region?.name ?? '';
        $('region-discoverable').checked = region?.discoverable ?? true;
        this.picker.set(target.color);
        disarm($('region-delete'));
        $('region-delete').hidden = !region?.id;
        this.setFog(region?.fog ?? 'hide');
        this.showError(null);
        this.dialog.showModal();
        $('region-name').focus();
    }

    setFog(fog) {
        this.fog = fog;
        for (const button of $('region-fog').querySelectorAll('[data-fog]')) {
            button.setAttribute('aria-pressed', String(button.dataset.fog === fog));
        }
        $('region-discoverable').closest('label').hidden = fog === 'none';
    }

    showError(problem) {
        $('region-error').hidden = !problem;
        $('region-error').textContent = problem ? t(`region.problem.${problem}`) : '';
    }

    async save() {
        const target = this.target;
        if (!target) {
            return;
        }
        const problem = await target.onSave({
            name: $('region-name').value.trim(), color: this.color, fog: this.fog,
            discoverable: this.fog !== 'none' && $('region-discoverable').checked
        });
        if (problem) {
            this.showError(problem);
            return;
        }
        this.saved = true;
        this.dialog.close();
    }

    async remove() {
        const button = $('region-delete');
        if (!arm(button, 'waypoint.delete', 'waypoint.confirmDelete')) {
            return;
        }
        const problem = await this.target?.onDelete();
        if (problem) {
            disarm(button);
            this.showError(problem);
            return;
        }
        this.saved = true;
        this.dialog.close();
    }

}

class RegionList {
    constructor() {
        this.dialog = $('regions');
        this.actions = null;
    }

    initialize(actions) {
        this.actions = actions;
        $('regions-filter').placeholder = t('manager.filter');
        $('regions-open').addEventListener('click', () => this.open());
        $('regions-close').addEventListener('click', () => this.dialog.close());
        $('regions-filter').addEventListener('input', () => this.render());
        $('regions-new').addEventListener('click', () => {
            this.dialog.close();
            this.actions.create();
        });
        this.dialog.addEventListener('click', (event) => {
            if (event.target === this.dialog) {
                this.dialog.close();
            }
        });
    }

    open() {
        $('regions-filter').value = '';
        this.render();
        this.dialog.showModal();
    }

    refresh() {
        if (this.dialog.open) {
            this.render();
        }
    }

    render() {
        const filter = fold($('regions-filter').value.trim());
        const regions = regionManager.regions
            .filter(region => !filter || fold(regionName(region)).includes(filter))
            .sort((a, b) => regionName(a).localeCompare(regionName(b)) || a.id - b.id);
        $('regions-count').textContent = numbers.format(regionManager.regions.length);
        $('regions-list').replaceChildren(...regions.map(region => this.row(region)));
        $('regions-empty').hidden = regions.length > 0;
        $('regions-empty').textContent = t(regionManager.regions.length === 0 ? 'regions.none' : 'manager.noMatch');
    }

    row(region) {
        const row = element('li', 'manager__row manager__row--region');
        const icon = element('span', 'search__icon search__icon--area');
        icon.style.setProperty('--marker', region.color);
        const text = element('button', 'manager__text');
        text.type = 'button';
        text.title = t('manager.showOnMap');
        text.append(element('span', 'manager__name', regionName(region)), element('span', 'manager__meta', meta(region)));
        text.addEventListener('click', () => {
            this.dialog.close();
            const [x, z] = region.label;
            mapManager.centerOnBlock(x, z);
        });
        const actions = element('span', 'manager__actions');
        const action = (name, label, run) => {
            const button = element('button', 'manager__action');
            button.type = 'button';
            button.dataset.action = name;
            button.title = t(label);
            button.setAttribute('aria-label', `${t(label)}: ${regionName(region)}`);
            button.addEventListener('click', () => void run(button));
            actions.append(button);
        };
        action('edit', 'waypoint.editShort', () => {
            this.dialog.close();
            const [x, z] = region.label;
            mapManager.centerOnBlock(x, z);
            this.actions.edit(region);
        });
        const confirmed = (confirmKey, path, method) => async (button) => {
            if (!button.classList.contains('is-armed')) {
                button.classList.add('is-armed');
                button.title = t(confirmKey);
                return;
            }
            let response = null;
            try {
                response = await fetch(`/api/regions/${region.id}${path}`, {method});
            } catch {
            }
            button.classList.remove('is-armed');
            if (!response?.ok) {
                button.title = t('region.problem.failed');
            }
        };
        if (region.fog !== 'none' && region.discoverable && region.discoveries > 0) {
            action('reset', 'region.reset', confirmed('region.resetConfirm', '/reset', 'POST'));
        }
        action('delete', 'waypoint.delete', confirmed('waypoint.confirmDelete', '', 'DELETE'));
        row.append(icon, text, actions);
        return row;
    }
}

export const regionEditor = new RegionEditor();
export const regionList = new RegionList();
