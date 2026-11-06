package simplifiedsimulator.network;

/**
 * A bus stop along a lane.
 *
 * @param dwell typical time a bus stands still at the stop, in seconds
 */
public record BusStop(String id, String name, Lane lane, double s, double dwell) {
}
