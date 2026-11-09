package cu.observadorcubano.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

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

    /**
     * Abre un enlace HTTPS con el Intent del sistema. Si WhatsApp está
     * instalado y reconoce el dominio, se abre la app; si no, el navegador.
     */
    @PluginMethod
    public void openExternal(PluginCall call) {
        String url = call.getString("url");
        if (url == null || !url.startsWith("https://")) {
            call.reject("Solo se permiten enlaces HTTPS");
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            call.reject("No hay una actividad disponible");
            return;
        }

        call.resolve();
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                activity.startActivity(intent);
            } catch (ActivityNotFoundException exception) {
                // Sin navegador ni app compatible: no se puede abrir el enlace.
                android.widget.Toast.makeText(
                    activity,
                    "No se encontró una aplicación para abrir el enlace.",
                    android.widget.Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}
