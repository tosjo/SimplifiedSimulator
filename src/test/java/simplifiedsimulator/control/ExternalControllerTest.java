package simplifiedsimulator.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import simplifiedsimulator.demand.DemandModel;
import simplifiedsimulator.demand.DemandProfile;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.sim.SignalState;
import simplifiedsimulator.sim.Simulation;
import simplifiedsimulator.sim.SimulationOptions;

class ExternalControllerTest {

    @Test
    void sendsSensorMessagesAndFollowsLightStates() throws Exception {
        Network network = Network.loadDefault();
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<String> received = CompletableFuture.supplyAsync(() -> {
                try (Socket s = server.accept()) {
                    InputStream in = s.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    int c;
                    while ((c = in.read()) != 0 && c != -1) {
                        sb.append((char) c);
                    }
                    OutputStream out = s.getOutputStream();
                    out.write("{\"lightstate\":{\"id\":5,\"state\":2}}\0".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Thread.sleep(500);
                    return sb.toString();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            Simulation sim = new Simulation(network, SimulationOptions.defaults()
                    .withDemand(new DemandModel(Map.of("N2_T", 600.0), DemandProfile.flat(1.0))));
            ExternalController external = new ExternalController("localhost", server.getLocalPort());
            sim.setController(external);
            for (int i = 0; i < 3000 && sim.signalState(network.signalGroup("05")) != SignalState.GREEN; i++) {
                sim.step();
                Thread.sleep(1);
            }
            assertEquals("{\"sensorstate\":{\"id\":5}}", received.get(5, TimeUnit.SECONDS));
            assertEquals(SignalState.GREEN, sim.signalState(network.signalGroup("05")));
            assertTrue(external.status().startsWith("connected"));
            external.close();
        }
    }
}
