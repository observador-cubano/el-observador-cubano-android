package cu.observadorcubano.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Widget de últimas noticias.
 *
 * Al colocarlo o actualizarse pide el feed en segundo plano. Tocar un título
 * abre el artículo en la app y la flecha de actualizar vuelve a consultarlo.
 */
public class ArticleWidgetProvider extends AppWidgetProvider {

    static final String ACTION_REFRESH = "cu.observadorcubano.app.action.REFRESH_WIDGET";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetRenderer.updateAll(context);
        WidgetRefresh.request(context);
    }

    @Override
    public void onEnabled(Context context) {
        WidgetRenderer.updateAll(context);
        WidgetRefresh.request(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(
        Context context,
        AppWidgetManager appWidgetManager,
        int appWidgetId,
        Bundle newOptions
    ) {
        WidgetRenderer.updateAll(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            if (WidgetRefresh.isOnline(context)) {
                WidgetRenderer.showRefreshing(context);
                WidgetRefresh.request(context);
            } else {
                // Sin red no se promete una descarga: se explica que se
                // mantienen los últimos datos disponibles.
                WidgetRenderer.showOffline(context);
            }
        }
    }
}
