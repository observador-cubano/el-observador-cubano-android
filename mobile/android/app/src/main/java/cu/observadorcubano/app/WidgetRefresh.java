package cu.observadorcubano.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

/**
 * Descarga las últimas publicaciones para el widget.
 *
 * Es un trabajo único con red obligatoria. Si el teléfono está sin conexión,
 * WorkManager lo reintenta con su propia espera y el widget sigue mostrando lo
 * último que tenía.
 */
final class WidgetRefresh {

    private static final String WORK_NAME = "eoc_widget_refresh";

    private WidgetRefresh() {
        // Clase de utilidades.
    }

    static void request(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WidgetWorker.class)
            .setConstraints(
                new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        );
    }

    /**
     * Hay red con acceso a Internet.
     *
     * Se comprueba antes de mostrar «Actualizando…»: con el trabajo aplazado
     * por falta de red, ese texto se quedaría para siempre en el widget.
     */
    static boolean isOnline(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        );
        if (manager == null) {
            return true;
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
            && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
