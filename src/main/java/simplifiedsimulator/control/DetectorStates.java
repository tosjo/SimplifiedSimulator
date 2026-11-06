package simplifiedsimulator.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import simplifiedsimulator.network.Detector;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.SignalGroup;

/** The current state of every detector, updated by the simulation after each step. */
public final class DetectorStates {

    private final List<Detector> detectors;
    private final Map<Detector, State> states = new HashMap<>();
    private final Map<SignalGroup, List<Detector>> byGroup = new HashMap<>();
    private double now;

    private static final class State {
        boolean occupied;
        boolean risingEdge;
        boolean fallingEdge;
        double lastOccupied = Double.NEGATIVE_INFINITY;
    }

    public DetectorStates(Network network) {
        this.detectors = network.detectors();
        for (Detector d : detectors) {
            states.put(d, new State());
            byGroup.computeIfAbsent(d.group(), g -> new ArrayList<>()).add(d);
        }
    }

    /** Records which detectors are occupied at time {@code time}. */
    public void update(double time, Set<Detector> occupied) {
        now = time;
        for (Detector d : detectors) {
            State s = states.get(d);
            boolean occ = occupied.contains(d);
            s.risingEdge = occ && !s.occupied;
            s.fallingEdge = !occ && s.occupied;
            s.occupied = occ;
            if (occ) {
                s.lastOccupied = time;
            }
        }
    }

    public List<Detector> all() {
        return detectors;
    }

    /** Detectors that request or extend {@code group}. */
    public List<Detector> of(SignalGroup group) {
        return Collections.unmodifiableList(byGroup.getOrDefault(group, List.of()));
    }

    public boolean occupied(Detector d) {
        return states.get(d).occupied;
    }

    /** True in the step in which a road user arrived on the detector. */
    public boolean justOccupied(Detector d) {
        return states.get(d).risingEdge;
    }

    /** True in the step in which the last road user left the detector. */
    public boolean justFreed(Detector d) {
        return states.get(d).fallingEdge;
    }

    /** Seconds since the detector was last occupied; 0 while it is occupied. */
    public double timeSinceOccupied(Detector d) {
        return now - states.get(d).lastOccupied;
    }

    /** True when any detector of {@code group} is occupied now. */
    public boolean anyOccupied(SignalGroup group) {
        for (Detector d : of(group)) {
            if (occupied(d)) {
                return true;
            }
        }
        return false;
    }

    /** True when any detector of {@code group} was occupied less than {@code gap} seconds ago. */
    public boolean detectedWithin(SignalGroup group, double gap) {
        for (Detector d : of(group)) {
            if (timeSinceOccupied(d) <= gap) {
                return true;
            }
        }
        return false;
    }
}
