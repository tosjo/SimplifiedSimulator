package simplifiedsimulator.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class ControllerClientTest {

    @Test
    void exchangesMessagesWithController() throws Exception {
        BlockingQueue<LightUpdate> updates = new LinkedBlockingQueue<>();

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             ControllerClient client = new ControllerClient("127.0.0.1", server.getLocalPort(), updates::add)) {

            assertTrue(client.connect());
            try (Socket controller = server.accept()) {
                client.send(ProtocolCodec.encodeSensor(3));
                assertEquals("{\"sensorstate\":{\"id\":3}}", readMessage(controller.getInputStream()));

                OutputStream out = controller.getOutputStream();
                out.write(("{\"lightstate\":{\"id\":3,\"state\":2}}\0"
                        + "not json\0"
                        + "{\"lightstate\":{\"id\":4,\"state\":0}}\0").getBytes(StandardCharsets.UTF_8));
                out.flush();

                assertEquals(new LightUpdate(3, LightState.GREEN), updates.poll(5, TimeUnit.SECONDS));
                assertEquals(new LightUpdate(4, LightState.RED), updates.poll(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void reportsDisconnectWhenControllerCloses() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             ControllerClient client = new ControllerClient("127.0.0.1", server.getLocalPort(), update -> { })) {

            assertTrue(client.connect());
            server.accept().close();

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (client.isConnected() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertFalse(client.isConnected());
            client.send("ignored while disconnected");
        }
    }

    @Test
    void connectFailsWhenNobodyListens() throws Exception {
        int port;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = server.getLocalPort();
        }
        try (ControllerClient client = new ControllerClient("127.0.0.1", port, update -> { })) {
            assertFalse(client.connect());
            assertFalse(client.isConnected());
        }
    }

    private static String readMessage(InputStream in) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != 0) {
            buffer.write(b);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
