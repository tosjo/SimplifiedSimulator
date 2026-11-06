package simplifiedsimulator.render.agents;

/**
 * Where an agent is drawn: position in the ground plane (x east, y north) and heading
 * (radians counter-clockwise from east).
 */
public record Pose(double x, double y, double heading) {

    /**
     * Interpolates between two poses; {@code t = 0} gives {@code from}, {@code t = 1} gives
     * {@code to}. The heading turns the short way round.
     */
    public static Pose lerp(Pose from, Pose to, double t) {
        double dh = Math.atan2(Math.sin(to.heading - from.heading), Math.cos(to.heading - from.heading));
        return new Pose(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, from.heading + dh * t);
    }

    public double distance(Pose other) {
        return Math.hypot(other.x - x, other.y - y);
    }
}
