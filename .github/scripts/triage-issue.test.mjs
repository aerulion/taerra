import assert from 'node:assert/strict';
import {test} from 'node:test';
import {build, debugInfo, newest, triage} from './triage-issue.mjs';

const latest = {plugin: 412, mod: 410};

function pluginBug(debug, where = 'Web map') {
    return `### Debug info\n\n\`\`\`text\n${debug}\n\`\`\`\n\n### Where\n\n${where}\n\n### What happened\n\nTiles stay grey`;
}

const debug = 'TAERRA debug\nPlugin: 400-a1b2c3d\nServer: Paper 26.3-17-abcdef0 (MC: 26.3)\nMods: none';

test('reads the debug block wherever it was pasted', () => {
    assert.deepEqual(debugInfo(`Here:\n${debug}\n\nmore text`), {
        Plugin: '400-a1b2c3d',
        Server: 'Paper 26.3-17-abcdef0 (MC: 26.3)',
        Mods: 'none'
    });
    assert.equal(debugInfo('no block'), null);
});

test('reads build numbers from versions and jar names', () => {
    assert.equal(build('412-a1b2c3d'), 412);
    assert.equal(build('pr7-412-a1b2c3d'), 412);
    assert.equal(build('taerra-fabric-build-410-9f8e7d6.jar'), 410);
    assert.equal(build('a1b2c3d-dev'), null);
    assert.equal(build(undefined), null);
});

test('an outdated plugin bug gets labelled and told to update', () => {
    const result = triage({body: pluginBug(debug), labels: ['bug', 'plugin', 'triage'], latest});
    assert.deepEqual(result.add.sort(), ['outdated', 'web map']);
    assert.deepEqual(result.remove, []);
    assert.match(result.comment, /Build 400, the newest is Build 412/);
});

test('a plugin bug without the debug block needs info, and loses it once fixed', () => {
    const missing = triage({body: pluginBug('I use the latest'), labels: ['bug', 'plugin'], latest});
    assert.ok(missing.add.includes('needs info'));
    const fixed = triage({
        body: pluginBug(debug.replace('400', '412'), 'Rendering'),
        labels: ['bug', 'plugin', 'needs info'],
        latest
    });
    assert.deepEqual(fixed, {add: [], remove: ['needs info'], comment: null});
});

test('a dev build is neither outdated nor missing', () => {
    const result = triage({body: pluginBug(debug.replace('400-a1b2c3d', 'a1b2c3d-dev')), labels: ['bug', 'plugin'], latest});
    assert.equal(result.comment, null);
});

test('a mod bug checks the mod build and the server it played on', () => {
    const body = '### Mod build\n\n409-1234567\n\n### Server debug info\n\n```text\n' + debug + '\n```';
    const result = triage({body, labels: ['bug', 'mod'], latest});
    assert.deepEqual(result.add, ['outdated']);
    assert.match(result.comment, /mod Build 409/);
    assert.match(result.comment, /plugin Build 400/);
});

test('an idea gets a label for each part it names', () => {
    const result = triage({body: '### Part\n\nWeb map, Mod\n\n### Your idea\n\nMore', labels: ['enhancement'], latest});
    assert.deepEqual(result.add, ['web map', 'mod']);
    assert.equal(result.comment, null);
});

test('the newest build only counts full releases of that stream', () => {
    const releases = [
        {tag_name: 'plugin-412', prerelease: false, draft: false},
        {tag_name: 'plugin-415', prerelease: true, draft: false},
        {tag_name: 'mod-420', prerelease: false, draft: false},
        {tag_name: 'site', prerelease: false, draft: false}
    ];
    assert.equal(newest(releases, 'plugin-'), 412);
    assert.equal(newest(releases, 'mod-'), 420);
    assert.equal(newest([], 'plugin-'), null);
});
