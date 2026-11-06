package simplifiedsimulator.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.SignalGroup;
import simplifiedsimulator.sim.SignalState;

/**
 * A fixed-time plan: the modules follow each other with fixed green times, whether or not
 * anybody is waiting. Green times are split over the modules with Webster's method, using
 * the expected peak-hour volumes, which is how fixed plans were traditionally designed.
 */
public final class FixedTimeController implements SignalController {

    /** Saturation flow of one lane, in vehicles per hour of green. */
    private static final double SATURATION_FLOW = 1800;
    private static final double MIN_MODULE_GREEN = 8;

    private ControlContext ctx;
    private ModuleStructure structure;
    private double[] greenTimes;
    private double cycleTime;
    private int current;
    /** When the current module's green ends; NaN until its first primary group turned green. */
    private double moduleEnd = Double.NaN;
    private final Map<SignalGroup, Double> endAt = new HashMap<>();
    private final List<SignalGroup> startedThisModule = new ArrayList<>();

    @Override
    public String name() {
        return "Fixed time";
    }

    @Override
    public void attach(ControlContext context) {
        this.ctx = context;
        this.structure = ModuleStructure.build(context);
        planGreenTimes();
        current = 0;
        moduleEnd = Double.NaN;
    }

    public ModuleStructure structure() {
        return structure;
    }

    /** Green time of each module, in seconds. */
    public double[] greenTimes() {
        return greenTimes.clone();
    }

    /** Planned cycle time in seconds. */
    public double cycleTime() {
        return cycleTime;
    }

    @Override
    public String status() {
        return String.format("cycle %.0f s, module %d of %d", cycleTime, current + 1, structure.modules().size());
    }

    private void planGreenTimes() {
        List<ModuleStructure.Module> modules = structure.modules();
        int n = modules.size();
        double[] y = new double[n];
        double total = 0;
        double lost = 0;
        for (int i = 0; i < n; i++) {
            for (SignalGroup g : modules.get(i).primary()) {
                if (g.mode() == Mode.CAR || g.mode() == Mode.BUS) {
                    double lanes = Math.max(1, g.routes().size());
                    y[i] = Math.max(y[i], ctx.volume(g) / (lanes * SATURATION_FLOW));
                }
            }
            total += y[i];
            // lost time: the longest intergreen into the next module, plus start-up loss
            ModuleStructure.Module next = modules.get((i + 1) % n);
            double switchTime = 0;
            for (SignalGroup a : modules.get(i).primary()) {
                for (SignalGroup b : next.primary()) {
                    switchTime = Math.max(switchTime, ctx.matrix().intergreen(a, b));
                }
            }
            lost += switchTime + 2;
        }
        // Webster's optimum cycle, kept within the usual Dutch range
        cycleTime = total < 0.9 ? (1.5 * lost + 5) / (1 - total) : 120;
        cycleTime = Math.max(60, Math.min(120, cycleTime));
        greenTimes = new double[n];
        double effective = Math.max(n * MIN_MODULE_GREEN, cycleTime - lost);
        for (int i = 0; i < n; i++) {
            double share = total > 0 ? y[i] / total : 1.0 / n;
            greenTimes[i] = Math.max(MIN_MODULE_GREEN, Math.round(effective * share));
        }
    }

    /**
     * How much earlier than the module's primaries an optional group has to end, so that its
     * (longer) intergreen does not delay the next module's primaries.
     */
    private double extraClearance(SignalGroup g, ModuleStructure.Module next) {
        ConflictMatrix m = ctx.matrix();
        double extra = 0;
        for (SignalGroup p : next.primary()) {
            double primariesClear = 0;
            for (SignalGroup q : structure.modules().get(current).primary()) {
                primariesClear = Math.max(primariesClear, m.intergreen(q, p));
            }
            extra = Math.max(extra, m.intergreen(g, p) - primariesClear);
        }
        return extra;
    }

    @Override
    public void update(double dt) {
        SignalPlan plan = ctx.plan();
        double now = plan.now();
        List<ModuleStructure.Module> modules = structure.modules();
        ModuleStructure.Module module = modules.get(current);

        // the module's green time runs from the moment its first primary group turns green
        if (Double.isNaN(moduleEnd)) {
            for (SignalGroup g : module.primary()) {
                if (plan.state(g) == SignalState.GREEN) {
                    moduleEnd = now + greenTimes[current];
                    break;
                }
            }
        }

        if (!Double.isNaN(moduleEnd) && now >= moduleEnd) {
            current = (current + 1) % modules.size();
            ModuleStructure.Module next = modules.get(current);
            for (SignalGroup g : plan.groups()) {
                if (plan.state(g) == SignalState.GREEN) {
                    if (next.members().contains(g)) {
                        // stays green into the next module and ends with it
                        endAt.remove(g);
                    } else {
                        // let a late starter have its minimum green, then end it
                        endAt.put(g, now - plan.timeInState(g) + ctx.timing(g).minGreen());
                    }
                }
            }
            startedThisModule.clear();
            module = next;
            moduleEnd = Double.NaN;
            for (SignalGroup g : module.primary()) {
                if (plan.state(g) == SignalState.GREEN) {
                    moduleEnd = now + greenTimes[current];
                    break;
                }
            }
        }

        for (SignalGroup g : module.members()) {
            if (startedThisModule.contains(g) || plan.state(g) != SignalState.RED || !plan.mayStartGreen(g)) {
                continue;
            }
            if (module.primary().contains(g)) {
                plan.startGreen(g);
                startedThisModule.add(g);
                if (Double.isNaN(moduleEnd)) {
                    moduleEnd = now + greenTimes[current];
                }
                continue;
            }
            if (Double.isNaN(moduleEnd)) {
                continue; // optional greens wait until the module has started
            }
            double end = moduleEnd - extraClearance(g, modules.get((current + 1) % modules.size()));
            if (end - now >= ctx.timing(g).minGreen()) {
                plan.startGreen(g);
                startedThisModule.add(g);
                endAt.put(g, end);
            }
        }

        // optional greens carried over from the previous module must also make way in time
        if (!Double.isNaN(moduleEnd)) {
            ModuleStructure.Module next = modules.get((current + 1) % modules.size());
            for (SignalGroup g : module.members()) {
                if (!module.primary().contains(g) && plan.state(g) == SignalState.GREEN && !endAt.containsKey(g)) {
                    endAt.put(g, moduleEnd - extraClearance(g, next));
                }
            }
        }

        for (SignalGroup g : plan.groups()) {
            Double end = endAt.get(g);
            if (end != null && plan.state(g) == SignalState.GREEN && now >= end
                    && plan.timeInState(g) >= ctx.timing(g).minGreen()) {
                plan.endGreen(g);
                endAt.remove(g);
            }
        }
    }
}
