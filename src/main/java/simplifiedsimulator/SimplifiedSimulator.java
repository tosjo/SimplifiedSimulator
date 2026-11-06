package simplifiedsimulator;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import simplifiedsimulator.control.ExternalController;
import simplifiedsimulator.control.SignalControllers;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.render.CrossingRenderer;
import simplifiedsimulator.sim.Simulation;
import simplifiedsimulator.ui.AppStatus;
import simplifiedsimulator.ui.HudState;
import simplifiedsimulator.ui.ScreenshotCapture;

/**
 * The simulator window: runs the traffic simulation of the crossing in real time (or faster),
 * renders it in 3D and shows statistics and controls.
 */
public final class SimplifiedSimulator extends SimpleApplication implements ActionListener {

    private static final Logger LOG = Logger.getLogger(SimplifiedSimulator.class.getName());
    private static final String PAUSE = "Sim.Pause";
    private static final String FASTER = "Sim.Faster";
    private static final String SLOWER = "Sim.Slower";
    private static final String CONTROLLER = "Sim.Controller";
    private static final String RECONNECT = "Sim.Reconnect";
    private static final String MORE = "Sim.MoreTraffic";
    private static final String LESS = "Sim.LessTraffic";
    private static final String EXPORT = "Sim.Export";
    private static final String HELP = "Hud.Help";
    private static final String TIMELINE = "Hud.Timeline";
    private static final String STATS = "Hud.Statistics";

    private final SimulatorConfig config;
    private final Simulation simulation;
    private CrossingRenderer renderer;
    private HudState hud;
    private String controllerName;
    private final StepClock clock;
    private int frames;
    private float fpsTime;
    private int fpsFrames;
    private int fps;

    SimplifiedSimulator(SimulatorConfig config, Simulation simulation) {
        super();
        this.config = config;
        this.simulation = simulation;
        this.clock = new StepClock(simulation.options().timeStep(), config.speed());
        this.controllerName = config.controller();
    }

    @Override
    public void simpleInitApp() {
        setDisplayStatView(false);
        setDisplayFps(false);
        // the default "C prints the camera position" and "M prints memory" keys are not wanted here
        for (String mapping : List.of(INPUT_MAPPING_CAMERA_POS, INPUT_MAPPING_MEMORY, INPUT_MAPPING_HIDE_STATS)) {
            if (inputManager.hasMapping(mapping)) {
                inputManager.deleteMapping(mapping);
            }
        }

        Network network = simulation.network();
        renderer = new CrossingRenderer(network, config.quality());
        renderer.setDate(config.date());
        stateManager.attach(renderer);
        boolean screenshot = config.screenshot() != null;
        // a screenshot shows the moment as if the simulation were running normally
        hud = new HudState(() -> simulation, renderer,
                () -> screenshot ? new AppStatus(clock.speed(), false, -1) : new AppStatus(clock.speed(), clock.paused(), fps));
        stateManager.attach(hud);

        if (screenshot) {
            // render a frozen moment: the simulation has already run for --duration seconds
            clock.setPaused(true);
            guiViewPort.addProcessor(new ScreenshotCapture(config.screenshot(), 10, this::screenshotSaved));
        } else {
            ScreenshotAppState screenshots = new ScreenshotAppState("", "simplified-simulator");
            stateManager.attach(screenshots);
            inputManager.addMapping("ScreenShot", new KeyTrigger(KeyInput.KEY_F12));
            addKeys();
        }
    }

    private void addKeys() {
        map(PAUSE, KeyInput.KEY_SPACE, KeyInput.KEY_P);
        map(FASTER, KeyInput.KEY_EQUALS, KeyInput.KEY_ADD);
        map(SLOWER, KeyInput.KEY_MINUS, KeyInput.KEY_SUBTRACT);
        map(CONTROLLER, KeyInput.KEY_C);
        map(RECONNECT, KeyInput.KEY_R);
        map(MORE, KeyInput.KEY_RBRACKET);
        map(LESS, KeyInput.KEY_LBRACKET);
        map(EXPORT, KeyInput.KEY_X);
        map(HELP, KeyInput.KEY_H, KeyInput.KEY_F1);
        map(TIMELINE, KeyInput.KEY_T);
        map(STATS, KeyInput.KEY_G);
    }

    private void map(String name, int... keys) {
        for (int key : keys) {
            inputManager.addMapping(name, new KeyTrigger(key));
        }
        inputManager.addListener(this, name);
    }

    @Override
    public void onAction(String name, boolean pressed, float tpf) {
        if (!pressed) {
            return;
        }
        switch (name) {
            case PAUSE -> {
                clock.togglePause();
                hud.flash(clock.paused() ? "Paused" : "Running");
            }
            case FASTER -> setSpeed(clock.speed() * 2);
            case SLOWER -> setSpeed(clock.speed() / 2);
            case CONTROLLER -> nextController();
            case RECONNECT -> reconnect();
            case MORE -> setDemandScale(simulation.demandScale() + 0.1);
            case LESS -> setDemandScale(simulation.demandScale() - 0.1);
            case EXPORT -> exportStatistics();
            case HELP -> hud.toggleHelp();
            case TIMELINE -> hud.toggleTimeline();
            case STATS -> hud.toggleStatistics();
            default -> {
                // not ours
            }
        }
    }

    private void setSpeed(double value) {
        clock.setSpeed(value);
        double speed = clock.speed();
        hud.flash(String.format(Locale.ROOT, "Speed %s", speed >= 1 ? (long) speed + "x" : "1/" + (long) (1 / speed) + "x"));
    }

    private void setDemandScale(double value) {
        double scale = Math.max(0, Math.min(3, Math.round(value * 10) / 10.0));
        simulation.setDemandScale(scale);
        hud.flash(String.format(Locale.ROOT, "Traffic %.0f%% of the estimated volumes", scale * 100));
    }

    private void nextController() {
        List<String> names = SignalControllers.NAMES;
        controllerName = names.get((names.indexOf(controllerName) + 1) % names.size());
        simulation.setController(SignalControllers.create(controllerName, config.host(), config.port()));
        hud.flash(simulation.controller().name());
    }

    private void reconnect() {
        if (simulation.controller() instanceof ExternalController external) {
            external.connect();
            hud.flash("Connecting to " + config.host() + ":" + config.port());
        } else {
            hud.flash("Press C to switch to the external controller first");
        }
    }

    private void exportStatistics() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = Path.of("statistics-" + stamp + ".csv");
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            simulation.statistics().writeMovementsCsv(out, simulation.time());
            hud.flash("Saved " + file.toAbsolutePath());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot save statistics", e);
            hud.flash("Could not save statistics: " + e.getMessage());
        }
    }

    @Override
    public void simpleUpdate(float tpf) {
        frames++;
        if (frames == 2) {
            // the camera rig exists once the renderer has been initialised
            renderer.cameraRig().preset(config.camera());
        }
        countFps(tpf);

        int steps = clock.advance(tpf);
        for (int i = 0; i < steps; i++) {
            simulation.step();
        }
        renderer.update(simulation, clock.alpha());
        if (config.duration() > 0 && config.screenshot() == null && simulation.time() >= config.duration()) {
            stop();
        }
    }

    private void countFps(float tpf) {
        fpsTime += tpf;
        fpsFrames++;
        if (fpsTime >= 1) {
            fps = Math.round(fpsFrames / fpsTime);
            fpsTime = 0;
            fpsFrames = 0;
        }
    }

    private void screenshotSaved() {
        System.out.println("Saved " + config.screenshot().toAbsolutePath());
        stop();
    }

    @Override
    public void destroy() {
        try {
            simulation.controller().close();
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Error while closing the controller", e);
        }
        super.destroy();
    }
}
