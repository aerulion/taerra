[![Release](https://github.com/aerulion/taerra/actions/workflows/release.yml/badge.svg?branch=main)](https://github.com/aerulion/taerra/actions/workflows/release.yml)
[![Pull Request](https://github.com/aerulion/taerra/actions/workflows/pr.yml/badge.svg)](https://github.com/aerulion/taerra/actions/workflows/pr.yml)

![TAERRA](branding/images/github/readme-header.png)

# TAERRA

One map, rendered on your server: in the browser, and in game.

| Directory      | Part                                                                          |
|----------------|-------------------------------------------------------------------------------|
| `plugin/`      | The Paper plugin: renders the tiles, serves the web map                       |
| `api/`         | The API other plugins add markers and layers and report block changes through, and its events |
| `mod/`         | The Fabric mod: map screen and minimap, a viewer only                         |
| `tools/mcdata` | Generates `block_data.json` and `colormaps.json`                              |
| `branding/`    | Renders the logo and store images from the map tiles                          |
| `site/`        | taerra.dev, the project's web page (Astro, Bun)                               |

Rendering happens on the server and nowhere else. The mod draws the tiles the plugin serves and speaks the same HTTP
API as the web map, which is why both live in one repository: a change to that API and every client of it belong in
one commit.

## Prerequisites

* **JDK 25**. With [sdkman](https://sdkman.io), a one-time `sdk env install` in the repo installs the pinned Temurin 25
  from `.sdkmanrc` (with `sdkman_auto_env=true` it switches automatically on every entry into the directory).
* **Python 3**. Only for `tools/mcdata`, see its [README](tools/mcdata/README.md), and `branding/`, see
  [its README](branding/README.md).
* **Bun**. Only for the website in `site/`.

## Tasks

``` bash
./gradlew preCommit                  # format everything, then build and test it
./gradlew build                      # compile, test, jar
./gradlew :plugin:test --tests "WebSessionsTest"
./gradlew -q printVersion            # the version this build publishes under
cd site && bun install && bun run dev # taerra.dev on http://localhost:4321
```

**Run `preCommit` before committing.** CI fails on unformatted Java, Kotlin build scripts or JSON. `preCommit` formats
the lot, then runs the full build, so what it leaves behind is what the pipeline will accept.

| Task                         | Purpose                                                        |
|------------------------------|----------------------------------------------------------------|
| `preCommit`                  | Format everything, then build and test                         |
| `:plugin:runDevBundleServer` | Paper in `run/`, with WorldEdit, WorldGuard, Chunky, LuckPerms |
| `:mod:runClient`             | A Minecraft client with the mod loaded                         |
| `runDev`                     | Both at once: offline-mode server, client joins as `TaerraDev` |
| `installGitHooks`            | Point git at `.githooks` (any build does this)                 |

## Versions

Build numbers, not semver. `<build>` is the run number of the release workflow.

```
412-a1b2c3d        release, tagged plugin-412 (plugin) or mod-412 (mod)
pr57-88-a1b2c3d    snapshot from PR #57
a1b2c3d-dev        local build (-dirty-dev with uncommitted changes)
```

Only a release starts with a number, so PR and local builds always sort below one. `./gradlew -q printVersion` is the
single source of truth; no workflow assembles a version string. People read it as "Build 412": the jars are
`taerra-paper-build-412-a1b2c3d.jar` and `taerra-fabric-build-412-a1b2c3d.jar`, while Maven coordinates and the plugin
and mod metadata keep the plain version.

## Releases

Every push to `main` releases whichever parts it changed.

| Touches            | Releases                          | Tags         |
|--------------------|-----------------------------------|--------------|
| anything else      | plugin jar, API → GitHub Packages | `plugin-<n>` |
| `mod/`             | mod jar                           | `mod-<n>`    |
| shared build files | both                              | both         |
| `site/`            | taerra.dev → GitHub Pages         | moves `site` |

Each release starts as a GitHub prerelease: it is a development build until it is published to Modrinth. `<n>` is the
release workflow's run number, shared by both streams, so each stream's tags skip numbers. The shared build files are
`gradle/`, `build-logic/`, the root build scripts and the wrapper. The website also counts the in-game shots, the
showreel thumbnail and the web map's font and icons it takes from elsewhere; the rest of `branding/`, `.github/` and
Markdown are excluded everywhere. Anything else counts as the plugin, so a new top-level file releases a build rather
than silently not releasing one.

Every release runs the whole Gradle build first, and the API is published in a second step, after every test has
passed. The ten most recent releases per stream and the ten most recent API versions are kept, and so is every release
on Modrinth. `manual-cleanup.yml` runs the same cleanup on demand.

Workflows that release, deploy or publish only run in `aerulion/taerra`, never in a fork.

## Modrinth

Plugin and mod are one Modrinth project, told apart by loader. Nothing reaches it on its own: run the **Publish to
Modrinth** workflow. By default it releases the newest `plugin-<n>` and the newest `mod-<n>` that are not on Modrinth
yet; pick `plugin` or `mod` and a build number to release one part alone. It uploads each release's jar, never a
rebuild, with the player-facing changes since the previous Modrinth version of that loader, turns its GitHub
prerelease into a full release (the plugin's as Latest), deploys the website and announces each part on Discord. Only the ten most recent builds per stream still have a
release to publish from.

| Secret                                        | Value                                                                                                                                                    |
|-----------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `MODRINTH_TOKEN`                              | A personal access token with the *Read projects* and *Create versions* scopes; the project is a draft until approved, so even reading it needs the token |
| `DISCORD_WEBHOOK_ID`, `DISCORD_WEBHOOK_TOKEN` | The webhook of the public announcement channel                                                                                                           |

Publishing a plugin build also rebuilds and tests its tag and publishes its API to Maven Central under the same version.
Central versions can never be deleted, so this is the only path there. It needs:

| Secret                                 | Value                                                                 |
|----------------------------------------|-----------------------------------------------------------------------|
| `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` | A Central Portal user token, from an account that owns `net.aerulion` |
| `SIGNING_KEY`                          | An ASCII-armored PGP secret key whose public key is on a keyserver    |
| `SIGNING_PASSWORD`                     | Its passphrase                                                        |

## Website

`site/` is [taerra.dev](https://taerra.dev), the project's page and its download page, built with Astro and Bun. CI
runs `bun run check` (Prettier, ESLint, `astro check`) on every pull request that touches it. The download page lists
every build on Modrinth and links each to its Modrinth version page: `bun run downloads` lists them from Modrinth's
API, and without it the page shows none. `site.yml` is the only way to GitHub Pages and can be run by hand for a deploy
alone.

## Commit messages

`<gitmoji> <type>: <Subject>`. Eleven types, each with exactly one emoji.

```
:sparkles: feat: Add minimap overlay
:bug: fix(tiles): Correct seams between iso tiles
:construction_worker: build!: Drop Java 21 support
```

| Emoji                   | Type       | For                                       |
|-------------------------|------------|-------------------------------------------|
| `:sparkles:`            | `feat`     | a new feature                             |
| `:bug:`                 | `fix`      | a bug fix                                 |
| `:construction_worker:` | `build`    | the build system itself                   |
| `:green_heart:`         | `ci`       | CI configuration and scripts              |
| `:memo:`                | `docs`     | documentation only                        |
| `:recycle:`             | `refactor` | neither fixes a bug nor adds a feature    |
| `:zap:`                 | `perf`     | improves performance                      |
| `:white_check_mark:`    | `test`     | adds or corrects tests                    |
| `:rewind:`              | `revert`   | reverts a previous commit                 |
| `:arrow_up:`            | `deps`     | dependency updates, what dependabot opens |
| `:wrench:`              | `chore`    | anything else                             |

Write the emoji as the shortcode, not the character. A `(scope)` and a `!` for a breaking change are optional, and the
subject starts with a capital letter.

`.githooks/commit-msg` rejects anything else, in the IDE and on the command line alike. Any Gradle build installs it,
so it is enough to have run `./gradlew build` once. Pull requests re-check every commit and the title with the same
script. Merge, revert and fixup messages git writes itself are exempt, as are dependabot's.

These types are also the sections the release notes group by, so a well-formed history writes the changelog.

## Pull requests

Every pull request is validated, and the **✅ Checks** job sums the result up: it is the one check `main` requires. A
title edit or a label other than `deploy` checks the title and repeats the last validation's result rather than running
it again. Add the `deploy` label and the PR also gets a snapshot, refreshed on every push while
the label is on:

| PR changes | Gets                                                   |
|------------|--------------------------------------------------------|
| plugin     | `pr<n>-<build>-<commit>` of the API in GitHub Packages |
| any part   | the plugin and mod jars as workflow artifacts          |

All of it is listed in one comment on the PR. Removing the label or closing the PR deletes the API versions; the jar
artifacts expire on their own. A pull request from a fork gets no secrets and no write access, so it is validated but
gets no snapshot.

## API

Released versions are on Maven Central; `pr<n>-…` snapshots and builds not yet on Modrinth are only in GitHub
Packages, which needs authentication. The plugin ships the API, so depend on it `compileOnly`.

``` kotlin
repositories {
  mavenCentral()
}

dependencies {
  compileOnly("net.aerulion", "taerra-api", "412-a1b2c3d")
}
```

TAERRA also fires Bukkit events, in `net.aerulion.taerra.api.event`: a player discovering a region, regions and
waypoints being created, changed or deleted, web map logins and logouts, and teleports from the map, which a listener
can cancel. Listen to them like any other event:

``` java
@EventHandler
public void onDiscover(final RegionDiscoverEvent event) {
  if (event.region().name().equals("Old Town")) {
    event.player().getInventory().addItem(new ItemStack(Material.EMERALD, 5));
  }
}
```

## License

TAERRA is source available, not open source. [`LICENSE.md`](LICENSE.md) lets you use it freely, commercial servers
included, and publish forks that change it under their own name. It does not let you re-upload TAERRA or use its name
and logo for your own work. Contributions are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md).
