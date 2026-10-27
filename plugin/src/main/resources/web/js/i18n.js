export const MAC = /Mac|iPhone|iPad/.test(navigator.platform);

export const LANGUAGES = {en: 'English', de: 'Deutsch'};

function browserLanguage() {
    for (const tag of navigator.languages ?? [navigator.language]) {
        const base = tag.toLowerCase().split('-')[0];
        if (Object.hasOwn(LANGUAGES, base)) {
            return base;
        }
    }
    return 'en';
}

export const defaultLanguage = browserLanguage();

export function hashLanguage(params) {
    const code = params.get('lang');
    return Object.hasOwn(LANGUAGES, code) ? code : defaultLanguage;
}

async function load(code) {
    try {
        const response = await fetch(`lang/${code}.json`);
        return response.ok ? await response.json() : {};
    } catch {
        return {};
    }
}

export const language = hashLanguage(new URLSearchParams(location.hash.slice(1)));
const [strings, fallback] = await Promise.all([load(language), language === 'en' ? {} : load('en')]);

export const numbers = new Intl.NumberFormat(language);
export const dates = new Intl.DateTimeFormat(language, {dateStyle: 'long'});
export const plurals = new Intl.PluralRules(language);

export function t(key, params = {}) {
    const template = strings[key] ?? fallback[key] ?? key;
    return template.replace(/\{(\w+)}/g, (match, name) => params[name] ?? match);
}

export function translatePage() {
    document.documentElement.lang = language;
    for (const element of document.querySelectorAll('[data-i18n]')) {
        element.textContent = t(element.dataset.i18n);
    }
    for (const element of document.querySelectorAll('[data-i18n-label]')) {
        element.setAttribute('aria-label', t(element.dataset.i18nLabel));
    }
    if (MAC) {
        for (const element of document.querySelectorAll('[aria-keyshortcuts^="Control+"]')) {
            element.setAttribute(
                'aria-keyshortcuts',
                element.getAttribute('aria-keyshortcuts').replace('Control+', 'Meta+')
            );
        }
    }
    const mod = t(MAC ? 'key.command' : 'key.control');
    for (const element of document.querySelectorAll('[data-i18n-title]')) {
        element.title = t(element.dataset.i18nTitle, {key: element.getAttribute('aria-keyshortcuts') ?? '', mod});
    }
}

export function setLanguage(code) {
    const params = new URLSearchParams(location.hash.slice(1));
    params.set('lang', code);
    history.replaceState(null, '', `#${params}`);
    location.reload();
}
