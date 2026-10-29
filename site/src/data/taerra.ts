import {de} from './taerra.de';

export const links = {
    demo: 'https://map.corpium.net/',
    discord: 'https://discord.taerra.dev',
    source: 'https://github.com/aerulion/taerra',
    issues: 'https://github.com/aerulion/taerra/issues',
    contact: 'mailto:contact@taerra.dev',
    deepDive: 'https://aerulion.net/work/taerra/'
};

export const showreel: string | null = '7u9XtATjbF8';

export const languages = {en: 'English', de: 'Deutsch'};

export type Language = keyof typeof languages;

export type Marked = [string, string, string];

export interface Stat {
    value: string;
    unit?: string;
    label: string;
}

export interface Feature {
    title: string;
    text: string;
}

export interface Group {
    kicker: string;
    title: string;
    features: Feature[];
}

export interface Step {
    title: string;
    text: string;
    command?: string;
}

export interface Command {
    command: string;
    text: string;
}

export interface Permission {
    permission: string;
    standard: string;
    text: string;
}

const en = {
    meta: {
        title: 'TAERRA: a live map for Minecraft servers',
        description:
            'A live map for Minecraft servers: block by block, with sunlight and shadows, isometric or top-down, rendered on your Paper server. With a Fabric mod for the same map in game.',
        ogImageAlt: 'The TAERRA logo over a blurred map of a Minecraft world'
    },

    header: {
        home: 'TAERRA, back to the top',
        sections: 'Sections',
        language: 'Language',
        nav: [
            {label: 'Features', href: '#features'},
            {label: 'Mod', href: '#mod'},
            {label: 'Get started', href: '#start'},
            {label: 'Commands', href: '#reference'}
        ],
        download: 'Download'
    },

    clipboard: {
        copy: 'Copy',
        copied: 'Copied'
    },

    hero: {
        kicker: 'Paper + Fabric',
        title: ['Your world, ', 'live', ' in 3D'] as Marked,
        tagline: 'One map, rendered on your server: in the browser, and in game.',
        lede: 'TAERRA turns your Minecraft worlds into a live map anyone can open: block by block, with sunlight and shadows, seen from above or at an angle from all four sides. Players get the same map in game, with everything the browser has and a minimap on top.',
        download: 'Get TAERRA',
        demo: 'See it live',
        note: 'Free for every server, commercial ones included.',
        alt: 'A whole Minecraft world rendered by TAERRA as one isometric block of terrain',
        rotate: 'Turn it',
        flatten: 'Flatten it',
        tilt: 'Tilt it',
        top: 'Top-down',
        facing: ['Looking northwest', 'Looking northeast', 'Looking southeast', 'Looking southwest']
    },

    showreel: {
        kicker: 'Showreel',
        title: 'See it move',
        play: 'Play the showreel',
        frame: 'TAERRA showreel',
        note: 'Plays from YouTube, which sets its own cookies once started.'
    },

    speed: {
        kicker: 'Speed',
        title: 'A whole world in minutes',
        lede: 'Everything is rendered on your server, on its own threads. Visitors only download finished images, so the map is as fast on a phone as on a gaming PC.',
        stats: [
            {
                value: '2:21',
                unit: 'min',
                label: 'for a 6,000-block-radius world, about 440,000 chunks, in every view at once.'
            },
            {
                value: '~2',
                unit: 'ms',
                label: 'per tick on the main thread for live updates. Full renders take nothing from it.'
            },
            {
                value: '50',
                unit: '%',
                label: 'of your CPU cores render by default, always leaving at least two to the game.'
            }
        ] satisfies Stat[],
        footnote: 'Measured on an Intel Core Ultra 7 265 with 64 GB of RAM.'
    },

    views: {
        kicker: 'The map',
        title: 'A map worth looking at',
        lede: 'Switch between isometric and top-down any time. The isometric view turns in four directions, so nothing hides behind a mountain.',
        rows: {
            detail: {
                title: 'Every block, one cube',
                text: 'Lit, shaded and biome-tinted, from the whole world at a glance down to a single block. A warm sun and a cool sky turn shadows blue-violet instead of muddy gray, and water is something you see into: reefs glow turquoise, trenches fade to navy, a sunken build shows through.',
                alt: 'A close-up of the isometric map: forest, a river, cherry trees and hills, every block a shaded cube'
            },
            topDown: {
                title: 'A classic flat map, one click away',
                text: 'Prefer it flat? The top-down view is right there. Switch the map to gray when the markers on it matter more than the terrain.',
                alt: 'The same world from straight above, as a flat map'
            },
            dimensions: {
                title: 'Overworld, Nether, End',
                text: 'Every dimension, and every custom world, gets a map of its own.',
                nether: 'The whole Nether as one isometric block',
                end: 'The End’s islands as one isometric block'
            }
        }
    },

    features: {
        label: 'Features',
        groups: [
            {
                kicker: 'Live',
                title: 'Always the world as it is now',
                features: [
                    {
                        title: 'Players move live',
                        text: 'Positions update every second, with a player list and a follow mode that keeps someone in view, even across worlds.'
                    },
                    {
                        title: 'Colors from the Locator Bar',
                        text: 'Every player wears the color of their dot on the Locator Bar, so you know who is who at a glance, on the map and in game.'
                    },
                    {
                        title: 'The map keeps up',
                        text: 'Every change is recorded, built by players or grown by nature, and redrawn on an interval you choose. A spot changed a hundred times is drawn once.'
                    },
                    {
                        title: 'Watch it render',
                        text: 'A render shows its progress right on the map, with the time it has left, in the browser and in game. Staff start a full render, or cancel one, with a right-click.'
                    },
                    {
                        title: 'Discover the world',
                        text: 'Discoverable regions stay under fog until you walk into them, then clear for you alone, with a sound and a burst of sparks on the web map, a toast in the mod or a line in chat.'
                    },
                    {
                        title: 'Regions on the map',
                        text: 'Every region shows as an outline in its own color, and gets its name and fill once you have found it, so you always see what is left to explore.'
                    }
                ]
            },
            {
                kicker: 'For servers',
                title: 'Made to run on yours',
                features: [
                    {
                        title: 'Logins without passwords',
                        text: 'Type the short code the map shows after /taerra login, and the browser logs itself in for 30 days, then hands the login to your phone as a QR code. Codes work once, and players see every login in game.'
                    },
                    {
                        title: 'Waypoints, areas and lines',
                        text: 'In named groups and colors, private ones too, each with an optional description and an icon: built in, uploaded or drawn by your staff or added by plugins. Players with permission draw, move and recolor them on the map: areas chunk by chunk or point by point, lines straight or smooth.'
                    },
                    {
                        title: 'Waypoints of your own',
                        text: 'Every logged-in player keeps up to 100 waypoints, areas and lines only they see, in the browser and in game, and a Last Death waypoint marks where they died. Other plugins can show players their own markers too, like their homes.'
                    },
                    {
                        title: 'Privacy built in',
                        text: 'Spectators show up like players in a world the viewer cannot see: with their name, but without their world and position. Vanished and invisible players stay off the map, and players who hide from server lists show as an anonymous dot. Player heads come from your server, so viewing the map never reveals a visitor to Mojang.'
                    },
                    {
                        title: 'Private worlds and layers',
                        text: 'Hide a world or a layer from everyone except logged-in players with the right permission, or only where the players in a world are, and choose who may teleport, draw markers or render from the map. Staff can also see the loaded chunks, colored by load level.'
                    },
                    {
                        title: 'Right-click anywhere',
                        text: 'Copy coordinates, or a link that opens the map right there. Logged-in players with permission teleport to the spot in one click.'
                    },
                    {
                        title: 'One jar, set up live',
                        text: 'No database required, no CDN, no web server to set up. Staff change worlds, their order, names and limits, private layers, the server name and the renderer on a settings page in the browser or in game, together and without a restart, or with /taerra config.'
                    },
                    {
                        title: 'Search everything',
                        text: 'Press [/] to find players, markers, worlds and coordinates across every world. The mod has the same search on a key of its own.'
                    },
                    {
                        title: 'Show only what is ready',
                        text: 'Limit the map to a circle, square or rectangle, so it ends where your playable area does, and draw named regions chunk by chunk. Cover one in fog while staff build there, or in a thin veil that only hints at what lies beneath, and let every player clear the fog for themselves by walking in.'
                    },
                    {
                        title: 'Installs like an app',
                        text: 'On phones and desktops, as soon as the map is served over HTTPS.'
                    },
                    {
                        title: 'English and German',
                        text: 'The web map, the mod and every message in game speak both.'
                    },
                    {
                        title: 'Export as images',
                        text: 'One command turns the map into images, ready for a wiki page or a poster.'
                    }
                ]
            },
            {
                kicker: 'Plays well with others',
                title: 'Fits the plugins you already have',
                features: [
                    {title: 'WorldGuard', text: 'Regions as a layer, private to staff unless you share it.'},
                    {title: 'Towny', text: 'Towns as a layer, each with its mayor, nation and residents.'},
                    {title: 'GriefPrevention', text: 'Claims as a layer, private to staff unless you share it.'},
                    {title: 'PlotSquared', text: 'Claimed plots as a layer, each with its owners and members.'},
                    {title: 'World borders', text: "The vanilla world border and ChunkyBorder's borders as a layer."},
                    {
                        title: 'Chunky',
                        text: 'Stays out of the way while Chunky generates, then says when a full render is worth it.'
                    },
                    {
                        title: 'dynmap',
                        text: 'Bring your markers over as waypoints with one command, their icons as the closest built-in ones.'
                    }
                ]
            }
        ] satisfies Group[]
    },

    mod: {
        kicker: 'Fabric mod',
        title: 'The same map, in game',
        lede: 'The optional Fabric mod brings your server’s map to your players. Join a server running TAERRA and it connects by itself, already logged in, so no /taerra login is needed in game.',
        note: 'Needs Fabric API and a server running the plugin. Mod Menu is optional.',
        download: 'Get the mod',
        rows: {
            map: {
                title: 'The whole map on M',
                text: 'Press [M] for the full world map, with everything the web map has: both views and all four rotations, color or gray, layers, the player list and follow mode. [K] searches players, markers, worlds and coordinates. Right-click any spot to navigate there, set a waypoint, or copy its coordinates or a link to it.',
                alt: 'The mod’s world map in the isometric view, the whole world as one tilted block over the game, with waypoints at spawn and a village'
            },
            minimap: {
                title: 'A minimap, your way',
                text: 'Size it, zoom it with [=] and [-], put it on either side and let it turn with you. Below it, pick the lines you need: coordinates and their Nether counterparts, chunk, facing, biome, weather, time and day, light level, real time and FPS. Markers past its edge stay in view, so nothing nearby gets lost.',
                alt: 'A cherry grove in game, with the minimap in the top right corner and the coordinates and biome below it'
            },
            labels: {
                title: 'Waypoints where they are',
                text: 'Waypoints float as labels over the places they mark, with the distance to each, so you find your way without opening a map. [B] hides them when you want a clear view.',
                alt: 'Plains seen from a hill, with the labels Village and Lumber Camp and their distances floating over the places they mark and the minimap in the corner'
            },
            navigation: {
                title: 'Find your way there',
                text: 'Navigate to a waypoint, a player or any spot, started on the map in game or from the web map while you play. A beacon beam rises at the target, and an arrow at the top of the screen shows the way and the distance, through a portal when it is in another dimension.',
                alt: 'Navigating in game: a beacon beam rises at the target, and the arrow at the top of the screen shows its coordinates and the distance'
            },
            list: {
                title: 'Your waypoints, one key away',
                text: '[H] sets one where you stand, and on the map you draw areas and lines too. [J] opens your list: filter it, sort it by name or distance, hide single ones, and export them as a file to share or keep, or import one. When you die, a Last Death waypoint marks the spot.',
                alt: 'The My Waypoints list with three waypoints, each with its world, coordinates and distance and buttons to show, move, navigate to, center, edit and delete it'
            },
            layers: {
                title: 'Layers and staff tools',
                text: 'WorldGuard regions and world borders are layers you switch on. With permission, select chunks to render them again, draw regions and fog them, and watch a render’s progress right on the map. Staff change the server’s settings on a screen of their own.',
                alt: 'A close isometric view of a coast with WorldGuard regions outlined in green, a hidden area in caution stripes and 112 chunks selected for rendering again'
            }
        },
        more: {
            title: 'And also',
            items: [
                {title: 'Teleport from the map', text: 'Right-click to go anywhere, where the server lets you.'},
                {
                    title: 'Your devices',
                    text: 'See every browser logged in as you, and log any of them out, from the settings.'
                },
                {
                    title: 'Every key yours',
                    text: 'Change any key in Minecraft’s controls. The settings open from the world map or Mod Menu.'
                },
                {
                    title: 'Always in step',
                    text: 'When the mod and the server’s plugin don’t match, it tells you which one to update.'
                },
                {
                    title: 'Light on your game',
                    text: 'It renders nothing itself: it draws the images the server already made.'
                },
                {title: 'English and German', text: 'Every screen and message speaks both.'}
            ] satisfies Feature[]
        },
        viewer: {
            label: 'Screenshot viewer',
            open: 'Enlarge',
            close: 'Close',
            previous: 'Previous screenshot',
            next: 'Next screenshot'
        }
    },

    start: {
        kicker: 'Getting started',
        title: 'Six steps to a live map',
        steps: [
            {
                title: 'Install',
                text: 'Put the plugin jar in plugins/ and start the server. It downloads its libraries on the first start, so the server needs internet access once.'
            },
            {
                title: 'Choose your worlds',
                text: 'None are on the map until you enable them.',
                command: '/taerra config world minecraft:overworld enable'
            },
            {
                title: 'Render',
                text: 'Draw the whole world once. From then on, changes are drawn by themselves.',
                command: '/taerra render full minecraft:overworld'
            },
            {
                title: 'Open the map',
                text: 'The plugin serves it itself, no web server needed.',
                command: 'http://<your-server>:8100'
            },
            {
                title: 'Make it public',
                text: 'Put the map behind a reverse proxy with HTTPS, on its own domain or under a path like /map/, and tell TAERRA its address. Login links and link previews use it.',
                command: '/taerra config web.public-url https://map.example.com'
            },
            {
                title: 'Name it',
                text: 'Your server’s name in the map’s header, page title and link previews.',
                command: '/taerra config web.name "My Server"'
            }
        ] satisfies Step[],
        requirements: {
            plugin: {part: 'Plugin', needs: 'Paper 26.3 and Java 25. Spigot and Folia are not supported.'},
            mod: {part: 'Mod', needs: 'Fabric Loader, Fabric API and Minecraft 26.3, on a server running the plugin'},
            updates: {
                part: 'Updates',
                needs: 'Update the plugin first. A mod newer than the plugin asks players to have the server updated. Plugin and mod share one build counter, so their numbers skip and don’t have to match.'
            }
        },
        more: [
            'Every setting can be changed in game with /taerra config, or on the settings page of the map. Every build and what changed in it is on the ',
            'download page',
            '.'
        ] as Marked
    },

    reference: {
        kicker: 'Reference',
        title: 'Commands and permissions',
        lede: 'Everything runs through /taerra. The permissions work with LuckPerms or any other permissions plugin, and every private world, layer and world’s players get one of their own. With LuckPerms, changes reach the map right away, even for players who are offline.',
        commands: {
            title: 'Commands',
            head: ['Command', 'What it does'],
            rows: [
                {command: '/taerra status', text: 'What the renderer is doing, with details on hover'},
                {command: '/taerra render full <world>', text: 'Render a whole world'},
                {
                    command: '/taerra render radius <world> <radius> [x z]',
                    text: 'Render an area around you, around spawn or around a point'
                },
                {command: '/taerra render image <world> [zoom]', text: 'Export the map as images'},
                {command: '/taerra render cancel', text: 'Stop a running render'},
                {command: '/taerra render flush', text: 'Drop every queued tile, unrendered changes included'},
                {
                    command: '/taerra limit <world> circle|square|rectangle …',
                    text: 'Set how much of a world the map shows'
                },
                {
                    command: '/taerra config …',
                    text: 'Change settings, enable, name, order and hide worlds, make layers private or give them an icon'
                },
                {command: '/taerra waypoint add|remove …', text: 'Add or remove waypoints'},
                {command: '/taerra import dynmap <file>', text: 'Import markers from dynmap'},
                {command: '/taerra login [code]', text: 'Log in to the web map'},
                {command: '/taerra logout [player]', text: 'Log out of the web map in every browser'},
                {command: '/taerra devices', text: 'See and log out your web map devices'},
                {command: '/taerra mod', text: 'See who plays with the TAERRA mod, and which build'},
                {command: '/taerra debug', text: 'Copy the versions and setup a bug report needs'}
            ] satisfies Command[]
        },
        permissions: {
            title: 'Permissions',
            head: ['Permission', 'Default', 'Allows'],
            rows: [
                {
                    permission: 'taerra.login',
                    standard: 'everyone',
                    text: '`/taerra login`, `/taerra logout` and `/taerra devices`, and being logged in by the mod'
                },
                {
                    permission: 'taerra.logout.others',
                    standard: 'op',
                    text: 'Logging other players out with `/taerra logout <player>`'
                },
                {permission: 'taerra.status', standard: 'op', text: '`/taerra status`'},
                {permission: 'taerra.mods', standard: 'op', text: '`/taerra mod`'},
                {permission: 'taerra.debug', standard: 'op', text: '`/taerra debug`'},
                {permission: 'taerra.teleport', standard: 'op', text: 'Teleporting from the map and the waypoint list'},
                {
                    permission: 'taerra.render.world',
                    standard: 'op',
                    text: 'Rendering a whole world again, with `/taerra render full` or from the map'
                },
                {
                    permission: 'taerra.render.area',
                    standard: 'op',
                    text: 'Rendering an area again, with `/taerra render radius` or by selecting chunks on the map'
                },
                {
                    permission: 'taerra.render.cancel',
                    standard: 'op',
                    text: 'Canceling the running render, with `/taerra render cancel` or from the map'
                },
                {permission: 'taerra.render.flush', standard: 'op', text: '`/taerra render flush`'},
                {permission: 'taerra.render.image', standard: 'op', text: '`/taerra render image`'},
                {permission: 'taerra.limit', standard: 'op', text: '`/taerra limit`'},
                {
                    permission: 'taerra.config',
                    standard: 'op',
                    text: '`/taerra config` and the settings on the map and in the mod'
                },
                {permission: 'taerra.import', standard: 'op', text: '`/taerra import`'},
                {
                    permission: 'taerra.waypoint.edit',
                    standard: 'op',
                    text: 'Adding, editing, moving and removing the server’s waypoints, with `/taerra waypoint` or on the map'
                },
                {
                    permission: 'taerra.waypoint.private-groups',
                    standard: 'op',
                    text: 'Creating private waypoint groups'
                },
                {permission: 'taerra.waypoint.personal', standard: 'everyone', text: 'Keeping waypoints only you see'},
                {
                    permission: 'taerra.waypoint.death',
                    standard: 'everyone',
                    text: 'Getting a personal waypoint where you last died'
                },
                {
                    permission: 'taerra.icon.edit',
                    standard: 'op',
                    text: 'Uploading, renaming, replacing and deleting the server’s map icons'
                },
                {
                    permission: 'taerra.region.edit',
                    standard: 'op',
                    text: 'Drawing, naming, fogging and removing regions'
                },
                {
                    permission: 'taerra.region.reset',
                    standard: 'op',
                    text: 'Resetting who discovered a region, with `taerra.region.edit`'
                },
                {
                    permission: 'taerra.see.hidden-players',
                    standard: 'op',
                    text: 'Seeing hidden and anonymous players on the map'
                },
                {
                    permission: 'taerra.see.fog',
                    standard: 'op',
                    text: 'Seeing through every region’s fog, and the players in it'
                },
                {
                    permission: 'taerra.world.<world>',
                    standard: 'op',
                    text: 'Seeing a private world, such as `taerra.world.minecraft.the_end`'
                },
                {
                    permission: 'taerra.players.<world>',
                    standard: 'op',
                    text: 'Seeing where players are in a world with private players, such as `taerra.players.minecraft.overworld`'
                },
                {
                    permission: 'taerra.layer.<layer>',
                    standard: 'op',
                    text: 'Seeing a private layer, such as `taerra.layer.worldguard`'
                },
                {permission: 'taerra.hide', standard: 'nobody', text: 'Being hidden from the map'}
            ] satisfies Permission[]
        }
    },

    closing: {
        kicker: 'Early access',
        title: 'Try it out, break it, report it',
        text: [
            'TAERRA already runs on live servers, and it is still growing: things can change between versions, and you may find rough edges. If something breaks or looks wrong, or you have an idea, ',
            'report it on GitHub',
            '. Every report makes it better for everyone, and questions are welcome on Discord.'
        ] as Marked,
        download: 'Get TAERRA',
        discord: 'Join the Discord',
        demo: 'See it live',
        license: 'Free to use on any server, commercial ones included. Source available on GitHub.',
        deepDive: ['Curious how it works under the hood? Read the ', 'technical deep dive', '.'] as Marked
    },

    downloads: {
        meta: {
            title: 'Download TAERRA: every build of the plugin and the mod',
            description:
                'Download the TAERRA plugin for Paper and the Fabric mod: the newest build and every one before it, each with what changed.',
            image: {
                src: '/og-download.png',
                alt: 'The TAERRA logo with download buttons for the Paper plugin and the Fabric mod, beside an isometric Minecraft world'
            }
        },
        kicker: 'Downloads',
        title: 'Get TAERRA',
        lede: 'Running a server? You need the plugin. Playing on a server that runs TAERRA? Get the mod: it is optional and brings the same map into your game.',
        parts: {
            plugin: {
                label: 'Plugin',
                loader: 'Paper',
                audience: 'For server owners',
                title: 'Paper Plugin',
                text: 'Renders the map on your server and serves it to every browser. Put the jar in plugins/.'
            },
            mod: {
                label: 'Mod',
                loader: 'Fabric',
                audience: 'For players',
                title: 'Fabric Mod',
                text: 'The server’s map in your game, with a minimap and waypoints in the world. Put the jar in mods/.'
            }
        },
        latest: 'Latest build',
        build: 'Build',
        download: 'Download',
        none: 'No build released yet.',
        minecraft: 'Minecraft',
        changes: 'What changed',
        modrinth: 'Download on Modrinth',
        channels: {release: 'Release', beta: 'Beta', alpha: 'Alpha'},
        archive: {
            kicker: 'Archive',
            title: 'Every build',
            lede: 'Older builds stay here in case you need to go back. Plugin and mod share one build counter, so their numbers skip.',
            filter: 'Show builds',
            all: 'All',
            empty: 'Builds show up here as soon as one is released.'
        },
        credit: [
            'The Paper logo belongs to PaperMC. The Fabric logo is by FabricMC, under ',
            'CC BY-NC-SA 4.0',
            '.'
        ] as Marked,
        sections: {
            Features: 'New',
            Fixes: 'Fixed',
            Performance: 'Faster',
            Contributors: 'Contributors'
        } as Record<string, string>,
        phrases: {
            'The first release on Modrinth.': 'The first released build.'
        } as Record<string, string>
    },

    footer: {
        elsewhere: 'TAERRA elsewhere',
        nav: {
            download: 'Downloads',
            demo: 'Live demo',
            discord: 'Discord',
            source: 'Source code',
            contact: 'Contact',
            deepDive: 'Technical deep dive'
        },
        build: 'Build',
        disclaimer: 'Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.'
    },

    notFound: {
        title: 'Page not found – TAERRA',
        heading: 'Page not found',
        text: 'This chunk was never rendered: the page you are looking for does not exist.',
        back: 'Back to TAERRA'
    }
};

export type Copy = typeof en;

export function copy(locale: string | undefined): Copy {
    return locale === 'de' ? de : en;
}
