package cu.observadorcubano.app;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Construye el documento HTML de un artículo guardado o del lector nativo.
 *
 * El contenido proviene de la página remota, por lo que se limpia otra vez
 * antes de escribirlo. El documento final no ejecuta JavaScript y solo puede
 * cargar las imágenes permitidas por su política de contenido.
 */
final class SavedArticleRenderer {

    private static final Pattern IMG_TAG = Pattern.compile("(?is)<img\\b[^>]*>");

    private SavedArticleRenderer() {
        // Clase de utilidades.
    }

    static String render(
        String title,
        String byline,
        String sourceUrl,
        String savedDateLabel,
        String contentHtml,
        boolean dark
    ) {
        String safeUrl = escapeHtml(trimTo(sourceUrl, 2000));
        String safeDate = escapeHtml(trimTo(savedDateLabel, 120));

        StringBuilder footer = new StringBuilder();
        if (!safeDate.isEmpty()) {
            footer.append("Guardado el ").append(safeDate);
        }
        if (!safeUrl.isEmpty()) {
            footer.append(footer.length() > 0 ? " · " : "").append("Fuente: ").append(safeUrl);
        }

        return buildDocument(
            title,
            byline,
            "Artículo guardado",
            contentHtml,
            dark,
            footer.toString(),
            "Guardado para leer sin conexión",
            "img-src 'self' data: file:",
            ""
        );
    }

    /**
     * Documento del lector nativo para un artículo en línea. Las imágenes solo
     * pueden llegar por HTTPS y se omiten por completo cuando el ahorro de
     * datos está activo.
     */
    static String renderOnline(
        String title,
        String byline,
        String sourceUrl,
        String contentHtml,
        boolean dark,
        boolean allowImages
    ) {
        String safeUrl = escapeHtml(trimTo(sourceUrl, 2000));
        String footer = safeUrl.isEmpty() ? "" : "Fuente: " + safeUrl;

        String cleanContent = sanitizeContent(contentHtml);
        boolean hadImages = IMG_TAG.matcher(cleanContent).find();
        if (!allowImages) {
            cleanContent = IMG_TAG.matcher(cleanContent).replaceAll("");
        }
        String notice = hadImages && !allowImages
            ? "<p class=\"eoc-image-note\">Las imágenes no se cargan porque el ahorro de datos está activo.</p>"
            : "";

        return buildDocument(
            title,
            byline,
            "Artículo",
            cleanContent,
            dark,
            footer,
            "",
            allowImages ? "img-src https: data:" : "img-src data:",
            notice
        );
    }

    private static String buildDocument(
        String title,
        String byline,
        String fallbackTitle,
        String contentHtml,
        boolean dark,
        String footerText,
        String bylineFallback,
        String imageSources,
        String notice
    ) {
        String safeTitle = escapeHtml(trimTo(title, 300));
        if (safeTitle.isEmpty()) {
            safeTitle = escapeHtml(trimTo(fallbackTitle, 300));
        }
        String safeByline = escapeHtml(trimTo(byline, 300));
        String cleanContent = sanitizeContent(contentHtml);
        String theme = dark ? "dark" : "light";

        StringBuilder html = new StringBuilder(cleanContent.length() + 4096);
        html.append("<!doctype html><html lang=\"es\" data-theme=\"")
            .append(theme)
            .append("\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">")
            .append("<meta name=\"referrer\" content=\"no-referrer\">")
            .append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; ")
            .append(imageSources)
            .append("; style-src 'unsafe-inline';\">")
            .append("<title>")
            .append(safeTitle)
            .append("</title><style>")
            .append(ReaderStyles.savedCss())
            .append("</style></head><body><main class=\"eoc-page\">")
            .append("<p class=\"eoc-kicker\">El Observador Cubano</p>")
            .append("<h1 class=\"eoc-title\">")
            .append(safeTitle)
            .append("</h1>");

        if (!safeByline.isEmpty()) {
            html.append("<p class=\"eoc-byline\">").append(safeByline).append("</p>");
        } else if (!bylineFallback.isEmpty()) {
            html.append("<p class=\"eoc-byline\">")
                .append(escapeHtml(bylineFallback))
                .append("</p>");
        }

        if (!notice.isEmpty()) {
            html.append(notice);
        }

        html.append("<article class=\"eoc-content\">")
            .append(cleanContent)
            .append("</article>");

        if (!footerText.isEmpty()) {
            html.append("<footer class=\"eoc-footer\">").append(footerText).append("</footer>");
        }

        html.append("</main></body></html>");
        return html.toString();
    }

    /**
     * Limpieza defensiva del HTML extraído. El WebView de lectura no ejecuta
     * JavaScript, pero se eliminan igualmente scripts, estilos incrustados,
     * manejadores de eventos, iframes y esquemas javascript:.
     */
    static String sanitizeContent(String html) {
        if (html == null) {
            return "";
        }
        String clean = html;
        clean = clean.replaceAll("(?is)<script\\b[^>]*>.*?</script\\s*>", "");
        clean = clean.replaceAll("(?is)<style\\b[^>]*>.*?</style\\s*>", "");
        clean = clean.replaceAll("(?is)<iframe\\b[^>]*>.*?</iframe\\s*>", "");
        clean = clean.replaceAll("(?is)<(object|embed|applet|link|meta|base)\\b[^>]*>", "");
        clean = clean.replaceAll(
            "(?i)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)",
            ""
        );
        clean = clean.replaceAll(
            "(?i)\\s+(href|src)\\s*=\\s*(\"|')\\s*javascript:[^\"']*(\"|')",
            ""
        );
        clean = clean.replaceAll(
            "(?i)\\s+(srcset|sizes)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)",
            ""
        );
        clean = clean.replaceAll(
            "(?i)\\s+style\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)",
            ""
        );
        return clean.trim();
    }

    static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&#39;");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    private static String trimTo(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength).trim();
    }

    /** Formato estable para la fecha que se muestra en el pie del guardado. */
    static String formatSavedDate(long timestamp) {
        return java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM,
            java.text.DateFormat.SHORT,
            Locale.getDefault()
        ).format(new java.util.Date(timestamp));
    }
}
