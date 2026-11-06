package simplifiedsimulator.render;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import simplifiedsimulator.network.Vec2;

/**
 * Conversion from the network's ground plane (x east, y north, metres) to jME's world
 * (X east, Y up, Z south).
 */
public final class Coords {

    private Coords() {
    }

    public static Vector3f world(double x, double y, double height) {
        return new Vector3f((float) x, (float) height, (float) -y);
    }

    public static Vector3f world(Vec2 p, double height) {
        return world(p.x(), p.y(), height);
    }

    /**
     * Rotation for a model whose forward direction is +X, for a heading in radians
     * counter-clockwise from east.
     */
    public static Quaternion heading(double heading) {
        return new Quaternion().fromAngleAxis((float) heading, Vector3f.UNIT_Y);
    }

    /** Rotation that turns +X into the given compass bearing (0 = north, 90 = east). */
    public static Quaternion bearing(double degrees) {
        return heading(Math.toRadians(90 - degrees));
    }
}
