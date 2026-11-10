package cu.observadorcubano.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Búsqueda en el archivo público del blog.
 *
 * Usa el propio feed de Blogger con el parámetro {@code q}, así que no hace
 * falta un servidor intermedio ni un buscador externo. Solo se piden datos de
 * la publicación: título, fecha, enlace y un fragmento de texto.
 */
final class SearchFeed {

    static final String FEED_BASE =
        "https://observadorcubano.blogspot.com/feeds/posts/default"
            + "?alt=json&max-results=15&q=";

    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int SNIPPET_CHARS = 170;

    /** Resultado de búsqueda listo para mostrar. */
    static final class Result {

        final String id;
        final String title;
        final String url;
        final long publishedAt;
        final String snippet;

        Result(String id, String title, String url, long publishedAt, String snippet) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.publishedAt = publishedAt;
            this.snippet = snippet;
        }
    }

    private SearchFeed() {
        // Clase de utilidades.
    }

    static String feedUrl(String query) {
        String value = query == null ? "" : query.trim();
        try {
            return FEED_BASE + URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 siempre está disponible; se evita romper la búsqueda.
            return FEED_BASE + value;
        }
    }

    static List<Result> search(String query) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(feedUrl(query)).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ElObservadorCubano/Android");
            connection.setInstanceFollowRedirects(true);

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("El buscador respondió " + status);
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

    /** Extrae los resultados del JSON del feed; descarta lo que salga del sitio. */
    static List<Result> parse(String json) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JSONObject feed = new JSONObject(json).optJSONObject("feed");
            JSONArray entries = feed != null ? feed.optJSONArray("entry") : null;
            if (entries == null) {
                return Collections.emptyList();
            }

            List<Result> results = new ArrayList<>();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                String id = text(entry.optJSONObject("id"));
                String url = UrlPolicy.normalize(alternateLink(entry.optJSONArray("link")));
                if (id.isEmpty() || url == null || !UrlPolicy.isInternal(url)) {
                    continue;
                }

                String raw = text(entry.optJSONObject("content"));
                if (raw.isEmpty()) {
                    raw = text(entry.optJSONObject("summary"));
                }

                results.add(
                    new Result(
                        id,
                        clean(text(entry.optJSONObject("title"))),
                        url,
                        parsePublished(text(entry.optJSONObject("published"))),
                        snippet(raw)
                    )
                );
            }
            return results;
        } catch (JSONException e) {
            throw new IOException("Respuesta del buscador inválida", e);
        }
    }

    /**
     * Blogger publica la fecha en ISO 8601. Se conserva solo el día y se
     * interpreta en UTC para que el resultado no cambie de fecha según la zona
     * horaria del teléfono.
     */
    static long parsePublished(String value) {
        if (value == null || value.length() < 10) {
            return 0L;
        }
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        parser.setTimeZone(TimeZone.getTimeZone("UTC"));
        parser.setLenient(false);
        try {
            Date date = parser.parse(value.substring(0, 10));
            return date != null ? date.getTime() : 0L;
        } catch (ParseException e) {
            return 0L;
        }
    }

    /** Fecha legible, vacía si el feed no la trajo. */
    static String formatPublished(long timestamp) {
        if (timestamp <= 0L) {
            return "";
        }
        SimpleDateFormat format = new SimpleDateFormat("d MMM yyyy", new Locale("es", "ES"));
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(timestamp));
    }

    /** Texto plano y corto a partir del HTML de la publicación. */
    static String snippet(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String text = html
            .replaceAll("(?s)<script.*?</script>", " ")
            .replaceAll("(?s)<style.*?</style>", " ")
            .replaceAll("(?s)<!--.*?-->", " ")
            .replaceAll("<[^>]+>", " ");

        text = decodeEntities(text).replaceAll("\\s+", " ").trim();
        if (text.length() <= SNIPPET_CHARS) {
            return text;
        }

        int cut = text.lastIndexOf(' ', SNIPPET_CHARS);
        if (cut < SNIPPET_CHARS / 2) {
            cut = SNIPPET_CHARS;
        }
        return text.substring(0, cut).trim() + "…";
    }

    private static String decodeEntities(String value) {
        return value
            .replace("&nbsp;", " ")
            .replace("&#8217;", "'")
            .replace("&#8216;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&");
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
                throw new IOException("Respuesta del buscador demasiado grande");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
