package simplifiedsimulator.control;

import simplifiedsimulator.network.Movement;
import simplifiedsimulator.network.SignalGroup;

/**
 * Timing settings of a signal group for the built-in controllers.
 *
 * @param minGreen shortest green (s)
 * @param maxGreen longest green when traffic keeps coming (s)
 * @param gap      the green is extended while a detector saw a road user less than this many seconds ago
 */
public record GroupTiming(double minGreen, double maxGreen, double gap) {

    /**
     * Typical Dutch peak-hour settings for the kind of traffic the group controls. A crossing
     * with this many conflicting streams needs short maximum greens to keep the cycle (and so
     * everybody's waiting time) reasonable.
     */
    public static GroupTiming defaultFor(SignalGroup group) {
        Movement movement = group.routes().isEmpty() ? Movement.THROUGH : group.routes().get(0).movement();
        boolean mainRoad = group.approach().equals("W") || group.approach().equals("E");
        return switch (group.mode()) {
            case CAR -> movement == Movement.THROUGH
                    ? new GroupTiming(6, mainRoad ? 30 : 20, 2.5)
                    : new GroupTiming(5, 15, 2.5);
            case BUS -> new GroupTiming(4, 12, 3.0);
            case BIKE -> new GroupTiming(6, 10, 2.0);
            // pedestrians get a fixed green long enough to start crossing safely
            case FOOT -> new GroupTiming(7, 7, 0);
        };
    }
}
