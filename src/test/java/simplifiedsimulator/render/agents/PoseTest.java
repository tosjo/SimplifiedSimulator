package simplifiedsimulator.render.agents;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PoseTest {

    @Test
    void interpolatesPositionLinearly() {
        Pose p = Pose.lerp(new Pose(0, 0, 0), new Pose(10, -4, 0), 0.25);
        assertEquals(2.5, p.x(), 1e-9);
        assertEquals(-1.0, p.y(), 1e-9);
    }

    @Test
    void headingTurnsTheShortWayRound() {
        double almostWest = Math.toRadians(179);
        double justPastWest = Math.toRadians(-179);
        Pose mid = Pose.lerp(new Pose(0, 0, almostWest), new Pose(0, 0, justPastWest), 0.5);
        assertEquals(Math.PI, Math.abs(Math.atan2(Math.sin(mid.heading()), Math.cos(mid.heading()))), 1e-9);
    }

    @Test
    void endsMatchTheInputs() {
        Pose a = new Pose(1, 2, 0.3);
        Pose b = new Pose(5, 6, 1.0);
        assertEquals(a, Pose.lerp(a, b, 0));
        assertEquals(b.x(), Pose.lerp(a, b, 1).x(), 1e-12);
        assertEquals(b.heading(), Pose.lerp(a, b, 1).heading(), 1e-12);
    }
}
