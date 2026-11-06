package simplifiedsimulator.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NetworkTest {

    private final Network network = Network.loadDefault();

    @Test
    void loadsAllApproachesAndGroups() {
        assertEquals(4, network.approaches().size());
        assertEquals("02", network.signalGroup("02").id());
        assertFalse(network.routes().isEmpty());
        assertFalse(network.detectors().isEmpty());
    }

    @Test
    void everySignalGroupControlsAtLeastOneRouteAndHasAHead() {
        for (SignalGroup group : network.signalGroups()) {
            assertFalse(group.routes().isEmpty(), group + " has no routes");
            assertFalse(group.heads().isEmpty(), group + " has no signal heads");
        }
    }

    @Test
    void routeLanesAreConnected() {
        for (Route route : network.routes()) {
            for (int i = 0; i + 1 < route.lanes().size(); i++) {
                Vec2 end = route.lanes().get(i).path().end();
                Vec2 start = route.lanes().get(i + 1).path().start();
                double gap = end.distance(start);
                // the next lane may start part-way along a shared path, but never far away
                assertTrue(gap < 1.0 || route.lanes().get(i + 1).path().distanceTo(end) < 1.0,
                        route.id() + " has a gap of " + gap + " m after " + route.lanes().get(i).id());
            }
        }
    }

    @Test
    void stopLinesAreAtTheEndOfTheirLane() {
        for (Route route : network.routes()) {
            assertEquals(route.stopLane().length(), route.stopS(), 0.5, route.id());
            assertTrue(route.stopLineDistance() > 20, route.id() + " starts too close to the stop line");
        }
    }

    @Test
    void routePathFollowsTheLanesWithoutDoublingBack() {
        for (Route route : network.routes()) {
            Polyline path = route.path();
            assertEquals(route.length(), path.length(), 1.0, route.id());
            for (double s = 0; s + 2 < path.length(); s += 2) {
                double turn = Math.abs(Math.atan2(Math.sin(path.headingAt(s + 2) - path.headingAt(s)),
                        Math.cos(path.headingAt(s + 2) - path.headingAt(s))));
                assertTrue(turn < Math.toRadians(120), route.id() + " turns back at " + s);
            }
        }
    }

    @Test
    void polylineArcLength() {
        Polyline p = new Polyline(java.util.List.of(new Vec2(0, 0), new Vec2(10, 0), new Vec2(10, 10)));
        assertEquals(20, p.length(), 1e-9);
        assertEquals(new Vec2(10, 5), p.pointAt(15));
        assertEquals(15, p.project(new Vec2(12, 5)), 1e-9);
        assertEquals(1, p.crossings(new Polyline(java.util.List.of(new Vec2(5, -5), new Vec2(5, 5)))).size());
    }
}
