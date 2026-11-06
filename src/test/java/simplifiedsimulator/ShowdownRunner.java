package simplifiedsimulator;

import java.io.IOException;
import java.util.Locale;
import simplifiedsimulator.control.ExternalController;
import simplifiedsimulator.sim.Simulation;
import simplifiedsimulator.stats.MovementStats;
import simplifiedsimulator.stats.Summary;

/**
 * Measures one controller without a window, for comparing and tuning controllers.
 *
 * <p>Runs as fast as possible. An external controller runs in lockstep: every step waits for
 * its answer, so the result does not depend on timing or network delays. Takes the
 * simulator's usual options, for example
 * {@code ./gradlew showdown --args="--controller external --port 1338 --start 07:00 --duration 3600"}.
 */
public final class ShowdownRunner {

    private static final long CONNECT_TIMEOUT_MS = 10_000;
    private static final long LOCKSTEP_TIMEOUT_MS = 10_000;

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
            external.setLockstep(true, LOCKSTEP_TIMEOUT_MS);
        }
        sim.run(config.duration());
        if (sim.controller() instanceof ExternalController external && !external.lockstep()) {
            System.err.println("The controller stopped answering; the result is not valid");
            System.exit(1);
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
        for (MovementStats m : sim.statistics().movements(sim.time())) {
            System.out.printf(Locale.ROOT, "MOVEMENT,%s,%d,%.1f,%.2f,%d%n", m.key(), m.completed(), m.meanDelay(),
                    m.meanStops(), m.queue());
        }
    }
}
