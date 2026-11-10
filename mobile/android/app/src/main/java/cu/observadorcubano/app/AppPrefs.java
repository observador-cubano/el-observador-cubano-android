package cu.observadorcubano.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Preferencias compartidas con la pantalla web de configuración.
 *
 * Capacitor Preferences guarda los valores en el archivo "CapacitorStorage"
 * sin prefijos adicionales, por lo que la interfaz nativa puede leerlos.
 */
final class AppPrefs {

    private static final String PREFS_NAME = "CapacitorStorage";
    private static final String KEY_LINK_MODE = "externalLinkMode";
    private static final String KEY_TEXT_ZOOM = "textZoom";
    private static final String KEY_DARK_MODE = "darkMode";
    private static final String KEY_DATA_SAVER = "dataSaver";
    private static final String KEY_NEW_POST_ALERTS = "newPostAlerts";

    private static final String VALUE_ON = "on";

    private static final String MODE_SYSTEM = "system";
    private static final String MODE_INAPP = "inapp";
    private static final String DARK_SYSTEM = "system";
    private static final String DARK_LIGHT = "light";
    private static final String DARK_DARK = "dark";
    private static final String DATA_SAVER_ON = "on";
    private static final int MIN_ZOOM = 80;
    private static final int MAX_ZOOM = 140;
    private static final int DEFAULT_ZOOM = 100;

    private AppPrefs() {
        // Clase de utilidades.
    }

    static String getExternalLinkMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_LINK_MODE, MODE_SYSTEM);
        return MODE_INAPP.equals(value) ? MODE_INAPP : MODE_SYSTEM;
    }

    static boolean opensLinksInApp(Context context) {
        return MODE_INAPP.equals(getExternalLinkMode(context));
    }

    static int getTextZoom(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_TEXT_ZOOM, String.valueOf(DEFAULT_ZOOM));
        try {
            int zoom = Integer.parseInt(value);
            return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
        } catch (NumberFormatException e) {
            return DEFAULT_ZOOM;
        }
    }

    static String getDarkMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_DARK_MODE, DARK_SYSTEM);
        if (DARK_LIGHT.equals(value) || DARK_DARK.equals(value)) {
            return value;
        }
        return DARK_SYSTEM;
    }

    /**
     * Resuelve el modo oscuro efectivo. En modo "system" se respeta la
     * configuración nocturna del dispositivo.
     */
    static boolean isDarkMode(Context context) {
        String mode = getDarkMode(context);
        if (DARK_DARK.equals(mode)) {
            return true;
        }
        if (DARK_LIGHT.equals(mode)) {
            return false;
        }
        int nightMode = context.getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    static boolean isDataSaverEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return DATA_SAVER_ON.equals(prefs.getString(KEY_DATA_SAVER, "off"));
    }

    /**
     * Avisos locales de artículos nuevos. La propia app revisa el feed público
     * del blog; no hay push ni servidor de por medio.
     */
    static boolean areNewPostAlertsEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return VALUE_ON.equals(prefs.getString(KEY_NEW_POST_ALERTS, "off"));
    }

    static void setNewPostAlertsEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_NEW_POST_ALERTS, enabled ? VALUE_ON : "off")
            .apply();
    }

    /**
     * Sincroniza el modo nocturno nativo con la preferencia guardada. Se llama
     * al arrancar la aplicación y cuando cambia el ajuste desde la pantalla
     * de información.
     */
    static void applyNightMode(Context context) {
        String mode = getDarkMode(context);
        int nightMode;
        if (DARK_DARK.equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else if (DARK_LIGHT.equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else {
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode);
        }
    }
}
