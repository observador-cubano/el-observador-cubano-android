#!/usr/bin/env node
/**
 * Administración del feed de «Desde nuestro canal».
 *
 * Uso:
 *   node mobile/tools/channel-admin.mjs list
 *   node mobile/tools/channel-admin.mjs add
 *   node mobile/tools/channel-admin.mjs add --text "..." --link "https://..."
 *   node mobile/tools/channel-admin.mjs edit <id>
 *   node mobile/tools/channel-admin.mjs remove <id>
 *   node mobile/tools/channel-admin.mjs build
 *   node mobile/tools/channel-admin.mjs review
 *   node mobile/tools/channel-admin.mjs link <postId>
 *   node mobile/tools/channel-admin.mjs separate <postId>
 *   node mobile/tools/channel-admin.mjs validate
 *   node mobile/tools/channel-admin.mjs publish --message "..."
 *
 * No usa tokens de GitHub dentro de la aplicación. Publicar requiere un clon
 * local con permisos de escritura en el repositorio.
 */

import { createInterface } from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';
import {
  mkdir,
  readFile,
  readdir,
  rm,
  writeFile,
} from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  CHANNEL_NAME,
  CHANNEL_URL,
  DEFAULT_PAGE_SIZE,
  bloggerArticlesFromFeed,
  buildFeed,
  isHttpsUrl,
  normalizeUrl,
  validatePostsDocument,
} from './lib/channel-feed.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..');
const DATA_DIR = path.join(ROOT, 'data', 'channel');
const POSTS_FILE = path.join(DATA_DIR, 'posts.json');
const DECISIONS_FILE = path.join(DATA_DIR, 'decisions.json');
const INDEX_FILE = path.join(DATA_DIR, 'index.json');
const CACHE_DIR = path.join(DATA_DIR, '.cache');
const BLOGGER_CACHE = path.join(CACHE_DIR, 'blogger-articles.json');
const BLOGGER_FEED =
  process.env.EOC_BLOGGER_FEED ??
  'https://observadorcubano.blogspot.com/feeds/posts/default?alt=json&max-results=150';

const TYPES = new Map([
  ['1', 'text'],
  ['2', 'image'],
  ['3', 'video'],
  ['4', 'link'],
  ['5', 'comunicado'],
]);

const TYPE_LABELS = {
  text: 'Texto',
  image: 'Imagen',
  video: 'Vídeo',
  link: 'Enlace',
  comunicado: 'Comunicado',
};

function questioner() {
  const rl = createInterface({ input, output });
  return {
    ask: (label, fallback = '') => rl.question(`${label}${fallback ? ` [${fallback}]` : ''}: `).then((value) => value.trim() || fallback),
    close: () => rl.close(),
  };
}

async function readJson(file, fallback) {
  try {
    return JSON.parse(await readFile(file, 'utf8'));
  } catch (error) {
    if (error.code === 'ENOENT') return fallback;
    throw error;
  }
}

async function writeJson(file, value) {
  await mkdir(path.dirname(file), { recursive: true });
  await writeFile(file, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

async function loadPosts() {
  return readJson(POSTS_FILE, {
    schemaVersion: 1,
    channel: { name: CHANNEL_NAME, url: CHANNEL_URL },
    posts: [],
  });
}

async function savePosts(document) {
  await writeJson(POSTS_FILE, document);
}

function slugify(value) {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 46);
}

function newId(title, text, publishedAt) {
  const date = publishedAt.slice(0, 10);
  const base = slugify(title || text).slice(0, 32) || 'publicacion';
  return `${date}-${base}`;
}

function isoDate(value, fallbackNow = true) {
  if (!value) {
    if (!fallbackNow) return '';
    return new Date().toISOString();
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    throw new Error(`Fecha no válida: ${value}`);
  }
  return parsed.toISOString();
}

function parseLinks(value) {
  return String(value ?? '')
    .split(/\s*[\n,]+\s*/)
    .map((link) => link.trim())
    .filter(Boolean);
}

function printPost(post) {
  const parts = [
    `- ${post.id}`,
    `  ${TYPE_LABELS[post.type] ?? post.type} · ${post.publishedAt}`,
    `  ${post.text.replace(/\s+/g, ' ').slice(0, 160)}`,
  ];
  if (post.articleUrl) parts.push(`  artículo: ${post.articleUrl}`);
  if (post.image?.url) parts.push(`  imagen: ${post.image.url}`);
  if (post.video?.url) parts.push(`  vídeo: ${post.video.url}`);
  return parts.join('\n');
}

async function commandList() {
  const document = await loadPosts();
  if (document.posts.length === 0) {
    console.log('No hay publicaciones registradas.');
    return;
  }
  for (const post of document.posts) {
    console.log(printPost(post));
  }
}

async function promptPost(base = {}) {
  const ui = questioner();
  try {
    const typeLabel = base.type
      ? `${base.type} (${TYPE_LABELS[base.type]})`
      : '1';
    const typeAnswer = await ui.ask(
      'Tipo: 1 texto, 2 imagen, 3 vídeo, 4 enlace, 5 comunicado',
      typeLabel,
    );
    const type = TYPES.get(typeAnswer) ?? (TYPE_LABELS[typeAnswer] ? typeAnswer : 'text');

    const text = await ui.ask(
      'Texto (usa \\n para saltos de línea)',
      base.text ?? '',
    );
    if (!text) throw new Error('El texto es obligatorio');
    const cleanText = text.replace(/\\n/g, '\n');

    const title = await ui.ask('Título (opcional)', base.title ?? '');
    const publishedAt = isoDate(
      await ui.ask('Fecha ISO (vacío = ahora)', base.publishedAt ?? ''),
    );
    const whatsappUrl = await ui.ask(
      'Enlace original de WhatsApp (vacío = canal)',
      base.whatsappUrl ?? CHANNEL_URL,
    );

    const links = parseLinks(
      await ui.ask(
        'Enlaces adicionales separados por coma (opcional)',
        (base.links ?? []).join(', '),
      ),
    );

    const imageUrl = await ui.ask('URL de imagen HTTPS (opcional)', base.image?.url ?? '');
    const videoUrl = await ui.ask('URL de vídeo HTTPS (opcional)', base.video?.url ?? '');
    const videoThumb = videoUrl
      ? await ui.ask('Miniatura del vídeo HTTPS (opcional)', base.video?.thumbnail ?? '')
      : '';

    const id = await ui.ask(
      'Identificador (vacío = automático)',
      base.id ?? newId(title, cleanText, publishedAt),
    );

    return {
      id,
      type,
      publishedAt,
      updatedAt: base.updatedAt ?? publishedAt,
      title,
      text: cleanText,
      whatsappUrl,
      links: [...new Set(links)],
      image: imageUrl
        ? {
            url: imageUrl,
            alt: base.image?.alt ?? title ?? 'Imagen de la publicación',
          }
        : undefined,
      video: videoUrl
        ? {
            url: videoUrl,
            thumbnail: videoThumb || undefined,
          }
        : undefined,
      status: 'published',
    };
  } finally {
    ui.close();
  }
}

function cleanPost(post) {
  const clean = { ...post };
  for (const key of ['image', 'video', 'title', 'updatedAt']) {
    if (clean[key] === undefined || clean[key] === '') delete clean[key];
  }
  if (Array.isArray(clean.links) && clean.links.length === 0) delete clean.links;
  if (clean.video && !clean.video.thumbnail) delete clean.video.thumbnail;
  return clean;
}

async function commandAdd() {
  const document = await loadPosts();
  const post = cleanPost(await promptPost());
  const errors = validatePostsDocument({ posts: [post] });
  if (errors.length > 0) {
    console.error('No se añadió la publicación:');
    for (const error of errors) console.error(`  - ${error}`);
    process.exitCode = 1;
    return;
  }
  if (document.posts.some((item) => item.id === post.id)) {
    console.error(`Ya existe una publicación con id ${post.id}`);
    process.exitCode = 1;
    return;
  }
  document.posts.push(post);
  await savePosts(document);
  console.log(`Añadida ${post.id}. Ejecuta "build" para regenerar el feed.`);
}

async function commandEdit(id) {
  const document = await loadPosts();
  const index = document.posts.findIndex((post) => post.id === id);
  if (index === -1) throw new Error(`No existe la publicación ${id}`);
  const updated = cleanPost(await promptPost(document.posts[index]));
  updated.id = id;
  updated.updatedAt = new Date().toISOString();
  const errors = validatePostsDocument({ posts: [updated] });
  if (errors.length > 0) {
    console.error('No se guardó la publicación:');
    for (const error of errors) console.error(`  - ${error}`);
    process.exitCode = 1;
    return;
  }
  document.posts[index] = updated;
  await savePosts(document);
  console.log(`Actualizada ${id}. Ejecuta "build" para regenerar el feed.`);
}

async function commandRemove(id) {
  const document = await loadPosts();
  const before = document.posts.length;
  document.posts = document.posts.filter((post) => post.id !== id);
  if (document.posts.length === before) {
    throw new Error(`No existe la publicación ${id}`);
  }
  await savePosts(document);
  console.log(`Eliminada ${id}.`);
}

async function fetchBloggerArticles() {
  const response = await fetch(BLOGGER_FEED, {
    headers: { 'User-Agent': 'ElObservadorCubano-ChannelFeed/1.0' },
    signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok) {
    throw new Error(`Blogger respondió HTTP ${response.status}`);
  }
  const json = await response.json();
  const articles = bloggerArticlesFromFeed(json);
  await mkdir(CACHE_DIR, { recursive: true });
  await writeJson(BLOGGER_CACHE, { fetchedAt: new Date().toISOString(), articles });
  return articles;
}

async function loadArticles(skipBlogger) {
  if (skipBlogger) {
    const cache = await readJson(BLOGGER_CACHE, null);
    if (!cache) throw new Error('No hay caché de Blogger; ejecuta "build" con conexión.');
    console.log(`Blogger: usando caché de ${cache.fetchedAt} (${cache.articles.length} artículos).`);
    return cache.articles;
  }
  try {
    const articles = await fetchBloggerArticles();
    console.log(`Blogger: ${articles.length} artículos leídos.`);
    return articles;
  } catch (error) {
    const cache = await readJson(BLOGGER_CACHE, null);
    if (cache) {
      console.warn(`No se pudo consultar Blogger (${error.message}). Uso la caché de ${cache.fetchedAt}.`);
      return cache.articles;
    }
    throw error;
  }
}

async function commandBuild(options = {}) {
  const document = await loadPosts();
  const errors = validatePostsDocument(document);
  if (errors.length > 0) {
    console.error('Publicaciones inválidas:');
    for (const error of errors) console.error(`  - ${error}`);
    process.exitCode = 1;
    return;
  }

  const articles = await loadArticles(options.skipBlogger === true);
  const decisions = await readJson(DECISIONS_FILE, { schemaVersion: 1, links: {} });
  const pageSize = options.pageSize ?? DEFAULT_PAGE_SIZE;
  const feed = buildFeed({
    posts: document.posts,
    articles,
    decisions,
    channel: document.channel ?? { name: CHANNEL_NAME, url: CHANNEL_URL },
    pageSize,
    now: new Date().toISOString(),
  });

  await writeJson(INDEX_FILE, feed.index);
  const expected = new Set(feed.index.pages);
  for (const [index, page] of feed.pages.entries()) {
    await writeJson(path.join(DATA_DIR, feed.index.pages[index]), page);
  }
  for (const name of await readdir(DATA_DIR)) {
    if (/^page-\d+\.json$/.test(name) && !expected.has(name)) {
      await rm(path.join(DATA_DIR, name));
      console.log(`Eliminada página obsoleta ${name}`);
    }
  }

  const pending = feed.items.filter((item) => item.match?.state === 'pending');
  console.log(
    `Feed generado: ${feed.index.total} elementos, ${feed.index.pageCount} página(s).`,
  );
  if (pending.length > 0) {
    console.log(`\n${pending.length} coincidencia(s) pendientes de revisión:`);
    for (const item of pending) {
      console.log(`- ${item.id} (${Math.round(item.match.confidence * 100)} %)`);
      console.log(`  candidato: ${item.match.candidateArticleUrl}`);
      console.log(`  usa: link ${item.id}  |  separate ${item.id}`);
    }
  }
}

async function commandReview() {
  const index = await readJson(INDEX_FILE, null);
  if (!index) {
    console.log('Todavía no hay feed generado. Ejecuta "build".');
    return;
  }
  const items = [];
  for (const name of index.pages) {
    const page = await readJson(path.join(DATA_DIR, name), null);
    if (page) items.push(...page.items);
  }
  const pending = items.filter((item) => item.match?.state === 'pending');
  if (pending.length === 0) {
    console.log('No hay coincidencias pendientes de revisión.');
    return;
  }
  for (const item of pending) {
    console.log(`- ${item.id} (${Math.round(item.match.confidence * 100)} %)`);
    console.log(`  ${item.text.replace(/\s+/g, ' ').slice(0, 160)}`);
    console.log(`  candidato: ${item.match.candidateArticleUrl}`);
    console.log(`  link ${item.id}  |  separate ${item.id}`);
  }
}

async function saveDecision(id, decision) {
  const index = await readJson(INDEX_FILE, null);
  if (!index) throw new Error('No hay feed generado; ejecuta "build" antes de revisar.');
  const items = [];
  for (const name of index.pages) {
    const page = await readJson(path.join(DATA_DIR, name), null);
    if (page) items.push(...page.items);
  }
  const item = items.find((entry) => entry.id === id);
  if (!item) throw new Error(`No existe el elemento ${id} en el feed actual.`);
  if (!item.match?.candidateArticleUrl) {
    throw new Error('Ese elemento no tiene un candidato pendiente.');
  }
  const decisions = await readJson(DECISIONS_FILE, { schemaVersion: 1, links: {} });
  decisions.links[item.id] = {
    articleUrl: item.match.candidateArticleUrl,
    decision,
    decidedAt: new Date().toISOString(),
  };
  await writeJson(DECISIONS_FILE, decisions);
  console.log(
    `${decision === 'linked' ? 'Vinculada' : 'Separada'} ${item.id}. Ejecuta "build" para aplicar el cambio.`,
  );
}

async function commandValidate() {
  const document = await loadPosts();
  const errors = validatePostsDocument(document);
  const index = await readJson(INDEX_FILE, null);
  if (!index) {
    errors.push('Falta data/channel/index.json; ejecuta "build".');
  } else {
    if (index.schemaVersion !== 1) errors.push('index.json: schemaVersion inválido');
    if (!Array.isArray(index.pages) || index.pages.length === 0) {
      errors.push('index.json: no declara páginas');
    }
    const seenArticles = new Set();
    let total = 0;
    for (const name of index.pages ?? []) {
      if (!/^page-\d+\.json$/.test(name)) {
        errors.push(`index.json: nombre de página inválido ${name}`);
        continue;
      }
      const page = await readJson(path.join(DATA_DIR, name), null);
      if (!page) {
        errors.push(`Falta ${name}`);
        continue;
      }
      total += page.items?.length ?? 0;
      for (const item of page.items ?? []) {
        if (item.articleUrl) {
          const key = normalizeUrl(item.articleUrl);
          if (seenArticles.has(key)) {
            errors.push(`Artículo duplicado en el feed: ${item.articleUrl}`);
          }
          seenArticles.add(key);
        }
        if (item.image && !isHttpsUrl(item.image.url)) {
          errors.push(`${item.id}: imagen no HTTPS`);
        }
        if (item.video && !isHttpsUrl(item.video.url)) {
          errors.push(`${item.id}: vídeo no HTTPS`);
        }
      }
    }
    if (total !== index.total) {
      errors.push(`index.json declara ${index.total} elementos pero las páginas suman ${total}`);
    }
    if (index.pageCount !== (index.pages?.length ?? 0)) {
      errors.push('index.json: pageCount no coincide con pages');
    }
  }

  if (errors.length > 0) {
    console.error('Validación fallida:');
    for (const error of errors) console.error(`  - ${error}`);
    process.exitCode = 1;
    return;
  }
  console.log(
    `Validación correcta: ${document.posts.length} publicación(es), ${index.total} elemento(s), ${index.pageCount} página(s).`,
  );
}

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: ROOT,
    stdio: 'inherit',
    shell: false,
    ...options,
  });
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(' ')} falló con código ${result.status}`);
  }
}

function git(args) {
  return run('git', [
    '-c',
    'credential.helper=',
    '-c',
    'credential.helper=!gh auth git-credential',
    ...args,
  ]);
}

async function commandPublish(message) {
  await commandBuild({});
  if (process.exitCode) return;

  const status = spawnSync('git', ['status', '--porcelain', '--', 'data/channel'], {
    cwd: ROOT,
    encoding: 'utf8',
  });
  if (status.status !== 0) throw new Error('No se pudo leer el estado de Git.');
  if (status.stdout.trim() === '') {
    console.log('No hay cambios en el feed; no se crea un pull request.');
    return;
  }

  const branch = spawnSync('git', ['rev-parse', '--abbrev-ref', 'HEAD'], {
    cwd: ROOT,
    encoding: 'utf8',
  }).stdout.trim();
  let target = branch;
  if (branch === 'main' || branch === 'master') {
    target = `canal/${new Date().toISOString().slice(0, 10)}-${Date.now().toString().slice(-5)}`;
    git(['switch', '-c', target]);
  }

  git(['add', '--', 'data/channel']);
  git([
    'commit',
    '-m',
    message || 'canal: actualizar feed de publicaciones',
  ]);
  git(['push', '-u', 'origin', target]);

  const repo = spawnSync('gh', ['repo', 'view', '--json', 'nameWithOwner', '--jq', '.nameWithOwner'], {
    cwd: ROOT,
    encoding: 'utf8',
  }).stdout.trim();
  if (!repo) throw new Error('No se pudo identificar el repositorio con gh.');
  const pr = spawnSync(
    'gh',
    [
      'pr',
      'create',
      '--repo',
      repo,
      '--base',
      'main',
      '--head',
      target,
      '--title',
      message || 'canal: actualizar feed de publicaciones',
      '--body',
      'Actualiza el feed JSON del canal de WhatsApp. No modifica el APK ni requiere una versión nueva.',
    ],
    { cwd: ROOT, encoding: 'utf8' },
  );
  if (pr.status !== 0) {
    process.stderr.write(pr.stderr);
    throw new Error('No se pudo crear el pull request.');
  }
  process.stdout.write(pr.stdout);
}

function valueOf(name) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : undefined;
}

async function main() {
  const command = process.argv[2] ?? 'list';

  if (process.argv.includes('--text')) {
    // Alta no interactiva para automatizaciones y documentación.
    const document = await loadPosts();
    const publishedAt = isoDate(valueOf('--published') ?? '');
    const text = await (async () => {
      const file = valueOf('--text-file');
      return file ? (await readFile(path.resolve(process.cwd(), file), 'utf8')).trim() : valueOf('--text');
    })();
    const title = valueOf('--title') ?? '';
    const post = cleanPost({
      id: valueOf('--id') ?? newId(title, text, publishedAt),
      type: valueOf('--type') ?? 'text',
      publishedAt,
      updatedAt: publishedAt,
      title,
      text,
      whatsappUrl: valueOf('--whatsapp') ?? CHANNEL_URL,
      links: parseLinks(valueOf('--link') ?? ''),
      image: valueOf('--image') ? { url: valueOf('--image') } : undefined,
      video: valueOf('--video')
        ? { url: valueOf('--video'), thumbnail: valueOf('--thumb') }
        : undefined,
      status: process.argv.includes('--draft') ? 'draft' : 'published',
    });
    const errors = validatePostsDocument({ posts: [post] });
    if (errors.length > 0) {
      for (const error of errors) console.error(`- ${error}`);
      process.exitCode = 1;
      return;
    }
    document.posts.push(post);
    await savePosts(document);
    console.log(`Añadida ${post.id}.`);
    return;
  }

  switch (command) {
    case 'list':
      await commandList();
      break;
    case 'add':
      await commandAdd();
      break;
    case 'edit':
      await commandEdit(process.argv[3]);
      break;
    case 'remove':
      await commandRemove(process.argv[3]);
      break;
    case 'build':
      await commandBuild({
        skipBlogger: process.argv.includes('--skip-blogger'),
        pageSize: valueOf('--page-size')
          ? Number(valueOf('--page-size'))
          : undefined,
      });
      break;
    case 'review':
      await commandReview();
      break;
    case 'link':
      await saveDecision(process.argv[3], 'linked');
      break;
    case 'separate':
      await saveDecision(process.argv[3], 'separated');
      break;
    case 'validate':
      await commandValidate();
      break;
    case 'publish':
      await commandPublish(valueOf('--message'));
      break;
    default:
      console.error(`Comando desconocido: ${command}`);
      process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
