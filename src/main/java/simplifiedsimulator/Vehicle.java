package simplifiedsimulator;

import com.jme3.asset.AssetManager;
import com.jme3.cinematic.MotionPath;
import com.jme3.cinematic.PlayState;
import com.jme3.cinematic.events.CinematicEvent;
import com.jme3.cinematic.events.CinematicEventListener;
import com.jme3.cinematic.events.MotionEvent;
import com.jme3.collision.CollisionResults;
import com.jme3.material.Material;
import com.jme3.math.Spline.SplineType;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import java.util.List;

/**
 * A road user that follows a route over the crossroad at constant speed. It waits
 * while it touches a traffic light that is not green, or while it touches a road user
 * that entered the simulation before it (older road users have right of way, which
 * keeps queues from deadlocking).
 */
public final class Vehicle {

    private final int id;
    private final RoadUserType type;
    private final Geometry geometry;
    private final MotionEvent motion;
    private boolean finished;
    private boolean waiting;

    public Vehicle(int id, RoadUserType type, List<Vector3f> route, AssetManager assetManager) {
        if (route.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two waypoints");
        }
        this.id = id;
        this.type = type;

        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", type.color());
        Vector3f size = type.halfExtents();
        geometry = new Geometry(type.name().toLowerCase() + "-" + id, new Box(size.x, size.y, size.z));
        geometry.setMaterial(material);
        geometry.setLocalTranslation(route.get(0));

        MotionPath path = new MotionPath();
        path.setPathSplineType(SplineType.Linear);
        route.forEach(point -> path.addWayPoint(point.clone()));

        motion = new MotionEvent(geometry, path, path.getLength() / type.speed());
        motion.setDirectionType(MotionEvent.Direction.Path);
        motion.addListener(new CinematicEventListener() {
            @Override
            public void onPlay(CinematicEvent cinematic) {
            }

            @Override
            public void onPause(CinematicEvent cinematic) {
            }

            @Override
            public void onStop(CinematicEvent cinematic) {
                finished = true;
            }
        });
        motion.play();
    }

    public int id() {
        return id;
    }

    public RoadUserType type() {
        return type;
    }

    public Geometry geometry() {
        return geometry;
    }

    /** Whether the road user reached the end of its route. */
    public boolean isFinished() {
        return finished;
    }

    /** Whether the road user is currently waiting for a light or another road user. */
    public boolean isWaiting() {
        return waiting;
    }

    /** Decides whether to drive or wait this frame. */
    public void update(Iterable<TrafficLight> lights, Iterable<Vehicle> others) {
        if (finished) {
            return;
        }
        boolean blocked = isBlockedByLight(lights) || isBlockedByVehicle(others);
        if (blocked && motion.getPlayState() == PlayState.Playing) {
            motion.pause();
        } else if (!blocked && motion.getPlayState() == PlayState.Paused) {
            motion.play();
        }
        waiting = blocked;
    }

    /** Stops the motion and detaches the road user from the scene. */
    public void remove() {
        if (motion.getPlayState() != PlayState.Stopped) {
            motion.stop();
        }
        geometry.removeControl(motion);
        geometry.removeFromParent();
    }

    private boolean isBlockedByLight(Iterable<TrafficLight> lights) {
        for (TrafficLight light : lights) {
            if (!light.isGreen() && light.touches(this)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockedByVehicle(Iterable<Vehicle> others) {
        for (Vehicle other : others) {
            if (other.id < id && !other.finished && touches(other)) {
                return true;
            }
        }
        return false;
    }

    private boolean touches(Vehicle other) {
        if (!geometry.getWorldBound().intersects(other.geometry.getWorldBound())) {
            return false;
        }
        CollisionResults results = new CollisionResults();
        return other.geometry.collideWith(geometry.getWorldBound(), results) > 0;
    }
}
