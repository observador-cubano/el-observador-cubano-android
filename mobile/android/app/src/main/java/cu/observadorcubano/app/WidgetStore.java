package cu.observadorcubano.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Últimas publicaciones mostradas en el widget de la pantalla de inicio.
 *
 * Solo guarda título y enlace; el contenido se descarga del feed en segundo
 * plano y nunca sale del dispositivo.
 */
final class WidgetStore {

    static final int MAX_POSTS = 3;

    private static final String PREFS_NAME = "eoc_widget";
    private static final String KEY_POSTS = "posts";
    private static final String KEY_UPDATED_AT = "updated_at";

    /** Publicación mínima para pintar una fila del widget. */
    static final class Item {

        final String title;
        final String url;

        Item(String title, String url) {
            this.title = title == null ? "" : title;
            this.url = url;
        }
    }

    private WidgetStore() {
        // Clase de utilidades.
    }

    static List<Item> posts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return decode(prefs.getString(KEY_POSTS, ""));
    }

    static long updatedAt(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_UPDATED_AT, 0L);
    }

    /** Guarda las primeras publicaciones; si no hay datos conserva lo anterior. */
    static void save(Context context, List<NewPostsFeed.Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }

        List<Item> items = new ArrayList<>();
        for (NewPostsFeed.Post post : posts) {
            if (items.size() >= MAX_POSTS) {
                break;
            }
            items.add(new Item(post.title, post.url));
        }
        if (items.isEmpty()) {
            return;
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_POSTS, encode(items))
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply();
    }

    static String encode(List<Item> items) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < items.size() && i < MAX_POSTS; i++) {
            JSONObject object = new JSONObject();
            try {
                object.put("title", items.get(i).title);
                object.put("url", items.get(i).url);
            } catch (JSONException ignored) {
                // Los valores son texto; no debería ocurrir.
            }
            array.put(object);
        }
        return array.toString();
    }

    static List<Item> decode(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JSONArray array = new JSONArray(raw);
            List<Item> items = new ArrayList<>();
            for (int i = 0; i < array.length() && items.size() < MAX_POSTS; i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String url = UrlPolicy.normalize(object.optString("url", ""));
                if (url == null || !UrlPolicy.isInternal(url)) {
                    continue;
                }
                items.add(new Item(object.optString("title", "").trim(), url));
            }
            return items;
        } catch (JSONException e) {
            return Collections.emptyList();
        }
    }
}
