package simplifiedsimulator.stats;

/**
 * Overall results, for comparing controllers.
 *
 * @param motorCompleted      motor vehicles that crossed
 * @param motorDelay          their average delay (s)
 * @param motorStops          their average number of stops
 * @param busDelay            average delay of buses (s)
 * @param bikeDelay           average delay of cyclists (s)
 * @param footDelay           average delay of pedestrians (s)
 * @param maxQueue            longest queue on any lane (vehicles)
 * @param entryQueue          road users still waiting to enter the network
 * @param redLightRuns        road users that crossed a stop line at red
 * @param conflictViolations  times two road users overlapped (must be 0)
 * @param signalConflicts     times conflicting groups showed green or amber together (must be 0)
 */
public record Summary(int motorCompleted, double motorDelay, double motorStops, double busDelay, double bikeDelay,
                      double footDelay, int maxQueue, int entryQueue, int redLightRuns, int conflictViolations,
                      int signalConflicts) {
}
