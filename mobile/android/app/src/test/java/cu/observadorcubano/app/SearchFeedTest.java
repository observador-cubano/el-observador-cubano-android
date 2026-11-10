package cu.observadorcubano.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

/** Pruebas del buscador: parseo del feed, fechas y fragmentos de texto. */
public class SearchFeedTest {

    private static final String FEED = "{"
        + "\"feed\":{\"entry\":["
        + "{"
        + "\"id\":{\"$t\":\"tag:blogger.com,1999:blog-1.post-7\"},"
        + "\"title\":{\"$t\":\"  Joven   desaparecido en Morón \"},"
        + "\"published\":{\"$t\":\"2026-05-14T09:30:00-04:00\"},"
        + "\"content\":{\"$t\":\"<div>La familia <b>pide</b> ayuda</div>"
        + "<p>para localizarlo&nbsp;desde el martes &amp; difundir el caso.</p>\"},"
        + "\"link\":[{\"rel\":\"replies\",\"href\":\"https://example.com/r\"},"
        + "{\"rel\":\"alternate\","
        + "\"href\":\"http://observadorcubano.blogspot.com/2026/05/joven.html\"}]"
        + "},"
        + "{"
        + "\"id\":{\"$t\":\"tag:blogger.com,1999:blog-1.post-8\"},"
        + "\"title\":{\"$t\":\"\"},"
        + "\"published\":{\"$t\":\"2026-01-21T00:00:00-05:00\"},"
        + "\"content\":{\"$t\":\"Texto sin título\"},"
        + "\"link\":[{\"rel\":\"alternate\","
        + "\"href\":\"https://observadorcubano.blogspot.com/2026/01/sin-titulo.html\"}]"
        + "},"
        + "{"
        + "\"id\":{\"$t\":\"tag:blogger.com,1999:blog-1.post-9\"},"
        + "\"title\":{\"$t\":\"Fuera del sitio\"},"
        + "\"link\":[{\"rel\":\"alternate\",\"href\":\"https://example.com/nota.html\"}]"
        + "}"
        + "]}}";

    @Test
    public void parseReadsResultsAndNormalizesLinks() throws IOException {
        List<SearchFeed.Result> results = SearchFeed.parse(FEED);

        assertEquals(2, results.size());
        SearchFeed.Result first = results.get(0);
        assertEquals("Joven desaparecido en Morón", first.title);
        assertEquals(
            "https://observadorcubano.blogspot.com/2026/05/joven.html",
            first.url
        );
        assertEquals("La familia pide ayuda para localizarlo desde el martes & difundir el caso.", first.snippet);
        assertTrue(first.publishedAt > 0L);
    }

    @Test
    public void parseKeepsPublishedEntriesWithoutTitle() throws IOException {
        List<SearchFeed.Result> results = SearchFeed.parse(FEED);

        assertEquals("", results.get(1).title);
        assertEquals("Texto sin título", results.get(1).snippet);
    }

    @Test
    public void parseSkipsExternalAndIncompleteEntries() throws IOException {
        String json = "{\"feed\":{\"entry\":["
            + "{\"id\":{\"$t\":\"x\"},\"link\":[{\"rel\":\"alternate\","
            + "\"href\":\"https://example.com/nota.html\"}]},"
            + "{\"id\":{\"$t\":\"y\"},\"title\":{\"$t\":\"Sin enlace\"}},"
            + "{\"title\":{\"$t\":\"Sin id\"},\"link\":[{\"rel\":\"alternate\","
            + "\"href\":\"https://observadorcubano.blogspot.com/2026/01/nota.html\"}]}"
            + "]}}";

        assertTrue(SearchFeed.parse(json).isEmpty());
    }

    @Test
    public void parseReturnsEmptyWithoutEntries() throws IOException {
        assertTrue(SearchFeed.parse("{\"feed\":{}}").isEmpty());
        assertTrue(SearchFeed.parse("").isEmpty());
        assertTrue(SearchFeed.parse(null).isEmpty());
    }

    @Test
    public void parseRejectsMalformedJson() {
        assertThrows(IOException.class, () -> SearchFeed.parse("{no es json}"));
    }

    @Test
    public void feedUrlEncodesTheQuery() {
        assertTrue(SearchFeed.feedUrl("salud mental").endsWith("q=salud+mental"));
        assertTrue(SearchFeed.feedUrl("  Morón ").endsWith("q=Mor%C3%B3n"));
        assertEquals(SearchFeed.FEED_BASE, SearchFeed.feedUrl("   "));
    }

    @Test
    public void publishedDateUsesUtcAndIgnoresTheTime() {
        long withOffset = SearchFeed.parsePublished("2026-10-21T18:00:00-04:00");

        assertEquals(SearchFeed.parsePublished("2026-10-21"), withOffset);
        assertTrue(withOffset > 0L);
        assertTrue(SearchFeed.formatPublished(withOffset).contains("2026"));
        assertEquals(0L, SearchFeed.parsePublished("sin fecha"));
        assertEquals(0L, SearchFeed.parsePublished(null));
        assertEquals("", SearchFeed.formatPublished(0L));
    }

    @Test
    public void snippetStripsHtmlAndDecodesEntities() {
        String html = "<div>Hola <b>mundo</b></div>"
            + "<style>.x{color:red}</style>"
            + "<p>Texto&nbsp;con &amp; entidades &quot;citadas&quot;</p>";

        assertEquals("Hola mundo Texto con & entidades \"citadas\"", SearchFeed.snippet(html));
        assertEquals("", SearchFeed.snippet(null));
    }

    @Test
    public void snippetShortensLongTextWithoutBreakingWords() {
        String text = ("palabra ".repeat(60)).trim();

        String snippet = SearchFeed.snippet(text);

        assertTrue(snippet.length() <= 172);
        assertTrue(snippet.endsWith("…"));
        assertFalse(snippet.contains("  "));
        assertFalse(snippet.substring(0, snippet.length() - 1).endsWith(" "));
    }
}
