package simplifiedsimulator.scenario;

/**
 * A single entry of a scenario file: at {@code time} seconds after the start of the
 * simulation a road user of the given {@code type} enters at {@code startPoint} and
 * drives towards {@code endPoint}.
 *
 * @param type       road user type code as used in the scenario file
 * @param startPoint id of the entry point on the crossroad map
 * @param endPoint   id of the exit point on the crossroad map
 * @param time       spawn time in whole seconds since the start of the simulation
 */
public record SpawnEvent(int type, int startPoint, int endPoint, int time) {

    public SpawnEvent {
        if (time < 0) {
            throw new IllegalArgumentException("time must not be negative: " + time);
        }
    }
}
