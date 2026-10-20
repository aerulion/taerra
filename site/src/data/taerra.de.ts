import type {Copy, Marked} from './taerra';

export const de: Copy = {
    meta: {
        title: 'TAERRA: eine Live-Karte für Minecraft-Server',
        description:
            'Eine Live-Karte für Minecraft-Server: Block für Block, mit Sonnenlicht und Schatten, isometrisch oder von oben, gerendert auf deinem Paper-Server. Dazu eine Fabric-Mod.',
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
        lede: 'TAERRA macht aus deinen Minecraft-Welten eine Live-Karte, die alle öffnen können: Block für Block, mit Sonnenlicht und Schatten, von oben oder schräg von allen vier Seiten. Im Spiel gibt es dieselbe Karte, mit allem, was der Browser kann, und dazu eine Minikarte.',
        download: 'TAERRA holen',
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
        lede: 'Alles wird auf deinem Server gerendert, in eigenen Threads. Geladen werden nur fertige Bilder, deshalb ist die Karte auf dem Handy so schnell wie auf einem Gaming-PC.',
        stats: [
            {
                value: '2:21',
                unit: 'min',
                label: 'für eine Welt mit 6.000 Blöcken Radius, etwa 440.000 Chunks, in allen Ansichten auf einmal.'
            },
            {
                value: '~2',
                unit: 'ms',
                label: 'pro Tick auf dem Hauptthread für Live-Updates. Vollständiges Rendern nimmt ihm nichts weg.'
            },
            {
                value: '50',
                unit: '%',
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
                text: 'Lieber flach? Die Draufsicht ist gleich da. Schalte die Karte auf Grau, wenn die Marker darauf wichtiger sind als das Gelände.',
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
                        title: 'Wer wo ist, live',
                        text: 'Positionen aktualisieren sich jede Sekunde, mit einer Spielerliste und einem Folgemodus, der jemanden im Blick behält, auch über Welten hinweg.'
                    },
                    {
                        title: 'Farben aus der Ortungsleiste',
                        text: 'Alle tragen die Farbe ihres Punkts in der Ortungsleiste, so siehst du auf einen Blick, wer wer ist, auf der Karte und im Spiel.'
                    },
                    {
                        title: 'Die Karte bleibt aktuell',
                        text: 'Jede Änderung wird erfasst, ob gebaut oder von der Natur gewachsen, und in einem Intervall deiner Wahl neu gezeichnet. Eine Stelle, die sich hundertmal ändert, wird einmal gezeichnet.'
                    },
                    {
                        title: 'Zusehen beim Rendern',
                        text: 'Beim Rendern siehst du den Fortschritt direkt auf der Karte, mit der verbleibenden Zeit, im Browser und im Spiel. Dein Team rendert eine ganze Welt oder bricht das Rendern ab, mit einem Rechtsklick.'
                    },
                    {
                        title: 'Entdecke die Welt',
                        text: 'Entdeckbare Regionen bleiben im Nebel, bis du sie betrittst. Dann lichtet er sich nur für dich, mit einem Klang und einem Funkenregen auf der Webkarte, einer Benachrichtigung in der Mod oder einer Zeile im Chat.'
                    },
                    {
                        title: 'Regionen auf der Karte',
                        text: 'Jede Region zeigt sich als Umriss in ihrer eigenen Farbe und bekommt Namen und Füllung, sobald du sie gefunden hast. So siehst du immer, was noch zu erkunden ist.'
                    }
                ]
            },
            {
                kicker: 'Für Server',
                title: 'Gemacht für deinen Server',
                features: [
                    {
                        title: 'Anmelden ohne Passwort',
                        text: 'Gib den kurzen Code, den die Karte zeigt, nach /taerra login ein, und der Browser meldet sich von selbst für 30 Tage an und reicht die Anmeldung per QR-Code an dein Handy weiter. Codes gelten einmal, und jede Anmeldung wird im Spiel gemeldet.'
                    },
                    {
                        title: 'Wegpunkte, Flächen und Linien',
                        text: 'In benannten Gruppen und Farben, auch privaten, jeweils mit optionaler Beschreibung. Wer die Berechtigung hat, zeichnet, verschiebt und färbt sie auf der Karte: Flächen Chunk für Chunk oder Punkt für Punkt, Linien gerade oder geglättet.'
                    },
                    {
                        title: 'Eigene Wegpunkte',
                        text: 'Angemeldet hast du bis zu 100 Wegpunkte, Flächen und Linien, die nur du siehst, im Browser und im Spiel, und ein Wegpunkt „Letzter Tod“ markiert, wo du gestorben bist. Andere Plugins können dir so auch eigene Marker zeigen, etwa deine Homes.'
                    },
                    {
                        title: 'Datenschutz eingebaut',
                        text: 'Wer im Zuschauermodus, unsichtbar oder per Vanish versteckt ist, bleibt von der Karte fern, und wer sich in Serverlisten verbirgt, erscheint als anonymer Punkt. Die Köpfe kommen von deinem Server, deshalb verrät die Karte niemanden an Mojang.'
                    },
                    {
                        title: 'Private Welten und Ebenen',
                        text: 'Zeige eine Welt oder Ebene nur denen, die angemeldet sind und die passende Berechtigung haben, oder verbirg nur, wo die Spieler:innen einer Welt sind, und lege fest, wer von der Karte aus teleportieren, Marker zeichnen oder rendern darf. Dein Team sieht außerdem die geladenen Chunks, eingefärbt nach Ladestufe.'
                    },
                    {
                        title: 'Rechtsklick auf jede Stelle',
                        text: 'Kopiere Koordinaten oder einen Link, der die Karte genau dort öffnet. Bist du angemeldet und hast die Berechtigung, teleportierst du dich mit einem Klick dorthin.'
                    },
                    {
                        title: 'Eine Jar, live eingerichtet',
                        text: 'Keine externe Datenbank, kein CDN, kein Webserver einzurichten. Dein Team ändert Welten, ihre Reihenfolge, Namen und Grenzen, private Ebenen, den Servernamen und den Renderer auf einer Einstellungsseite im Browser oder im Spiel, gemeinsam und ohne Neustart, oder mit /taerra config.'
                    },
                    {
                        title: 'Alles durchsuchen',
                        text: 'Drück [/], um Spieler:innen, Marker, Welten und Koordinaten in jeder Welt zu finden. Die Mod hat dieselbe Suche auf einer eigenen Taste.'
                    },
                    {
                        title: 'Nur zeigen, was fertig ist',
                        text: 'Begrenze die Karte auf einen Kreis, ein Quadrat oder ein Rechteck, damit sie endet, wo dein Spielbereich endet, und zeichne benannte Regionen Chunk für Chunk. Decke eine mit Nebel ab, solange das Team dort baut, oder mit einem dünnen Schleier, der nur erahnen lässt, was darunter liegt, und lass alle den Nebel für sich lichten, sobald sie die Region betreten.'
                    },
                    {
                        title: 'Installierbar wie eine App',
                        text: 'Auf Handy und Desktop, sobald die Karte über HTTPS läuft.'
                    },
                    {
                        title: 'Deutsch und Englisch',
                        text: 'Die Webkarte, die Mod und jede Nachricht im Spiel sprechen beides.'
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
                    {
                        title: 'WorldGuard',
                        text: 'Regionen als Ebene, nur für dein Team, solange du sie nicht freigibst.'
                    },
                    {
                        title: 'Weltbarrieren',
                        text: 'Die Weltbarriere aus Vanilla und die Grenzen von ChunkyBorder als Ebene.'
                    },
                    {
                        title: 'Chunky',
                        text: 'Hält sich zurück, während Chunky generiert, und sagt dann, wann sich das Rendern der ganzen Welt lohnt.'
                    },
                    {title: 'dynmap', text: 'Übernimm deine Marker mit einem Befehl als Wegpunkte.'}
                ]
            }
        ]
    },

    mod: {
        kicker: 'Fabric-Mod',
        title: 'Dieselbe Karte im Spiel',
        lede: 'Die optionale Fabric-Mod bringt die Karte deines Servers ins Spiel. Tritt einem Server mit TAERRA bei, und sie verbindet sich von selbst, schon angemeldet, ganz ohne /taerra login.',
        note: 'Braucht Fabric API und einen Server mit dem Plugin. Mod Menu ist optional.',
        download: 'Mod holen',
        rows: {
            map: {
                title: 'Die ganze Karte auf M',
                text: 'Drück [M] für die Weltkarte, mit allem, was die Webkarte kann: beide Ansichten und alle vier Drehungen, farbig oder grau, Ebenen, die Spielerliste und der Folgemodus. [K] durchsucht Spieler:innen, Marker, Welten und Koordinaten. Ein Rechtsklick auf jede Stelle startet die Navigation dorthin, setzt einen Wegpunkt oder kopiert ihre Koordinaten oder einen Link.',
                alt: 'Die Weltkarte der Mod in der isometrischen Ansicht, die ganze Welt als ein schräger Block vor dem Spiel, mit Wegpunkten am Spawn und an einem Dorf'
            },
            minimap: {
                title: 'Eine Minikarte nach deinem Geschmack',
                text: 'Stell ihre Größe ein, zoome mit [=] und [-], setz sie auf eine Seite deiner Wahl und lass sie sich mit dir drehen. Darunter wählst du die Zeilen, die du brauchst: Koordinaten und ihre Nether-Gegenstücke, Chunk, Blickrichtung, Biom, Wetter, Uhrzeit und Tag, Blocklicht, Echtzeit und FPS. Marker jenseits ihres Rands bleiben im Blick, damit nichts in der Nähe verloren geht.',
                alt: 'Ein Kirschhain im Spiel, oben rechts die Minikarte, darunter Koordinaten und Biom'
            },
            labels: {
                title: 'Wegpunkte, wo sie sind',
                text: 'Wegpunkte schweben als Beschriftung über den Orten, die sie markieren, mit der Entfernung zu jedem, damit du ohne Karte hinfindest. [B] blendet sie aus, wenn du freie Sicht willst.',
                alt: 'Eine Ebene von einem Hügel aus, mit den Beschriftungen Village und Lumber Camp samt Entfernung über den Orten, die sie markieren, und der Minikarte in der Ecke'
            },
            navigation: {
                title: 'Finde den Weg dorthin',
                text: 'Navigiere zu einem Wegpunkt, zu jemandem aus der Spielerliste oder zu jeder Stelle, gestartet auf der Karte im Spiel oder auf der Webkarte, während du spielst. Ein Leuchtfeuerstrahl steigt am Ziel auf, und ein Pfeil oben am Bildschirm zeigt Richtung und Entfernung, durch ein Portal, wenn das Ziel in einer anderen Dimension liegt.',
                alt: 'Navigation im Spiel: Am Ziel steigt ein Leuchtfeuerstrahl auf, oben am Bildschirm zeigt der Pfeil seine Koordinaten und die Entfernung'
            },
            list: {
                title: 'Deine Wegpunkte, eine Taste entfernt',
                text: '[H] setzt einen dort, wo du stehst, und auf der Karte zeichnest du auch Flächen und Linien. [J] öffnet deine Liste: filtere sie, sortiere nach Namen oder Entfernung, blende einzelne aus und exportiere sie als Datei zum Teilen oder Sichern, oder importiere eine. Wenn du stirbst, markiert ein Wegpunkt „Letzter Tod“ die Stelle.',
                alt: 'Die Liste „Meine Wegpunkte“ mit drei Wegpunkten, jeder mit Welt, Koordinaten und Entfernung und Knöpfen zum Anzeigen, Verschieben, Hinnavigieren, Zentrieren, Bearbeiten und Löschen'
            },
            layers: {
                title: 'Ebenen und Werkzeuge fürs Team',
                text: 'WorldGuard-Regionen und Weltbarrieren sind Ebenen, die du einschaltest. Mit Berechtigung wählst du Chunks aus, um sie neu zu rendern, zeichnest Regionen und legst Nebel darüber, und siehst den Fortschritt beim Rendern direkt auf der Karte. Dein Team ändert die Einstellungen des Servers auf einem eigenen Bildschirm.',
                alt: 'Eine nahe isometrische Ansicht einer Küste mit grün umrandeten WorldGuard-Regionen, einem verborgenen Bereich mit Warnstreifen und 112 Chunks, die zum Neurendern ausgewählt sind'
            }
        },
        more: {
            title: 'Außerdem',
            items: [
                {title: 'Teleport von der Karte', text: 'Rechtsklick, und du bist dort, wo der Server es dir erlaubt.'},
                {
                    title: 'Deine Geräte',
                    text: 'Sieh jeden Browser, der als du angemeldet ist, und melde ihn in den Einstellungen ab.'
                },
                {
                    title: 'Jede Taste gehört dir',
                    text: 'Ändere jede Taste in der Steuerung von Minecraft. Die Einstellungen öffnest du auf der Weltkarte oder in Mod Menu.'
                },
                {
                    title: 'Immer im Takt',
                    text: 'Passen Mod und Plugin des Servers nicht zusammen, sagt sie dir, was aktualisiert werden muss.'
                },
                {
                    title: 'Leicht für dein Spiel',
                    text: 'Sie rendert selbst nichts: Sie zeichnet die Bilder, die der Server schon gemacht hat.'
                },
                {title: 'Deutsch und Englisch', text: 'Jeder Bildschirm und jede Meldung spricht beides.'}
            ]
        },
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
                text: 'Stell die Karte hinter einen Reverse Proxy mit HTTPS und sag TAERRA ihre Adresse. Anmeldelinks und Linkvorschauen nutzen sie.',
                command: '/taerra config web.public-url https://map.example.com'
            },
            {
                title: 'Benennen',
                text: 'Der Name deines Servers in der Kopfzeile der Karte, im Seitentitel und in Linkvorschauen.',
                command: '/taerra config web.name "Mein Server"'
            }
        ],
        requirements: {
            plugin: {part: 'Plugin', needs: 'Paper 26.3 und Java 25. Spigot und Folia werden nicht unterstützt.'},
            mod: {part: 'Mod', needs: 'Fabric Loader, Fabric API und Minecraft 26.3, auf einem Server mit dem Plugin'},
            updates: {
                part: 'Updates',
                needs: 'Aktualisiere zuerst das Plugin. Ist die Mod neuer als das Plugin, bittet sie darum, den Server aktualisieren zu lassen. Plugin und Mod teilen sich einen Build-Zähler, ihre Nummern springen also und müssen nicht übereinstimmen.'
            }
        },
        more: [
            'Jede Einstellung lässt sich im Spiel mit /taerra config ändern, oder auf der Einstellungsseite der Karte. Alle Builds und was sich in ihnen geändert hat, findest du auf der ',
            'Download-Seite',
            '.'
        ] as Marked
    },

    reference: {
        kicker: 'Referenz',
        title: 'Befehle und Berechtigungen',
        lede: 'Alles läuft über /taerra. Die Berechtigungen funktionieren mit LuckPerms oder jedem anderen Berechtigungs-Plugin, und jede private Welt, Ebene und Welt mit privaten Positionen bekommt ihre eigene. Mit LuckPerms kommen Änderungen sofort auf der Karte an, auch bei Spielern, die gerade offline sind.',
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
                {command: '/taerra render cancel', text: 'Laufendes Rendern abbrechen'},
                {
                    command: '/taerra render flush',
                    text: 'Alle wartenden Kacheln verwerfen, auch noch nicht gerenderte Änderungen'
                },
                {
                    command: '/taerra limit <welt> circle|square|rectangle …',
                    text: 'Festlegen, wie viel einer Welt die Karte zeigt'
                },
                {
                    command: '/taerra config …',
                    text: 'Einstellungen ändern, Welten aktivieren, benennen, ordnen und verbergen, Ebenen privat machen'
                },
                {command: '/taerra waypoint add|remove …', text: 'Wegpunkte hinzufügen oder entfernen'},
                {command: '/taerra import dynmap <datei>', text: 'Marker aus dynmap importieren'},
                {command: '/taerra login [code]', text: 'Bei der Webkarte anmelden'},
                {command: '/taerra logout [name]', text: 'In jedem Browser von der Webkarte abmelden'},
                {command: '/taerra devices', text: 'Deine Geräte auf der Webkarte anzeigen und abmelden'},
                {command: '/taerra mod', text: 'Anzeigen, wer mit der TAERRA-Mod spielt, und mit welchem Build'}
            ]
        },
        permissions: {
            title: 'Berechtigungen',
            head: ['Berechtigung', 'Standard', 'Erlaubt'],
            rows: [
                {
                    permission: 'taerra.login',
                    standard: 'alle',
                    text: '`/taerra login`, `/taerra logout` und `/taerra devices`, und von der Mod angemeldet werden'
                },
                {
                    permission: 'taerra.logout.others',
                    standard: 'OP',
                    text: 'Andere mit `/taerra logout <spieler>` abmelden'
                },
                {permission: 'taerra.status', standard: 'OP', text: '`/taerra status`'},
                {permission: 'taerra.mods', standard: 'OP', text: '`/taerra mod`'},
                {
                    permission: 'taerra.teleport',
                    standard: 'OP',
                    text: 'Von der Karte und aus der Wegpunktliste teleportieren'
                },
                {
                    permission: 'taerra.render.world',
                    standard: 'OP',
                    text: 'Eine ganze Welt neu rendern, mit `/taerra render full` oder auf der Karte'
                },
                {
                    permission: 'taerra.render.area',
                    standard: 'OP',
                    text: 'Einen Bereich neu rendern, mit `/taerra render radius` oder ausgewählten Chunks auf der Karte'
                },
                {
                    permission: 'taerra.render.cancel',
                    standard: 'OP',
                    text: 'Das laufende Rendern abbrechen, mit `/taerra render cancel` oder auf der Karte'
                },
                {permission: 'taerra.render.flush', standard: 'OP', text: '`/taerra render flush`'},
                {permission: 'taerra.render.image', standard: 'OP', text: '`/taerra render image`'},
                {permission: 'taerra.limit', standard: 'OP', text: '`/taerra limit`'},
                {
                    permission: 'taerra.config',
                    standard: 'OP',
                    text: '`/taerra config` und die Einstellungen auf der Karte und in der Mod'
                },
                {permission: 'taerra.import', standard: 'OP', text: '`/taerra import`'},
                {
                    permission: 'taerra.waypoint.edit',
                    standard: 'OP',
                    text: 'Die Wegpunkte des Servers hinzufügen, bearbeiten, verschieben und entfernen, mit `/taerra waypoint` oder auf der Karte'
                },
                {permission: 'taerra.waypoint.private-groups', standard: 'OP', text: 'Private Wegpunktgruppen anlegen'},
                {
                    permission: 'taerra.waypoint.personal',
                    standard: 'alle',
                    text: 'Wegpunkte anlegen, die nur du siehst'
                },
                {
                    permission: 'taerra.waypoint.death',
                    standard: 'alle',
                    text: 'Einen eigenen Wegpunkt da bekommen, wo du zuletzt gestorben bist'
                },
                {
                    permission: 'taerra.region.edit',
                    standard: 'OP',
                    text: 'Regionen zeichnen, benennen, vernebeln und entfernen'
                },
                {
                    permission: 'taerra.region.reset',
                    standard: 'OP',
                    text: 'Zurücksetzen, wer eine Region entdeckt hat, zusammen mit `taerra.region.edit`'
                },
                {
                    permission: 'taerra.see.hidden-players',
                    standard: 'OP',
                    text: 'Versteckte und anonyme Personen auf der Karte sehen'
                },
                {
                    permission: 'taerra.see.fog',
                    standard: 'OP',
                    text: 'Durch den Nebel jeder Region sehen, samt den Personen darin'
                },
                {
                    permission: 'taerra.world.<welt>',
                    standard: 'OP',
                    text: 'Eine private Welt sehen, etwa `taerra.world.minecraft.the_end`'
                },
                {
                    permission: 'taerra.players.<welt>',
                    standard: 'OP',
                    text: 'Sehen, wo Spieler:innen in einer Welt mit privaten Positionen sind, etwa `taerra.players.minecraft.overworld`'
                },
                {
                    permission: 'taerra.layer.<ebene>',
                    standard: 'OP',
                    text: 'Eine private Ebene sehen, etwa `taerra.layer.worldguard`'
                },
                {permission: 'taerra.hide', standard: 'niemand', text: 'Nicht auf der Karte erscheinen'}
            ]
        }
    },

    closing: {
        kicker: 'Early Access',
        title: 'Ausprobieren, kaputt machen, melden',
        text: [
            'TAERRA läuft schon auf echten Servern und wächst noch: Zwischen Versionen kann sich etwas ändern, und hier und da gibt es Ecken und Kanten. Wenn etwas kaputtgeht oder falsch aussieht, ',
            'melde es auf GitHub',
            '. Jede Meldung macht es für alle besser, und Fragen sind auf Discord willkommen.'
        ] as Marked,
        download: 'TAERRA holen',
        discord: 'Zum Discord',
        demo: 'Live ansehen',
        license: 'Kostenlos auf jedem Server, auch auf kommerziellen. Quellcode einsehbar auf GitHub.',
        deepDive: [
            'Neugierig, wie es unter der Haube funktioniert? Lies den ',
            'technischen Deep Dive',
            ' (Englisch).'
        ] as Marked
    },

    downloads: {
        meta: {
            title: 'TAERRA herunterladen: jeder Build von Plugin und Mod',
            description:
                'Lade das TAERRA-Plugin für Paper und die Fabric-Mod herunter: den neuesten Build und jeden davor, jeweils mit allem, was sich geändert hat.',
            image: {
                src: '/og-download.png',
                alt: 'Das TAERRA-Logo mit Download-Schaltflächen für das Paper-Plugin und die Fabric-Mod neben einer isometrischen Minecraft-Welt'
            }
        },
        kicker: 'Downloads',
        title: 'TAERRA holen',
        lede: 'Du betreibst einen Server? Dann brauchst du das Plugin. Du spielst auf einem Server mit TAERRA? Hol dir die Mod: Sie ist optional und bringt dieselbe Karte in dein Spiel.',
        parts: {
            plugin: {
                label: 'Plugin',
                loader: 'Paper',
                audience: 'Für den Server',
                title: 'Paper-Plugin',
                text: 'Rendert die Karte auf deinem Server und liefert sie an jeden Browser aus. Leg die Jar in plugins/.',
                download: 'Plugin herunterladen'
            },
            mod: {
                label: 'Mod',
                loader: 'Fabric',
                audience: 'Fürs Spiel',
                title: 'Fabric-Mod',
                text: 'Die Karte des Servers in deinem Spiel, mit Minikarte und Wegpunkten in der Welt. Leg die Jar in mods/.',
                download: 'Mod herunterladen'
            }
        },
        latest: 'Neuester Build',
        build: 'Build',
        download: 'Herunterladen',
        none: 'Noch kein Build veröffentlicht.',
        minecraft: 'Minecraft',
        changes: 'Änderungen',
        modrinth: 'Auf Modrinth',
        channels: {release: 'Release', beta: 'Beta', alpha: 'Alpha'},
        archive: {
            kicker: 'Archiv',
            title: 'Jeder Build',
            lede: 'Ältere Builds bleiben hier, falls du zurück musst. Plugin und Mod teilen sich einen Build-Zähler, deshalb springen ihre Nummern.',
            filter: 'Builds zeigen',
            all: 'Alle',
            empty: 'Sobald ein Build veröffentlicht ist, steht er hier.'
        },
        credit: [
            'Das Paper-Logo gehört PaperMC. Das Fabric-Logo stammt von FabricMC, unter ',
            'CC BY-NC-SA 4.0',
            '.'
        ] as Marked,
        hash: 'SHA-512',
        copy: 'SHA-512-Prüfsumme kopieren',
        copied: 'Kopiert',
        sections: {
            Features: 'Neu',
            Fixes: 'Behoben',
            Performance: 'Schneller',
            Contributors: 'Mitwirkende'
        } as Record<string, string>,
        phrases: {
            'Internal improvements only.': 'Nur Verbesserungen unter der Haube.',
            'The first release on Modrinth.': 'Der erste veröffentlichte Build.'
        } as Record<string, string>
    },

    footer: {
        elsewhere: 'TAERRA anderswo',
        nav: {
            download: 'Downloads',
            demo: 'Live-Demo',
            discord: 'Discord',
            source: 'Quellcode',
            contact: 'Kontakt',
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
