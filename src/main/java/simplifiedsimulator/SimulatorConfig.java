package simplifiedsimulator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Command line options of the simulator.
 *
 * @param host      host name of the traffic light controller
 * @param port      TCP port of the traffic light controller
 * @param scenario  built-in scenario name ({@code small}, {@code medium}, {@code large})
 *                  or a path to a scenario file
 * @param connect   connect to the controller on start-up instead of waiting for Space
 * @param demo      start with the built-in demo light controller
 * @param headless  run without a window (useful for testing a controller)
 * @param duration  stop after this many seconds; 0 runs until the window is closed
 * @param recordTo  if not {@code null}, start the route recorder instead of the
 *                  simulation and save recorded routes to this file
 */
public record SimulatorConfig(
        String host,
        int port,
        String scenario,
        boolean connect,
        boolean demo,
        boolean headless,
        int duration,
        Path recordTo) {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 1337;
    public static final String DEFAULT_SCENARIO = "large";
    public static final Set<String> BUILT_IN_SCENARIOS = Set.of("small", "medium", "large");

    public static final String USAGE = """
            Usage: simplified-simulator [options]

              --host <name>        controller host (default: localhost)
              --port <number>      controller port (default: 1337)
              --scenario <name>    small, medium, large or a path to a scenario XML file
                                   (default: large)
              --connect            connect to the controller on start-up
              --demo               use the built-in fixed-time light controller
              --headless           run without opening a window
              --duration <sec>     stop after the given number of seconds
              --record <file>      draw a new route on the map and save it to <file>
              --help               show this help
            """;

    public SimulatorConfig {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(scenario, "scenario");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535, got " + port);
        }
        if (duration < 0) {
            throw new IllegalArgumentException("Duration must not be negative, got " + duration);
        }
        if (host.isBlank()) {
            throw new IllegalArgumentException("Host must not be empty");
        }
        if (recordTo != null && headless) {
            throw new IllegalArgumentException("--record needs a window and cannot be used with --headless");
        }
    }

    public static SimulatorConfig defaults() {
        return new SimulatorConfig(DEFAULT_HOST, DEFAULT_PORT, DEFAULT_SCENARIO, false, false, false, 0, null);
    }

    /**
     * Parses command line arguments.
     *
     * @throws IllegalArgumentException on unknown options or invalid values
     * @throws HelpRequested            if {@code --help} was given
     */
    public static SimulatorConfig parse(String... args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        String scenario = DEFAULT_SCENARIO;
        boolean connect = false;
        boolean demo = false;
        boolean headless = false;
        int duration = 0;
        Path recordTo = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--host" -> host = value(args, ++i, arg);
                case "--port" -> port = intValue(args, ++i, arg);
                case "--scenario" -> scenario = value(args, ++i, arg);
                case "--connect" -> connect = true;
                case "--demo" -> demo = true;
                case "--headless" -> headless = true;
                case "--duration" -> duration = intValue(args, ++i, arg);
                case "--record" -> recordTo = Path.of(value(args, ++i, arg));
                case "--help", "-h" -> throw new HelpRequested();
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        return new SimulatorConfig(host, port, scenario, connect, demo, headless, duration, recordTo);
    }

    /** Opens the configured scenario, either from the classpath or from disk. */
    public InputStream openScenario() {
        String name = scenario.toLowerCase(Locale.ROOT);
        if (BUILT_IN_SCENARIOS.contains(name)) {
            InputStream in = SimulatorConfig.class.getResourceAsStream("/scenarios/" + name + ".xml");
            if (in == null) {
                throw new IllegalStateException("Built-in scenario " + name + " is missing from the classpath");
            }
            return in;
        }
        try {
            return Files.newInputStream(Path.of(scenario));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open scenario file " + scenario, e);
        }
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private static int intValue(String[] args, int index, String option) {
        String value = value(args, index, option);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Value for " + option + " must be a number, got '" + value + "'", e);
        }
    }

    /** Signals that the user asked for the usage text. */
    public static final class HelpRequested extends RuntimeException {
        private static final long serialVersionUID = 1L;

        HelpRequested() {
            super(null, null, false, false);
        }
    }
}
