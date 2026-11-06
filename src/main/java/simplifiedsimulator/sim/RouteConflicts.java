package simplifiedsimulator.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import simplifiedsimulator.network.Lane;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.Polyline;
import simplifiedsimulator.network.Route;
import simplifiedsimulator.network.Vec2;

/**
 * All places where the routes of a network cross, merge or pass too close to each other for
 * two road users to be there at the same time, found from their geometry. Used by the
 * simulation to keep road users from driving through each other, and by the signal control
 * to work out which signal groups conflict.
 *
 * <p>A conflict zone is a stretch of one route where its path comes closer to the other
 * route than half the width of the widest road users on both, plus a margin. Where two
 * routes join the same lane the zone runs from the point where the paths start to converge
 * up to a little past the joining point; after that the road users simply follow each other.
 */
public final class RouteConflicts {

    private static final double SAMPLE = 0.5;
    /** Extra clearance on straight paths. */
    private static final double MARGIN = 0.3;
    /**
     * In a curve the middle of a vehicle of wheelbase-ish length L cuts the inside by about
     * L² × curvature / 8; these are the lengths used for that on general and bus lanes.
     */
    private static final double GENERAL_TRAFFIC_LENGTH = 7.0;
    /** Paths at less than about 20 degrees to each other run alongside each other. */
    private static final double PARALLEL_SINE = 0.34;
    /** Before a merge, paths converge over at most this distance. */
    private static final double MERGE_APPROACH = 30;
    /** The zone of a merge ends this far past the point where both routes are on the same lane. */
    private static final double MERGE_TAIL = 3.0;

    private final Map<Route, List<ConflictPoint>> byRoute = new HashMap<>();

    public RouteConflicts(Network network) {
        List<Route> routes = network.routes();
        Map<Route, Polyline> paths = new HashMap<>();
        Map<Route, double[]> bounds = new HashMap<>();
        for (Route r : routes) {
            Polyline p = r.path();
            paths.put(r, p);
            bounds.put(r, bounds(p));
            byRoute.put(r, new ArrayList<>());
        }
        for (int i = 0; i < routes.size(); i++) {
            for (int j = i + 1; j < routes.size(); j++) {
                Route a = routes.get(i);
                Route b = routes.get(j);
                if (!isMotor(a) && !isMotor(b)) {
                    continue; // cyclists and pedestrians find their way around each other
                }
                double base = (widestUser(a) + widestUser(b)) / 2 + MARGIN;
                if (boxesApart(bounds.get(a), bounds.get(b), base + 3)) {
                    continue;
                }
                analyse(a, b, paths.get(a), paths.get(b), base);
            }
        }
        for (List<ConflictPoint> list : byRoute.values()) {
            list.sort(Comparator.comparingDouble(ConflictPoint::zoneStart));
        }
    }

    /** Conflict zones along {@code route}, ordered by where they start. */
    public List<ConflictPoint> of(Route route) {
        return Collections.unmodifiableList(byRoute.getOrDefault(route, List.of()));
    }

    /** A stretch of one path that is close to another path, and the matching stretch of the other path. */
    private record Run(double from, double to, double otherFrom, double otherTo, boolean reachesEnd) {
    }

    private void analyse(Route a, Route b, Polyline pa, Polyline pb, double base) {
        double[] merge = mergePoint(a, b);
        double endA = merge == null ? pa.length() : merge[0];
        double endB = merge == null ? pb.length() : merge[1];
        List<Run> runsA = runs(pa, pb, endA, merge != null, base, maxOffTracking(a), maxOffTracking(b));
        List<Run> runsB = runs(pb, pa, endB, merge != null, base, maxOffTracking(b), maxOffTracking(a));
        boolean[] usedB = new boolean[runsB.size()];
        boolean mergeAdded = false;
        for (Run ra : runsA) {
            double a0 = ra.from();
            double a1 = ra.to();
            double b0 = ra.otherFrom();
            double b1 = ra.otherTo();
            boolean reachesMerge = ra.reachesEnd();
            for (int k = 0; k < runsB.size(); k++) {
                Run rb = runsB.get(k);
                if (rb.to() >= b0 - 1 && rb.from() <= b1 + 1) {
                    usedB[k] = true;
                    a0 = Math.min(a0, rb.otherFrom());
                    a1 = Math.max(a1, rb.otherTo());
                    b0 = Math.min(b0, rb.from());
                    b1 = Math.max(b1, rb.to());
                    reachesMerge |= rb.reachesEnd();
                }
            }
            if (merge != null && reachesMerge) {
                // converging into a shared lane: one zone up to just past the joining point
                addZone(a, a0, merge[0] + MERGE_TAIL, b, b0, merge[1] + MERGE_TAIL, ConflictPoint.Kind.MERGE);
                mergeAdded = true;
            } else {
                addZone(a, a0, a1, b, b0, b1, ConflictPoint.Kind.CROSSING);
            }
        }
        for (int k = 0; k < runsB.size(); k++) {
            Run rb = runsB.get(k);
            if (usedB[k]) {
                continue;
            }
            if (merge != null && rb.reachesEnd()) {
                addZone(a, rb.otherFrom(), merge[0] + MERGE_TAIL, b, rb.from(), merge[1] + MERGE_TAIL,
                        ConflictPoint.Kind.MERGE);
                mergeAdded = true;
            } else {
                addZone(a, rb.otherFrom(), rb.otherTo(), b, rb.from(), rb.to(), ConflictPoint.Kind.CROSSING);
            }
        }
        if (merge != null && !mergeAdded) {
            addZone(a, merge[0] - MERGE_TAIL, merge[0] + MERGE_TAIL, b, merge[1] - MERGE_TAIL, merge[1] + MERGE_TAIL,
                    ConflictPoint.Kind.MERGE);
        }
    }

    /**
     * Stretches of path {@code p} (up to {@code end}) where road users on {@code p} and {@code q}
     * could touch: where the paths cross or converge into a merge, closer than the half widths
     * of the widest users plus a margin and the amount long vehicles cut the inside of a curve.
     * Paths that merely run alongside each other (neighbouring lanes, a footpath next to a
     * lane) do not conflict: kerbs and lane markings keep their users apart.
     */
    private static List<Run> runs(Polyline p, Polyline q, double end, boolean merging, double base,
                                  double offP, double offQ) {
        List<Run> out = new ArrayList<>();
        double runStart = Double.NaN;
        double qMin = Double.POSITIVE_INFINITY;
        double qMax = Double.NEGATIVE_INFINITY;
        int samples = (int) Math.ceil(end / SAMPLE);
        for (int k = 0; k <= samples; k++) {
            double s = Math.min(k * SAMPLE, end);
            Vec2 point = p.pointAt(s);
            double sq = q.project(point);
            double sin = Math.abs(p.directionAt(s).cross(q.directionAt(sq)));
            boolean alongside = sin < PARALLEL_SINE && !(merging && end - s < MERGE_APPROACH);
            double threshold = base + offTracking(p, s, offP) + offTracking(q, sq, offQ);
            boolean close = !alongside && q.pointAt(sq).distance(point) < threshold;
            if (close) {
                if (Double.isNaN(runStart)) {
                    runStart = s;
                }
                qMin = Math.min(qMin, sq);
                qMax = Math.max(qMax, sq);
            }
            boolean last = k == samples;
            if (!Double.isNaN(runStart) && (!close || last)) {
                out.add(new Run(runStart, close ? s : s - SAMPLE, qMin, qMax, close && last));
                runStart = Double.NaN;
                qMin = Double.POSITIVE_INFINITY;
                qMax = Double.NEGATIVE_INFINITY;
            }
        }
        return out;
    }

    /** Where two routes join the same lane: positions along a and b, or null if they never do. */
    private static double[] mergePoint(Route a, Route b) {
        for (int i = 1; i < a.lanes().size(); i++) {
            Lane lane = a.lanes().get(i);
            int j = b.lanes().indexOf(lane);
            if (j < 1 || a.lanes().get(i - 1) == b.lanes().get(j - 1)) {
                continue;
            }
            double joined = Math.max(a.entryOffset(i), b.entryOffset(j));
            return new double[] {a.laneStart(i) + joined - a.entryOffset(i), b.laneStart(j) + joined - b.entryOffset(j)};
        }
        return null;
    }

    private void addZone(Route a, double a0, double a1, Route b, double b0, double b1, ConflictPoint.Kind kind) {
        a0 -= SAMPLE;
        a1 += SAMPLE;
        b0 -= SAMPLE;
        b1 += SAMPLE;
        double sa = (a0 + a1) / 2;
        double sb = (b0 + b1) / 2;
        byRoute.get(a).add(new ConflictPoint(a, sa, (a1 - a0) / 2, b, sb, (b1 - b0) / 2, kind));
        byRoute.get(b).add(new ConflictPoint(b, sb, (b1 - b0) / 2, a, sa, (a1 - a0) / 2, kind));
    }

    private static double offTracking(Polyline path, double s, double length) {
        return length == 0 ? 0 : Math.min(1.5, length * length * Math.abs(path.curvatureAt(s)) / 8);
    }

    /** Length used for off-tracking: only motor vehicles are long enough for it to matter. */
    private static double maxOffTracking(Route route) {
        if (route.modes().contains(Mode.CAR)) {
            return GENERAL_TRAFFIC_LENGTH;
        }
        return route.modes().contains(Mode.BUS) ? AgentType.BUS.length() : 0;
    }

    /** Width to keep clear for: a wide van on general lanes, a bus on bus lanes. */
    private static double widestUser(Route route) {
        if (route.modes().contains(Mode.CAR)) {
            return AgentType.VAN.width();
        }
        if (route.modes().contains(Mode.BUS)) {
            return AgentType.BUS.width();
        }
        return route.modes().contains(Mode.BIKE) ? AgentType.BICYCLE.width() : AgentType.PEDESTRIAN.width();
    }

    private static boolean isMotor(Route route) {
        return route.modes().contains(Mode.CAR) || route.modes().contains(Mode.BUS);
    }

    private static double[] bounds(Polyline p) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Vec2 v : p.points()) {
            b[0] = Math.min(b[0], v.x());
            b[1] = Math.min(b[1], v.y());
            b[2] = Math.max(b[2], v.x());
            b[3] = Math.max(b[3], v.y());
        }
        return b;
    }

    private static boolean boxesApart(double[] a, double[] b, double margin) {
        return a[2] + margin < b[0] || b[2] + margin < a[0] || a[3] + margin < b[1] || b[3] + margin < a[1];
    }
}
