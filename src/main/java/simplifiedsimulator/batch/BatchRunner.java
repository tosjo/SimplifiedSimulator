package simplifiedsimulator.batch;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import simplifiedsimulator.control.SignalControllers;
import simplifiedsimulator.demand.DemandModel;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.sim.Simulation;
import simplifiedsimulator.sim.SimulationOptions;
import simplifiedsimulator.stats.Summary;

/**
 * Runs the simulation without graphics, for several controllers and random seeds, and
 * prints a comparison table.
 *
 * <pre>
 * ./gradlew batch --args="--controllers fixed,actuated --seeds 3 --duration 3600 --start 08:00"
 * </pre>
 */
public final class BatchRunner {

    static final String USAGE = """
            Usage: batch [options]

              --controllers <list>  comma-separated: actuated, fixed (default: fixed,actuated)
              --seeds <n>           runs per controller with different random traffic (default: 3)
              --duration <sec>      simulated seconds per run (default: 3600)
              --warmup <sec>        simulated seconds before measuring starts (default: 300)
              --start <hh:mm>       time of day at the start of the warm-up (default: 07:25)
              --scale <factor>      multiply the demand (default: 1.0)
              --demand <file>       demand CSV instead of the bundled estimates
              --csv <file>          also write every run to a CSV file
            """;

    /** Result of one run. */
    public record Run(String controller, long seed, Summary summary, double seconds) {
    }

    private BatchRunner() {
    }

    public static void main(String[] args) throws IOException {
        List<String> controllers = List.of("fixed", "actuated");
        int seeds = 3;
        double duration = 3600;
        double warmup = 300;
        double start = 7 * 3600 + 25 * 60;
        double scale = 1.0;
        Path demandFile = null;
        Path csv = null;
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--controllers" -> controllers = Arrays.asList(args[++i].split(","));
                    case "--seeds" -> seeds = Integer.parseInt(args[++i]);
                    case "--duration" -> duration = Double.parseDouble(args[++i]);
                    case "--warmup" -> warmup = Double.parseDouble(args[++i]);
                    case "--start" -> start = parseTime(args[++i]);
                    case "--scale" -> scale = Double.parseDouble(args[++i]);
                    case "--demand" -> demandFile = Path.of(args[++i]);
                    case "--csv" -> csv = Path.of(args[++i]);
                    case "--help", "-h" -> {
                        System.out.print(USAGE);
                        return;
                    }
                    default -> throw new IllegalArgumentException("Unknown option " + args[i]);
                }
            }
        } catch (RuntimeException e) {
            System.err.println(e.getMessage() == null ? e.toString() : e.getMessage());
            System.err.print(USAGE);
            System.exit(2);
            return;
        }

        Network network = Network.loadDefault();
        DemandModel demand = demandFile == null ? DemandModel.loadDefault() : DemandModel.load(demandFile);
        SimulationOptions base = SimulationOptions.defaults().withDemand(demand).withDemandScale(scale)
                .withStartTimeOfDay(start);
        List<Run> runs = new ArrayList<>();
        for (String controller : controllers) {
            for (long seed = 1; seed <= seeds; seed++) {
                runs.add(run(network, base.withSeed(seed), controller, warmup, duration));
                System.err.printf(Locale.ROOT, "%s seed %d done%n", controller, seed);
            }
        }
        printTable(runs, System.out);
        if (csv != null) {
            writeCsv(runs, csv);
        }
    }

    /** Runs one simulation: a warm-up, then a measured period whose results are returned. */
    public static Run run(Network network, SimulationOptions options, String controller, double warmup,
                          double duration) {
        long t0 = System.nanoTime();
        Simulation sim = new Simulation(network, options);
        sim.setController(SignalControllers.create(controller, "localhost", 1337));
        sim.run(warmup);
        sim.resetStatistics();
        sim.run(duration);
        return new Run(controller, options.seed(), sim.summary(), (System.nanoTime() - t0) / 1e9);
    }

    static void printTable(List<Run> runs, PrintStream out) {
        out.printf(Locale.ROOT, "%-10s %4s %8s %9s %6s %8s %8s %8s %8s %8s %6s %6s %6s%n", "controller", "seed",
                "vehicles", "delay(s)", "stops", "bus(s)", "bike(s)", "walk(s)", "maxQueue", "waiting", "red", "crash",
                "sig");
        for (Run r : runs) {
            Summary s = r.summary();
            out.printf(Locale.ROOT, "%-10s %4d %8d %9.1f %6.2f %8.1f %8.1f %8.1f %8d %8d %6d %6d %6d%n", r.controller(),
                    r.seed(), s.motorCompleted(), s.motorDelay(), s.motorStops(), s.busDelay(), s.bikeDelay(),
                    s.footDelay(), s.maxQueue(), s.entryQueue(), s.redLightRuns(), s.conflictViolations(),
                    s.signalConflicts());
        }
        out.println();
        out.println("Averages over the seeds:");
        runs.stream().map(Run::controller).distinct().forEach(c -> {
            List<Summary> list = runs.stream().filter(r -> r.controller().equals(c)).map(Run::summary).toList();
            out.printf(Locale.ROOT, "  %-10s vehicles %6.0f  delay %5.1f s  stops %4.2f  cyclists %5.1f s  "
                            + "pedestrians %5.1f s  buses %5.1f s%n", c,
                    list.stream().mapToInt(Summary::motorCompleted).average().orElse(0),
                    list.stream().mapToDouble(Summary::motorDelay).average().orElse(Double.NaN),
                    list.stream().mapToDouble(Summary::motorStops).average().orElse(Double.NaN),
                    list.stream().mapToDouble(Summary::bikeDelay).average().orElse(Double.NaN),
                    list.stream().mapToDouble(Summary::footDelay).average().orElse(Double.NaN),
                    list.stream().mapToDouble(Summary::busDelay).average().orElse(Double.NaN));
        });
    }

    static void writeCsv(List<Run> runs, Path file) throws IOException {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("controller,seed,vehicles,mean_delay_s,mean_stops,bus_delay_s,bike_delay_s,foot_delay_s,"
                    + "max_queue,entry_queue,red_light_runs,conflict_violations,signal_conflicts,runtime_s\n");
            for (Run r : runs) {
                Summary s = r.summary();
                w.write(String.format(Locale.ROOT, "%s,%d,%d,%.2f,%.3f,%.2f,%.2f,%.2f,%d,%d,%d,%d,%d,%.1f%n",
                        r.controller(), r.seed(), s.motorCompleted(), s.motorDelay(), s.motorStops(), s.busDelay(),
                        s.bikeDelay(), s.footDelay(), s.maxQueue(), s.entryQueue(), s.redLightRuns(),
                        s.conflictViolations(), s.signalConflicts(), r.seconds()));
            }
        }
    }

    static double parseTime(String hhmm) {
        String[] parts = hhmm.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected a time like 08:00, got " + hhmm);
        }
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        if (h < 0 || h > 23 || m < 0 || m > 59) {
            throw new IllegalArgumentException("Invalid time " + hhmm);
        }
        return h * 3600 + m * 60;
    }
}
