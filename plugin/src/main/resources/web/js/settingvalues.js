export const ORDER = 'order';
export const SHAPES = ['none', 'circle', 'square', 'rectangle'];
export const STORAGE_TYPE = 'setting:storage.type';
const MAX_COORDINATE = 30_000_000;
const MIN_SCALE = -12;
const MAX_SCALE = 8;
const SCALE = /^(\d{1,5})(px|blocks?)$/;
export const VIEWS = {both: ['top_down', 'isometric'], top_down: ['top_down'], isometric: ['isometric']};

export const settingField = (key) => `setting:${key}`;
export const worldField = (id, part) => `world:${id}:${part}`;
export const layerField = (id) => `layer:${id}`;
export const layerIconField = (id) => `layer-icon:${id}`;
export const layerScaleField = (id) => `layer-scale:${id}`;
export const isLimit = (field) => field.startsWith('world:') && field.endsWith(':limit');
export const isViews = (field) => field.startsWith('world:') && field.endsWith(':views');
export const isStorage = (field) => field.startsWith('setting:storage.');

export function parseScale(text) {
    const match = SCALE.exec(text ?? '');
    const exponent = match ? Math.log2(Number(match[1])) : NaN;
    if (!Number.isInteger(exponent)) {
        return null;
    }
    const scale = (match[2] === 'px' ? exponent : -exponent) || 0;
    return scale >= MIN_SCALE && scale <= MAX_SCALE ? scale : null;
}

export const scaleText = (scale) => (scale === null ? '' : scale >= 0 ? `${2 ** scale}px` : `${2 ** -scale}blocks`);
export const same = (a, b) => JSON.stringify(a ?? null) === JSON.stringify(b ?? null);
const relative = (order, other) => (order ?? []).filter((id) => (other ?? []).includes(id));
export const sameOrder = (a, b) => same(relative(a, b), relative(b, a));
export const outOfRange = (setting, value) =>
    setting?.kind === 'number' && !(Number.isInteger(value) && value >= setting.min && value <= setting.max);
export const viewsOf = (value) => VIEWS[value] ?? VIEWS.both;
export const addsView = (before, after) => viewsOf(after).some((view) => !viewsOf(before).includes(view));
export const limit = (shape, minX, minZ, maxX, maxZ) => (shape === 'none' ? {shape} : {shape, minX, minZ, maxX, maxZ});

export function badLimit(value) {
    if (!value || value.shape === 'none') {
        return false;
    }
    if (!SHAPES.includes(value.shape)) {
        return true;
    }
    const bounds = [value.minX, value.minZ, value.maxX, value.maxZ];
    if (!bounds.every((bound) => Number.isInteger(bound) && Math.abs(bound) <= MAX_COORDINATE)) {
        return true;
    }
    const width = value.maxX - value.minX;
    const depth = value.maxZ - value.minZ;
    if (width < 0 || depth < 0) {
        return true;
    }
    return value.shape !== 'rectangle' && (width !== depth || width < 2 || width % 2 !== 0);
}

export function reshape(value, shape) {
    const known = value && value.shape !== 'none' && !badLimit(value);
    const {minX, minZ, maxX, maxZ} = known ? value : {minX: -1000, minZ: -1000, maxX: 1000, maxZ: 1000};
    if (shape !== 'circle' && shape !== 'square') {
        return limit(shape, minX, minZ, maxX, maxZ);
    }
    const radius = Math.max(1, Math.round(Math.max(maxX - minX, maxZ - minZ) / 2));
    const x = Math.floor((minX + maxX) / 2);
    const z = Math.floor((minZ + maxZ) / 2);
    return limit(shape, x - radius, z - radius, x + radius, z + radius);
}

export function rebase({base, draft, conflicts}, next) {
    const now = next.values;
    const changed = new Set();
    const by = (field) => next.authors?.[field]?.name;
    for (const field of new Set([...Object.keys(base), ...Object.keys(now), ...Object.keys(draft)])) {
        if (!(field in now)) {
            delete draft[field];
            conflicts.delete(field);
            continue;
        }
        if (field === ORDER) {
            continue;
        }
        const mine = draft[field];
        const touched = field in draft && !same(mine, base[field]);
        if (!touched) {
            if (field in base && !same(mine, now[field])) {
                changed.add(field);
            }
            draft[field] = structuredClone(now[field]);
            conflicts.delete(field);
        } else if (same(mine, now[field])) {
            conflicts.delete(field);
        } else if (!same(base[field], now[field])) {
            conflicts.set(field, {theirs: structuredClone(now[field]), by: by(field)});
        }
    }
    if (same(draft[ORDER], base[ORDER])) {
        if (!sameOrder(draft[ORDER], now[ORDER])) {
            changed.add(ORDER);
        }
        draft[ORDER] = structuredClone(now[ORDER]);
        conflicts.delete(ORDER);
    } else if (same(draft[ORDER], now[ORDER])) {
        conflicts.delete(ORDER);
    } else if (!sameOrder(base[ORDER], now[ORDER])) {
        conflicts.set(ORDER, {theirs: structuredClone(now[ORDER]), by: by(ORDER)});
    }
    return changed;
}
