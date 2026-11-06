package simplifiedsimulator.scenario;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Collects waypoints for a new route and formats them in the route file format read
 * by {@link RouteParser}.
 */
public final class RouteRecorder {

    private final List<Vector3f> waypoints = new ArrayList<>();

    public void add(Vector3f point) {
        waypoints.add(new Vector3f(point.x, RouteParser.ROAD_HEIGHT, point.z));
    }

    /** Removes the most recently added waypoint, if any. */
    public void undo() {
        if (!waypoints.isEmpty()) {
            waypoints.remove(waypoints.size() - 1);
        }
    }

    public void clear() {
        waypoints.clear();
    }

    public List<Vector3f> waypoints() {
        return List.copyOf(waypoints);
    }

    /** Formats the waypoints as the contents of a route file. */
    public String format() {
        StringBuilder text = new StringBuilder();
        for (Vector3f point : waypoints) {
            text.append(String.format(Locale.ROOT, "(%s, %s, %s)", point.x, point.y, point.z));
        }
        return text.append(System.lineSeparator()).toString();
    }
}
