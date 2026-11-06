package simplifiedsimulator.stats;

import simplifiedsimulator.network.Mode;

/**
 * Statistics of one movement (for example "W through" for cars), since the start.
 *
 * @param key            short id, for example {@code W-THROUGH-CAR}
 * @param label          readable name, for example "West, straight on (cars)"
 * @param completed      road users that finished the trip
 * @param meanDelay      average delay in seconds
 * @param meanStops      average number of stops
 * @param meanWaiting    average time spent waiting, in seconds
 * @param queue          road users queued now, including those waiting to enter the network
 * @param maxQueue       largest queue seen
 * @param throughput     road users per hour over the last 15 minutes
 */
public record MovementStats(String key, String label, Mode mode, int completed, double meanDelay, double meanStops,
                            double meanWaiting, int queue, int maxQueue, double throughput) {
}
