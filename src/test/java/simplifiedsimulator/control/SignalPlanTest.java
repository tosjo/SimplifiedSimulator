package simplifiedsimulator.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.SignalGroup;
import simplifiedsimulator.sim.RouteConflicts;
import simplifiedsimulator.sim.SignalState;

class SignalPlanTest {

    private static final Network NETWORK = Network.loadDefault();
    private static final ConflictMatrix MATRIX = new ConflictMatrix(NETWORK, new RouteConflicts(NETWORK));

    @Test
    void amberRunsOutAndConflictingGroupsWaitForTheIntergreen() {
        SignalPlan plan = new SignalPlan(NETWORK, MATRIX);
        SignalGroup west = NETWORK.signalGroup("02");
        SignalGroup north = NETWORK.signalGroup("05");
        plan.advance(0);
        plan.startGreen(west);
        assertThrows(IllegalStateException.class, () -> plan.startGreen(north));
        plan.advance(10);
        assertEquals(10, plan.timeInState(west), 1e-9);
        plan.endGreen(west);
        assertEquals(SignalState.AMBER, plan.state(west));
        plan.advance(12.9);
        assertEquals(SignalState.AMBER, plan.state(west));
        plan.advance(13.0);
        assertEquals(SignalState.RED, plan.state(west));
        double ig = MATRIX.intergreen(west, north);
        plan.advance(10 + ig - 0.1);
        assertFalse(plan.mayStartGreen(north));
        plan.advance(10 + ig);
        assertTrue(plan.mayStartGreen(north));
        assertEquals(4, plan.history(west).size());
    }

    @Test
    void pedestrianGreenEndsInRed() {
        SignalPlan plan = new SignalPlan(NETWORK, MATRIX);
        SignalGroup walk = NETWORK.signalGroup("32");
        plan.startGreen(walk);
        plan.advance(7);
        plan.endGreen(walk);
        assertEquals(SignalState.RED, plan.state(walk));
    }

    @Test
    void externalChangesAgainstTheIntergreenAreCounted() {
        SignalPlan plan = new SignalPlan(NETWORK, MATRIX);
        plan.set(NETWORK.signalGroup("02"), SignalState.GREEN);
        plan.set(NETWORK.signalGroup("05"), SignalState.GREEN);
        assertEquals(1, plan.intergreenViolations());
    }
}
