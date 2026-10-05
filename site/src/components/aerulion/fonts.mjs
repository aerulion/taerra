import {fontProviders} from 'astro/config';

export const nyxFonts = [
    {name: 'Tektur', cssVariable: '--font-nyx-wordmark', weights: [700], fallbacks: ['system-ui', 'sans-serif']},
    {name: 'IBM Plex Mono', cssVariable: '--font-nyx-label', weights: [400], fallbacks: ['ui-monospace', 'monospace']}
].map((font) => ({...font, provider: fontProviders.fontsource()}));
