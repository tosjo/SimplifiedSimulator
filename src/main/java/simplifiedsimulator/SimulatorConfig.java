package simplifiedsimulator;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import simplifiedsimulator.control.SignalControllers;
import simplifiedsimulator.render.CameraRig;
import simplifiedsimulator.render.RenderQuality;

/**
 * Command line options of the simulator.
 *
 * @param controller  signal controller: {@code actuated}, {@code fixed} or {@code external}
 * @param host        host name of an external traffic light controller
 * @param port        TCP port of an external traffic light controller
 * @param start       time of day the simulation starts, in seconds after midnight
 * @param date        date used for the position of the sun
 * @param speed       simulation speed relative to real time
 * @param demandScale factor applied to all traffic volumes
 * @param demandFile  CSV file with traffic volumes per route, or {@code null} for the built-in estimates
 * @param seed        random seed; the same seed gives the same traffic
 * @param quality     graphics quality
 * @param headless    run without a window and print the results
 * @param duration    stop after this many simulated seconds; 0 runs until the window is closed
 * @param csv         write statistics to this file (headless mode), or {@code null}
 * @param camera      initial camera viewpoint
 * @param screenshot  save a screenshot to this file after {@code duration} seconds and exit, or {@code null}
 * @param duel        two controllers to show side by side, or an empty list for the normal single view
 */
public record SimulatorConfig(
        String controller,
        String host,
        int port,
        double start,
        LocalDate date,
        double speed,
        double demandScale,
        Path demandFile,
        long seed,
        RenderQuality quality,
        boolean headless,
        double duration,
        Path csv,
        CameraRig.Preset camera,
        Path screenshot,
        List<String> duel) {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 1337;

    public static final String USAGE = """
            Usage: simplified-simulator [options]

            Simulation
              --controller <name>  actuated (default), fixed or external
              --start <HH:MM>      time of day to start at (default 07:30)
              --speed <factor>     simulation speed, 1 = real time (default 1)
              --scale <factor>     multiply all traffic volumes (default 1)
              --demand <file.csv>  traffic volumes per route (default: built-in estimates)
              --seed <number>      random seed; the same seed gives the same traffic (default 1)
              --duel <a>,<b>       two controllers side by side on the same traffic,
                                   for example actuated,external (default quality medium)

            External traffic light controller
              --host <name>        controller host (default localhost)
              --port <number>      controller port (default 1337)

            Display
              --quality <level>    high (default), medium or low
              --date <YYYY-MM-DD>  date for the position of the sun (default today)
              --camera <view>      overview, birds_eye, street_west, street_east,
                                   street_north or street_south

            Batch use
              --headless           run without a window and print the results
              --duration <sec>     stop after this many simulated seconds
              --csv <file>         with --headless: write statistics per movement
              --screenshot <file>  render, save a PNG after --duration seconds and exit

              --help               show this help
            """;

    public SimulatorConfig {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(camera, "camera");
        if (!SignalControllers.NAMES.contains(controller)) {
            throw new IllegalArgumentException("Unknown controller '" + controller + "', expected one of "
                    + String.join(", ", SignalControllers.NAMES));
        }
        if (host.isBlank()) {
            throw new IllegalArgumentException("Host must not be empty");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535, got " + port);
        }
        if (start < 0 || start >= 24 * 3600) {
            throw new IllegalArgumentException("Start time must be between 00:00 and 23:59");
        }
        if (!(speed > 0) || speed > 64) {
            throw new IllegalArgumentException("Speed must be more than 0 and at most 64, got " + speed);
        }
        if (!(demandScale >= 0) || demandScale > 5) {
            throw new IllegalArgumentException("Scale must be between 0 and 5, got " + demandScale);
        }
        if (duration < 0) {
            throw new IllegalArgumentException("Duration must not be negative, got " + duration);
        }
        if (headless && duration == 0) {
            throw new IllegalArgumentException("--headless needs --duration");
        }
        if (headless && screenshot != null) {
            throw new IllegalArgumentException("--screenshot needs a window and cannot be used with --headless");
        }
        if (csv != null && !headless) {
            throw new IllegalArgumentException("--csv can only be used with --headless");
        }
        duel = List.copyOf(Objects.requireNonNull(duel, "duel"));
        validateDuel(duel, headless, port);
    }

    private static void validateDuel(List<String> duel, boolean headless, int port) {
        if (duel.isEmpty()) {
            return;
        }
        if (duel.size() != 2) {
            throw new IllegalArgumentException("--duel needs exactly two controllers, for example actuated,fixed");
        }
        for (String name : duel) {
            if (!SignalControllers.NAMES.contains(name)) {
                throw new IllegalArgumentException("Unknown controller '" + name + "' in --duel, expected one of "
                        + String.join(", ", SignalControllers.NAMES));
            }
        }
        if (headless) {
            throw new IllegalArgumentException("--duel needs a window and cannot be used with --headless");
        }
        if (bothExternal(duel) && port == 65535) {
            throw new IllegalArgumentException("Two external controllers use --port and --port + 1, so --port must be below 65535");
        }
    }

    private static boolean bothExternal(List<String> duel) {
        return duel.size() == 2 && duel.stream().allMatch("external"::equals);
    }

    public boolean isDuel() {
        return !duel.isEmpty();
    }

    /** The controller of side 0 (left) or 1 (right) of a duel. */
    public String controllerFor(int side) {
        checkSide(side);
        return duel.get(side);
    }

    /** The external controller port of a duel side: two external controllers get neighbouring ports. */
    public int portFor(int side) {
        checkSide(side);
        return side == 1 && bothExternal(duel) ? port + 1 : port;
    }

    private void checkSide(int side) {
        if (!isDuel() || side < 0 || side > 1) {
            throw new IllegalArgumentException("No duel side " + side);
        }
    }

    public static SimulatorConfig defaults() {
        return new SimulatorConfig("actuated", DEFAULT_HOST, DEFAULT_PORT, 7.5 * 3600, LocalDate.now(), 1, 1, null, 1,
                RenderQuality.HIGH, false, 0, null, CameraRig.Preset.OVERVIEW, null, List.of());
    }

    /**
     * Parses command line arguments.
     *
     * @throws IllegalArgumentException on unknown options or invalid values
     * @throws HelpRequested            if {@code --help} was given
     */
    public static SimulatorConfig parse(String... args) {
        SimulatorConfig d = defaults();
        String controller = d.controller;
        String host = d.host;
        int port = d.port;
        double start = d.start;
        LocalDate date = d.date;
        double speed = d.speed;
        double scale = d.demandScale;
        Path demand = null;
        long seed = d.seed;
        RenderQuality quality = d.quality;
        boolean headless = false;
        double duration = 0;
        Path csv = null;
        CameraRig.Preset camera = d.camera;
        Path screenshot = null;
        List<String> duel = List.of();
        boolean qualityGiven = false;
        boolean controllerGiven = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--controller" -> {
                    controller = value(args, ++i, arg).toLowerCase(Locale.ROOT);
                    controllerGiven = true;
                }
                case "--duel" -> duel = parseDuel(value(args, ++i, arg));
                case "--host" -> host = value(args, ++i, arg);
                case "--port" -> port = (int) number(args, ++i, arg);
                case "--start" -> start = parseTime(value(args, ++i, arg));
                case "--date" -> date = parseDate(value(args, ++i, arg));
                case "--speed" -> speed = number(args, ++i, arg);
                case "--scale" -> scale = number(args, ++i, arg);
                case "--demand" -> demand = Path.of(value(args, ++i, arg));
                case "--seed" -> seed = (long) number(args, ++i, arg);
                case "--quality" -> {
                    quality = parseEnum(RenderQuality.class, value(args, ++i, arg), arg);
                    qualityGiven = true;
                }
                case "--headless" -> headless = true;
                case "--duration" -> duration = number(args, ++i, arg);
                case "--csv" -> csv = Path.of(value(args, ++i, arg));
                case "--camera" -> camera = parseEnum(CameraRig.Preset.class, value(args, ++i, arg), arg);
                case "--screenshot" -> screenshot = Path.of(value(args, ++i, arg));
                case "--help", "-h" -> throw new HelpRequested();
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if (!duel.isEmpty() && controllerGiven) {
            throw new IllegalArgumentException("--duel picks both controllers and cannot be used with --controller");
        }
        if (!duel.isEmpty() && !qualityGiven) {
            // two scenes on one graphics card
            quality = RenderQuality.MEDIUM;
        }
        return new SimulatorConfig(controller, host, port, start, date, speed, scale, demand, seed, quality, headless,
                duration, csv, camera, screenshot, duel);
    }

    private static List<String> parseDuel(String text) {
        return Arrays.stream(text.split(",", -1)).map(n -> n.trim().toLowerCase(Locale.ROOT)).toList();
    }

    /** Parses {@code HH:MM} or {@code HH:MM:SS} into seconds after midnight. */
    static double parseTime(String text) {
        String[] parts = text.split(":");
        try {
            if (parts.length < 2 || parts.length > 3) {
                throw new NumberFormatException();
            }
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int s = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59 || s < 0 || s > 59) {
                throw new NumberFormatException();
            }
            return h * 3600 + m * 60 + s;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid time '" + text + "', expected HH:MM", e);
        }
    }

    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date '" + text + "', expected YYYY-MM-DD", e);
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String text, String option) {
        try {
            return Enum.valueOf(type, text.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value '" + text + "' for " + option, e);
        }
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private static double number(String[] args, int index, String option) {
        String text = value(args, index, option);
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number '" + text + "' for " + option, e);
        }
    }

    /** Thrown by {@link #parse} when the user asked for help. */
    public static final class HelpRequested extends RuntimeException {
        private static final long serialVersionUID = 1L;

        HelpRequested() {
            super(null, null, false, false);
        }
    }
}
