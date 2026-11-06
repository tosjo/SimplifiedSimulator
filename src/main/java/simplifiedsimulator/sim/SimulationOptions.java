package simplifiedsimulator.sim;

import java.util.Objects;
import simplifiedsimulator.demand.DemandModel;

/**
 * Settings of a simulation run.
 *
 * @param timeStep       length of one simulation step in seconds
 * @param seed           seed of the random numbers; the same seed gives the same run
 * @param startTimeOfDay time of day at the start, in seconds after midnight
 * @param demand         traffic demand
 * @param demandScale    multiplies the demand (1 = as in the demand file)
 */
public record SimulationOptions(double timeStep, long seed, double startTimeOfDay, DemandModel demand,
                                double demandScale) {

    public static final double DEFAULT_TIME_STEP = 0.1;
    /** 07:30, early in the morning peak. */
    public static final double DEFAULT_START = 7.5 * 3600;

    public SimulationOptions {
        Objects.requireNonNull(demand, "demand");
        if (timeStep <= 0 || timeStep > 0.5) {
            throw new IllegalArgumentException("Time step must be between 0 and 0.5 s, got " + timeStep);
        }
        if (demandScale < 0) {
            throw new IllegalArgumentException("Demand scale must not be negative, got " + demandScale);
        }
    }

    /** Default settings with the bundled demand. */
    public static SimulationOptions defaults() {
        return new SimulationOptions(DEFAULT_TIME_STEP, 1, DEFAULT_START, DemandModel.loadDefault(), 1.0);
    }

    public SimulationOptions withSeed(long newSeed) {
        return new SimulationOptions(timeStep, newSeed, startTimeOfDay, demand, demandScale);
    }

    public SimulationOptions withStartTimeOfDay(double seconds) {
        return new SimulationOptions(timeStep, seed, seconds, demand, demandScale);
    }

    public SimulationOptions withDemand(DemandModel newDemand) {
        return new SimulationOptions(timeStep, seed, startTimeOfDay, newDemand, demandScale);
    }

    public SimulationOptions withDemandScale(double scale) {
        return new SimulationOptions(timeStep, seed, startTimeOfDay, demand, scale);
    }

    public SimulationOptions withTimeStep(double dt) {
        return new SimulationOptions(dt, seed, startTimeOfDay, demand, demandScale);
    }
}
