package simplifiedsimulator.stats;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Movement;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.Route;
import simplifiedsimulator.sim.AgentType;

/** Collects trip results, queue lengths and a per-minute time series. */
public final class Statistics {

    private static final double THROUGHPUT_WINDOW = 900;

    private static final class Accumulator {
        final String key;
        final String label;
        final Mode mode;
        int count;
        double delay;
        double stops;
        double waiting;
        int queue;
        int maxQueue;
        final Deque<Double> recentEnds = new ArrayDeque<>();

        Accumulator(String key, String label, Mode mode) {
            this.key = key;
            this.label = label;
            this.mode = mode;
        }

        void add(TripRecord t) {
            count++;
            delay += t.delay();
            stops += t.stops();
            waiting += t.waiting();
            recentEnds.addLast(t.endTime());
        }

        double mean(double sum) {
            return count == 0 ? Double.NaN : sum / count;
        }
    }

    private final Map<String, Accumulator> movements = new LinkedHashMap<>();
    private final Map<String, String> keyOfRoute = new LinkedHashMap<>();
    private final Map<String, Accumulator> byKind = new LinkedHashMap<>();
    private final List<TimeSample> series = new ArrayList<>();
    private final List<TripRecord> minute = new ArrayList<>();
    private int maxQueueAnyLane;

    public Statistics(Network network) {
        for (Route r : network.routes()) {
            String key = keyOf(r);
            keyOfRoute.put(r.id(), key);
            movements.computeIfAbsent(key, k -> new Accumulator(k, labelOf(r), r.primaryMode()));
        }
        for (String kind : List.of("motor", "bus", "bike", "foot")) {
            byKind.put(kind, new Accumulator(kind, kind, Mode.CAR));
        }
    }

    /** Movement key of a route, for example {@code W-THROUGH-CAR}. */
    public static String keyOf(Route r) {
        return r.approach() + "-" + r.movement() + "-" + r.primaryMode();
    }

    private static String labelOf(Route r) {
        String from = switch (r.approach()) {
            case "N" -> "North";
            case "E" -> "East";
            case "S" -> "South";
            default -> "West";
        };
        return switch (r.primaryMode()) {
            case BUS -> from + ", bus lane";
            case BIKE -> from + " crossing, cyclists";
            case FOOT -> from + " crossing, pedestrians";
            case CAR -> from + ", " + (r.movement() == Movement.LEFT ? "left turn"
                    : r.movement() == Movement.RIGHT ? "right turn" : "straight on");
        };
    }

    /** Records a finished trip. */
    public void record(TripRecord trip) {
        movements.get(keyOfRoute.get(trip.routeId())).add(trip);
        byKind.get(kindOf(trip.type())).add(trip);
        minute.add(trip);
    }

    private static String kindOf(AgentType type) {
        return switch (type) {
            case CAR, VAN, TRUCK -> "motor";
            case BUS -> "bus";
            case BICYCLE -> "bike";
            case PEDESTRIAN -> "foot";
        };
    }

    /**
     * Updates the queue lengths.
     *
     * @param queuePerRoute queued road users per route id (on the lane and waiting to enter)
     * @param longestLane   the longest queue on a single lane right now
     */
    public void updateQueues(Map<String, Integer> queuePerRoute, int longestLane) {
        for (Accumulator a : movements.values()) {
            a.queue = 0;
        }
        for (Map.Entry<String, Integer> e : queuePerRoute.entrySet()) {
            movements.get(keyOfRoute.get(e.getKey())).queue += e.getValue();
        }
        for (Accumulator a : movements.values()) {
            a.maxQueue = Math.max(a.maxQueue, a.queue);
        }
        maxQueueAnyLane = Math.max(maxQueueAnyLane, longestLane);
    }

    /** Takes the per-minute sample. */
    public void sample(double time, double timeOfDay, int agents, int entryQueue, Map<String, Integer> queues) {
        double motorDelay = 0;
        int motor = 0;
        double slowDelay = 0;
        int slow = 0;
        for (TripRecord t : minute) {
            if (t.type().isMotorVehicle()) {
                motorDelay += t.delay();
                motor++;
            } else {
                slowDelay += t.delay();
                slow++;
            }
        }
        series.add(new TimeSample(time, timeOfDay, agents, entryQueue, queues,
                motor == 0 ? Double.NaN : motorDelay / motor, slow == 0 ? Double.NaN : slowDelay / slow, motor * 60.0));
        minute.clear();
    }

    /** Statistics per movement at time {@code now}. */
    public List<MovementStats> movements(double now) {
        List<MovementStats> out = new ArrayList<>();
        for (Accumulator a : movements.values()) {
            while (!a.recentEnds.isEmpty() && a.recentEnds.peekFirst() < now - THROUGHPUT_WINDOW) {
                a.recentEnds.removeFirst();
            }
            double window = Math.min(THROUGHPUT_WINDOW, Math.max(1, now));
            out.add(new MovementStats(a.key, a.label, a.mode, a.count, a.mean(a.delay), a.mean(a.stops),
                    a.mean(a.waiting), a.queue, a.maxQueue, a.recentEnds.size() * 3600.0 / window));
        }
        return out;
    }

    public List<TimeSample> timeSeries() {
        return List.copyOf(series);
    }

    /** Builds the overall summary, combining the trip statistics with the simulation's safety counters. */
    public Summary summary(int entryQueue, int redLightRuns, int conflictViolations, int signalConflicts) {
        Accumulator motor = byKind.get("motor");
        return new Summary(motor.count, motor.mean(motor.delay), motor.mean(motor.stops),
                byKind.get("bus").mean(byKind.get("bus").delay), byKind.get("bike").mean(byKind.get("bike").delay),
                byKind.get("foot").mean(byKind.get("foot").delay), maxQueueAnyLane, entryQueue, redLightRuns,
                conflictViolations, signalConflicts);
    }

    /** Writes the per-movement statistics as CSV. */
    public void writeMovementsCsv(Writer out, double now) throws IOException {
        out.write("movement,label,completed,mean_delay_s,mean_stops,mean_waiting_s,queue,max_queue,per_hour\n");
        for (MovementStats m : movements(now)) {
            out.write(String.format(Locale.ROOT, "%s,\"%s\",%d,%.1f,%.2f,%.1f,%d,%d,%.0f%n", m.key(), m.label(),
                    m.completed(), m.meanDelay(), m.meanStops(), m.meanWaiting(), m.queue(), m.maxQueue(),
                    m.throughput()));
        }
    }

    /** Writes the per-minute time series as CSV. */
    public void writeTimeSeriesCsv(Writer out) throws IOException {
        out.write("time_s,time_of_day,agents,entry_queue,queue_N,queue_E,queue_S,queue_W,"
                + "motor_delay_s,slow_delay_s,motor_per_hour\n");
        for (TimeSample s : series) {
            int tod = (int) s.timeOfDay();
            out.write(String.format(Locale.ROOT, "%.0f,%02d:%02d,%d,%d,%d,%d,%d,%d,%.1f,%.1f,%.0f%n", s.time(),
                    tod / 3600 % 24, tod / 60 % 60, s.agents(), s.entryQueue(), s.queues().getOrDefault("N", 0),
                    s.queues().getOrDefault("E", 0), s.queues().getOrDefault("S", 0),
                    s.queues().getOrDefault("W", 0), s.motorDelay(), s.slowDelay(), s.motorPerHour()));
        }
    }
}
