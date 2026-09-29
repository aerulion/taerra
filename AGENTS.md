# AGENTS.md

Guidance for AI coding agents in this repository. `CLAUDE.md` is a symlink to this file.

## Project

Taerra is a live web map for Paper servers (Java 25, Paper 26.2 via `paperweight-userdev`, Mojang mappings, NMS
allowed) and a Fabric mod that shows the same map in game. Gradle multi-module build, versions in
`gradle/libs.versions.toml`, shared Java setup in `build-logic` (`taerra.java-conventions`).

| Module         | What                                                                                   |
|----------------|----------------------------------------------------------------------------------------|
| `plugin/`      | Renders top-down and isometric tiles, serves the web map and its HTTP/WebSocket API (Javalin) |
| `api/`         | Marker and layer API for other plugins, published as `net.aerulion:taerra-api`, bundled into the plugin jar |
| `mod/`         | Fabric client mod (Loom): map screen and minimap                                       |
| `tools/mcdata` | Python generator for `plugin/src/main/resources/block_data.json`                      |

It is a public plugin: nothing in it may be specific to one server network.

**Rendering is server side only.** The mod is a viewer: it draws the tiles the plugin serves and talks to the same
`/api/...` routes as the web map in `plugin/src/main/resources/web/`. Never render, or share rendering code, on the
client. A change to a route or its JSON lands together with the web map and mod changes it needs, in one commit.
The mod learns the web map's address and session from the plugin's `taerra:web` join message (`ModHandshake`), which
carries a protocol number: bump `ModHandshake.PROTOCOL` and the mod's `TaerraClient.PROTOCOL` together whenever a
route, its JSON or that message changes in a way an older mod would misread.

The look is fixed product identity: full-block cubes, one colour per face, lighting hardcoded in
`rendering/style/Lighting.java`. No style options or toggles; change the constants.

Web identity is the in-game `/taerra login` one-time link and its 30-day session, with permission-gated worlds,
layers and teleport. Extend that model; don't add external authentication.

## Commands

```bash
./gradlew preCommit                          # spotlessApply everywhere, then build + test
./gradlew build                              # compile + test + jars
./gradlew :plugin:test [--tests "WebSessionsTest"]
./gradlew :plugin:runDevBundleServer         # local Paper dev server in ./run (downloads WorldEdit/WorldGuard/Chunky/LuckPerms)
./gradlew :mod:runClient                     # Minecraft client with the mod
./gradlew runDev                             # both: server in offline mode, client joins it as TaerraDev
./gradlew -q printVersion
```

Spotless formats Java (google-java-format, per module), every `*.gradle.kts` (ktfmt kotlinlang style) and the JSON
under `plugin/src/main/resources/web/` and `mod/src/main/resources/` (Jackson, root project). `block_data.json` is
excluded: `tools/mcdata` writes it. The `pre-commit` hook runs `spotlessApply` and re-stages; CI runs `spotlessCheck`
through `build`.

Paperweight and Loom are both declared `apply false` in the root `plugins` block so they share one classloader; keep
new Gradle plugins that either of them touches there too. The spotless plugin is applied to the root through the
catalog alias because `build-logic` already puts it on the classpath.

## Versions and publishing

Build numbers, not semver: release `<build>-<commit>` (`412-a1b2c3d`), PR snapshot `pr<pr>-<build>-<commit>`, local
`<commit>-dev` / `<commit>-dirty-dev`. `settings.gradle.kts` derives it from `TAERRA_BUILD_NUMBER`,
`TAERRA_PR_NUMBER` and `git` for every project; workflows never build a version string — `./gradlew -q printVersion`
is the source of truth. `<build>` is the `release.yml` run number, shared by both streams, so tags skip numbers.

`build.yml` is the only reusable Gradle workflow. It runs `./gradlew build`, then `./gradlew :api:publish` as a
separate step, so nothing is published before every test has passed. Caching is `gradle/actions/setup-gradle` alone —
never add `cache: gradle` to `setup-java`. The Paper dev bundle is pinned in the catalog, not `+`: a dynamic version
misses the userdev CI cache.

## Release pipeline

`release.yml` runs on every push to `main`. `.github/actions/classify-changes` splits the push into two streams:
`mod/` is the mod, shared build files (`gradle/`, `build-logic/`, root `*.gradle.kts`, `gradle.properties`, wrapper)
are both, anything else is the plugin. `build.yml` runs once, then:

- plugin: tag `build-<n>`, plugin and API jars attached, API published to GitHub Packages
- mod: tag `mod-<n>`, mod jar attached

With `release-tags: 'true'`, `classify-changes` diffs each stream against the newest `build-`/`mod-` tag HEAD descends
from, falling back to `github.event.before`. Required: GitHub keeps one pending run per concurrency group, so a
cancelled run's commits would otherwise never be classified.

`.github/actions/github-release` writes notes with `release-notes.sh` from the commit log, grouped by the commit types
the `commit-msg` hook enforces. Its pathspecs must match `classify-changes`; edit both together, and the hook's types
together with the notes' sections. Both resolve the previous tag with `git describe` (ancestry), never tag dates.

Both streams share one release list, so `cleanup-releases.yml` refuses to run without a tag prefix.
`cleanup-packages.yml` only deletes versions matching `^\d+-[0-9a-f]{7,}$` and fails if nothing matches, and
`pr-cleanup.yml` matches the `pr<n>-` prefix; both follow the version format in `settings.gradle.kts`. The package API
calls pick `/users/...` or `/orgs/...` from the repository owner type.

Discord notifications need the `DISCORD_WEBHOOK_ID` and `DISCORD_WEBHOOK_TOKEN` secrets.

## Pull requests

`pr.yml` always classifies, checks every commit message and the PR title with `.githooks/commit-msg` (a squash merge
lands the title as the subject), and validates. With the `deploy` label it also publishes a `pr<n>-…` API snapshot
when the plugin changed and uploads the plugin and mod jars as workflow artifacts, listed in one PR comment. Label
events other than `deploy` and title edits skip everything but the message check, in their own concurrency group.
