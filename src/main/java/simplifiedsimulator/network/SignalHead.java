package simplifiedsimulator.network;

/**
 * Where a traffic light stands.
 *
 * @param position position of the pole
 * @param facing   compass heading the lamps face (0 = north, 90 = east), i.e. towards approaching traffic
 * @param lane     the lane the head controls
 */
public record SignalHead(Vec2 position, double facing, Lane lane) {
}
