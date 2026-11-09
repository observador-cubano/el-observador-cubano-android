package cu.observadorcubano.app;

/** Utilidades puras para la posición de lectura. */
final class ReadingProgress {

    private ReadingProgress() {
        // Clase de utilidades.
    }

    static double clamp(double value) {
        if (Double.isNaN(value) || value < 0d) {
            return 0d;
        }
        return value > 1d ? 1d : value;
    }

    static int percent(double progress) {
        return (int) Math.round(clamp(progress) * 100d);
    }
}
