package simplifiedsimulator.render.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AtmosphereTest {

    @Test
    void nightAndDayExtremes() {
        Atmosphere night = Atmosphere.forElevation(-30);
        Atmosphere noon = Atmosphere.forElevation(55);
        assertEquals(1f, night.night());
        assertEquals(0f, noon.night());
        assertTrue(night.starBrightness() > 0.9f);
        assertEquals(0f, noon.starBrightness());
        assertTrue(noon.sun().r > night.sun().r * 5, "daylight is much brighter");
    }

    @Test
    void changesGraduallyThroughTwilight() {
        float previous = Atmosphere.forElevation(-20).zenith().b;
        for (double e = -19; e <= 60; e += 1) {
            float b = Atmosphere.forElevation(e).zenith().b;
            assertTrue(Math.abs(b - previous) < 0.1f, "no jump at " + e + " degrees");
            previous = b;
        }
    }
}
