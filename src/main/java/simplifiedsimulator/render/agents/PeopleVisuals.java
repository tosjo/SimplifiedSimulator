package simplifiedsimulator.render.agents;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import simplifiedsimulator.render.mesh.MeshBuilder;
import simplifiedsimulator.sim.AgentView;

/**
 * Cyclists on Dutch city bikes and pedestrians. Bodies are simple, but limbs move: legs
 * pedal or walk in step with the distance travelled, and arms swing while walking.
 */
final class PeopleVisuals {

    /** Proportions and colours of one person. */
    record Person(float height, ColorRGBA top, ColorRGBA trousers, ColorRGBA skin, ColorRGBA hair) {

        static Person of(int variant) {
            float h = 1.62f + 0.30f * (float) Palette.unit(variant, 60);
            return new Person(h, Palette.pick(Palette.CLOTHING, variant, 61), Palette.pick(Palette.TROUSERS, variant, 62),
                    Palette.pick(Palette.SKIN, variant, 63), Palette.pick(Palette.HAIR, variant, 64));
        }
    }

    private PeopleVisuals() {
    }

    /** A pedestrian with swinging arms and legs. */
    static final class Pedestrian extends AgentVisual {
        private final Node leftLeg = new Node("left leg");
        private final Node rightLeg = new Node("right leg");
        private final Node leftArm = new Node("left arm");
        private final Node rightArm = new Node("right arm");
        private final float stride;
        private float phase;

        Pedestrian(long id, Person p, Material material) {
            super(id);
            float s = p.height() / 1.75f;
            float hip = 0.92f * s;
            float shoulder = 1.42f * s;
            stride = 1.4f * s;

            MeshBuilder torso = new MeshBuilder();
            torso.color(p.top());
            torso.boxBetween(new Vector3f(-0.12f * s, hip - 0.05f, -0.19f * s), new Vector3f(0.12f * s, shoulder, 0.19f * s));
            torso.color(p.skin());
            torso.cylinder(new Vector3f(0, shoulder, 0), 0.05f * s, 0.08f * s, 6, false);
            torso.sphere(new Vector3f(0.01f, shoulder + 0.19f * s, 0), new Vector3f(0.10f, 0.12f, 0.09f).multLocal(s), 1,
                    null);
            torso.color(p.hair());
            torso.sphere(new Vector3f(-0.015f, shoulder + 0.23f * s, 0), new Vector3f(0.105f, 0.09f, 0.095f).multLocal(s),
                    1, null);
            node.attachChild(geometry("torso", torso.build(), material));

            Mesh leg = limb(0.075f * s, hip, p.trousers(), new ColorRGBA(0.08f, 0.08f, 0.08f, 1f), 0.08f * s);
            Mesh arm = limb(0.05f * s, shoulder - hip + 0.06f, p.top(), p.skin(), 0.09f * s);
            attachLimb(leftLeg, leg, material, new Vector3f(0, hip, -0.09f * s));
            attachLimb(rightLeg, leg, material, new Vector3f(0, hip, 0.09f * s));
            attachLimb(leftArm, arm, material, new Vector3f(0, shoulder - 0.03f, -0.23f * s));
            attachLimb(rightArm, arm, material, new Vector3f(0, shoulder - 0.03f, 0.23f * s));
        }

        private void attachLimb(Node pivot, Mesh mesh, Material material, Vector3f at) {
            pivot.setLocalTranslation(at);
            pivot.attachChild(geometry("limb", mesh, material));
            node.attachChild(pivot);
        }

        @Override
        public void setDetailed(boolean detailed) {
            Spatial.CullHint hint = detailed ? Spatial.CullHint.Inherit : Spatial.CullHint.Always;
            leftLeg.setCullHint(hint);
            rightLeg.setCullHint(hint);
            leftArm.setCullHint(hint);
            rightArm.setCullHint(hint);
        }

        @Override
        public void animate(AgentView agent, float travelled, double time, boolean lightsOn) {
            phase += travelled / stride * FastMath.TWO_PI;
            float amount = Math.min(1f, (float) agent.speed() / 1.0f);
            float swing = FastMath.sin(phase) * 0.45f * amount;
            leftLeg.setLocalRotation(new Quaternion().fromAngleAxis(swing, Vector3f.UNIT_Z));
            rightLeg.setLocalRotation(new Quaternion().fromAngleAxis(-swing, Vector3f.UNIT_Z));
            leftArm.setLocalRotation(new Quaternion().fromAngleAxis(-swing * 0.8f, Vector3f.UNIT_Z));
            rightArm.setLocalRotation(new Quaternion().fromAngleAxis(swing * 0.8f, Vector3f.UNIT_Z));
        }
    }

    /** A cyclist pedalling a Dutch city bike with a rear carrier. */
    static final class Cyclist extends AgentVisual {
        private static final float CRANK = 0.17f;
        private static final Vector3f BOTTOM_BRACKET = new Vector3f(-0.05f, 0.30f, 0);
        private static final float WHEEL = 0.34f;

        private final Node frontWheel = new Node("front wheel");
        private final Node rearWheel = new Node("rear wheel");
        private final Node[] thighs = {new Node("thigh"), new Node("thigh")};
        private final Node[] shins = {new Node("shin"), new Node("shin")};
        private final Vector3f hip;
        private final float thigh;
        private final float shin;
        private float wheelAngle;
        private float crankAngle;

        Cyclist(long id, Person p, ColorRGBA frame, Material material) {
            super(id);
            float s = p.height() / 1.75f;
            hip = new Vector3f(-0.22f, 0.98f + 0.05f * (s - 1), 0);
            thigh = 0.44f * s;
            shin = 0.46f * s;

            MeshBuilder bike = new MeshBuilder();
            bike.color(frame);
            tube(bike, new Vector3f(-0.58f, WHEEL, 0), BOTTOM_BRACKET, 0.025f);           // chain stay
            tube(bike, new Vector3f(-0.58f, WHEEL, 0), new Vector3f(-0.22f, 0.85f, 0), 0.02f); // seat stay
            tube(bike, BOTTOM_BRACKET, new Vector3f(-0.24f, 0.90f, 0), 0.028f);           // seat tube
            tube(bike, BOTTOM_BRACKET, new Vector3f(0.36f, 0.86f, 0), 0.032f);            // down tube
            tube(bike, new Vector3f(-0.22f, 0.72f, 0), new Vector3f(0.36f, 0.80f, 0), 0.028f); // low top tube
            tube(bike, new Vector3f(0.36f, 0.95f, 0), new Vector3f(0.58f, WHEEL, 0), 0.025f);  // fork
            bike.color(new ColorRGBA(0.12f, 0.12f, 0.12f, 1f));
            tube(bike, new Vector3f(0.36f, 0.95f, 0), new Vector3f(0.30f, 1.12f, 0), 0.02f);   // stem
            tube(bike, new Vector3f(0.22f, 1.12f, -0.30f), new Vector3f(0.22f, 1.12f, 0.30f), 0.016f); // handlebar
            tube(bike, new Vector3f(0.30f, 1.12f, -0.27f), new Vector3f(0.22f, 1.12f, -0.30f), 0.016f);
            tube(bike, new Vector3f(0.30f, 1.12f, 0.27f), new Vector3f(0.22f, 1.12f, 0.30f), 0.016f);
            bike.boxBetween(new Vector3f(-0.36f, 0.92f, -0.08f), new Vector3f(-0.12f, 0.98f, 0.08f)); // saddle
            bike.boxBetween(new Vector3f(-0.82f, 0.74f, -0.09f), new Vector3f(-0.40f, 0.77f, 0.09f)); // carrier
            bike.color(new ColorRGBA(0.55f, 0.55f, 0.56f, 1f));
            bike.boxBetween(new Vector3f(-0.75f, 0.42f, -0.05f), new Vector3f(-0.20f, 0.47f, 0.05f)); // chain guard

            // rider: torso upright, as on a Dutch "omafiets"
            Vector3f shoulder = hip.add(0.12f * s, 0.52f * s, 0);
            bike.color(p.top());
            tube(bike, hip.add(0, 0.05f, 0), shoulder, 0.17f * s);
            bike.color(p.skin());
            bike.sphere(shoulder.add(0.03f, 0.21f * s, 0), new Vector3f(0.10f, 0.12f, 0.09f).multLocal(s), 1, null);
            bike.color(p.hair());
            bike.sphere(shoulder.add(0.01f, 0.25f * s, 0), new Vector3f(0.105f, 0.09f, 0.095f).multLocal(s), 1, null);
            bike.color(p.top());
            for (float side : new float[] {-1, 1}) {
                Vector3f from = shoulder.add(0, -0.03f, side * 0.2f * s);
                Vector3f to = new Vector3f(0.22f, 1.14f, side * 0.26f);
                tube(bike, from, to, 0.045f * s);
            }
            node.attachChild(geometry("bike and rider", bike.build(), material));

            MeshBuilder wheel = new MeshBuilder();
            wheel.color(new ColorRGBA(0.06f, 0.06f, 0.06f, 1f));
            wheel.torus(Vector3f.ZERO, WHEEL - 0.02f, 0.022f, 24, 6);
            wheel.color(new ColorRGBA(0.6f, 0.6f, 0.62f, 1f));
            for (int i = 0; i < 6; i++) {
                float a = FastMath.PI * i / 6;
                tube(wheel, new Vector3f(FastMath.cos(a) * (WHEEL - 0.03f), FastMath.sin(a) * (WHEEL - 0.03f), 0),
                        new Vector3f(-FastMath.cos(a) * (WHEEL - 0.03f), -FastMath.sin(a) * (WHEEL - 0.03f), 0), 0.004f);
            }
            Mesh wheelMesh = wheel.build();
            frontWheel.setLocalTranslation(0.58f, WHEEL, 0);
            rearWheel.setLocalTranslation(-0.58f, WHEEL, 0);
            frontWheel.attachChild(geometry("wheel", wheelMesh, material));
            rearWheel.attachChild(geometry("wheel", wheelMesh, material));
            node.attachChild(frontWheel);
            node.attachChild(rearWheel);

            Mesh thighMesh = limb(0.075f * s, thigh, p.trousers(), p.trousers(), 0f);
            Mesh shinMesh = limb(0.06f * s, shin, p.trousers(), new ColorRGBA(0.08f, 0.08f, 0.08f, 1f), 0.07f * s);
            for (int i = 0; i < 2; i++) {
                float z = (i == 0 ? -1 : 1) * 0.11f;
                thighs[i].setLocalTranslation(hip.x, hip.y, z);
                thighs[i].attachChild(geometry("thigh", thighMesh, material));
                shins[i].attachChild(geometry("shin", shinMesh, material));
                node.attachChild(thighs[i]);
                node.attachChild(shins[i]);
            }
        }

        @Override
        public void setDetailed(boolean detailed) {
            Spatial.CullHint hint = detailed ? Spatial.CullHint.Inherit : Spatial.CullHint.Always;
            for (int i = 0; i < 2; i++) {
                thighs[i].setCullHint(hint);
                shins[i].setCullHint(hint);
            }
        }

        @Override
        public void animate(AgentView agent, float travelled, double time, boolean lightsOn) {
            wheelAngle -= travelled / WHEEL;
            Quaternion spin = new Quaternion().fromAngleAxis(wheelAngle, Vector3f.UNIT_Z);
            frontWheel.setLocalRotation(spin);
            rearWheel.setLocalRotation(spin);
            // a city bike has a gear ratio of about 1:2.6
            crankAngle -= travelled / WHEEL / 2.6f;
            for (int i = 0; i < 2; i++) {
                float a = crankAngle + i * FastMath.PI;
                Vector3f pedal = BOTTOM_BRACKET.add(FastMath.cos(a) * CRANK, FastMath.sin(a) * CRANK, 0);
                legTo(i, pedal);
            }
        }

        /** Two-bone inverse kinematics from the hip to the pedal, knee pointing forward. */
        private void legTo(int i, Vector3f pedal) {
            float z = thighs[i].getLocalTranslation().z;
            Vector3f h = new Vector3f(hip.x, hip.y, 0);
            Vector3f d = pedal.subtract(h);
            float dist = Math.min(d.length(), thigh + shin - 0.001f);
            float cosA = (thigh * thigh + dist * dist - shin * shin) / (2 * thigh * dist);
            float a = FastMath.acos(Math.max(-1, Math.min(1, cosA)));
            float base = FastMath.atan2(d.y, d.x);
            float thighAngle = base + a;
            Vector3f knee = h.add(FastMath.cos(thighAngle) * thigh, FastMath.sin(thighAngle) * thigh, 0);
            // limb meshes hang down along -Y: rotate so that -Y points along the bone
            thighs[i].setLocalRotation(new Quaternion().fromAngleAxis(thighAngle + FastMath.HALF_PI, Vector3f.UNIT_Z));
            Vector3f k2p = pedal.subtract(knee);
            float shinAngle = FastMath.atan2(k2p.y, k2p.x);
            shins[i].setLocalTranslation(knee.x, knee.y, z);
            shins[i].setLocalRotation(new Quaternion().fromAngleAxis(shinAngle + FastMath.HALF_PI, Vector3f.UNIT_Z));
        }
    }

    /** A limb hanging down along -Y from its pivot, with a different colour at the end (hand, shoe). */
    static Mesh limb(float radius, float length, ColorRGBA color, ColorRGBA end, float endLength) {
        MeshBuilder mb = new MeshBuilder();
        mb.color(color);
        mb.boxBetween(new Vector3f(-radius, -length + endLength, -radius), new Vector3f(radius, 0, radius));
        if (endLength > 0) {
            mb.color(end);
            mb.boxBetween(new Vector3f(-radius, -length, -radius), new Vector3f(radius + endLength * 0.6f,
                    -length + endLength, radius));
        }
        return mb.build();
    }

    /** A square tube between two points. */
    static void tube(MeshBuilder mb, Vector3f from, Vector3f to, float thickness) {
        Vector3f dir = to.subtract(from);
        float len = dir.length();
        if (len < 1e-4f) {
            return;
        }
        Quaternion q = new Quaternion();
        q.lookAt(dir.normalize(), Math.abs(dir.normalize().y) > 0.95f ? Vector3f.UNIT_X : Vector3f.UNIT_Y);
        com.jme3.math.Matrix4f m = new com.jme3.math.Matrix4f();
        m.setRotationQuaternion(q);
        m.setTranslation(from);
        mb.push(m);
        mb.boxBetween(new Vector3f(-thickness / 2, -thickness / 2, 0), new Vector3f(thickness / 2, thickness / 2, len));
        mb.pop();
    }

    static Geometry geometry(String name, Mesh mesh, Material material) {
        Geometry g = new Geometry(name, mesh);
        g.setMaterial(material);
        g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        return g;
    }
}
