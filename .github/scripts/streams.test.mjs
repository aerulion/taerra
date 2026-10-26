import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {test} from 'node:test';

const script = new URL('../actions/classify-changes/streams.sh', import.meta.url).pathname;

function streams(path) {
    return execFileSync('bash', ['-c', `source "$0"; for s in plugin mod site; do in_stream "$s" "$1" && printf '%s ' "$s"; done; true`, script, path])
        .toString().trim();
}

const expected = {
    'plugin/src/main/java/net/aerulion/taerra/bootstrap/Taerra.java': 'plugin',
    'plugin/src/main/resources/web/app.js': 'plugin',
    'api/build.gradle.kts': 'plugin',
    'tools/mcdata/pyproject.toml': 'plugin',
    '.githooks/commit-msg': 'plugin',
    'mod/src/main/java/net/aerulion/taerra/mod/TaerraClient.java': 'mod',
    'mod/build.gradle.kts': 'mod',
    'gradle/libs.versions.toml': 'plugin mod',
    'build-logic/src/main/kotlin/taerra.java-conventions.gradle.kts': 'plugin mod',
    'settings.gradle.kts': 'plugin mod',
    'gradlew': 'plugin mod',
    'plugin/src/main/resources/web/images/icon.svg': 'plugin site',
    'plugin/src/main/resources/web/fonts/QuinqueFive-Regular.woff2': 'plugin site',
    'plugin/src/main/resources/web/favicon.ico': 'plugin site',
    'site/src/data/taerra.ts': 'site',
    'branding/ingame/mod-map.png': 'site',
    'branding/images/youtube/thumbnail.png': 'site',
    '.github/actions/build-site/action.yml': 'site',
    '.github/workflows/site.yml': 'site',
    'branding/generate.py': '',
    '.github/workflows/release.yml': '',
    '.github/actions/classify-changes/streams.sh': '',
    'README.md': '',
    'plugin/README.md': '',
    'site/README.md': ''
};

for (const [path, want] of Object.entries(expected)) {
    test(`${path} is ${want || 'no stream'}`, () => assert.equal(streams(path), want));
}
