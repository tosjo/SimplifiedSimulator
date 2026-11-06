package simplifiedsimulator.control;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
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
        client = new ControllerClient(host, port, incoming::add, name -> announcedName = name);
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
        if (isConnected()) {
            for (Detector d : ctx.detectors().all()) {
                if (ctx.detectors().justOccupied(d)) {
                    client.send(ProtocolCodec.encodeSensor(d.group().number()));
                }
            }
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
