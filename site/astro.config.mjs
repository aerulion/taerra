import {defineConfig, envField, fontProviders} from 'astro/config';
import sitemap from '@astrojs/sitemap';
import {nyxFonts} from './src/components/aerulion/fonts.mjs';

const fonts = [
    {
        name: 'QuinqueFive',
        cssVariable: '--font-display',
        provider: fontProviders.local(),
        options: {variants: [{src: ['../plugin/src/main/resources/web/fonts/QuinqueFive-Regular.woff2'], weight: 400}]},
        fallbacks: ['system-ui', 'sans-serif']
    },
    {
        name: 'Schibsted Grotesk',
        cssVariable: '--font-body',
        provider: fontProviders.fontsource(),
        weights: ['400 900'],
        styles: ['normal'],
        fallbacks: ['system-ui', 'sans-serif']
    },
    {
        name: 'Azeret Mono',
        cssVariable: '--font-mono',
        provider: fontProviders.fontsource(),
        weights: [400],
        styles: ['normal'],
        fallbacks: ['ui-monospace', 'monospace']
    }
];

export default defineConfig({
    site: 'https://taerra.dev',
    fonts: [...fonts, ...nyxFonts],
    image: {layout: 'constrained', responsiveStyles: true},
    env: {
        schema: {
            PUBLIC_COMMIT_HASH: envField.string({context: 'server', access: 'public', default: 'dev'})
        }
    },
    i18n: {locales: ['en', 'de'], defaultLocale: 'en'},
    integrations: [
        sitemap({
            filter: (page) => page.endsWith('/') && !page.includes('/404'),
            i18n: {defaultLocale: 'en', locales: {en: 'en', de: 'de'}}
        })
    ],
    vite: {server: {fs: {allow: ['..']}}}
});
