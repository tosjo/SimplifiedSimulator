package simplifiedsimulator.render.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import simplifiedsimulator.network.Lane;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Network;

class StreetLightsTest {

    @Test
    void lightsStandBesideTheRoadNotOnIt() {
        Network network = Network.loadDefault();
        List<StreetLights.Placement> placements = StreetLights.place(network);
        assertTrue(placements.size() > 20, "found " + placements.size());
        for (StreetLights.Placement p : placements) {
            for (Lane lane : network.lanes()) {
                if (lane.mode() == Mode.CAR || lane.mode() == Mode.BUS) {
                    assertTrue(lane.path().distanceTo(p.position()) > lane.width() / 2,
                            "light at " + p.position() + " stands on " + lane.id());
                }
            }
        }
        for (int i = 0; i < placements.size(); i++) {
            for (int j = i + 1; j < placements.size(); j++) {
                assertTrue(placements.get(i).position().distance(placements.get(j).position()) > 15);
            }
        }
    }

    @Test
    void symbolsAreDrawn() {
        for (SignalSymbols.Symbol s : SignalSymbols.Symbol.values()) {
            var raster = SignalSymbols.raster(s);
            float covered = 0;
            for (int y = 0; y < raster.height(); y++) {
                for (int x = 0; x < raster.width(); x++) {
                    covered += raster.get(x, y).a;
                }
            }
            float share = covered / (raster.width() * raster.height());
            assertTrue(share > 0.03 && share < 0.8, s + " covers " + share);
        }
    }
}
