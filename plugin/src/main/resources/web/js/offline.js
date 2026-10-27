import {t} from './i18n.js';
import {wsManager} from './websocket.js';

const OFFLINE_DELAY_MILLIS = 4000;
const FADE_MILLIS = 600;

export function setupOffline() {
    const overlay = document.getElementById('offline');
    const button = document.getElementById('offline-now');
    let timer = 0;
    let hiding = 0;
    const show = (shown) => {
        clearTimeout(hiding);
        if (shown) {
            document.querySelectorAll(':popover-open').forEach((popover) => {
                if (popover !== overlay) {
                    popover.hidePopover();
                }
            });
            if (overlay.matches(':popover-open')) {
                overlay.hidePopover();
            }
            overlay.showPopover();
            requestAnimationFrame(() => overlay.classList.add('is-shown'));
        } else {
            overlay.classList.remove('is-shown');
            hiding = setTimeout(() => {
                if (overlay.matches(':popover-open')) {
                    overlay.hidePopover();
                }
            }, FADE_MILLIS);
        }
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
