package cu.observadorcubano.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pruebas del documento local que se muestra sin conexión. */
public class SavedArticleRendererTest {

    @Test
    public void renderEscapesTitleAndByline() {
        String html = SavedArticleRenderer.render(
            "Título <b>con etiquetas</b> & comillas \"dobles\"",
            "Por <script>alert(1)</script>",
            "https://observadorcubano.blogspot.com/2026/01/nota.html?a=1&b=2",
            "9 nov 2026, 10:00",
            "<p>Contenido del artículo.</p>",
            false
        );

        assertTrue(html.contains("Título &lt;b&gt;con etiquetas&lt;/b&gt; &amp; comillas &quot;dobles&quot;"));
        assertTrue(html.contains("Por &lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("a=1&amp;b=2"));
    }

    @Test
    public void renderIncludesOfflineDocumentProtections() {
        String html = SavedArticleRenderer.render(
            "Nota",
            "",
            "https://observadorcubano.blogspot.com/2026/01/nota.html",
            "9 nov 2026",
            "<p>Texto</p>",
            true
        );

        assertTrue(html.contains("default-src 'none'"));
        assertTrue(html.contains("data-theme=\"dark\""));
        assertTrue(html.contains("Guardado el 9 nov 2026"));
        assertTrue(html.contains("Fuente: https://observadorcubano.blogspot.com/2026/01/nota.html"));
        assertTrue(html.contains("Guardado para leer sin conexión"));
    }

    @Test
    public void renderUsesFallbackTitleWhenEmpty() {
        String html = SavedArticleRenderer.render(
            "   ",
            null,
            "",
            "",
            "<p>Texto del artículo</p>",
            false
        );

        assertTrue(html.contains("<title>Artículo guardado</title>"));
        assertTrue(html.contains("data-theme=\"light\""));
    }

    @Test
    public void sanitizeContentRemovesExecutableMarkup() {
        String dirty = "<p onclick=\"steal()\">Hola</p>"
            + "<script>alert(1)</script>"
            + "<style>body{display:none}</style>"
            + "<iframe src=\"https://evil.example\"></iframe>"
            + "<a href=\"javascript:alert(2)\">Enlace</a>"
            + "<img src=\"https://example.com/a.jpg\" srcset=\"x 2x\" onerror=\"boom()\">"
            + "<object data=\"x\"></object>";

        String clean = SavedArticleRenderer.sanitizeContent(dirty);

        assertFalse(clean.toLowerCase().contains("<script"));
        assertFalse(clean.toLowerCase().contains("<style"));
        assertFalse(clean.toLowerCase().contains("<iframe"));
        assertFalse(clean.toLowerCase().contains("<object"));
        assertFalse(clean.contains("onclick"));
        assertFalse(clean.contains("onerror"));
        assertFalse(clean.contains("javascript:"));
        assertFalse(clean.contains("srcset"));
        assertTrue(clean.contains("Hola"));
        assertTrue(clean.contains("Enlace"));
        assertTrue(clean.contains("https://example.com/a.jpg"));
    }

    @Test
    public void sanitizeContentHandlesNull() {
        assertTrue(SavedArticleRenderer.sanitizeContent(null).isEmpty());
    }

    @Test
    public void escapeHtmlHandlesAllSpecialCharacters() {
        String escaped = SavedArticleRenderer.escapeHtml("<&>\"'");
        assertTrue(escaped.equals("&lt;&amp;&gt;&quot;&#39;"));
    }
}
