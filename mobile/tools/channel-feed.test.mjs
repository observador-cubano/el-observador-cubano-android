import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildFeed,
  normalizeTitle,
  normalizeUrl,
  similarity,
  validatePostsDocument,
} from './lib/channel-feed.mjs';

const CHANNEL = {
  name: 'El Observador Cubano',
  url: 'https://whatsapp.com/channel/0029Vb73VkN7tkj5MlScKL1k',
};

function article(overrides = {}) {
  return {
    id: 'tag:blogger.com,1999:blog-1.post-1',
    url: 'https://observadorcubano.blogspot.com/2026/11/una-noticia.html',
    urlNorm:
      'https://observadorcubano.blogspot.com/2026/11/una-noticia.html',
    title: 'Una noticia importante sobre La Habana',
    titleNorm: 'una noticia importante sobre la habana',
    summary: 'El hecho ocurrió este viernes en el municipio de Centro Habana.',
    publishedAt: '2026-11-08T10:00:00-05:00',
    updatedAt: '2026-11-08T10:00:00-05:00',
    image: null,
    ...overrides,
  };
}

function post(overrides = {}) {
  return {
    id: '2026-11-08-una-noticia',
    type: 'link',
    publishedAt: '2026-11-08T12:00:00-05:00',
    updatedAt: '2026-11-08T12:00:00-05:00',
    title: 'Una noticia importante sobre La Habana',
    text: 'Una noticia importante sobre La Habana. Lee el artículo completo.',
    whatsappUrl:
      'https://whatsapp.com/channel/0029Vb73VkN7tkj5MlScKL1k/abc123',
    links: [
      'https://observadorcubano.blogspot.com/2026/11/una-noticia.html?utm_source=whatsapp&utm_medium=social',
    ],
    status: 'published',
    ...overrides,
  };
}

test('normaliza URLs y elimina solo parámetros de seguimiento', () => {
  assert.equal(
    normalizeUrl(
      'https://www.observadorcubano.blogspot.com/2026/11/una-noticia.html/?utm_source=whatsapp&m=1#comentarios',
    ),
    'https://observadorcubano.blogspot.com/2026/11/una-noticia.html',
  );
});

test('conserva parámetros que identifican un recurso distinto', () => {
  const a = normalizeUrl('https://example.com/leer?p=123');
  const b = normalizeUrl('https://example.com/leer?p=456');
  assert.notEqual(a, b);
  assert.equal(a, 'https://example.com/leer?p=123');
});

test('normaliza títulos con acentos y puntuación', () => {
  assert.equal(
    normalizeTitle('  ¡Una NOTICIA, importante!  '),
    'una noticia importante',
  );
});

test('detecta similitud de contenido sin confundir textos distintos', () => {
  const high = similarity(
    'Una noticia importante sobre La Habana',
    'Una noticia importante sobre La Habana',
  );
  const low = similarity(
    'Una noticia importante sobre La Habana',
    'El equipo de béisbol ganó el partido del domingo',
  );
  assert.ok(high >= 0.86);
  assert.ok(low < 0.5);
});

test('unifica por URL y conserva ambos enlaces', () => {
  const articles = [article()];
  const feed = buildFeed({
    posts: [post()],
    articles,
    channel: CHANNEL,
    now: '2026-11-09T00:00:00Z',
  });

  assert.equal(feed.items.length, 1);
  const [item] = feed.items;
  assert.deepEqual(item.sources, ['whatsapp', 'blog']);
  assert.equal(item.articleUrl, articles[0].url);
  assert.equal(item.whatsappUrl, post().whatsappUrl);
  assert.equal(item.match.reason, 'url');
  assert.equal(item.match.state, 'auto');
});

test('una noticia compartida dos veces en el canal aparece una sola vez', () => {
  const second = post({
    id: '2026-11-09-una-noticia-repetida',
    publishedAt: '2026-11-09T09:00:00-05:00',
    whatsappUrl:
      'https://whatsapp.com/channel/0029Vb73VkN7tkj5MlScKL1k/def456',
  });
  const feed = buildFeed({
    posts: [post(), second],
    articles: [article()],
    channel: CHANNEL,
    now: '2026-11-09T00:00:00Z',
  });

  assert.equal(feed.items.length, 1);
  assert.equal(feed.items[0].whatsappUrls.length, 2);
  assert.equal(feed.items[0].whatsappUrl, second.whatsappUrl);
});

test('una coincidencia media no se unifica y queda para revisión', () => {
  const feed = buildFeed({
    posts: [
      post({
        id: '2026-11-08-cronica',
        title: 'La Habana: reportan un hecho este viernes en Centro Habana',
        text: 'La Habana: reportan un hecho este viernes en Centro Habana.',
        links: [],
      }),
    ],
    articles: [article()],
    channel: CHANNEL,
    now: '2026-11-09T00:00:00Z',
  });

  assert.equal(feed.items.length, 1);
  assert.deepEqual(feed.items[0].sources, ['whatsapp']);
  assert.equal(feed.items[0].match.state, 'pending');
  assert.equal(
    feed.items[0].match.candidateArticleUrl,
    'https://observadorcubano.blogspot.com/2026/11/una-noticia.html',
  );
});

test('una revisión manual puede separar una coincidencia alta', () => {
  const feed = buildFeed({
    posts: [post()],
    articles: [article()],
    decisions: {
      links: {
        '2026-11-08-una-noticia': {
          articleUrl:
            'https://observadorcubano.blogspot.com/2026/11/una-noticia.html',
          decision: 'separated',
        },
      },
    },
    channel: CHANNEL,
    now: '2026-11-09T00:00:00Z',
  });

  assert.equal(feed.items.length, 1);
  assert.deepEqual(feed.items[0].sources, ['whatsapp']);
  assert.equal(feed.items[0].match.state, 'separated');
});

test('pagina el feed sin perder elementos', () => {
  const posts = Array.from({ length: 23 }, (_, index) =>
    post({
      id: `2026-11-08-post-${index}`,
      title: `Nota ${index}`,
      text: `Nota ${index} con contenido suficiente para el feed.`,
      links: [],
      publishedAt: `2026-11-${String(1 + (index % 9)).padStart(2, '0')}T10:00:00-05:00`,
    }),
  );
  const feed = buildFeed({
    posts,
    articles: [],
    channel: CHANNEL,
    pageSize: 10,
    now: '2026-11-09T00:00:00Z',
  });

  assert.equal(feed.index.total, 23);
  assert.equal(feed.index.pageCount, 3);
  assert.equal(feed.pages[0].items.length, 10);
  assert.equal(feed.pages[2].items.length, 3);
  assert.equal(
    feed.pages.flatMap((page) => page.items).length,
    feed.index.total,
  );
});

test('valida publicaciones y rechaza HTTP, duplicados y tipos inválidos', () => {
  const errors = validatePostsDocument({
    posts: [
      post({
        id: '2026-11-08-una-noticia',
        image: { url: 'http://example.com/foto.jpg' },
        type: 'audio',
      }),
      post({ id: '2026-11-08-una-noticia' }),
    ],
  });

  assert.ok(errors.some((error) => error.includes('type')));
  assert.ok(errors.some((error) => error.includes('image.url')));
  assert.ok(errors.some((error) => error.includes('duplicado')));
});
