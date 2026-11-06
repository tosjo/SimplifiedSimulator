package simplifiedsimulator.sim;

import java.util.List;
import simplifiedsimulator.network.BusStop;
import simplifiedsimulator.network.Lane;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.Polyline;
import simplifiedsimulator.network.Route;

/**
 * Pre-computed geometry of one route: the path, where each lane begins, the speed limits
 * and curvature sampled every metre, the conflict points and the bus stop.
 */
public final class RouteGeometry {

    /** Spacing of the speed and curvature samples, in metres. */
    static final double SAMPLE = 1.0;

    private final Route route;
    private final Polyline path;
    private final double length;
    private final double stopLine;
    private final Lane[] lanes;
    private final double[] laneStart;
    private final double[] entry;
    private final double[] curvature;
    private final double[] laneSpeed;
    private final List<ConflictPoint> conflicts;
    private final BusStop busStop;
    private final double busStopS;
    private final double turnStart;
    private final double turnEnd;

    RouteGeometry(Route route, Network network, RouteConflicts conflicts) {
        this.route = route;
        this.path = route.path();
        this.lanes = route.lanes().toArray(Lane[]::new);
        this.laneStart = new double[lanes.length + 1];
        this.entry = new double[lanes.length];
        for (int i = 0; i < lanes.length; i++) {
            entry[i] = route.entryOffset(i);
            laneStart[i + 1] = laneStart[i] + lanes[i].length() - entry[i];
        }
        this.length = laneStart[lanes.length];
        this.stopLine = waitingPosition(route, conflicts.of(route));
        int n = (int) Math.ceil(length / SAMPLE) + 1;
        this.curvature = new double[n];
        this.laneSpeed = new double[n];
        for (int k = 0; k < n; k++) {
            double s = k * SAMPLE;
            curvature[k] = Math.abs(path.curvatureAt(s));
            laneSpeed[k] = lanes[laneIndexAt(s)].speedLimit();
        }
        this.conflicts = conflicts.of(route);
        BusStop found = null;
        double foundS = Double.NaN;
        for (BusStop stop : network.busStops()) {
            int i = route.lanes().indexOf(stop.lane());
            if (i >= 0 && stop.s() >= entry[i]) {
                found = stop;
                foundS = laneStart[i] + stop.s() - entry[i];
            }
        }
        this.busStop = found;
        this.busStopS = foundS;
        // indicators: from 60 m before the stop line until the end of the path across the crossing
        this.turnStart = stopLine - 60;
        int stopIndex = route.lanes().indexOf(route.stopLane());
        this.turnEnd = stopIndex + 1 < lanes.length ? laneStart[stopIndex + 2] : length;
    }

    /**
     * Where road users wait for the light: at the stop line, or a little before it when the
     * mapped stop line lies within reach of crossing traffic (kerb positions in the map data
     * are accurate to about a metre), so nobody waits where others drive past.
     */
    private static double waitingPosition(Route route, List<ConflictPoint> conflicts) {
        double stop = route.stopLineDistance();
        double wait = stop;
        for (ConflictPoint cp : conflicts) {
            if (cp.other().group() != route.group() && cp.zoneStart() < stop && cp.zoneEnd() > stop - 0.5
                    && cp.zoneStart() > stop - 6) {
                wait = Math.min(wait, cp.zoneStart() - 0.3);
            }
        }
        return wait;
    }

    public Route route() {
        return route;
    }

    public Polyline path() {
        return path;
    }

    public double length() {
        return length;
    }

    /** Where road users wait for the light: the stop line, moved back where necessary (see above). */
    public double stopLine() {
        return stopLine;
    }

    public List<ConflictPoint> conflicts() {
        return conflicts;
    }

    public int laneCount() {
        return lanes.length;
    }

    public Lane lane(int index) {
        return lanes[index];
    }

    /** Index of the lane at route position {@code s}. */
    public int laneIndexAt(double s) {
        for (int i = lanes.length - 1; i > 0; i--) {
            if (s >= laneStart[i]) {
                return i;
            }
        }
        return 0;
    }

    /** Where the route begins on lane {@code index}, in the lane's own coordinates. */
    public double entry(int index) {
        return entry[index];
    }

    public double laneStart(int index) {
        return laneStart[index];
    }

    /** Converts a route position on lane {@code index} to a position along that lane. */
    public double toLane(int index, double s) {
        return entry[index] + s - laneStart[index];
    }

    /** Converts a position along lane {@code index} to a route position. */
    public double fromLane(int index, double lanePos) {
        return laneStart[index] + lanePos - entry[index];
    }

    /**
     * Highest speed at {@code s} for a road user with the given speed factor and lateral
     * acceleration limit: the lane's design speed, and the speed at which the curve can be
     * taken.
     */
    public double maxSpeed(double s, double speedFactor, double lateralAccel) {
        int k = sample(s);
        double v = laneSpeed[k] * speedFactor;
        double c = curvature[k];
        if (c > 1e-4) {
            v = Math.min(v, Math.sqrt(lateralAccel / c));
        }
        return v;
    }

    /**
     * The speed a road user should have now so that, braking at {@code decel}, it never
     * exceeds the maximum speed anywhere in the next {@code lookahead} metres.
     */
    public double anticipatedSpeed(double s, double lookahead, double speedFactor, double lateralAccel,
                                   double decel) {
        double v = maxSpeed(s, speedFactor, lateralAccel);
        for (double d = 2; d <= lookahead && s + d < length; d += 2) {
            double ahead = maxSpeed(s + d, speedFactor, lateralAccel);
            v = Math.min(v, Math.sqrt(ahead * ahead + 2 * decel * d));
        }
        return v;
    }

    /**
     * Travel time over the whole route on an empty road with every light green, for a road
     * user starting at {@code startSpeed} (used to compute delays).
     */
    public double freeFlowTime(double startSpeed, double speedFactor, double lateralAccel, double accel,
                               double decel) {
        int n = curvature.length;
        double[] v = new double[n];
        for (int k = 0; k < n; k++) {
            v[k] = maxSpeed(k * SAMPLE, speedFactor, lateralAccel);
        }
        for (int k = n - 2; k >= 0; k--) {
            v[k] = Math.min(v[k], Math.sqrt(v[k + 1] * v[k + 1] + 2 * decel * SAMPLE));
        }
        v[0] = Math.min(v[0], Math.max(startSpeed, 0.5));
        for (int k = 1; k < n; k++) {
            v[k] = Math.min(v[k], Math.sqrt(v[k - 1] * v[k - 1] + 2 * accel * SAMPLE));
        }
        double t = 0;
        for (int k = 1; k < n; k++) {
            double ds = Math.min(SAMPLE, length - (k - 1) * SAMPLE);
            t += ds / Math.max(0.3, (v[k - 1] + v[k]) / 2);
        }
        return t;
    }

    /** The bus stop on this route, or {@code null}. */
    public BusStop busStop() {
        return busStop;
    }

    public double busStopS() {
        return busStopS;
    }

    /** Whether the direction indicator should be on at route position {@code s}. */
    public boolean indicating(double s) {
        return s >= turnStart && s <= turnEnd;
    }

    private int sample(double s) {
        int k = (int) Math.round(s / SAMPLE);
        return Math.max(0, Math.min(curvature.length - 1, k));
    }
}
