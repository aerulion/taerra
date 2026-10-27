export function element(tag, className, text) {
    const node = document.createElement(tag);
    node.className = className;
    if (text !== undefined) {
        node.textContent = text;
    }
    return node;
}

function button(className, icon, onClick) {
    const node = element('button', className);
    node.type = 'button';
    if (icon) {
        node.dataset.icon = icon;
    }
    if (onClick) {
        node.addEventListener('click', () => onClick(node));
    }
    return node;
}

export function iconButton(icon, label, onClick) {
    const node = button('icon-btn', icon, onClick);
    setLabel(node, label);
    return node;
}

export function menuItem(icon, text, onClick) {
    const node = button('menu__item', icon, onClick);
    node.textContent = text;
    return node;
}

function setLabel(node, text, label = text) {
    if (node.classList.contains('icon-btn')) {
        node.title = text;
        node.setAttribute('aria-label', label);
    } else {
        node.textContent = text;
    }
}

export function armed(node, confirm) {
    if (node.classList.contains('is-armed')) {
        return true;
    }
    node.dataset.idle = node.classList.contains('icon-btn') ? node.title : node.textContent;
    node.dataset.idleLabel = node.getAttribute('aria-label') ?? '';
    node.classList.add('is-armed');
    setLabel(node, confirm);
    return false;
}

export function disarm(node) {
    if (node.classList.contains('is-armed')) {
        node.classList.remove('is-armed');
        setLabel(node, node.dataset.idle, node.dataset.idleLabel);
    }
}

export function replay(node) {
    node.style.animation = 'none';
    void node.offsetWidth;
    node.style.animation = '';
}

export function slide(container, target) {
    if (!target) {
        return;
    }
    container.style.setProperty('--slide-x', `${target.offsetLeft}px`);
    container.style.setProperty('--slide-y', `${target.offsetTop}px`);
    container.style.setProperty('--slide-w', `${target.offsetWidth}px`);
    container.style.setProperty('--slide-h', `${target.offsetHeight}px`);
    if (!container.classList.contains('is-sliding')) {
        requestAnimationFrame(() => container.classList.add('is-sliding'));
    }
}

export function haptic() {
    navigator.vibrate?.(10);
}

export function switchInput(field, checked, label, onChange) {
    const wrap = element('span', 'switch');
    const input = element('input', 'switch__input');
    input.type = 'checkbox';
    input.role = 'switch';
    input.setAttribute('switch', '');
    input.checked = checked;
    input.dataset.field = field;
    input.setAttribute('aria-label', label);
    input.addEventListener('change', () => {
        onChange(input.checked);
        const replaced =
            !input.isConnected && document.querySelector(`.switch__input[data-field="${CSS.escape(field)}"]`);
        if (replaced && replaced.checked === input.checked) {
            const track = replaced.nextElementSibling;
            track.classList.add('is-instant');
            replaced.checked = !input.checked;
            void track.offsetWidth;
            track.classList.remove('is-instant');
            replaced.checked = input.checked;
        }
    });
    wrap.append(input, element('span', 'switch__track'));
    return {wrap, input};
}

export function dismiss(dialog) {
    if (dialog.requestClose) {
        dialog.requestClose();
    } else if (dialog.dispatchEvent(new Event('cancel', {cancelable: true}))) {
        dialog.close();
    }
}

export function modal(dialog) {
    dialog.addEventListener('click', (event) => {
        if (event.target === dialog || event.target.closest('[data-close]')) {
            dismiss(dialog);
        }
    });
}
