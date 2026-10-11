import {de} from './taerra.de';

export const links = {
    modrinth: 'https://download.taerra.dev',
    demo: 'https://map.corpium.net/',
    discord: 'https://discord.taerra.dev',
    deepDive: 'https://aerulion.net/work/taerra/'
};

export const showreel: string | null = '7u9XtATjbF8';

export const languages = {en: 'English', de: 'Deutsch'};

export type Language = keyof typeof languages;

export type Marked = [string, string, string];

export interface Stat {
    value: string;
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
            'A live map for Minecraft servers: every block a lit cube, isometric or top-down, rendered on your Paper server. With a Fabric mod for the same map in game.',
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

    hero: {
        kicker: 'Paper + Fabric',
        title: ['Your world, ', 'live', ' in 3D'] as Marked,
        tagline: 'One map, rendered on your server: in the browser, and in game.',
        lede: 'TAERRA turns your Minecraft worlds into a live map anyone can open: every block a lit, shaded cube, seen from above or at an angle from all four sides. Players get the same map in game, with everything the browser has and a minimap on top.',
        modrinth: 'Get it on Modrinth',
        demo: 'See it live',
        note: 'Free for every server, commercial ones included.',
        alt: 'A whole Minecraft world rendered by TAERRA as one isometric block of terrain',
        rotate: 'Turn it',
        top: 'Top-down',
        facing: ['Looking north-west', 'Looking north-east', 'Looking south-east', 'Looking south-west']
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
                value: '2:46',
                label: 'for a 6,000-block-radius world, about 560,000 chunks, in every view at once. Another widely used map plugin needed more than sixteen hours for the same world.'
            },
            {value: '~2 ms', label: 'per tick on the main thread for live updates. Full renders take nothing from it.'},
            {value: '½', label: 'of your CPU cores render by default, always leaving at least two to the game.'}
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
                        title: 'Colors from the locator bar',
                        text: 'Every player wears the color of their dot on the locator bar, so you know who is who at a glance, on the map and in game.'
                    },
                    {
                        title: 'The map keeps up',
                        text: 'Every change is recorded, built by players or grown by nature, and redrawn on an interval you choose. A spot changed a hundred times is drawn once.'
                    },
                    {
                        title: 'Watch it render',
                        text: 'A render shows its progress right on the map, with the time it has left, in the browser and in game.'
                    }
                ]
            },
            {
                kicker: 'For servers',
                title: 'Made to run on yours',
                features: [
                    {
                        title: 'Logins without passwords',
                        text: 'The map shows a short code to type after /taerra login in game, and the browser logs in by itself. A logged-in browser shows a QR code for your phone. Codes work once, browsers stay logged in for 30 days, and players are told in game whenever a browser logs in as them and can log out any device.'
                    },
                    {
                        title: 'Private worlds and layers',
                        text: 'Hide a world or a layer from everyone except logged-in players with the right permission.'
                    },
                    {
                        title: 'Teleport from the map',
                        text: 'Logged-in players with permission right-click anywhere on the map to go there.'
                    },
                    {
                        title: 'Waypoints, areas and lines',
                        text: 'In named groups and colors, private ones too, each with an optional description. Players with permission draw, move and recolor them right on the map, areas from selected chunks or point by point, lines straight or smooth, and the mod shows waypoints as labels in the world itself.'
                    },
                    {
                        title: 'Waypoints of your own',
                        text: "Every logged-in player keeps up to 100 waypoints, areas and lines only they see, in the browser and in game, drawn as gems and dashed lines so they never pass for the server's. A list, in the browser and in the mod, sorts them by name or distance, hides single ones and exports them as a JSON file to share or back up. When you die, a Last Death waypoint marks the spot. Other plugins can show players their own markers the same way, like their homes."
                    },
                    {
                        title: 'Privacy built in',
                        text: 'Spectators, vanished and invisible players stay off the map, and players who hide from server lists show as an anonymous dot. Player heads come from your server, so viewing it never reveals a visitor to Mojang.'
                    },
                    {
                        title: 'One jar',
                        text: 'No external database, no CDN, no web admin panel. Everything is configured in game.'
                    },
                    {
                        title: 'Share any place',
                        text: 'Right-click to copy coordinates, or a link that opens the map right there.'
                    },
                    {
                        title: 'Search everything',
                        text: 'Press / to find players, markers, worlds and coordinates across every world. The mod has the same search on a key of its own.'
                    },
                    {
                        title: 'Hide what is not ready',
                        text: 'Select chunks to render them again, or to cover them in caution stripes while staff build there.'
                    },
                    {
                        title: 'Fits your world',
                        text: 'Limit the map to a circle, square or rectangle, so it ends where your playable area does.'
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
                    {title: 'WorldGuard', text: 'Regions as a layer viewers can switch on.'},
                    {title: 'World borders', text: 'The vanilla border and ChunkyBorder borders as a layer.'},
                    {
                        title: 'Chunky',
                        text: 'Stays out of the way while Chunky generates, then says when a full render is worth it.'
                    },
                    {title: 'dynmap', text: 'Bring your markers over as waypoints with one command.'}
                ]
            }
        ] satisfies Group[]
    },

    mod: {
        kicker: 'Fabric mod',
        title: 'The same map, in game',
        lede: 'The optional Fabric mod brings your server’s map to your players. Join a server running TAERRA and it connects by itself, already logged in, so no /taerra login is needed in game.',
        points: [
            'A full world map that does everything the web map does: both views and all four rotations, layers, players and follow mode, waypoints, search, teleporting, chunk selection and render progress',
            'A minimap you can size, zoom, place on either side and turn with the player, with the lines you pick below it, from coordinates and biome to time and weather',
            'Waypoints in the world, as labels where they are',
            'Navigation to a waypoint, a player or any spot, started in game or from the web map: a beacon beam where it is and an arrow with the distance, through the Nether too'
        ],
        note: 'It renders nothing itself: it draws the images the server already made. Needs Fabric API; Mod Menu is optional.',
        download: 'Get the mod',
        shots: [
            {
                caption: 'The world map, in game',
                alt: 'The mod’s full-screen world map from straight above: green islands and coastline in a blue sea'
            },
            {
                caption: 'Isometric, from all four sides',
                alt: 'The same world map in the isometric view, the whole world as one tilted block over the game behind it'
            },
            {
                caption: 'Regions and waypoints',
                alt: 'A close isometric view of forest and rivers with WorldGuard regions outlined in green and the layer list open'
            },
            {
                caption: 'A minimap, and waypoints in the world',
                alt: 'A player standing on a grassy hill, waypoint labels floating over the landscape and a minimap in the top right corner'
            }
        ],
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
                text: 'Put the map behind a reverse proxy with HTTPS and tell TAERRA its address. Login links and link previews use it.',
                command: '/taerra config web.public-url https://map.example.com'
            },
            {
                title: 'Name it',
                text: 'Your server’s name in the map’s header, page title and link previews.',
                command: '/taerra config web.name "My Server"'
            }
        ] satisfies Step[],
        requirements: [
            {part: 'Plugin', needs: 'Paper 26.3 and Java 25. Spigot and Folia are not supported.'},
            {part: 'Mod', needs: 'Fabric Loader, Fabric API and Minecraft 26.3, on a server running the plugin'},
            {
                part: 'Updates',
                needs: 'Update the plugin first. A mod newer than the plugin asks players to have the server updated. Plugin and mod share one build counter, so their numbers skip and don’t have to match.'
            }
        ],
        more: [
            'Every setting can be changed in game with /taerra config. Downloads and changelogs are on the ',
            'Modrinth page',
            '.'
        ] as Marked
    },

    reference: {
        kicker: 'Reference',
        title: 'Commands and permissions',
        lede: 'Everything runs through /taerra. The permissions work with LuckPerms or any other permissions plugin, and every private world and layer gets one of its own.',
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
                {command: '/taerra config …', text: 'Change settings, enable worlds, make worlds and layers private'},
                {command: '/taerra waypoint add|remove …', text: 'Add or remove waypoints'},
                {command: '/taerra import dynmap <file>', text: 'Import markers from dynmap'},
                {command: '/taerra login [code]', text: 'Log in to the web map'},
                {command: '/taerra logout [player]', text: 'Log out of the web map in every browser'},
                {command: '/taerra devices', text: 'See and log out your web map devices'}
            ] satisfies Command[]
        },
        permissions: {
            title: 'Permissions',
            head: ['Permission', 'Default', 'Allows'],
            rows: [
                {
                    permission: 'taerra.command.login',
                    standard: 'everyone',
                    text: '`/taerra login`, `/taerra logout` and `/taerra devices`'
                },
                {permission: 'taerra.command.status', standard: 'op', text: '`/taerra status`'},
                {permission: 'taerra.command.render', standard: 'op', text: '`/taerra render`'},
                {permission: 'taerra.command.limit', standard: 'op', text: '`/taerra limit`'},
                {permission: 'taerra.command.waypoint', standard: 'op', text: '`/taerra waypoint`'},
                {permission: 'taerra.command.config', standard: 'op', text: '`/taerra config` and `/taerra import`'},
                {permission: 'taerra.command.logout.others', standard: 'op', text: 'Logging other players out'},
                {permission: 'taerra.web.teleport', standard: 'op', text: 'Teleporting from the map while logged in'},
                {
                    permission: 'taerra.web.see-hidden',
                    standard: 'op',
                    text: 'Seeing hidden players on the map while logged in'
                },
                {
                    permission: 'taerra.web.render',
                    standard: 'op',
                    text: 'Rendering selected chunks again while logged in'
                },
                {
                    permission: 'taerra.web.hide',
                    standard: 'op',
                    text: 'Hiding and revealing selected chunks while logged in'
                },
                {
                    permission: 'taerra.web.markers',
                    standard: 'op',
                    text: 'Adding, editing, moving and removing markers while logged in'
                },
                {
                    permission: 'taerra.web.markers.private',
                    standard: 'op',
                    text: 'Creating private marker groups while logged in'
                },
                {
                    permission: 'taerra.web.markers.personal',
                    standard: 'everyone',
                    text: 'Keeping waypoints only you see while logged in'
                },
                {
                    permission: 'taerra.web.world.<world>',
                    standard: '—',
                    text: 'Seeing a private world, such as `taerra.web.world.minecraft.the_end`'
                },
                {
                    permission: 'taerra.web.layer.<layer>',
                    standard: '—',
                    text: 'Seeing a private layer, such as `taerra.web.layer.worldguard`'
                },
                {permission: 'taerra.hide', standard: 'nobody', text: 'Being hidden from the map'}
            ] satisfies Permission[]
        }
    },

    closing: {
        kicker: 'Early access',
        title: 'Try it out, break it, report it',
        text: 'TAERRA already runs on live servers, and it is still growing: things can change between versions, and you may find rough edges. If something breaks or looks wrong, every report on Discord makes it better for everyone.',
        modrinth: 'Get it on Modrinth',
        discord: 'Join the Discord',
        demo: 'See it live',
        license: 'Free to use on any server, commercial ones included. Closed source for now.',
        deepDive: ['Curious how it works under the hood? Read the ', 'technical deep dive', '.'] as Marked
    },

    footer: {
        elsewhere: 'TAERRA elsewhere',
        nav: {
            modrinth: 'Download on Modrinth',
            demo: 'Live map',
            discord: 'Discord',
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
