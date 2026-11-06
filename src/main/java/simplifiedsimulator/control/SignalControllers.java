package simplifiedsimulator.control;

import java.util.List;
import java.util.Locale;

/** Creates controllers by name, for command line options and the user interface. */
public final class SignalControllers {

    /** Names accepted by {@link #create}. */
    public static final List<String> NAMES = List.of("actuated", "fixed", "external");

    private SignalControllers() {
    }

    /**
     * @param name {@code actuated}, {@code fixed} or {@code external}
     * @param host controller host, for {@code external}
     * @param port controller port, for {@code external}
     * @throws IllegalArgumentException for an unknown name
     */
    public static SignalController create(String name, String host, int port) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "actuated" -> new ActuatedController();
            case "fixed" -> new FixedTimeController();
            case "external" -> new ExternalController(host, port);
            default -> throw new IllegalArgumentException("Unknown controller '" + name + "', expected one of " + NAMES);
        };
    }
}
