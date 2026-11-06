package simplifiedsimulator.render.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SunPositionTest {

    // solar noon in Leeuwarden is about 11:37 UTC: 13:37 in summer, 12:37 in winter
    private static final double SUMMER_NOON = 13 * 3600 + 37 * 60;
    private static final double WINTER_NOON = 12 * 3600 + 37 * 60;

    @Test
    void highestSunAtTheSolsticesMatchesTheLatitude() {
        SunPosition june = SunPosition.at(LocalDate.of(2026, 6, 21), SUMMER_NOON);
        assertEquals(90 - SunPosition.LATITUDE + 23.44, june.elevation(), 0.5);
        assertEquals(180, june.azimuth(), 3);
        SunPosition december = SunPosition.at(LocalDate.of(2026, 12, 21), WINTER_NOON);
        assertEquals(90 - SunPosition.LATITUDE - 23.44, december.elevation(), 0.5);
    }

    @Test
    void sunRisesInTheEastAndSetsInTheWest() {
        LocalDate june = LocalDate.of(2026, 6, 21);
        assertTrue(SunPosition.at(june, 3 * 3600).elevation() < 0, "night at 03:00");
        SunPosition morning = SunPosition.at(june, 7 * 3600);
        assertTrue(morning.elevation() > 0 && morning.azimuth() < 120, "morning sun in the east: " + morning);
        SunPosition evening = SunPosition.at(june, 21 * 3600);
        assertTrue(evening.elevation() > 0 && evening.azimuth() > 270, "evening sun in the north-west: " + evening);
        assertTrue(SunPosition.at(june, 23 * 3600).elevation() < 0, "night at 23:00");
    }

    @Test
    void directionPointsAtTheSunInJmeCoordinates() {
        Vector3f east = new SunPosition(0, 90).direction();
        assertEquals(1, east.x, 1e-5);
        assertEquals(0, east.y, 1e-5);
        Vector3f south = new SunPosition(30, 180).direction();
        assertTrue(south.z > 0, "south is +Z");
        assertEquals(0.5, south.y, 1e-5);
    }
}
