package simplifiedsimulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StepClockTest {

    private static final double DT = 0.1;

    @Test
    void realTimeTakesOneStepPerTimeStep() {
        assertEquals(1, new StepClock(DT, 1).advance(0.1f));
    }

    @Test
    void speedMultipliesTheSteps() {
        assertEquals(4, new StepClock(DT, 4).advance(0.1f));
    }

    @Test
    void leftoverTimeCarriesOverToTheNextFrame() {
        StepClock clock = new StepClock(DT, 1);
        assertEquals(0, clock.advance(0.06f));
        assertEquals(1, clock.advance(0.06f));
    }

    @Test
    void pausedClockTakesNoSteps() {
        StepClock clock = new StepClock(DT, 1);
        clock.togglePause();
        assertTrue(clock.paused());
        assertEquals(0, clock.advance(5f));
    }

    @Test
    void capLimitsStepsPerFrame() {
        StepClock clock = new StepClock(DT, 64);
        assertEquals((int) StepClock.MAX_STEPS_PER_FRAME, clock.advance(30f));
        assertEquals(0, clock.advance(0f), "time beyond the cap is dropped, not carried over");
    }

    @Test
    void speedIsClampedToTheSupportedRange() {
        StepClock clock = new StepClock(DT, 1);
        clock.setSpeed(1000);
        assertEquals(StepClock.MAX_SPEED, clock.speed());
        clock.setSpeed(0.01);
        assertEquals(StepClock.MIN_SPEED, clock.speed());
    }

    @Test
    void alphaIsTheFractionOfAStepLeftOver() {
        StepClock clock = new StepClock(DT, 1);
        clock.advance(0.125f);
        assertEquals(0.25, clock.alpha(), 1e-3);
        assertTrue(clock.alpha() >= 0 && clock.alpha() < 1);
    }
}
