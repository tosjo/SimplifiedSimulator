package simplifiedsimulator.render.preview;

import com.jme3.app.SimpleApplication;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeSystem;
import com.jme3.texture.FrameBuffer;
import com.jme3.util.BufferUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.logging.Level;
import java.util.logging.Logger;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.render.CameraRig;
import simplifiedsimulator.render.Coords;
import simplifiedsimulator.render.CrossingRenderer;
import simplifiedsimulator.render.RenderQuality;

/**
 * Development tool: shows the renderer with fake traffic, and can save a screenshot.
 *
 * <pre>
 *   --time HH:MM            time of day (default 14:00)
 *   --date YYYY-MM-DD       date, for the path of the sun (default today)
 *   --camera PRESET         OVERVIEW, BIRDS_EYE, STREET_WEST, STREET_EAST, STREET_NORTH, STREET_SOUTH
 *   --look x,y,h,tx,ty,th   camera at (x, y, height) looking at (tx, ty, th), network metres
 *   --follow                follow the first car that appears (chase camera)
 *   --quality Q             HIGH, MEDIUM or LOW
 *   --warmup SECONDS        simulate this long before showing (default 90)
 *   --size WxH              window size (default 1600x900)
 *   --screenshot FILE       render a few frames, save a PNG and exit
 * </pre>
 */
public final class RenderPreview extends SimpleApplication {

    private static final double STEP = 0.1;

    private final Options options;
    private FakeSimulation simulation;
    private CrossingRenderer renderer;
    private double accumulator;
    private int frames;

    private record Options(double time, java.time.LocalDate date, CameraRig.Preset preset, double[] look, boolean follow, RenderQuality quality,
                           double warmup, int width, int height, Path screenshot) {
    }

    private RenderPreview(Options options) {
        super();
        this.options = options;
    }

    public static void main(String[] args) {
        Logger.getLogger("com.jme3").setLevel(Level.WARNING);
        Options o = parse(args);
        RenderPreview app = new RenderPreview(o);
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Crossing renderer preview");
        settings.setResolution(o.width, o.height);
        settings.setSamples(o.screenshot != null ? 1 : 4);
        settings.setVSync(true);
        settings.setAudioRenderer(null);
        settings.setGammaCorrection(false);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.setPauseOnLostFocus(false);
        app.start();
    }

    private static Options parse(String[] args) {
        double time = 14 * 3600;
        java.time.LocalDate date = java.time.LocalDate.now();
        CameraRig.Preset preset = CameraRig.Preset.OVERVIEW;
        double[] look = null;
        boolean follow = false;
        RenderQuality quality = RenderQuality.HIGH;
        double warmup = 90;
        int w = 1600;
        int h = 900;
        Path shot = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--time" -> {
                    String[] hm = args[++i].split(":");
                    time = Integer.parseInt(hm[0]) * 3600 + Integer.parseInt(hm[1]) * 60;
                }
                case "--date" -> date = java.time.LocalDate.parse(args[++i]);
                case "--camera" -> preset = CameraRig.Preset.valueOf(args[++i].toUpperCase(java.util.Locale.ROOT));
                case "--look" -> {
                    String[] p = args[++i].split(",");
                    look = new double[6];
                    for (int k = 0; k < 6; k++) {
                        look[k] = Double.parseDouble(p[k]);
                    }
                }
                case "--follow" -> follow = true;
                case "--quality" -> quality = RenderQuality.valueOf(args[++i].toUpperCase(java.util.Locale.ROOT));
                case "--warmup" -> warmup = Double.parseDouble(args[++i]);
                case "--size" -> {
                    String[] p = args[++i].split("x");
                    w = Integer.parseInt(p[0]);
                    h = Integer.parseInt(p[1]);
                }
                case "--screenshot" -> shot = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        return new Options(time, date, preset, look, follow, quality, warmup, w, h, shot);
    }

    @Override
    public void simpleInitApp() {
        setDisplayStatView(false);
        setDisplayFps(options.screenshot == null);
        Network network = Network.loadDefault();
        simulation = new FakeSimulation(network, options.time - options.warmup, 42);
        for (double t = 0; t < options.warmup; t += STEP) {
            simulation.step(STEP);
        }
        renderer = new CrossingRenderer(network, options.quality);
        renderer.setTimeOfDayOverride(OptionalDouble.of(options.time));
        renderer.setDate(options.date);
        stateManager.attach(renderer);
    }

    @Override
    public void simpleUpdate(float tpf) {
        frames++;
        if (frames == 2) {
            // the renderer's filters exist now; capture after them so the image is final
            if (options.screenshot != null) {
                viewPort.addProcessor(new Capture(options.screenshot));
            }
            // the camera rig initialises with the renderer; apply the requested view now
            if (options.look != null) {
                double[] l = options.look;
                renderer.cameraRig().look(Coords.world(l[0], l[1], l[2]), Coords.world(l[3], l[4], l[5]));
            } else {
                renderer.cameraRig().preset(options.preset);
            }
        }
        if (options.follow && frames == 3) {
            simulation.agents().stream().filter(a -> a.type().isMotorVehicle()).findFirst()
                    .ifPresent(a -> renderer.cameraRig().follow(a.id(), false));
        }
        // screenshots are taken of a frozen moment so that they are reproducible
        if (options.screenshot == null) {
            accumulator += tpf;
            while (accumulator >= STEP) {
                simulation.step(STEP);
                accumulator -= STEP;
            }
        }
        renderer.update(simulation, (float) (accumulator / STEP));
    }

    /** Saves the final on-screen image of the 8th frame and stops the application. */
    private final class Capture implements SceneProcessor {
        private final Path file;
        private RenderManager rm;
        private boolean done;

        Capture(Path file) {
            this.file = file;
        }

        @Override
        public void initialize(RenderManager rm, ViewPort vp) {
            this.rm = rm;
        }

        @Override
        public void reshape(ViewPort vp, int w, int h) {
            // nothing to do
        }

        @Override
        public boolean isInitialized() {
            return rm != null;
        }

        @Override
        public void preFrame(float tpf) {
            // nothing to do
        }

        @Override
        public void postQueue(RenderQueue rq) {
            // nothing to do
        }

        @Override
        public void postFrame(FrameBuffer out) {
            if (done || frames < 8) {
                return;
            }
            done = true;
            int w = cam.getWidth();
            int h = cam.getHeight();
            ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
            rm.getRenderer().readFrameBuffer(null, buf);
            try {
                Path parent = file.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (OutputStream os = Files.newOutputStream(file)) {
                    JmeSystem.writeImageFile(os, "png", buf, w, h);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            System.out.println("Saved " + file);
            int[] counts = new int[2];
            rootNode.depthFirstTraversal(sp -> {
                if (sp instanceof com.jme3.scene.Geometry g && g.getCullHint() != com.jme3.scene.Spatial.CullHint.Always) {
                    counts[0]++;
                    counts[1] += g.getTriangleCount();
                }
            });
            System.out.printf("Scene: %d geometries, %d triangles, %d road users%n", counts[0], counts[1],
                    renderer.agentCount());
            stop();
        }

        @Override
        public void cleanup() {
            // nothing to release
        }

        @Override
        public void setProfiler(AppProfiler profiler) {
            // not profiled
        }
    }
}
