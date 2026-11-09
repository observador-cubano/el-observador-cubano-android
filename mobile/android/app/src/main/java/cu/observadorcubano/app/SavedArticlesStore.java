package cu.observadorcubano.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guarda artículos y sus imágenes en el almacenamiento privado de la app.
 *
 * La metadata vive en SharedPreferences y el contenido en archivos locales.
 * La descarga se hace en un hilo de fondo; las consultas de la interfaz solo
 * leen la metadata y nunca esperan a las imágenes.
 */
final class SavedArticlesStore {

    private static final String PREFS_NAME = "eoc_saved_articles";
    private static final String KEY_ITEMS = "items";
    private static final String DIR_NAME = "saved_articles";

    private static final int MAX_IMAGES = 30;
    private static final long MAX_IMAGE_BYTES = 6L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 40L * 1024L * 1024L;

    private static final Pattern IMG_TAG = Pattern.compile("(?is)<img\\b[^>]*>");
    private static final Pattern SRC_ATTR = Pattern.compile(
        "(?i)\\bsrc\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))"
    );
    private static final Pattern EXTRA_ATTR = Pattern.compile(
        "(?i)\\s+(srcset|sizes|data-src|data-original|data-lazy-src|loading)\\s*=\\s*"
            + "(\"[^\"]*\"|'[^']*'|[^\\s>]+)"
    );
    private static final Pattern VALID_ID = Pattern.compile("^[a-f0-9]{16}$");

    private final Context context;
    private final Object metadataLock = new Object();
    private final Object saveLock = new Object();

    SavedArticlesStore(Context context) {
        this.context = context.getApplicationContext();
    }

    List<SavedArticle> list() {
        synchronized (metadataLock) {
            List<SavedArticle> items = readItems();
            Collections.sort(items, new Comparator<SavedArticle>() {
                @Override
                public int compare(SavedArticle left, SavedArticle right) {
                    return Long.compare(right.savedAt, left.savedAt);
                }
            });
            return items;
        }
    }

    int count() {
        synchronized (metadataLock) {
            return readItems().size();
        }
    }

    SavedArticle findByUrl(String url) {
        if (url == null) {
            return null;
        }
        synchronized (metadataLock) {
            for (SavedArticle article : readItems()) {
                if (url.equals(article.url)) {
                    return article;
                }
            }
        }
        return null;
    }

    SavedArticle findById(String id) {
        if (!isValidId(id)) {
            return null;
        }
        synchronized (metadataLock) {
            for (SavedArticle article : readItems()) {
                if (id.equals(article.id)) {
                    return article;
                }
            }
        }
        return null;
    }

    boolean isSaved(String url) {
        return findByUrl(url) != null;
    }

    File folderFor(String id) {
        if (!isValidId(id)) {
            return null;
        }
        return new File(new File(context.getFilesDir(), DIR_NAME), id);
    }

    File contentFileFor(String id) {
        File folder = folderFor(id);
        return folder == null ? null : new File(folder, "content.html");
    }

    String readContent(String id) throws IOException {
        File file = contentFileFor(id);
        if (file == null || !file.isFile()) {
            throw new IOException("Contenido no disponible");
        }
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    /**
     * Descarga el contenido y las imágenes, y actualiza la metadata. Si el
     * mismo artículo ya existía, se reemplaza.
     */
    SavedArticle save(String url, String title, String byline, String contentHtml)
        throws IOException {
        if (!UrlPolicy.isInternal(url)) {
            throw new IOException("URL no permitida");
        }
        String hash = Sha256.hashText(url);
        if (hash == null || hash.length() < 16) {
            throw new IOException("No se pudo identificar el artículo");
        }
        String id = hash.substring(0, 16);

        synchronized (saveLock) {
            File folder = folderFor(id);
            if (folder == null) {
                throw new IOException("Identificador no válido");
            }
            deleteRecursively(folder);
            if (!folder.mkdirs() && !folder.isDirectory()) {
                throw new IOException("No se pudo crear la carpeta del artículo");
            }

            try {
                String safeTitle = title == null ? "" : title.trim();
                String safeByline = byline == null ? "" : byline.trim();
                String rewritten = rewriteImages(contentHtml, url, folder);
                writeText(new File(folder, "content.html"), rewritten);

                SavedArticle article = new SavedArticle(
                    id,
                    url,
                    safeTitle,
                    safeByline,
                    System.currentTimeMillis()
                );
                synchronized (metadataLock) {
                    List<SavedArticle> items = readItems();
                    List<SavedArticle> updated = new ArrayList<>();
                    updated.add(article);
                    for (SavedArticle existing : items) {
                        if (!id.equals(existing.id) && !url.equals(existing.url)) {
                            updated.add(existing);
                        }
                    }
                    writeItems(updated);
                }
                return article;
            } catch (IOException | RuntimeException e) {
                deleteRecursively(folder);
                if (e instanceof IOException) {
                    throw (IOException) e;
                }
                throw new IOException("No se pudo guardar el artículo", e);
            }
        }
    }

    void remove(String id) {
        if (!isValidId(id)) {
            return;
        }
        File folder = folderFor(id);
        if (folder != null) {
            deleteRecursively(folder);
        }
        synchronized (metadataLock) {
            List<SavedArticle> items = readItems();
            List<SavedArticle> updated = new ArrayList<>();
            for (SavedArticle article : items) {
                if (!id.equals(article.id)) {
                    updated.add(article);
                }
            }
            try {
                writeItems(updated);
            } catch (IOException ignored) {
                // El artículo ya no está en el disco; la lista se corrige en la
                // siguiente lectura.
            }
        }
    }

    private String rewriteImages(String contentHtml, String articleUrl, File folder) {
        if (contentHtml == null || contentHtml.isEmpty()) {
            return "";
        }
        Matcher matcher = IMG_TAG.matcher(contentHtml);
        StringBuffer output = new StringBuffer(contentHtml.length() + 1024);
        int[] imageIndex = {0};
        long[] totalBytes = {0L};

        while (matcher.find()) {
            String tag = matcher.group();
            String rewritten = rewriteImageTag(
                tag,
                articleUrl,
                folder,
                imageIndex,
                totalBytes
            );
            matcher.appendReplacement(output, Matcher.quoteReplacement(rewritten));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String rewriteImageTag(
        String tag,
        String articleUrl,
        File folder,
        int[] imageIndex,
        long[] totalBytes
    ) {
        Matcher srcMatcher = SRC_ATTR.matcher(tag);
        if (!srcMatcher.find()) {
            return tag;
        }
        String source = srcMatcher.group(2);
        if (source == null) {
            source = srcMatcher.group(3);
        }
        if (source == null) {
            source = srcMatcher.group(4);
        }
        if (source == null) {
            return tag;
        }
        source = source.trim();

        if (source.startsWith("data:image/")) {
            return tag;
        }
        if (imageIndex[0] >= MAX_IMAGES) {
            return missingImage();
        }

        String absolute = resolveImageUrl(articleUrl, source);
        if (absolute == null) {
            return missingImage();
        }

        int index = imageIndex[0]++;
        File target = new File(folder, "img_" + index + ".img");
        String extension = downloadImage(absolute, target, totalBytes);
        if (extension == null) {
            deleteFile(target);
            return missingImage();
        }

        File finalFile = new File(folder, "img_" + index + "." + extension);
        if (!target.renameTo(finalFile)) {
            deleteFile(target);
            return missingImage();
        }

        String rewrittenTag = removeExtraAttributes(tag);
        Matcher currentSrc = SRC_ATTR.matcher(rewrittenTag);
        if (!currentSrc.find()) {
            return missingImage();
        }
        String replacement = "src=\"" + finalFile.getName() + "\"";
        return rewrittenTag.substring(0, currentSrc.start())
            + replacement
            + rewrittenTag.substring(currentSrc.end());
    }

    private static String removeExtraAttributes(String tag) {
        return EXTRA_ATTR.matcher(tag).replaceAll("");
    }

    private static String missingImage() {
        return "<p class=\"eoc-image-missing\">Imagen no disponible sin conexión</p>";
    }

    private static String resolveImageUrl(String articleUrl, String source) {
        try {
            String value = source.replace(" ", "%20");
            URI resolved = new URI(articleUrl).resolve(value);
            String scheme = resolved.getScheme();
            if (scheme == null || resolved.getHost() == null) {
                return null;
            }
            if ("http".equalsIgnoreCase(scheme)) {
                resolved = new URI(
                    "https",
                    resolved.getRawUserInfo(),
                    resolved.getHost(),
                    resolved.getPort(),
                    resolved.getRawPath(),
                    resolved.getRawQuery(),
                    resolved.getRawFragment()
                );
            } else if (!"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            return resolved.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String downloadImage(String url, File target, long[] totalBytes) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "image/*");
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android) ElObservadorCubano/1.0"
            );

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }

            String contentType = connection.getContentType();
            String extension = extensionFor(contentType, url);
            if (extension == null) {
                return null;
            }

            long length = connection.getContentLengthLong();
            if (length > MAX_IMAGE_BYTES || totalBytes[0] + Math.max(length, 0) > MAX_TOTAL_BYTES) {
                return null;
            }

            long written = 0L;
            try (
                InputStream input = connection.getInputStream();
                OutputStream output = new FileOutputStream(target)
            ) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    written += read;
                    if (written > MAX_IMAGE_BYTES
                        || totalBytes[0] + written > MAX_TOTAL_BYTES) {
                        throw new IOException("Imagen demasiado grande");
                    }
                    output.write(buffer, 0, read);
                }
            } catch (IOException e) {
                deleteFile(target);
                return null;
            }

            if (written <= 0L) {
                deleteFile(target);
                return null;
            }
            totalBytes[0] += written;
            return extension;
        } catch (Exception e) {
            deleteFile(target);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String extensionFor(String contentType, String url) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        int separator = type.indexOf(';');
        if (separator >= 0) {
            type = type.substring(0, separator).trim();
        }
        if (type.isEmpty()) {
            return extensionFromPath(url);
        }
        if (!type.startsWith("image/")) {
            return null;
        }
        if (type.equals("image/jpeg") || type.equals("image/jpg")) {
            return "jpg";
        }
        if (type.equals("image/png")) {
            return "png";
        }
        if (type.equals("image/gif")) {
            return "gif";
        }
        if (type.equals("image/webp")) {
            return "webp";
        }
        if (type.equals("image/bmp")) {
            return "bmp";
        }
        if (type.equals("image/avif")) {
            return "avif";
        }
        // No se guardan SVG remotos: pueden contener recursos externos.
        return null;
    }

    private static String extensionFromPath(String url) {
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int fragment = path.indexOf('#');
        if (fragment >= 0) {
            path = path.substring(0, fragment);
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "jpg";
        }
        if (lower.endsWith(".png")) {
            return "png";
        }
        if (lower.endsWith(".gif")) {
            return "gif";
        }
        if (lower.endsWith(".webp")) {
            return "webp";
        }
        return null;
    }

    private List<SavedArticle> readItems() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_ITEMS, null);
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<SavedArticle> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.optString("id", "");
                String url = item.optString("url", "");
                if (!isValidId(id) || url.isEmpty()) {
                    continue;
                }
                items.add(new SavedArticle(
                    id,
                    url,
                    item.optString("title", ""),
                    item.optString("byline", ""),
                    item.optLong("savedAt", 0L)
                ));
            }
        } catch (Exception ignored) {
            // Si la metadata está dañada se empieza de nuevo.
        }
        return items;
    }

    private void writeItems(List<SavedArticle> items) throws IOException {
        try {
            JSONArray array = new JSONArray();
            for (SavedArticle article : items) {
                JSONObject item = new JSONObject();
                item.put("id", article.id);
                item.put("url", article.url);
                item.put("title", article.title);
                item.put("byline", article.byline);
                item.put("savedAt", article.savedAt);
                array.put(item);
            }
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (!prefs.edit().putString(KEY_ITEMS, array.toString()).commit()) {
                throw new IOException("No se pudo escribir la metadata");
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("No se pudo escribir la metadata", e);
        }
    }

    private static void writeText(File file, String text) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void deleteFile(File file) {
        if (file != null && file.isFile()) {
            // El resultado no es crítico: si falla, se reintenta al borrar la carpeta.
            file.delete();
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static boolean isValidId(String id) {
        return id != null && VALID_ID.matcher(id).matches();
    }
}
