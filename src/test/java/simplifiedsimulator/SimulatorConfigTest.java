package simplifiedsimulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
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
    void noDuelByDefault() {
        assertFalse(SimulatorConfig.parse().isDuel());
        assertEquals(List.of(), SimulatorConfig.parse().duel());
    }

    @Test
    void parsesADuelAndDefaultsToMediumQuality() {
        SimulatorConfig c = SimulatorConfig.parse("--duel", "Actuated, FIXED");
        assertEquals(List.of("actuated", "fixed"), c.duel());
        assertTrue(c.isDuel());
        assertEquals("actuated", c.controllerFor(0));
        assertEquals("fixed", c.controllerFor(1));
        assertEquals(RenderQuality.MEDIUM, c.quality());
    }

    @Test
    void explicitQualityWinsInADuelWhateverTheOrder() {
        assertEquals(RenderQuality.HIGH, SimulatorConfig.parse("--duel", "actuated,fixed", "--quality", "high").quality());
        assertEquals(RenderQuality.HIGH, SimulatorConfig.parse("--quality", "high", "--duel", "actuated,fixed").quality());
    }

    @Test
    void twoExternalControllersGetNeighbouringPorts() {
        SimulatorConfig both = SimulatorConfig.parse("--duel", "external,external", "--port", "4000");
        assertEquals(4000, both.portFor(0));
        assertEquals(4001, both.portFor(1));
        SimulatorConfig one = SimulatorConfig.parse("--duel", "actuated,external", "--port", "4000");
        assertEquals(4000, one.portFor(1));
    }

    @Test
    void rejectsInvalidDuels() {
        for (String[] args : List.of(
                new String[] {"--duel", "actuated"},
                new String[] {"--duel", "actuated,fixed,external"},
                new String[] {"--duel", "actuated,smart"},
                new String[] {"--duel", "actuated,fixed", "--headless", "--duration", "60"},
                new String[] {"--duel", "actuated,fixed", "--controller", "fixed"},
                new String[] {"--controller", "fixed", "--duel", "actuated,fixed"},
                new String[] {"--duel", "external,external", "--port", "65535"})) {
            assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse(args), String.join(" ", args));
        }
    }

    @Test
    void parsesNamesForTheDuelSides() {
        SimulatorConfig c = SimulatorConfig.parse("--duel", "external,external", "--names", " 2014 , Smart ");
        assertEquals(List.of("2014", "Smart"), c.names());
        assertEquals(List.of(), SimulatorConfig.parse("--duel", "actuated,fixed").names());
    }

    @Test
    void rejectsInvalidNames() {
        for (String[] args : List.of(
                new String[] {"--names", "a,b"},
                new String[] {"--duel", "actuated,fixed", "--names", "only-one"},
                new String[] {"--duel", "actuated,fixed", "--names", "a,b,c"},
                new String[] {"--duel", "actuated,fixed", "--names", "a, "},
                new String[] {"--duel", "actuated,fixed", "--names", "a,this name is far too long to fit"},
                new String[] {"--duel", "actuated,fixed", "--names", "a,café"})) {
            assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse(args), String.join(" ", args));
        }
    }

    @Test
    void sideOutsideTheDuelIsRejected() {
        SimulatorConfig c = SimulatorConfig.parse("--duel", "actuated,fixed");
        assertThrows(IllegalArgumentException.class, () -> c.portFor(2));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse().controllerFor(0));
    }

    @Test
    void parsesTimes() {
        assertEquals(0, SimulatorConfig.parseTime("00:00"));
        assertEquals(23 * 3600 + 59 * 60 + 30, SimulatorConfig.parseTime("23:59:30"));
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parseTime("7"));
    }
}
