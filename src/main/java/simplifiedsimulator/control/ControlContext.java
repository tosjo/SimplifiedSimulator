package simplifiedsimulator.control;

import java.util.Map;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.SignalGroup;

/**
 * Everything a controller needs to know about the crossing.
 *
 * @param volumes expected peak-hour volume per signal group (road users per hour), used to plan green times
 */
public record ControlContext(Network network, ConflictMatrix matrix, SignalPlan plan, DetectorStates detectors,
                             Map<SignalGroup, GroupTiming> timings, Map<SignalGroup, Double> volumes) {

    public ControlContext {
        timings = Map.copyOf(timings);
        volumes = Map.copyOf(volumes);
    }

    public GroupTiming timing(SignalGroup group) {
        return timings.get(group);
    }

    public double volume(SignalGroup group) {
        return volumes.getOrDefault(group, 0.0);
    }
}
