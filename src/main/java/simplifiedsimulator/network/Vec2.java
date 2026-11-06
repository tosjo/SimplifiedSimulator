package simplifiedsimulator.network;

/**
 * A point or direction in the ground plane of the crossing, in metres.
 * x points east and y points north; the origin is the middle of the crossing.
 */
public record Vec2(double x, double y) {

    public static final Vec2 ZERO = new Vec2(0, 0);

    public Vec2 plus(Vec2 o) {
        return new Vec2(x + o.x, y + o.y);
    }

    public Vec2 minus(Vec2 o) {
        return new Vec2(x - o.x, y - o.y);
    }

    public Vec2 times(double f) {
        return new Vec2(x * f, y * f);
    }

    public double dot(Vec2 o) {
        return x * o.x + y * o.y;
    }

    /** z component of the 3D cross product; positive when {@code o} lies to the left. */
    public double cross(Vec2 o) {
        return x * o.y - y * o.x;
    }

    public double length() {
        return Math.hypot(x, y);
    }

    public double distance(Vec2 o) {
        return Math.hypot(x - o.x, y - o.y);
    }

    public Vec2 normalized() {
        double len = length();
        return len < 1e-12 ? ZERO : new Vec2(x / len, y / len);
    }

    /** Unit vector pointing to the right of this direction. */
    public Vec2 rightNormal() {
        Vec2 n = normalized();
        return new Vec2(n.y, -n.x);
    }

    /** Angle in radians, counter-clockwise from east. */
    public double angle() {
        return Math.atan2(y, x);
    }

    public static Vec2 ofAngle(double radians) {
        return new Vec2(Math.cos(radians), Math.sin(radians));
    }
}
