package cu.observadorcubano.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/**
 * Construye y actualiza el widget de últimas noticias.
 *
 * RemoteViews no admite listas dinámicas, así que se usan tres filas fijas que
 * se ocultan cuando no hay publicaciones suficientes.
 */
final class WidgetRenderer {

    private static final int[] ROW_IDS = {
        R.id.widgetTitle1,
        R.id.widgetTitle2,
        R.id.widgetTitle3,
    };

    private static final int REQUEST_ARTICLE = 4200;
    private static final int REQUEST_HEADER = 4210;
    private static final int REQUEST_REFRESH = 4211;

    private WidgetRenderer() {
        // Clase de utilidades.
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
            new ComponentName(context, ArticleWidgetProvider.class)
        );
        if (ids == null || ids.length == 0) {
            return;
        }

        RemoteViews views = build(context);
        for (int id : ids) {
            manager.updateAppWidget(id, views);
        }
    }

    static RemoteViews build(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_articles);
        List<WidgetStore.Item> items = WidgetStore.posts(context);

        for (int i = 0; i < ROW_IDS.length; i++) {
            if (i < items.size()) {
                WidgetStore.Item item = items.get(i);
                views.setViewVisibility(ROW_IDS[i], View.VISIBLE);
                views.setTextViewText(
                    ROW_IDS[i],
                    item.title.isEmpty() ? context.getString(R.string.widget_untitled) : item.title
                );
                views.setOnClickPendingIntent(
                    ROW_IDS[i],
                    articleIntent(context, item.url, i)
                );
            } else {
                views.setViewVisibility(ROW_IDS[i], View.GONE);
            }
        }

        views.setOnClickPendingIntent(R.id.widgetHeader, appIntent(context));
        views.setOnClickPendingIntent(R.id.widgetRefresh, refreshIntent(context));
        views.setContentDescription(
            R.id.widgetHeader,
            context.getString(R.string.widget_description)
        );
        views.setTextViewText(R.id.widgetFooter, footerText(context, items));
        return views;
    }

    /** Aviso inmediato al tocar «Actualizar», antes de que termine la descarga. */
    static void showRefreshing(Context context) {
        patchFooter(context, context.getString(R.string.widget_refreshing));
    }

    /** Conserva los datos anteriores y explica que no hubo conexión. */
    static void showOffline(Context context) {
        boolean hasData = !WidgetStore.posts(context).isEmpty();
        patchFooter(
            context,
            context.getString(
                hasData ? R.string.widget_offline : R.string.widget_offline_empty
            )
        );
    }

    private static void patchFooter(Context context, CharSequence text) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
            new ComponentName(context, ArticleWidgetProvider.class)
        );
        if (ids == null || ids.length == 0) {
            return;
        }
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_articles);
        views.setTextViewText(R.id.widgetFooter, text);
        manager.partiallyUpdateAppWidget(ids, views);
    }

    private static CharSequence footerText(Context context, List<WidgetStore.Item> items) {
        if (items.isEmpty()) {
            return context.getString(R.string.widget_empty);
        }
        long updatedAt = WidgetStore.updatedAt(context);
        if (updatedAt <= 0L) {
            return context.getString(R.string.widget_subtitle);
        }
        String time = DateFormat
            .getTimeInstance(DateFormat.SHORT)
            .format(new Date(updatedAt));
        return context.getString(R.string.widget_updated, time);
    }

    private static PendingIntent articleIntent(Context context, String url, int index) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_ARTICLE_URL, url);
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        return PendingIntent.getActivity(
            context,
            REQUEST_ARTICLE + index,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent appIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        return PendingIntent.getActivity(
            context,
            REQUEST_HEADER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent refreshIntent(Context context) {
        Intent intent = new Intent(context, ArticleWidgetProvider.class);
        intent.setAction(ArticleWidgetProvider.ACTION_REFRESH);
        return PendingIntent.getBroadcast(
            context,
            REQUEST_REFRESH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
