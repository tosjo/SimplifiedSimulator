package simplifiedsimulator.render.world;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import simplifiedsimulator.network.Lane;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.SignalGroup;
import simplifiedsimulator.network.SignalHead;
import simplifiedsimulator.network.Vec2;
import simplifiedsimulator.render.Coords;
import simplifiedsimulator.render.Materials;
import simplifiedsimulator.render.mesh.MeshBuilder;
import simplifiedsimulator.sim.SignalState;

/**
 * The traffic lights of the crossing, built at every {@link SignalHead} of the network.
 *
 * <p>Heads on the kerb or an island stand on their own pole. When a head would end up on
 * the carriageway (lanes further from the kerb), it hangs above its lane from a mast arm
 * standing at the kerb, as on wide Dutch approaches.
 *
 * <p>Heads follow Dutch practice: black housings with a black contrast screen and white
 * border; cars get three 200 mm lenses, cyclists three smaller lenses with a bicycle,
 * pedestrians a red standing and a green walking figure, and buses the white bar signals
 * of public transport.
 */
public final class TrafficSignals {

    private static final ColorRGBA RED = new ColorRGBA(1.0f, 0.10f, 0.05f, 1f);
    private static final ColorRGBA AMBER = new ColorRGBA(1.0f, 0.55f, 0.02f, 1f);
    private static final ColorRGBA GREEN = new ColorRGBA(0.15f, 1.0f, 0.55f, 1f);
    private static final ColorRGBA BUS_WHITE = new ColorRGBA(1.0f, 0.97f, 0.88f, 1f);
    private static final float OVERHEAD_HEIGHT = 5.4f;

    /** One lens of one head. */
    private record Lamp(Geometry geometry, SignalState shows, Material lit, Material off) {
    }

    private final Materials materials;
    private final SignalSymbols symbols = new SignalSymbols();
    private final Node node = new Node("traffic signals");
    private final Map<SignalGroup, List<Lamp>> lamps = new IdentityHashMap<>();
    private final Map<SignalGroup, Boolean> pedestrian = new HashMap<>();

    public TrafficSignals(Materials materials, Network network) {
        this.materials = materials;
        MeshBuilder hardware = new MeshBuilder();
        List<Lane> roadLanes = new ArrayList<>();
        for (Lane lane : network.lanes()) {
            if (lane.mode() == Mode.CAR || lane.mode() == Mode.BUS) {
                roadLanes.add(lane);
            }
        }
        for (SignalGroup group : network.signalGroups()) {
            pedestrian.put(group, group.mode() == Mode.FOOT);
            for (SignalHead head : group.heads()) {
                buildHead(hardware, group, head, roadLanes);
            }
        }
        Geometry g = new Geometry("signal hardware", hardware.build());
        g.setMaterial(materials.vertexColored("signal-hardware", 0.25f, 24f));
        g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        node.attachChild(g);
    }

    public Node node() {
        return node;
    }

    /**
     * Shows the current state of every group.
     *
     * @param time seconds, used to flash pedestrian green during clearance
     */
    public void update(java.util.function.Function<SignalGroup, SignalState> states, double time) {
        boolean flashOn = (time % 1.0) < 0.5;
        lamps.forEach((group, list) -> {
            SignalState state = states.apply(group);
            for (Lamp lamp : list) {
                boolean on = lamp.shows == state;
                if (pedestrian.get(group) && state == SignalState.AMBER) {
                    // Dutch pedestrian lights have no amber: show flashing green instead
                    on = lamp.shows == SignalState.GREEN && flashOn;
                }
                Material wanted = on ? lamp.lit : lamp.off;
                if (lamp.geometry.getMaterial() != wanted) {
                    lamp.geometry.setMaterial(wanted);
                }
            }
        });
    }

    private void buildHead(MeshBuilder hardware, SignalGroup group, SignalHead head, List<Lane> roadLanes) {
        Lane lane = head.lane();
        Vec2 pole = head.position();
        boolean overhead = lane != null && (group.mode() == Mode.CAR || group.mode() == Mode.BUS)
                && onCarriageway(pole, roadLanes, lane);
        Quaternion facing = Coords.bearing(head.facing());
        Vector3f headPos;
        if (overhead) {
            Vec2 end = lane.path().end();
            Vec2 right = lane.path().directionAt(lane.length()).rightNormal();
            Vec2 kerb = end;
            for (double d = lane.width() / 2 + 0.6; d < 30; d += 0.25) {
                kerb = end.plus(right.times(d));
                if (!onCarriageway(kerb, roadLanes, null)) {
                    kerb = end.plus(right.times(d + 0.6));
                    break;
                }
            }
            Vec2 forward = lane.path().directionAt(lane.length());
            kerb = kerb.plus(forward.times(0.8));
            Vec2 above = end.plus(forward.times(0.8));
            mast(hardware, kerb, above);
            headPos = Coords.world(above, OVERHEAD_HEIGHT - 0.1);
        } else {
            float h = switch (group.mode()) {
                case BIKE -> 1.55f;
                case FOOT -> 2.05f;
                default -> 2.4f;
            };
            pole(hardware, pole, h + headHeight(group.mode()) + 0.15f);
            headPos = Coords.world(pole, h);
        }

        Matrix4f frame = new Matrix4f();
        frame.setRotationQuaternion(facing);
        frame.setTranslation(headPos);
        if (!overhead) {
            // the head sits in front of the pole
            Vector3f offset = facing.mult(new Vector3f(0.12f, 0, 0));
            frame.setTranslation(headPos.add(offset));
        }
        hardware.push(frame);
        List<Lamp> list = lamps.computeIfAbsent(group, g -> new ArrayList<>());
        switch (group.mode()) {
            case FOOT -> housing(hardware, list, frame, 0.30f,
                    new SignalState[] {SignalState.RED, SignalState.GREEN},
                    new SignalSymbols.Symbol[] {SignalSymbols.Symbol.STANDING, SignalSymbols.Symbol.WALKING},
                    new ColorRGBA[] {RED, GREEN}, overhead);
            case BIKE -> housing(hardware, list, frame, 0.15f,
                    new SignalState[] {SignalState.RED, SignalState.AMBER, SignalState.GREEN},
                    new SignalSymbols.Symbol[] {SignalSymbols.Symbol.BICYCLE, SignalSymbols.Symbol.BICYCLE,
                        SignalSymbols.Symbol.BICYCLE},
                    new ColorRGBA[] {RED, AMBER, GREEN}, overhead);
            case BUS -> housing(hardware, list, frame, 0.22f,
                    new SignalState[] {SignalState.RED, SignalState.AMBER, SignalState.GREEN},
                    new SignalSymbols.Symbol[] {SignalSymbols.Symbol.BAR_STOP, SignalSymbols.Symbol.DOT,
                        SignalSymbols.Symbol.BAR_GO},
                    new ColorRGBA[] {BUS_WHITE, BUS_WHITE, BUS_WHITE}, overhead);
            default -> housing(hardware, list, frame, 0.21f,
                    new SignalState[] {SignalState.RED, SignalState.AMBER, SignalState.GREEN},
                    new SignalSymbols.Symbol[] {SignalSymbols.Symbol.DISC, SignalSymbols.Symbol.DISC,
                        SignalSymbols.Symbol.DISC},
                    new ColorRGBA[] {RED, AMBER, GREEN}, overhead);
        }
        hardware.pop();
    }

    private static float headHeight(Mode mode) {
        return switch (mode) {
            case FOOT -> 0.72f;
            case BIKE -> 0.58f;
            default -> 0.92f;
        };
    }

    /**
     * Builds a housing with its lenses, top lens first, around the origin of the current
     * frame (lenses face +X). The origin is the bottom of the housing, or its top when
     * the head hangs from a mast arm.
     */
    private void housing(MeshBuilder mb, List<Lamp> list, Matrix4f frame, float lens, SignalState[] states,
                         SignalSymbols.Symbol[] symbols, ColorRGBA[] colors, boolean hanging) {
        int n = states.length;
        float pitch = lens * 1.3f;
        float height = pitch * n + lens * 0.3f;
        float width = lens * 1.45f;
        float bottom = hanging ? -height : 0;
        ColorRGBA black = new ColorRGBA(0.035f, 0.035f, 0.04f, 1f);
        mb.color(black);
        mb.boxBetween(new Vector3f(-0.22f, bottom, -width / 2), new Vector3f(0f, bottom + height, width / 2));
        // contrast screen with a white border
        float sw = width + lens * 1.3f;
        float sh = height + lens * 0.9f;
        mb.color(ColorRGBA.White.mult(0.92f));
        mb.boxBetween(new Vector3f(-0.25f, bottom - (sh - height) / 2, -sw / 2),
                new Vector3f(-0.23f, bottom + height + (sh - height) / 2, sw / 2));
        mb.color(black);
        float border = lens * 0.18f;
        mb.boxBetween(new Vector3f(-0.235f, bottom - (sh - height) / 2 + border, -sw / 2 + border),
                new Vector3f(-0.225f, bottom + height + (sh - height) / 2 - border, sw / 2 - border));
        if (hanging) {
            mb.cylinder(new Vector3f(-0.11f, 0, 0), 0.04f, 0.1f, 6, true);
        }
        for (int i = 0; i < n; i++) {
            float y = bottom + height - lens * 0.15f - pitch * (i + 0.5f);
            // visor above each lens
            mb.color(black);
            mb.boxBetween(new Vector3f(0f, y + lens * 0.48f, -lens * 0.55f), new Vector3f(lens * 0.9f, y + lens * 0.56f,
                    lens * 0.55f));
            mb.color(new ColorRGBA(0.01f, 0.01f, 0.012f, 1f));
            mb.boxBetween(new Vector3f(0f, y - lens * 0.5f, -lens * 0.5f), new Vector3f(0.01f, y + lens * 0.5f, lens * 0.5f));

            Geometry lensGeom = new Geometry("lens", lensMesh(lens, symbols[i] == SignalSymbols.Symbol.DISC));
            Matrix4f m = frame.mult(translation(new Vector3f(0.015f, y, 0)));
            lensGeom.setLocalTranslation(m.toTranslationVector());
            lensGeom.setLocalRotation(m.toRotationQuat());
            Material lit = lensMaterial(symbols[i], colors[i], true);
            Material off = lensMaterial(symbols[i], colors[i], false);
            lensGeom.setMaterial(off);
            lensGeom.setShadowMode(RenderQueue.ShadowMode.Off);
            node.attachChild(lensGeom);
            list.add(new Lamp(lensGeom, states[i], lit, off));
        }
    }

    private Material lensMaterial(SignalSymbols.Symbol symbol, ColorRGBA color, boolean lit) {
        String key = symbol + "-" + color + "-" + lit;
        ColorRGBA tint = lit ? color : new ColorRGBA(color.r * 0.10f + 0.03f, color.g * 0.10f + 0.03f,
                color.b * 0.10f + 0.03f, 1f);
        if (symbol == SignalSymbols.Symbol.DISC) {
            return lit ? materials.glow(tint, 1.6f) : materials.glow(tint, 0f);
        }
        return materials.symbol(key, symbols.texture(symbol), tint, lit ? 1.8f : 0f);
    }

    /** A round lens, or a square facing +X on which a pictogram texture is drawn. */
    private static Mesh lensMesh(float size, boolean round) {
        MeshBuilder mb = new MeshBuilder();
        if (round) {
            mb.disc(Vector3f.ZERO, size * 0.47f, 20, Vector3f.UNIT_X);
        } else {
            Vector3f h = new Vector3f(0, size / 2, 0);
            Vector3f w = new Vector3f(0, 0, size / 2);
            mb.quad(w.subtract(h), w.negate().subtractLocal(h), w.negate().addLocal(h), w.add(h));
        }
        return mb.build();
    }

    private static void pole(MeshBuilder mb, Vec2 at, float height) {
        mb.color(new ColorRGBA(0.55f, 0.56f, 0.57f, 1f));
        mb.cylinder(Coords.world(at, 0), 0.06f, height, 10, true);
        mb.color(new ColorRGBA(0.2f, 0.2f, 0.2f, 1f));
        mb.cylinder(Coords.world(at, 0), 0.085f, 0.25f, 10, true);
    }

    /** A mast at the kerb with a horizontal arm out over the lane. */
    private static void mast(MeshBuilder mb, Vec2 kerb, Vec2 over) {
        float top = OVERHEAD_HEIGHT;
        mb.color(new ColorRGBA(0.55f, 0.56f, 0.57f, 1f));
        mb.cone(Coords.world(kerb, 0), 0.14f, 0.10f, top + 0.4f, 12, true);
        Vector3f a = Coords.world(kerb, top);
        Vector3f b = Coords.world(over, top);
        Vector3f dir = b.subtract(a);
        float len = dir.length() + 0.3f;
        Matrix4f m = new Matrix4f();
        Quaternion q = new Quaternion().fromAngleAxis(-FastMath.atan2(dir.z, dir.x), Vector3f.UNIT_Y);
        q = q.mult(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_Z));
        m.setRotationQuaternion(q);
        m.setTranslation(a);
        mb.push(m);
        mb.cylinder(Vector3f.ZERO, 0.075f, len, 10, true);
        mb.pop();
    }

    private static Matrix4f translation(Vector3f t) {
        Matrix4f m = new Matrix4f();
        m.setTranslation(t);
        return m;
    }

    /** True when {@code p} lies on a carriageway other than {@code except}. */
    private static boolean onCarriageway(Vec2 p, List<Lane> lanes, Lane except) {
        for (Lane lane : lanes) {
            if (lane == except) {
                continue;
            }
            if (lane.path().distanceTo(p) < lane.width() / 2 + 0.3) {
                return true;
            }
        }
        return false;
    }
}
