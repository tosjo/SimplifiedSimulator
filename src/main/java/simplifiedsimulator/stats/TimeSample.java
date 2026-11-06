package simplifiedsimulator.stats;

import java.util.Map;

/**
 * Snapshot taken every simulated minute.
 *
 * @param time            simulated seconds since the start
 * @param timeOfDay       seconds after midnight
 * @param agents          road users in the network
 * @param entryQueue      road users waiting to enter the network because their lane is full
 * @param queues          queued motor vehicles per approach (N, E, S, W)
 * @param motorDelay      average delay of motor vehicles that finished in the last minute (NaN if none)
 * @param slowDelay       average delay of cyclists and pedestrians that finished in the last minute (NaN if none)
 * @param motorPerHour    motor vehicles that finished in the last minute, as an hourly rate
 */
public record TimeSample(double time, double timeOfDay, int agents, int entryQueue, Map<String, Integer> queues,
                         double motorDelay, double slowDelay, double motorPerHour) {

    public TimeSample {
        queues = Map.copyOf(queues);
    }
}
