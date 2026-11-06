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

    /**
     * Where the route joins lane {@code index}, measured along that lane. Paths across the
     * crossing often join an exit lane part-way along it, after the point where other routes
     * have already joined; the first lane is always entered at its start.
     */
    public double entryOffset(int index) {
        if (index == 0) {
            return 0;
        }
        return lanes.get(index).path().project(lanes.get(index - 1).path().end());
    }

    /** Distance along the whole route at which a lane starts. */
    public double laneStart(int index) {
        double s = 0;
        for (int i = 0; i < index; i++) {
            s += lanes.get(i).length() - entryOffset(i);
        }
        return s;
    }

    /** Position of the stop line measured along the whole route. */
    public double stopLineDistance() {
        int index = lanes.indexOf(stopLane);
        return laneStart(index) + stopS - entryOffset(index);
    }

    public double length() {
        return laneStart(lanes.size());
    }

    /** The whole route as one polyline. */
    public Polyline path() {
        List<Vec2> pts = new ArrayList<>();
        for (int i = 0; i < lanes.size(); i++) {
            Polyline lanePath = lanes.get(i).path();
            double entry = entryOffset(i);
            List<Vec2> lanePoints = lanePath.points();
            add(pts, lanePath.pointAt(entry));
            double s = 0;
            for (int k = 1; k < lanePoints.size(); k++) {
                s += lanePoints.get(k).distance(lanePoints.get(k - 1));
                if (s > entry) {
                    add(pts, lanePoints.get(k));
                }
            }
        }
        return new Polyline(pts);
    }

    private static void add(List<Vec2> pts, Vec2 p) {
        if (pts.isEmpty() || pts.get(pts.size() - 1).distance(p) > 0.05) {
            pts.add(p);
        }
    }

    /** Approach id ({@code N}, {@code E}, {@code S}, {@code W}) this route starts from. */
    public String approach() {
        return group.approach();
    }

    public Mode primaryMode() {
        return modes.contains(Mode.CAR) ? Mode.CAR : modes.iterator().next();
    }
}
