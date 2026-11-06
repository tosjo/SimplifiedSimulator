package simplifiedsimulator;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import simplifiedsimulator.scenario.ScenarioReader;
import simplifiedsimulator.scenario.SpawnEvent;

/** Entry point: parses the command line and starts the simulator. */
public final class Main {

    /**
     * jME logs a warning for every optional asset loader that is not on the classpath.
     * None of them are used here, so keep the console readable. The reference keeps
     * the configured logger from being garbage collected.
     */
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private static final Logger ASSET_CONFIG_LOG = Logger.getLogger("com.jme3.asset.AssetConfig");

    private Main() {
    }

    public static void main(String[] args) {
        ASSET_CONFIG_LOG.setLevel(Level.SEVERE);

        SimulatorConfig config;
        try {
            config = SimulatorConfig.parse(args);
        } catch (SimulatorConfig.HelpRequested e) {
            System.out.print(SimulatorConfig.USAGE);
            return;
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println();
            System.err.print(SimulatorConfig.USAGE);
            System.exit(2);
            return;
        }

        List<SpawnEvent> events;
        try {
            events = loadEvents(config);
        } catch (UncheckedIOException | ScenarioReader.ScenarioException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        SimplifiedSimulator app = new SimplifiedSimulator(config, events);
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Simplified Simulator");
        settings.setResolution(1280, 720);
        settings.setSamples(4);
        settings.setVSync(true);
        settings.setFrameRate(60);
        // The simulation has no sound; skipping OpenAL also avoids failures on
        // machines without an audio device.
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.setPauseOnLostFocus(false);

        if (config.headless()) {
            app.start(JmeContext.Type.Headless);
        } else {
            app.start();
        }
    }

    private static List<SpawnEvent> loadEvents(SimulatorConfig config) {
        if (config.recordTo() != null) {
            // Keep the map clear of traffic while drawing a route.
            return List.of();
        }
        try (InputStream in = config.openScenario()) {
            List<SpawnEvent> events = ScenarioReader.read(in);
            LOG.info(() -> "Loaded scenario '" + config.scenario() + "' with " + events.size() + " road users");
            return events;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
