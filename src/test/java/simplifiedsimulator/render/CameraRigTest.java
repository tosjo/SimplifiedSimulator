package simplifiedsimulator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CameraRigTest {

    /** jME reports one notch of a mouse wheel on LWJGL 3 as 120 / 100. */
    private static final float NOTCH = 1.2f;

    @Test
    void oneNotchInThenOutReturnsToTheSameDistance() {
        float in = CameraRig.zoomedDistance(170f, -NOTCH);
        assertEquals(170f, CameraRig.zoomedDistance(in, NOTCH), 1e-3);
    }

    @Test
    void oneNotchInMovesCloserByAModestStep() {
        float in = CameraRig.zoomedDistance(170f, -NOTCH);
        assertTrue(in < 170f, "zooming in brings the camera closer");
        assertTrue(in > 170f * 0.8f, "one notch must not jump most of the way in, but was " + in);
    }

    @Test
    void oneNotchOutMovesFurtherByAModestStep() {
        float out = CameraRig.zoomedDistance(170f, NOTCH);
        assertTrue(out > 170f, "zooming out moves the camera away");
        assertTrue(out < 170f * 1.25f, "one notch must not jump far out, but was " + out);
    }

    @Test
    void aFastFlickInNeverGivesAZeroOrNegativeDistance() {
        assertTrue(CameraRig.zoomedDistance(170f, -10 * NOTCH) > 0);
    }

    @Test
    void flyModeMovesAFewMetresPerNotch() {
        float step = CameraRig.flyStep(NOTCH);
        assertTrue(step > 2f && step < 15f, "one notch should move a few metres, but moved " + step);
        assertEquals(-step, CameraRig.flyStep(-NOTCH), 1e-6);
    }
}
