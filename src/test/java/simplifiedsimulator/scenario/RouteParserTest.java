package simplifiedsimulator.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jme3.math.Vector3f;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteParserTest {

    @Test
    void parsesWaypointsAtRoadHeight() {
        List<Vector3f> route = RouteParser.parse("(-15.618567, 0.1, 251.05597)(-14.261513, 1.5, 223.41939)\n");

        assertEquals(List.of(
                new Vector3f(-15.618567f, RouteParser.ROAD_HEIGHT, 251.05597f),
                new Vector3f(-14.261513f, RouteParser.ROAD_HEIGHT, 223.41939f)), route);
    }

    @Test
    void acceptsWhitespaceAndScientificNotation() {
        List<Vector3f> route = RouteParser.parse(" ( 1 ,0, 2 )  (3.5,0,-4e-2) ");

        assertEquals(List.of(
                new Vector3f(1f, RouteParser.ROAD_HEIGHT, 2f),
                new Vector3f(3.5f, RouteParser.ROAD_HEIGHT, -0.04f)), route);
    }

    @Test
    void collapsesConsecutiveDuplicates() {
        List<Vector3f> route = RouteParser.parse("(1, 0, 1)(1, 0.1, 1)(2, 0, 2)(2, 0, 2)(1, 0, 1)");

        assertEquals(3, route.size());
        assertEquals(new Vector3f(1f, RouteParser.ROAD_HEIGHT, 1f), route.get(2));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> RouteParser.parse("(1, 0, 1)oops(2, 0, 2)"));
        assertThrows(IllegalArgumentException.class, () -> RouteParser.parse("(1, 0, 1)(2, 0)"));
    }

    @Test
    void rejectsRoutesWithFewerThanTwoPoints() {
        assertThrows(IllegalArgumentException.class, () -> RouteParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> RouteParser.parse("(1, 0, 1)(1, 0, 1)"));
    }
}
