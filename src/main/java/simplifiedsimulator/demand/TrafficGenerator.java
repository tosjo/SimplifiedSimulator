package simplifiedsimulator.demand;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.random.RandomGenerator;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.Route;
import simplifiedsimulator.sim.AgentType;

/**
 * Decides when new road users arrive. Cars, cyclists and pedestrians arrive at random
 * (a Poisson process following the demand); buses run to a timetable with some delay.
 */
public final class TrafficGenerator {

    private final Network network;
    private final DemandModel demand;
    private final RandomGenerator rng;
    private final Map<Route, Double> nextBus = new HashMap<>();

    public TrafficGenerator(Network network, DemandModel demand, RandomGenerator rng) {
        this.network = network;
        this.demand = demand;
        this.rng = rng;
    }

    /**
     * Generates the arrivals during one time step.
     *
     * @param timeOfDay seconds after midnight at the start of the step
     * @param dt        length of the step in seconds
     * @param scale     multiplies the demand (1 = as in the demand file)
     * @param out       receives each new road user's route and type
     */
    public void generate(double timeOfDay, double dt, double scale, BiConsumer<Route, AgentType> out) {
        for (Route route : network.routes()) {
            double rate = demand.rate(route, timeOfDay) * scale;
            if (rate <= 0) {
                continue;
            }
            if (route.primaryMode() == Mode.BUS) {
                scheduledBus(route, rate, timeOfDay, out);
                continue;
            }
            int n = poisson(rate * dt);
            for (int i = 0; i < n; i++) {
                out.accept(route, typeFor(route));
            }
        }
    }

    private void scheduledBus(Route route, double rate, double now, BiConsumer<Route, AgentType> out) {
        double headway = 1 / rate;
        Double next = nextBus.get(route);
        if (next == null) {
            // first bus somewhere within one headway
            nextBus.put(route, now + rng.nextDouble() * headway);
            return;
        }
        if (now >= next) {
            out.accept(route, AgentType.BUS);
            // buses run to a timetable but are a minute or two early or late
            double delay = Math.max(-0.4, Math.min(0.4, 0.15 * rng.nextGaussian()));
            nextBus.put(route, now + headway * (1 + delay));
        }
    }

    /** Picks the kind of vehicle: mostly cars, some vans and trucks, fewer trucks off the main road. */
    private AgentType typeFor(Route route) {
        return switch (route.primaryMode()) {
            case BUS -> AgentType.BUS;
            case BIKE -> AgentType.BICYCLE;
            case FOOT -> AgentType.PEDESTRIAN;
            case CAR -> {
                boolean mainRoad = route.approach().equals("W") || route.approach().equals("E");
                double r = rng.nextDouble();
                double truck = mainRoad ? 0.05 : 0.02;
                yield r < truck ? AgentType.TRUCK : r < truck + 0.09 ? AgentType.VAN : AgentType.CAR;
            }
        };
    }

    private int poisson(double mean) {
        double l = Math.exp(-mean);
        int k = 0;
        double p = rng.nextDouble();
        while (p > l) {
            k++;
            p *= rng.nextDouble();
        }
        return k;
    }
}
