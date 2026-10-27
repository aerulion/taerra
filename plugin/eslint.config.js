import js from '@eslint/js';
import globals from 'globals';

export default [
    js.configs.recommended,
    {
        rules: {
            'no-empty': ['error', {allowEmptyCatch: true}],
            'no-unused-vars': ['error', {ignoreRestSiblings: true}]
        }
    },
    {
        files: ['src/main/resources/web/js/**/*.js'],
        languageOptions: {globals: {...globals.browser, L: 'readonly'}}
    },
    {
        files: ['src/test/js/**/*.mjs', 'eslint.config.js'],
        languageOptions: {globals: {...globals.node}}
    }
];
