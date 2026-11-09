package cu.observadorcubano.app;

/** Metadatos de un artículo guardado en el dispositivo. */
final class SavedArticle {

    final String id;
    final String url;
    final String title;
    final String byline;
    final long savedAt;

    SavedArticle(String id, String url, String title, String byline, long savedAt) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.byline = byline;
        this.savedAt = savedAt;
    }
}
