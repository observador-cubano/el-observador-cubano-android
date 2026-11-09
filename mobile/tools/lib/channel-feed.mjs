/**
 * Lógica del feed del canal de WhatsApp.
 *
 * WhatsApp Channels no tiene una API pública de lectura. Este módulo construye
 * un feed propio a partir de publicaciones registradas por el medio y las
 * relaciona con los artículos de Blogger para no mostrar dos veces la misma
 * noticia.
 *
 * No contiene dependencias externas y se puede probar sin red.
 */

export const CHANNEL_URL =
  'https://whatsapp.com/channel/0029Vb73VkN7tkj5MlScKL1k';

export const CHANNEL_NAME = 'El Observador Cubano';

export const DEFAULT_PAGE_SIZE = 10;

/** Parámetros que solo sirven para medir o atribuir tráfico. */
const TRACKING_PARAMS = new Set([
  'fbclid',
  'gclid',
  'dclid',
  'gbraid',
  'wbraid',
  'msclkid',
  'mc_cid',
  'mc_eid',
  'igshid',
  'si',
  'spm',
  'ref_src',
  'ref_url',
]);

const SPANISH_STOPWORDS = new Set([
  'a', 'al', 'algo', 'algunas', 'algunos', 'ante', 'antes', 'como', 'con',
  'contra', 'cual', 'cuando', 'de', 'del', 'desde', 'donde', 'dos', 'el',
  'ella', 'ellas', 'ellos', 'en', 'entre', 'era', 'es', 'esa', 'ese', 'eso',
  'esta', 'este', 'esto', 'fue', 'ha', 'hasta', 'hay', 'la', 'las', 'le',
  'les', 'lo', 'los', 'mas', 'más', 'mi', 'mis', 'mucho', 'muy', 'no', 'nos',
  'o', 'otra', 'otro', 'para', 'pero', 'poco', 'por', 'porque', 'que', 'quien',
  'se', 'sin', 'sobre', 'su', 'sus', 'tambien', 'también', 'tanto', 'te', 'tu',
  'tus', 'un', 'una', 'uno', 'unos', 'y', 'ya',
]);

export function isHttpsUrl(value) {
  if (typeof value !== 'string' || value.trim() === '') return false;
  try {
    return new URL(value).protocol === 'https:';
  } catch {
    return false;
  }
}

/**
 * Normaliza una URL para comparar contenido sin confundir artículos distintos.
 * Conserva los parámetros que pueden identificar un recurso; solo elimina los
 * de seguimiento conocidos y los fragmentos.
 */
export function normalizeUrl(raw) {
  if (typeof raw !== 'string' || raw.trim() === '') return '';
  let url;
  try {
    url = new URL(raw.trim());
  } catch {
    return '';
  }

  if (url.protocol !== 'http:' && url.protocol !== 'https:') return '';

  let host = url.hostname.toLowerCase();
  if (host.startsWith('www.')) host = host.slice(4);
  if (host === 'm.whatsapp.com') host = 'whatsapp.com';

  let path = url.pathname || '/';
  // Blogger sirve la misma entrada con ?m=1; no es un recurso distinto.
  const params = new URLSearchParams(url.search);
  params.delete('m');

  for (const key of [...params.keys()]) {
    const lower = key.toLowerCase();
    if (TRACKING_PARAMS.has(lower) || lower.startsWith('utm_')) {
      params.delete(key);
    }
  }

  const sorted = [...params.entries()].sort(([a, av], [b, bv]) => {
    if (a !== b) return a < b ? -1 : 1;
    return av < bv ? -1 : av > bv ? 1 : 0;
  });
  const query = new URLSearchParams(sorted).toString();

  if (path.length > 1 && path.endsWith('/')) path = path.slice(0, -1);
  if (path === '') path = '/';

  return `https://${host}${path}${query ? `?${query}` : ''}`;
}

export function normalizeTitle(value) {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
    .replace(/\s+/g, ' ');
}

export function textTokens(value) {
  return new Set(
    normalizeTitle(value)
      .split(' ')
      .filter((token) => token.length > 2 && !SPANISH_STOPWORDS.has(token)),
  );
}

/** Similitud de Jaccard con una corrección por contención. */
export function similarity(left, right) {
  const a = textTokens(left);
  const b = textTokens(right);
  if (a.size === 0 || b.size === 0) return 0;

  let common = 0;
  for (const token of a) {
    if (b.has(token)) common += 1;
  }
  if (common === 0) return 0;

  const jaccard = common / (a.size + b.size - common);
  const containment = common / Math.min(a.size, b.size);
  return Math.max(jaccard, containment * 0.92);
}

export function stripHtml(value) {
  return String(value ?? '')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/\s+/g, ' ')
    .trim();
}

export function summarize(value, maxLength = 240) {
  const text = String(value ?? '').replace(/\s+/g, ' ').trim();
  if (text.length <= maxLength) return text;
  const cut = text.slice(0, maxLength);
  const space = cut.lastIndexOf(' ');
  return `${cut.slice(0, space > maxLength * 0.6 ? space : maxLength).trim()}…`;
}

function daysBetween(a, b) {
  const first = Date.parse(a);
  const second = Date.parse(b);
  if (!Number.isFinite(first) || !Number.isFinite(second)) return Infinity;
  return Math.abs(first - second) / 86_400_000;
}

/**
 * Convierte la respuesta JSON del feed de Blogger en artículos comparables.
 */
export function bloggerArticlesFromFeed(feed) {
  const entries = feed?.feed?.entry;
  if (!Array.isArray(entries)) return [];

  return entries
    .map((entry) => {
      const links = Array.isArray(entry.link) ? entry.link : [];
      const alternate = links.find((link) => link.rel === 'alternate');
      const url = alternate?.href ?? links[0]?.href;
      const title = stripHtml(entry.title?.$t ?? '');
      const summary = stripHtml(
        entry.summary?.$t ?? entry.content?.$t ?? '',
      );
      const image =
        entry.media$thumbnail?.url ??
        entry['media:thumbnail']?.url ??
        null;
      return {
        id: entry.id?.$t ?? url,
        url,
        urlNorm: normalizeUrl(url),
        title,
        titleNorm: normalizeTitle(title),
        summary,
        publishedAt: entry.published?.$t ?? entry.updated?.$t ?? '',
        updatedAt: entry.updated?.$t ?? entry.published?.$t ?? '',
        image,
      };
    })
    .filter((article) => article.urlNorm !== '');
}

function postLinks(post) {
  return (Array.isArray(post.links) ? post.links : [])
    .map((link) => normalizeUrl(link))
    .filter(Boolean);
}

/**
 * Puntúa la relación entre una publicación del canal y un artículo.
 * Devuelve null si no hay relación suficiente.
 */
export function scoreMatch(post, article, maxDays = 30) {
  if (!post || !article) return null;
  const links = postLinks(post);
  if (links.includes(article.urlNorm)) {
    return { confidence: 1, reason: 'url' };
  }

  const distance = daysBetween(post.publishedAt, article.publishedAt);
  if (distance > maxDays) return null;

  const postTitle = normalizeTitle(post.title ?? '');
  if (postTitle !== '' && postTitle === article.titleNorm) {
    return { confidence: 0.93, reason: 'title' };
  }

  const score = similarity(
    `${post.title ?? ''} ${post.text ?? ''}`,
    `${article.title} ${article.summary}`,
  );
  if (score >= 0.86) {
    return { confidence: 0.9, reason: 'content' };
  }
  if (score >= 0.72) {
    return { confidence: 0.78, reason: 'content-review' };
  }
  return null;
}

export function findBestMatch(post, articles, maxDays = 30) {
  let best = null;
  for (const article of articles) {
    const match = scoreMatch(post, article, maxDays);
    if (!match) continue;
    if (
      best === null ||
      match.confidence > best.match.confidence ||
      (match.confidence === best.match.confidence &&
        daysBetween(post.publishedAt, article.publishedAt) <
          daysBetween(post.publishedAt, best.article.publishedAt))
    ) {
      best = { article, match };
    }
  }
  return best;
}

function decisionFor(postId, article, decisions) {
  const entry = decisions?.links?.[postId];
  if (!entry || typeof entry !== 'object') return null;
  if (normalizeUrl(entry.articleUrl) !== article.urlNorm) return null;
  return entry.decision === 'linked' || entry.decision === 'separated'
    ? entry.decision
    : null;
}

function mergeGroup(group, post) {
  group.posts.push(post);
  const latest = group.posts[group.posts.length - 1];
  group.item = {
    ...group.item,
    id: `wa-${latest.id}`,
    text: latest.text,
    title: latest.title || group.item.title,
    updatedAt: latest.updatedAt || latest.publishedAt,
    publishedAt: latest.publishedAt,
    image: latest.image || group.item.image,
    video: latest.video || group.item.video,
  };
  group.item.whatsappUrls.push(latest.whatsappUrl);
  group.item.whatsappUrl = latest.whatsappUrl;
}

/**
 * Construye el feed unificado y paginado.
 *
 * `posts` son las publicaciones registradas; `articles` los artículos de
 * Blogger; `decisions` guarda las revisiones manuales de coincidencias medias.
 */
export function buildFeed({
  posts,
  articles = [],
  decisions = {},
  channel = { name: CHANNEL_NAME, url: CHANNEL_URL },
  pageSize = DEFAULT_PAGE_SIZE,
  now = new Date().toISOString(),
} = {}) {
  if (!Array.isArray(posts)) throw new Error('posts debe ser un arreglo');
  if (!Number.isInteger(pageSize) || pageSize < 1) {
    throw new Error('pageSize debe ser un entero positivo');
  }

  const published = posts
    .filter((post) => post && post.status !== 'draft')
    .sort(
      (a, b) =>
        Date.parse(b.publishedAt ?? 0) - Date.parse(a.publishedAt ?? 0),
    );

  const groups = new Map();
  const items = [];

  for (const post of published) {
    const best = findBestMatch(post, articles);
    let matched = null;
    let review = null;

    if (best) {
      const decision = decisionFor(post.id, best.article, decisions);
      if (decision === 'separated') {
        matched = null;
        review = { ...best.match, state: 'separated' };
      } else if (best.match.confidence >= 0.86 || decision === 'linked') {
        matched = best;
        review = { ...best.match, state: 'auto' };
      } else {
        review = { ...best.match, state: 'pending' };
      }
    }

    if (matched) {
      const key = matched.article.urlNorm;
      if (groups.has(key)) {
        mergeGroup(groups.get(key), post);
      } else {
        const group = {
          article: matched.article,
          posts: [post],
          item: {
            id: `wa-${post.id}`,
            type: post.type,
            publishedAt: post.publishedAt,
            updatedAt: post.updatedAt ?? post.publishedAt,
            title: post.title ?? '',
            text: post.text,
            summary: summarize(post.text),
            image: post.image ?? null,
            video: post.video ?? null,
            whatsappUrl: post.whatsappUrl,
            whatsappUrls: [post.whatsappUrl],
            articleUrl: matched.article.url,
            articleTitle: matched.article.title,
            sources: ['whatsapp', 'blog'],
            match: {
              confidence: review.confidence,
              reason: review.reason,
              state: review.state,
            },
          },
        };
        groups.set(key, group);
        items.push(group.item);
      }
      continue;
    }

    items.push({
      id: post.id,
      type: post.type,
      publishedAt: post.publishedAt,
      updatedAt: post.updatedAt ?? post.publishedAt,
      title: post.title ?? '',
      text: post.text,
      summary: summarize(post.text),
      image: post.image ?? null,
      video: post.video ?? null,
      whatsappUrl: post.whatsappUrl,
      whatsappUrls: [post.whatsappUrl],
      articleUrl: null,
      articleTitle: null,
      sources: ['whatsapp'],
      match: review
        ? {
            confidence: review.confidence,
            reason: review.reason,
            state: review.state,
            candidateArticleUrl: best.article.url,
            candidateArticleTitle: best.article.title,
          }
        : null,
    });
  }

  items.sort(
    (a, b) => Date.parse(b.publishedAt ?? 0) - Date.parse(a.publishedAt ?? 0),
  );

  const pages = [];
  for (let index = 0; index < items.length; index += pageSize) {
    pages.push(items.slice(index, index + pageSize));
  }
  if (pages.length === 0) pages.push([]);

  return {
    index: {
      schemaVersion: 1,
      channel,
      updatedAt: now,
      pageSize,
      pageCount: pages.length,
      total: items.length,
      pages: pages.map((_, index) => `page-${index + 1}.json`),
    },
    pages: pages.map((pageItems, index) => ({
      schemaVersion: 1,
      page: index + 1,
      pageSize,
      total: items.length,
      updatedAt: now,
      items: pageItems,
    })),
    items,
  };
}

/** Validaciones de las publicaciones de origen. */
export function validatePostsDocument(document) {
  const errors = [];
  if (!document || typeof document !== 'object') {
    return ['El documento debe ser un objeto'];
  }
  if (!Array.isArray(document.posts)) {
    return ['posts debe ser un arreglo'];
  }

  const ids = new Set();
  document.posts.forEach((post, index) => {
    const at = `posts[${index}]`;
    if (!post || typeof post !== 'object') {
      errors.push(`${at}: debe ser un objeto`);
      return;
    }
    if (!post.id || !/^[a-z0-9][a-z0-9-]{2,80}$/.test(post.id)) {
      errors.push(`${at}.id: usa minúsculas, números y guiones (3-80)`);
    } else if (ids.has(post.id)) {
      errors.push(`${at}.id: duplicado (${post.id})`);
    } else {
      ids.add(post.id);
    }
    if (!['text', 'image', 'video', 'link', 'comunicado'].includes(post.type)) {
      errors.push(`${at}.type: valor no admitido`);
    }
    if (!post.publishedAt || !Number.isFinite(Date.parse(post.publishedAt))) {
      errors.push(`${at}.publishedAt: fecha ISO no válida`);
    }
    if (typeof post.text !== 'string' || post.text.trim().length < 4) {
      errors.push(`${at}.text: texto demasiado corto`);
    }
    if (!isHttpsUrl(post.whatsappUrl)) {
      errors.push(`${at}.whatsappUrl: debe ser HTTPS`);
    }
    if (post.image && !isHttpsUrl(post.image.url)) {
      errors.push(`${at}.image.url: debe ser HTTPS`);
    }
    if (post.video) {
      if (!isHttpsUrl(post.video.url)) {
        errors.push(`${at}.video.url: debe ser HTTPS`);
      }
      if (post.video.thumbnail && !isHttpsUrl(post.video.thumbnail)) {
        errors.push(`${at}.video.thumbnail: debe ser HTTPS`);
      }
    }
    if (post.links !== undefined && !Array.isArray(post.links)) {
      errors.push(`${at}.links: debe ser un arreglo`);
    } else {
      (post.links ?? []).forEach((link, linkIndex) => {
        if (!isHttpsUrl(link)) {
          errors.push(`${at}.links[${linkIndex}]: debe ser HTTPS`);
        }
      });
    }
    if (post.status !== undefined && !['published', 'draft'].includes(post.status)) {
      errors.push(`${at}.status: usa published o draft`);
    }
  });

  return errors;
}
