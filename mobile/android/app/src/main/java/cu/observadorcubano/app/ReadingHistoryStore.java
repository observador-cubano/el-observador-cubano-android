package cu.observadorcubano.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Historial local de artículos abiertos.
 *
 * Solo se guarda metadata (URL, título, fecha, posición y si se estaba usando
 * el modo lectura). No se guarda el contenido: para eso están los guardados.
 * Nada de esto sale del dispositivo.
 */
final class ReadingHistoryStore {

    private static final String PREFS_NAME = "eoc_reading_history";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 60;

    private final SharedPreferences prefs;

    ReadingHistoryStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    synchronized List<ReadingHistoryEntry> list() {
        // El orden natural del archivo es el más reciente primero.
        return read();
    }

    /**
     * Registra una visita sin alterar la posición ya conocida. Se usa cuando
     * la página termina de cargar.
     */
    synchronized void recordVisit(String url, String title) {
        if (url == null || url.isEmpty()) {
            return;
        }
        List<ReadingHistoryEntry> items = read();
        ReadingHistoryEntry existing = removeByUrl(items, url);
        double progress = existing != null ? existing.progress : 0d;
        boolean reader = existing != null && existing.reader;
        items.add(
            0,
            new ReadingHistoryEntry(url, safeTitle(title), System.currentTimeMillis(), progress, reader)
        );
        trim(items);
        write(items);
    }

    /**
     * Actualiza la posición de lectura. Si el artículo aún no estaba en el
     * historial, lo añade.
     */
    synchronized void saveProgress(
        String url,
        String title,
        double progress,
        boolean reader
    ) {
        if (url == null || url.isEmpty()) {
            return;
        }
        List<ReadingHistoryEntry> items = read();
        ReadingHistoryEntry existing = removeByUrl(items, url);
        String finalTitle = safeTitle(title);
        if (finalTitle.isEmpty() && existing != null) {
            finalTitle = existing.title;
        }
        items.add(
            0,
            new ReadingHistoryEntry(
                url,
                finalTitle,
                System.currentTimeMillis(),
                ReadingProgress.clamp(progress),
                reader
            )
        );
        trim(items);
        write(items);
    }

    synchronized void remove(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        List<ReadingHistoryEntry> items = read();
        if (removeByUrl(items, url) != null) {
            write(items);
        }
    }

    synchronized void clear() {
        prefs.edit().remove(KEY_ITEMS).apply();
    }

    private List<ReadingHistoryEntry> read() {
        List<ReadingHistoryEntry> items = new ArrayList<>();
        String raw = prefs.getString(KEY_ITEMS, "");
        if (raw == null || raw.isEmpty()) {
            return items;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json == null) {
                    continue;
                }
                String url = json.optString("url", "");
                if (url.isEmpty()) {
                    continue;
                }
                items.add(
                    new ReadingHistoryEntry(
                        url,
                        json.optString("title", ""),
                        json.optLong("readAt", 0L),
                        json.optDouble("progress", 0d),
                        json.optBoolean("reader", false)
                    )
                );
            }
        } catch (Exception ignored) {
            // Un archivo dañado no debe impedir abrir el historial.
        }
        return items;
    }

    private void write(List<ReadingHistoryEntry> items) {
        JSONArray array = new JSONArray();
        for (ReadingHistoryEntry entry : items) {
            try {
                JSONObject json = new JSONObject();
                json.put("url", entry.url);
                json.put("title", entry.title);
                json.put("readAt", entry.readAt);
                json.put("progress", entry.progress);
                json.put("reader", entry.reader);
                array.put(json);
            } catch (Exception ignored) {
                // Se omite la entrada inválida y se conserva el resto.
            }
        }
        // apply() evita bloquear la interfaz; el historial no necesita una
        // escritura sincrónica para ser útil.
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    private ReadingHistoryEntry removeByUrl(List<ReadingHistoryEntry> items, String url) {
        for (int i = 0; i < items.size(); i++) {
            if (url.equals(items.get(i).url)) {
                return items.remove(i);
            }
        }
        return null;
    }

    private void trim(List<ReadingHistoryEntry> items) {
        while (items.size() > MAX_ITEMS) {
            items.remove(items.size() - 1);
        }
    }

    private String safeTitle(String title) {
        if (title == null) {
            return "";
        }
        String clean = title.replaceAll("\\s+", " ").trim();
        return clean.length() > 300 ? clean.substring(0, 300) : clean;
    }
}
