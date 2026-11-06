package simplifiedsimulator.control;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import simplifiedsimulator.network.Detector;
import simplifiedsimulator.network.Network;

class DetectorStatesTest {

    private static final Network NETWORK = Network.loadDefault();

    @Test
    void reportsTheStepInWhichALoopBecomesFree() {
        DetectorStates states = new DetectorStates(NETWORK);
        Detector d = NETWORK.detectors().get(0);
        states.update(0.1, Set.of(d));
        assertTrue(states.justOccupied(d));
        assertFalse(states.justFreed(d));
        states.update(0.2, Set.of(d));
        assertFalse(states.justFreed(d), "still occupied");
        states.update(0.3, Set.of());
        assertTrue(states.justFreed(d));
        states.update(0.4, Set.of());
        assertFalse(states.justFreed(d), "only in the step it became free");
    }
}
