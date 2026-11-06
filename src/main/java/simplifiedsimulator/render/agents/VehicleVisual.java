package simplifiedsimulator.render.agents;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import simplifiedsimulator.sim.AgentView;
import simplifiedsimulator.sim.Indicator;

/** A car, van, truck or bus: body, spinning wheels and working lights. */
final class VehicleVisual extends AgentVisual {

    /** Shared light materials. */
    record LightMaterials(Material headOff, Material headOn, Material tailOff, Material tailNight, Material brake,
                          Material indicatorOff, Material indicatorOn) {
    }

    private final List<Node> wheels = new ArrayList<>();
    private final float wheelRadius;
    private final Geometry head;
    private final Geometry tail;
    private final Geometry left;
    private final Geometry right;
    private final Geometry beams;
    private final LightMaterials lights;
    private float wheelAngle;
    private boolean detailed = true;

    VehicleVisual(long id, VehicleModels.Model model, Material body, Material wheel, LightMaterials lights,
                  Mesh beamMesh, Material beamMaterial, Geometry extra) {
        super(id);
        this.lights = lights;
        this.wheelRadius = model.wheelRadius();
        Geometry bodyGeom = new Geometry("body", model.body());
        bodyGeom.setMaterial(body);
        bodyGeom.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        node.attachChild(bodyGeom);
        for (Vector3f at : model.wheels()) {
            Node pivot = new Node("wheel");
            pivot.setLocalTranslation(at);
            Geometry g = new Geometry("tyre", model.wheel());
            g.setMaterial(wheel);
            g.setShadowMode(RenderQueue.ShadowMode.Cast);
            pivot.attachChild(g);
            node.attachChild(pivot);
            wheels.add(pivot);
        }
        head = lamp("headlights", model.headlights(), lights.headOff());
        tail = lamp("taillights", model.taillights(), lights.tailOff());
        left = lamp("indicator left", model.indicatorsLeft(), lights.indicatorOff());
        right = lamp("indicator right", model.indicatorsRight(), lights.indicatorOff());
        beams = new Geometry("beams", beamMesh);
        beams.setMaterial(beamMaterial);
        beams.setQueueBucket(RenderQueue.Bucket.Transparent);
        beams.setShadowMode(RenderQueue.ShadowMode.Off);
        beams.setLocalTranslation(model.length() / 2, 0, 0);
        beams.setCullHint(Spatial.CullHint.Always);
        node.attachChild(beams);
        if (extra != null) {
            node.attachChild(extra);
        }
    }

    private Geometry lamp(String name, Mesh mesh, Material material) {
        Geometry g = new Geometry(name, mesh);
        g.setMaterial(material);
        g.setShadowMode(RenderQueue.ShadowMode.Off);
        node.attachChild(g);
        return g;
    }

    @Override
    public void setDetailed(boolean detailed) {
        if (detailed == this.detailed) {
            return;
        }
        this.detailed = detailed;
        Spatial.CullHint hint = detailed ? Spatial.CullHint.Inherit : Spatial.CullHint.Always;
        for (Node w : wheels) {
            w.setCullHint(hint);
        }
        left.setCullHint(hint);
        right.setCullHint(hint);
    }

    @Override
    public void animate(AgentView agent, float travelled, double time, boolean lightsOn) {
        wheelAngle -= travelled / wheelRadius;
        Quaternion spin = new Quaternion().fromAngleAxis(wheelAngle, Vector3f.UNIT_Z);
        for (Node w : wheels) {
            w.setLocalRotation(spin);
        }
        set(head, lightsOn ? lights.headOn() : lights.headOff());
        set(tail, agent.braking() ? lights.brake() : lightsOn ? lights.tailNight() : lights.tailOff());
        boolean blink = (time % 0.75) < 0.4;
        Indicator ind = agent.indicator();
        set(left, ind == Indicator.LEFT && blink ? lights.indicatorOn() : lights.indicatorOff());
        set(right, ind == Indicator.RIGHT && blink ? lights.indicatorOn() : lights.indicatorOff());
        beams.setCullHint(lightsOn ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }

    private static void set(Geometry g, Material m) {
        if (g.getMaterial() != m) {
            g.setMaterial(m);
        }
    }

    /** Colour of the headlight pool on the road. */
    static ColorRGBA beamColor() {
        return new ColorRGBA(0.30f, 0.29f, 0.25f, 1f);
    }
}
