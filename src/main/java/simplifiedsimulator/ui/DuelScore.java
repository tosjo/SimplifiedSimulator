package simplifiedsimulator.ui;

import java.util.List;
import java.util.Locale;
import simplifiedsimulator.stats.Summary;

/** Compares the two sides of a controller duel. Kept free of graphics so it can be tested. */
public final class DuelScore {

    /** Differences in vehicle delay below this fraction count as a draw. */
    static final double NECK_AND_NECK = 0.02;

    /**
     * One side of the duel.
     *
     * @param name     controller name shown to the user
     * @param summary  the side's results since the start
     * @param queueNow road users queued right now, including those waiting to enter
     */
    public record Side(String name, Summary summary, int queueNow) {
    }

    /** Which side has the better value in a row. */
    public enum Better {
        A, B, NONE
    }

    /** One line of the scoreboard, with the values already formatted. */
    public record Row(String label, String a, String b, Better better) {
    }

    private DuelScore() {
    }

    public static List<Row> rows(Side a, Side b) {
        Summary x = a.summary();
        Summary y = b.summary();
        return List.of(
                lower("Vehicle delay", x.motorDelay(), y.motorDelay(), "%.0f s"),
                lower("Stops per vehicle", x.motorStops(), y.motorStops(), "%.2f"),
                lower("Bus delay", x.busDelay(), y.busDelay(), "%.0f s"),
                lower("Cyclist delay", x.bikeDelay(), y.bikeDelay(), "%.0f s"),
                lower("Pedestrian delay", x.footDelay(), y.footDelay(), "%.0f s"),
                row("Vehicles through", x.motorCompleted(), y.motorCompleted(), "%.0f", false),
                lower("Queue now", a.queueNow(), b.queueNow(), "%.0f"));
    }

    /** One line saying who leads on vehicle delay, and by how much. */
    public static String headline(Side a, Side b) {
        double da = a.summary().motorDelay();
        double db = b.summary().motorDelay();
        if (a.summary().motorCompleted() == 0 || b.summary().motorCompleted() == 0 || Double.isNaN(da)
                || Double.isNaN(db)) {
            return "warming up…";
        }
        double worst = Math.max(da, db);
        double margin = worst > 0 ? Math.abs(da - db) / worst : 0;
        if (margin < NECK_AND_NECK) {
            return "neck and neck";
        }
        String leader = da < db ? a.name() : b.name();
        return String.format(Locale.ROOT, "%s leads: %d%% less vehicle delay", leader, Math.round(margin * 100));
    }

    public static String safety(Summary s) {
        return String.format(Locale.ROOT, "%d red-light runs, %d conflicting greens, %d overlaps", s.redLightRuns(),
                s.signalConflicts(), s.conflictViolations());
    }

    public static boolean unsafe(Summary s) {
        return s.redLightRuns() > 0 || s.signalConflicts() > 0 || s.conflictViolations() > 0;
    }

    private static Row lower(String label, double a, double b, String format) {
        return row(label, a, b, format, true);
    }

    private static Row row(String label, double a, double b, String format, boolean lowerIsBetter) {
        Better better;
        if (Double.isNaN(a) || Double.isNaN(b) || a == b) {
            better = Better.NONE;
        } else {
            better = (a < b) == lowerIsBetter ? Better.A : Better.B;
        }
        return new Row(label, format(a, format), format(b, format), better);
    }

    private static String format(double value, String format) {
        return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, format, value);
    }
}
