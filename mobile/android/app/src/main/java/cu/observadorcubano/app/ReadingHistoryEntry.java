package cu.observadorcubano.app;

/**
 * Entrada del historial de lectura.
 *
 * Guarda la posición aproximada para poder ofrecer «continuar leyendo» sin
 * sincronizar nada fuera del dispositivo.
 */
final class ReadingHistoryEntry {

    final String url;
    final String title;
    final long readAt;
    final double progress;
    final boolean reader;

    ReadingHistoryEntry(
        String url,
        String title,
        long readAt,
        double progress,
        boolean reader
    ) {
        this.url = url;
        this.title = title;
        this.readAt = readAt;
        this.progress = ReadingProgress.clamp(progress);
        this.reader = reader;
    }
}
