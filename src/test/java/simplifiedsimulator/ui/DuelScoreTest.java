package simplifiedsimulator.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import simplifiedsimulator.stats.Summary;
import simplifiedsimulator.ui.DuelScore.Better;
import simplifiedsimulator.ui.DuelScore.Row;
import simplifiedsimulator.ui.DuelScore.Side;

class DuelScoreTest {

    private static Summary summary(int completed, double delay) {
        return new Summary(completed, delay, 1.2, 30, 40, 20, 8, 0, 0, 0, 0);
    }

    private static Side side(String name, Summary s, int queue) {
        return new Side(name, s, queue);
    }

    private static Row row(List<Row> rows, String label) {
        return rows.stream().filter(r -> r.label().equals(label)).findFirst().orElseThrow();
    }

    @Test
    void lowerDelayWins() {
        Row delay = row(DuelScore.rows(side("Actuated", summary(100, 40), 5), side("Fixed", summary(100, 80), 5)),
                "Vehicle delay");
        assertEquals(Better.A, delay.better());
        assertEquals("40 s", delay.a());
        assertEquals("80 s", delay.b());
    }

    @Test
    void moreVehiclesThroughWins() {
        Row through = row(DuelScore.rows(side("A", summary(90, 40), 5), side("B", summary(120, 40), 5)),
                "Vehicles through");
        assertEquals(Better.B, through.better());
    }

    @Test
    void shorterQueueWins() {
        Row queue = row(DuelScore.rows(side("A", summary(90, 40), 3), side("B", summary(90, 40), 12)), "Queue now");
        assertEquals(Better.A, queue.better());
    }

    @Test
    void equalValuesHaveNoWinner() {
        Row stops = row(DuelScore.rows(side("A", summary(90, 40), 3), side("B", summary(90, 40), 3)),
                "Stops per vehicle");
        assertEquals(Better.NONE, stops.better());
    }

    @Test
    void missingDataShowsADashAndNoWinner() {
        Row delay = row(DuelScore.rows(side("A", summary(0, Double.NaN), 0), side("B", summary(10, 50), 0)),
                "Vehicle delay");
        assertEquals("-", delay.a());
        assertEquals(Better.NONE, delay.better());
    }

    @Test
    void headlineNamesTheLeaderAndTheMargin() {
        assertEquals("Actuated leads: 50% less vehicle delay",
                DuelScore.headline(side("Actuated", summary(100, 40), 0), side("Fixed", summary(100, 80), 0)));
        assertEquals("Fixed leads: 25% less vehicle delay",
                DuelScore.headline(side("Actuated", summary(100, 80), 0), side("Fixed", summary(100, 60), 0)));
    }

    @Test
    void headlineWaitsUntilBothSidesHaveFinishedTrips() {
        assertEquals("warming up…",
                DuelScore.headline(side("A", summary(0, Double.NaN), 0), side("B", summary(30, 50), 0)));
    }

    @Test
    void smallDifferencesAreNeckAndNeck() {
        assertEquals("neck and neck",
                DuelScore.headline(side("A", summary(100, 50), 0), side("B", summary(100, 50.5), 0)));
    }

    @Test
    void anySafetyCounterMakesASideUnsafe() {
        assertFalse(DuelScore.unsafe(new Summary(1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0)));
        assertTrue(DuelScore.unsafe(new Summary(1, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0)));
        assertTrue(DuelScore.unsafe(new Summary(1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0)));
        assertTrue(DuelScore.unsafe(new Summary(1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 1)));
        assertEquals("2 red-light runs, 1 conflicting greens, 3 overlaps",
                DuelScore.safety(new Summary(1, 1, 1, 1, 1, 1, 1, 0, 2, 3, 1)));
    }
}
