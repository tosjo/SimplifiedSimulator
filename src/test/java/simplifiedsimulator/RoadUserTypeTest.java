package simplifiedsimulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoadUserTypeTest {

    @Test
    void mapsScenarioCodes() {
        for (RoadUserType type : RoadUserType.values()) {
            assertEquals(type, RoadUserType.fromCode(type.code()));
        }
        assertThrows(IllegalArgumentException.class, () -> RoadUserType.fromCode(9));
    }

    @Test
    void pedestriansAreNotSimulated() {
        assertFalse(RoadUserType.PEDESTRIAN.isSimulated());
        assertTrue(RoadUserType.CAR.isSimulated());
    }
}
