package simplifiedsimulator.render.agents;

import com.jme3.scene.Node;
import simplifiedsimulator.render.Coords;
import simplifiedsimulator.sim.AgentView;

/** The 3D model of one road user, positioned and animated from its {@link AgentView}. */
public abstract class AgentVisual {

    /** User-data key on the model node holding the agent id (used for picking). */
    public static final String AGENT_ID = "agentId";

    protected final Node node = new Node("agent");

    protected AgentVisual(long id) {
        node.setUserData(AGENT_ID, id);
    }

    public Node node() {
        return node;
    }

    /** Places the model; the model's forward direction is +X. */
    public void place(Pose pose) {
        node.setLocalTranslation(Coords.world(pose.x(), pose.y(), 0));
        node.setLocalRotation(Coords.heading(pose.heading()));
    }

    /**
     * Updates moving parts and lights.
     *
     * @param agent      the agent's latest state
     * @param travelled  metres moved since the previous frame (spins wheels, moves legs)
     * @param time       animation clock in seconds (blinks indicators)
     * @param lightsOn   true at dusk and at night
     */
    public abstract void animate(AgentView agent, float travelled, double time, boolean lightsOn);
}
