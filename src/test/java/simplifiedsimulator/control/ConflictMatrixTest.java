package simplifiedsimulator.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.SignalGroup;
import simplifiedsimulator.sim.RouteConflicts;

class ConflictMatrixTest {

    private static final Network NETWORK = Network.loadDefault();
    private static final ConflictMatrix MATRIX = new ConflictMatrix(NETWORK, new RouteConflicts(NETWORK));

    private static SignalGroup g(String id) {
        return NETWORK.signalGroup(id);
    }

    @Test
    void conflictsAreSymmetricAndIntergreensAtLeastTheAmber() {
        for (SignalGroup a : NETWORK.signalGroups()) {
            assertFalse(MATRIX.conflicts(a, a));
            for (SignalGroup b : MATRIX.conflictsOf(a)) {
                assertTrue(MATRIX.conflicts(b, a), a + " / " + b);
                assertTrue(MATRIX.intergreen(a, b) >= MATRIX.amber(a), a + " -> " + b);
                assertTrue(MATRIX.intergreen(a, b) < 40, a + " -> " + b + " " + MATRIX.intergreen(a, b));
            }
        }
    }

    @Test
    void crossingStreamsConflictAndOpposingThroughTrafficDoesNot() {
        assertTrue(MATRIX.conflicts(g("02"), g("05")), "west and north through traffic cross");
        assertTrue(MATRIX.conflicts(g("08"), g("11")), "east and south through traffic cross");
        assertFalse(MATRIX.conflicts(g("02"), g("08")), "opposite through traffic on separate carriageways");
        assertFalse(MATRIX.conflicts(g("05"), g("11")), "opposite through traffic on separate carriageways");
    }

    @Test
    void crossingsConflictWithTheTrafficTheyCross() {
        assertTrue(MATRIX.conflicts(g("22"), g("05")), "cyclists crossing the Douwe Kalmaleane");
        assertTrue(MATRIX.conflicts(g("26"), g("11")), "cyclists crossing the Slauerhoffweg");
        assertTrue(MATRIX.conflicts(g("34"), g("08")), "pedestrians crossing the Harlingerstraatweg");
    }

    @Test
    void pedestriansGetNoAmberAndLongerClearanceTimes() {
        assertEquals(0, MATRIX.amber(g("32")));
        assertEquals(ConflictMatrix.AMBER, MATRIX.amber(g("02")));
        for (SignalGroup p : NETWORK.signalGroups()) {
            if (p.mode() == Mode.FOOT) {
                for (SignalGroup c : MATRIX.conflictsOf(p)) {
                    assertTrue(MATRIX.intergreen(p, c) > 0);
                }
            }
        }
    }
}
