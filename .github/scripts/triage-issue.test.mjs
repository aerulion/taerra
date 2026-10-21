import assert from 'node:assert/strict';
import {test} from 'node:test';
import {build, debugInfo, newest, stale, triage} from './triage-issue.mjs';

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
    assert.equal(debugInfo(`[01:05:43 INFO]: ${debug}`).Plugin, '400-a1b2c3d');
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
    const body = '### Debug info\n\n```text\nTAERRA debug\nMod: 409-1234567, protocol 6\nPlugin: 400-a1b2c3d\n```';
    const result = triage({body, labels: ['bug', 'mod'], latest});
    assert.deepEqual(result.add, ['outdated']);
    assert.match(result.comment, /mod Build 409/);
    assert.match(result.comment, /plugin Build 400/);
});

test('a mod bug without the debug block needs info, a typed build is enough', () => {
    const missing = triage({body: '### Debug info\n\n_No response_', labels: ['bug', 'mod'], latest});
    assert.deepEqual(missing.add, ['needs info']);
    const typed = triage({body: '### Debug info\n\nmod 410-9f8e7d6', labels: ['bug', 'mod'], latest});
    assert.equal(typed.comment, null);
});

test('a web map report gets its label, and a stopped web server gets a hint', () => {
    const web = 'TAERRA debug\nPlugin: 412-a1b2c3d\nBrowser: Mozilla/5.0\nScreen: 1440x900 at 2x';
    assert.deepEqual(triage({body: pluginBug(web, 'Something else'), labels: ['bug', 'plugin'], latest}).add,
        ['web map']);
    const down = debug.replace('400', '412') + '\nWeb: not running on port 8100, no public URL';
    assert.match(triage({body: pluginBug(down, 'Rendering'), labels: ['bug', 'plugin'], latest}).comment,
        /Failed to start the web server/);
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

test('only open issues waiting on info for two weeks go stale', () => {
    const now = Date.parse('2026-10-22T00:00:00Z');
    const issue = (number, labels, updated, extra = {}) =>
        ({number, state: 'open', labels: labels.map(name => ({name})), updated_at: updated, ...extra});
    assert.deepEqual(stale([
        issue(1, ['bug', 'needs info'], '2026-10-01T00:00:00Z'),
        issue(2, ['bug', 'needs info'], '2026-10-20T00:00:00Z'),
        issue(3, ['bug'], '2026-09-01T00:00:00Z'),
        issue(4, ['needs info'], '2026-09-01T00:00:00Z', {pull_request: {}}),
        issue(5, ['needs info'], '2026-09-01T00:00:00Z', {state: 'closed'})
    ], now), [1]);
});
