package simplifiedsimulator.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A way through the crossing: an approach lane, a path across the crossing and an exit lane.
 *
 * @param id         unique name, for example {@code W2_T}
 * @param modes      who may use the route
 * @param lanes      the lanes in driving order
 * @param group      the signal group that controls the route
 * @param stopLane   the lane that ends at the stop line (normally the first one)
 * @param stopS      position of the stop line along {@code stopLane}
 * @param movement   left, through, right or crossing
 * @param weight     relative share of the approach's traffic when no other information is available
 * @param mergeYield true when the route merges into other traffic and must give way there
 */
public record Route(String id, Set<Mode> modes, List<Lane> lanes, SignalGroup group, Lane stopLane, double stopS,
                    Movement movement, double weight, boolean mergeYield) {

    public Route {
        modes = Set.copyOf(modes);
        lanes = List.copyOf(lanes);
    }

    /** Distance along the whole route at which a lane starts. */
    public double laneStart(int index) {
        double s = 0;
        for (int i = 0; i < index; i++) {
            s += lanes.get(i).length();
        }
        return s;
    }

    /** Position of the stop line measured along the whole route. */
    public double stopLineDistance() {
        return laneStart(lanes.indexOf(stopLane)) + stopS;
    }

    public double length() {
        return laneStart(lanes.size());
    }

    /** The whole route as one polyline. */
    public Polyline path() {
        List<Vec2> pts = new ArrayList<>();
        for (Lane lane : lanes) {
            for (Vec2 p : lane.path().points()) {
                if (pts.isEmpty() || pts.get(pts.size() - 1).distance(p) > 0.05) {
                    pts.add(p);
                }
            }
        }
        return new Polyline(pts);
    }

    /** Approach id ({@code N}, {@code E}, {@code S}, {@code W}) this route starts from. */
    public String approach() {
        return group.approach();
    }

    public Mode primaryMode() {
        return modes.contains(Mode.CAR) ? Mode.CAR : modes.iterator().next();
    }
}
