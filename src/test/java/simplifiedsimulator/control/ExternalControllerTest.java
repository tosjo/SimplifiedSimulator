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

    /** Reads NUL-terminated messages until the stream ends. */
    private static String readMessage(InputStream in) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != 0) {
            if (c == -1) {
                return null;
            }
            sb.append((char) c);
        }
        return sb.toString();
    }

    @Test
    void lockstepAppliesTheControllersAnswerInTheSameStep() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Void> controller = CompletableFuture.runAsync(() -> {
                try (Socket s = server.accept()) {
                    InputStream in = s.getInputStream();
                    OutputStream out = s.getOutputStream();
                    String message;
                    while ((message = readMessage(in)) != null) {
                        if (message.startsWith("{\"clock\"")) {
                            String time = message.replaceAll("[^0-9.]", "");
                            out.write(("{\"lightstate\":{\"id\":5,\"state\":2}}\0{\"clockdone\":{\"time\":" + time
                                    + "}}\0").getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            Simulation sim = Simulation.createDefault();
            ExternalController external = new ExternalController("localhost", server.getLocalPort());
            external.setLockstep(true, 5000);
            sim.setController(external);
            waitUntilConnected(external);
            sim.step();
            assertEquals(SignalState.GREEN, sim.signalState(sim.network().signalGroup("05")),
                    "the answer to this step's clock is applied in this step");
            assertTrue(external.lockstep());
            external.close();
            controller.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void lockstepGivesUpWhenTheControllerStopsAnswering() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Void> silent = CompletableFuture.runAsync(() -> {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(3000);
                    s.getInputStream().readAllBytes();  // listen, never answer
                } catch (java.net.SocketTimeoutException e) {
                    // the test is over
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            Simulation sim = Simulation.createDefault();
            ExternalController external = new ExternalController("localhost", server.getLocalPort());
            external.setLockstep(true, 200);
            sim.setController(external);
            waitUntilConnected(external);
            long start = System.nanoTime();
            sim.step();
            sim.step();
            long ms = (System.nanoTime() - start) / 1_000_000;
            assertTrue(ms < 1500, "waited only once, " + ms + " ms");
            org.junit.jupiter.api.Assertions.assertFalse(external.lockstep(), "lockstep switched itself off");
            external.close();
            silent.get(5, TimeUnit.SECONDS);
        }
    }

    private static void waitUntilConnected(ExternalController external) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!external.isConnected() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(external.isConnected());
    }

    @Test
    void takesTheNameTheControllerAnnounces() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Void> controller = CompletableFuture.runAsync(() -> {
                try (Socket s = server.accept()) {
                    OutputStream out = s.getOutputStream();
                    out.write("{\"controllername\":{\"name\":\"Smart\"}}\0".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            ExternalController external = new ExternalController("localhost", server.getLocalPort());
            assertEquals("External controller", external.name());
            Simulation sim = Simulation.createDefault();
            sim.setController(external);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!external.name().equals("Smart") && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals("Smart", external.name());
            external.close();
            controller.get(5, TimeUnit.SECONDS);
        }
    }
}
