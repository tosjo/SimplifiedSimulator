package simplifiedsimulator.render;

import com.jme3.app.Application;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import simplifiedsimulator.render.agents.Pose;

/**
 * Camera control for the crossing.
 *
 * <ul>
 *   <li><b>Orbit</b> (default): left-drag rotates around the crossing, the mouse wheel zooms,
 *       right-drag pans; W/A/S/D also pan.</li>
 *   <li><b>Fly</b> (F toggles): W/A/S/D move, Q/E go down/up, left-drag looks around,
 *       Shift moves faster.</li>
 *   <li><b>Follow</b>: click a road user to follow it with a chase camera; V switches to the
 *       driver's view and back, Backspace stops following.</li>
 *   <li>Keys 1-6 jump to preset viewpoints (see {@link Preset}).</li>
 * </ul>
 */
public final class CameraRig extends BaseAppState implements ActionListener, AnalogListener {

    /** How the camera is controlled. */
    public enum Mode {
        ORBIT, FLY, FOLLOW, DRIVER
    }

    /** Preset viewpoints, on keys 1 to 6. */
    public enum Preset {
        /** The whole crossing from the south-west, at an angle. */
        OVERVIEW,
        /** Looking straight down from 300 m. */
        BIRDS_EYE,
        /** At eye height beside the Harlingerstraatweg, west of the crossing, looking east. */
        STREET_WEST,
        /** Beside the Harlingerstraatweg east of the crossing, looking west. */
        STREET_EAST,
        /** Beside the Douwe Kalmaleane, looking south over the crossing. */
        STREET_NORTH,
        /** Beside the Slauerhoffweg, looking north towards the crossing. */
        STREET_SOUTH
    }

    private static final String PREFIX = "CameraRig.";
    private static final String DRAG = PREFIX + "Drag";
    private static final String PAN = PREFIX + "Pan";
    private static final String[] AXES = {"Left", "Right", "Up", "Down", "ZoomIn", "ZoomOut"};
    private static final String[] KEYS = {"Forward", "Back", "StrafeLeft", "StrafeRight", "Rise", "Sink", "Fast",
        "ToggleFly", "ToggleDriver", "StopFollow"};
    private static final float EYE = 1.7f;
    /**
     * jME reports one mouse wheel notch as 1.2 (GLFW's 1.0, times 120, divided by 100), so a
     * linear factor such as {@code 1 + amount * k} is lopsided: big enough to feel on the way
     * out, it collapses the distance on the way in. An exponential step is the same size in both
     * directions: about 15% per notch.
     */
    private static final float ZOOM_RATE = 0.12f;
    /** Metres the free camera moves per unit of wheel, about 10 m per notch. */
    private static final float FLY_ZOOM_STEP = 8f;

    private final Function<Long, Optional<Pose>> agentPoses;
    private final ToDoubleFunction<Long> agentLengths;
    private final Function<Ray, Optional<Long>> picker;
    private Camera cam;
    private InputManager input;
    private Mode mode = Mode.ORBIT;
    private Mode beforeFollow = Mode.ORBIT;

    private final Vector3f target = new Vector3f();
    private float yaw;
    private float pitch;
    private float distance;

    private final Vector3f flyPosition = new Vector3f();
    private float flyYaw;
    private float flyPitch;

    private long followed = -1;
    private float followLength = 4.5f;
    private final Vector3f chase = new Vector3f();
    private boolean chaseInitialised;

    private boolean dragging;
    private boolean panning;
    private boolean dragMoved;
    private boolean picking = true;
    private final boolean[] held = new boolean[KEYS.length];

    /**
     * @param agentPoses   where a followed agent is drawn (empty once it has left)
     * @param agentLengths length in metres of an agent, to keep the chase camera at a sensible distance
     * @param picker       finds the agent under a ray from the cursor
     */
    public CameraRig(Function<Long, Optional<Pose>> agentPoses, ToDoubleFunction<Long> agentLengths,
                     Function<Ray, Optional<Long>> picker) {
        this.agentPoses = agentPoses;
        this.agentLengths = agentLengths;
        this.picker = picker;
    }

    @Override
    protected void initialize(Application app) {
        cam = app.getCamera();
        cam.setFrustumPerspective(50f, aspect(cam), 0.25f, 12000f);
        FlyCamAppState fly = app.getStateManager().getState(FlyCamAppState.class);
        if (fly != null) {
            app.getStateManager().detach(fly);
        }
        input = app.getInputManager();
        preset(Preset.OVERVIEW);
    }

    @Override
    protected void cleanup(Application app) {
        // nothing to release
    }

    @Override
    protected void onEnable() {
        if (input == null) {
            return;
        }
        input.addMapping(DRAG, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        input.addMapping(PAN, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        input.addMapping(PREFIX + "Left", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        input.addMapping(PREFIX + "Right", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        input.addMapping(PREFIX + "Up", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        input.addMapping(PREFIX + "Down", new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        input.addMapping(PREFIX + "ZoomIn", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        input.addMapping(PREFIX + "ZoomOut", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        input.addMapping(PREFIX + "Forward", new KeyTrigger(KeyInput.KEY_W));
        input.addMapping(PREFIX + "Back", new KeyTrigger(KeyInput.KEY_S));
        input.addMapping(PREFIX + "StrafeLeft", new KeyTrigger(KeyInput.KEY_A));
        input.addMapping(PREFIX + "StrafeRight", new KeyTrigger(KeyInput.KEY_D));
        input.addMapping(PREFIX + "Rise", new KeyTrigger(KeyInput.KEY_E));
        input.addMapping(PREFIX + "Sink", new KeyTrigger(KeyInput.KEY_Q));
        input.addMapping(PREFIX + "Fast", new KeyTrigger(KeyInput.KEY_LSHIFT), new KeyTrigger(KeyInput.KEY_RSHIFT));
        input.addMapping(PREFIX + "ToggleFly", new KeyTrigger(KeyInput.KEY_F));
        input.addMapping(PREFIX + "ToggleDriver", new KeyTrigger(KeyInput.KEY_V));
        input.addMapping(PREFIX + "StopFollow", new KeyTrigger(KeyInput.KEY_BACK));
        int[] numbers = {KeyInput.KEY_1, KeyInput.KEY_2, KeyInput.KEY_3, KeyInput.KEY_4, KeyInput.KEY_5, KeyInput.KEY_6};
        for (int i = 0; i < numbers.length; i++) {
            input.addMapping(PREFIX + "Preset" + i, new KeyTrigger(numbers[i]));
            input.addListener(this, PREFIX + "Preset" + i);
        }
        input.addListener(this, DRAG, PAN);
        for (String a : AXES) {
            input.addListener(this, PREFIX + a);
        }
        for (String k : KEYS) {
            input.addListener(this, PREFIX + k);
        }
    }

    @Override
    protected void onDisable() {
        if (input == null) {
            return;
        }
        input.removeListener(this);
        for (String a : AXES) {
            input.deleteMapping(PREFIX + a);
        }
        for (String k : KEYS) {
            input.deleteMapping(PREFIX + k);
        }
        for (int i = 0; i < 6; i++) {
            input.deleteMapping(PREFIX + "Preset" + i);
        }
        input.deleteMapping(DRAG);
        input.deleteMapping(PAN);
    }

    public Mode mode() {
        return mode;
    }

    /** Id of the agent being followed, if any. */
    public OptionalLong followed() {
        return mode == Mode.FOLLOW || mode == Mode.DRIVER ? OptionalLong.of(followed) : OptionalLong.empty();
    }

    /** Jumps to a preset viewpoint (stops following). */
    public void preset(Preset preset) {
        switch (preset) {
            case OVERVIEW -> orbit(new Vector3f(4, 0, -4), 30, 30, 170);
            case BIRDS_EYE -> orbit(new Vector3f(2, 0, -2), 0, 89, 300);
            case STREET_WEST -> look(new Vector3f(-62, EYE, 15.5f), new Vector3f(0, 3, 2));
            case STREET_EAST -> look(new Vector3f(60, EYE, -17f), new Vector3f(0, 3, -4));
            case STREET_NORTH -> look(new Vector3f(-14, EYE, -55), new Vector3f(-2, 2, 5));
            case STREET_SOUTH -> look(new Vector3f(9, EYE, 52), new Vector3f(-2, 3, -4));
        }
    }

    /**
     * Orbits around a point.
     *
     * @param yawDegrees   direction the camera looks, 0 = north, 90 = east... measured from the south-west view
     * @param pitchDegrees angle above the horizon
     */
    public void orbit(Vector3f around, float yawDegrees, float pitchDegrees, float dist) {
        mode = Mode.ORBIT;
        target.set(around);
        yaw = yawDegrees * FastMath.DEG_TO_RAD;
        pitch = pitchDegrees * FastMath.DEG_TO_RAD;
        distance = dist;
        applyOrbit();
    }

    /** Places a free camera at {@code from}, looking at {@code at} (jME coordinates). */
    public void look(Vector3f from, Vector3f at) {
        mode = Mode.FLY;
        flyPosition.set(from);
        Vector3f d = at.subtract(from).normalizeLocal();
        flyYaw = FastMath.atan2(d.x, -d.z);
        flyPitch = FastMath.asin(d.y);
        applyFly();
    }

    /** Follows an agent with a chase camera, or from the driver's seat. */
    public void follow(long agentId, boolean driverView) {
        if (mode == Mode.ORBIT || mode == Mode.FLY) {
            beforeFollow = mode;
        }
        followed = agentId;
        followLength = (float) agentLengths.applyAsDouble(agentId);
        mode = driverView ? Mode.DRIVER : Mode.FOLLOW;
        chaseInitialised = false;
    }

    public void stopFollowing() {
        if (mode == Mode.FOLLOW || mode == Mode.DRIVER) {
            mode = beforeFollow;
            if (mode == Mode.ORBIT) {
                applyOrbit();
            } else {
                applyFly();
            }
        }
    }

    @Override
    public void update(float tpf) {
        switch (mode) {
            case ORBIT -> {
                Vector3f move = keyboardMove();
                if (move.lengthSquared() > 0) {
                    float speed = distance * 0.8f * tpf * (held[6] ? 3 : 1);
                    Quaternion q = new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y);
                    target.addLocal(q.mult(new Vector3f(move.x, 0, -move.z)).multLocal(speed));
                    applyOrbit();
                }
            }
            case FLY -> {
                Vector3f move = keyboardMove();
                if (move.lengthSquared() > 0 || held[4] || held[5]) {
                    float speed = (held[6] ? 60f : 15f) * tpf;
                    Vector3f forward = cam.getDirection().clone();
                    Vector3f left = cam.getLeft().clone();
                    flyPosition.addLocal(forward.multLocal(move.z * speed)).addLocal(left.multLocal(-move.x * speed));
                    flyPosition.y += ((held[4] ? 1 : 0) - (held[5] ? 1 : 0)) * speed;
                    flyPosition.y = Math.max(0.4f, flyPosition.y);
                    applyFly();
                }
            }
            case FOLLOW, DRIVER -> updateFollow(tpf);
        }
    }

    private Vector3f keyboardMove() {
        return new Vector3f((held[3] ? 1 : 0) - (held[2] ? 1 : 0), 0, (held[0] ? 1 : 0) - (held[1] ? 1 : 0));
    }

    private void updateFollow(float tpf) {
        Optional<Pose> pose = agentPoses.apply(followed);
        if (pose.isEmpty()) {
            stopFollowing();
            return;
        }
        Pose p = pose.get();
        Vector3f pos = Coords.world(p.x(), p.y(), 0);
        Vector3f forward = Coords.heading(p.heading()).mult(Vector3f.UNIT_X);
        if (mode == Mode.DRIVER) {
            float eye = followLength > 9 ? 2.6f : followLength < 2.5f ? 1.75f : 1.25f;
            Vector3f at = pos.add(forward.mult(followLength * 0.12f)).addLocal(0, eye, 0);
            if (followLength >= 2.5f) {
                // sit on the left-hand (driver's) side
                at.addLocal(Coords.heading(p.heading()).mult(new Vector3f(0, 0, -0.35f)));
            }
            cam.setLocation(at);
            cam.lookAtDirection(forward.add(0, -0.06f, 0).normalizeLocal(), Vector3f.UNIT_Y);
            return;
        }
        float back = 6f + followLength * 1.2f;
        Vector3f wanted = pos.subtract(forward.mult(back)).addLocal(0, 2.5f + followLength * 0.35f, 0);
        if (!chaseInitialised) {
            chase.set(wanted);
            chaseInitialised = true;
        } else {
            chase.interpolateLocal(wanted, Math.min(1f, tpf * 3f));
        }
        cam.setLocation(chase);
        cam.lookAt(pos.add(forward.mult(followLength)).addLocal(0, 1.2f, 0), Vector3f.UNIT_Y);
    }

    private void applyOrbit() {
        pitch = FastMath.clamp(pitch, 3 * FastMath.DEG_TO_RAD, 89.5f * FastMath.DEG_TO_RAD);
        distance = FastMath.clamp(distance, 5f, 1500f);
        Vector3f offset = new Vector3f(FastMath.sin(yaw) * FastMath.cos(pitch), FastMath.sin(pitch),
                FastMath.cos(yaw) * FastMath.cos(pitch)).multLocal(distance);
        cam.setLocation(target.add(offset));
        cam.lookAt(target, pitch > 89 * FastMath.DEG_TO_RAD ? Vector3f.UNIT_Z.negate() : Vector3f.UNIT_Y);
    }

    private void applyFly() {
        flyPitch = FastMath.clamp(flyPitch, -1.5f, 1.5f);
        Vector3f dir = new Vector3f(FastMath.sin(flyYaw) * FastMath.cos(flyPitch), FastMath.sin(flyPitch),
                -FastMath.cos(flyYaw) * FastMath.cos(flyPitch));
        cam.setLocation(flyPosition);
        cam.lookAtDirection(dir, Vector3f.UNIT_Y);
    }

    @Override
    public void onAction(String name, boolean pressed, float tpf) {
        if (name.startsWith(PREFIX + "Preset") && pressed) {
            preset(Preset.values()[Integer.parseInt(name.substring((PREFIX + "Preset").length()))]);
            return;
        }
        for (int i = 0; i < KEYS.length; i++) {
            if (name.equals(PREFIX + KEYS[i])) {
                held[i] = pressed;
            }
        }
        if (!pressed) {
            switch (name.substring(PREFIX.length())) {
                case "ToggleFly" -> toggleFly();
                case "ToggleDriver" -> {
                    if (mode == Mode.FOLLOW) {
                        mode = Mode.DRIVER;
                    } else if (mode == Mode.DRIVER) {
                        mode = Mode.FOLLOW;
                        chaseInitialised = false;
                    }
                }
                case "StopFollow" -> stopFollowing();
                default -> {
                }
            }
        }
        if (name.equals(DRAG)) {
            if (pressed) {
                dragging = true;
                dragMoved = false;
            } else {
                dragging = false;
                if (!dragMoved) {
                    click();
                }
            }
        } else if (name.equals(PAN)) {
            panning = pressed;
        }
    }

    private void toggleFly() {
        if (mode == Mode.ORBIT) {
            look(cam.getLocation().clone(), cam.getLocation().add(cam.getDirection()));
        } else if (mode == Mode.FLY) {
            Vector3f loc = cam.getLocation();
            Vector3f dir = cam.getDirection();
            // orbit around the point on the ground the camera looks at
            float t = dir.y < -0.05f ? -loc.y / dir.y : 120f;
            Vector3f around = loc.add(dir.mult(t));
            around.y = 0;
            float dist = loc.distance(around);
            float p = FastMath.asin(Math.max(0.05f, (loc.y - around.y) / Math.max(dist, 1e-3f)));
            float y = FastMath.atan2(loc.x - around.x, loc.z - around.z);
            orbit(around, y * FastMath.RAD_TO_DEG, p * FastMath.RAD_TO_DEG, dist);
        }
    }

    /** Width over height of the part of the window the camera draws into. */
    static float aspect(Camera cam) {
        float w = cam.getWidth() * (cam.getViewPortRight() - cam.getViewPortLeft());
        float h = cam.getHeight() * (cam.getViewPortTop() - cam.getViewPortBottom());
        return w / h;
    }

    /** Whether clicking a road user starts following it. */
    public void setPicking(boolean enabled) {
        picking = enabled;
    }

    private void click() {
        if (!picking) {
            return;
        }
        Vector2f cursor = input.getCursorPosition();
        Vector3f near = cam.getWorldCoordinates(cursor, 0f);
        Vector3f far = cam.getWorldCoordinates(cursor, 1f);
        Ray ray = new Ray(near, far.subtractLocal(near).normalizeLocal());
        picker.apply(ray).ifPresent(id -> follow(id, false));
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        switch (name.substring(PREFIX.length())) {
            case "Left" -> mouse(-value, 0);
            case "Right" -> mouse(value, 0);
            case "Up" -> mouse(0, value);
            case "Down" -> mouse(0, -value);
            case "ZoomIn" -> zoom(-value);
            case "ZoomOut" -> zoom(value);
            default -> {
            }
        }
    }

    private void mouse(float dx, float dy) {
        if (dragging) {
            dragMoved = true;
        }
        if (mode == Mode.ORBIT) {
            if (dragging) {
                yaw -= dx * 2.5f;
                pitch -= dy * 2.0f;
                applyOrbit();
            } else if (panning) {
                Quaternion q = new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y);
                target.addLocal(q.mult(new Vector3f(-dx, 0, dy)).multLocal(distance * 0.9f));
                applyOrbit();
            }
        } else if (mode == Mode.FLY && dragging) {
            flyYaw += dx * 2.0f;
            flyPitch += dy * 2.0f;
            applyFly();
        }
    }

    private void zoom(float amount) {
        if (mode == Mode.ORBIT) {
            distance = zoomedDistance(distance, amount);
            applyOrbit();
        } else if (mode == Mode.FLY) {
            flyPosition.addLocal(cam.getDirection().mult(-flyStep(amount)));
            applyFly();
        }
    }

    /** Orbit distance after turning the wheel by {@code amount} (positive is out). */
    static float zoomedDistance(float distance, float amount) {
        return distance * FastMath.exp(amount * ZOOM_RATE);
    }

    /** Metres the free camera moves back for a wheel turn of {@code amount} (negative is forward). */
    static float flyStep(float amount) {
        return amount * FLY_ZOOM_STEP;
    }
}
