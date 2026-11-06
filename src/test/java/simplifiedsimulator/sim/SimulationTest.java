package simplifiedsimulator.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import simplifiedsimulator.control.SignalControllers;
import simplifiedsimulator.demand.DemandModel;
import simplifiedsimulator.demand.DemandProfile;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.stats.MovementStats;
import simplifiedsimulator.stats.Summary;

class SimulationTest {

    private static final Network NETWORK = Network.loadDefault();

    private static Simulation only(Map<String, Double> demand, long seed) {
        return new Simulation(NETWORK, SimulationOptions.defaults().withSeed(seed)
                .withDemand(new DemandModel(demand, DemandProfile.flat(1.0))));
    }

    @Test
    void sameSeedGivesTheSameArrivalsWhateverTheController() {
        Simulation a = new Simulation(NETWORK, SimulationOptions.defaults().withSeed(3));
        Simulation b = new Simulation(NETWORK, SimulationOptions.defaults().withSeed(3));
        a.setController(SignalControllers.create("actuated", "localhost", 1));
        b.setController(SignalControllers.create("fixed", "localhost", 1));
        a.run(900);
        b.run(900);
        assertTrue(a.arrivals() > 100, "expected a busy crossing, got " + a.arrivals());
        assertEquals(a.arrivals(), b.arrivals());
    }

    @Test
    void jumpingTheClockChangesTheTimeOfDayButNotTheSimulatedTime() {
        Simulation sim = new Simulation(NETWORK, SimulationOptions.defaults().withStartTimeOfDay(13 * 3600));
        sim.run(60);
        sim.jumpToTimeOfDay(17 * 3600);
        assertEquals(17 * 3600, sim.timeOfDay(), 1e-6);
        assertEquals(60, sim.time(), 1e-6);
        sim.run(30);
        assertEquals(17 * 3600 + 30, sim.timeOfDay(), 1e-6);
    }

    @Test
    void jumpingBackPastMidnightWrapsTheClock() {
        Simulation sim = new Simulation(NETWORK, SimulationOptions.defaults().withStartTimeOfDay(23 * 3600));
        sim.jumpToTimeOfDay(10);
        sim.run(20);
        assertEquals(30, sim.timeOfDay(), 1e-6);
    }

    @Test
    void trafficFollowsTheClockAfterAJump() {
        Simulation night = new Simulation(NETWORK, SimulationOptions.defaults().withSeed(4));
        night.jumpToTimeOfDay(2 * 3600);
        night.run(600);
        Simulation rush = new Simulation(NETWORK, SimulationOptions.defaults().withSeed(4));
        rush.jumpToTimeOfDay(7.5 * 3600);
        rush.run(600);
        assertTrue(rush.arrivals() > 4 * night.arrivals(),
                "rush " + rush.arrivals() + " against night " + night.arrivals());
    }

    @Test
    void clockOnlyJumpsToATimeOfDay() {
        Simulation sim = Simulation.createDefault();
        for (double bad : new double[] {-1, 86_400, Double.NaN}) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> sim.jumpToTimeOfDay(bad));
        }
    }

    @Test
    void carsStopAtARedLight() {
        Simulation sim = only(Map.of("W2_T", 600.0), 1);
        sim.setController(TestControllers.allRed());
        sim.run(300);
        assertFalse(sim.agents().isEmpty());
        for (Agent a : sim.agents()) {
            assertTrue(a.position() < a.geometry().stopLine(), "car " + a.id() + " passed the red light");
        }
        assertEquals(0, sim.redLightRuns());
        Agent first = sim.agents().stream().max((a, b) -> Double.compare(a.position(), b.position())).orElseThrow();
        assertEquals(0, first.speed(), 0.05);
        assertEquals(Agent.Reason.SIGNAL, first.reason());
        assertTrue(first.braking());
    }

    @Test
    void carsOnAGreenRoadHardlyLoseTime() {
        Simulation sim = only(Map.of("W2_T", 300.0), 2);
        sim.setController(TestControllers.alwaysGreen(Set.of("02")));
        sim.run(900);
        MovementStats w = sim.statistics().movements(sim.time()).stream()
                .filter(m -> m.key().equals("W-THROUGH-CAR")).findFirst().orElseThrow();
        assertTrue(w.completed() > 40, "completed " + w.completed());
        assertTrue(w.meanDelay() < 3, "delay " + w.meanDelay());
        assertEquals(0, sim.conflictViolations());
    }

    @Test
    void turningVehiclesIndicateAndSlowDown() {
        Simulation sim = only(Map.of("W1_L", 400.0), 3);
        sim.setController(TestControllers.alwaysGreen(Set.of("03")));
        boolean indicated = false;
        double slowest = Double.MAX_VALUE;
        for (int i = 0; i < 3000; i++) {
            sim.step();
            for (Agent a : sim.agents()) {
                indicated |= a.indicator() == Indicator.LEFT;
                double s = a.position() - a.geometry().stopLine();
                if (s > 25 && s < 40) {
                    slowest = Math.min(slowest, a.speed());
                }
            }
        }
        assertTrue(indicated);
        assertTrue(slowest < 8, "left turn taken at " + slowest + " m/s");
    }

    @Test
    void busesStopAtTheBusStop() {
        Simulation sim = only(Map.of("Wb_T", 30.0), 4);
        sim.setController(TestControllers.alwaysGreen(Set.of("41")));
        boolean stopped = false;
        for (int i = 0; i < 9000 && !stopped; i++) {
            sim.step();
            for (Agent a : sim.agents()) {
                stopped |= a.type() == AgentType.BUS && a.reason() == Agent.Reason.BUS_STOP && a.speed() < 0.1;
            }
        }
        assertTrue(stopped);
    }

    @Test
    void sameSeedGivesTheSameRun() {
        Summary a = runSummary(9);
        Summary b = runSummary(9);
        assertEquals(a, b);
        Summary c = runSummary(10);
        assertFalse(a.equals(c));
    }

    private static Summary runSummary(long seed) {
        Simulation sim = new Simulation(NETWORK, SimulationOptions.defaults().withSeed(seed));
        sim.run(600);
        return sim.summary();
    }

    /**
     * Two hours of morning peak with every kind of traffic: nobody may run into anybody,
     * conflicting lights may never be green together, and nobody may get stuck.
     */
    @ParameterizedTest
    @ValueSource(strings = {"actuated", "fixed"})
    void busyMorningIsSafeAndNobodyGetsStuck(String controller) {
        Simulation sim = new Simulation(NETWORK, SimulationOptions.defaults().withSeed(7).withStartTimeOfDay(7 * 3600));
        sim.setController(SignalControllers.create(controller, "localhost", 1));
        for (int minute = 0; minute < 120; minute++) {
            sim.run(60);
            for (Agent a : sim.agents()) {
                assertTrue(a.waitingTime() < 900, controller + ": " + a.type() + " " + a.id() + " on " + a.routeId()
                        + " has been waiting " + a.waitingTime() + " s (" + a.reason() + ")");
            }
        }
        Summary s = sim.summary();
        assertEquals(0, s.conflictViolations(), "road users ran into each other");
        assertEquals(0, s.signalConflicts(), "conflicting greens");
        assertEquals(0, sim.signalPlan().intergreenViolations());
        assertTrue(s.motorCompleted() > 4000, "only " + s.motorCompleted() + " vehicles in two hours");
        assertTrue(s.bikeDelay() < 240 && s.footDelay() < 240 && s.busDelay() < 240, s.toString());
    }

    @Test
    void timeOfDayWrapsAroundMidnight() {
        Simulation sim = new Simulation(NETWORK, SimulationOptions.defaults().withStartTimeOfDay(86400 - 30));
        sim.run(60);
        assertEquals(30, sim.timeOfDay(), 0.2);
    }
}
