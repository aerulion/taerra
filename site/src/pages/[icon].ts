import type {APIRoute, GetStaticPaths} from 'astro';
import ico from '../../../plugin/src/main/resources/web/favicon.ico?inline';
import svg from '../../../plugin/src/main/resources/web/images/favicon.svg?inline';
import touch from '../../../plugin/src/main/resources/web/images/apple-touch-icon.png?inline';

const icons: Record<string, string> = {'favicon.ico': ico, 'favicon.svg': svg, 'apple-touch-icon.png': touch};

export const getStaticPaths = (() => Object.keys(icons).map((icon) => ({params: {icon}}))) satisfies GetStaticPaths;

export const GET: APIRoute = ({params}) => fetch(icons[params.icon!]);
