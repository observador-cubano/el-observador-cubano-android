package cu.observadorcubano.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lectura del feed público de Blogger para los avisos de artículos nuevos.
 *
 * Solo se consulta metadata (identificador, título y enlace). No se descarga
 * ni se guarda el contenido de las publicaciones. Las URLs se validan con
 * {@link UrlPolicy} antes de usarlas.
 */
final class NewPostsFeed {

    static final String FEED_URL =
        "https://observadorcubano.blogspot.com/feeds/posts/default?alt=json&max-results=6";

    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    /** Blogger puede devolver una entrada publicada sin título. */
    private static final String FALLBACK_TITLE = "Nuevo artículo";

    /** Publicación mínima necesaria para comparar y avisar. */
    static final class Post {

        final String id;
        final String title;
        final String url;

        Post(String id, String title, String url) {
            this.id = id;
            this.title = title;
            this.url = url;
        }
    }

    private NewPostsFeed() {
        // Clase de utilidades.
    }

    static List<Post> fetch() throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(FEED_URL).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ElObservadorCubano/Android");
            connection.setInstanceFollowRedirects(true);

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("El feed respondió " + status);
            }

            try (InputStream input = connection.getInputStream()) {
                return parse(readUtf8(input));
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Extrae las publicaciones del JSON de Blogger. Las entradas incompletas o
     * con enlaces fuera del sitio oficial se descartan.
     */
    static List<Post> parse(String json) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JSONObject feed = new JSONObject(json).optJSONObject("feed");
            JSONArray entries = feed != null ? feed.optJSONArray("entry") : null;
            if (entries == null) {
                return Collections.emptyList();
            }

            List<Post> posts = new ArrayList<>();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                String id = text(entry.optJSONObject("id"));
                String title = clean(text(entry.optJSONObject("title")));
                String link = UrlPolicy.normalize(alternateLink(entry.optJSONArray("link")));
                if (id.isEmpty() || link == null || !UrlPolicy.isInternal(link)) {
                    continue;
                }
                if (title.isEmpty()) {
                    title = FALLBACK_TITLE;
                }
                posts.add(new Post(id, title, link));
            }
            return posts;
        } catch (JSONException e) {
            throw new IOException("Respuesta del feed inválida", e);
        }
    }

    private static String text(JSONObject value) {
        return value == null ? "" : value.optString("$t", "").trim();
    }

    private static String alternateLink(JSONArray links) {
        if (links == null) {
            return "";
        }
        for (int i = 0; i < links.length(); i++) {
            JSONObject link = links.optJSONObject(i);
            if (link != null && "alternate".equals(link.optString("rel"))) {
                return link.optString("href", "").trim();
            }
        }
        return "";
    }

    private static String clean(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                throw new IOException("Respuesta del feed demasiado grande");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
