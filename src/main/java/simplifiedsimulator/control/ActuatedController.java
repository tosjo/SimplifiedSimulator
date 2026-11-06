package simplifiedsimulator.control;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import simplifiedsimulator.network.Detector;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.SignalGroup;
import simplifiedsimulator.sim.SignalState;

/**
 * Vehicle-actuated control in the Dutch style.
 *
 * <ul>
 *   <li>A group only gets green when somebody is waiting for it: a vehicle on a loop, a cyclist
 *       on the detector at the stop line or a pedestrian who pressed the button (an "aanvraag").</li>
 *   <li>The modules of the {@link ModuleStructure} take turns; modules nobody is waiting for are
 *       skipped, and with no traffic at all every light rests at red.</li>
 *   <li>A green is extended while the loops keep detecting traffic (gap-out after
 *       {@link GroupTiming#gap()} seconds without a vehicle), up to the maximum green.</li>
 *   <li>Groups outside the current module may get green in between when they do not conflict
 *       with anything green or waiting ("alternatieve realisatie").</li>
 *   <li>Buses get priority: a bus on the bus lane's loops makes its module the next one, cuts
 *       the current module short and extends the bus green.</li>
 * </ul>
 */
public final class ActuatedController implements SignalController {

    /** Extra green a bus may get to make it through. */
    private static final double BUS_EXTENSION = 10;
    /** Longest green for a group that got green outside its own module. */
    private static final double ALTERNATIVE_MAX = 12;
    /** When a bus is waiting elsewhere, the current module gets at most this beyond minimum green. */
    private static final double PRIORITY_CUT = 4;

    private ControlContext ctx;
    private ModuleStructure structure;
    private int current = -1;
    private int last = -1;
    private final Set<SignalGroup> requests = new HashSet<>();
    private final Set<SignalGroup> served = new HashSet<>();
    private final Set<SignalGroup> alternative = new HashSet<>();

    @Override
    public String name() {
        return "Vehicle actuated";
    }

    @Override
    public void attach(ControlContext context) {
        this.ctx = context;
        this.structure = ModuleStructure.build(context);
        current = -1;
        last = structure.modules().size() - 1;
    }

    public ModuleStructure structure() {
        return structure;
    }

    /** Groups that somebody is waiting for. */
    public Set<SignalGroup> requests() {
        return Set.copyOf(requests);
    }

    @Override
    public String status() {
        return current < 0 ? "resting at red" : "module " + (current + 1) + " of " + structure.modules().size();
    }

    @Override
    public void update(double dt) {
        SignalPlan plan = ctx.plan();
        updateRequests(plan);

        if (current >= 0 && moduleFinished(plan)) {
            last = current;
            current = -1;
        }
        if (current < 0) {
            current = nextModule();
            served.clear();
        }

        SignalGroup busWaiting = waitingBus(plan);
        if (current >= 0) {
            ModuleStructure.Module module = structure.modules().get(current);
            for (SignalGroup g : module.members()) {
                if (requests.contains(g) && !served.contains(g) && plan.state(g) == SignalState.RED) {
                    endConflictingAlternatives(plan, g);
                    if (plan.mayStartGreen(g)) {
                        plan.startGreen(g);
                        served.add(g);
                        alternative.remove(g);
                    }
                }
            }
        }

        for (SignalGroup g : plan.groups()) {
            if (plan.state(g) == SignalState.GREEN && !keepGreen(plan, g, busWaiting)) {
                plan.endGreen(g);
                alternative.remove(g);
            }
        }

        startAlternatives(plan);
    }

    private void updateRequests(SignalPlan plan) {
        DetectorStates det = ctx.detectors();
        for (SignalGroup g : plan.groups()) {
            if (plan.state(g) == SignalState.GREEN) {
                requests.remove(g);
            } else if (det.anyOccupied(g)) {
                requests.add(g);
            }
        }
    }

    /** A module is done when nothing in it is green and everybody who asked has had a turn. */
    private boolean moduleFinished(SignalPlan plan) {
        ModuleStructure.Module module = structure.modules().get(current);
        for (SignalGroup g : module.members()) {
            if (plan.state(g) == SignalState.GREEN && !alternative.contains(g)) {
                return false;
            }
            if (requests.contains(g) && !served.contains(g) && module.primary().contains(g)) {
                return false;
            }
        }
        // members that are not primary only get a turn if they could start while the primaries ran
        for (SignalGroup g : module.members()) {
            if (requests.contains(g) && !served.contains(g) && plan.mayStartGreen(g)) {
                return false;
            }
        }
        return true;
    }

    /** The next module with somebody waiting, buses first; -1 when nobody is waiting. */
    private int nextModule() {
        List<ModuleStructure.Module> modules = structure.modules();
        for (int k = 1; k <= modules.size(); k++) {
            int i = (last + k) % modules.size();
            for (SignalGroup g : modules.get(i).primary()) {
                if (g.mode() == Mode.BUS && requests.contains(g)) {
                    return i;
                }
            }
        }
        for (int k = 1; k <= modules.size(); k++) {
            int i = (last + k) % modules.size();
            for (SignalGroup g : modules.get(i).primary()) {
                if (requests.contains(g)) {
                    return i;
                }
            }
        }
        for (int k = 1; k <= modules.size(); k++) {
            int i = (last + k) % modules.size();
            for (SignalGroup g : modules.get(i).members()) {
                if (requests.contains(g)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** A bus group that is waiting at red outside the current module, or null. */
    private SignalGroup waitingBus(SignalPlan plan) {
        for (SignalGroup g : requests) {
            if (g.mode() == Mode.BUS && plan.state(g) == SignalState.RED
                    && (current < 0 || !structure.modules().get(current).members().contains(g))) {
                return g;
            }
        }
        return null;
    }

    private boolean keepGreen(SignalPlan plan, SignalGroup g, SignalGroup busWaiting) {
        GroupTiming timing = ctx.timing(g);
        double t = plan.timeInState(g);
        if (t < timing.minGreen()) {
            return true;
        }
        double max = alternative.contains(g) ? Math.min(timing.maxGreen(), ALTERNATIVE_MAX) : timing.maxGreen();
        if (g.mode() == Mode.BUS && busApproaching(g)) {
            max += BUS_EXTENSION;
        }
        if (busWaiting != null && ctx.matrix().conflicts(g, busWaiting)) {
            max = Math.min(max, timing.minGreen() + PRIORITY_CUT);
        }
        if (t >= max) {
            return false;
        }
        if (alternative.contains(g) && conflictsWithWaitingMember(g)) {
            return false;
        }
        return timing.gap() > 0 && ctx.detectors().detectedWithin(g, timing.gap());
    }

    private boolean busApproaching(SignalGroup g) {
        for (Detector d : ctx.detectors().of(g)) {
            if (d.role() == Detector.Role.FAR && ctx.detectors().timeSinceOccupied(d) < 8) {
                return true;
            }
        }
        return false;
    }

    private boolean conflictsWithWaitingMember(SignalGroup g) {
        if (current < 0) {
            return false;
        }
        for (SignalGroup m : structure.modules().get(current).members()) {
            if (requests.contains(m) && !served.contains(m) && ctx.matrix().conflicts(g, m)) {
                return true;
            }
        }
        return false;
    }

    /** Ends alternative greens that stand in the way of a waiting module member (after their minimum green). */
    private void endConflictingAlternatives(SignalPlan plan, SignalGroup waiting) {
        for (SignalGroup g : List.copyOf(alternative)) {
            if (ctx.matrix().conflicts(g, waiting) && plan.state(g) == SignalState.GREEN
                    && plan.timeInState(g) >= ctx.timing(g).minGreen()) {
                plan.endGreen(g);
                alternative.remove(g);
            }
        }
    }

    private void startAlternatives(SignalPlan plan) {
        for (SignalGroup g : plan.groups()) {
            if (!requests.contains(g) || plan.state(g) != SignalState.RED || !plan.mayStartGreen(g)
                    || conflictsWithWaitingMember(g)) {
                continue;
            }
            if (current >= 0 && structure.modules().get(current).members().contains(g)) {
                continue;
            }
            if (!fitsBeside(plan, g)) {
                continue;
            }
            plan.startGreen(g);
            alternative.add(g);
        }
    }

    /** True when nothing conflicting is green or amber, so {@code g} can run alongside. */
    private boolean fitsBeside(SignalPlan plan, SignalGroup g) {
        for (SignalGroup other : ctx.matrix().conflictsOf(g)) {
            if (plan.state(other) != SignalState.RED) {
                return false;
            }
        }
        return true;
    }
}
