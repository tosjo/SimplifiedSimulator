package simplifiedsimulator.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.logging.Logger;
import simplifiedsimulator.control.ActuatedController;
import simplifiedsimulator.control.ConflictMatrix;
import simplifiedsimulator.control.ControlContext;
import simplifiedsimulator.control.DetectorStates;
import simplifiedsimulator.control.GroupTiming;
import simplifiedsimulator.control.SignalController;
import simplifiedsimulator.control.SignalPlan;
import simplifiedsimulator.demand.TrafficGenerator;
import simplifiedsimulator.network.Detector;
import simplifiedsimulator.network.Lane;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.Route;
import simplifiedsimulator.network.SignalGroup;
import simplifiedsimulator.network.Vec2;
import simplifiedsimulator.stats.Statistics;
import simplifiedsimulator.stats.Summary;
import simplifiedsimulator.stats.TripRecord;

/**
 * The traffic simulation of the crossing.
 *
 * <p>Every step of {@link SimulationOptions#timeStep()} seconds: the signal controller decides,
 * new road users arrive, every road user chooses an acceleration with the Intelligent Driver
 * Model (taking into account the road user ahead, curves, the traffic light, conflicting
 * traffic and the bus stop), everybody moves, and detectors and statistics are updated.
 * The simulation is deterministic: the same options give exactly the same run.
 */
public final class Simulation implements SimulationView {

    private static final Logger LOG = Logger.getLogger(Simulation.class.getName());

    /** Road users slower than this count as waiting. */
    private static final double WAITING_SPEED = 1.0;
    /** Queued road users: slower than this and less than this far before the stop line. */
    private static final double QUEUE_SPEED = 2.0;
    private static final double QUEUE_RANGE = 300;
    /** Deceleration above which a driver no longer tries to give way and simply continues. */
    private static final double COMMITTED_DECEL = 5.0;
    /**
     * Extra time a driver merging from a slip road wants between the vehicle on the main stream
     * and itself (merging drivers accept gaps of about 2.5-3 s in total).
     */
    private static final double CRITICAL_GAP = 2.5;

    private final Network network;
    private final SimulationOptions options;
    private final Map<Route, RouteGeometry> geometry = new LinkedHashMap<>();
    private final ConflictMatrix matrix;
    private final SignalPlan plan;
    private final DetectorStates detectors;
    private final ControlContext context;
    private final TrafficGenerator generator;
    private final SplittableRandom driverRng;
    private Statistics statistics;

    private final List<Agent> agents = new ArrayList<>();
    private final Map<Long, Agent> byId = new HashMap<>();
    private final Map<Lane, List<Agent>> onLane = new HashMap<>();
    private final Map<Route, List<Agent>> onRoute = new HashMap<>();
    private final Map<Route, ArrayDeque<AgentType>> entryQueues = new LinkedHashMap<>();
    private final Map<Lane, List<Detector>> detectorsOnLane = new HashMap<>();
    private final List<SignalGroup[]> conflictingPairs = new ArrayList<>();
    private final Set<String> activeSignalConflicts = new HashSet<>();
    private Set<Long> overlapping = new HashSet<>();
    /** Road users per 10 m grid cell, rebuilt every step. */
    private Map<Long, List<Agent>> grid = new HashMap<>();
    private static final double CELL = 10;

    private SignalController controller;
    private double demandScale;
    private double time;
    private long nextId = 1;
    private long arrivals;
    private double nextSample = 60;
    private double nextQueueUpdate;
    private int redLightRuns;
    private int conflictViolations;
    private int signalConflicts;

    public Simulation(Network network, SimulationOptions options) {
        this.network = network;
        this.options = options;
        this.demandScale = options.demandScale();
        options.demand().validate(network);
        RouteConflicts conflicts = new RouteConflicts(network);
        for (Route r : network.routes()) {
            geometry.put(r, new RouteGeometry(r, network, conflicts));
            onRoute.put(r, new ArrayList<>());
            entryQueues.put(r, new ArrayDeque<>());
        }
        for (Lane lane : network.lanes()) {
            onLane.put(lane, new ArrayList<>());
        }
        for (Detector d : network.detectors()) {
            detectorsOnLane.computeIfAbsent(d.lane(), l -> new ArrayList<>()).add(d);
        }
        this.matrix = new ConflictMatrix(network, conflicts);
        this.plan = new SignalPlan(network, matrix);
        this.detectors = new DetectorStates(network);
        Map<SignalGroup, GroupTiming> timings = new HashMap<>();
        Map<SignalGroup, Double> volumes = new HashMap<>();
        for (SignalGroup g : network.signalGroups()) {
            timings.put(g, GroupTiming.defaultFor(g));
            double v = 0;
            for (Route r : g.routes()) {
                v += options.demand().peakPerHour(r);
            }
            volumes.put(g, v);
            for (SignalGroup other : matrix.conflictsOf(g)) {
                if (g.id().compareTo(other.id()) < 0) {
                    conflictingPairs.add(new SignalGroup[] {g, other});
                }
            }
        }
        this.context = new ControlContext(network, matrix, plan, detectors, timings, volumes);
        SplittableRandom root = new SplittableRandom(options.seed());
        this.generator = new TrafficGenerator(network, options.demand(), root.split());
        this.driverRng = root.split();
        this.statistics = new Statistics(network);
        setController(new ActuatedController());
    }

    /** A simulation of the bundled crossing with default options. */
    public static Simulation createDefault() {
        return new Simulation(Network.loadDefault(), SimulationOptions.defaults());
    }

    // ---------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------

    @Override
    public Network network() {
        return network;
    }

    @Override
    public double time() {
        return time;
    }

    @Override
    public double timeOfDay() {
        return (options.startTimeOfDay() + time) % 86400;
    }

    @Override
    public Collection<Agent> agents() {
        return Collections.unmodifiableList(agents);
    }

    @Override
    public SignalState signalState(SignalGroup group) {
        return plan.state(group);
    }

    public SimulationOptions options() {
        return options;
    }

    /** The road user with the given id, or {@code null} when it has left. */
    public Agent agent(long id) {
        return byId.get(id);
    }

    public SignalController controller() {
        return controller;
    }

    /**
     * Replaces the signal controller. All greens end first, so the new controller starts
     * from a safe state.
     */
    public void setController(SignalController next) {
        if (controller != null) {
            controller.close();
            plan.endAllGreens();
        }
        controller = next;
        controller.attach(context);
    }

    public SignalPlan signalPlan() {
        return plan;
    }

    public ConflictMatrix conflictMatrix() {
        return matrix;
    }

    public DetectorStates detectors() {
        return detectors;
    }

    public ControlContext controlContext() {
        return context;
    }

    public Statistics statistics() {
        return statistics;
    }

    public double demandScale() {
        return demandScale;
    }

    /** Changes the amount of traffic (1 = as in the demand file). */
    public void setDemandScale(double scale) {
        if (scale < 0) {
            throw new IllegalArgumentException("Demand scale must not be negative");
        }
        demandScale = scale;
    }

    /** Road users waiting to enter the network on {@code route} because its first lane is full. */
    public int entryQueue(Route route) {
        return entryQueues.get(route).size();
    }

    public int totalEntryQueue() {
        int n = 0;
        for (ArrayDeque<AgentType> q : entryQueues.values()) {
            n += q.size();
        }
        return n;
    }

    public RouteGeometry geometry(Route route) {
        return geometry.get(route);
    }

    /** Road users generated so far, including those still waiting to enter the network. */
    public long arrivals() {
        return arrivals;
    }

    /** Road users that crossed a stop line while their light was red. */
    public int redLightRuns() {
        return redLightRuns;
    }

    /**
     * Times two road users overlapped where their routes cross or merge, or on the same lane.
     * A correct simulation keeps this at 0.
     */
    public int conflictViolations() {
        return conflictViolations;
    }

    /** Times two conflicting signal groups were green or amber at the same time. */
    public int signalConflicts() {
        return signalConflicts;
    }

    /**
     * Starts the statistics and safety counters afresh, for example after a warm-up period
     * in which the network filled with traffic.
     */
    public void resetStatistics() {
        statistics = new Statistics(network);
        redLightRuns = 0;
        conflictViolations = 0;
        signalConflicts = 0;
        nextSample = time + 60;
    }

    /** Overall results so far. */
    public Summary summary() {
        return statistics.summary(totalEntryQueue(), redLightRuns, conflictViolations, signalConflicts);
    }

    /** Runs the simulation for {@code seconds} simulated seconds. */
    public void run(double seconds) {
        int steps = (int) Math.round(seconds / options.timeStep());
        for (int i = 0; i < steps; i++) {
            step();
        }
    }

    /** Advances the simulation by one time step. */
    public void step() {
        double dt = options.timeStep();
        plan.advance(time);
        controller.update(dt);
        checkSignals();
        spawn(dt);
        for (Agent a : agents) {
            a.acc = decide(a);
        }
        move(dt);
        afterMove(dt);
        time += dt;
    }

    // ---------------------------------------------------------------------------------------
    // Arrivals
    // ---------------------------------------------------------------------------------------

    private void spawn(double dt) {
        generator.generate(timeOfDay(), dt, demandScale, (route, type) -> {
            entryQueues.get(route).addLast(type);
            arrivals++;
        });
        for (Map.Entry<Route, ArrayDeque<AgentType>> e : entryQueues.entrySet()) {
            ArrayDeque<AgentType> queue = e.getValue();
            if (!queue.isEmpty() && tryInsert(e.getKey(), queue.peekFirst())) {
                queue.removeFirst();
            }
        }
    }

    private boolean tryInsert(Route route, AgentType type) {
        RouteGeometry g = geometry.get(route);
        DriverParameters p = DriverParameters.sample(type, driverRng);
        double front = type.length();
        double gap = Double.POSITIVE_INFINITY;
        double leaderSpeed = 0;
        for (Agent other : onLane.get(g.lane(0))) {
            double otherRear = other.lanePos - other.length();
            if (otherRear - front < gap) {
                gap = otherRear - front;
                leaderSpeed = other.v;
            }
        }
        if (gap < p.minGap() + 1.0) {
            return false;
        }
        double v = g.maxSpeed(front, p.speedFactor(), p.lateralAccel());
        if (!Double.isInfinite(gap)) {
            // enter at a speed that keeps a safe distance to the road user ahead
            v = Math.min(v, Math.max(leaderSpeed, (gap - p.minGap()) / p.timeHeadway()));
        }
        Agent a = new Agent(nextId++, type, driverRng.nextInt() & 0x7fffffff, g, p, time, front, v);
        a.laneIndex = 0;
        a.lanePos = front;
        agents.add(a);
        byId.put(a.id(), a);
        onLane.get(g.lane(0)).add(a);
        onRoute.get(route).add(a);
        return true;
    }

    // ---------------------------------------------------------------------------------------
    // Driving decisions
    // ---------------------------------------------------------------------------------------

    private double decide(Agent a) {
        RouteGeometry g = a.geometry();
        DriverParameters p = a.parameters();
        double v = a.v;
        double brakingDistance = v * v / (2 * p.comfortDecel());
        double lookahead = Math.min(120, brakingDistance + 20);
        double v0 = Math.max(0.3, g.anticipatedSpeed(a.s, lookahead, p.speedFactor(), p.lateralAccel(),
                p.comfortDecel()));
        double desired = g.maxSpeed(a.s, p.speedFactor(), p.lateralAccel());
        double acc = p.accelerate(v, v0, Double.POSITIVE_INFINITY, 0);
        a.reason = v0 < desired - 0.5 ? Agent.Reason.CURVE : Agent.Reason.FREE;

        findLeader(a, lookahead + 40);
        if (a.leader != null) {
            acc = pick(a, acc, p.accelerate(v, v0, a.leaderGap, v - a.leader.v), Agent.Reason.FOLLOWING);
        }
        if (!a.type().isMotorVehicle()) {
            acc = pick(a, acc, followOnSharedPath(a, v0), Agent.Reason.FOLLOWING);
        }
        acc = pick(a, acc, followMergingVehicles(a, v0), Agent.Reason.FOLLOWING);
        acc = pick(a, acc, signalResponse(a, v0), Agent.Reason.SIGNAL);
        acc = pick(a, acc, busStopResponse(a, v0), Agent.Reason.BUS_STOP);
        Agent.Reason before = a.reason;
        double conflict = conflictResponse(a, v0, lookahead + 20);
        if (conflict < acc) {
            acc = conflict;
        } else {
            a.reason = before;
        }
        return acc;
    }

    /**
     * Cycle paths and footpaths of different routes overlap where they share a stretch of
     * path. Cyclists and pedestrians follow whoever is just ahead of them in the same
     * direction, whatever route that person takes.
     */
    private double followOnSharedPath(Agent a, double v0) {
        Vec2 dir = Vec2.ofAngle(a.heading());
        Vec2 pos = new Vec2(a.x(), a.y());
        double best = Double.POSITIVE_INFINITY;
        double bestSpeed = 0;
        int cx = (int) Math.floor(a.x() / CELL);
        int cy = (int) Math.floor(a.y() / CELL);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (Agent b : grid.getOrDefault(cellKey(cx + dx, cy + dy), List.of())) {
                    if (b == a || b.type().isMotorVehicle() || b.routeId().equals(a.routeId())
                            || Math.cos(b.heading() - a.heading()) < 0.8) {
                        continue;
                    }
                    Vec2 d = new Vec2(b.x(), b.y()).minus(pos);
                    double along = d.dot(dir);
                    double lateral = Math.abs(dir.cross(d));
                    if (along <= 0 || along > 12 || lateral > (a.type().width() + b.type().width()) / 2 + 0.2) {
                        continue;
                    }
                    double gap = along - (a.length() + b.length()) / 2;
                    if (gap < best) {
                        best = gap;
                        bestSpeed = b.v * Math.cos(b.heading() - a.heading());
                    }
                }
            }
        }
        return Double.isInfinite(best) ? Double.POSITIVE_INFINITY
                : a.parameters().accelerate(a.v, v0, Math.max(0.05, best), a.v - bestSpeed);
    }

    /**
     * Where two routes merge, a vehicle that is already merging in ahead is followed as if it
     * were on the same lane, even though it has not reached the shared lane yet. Whoever is
     * closer to the point where the lanes join counts as ahead.
     */
    private double followMergingVehicles(Agent a, double v0) {
        double acc = Double.POSITIVE_INFINITY;
        a.mergeLeader = null;
        for (ConflictPoint cp : a.geometry().conflicts()) {
            if (cp.kind() != ConflictPoint.Kind.MERGE || cp.zoneEnd() < a.rear() || cp.zoneStart() - a.s > 80) {
                continue;
            }
            double mine = cp.zoneEnd() - a.s;
            for (Agent b : onRoute.get(cp.other())) {
                if (b.s < cp.otherZoneStart() || b.rear() > cp.otherZoneEnd()) {
                    continue;
                }
                double theirs = cp.otherZoneEnd() - b.s;
                if (theirs > mine || theirs == mine && b.id() > a.id()) {
                    continue; // this vehicle is ahead of b
                }
                // the paths still converge here, so keep extra room, more for long vehicles
                double gap = mine - theirs - b.length() - 1.0 - 0.2 * Math.max(a.length(), b.length());
                double follow = a.parameters().accelerate(a.v, v0, Math.max(0.05, gap), a.v - b.v);
                if (follow < acc) {
                    acc = follow;
                    a.mergeLeader = b;
                }
            }
        }
        return acc;
    }

    /** Keeps the lower of two accelerations and remembers why. */
    private static double pick(Agent a, double current, double candidate, Agent.Reason reason) {
        if (candidate < current) {
            a.reason = reason;
            return candidate;
        }
        return current;
    }

    /** IDM response to a standing obstacle {@code distance} ahead, stopping {@code standOff} before it. */
    private static double stopFor(Agent a, double v0, double distance, double standOff) {
        DriverParameters p = a.parameters();
        double gap = distance - standOff + p.minGap();
        return p.accelerate(a.v, v0, Math.max(0.05, gap), a.v);
    }

    private static double standOff(Agent a) {
        return switch (a.type()) {
            case PEDESTRIAN -> 0.2;
            case BICYCLE -> 0.4;
            default -> 1.0;
        };
    }

    private double signalResponse(Agent a, double v0) {
        if (a.passedStopLine) {
            return Double.POSITIVE_INFINITY;
        }
        RouteGeometry g = a.geometry();
        double distance = g.stopLine() - a.s;
        SignalState state = plan.state(g.route().group());
        switch (state) {
            case GREEN -> {
                a.amberChoice = Agent.AmberChoice.UNDECIDED;
                return Double.POSITIVE_INFINITY;
            }
            case AMBER -> {
                if (a.amberChoice == Agent.AmberChoice.UNDECIDED) {
                    // Dutch drivers stop for amber when they can do so comfortably
                    double needed = a.v * a.v / (2 * Math.max(0.1, distance - standOff(a)));
                    a.amberChoice = needed <= a.parameters().amberDecel() || a.v < 1
                            ? Agent.AmberChoice.STOP : Agent.AmberChoice.GO;
                }
                return a.amberChoice == Agent.AmberChoice.STOP ? stopFor(a, v0, distance, standOff(a))
                        : Double.POSITIVE_INFINITY;
            }
            default -> {
                if (a.amberChoice == Agent.AmberChoice.GO) {
                    return Double.POSITIVE_INFINITY;
                }
                return stopFor(a, v0, distance, standOff(a));
            }
        }
    }

    private double busStopResponse(Agent a, double v0) {
        RouteGeometry g = a.geometry();
        if (g.busStop() == null || a.busStopServed || a.type() != AgentType.BUS) {
            return Double.POSITIVE_INFINITY;
        }
        double distance = g.busStopS() - a.s;
        if (distance < -2) {
            return Double.POSITIVE_INFINITY;
        }
        return stopFor(a, v0, distance, 0);
    }

    /**
     * Gives way at conflict zones where another road user has priority (see
     * {@link #priorityOver}). Before entering the crossing it also keeps the crossing clear:
     * nobody drives past the stop line unless there is room to get through the conflict zones
     * ahead, as Dutch traffic law requires. Once on the crossing, road users carry on and only
     * stop for somebody who is actually in their way.
     */
    private double conflictResponse(Agent a, double v0, double lookahead) {
        List<ConflictPoint> zones = a.geometry().conflicts();
        // where this road user will have to stop anyway: behind a slow leader or before a zone it must give way at
        double stopAhead = a.leader != null && a.leader.v < 3 ? a.s + a.leaderGap : Double.POSITIVE_INFINITY;
        ConflictPoint yieldZone = null;
        for (ConflictPoint cp : zones) {
            double distance = cp.zoneStart() - a.s;
            if (cp.zoneEnd() < a.rear() || distance < 0) {
                continue;
            }
            if (distance > lookahead || cp.zoneStart() >= stopAhead) {
                break;
            }
            Agent other = priorityOver(a, cp, distance, committed(a, distance));
            if (other != null) {
                stopAhead = cp.zoneStart();
                a.yieldingTo = other;
                yieldZone = cp;
                break;
            }
        }
        if (Double.isInfinite(stopAhead)) {
            return Double.POSITIVE_INFINITY;
        }
        double stopLine = a.geometry().stopLine();
        boolean entering = !a.passedStopLine;
        for (ConflictPoint cp : zones) {
            double distance = cp.zoneStart() - a.s;
            if (cp.zoneEnd() < a.rear() || distance < 0) {
                continue;
            }
            if (cp.zoneStart() > stopAhead) {
                break; // zones are ordered by their start
            }
            // zones of streams that conflict with our green are protected by the lights: they stay red until we are through
            boolean protectedByLights = cp != yieldZone && matrix.conflicts(a.geometry().route().group(), cp.other().group())
                    && plan.state(a.geometry().route().group()) == SignalState.GREEN;
            boolean blocked = cp == yieldZone || !protectedByLights && cp.zoneEnd() + a.length() + 0.5 > stopAhead;
            if (!blocked || (committed(a, distance) && cp != yieldZone)) {
                continue;
            }
            // a merge lies beyond the crossing: drive up to it and wait there for a gap
            boolean waitAtZone = cp.kind() == ConflictPoint.Kind.MERGE
                    || !(entering && cp.zoneStart() > stopLine && !committed(a, stopLine - a.s));
            if (cp == yieldZone && waitAtZone) {
                a.reason = Agent.Reason.GIVING_WAY;
                return stopFor(a, v0, distance, standOff(a) * 0.8);
            }
            if (entering) {
                // wait at the stop line rather than somewhere on the crossing
                double at = cp.zoneStart() > stopLine ? stopLine : cp.zoneStart();
                double d = at - a.s;
                if (!committed(a, d)) {
                    a.reason = cp == yieldZone ? Agent.Reason.GIVING_WAY : Agent.Reason.KEEPING_CLEAR;
                    return stopFor(a, v0, d, standOff(a));
                }
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    /** True when the road user is too close to stop comfortably before {@code distance}. */
    private static boolean committed(Agent a, double distance) {
        return a.v * a.v / (2 * Math.max(0.1, distance)) > COMMITTED_DECEL;
    }

    /**
     * The road user that has priority over {@code a} at a conflict zone, or {@code null}.
     * Somebody already in the zone goes first (unless it is waiting for {@code a} or following
     * it into a merge); somebody
     * already past its stop line goes before somebody who still has to cross its own; a slip
     * road gives way to the main stream; otherwise whoever gets there first goes first. A road
     * user that is too close to stop comfortably ({@code committed}) only stops for somebody
     * who is physically in the zone.
     */
    private Agent priorityOver(Agent a, ConflictPoint cp, double distance, boolean committed) {
        Route mine = cp.route();
        Route theirs = cp.other();
        double maxSpeed = Math.max(a.v, a.geometry().maxSpeed(cp.s(), a.parameters().speedFactor(),
                a.parameters().lateralAccel()));
        double arrive = timeToCover(distance, a.v, a.parameters().maxAccel(), maxSpeed);
        double clear = timeToCover(cp.zoneEnd() + a.length() - a.s, a.v, a.parameters().maxAccel(), maxSpeed);
        boolean merge = cp.kind() == ConflictPoint.Kind.MERGE;
        double myProgress = a.s - cp.zoneEnd();
        for (Agent b : onRoute.get(theirs)) {
            if (b.rear() > cp.otherZoneEnd()) {
                continue; // has passed
            }
            if (b.s > cp.otherZoneStart()) {
                if (b.reason == Agent.Reason.GIVING_WAY && b.yieldingTo == a || b.mergeLeader == a) {
                    continue; // waiting for us, or following us into the merge
                }
                if (!b.passedStopLine && !willProceed(b)) {
                    continue; // waiting at its own red light, at the edge of the zone
                }
                return b; // in the conflict zone now
            }
            if (merge && b.s - cp.otherZoneEnd() >= myProgress) {
                continue; // nearer the merge than us: if it goes first we follow it
            }
            double bDistance = cp.otherZoneStart() - b.s;
            boolean waitingInQueue = b.v < 0.5 && b.acc < 0.2 && bDistance > 8;
            if (committed || !willProceed(b) || waitingInQueue || bDistance > 120) {
                continue; // not coming: held by its light, or standing still in a queue further back
            }
            if (a.passedStopLine && !b.passedStopLine && cp.s() > a.geometry().stopLine()
                    && cp.otherS() > b.geometry().stopLine()) {
                continue; // already on the crossing: the other one waits
            }
            double bMax = Math.max(b.v, b.geometry().maxSpeed(cp.otherS(), b.parameters().speedFactor(),
                    b.parameters().lateralAccel()));
            double bArrive = timeToCover(bDistance, b.v, b.parameters().maxAccel(), bMax);
            if (!a.passedStopLine && b.passedStopLine) {
                if (bArrive < clear + 0.5) {
                    return b;
                }
            } else if (mine.mergeYield() && !theirs.mergeYield()) {
                if (bArrive < clear + CRITICAL_GAP) {
                    return b;
                }
            } else if (theirs.mergeYield() && !mine.mergeYield()) {
                continue;
            } else {
                boolean theyAreFirst = bArrive < arrive || (bArrive == arrive && b.id() < a.id());
                if (theyAreFirst && bArrive < clear + 0.5) {
                    return b;
                }
            }
        }
        return null;
    }

    /** True when the road user is past its stop line or is allowed to drive on. */
    private boolean willProceed(Agent b) {
        if (b.passedStopLine) {
            return true;
        }
        SignalState state = plan.state(b.geometry().route().group());
        return state == SignalState.GREEN || state == SignalState.AMBER && b.amberChoice == Agent.AmberChoice.GO;
    }

    /** Time to cover {@code d} metres starting at speed {@code v}, accelerating at {@code a} up to {@code vMax}. */
    static double timeToCover(double d, double v, double a, double vMax) {
        if (d <= 0) {
            return 0;
        }
        vMax = Math.max(vMax, 0.5);
        if (v >= vMax) {
            return d / v;
        }
        double tAccel = (vMax - v) / a;
        double dAccel = v * tAccel + 0.5 * a * tAccel * tAccel;
        if (d <= dAccel) {
            return (-v + Math.sqrt(v * v + 2 * a * d)) / a;
        }
        return tAccel + (d - dAccel) / vMax;
    }

    /** Finds the nearest road user ahead along the route, across lane boundaries. */
    private void findLeader(Agent a, double maxDistance) {
        RouteGeometry g = a.geometry();
        a.leader = null;
        a.leaderGap = Double.POSITIVE_INFINITY;
        double travelled = 0;
        for (int j = a.laneIndex; j < g.laneCount(); j++) {
            Lane lane = g.lane(j);
            double from = j == a.laneIndex ? a.lanePos : g.entry(j);
            Agent best = null;
            double bestPos = Double.POSITIVE_INFINITY;
            for (Agent other : onLane.get(lane)) {
                if (other == a) {
                    continue;
                }
                boolean ahead = j == a.laneIndex ? other.lanePos > from || other.lanePos == from && other.id() < a.id()
                        : other.lanePos >= from;
                if (ahead && other.lanePos < bestPos) {
                    best = other;
                    bestPos = other.lanePos;
                }
            }
            if (best != null) {
                a.leader = best;
                a.leaderGap = travelled + bestPos - from - best.length();
                return;
            }
            travelled += lane.length() - from;
            if (travelled > maxDistance) {
                return;
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Movement
    // ---------------------------------------------------------------------------------------

    private void move(double dt) {
        double[] ds = new double[agents.size()];
        Map<Agent, Integer> index = new HashMap<>();
        for (int i = 0; i < agents.size(); i++) {
            Agent a = agents.get(i);
            index.put(a, i);
            double vNew = a.v + a.acc * dt;
            if (vNew < 0) {
                ds[i] = a.acc < 0 ? -a.v * a.v / (2 * a.acc) : 0;
                vNew = 0;
            } else {
                ds[i] = a.v * dt + 0.5 * a.acc * dt * dt;
            }
            if (a.dwellLeft >= 0) {
                ds[i] = 0;
                vNew = 0;
            }
            a.v = vNew;
        }
        // never move into the road user ahead, whatever the model says
        for (int i = 0; i < agents.size(); i++) {
            Agent a = agents.get(i);
            if (a.leader != null) {
                Integer li = index.get(a.leader);
                double leaderMove = li == null ? 0 : ds[li];
                double newGap = a.leaderGap + leaderMove - ds[i];
                if (newGap < 0.2) {
                    ds[i] = Math.max(0, a.leaderGap + leaderMove - 0.2);
                    a.v = Math.min(a.v, a.leader.v);
                }
            }
        }
        for (int i = 0; i < agents.size(); i++) {
            agents.get(i).s += ds[i];
        }
    }

    private void afterMove(double dt) {
        List<Agent> finished = new ArrayList<>();
        for (List<Agent> list : onLane.values()) {
            list.clear();
        }
        for (Agent a : agents) {
            RouteGeometry g = a.geometry();
            updateStopLine(a);
            updateBusStop(a, dt);
            if (a.v < WAITING_SPEED && a.dwellLeft < 0) {
                a.waiting += dt;
            }
            if (a.v < 0.3 && !a.stopped) {
                a.stopped = true;
                a.stops++;
            } else if (a.v > 2.0) {
                a.stopped = false;
            }
            if (a.s >= g.length()) {
                finished.add(a);
                continue;
            }
            a.laneIndex = g.laneIndexAt(a.s);
            a.lanePos = g.toLane(a.laneIndex, a.s);
            onLane.get(g.lane(a.laneIndex)).add(a);
            a.updatePose();
        }
        for (Agent a : finished) {
            finish(a);
        }
        updateDetectors();
        checkOverlaps();
        if (time >= nextQueueUpdate) {
            nextQueueUpdate = time + 1;
            updateQueues();
        }
        if (time + dt >= nextSample) {
            nextSample += 60;
            Map<String, Integer> perApproach = new HashMap<>();
            for (Route r : network.routes()) {
                if (r.primaryMode() == Mode.CAR) {
                    perApproach.merge(r.approach(), queued(r), Integer::sum);
                }
            }
            statistics.sample(time + dt, timeOfDay(), agents.size(), totalEntryQueue(), perApproach);
        }
    }

    private void updateStopLine(Agent a) {
        if (a.passedStopLine || a.s < a.geometry().stopLine()) {
            return;
        }
        a.passedStopLine = true;
        if (plan.state(a.geometry().route().group()) == SignalState.RED) {
            a.ranRedLight = true;
            redLightRuns++;
        }
    }

    private void updateBusStop(Agent a, double dt) {
        RouteGeometry g = a.geometry();
        if (g.busStop() == null || a.busStopServed || a.type() != AgentType.BUS) {
            return;
        }
        if (a.dwellLeft < 0 && a.v < 0.2 && Math.abs(g.busStopS() - a.s) < 2.0) {
            // passengers get on and off: a stop of 10-30 s
            a.dwellLeft = Math.max(8, g.busStop().dwell() * (0.6 + 0.8 * driverRng.nextDouble()));
            a.dwellTotal = a.dwellLeft;
        } else if (a.dwellLeft >= 0) {
            a.dwellLeft -= dt;
            if (a.dwellLeft < 0) {
                a.busStopServed = true;
            }
        }
    }

    private void finish(Agent a) {
        agents.remove(a);
        byId.remove(a.id());
        Route r = a.geometry().route();
        onRoute.get(r).remove(a);
        double travel = time + options.timeStep() - a.spawnTime();
        double delay = Math.max(0, travel - a.freeFlowTime() - a.dwellTotal);
        statistics.record(new TripRecord(r.id(), r.approach(), r.movement(), r.primaryMode(), a.type(), a.spawnTime(),
                time + options.timeStep(), delay, a.stops, a.waiting));
    }

    private int queued(Route r) {
        RouteGeometry g = geometry.get(r);
        int n = entryQueues.get(r).size();
        for (Agent a : onRoute.get(r)) {
            if (!a.passedStopLine && a.v < QUEUE_SPEED && g.stopLine() - a.s < QUEUE_RANGE) {
                n++;
            }
        }
        return n;
    }

    private void updateQueues() {
        Map<String, Integer> perRoute = new HashMap<>();
        int longest = 0;
        for (Route r : network.routes()) {
            int q = queued(r);
            perRoute.put(r.id(), q);
            if (r.primaryMode() == Mode.CAR || r.primaryMode() == Mode.BUS) {
                longest = Math.max(longest, q);
            }
        }
        statistics.updateQueues(perRoute, longest);
    }

    // ---------------------------------------------------------------------------------------
    // Detectors and safety checks
    // ---------------------------------------------------------------------------------------

    private void updateDetectors() {
        Set<Detector> occupied = new HashSet<>();
        for (Agent a : agents) {
            RouteGeometry g = a.geometry();
            double rear = a.rear();
            for (int j = a.laneIndex; j >= 0; j--) {
                List<Detector> list = detectorsOnLane.get(g.lane(j));
                double hi = j == a.laneIndex ? a.lanePos : g.lane(j).length();
                double lo = Math.max(g.entry(j), g.toLane(j, rear));
                if (list != null && hi > lo) {
                    for (Detector d : list) {
                        if (hi >= d.from() && lo <= d.to()) {
                            occupied.add(d);
                        }
                    }
                }
                if (rear >= g.laneStart(j)) {
                    break;
                }
            }
        }
        detectors.update(time, occupied);
    }

    /** Counts road users that run into each other; with correct behaviour this never happens. */
    private void checkOverlaps() {
        grid = new HashMap<>();
        double cell = CELL;
        for (Agent a : agents) {
            long key = cellKey((int) Math.floor(a.x() / cell), (int) Math.floor(a.y() / cell));
            grid.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }
        Set<Long> now = new HashSet<>();
        for (Agent a : agents) {
            int cx = (int) Math.floor(a.x() / cell);
            int cy = (int) Math.floor(a.y() / cell);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    List<Agent> list = grid.get(cellKey(cx + dx, cy + dy));
                    if (list == null) {
                        continue;
                    }
                    for (Agent b : list) {
                        if (b.id() <= a.id() || !a.type().isMotorVehicle() && !b.type().isMotorVehicle()) {
                            continue; // cyclists and pedestrians squeeze past each other
                        }
                        double reach = (a.length() + b.length()) / 2 + 1;
                        if (Math.hypot(a.x() - b.x(), a.y() - b.y()) > reach) {
                            continue;
                        }
                        if (overlap(a.corners(), b.corners()) && atConflictZone(a, b)) {
                            long pair = a.id() * 1_000_003L + b.id();
                            now.add(pair);
                            if (!overlapping.contains(pair)) {
                                conflictViolations++;
                                LOG.warning(() -> String.format(java.util.Locale.ROOT,
                                        "Overlap at t=%.1f: %s %d on %s (s=%.1f, v=%.1f) and %s %d on %s (s=%.1f, v=%.1f)",
                                        time, a.type(), a.id(), a.routeId(), a.s, a.v, b.type(), b.id(), b.routeId(),
                                        b.s, b.v));
                            }
                        }
                    }
                }
            }
        }
        overlapping = now;
    }

    /** True when both road users are in the zone where their routes cross or merge. */
    private static boolean atConflictZone(Agent a, Agent b) {
        Route other = b.geometry().route();
        for (ConflictPoint cp : a.geometry().conflicts()) {
            if (cp.other() == other && a.s > cp.zoneStart() && a.rear() < cp.zoneEnd()
                    && b.s > cp.otherZoneStart() && b.rear() < cp.otherZoneEnd()) {
                return true;
            }
        }
        // two vehicles on the same lanes must never overlap either
        return a.type().isMotorVehicle() && b.type().isMotorVehicle()
                && a.geometry().lane(a.laneIndex) == b.geometry().lane(b.laneIndex);
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    /** Separating axis test for two convex quadrilaterals, shrunk slightly to ignore touching. */
    static boolean overlap(Vec2[] p, Vec2[] q) {
        Vec2[][] shapes = {shrink(p), shrink(q)};
        for (Vec2[] shape : shapes) {
            for (int i = 0; i < shape.length; i++) {
                Vec2 edge = shape[(i + 1) % shape.length].minus(shape[i]);
                Vec2 axis = new Vec2(-edge.y(), edge.x());
                double minP = Double.MAX_VALUE;
                double maxP = -Double.MAX_VALUE;
                for (Vec2 v : shapes[0]) {
                    double d = v.dot(axis);
                    minP = Math.min(minP, d);
                    maxP = Math.max(maxP, d);
                }
                double minQ = Double.MAX_VALUE;
                double maxQ = -Double.MAX_VALUE;
                for (Vec2 v : shapes[1]) {
                    double d = v.dot(axis);
                    minQ = Math.min(minQ, d);
                    maxQ = Math.max(maxQ, d);
                }
                if (maxP < minQ || maxQ < minP) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Vec2[] shrink(Vec2[] shape) {
        Vec2 c = shape[0].plus(shape[1]).plus(shape[2]).plus(shape[3]).times(0.25);
        Vec2[] out = new Vec2[4];
        for (int i = 0; i < 4; i++) {
            Vec2 d = shape[i].minus(c);
            double len = d.length();
            out[i] = c.plus(d.times(Math.max(0, len - 0.15) / Math.max(len, 1e-9)));
        }
        return out;
    }

    /** Counts moments where conflicting groups show green or amber together. */
    private void checkSignals() {
        for (SignalGroup[] pair : conflictingPairs) {
            String key = pair[0].id() + "/" + pair[1].id();
            boolean both = plan.state(pair[0]) != SignalState.RED && plan.state(pair[1]) != SignalState.RED;
            if (both && activeSignalConflicts.add(key)) {
                signalConflicts++;
            } else if (!both) {
                activeSignalConflicts.remove(key);
            }
        }
    }
}
