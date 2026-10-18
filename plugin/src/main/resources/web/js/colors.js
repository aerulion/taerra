import {t} from './i18n.js';
import {element} from './markers.js';

export const DYES = [
    ['white', '#f9fffe'], ['light_gray', '#9d9d97'], ['gray', '#474f52'], ['black', '#1d1d21'],
    ['brown', '#835432'], ['red', '#b02e26'], ['orange', '#f9801d'], ['yellow', '#fed83d'],
    ['lime', '#80c71f'], ['green', '#5e7c16'], ['cyan', '#169c9c'], ['light_blue', '#3ab3da'],
    ['blue', '#3c44aa'], ['purple', '#8932b8'], ['magenta', '#c74ebd'], ['pink', '#f38baa']
];
export const DEFAULT_COLOR = '#3ab3da';
export const HEX = /^#[0-9a-f]{6}$/i;
const SHADE_STEP = .02;

let pickers = 0;

function hsvToHex(h, s, v) {
    const channel = (n) => {
        const k = (n + h / 60) % 6;
        return v - v * s * Math.max(0, Math.min(k, 4 - k, 1));
    };
    return `#${[5, 3, 1].map(n => Math.round(channel(n) * 255).toString(16).padStart(2, '0')).join('')}`;
}

function hexToHsv(hex) {
    const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255);
    const max = Math.max(r, g, b);
    const range = max - Math.min(r, g, b);
    const h = range === 0 ? 0
        : max === r ? ((g - b) / range + 6) % 6
            : max === g ? (b - r) / range + 2
                : (r - g) / range + 4;
    return {h: h * 60, s: max === 0 ? 0 : range / max, v: max};
}

const clamp = (value) => Math.min(1, Math.max(0, value));

const labelled = (node, key) => {
    node.dataset.i18nLabel = key;
    node.setAttribute('aria-label', t(key));
    return node;
};

export class ColorPicker {
    constructor(root, onChange) {
        this.root = root;
        this.onChange = onChange;
        this.hsv = hexToHsv(DEFAULT_COLOR);
        this.hex = DEFAULT_COLOR;
        const name = `color-${++pickers}`;

        this.swatches = element('div', 'swatches');
        this.swatches.replaceChildren(...DYES.map(([dye, hex]) => {
            const swatch = element('label', 'swatch');
            swatch.title = t(`dye.${dye}`);
            swatch.style.setProperty('--swatch', hex);
            const radio = labelled(document.createElement('input'), `dye.${dye}`);
            radio.type = 'radio';
            radio.name = name;
            radio.value = hex;
            radio.addEventListener('change', () => this.set(hex));
            swatch.append(radio);
            return swatch;
        }));

        this.shade = labelled(element('div', 'picker'), 'waypoint.shade');
        this.shade.setAttribute('role', 'slider');
        this.shade.tabIndex = 0;
        this.thumb = element('span', 'picker__thumb');
        this.shade.append(this.thumb);
        this.setupShade();

        this.hue = labelled(document.createElement('input'), 'waypoint.hue');
        this.hue.className = 'hue';
        this.hue.type = 'range';
        this.hue.min = '0';
        this.hue.max = '359';
        this.hue.addEventListener('input', () => {
            this.hsv.h = Number(this.hue.value);
            this.set(hsvToHex(this.hsv.h, this.hsv.s, this.hsv.v), false);
        });

        const hexField = element('label', 'hex');
        hexField.append(element('span', '', '#'));
        hexField.firstChild.setAttribute('aria-hidden', 'true');
        this.hexInput = labelled(document.createElement('input'), 'waypoint.hex');
        this.hexInput.autocomplete = 'off';
        this.hexInput.maxLength = 6;
        this.hexInput.spellcheck = false;
        this.hexInput.type = 'text';
        this.hexInput.addEventListener('input', () => {
            const value = this.hexInput.value.replace(/[^0-9a-f]/gi, '');
            if (value.length === 6) {
                this.set(`#${value.toLowerCase()}`, true, false);
            }
        });
        this.hexInput.addEventListener('blur', () => this.hexInput.value = this.hex.slice(1));
        hexField.append(this.hexInput);

        root.replaceChildren(this.swatches, this.shade, this.hue, hexField);
    }

    setupShade() {
        const field = this.shade;
        const pick = (event) => {
            const box = field.getBoundingClientRect();
            this.hsv.s = clamp((event.clientX - box.left) / box.width);
            this.hsv.v = clamp(1 - (event.clientY - box.top) / box.height);
            this.set(hsvToHex(this.hsv.h, this.hsv.s, this.hsv.v), false);
        };
        field.addEventListener('pointerdown', (event) => {
            field.setPointerCapture(event.pointerId);
            field.focus();
            pick(event);
        });
        field.addEventListener('pointermove', (event) => {
            if (field.hasPointerCapture(event.pointerId)) {
                pick(event);
            }
        });
        field.addEventListener('keydown', (event) => {
            const step = SHADE_STEP * (event.shiftKey ? 5 : 1);
            const moves = {ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, step], ArrowDown: [0, -step]};
            const move = moves[event.key];
            if (move) {
                event.preventDefault();
                this.hsv.s = clamp(this.hsv.s + move[0]);
                this.hsv.v = clamp(this.hsv.v + move[1]);
                this.set(hsvToHex(this.hsv.h, this.hsv.s, this.hsv.v), false);
            }
        });
    }

    set(hex, fromHex = true, writeHex = true) {
        hex = HEX.test(hex) ? hex.toLowerCase() : DEFAULT_COLOR;
        this.hex = hex;
        if (fromHex) {
            const hsv = hexToHsv(hex);
            this.hsv = {h: hsv.s === 0 ? this.hsv.h : hsv.h, s: hsv.s, v: hsv.v};
        }
        this.root.style.setProperty('--hue', `hsl(${this.hsv.h} 100% 50%)`);
        this.hue.value = String(Math.round(this.hsv.h));
        this.thumb.style.left = `${this.hsv.s * 100}%`;
        this.thumb.style.top = `${(1 - this.hsv.v) * 100}%`;
        this.shade.setAttribute('aria-valuetext', hex);
        if (writeHex) {
            this.hexInput.value = hex.slice(1);
        }
        for (const radio of this.swatches.querySelectorAll('input')) {
            radio.checked = radio.value === hex;
        }
        this.onChange(hex);
    }
}
