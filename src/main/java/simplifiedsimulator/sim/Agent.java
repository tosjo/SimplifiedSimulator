package simplifiedsimulator.sim;

import simplifiedsimulator.network.Movement;
import simplifiedsimulator.network.Vec2;

/** A road user moving along a route. Positions refer to the front of the vehicle or person. */
public final class Agent implements AgentView {

    /** What a driver decided when the light turned amber. */
    enum AmberChoice {
        UNDECIDED, STOP, GO
    }

    private final long id;
    private final AgentType type;
    private final int variant;
    private final RouteGeometry geometry;
    private final DriverParameters params;
    private final double spawnTime;
    private final double freeFlowTime;

    double s;
    double v;
    double acc;
    int laneIndex;
    double lanePos;

    double waiting;
    int stops;
    boolean stopped;
    double dwellLeft = -1;
    double dwellTotal;
    boolean busStopServed;
    AmberChoice amberChoice = AmberChoice.UNDECIDED;
    boolean passedStopLine;
    boolean ranRedLight;

    /** Why the road user is slowing down or waiting, for display. */
    public enum Reason {
        /** Driving freely. */
        FREE,
        /** Following the road user ahead or waiting in a queue. */
        FOLLOWING,
        /** Stopping for a red or amber light. */
        SIGNAL,
        /** Giving way to another road user at a crossing or merge. */
        GIVING_WAY,
        /** Waiting before a crossing area to keep it clear. */
        KEEPING_CLEAR,
        /** Stopping at the bus stop. */
        BUS_STOP,
        /** Slowing down for a curve. */
        CURVE
    }

    Reason reason = Reason.FREE;
    /** The road user this one gives way to, when {@link #reason} is {@link Reason#GIVING_WAY}. */
    Agent yieldingTo;

    /** A vehicle merging in ahead that this one follows, or {@code null}. */
    Agent mergeLeader;

    /** Leader found in the current step, used to keep the integration collision-free. */
    Agent leader;
    double leaderGap;

    private double x;
    private double y;
    private double heading;

    Agent(long id, AgentType type, int variant, RouteGeometry geometry, DriverParameters params, double spawnTime,
          double s, double v) {
        this.id = id;
        this.type = type;
        this.variant = variant;
        this.geometry = geometry;
        this.params = params;
        this.spawnTime = spawnTime;
        this.s = s;
        this.v = v;
        this.freeFlowTime = geometry.freeFlowTime(v, params.speedFactor(), params.lateralAccel(), params.maxAccel(),
                params.comfortDecel());
        updatePose();
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public AgentType type() {
        return type;
    }

    @Override
    public int variant() {
        return variant;
    }

    @Override
    public double x() {
        return x;
    }

    @Override
    public double y() {
        return y;
    }

    @Override
    public double heading() {
        return heading;
    }

    @Override
    public double speed() {
        return v;
    }

    @Override
    public double acceleration() {
        return acc;
    }

    @Override
    public boolean braking() {
        return type.isMotorVehicle() && (acc < -0.6 || v < 0.3);
    }

    @Override
    public Indicator indicator() {
        if (!type.isMotorVehicle() || !geometry.indicating(s)) {
            return Indicator.NONE;
        }
        Movement m = geometry.route().movement();
        return m == Movement.LEFT ? Indicator.LEFT : m == Movement.RIGHT ? Indicator.RIGHT : Indicator.NONE;
    }

    @Override
    public String routeId() {
        return geometry.route().id();
    }

    @Override
    public double waitingTime() {
        return waiting;
    }

    public RouteGeometry geometry() {
        return geometry;
    }

    public DriverParameters parameters() {
        return params;
    }

    /** Position of the front along the route, in metres. */
    public double position() {
        return s;
    }

    /** Position of the rear along the route. */
    public double rear() {
        return s - type.length();
    }

    public double length() {
        return type.length();
    }

    public double spawnTime() {
        return spawnTime;
    }

    /** Travel time over the route on an empty road with every light green. */
    public double freeFlowTime() {
        return freeFlowTime;
    }

    public int stops() {
        return stops;
    }

    /** Why the road user is slowing down or waiting at the moment. */
    public Reason reason() {
        return reason;
    }

    /** The road user ahead on the same route, or {@code null}. */
    public Agent leader() {
        return leader;
    }

    /** The road user this one is giving way to, or {@code null}. */
    public Agent yieldingTo() {
        return reason == Reason.GIVING_WAY ? yieldingTo : null;
    }

    /** Recomputes the drawn position: the middle of the body, facing from the rear to the front. */
    void updatePose() {
        double len = type.length();
        Vec2 front = geometry.path().pointAt(s);
        Vec2 back = geometry.path().pointAt(s - len);
        if (len < 1.0 || front.distance(back) < 0.1) {
            Vec2 p = geometry.path().pointAt(s - len / 2);
            x = p.x();
            y = p.y();
            heading = geometry.path().headingAt(s - len / 2);
        } else {
            x = (front.x() + back.x()) / 2;
            y = (front.y() + back.y()) / 2;
            heading = front.minus(back).angle();
        }
    }

    /** Corners of the body outline, for the collision check. */
    Vec2[] corners() {
        Vec2 dir = Vec2.ofAngle(heading);
        Vec2 side = new Vec2(-dir.y(), dir.x());
        Vec2 c = new Vec2(x, y);
        Vec2 hl = dir.times(type.length() / 2);
        Vec2 hw = side.times(type.width() / 2);
        return new Vec2[] {c.plus(hl).plus(hw), c.plus(hl).minus(hw), c.minus(hl).minus(hw), c.minus(hl).plus(hw)};
    }
}
