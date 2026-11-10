package cu.observadorcubano.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pruebas de la lectura del feed y del conteo de publicaciones nuevas. */
public class NewPostsFeedTest {

    private static final String FEED = "{"
        + "\"feed\":{\"entry\":["
        + entry("post-3", "  Tercera   nota  ",
            "http://observadorcubano.blogspot.com/2026/11/tercera.html")
        + ","
        + entry("post-2", "Segunda nota",
            "https://observadorcubano.blogspot.com/2026/11/segunda.html")
        + "]}}";

    private static String entry(String id, String title, String url) {
        return "{\"id\":{\"$t\":\"tag:blogger.com,1999:blog-1." + id + "\"},"
            + "\"title\":{\"$t\":\"" + title + "\"},"
            + "\"link\":[{\"rel\":\"replies\",\"href\":\"https://example.com/replies\"},"
            + "{\"rel\":\"alternate\",\"href\":\"" + url + "\"}]}";
    }

    @Test
    public void parseReadsAndNormalizesEntries() throws IOException {
        List<NewPostsFeed.Post> posts = NewPostsFeed.parse(FEED);

        assertEquals(2, posts.size());
        assertEquals("Tercera nota", posts.get(0).title);
        assertEquals(
            "https://observadorcubano.blogspot.com/2026/11/tercera.html",
            posts.get(0).url
        );
        assertTrue(posts.get(0).id.endsWith("post-3"));
    }

    @Test
    public void parseSkipsExternalAndIncompleteEntries() throws IOException {
        String json = "{\"feed\":{\"entry\":["
            + entry("post-1", "Enlace externo", "https://example.com/nota.html")
            + ",{\"id\":{\"$t\":\"post-2\"},\"title\":{\"$t\":\"Sin enlace\"}}"
            + ",{\"title\":{\"$t\":\"Sin id\"},\"link\":[{\"rel\":\"alternate\","
            + "\"href\":\"https://observadorcubano.blogspot.com/2026/11/nota.html\"}]}"
            + ",{\"id\":{\"$t\":\"post-3\"},\"title\":{\"$t\":\"Sin enlace alterno\"}}"
            + "]}}";

        assertTrue(NewPostsFeed.parse(json).isEmpty());
    }

    @Test
    public void parseKeepsPublishedEntriesWithoutTitle() throws IOException {
        String json = "{\"feed\":{\"entry\":["
            + "{\"id\":{\"$t\":\"post-1\"},\"title\":{\"$t\":\"  \"},"
            + "\"link\":[{\"rel\":\"alternate\","
            + "\"href\":\"https://observadorcubano.blogspot.com/2026/11/nota.html\"}]}"
            + "]}}";

        List<NewPostsFeed.Post> posts = NewPostsFeed.parse(json);

        assertEquals(1, posts.size());
        assertEquals("Nuevo artículo", posts.get(0).title);
    }

    @Test
    public void parseReturnsEmptyWithoutFeed() throws IOException {
        assertTrue(NewPostsFeed.parse("{\"version\":\"1.0\"}").isEmpty());
        assertTrue(NewPostsFeed.parse("").isEmpty());
        assertTrue(NewPostsFeed.parse(null).isEmpty());
    }

    @Test
    public void parseRejectsMalformedJson() {
        assertThrows(IOException.class, () -> NewPostsFeed.parse("{no es json}"));
    }

    @Test
    public void countNewCountsPostsBeforeTheKnownOne() {
        List<NewPostsFeed.Post> posts = posts("c", "b", "a");

        assertEquals(0, NewPostsAlerts.countNew("", posts));
        assertEquals(0, NewPostsAlerts.countNew("c", posts));
        assertEquals(2, NewPostsAlerts.countNew("a", posts));
        assertEquals(0, NewPostsAlerts.countNew("a", Collections.emptyList()));
    }

    @Test
    public void countNewTreatsAllAsNewWhenTheKnownOneIsGone() {
        List<NewPostsFeed.Post> posts = posts("d", "c");

        assertEquals(2, NewPostsAlerts.countNew("a", posts));
    }

    private static List<NewPostsFeed.Post> posts(String... ids) {
        return Arrays.stream(ids)
            .map((id) -> new NewPostsFeed.Post(
                id,
                "Nota " + id,
                "https://observadorcubano.blogspot.com/2026/11/" + id + ".html"
            ))
            .toList();
    }
}
