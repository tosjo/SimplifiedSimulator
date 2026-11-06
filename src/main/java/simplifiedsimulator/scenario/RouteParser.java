package simplifiedsimulator.scenario;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses route files. A route file contains the waypoints of one path over the
 * crossroad as a sequence of {@code (x, y, z)} tuples, e.g.
 *
 * <pre>(-15.6, 1.5, 251.0)(-14.2, 1.5, 223.4)(-13.1, 1.5, 193.1)</pre>
 *
 * The recorded height is ignored: every waypoint is placed at {@link #ROAD_HEIGHT}
 * so vehicles drive just above the road surface.
 */
public final class RouteParser {

    /** Height at which vehicles drive above the floor. */
    public static final float ROAD_HEIGHT = 1.5f;

    private static final String NUMBER = "\\s*(-?\\d+(?:\\.\\d+)?(?:[eE]-?\\d+)?)\\s*";
    private static final Pattern TUPLE = Pattern.compile("\\(" + NUMBER + "," + NUMBER + "," + NUMBER + "\\)");

    private RouteParser() {
    }

    /**
     * Parses all waypoints in {@code text}. Consecutive duplicate waypoints (which the
     * recording tool sometimes produced) are collapsed, as zero-length segments
     * confuse the motion path interpolation.
     *
     * @throws IllegalArgumentException if the text contains no waypoints or has
     *                                  unparseable content between the tuples
     */
    public static List<Vector3f> parse(String text) {
        List<Vector3f> waypoints = new ArrayList<>();
        Matcher matcher = TUPLE.matcher(text);
        int end = 0;

        while (matcher.find()) {
            if (!text.substring(end, matcher.start()).isBlank()) {
                throw new IllegalArgumentException("Unexpected content in route near position " + end);
            }
            Vector3f point = new Vector3f(
                    Float.parseFloat(matcher.group(1)),
                    ROAD_HEIGHT,
                    Float.parseFloat(matcher.group(3)));
            if (waypoints.isEmpty() || !waypoints.get(waypoints.size() - 1).equals(point)) {
                waypoints.add(point);
            }
            end = matcher.end();
        }

        if (!text.substring(end).isBlank()) {
            throw new IllegalArgumentException("Unexpected content in route near position " + end);
        }
        if (waypoints.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two waypoints, found " + waypoints.size());
        }
        return List.copyOf(waypoints);
    }
}
