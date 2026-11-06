package simplifiedsimulator.render.agents;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import simplifiedsimulator.render.mesh.MeshBuilder;

/**
 * Procedural vehicle meshes. Models face +X with Y up and +Z to the right, and are centred
 * on the middle of the vehicle at ground level. Body meshes are white where the paint goes
 * so that one mesh serves every colour (the paint is applied by the material).
 */
public final class VehicleModels {

    /** Car body styles with their share of Dutch traffic. */
    public enum CarBody {
        HATCHBACK(0.38), ESTATE(0.20), SEDAN(0.14), SUV(0.28);

        private final double share;

        CarBody(double share) {
            this.share = share;
        }

        public double share() {
            return share;
        }
    }

    /**
     * Meshes of one vehicle type.
     *
     * @param body         painted body (vertex colours: white = paint)
     * @param painted      whether the body material multiplies with a paint colour
     * @param wheel        one wheel, centred on its axle, rolling around Z
     * @param wheelRadius  for converting distance into wheel rotation
     * @param wheels       wheel centres
     * @param headlights   front lamps
     * @param taillights   rear lamps (also brake lights)
     * @param indicatorsLeft  left indicator lamps
     * @param indicatorsRight right indicator lamps
     * @param length       overall length (m)
     */
    public record Model(Mesh body, boolean painted, Mesh wheel, float wheelRadius, List<Vector3f> wheels,
                        Mesh headlights, Mesh taillights, Mesh indicatorsLeft, Mesh indicatorsRight, float length) {
    }

    private static final ColorRGBA PAINT = ColorRGBA.White;
    private static final ColorRGBA GLASS = new ColorRGBA(0.13f, 0.16f, 0.19f, 1f);
    private static final ColorRGBA TRIM = new ColorRGBA(0.06f, 0.06f, 0.065f, 1f);
    private static final ColorRGBA UNDER = new ColorRGBA(0.03f, 0.03f, 0.03f, 1f);

    private final Map<CarBody, Model> cars = new EnumMap<>(CarBody.class);
    private final Map<String, Model> others = new HashMap<>();

    public Model car(CarBody style) {
        return cars.computeIfAbsent(style, VehicleModels::buildCar);
    }

    public Model van() {
        return others.computeIfAbsent("van", k -> buildVan());
    }

    /** Truck with a cab in the given colour and a white box body. */
    public Model truck(ColorRGBA cab) {
        return others.computeIfAbsent("truck" + cab, k -> buildTruck(cab));
    }

    /** City bus in the given two-tone livery (lower, upper). */
    public Model bus(ColorRGBA[] livery) {
        return others.computeIfAbsent("bus" + livery[0] + livery[1], k -> buildBus(livery));
    }

    // ---------------------------------------------------------------------------------------
    // Cars
    // ---------------------------------------------------------------------------------------

    /** A station of the lower body: position along the car, bottom, top and width factor. */
    private record Station(float x, float bottom, float top, float width) {
    }

    /** A station of the glasshouse: position along the car and roof height. */
    private record Roof(float x, float height) {
    }

    private static Model buildCar(CarBody style) {
        return switch (style) {
            case HATCHBACK -> car(4.05f, 1.78f, 2.55f, 0.31f, List.of(
                    new Station(-2.02f, 0.38f, 0.86f, 0.90f), new Station(-1.95f, 0.30f, 0.96f, 0.97f),
                    new Station(-1.30f, 0.27f, 0.99f, 1.0f), new Station(1.00f, 0.27f, 0.94f, 1.0f),
                    new Station(1.80f, 0.29f, 0.82f, 0.97f), new Station(2.02f, 0.34f, 0.70f, 0.90f)),
                    List.of(new Roof(-1.92f, 1.0f), new Roof(-1.72f, 1.44f), new Roof(0.25f, 1.47f), new Roof(1.05f, 0.95f)));
            case SEDAN -> car(4.70f, 1.82f, 2.80f, 0.32f, List.of(
                    new Station(-2.35f, 0.40f, 0.88f, 0.92f), new Station(-2.28f, 0.30f, 0.98f, 0.97f),
                    new Station(-1.30f, 0.27f, 1.00f, 1.0f), new Station(1.25f, 0.27f, 0.94f, 1.0f),
                    new Station(2.10f, 0.29f, 0.80f, 0.97f), new Station(2.35f, 0.34f, 0.68f, 0.90f)),
                    List.of(new Roof(-1.75f, 1.0f), new Roof(-1.10f, 1.43f), new Roof(0.45f, 1.45f), new Roof(1.30f, 0.95f)));
            case ESTATE -> car(4.75f, 1.82f, 2.80f, 0.32f, List.of(
                    new Station(-2.37f, 0.40f, 0.88f, 0.92f), new Station(-2.30f, 0.30f, 0.98f, 0.97f),
                    new Station(-1.30f, 0.27f, 1.00f, 1.0f), new Station(1.25f, 0.27f, 0.94f, 1.0f),
                    new Station(2.12f, 0.29f, 0.80f, 0.97f), new Station(2.37f, 0.34f, 0.68f, 0.90f)),
                    List.of(new Roof(-2.28f, 1.02f), new Roof(-2.12f, 1.46f), new Roof(0.45f, 1.48f), new Roof(1.30f, 0.95f)));
            case SUV -> car(4.55f, 1.86f, 2.70f, 0.36f, List.of(
                    new Station(-2.27f, 0.48f, 1.02f, 0.92f), new Station(-2.20f, 0.38f, 1.12f, 0.97f),
                    new Station(-1.30f, 0.35f, 1.15f, 1.0f), new Station(1.20f, 0.35f, 1.10f, 1.0f),
                    new Station(2.05f, 0.37f, 0.98f, 0.97f), new Station(2.27f, 0.42f, 0.84f, 0.92f)),
                    List.of(new Roof(-2.18f, 1.15f), new Roof(-2.00f, 1.66f), new Roof(0.40f, 1.69f), new Roof(1.20f, 1.10f)));
        };
    }

    private static Model car(float length, float width, float wheelbase, float wheelRadius, List<Station> stations,
                             List<Roof> roof) {
        MeshBuilder body = new MeshBuilder();
        lowerBody(body, stations, width);
        glasshouse(body, stations, roof, width, x -> true);

        // B-pillar and dark trim: grille, bumper strips, mirrors
        float pillarX = (roof.get(1).x() + roof.get(2).x()) / 2 + 0.15f;
        float belt = topAt(stations, pillarX);
        float roofH = roofAt(roof, pillarX);
        float half = width / 2 * 0.96f;
        body.color(PAINT);
        body.boxBetween(new Vector3f(pillarX - 0.06f, belt, -half + 0.03f), new Vector3f(pillarX + 0.06f, roofH - 0.04f,
                -half + 0.16f));
        body.boxBetween(new Vector3f(pillarX - 0.06f, belt, half - 0.16f), new Vector3f(pillarX + 0.06f, roofH - 0.04f,
                half - 0.03f));
        Station front = stations.get(stations.size() - 1);
        Station rear = stations.get(0);
        body.color(TRIM);
        body.boxBetween(new Vector3f(front.x() - 0.05f, front.bottom() + 0.06f, -width * 0.30f),
                new Vector3f(front.x() + 0.01f, front.bottom() + 0.20f, width * 0.30f));
        body.boxBetween(new Vector3f(rear.x() - 0.01f, rear.bottom() + 0.02f, -width * 0.42f),
                new Vector3f(rear.x() + 0.05f, rear.bottom() + 0.10f, width * 0.42f));
        float mirrorX = roof.get(3).x() - 0.15f;
        float mirrorY = topAt(stations, mirrorX) + 0.1f;
        body.color(PAINT);
        for (float side : new float[] {-1, 1}) {
            body.boxBetween(new Vector3f(mirrorX - 0.08f, mirrorY, side * (width / 2 + 0.12f) - 0.06f),
                    new Vector3f(mirrorX + 0.08f, mirrorY + 0.12f, side * (width / 2 + 0.12f) + 0.06f));
        }

        float wheelZ = width / 2 - 0.13f;
        List<Vector3f> wheels = List.of(new Vector3f(wheelbase / 2, wheelRadius, -wheelZ),
                new Vector3f(wheelbase / 2, wheelRadius, wheelZ), new Vector3f(-wheelbase / 2, wheelRadius, -wheelZ),
                new Vector3f(-wheelbase / 2, wheelRadius, wheelZ));

        float headY = front.top() - 0.12f;
        float tailY = rear.top() - 0.12f;
        Mesh head = lamps(front.x() - 0.03f, headY, width / 2 - 0.30f, new Vector3f(0.08f, 0.10f, 0.34f));
        Mesh tail = lamps(rear.x() + 0.02f, tailY, width / 2 - 0.22f, new Vector3f(0.06f, 0.11f, 0.30f));
        Mesh left = indicator(front.x() - 0.04f, rear.x() + 0.03f, headY - 0.10f, tailY - 0.14f, -(width / 2 - 0.12f));
        Mesh right = indicator(front.x() - 0.04f, rear.x() + 0.03f, headY - 0.10f, tailY - 0.14f, width / 2 - 0.12f);
        return new Model(body.build(), true, wheel(wheelRadius, 0.21f), wheelRadius, wheels, head, tail, left, right,
                length);
    }

    /** Rounded box sections along the car, joined into a solid. */
    private static void lowerBody(MeshBuilder mb, List<Station> stations, float width) {
        List<Vector3f[]> sections = new ArrayList<>();
        for (Station s : stations) {
            float hw = width / 2 * s.width();
            float r = Math.min(0.10f, (s.top() - s.bottom()) / 4);
            sections.add(new Vector3f[] {
                new Vector3f(s.x(), s.top(), hw - r), new Vector3f(s.x(), s.top() - r, hw),
                new Vector3f(s.x(), s.bottom() + r, hw), new Vector3f(s.x(), s.bottom(), hw - r),
                new Vector3f(s.x(), s.bottom(), -hw + r), new Vector3f(s.x(), s.bottom() + r, -hw),
                new Vector3f(s.x(), s.top() - r, -hw), new Vector3f(s.x(), s.top(), -hw + r)});
        }
        mb.loft(sections, (n, section, edge) -> n.y < -0.5f ? UNDER : PAINT);
    }

    /** Predicate on the x position of a side window. */
    private interface Glazed {
        boolean test(float x);
    }

    /**
     * The upper part of the body. Faces looking up are painted (the roof); the others are
     * glass, except at positions where {@code glazed} says the side is solid.
     */
    private static void glasshouse(MeshBuilder mb, List<Station> stations, List<Roof> roof, float width, Glazed glazed) {
        List<Vector3f[]> sections = new ArrayList<>();
        float[] xs = new float[roof.size()];
        for (int i = 0; i < roof.size(); i++) {
            Roof r = roof.get(i);
            xs[i] = r.x();
            float belt = topAt(stations, r.x()) - 0.02f;
            float top = Math.max(r.height(), belt + 0.02f);
            float hb = width / 2 * widthAt(stations, r.x()) * 0.96f;
            float ht = hb * 0.80f;
            float bevel = Math.min(0.06f, (top - belt) / 3);
            sections.add(new Vector3f[] {
                new Vector3f(r.x(), top, ht - bevel), new Vector3f(r.x(), top - bevel, ht), new Vector3f(r.x(), belt, hb),
                new Vector3f(r.x(), belt, -hb), new Vector3f(r.x(), top - bevel, -ht), new Vector3f(r.x(), top, -ht + bevel)});
        }
        mb.loft(sections, (n, section, edge) -> {
            if (n.y > 0.8f || section < 0 || section >= xs.length) {
                return PAINT;
            }
            boolean side = Math.abs(n.z) > 0.6f;
            float mid = (xs[section] + xs[Math.min(section + 1, xs.length - 1)]) / 2;
            return side && !glazed.test(mid) ? PAINT : GLASS;
        });
    }

    private static float topAt(List<Station> st, float x) {
        return interpolate(st, x, Station::top);
    }

    private static float widthAt(List<Station> st, float x) {
        return interpolate(st, x, Station::width);
    }

    private static float interpolate(List<Station> st, float x, java.util.function.ToDoubleFunction<Station> f) {
        if (x <= st.get(0).x()) {
            return (float) f.applyAsDouble(st.get(0));
        }
        for (int i = 0; i + 1 < st.size(); i++) {
            Station a = st.get(i);
            Station b = st.get(i + 1);
            if (x <= b.x()) {
                float t = (x - a.x()) / (b.x() - a.x());
                return (float) (f.applyAsDouble(a) + (f.applyAsDouble(b) - f.applyAsDouble(a)) * t);
            }
        }
        return (float) f.applyAsDouble(st.get(st.size() - 1));
    }

    private static float roofAt(List<Roof> roof, float x) {
        for (int i = 0; i + 1 < roof.size(); i++) {
            Roof a = roof.get(i);
            Roof b = roof.get(i + 1);
            if (x <= b.x()) {
                float t = Math.max(0, (x - a.x()) / (b.x() - a.x()));
                return a.height() + (b.height() - a.height()) * t;
            }
        }
        return roof.get(roof.size() - 1).height();
    }

    // ---------------------------------------------------------------------------------------
    // Van, truck and bus
    // ---------------------------------------------------------------------------------------

    private static Model buildVan() {
        float width = 2.0f;
        List<Station> stations = List.of(
                new Station(-2.62f, 0.42f, 1.10f, 0.97f), new Station(-2.55f, 0.36f, 1.12f, 1.0f),
                new Station(1.55f, 0.36f, 1.10f, 1.0f), new Station(2.35f, 0.38f, 1.00f, 0.97f),
                new Station(2.62f, 0.42f, 0.86f, 0.92f));
        List<Roof> roof = List.of(new Roof(-2.60f, 2.40f), new Roof(1.25f, 2.42f), new Roof(1.95f, 1.15f));
        MeshBuilder body = new MeshBuilder();
        lowerBody(body, stations, width);
        // the cargo area has no side windows
        glasshouse(body, stations, roof, width, x -> x > 0.9f);
        body.color(PAINT);
        body.boxBetween(new Vector3f(-2.62f, 1.08f, -0.96f), new Vector3f(1.0f, 2.38f, 0.96f));
        body.color(TRIM);
        body.boxBetween(new Vector3f(2.58f, 0.45f, -0.6f), new Vector3f(2.64f, 0.62f, 0.6f));
        List<Vector3f> wheels = wheelsFor(3.35f, 0.33f, width);
        Mesh head = lamps(2.58f, 0.92f, width / 2 - 0.30f, new Vector3f(0.08f, 0.12f, 0.30f));
        Mesh tail = lamps(-2.60f, 1.00f, width / 2 - 0.08f, new Vector3f(0.06f, 0.35f, 0.10f));
        Mesh left = indicator(2.56f, -2.59f, 0.78f, 0.78f, -(width / 2 - 0.12f));
        Mesh right = indicator(2.56f, -2.59f, 0.78f, 0.78f, width / 2 - 0.12f);
        return new Model(body.build(), true, wheel(0.33f, 0.22f), 0.33f, wheels, head, tail, left, right, 5.3f);
    }

    private static Model buildTruck(ColorRGBA cab) {
        float width = 2.5f;
        MeshBuilder body = new MeshBuilder();
        // cab
        body.color(cab);
        body.boxBetween(new Vector3f(2.75f, 0.70f, -1.25f), new Vector3f(5.0f, 3.05f, 1.25f));
        body.color(GLASS);
        body.boxBetween(new Vector3f(4.98f, 1.75f, -1.12f), new Vector3f(5.03f, 2.75f, 1.12f));
        body.boxBetween(new Vector3f(3.6f, 1.75f, -1.27f), new Vector3f(4.85f, 2.65f, 1.27f));
        body.color(TRIM);
        body.boxBetween(new Vector3f(4.95f, 0.55f, -1.25f), new Vector3f(5.05f, 0.95f, 1.25f));
        body.boxBetween(new Vector3f(4.98f, 1.05f, -0.9f), new Vector3f(5.04f, 1.6f, 0.9f));
        // chassis and cargo box (white sides, painted front band)
        body.color(UNDER);
        body.boxBetween(new Vector3f(-5.0f, 0.55f, -0.5f), new Vector3f(4.9f, 0.95f, 0.5f));
        body.color(new ColorRGBA(0.90f, 0.90f, 0.89f, 1f));
        body.boxBetween(new Vector3f(-5.0f, 1.05f, -1.27f), new Vector3f(2.6f, 3.85f, 1.27f));
        body.color(new ColorRGBA(0.6f, 0.6f, 0.6f, 1f));
        body.boxBetween(new Vector3f(-5.05f, 1.05f, -1.2f), new Vector3f(-4.98f, 3.8f, 1.2f));
        List<Vector3f> wheels = new ArrayList<>();
        for (float x : new float[] {3.9f, -2.6f, -3.9f}) {
            wheels.add(new Vector3f(x, 0.5f, -1.0f));
            wheels.add(new Vector3f(x, 0.5f, 1.0f));
        }
        Mesh head = lamps(5.02f, 0.80f, width / 2 - 0.25f, new Vector3f(0.06f, 0.14f, 0.32f));
        Mesh tail = lamps(-5.05f, 0.85f, width / 2 - 0.15f, new Vector3f(0.05f, 0.14f, 0.25f));
        Mesh left = indicator(5.03f, -5.06f, 1.0f, 1.0f, -(width / 2 - 0.08f));
        Mesh right = indicator(5.03f, -5.06f, 1.0f, 1.0f, width / 2 - 0.08f);
        return new Model(body.build(), false, wheel(0.5f, 0.32f), 0.5f, wheels, head, tail, left, right, 10f);
    }

    private static Model buildBus(ColorRGBA[] livery) {
        float width = 2.55f;
        float half = width / 2;
        MeshBuilder body = new MeshBuilder();
        List<Vector3f[]> sections = new ArrayList<>();
        float[][] st = {{-5.95f, 0.40f, 3.0f}, {-5.85f, 0.32f, 3.08f}, {5.80f, 0.32f, 3.08f}, {6.0f, 0.38f, 2.95f}};
        for (float[] s : st) {
            float r = 0.12f;
            sections.add(new Vector3f[] {
                new Vector3f(s[0], s[2], half - r), new Vector3f(s[0], s[2] - r, half), new Vector3f(s[0], s[1] + r, half),
                new Vector3f(s[0], s[1], half - r), new Vector3f(s[0], s[1], -half + r), new Vector3f(s[0], s[1] + r, -half),
                new Vector3f(s[0], s[2] - r, -half), new Vector3f(s[0], s[2], -half + r)});
        }
        body.loft(sections, (n, section, edge) -> n.y < -0.5f ? UNDER : livery[1]);
        // lower livery band, black skirt
        body.color(livery[0]);
        body.boxBetween(new Vector3f(-5.9f, 0.35f, -half - 0.01f), new Vector3f(5.85f, 1.0f, half + 0.01f));
        body.color(TRIM);
        body.boxBetween(new Vector3f(-5.9f, 0.30f, -half - 0.015f), new Vector3f(5.9f, 0.45f, half + 0.015f));
        // window band on both sides, windscreen and rear window
        body.color(GLASS);
        body.boxBetween(new Vector3f(-5.6f, 1.12f, -half - 0.02f), new Vector3f(4.6f, 2.62f, half + 0.02f));
        body.boxBetween(new Vector3f(5.70f, 0.95f, -half + 0.08f), new Vector3f(6.03f, 2.82f, half - 0.08f));
        body.boxBetween(new Vector3f(-5.98f, 1.75f, -half + 0.2f), new Vector3f(-5.9f, 2.6f, half - 0.2f));
        // window pillars
        body.color(livery[1]);
        for (float x = -4.5f; x < 4.6f; x += 1.55f) {
            body.boxBetween(new Vector3f(x, 1.1f, -half - 0.03f), new Vector3f(x + 0.12f, 2.64f, half + 0.03f));
        }
        // doors on the right-hand side (front and middle)
        body.color(new ColorRGBA(0.12f, 0.14f, 0.16f, 1f));
        body.boxBetween(new Vector3f(4.35f, 0.4f, half - 0.01f), new Vector3f(5.45f, 2.6f, half + 0.035f));
        body.boxBetween(new Vector3f(-0.4f, 0.4f, half - 0.01f), new Vector3f(0.85f, 2.6f, half + 0.035f));
        // roof equipment
        body.color(new ColorRGBA(0.75f, 0.76f, 0.77f, 1f));
        body.boxBetween(new Vector3f(-1.5f, 3.08f, -0.9f), new Vector3f(1.5f, 3.35f, 0.9f));
        // destination display
        body.color(new ColorRGBA(0.05f, 0.05f, 0.05f, 1f));
        body.boxBetween(new Vector3f(5.85f, 2.6f, -0.95f), new Vector3f(6.0f, 2.92f, 0.95f));
        List<Vector3f> wheels = List.of(new Vector3f(4.0f, 0.5f, -1.05f), new Vector3f(4.0f, 0.5f, 1.05f),
                new Vector3f(-2.4f, 0.5f, -1.05f), new Vector3f(-2.4f, 0.5f, 1.05f));
        Mesh head = lamps(6.0f, 0.70f, half - 0.30f, new Vector3f(0.06f, 0.14f, 0.32f));
        Mesh tail = lamps(-5.96f, 0.85f, half - 0.12f, new Vector3f(0.05f, 0.35f, 0.14f));
        Mesh left = indicator(5.99f, -5.97f, 0.95f, 1.3f, -(half - 0.12f));
        Mesh right = indicator(5.99f, -5.97f, 0.95f, 1.3f, half - 0.12f);
        return new Model(body.build(), false, wheel(0.5f, 0.3f), 0.5f, wheels, head, tail, left, right, 12f);
    }

    /** The amber LED destination display of a bus, as its own mesh so that it can glow. */
    public static Mesh busDisplay() {
        MeshBuilder mb = new MeshBuilder();
        mb.boxBetween(new Vector3f(5.995f, 2.66f, -0.85f), new Vector3f(6.01f, 2.86f, 0.85f));
        return mb.build();
    }

    // ---------------------------------------------------------------------------------------
    // Parts
    // ---------------------------------------------------------------------------------------

    private static List<Vector3f> wheelsFor(float wheelbase, float radius, float width) {
        float z = width / 2 - 0.14f;
        return List.of(new Vector3f(wheelbase / 2, radius, -z), new Vector3f(wheelbase / 2, radius, z),
                new Vector3f(-wheelbase / 2, radius, -z), new Vector3f(-wheelbase / 2, radius, z));
    }

    /** A tyre with a silver rim on both sides, rolling around the Z axis. */
    static Mesh wheel(float radius, float width) {
        MeshBuilder mb = new MeshBuilder();
        Matrix4f toZ = new Matrix4f();
        toZ.setRotationQuaternion(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
        mb.push(toZ);
        mb.color(new ColorRGBA(0.05f, 0.05f, 0.05f, 1f));
        mb.cylinder(new Vector3f(0, -width / 2, 0), radius, width, 18, false);
        mb.color(new ColorRGBA(0.62f, 0.63f, 0.65f, 1f));
        mb.disc(new Vector3f(0, width / 2 + 0.002f, 0), radius * 0.63f, 12, Vector3f.UNIT_Y);
        mb.disc(new Vector3f(0, -width / 2 - 0.002f, 0), radius * 0.63f, 12, Vector3f.UNIT_Y.negate());
        mb.color(new ColorRGBA(0.04f, 0.04f, 0.04f, 1f));
        mb.disc(new Vector3f(0, width / 2 + 0.001f, 0), radius * 0.98f, 18, Vector3f.UNIT_Y);
        mb.disc(new Vector3f(0, -width / 2 - 0.001f, 0), radius * 0.98f, 18, Vector3f.UNIT_Y.negate());
        // spokes so that rotation is visible
        mb.color(new ColorRGBA(0.30f, 0.31f, 0.33f, 1f));
        for (int i = 0; i < 5; i++) {
            float a = FastMath.TWO_PI * i / 5;
            Matrix4f spoke = new Matrix4f();
            spoke.setRotationQuaternion(new Quaternion().fromAngleAxis(a, Vector3f.UNIT_Y));
            mb.push(spoke);
            mb.boxBetween(new Vector3f(radius * 0.1f, width / 2 + 0.003f, -0.025f),
                    new Vector3f(radius * 0.6f, width / 2 + 0.012f, 0.025f));
            mb.boxBetween(new Vector3f(radius * 0.1f, -width / 2 - 0.012f, -0.025f),
                    new Vector3f(radius * 0.6f, -width / 2 - 0.003f, 0.025f));
            mb.pop();
        }
        mb.pop();
        return mb.build();
    }

    /** A pair of lamps, mirrored left and right. */
    private static Mesh lamps(float x, float y, float z, Vector3f size) {
        MeshBuilder mb = new MeshBuilder();
        mb.box(new Vector3f(x, y, -z), size);
        mb.box(new Vector3f(x, y, z), size);
        return mb.build();
    }

    /** Front and rear indicator on one side. */
    private static Mesh indicator(float frontX, float rearX, float frontY, float rearY, float z) {
        MeshBuilder mb = new MeshBuilder();
        mb.box(new Vector3f(frontX, frontY, z), new Vector3f(0.06f, 0.06f, 0.14f));
        mb.box(new Vector3f(rearX, rearY, z), new Vector3f(0.06f, 0.06f, 0.12f));
        return mb.build();
    }
}
