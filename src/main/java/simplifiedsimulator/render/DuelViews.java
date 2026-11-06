package simplifiedsimulator.render;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;
import java.util.Objects;

/**
 * Keeps the second scene of a duel up to date and its camera locked to the first one.
 *
 * <p>jME only updates the application's own root node, so the scene of the second view port
 * is updated here. The camera is copied in {@link #render}, which runs after every update
 * (including the camera rig's) and before anything is drawn, so both halves always show the
 * same viewpoint.
 */
public final class DuelViews extends BaseAppState {

    private final Node secondRoot;
    private final Camera leader;
    private final Camera follower;

    public DuelViews(Node secondRoot, Camera leader, Camera follower) {
        this.secondRoot = Objects.requireNonNull(secondRoot);
        this.leader = Objects.requireNonNull(leader);
        this.follower = Objects.requireNonNull(follower);
    }

    @Override
    protected void initialize(Application app) {
        // nothing to set up: the view port and scene are created by the application
    }

    @Override
    protected void cleanup(Application app) {
        // nothing to release
    }

    @Override
    protected void onEnable() {
        // always active
    }

    @Override
    protected void onDisable() {
        // always active
    }

    @Override
    public void update(float tpf) {
        secondRoot.updateLogicalState(tpf);
    }

    @Override
    public void render(RenderManager rm) {
        secondRoot.updateGeometricState();
        follower.setFrustum(leader.getFrustumNear(), leader.getFrustumFar(), leader.getFrustumLeft(),
                leader.getFrustumRight(), leader.getFrustumTop(), leader.getFrustumBottom());
        follower.setLocation(leader.getLocation());
        follower.setRotation(leader.getRotation());
    }
}
