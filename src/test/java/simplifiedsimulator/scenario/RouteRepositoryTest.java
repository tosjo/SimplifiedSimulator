package simplifiedsimulator.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class RouteRepositoryTest {

    private final RouteRepository routes = new RouteRepository();

    @Test
    void loadsExistingRoute() {
        var route = routes.find(14, 6).orElseThrow();

        assertTrue(route.size() >= 2);
        assertTrue(route.stream().allMatch(p -> p.y == RouteParser.ROAD_HEIGHT));
    }

    @Test
    void cachesRoutes() {
        assertSame(routes.find(14, 6).orElseThrow(), routes.find(14, 6).orElseThrow());
    }

    @Test
    void returnsEmptyForUnknownRoute() {
        assertTrue(routes.find(99, 98).isEmpty());
    }

    /**
     * Every route used by the built-in scenarios should exist. Route 1-4 was never
     * recorded; road users on it are skipped.
     */
    @Test
    void builtInScenariosOnlyUseKnownRoutes() throws Exception {
        Set<String> missing = new TreeSet<>();
        for (String name : new String[] {"small", "medium", "large"}) {
            try (InputStream in = getClass().getResourceAsStream("/scenarios/" + name + ".xml")) {
                for (SpawnEvent event : ScenarioReader.read(in)) {
                    if (routes.find(event.startPoint(), event.endPoint()).isEmpty()) {
                        missing.add(event.startPoint() + "-" + event.endPoint());
                    }
                }
            }
        }
        assertEquals(Set.of("1-4"), missing);
    }
}
