package simplifiedsimulator.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import simplifiedsimulator.demand.DemandModel;
import simplifiedsimulator.demand.DemandProfile;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.SignalGroup;
import simplifiedsimulator.sim.SignalState;
import simplifiedsimulator.sim.Simulation;
import simplifiedsimulator.sim.SimulationOptions;

class ControllersTest {

    private static final Network NETWORK = Network.loadDefault();

    private static Simulation sim(Map<String, Double> demand) {
        return new Simulation(NETWORK, SimulationOptions.defaults()
                .withDemand(new DemandModel(demand, DemandProfile.flat(1.0))));
    }

    @Test
    void moduleStructureServesEveryGroupWithCompatibleMembers() {
        Simulation sim = sim(Map.of());
        ModuleStructure structure = ModuleStructure.build(sim.controlContext());
        Set<SignalGroup> served = new HashSet<>();
        for (ModuleStructure.Module m : structure.modules()) {
            served.addAll(m.members());
            assertTrue(m.members().containsAll(m.primary()));
            for (SignalGroup a : m.members()) {
                for (SignalGroup b : m.members()) {
                    assertFalse(sim.conflictMatrix().conflicts(a, b), a + " and " + b + " in " + m);
                }
            }
        }
        NETWORK.signalGroups().forEach(g -> assertTrue(served.contains(g), g + " never gets green"));
    }

    @Test
    void actuatedControlRestsAtRedWithoutTraffic() {
        Simulation sim = sim(Map.of());
        sim.run(120);
        for (SignalGroup g : NETWORK.signalGroups()) {
            assertEquals(SignalState.RED, sim.signalState(g));
        }
        assertEquals("resting at red", sim.controller().status());
    }

    @Test
    void actuatedControlServesAWaitingCarAndThenGapsOut() {
        Simulation sim = sim(Map.of("N2_T", 30.0));
        SignalGroup north = NETWORK.signalGroup("05");
        double firstGreen = -1;
        double greenLength = 0;
        for (int i = 0; i < 6000; i++) {
            sim.step();
            if (sim.signalState(north) == SignalState.GREEN) {
                if (firstGreen < 0) {
                    firstGreen = sim.time();
                }
                greenLength += 0.1;
            }
        }
        assertTrue(firstGreen > 0, "never turned green");
        // a lone car gets a short green: the minimum plus a few seconds of extension
        double perGreen = greenLength / Math.max(1, sim.statistics().movements(sim.time()).stream()
                .filter(m -> m.key().equals("N-THROUGH-CAR")).findFirst().orElseThrow().completed());
        assertTrue(perGreen < 15, "green per car " + perGreen);
    }

    @Test
    void fixedTimeCycleStaysWithinUsualLimits() {
        Simulation sim = sim(Map.of());
        FixedTimeController fixed = new FixedTimeController();
        sim.setController(fixed);
        assertTrue(fixed.cycleTime() >= 60 && fixed.cycleTime() <= 120);
        sim.run(600);
        boolean anyGreen = false;
        for (SignalGroup g : NETWORK.signalGroups()) {
            anyGreen |= !sim.signalPlan().history(g).isEmpty() && sim.signalPlan().history(g).size() > 2;
        }
        assertTrue(anyGreen);
        assertEquals(0, sim.signalConflicts());
    }

    @Test
    void unknownControllerNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SignalControllers.create("magic", "x", 1));
    }
}
