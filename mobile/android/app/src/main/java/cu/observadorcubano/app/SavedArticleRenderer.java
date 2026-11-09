package cu.observadorcubano.app;

import java.util.Locale;

/**
 * Construye el documento HTML de un artículo guardado.
 *
 * El contenido proviene de la página remota, por lo que se limpia otra vez
 * antes de escribirlo. El documento final no ejecuta JavaScript y solo puede
 * cargar imágenes locales o datos incrustados.
 */
final class SavedArticleRenderer {

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
        String safeTitle = escapeHtml(trimTo(title, 300));
        if (safeTitle.isEmpty()) {
            safeTitle = "Artículo guardado";
        }
        String safeByline = escapeHtml(trimTo(byline, 300));
        String safeUrl = escapeHtml(trimTo(sourceUrl, 2000));
        String safeDate = escapeHtml(trimTo(savedDateLabel, 120));
        String cleanContent = sanitizeContent(contentHtml);

        StringBuilder footer = new StringBuilder();
        if (!safeDate.isEmpty()) {
            footer.append(footer.length() > 0 ? " · " : "").append("Guardado el ").append(safeDate);
        }
        if (!safeUrl.isEmpty()) {
            footer.append(footer.length() > 0 ? " · " : "").append("Fuente: ").append(safeUrl);
        }

        String theme = dark ? "dark" : "light";
        StringBuilder html = new StringBuilder(cleanContent.length() + 4096);
        html.append("<!doctype html><html lang=\"es\" data-theme=\"")
            .append(theme)
            .append("\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">")
            .append("<meta name=\"referrer\" content=\"no-referrer\">")
            .append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; ")
            .append("img-src 'self' data: file:; style-src 'unsafe-inline';\">")
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
        } else {
            html.append("<p class=\"eoc-byline\">Guardado para leer sin conexión</p>");
        }

        html.append("<article class=\"eoc-content\">")
            .append(cleanContent)
            .append("</article>");

        if (footer.length() > 0) {
            html.append("<footer class=\"eoc-footer\">").append(footer).append("</footer>");
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
