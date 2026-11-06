package simplifiedsimulator.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Movement;
import simplifiedsimulator.network.Route;
import simplifiedsimulator.sim.Agent;
import simplifiedsimulator.sim.AgentType;
import simplifiedsimulator.sim.SignalState;
import simplifiedsimulator.stats.MovementStats;

/** Builds the texts shown in the head-up display. Kept free of graphics so it can be tested. */
final class HudText {

    static final String GREY = "\\#9aa3ad#";
    static final String WHITE = "\\#ffffff#";
    static final String GREEN = "\\#7fd98b#";
    static final String AMBER = "\\#ffc34d#";
    static final String RED = "\\#ff6b6b#";

    private static final Map<String, String> APPROACH_NAMES = Map.of(
            "W", "West (from Harlingen)",
            "E", "East (from the centre)",
            "N", "North (Douwe Kalmaleane)",
            "S", "South (Slauerhoffweg)");

    private HudText() {
    }

    /** Formats seconds after midnight as {@code HH:MM:SS}. */
    static String clock(double secondsOfDay) {
        long s = (long) Math.floor(secondsOfDay) % 86_400;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }

    static String speed(double factor, boolean paused) {
        if (paused) {
            return AMBER + "paused" + WHITE;
        }
        if (factor >= 1) {
            return String.format(Locale.ROOT, "%sx", trim(factor));
        }
        return String.format(Locale.ROOT, "1/%sx", trim(1 / factor));
    }

    private static String trim(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-9 ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** One row of the statistics table. */
    record Row(String name, int queue, double delay, double perHour, int completed) {
    }

    /**
     * Combines movement statistics into one row per approach for motor traffic, plus rows for
     * buses, cyclists and pedestrians.
     */
    static List<Row> rows(List<MovementStats> movements) {
        Map<String, double[]> sums = new LinkedHashMap<>();
        for (String id : List.of("W", "E", "N", "S")) {
            sums.put(APPROACH_NAMES.get(id), new double[4]);
        }
        sums.put("Buses", new double[4]);
        sums.put("Cyclists", new double[4]);
        sums.put("Pedestrians", new double[4]);
        for (MovementStats m : movements) {
            String row = switch (m.mode()) {
                case CAR -> APPROACH_NAMES.get(m.key().substring(0, 1));
                case BUS -> "Buses";
                case BIKE -> "Cyclists";
                case FOOT -> "Pedestrians";
            };
            double[] s = sums.get(row);
            s[0] += m.queue();
            s[1] += m.meanDelay() * m.completed();
            s[2] += m.throughput();
            s[3] += m.completed();
        }
        return sums.entrySet().stream()
                .map(e -> new Row(e.getKey(), (int) e.getValue()[0],
                        e.getValue()[3] > 0 ? e.getValue()[1] / e.getValue()[3] : Double.NaN, e.getValue()[2],
                        (int) e.getValue()[3]))
                .toList();
    }

    static String table(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(GREY).append(String.format(Locale.ROOT, "%-26s %5s %7s %8s%n", "", "Queue", "Delay", "Per hour"));
        sb.append(WHITE);
        for (Row r : rows) {
            String delay = Double.isNaN(r.delay()) ? "-" : String.format(Locale.ROOT, "%.0f s", r.delay());
            String colour = Double.isNaN(r.delay()) || r.delay() < 40 ? GREEN : r.delay() < 90 ? AMBER : RED;
            sb.append(String.format(Locale.ROOT, "%-26s %5d %s%7s%s %8.0f%n", r.name(), r.queue(), colour, delay, WHITE,
                    r.perHour()));
        }
        return sb.toString().stripTrailing();
    }

    /** The duel scoreboard: who leads, then one row per measure with the better value in green. */
    static String duelTable(DuelScore.Side a, DuelScore.Side b) {
        String headline = DuelScore.headline(a, b);
        String colour = headline.contains(" leads: ") ? GREEN : headline.startsWith("warming") ? GREY : WHITE;
        StringBuilder sb = new StringBuilder(colour).append(headline).append('\n');
        sb.append(GREY).append("left  ").append(WHITE).append(a.name()).append(GREY).append("   vs   right  ")
                .append(WHITE).append(b.name()).append('\n');
        for (DuelScore.Row r : DuelScore.rows(a, b)) {
            sb.append(GREY).append(String.format(Locale.ROOT, "%-18s", r.label()))
                    .append(r.better() == DuelScore.Better.A ? GREEN : WHITE).append(String.format(Locale.ROOT, "%8s", r.a()))
                    .append("   ")
                    .append(r.better() == DuelScore.Better.B ? GREEN : WHITE).append(String.format(Locale.ROOT, "%8s", r.b()))
                    .append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /** A readable description of a route, for example "West (from Harlingen), left turn". */
    static String describe(Route route) {
        String from = APPROACH_NAMES.getOrDefault(route.approach(), route.approach());
        return switch (route.primaryMode()) {
            case BUS -> from + ", bus lane";
            case BIKE, FOOT -> "Crossing at " + from.replaceAll(" \\(.*", "").toLowerCase(Locale.ROOT) + " side";
            case CAR -> from + ", " + (route.movement() == Movement.LEFT ? "left turn"
                    : route.movement() == Movement.RIGHT ? "right turn" : "straight on");
        };
    }

    static String typeName(AgentType type) {
        return switch (type) {
            case CAR -> "Car";
            case VAN -> "Van";
            case TRUCK -> "Lorry";
            case BUS -> "Bus";
            case BICYCLE -> "Cyclist";
            case PEDESTRIAN -> "Pedestrian";
        };
    }

    /** Why the road user is doing what it does right now. */
    static String activity(Agent agent, SignalState signal) {
        boolean stopped = agent.speed() < 0.5;
        Agent other = agent.yieldingTo();
        return switch (agent.reason()) {
            case FREE -> "Moving freely";
            case FOLLOWING -> stopped ? "Waiting in the queue" : "Following the " + noun(agent.leader());
            case SIGNAL -> (stopped ? "Waiting for " : "Stopping for ")
                    + (signal == SignalState.AMBER ? AMBER + "amber" : RED + "red") + WHITE + " at signal "
                    + agent.geometry().route().group().id();
            case GIVING_WAY -> "Giving way to " + (other == null ? "crossing traffic" : "a " + noun(other).toLowerCase(Locale.ROOT));
            case KEEPING_CLEAR -> "Keeping the crossing clear";
            case BUS_STOP -> "At the bus stop Westeinde";
            case CURVE -> "Slowing down for the turn";
        };
    }

    private static String noun(Agent other) {
        if (other == null) {
            return "vehicle ahead";
        }
        return switch (other.type()) {
            case BICYCLE -> "cyclist";
            case PEDESTRIAN -> "pedestrian";
            default -> typeName(other.type()).toLowerCase(Locale.ROOT);
        };
    }

    /** The panel about the road user the camera follows. */
    static String agentInfo(Agent agent, SignalState signal) {
        Route route = agent.geometry().route();
        double kmh = agent.speed() * 3.6;
        String speed = agent.type() == AgentType.PEDESTRIAN
                ? String.format(Locale.ROOT, "%.1f km/h", kmh)
                : String.format(Locale.ROOT, "%.0f km/h", kmh);
        StringBuilder sb = new StringBuilder();
        sb.append(typeName(agent.type())).append("  ").append(GREY).append(speed).append(WHITE).append('\n');
        sb.append(describe(route)).append('\n');
        sb.append(activity(agent, signal)).append('\n');
        sb.append(GREY).append(String.format(Locale.ROOT, "Waited %.0f s, stopped %d times", agent.waitingTime(),
                agent.stops()));
        if (route.primaryMode() != Mode.FOOT) {
            sb.append(String.format(Locale.ROOT, ", signal group %s", route.group().id()));
        }
        return sb.toString();
    }

    static final String HELP = """
            Camera
              drag              rotate            right-drag / WASD   move
              wheel             zoom              F                   fly mode
              click a road user follow it         V                   driver's view
              Backspace         stop following    1-6                 viewpoints

            Simulation
              Space / P         pause             + / -               faster / slower
              C                 next controller   [ / ]               less / more traffic
              R                 reconnect the external controller
              X                 save statistics   F12                 screenshot

            Display
              T  signal timeline   G  statistics   H  this help   Esc  quit""";

    static final String HELP_DUEL = """
            Camera (both sides follow it)
              drag              rotate            right-drag / WASD   move
              wheel             zoom              F                   fly mode
              1-6               viewpoints

            Duel (keys work on both sides at once)
              Space / P         pause             + / -               faster / slower
              [ / ]             less / more traffic
              R                 reconnect the external controllers
              X                 save statistics   F12                 screenshot

            Display
              H  this help   Esc  quit""";
}
