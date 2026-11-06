package simplifiedsimulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimulatorConfigTest {

    @Test
    void usesDefaultsWithoutArguments() {
        assertEquals(SimulatorConfig.defaults(), SimulatorConfig.parse());
    }

    @Test
    void parsesAllOptions() {
        SimulatorConfig config = SimulatorConfig.parse(
                "--host", "10.0.0.5", "--port", "4000", "--scenario", "small",
                "--connect", "--demo", "--headless", "--duration", "30");

        assertEquals(new SimulatorConfig("10.0.0.5", 4000, "small", true, true, true, 30, null), config);
    }

    @Test
    void parsesRecordOption() {
        assertEquals(Path.of("new-route.txt"), SimulatorConfig.parse("--record", "new-route.txt").recordTo());
        assertThrows(IllegalArgumentException.class,
                () -> SimulatorConfig.parse("--record", "new-route.txt", "--headless"));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse("--port"));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse("--port", "abc"));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse("--port", "70000"));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse("--duration", "-1"));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse("--host", " "));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse("--fast"));
    }

    @Test
    void signalsHelp() {
        assertThrows(SimulatorConfig.HelpRequested.class, () -> SimulatorConfig.parse("--help"));
    }

    @Test
    void opensBuiltInScenarioCaseInsensitively() throws Exception {
        try (InputStream in = SimulatorConfig.parse("--scenario", "Medium").openScenario()) {
            assertNotNull(in);
        }
    }

    @Test
    void opensScenarioFromFile(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("custom.xml"), "<Data/>");
        try (InputStream in = SimulatorConfig.parse("--scenario", file.toString()).openScenario()) {
            assertEquals("<Data/>", new String(in.readAllBytes()));
        }
    }

    @Test
    void reportsMissingScenarioFile(@TempDir Path dir) {
        SimulatorConfig config = SimulatorConfig.parse("--scenario", dir.resolve("nope.xml").toString());
        assertThrows(UncheckedIOException.class, config::openScenario);
    }
}
