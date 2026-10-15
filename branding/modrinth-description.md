![TAERRA](https://assets.corpium.net/taerra/description-header.png?v=c7977d85)

**A live map of your Minecraft worlds, isometric or top-down, in the browser and in game.**

TAERRA renders your server's worlds into a map anyone can open in a browser, drawn block by block, with sunlight and shadows, seen from above or at an angle from all four sides. It keeps itself up to date as the world changes, shows who is online and where, and with the optional Fabric mod, players get the same map as a minimap, and a full world map in game.

Everything is rendered on the server. Visitors and players only download finished images, so the map is as fast on a phone as on a gaming PC.

**[See it live on map.corpium.net →](https://map.corpium.net/)**

![Showreel](https://assets.corpium.net/taerra/section-showreel.png?v=769ac87e)

<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/7u9XtATjbF8" title="TAERRA showreel" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>

![Early access](https://assets.corpium.net/taerra/section-early-access.png?v=36e9fab5)

TAERRA is early in its development. It already runs on live servers, but it is not feature complete yet, and you may run into bugs or rough edges. Things can still change between versions as it grows.

If something breaks or looks wrong, please report it on [GitHub](https://github.com/aerulion/taerra/issues): every report helps make TAERRA better for everyone. Questions are welcome on [Discord](https://discord.taerra.dev).

![Features](https://assets.corpium.net/taerra/section-features.png?v=b03ca4e5)

### A map worth looking at

- **Isometric and top-down views.** Switch any time. The isometric view turns in four directions, so nothing hides behind a mountain.
- **Every block, one cube.** Lit, shaded, and biome-tinted, from the whole world at a glance down to a single block.
- **Light like a trailer.** A warm sun and a cool sky, so shadows turn blue-violet instead of muddy gray.
- **Water you can see into.** Reefs glow turquoise, trenches fade to navy, and a sunken build shows through.
- **Every dimension.** Overworld, Nether, End, and custom worlds, each on its own map.
- **Color or gray.** Switch the map to gray when the markers on it matter more than the terrain.

### Live

- **Players move live**, updated every second, with a player list, and a follow mode that keeps someone in view, even across worlds.
- **Colors from the locator bar.** Every player wears the color of their dot on the locator bar, so you know who is who at a glance, on the map and in game.
- **The map updates itself.** Every change is recorded, built by players or grown by nature, and redrawn at an interval you choose, 15 minutes by default. A spot changed a hundred times is drawn once.
- **Watch it render.** A render shows its progress right on the map, with the time it has left, in the browser and in game. Staff start a full render of a world, or cancel one, from the map's right-click menu.
- **Installable as an app** on phones and desktops, when the map is served over HTTPS.

### Made for servers

- **Logins without passwords.** The map's login menu shows a short code: type `/taerra login <code>` in game and that browser logs in by itself. `/taerra login` on its own sends a link instead, and a browser that is logged in shows a QR code that logs in a phone. Browsers stay logged in for 30 days, codes and links work once, for five minutes, and players are told in game whenever a browser logs in as them. `/taerra devices`, the map's account menu and the mod list every device that is logged in and log out any one of them. There are no accounts to create and no passwords to leak. Banned players are logged out of the web map on their own, and `/taerra logout <player>` logs anyone out by hand, say after taking their permissions away.
- **Private worlds and layers.** Keep a world, or a layer hidden from everyone except logged-in players with the right permission.
- **Teleport from the map.** Logged-in players with permission right-click the map to teleport there.
- **Waypoints, areas, and lines** in named groups and colors, private ones too, each with an optional description. Players with permission draw, move, and recolor them right on the map, in the browser or in game, with areas traced from selected chunks or clicked point by point, and lines straight or smooth. The mod shows waypoints as labels in the world itself.
- **Waypoints of your own.** Every logged-in player can keep up to 100 waypoints, areas, and lines only they see, in the browser and in game. They are drawn as gems and dashed lines, so they never pass for the server's. A list of them, on the web map and in the mod, sorts by name or distance, filters by world, hides single ones from the map, and exports any of them to a JSON file to share or back up, which imports again by adding to yours or replacing them. When you die, a Last Death waypoint marks the spot, replacing the one before. Other plugins can show players their own markers the same way, like their homes.
- **Privacy built in.** Players in spectator mode, invisible or vanished, don't appear, and neither does anyone with `taerra.hide`. Staff with `taerra.web.see-hidden` still see them, marked as hidden. Players who turned off server listings in their client show as an anonymous gray dot. Player heads are served by your server, so viewing the map never reveals a visitor's address to Mojang.
- **One jar.** No external database, no CDN, and no separate admin panel: everything is configured in game, or on the settings page of the web map and the mod.
- **A settings page** for staff with `taerra.web.config`, in the browser and in the mod: which worlds are on the map, their order and names, private worlds and layers, the built-in layers, the server name, the join hint, and how the renderer and its caches run. Every change applies right away, without a restart. Several staff can edit at once: the page shows who else has it open, and where, updates live as others save, and when two people changed the same setting, asks which to keep. Each setting shows who changed it last. The web server's port and address stay in game only, so the page can never cut itself off.
- **Right-click any place** to copy its coordinates, or a link straight to it.
- **Search everything.** Press `/` to find players, markers, worlds, or coordinates across every world, and jump there. The mod has the same search on a key of its own.
- **Limit the map** to a circle, square, or rectangle, so it ends where your playable area does.
- **Select chunks on the map**, in the browser or in game, to render them again, or to hide them behind caution stripes while staff build there. Players inside a hidden area are hidden too.
- **Loaded chunks** as a private layer for staff, outlined by load level (entity ticking, ticking, loaded), with each area's players, force-loaded chunks and plugin tickets.
- **Export the map as images** with `/taerra render image`.
- **English and German** in the web map, the mod, and every in-game message.

### Plays well with others

- **WorldGuard:** regions as a layer, private to staff unless you share it.
- **World borders:** the vanilla border and ChunkyBorder borders as a layer.
- **Chunky:** while Chunky pre-generates a world, TAERRA stays out of its way, then tells you when a full render is worth starting.
- **dynmap:** bring your existing markers over as waypoints with `/taerra import dynmap`, each marker set becoming a waypoint group.

![Speed](https://assets.corpium.net/taerra/section-speed.png?v=b898f732)

- **A whole world in minutes.** A 6,000-block-radius world, about 440,000 chunks, renders in every view, top-down and all four isometric rotations, in **2 minutes 21 seconds** on an Intel Core Ultra 7 265 with 64 GB of RAM.
- **Easy on your server.** Live updates cost the main thread about 2 ms per tick, full renders nothing at all: rendering runs on its own threads, half your CPU cores by default, always leaving at least two to the game.
- **Light on disk.** Tiles are stored as compact indexed PNGs and only rewritten when they actually changed.

![The mod](https://assets.corpium.net/taerra/section-mod.png?v=38e6afab)

The optional Fabric mod brings the server's map into the game. Join a server running TAERRA, and it connects by itself, already logged in, so no `/taerra login` is needed in game:

- a **full world map** on M that does everything the web map does: both views and all four rotations, layers, players and follow mode, waypoints, search on K, teleporting, chunk selection, and render progress. Right-click any spot to navigate there, set a waypoint, or copy its coordinates or a link
- a **minimap** you can size, zoom, place on either side, and turn with the player, with the lines you pick below it: coordinates, Nether coordinates, chunk, facing, biome, weather, time, day, light level, real time, and FPS
- **waypoints in the world** as labels where they are, with the distance, hidden and shown with B, a list of your own on J to filter, sort, hide, export and import them, and a key to set one where you stand, H by default
- **navigation** to a waypoint, a player, or any spot on the map, started in game or from the web map while you play: a beacon beam where it is, and an arrow with the distance at the top of the screen, through the Nether too
- the **server's settings page** for staff with `taerra.web.config`, the same as on the web map, from the mod's settings

It only works on servers running the TAERRA plugin, and it renders nothing itself: it draws the images the server already made. It needs Fabric API. Mod Menu is optional, for the settings screen.

![Getting started](https://assets.corpium.net/taerra/section-getting-started.png?v=79f9fba5)

1. **Install.** Put the plugin jar in `plugins/` and start the server. On its first start, Paper's library loader downloads the libraries the plugin needs, so the server needs internet access once.
2. **Choose your worlds.** None are on the map until you enable them: `/taerra config world minecraft:overworld enable`
3. **Render.** `/taerra render full minecraft:overworld` draws the whole world. From then on, changes are drawn automatically. `/taerra status` shows the progress.
4. **Open the map** at `http://<your-server>:8100`.
5. **Make it public.** Put the map behind a reverse proxy with HTTPS and set its address with `/taerra config web.public-url https://map.example.com`. Login links and link previews in chat apps use it.
6. **Name it.** `/taerra config web.name "My Server"` shows your server's name in the map's header, page title, and link previews.

Every setting can also be changed in game with `/taerra config`, or on the settings page of the web map and the mod.

![Commands](https://assets.corpium.net/taerra/section-commands.png?v=82d4a213)

| Command | What it does |
|---|---|
| `/taerra status` | What the renderer is doing, with details on hover |
| `/taerra render full <world>` | Render a whole world |
| `/taerra render radius <world> <radius> [x z]` | Render an area around you, around spawn, or around a point |
| `/taerra render image <world> [zoom]` | Export the map as images |
| `/taerra render cancel` | Stop a running render |
| `/taerra render flush` | Drop every queued tile, unrendered changes included |
| `/taerra limit <world> circle\|square\|rectangle …` | Set how much of a world the map shows |
| `/taerra config …` | Change settings, enable, name, order and hide worlds, make layers private |
| `/taerra waypoint add\|remove …` | Add or remove waypoints |
| `/taerra import dynmap <file>` | Import markers from dynmap |
| `/taerra login [code]` | Log in to the web map |
| `/taerra logout [player]` | Log out of the web map in every browser |
| `/taerra devices` | See and log out your web map devices |
| `/taerra mod` | See who plays with the TAERRA mod, and which build |

![Permissions](https://assets.corpium.net/taerra/section-permissions.png?v=259ea2d6)

| Permission | Default | Allows |
|---|---|---|
| `taerra.command.login` | everyone | `/taerra login`, `/taerra logout` and `/taerra devices` |
| `taerra.command.status` | op | `/taerra status` |
| `taerra.command.render` | op | `/taerra render` |
| `taerra.command.limit` | op | `/taerra limit` |
| `taerra.command.waypoint` | op | `/taerra waypoint` |
| `taerra.command.config` | op | `/taerra config` and `/taerra import` |
| `taerra.command.logout.others` | op | Logging other players out |
| `taerra.command.mod` | op | `/taerra mod` |
| `taerra.web.teleport` | op | Teleporting from the map while logged in |
| `taerra.web.see-hidden` | op | Seeing hidden players on the map while logged in |
| `taerra.web.render` | op | Rendering a whole world or selected chunks again, and canceling renders, while logged in |
| `taerra.web.hide` | op | Hiding and revealing selected chunks while logged in |
| `taerra.web.markers` | op | Adding, editing, moving, and removing markers while logged in |
| `taerra.web.markers.private` | op | Creating private marker groups while logged in |
| `taerra.web.markers.personal` | everyone | Keeping waypoints only you see while logged in |
| `taerra.web.config` | op | Changing the settings on the web map and in the mod while logged in |
| `taerra.web.world.<world>` | op | Seeing a private world, e.g. `taerra.web.world.minecraft.the_end` |
| `taerra.web.layer.<layer>` | op | Seeing a private layer, e.g. `taerra.web.layer.worldguard` |
| `taerra.hide` | nobody | Being hidden from the map |

![Requirements](https://assets.corpium.net/taerra/section-requirements.png?v=6a05f98a)

- **Plugin:** Paper 26.3 and Java 25. Spigot and Folia are not supported.
- **Mod:** Fabric Loader, Fabric API, and Minecraft 26.3, on a server running the TAERRA plugin.
- **Updates:** when a new version of both comes out, update the plugin first. A mod newer than the server's plugin tells players to ask the server to update. Plugin and mod share one build counter, so their build numbers skip and don't have to match.

Render threads and cache sizes are configurable, so you decide how much CPU and memory the map may use.

![More](https://assets.corpium.net/taerra/section-more.png?v=73f34c18)

See TAERRA running on a real server at [map.corpium.net](https://map.corpium.net/), and if you are curious how it works under the hood, read the [technical deep dive](https://aerulion.net/work/taerra/). Questions and ideas are welcome on [Discord](https://discord.taerra.dev), bug reports on [GitHub](https://github.com/aerulion/taerra/issues).

TAERRA is free to use on any server, commercial ones included. Its source code is available on [GitHub](https://github.com/aerulion/taerra), and the [license](https://github.com/aerulion/taerra/blob/main/LICENSE.md) has the details.

<sub>NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.</sub>
