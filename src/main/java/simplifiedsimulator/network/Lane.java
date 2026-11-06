package simplifiedsimulator.network;

/**
 * A single lane or path that road users follow in one direction.
 *
 * @param id         unique name, for example {@code W_in_2} or {@code W_T_1}
 * @param mode       who uses it
 * @param width      width in metres
 * @param speedLimit design speed in m/s (lower on tight turns and paths)
 * @param path       centre line in the direction of travel
 */
public record Lane(String id, Mode mode, double width, double speedLimit, Polyline path) {

    public double length() {
        return path.length();
    }
}
