package simplifiedsimulator.control;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.SignalGroup;

/**
 * The order in which conflicting traffic streams take turns (a Dutch "modulestructuur").
 *
 * <p>Each module has primary groups, the motor traffic streams it exists for, and member
 * groups: every group that can be green at the same time as all the primaries, including
 * cyclists, pedestrians and slip roads, which may therefore get green in several modules.
 * The structure is built automatically from the conflict matrix: the smallest set of modules
 * that gives every motor stream a turn, balanced so that busy streams share a module.
 */
public final class ModuleStructure {

    /**
     * @param primary the motor traffic streams the module exists for
     * @param members every group that may be green during the module (includes the primaries)
     */
    public record Module(List<SignalGroup> primary, List<SignalGroup> members) {

        public Module {
            primary = List.copyOf(primary);
            members = List.copyOf(members);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (SignalGroup g : members) {
                sb.append(sb.isEmpty() ? "" : " ").append(primary.contains(g) ? g.id() : "(" + g.id() + ")");
            }
            return sb.toString();
        }
    }

    private final List<Module> modules;

    private ModuleStructure(List<Module> modules) {
        this.modules = List.copyOf(modules);
    }

    public List<Module> modules() {
        return modules;
    }

    /** Builds the module structure for the crossing in {@code context}. */
    public static ModuleStructure build(ControlContext context) {
        ConflictMatrix matrix = context.matrix();
        Comparator<SignalGroup> busiestFirst = Comparator.comparingDouble((SignalGroup g) -> -context.volume(g))
                .thenComparing(SignalGroup::id);
        List<SignalGroup> all = new ArrayList<>();
        context.network().signalGroups().forEach(all::add);
        all.sort(busiestFirst);
        List<SignalGroup> motor = all.stream().filter(ModuleStructure::isMotor).toList();
        List<SignalGroup> slow = all.stream().filter(g -> !isMotor(g)).toList();

        List<List<SignalGroup>> primaries = uniquePrimaries(bestCover(motor, matrix, context), context);
        // slow traffic that fits beside no motor module gets a module of its own
        for (SignalGroup g : slow) {
            if (primaries.stream().noneMatch(p -> compatible(matrix, g, p))) {
                List<SignalGroup> block = new ArrayList<>(List.of(g));
                for (SignalGroup h : slow) {
                    if (h != g && compatible(matrix, h, block)) {
                        block.add(h);
                    }
                }
                if (primaries.stream().noneMatch(p -> p.containsAll(block))) {
                    primaries.add(block);
                }
            }
        }

        List<Module> modules = new ArrayList<>();
        for (List<SignalGroup> p : order(primaries, matrix)) {
            List<SignalGroup> members = new ArrayList<>(p);
            // cyclists and pedestrians first: they have nowhere else to go in a fixed plan
            for (List<SignalGroup> candidates : List.of(slow, motor)) {
                for (SignalGroup g : candidates) {
                    if (!members.contains(g) && compatible(matrix, g, members)) {
                        members.add(g);
                    }
                }
            }
            modules.add(new Module(p, members));
        }
        return new ModuleStructure(modules);
    }

    private static boolean isMotor(SignalGroup g) {
        return g.mode() == Mode.CAR || g.mode() == Mode.BUS;
    }

    /**
     * The cheapest way to give every motor stream a turn: as few modules as possible, and among
     * those, the smallest sum over the modules of the heaviest load (volume per lane) that has
     * to be served in that module. Modules are maximal sets of mutually compatible streams.
     */
    private static List<List<SignalGroup>> bestCover(List<SignalGroup> motor, ConflictMatrix matrix,
                                                     ControlContext context) {
        List<List<SignalGroup>> cliques = new ArrayList<>();
        maximalSets(new ArrayList<>(), new ArrayList<>(motor), new ArrayList<>(), matrix, cliques);
        for (int k = 1; k <= Math.min(cliques.size(), 8); k++) {
            List<List<SignalGroup>> best = null;
            double bestCost = Double.MAX_VALUE;
            int[] pick = new int[k];
            for (int i = 0; i < k; i++) {
                pick[i] = i;
            }
            while (true) {
                List<List<SignalGroup>> cover = new ArrayList<>();
                for (int i : pick) {
                    cover.add(cliques.get(i));
                }
                if (covers(cover, motor)) {
                    double cost = cost(cover, context);
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = cover;
                    }
                }
                if (!nextCombination(pick, cliques.size())) {
                    break;
                }
            }
            if (best != null) {
                return new ArrayList<>(best.stream().map(ArrayList::new).toList());
            }
        }
        throw new IllegalStateException("No module structure found");
    }

    /**
     * Makes every stream primary in one module only: the least busy module it fits in. In the
     * other modules it stays an optional member, so cyclists and pedestrians can be given
     * green there instead.
     */
    private static List<List<SignalGroup>> uniquePrimaries(List<List<SignalGroup>> cover, ControlContext context) {
        List<List<SignalGroup>> out = new ArrayList<>();
        for (List<SignalGroup> m : cover) {
            out.add(new ArrayList<>());
        }
        List<SignalGroup> groups = cover.stream().flatMap(List::stream).distinct()
                .sorted(Comparator.comparingDouble((SignalGroup g) -> -context.volume(g)).thenComparing(SignalGroup::id))
                .toList();
        for (SignalGroup g : groups) {
            int best = -1;
            double bestLoad = Double.MAX_VALUE;
            for (int i = 0; i < cover.size(); i++) {
                if (!cover.get(i).contains(g)) {
                    continue;
                }
                double load = 0;
                for (SignalGroup h : out.get(i)) {
                    load = Math.max(load, context.volume(h) / Math.max(1, h.routes().size()));
                }
                if (load < bestLoad) {
                    bestLoad = load;
                    best = i;
                }
            }
            out.get(best).add(g);
        }
        out.removeIf(List::isEmpty);
        return out;
    }

    /** Bron-Kerbosch enumeration of the maximal sets of mutually compatible groups. */
    private static void maximalSets(List<SignalGroup> current, List<SignalGroup> candidates,
                                    List<SignalGroup> excluded, ConflictMatrix matrix, List<List<SignalGroup>> out) {
        if (candidates.isEmpty() && excluded.isEmpty()) {
            out.add(List.copyOf(current));
            return;
        }
        for (SignalGroup g : List.copyOf(candidates)) {
            List<SignalGroup> next = new ArrayList<>(current);
            next.add(g);
            maximalSets(next, candidates.stream().filter(h -> h != g && !matrix.conflicts(g, h)).toList(),
                    excluded.stream().filter(h -> !matrix.conflicts(g, h)).toList(), matrix, out);
            candidates = new ArrayList<>(candidates);
            candidates.remove(g);
            excluded = new ArrayList<>(excluded);
            excluded.add(g);
        }
    }

    private static boolean covers(List<List<SignalGroup>> cover, List<SignalGroup> groups) {
        for (SignalGroup g : groups) {
            if (cover.stream().noneMatch(m -> m.contains(g))) {
                return false;
            }
        }
        return true;
    }

    /** Sum over modules of the heaviest per-lane load, sharing a stream's load over its modules. */
    private static double cost(List<List<SignalGroup>> cover, ControlContext context) {
        double total = 0;
        for (List<SignalGroup> module : cover) {
            double max = 0;
            for (SignalGroup g : module) {
                long times = cover.stream().filter(m -> m.contains(g)).count();
                double load = context.volume(g) / Math.max(1, g.routes().size()) / times;
                max = Math.max(max, load);
            }
            total += max;
        }
        return total;
    }

    private static boolean nextCombination(int[] pick, int n) {
        int k = pick.length;
        int i = k - 1;
        while (i >= 0 && pick[i] == n - k + i) {
            i--;
        }
        if (i < 0) {
            return false;
        }
        pick[i]++;
        for (int j = i + 1; j < k; j++) {
            pick[j] = pick[j - 1] + 1;
        }
        return true;
    }

    /** Orders the modules so that the time lost between consecutive modules is small. */
    private static List<List<SignalGroup>> order(List<List<SignalGroup>> blocks, ConflictMatrix matrix) {
        List<List<SignalGroup>> left = new ArrayList<>(blocks);
        List<List<SignalGroup>> out = new ArrayList<>();
        List<SignalGroup> current = left.remove(0);
        out.add(current);
        while (!left.isEmpty()) {
            List<SignalGroup> best = left.get(0);
            double bestCost = Double.MAX_VALUE;
            for (List<SignalGroup> candidate : left) {
                double cost = 0;
                for (SignalGroup a : current) {
                    for (SignalGroup b : candidate) {
                        cost = Math.max(cost, matrix.intergreen(a, b));
                    }
                }
                if (cost < bestCost) {
                    bestCost = cost;
                    best = candidate;
                }
            }
            left.remove(best);
            out.add(best);
            current = best;
        }
        return out;
    }

    static boolean compatible(ConflictMatrix matrix, SignalGroup g, List<SignalGroup> groups) {
        for (SignalGroup h : groups) {
            if (matrix.conflicts(g, h)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < modules.size(); i++) {
            sb.append("M").append(i + 1).append(": ").append(modules.get(i)).append('\n');
        }
        return sb.toString();
    }
}
