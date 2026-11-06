package simplifiedsimulator.control;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.SignalGroup;
import simplifiedsimulator.sim.SignalState;

/**
 * The colour every signal group shows, and how long it has shown it. Built-in controllers
 * use {@link #startGreen} and {@link #endGreen}, which take care of the amber time and of
 * the intergreen times; an external controller sets colours directly with {@link #set}.
 */
public final class SignalPlan {

    private static final Logger LOG = Logger.getLogger(SignalPlan.class.getName());
    /** How much history is kept for the signal timeline display. */
    private static final double HISTORY_SECONDS = 300;

    /** A change of colour at a moment in time. */
    public record Change(double time, SignalState state) {
    }

    private static final class GroupState {
        SignalState state = SignalState.RED;
        double since;
        double greenEnded = Double.NEGATIVE_INFINITY;
        double amberUntil = Double.NaN;
        final Deque<Change> history = new ArrayDeque<>();
    }

    private final ConflictMatrix matrix;
    private final Map<SignalGroup, GroupState> groups = new LinkedHashMap<>();
    private double now;
    private int intergreenViolations;

    public SignalPlan(Network network, ConflictMatrix matrix) {
        this.matrix = matrix;
        for (SignalGroup g : network.signalGroups()) {
            GroupState gs = new GroupState();
            gs.history.add(new Change(0, SignalState.RED));
            groups.put(g, gs);
        }
    }

    /** Moves the clock forward and ends amber times that have run out. */
    public void advance(double time) {
        now = time;
        for (Map.Entry<SignalGroup, GroupState> e : groups.entrySet()) {
            GroupState gs = e.getValue();
            if (gs.state == SignalState.AMBER && !Double.isNaN(gs.amberUntil) && now >= gs.amberUntil - 1e-9) {
                change(e.getKey(), gs, SignalState.RED);
            }
        }
    }

    public double now() {
        return now;
    }

    public ConflictMatrix matrix() {
        return matrix;
    }

    public Iterable<SignalGroup> groups() {
        return groups.keySet();
    }

    public SignalState state(SignalGroup group) {
        return groups.get(group).state;
    }

    /** Seconds the group has shown its current colour. */
    public double timeInState(SignalGroup group) {
        return now - groups.get(group).since;
    }

    /** When the group's last green ended (the start of its amber), or minus infinity. */
    public double greenEndedAt(SignalGroup group) {
        return groups.get(group).greenEnded;
    }

    /**
     * True when the group may turn green now: it is red, every conflicting group is red, and
     * the intergreen time since each conflicting group's green has passed.
     */
    public boolean mayStartGreen(SignalGroup group) {
        if (state(group) != SignalState.RED) {
            return false;
        }
        for (SignalGroup other : matrix.conflictsOf(group)) {
            GroupState os = groups.get(other);
            if (os.state != SignalState.RED || now - os.greenEnded < matrix.intergreen(other, group) - 1e-9) {
                return false;
            }
        }
        return true;
    }

    /**
     * Turns a group green.
     *
     * @throws IllegalStateException if that would conflict with another group
     */
    public void startGreen(SignalGroup group) {
        if (!mayStartGreen(group)) {
            throw new IllegalStateException(group.id() + " may not turn green now");
        }
        change(group, groups.get(group), SignalState.GREEN);
    }

    /** Ends a green: amber for motor traffic and cyclists, straight to red for pedestrians. */
    public void endGreen(SignalGroup group) {
        GroupState gs = groups.get(group);
        if (gs.state != SignalState.GREEN) {
            return;
        }
        double amber = matrix.amber(group);
        if (amber > 0) {
            change(group, gs, SignalState.AMBER);
            gs.amberUntil = now + amber;
        } else {
            change(group, gs, SignalState.RED);
        }
    }

    /** Ends every green (used when the controller is replaced). */
    public void endAllGreens() {
        for (SignalGroup g : groups.keySet()) {
            endGreen(g);
        }
    }

    /**
     * Sets a colour directly, as an external controller does. Turning green before the
     * intergreen times allow is recorded as a violation but carried out.
     */
    public void set(SignalGroup group, SignalState state) {
        GroupState gs = groups.get(group);
        if (gs.state == state) {
            return;
        }
        if (state == SignalState.GREEN && !mayStartGreen(group)) {
            intergreenViolations++;
            LOG.warning(() -> "Signal group " + group.id() + " turned green too early or against a conflicting group");
        }
        change(group, gs, state);
        gs.amberUntil = Double.NaN;
    }

    /** Number of times a group was turned green against the intergreen times (external controllers only). */
    public int intergreenViolations() {
        return intergreenViolations;
    }

    /** Colour changes of the group in the last few minutes, oldest first. */
    public List<Change> history(SignalGroup group) {
        return new ArrayList<>(groups.get(group).history);
    }

    private void change(SignalGroup group, GroupState gs, SignalState next) {
        if (gs.state == SignalState.GREEN && next != SignalState.GREEN) {
            gs.greenEnded = now;
        }
        gs.state = next;
        gs.since = now;
        gs.history.addLast(new Change(now, next));
        // keep the change that was in force at the start of the window
        while (gs.history.size() > 1) {
            var it = gs.history.iterator();
            it.next();
            if (now - it.next().time() <= HISTORY_SECONDS) {
                break;
            }
            gs.history.removeFirst();
        }
    }
}
