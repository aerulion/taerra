import {t} from './i18n.js';

const MAX_RECONNECT_DELAY = 30000;
const KEEP_ALIVE_MILLIS = 20000;

class WebSocketManager {
    constructor() {
        this.ws = null;
        this.desiredWorld = null;
        this.reconnectAttempts = 0;
        this.reconnectTimer = null;
        this.listeners = new Map();
        setInterval(() => this.send({type: 'ping'}), KEEP_ALIVE_MILLIS);

        const retry = () => {
            if (!document.hidden && !this.connected) {
                this.connect();
            }
        };
        document.addEventListener('visibilitychange', retry);
        addEventListener('online', retry);
    }

    get connected() {
        return this.ws?.readyState === WebSocket.OPEN;
    }

    connect() {
        clearTimeout(this.reconnectTimer);
        if (this.ws && this.ws.readyState <= WebSocket.OPEN) {
            return;
        }
        this.updateStatus('connecting', t('status.connecting'));
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const ws = new WebSocket(`${protocol}//${location.host}/ws`);
        this.ws = ws;

        ws.onopen = () => {
            this.reconnectAttempts = 0;
            if (this.desiredWorld) {
                this.send({type: 'subscribe', world: this.desiredWorld});
            }
            this.updateStatus('connected', t('status.connected'));
            this.emit('connect');
        };

        ws.onclose = () => {
            if (this.ws !== ws) {
                return;
            }
            this.emit('disconnect');
            this.scheduleReconnect();
        };

        ws.onmessage = (event) => {
            let message;
            try {
                message = JSON.parse(event.data);
            } catch (error) {
                console.error('Failed to parse message:', error);
                return;
            }
            this.emit(message.type, message);
        };
    }

    reconnect() {
        const ws = this.ws;
        this.ws = null;
        ws?.close();
        this.connect();
    }

    subscribe(world) {
        this.desiredWorld = world;
        this.send({type: 'subscribe', world});
    }

    send(message) {
        if (this.connected) {
            this.ws.send(JSON.stringify(message));
        }
    }

    scheduleReconnect() {
        this.reconnectAttempts++;
        const delay = Math.min(MAX_RECONNECT_DELAY, 1000 * 1.5 ** (this.reconnectAttempts - 1));
        const countdown = (seconds) => {
            if (seconds === 0) {
                this.connect();
                return;
            }
            this.updateStatus('disconnected', t('status.disconnected'), t('status.retry', {seconds}));
            this.reconnectTimer = setTimeout(() => countdown(seconds - 1), 1000);
        };
        countdown(Math.max(1, Math.round(delay / 1000)));
    }

    updateStatus(state, text, retry = '') {
        document.querySelector('.status__dot').dataset.state = state;
        const status = document.getElementById('status-text');
        if (status.textContent !== text) {
            status.textContent = text;
        }
        document.getElementById('status-retry').textContent = retry;
    }

    on(event, callback) {
        if (!this.listeners.has(event)) {
            this.listeners.set(event, []);
        }
        this.listeners.get(event).push(callback);
    }

    emit(event, data) {
        this.listeners.get(event)?.forEach(callback => callback(data));
    }
}

export const wsManager = new WebSocketManager();
