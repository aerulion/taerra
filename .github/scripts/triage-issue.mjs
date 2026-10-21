export const MARKER = '<!-- taerra-triage -->';

const AREA_LABELS = {
    'Web map': 'web map',
    'API': 'api'
};

const PART_LABELS = {
    'Plugin': 'plugin',
    'Web map': 'web map',
    'Mod': 'mod',
    'API': 'api',
    'Website': 'website'
};

export function sections(body) {
    const found = {};
    for (const part of (body ?? '').replace(/\r\n/g, '\n').split(/^### /m).slice(1)) {
        const end = part.indexOf('\n');
        const heading = (end < 0 ? part : part.slice(0, end)).trim();
        const value = (end < 0 ? '' : part.slice(end + 1)).trim().replace(/^```\w*\n([\s\S]*?)\n?```$/, '$1').trim();
        found[heading] = value === '_No response_' ? '' : value;
    }
    return found;
}

export function debugInfo(text) {
    const lines = (text ?? '').replace(/\r\n/g, '\n').split('\n');
    const start = lines.findIndex(line => line.trim() === 'TAERRA debug');
    if (start < 0) {
        return null;
    }
    const info = {};
    for (const line of lines.slice(start + 1)) {
        const match = line.match(/^\s*([A-Za-z]+):\s*(.*?)\s*$/);
        if (!match) {
            break;
        }
        info[match[1]] = match[2];
    }
    return info;
}

export function build(version) {
    const match = (version ?? '').match(/(?:^|[\s-])(?:pr\d+-)?(\d+)-[0-9a-f]{7,}\b/);
    return match ? Number(match[1]) : null;
}

function outdated(what, reported, latest) {
    return `This was reported on ${what} Build ${reported}, the newest is Build ${latest}. Please update from `
        + '[download.taerra.dev](https://download.taerra.dev) and check whether it still happens, then edit this issue '
        + 'with the new build.';
}

export function triage({body, labels, latest}) {
    const has = new Set(labels);
    const fields = sections(body);
    const add = new Set();
    const notes = [];
    let missing = false;
    let old = false;

    if (has.has('bug') && has.has('plugin')) {
        const plugin = build(debugInfo(fields['Debug info'])?.Plugin);
        if (!debugInfo(fields['Debug info'])) {
            missing = true;
            notes.push('Please run `/taerra debug` on the server, click **Copy** and paste it into **Debug info** by '
                + 'editing this issue. It says which builds and integrations are involved.');
        } else if (plugin !== null && latest.plugin !== null && plugin < latest.plugin) {
            old = true;
            notes.push(outdated('plugin', plugin, latest.plugin));
        }
        const area = AREA_LABELS[fields['Where']];
        if (area) {
            add.add(area);
        }
    }

    if (has.has('bug') && has.has('mod')) {
        const mod = build(fields['Mod build']);
        if (mod === null) {
            missing = true;
            notes.push('Please add the mod build from the jar\'s name, for example `412-a1b2c3d` from '
                + '`taerra-fabric-build-412-a1b2c3d.jar`, by editing this issue.');
        } else if (latest.mod !== null && mod < latest.mod) {
            old = true;
            notes.push(outdated('mod', mod, latest.mod));
        }
        const plugin = build(debugInfo(fields['Server debug info'])?.Plugin);
        if (plugin !== null && latest.plugin !== null && plugin < latest.plugin) {
            notes.push(`The server runs plugin Build ${plugin}, the newest is Build ${latest.plugin}. If it is your `
                + 'server, update it too.');
        }
    }

    if (has.has('enhancement')) {
        for (const part of (fields['Part'] ?? '').split(',')) {
            const label = PART_LABELS[part.trim()];
            if (label) {
                add.add(label);
            }
        }
    }

    const remove = new Set();
    (missing ? add : remove).add('needs info');
    (old ? add : remove).add('outdated');
    return {
        add: [...add].filter(label => !has.has(label)),
        remove: [...remove].filter(label => has.has(label)),
        comment: notes.length ? `${MARKER}\n${notes.join('\n\n')}` : null
    };
}

export function newest(releases, prefix) {
    const builds = releases
        .filter(release => !release.prerelease && !release.draft && release.tag_name.startsWith(prefix))
        .map(release => Number(release.tag_name.slice(prefix.length)))
        .filter(Number.isInteger);
    return builds.length ? Math.max(...builds) : null;
}
