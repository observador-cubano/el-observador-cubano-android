package cu.observadorcubano.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

/**
 * Revisión periódica del feed público para detectar artículos nuevos.
 *
 * No se ejecuta si el usuario desactivó los avisos. Si la descarga falla,
 * WorkManager reintenta más tarde con su propia política de espera.
 */
public class NewPostsWorker extends Worker {

    public NewPostsWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!AppPrefs.areNewPostAlertsEnabled(context)
            || !NewPostsNotifier.canPostNotifications(context)) {
            return Result.success();
        }

        try {
            NewPostsAlerts.evaluate(context, NewPostsFeed.fetch());
        } catch (IOException e) {
            return Result.retry();
        }
        return Result.success();
    }
}
