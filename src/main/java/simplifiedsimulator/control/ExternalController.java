package simplifiedsimulator.control;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import simplifiedsimulator.network.Detector;
import simplifiedsimulator.network.SignalGroup;
import simplifiedsimulator.net.ControllerClient;
import simplifiedsimulator.net.LightState;
import simplifiedsimulator.net.LightUpdate;
import simplifiedsimulator.net.ProtocolCodec;
import simplifiedsimulator.sim.SignalState;

/**
 * Hands control to an external traffic light controller over TCP, using the original
 * JSON protocol of the project (see {@link ProtocolCodec}). Light and sensor ids are the
 * signal group numbers (2 for group 02, 41 for group 41, ...).
 *
 * <p>The simulator sends a sensor message whenever a road user arrives on one of a group's
 * detectors and the controller answers with light states. The simulator does not stop a
 * controller from showing conflicting greens, but counts them, so a controller can be tested
 * against the crossing.
 */
public final class ExternalController implements SignalController {

    private static final Logger LOG = Logger.getLogger(ExternalController.class.getName());

    private final String host;
    private final int port;
    private final Queue<LightUpdate> incoming = new ConcurrentLinkedQueue<>();
    private final Set<Integer> unknownIds = new HashSet<>();
    private final Map<Integer, SignalGroup> byNumber = new HashMap<>();
    private ControllerClient client;
    private ControlContext ctx;
    private volatile boolean connecting;
    /** The name the controller announced, if any. */
    private volatile String announcedName;
    /** In lockstep every step waits for the controller's reply to the clock (for testing controllers). */
    private volatile boolean lockstep;
    /** Whether the controller asked to hear when loops become free. */
    private volatile boolean reportFreed;
    private boolean lockstepStarted;
    private long lockstepTimeoutMs = 5000;
    private final BlockingQueue<Double> clockReplies = new LinkedBlockingQueue<>();

    public ExternalController(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String name() {
        String announced = announcedName;
        return announced != null ? announced : "External controller";
    }

    @Override
    public void attach(ControlContext context) {
        this.ctx = context;
        for (SignalGroup g : context.network().signalGroups()) {
            byNumber.put(g.number(), g);
        }
        client = new ControllerClient(host, port, incoming::add, name -> announcedName = name, clockReplies::add,
                features -> reportFreed = features.contains("sensorfree"));
        connect();
    }

    /** Tries to (re)connect in the background. */
    public void connect() {
        if (connecting || client.isConnected()) {
            return;
        }
        connecting = true;
        Thread t = new Thread(() -> {
            try {
                client.connect();
            } finally {
                connecting = false;
            }
        }, "controller-connect");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Switches lockstep on or off. In lockstep each step sends the simulated time after the
     * sensor messages and waits up to {@code timeoutMs} for the controller to answer, so the
     * controller's light states take effect in the same step, whatever the simulation speed.
     * A controller that does not answer in time switches lockstep off again.
     */
    public void setLockstep(boolean on, long timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("Timeout must be positive, got " + timeoutMs);
        }
        lockstep = on;
        lockstepStarted = false;
        lockstepTimeoutMs = timeoutMs;
    }

    public boolean lockstep() {
        return lockstep;
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public String status() {
        if (isConnected()) {
            return "connected to " + client.describe();
        }
        return (connecting ? "connecting to " : "not connected to ") + host + ":" + port;
    }

    @Override
    public void update(double dt) {
        if (lockstep && isConnected()) {
            double time = ctx.plan().now();
            if (!lockstepStarted) {
                // a first clock before any sensor message, so the controller has switched to the
                // simulator's time before it hears about any road user
                lockstepStarted = true;
                client.send(ProtocolCodec.encodeClock(time));
                awaitClockReply(time);
            }
            if (lockstep) {
                sendSensors();
                client.send(ProtocolCodec.encodeClock(time));
                awaitClockReply(time);
            }
        }
        applyLightUpdates();
        if (!lockstep && isConnected()) {
            sendSensors();
        }
    }

    private void awaitClockReply(double time) {
        long deadline = System.nanoTime() + lockstepTimeoutMs * 1_000_000;
        try {
            while (true) {
                long left = deadline - System.nanoTime();
                Double reply = left > 0 ? clockReplies.poll(left, TimeUnit.NANOSECONDS) : null;
                if (reply == null) {
                    LOG.warning(() -> "Controller did not answer the clock within " + lockstepTimeoutMs
                            + " ms; lockstep switched off");
                    lockstep = false;
                    return;
                }
                if (Math.abs(reply - time) < 1e-6) {
                    return;  // older replies are skipped
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendSensors() {
        for (Detector d : ctx.detectors().all()) {
            if (ctx.detectors().justOccupied(d)) {
                client.send(ProtocolCodec.encodeSensor(d.group().number()));
            }
            if (reportFreed && ctx.detectors().justFreed(d)) {
                client.send(ProtocolCodec.encodeSensorFree(d.group().number()));
            }
        }
    }

    private void applyLightUpdates() {
        LightUpdate update;
        while ((update = incoming.poll()) != null) {
            SignalGroup group = byNumber.get(update.lightId());
            if (group == null) {
                int id = update.lightId();
                if (unknownIds.add(id)) {
                    LOG.warning(() -> "Controller switched unknown signal group " + id);
                }
                continue;
            }
            ctx.plan().set(group, toSignalState(update.state()));
        }
    }

    static SignalState toSignalState(LightState state) {
        return switch (state) {
            case RED -> SignalState.RED;
            case ORANGE -> SignalState.AMBER;
            case GREEN -> SignalState.GREEN;
        };
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
