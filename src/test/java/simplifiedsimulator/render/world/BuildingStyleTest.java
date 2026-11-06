package simplifiedsimulator.render.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildingStyleTest {

    @Test
    void sizeAndAgeDecideTheFacade() {
        assertEquals(BuildingStyle.Facade.SHED, BuildingStyle.facade(2.8f, 20, 1990, 1));
        assertEquals(BuildingStyle.Facade.INDUSTRIAL, BuildingStyle.facade(9f, 5000, 2005, 1));
        assertEquals(BuildingStyle.Facade.OFFICE, BuildingStyle.facade(18f, 700, 1970, 1));
        assertEquals(BuildingStyle.Facade.OLD_BRICK, BuildingStyle.facade(9f, 80, 1935, 1));
        assertEquals(BuildingStyle.Facade.RED_BRICK, BuildingStyle.facade(9f, 80, 1985, 1));
    }

    @Test
    void newHousesComeInSeveralFinishes() {
        Set<BuildingStyle.Facade> seen = EnumSet.noneOf(BuildingStyle.Facade.class);
        for (int seed = 0; seed < 200; seed++) {
            seen.add(BuildingStyle.facade(9f, 80, 2010, seed));
        }
        assertTrue(seen.size() >= 3, "variety: " + seen);
    }
}
