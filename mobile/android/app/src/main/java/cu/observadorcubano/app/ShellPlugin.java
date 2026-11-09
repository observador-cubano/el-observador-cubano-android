package cu.observadorcubano.app;

import android.app.Activity;
import android.content.Intent;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Puente mínimo para la pantalla local de información.
 *
 * Se registra únicamente en InfoActivity. El WebView que muestra Blogger no
 * tiene ningún puente JavaScript-nativo expuesto.
 */
@CapacitorPlugin(name = "Shell")
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
}
