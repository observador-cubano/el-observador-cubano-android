package cu.observadorcubano.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pruebas del almacén del widget: codificación, límite y validación de enlaces. */
public class WidgetStoreTest {

    @Test
    public void encodeAndDecodeKeepTitleAndUrl() {
        List<WidgetStore.Item> items = Arrays.asList(
            new WidgetStore.Item("Primera nota", "https://observadorcubano.blogspot.com/2026/11/a.html"),
            new WidgetStore.Item("Segunda nota", "https://observadorcubano.blogspot.com/2026/11/b.html")
        );

        List<WidgetStore.Item> decoded = WidgetStore.decode(WidgetStore.encode(items));

        assertEquals(2, decoded.size());
        assertEquals("Primera nota", decoded.get(0).title);
        assertEquals("https://observadorcubano.blogspot.com/2026/11/a.html", decoded.get(0).url);
        assertEquals("Segunda nota", decoded.get(1).title);
    }

    @Test
    public void encodeLimitsToThreePosts() {
        List<WidgetStore.Item> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            items.add(
                new WidgetStore.Item(
                    "Nota " + i,
                    "https://observadorcubano.blogspot.com/2026/11/" + i + ".html"
                )
            );
        }

        assertEquals(WidgetStore.MAX_POSTS, WidgetStore.decode(WidgetStore.encode(items)).size());
    }

    @Test
    public void decodeNormalizesLinksAndSkipsExternalOnes() {
        String raw = "["
            + "{\"title\":\"HTTP\",\"url\":\"http://observadorcubano.blogspot.com/2026/11/a.html\"},"
            + "{\"title\":\"Fuera\",\"url\":\"https://example.com/nota.html\"},"
            + "{\"title\":\"Sin URL\"}"
            + "]";

        List<WidgetStore.Item> items = WidgetStore.decode(raw);

        assertEquals(1, items.size());
        assertTrue(items.get(0).url.startsWith("https://observadorcubano.blogspot.com/"));
    }

    @Test
    public void decodeToleratesMissingOrBrokenData() {
        assertTrue(WidgetStore.decode(null).isEmpty());
        assertTrue(WidgetStore.decode("").isEmpty());
        assertTrue(WidgetStore.decode("no es json").isEmpty());
        assertTrue(WidgetStore.decode("[]").isEmpty());
        assertTrue(WidgetStore.decode(WidgetStore.encode(Collections.emptyList())).isEmpty());
    }
}
