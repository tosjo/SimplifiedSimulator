package simplifiedsimulator.demand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.Route;
import simplifiedsimulator.sim.AgentType;

class DemandTest {

    private static final Network NETWORK = Network.loadDefault();

    @Test
    void bundledDemandCoversKnownRoutesAndPeaksInTheRushHours() {
        DemandModel demand = DemandModel.loadDefault();
        demand.validate(NETWORK);
        Route west = NETWORK.route("W2_T");
        assertTrue(demand.peakPerHour(west) > 100);
        assertTrue(demand.rate(west, 8.5 * 3600) > 4 * demand.rate(west, 3.5 * 3600));
        assertEquals(1.0, demand.profile().factor(Mode.CAR, 8.5 * 3600), 1e-9);
    }

    @Test
    void profileInterpolatesBetweenTheMiddlesOfTheHours() {
        DemandProfile p = DemandProfile.loadDefault();
        double at8 = p.factor(Mode.CAR, 8.5 * 3600);
        double at9 = p.factor(Mode.CAR, 9.5 * 3600);
        assertEquals((at8 + at9) / 2, p.factor(Mode.CAR, 9 * 3600), 1e-9);
    }

    @Test
    void readsVolumesAndRejectsNegativeOnes() throws Exception {
        String csv = "# comment\nroute,perHour,description\nW2_T,120,west\nN2_T, 30 ,north, with comma\n";
        Map<String, Double> v = DemandModel.readVolumes(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertEquals(Map.of("W2_T", 120.0, "N2_T", 30.0), v);
        assertThrows(IllegalArgumentException.class, () -> DemandModel.readVolumes(new ByteArrayInputStream(
                "route,perHour\nW2_T,-1\n".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void arrivalsFollowTheDemand() {
        DemandModel demand = new DemandModel(Map.of("W2_T", 720.0, "BN_fwd", 180.0, "Wb_T", 6.0),
                DemandProfile.flat(1.0));
        TrafficGenerator gen = new TrafficGenerator(NETWORK, demand, new SplittableRandom(3));
        Map<String, Integer> count = new HashMap<>();
        Map<AgentType, Integer> types = new HashMap<>();
        for (int i = 0; i < 36000; i++) {
            gen.generate(8 * 3600 + i * 0.1, 0.1, 1.0, (route, type) -> {
                count.merge(route.id(), 1, Integer::sum);
                types.merge(type, 1, Integer::sum);
            });
        }
        // one hour: 720 +- 4 sigma, 180 +- 4 sigma, buses to a timetable
        assertEquals(720, count.get("W2_T"), 4 * Math.sqrt(720));
        assertEquals(180, count.get("BN_fwd"), 4 * Math.sqrt(180));
        assertEquals(6, count.get("Wb_T"), 2);
        assertTrue(types.get(AgentType.CAR) > types.getOrDefault(AgentType.VAN, 0));
        assertTrue(types.containsKey(AgentType.BICYCLE) && types.containsKey(AgentType.BUS));
    }
}
