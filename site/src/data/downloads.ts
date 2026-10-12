import {existsSync, readFileSync} from 'node:fs';

export const parts = ['plugin', 'mod'] as const;

export type Part = (typeof parts)[number];

export type Channel = 'release' | 'beta' | 'alpha';

export interface Build {
    part: Part;
    build: number;
    version: string;
    name: string;
    channel: Channel;
    minecraft: string[];
    published: string;
    changelog: string;
    file: string;
    size: number;
    sha512: string;
    modrinth: string;
}

export interface Mirror {
    modrinth: boolean;
    builds: Build[];
}

export type Block = {list: string[]} | {text: string};

export interface Section {
    heading?: string;
    blocks: Block[];
}

export const folder = 'downloads';

const index = `${folder}/index.json`;

export const mirror: Mirror = existsSync(index)
    ? JSON.parse(readFileSync(index, 'utf8'))
    : {modrinth: false, builds: []};

export function latest(part: Part) {
    const own = mirror.builds.filter((build) => build.part === part);
    return own.find((build) => build.channel === 'release') ?? own[0];
}

export const anchor = ({part, build}: Build) => `${part}-${build}`;

export const modrinthUrl = ({modrinth}: Build) => `https://modrinth.com/project/taerra/version/${modrinth}`;

export function changelog(markdown: string): Section[] {
    const sections: Section[] = [{blocks: []}];
    for (const raw of markdown.split('\n')) {
        const line = raw.trim();
        if (!line || line.startsWith('## ')) continue;
        if (line.startsWith('### ')) {
            sections.push({heading: line.slice(4).replace(/^[^\p{L}]+/u, ''), blocks: []});
            continue;
        }
        const {blocks} = sections.at(-1)!;
        const item = line.match(/^[*-]\s+(.*)$/);
        const last = blocks.at(-1);
        if (!item) blocks.push({text: line.replace(/^_(.*)_$/, '$1')});
        else if (last && 'list' in last) last.list.push(item[1]);
        else blocks.push({list: [item[1]]});
    }
    return sections.filter((section) => section.blocks.length);
}

export const inline = (text: string) => text.split('`').map((part, i) => ({code: i % 2 === 1, text: part}));

export function size(bytes: number, lang: string) {
    const mega = bytes >= 1_000_000;
    return new Intl.NumberFormat(lang, {
        style: 'unit',
        unit: mega ? 'megabyte' : 'kilobyte',
        maximumFractionDigits: mega ? 1 : 0
    }).format(mega ? bytes / 1_000_000 : bytes / 1_000);
}

export const date = (iso: string, lang: string) =>
    new Intl.DateTimeFormat(lang, {dateStyle: 'medium', timeZone: 'UTC'}).format(new Date(iso));
