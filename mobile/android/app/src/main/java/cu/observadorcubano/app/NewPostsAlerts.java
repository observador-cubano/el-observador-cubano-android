package cu.observadorcubano.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Programación y estado de los avisos de artículos nuevos.
 *
 * El trabajo periódico solo se programa si el usuario lo activó. La primera
 * revisión guarda la publicación más reciente sin avisar, para no notificar
 * contenido que ya existía antes de activar el ajuste.
 */
final class NewPostsAlerts {

    static final String WORK_NAME = "eoc_new_posts";
    static final long PERIOD_HOURS = 6L;

    private static final String PREFS_NAME = "eoc_new_posts_state";
    private static final String KEY_LAST_ID = "last_id";

    private NewPostsAlerts() {
        // Clase de utilidades.
    }

    /** Mantiene el trabajo periódico en sintonía con la preferencia. */
    static void sync(Context context) {
        WorkManager work = WorkManager.getInstance(context);
        if (!AppPrefs.areNewPostAlertsEnabled(context)) {
            work.cancelUniqueWork(WORK_NAME);
            work.cancelUniqueWork(WORK_NAME + "_now");
            NewPostsNotifier.cancel(context);
            return;
        }

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
            NewPostsWorker.class,
            PERIOD_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(networkConstraint())
            .build();
        NewPostsNotifier.ensureChannel(context);
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    /** Comprueba el feed ahora mismo, por ejemplo al activar el ajuste. */
    static void checkNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(NewPostsWorker.class)
            .setConstraints(networkConstraint())
            .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME + "_now",
            ExistingWorkPolicy.REPLACE,
            request
        );
    }

    /**
     * Compara el feed con la última publicación conocida. Devuelve true solo
     * cuando se publicó el aviso; la primera ejecución únicamente guarda la
     * referencia.
     */
    static boolean evaluate(Context context, List<NewPostsFeed.Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return false;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lastId = prefs.getString(KEY_LAST_ID, "");
        NewPostsFeed.Post newest = posts.get(0);

        if (lastId.isEmpty()) {
            remember(prefs, newest);
            return false;
        }

        int fresh = countNew(lastId, posts);
        if (lastId.equals(newest.id)) {
            return false;
        }

        remember(prefs, newest);
        if (fresh <= 0) {
            return false;
        }
        NewPostsNotifier.show(context, newest, fresh);
        return true;
    }

    /**
     * Cantidad de publicaciones anteriores a {@code lastId}. Si la referencia
     * ya no aparece en la ventana del feed, se consideran todas nuevas.
     */
    static int countNew(String lastId, List<NewPostsFeed.Post> posts) {
        if (lastId == null || lastId.isEmpty() || posts == null || posts.isEmpty()) {
            return 0;
        }
        int fresh = 0;
        for (NewPostsFeed.Post post : posts) {
            if (lastId.equals(post.id)) {
                return fresh;
            }
            fresh++;
        }
        return fresh;
    }

    private static void remember(SharedPreferences prefs, NewPostsFeed.Post post) {
        prefs.edit()
            .putString(KEY_LAST_ID, post.id)
            .apply();
    }

    private static Constraints networkConstraint() {
        return new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();
    }
}
