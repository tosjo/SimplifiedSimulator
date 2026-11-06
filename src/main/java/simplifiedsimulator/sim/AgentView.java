package simplifiedsimulator.sim;

/**
 * Read-only view of one road user, used by the renderer and the statistics display.
 * Positions are in metres in the ground plane (x east, y north).
 */
public interface AgentView {

    /** Unique id, stable for the lifetime of the agent. */
    long id();

    AgentType type();

    /** Stable random number used to pick colours and body styles. */
    int variant();

    /** Position of the middle of the vehicle (or the person). */
    double x();

    double y();

    /** Direction of travel in radians, counter-clockwise from east. */
    double heading();

    /** Speed in m/s. */
    double speed();

    /** Acceleration in m/s². Negative while braking. */
    double acceleration();

    /** True while the brake lights are on. */
    boolean braking();

    Indicator indicator();

    /** Id of the route the agent follows. */
    String routeId();

    /** Seconds spent waiting (speed below 1 m/s) since the agent entered the simulation. */
    double waitingTime();
}
