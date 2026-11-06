package simplifiedsimulator;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;

/**
 * Kinds of road users that can appear in a scenario. The colours follow the legend of
 * the crossroad map (green cars, blue cyclists, red pedestrians, yellow buses).
 */
public enum RoadUserType {
    CYCLIST(0, ColorRGBA.Blue, new Vector3f(0.6f, 1f, 1.6f), 8f),
    /** The recorded routes only cover the roads and cycle paths, so pedestrians are not spawned. */
    PEDESTRIAN(1, ColorRGBA.Red, new Vector3f(0.5f, 1f, 0.5f), 0f),
    CAR(2, new ColorRGBA(0.2f, 0.75f, 0.3f, 1f), new Vector3f(1.8f, 1.25f, 4f), 20f),
    BUS(3, ColorRGBA.Yellow, new Vector3f(2.2f, 1.75f, 9f), 15f),
    OTHER(4, ColorRGBA.DarkGray, new Vector3f(2f, 1.5f, 6f), 16f);

    private final int code;
    private final ColorRGBA color;
    private final Vector3f halfExtents;
    private final float speed;

    RoadUserType(int code, ColorRGBA color, Vector3f halfExtents, float speed) {
        this.code = code;
        this.color = color;
        this.halfExtents = halfExtents;
        this.speed = speed;
    }

    /** Code used for this type in scenario files. */
    public int code() {
        return code;
    }

    public ColorRGBA color() {
        return color.clone();
    }

    /** Half size of the box that represents this road user. */
    public Vector3f halfExtents() {
        return halfExtents.clone();
    }

    /** Travel speed in world units per second. */
    public float speed() {
        return speed;
    }

    /** Whether road users of this type are added to the simulation. */
    public boolean isSimulated() {
        return speed > 0f;
    }

    /**
     * @throws IllegalArgumentException if {@code code} is not a known type
     */
    public static RoadUserType fromCode(int code) {
        for (RoadUserType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown road user type: " + code);
    }
}
