package simplifiedsimulator.render.agents;

import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import simplifiedsimulator.render.Materials;
import simplifiedsimulator.render.mesh.MeshBuilder;
import simplifiedsimulator.render.mesh.Raster;
import com.jme3.texture.Texture2D;
import simplifiedsimulator.sim.AgentView;
import simplifiedsimulator.sim.SimulationView;

/**
 * Keeps one 3D model per road user in sync with the simulation.
 *
 * <p>The simulation advances in fixed steps while the screen refreshes at its own rate. To
 * keep motion smooth the renderer remembers the last two simulation states of each agent
 * and draws it in between: call {@link #update} every frame with {@code alpha}, the fraction
 * of a simulation step that has elapsed since the latest step (0 shows the previous state,
 * 1 the latest). A new simulation step is detected automatically from
 * {@link SimulationView#time()}.
 */
public final class AgentRenderer {

    private static final List<Palette.Weighted<VehicleModels.CarBody>> BODIES = List.of(
            new Palette.Weighted<>(VehicleModels.CarBody.HATCHBACK, VehicleModels.CarBody.HATCHBACK.share()),
            new Palette.Weighted<>(VehicleModels.CarBody.ESTATE, VehicleModels.CarBody.ESTATE.share()),
            new Palette.Weighted<>(VehicleModels.CarBody.SEDAN, VehicleModels.CarBody.SEDAN.share()),
            new Palette.Weighted<>(VehicleModels.CarBody.SUV, VehicleModels.CarBody.SUV.share()));

    /** Interpolation state of one agent. */
    private static final class Entry {
        final AgentVisual visual;
        Pose previous;
        Pose latest;
        Pose drawn;
        AgentView agent;

        Entry(AgentVisual visual, Pose pose, AgentView agent) {
            this.visual = visual;
            this.previous = pose;
            this.latest = pose;
            this.drawn = pose;
            this.agent = agent;
        }
    }

    private final Materials materials;
    private final Node node = new Node("agents");
    private final VehicleModels models = new VehicleModels();
    private final Map<Long, Entry> entries = new HashMap<>();
    private final VehicleVisual.LightMaterials lights;
    private final Material wheelMaterial;
    private final Material peopleMaterial;
    private final Material busMaterial;
    private final Material beamMaterial;
    private final Material displayMaterial;
    private final Mesh beamMesh;
    private final Mesh busDisplay = VehicleModels.busDisplay();
    private double lastStepTime = Double.NaN;

    public AgentRenderer(Materials materials) {
        this.materials = materials;
        lights = new VehicleVisual.LightMaterials(
                materials.glow(new ColorRGBA(0.80f, 0.82f, 0.84f, 1f), 0f),
                materials.glow(new ColorRGBA(1.0f, 0.97f, 0.88f, 1f), 1.4f),
                materials.glow(new ColorRGBA(0.35f, 0.03f, 0.03f, 1f), 0f),
                materials.glow(new ColorRGBA(0.75f, 0.05f, 0.04f, 1f), 0.5f),
                materials.glow(new ColorRGBA(1.0f, 0.08f, 0.05f, 1f), 1.5f),
                materials.glow(new ColorRGBA(0.45f, 0.25f, 0.03f, 1f), 0f),
                materials.glow(new ColorRGBA(1.0f, 0.60f, 0.05f, 1f), 1.6f));
        wheelMaterial = materials.vertexColored("wheels", 0.25f, 24f);
        peopleMaterial = materials.vertexColored("people", 0.05f, 6f);
        busMaterial = materials.vertexColored("large vehicles", 0.5f, 48f);
        displayMaterial = materials.glow(new ColorRGBA(1.0f, 0.62f, 0.10f, 1f), 0.8f);
        beamMaterial = materials.lightPool("headlights", beamTexture(), VehicleVisual.beamColor());
        MeshBuilder mb = new MeshBuilder();
        mb.quad(new Vector3f(0, 0.04f, 3.5f), new Vector3f(16, 0.04f, 3.5f), new Vector3f(16, 0.04f, -3.5f),
                new Vector3f(0, 0.04f, -3.5f));
        beamMesh = mb.build();
    }

    public Node node() {
        return node;
    }

    /**
     * Light thrown on the road by a pair of headlights: a cone widening ahead of the car
     * (u along the road, v across), brightest a few metres in front.
     */
    private static Texture2D beamTexture() {
        int w = 128;
        int h = 64;
        Raster r = new Raster(w, h).fill(new ColorRGBA(0, 0, 0, 0));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float u = (x + 0.5f) / w;
                float v = Math.abs((y + 0.5f) / h * 2 - 1);
                float spread = 0.22f + 0.78f * u;
                float across = Math.max(0, 1 - (v / spread) * (v / spread));
                float along = (float) Math.pow(1 - u, 1.3) * Math.min(1, u * 8);
                r.set(x, y, new ColorRGBA(1, 1, 1, across * along));
            }
        }
        return r.toTexture(false);
    }

    /** Number of agents currently drawn. */
    public int count() {
        return entries.size();
    }

    /**
     * Brings the models up to date.
     *
     * @param view     the simulation
     * @param alpha    fraction (0-1) of the next simulation step that has elapsed
     * @param tpf      seconds since the previous frame
     * @param lightsOn true when vehicles should drive with their lights on
     */
    public void update(SimulationView view, float alpha, float tpf, boolean lightsOn) {
        if (view.time() != lastStepTime) {
            lastStepTime = view.time();
            onSimulationStep(view);
        }
        float a = Math.max(0f, Math.min(1f, alpha));
        double clock = view.time();
        for (Entry e : entries.values()) {
            Pose pose = Pose.lerp(e.previous, e.latest, a);
            float travelled = (float) e.drawn.distance(pose);
            e.drawn = pose;
            e.visual.place(pose);
            e.visual.animate(e.agent, travelled, clock, lightsOn);
        }
    }

    /** Takes in a new simulation state: adds new agents, removes departed ones. */
    private void onSimulationStep(SimulationView view) {
        Set<Long> seen = new HashSet<>();
        for (AgentView agent : view.agents()) {
            seen.add(agent.id());
            Pose pose = new Pose(agent.x(), agent.y(), agent.heading());
            Entry e = entries.get(agent.id());
            if (e == null) {
                AgentVisual visual = create(agent);
                node.attachChild(visual.node());
                entries.put(agent.id(), new Entry(visual, pose, agent));
            } else {
                e.previous = e.latest;
                e.latest = pose;
                e.agent = agent;
            }
        }
        for (Iterator<Map.Entry<Long, Entry>> it = entries.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Entry> e = it.next();
            if (!seen.contains(e.getKey())) {
                e.getValue().visual.node().removeFromParent();
                it.remove();
            }
        }
    }

    /** Where an agent is drawn right now, for cameras that follow it. */
    public Optional<Pose> pose(long id) {
        Entry e = entries.get(id);
        return e == null ? Optional.empty() : Optional.of(e.drawn);
    }

    /** The agent hit by a ray (for example from the mouse cursor), if any. */
    public Optional<Long> pick(Ray ray) {
        CollisionResults results = new CollisionResults();
        node.collideWith(ray, results);
        for (CollisionResult r : results) {
            Spatial s = r.getGeometry();
            while (s != null) {
                Long id = s.getUserData(AgentVisual.AGENT_ID);
                if (id != null) {
                    return Optional.of(id);
                }
                s = s.getParent();
            }
        }
        return Optional.empty();
    }

    private AgentVisual create(AgentView agent) {
        int v = agent.variant();
        long id = agent.id();
        return switch (agent.type()) {
            case CAR -> vehicle(id, models.car(Palette.pick(BODIES, v, 1)),
                    materials.paint(Palette.pick(Palette.CAR_PAINT, v, 2)), null);
            case VAN -> vehicle(id, models.van(), materials.paint(Palette.pick(Palette.VAN_PAINT, v, 2)), null);
            case TRUCK -> vehicle(id, models.truck(Palette.pick(Palette.TRUCK_CAB, v, 2)), busMaterial, null);
            case BUS -> {
                Geometry display = new Geometry("destination display", busDisplay);
                display.setMaterial(displayMaterial);
                display.setShadowMode(RenderQueue.ShadowMode.Off);
                yield vehicle(id, models.bus(Palette.pick(Palette.BUS_LIVERY, v, 2)), busMaterial, display);
            }
            case BICYCLE -> new PeopleVisuals.Cyclist(id, PeopleVisuals.Person.of(v), Palette.pick(Palette.BIKE_FRAME, v, 3),
                    peopleMaterial);
            case PEDESTRIAN -> new PeopleVisuals.Pedestrian(id, PeopleVisuals.Person.of(v), peopleMaterial);
        };
    }

    private AgentVisual vehicle(long id, VehicleModels.Model model, Material body, Geometry extra) {
        return new VehicleVisual(id, model, body, wheelMaterial, lights, beamMesh, beamMaterial, extra);
    }
}
