package simplifiedsimulator;

import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import simplifiedsimulator.control.SignalControllers;
import simplifiedsimulator.demand.DemandModel;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.sim.Simulation;
import simplifiedsimulator.sim.SimulationOptions;
import simplifiedsimulator.stats.MovementStats;
import simplifiedsimulator.stats.Summary;

/** Entry point: parses the command line, prepares the simulation and opens the window. */
public final class Main {

    /**
     * Simulated time before the window opens, so the crossing is already busy. The clock is
     * started this much earlier, so it shows the requested start time when the window opens.
     */
    static final double WARM_UP = 180;

    /** Keep the configured loggers from being garbage collected. */
    private static final Logger JME_LOG = Logger.getLogger("com.jme3");
    private static final Logger ASSET_CONFIG_LOG = Logger.getLogger("com.jme3.asset.AssetConfig");

    private Main() {
    }

    public static void main(String[] args) {
        JME_LOG.setLevel(Level.WARNING);
        // jME warns about every optional asset loader that is not on the classpath; none are used
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

        if (config.isDuel()) {
            startDuel(config);
            return;
        }

        Simulation simulation;
        try {
            simulation = createSimulation(config);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Cannot start the simulation: " + e.getMessage());
            System.exit(1);
            return;
        }

        if (config.headless()) {
            System.exit(runHeadless(simulation, config, System.out) ? 0 : 1);
            return;
        }
        if (config.screenshot() != null) {
            simulation.run(config.duration());
        }
        startWindow(simulation, config);
    }

    private static void startDuel(SimulatorConfig config) {
        Simulation left;
        Simulation right;
        // both sides get the same start; an external controller cannot be warmed up, so then neither is
        boolean warmUp = !config.duel().contains("external");
        try {
            left = createSimulation(config, config.controllerFor(0), config.portFor(0), warmUp);
            right = createSimulation(config, config.controllerFor(1), config.portFor(1), warmUp);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Cannot start the simulation: " + e.getMessage());
            System.exit(1);
            return;
        }
        if (config.screenshot() != null) {
            left.run(config.duration());
            right.run(config.duration());
        }
        start(new DuelSimulator(config, left, right), config, "Controller duel - Harlingerstraatweg, Leeuwarden");
    }

    /** Builds the simulation, warms it up and attaches the chosen controller. */
    static Simulation createSimulation(SimulatorConfig config) throws IOException {
        // an external controller has to see the simulation from its first second
        return createSimulation(config, config.controller(), config.port(), !"external".equals(config.controller()));
    }

    /** Builds a simulation with the given controller; both sides of a duel get the same traffic from the seed. */
    static Simulation createSimulation(SimulatorConfig config, String controller, int port, boolean warmUp)
            throws IOException {
        Network network = Network.loadDefault();
        DemandModel demand = config.demandFile() == null ? DemandModel.loadDefault() : DemandModel.load(config.demandFile());
        // the warm-up runs before the window opens, so only then does the clock start earlier
        double start = (config.start() - (warmUp ? WARM_UP : 0) + 86_400) % 86_400;
        SimulationOptions options = SimulationOptions.defaults()
                .withDemand(demand)
                .withDemandScale(config.demandScale())
                .withSeed(config.seed())
                .withStartTimeOfDay(start);
        Simulation simulation = new Simulation(network, options);
        simulation.setController(SignalControllers.create(controller, config.host(), port));
        if (warmUp) {
            simulation.run(WARM_UP);
            simulation.resetStatistics();
        }
        return simulation;
    }

    private static void startWindow(Simulation simulation, SimulatorConfig config) {
        start(new SimplifiedSimulator(config, simulation), config, "Simplified Simulator - Harlingerstraatweg, Leeuwarden");
    }

    private static void start(SimpleApplication app, SimulatorConfig config, String title) {
        AppSettings settings = new AppSettings(true);
        settings.setTitle(title);
        settings.setResolution(1600, 900);
        settings.setResizable(true);
        settings.setSamples(config.screenshot() != null ? 1 : 4);
        settings.setVSync(true);
        settings.setGammaCorrection(false);
        // The simulation has no sound; skipping OpenAL also avoids failures on
        // machines without an audio device.
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.setPauseOnLostFocus(false);
        app.start();
    }

    /**
     * Runs the simulation without a window and prints the results.
     *
     * @return {@code false} if anything unsafe happened (conflicting greens or overlapping road users)
     */
    static boolean runHeadless(Simulation simulation, SimulatorConfig config, PrintStream out) {
        out.printf(Locale.ROOT, "Simulating %.0f s with the %s controller...%n", config.duration(),
                simulation.controller().name());
        simulation.run(config.duration());
        double now = simulation.time();
        out.printf(Locale.ROOT, "%n%-40s %9s %9s %7s %6s %9s%n", "Movement", "Completed", "Delay (s)", "Stops",
                "Queue", "Per hour");
        for (MovementStats m : simulation.statistics().movements(now)) {
            out.printf(Locale.ROOT, "%-40s %9d %9.1f %7.2f %6d %9.0f%n", m.label(), m.completed(), m.meanDelay(),
                    m.meanStops(), m.queue(), m.throughput());
        }
        Summary s = simulation.summary();
        out.printf(Locale.ROOT, "%nMotor vehicles: %d, average delay %.1f s, %.2f stops%n", s.motorCompleted(),
                s.motorDelay(), s.motorStops());
        out.printf(Locale.ROOT, "Average delay: buses %s, cyclists %s, pedestrians %s%n", seconds(s.busDelay()),
                seconds(s.bikeDelay()), seconds(s.footDelay()));
        out.printf(Locale.ROOT, "Safety: %d red-light runs, %d conflicting greens, %d overlaps%n", s.redLightRuns(),
                s.signalConflicts(), s.conflictViolations());
        if (config.csv() != null) {
            try (Writer w = Files.newBufferedWriter(config.csv(), StandardCharsets.UTF_8)) {
                simulation.statistics().writeMovementsCsv(w, now);
                out.println("Wrote " + config.csv().toAbsolutePath());
            } catch (IOException e) {
                System.err.println("Cannot write " + config.csv() + ": " + e.getMessage());
                return false;
            }
        }
        try {
            simulation.controller().close();
        } catch (RuntimeException e) {
            // closing an external connection is best effort
        }
        return s.signalConflicts() == 0 && s.conflictViolations() == 0;
    }

    /** Formats an average in seconds, or "-" when nobody has finished a trip yet. */
    private static String seconds(double value) {
        return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%.1f s", value);
    }
}
