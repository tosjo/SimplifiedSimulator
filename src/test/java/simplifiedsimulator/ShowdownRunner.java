package simplifiedsimulator;

import java.io.IOException;
import java.util.Locale;
import simplifiedsimulator.control.ExternalController;
import simplifiedsimulator.sim.Simulation;
import simplifiedsimulator.stats.Summary;

/**
 * Measures one controller without a window, for comparing and tuning controllers.
 *
 * <p>Built-in controllers run as fast as possible. An external controller runs on wall-clock
 * time, so the simulation is paced at {@code --speed} times real time; start the controller
 * with the same {@code --speed}. Takes the simulator's usual options, for example
 * {@code ./gradlew showdown --args="--controller external --port 1338 --speed 16 --start 07:00 --duration 3600"}.
 */
public final class ShowdownRunner {

    private static final long CONNECT_TIMEOUT_MS = 10_000;

    private ShowdownRunner() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        SimulatorConfig config = SimulatorConfig.parse(args);
        if (config.duration() <= 0) {
            throw new IllegalArgumentException("--duration is required");
        }
        // no warm-up for anyone: an external controller cannot have one, so every run starts empty
        Simulation sim = Main.createSimulation(config, config.controller(), config.port(), false);
        long started = System.nanoTime();
        if (sim.controller() instanceof ExternalController external) {
            waitForConnection(external, config);
            pace(sim, config.duration(), config.speed());
        } else {
            sim.run(config.duration());
        }
        report(sim, config, (System.nanoTime() - started) / 1e9);
        sim.controller().close();
        System.exit(0);
    }

    private static void waitForConnection(ExternalController external, SimulatorConfig config)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS;
        while (!external.isConnected()) {
            if (System.currentTimeMillis() > deadline) {
                System.err.println("No controller on " + config.host() + ":" + config.port());
                System.exit(1);
            }
            Thread.sleep(50);
        }
    }

    /** Advances the simulation at {@code speed} times real time until {@code seconds} have passed. */
    private static void pace(Simulation sim, double seconds, double speed) throws InterruptedException {
        double dt = sim.options().timeStep();
        long start = System.nanoTime();
        // stop half a step early: floating point steps may never reach the end exactly
        while (sim.time() < seconds - dt / 2) {
            double target = Math.min(seconds, (System.nanoTime() - start) / 1e9 * speed);
            if (target - sim.time() >= dt) {
                sim.run(target - sim.time());
            } else {
                Thread.sleep(1);
            }
        }
    }

    private static void report(Simulation sim, SimulatorConfig config, double wallSeconds) {
        Summary s = sim.summary();
        System.out.printf(Locale.ROOT, "%s: %.0f s simulated in %.0f s%n", sim.controller().name(), sim.time(),
                wallSeconds);
        System.out.printf(Locale.ROOT, "  vehicles %d, delay %.1f s, stops %.2f%n", s.motorCompleted(),
                s.motorDelay(), s.motorStops());
        System.out.printf(Locale.ROOT, "  buses %.1f s, cyclists %.1f s, pedestrians %.1f s%n", s.busDelay(),
                s.bikeDelay(), s.footDelay());
        System.out.printf(Locale.ROOT, "  longest queue %d, still waiting to enter %d%n", s.maxQueue(), s.entryQueue());
        System.out.printf(Locale.ROOT, "  safety: %d red-light runs, %d conflicting greens, %d overlaps%n",
                s.redLightRuns(), s.signalConflicts(), s.conflictViolations());
        System.out.printf(Locale.ROOT, "RESULT,%s,%d,%.1f,%.2f,%.1f,%.1f,%.1f,%d,%d,%d,%d,%d%n", config.controller(),
                s.motorCompleted(), s.motorDelay(), s.motorStops(), s.busDelay(), s.bikeDelay(), s.footDelay(),
                s.maxQueue(), s.entryQueue(), s.redLightRuns(), s.signalConflicts(), s.conflictViolations());
    }
}
