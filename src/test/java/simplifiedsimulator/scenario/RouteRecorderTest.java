package simplifiedsimulator.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteRecorderTest {

    @Test
    void recordedRouteCanBeParsedAgain() {
        RouteRecorder recorder = new RouteRecorder();
        recorder.add(new Vector3f(-15.618567f, 0.25f, 251.05597f));
        recorder.add(new Vector3f(3f, 0.25f, -0.00001f));
        recorder.add(new Vector3f(120.5f, 0.25f, 40f));

        List<Vector3f> parsed = RouteParser.parse(recorder.format());

        assertEquals(recorder.waypoints(), parsed);
        assertTrue(parsed.stream().allMatch(p -> p.y == RouteParser.ROAD_HEIGHT));
    }

    @Test
    void undoAndClear() {
        RouteRecorder recorder = new RouteRecorder();
        recorder.undo();
        recorder.add(new Vector3f(1f, 0f, 1f));
        recorder.add(new Vector3f(2f, 0f, 2f));
        recorder.undo();

        assertEquals(List.of(new Vector3f(1f, RouteParser.ROAD_HEIGHT, 1f)), recorder.waypoints());

        recorder.clear();
        assertTrue(recorder.waypoints().isEmpty());
    }
}
