package simplifiedsimulator;

import com.jme3.asset.AssetManager;
import com.jme3.collision.CollisionResults;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import simplifiedsimulator.net.LightState;

/**
 * A traffic light at a stop line. Road users stop while they touch the light's stop
 * zone and the light is not green; the first road user to touch the zone triggers the
 * light's sensor.
 */
public final class TrafficLight {

    private final int id;
    private final Node node;
    /** Invisible box marking the stop line; used for all collision checks. */
    private final Geometry stopZone;
    private final Material lampMaterial;
    private LightState state = LightState.RED;
    private boolean sensorArmed = true;

    public TrafficLight(int id, float x, float z, AssetManager assetManager) {
        this.id = id;
        node = new Node("TrafficLight" + id);
        node.setLocalTranslation(x, 0f, z);

        stopZone = new Geometry("StopZone" + id, new Box(1f, 5f, 1f));
        stopZone.setMaterial(unshaded(assetManager, ColorRGBA.White));
        stopZone.setLocalTranslation(0f, 6f, 0f);
        stopZone.setCullHint(Spatial.CullHint.Always);
        node.attachChild(stopZone);

        Geometry pole = new Geometry("Pole" + id, new Box(0.35f, 4f, 0.35f));
        pole.setMaterial(unshaded(assetManager, ColorRGBA.DarkGray));
        pole.setLocalTranslation(0f, 4f, 0f);
        node.attachChild(pole);

        lampMaterial = unshaded(assetManager, ColorRGBA.Red);
        Geometry lamp = new Geometry("Lamp" + id, new Sphere(12, 12, 1.6f));
        lamp.setMaterial(lampMaterial);
        lamp.setLocalTranslation(0f, 9f, 0f);
        node.attachChild(lamp);

        applyColor();
    }

    private static Material unshaded(AssetManager assetManager, ColorRGBA color) {
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        return material;
    }

    public int id() {
        return id;
    }

    /** Scene node holding the visible light and its stop zone. */
    public Node node() {
        return node;
    }

    public LightState state() {
        return state;
    }

    public boolean isGreen() {
        return state == LightState.GREEN;
    }

    public void setState(LightState newState) {
        state = newState;
        // A new red or green phase means the controller has processed the previous
        // sensor message, so the next arrival should be reported again.
        if (newState != LightState.ORANGE) {
            sensorArmed = true;
        }
        applyColor();
    }

    /** Whether the given road user currently touches this light. */
    public boolean touches(Vehicle vehicle) {
        if (!stopZone.getWorldBound().intersects(vehicle.geometry().getWorldBound())) {
            return false;
        }
        CollisionResults results = new CollisionResults();
        return vehicle.geometry().collideWith(stopZone.getWorldBound(), results) > 0;
    }

    /**
     * Checks whether the sensor of this light should fire for the given road user.
     * The sensor fires at most once per light phase.
     */
    public boolean triggerSensor(Vehicle vehicle) {
        if (sensorArmed && touches(vehicle)) {
            sensorArmed = false;
            return true;
        }
        return false;
    }

    private void applyColor() {
        lampMaterial.setColor("Color", switch (state) {
            case RED -> ColorRGBA.Red;
            case ORANGE -> ColorRGBA.Orange;
            case GREEN -> ColorRGBA.Green;
        });
    }
}
