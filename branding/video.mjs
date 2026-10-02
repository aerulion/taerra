// Renders video.html frame by frame with headless Chrome and encodes it with ffmpeg. Every frame is drawn by the page's
// seek(t), so the result is the same however slow the machine is.
//
//   node branding/video.mjs [--fps 60] [--scale 1] [--from 0] [--to <end>] [--music <file> [--music-at m:ss]] [--tiles <dir>]
//                           [--web <url>] [--out <file>]

import {execFileSync, spawn} from 'node:child_process';
import {createServer} from 'node:http';
import {mkdirSync, mkdtempSync, readFile, renameSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {dirname, extname, join, normalize, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {parseArgs} from 'node:util';

const HERE = dirname(fileURLToPath(import.meta.url));
const {values: args} = parseArgs({
    options: {
        fps: {type: 'string', default: '60'},
        // Device pixels per page pixel: 2 renders 3840 × 2160.
        scale: {type: 'string', default: '1'},
        from: {type: 'string', default: '0'},
        to: {type: 'string'},
        tiles: {type: 'string', default: join(HERE, '../run/plugins/Taerra')},
        web: {type: 'string', default: 'http://localhost:8100'},
        out: {type: 'string', default: join(HERE, 'images/video/showreel.mp4')},
        // Only these times, comma separated, as PNGs next to --out: for checking a frame without encoding.
        still: {type: 'string'},
        // A music track, and where in it the video starts, in seconds or as m:ss.
        music: {type: 'string'},
        'music-at': {type: 'string', default: '0'},
        // Only the music, laid anew onto the video already at --out: for trying tracks without rendering a frame.
        sound: {type: 'boolean'},
        chrome: {type: 'string', default: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'},
    },
});
const [WIDTH, HEIGHT, PORT, FILES] = [1920, 1080, 9333, 9334];
mkdirSync(dirname(resolve(args.out)), {recursive: true});

if (args.sound) {
    const duration = +execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', args.out]);
    const mixed = `${args.out}.mixing.mp4`;
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', args.out, ...musicArgs(0, duration), '-c:v', 'copy', mixed]);
    renameSync(mixed, args.out);
    console.log(`${args.out}  music laid anew`);
    process.exit();
}

// The page is served over http, not opened as a file: only then may it script the web map's frame. /tiles is the
// plugin folder, everything else the repository.
const TYPES = {'.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png'};
const server = createServer((request, response) => {
    const path = normalize(decodeURIComponent(new URL(request.url, 'http://x').pathname));
    const file = path.startsWith('/tiles/') ? join(resolve(args.tiles), path.slice(7)) : join(HERE, '..', path);
    readFile(file, (error, data) => {
        response.writeHead(error ? 404 : 200, {'Content-Type': TYPES[extname(file)] ?? 'application/octet-stream'});
        response.end(data);
    });
}).listen(FILES, '127.0.0.1');

const profile = mkdtempSync(join(tmpdir(), 'taerra-video-'));
const chrome = spawn(args.chrome, [
    '--headless',
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${profile}`,
    // The web map runs in a frame from another origin, which the page scripts frame by frame.
    '--disable-web-security',
    '--disable-site-isolation-trials',
    '--hide-scrollbars',
    '--force-device-scale-factor=1',
    '--autoplay-policy=no-user-gesture-required',
    `--window-size=${WIDTH},${HEIGHT}`,
    'about:blank',
], {stdio: 'ignore'});

let page;
try {
    page = await connect();
    await page.send('Emulation.setDeviceMetricsOverride', {width: WIDTH, height: HEIGHT, deviceScaleFactor: +args.scale, mobile: false});
    const query = new URLSearchParams({tiles: '/tiles', web: args.web, scale: args.scale});
    await page.send('Page.navigate', {url: `http://127.0.0.1:${FILES}/branding/video.html?${query}`});
    while (await evaluate('typeof ready') === 'undefined') {
        await new Promise(wait => setTimeout(wait, 100));
    }
    const duration = await evaluate('ready');
    if (args.still) {
        for (const t of args.still.split(',')) {
            await evaluate(`seek(${t})`);
            const {data} = await page.send('Page.captureScreenshot', {format: 'png'});
            writeFileSync(join(dirname(args.out), `still-${t}.png`), Buffer.from(data, 'base64'));
        }
    } else {
        await render(duration);
    }
} finally {
    page?.close();
    chrome.kill();
    server.close();
    // Chrome still writes to its profile while it exits.
    setTimeout(() => rmSync(profile, {recursive: true, force: true}), 1000);
}

async function render(duration) {
    const fps = +args.fps, from = +args.from, to = Math.min(+(args.to ?? duration), duration);
    const frames = Math.round((to - from) * fps);

    const ffmpeg = spawn('ffmpeg', [
        '-y', '-loglevel', 'error',
        '-f', 'image2pipe', '-framerate', `${fps}`, '-i', '-',
        ...musicArgs(from, to),
        '-c:v', 'libx264', '-preset', 'slow', '-crf', '14', '-pix_fmt', 'yuv420p', '-t', `${to - from}`,
        args.out,
    ], {stdio: ['pipe', 'inherit', 'inherit']});
    const encoded = new Promise((done, fail) => ffmpeg.on('close', code => code ? fail(new Error(`ffmpeg ${code}`)) : done()));

    const started = Date.now();
    for (let i = 0; i < frames; i++) {
        await evaluate(`seek(${from + i / fps})`);
        const {data} = await page.send('Page.captureScreenshot', {format: 'jpeg', quality: 98});
        if (!ffmpeg.stdin.write(Buffer.from(data, 'base64'))) {
            await new Promise(drained => ffmpeg.stdin.once('drain', drained));
        }
        if (i % fps === 0) {
            process.stdout.write(`\r${(from + i / fps).toFixed(0)}s / ${to}s  ${((Date.now() - started) / 1000).toFixed(0)}s elapsed`);
        }
    }
    ffmpeg.stdin.end();
    await encoded;
    console.log(`\n${args.out}  ${frames} frames`);
}

// The music, if any, after the video input: the part under from..to, faded out over the last half second.
function musicArgs(from, to) {
    if (!args.music) {
        return ['-map', '0:v', '-movflags', '+faststart'];
    }
    const at = args['music-at'].split(':').reduce((sum, part) => sum * 60 + +part, 0);
    // Padded with silence, so a video that runs on past the track keeps its sound to the end.
    return ['-ss', `${at + from}`, '-i', resolve(args.music), '-af', `afade=t=out:st=${Math.max(0, to - from - .5)}:d=0.5,apad`,
        '-map', '0:v', '-map', '1:a', '-c:a', 'aac', '-b:a', '256k', '-t', `${to - from}`, '-movflags', '+faststart'];
}

async function evaluate(expression) {
    const {result, exceptionDetails} = await page.send('Runtime.evaluate', {expression, awaitPromise: true, returnByValue: true});
    if (exceptionDetails) {
        throw new Error(exceptionDetails.exception?.description ?? exceptionDetails.text);
    }
    return result.value;
}

async function connect() {
    let targets;
    for (let attempt = 0; !targets; attempt++) {
        try {
            targets = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
        } catch (error) {
            if (attempt > 100) {
                throw error;
            }
            await new Promise(wait => setTimeout(wait, 100));
        }
    }
    const socket = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl);
    await new Promise((open, fail) => {
        socket.onopen = open;
        socket.onerror = fail;
    });
    const pending = new Map();
    let id = 0;
    socket.onmessage = ({data}) => {
        const message = JSON.parse(data);
        const call = pending.get(message.id);
        if (call) {
            pending.delete(message.id);
            message.error ? call.fail(new Error(message.error.message)) : call.done(message.result);
        }
    };
    return {
        send: (method, params = {}) => new Promise((done, fail) => {
            pending.set(++id, {done, fail});
            socket.send(JSON.stringify({id, method, params}));
        }),
        close: () => socket.close(),
    };
}
