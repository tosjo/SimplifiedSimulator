package simplifiedsimulator.render.preview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import simplifiedsimulator.network.Lane;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Movement;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.Polyline;
import simplifiedsimulator.network.Route;
import simplifiedsimulator.network.SignalGroup;
import simplifiedsimulator.network.Vec2;
import simplifiedsimulator.sim.AgentType;
import simplifiedsimulator.sim.AgentView;
import simplifiedsimulator.sim.Indicator;
import simplifiedsimulator.sim.SignalState;
import simplifiedsimulator.sim.SimulationView;

/**
 * A deliberately simple stand-in for the real simulation, used only to preview the
 * renderer: road users drive their route, keep their distance on the same route and stop
 * at red lights; the lights run a fixed four-stage cycle.
 */
public final class FakeSimulation implements SimulationView {

    private static final String[][] STAGES = {
        {"01", "02", "07", "08", "41", "42", "22", "32"},
        {"03", "09"},
        {"04", "05", "10", "11", "24", "34", "26", "36"},
        {"06", "12"},
    };
    private static final double[] GREEN = {24, 9, 16, 9};
    private static final double AMBER = 3;
    private static final double ALL_RED = 2;

    private final Network network;
    private final Random random;
    private final List<Agent> agents = new ArrayList<>();
    private final Map<String, Double> nextSpawn = new HashMap<>();
    private final Map<SignalGroup, SignalState> states = new HashMap<>();
    private final double startOfDay;
    private double time;
    private long nextId = 1;

    public FakeSimulation(Network network, double timeOfDay, long seed) {
        this.network = network;
        this.startOfDay = timeOfDay;
        this.random = new Random(seed);
        for (Route r : network.routes()) {
            nextSpawn.put(r.id(), random.nextDouble() * interval(r));
        }
        updateSignals();
    }

    @Override
    public Network network() {
        return network;
    }

    @Override
    public double time() {
        return time;
    }

    @Override
    public double timeOfDay() {
        return (startOfDay + time) % 86400;
    }

    @Override
    public Collection<? extends AgentView> agents() {
        return agents;
    }

    @Override
    public SignalState signalState(SignalGroup group) {
        return states.getOrDefault(group, SignalState.RED);
    }

    /** Advances by {@code dt} seconds. */
    public void step(double dt) {
        time += dt;
        updateSignals();
        for (Route r : network.routes()) {
            if (time >= nextSpawn.get(r.id())) {
                spawn(r);
                nextSpawn.put(r.id(), time + interval(r) * (0.5 + random.nextDouble()));
            }
        }
        for (Agent a : agents) {
            a.move(dt);
        }
        agents.removeIf(a -> a.s > a.length);
    }

    private double interval(Route r) {
        return switch (r.primaryMode()) {
            case BUS -> 90;
            case BIKE -> 20;
            case FOOT -> 30;
            default -> r.movement() == Movement.THROUGH ? 7 : 16;
        };
    }

    private void spawn(Route r) {
        Mode mode = r.primaryMode();
        AgentType type;
        if (mode == Mode.BUS) {
            type = AgentType.BUS;
        } else if (mode == Mode.BIKE) {
            type = AgentType.BICYCLE;
        } else if (mode == Mode.FOOT) {
            type = AgentType.PEDESTRIAN;
        } else {
            double p = random.nextDouble();
            type = p < 0.86 ? AgentType.CAR : p < 0.95 ? AgentType.VAN : AgentType.TRUCK;
        }
        for (Agent other : agents) {
            if (other.route == r && other.s < type.length() + 6) {
                return; // no room at the start of the route
            }
        }
        agents.add(new Agent(nextId++, type, r, random.nextInt()));
    }

    private void updateSignals() {
        double cycle = 0;
        for (double g : GREEN) {
            cycle += g + AMBER + ALL_RED;
        }
        double t = time % cycle;
        int stage = 0;
        while (t >= GREEN[stage] + AMBER + ALL_RED) {
            t -= GREEN[stage] + AMBER + ALL_RED;
            stage++;
        }
        for (SignalGroup g : network.signalGroups()) {
            states.put(g, SignalState.RED);
        }
        SignalState s = t < GREEN[stage] ? SignalState.GREEN : t < GREEN[stage] + AMBER ? SignalState.AMBER
                : SignalState.RED;
        for (String id : STAGES[stage]) {
            states.put(network.signalGroup(id), s);
        }
    }

    /** One road user. */
    private final class Agent implements AgentView {
        final long id;
        final AgentType type;
        final Route route;
        final Polyline path;
        final double length;
        final double stopAt;
        final int variant;
        final double desired;
        double s;
        double v;
        double a;
        double waiting;

        Agent(long id, AgentType type, Route route, int variant) {
            this.id = id;
            this.type = type;
            this.route = route;
            this.path = route.path();
            this.length = path.length();
            this.stopAt = route.stopLineDistance();
            this.variant = variant;
            this.desired = switch (type) {
                case BICYCLE -> 4.5 + random.nextDouble();
                case PEDESTRIAN -> 1.2 + random.nextDouble() * 0.3;
                case TRUCK, BUS -> 11.5;
                default -> 12.5 + random.nextDouble() * 2;
            };
            this.s = type.length() / 2;
            this.v = desired * 0.8;
        }

        void move(double dt) {
            double target = Math.min(desired, laneSpeed());
            // keep distance to the agent ahead on the same route
            for (Agent other : agents) {
                if (other.route == route && other.s > s) {
                    double gap = other.s - s - (other.type.length() + type.length()) / 2 - 2.0;
                    target = Math.min(target, Math.max(0, gap / 1.4));
                }
            }
            // stop at the stop line unless the light is green or we are already past it
            double toStop = stopAt - s - type.length() / 2 - 0.5;
            SignalState light = signalState(route.group());
            if (toStop > -0.5 && light != SignalState.GREEN && !(light == SignalState.AMBER && toStop < v * 1.2)) {
                target = Math.min(target, Math.sqrt(Math.max(0, 2 * 2.5 * Math.max(0, toStop))));
            }
            double accel = type == AgentType.PEDESTRIAN ? 1.0 : type == AgentType.BICYCLE ? 1.2 : 1.6;
            double newV = v < target ? Math.min(target, v + accel * dt) : Math.max(target, v - 4.0 * dt);
            a = (newV - v) / dt;
            v = newV;
            s += v * dt;
            if (v < 1) {
                waiting += dt;
            }
        }

        private double laneSpeed() {
            double start = 0;
            for (Lane lane : route.lanes()) {
                if (s < start + lane.length()) {
                    return lane.speedLimit();
                }
                start += lane.length();
            }
            return desired;
        }

        @Override
        public long id() {
            return id;
        }

        @Override
        public AgentType type() {
            return type;
        }

        @Override
        public int variant() {
            return variant;
        }

        @Override
        public double x() {
            return path.pointAt(s).x();
        }

        @Override
        public double y() {
            return path.pointAt(s).y();
        }

        @Override
        public double heading() {
            Vec2 d = path.directionAt(s);
            return Math.atan2(d.y(), d.x());
        }

        @Override
        public double speed() {
            return v;
        }

        @Override
        public double acceleration() {
            return a;
        }

        @Override
        public boolean braking() {
            return a < -0.4 || (v < 0.5 && type.isMotorVehicle());
        }

        @Override
        public Indicator indicator() {
            boolean near = s > stopAt - 60 && s < stopAt + 40;
            if (!near) {
                return Indicator.NONE;
            }
            return switch (route.movement()) {
                case LEFT -> Indicator.LEFT;
                case RIGHT -> Indicator.RIGHT;
                default -> Indicator.NONE;
            };
        }

        @Override
        public String routeId() {
            return route.id();
        }

        @Override
        public double waitingTime() {
            return waiting;
        }
    }
}
