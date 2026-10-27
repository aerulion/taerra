import {ColorPicker} from './colors.js';
import {MAC, t} from './i18n.js';
import {monochrome} from './pixels.js';
import {badgeFrame, NOT_NAME, tintFilter} from './icons.js';
import {armed, disarm, element} from './ui.js';

const $ = (id) => document.getElementById(id);

const SIZE = 8;
const HISTORY = 200;
const PREVIEWS = ['#f9fffe', '#3ab3da', '#1d1d21'];
const WHITE = '#ffffff';

const opaque = (hex) => ((parseInt(hex.slice(1), 16) << 8) | 0xff) >>> 0;
const css = (pixel) =>
    `rgb(${pixel >>> 24} ${(pixel >>> 16) & 0xff} ${(pixel >>> 8) & 0xff} / ${(pixel & 0xff) / 255})`;

function image(pixels) {
    const canvas = element('canvas', '');
    canvas.width = canvas.height = SIZE;
    const context = canvas.getContext('2d');
    const data = context.createImageData(SIZE, SIZE);
    pixels.forEach((pixel, i) =>
        data.data.set([pixel >>> 24, (pixel >>> 16) & 0xff, (pixel >>> 8) & 0xff, pixel & 0xff], i * 4)
    );
    context.putImageData(data, 0, 0);
    return canvas.toDataURL('image/png');
}

async function read(url) {
    const bitmap = await createImageBitmap(await (await fetch(url)).blob());
    const canvas = element('canvas', '');
    canvas.width = canvas.height = SIZE;
    const context = canvas.getContext('2d', {willReadFrequently: true});
    context.drawImage(bitmap, 0, 0);
    bitmap.close();
    const data = context.getImageData(0, 0, SIZE, SIZE).data;
    return Array.from(
        {length: SIZE * SIZE},
        (_, i) => ((data[i * 4] << 24) | (data[i * 4 + 1] << 16) | (data[i * 4 + 2] << 8) | data[i * 4 + 3]) >>> 0
    );
}

class IconEditor {
    constructor() {
        this.dialog = null;
        this.pixels = new Array(SIZE * SIZE).fill(0);
        this.history = [];
        this.future = [];
        this.stroke = null;
        this.erasing = false;
        this.color = WHITE;
        this.save = null;
    }

    initialize() {
        this.dialog = $('icon-editor');
        this.picker = new ColorPicker($('icon-editor-color'), (hex) => (this.color = hex));
        const grid = $('icon-editor-grid');
        grid.replaceChildren(
            ...this.pixels.map((_, i) => {
                const cell = element('button', 'icon-editor__cell');
                cell.type = 'button';
                cell.tabIndex = -1;
                cell.dataset.index = String(i);
                cell.setAttribute(
                    'aria-label',
                    t('iconEditor.pixel', {x: (i % SIZE) + 1, y: Math.floor(i / SIZE) + 1})
                );
                cell.addEventListener('click', (event) => {
                    if (event.detail === 0) {
                        this.begin(this.erasing);
                        this.paint(i);
                        this.end();
                    }
                });
                return cell;
            })
        );
        grid.addEventListener('contextmenu', (event) => event.preventDefault());
        grid.addEventListener('pointerdown', (event) => {
            const cell = event.target.closest('.icon-editor__cell');
            if (!cell || (event.button !== 0 && event.button !== 2)) {
                return;
            }
            event.preventDefault();
            grid.setPointerCapture(event.pointerId);
            this.focusCell(Number(cell.dataset.index), true);
            this.begin(this.erasing || event.button === 2);
            this.paint(Number(cell.dataset.index));
        });
        grid.addEventListener('pointermove', (event) => {
            const cell =
                this.stroke && document.elementFromPoint(event.clientX, event.clientY)?.closest('.icon-editor__cell');
            if (cell && grid.contains(cell)) {
                this.line(Number(cell.dataset.index));
            }
        });
        grid.addEventListener('pointerup', () => this.end());
        grid.addEventListener('pointercancel', () => this.end());
        grid.addEventListener('keydown', (event) => {
            const moves = {ArrowLeft: -1, ArrowRight: 1, ArrowUp: -SIZE, ArrowDown: SIZE};
            const index = Number(event.target.dataset.index);
            const next = index + (moves[event.key] ?? 0);
            if (
                moves[event.key] &&
                next >= 0 &&
                next < SIZE * SIZE &&
                (Math.abs(moves[event.key]) === SIZE || Math.floor(next / SIZE) === Math.floor(index / SIZE))
            ) {
                event.preventDefault();
                this.focusCell(next, true);
            }
        });

        this.dialog.querySelectorAll('[data-tool]').forEach((button) =>
            button.addEventListener('click', () => {
                this.erasing = button.dataset.tool === 'erase';
                this.renderTools();
            })
        );
        $('icon-editor-undo').addEventListener('click', () => this.undo());
        $('icon-editor-redo').addEventListener('click', () => this.redo());
        $('icon-editor-clear').addEventListener('click', () => {
            this.begin(true);
            this.pixels.forEach((_, i) => this.paint(i));
            this.end();
        });
        $('icon-editor-name').addEventListener('input', () => {
            const name = $('icon-editor-name');
            const clean = name.value.normalize('NFC').replace(NOT_NAME, '');
            if (clean !== name.value) {
                name.value = clean;
            }
        });
        this.dialog.addEventListener('keydown', (event) => {
            const command = MAC ? event.metaKey : event.ctrlKey;
            if (!command || event.target.closest('input[type="text"], input[type="search"], textarea')) {
                return;
            }
            const key = event.key.toLowerCase();
            if (key === 'z' || key === 'y') {
                event.preventDefault();
                key === 'y' || event.shiftKey ? this.redo() : this.undo();
            }
        });
        this.dialog.addEventListener('cancel', (event) => {
            if (this.history.length > 0 && !armed($('icon-editor-cancel'), t('settings.discard'))) {
                event.preventDefault();
                $('icon-editor-cancel').focus();
            }
        });
        this.dialog.addEventListener('close', () => {
            disarm($('icon-editor-cancel'));
            this.save = null;
        });
        $('icon-editor-form').addEventListener('submit', (event) => {
            event.preventDefault();
            void this.submit();
        });
    }

    async open({icon = null, url = null, save}) {
        this.pixels = url ? await read(url) : new Array(SIZE * SIZE).fill(0);
        this.history = [];
        this.future = [];
        this.stroke = null;
        this.erasing = false;
        this.save = save;
        this.color = WHITE;
        this.picker.set(WHITE);
        $('icon-editor-title').textContent = icon ? t('iconEditor.edit', {name: icon.label}) : t('iconEditor.new');
        $('icon-editor-name-field').hidden = Boolean(icon);
        $('icon-editor-name').value = '';
        this.showError(null);
        this.render();
        this.renderTools();
        this.dialog.showModal();
        icon ? this.focusCell(0, true) : $('icon-editor-name').focus();
    }

    focusCell(index, focus = false) {
        this.dialog.querySelectorAll('.icon-editor__cell').forEach((cell, i) => (cell.tabIndex = i === index ? 0 : -1));
        if (focus) {
            this.dialog.querySelector(`[data-index="${index}"]`).focus();
        }
    }

    begin(erasing) {
        this.stroke = {before: this.pixels.slice(), value: erasing ? 0 : opaque(this.color), changed: false};
    }

    paint(index) {
        if (!this.stroke) {
            return;
        }
        this.stroke.last = index;
        if (this.pixels[index] === this.stroke.value) {
            return;
        }
        this.pixels[index] = this.stroke.value;
        this.stroke.changed = true;
        this.render();
    }

    line(to) {
        const from = this.stroke.last ?? to;
        const [x0, y0, x1, y1] = [from % SIZE, Math.floor(from / SIZE), to % SIZE, Math.floor(to / SIZE)];
        const steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (let step = 1; step <= steps; step++) {
            this.paint(
                Math.round(y0 + ((y1 - y0) * step) / steps) * SIZE + Math.round(x0 + ((x1 - x0) * step) / steps)
            );
        }
    }

    end() {
        if (this.stroke?.changed) {
            this.history.push(this.stroke.before);
            if (this.history.length > HISTORY) {
                this.history.shift();
            }
            this.future = [];
            this.renderTools();
        }
        this.stroke = null;
    }

    undo() {
        this.step(this.history, this.future);
    }

    redo() {
        this.step(this.future, this.history);
    }

    step(from, to) {
        if (this.stroke || from.length === 0) {
            return;
        }
        to.push(this.pixels);
        this.pixels = from.pop();
        this.render();
        this.renderTools();
    }

    render() {
        this.dialog.querySelectorAll('.icon-editor__cell').forEach((cell, i) => {
            const pixel = this.pixels[i];
            cell.style.background = pixel & 0xff ? css(pixel) : '';
            cell.classList.toggle('is-empty', !(pixel & 0xff));
        });
        const tint = monochrome(this.pixels);
        const squares = this.pixels
            .map((pixel, i) =>
                pixel & 0xff
                    ? `<rect x="${i % SIZE}" y="${Math.floor(i / SIZE)}" width="1" height="1" fill="${css(pixel)}"/>`
                    : ''
            )
            .join('');
        $('icon-editor-previews').replaceChildren(
            ...PREVIEWS.map((color) => {
                const preview = element('span', 'icon-editor__preview');
                preview.style.color = color;
                preview.innerHTML = badgeFrame(
                    `<g transform="translate(2 2)"${tintFilter(tint, color)}>${squares}</g>`
                );
                return preview;
            })
        );
        $('icon-editor-tint').hidden = !tint;
    }

    renderTools() {
        this.dialog
            .querySelectorAll('[data-tool]')
            .forEach((button) =>
                button.setAttribute('aria-pressed', String((button.dataset.tool === 'erase') === this.erasing))
            );
        $('icon-editor-undo').disabled = this.history.length === 0;
        $('icon-editor-redo').disabled = this.future.length === 0;
    }

    showError(message) {
        $('icon-editor-error').hidden = !message;
        $('icon-editor-error').textContent = message ?? '';
    }

    async submit() {
        const name = $('icon-editor-name').value.trim();
        if (!$('icon-editor-name-field').hidden && !/[\p{L}\p{N}]/u.test(name)) {
            this.showError(t('icons.badName'));
            $('icon-editor-name').focus();
            return;
        }
        if (!this.pixels.some((pixel) => pixel & 0xff)) {
            this.showError(t('iconEditor.empty'));
            return;
        }
        const problem = await this.save?.(name, image(this.pixels).split(',')[1]);
        if (problem) {
            this.showError(problem);
            return;
        }
        this.history = [];
        this.dialog.close();
    }
}

export const iconEditor = new IconEditor();
