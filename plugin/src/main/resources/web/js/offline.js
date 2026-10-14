import {t} from './i18n.js';
import {wsManager} from './websocket.js';

const OFFLINE_DELAY_MILLIS = 4000;

export function setupOffline() {
    const overlay = document.getElementById('offline');
    const button = document.getElementById('offline-now');
    let timer = 0;
    const show = (shown) => {
        if (shown) {
            document.querySelectorAll(':popover-open').forEach(popover => popover.hidePopover());
        }
        overlay.classList.toggle('is-shown', shown);
        overlay.inert = !shown;
    };
    wsManager.on('status', ({state, seconds}) => {
        document.getElementById('offline-retry').textContent =
            state === 'disconnected' ? t('offline.retry', {seconds}) : t('status.connecting');
        button.disabled = state === 'connecting';
        if (state === 'connected') {
            clearTimeout(timer);
            timer = 0;
            show(false);
        } else if (!timer && overlay.inert) {
            timer = setTimeout(() => show(true), OFFLINE_DELAY_MILLIS);
        }
    });
    button.addEventListener('click', () => wsManager.connect());
}
