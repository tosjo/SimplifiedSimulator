package simplifiedsimulator.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DriverParametersTest {

    private static final DriverParameters CAR = new DriverParameters(1, 1.6, 2.4, 1.2, 2.0, 2.2, 3.0);

    @Test
    void acceleratesTowardsDesiredSpeedOnAnEmptyRoad() {
        double v = 0;
        for (int i = 0; i < 600; i++) {
            v += CAR.accelerate(v, 13.9, Double.POSITIVE_INFINITY, 0) * 0.1;
        }
        assertEquals(13.9, v, 0.3);
        assertEquals(CAR.maxAccel(), CAR.accelerate(0, 13.9, Double.POSITIVE_INFINITY, 0), 1e-9);
    }

    @Test
    void settlesBehindALeaderAtTheDesiredTimeGap() {
        double v = 13.9;
        double gap = 60;
        double leaderSpeed = 10;
        for (int i = 0; i < 3000; i++) {
            double a = CAR.accelerate(v, 13.9, gap, v - leaderSpeed);
            v = Math.max(0, v + a * 0.1);
            gap += (leaderSpeed - v) * 0.1;
        }
        assertEquals(leaderSpeed, v, 0.05);
        // the IDM equilibrium gap is a little more than s0 + v T
        assertTrue(gap > CAR.minGap() + leaderSpeed * CAR.timeHeadway() - 0.5, "gap " + gap);
        assertTrue(gap < CAR.minGap() + leaderSpeed * CAR.timeHeadway() + 8, "gap " + gap);
    }

    @Test
    void stopsBeforeAStandingObstacleWithoutHittingIt() {
        double v = 13.9;
        double distance = 80;
        for (int i = 0; i < 2000; i++) {
            double a = CAR.accelerate(v, 13.9, distance, v);
            double vNew = Math.max(0, v + a * 0.1);
            distance -= (v + vNew) / 2 * 0.1;
            v = vNew;
        }
        assertEquals(0, v, 0.01);
        assertTrue(distance > 0.5 && distance < CAR.minGap() + 0.5, "stopped " + distance + " m before it");
    }

    @ParameterizedTest
    @EnumSource(AgentType.class)
    void sampledParametersArePlausible(AgentType type) {
        SplittableRandom rng = new SplittableRandom(5);
        for (int i = 0; i < 200; i++) {
            DriverParameters p = DriverParameters.sample(type, rng);
            assertTrue(p.speedFactor() > 0.6 && p.speedFactor() < 1.5);
            assertTrue(p.maxAccel() > 0 && p.comfortDecel() > 0 && p.timeHeadway() > 0 && p.minGap() > 0);
        }
    }
}
