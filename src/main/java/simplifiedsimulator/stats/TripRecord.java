package simplifiedsimulator.stats;

import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Movement;
import simplifiedsimulator.sim.AgentType;

/**
 * One completed trip through the crossing.
 *
 * @param delay   travel time minus the time it would have taken on an empty road with green lights
 * @param waiting seconds spent (almost) standing still
 */
public record TripRecord(String routeId, String approach, Movement movement, Mode mode, AgentType type,
                         double startTime, double endTime, double delay, int stops, double waiting) {

    public double travelTime() {
        return endTime - startTime;
    }
}
