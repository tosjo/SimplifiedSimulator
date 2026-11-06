package simplifiedsimulator.sim;

import java.util.random.RandomGenerator;

/**
 * Behaviour of one driver, cyclist or pedestrian, for the Intelligent Driver Model (IDM).
 *
 * @param speedFactor    multiplies the lane's design speed to give the desired speed
 * @param maxAccel       IDM maximum acceleration a (m/s²)
 * @param comfortDecel   IDM comfortable deceleration b (m/s²)
 * @param timeHeadway    IDM desired time gap T (s)
 * @param minGap         IDM standstill distance s0 (m)
 * @param lateralAccel   largest sideways acceleration accepted in curves (m/s²)
 * @param amberDecel     deceleration this driver accepts to stop for an amber light (m/s²)
 */
public record DriverParameters(double speedFactor, double maxAccel, double comfortDecel, double timeHeadway,
                               double minGap, double lateralAccel, double amberDecel) {

    /** Hardest braking the model ever uses (an emergency stop on dry asphalt). */
    public static final double EMERGENCY_DECEL = 7.0;

    /**
     * Draws parameters for a new road user. The means follow common IDM calibrations for
     * urban traffic (Treiber &amp; Kesting, "Traffic Flow Dynamics", 2013); the spread gives
     * every driver a slightly different character.
     */
    public static DriverParameters sample(AgentType type, RandomGenerator rng) {
        return switch (type) {
            // Dutch drivers on a 50 km/h road mostly drive 45-55 km/h.
            case CAR -> new DriverParameters(normal(rng, 1.02, 0.06, 0.88, 1.15), normal(rng, 1.6, 0.25, 1.1, 2.2),
                    normal(rng, 2.4, 0.3, 1.8, 3.2), normal(rng, 1.2, 0.2, 0.9, 1.7), normal(rng, 2.0, 0.3, 1.5, 2.8),
                    2.2, normal(rng, 3.2, 0.5, 2.3, 4.2));
            case VAN -> new DriverParameters(normal(rng, 1.0, 0.05, 0.88, 1.1), normal(rng, 1.3, 0.2, 0.9, 1.8),
                    normal(rng, 2.2, 0.3, 1.7, 3.0), normal(rng, 1.3, 0.2, 1.0, 1.8), 2.2, 2.0,
                    normal(rng, 3.0, 0.4, 2.3, 3.8));
            // Heavy vehicles accelerate slowly and keep longer gaps.
            case TRUCK -> new DriverParameters(normal(rng, 0.95, 0.04, 0.85, 1.02), normal(rng, 0.8, 0.12, 0.6, 1.1),
                    normal(rng, 1.9, 0.2, 1.5, 2.4), normal(rng, 1.6, 0.2, 1.3, 2.0), 2.8, 1.6,
                    normal(rng, 2.5, 0.3, 2.0, 3.2));
            case BUS -> new DriverParameters(normal(rng, 0.97, 0.03, 0.9, 1.03), normal(rng, 1.0, 0.1, 0.8, 1.2),
                    normal(rng, 1.8, 0.2, 1.5, 2.2), normal(rng, 1.5, 0.15, 1.2, 1.8), 2.8, 1.5,
                    normal(rng, 2.3, 0.3, 1.8, 3.0));
            // Lane design speed for cycle paths is 18 km/h; real cyclists vary from 12 to 25 km/h.
            case BICYCLE -> new DriverParameters(normal(rng, 1.0, 0.15, 0.65, 1.4), normal(rng, 1.0, 0.2, 0.6, 1.5),
                    normal(rng, 1.5, 0.2, 1.2, 2.0), normal(rng, 1.0, 0.2, 0.7, 1.5), normal(rng, 1.2, 0.2, 0.8, 1.6),
                    2.0, normal(rng, 2.0, 0.3, 1.5, 2.6));
            // Walking speed 1.35 m/s on average (CROW), slower for elderly people and children.
            case PEDESTRIAN -> new DriverParameters(normal(rng, 1.0, 0.13, 0.65, 1.35), 1.0, 1.5, 0.6,
                    normal(rng, 0.6, 0.1, 0.4, 0.9), 10.0, 1.5);
        };
    }

    private static double normal(RandomGenerator rng, double mean, double sd, double min, double max) {
        return Math.max(min, Math.min(max, mean + sd * rng.nextGaussian()));
    }

    /**
     * IDM acceleration.
     *
     * @param v       own speed
     * @param v0      desired speed at this point
     * @param gap     free distance to the vehicle or obstacle ahead, or infinity
     * @param dv      approach rate: own speed minus the speed of the vehicle ahead
     */
    public double accelerate(double v, double v0, double gap, double dv) {
        double ratio = v0 <= 0.01 ? 2 : v / v0;
        double free = 1 - ratio * ratio * ratio * ratio;
        if (Double.isInfinite(gap)) {
            return Math.max(-EMERGENCY_DECEL, maxAccel * free);
        }
        if (gap <= 0.05) {
            return -EMERGENCY_DECEL;
        }
        double desired = minGap + Math.max(0, v * timeHeadway + v * dv / (2 * Math.sqrt(maxAccel * comfortDecel)));
        double interaction = desired / gap;
        return Math.max(-EMERGENCY_DECEL, maxAccel * (free - interaction * interaction));
    }
}
