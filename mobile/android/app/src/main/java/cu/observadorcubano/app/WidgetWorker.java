package cu.observadorcubano.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.util.List;

/** Trae el feed y actualiza el widget con las tres publicaciones más recientes. */
public class WidgetWorker extends Worker {

    public WidgetWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        try {
            List<NewPostsFeed.Post> posts = NewPostsFeed.fetch();
            WidgetStore.save(context, posts);
            WidgetRenderer.updateAll(context);
            return Result.success();
        } catch (IOException e) {
            WidgetRenderer.showOffline(context);
            return Result.retry();
        }
    }
}
