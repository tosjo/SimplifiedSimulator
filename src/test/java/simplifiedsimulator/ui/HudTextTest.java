package simplifiedsimulator.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import simplifiedsimulator.control.SignalPlan;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.sim.SignalState;
import simplifiedsimulator.stats.MovementStats;
import simplifiedsimulator.stats.Summary;

class HudTextTest {

    @Test
    void duelTableHighlightsTheBetterSideAndNeverShowsNaN() {
        DuelScore.Side a = new DuelScore.Side("Vehicle actuated",
                new Summary(100, 40, 1.2, Double.NaN, 30, 20, 8, 0, 0, 0, 0), 4);
        DuelScore.Side b = new DuelScore.Side("Fixed time",
                new Summary(100, 80, 1.6, Double.NaN, 50, 25, 8, 0, 0, 0, 0), 9);
        String table = HudText.duelTable(a, b);
        assertTrue(table.startsWith(HudText.GREEN + "Vehicle actuated leads: 50% less vehicle delay"), table);
        // the leader is named in the headline; the columns are headed left and right
        assertTrue(table.contains("left") && table.contains("right"), table);
        assertTrue(table.contains(HudText.GREEN + String.format("%8s", "40 s")), table);
        assertTrue(table.contains(HudText.WHITE + String.format("%8s", "80 s")), table);
        assertFalse(table.contains("NaN"), table);
    }

    @Test
    void duelTableColumnsLineUpUnderTheirHeadings() {
        Summary some = new Summary(100, 40, 1.2, 30, 30, 20, 8, 0, 0, 0, 0);
        String table = HudText.duelTable(new DuelScore.Side("External controller (left)", some, 4),
                new DuelScore.Side("External controller (right)", some, 9));
        // strip jME's colour tags, which look like \#9aa3ad#
        List<String> lines = table.lines().map(l -> l.replaceAll("\\\\#[0-9a-f]{6}#", "")).toList();
        String header = lines.get(1);
        assertTrue(header.contains("left") && header.contains("right"), header);
        // every line under the headline has the width of the rows, so the panel stays compact
        for (String line : lines.subList(1, lines.size())) {
            assertEquals(lines.get(2).length(), line.length(), "'" + line + "'");
        }
    }

    @Test
    void duelTableShowsWarmingUpInGrey() {
        Summary none = new Summary(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, 0, 0, 0, 0);
        String table = HudText.duelTable(new DuelScore.Side("A", none, 0), new DuelScore.Side("B", none, 0));
        assertTrue(table.startsWith(HudText.GREY + "warming up"), table);
        assertFalse(table.contains("NaN"), table);
    }

    @Test
    void duelTableOnlyUsesCharactersTheConsoleFontHas() {
        Summary none = new Summary(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, 0, 0, 0, 0);
        Summary some = new Summary(100, 40, 1.2, 30, 30, 20, 8, 0, 0, 0, 0);
        for (Summary s : new Summary[] {none, some}) {
            String table = HudText.duelTable(new DuelScore.Side("A", s, 0), new DuelScore.Side("B", none, 0));
            // Console.fnt has the printable ASCII range only
            assertTrue(table.chars().allMatch(c -> c == 10 || (c >= 32 && c <= 126)), table);
        }
    }

    @Test
    void formatsClockAndSpeed() {
        assertEquals("07:30:05", HudText.clock(7.5 * 3600 + 5));
        assertEquals("00:00:10", HudText.clock(86_410));
        assertEquals("4x", HudText.speed(4, false));
        assertEquals("1/2x", HudText.speed(0.5, false));
        assertTrue(HudText.speed(1, true).contains("paused"));
    }

    @Test
    void combinesMovementsPerApproach() {
        List<MovementStats> movements = List.of(
                new MovementStats("W-THROUGH-CAR", "", Mode.CAR, 10, 30, 1, 20, 4, 9, 600),
                new MovementStats("W-LEFT-CAR", "", Mode.CAR, 30, 70, 1, 50, 2, 5, 200),
                new MovementStats("N-CROSSING-BIKE", "", Mode.BIKE, 5, 12, 0.5, 10, 1, 3, 100));
        List<HudText.Row> rows = HudText.rows(movements);
        HudText.Row west = rows.get(0);
        assertTrue(west.name().startsWith("West"));
        assertEquals(6, west.queue());
        assertEquals((10 * 30 + 30 * 70) / 40.0, west.delay(), 1e-9);
        assertEquals(800, west.perHour(), 1e-9);
        HudText.Row bikes = rows.stream().filter(r -> r.name().equals("Cyclists")).findFirst().orElseThrow();
        assertEquals(12, bikes.delay(), 1e-9);
        assertTrue(Double.isNaN(rows.get(1).delay()), "no trips yet shows no delay");
        assertTrue(HudText.table(rows).contains("Pedestrians"));
    }

    @Test
    void describesRoutes() {
        Network network = Network.loadDefault();
        assertEquals("West (from Harlingen), left turn", HudText.describe(network.route("W1_L")));
        assertEquals("East (from the centre), bus lane", HudText.describe(network.route("Eb_T")));
    }

    @Test
    void timelineColumnsFollowTheHistory() {
        List<SignalPlan.Change> history = List.of(new SignalPlan.Change(0, SignalState.RED),
                new SignalPlan.Change(10, SignalState.GREEN), new SignalPlan.Change(20, SignalState.AMBER));
        SignalState[] columns = TimelineColumns.compute(history, SignalState.AMBER, 20, 5, 20);
        // columns at t = 0, 5, 10, 15, 20
        assertArrayEquals(new SignalState[] {SignalState.RED, SignalState.RED, SignalState.GREEN, SignalState.GREEN,
            SignalState.AMBER}, columns);
        SignalState[] early = TimelineColumns.compute(history, SignalState.AMBER, 20, 3, 40);
        assertEquals(null, early[0], "before the first change nothing is known");
    }
}
