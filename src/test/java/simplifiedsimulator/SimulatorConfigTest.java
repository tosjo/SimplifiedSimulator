package simplifiedsimulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import simplifiedsimulator.render.CameraRig;
import simplifiedsimulator.render.RenderQuality;

class SimulatorConfigTest {

    @Test
    void defaults() {
        SimulatorConfig c = SimulatorConfig.parse();
        assertEquals("actuated", c.controller());
        assertEquals(7.5 * 3600, c.start());
        assertEquals(1, c.speed());
        assertEquals(RenderQuality.HIGH, c.quality());
        assertEquals(CameraRig.Preset.OVERVIEW, c.camera());
    }

    @Test
    void parsesAllOptions() {
        SimulatorConfig c = SimulatorConfig.parse("--controller", "FIXED", "--start", "17:15", "--speed", "4",
                "--scale", "1.3", "--seed", "7", "--quality", "low", "--camera", "street-west", "--host", "ctrl",
                "--port", "4000", "--date", "2026-06-21");
        assertEquals("fixed", c.controller());
        assertEquals(17 * 3600 + 15 * 60, c.start());
        assertEquals(4, c.speed());
        assertEquals(1.3, c.demandScale());
        assertEquals(7, c.seed());
        assertEquals(RenderQuality.LOW, c.quality());
        assertEquals(CameraRig.Preset.STREET_WEST, c.camera());
        assertEquals("ctrl", c.host());
        assertEquals(4000, c.port());
        assertEquals(2026, c.date().getYear());
    }

    @Test
    void headlessNeedsDuration() {
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse("--headless"));
        SimulatorConfig c = SimulatorConfig.parse("--headless", "--duration", "60", "--csv", "out.csv");
        assertTrue(c.headless());
        assertEquals(Path.of("out.csv"), c.csv());
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse("--controller", "magic"));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse("--start", "25:00"));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse("--speed", "0"));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse("--port", "70000"));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse("--csv", "x.csv"));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse("--speed"));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse("--bogus"));
        assertThrows(SimulatorConfig.HelpRequested.class, () -> SimulatorConfig.parse("--help"));
    }

    @Test
    void parsesTimes() {
        assertEquals(0, SimulatorConfig.parseTime("00:00"));
        assertEquals(23 * 3600 + 59 * 60 + 30, SimulatorConfig.parseTime("23:59:30"));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parseTime("7"));
    }
}
