import {defineConfig, globalIgnores} from 'eslint/config';
import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import astro from 'eslint-plugin-astro';
import globals from 'globals';

export default defineConfig([
    globalIgnores(['dist/', '.astro/', 'node_modules/']),
    js.configs.recommended,
    tseslint.configs.recommended,
    astro.configs.recommended,
    astro.configs['jsx-a11y-recommended'],
    {languageOptions: {globals: {...globals.browser}}},
    {files: ['eslint.config.js', 'astro.config.mjs'], languageOptions: {globals: {...globals.node}}}
]);
