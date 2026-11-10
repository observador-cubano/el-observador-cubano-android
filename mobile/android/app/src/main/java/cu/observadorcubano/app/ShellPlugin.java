package cu.observadorcubano.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * Puente mínimo para la pantalla local de información.
 *
 * Se registra únicamente en InfoActivity. El WebView que muestra Blogger no
 * tiene ningún puente JavaScript-nativo expuesto.
 */
@CapacitorPlugin(
    name = "Shell",
    permissions = {
        @Permission(
            alias = "notifications",
            strings = { Manifest.permission.POST_NOTIFICATIONS }
        )
    }
)
public class ShellPlugin extends Plugin {

    @PluginMethod
    public void close(PluginCall call) {
        call.resolve();
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(activity::finish);
        }
    }

    /** Abre la pantalla nativa de Novedades y actualizaciones. */
    @PluginMethod
    public void openUpdates(PluginCall call) {
        Activity activity = getActivity();
        call.resolve();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                Intent intent = new Intent(activity, UpdateActivity.class);
                activity.startActivity(intent);
            });
        }
    }

    /** Abre la pantalla nativa de artículos guardados. */
    @PluginMethod
    public void openSaved(PluginCall call) {
        Activity activity = getActivity();
        call.resolve();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                Intent intent = new Intent(activity, SavedArticlesActivity.class);
                activity.startActivity(intent);
            });
        }
    }

    /** Abre la pantalla nativa del historial de lectura. */
    @PluginMethod
    public void openHistory(PluginCall call) {
        Activity activity = getActivity();
        call.resolve();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                Intent intent = new Intent(activity, ReadingHistoryActivity.class);
                activity.startActivity(intent);
            });
        }
    }

    /**
     * Aplica el modo claro/oscuro guardado en las preferencias. Cambiar el
     * modo por defecto hace que las actividades existentes se vuelvan a crear
     * con los recursos nocturnos correctos.
     */
    @PluginMethod
    public void applyTheme(PluginCall call) {
        Activity activity = getActivity();
        call.resolve();
        if (activity != null) {
            activity.runOnUiThread(() -> AppPrefs.applyNightMode(activity));
        }
    }

    /**
     * Activa o desactiva los avisos locales de artículos nuevos. En Android 13
     * o superior el permiso se pide en ese momento, nunca al arrancar.
     */
    @PluginMethod
    public void setNewPostAlerts(PluginCall call) {
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("Sin actividad");
            return;
        }

        boolean enabled = Boolean.TRUE.equals(call.getBoolean("enabled", false));
        if (!enabled) {
            AppPrefs.setNewPostAlertsEnabled(activity, false);
            NewPostsAlerts.sync(activity);
            call.resolve(alertResult(false, true));
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && getPermissionState("notifications") != PermissionState.GRANTED) {
            requestPermissionForAlias("notifications", call, "notificationsResult");
            return;
        }
        enableNewPostAlerts(call);
    }

    @PermissionCallback
    private void notificationsResult(PluginCall call) {
        if (getPermissionState("notifications") != PermissionState.GRANTED) {
            call.resolve(alertResult(false, false));
            return;
        }
        enableNewPostAlerts(call);
    }

    private void enableNewPostAlerts(PluginCall call) {
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("Sin actividad");
            return;
        }
        AppPrefs.setNewPostAlertsEnabled(activity, true);
        NewPostsAlerts.sync(activity);
        NewPostsAlerts.checkNow(activity);
        call.resolve(alertResult(true, NewPostsNotifier.canPostNotifications(activity)));
    }

    private JSObject alertResult(boolean enabled, boolean granted) {
        JSObject result = new JSObject();
        result.put("enabled", enabled);
        result.put("granted", granted);
        return result;
    }
}
