package cu.observadorcubano.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Pruebas de la posición de lectura usada por el historial. */
public class ReadingProgressTest {

    @Test
    public void clampKeepsValuesInsideRange() {
        assertEquals(0d, ReadingProgress.clamp(-2d), 0.0001d);
        assertEquals(0.42d, ReadingProgress.clamp(0.42d), 0.0001d);
        assertEquals(1d, ReadingProgress.clamp(3d), 0.0001d);
    }

    @Test
    public void clampTreatsNaNAsZero() {
        assertEquals(0d, ReadingProgress.clamp(Double.NaN), 0.0001d);
    }

    @Test
    public void percentRoundsToNearestWholeNumber() {
        assertEquals(0, ReadingProgress.percent(0d));
        assertEquals(42, ReadingProgress.percent(0.42d));
        assertEquals(67, ReadingProgress.percent(0.666d));
        assertEquals(100, ReadingProgress.percent(1d));
    }

    @Test
    public void percentSaturatesOutOfRangeValues() {
        assertEquals(0, ReadingProgress.percent(-1d));
        assertEquals(100, ReadingProgress.percent(4d));
    }
}
