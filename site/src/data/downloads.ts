import {existsSync, readFileSync} from 'node:fs';
import {links} from './taerra';

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
    modrinth: string;
}

export type Block = {list: string[]} | {text: string};

export interface Section {
    heading?: string;
    blocks: Block[];
}

const index = 'downloads/index.json';

export const builds: Build[] = existsSync(index) ? JSON.parse(readFileSync(index, 'utf8')).builds : [];

export function latest(part: Part) {
    const own = builds.filter((build) => build.part === part);
    return own.find((build) => build.channel === 'release') ?? own[0];
}

export const anchor = ({part, build}: Build) => `${part}-${build}`;

export const modrinthUrl = ({modrinth}: Build) => `https://modrinth.com/project/taerra/version/${modrinth}`;

export const commitUrl = (hash: string) => `${links.source}/commit/${hash}`;

export const commit = ({version}: Build) => version.slice(version.indexOf('-') + 1);

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

export const inline = (text: string) =>
    [...text.matchAll(/`([^`]*)`|\[([^\]]+)\]\((https:\/\/[^)\s]+)\)|([^`[]+|\[)/g)].map(
        ([, code, label, href, plain]) =>
            code !== undefined
                ? {kind: 'code' as const, text: code}
                : href
                  ? {kind: 'link' as const, text: label, href}
                  : {kind: 'text' as const, text: plain}
    );

export const date = (iso: string, lang: string) =>
    new Intl.DateTimeFormat(lang, {dateStyle: 'medium', timeZone: 'UTC'}).format(new Date(iso));
