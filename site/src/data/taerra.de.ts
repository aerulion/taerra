import type {Copy, Marked} from './taerra';

export const de: Copy = {
    meta: {
        title: 'TAERRA: eine Live-Karte für Minecraft-Server',
        description:
            'Eine Live-Karte für Minecraft-Server: jeder Block ein beleuchteter Würfel, isometrisch oder von oben, gerendert auf deinem Paper-Server. Dazu eine Fabric-Mod.',
        ogImageAlt: 'Das TAERRA-Logo über der unscharfen Karte einer Minecraft-Welt'
    },

    header: {
        home: 'TAERRA, zurück nach oben',
        sections: 'Abschnitte',
        language: 'Sprache',
        nav: [
            {label: 'Funktionen', href: '#features'},
            {label: 'Mod', href: '#mod'},
            {label: 'Loslegen', href: '#start'},
            {label: 'Befehle', href: '#reference'}
        ],
        download: 'Download'
    },

    hero: {
        kicker: 'Paper + Fabric',
        title: ['Deine Welt, ', 'live', ' in 3D'] as Marked,
        tagline: 'Eine Karte, gerendert auf deinem Server: im Browser und im Spiel.',
        lede: 'TAERRA macht aus deinen Minecraft-Welten eine Live-Karte, die alle öffnen können: jeder Block ein beleuchteter, schattierter Würfel, von oben oder schräg von allen vier Seiten. Spieler:innen bekommen dieselbe Karte im Spiel, mit allem, was der Browser kann, und dazu einer Minikarte.',
        modrinth: 'Auf Modrinth holen',
        demo: 'Live ansehen',
        note: 'Kostenlos für jeden Server, auch für kommerzielle.',
        alt: 'Eine ganze Minecraft-Welt, von TAERRA als ein isometrischer Block Landschaft gerendert',
        rotate: 'Drehen',
        top: 'Draufsicht',
        facing: ['Blick nach Nordwesten', 'Blick nach Nordosten', 'Blick nach Südosten', 'Blick nach Südwesten']
    },

    showreel: {
        kicker: 'Showreel',
        title: 'In Bewegung',
        play: 'Showreel abspielen',
        frame: 'TAERRA-Showreel',
        note: 'Läuft über YouTube, das ab dem Start eigene Cookies setzt.'
    },

    speed: {
        kicker: 'Tempo',
        title: 'Eine ganze Welt in Minuten',
        lede: 'Alles wird auf deinem Server gerendert, in eigenen Threads. Besucher:innen laden nur fertige Bilder, deshalb ist die Karte auf dem Handy so schnell wie auf einem Gaming-PC.',
        stats: [
            {
                value: '2:46',
                label: 'für eine Welt mit 6.000 Blöcken Radius, etwa 560.000 Chunks, in allen Ansichten auf einmal. Ein anderes verbreitetes Karten-Plugin brauchte für dieselbe Welt über sechzehn Stunden.'
            },
            {
                value: '~2 ms',
                label: 'pro Tick auf dem Hauptthread für Live-Updates. Vollständige Renders nehmen ihm nichts weg.'
            },
            {
                value: '½',
                label: 'deiner CPU-Kerne rendern standardmäßig, mindestens zwei bleiben immer für das Spiel.'
            }
        ],
        footnote: 'Gemessen auf einem Intel Core Ultra 7 265 mit 64 GB RAM.'
    },

    views: {
        kicker: 'Die Karte',
        title: 'Eine Karte, die man gern ansieht',
        lede: 'Wechsle jederzeit zwischen isometrisch und Draufsicht. Die isometrische Ansicht dreht sich in vier Richtungen, damit sich nichts hinter einem Berg versteckt.',
        rows: {
            detail: {
                title: 'Jeder Block ein Würfel',
                text: 'Beleuchtet, schattiert und nach Biom gefärbt, von der ganzen Welt auf einen Blick bis zum einzelnen Block. Warme Sonne und kühler Himmel färben Schatten blauviolett statt schlammgrau, und in Wasser kann man hineinsehen: Riffe leuchten türkis, Gräben verlaufen ins Marineblau, ein versunkener Bau schimmert durch.',
                alt: 'Ein Ausschnitt der isometrischen Karte: Wald, ein Fluss, Kirschbäume und Hügel, jeder Block ein schattierter Würfel'
            },
            topDown: {
                title: 'Die klassische flache Karte, einen Klick entfernt',
                text: 'Lieber flach? Die Draufsicht ist gleich da. Schalte die Karte auf Grau, wenn die Markierungen darauf wichtiger sind als das Gelände.',
                alt: 'Dieselbe Welt senkrecht von oben, als flache Karte'
            },
            dimensions: {
                title: 'Oberwelt, Nether, Ende',
                text: 'Jede Dimension und jede eigene Welt bekommt ihre eigene Karte.',
                nether: 'Der ganze Nether als ein isometrischer Block',
                end: 'Die Inseln des Endes als ein isometrischer Block'
            }
        }
    },

    features: {
        label: 'Funktionen',
        groups: [
            {
                kicker: 'Live',
                title: 'Immer die Welt, wie sie gerade ist',
                features: [
                    {
                        title: 'Spieler:innen bewegen sich live',
                        text: 'Positionen aktualisieren sich jede Sekunde, mit einer Liste aller Spieler:innen und einem Folgemodus, der jemanden im Blick behält, auch über Welten hinweg.'
                    },
                    {
                        title: 'Farben aus der Ortungsleiste',
                        text: 'Alle Spieler:innen tragen die Farbe ihres Punkts in der Ortungsleiste, so siehst du auf einen Blick, wer wer ist, auf der Karte und im Spiel.'
                    },
                    {
                        title: 'Die Karte bleibt aktuell',
                        text: 'Jede Änderung wird erfasst, ob von Spieler:innen gebaut oder von der Natur gewachsen, und in einem Intervall deiner Wahl neu gezeichnet. Eine Stelle, die sich hundertmal ändert, wird einmal gezeichnet.'
                    },
                    {
                        title: 'Zusehen beim Rendern',
                        text: 'Ein Render zeigt seinen Fortschritt direkt auf der Karte, mit der verbleibenden Zeit, im Browser und im Spiel.'
                    }
                ]
            },
            {
                kicker: 'Für Server',
                title: 'Gemacht für deinen',
                features: [
                    {
                        title: 'Anmelden ohne Passwort',
                        text: 'Spieler:innen geben /taerra login ein und klicken auf den Link, den sie bekommen. Der Link funktioniert einmal, der Browser bleibt 30 Tage angemeldet, und Spieler:innen erfahren im Spiel, wann immer sich ein Browser als sie anmeldet.'
                    },
                    {
                        title: 'Private Welten und Ebenen',
                        text: 'Verstecke eine Welt oder Ebene vor allen außer angemeldeten Spieler:innen mit der passenden Berechtigung.'
                    },
                    {
                        title: 'Teleport von der Karte',
                        text: 'Angemeldete Spieler:innen mit Berechtigung klicken mit rechts auf eine beliebige Stelle der Karte, um dorthin zu gelangen.'
                    },
                    {
                        title: 'Wegpunkte, Flächen und Linien',
                        text: 'In benannten Gruppen und Farben, auch privaten, jeweils mit optionaler Beschreibung. Spieler:innen mit Berechtigung zeichnen, verschieben und färben sie direkt auf der Karte, Flächen aus ausgewählten Chunks oder Punkt für Punkt, Linien gerade oder weich, und die Mod zeigt Wegpunkte als Beschriftung in der Welt selbst.'
                    },
                    {
                        title: 'Eigene Wegpunkte',
                        text: 'Alle angemeldeten Spieler:innen haben bis zu 100 Wegpunkte, Flächen und Linien, die nur sie sehen, im Browser und im Spiel, gezeichnet als Edelsteine und gestrichelte Linien, damit sie nie mit denen des Servers verwechselt werden. Andere Plugins zeigen Spieler:innen so auch ihre eigenen Markierungen, etwa ihre Homes.'
                    },
                    {
                        title: 'Datenschutz eingebaut',
                        text: 'Spieler:innen im Zuschauermodus, unsichtbar oder per Vanish versteckt, bleiben von der Karte fern, und wer sich in Serverlisten verbirgt, erscheint als anonymer Punkt. Die Köpfe der Spieler:innen kommen von deinem Server, deshalb verrät das Ansehen der Karte niemanden an Mojang.'
                    },
                    {
                        title: 'Eine Jar',
                        text: 'Keine externe Datenbank, kein CDN, kein Web-Adminpanel. Alles wird im Spiel eingestellt.'
                    },
                    {
                        title: 'Jeden Ort teilen',
                        text: 'Rechtsklick, um Koordinaten zu kopieren oder einen Link, der die Karte genau dort öffnet.'
                    },
                    {
                        title: 'Alles durchsuchen',
                        text: 'Drück /, um Spieler:innen, Marker, Welten und Koordinaten in jeder Welt zu finden. Die Mod hat dieselbe Suche auf einer eigenen Taste.'
                    },
                    {
                        title: 'Verstecken, was noch nicht fertig ist',
                        text: 'Wähle Chunks aus, um sie neu zu rendern oder mit Warnstreifen abzudecken, solange das Team dort baut.'
                    },
                    {
                        title: 'Passt zu deiner Welt',
                        text: 'Begrenze die Karte auf einen Kreis, ein Quadrat oder ein Rechteck, damit sie dort endet, wo dein Spielbereich endet.'
                    },
                    {
                        title: 'Installierbar wie eine App',
                        text: 'Auf Handy und Desktop, sobald die Karte über HTTPS läuft.'
                    },
                    {
                        title: 'Deutsch und Englisch',
                        text: 'Die Web-Karte, die Mod und jede Nachricht im Spiel sprechen beides.'
                    },
                    {
                        title: 'Als Bilder exportieren',
                        text: 'Ein Befehl macht aus der Karte Bilder, bereit für eine Wiki-Seite oder ein Poster.'
                    }
                ]
            },
            {
                kicker: 'Verträgt sich mit anderen',
                title: 'Passt zu den Plugins, die du schon hast',
                features: [
                    {title: 'WorldGuard', text: 'Regionen als Ebene, die Besucher:innen einschalten können.'},
                    {title: 'Weltgrenzen', text: 'Die Vanilla-Grenze und ChunkyBorder-Grenzen als Ebene.'},
                    {
                        title: 'Chunky',
                        text: 'Hält sich zurück, während Chunky generiert, und sagt dann, wann sich ein vollständiger Render lohnt.'
                    },
                    {title: 'dynmap', text: 'Übernimm deine Markierungen mit einem Befehl als Wegpunkte.'}
                ]
            }
        ]
    },

    mod: {
        kicker: 'Fabric-Mod',
        title: 'Dieselbe Karte im Spiel',
        lede: 'Die optionale Fabric-Mod bringt die Karte deines Servers zu deinen Spieler:innen. Betritt einen Server mit TAERRA, und sie verbindet sich von selbst, schon angemeldet, ganz ohne /taerra login.',
        points: [
            'Eine ganze Weltkarte, die alles kann, was die Web-Karte kann: beide Ansichten und alle vier Drehungen, Ebenen, Spieler:innen und Folgemodus, Wegpunkte, Suche, Teleport, Chunk-Auswahl und Render-Fortschritt',
            'Eine Minikarte, die du in Größe und Zoom anpassen, links oder rechts platzieren und mit deiner Blickrichtung drehen kannst',
            'Wegpunkte in der Welt, als Beschriftung dort, wo sie sind',
            'Navigation zu einem Wegpunkt, Spieler:innen oder jedem Ort, gestartet im Spiel oder auf der Web-Karte: ein Leuchtfeuerstrahl, wo er ist, und ein Pfeil mit der Entfernung, auch durch den Nether'
        ],
        note: 'Sie rendert selbst nichts: Sie zeichnet die Bilder, die der Server schon gemacht hat. Braucht Fabric API; Mod Menu ist optional.',
        download: 'Mod holen',
        shots: [
            {
                caption: 'Die Weltkarte im Spiel',
                alt: 'Die Vollbild-Weltkarte der Mod senkrecht von oben: grüne Inseln und Küsten in einem blauen Meer'
            },
            {
                caption: 'Isometrisch, von allen vier Seiten',
                alt: 'Dieselbe Weltkarte in der isometrischen Ansicht, die ganze Welt als ein schräger Block vor dem Spiel dahinter'
            },
            {
                caption: 'Regionen und Wegpunkte',
                alt: 'Eine nahe isometrische Ansicht von Wald und Flüssen mit grün umrandeten WorldGuard-Regionen und offener Ebenenliste'
            },
            {
                caption: 'Eine Minikarte und Wegpunkte in der Welt',
                alt: 'Eine Spielfigur auf einem grasbewachsenen Hügel, Wegpunkt-Beschriftungen über der Landschaft und eine Minikarte oben rechts'
            }
        ],
        viewer: {
            label: 'Screenshot-Ansicht',
            open: 'Vergrößern',
            close: 'Schließen',
            previous: 'Vorheriger Screenshot',
            next: 'Nächster Screenshot'
        }
    },

    start: {
        kicker: 'Loslegen',
        title: 'In sechs Schritten zur Live-Karte',
        steps: [
            {
                title: 'Installieren',
                text: 'Leg die Plugin-Jar in plugins/ und starte den Server. Beim ersten Start lädt es seine Bibliotheken herunter, dafür braucht der Server einmal Internet.'
            },
            {
                title: 'Welten wählen',
                text: 'Keine ist auf der Karte, bis du sie aktivierst.',
                command: '/taerra config world minecraft:overworld enable'
            },
            {
                title: 'Rendern',
                text: 'Zeichne die ganze Welt einmal. Danach werden Änderungen von selbst gezeichnet.',
                command: '/taerra render full minecraft:overworld'
            },
            {
                title: 'Karte öffnen',
                text: 'Das Plugin liefert sie selbst aus, ganz ohne Webserver.',
                command: 'http://<dein-server>:8100'
            },
            {
                title: 'Öffentlich machen',
                text: 'Stell die Karte hinter einen Reverse Proxy mit HTTPS und sag TAERRA ihre Adresse. Login-Links und Link-Vorschauen nutzen sie.',
                command: '/taerra config web.public-url https://map.example.com'
            },
            {
                title: 'Benennen',
                text: 'Der Name deines Servers in der Kopfzeile der Karte, im Seitentitel und in Link-Vorschauen.',
                command: '/taerra config web.name "Mein Server"'
            }
        ],
        requirements: [
            {part: 'Plugin', needs: 'Paper 26.3 und Java 25. Spigot und Folia werden nicht unterstützt.'},
            {part: 'Mod', needs: 'Fabric Loader, Fabric API und Minecraft 26.3, auf einem Server mit dem Plugin'},
            {
                part: 'Updates',
                needs: 'Aktualisiere zuerst das Plugin. Ist die Mod neuer als das Plugin, bittet sie die Spieler:innen, den Server aktualisieren zu lassen. Plugin und Mod teilen sich einen Build-Zähler, ihre Nummern springen also und müssen nicht übereinstimmen.'
            }
        ],
        more: [
            'Jede Einstellung lässt sich im Spiel mit /taerra config ändern. Downloads und Changelogs findest du auf der ',
            'Modrinth-Seite',
            '.'
        ] as Marked
    },

    reference: {
        kicker: 'Referenz',
        title: 'Befehle und Berechtigungen',
        lede: 'Alles läuft über /taerra. Die Berechtigungen funktionieren mit LuckPerms oder jedem anderen Berechtigungs-Plugin, und jede private Welt und Ebene bekommt ihre eigene.',
        commands: {
            title: 'Befehle',
            head: ['Befehl', 'Was er tut'],
            rows: [
                {command: '/taerra status', text: 'Was der Renderer gerade tut, mit Details beim Darüberfahren'},
                {command: '/taerra render full <welt>', text: 'Eine ganze Welt rendern'},
                {
                    command: '/taerra render radius <welt> <radius> [x z]',
                    text: 'Einen Bereich um dich, um den Spawn oder um einen Punkt rendern'
                },
                {command: '/taerra render image <welt> [zoom]', text: 'Die Karte als Bilder exportieren'},
                {command: '/taerra render cancel', text: 'Einen laufenden Render abbrechen'},
                {
                    command: '/taerra limit <welt> circle|square|rectangle …',
                    text: 'Festlegen, wie viel einer Welt die Karte zeigt'
                },
                {
                    command: '/taerra config …',
                    text: 'Einstellungen ändern, Welten aktivieren, Welten und Ebenen privat machen'
                },
                {command: '/taerra waypoint add|remove …', text: 'Wegpunkte hinzufügen oder entfernen'},
                {command: '/taerra import dynmap <datei>', text: 'Markierungen aus dynmap importieren'},
                {command: '/taerra login', text: 'Bei der Web-Karte anmelden'},
                {command: '/taerra logout [name]', text: 'In jedem Browser von der Web-Karte abmelden'}
            ]
        },
        permissions: {
            title: 'Berechtigungen',
            head: ['Berechtigung', 'Standard', 'Erlaubt'],
            rows: [
                {permission: 'taerra.command.login', standard: 'alle', text: '`/taerra login` und `/taerra logout`'},
                {permission: 'taerra.command.status', standard: 'OP', text: '`/taerra status`'},
                {permission: 'taerra.command.render', standard: 'OP', text: '`/taerra render`'},
                {permission: 'taerra.command.limit', standard: 'OP', text: '`/taerra limit`'},
                {permission: 'taerra.command.waypoint', standard: 'OP', text: '`/taerra waypoint`'},
                {permission: 'taerra.command.config', standard: 'OP', text: '`/taerra config` und `/taerra import`'},
                {permission: 'taerra.command.logout.others', standard: 'OP', text: 'Andere Spieler:innen abmelden'},
                {
                    permission: 'taerra.web.teleport',
                    standard: 'OP',
                    text: 'Von der Karte teleportieren, wenn angemeldet'
                },
                {
                    permission: 'taerra.web.see-hidden',
                    standard: 'OP',
                    text: 'Versteckte Spieler:innen auf der Karte sehen, wenn angemeldet'
                },
                {
                    permission: 'taerra.web.render',
                    standard: 'OP',
                    text: 'Ausgewählte Chunks neu rendern, wenn angemeldet'
                },
                {
                    permission: 'taerra.web.hide',
                    standard: 'OP',
                    text: 'Ausgewählte Chunks verstecken und wieder zeigen, wenn angemeldet'
                },
                {
                    permission: 'taerra.web.markers',
                    standard: 'OP',
                    text: 'Marker hinzufügen, bearbeiten, verschieben und entfernen, wenn angemeldet'
                },
                {
                    permission: 'taerra.web.markers.private',
                    standard: 'OP',
                    text: 'Private Marker-Gruppen anlegen, wenn angemeldet'
                },
                {
                    permission: 'taerra.web.markers.personal',
                    standard: 'alle',
                    text: 'Wegpunkte anlegen, die nur du siehst, wenn angemeldet'
                },
                {
                    permission: 'taerra.web.world.<welt>',
                    standard: '—',
                    text: 'Eine private Welt sehen, etwa `taerra.web.world.minecraft.the_end`'
                },
                {
                    permission: 'taerra.web.layer.<ebene>',
                    standard: '—',
                    text: 'Eine private Ebene sehen, etwa `taerra.web.layer.worldguard`'
                },
                {permission: 'taerra.hide', standard: 'niemand', text: 'Von der Karte versteckt werden'}
            ]
        }
    },

    closing: {
        kicker: 'Early Access',
        title: 'Ausprobieren, kaputt machen, melden',
        text: 'TAERRA läuft schon auf echten Servern und wächst noch: Zwischen Versionen kann sich etwas ändern, und hier und da gibt es Ecken und Kanten. Wenn etwas kaputtgeht oder falsch aussieht, macht jede Meldung auf Discord es für alle besser.',
        modrinth: 'Auf Modrinth holen',
        discord: 'Zum Discord',
        demo: 'Live ansehen',
        license: 'Kostenlos auf jedem Server, auch auf kommerziellen. Vorerst Closed Source.',
        deepDive: [
            'Neugierig, wie es unter der Haube funktioniert? Lies den ',
            'technischen Deep Dive',
            ' (Englisch).'
        ] as Marked
    },

    footer: {
        elsewhere: 'TAERRA anderswo',
        nav: {
            modrinth: 'Download auf Modrinth',
            demo: 'Live-Karte',
            discord: 'Discord',
            deepDive: 'Technischer Deep Dive (Englisch)'
        },
        build: 'Build',
        disclaimer:
            'Kein offizielles Minecraft-Produkt. Nicht von Mojang oder Microsoft genehmigt oder mit ihnen verbunden.'
    },

    notFound: {
        title: 'Seite nicht gefunden – TAERRA',
        heading: 'Seite nicht gefunden',
        text: 'Dieser Chunk wurde nie gerendert: Die Seite, die du suchst, gibt es nicht.',
        back: 'Zurück zu TAERRA'
    }
};
