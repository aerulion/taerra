export function monochrome(pixels) {
    const colors = new Set(pixels.filter((pixel) => pixel & 0xff).map((pixel) => pixel >>> 8));
    return colors.size === 1 && (colors.has(0xffffff) || colors.has(0));
}
