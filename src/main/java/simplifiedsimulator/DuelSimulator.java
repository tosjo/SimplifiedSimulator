package simplifiedsimulator;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import java.io.IOException;
import java.io.StringWriter;
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
import simplifiedsimulator.render.CameraRig;
import simplifiedsimulator.render.CrossingRenderer;
import simplifiedsimulator.render.DuelViews;
import simplifiedsimulator.render.RenderTarget;
import simplifiedsimulator.sim.Simulation;
import simplifiedsimulator.ui.AppStatus;
import simplifiedsimulator.ui.DuelExport;
import simplifiedsimulator.ui.DuelHud;
import simplifiedsimulator.ui.ScreenshotCapture;

/**
 * The controller duel: two simulations of the same traffic under different controllers, side
 * by side. Both advance by the same steps of one {@link StepClock}; the right camera follows
 * the left one, which the mouse and keyboard control.
 */
final class DuelSimulator extends SimpleApplication implements ActionListener {

    private static final Logger LOG = Logger.getLogger(DuelSimulator.class.getName());

    private static final String PAUSE = "Duel.Pause";
    private static final String FASTER = "Duel.Faster";
    private static final String SLOWER = "Duel.Slower";
    private static final String RECONNECT = "Duel.Reconnect";
    private static final String MORE = "Duel.MoreTraffic";
    private static final String LESS = "Duel.LessTraffic";
    private static final String EXPORT = "Duel.Export";
    private static final String HELP = "Duel.Help";

    private final SimulatorConfig config;
    private final Simulation left;
    private final Simulation right;
    private final StepClock clock;
    private CrossingRenderer leftRenderer;
    private CrossingRenderer rightRenderer;
    private DuelHud hud;
    private int frames;
    private float fpsTime;
    private int fpsFrames;
    private int fps;

    DuelSimulator(SimulatorConfig config, Simulation left, Simulation right) {
        super();
        this.config = config;
        this.left = left;
        this.right = right;
        this.clock = new StepClock(left.options().timeStep(), config.speed());
    }

    @Override
    public void simpleInitApp() {
        setDisplayStatView(false);
        setDisplayFps(false);
        for (String mapping : List.of(INPUT_MAPPING_CAMERA_POS, INPUT_MAPPING_MEMORY, INPUT_MAPPING_HIDE_STATS)) {
            if (inputManager.hasMapping(mapping)) {
                inputManager.deleteMapping(mapping);
            }
        }

        // set before the renderers start, so the filters and the camera rig see half a window
        cam.setViewPort(0, 0.5f, 0, 1);
        Camera rightCam = cam.clone();
        rightCam.setViewPort(0.5f, 1, 0, 1);
        Node rightRoot = new Node("duel right");
        ViewPort rightView = renderManager.createMainView("duel right", rightCam);
        rightView.setClearFlags(true, true, true);
        rightView.setBackgroundColor(viewPort.getBackgroundColor());
        rightView.attachScene(rightRoot);

        leftRenderer = new CrossingRenderer(left.network(), config.quality());
        leftRenderer.setDate(config.date());
        rightRenderer = new CrossingRenderer(right.network(), config.quality(), new RenderTarget(rightRoot, rightView, false));
        rightRenderer.setDate(config.date());
        stateManager.attach(leftRenderer);
        stateManager.attach(rightRenderer);
        stateManager.attach(new DuelViews(rightRoot, cam, rightCam));

        boolean screenshot = config.screenshot() != null;
        hud = new DuelHud(left, right,
                () -> screenshot ? new AppStatus(clock.speed(), false, -1) : new AppStatus(clock.speed(), clock.paused(), fps));
        stateManager.attach(hud);

        if (screenshot) {
            clock.setPaused(true);
            guiViewPort.addProcessor(new ScreenshotCapture(config.screenshot(), 10, this::screenshotSaved));
        } else {
            stateManager.attach(new ScreenshotAppState("", "simplified-simulator-duel"));
            inputManager.addMapping("ScreenShot", new KeyTrigger(KeyInput.KEY_F12));
            addKeys();
        }
    }

    private void addKeys() {
        map(PAUSE, KeyInput.KEY_SPACE, KeyInput.KEY_P);
        map(FASTER, KeyInput.KEY_EQUALS, KeyInput.KEY_ADD);
        map(SLOWER, KeyInput.KEY_MINUS, KeyInput.KEY_SUBTRACT);
        map(RECONNECT, KeyInput.KEY_R);
        map(MORE, KeyInput.KEY_RBRACKET);
        map(LESS, KeyInput.KEY_LBRACKET);
        map(EXPORT, KeyInput.KEY_X);
        map(HELP, KeyInput.KEY_H, KeyInput.KEY_F1);
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
            case RECONNECT -> reconnect();
            case MORE -> setDemandScale(left.demandScale() + 0.1);
            case LESS -> setDemandScale(left.demandScale() - 0.1);
            case EXPORT -> exportStatistics();
            case HELP -> hud.toggleHelp();
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
        left.setDemandScale(scale);
        right.setDemandScale(scale);
        hud.flash(String.format(Locale.ROOT, "Traffic %.0f%% on both sides", scale * 100));
    }

    private void reconnect() {
        StringBuilder ports = new StringBuilder();
        for (int side = 0; side < 2; side++) {
            if ((side == 0 ? left : right).controller() instanceof ExternalController external) {
                external.connect();
                ports.append(ports.isEmpty() ? "" : " and ").append(config.host()).append(':').append(config.portFor(side));
            }
        }
        hud.flash(ports.isEmpty() ? "Neither side uses an external controller" : "Connecting to " + ports);
    }

    private void exportStatistics() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = Path.of("duel-statistics-" + stamp + ".csv");
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            String[] names = hud.sideNames();
            out.write(DuelExport.merge(names[0], csv(left), names[1], csv(right)));
            hud.flash("Saved " + file.toAbsolutePath());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot save statistics", e);
            hud.flash("Could not save statistics: " + e.getMessage());
        }
    }

    private static String csv(Simulation sim) throws IOException {
        StringWriter out = new StringWriter();
        sim.statistics().writeMovementsCsv(out, sim.time());
        return out.toString();
    }

    @Override
    public void simpleUpdate(float tpf) {
        frames++;
        if (frames == 2) {
            // the camera rig exists once the left renderer has been initialised
            CameraRig rig = leftRenderer.cameraRig();
            rig.preset(config.camera());
            // the two sides have different road users, so there is nothing to follow on both
            rig.setPicking(false);
        }
        countFps(tpf);

        int steps = clock.advance(tpf);
        for (int i = 0; i < steps; i++) {
            left.step();
            right.step();
        }
        leftRenderer.update(left, clock.alpha());
        rightRenderer.update(right, clock.alpha());
        if (config.duration() > 0 && config.screenshot() == null && left.time() >= config.duration()) {
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
        for (Simulation sim : List.of(left, right)) {
            try {
                sim.controller().close();
            } catch (RuntimeException e) {
                LOG.log(Level.FINE, "Error while closing a controller", e);
            }
        }
        super.destroy();
    }
}
