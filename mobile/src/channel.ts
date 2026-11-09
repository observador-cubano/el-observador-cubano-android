import { Browser } from '@capacitor/browser';
import { Preferences } from '@capacitor/preferences';
import {
  Capacitor,
  SystemBars,
  SystemBarsStyle,
  SystemBarType,
  registerPlugin,
} from '@capacitor/core';

import './style.css';
import './channel.css';

/**
 * Puente nativo mínimo. `openExternal` usa un Intent de Android para que los
 * enlaces de WhatsApp abran la aplicación si está instalada y, si no, el
 * navegador del sistema.
 */
interface ShellPlugin {
  close(): Promise<void>;
  openExternal(options: { url: string }): Promise<void>;
}

const Shell = registerPlugin<ShellPlugin>('Shell');

const FEED_BASE =
  'https://raw.githubusercontent.com/observador-cubano/el-observador-cubano-android/main/data/channel/';
const FEED_FALLBACK_BASE =
  'https://cdn.jsdelivr.net/gh/observador-cubano/el-observador-cubano-android@main/data/channel/';
const DEFAULT_CHANNEL_URL =
  'https://whatsapp.com/channel/0029Vb73VkN7tkj5MlScKL1k';

const CACHE_INDEX = 'eoc_channel_index_v1';
const CACHE_PAGE = 'eoc_channel_page_v1_';
const CACHE_LAST_SEEN = 'eoc_channel_last_seen_v1';
const CACHE_EDITS = 'eoc_channel_edits_v1';
const REFRESH_AFTER_MS = 5 * 60 * 1000;

interface ChannelInfo {
  name: string;
  url: string;
}

interface FeedIndex {
  schemaVersion: number;
  channel: ChannelInfo;
  updatedAt: string;
  pageSize: number;
  pageCount: number;
  total: number;
  pages: string[];
}

interface FeedImage {
  url: string;
  width?: number;
  height?: number;
  alt?: string;
}

interface FeedVideo {
  url: string;
  thumbnail?: string;
}

interface MatchInfo {
  confidence: number;
  reason: string;
  state: string;
  candidateArticleUrl?: string;
  candidateArticleTitle?: string;
}

interface FeedItem {
  id: string;
  type: string;
  publishedAt: string;
  updatedAt: string;
  title?: string;
  text: string;
  summary?: string;
  image?: FeedImage | null;
  video?: FeedVideo | null;
  whatsappUrl: string;
  whatsappUrls?: string[];
  articleUrl?: string | null;
  articleTitle?: string | null;
  sources: string[];
  match?: MatchInfo | null;
}

interface FeedPage {
  schemaVersion: number;
  page: number;
  pageSize: number;
  total: number;
  updatedAt: string;
  items: FeedItem[];
}

interface IndexCache {
  savedAt: number;
  index: FeedIndex;
}

interface PageCache {
  savedAt: number;
  updatedAt: string;
  page: FeedPage;
}

const $ = <T extends Element>(selector: string): T | null =>
  document.querySelector<T>(selector);

const feedElement = $('#feed') as HTMLDivElement;
const loadingElement = $('#loading') as HTMLDivElement;
const emptyElement = $('#empty') as HTMLDivElement;
const errorElement = $('#error') as HTMLDivElement;
const errorText = $('#error-text') as HTMLParagraphElement;
const statusElement = $('#feed-status') as HTMLParagraphElement;
const moreButton = $('#btn-more') as HTMLButtonElement;
const offlineBanner = $('#offline-banner') as HTMLDivElement;
const toastElement = $('#toast') as HTMLDivElement;

let index: FeedIndex | null = null;
let pages = new Map<number, FeedPage>();
let items: FeedItem[] = [];
let activeFilter = 'all';
let lastFetchAt = 0;
let refreshing = false;
let loadingMore = false;
let lastSeenAt = 0;
let edits: Record<string, string> = {};
let focusId: string | null = new URLSearchParams(window.location.search).get(
  'post',
);
let toastTimer: number | undefined;

function showToast(message: string): void {
  if (!toastElement) return;
  toastElement.textContent = message;
  toastElement.classList.add('toast--visible');
  if (toastTimer !== undefined) window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => {
    toastElement.classList.remove('toast--visible');
  }, 2400);
}

function channelUrl(): string {
  return index?.channel?.url || DEFAULT_CHANNEL_URL;
}

async function openExternal(url: string): Promise<void> {
  if (!/^https:\/\//i.test(url)) {
    showToast('El enlace no es seguro.');
    return;
  }
  try {
    await Shell.openExternal({ url });
  } catch {
    try {
      await Browser.open({ url, presentationStyle: 'popover' });
    } catch {
      window.open(url, '_blank', 'noopener');
    }
  }
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return new Intl.DateTimeFormat('es', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function relativeTime(value: number): string {
  const seconds = Math.max(1, Math.round((Date.now() - value) / 1000));
  if (seconds < 60) return 'hace unos segundos';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `hace ${minutes} min`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `hace ${hours} h`;
  const days = Math.round(hours / 24);
  return `hace ${days} d`;
}

function setStatus(message: string): void {
  if (statusElement) statusElement.textContent = message;
}

function setLoading(active: boolean): void {
  loadingElement.hidden = !active;
  if (active) {
    emptyElement.hidden = true;
    errorElement.hidden = true;
  }
}

function showError(message: string): void {
  errorText.textContent = message;
  errorElement.hidden = false;
}

function setOffline(active: boolean, message?: string): void {
  offlineBanner.hidden = !active;
  if (active && message) {
    const text = $('#offline-text') as HTMLSpanElement | null;
    if (text) text.textContent = message;
  }
}

function isValidIndex(value: unknown): value is FeedIndex {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as FeedIndex;
  return (
    candidate.schemaVersion === 1 &&
    typeof candidate.updatedAt === 'string' &&
    Number.isInteger(candidate.pageCount) &&
    candidate.pageCount > 0 &&
    Array.isArray(candidate.pages) &&
    candidate.pages.length === candidate.pageCount
  );
}

function isValidPage(value: unknown): value is FeedPage {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as FeedPage;
  return (
    candidate.schemaVersion === 1 &&
    Number.isInteger(candidate.page) &&
    Array.isArray(candidate.items)
  );
}

async function fetchJson(path: string, version: string): Promise<unknown> {
  const query = version ? `?v=${encodeURIComponent(version)}` : '';
  const urls = [
    `${FEED_BASE}${path}${query}`,
    `${FEED_FALLBACK_BASE}${path}${query}`,
  ];
  let lastError: unknown = null;

  for (const url of urls) {
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), 15_000);
    try {
      const response = await fetch(url, {
        signal: controller.signal,
        cache: 'no-store',
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return await response.json();
    } catch (error) {
      lastError = error;
    } finally {
      window.clearTimeout(timer);
    }
  }
  throw lastError instanceof Error ? lastError : new Error('Sin conexión');
}

async function loadSeen(): Promise<void> {
  const [seen, storedEdits] = await Promise.all([
    Preferences.get({ key: CACHE_LAST_SEEN }),
    Preferences.get({ key: CACHE_EDITS }),
  ]);
  const parsed = Number(seen.value ?? 0);
  lastSeenAt = Number.isFinite(parsed) ? parsed : 0;
  if (storedEdits.value) {
    try {
      const value = JSON.parse(storedEdits.value) as Record<string, string>;
      if (value && typeof value === 'object') edits = value;
    } catch {
      edits = {};
    }
  }
}

async function saveSeen(): Promise<void> {
  const merged = { ...edits };
  for (const item of items) merged[item.id] = item.updatedAt;
  edits = merged;
  lastSeenAt = Date.now();
  await Promise.all([
    Preferences.set({ key: CACHE_LAST_SEEN, value: String(lastSeenAt) }),
    Preferences.set({ key: CACHE_EDITS, value: JSON.stringify(edits) }),
  ]);
}

async function loadCachedFeed(): Promise<void> {
  const cached = await Preferences.get({ key: CACHE_INDEX });
  if (!cached.value) return;
  try {
    const envelope = JSON.parse(cached.value) as IndexCache;
    if (!isValidIndex(envelope.index)) return;
    index = envelope.index;
    const loaded: FeedPage[] = [];
    for (let page = 1; page <= index.pageCount; page += 1) {
      const raw = await Preferences.get({
        key: `${CACHE_PAGE}${index.updatedAt}_${page}`,
      });
      if (!raw.value) continue;
      const pageEnvelope = JSON.parse(raw.value) as PageCache;
      if (
        pageEnvelope.updatedAt === index.updatedAt &&
        isValidPage(pageEnvelope.page)
      ) {
        loaded.push(pageEnvelope.page);
      }
    }
    if (loaded.length === 0) return;
    pages = new Map(loaded.map((page) => [page.page, page]));
    items = loaded.flatMap((page) => page.items);
    render();
    setStatus(`Copia guardada · ${relativeTime(envelope.savedAt)}`);
  } catch {
    // Una caché corrupta no debe impedir la actualización.
  }
}

async function saveIndexCache(): Promise<void> {
  if (!index) return;
  const envelope: IndexCache = { savedAt: Date.now(), index };
  await Preferences.set({
    key: CACHE_INDEX,
    value: JSON.stringify(envelope),
  });
}

async function savePageCache(page: FeedPage): Promise<void> {
  if (!index) return;
  const envelope: PageCache = {
    savedAt: Date.now(),
    updatedAt: index.updatedAt,
    page,
  };
  await Preferences.set({
    key: `${CACHE_PAGE}${index.updatedAt}_${page.page}`,
    value: JSON.stringify(envelope),
  });
}

async function loadPage(pageNumber: number): Promise<FeedPage> {
  if (!index) throw new Error('Falta el índice del feed');
  const cached = pages.get(pageNumber);
  if (cached) return cached;

  const name = index.pages[pageNumber - 1];
  if (!name) throw new Error('Página fuera de rango');
  const raw = await fetchJson(name, index.updatedAt);
  if (!isValidPage(raw)) throw new Error('La página del feed no es válida');
  pages.set(raw.page, raw);
  items = [...pages.values()]
    .sort((a, b) => a.page - b.page)
    .flatMap((page) => page.items);
  await savePageCache(raw);
  return raw;
}

function clearFeedView(): void {
  feedElement.replaceChildren();
  emptyElement.hidden = true;
  errorElement.hidden = true;
  moreButton.hidden = true;
}

function filteredItems(): FeedItem[] {
  return activeFilter === 'all'
    ? items
    : items.filter((item) => item.type === activeFilter);
}

function createBadge(label: string, className = ''): HTMLSpanElement {
  const badge = document.createElement('span');
  badge.className = `channel-card__flag ${className}`.trim();
  badge.textContent = label;
  return badge;
}

function renderText(container: HTMLElement, text: string): void {
  const paragraphs = String(text ?? '')
    .split(/\n{2,}/)
    .map((value) => value.trim())
    .filter(Boolean);
  for (const paragraph of paragraphs.length > 0 ? paragraphs : [text]) {
    const p = document.createElement('p');
    p.className = 'channel-card__paragraph';
    const parts = paragraph.split(/(https?:\/\/[^\s]+)/g);
    for (const part of parts) {
      if (/^https?:\/\//i.test(part)) {
        const link = document.createElement('a');
        link.className = 'channel-card__inline-link';
        link.href = part;
        link.target = '_blank';
        link.rel = 'noopener';
        link.textContent = part;
        link.addEventListener('click', (event) => {
          event.preventDefault();
          void openExternal(part);
        });
        p.append(link);
      } else {
        p.append(document.createTextNode(part));
      }
    }
    container.append(p);
  }
}

function renderImage(image: FeedImage): HTMLElement {
  const figure = document.createElement('figure');
  figure.className = 'channel-card__media';
  if (image.width && image.height && image.width > 0 && image.height > 0) {
    figure.style.aspectRatio = `${image.width} / ${image.height}`;
  }
  const img = document.createElement('img');
  img.src = image.url;
  img.alt = image.alt || 'Imagen de la publicación';
  img.loading = 'lazy';
  img.decoding = 'async';
  img.addEventListener('error', () => figure.remove());
  figure.append(img);
  return figure;
}

function renderVideo(video: FeedVideo): HTMLElement {
  const figure = document.createElement('figure');
  figure.className = 'channel-card__media channel-card__media--video';

  const play = document.createElement('button');
  play.type = 'button';
  play.className = 'channel-card__play';
  play.setAttribute('aria-label', 'Reproducir vídeo');

  if (video.thumbnail) {
    const poster = document.createElement('img');
    poster.src = video.thumbnail;
    poster.alt = '';
    poster.loading = 'lazy';
    poster.decoding = 'async';
    figure.append(poster);
  }

  const icon = document.createElement('span');
  icon.className = 'channel-card__play-icon';
  icon.setAttribute('aria-hidden', 'true');
  icon.textContent = '▶';
  play.append(icon);
  figure.append(play);

  play.addEventListener('click', () => {
    const player = document.createElement('video');
    player.className = 'channel-card__video';
    player.controls = true;
    player.autoplay = true;
    player.playsInline = true;
    if (video.thumbnail) player.poster = video.thumbnail;
    player.src = video.url;
    figure.replaceChildren(player);
    void player.play().catch(() => {
      // El control nativo permite iniciar la reproducción manualmente.
    });
  });

  return figure;
}

function createAction(
  label: string,
  url: string,
  primary: boolean,
): HTMLAnchorElement {
  const link = document.createElement('a');
  link.className = `btn ${primary ? 'btn--primary' : 'btn--ghost'} channel-card__action`;
  link.href = url;
  link.target = '_blank';
  link.rel = 'noopener';
  link.textContent = label;
  link.addEventListener('click', (event) => {
    event.preventDefault();
    void openExternal(url);
  });
  return link;
}

function renderCard(item: FeedItem): HTMLElement {
  const article = document.createElement('article');
  article.className = 'channel-card';
  article.id = `post-${item.id}`;

  const top = document.createElement('div');
  top.className = 'channel-card__top';
  top.append(createBadge('Canal', 'channel-card__flag--channel'));

  const time = document.createElement('time');
  time.className = 'channel-card__date';
  time.dateTime = item.publishedAt;
  time.textContent = formatDate(item.publishedAt);
  top.append(time);

  if (lastSeenAt > 0) {
    const published = Date.parse(item.publishedAt);
    if (Number.isFinite(published) && published > lastSeenAt) {
      top.append(createBadge('Nuevo', 'channel-card__flag--new'));
    } else if (edits[item.id] && edits[item.id] !== item.updatedAt) {
      top.append(createBadge('Actualizado', 'channel-card__flag--updated'));
    }
  }

  article.append(top);

  if (item.title) {
    const title = document.createElement('h2');
    title.className = 'channel-card__title';
    title.textContent = item.title;
    article.append(title);
  }

  const text = document.createElement('div');
  text.className = 'channel-card__text';
  renderText(text, item.text);
  article.append(text);

  if (item.image?.url) article.append(renderImage(item.image));
  if (item.video?.url) article.append(renderVideo(item.video));

  const sources = document.createElement('div');
  sources.className = 'channel-card__sources';
  const sourceLabel = document.createElement('span');
  sourceLabel.className = 'channel-card__sources-label';
  sourceLabel.textContent = 'Publicado en';
  sources.append(sourceLabel);
  if (item.sources.includes('blog')) {
    const blog = document.createElement('span');
    blog.className = 'channel-card__source';
    blog.textContent = 'Periódico digital';
    sources.append(blog);
  }
  if (item.sources.includes('whatsapp')) {
    const wa = document.createElement('span');
    wa.className = 'channel-card__source channel-card__source--wa';
    wa.textContent = 'Canal de WhatsApp';
    sources.append(wa);
  }
  article.append(sources);

  const actions = document.createElement('div');
  actions.className = 'channel-card__actions';
  if (item.articleUrl) {
    actions.append(
      createAction('Leer artículo completo', item.articleUrl, true),
    );
  }
  const whatsapp = item.whatsappUrl || channelUrl();
  actions.append(
    createAction(
      'Ver publicación original',
      whatsapp,
      !item.articleUrl,
    ),
  );
  article.append(actions);

  return article;
}

function updateMoreButton(): void {
  if (!index) {
    moreButton.hidden = true;
    return;
  }
  const loaded = pages.size;
  moreButton.hidden = loaded >= index.pageCount;
  moreButton.textContent =
    loaded + 1 >= index.pageCount
      ? 'Cargar última página'
      : 'Cargar más publicaciones';
}

function render(): void {
  clearFeedView();
  const list = filteredItems();

  if (list.length === 0) {
    if (index && index.total > 0 && activeFilter !== 'all') {
      emptyElement.hidden = false;
      const heading = emptyElement.querySelector('h2');
      const paragraph = emptyElement.querySelector('p');
      if (heading) heading.textContent = 'No hay publicaciones de este tipo';
      if (paragraph) {
        paragraph.textContent =
          'Prueba con otro filtro o carga más publicaciones del canal.';
      }
    } else if (index && index.total === 0) {
      emptyElement.hidden = false;
      const heading = emptyElement.querySelector('h2');
      const paragraph = emptyElement.querySelector('p');
      if (heading) heading.textContent = 'Aún no hay publicaciones';
      if (paragraph) {
        paragraph.textContent =
          'El canal se actualiza desde el feed editorial del medio. Mientras tanto, puedes abrir el canal oficial en WhatsApp.';
      }
    }
  }

  for (const item of list) feedElement.append(renderCard(item));

  updateMoreButton();
  if (index) {
    setStatus(
      list.length === 0
        ? `${index.total} publicación(es) en el canal`
        : `Mostrando ${list.length} de ${index.total} publicación(es)`,
    );
  }

  if (focusId) {
    const target = document.getElementById(`post-${focusId}`);
    if (target) {
      target.classList.add('is-highlight');
      requestAnimationFrame(() =>
        target.scrollIntoView({ behavior: 'smooth', block: 'start' }),
      );
      focusId = null;
    }
  }
}

async function loadFirstPage(): Promise<void> {
  if (!index) return;
  pages = new Map();
  items = [];
  const page = await loadPage(1);
  if (page.items.length === 0) render();
  else render();
  await saveIndexCache();
}

async function refresh(force: boolean): Promise<boolean> {
  if (refreshing) return false;
  refreshing = true;
  if (items.length === 0) setLoading(true);
  setStatus('Actualizando…');

  try {
    const raw = await fetchJson('index.json', String(Date.now()));
    if (!isValidIndex(raw)) throw new Error('El índice del feed no es válido');

    const unchanged =
      !force &&
      index !== null &&
      index.updatedAt === raw.updatedAt &&
      items.length > 0;

    index = raw;
    if (unchanged) {
      setLoading(false);
      setOffline(false);
      setStatus(`Al día · ${relativeTime(lastFetchAt || Date.now())}`);
      lastFetchAt = Date.now();
      await saveSeen();
      return true;
    }

    await loadFirstPage();
    lastFetchAt = Date.now();
    setLoading(false);
    setOffline(false);
    setStatus(`Actualizado ${relativeTime(lastFetchAt)}`);
    await saveSeen();
    void focusRequestedPost();
    return true;
  } catch (error) {
    setLoading(false);
    const message =
      error instanceof Error ? error.message : 'Error de conexión';
    if (items.length > 0) {
      setOffline(true, 'Sin conexión. Se muestra la copia guardada.');
      setStatus(`Copia guardada · ${message}`);
    } else {
      showError(
        'No se pudo consultar el feed del canal. Revisa la conexión e inténtalo de nuevo.',
      );
      setStatus('Sin conexión');
    }
    return false;
  } finally {
    refreshing = false;
  }
}

async function loadMore(): Promise<void> {
  if (!index || loadingMore || pages.size >= index.pageCount) return;
  loadingMore = true;
  moreButton.disabled = true;
  try {
    const next = pages.size + 1;
    await loadPage(next);
    render();
    await saveSeen();
    void focusRequestedPost();
  } catch (error) {
    showToast(
      error instanceof Error
        ? 'No se pudo cargar más publicaciones.'
        : 'Error de conexión.',
    );
  } finally {
    loadingMore = false;
    moreButton.disabled = false;
  }
}

async function focusRequestedPost(): Promise<void> {
  if (!focusId || !index) return;
  while (
    !document.getElementById(`post-${focusId}`) &&
    pages.size < index.pageCount
  ) {
    await loadPage(pages.size + 1);
    render();
  }
  const target = document.getElementById(`post-${focusId}`);
  if (target) {
    target.classList.add('is-highlight');
    requestAnimationFrame(() =>
      target.scrollIntoView({ behavior: 'smooth', block: 'start' }),
    );
    focusId = null;
  }
}

function bindEvents(): void {
  $('#btn-close')?.addEventListener('click', () => {
    void Shell.close().catch(() => window.history.back());
  });

  $('#btn-follow')?.addEventListener('click', () => {
    void openExternal(channelUrl());
  });
  $('#btn-empty-follow')?.addEventListener('click', () => {
    void openExternal(channelUrl());
  });
  $('#btn-refresh')?.addEventListener('click', () => {
    void refresh(true);
  });
  $('#btn-retry')?.addEventListener('click', () => {
    void refresh(true);
  });
  moreButton?.addEventListener('click', () => {
    void loadMore();
  });

  document.querySelectorAll<HTMLButtonElement>('[data-filter]').forEach((chip) => {
    chip.addEventListener('click', () => {
      activeFilter = chip.dataset.filter ?? 'all';
      document
        .querySelectorAll<HTMLButtonElement>('[data-filter]')
        .forEach((other) => {
          other.classList.toggle('is-active', other === chip);
          other.setAttribute(
            'aria-pressed',
            other === chip ? 'true' : 'false',
          );
        });
      render();
    });
  });

  window.addEventListener('online', () => {
    setOffline(false);
    void refresh(false);
  });
  window.addEventListener('offline', () => {
    if (items.length > 0) {
      setOffline(true, 'Sin conexión. Se muestra la copia guardada.');
    }
  });

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState !== 'visible') return;
    if (Date.now() - lastFetchAt > REFRESH_AFTER_MS) void refresh(false);
  });
}

function applySystemBarStyles(): void {
  if (!Capacitor.isNativePlatform()) return;
  void SystemBars.setStyle({
    style: SystemBarsStyle.Dark,
    bar: SystemBarType.StatusBar,
  }).catch(() => undefined);
  void SystemBars.setStyle({
    style: SystemBarsStyle.Light,
    bar: SystemBarType.NavigationBar,
  }).catch(() => undefined);
}

void (async () => {
  bindEvents();
  await loadSeen();
  await loadCachedFeed();
  applySystemBarStyles();
  await refresh(false);
})();
