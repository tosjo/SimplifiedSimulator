package simplifiedsimulator.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Movement;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.Route;
import simplifiedsimulator.network.SignalGroup;
import simplifiedsimulator.sim.ConflictPoint;
import simplifiedsimulator.sim.RouteConflicts;

/**
 * Which signal groups conflict, and the intergreen times between them, worked out from the
 * geometry of the crossing.
 *
 * <p>Two groups conflict when the paths of their routes cross, merge or pass too close to
 * each other after the stop lines.
 * Merges where one route has to give way (slip roads, the westbound bus lane) are left to the
 * drivers, as in reality. The intergreen time from group A to group B is the time from the
 * end of A's green until B may turn green. It follows the usual Dutch method: the last road
 * user of A, crossing the stop line at the end of the amber, must have cleared the conflict
 * point before the first road user of B can reach it:
 *
 * <pre>  intergreen = amber(A) + (clearing distance + vehicle length) / clearing speed
 *                         - entering distance / entering speed</pre>
 *
 * and never less than A's amber time.
 */
public final class ConflictMatrix {

    /** Amber for motor traffic at 50 km/h and for cyclists in the Netherlands. */
    public static final double AMBER = 3.0;

    private final Map<SignalGroup, Map<SignalGroup, Double>> intergreen = new LinkedHashMap<>();
    private final Map<SignalGroup, List<SignalGroup>> conflicts = new HashMap<>();

    public ConflictMatrix(Network network, RouteConflicts routeConflicts) {
        for (SignalGroup g : network.signalGroups()) {
            intergreen.put(g, new LinkedHashMap<>());
            conflicts.put(g, new ArrayList<>());
        }
        for (Route a : network.routes()) {
            double stopA = a.stopLineDistance();
            for (ConflictPoint p : routeConflicts.of(a)) {
                Route b = p.other();
                if (a.group() == b.group() || !controlled(a, p.zoneStart(), p.zoneEnd())
                        || !controlled(b, p.otherZoneStart(), p.otherZoneEnd())) {
                    continue;
                }
                if (p.kind() == ConflictPoint.Kind.MERGE && (a.mergeYield() || b.mergeYield())) {
                    continue;
                }
                // the last road user has to clear the zone, or the crossing if the zone runs on beyond it
                double clearing = Math.min(p.zoneEnd(), crossingEnd(a)) - stopA + clearingLength(a);
                double entering = Math.max(0, p.otherS() - b.stopLineDistance() - p.otherHalfLength());
                double time = amber(a.group()) + clearing / clearingSpeed(a) - entering / enteringSpeed(b);
                time = Math.max(amber(a.group()), Math.ceil(time * 10) / 10);
                intergreen.get(a.group()).merge(b.group(), time, Math::max);
            }
        }
        // a conflict is a conflict in both directions, even if only one direction needs time
        for (SignalGroup a : intergreen.keySet()) {
            for (SignalGroup b : List.copyOf(intergreen.get(a).keySet())) {
                intergreen.get(b).putIfAbsent(a, amber(b));
            }
        }
        for (Map.Entry<SignalGroup, Map<SignalGroup, Double>> e : intergreen.entrySet()) {
            conflicts.get(e.getKey()).addAll(e.getValue().keySet());
        }
    }

    public boolean conflicts(SignalGroup a, SignalGroup b) {
        return intergreen.get(a).containsKey(b);
    }

    /** Groups that may never be green at the same time as {@code group}. */
    public List<SignalGroup> conflictsOf(SignalGroup group) {
        return Collections.unmodifiableList(conflicts.get(group));
    }

    /**
     * Seconds from the end of {@code from}'s green until {@code to} may turn green, or 0
     * when they do not conflict.
     */
    public double intergreen(SignalGroup from, SignalGroup to) {
        return intergreen.get(from).getOrDefault(to, 0.0);
    }

    /**
     * True when a zone lies in the controlled area of a route: after its stop line and before
     * the end of its path across the crossing. Paths that meet further on (for example two
     * cycle paths beyond the crossing) are left to the road users, like any other junction.
     */
    private static boolean controlled(Route r, double zoneStart, double zoneEnd) {
        return zoneEnd >= r.stopLineDistance() && zoneStart <= crossingEnd(r);
    }

    /**
     * End of the controlled area of a route: the end of its path across the crossing, plus a
     * little for motor traffic, which may merge just after it on the exit lane.
     */
    private static double crossingEnd(Route r) {
        int stopIndex = r.lanes().indexOf(r.stopLane());
        double end = stopIndex + 2 <= r.lanes().size() ? r.laneStart(stopIndex + 2) : r.length();
        return end + (r.primaryMode() == Mode.CAR || r.primaryMode() == Mode.BUS ? 15 : 0.5);
    }

    /** Amber time of a group; pedestrians get none, their green turns straight to red. */
    public double amber(SignalGroup group) {
        return group.mode() == Mode.FOOT ? 0 : AMBER;
    }

    /** Length of the road user that has to clear the crossing (standard Dutch design values). */
    private static double clearingLength(Route r) {
        return switch (r.primaryMode()) {
            case CAR -> 6.0;
            case BUS -> 12.0;
            case BIKE -> 2.0;
            case FOOT -> 0.0;
        };
    }

    /** Speed of the last road user leaving the crossing. */
    private static double clearingSpeed(Route r) {
        return switch (r.primaryMode()) {
            case CAR -> r.movement() == Movement.THROUGH ? 10.0 : 7.0;
            case BUS -> 8.0;
            case BIKE -> 4.0;
            case FOOT -> 1.2;
        };
    }

    /** Speed of the first road user entering; deliberately high, which errs on the safe side. */
    private static double enteringSpeed(Route r) {
        return switch (r.primaryMode()) {
            case CAR, BUS -> 40 / 3.6;
            case BIKE -> 5.0;
            case FOOT -> 1.5;
        };
    }
}
