package simplifiedsimulator;

import java.util.List;

/**
 * Positions of the traffic lights on the crossroad map. Ids match the numbers on the
 * map: 1-15 cars, 21-25 cyclists, 51-53 buses. Pedestrian lights (31-42) are not
 * placed because pedestrians are not simulated.
 */
public final class TrafficLightLayout {

    /** Position of a single light on the ground plane. */
    public record Placement(int id, float x, float z) {
    }

    public static final List<Placement> PLACEMENTS = List.of(
            new Placement(1, -37.7f, -153.3f),
            new Placement(2, -26.4f, -148.6f),
            new Placement(3, -15.4f, -143.6f),
            new Placement(4, 122.9f, -62.2f),
            new Placement(5, 85.5f, -50.5f),
            new Placement(6, 82.0f, -35.4f),
            new Placement(7, 82.2f, -16.3f),
            new Placement(8, -7.4f, 143.4f),
            new Placement(9, -17.7f, 138.7f),
            new Placement(10, -28.3f, 136.3f),
            new Placement(11, -131.9f, 7.6f),
            new Placement(12, -126.0f, -12.4f),
            new Placement(13, -124.8f, -30.7f),
            new Placement(14, -126.2f, -48.8f),
            new Placement(15, -72.5f, 169.5f),
            new Placement(21, -92.1f, -114.3f),
            new Placement(22, 69.0f, -94.9f),
            new Placement(23, 63.3f, 75.4f),
            new Placement(24, 4.0f, 100.7f),
            new Placement(25, -62.7f, 90.3f),
            new Placement(51, 100.56f, -88.64f),
            new Placement(52, 123.6f, -74.70f),
            new Placement(53, -114.0f, 32.5f));

    /**
     * Light groups that the built-in demo controller turns green together: one group
     * per approach of the crossroad, plus the cycle paths.
     */
    public static final List<List<Integer>> DEMO_PHASES = List.of(
            List.of(1, 2, 3),
            List.of(4, 5, 6, 7, 51, 52),
            List.of(8, 9, 10, 15),
            List.of(11, 12, 13, 14, 53),
            List.of(21, 22, 23, 24, 25));

    private TrafficLightLayout() {
    }
}
