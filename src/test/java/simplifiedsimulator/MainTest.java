package simplifiedsimulator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import simplifiedsimulator.sim.Simulation;

class MainTest {

    private static final SimulatorConfig EIGHT_O_CLOCK = SimulatorConfig.parse("--start", "08:00");

    @Test
    void warmedUpSimulationOpensAtTheStartTime() throws IOException {
        Simulation sim = Main.createSimulation(EIGHT_O_CLOCK, "actuated", 1337, true);
        assertEquals(8 * 3600, sim.timeOfDay(), 1e-6);
    }

    @Test
    void simulationWithoutWarmUpAlsoOpensAtTheStartTime() throws IOException {
        // an external controller (or a duel with one) skips the warm-up, so the clock must not run early
        Simulation sim = Main.createSimulation(EIGHT_O_CLOCK, "actuated", 1337, false);
        assertEquals(8 * 3600, sim.timeOfDay(), 1e-6);
    }
}
