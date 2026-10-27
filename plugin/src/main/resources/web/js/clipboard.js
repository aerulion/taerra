export async function copyText(text) {
    try {
        await navigator.clipboard.writeText(text);
        return;
    } catch {}
    const field = document.createElement('textarea');
    field.value = text;
    field.style.position = 'fixed';
    field.style.opacity = '0';
    document.body.append(field);
    field.select();
    document.execCommand('copy');
    field.remove();
}
