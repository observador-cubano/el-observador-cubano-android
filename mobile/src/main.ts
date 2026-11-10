import { App } from '@capacitor/app';
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

/**
 * Pequeño puente nativo propio. Solo se registra en la pantalla local de
 * información; nunca se expone al sitio remoto de Blogger.
 */
interface ShellPlugin {
  close(): Promise<void>;
  openUpdates(): Promise<void>;
  openSaved(): Promise<void>;
  openHistory(): Promise<void>;
  applyTheme(): Promise<void>;
  setNewPostAlerts(options: { enabled: boolean }): Promise<{ enabled: boolean; granted: boolean }>;
}

const Shell = registerPlugin<ShellPlugin>('Shell');

const DEFAULT_SETTINGS = {
  externalLinkMode: 'system',
  textZoom: 100,
  darkMode: 'system',
  dataSaver: false,
  newPostAlerts: false,
} as const;

type ExternalLinkMode = 'system' | 'inapp';
type DarkMode = 'system' | 'light' | 'dark';

interface Settings {
  externalLinkMode: ExternalLinkMode;
  textZoom: number;
  darkMode: DarkMode;
  dataSaver: boolean;
  newPostAlerts: boolean;
}

const SETTING_KEYS = {
  externalLinkMode: 'externalLinkMode',
  textZoom: 'textZoom',
  darkMode: 'darkMode',
  dataSaver: 'dataSaver',
  newPostAlerts: 'newPostAlerts',
} as const;

const $ = <T extends Element>(selector: string): T | null =>
  document.querySelector<T>(selector);

const toast = $('#toast') as HTMLDivElement | null;
let toastTimer: number | undefined;

function showToast(message: string): void {
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('toast--visible');
  if (toastTimer !== undefined) {
    window.clearTimeout(toastTimer);
  }
  toastTimer = window.setTimeout(() => {
    toast.classList.remove('toast--visible');
  }, 2200);
}

function clampZoom(value: number): number {
  if (!Number.isFinite(value)) return DEFAULT_SETTINGS.textZoom;
  return Math.min(140, Math.max(80, Math.round(value / 5) * 5));
}

function normalizeMode(value: string | null | undefined): ExternalLinkMode {
  return value === 'inapp' ? 'inapp' : 'system';
}

function normalizeDarkMode(value: string | null | undefined): DarkMode {
  if (value === 'dark' || value === 'light') return value;
  return 'system';
}

async function loadSettings(): Promise<Settings> {
  const [mode, zoom, darkMode, dataSaver, newPostAlerts] = await Promise.all([
    Preferences.get({ key: SETTING_KEYS.externalLinkMode }),
    Preferences.get({ key: SETTING_KEYS.textZoom }),
    Preferences.get({ key: SETTING_KEYS.darkMode }),
    Preferences.get({ key: SETTING_KEYS.dataSaver }),
    Preferences.get({ key: SETTING_KEYS.newPostAlerts }),
  ]);

  return {
    externalLinkMode: normalizeMode(mode.value),
    textZoom: clampZoom(Number(zoom.value ?? DEFAULT_SETTINGS.textZoom)),
    darkMode: normalizeDarkMode(darkMode.value),
    dataSaver: dataSaver.value === 'on',
    newPostAlerts: newPostAlerts.value === 'on',
  };
}

async function saveSetting(key: string, value: string): Promise<void> {
  await Preferences.set({ key, value });
}

function applySettingsToForm(settings: Settings): void {
  document
    .querySelectorAll<HTMLInputElement>('input[name="externalLinkMode"]')
    .forEach((input) => {
      input.checked = input.value === settings.externalLinkMode;
    });

  document.querySelectorAll<HTMLInputElement>('input[name="darkMode"]').forEach((input) => {
    input.checked = input.value === settings.darkMode;
  });

  const dataSaver = $('#data-saver') as HTMLInputElement | null;
  if (dataSaver) dataSaver.checked = settings.dataSaver;

  const newPostAlerts = $('#new-post-alerts') as HTMLInputElement | null;
  if (newPostAlerts) newPostAlerts.checked = settings.newPostAlerts;

  const range = $('#text-zoom') as HTMLInputElement | null;
  if (range) range.value = String(settings.textZoom);
  updateZoomPreview(settings.textZoom);
  applyPageTheme(settings.darkMode);
  watchSystemTheme(settings.darkMode);
}

let systemThemeQuery: MediaQueryList | null = null;

function systemPrefersDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function applyPageTheme(mode: DarkMode): void {
  const dark = mode === 'dark' || (mode === 'system' && systemPrefersDark());
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
}

function watchSystemTheme(mode: DarkMode): void {
  systemThemeQuery?.removeEventListener('change', handleSystemThemeChange);
  systemThemeQuery = null;
  if (mode !== 'system') return;
  systemThemeQuery = window.matchMedia('(prefers-color-scheme: dark)');
  systemThemeQuery.addEventListener('change', handleSystemThemeChange);
}

function handleSystemThemeChange(): void {
  applyPageTheme('system');
  applySystemBarStyles();
}

function updateZoomPreview(zoom: number): void {
  const output = $('#text-zoom-value') as HTMLOutputElement | null;
  if (output) output.textContent = `${zoom} %`;

  const preview = $('#text-preview') as HTMLParagraphElement | null;
  if (preview) preview.style.fontSize = `${(16 * zoom) / 100}px`;
}

async function initAbout(): Promise<void> {
  const versionBadge = $('#version-badge');
  const buildNumber = $('#build-number');
  const packageName = $('#package-name');
  const platformName = $('#platform-name');

  try {
    const info = await App.getInfo();
    if (versionBadge) versionBadge.textContent = `Versión ${info.version}`;
    if (buildNumber) buildNumber.textContent = info.build || '—';
    if (packageName) packageName.textContent = info.id || 'cu.observadorcubano.app';
  } catch {
    if (versionBadge) versionBadge.textContent = 'Versión 1.1.0';
    if (packageName) packageName.textContent = 'cu.observadorcubano.app';
  }

  if (platformName) {
    const labels: Record<string, string> = {
      android: 'Android',
      ios: 'iOS',
      web: 'Web',
    };
    platformName.textContent = labels[Capacitor.getPlatform()] ?? Capacitor.getPlatform();
  }
}

async function openExternal(url: string): Promise<void> {
  try {
    await Browser.open({ url, presentationStyle: 'popover' });
  } catch {
    window.open(url, '_system');
  }
}

/** Activa o desactiva los avisos locales revisando el permiso en Android. */
async function applyNewPostAlerts(enabled: boolean): Promise<void> {
  const checkbox = $('#new-post-alerts') as HTMLInputElement | null;
  try {
    const result = await Shell.setNewPostAlerts({ enabled });
    if (checkbox) checkbox.checked = result.enabled;
    if (!result.enabled) {
      showToast(
        enabled
          ? 'No se pudo activar: revisa el permiso de notificaciones'
          : 'Avisos de artículos nuevos desactivados'
      );
      return;
    }
    if (!result.granted) {
      showToast('Avisos activados. Permite las notificaciones en Ajustes de Android');
      return;
    }
    showToast('Te avisaremos de los artículos nuevos');
  } catch {
    await saveSetting(SETTING_KEYS.newPostAlerts, enabled ? 'on' : 'off');
    showToast('Abre la app de Android para cambiar los avisos');
  }
}

function bindEvents(): void {
  $('#btn-close')?.addEventListener('click', () => {
    void Shell.close().catch(() => window.history.back());
  });

  document
    .querySelectorAll<HTMLInputElement>('input[name="externalLinkMode"]')
    .forEach((input) => {
      input.addEventListener('change', () => {
        if (!input.checked) return;
        void saveSetting(SETTING_KEYS.externalLinkMode, input.value).then(() => {
          showToast('Preferencia guardada');
        });
      });
    });

  document.querySelectorAll<HTMLInputElement>('input[name="darkMode"]').forEach((input) => {
    input.addEventListener('change', () => {
      if (!input.checked) return;
      const mode = normalizeDarkMode(input.value);
      applyPageTheme(mode);
      watchSystemTheme(mode);
      void saveSetting(SETTING_KEYS.darkMode, mode)
        .then(() => Shell.applyTheme().catch(() => undefined))
        .then(() => showToast('Apariencia actualizada'));
    });
  });

  const dataSaver = $('#data-saver') as HTMLInputElement | null;
  dataSaver?.addEventListener('change', () => {
    void saveSetting(SETTING_KEYS.dataSaver, dataSaver.checked ? 'on' : 'off').then(() => {
      showToast(
        dataSaver.checked
          ? 'Las imágenes se omitirán con datos móviles'
          : 'Las imágenes se cargarán siempre'
      );
    });
  });

  const range = $('#text-zoom') as HTMLInputElement | null;
  range?.addEventListener('input', () => {
    const zoom = clampZoom(Number(range.value));
    updateZoomPreview(zoom);
  });
  range?.addEventListener('change', () => {
    const zoom = clampZoom(Number(range.value));
    void saveSetting(SETTING_KEYS.textZoom, String(zoom)).then(() => {
      showToast('Tamaño del texto guardado');
    });
  });

  const newPostAlerts = $('#new-post-alerts') as HTMLInputElement | null;
  newPostAlerts?.addEventListener('change', () => {
    void applyNewPostAlerts(newPostAlerts.checked);
  });

  $('#btn-reset')?.addEventListener('click', () => {
    void Promise.all([
      saveSetting(SETTING_KEYS.externalLinkMode, DEFAULT_SETTINGS.externalLinkMode),
      saveSetting(SETTING_KEYS.textZoom, String(DEFAULT_SETTINGS.textZoom)),
      saveSetting(SETTING_KEYS.darkMode, DEFAULT_SETTINGS.darkMode),
      saveSetting(SETTING_KEYS.dataSaver, 'off'),
      saveSetting(SETTING_KEYS.newPostAlerts, 'off'),
      Shell.setNewPostAlerts({ enabled: false }).catch(() => undefined),
    ]).then(() => {
      applySettingsToForm({ ...DEFAULT_SETTINGS });
      void Shell.applyTheme().catch(() => undefined);
      showToast('Configuración restablecida');
    });
  });

  $('#btn-open-saved')?.addEventListener('click', () => {
    void Shell.openSaved().catch(() => {
      showToast('Abre la app de Android para ver los guardados');
    });
  });

  $('#btn-open-history')?.addEventListener('click', () => {
    void Shell.openHistory().catch(() => {
      showToast('Abre la app de Android para ver el historial');
    });
  });

  document.querySelectorAll<HTMLButtonElement>('[data-url]').forEach((button) => {
    button.addEventListener('click', () => {
      const url = button.dataset.url;
      if (url) void openExternal(url);
    });
  });

  $('#btn-check-updates')?.addEventListener('click', () => {
    void Shell.openUpdates().catch(() => {
      showToast('Abre la app de Android para comprobar actualizaciones');
    });
  });

  $('#btn-history')?.addEventListener('click', () => {
    void Shell.openUpdates().catch(() => {
      showToast('Abre la app de Android para ver el historial');
    });
  });
}

void (async () => {
  bindEvents();
  const settings = await loadSettings();
  applySettingsToForm(settings);
  await initAbout();
  applySystemBarStyles();
})();

/**
 * La cabecera de esta pantalla es azul y el borde inferior es blanco: se
 * mantienen los iconos claros arriba y oscuros abajo. Se aplica después de
 * que Capacitor inicialice sus barras para que no lo sobrescriba.
 */
function applySystemBarStyles(): void {
  if (!Capacitor.isNativePlatform()) return;

  const dark = document.documentElement.dataset.theme === 'dark';

  void SystemBars.setStyle({
    style: SystemBarsStyle.Dark,
    bar: SystemBarType.StatusBar,
  }).catch(() => {
    // Si la plataforma no expone la API, el estilo nativo ya está aplicado.
  });

  void SystemBars.setStyle({
    style: dark ? SystemBarsStyle.Dark : SystemBarsStyle.Light,
    bar: SystemBarType.NavigationBar,
  }).catch(() => {
    // Igual que arriba: no es un fallo funcional de la pantalla.
  });
}
