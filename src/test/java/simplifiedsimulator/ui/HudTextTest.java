package simplifiedsimulator.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import simplifiedsimulator.control.SignalPlan;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.sim.SignalState;
import simplifiedsimulator.stats.MovementStats;

class HudTextTest {

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
