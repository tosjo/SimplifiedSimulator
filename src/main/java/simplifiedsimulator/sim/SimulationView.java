package simplifiedsimulator.sim;

import java.util.Collection;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.SignalGroup;

/** Read-only view of the running simulation, for the renderer and user interface. */
public interface SimulationView {

    Network network();

    /** Simulated seconds since the start. */
    double time();

    /** Simulated time of day in seconds after midnight (drives the sun and the demand profile). */
    double timeOfDay();

    /** All road users currently in the simulation. */
    Collection<? extends AgentView> agents();

    SignalState signalState(SignalGroup group);
}
