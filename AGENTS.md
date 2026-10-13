# AGENTS.md

Guidance for AI coding agents in this repository. `CLAUDE.md` is a symlink to this file.

## Project

TAERRA is a live web map for Paper servers (Java 25, Paper 26.3 via `paperweight-userdev`, Mojang mappings, NMS
allowed) and a Fabric mod that shows the same map in game. Gradle multi-module build, versions in
`gradle/libs.versions.toml`, shared Java setup in `build-logic` (`taerra.java-conventions`).

| Module         | What                                                                                   |
|----------------|----------------------------------------------------------------------------------------|
| `plugin/`      | Renders top-down and isometric tiles, serves the web map and its HTTP/WebSocket API (Javalin) |
| `api/`         | Marker and layer API for other plugins, published as `net.aerulion:taerra-api`, bundled into the plugin jar |
| `mod/`         | Fabric client mod (Loom): map screen and minimap                                       |
| `tools/mcdata` | Python generator for `plugin/src/main/resources/block_data.json` and `colormaps.json`  |
| `branding/`    | Brand images (store icons, headers, gallery) rendered from the logo and dev server tiles |
| `site/`        | taerra.dev, the promo page: Astro and Bun, deployed to GitHub Pages by `release.yml`   |

It is a public plugin: nothing in it may be specific to one server network.

**Rendering is server side only.** The mod is a viewer: it draws the tiles the plugin serves and talks to the same
`/api/...` routes as the web map in `plugin/src/main/resources/web/`. Never render, or share rendering code, on the
client. A change to a route or its JSON lands together with the web map and mod changes it needs, in one commit. The mod
learns the web map's address and session from the plugin's `taerra:web` join message (`ModHandshake`), which carries a
protocol number: bump `ModHandshake.PROTOCOL` and the mod's `TaerraClient.PROTOCOL` together whenever a route, its JSON
or that message changes in a way an older mod would misread. The mod answers on the same channel with its version once
the server registers it, which `/taerra mod` lists; a player whose mod predates that answer shows as an older mod.

The plugin reads chunks straight from the region files, without the game's data fixers. `rendering/source/ChunkFormat`
maps each chunk's `DataVersion` to the reader for that format, back to 26.2 and no further: a Minecraft update that
changes the chunk format adds a constant with its release's `DataVersion` and leaves the older ones alone.

The look is fixed product identity: full-block cubes, one colour per face, lighting hardcoded in
`rendering/style/Lighting.java`. No style options or toggles; change the constants.

Web identity is the in-game `/taerra login` one-time link and its 30-day session, with permission-gated worlds,
layers and teleport. Extend that model; don't add external authentication.

Commands (`command/`), web routes (`web/http/route/`) and listeners are dispatchers: they parse input, check who
may do it and turn a result into chat or an HTTP status. What happens lives in a service beside its domain
(`rendering/RenderService`, `waypoint/WaypointEditor`, `config/ConfigEditor`, `world/Teleports`, ...), which returns
a result instead of chat and, when it touches the world, hops onto the main thread itself. A command and a route
doing the same thing share that service, never each other. Chat text is built in `chat/` (`Messages`, and
`RenderFeedback` for renders started from chat or the map); `web.*` settings are read through `config/WebSettings`.

## Commands

```bash
./gradlew preCommit                          # spotlessApply everywhere, then build + test
./gradlew build                              # compile + test + jars
./gradlew :plugin:test [--tests "WebSessionsTest"]
./gradlew :plugin:runDevBundleServer         # local Paper dev server in ./run (downloads WorldEdit/WorldGuard/Chunky/LuckPerms)
./gradlew :mod:runClient                     # Minecraft client with the mod
./gradlew runDev                             # both: server in offline mode, client joins it as TaerraDev
./gradlew -q printVersion
cd site && bun run dev                       # taerra.dev on http://localhost:4321; bun run check is what CI runs
```

Spotless formats Java (google-java-format, per module), every `*.gradle.kts` (ktfmt kotlinlang style) and the JSON
under `plugin/src/main/resources/web/` and `mod/src/main/resources/` (Jackson, root project). `block_data.json` and
`colormaps.json` are excluded: `tools/mcdata` writes them. The `pre-commit` hook runs `spotlessApply` and re-stages; CI
runs `spotlessCheck` through `build`.

Paperweight and Loom are both declared `apply false` in the root `plugins` block so they share one classloader; keep
new Gradle plugins that either of them touches there too. The spotless plugin is applied to the root through the
catalog alias because `build-logic` already puts it on the classpath.

## Versions and publishing

Build numbers, not semver: release `<build>-<commit>` (`412-a1b2c3d`), PR snapshot `pr<pr>-<build>-<commit>`, local
`<commit>-dev` / `<commit>-dirty-dev`. `settings.gradle.kts` derives it from `TAERRA_BUILD_NUMBER`,
`TAERRA_PR_NUMBER` and `git` for every project; workflows never build a version string — `./gradlew -q printVersion`
is the source of truth. `<build>` is the `release.yml` run number, shared by both streams, so tags skip numbers.
People read it as "Build 412": jars are `taerra-paper-build-412-a1b2c3d.jar` / `taerra-fabric-…` (`jarVersion`,
`pr7-build-…`, `dev-<commit>[-dirty]`), Modrinth and Discord say "TAERRA Build 412 for Paper", `/taerra` shows
"Build 412 (a1b2c3d)". Maven coordinates, the API jar and the plugin and mod metadata keep the plain version.

`build.yml` is the only reusable Gradle workflow. It runs `./gradlew build`, then `./gradlew :api:publish` as a
separate step, so nothing is published before every test has passed. Caching is `gradle/actions/setup-gradle` alone —
never add `cache: gradle` to `setup-java`. The Paper dev bundle is pinned in the catalog, not `+`: a dynamic version
misses the userdev CI cache.

## Release pipeline

`release.yml` runs on every push to `main`. `.github/actions/classify-changes` splits the push into three streams:
`mod/` is the mod, shared build files (`gradle/`, `build-logic/`, root `*.gradle.kts`, `gradle.properties`, wrapper)
are both the plugin and the mod, `site/` and what it takes from `branding/` and the web map are the website, the rest
of `branding/` and `.github/`, and Markdown are none, anything else is the plugin. `build.yml` runs once if the plugin or the mod
changed, then:

- plugin: tag `plugin-<n>`, plugin and API jars attached, API published to GitHub Packages
- mod: tag `mod-<n>`, mod jar attached
- website: `site.yml` builds `main` with `.github/actions/build-site`, the deploy goes to GitHub Pages, then the one
  `site` tag moves to that commit (no release). `site.yml` is the only way to Pages, and runs by hand for a deploy
  alone; its build and deploy jobs each have a concurrency group, so deploys from release runs, the download mirror and
  hand runs land in order

With `release-tags: 'true'`, `classify-changes` diffs each stream against the newest `plugin-`/`mod-` tag HEAD descends
from, or the `site` tag, falling back to `github.event.before`. Required: GitHub keeps one pending run per concurrency
group, so a cancelled run's commits would otherwise never be classified.

`.github/actions/github-release` writes notes with `release-notes.sh` from the commit log, grouped by the commit types
the `commit-msg` hook enforces. Its pathspecs must match `classify-changes`; edit both together, and the hook's types
together with the notes' sections. Both resolve the previous tag with `git describe` (ancestry), never tag dates.

Both streams share one release list, so `cleanup-releases.yml` refuses to run without a tag prefix. It never deletes a
release with a `modrinth.json`: those are the download mirror.
`cleanup-packages.yml` only deletes versions matching `^\d+-[0-9a-f]{7,}$` and fails if nothing matches, and
`pr-cleanup.yml` matches the `pr<n>-` prefix; both follow the version format in `settings.gradle.kts`. The package API
calls pick `/users/...` or `/orgs/...` from the repository owner type.

`modrinth.yml` is the only way to Modrinth, run by hand. By default (`newest`) it takes the newest `plugin-<n>` and the
newest `mod-<n>` release, which need not share a build number, and releases each that has no `modrinth.json` yet;
plugin or mod releases that part alone, its newest build or the build given, released or not. It uploads each GitHub
release's jar, never a rebuild, as a Paper or Fabric version of the one project, slug `taerra` (`MODRINTH_TOKEN`
secret), one part after the other in one job. Its changelog is `release-notes.sh` with `AUDIENCE=players` since the
commit in the newest Modrinth version number of that loader. A build already on Modrinth skips the upload and Maven
Central and is only mirrored and announced, which finishes a run that failed after its upload; one part failing
leaves the other to finish.

Every version on Modrinth is mirrored on taerra.dev/download/, every build, never only the newest. After the uploads,
`modrinth.yml` calls `downloads.yml` once per part with `skip-site` (run by hand for a build already on Modrinth, it
deploys too): it looks the version up with the token, checks the GitHub release's jar against Modrinth's SHA-512 and
attaches what Modrinth shows for it as `modrinth.json` to that release. `modrinth.yml` then deploys the site once,
and announces each part in its own Discord message. `site/scripts/downloads.sh` (`bun run downloads`, run by
`build-site`, needs `gh`) fetches every release with a `modrinth.json` and its jar into `site/downloads/`, ignored by
git, and the page serves the jars from `/download/<file>.jar`. Files only ever come from the GitHub releases, never
from Modrinth's CDN. The Discord embed links the Modrinth version and the mirror once the project is public there,
and only the mirror until then.

For the plugin, `modrinth.yml` then calls `build.yml` with `central: true`: it rebuilds and tests the tag under the
same version and publishes the API to Maven Central, signed, through the Central Portal API (`CENTRAL_USERNAME`,
`CENTRAL_PASSWORD`, `SIGNING_KEY`, `SIGNING_PASSWORD` secrets). Central versions are permanent, so nothing else
publishes there. The API's POM is public and links only to taerra.dev and the hosted license. Every public API type and
member has Javadoc; the `javadoc` task runs doclint with `missing` and fails the build without it.

The Discord channel is public and the repository private, so only `modrinth.yml` posts there, and nothing public
(Discord, Modrinth notes, the mod and plugin metadata, the web map) links into the repository. The public links are
taerra.dev and two redirects on its domain, `download.taerra.dev` to the download page (the Modrinth page once the
project is public there) and `discord.taerra.dev` to the Discord invite, set up with the domain's DNS rather than in
this repository: link those, not modrinth.com or the invite, except for a link to one Modrinth version. Messages are
embeds through `notify-discord.yml`, which blocks mentions. Discord needs the `DISCORD_WEBHOOK_ID` and
`DISCORD_WEBHOOK_TOKEN` secrets.

## Website

`site/` is taerra.dev, a promo page for players and server owners and its download page; aerulion.net/work/taerra stays
the technical deep dive. Astro with Bun, following aerulion.github.io: Prettier, ESLint and `astro check` (`bun run
check`), fonts from Fontsource through Astro's fonts API, except QuinqueFive: `global.css` declares the plugin's copy
with the web map's metric overrides, which cut its line box to the capitals so centring centres them, and the layout
preloads it. Copy lives in `src/data/taerra.ts` (English, at `/`) and `taerra.de.ts` (German, at `/de/`, in the web
map's du voice), never in components, and takes its facts from `branding/modrinth-description.md`; change all three
together. Each English page sends German browsers to its `/de/` twin until the header's language picker stores a choice.
The download page lists the plugin's and the mod's latest release and every mirrored build with its changelog,
Modrinth's Markdown parsed by `src/data/downloads.ts`; with no `site/downloads/` it shows none. Each locale has its own
`404.astro`, out of the sitemap; GitHub Pages serves the English one for every missing path, so it sends `/de/…` and
German browsers on to `/de/404/`. The look is the web map's: its tokens, fonts and slab buttons, the copper edge. Its
pictures are rendered by `branding/generate.py site/` into `src/assets/` and `public/og.png` (`og-download.png` for the
download page); the in-game shots and the web map's icons are imported from where they live. Motion is subtle and off
under `prefers-reduced-motion`: `data-reveal` elements rise in as they scroll into view (`--stagger` delays them), the
hero world floats and, until it is turned by hand, folds from the top-down map into each isometric rotation and back
(`branding/render.html` centres all five pictures on the world's middle at sea level, at one scale, for that), and the
mod screenshots open in a `<dialog>` viewer.

The page's own footer (`Footer.astro`: links, the Mojang disclaimer, the build's commit from `PUBLIC_COMMIT_HASH`, which
`build-site` sets and which is never a link) ends with `src/components/aerulion/`, the aerulion credit shared by every
aerulion project: Nyx only (#000, #fff, Tektur, IBM Plex Mono), no props and no tokens from the host page. Like
aerulion.net it is English on every page, its imprint and privacy links too. Copy the folder as it is, spread its
`nyxFonts` into `astro.config` and place it last in the page's footer.

It is the website stream of `release.yml`, checked by `pr.yml` with the same `build-site` action, and `release.yml` also
rebuilds it on 1 January for the footer's year. It is public, so like everything else public it never links into the
repository.

## Pull requests

`pr.yml` always classifies, checks every commit message and the PR title with `.githooks/commit-msg` (a squash merge
lands the title as the subject), and validates. With the `deploy` label it also publishes a `pr<n>-…` API snapshot
when the plugin changed and uploads the plugin and mod jars as workflow artifacts, listed in one PR comment. Label
events other than `deploy` and title edits skip everything but the message check, in their own concurrency group.
