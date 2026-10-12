import type {APIRoute, GetStaticPaths} from 'astro';
import {readFile} from 'node:fs/promises';
import {folder, mirror} from '../../data/downloads';

export const getStaticPaths = (() => mirror.builds.map(({file}) => ({params: {file}}))) satisfies GetStaticPaths;

export const GET: APIRoute = async ({params}) =>
    new Response(new Uint8Array(await readFile(`${folder}/${params.file}`)), {
        headers: {'Content-Type': 'application/java-archive'}
    });
