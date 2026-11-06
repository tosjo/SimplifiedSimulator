package simplifiedsimulator.render;

import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import java.util.Objects;

/**
 * Where a {@link CrossingRenderer} draws.
 *
 * @param parent    node the scene is attached to; it must be attached to {@code viewPort}
 * @param viewPort  view port that gets the post-processing filters, and whose camera is used
 * @param cameraRig whether the renderer installs the mouse and keyboard camera control
 */
public record RenderTarget(Node parent, ViewPort viewPort, boolean cameraRig) {

    public RenderTarget {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(viewPort, "viewPort");
    }
}
