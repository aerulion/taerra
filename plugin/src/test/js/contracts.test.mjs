import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {describe, test} from 'node:test';
import {monochrome} from '../../main/resources/web/js/pixels.js';
import {freshColor} from '../../main/resources/web/js/regioncolors.js';
import {badLimit, parseScale, rebase, scaleText} from '../../main/resources/web/js/settingvalues.js';

const cases = (name) => JSON.parse(readFileSync(new URL(`../resources/contracts/${name}.json`, import.meta.url)));
const rgba = (argb) => {
    const value = parseInt(argb, 16);
    return (((value & 0xffffff) << 8) | (value >>> 24)) >>> 0;
};

describe('icon tint', () => {
    for (const {name, pixels, tinted} of cases('icon-tint')) {
        test(name, () => assert.equal(monochrome(pixels.map(rgba)), tinted));
    }
});

describe('layer scales', () => {
    const {parse, format} = cases('layer-scales');
    for (const {text, scale} of parse) {
        test(`reads ${JSON.stringify(text)}`, () => assert.equal(parseScale(text), scale));
    }
    for (const {scale, text} of format) {
        test(`writes ${scale}`, () => assert.equal(scaleText(scale), text));
    }
});

describe('world limits', () => {
    for (const {name, limit, valid} of cases('world-limits')) {
        test(name, () => assert.equal(!badLimit(limit), valid));
    }
});

describe('region colours', () => {
    for (const {name, used, fresh} of cases('region-colors')) {
        test(name, () => assert.equal(freshColor(used.map((color) => ({color}))), fresh));
    }
});

describe('settings rebase', () => {
    for (const {name, base, edits, now, authors, expect} of cases('settings-rebase')) {
        test(name, () => {
            const state = {
                base: structuredClone(base),
                draft: {...structuredClone(base), ...edits},
                conflicts: new Map()
            };
            const changed = rebase(state, {
                values: now,
                authors: Object.fromEntries(Object.entries(authors ?? {}).map(([field, who]) => [field, {name: who}]))
            });
            for (const [field, value] of Object.entries(expect.values)) {
                assert.deepEqual(state.draft[field], value, field);
            }
            for (const field of expect.absent ?? []) {
                assert.ok(!(field in state.draft), field);
            }
            assert.deepEqual(Object.fromEntries(state.conflicts), expect.conflicts);
            assert.deepEqual([...changed].sort(), [...expect.changed].sort());
        });
    }
});
