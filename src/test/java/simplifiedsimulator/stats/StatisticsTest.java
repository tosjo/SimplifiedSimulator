package simplifiedsimulator.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Movement;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.sim.AgentType;

class StatisticsTest {

    @Test
    void aggregatesTripsPerMovementAndWritesCsv() throws Exception {
        Statistics stats = new Statistics(Network.loadDefault());
        stats.record(new TripRecord("W2_T", "W", Movement.THROUGH, Mode.CAR, AgentType.CAR, 0, 40, 10, 1, 8));
        stats.record(new TripRecord("W3_T", "W", Movement.THROUGH, Mode.CAR, AgentType.TRUCK, 10, 60, 20, 2, 12));
        stats.updateQueues(Map.of("W2_T", 3, "W3_T", 4), 4);
        MovementStats w = stats.movements(60).stream().filter(m -> m.key().equals("W-THROUGH-CAR")).findFirst()
                .orElseThrow();
        assertEquals(2, w.completed());
        assertEquals(15, w.meanDelay(), 1e-9);
        assertEquals(1.5, w.meanStops(), 1e-9);
        assertEquals(7, w.queue());
        assertEquals("West, straight on", w.label());

        stats.sample(60, 8 * 3600 + 60, 10, 0, Map.of("W", 7));
        assertEquals(15, stats.timeSeries().get(0).motorDelay(), 1e-9);
        Summary s = stats.summary(0, 0, 0, 0);
        assertEquals(2, s.motorCompleted());
        assertEquals(4, s.maxQueue());

        StringWriter csv = new StringWriter();
        stats.writeMovementsCsv(csv, 60);
        assertTrue(csv.toString().startsWith("movement,label,completed"));
        assertTrue(csv.toString().contains("W-THROUGH-CAR,\"West, straight on\",2,15.0"));
        StringWriter series = new StringWriter();
        stats.writeTimeSeriesCsv(series);
        assertTrue(series.toString().contains("08:01"));
    }
}
