package simplifiedsimulator;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import simplifiedsimulator.net.ControllerClient;
import simplifiedsimulator.net.LightState;
import simplifiedsimulator.net.LightUpdate;
import simplifiedsimulator.net.ProtocolCodec;
import simplifiedsimulator.scenario.RouteRepository;
import simplifiedsimulator.scenario.SpawnEvent;

/**
 * Traffic simulation of a single crossroad. Road users are spawned from a scenario
 * file and follow pre-recorded routes. Traffic lights are switched either by an
 * external controller over TCP or by the built-in demo controller.
 */
public final class SimplifiedSimulator extends SimpleApplication implements ActionListener {

    private static final Logger LOG = Logger.getLogger(SimplifiedSimulator.class.getName());

    private static final String CONNECT = "Connect";
    private static final String TOGGLE_DEMO = "ToggleDemo";
    private static final String ALL_GREEN = "AllGreen";
    private static final String ALL_RED = "AllRed";

    private final SimulatorConfig config;
    private final RouteRepository routes = new RouteRepository();
    private final Map<Integer, TrafficLight> lights = new LinkedHashMap<>();
    private final List<Vehicle> vehicles = new ArrayList<>();
    private final DemoLightController demoController;
    private final ControllerClient client;

    private final List<SpawnEvent> events;
    private int nextEvent;
    private int nextVehicleId;
    private int finishedCount;
    private float elapsed;
    private boolean demoActive;
    private boolean connecting;
    private BitmapText hud;

    /**
     * @param config options from the command line
     * @param events road users to spawn, ordered by time
     */
    public SimplifiedSimulator(SimulatorConfig config, List<SpawnEvent> events) {
        super();
        this.config = config;
        this.events = List.copyOf(events);
        this.demoController = new DemoLightController(TrafficLightLayout.DEMO_PHASES, this::setLight);
        // Network messages arrive on a background thread; jME scene changes must
        // happen on the render thread, so updates are queued.
        this.client = new ControllerClient(config.host(), config.port(),
                update -> enqueue(() -> applyControllerUpdate(update)));
    }

    @Override
    public void simpleInitApp() {
        setDisplayStatView(false);
        setDisplayFps(false);
        viewPort.setBackgroundColor(new ColorRGBA(0.55f, 0.7f, 0.85f, 1f));
        cam.setLocation(new Vector3f(0f, 390f, 245f));
        cam.lookAt(new Vector3f(0f, 0f, 5f), Vector3f.UNIT_Y);

        if (flyCam != null) {
            flyCam.setMoveSpeed(150f);
            flyCam.setDragToRotate(true);
        }

        Geometry floor = CrossroadScene.createFloor(assetManager);
        rootNode.attachChild(floor);
        for (TrafficLightLayout.Placement placement : TrafficLightLayout.PLACEMENTS) {
            TrafficLight light = new TrafficLight(placement.id(), placement.x(), placement.z(), assetManager);
            lights.put(light.id(), light);
            rootNode.attachChild(light.node());
        }

        if (config.recordTo() != null) {
            stateManager.attach(new RouteRecorderState(floor, config.recordTo()));
        }

        if (inputManager != null) {
            setUpKeys();
            if (config.recordTo() == null) {
                setUpHud();
            }
        }
        if (config.demo()) {
            setDemoActive(true);
        }
        if (config.connect()) {
            connect();
        }
    }

    @Override
    public void simpleUpdate(float tpf) {
        elapsed += tpf;

        if (demoActive) {
            demoController.update(tpf);
        }

        while (nextEvent < events.size() && events.get(nextEvent).time() <= elapsed) {
            spawn(events.get(nextEvent++));
        }

        for (Vehicle vehicle : vehicles) {
            vehicle.update(lights.values(), vehicles);
            for (TrafficLight light : lights.values()) {
                if (light.triggerSensor(vehicle)) {
                    client.send(ProtocolCodec.encodeSensor(light.id()));
                }
            }
        }

        for (Iterator<Vehicle> it = vehicles.iterator(); it.hasNext(); ) {
            Vehicle vehicle = it.next();
            if (vehicle.isFinished()) {
                vehicle.remove();
                it.remove();
                finishedCount++;
            }
        }

        if (hud != null) {
            hud.setText(statusText());
        }
        if (config.duration() > 0 && elapsed >= config.duration()) {
            LOG.info(() -> String.format("Stopping after %d s: %d spawned, %d finished, %d on the road",
                    config.duration(), nextVehicleId, finishedCount, vehicles.size()));
            stop();
        }
    }

    @Override
    public void destroy() {
        client.close();
        super.destroy();
    }

    @Override
    public void onAction(String binding, boolean isPressed, float tpf) {
        if (!isPressed) {
            return;
        }
        switch (binding) {
            case CONNECT -> connect();
            case TOGGLE_DEMO -> setDemoActive(!demoActive);
            case ALL_GREEN -> setAllLights(LightState.GREEN);
            case ALL_RED -> setAllLights(LightState.RED);
            default -> {
            }
        }
    }

    private void spawn(SpawnEvent event) {
        RoadUserType type;
        try {
            type = RoadUserType.fromCode(event.type());
        } catch (IllegalArgumentException e) {
            LOG.warning(() -> "Skipping event with " + e.getMessage());
            return;
        }
        if (!type.isSimulated()) {
            return;
        }
        routes.find(event.startPoint(), event.endPoint()).ifPresent(route -> {
            Vehicle vehicle = new Vehicle(nextVehicleId++, type, route, assetManager);
            vehicles.add(vehicle);
            rootNode.attachChild(vehicle.geometry());
        });
    }

    private void connect() {
        if (client.isConnected() || connecting) {
            return;
        }
        connecting = true;
        CompletableFuture.supplyAsync(client::connect).thenAccept(ok -> enqueue(() -> {
            connecting = false;
            if (ok) {
                // The external controller is in charge from now on.
                setDemoActive(false);
            }
        }));
    }

    private void setDemoActive(boolean active) {
        if (active && client.isConnected()) {
            LOG.info("Demo controller is not available while connected to a controller");
            return;
        }
        demoActive = active;
        if (active) {
            demoController.reset();
        }
    }

    private void setAllLights(LightState state) {
        demoActive = false;
        lights.values().forEach(light -> light.setState(state));
    }

    private void applyControllerUpdate(LightUpdate update) {
        if (lights.containsKey(update.lightId())) {
            setLight(update.lightId(), update.state());
        } else {
            LOG.fine(() -> "Controller switched unknown light " + update.lightId());
        }
    }

    private void setLight(int id, LightState state) {
        TrafficLight light = lights.get(id);
        if (light != null) {
            light.setState(state);
        }
    }

    private void setUpKeys() {
        inputManager.addMapping(CONNECT, new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping(TOGGLE_DEMO, new KeyTrigger(KeyInput.KEY_L));
        inputManager.addMapping(ALL_GREEN, new KeyTrigger(KeyInput.KEY_G));
        inputManager.addMapping(ALL_RED, new KeyTrigger(KeyInput.KEY_R));
        inputManager.addListener(this, CONNECT, TOGGLE_DEMO, ALL_GREEN, ALL_RED);
    }

    private void setUpHud() {
        hud = new BitmapText(guiFont);
        hud.setSize(guiFont.getCharSet().getRenderedSize());
        hud.setColor(ColorRGBA.White);
        hud.setLocalTranslation(10f, cam.getHeight() - 10f, 0f);
        guiNode.attachChild(hud);
    }

    private String statusText() {
        String lightsMode;
        if (client.isConnected()) {
            lightsMode = "controller at " + client.describe();
        } else if (connecting) {
            lightsMode = "connecting to " + client.describe() + "...";
        } else if (demoActive) {
            lightsMode = "built-in demo cycle";
        } else {
            lightsMode = "manual";
        }
        long waiting = vehicles.stream().filter(Vehicle::isWaiting).count();
        return String.format(
                "Time %d s   On the road %d (waiting %d)   Finished %d%n"
                        + "Lights: %s%n"
                        + "[Space] connect  [L] demo lights  [G] all green  [R] all red  "
                        + "[WASD/Q/Z + mouse drag] camera",
                (int) elapsed, vehicles.size(), waiting, finishedCount, lightsMode);
    }
}
