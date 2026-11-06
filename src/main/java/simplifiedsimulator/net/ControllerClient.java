package simplifiedsimulator.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TCP connection to the external traffic light controller.
 *
 * <p>Messages in both directions are JSON strings terminated by a NUL ({@code '\0'})
 * character. Incoming light updates are read on a background thread and handed to the
 * listener on that thread, so the listener has to pass them to the render thread itself.
 */
public final class ControllerClient implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ControllerClient.class.getName());
    private static final char TERMINATOR = '\0';
    private static final int CONNECT_TIMEOUT_MS = 3000;

    private final String host;
    private final int port;
    private final Consumer<LightUpdate> listener;

    private Socket socket;
    private Writer out;
    private volatile boolean connected;

    public ControllerClient(String host, int port, Consumer<LightUpdate> listener) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Connects to the controller and starts listening for light updates.
     *
     * @return {@code true} if the connection was established
     */
    public synchronized boolean connect() {
        if (connected) {
            return true;
        }
        try {
            Socket newSocket = new Socket();
            newSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket = newSocket;
            out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            Reader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            connected = true;

            Thread reader = new Thread(() -> readLoop(newSocket, in), "controller-reader");
            reader.setDaemon(true);
            reader.start();
            LOG.info(() -> "Connected to controller at " + describe());
            return true;
        } catch (IOException e) {
            LOG.warning(() -> "Could not connect to controller at " + describe() + ": " + e.getMessage());
            closeQuietly();
            return false;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public String describe() {
        return host + ":" + port;
    }

    /** Sends a message to the controller. Does nothing when not connected. */
    public synchronized void send(String message) {
        if (!connected) {
            return;
        }
        try {
            out.write(message);
            out.write(TERMINATOR);
            out.flush();
        } catch (IOException e) {
            LOG.warning(() -> "Lost connection to controller: " + e.getMessage());
            closeQuietly();
        }
    }

    private void readLoop(Socket ownSocket, Reader in) {
        StringBuilder message = new StringBuilder();
        try {
            int c;
            while ((c = in.read()) != -1) {
                if (c == TERMINATOR) {
                    handle(message.toString());
                    message.setLength(0);
                } else {
                    message.append((char) c);
                }
            }
            LOG.info("Controller closed the connection");
        } catch (IOException e) {
            if (connected) {
                LOG.warning(() -> "Lost connection to controller: " + e.getMessage());
            }
        } finally {
            synchronized (this) {
                // Only tear down the connection this thread was reading from.
                if (socket == ownSocket) {
                    closeQuietly();
                }
            }
        }
    }

    private void handle(String message) {
        if (message.isBlank()) {
            return;
        }
        ProtocolCodec.decodeLight(message).ifPresentOrElse(update -> {
            try {
                listener.accept(update);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Error while handling " + message, e);
            }
        }, () -> LOG.warning(() -> "Ignoring unrecognised message: " + message));
    }

    @Override
    public synchronized void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        connected = false;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Nothing sensible to do here.
            }
            socket = null;
        }
        out = null;
    }
}
