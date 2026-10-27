export const PALETTE = [
    '#3ab3da',
    '#b02e26',
    '#80c71f',
    '#f9801d',
    '#8932b8',
    '#fed83d',
    '#169c9c',
    '#c74ebd',
    '#3c44aa',
    '#5e7c16',
    '#f38baa',
    '#835432'
];

export function freshColor(regions) {
    const uses = new Map(PALETTE.map((color) => [color, 0]));
    for (const region of regions) {
        if (uses.has(region.color)) {
            uses.set(region.color, uses.get(region.color) + 1);
        }
    }
    return PALETTE.reduce((best, color) => (uses.get(color) < uses.get(best) ? color : best));
}
