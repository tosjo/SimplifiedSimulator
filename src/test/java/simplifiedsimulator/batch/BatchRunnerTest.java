package simplifiedsimulator.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.sim.SimulationOptions;

class BatchRunnerTest {

    @Test
    void parsesTimesOfDay() {
        assertEquals(8 * 3600 + 15 * 60, BatchRunner.parseTime("08:15"), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> BatchRunner.parseTime("25:00"));
        assertThrows(IllegalArgumentException.class, () -> BatchRunner.parseTime("8"));
    }

    @Test
    void runsAndReports(@TempDir Path dir) throws Exception {
        BatchRunner.Run run = BatchRunner.run(Network.loadDefault(), SimulationOptions.defaults(), "actuated", 60,
                300);
        assertTrue(run.summary().motorCompleted() > 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BatchRunner.printTable(List.of(run), new PrintStream(out, true, StandardCharsets.UTF_8));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("actuated"));
        Path csv = dir.resolve("runs.csv");
        BatchRunner.writeCsv(List.of(run), csv);
        assertEquals(2, Files.readAllLines(csv).size());
    }
}
