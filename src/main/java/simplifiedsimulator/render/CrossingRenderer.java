package simplifiedsimulator.render;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Ray;
import com.jme3.post.FilterPostProcessor;
import com.jme3.scene.Node;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.render.agents.AgentRenderer;
import simplifiedsimulator.render.agents.Pose;
import simplifiedsimulator.render.environment.Atmosphere;
import simplifiedsimulator.render.environment.Environment;
import simplifiedsimulator.render.world.BuildingBuilder;
import simplifiedsimulator.render.world.BuildingData;
import simplifiedsimulator.render.world.FacadeTextures;
import simplifiedsimulator.render.world.StreetLights;
import simplifiedsimulator.render.world.TrafficSignals;
import simplifiedsimulator.render.world.TreeBuilder;
import simplifiedsimulator.render.world.TreeData;
import simplifiedsimulator.sim.SimulationView;

/**
 * Renders the Harlingerstraatweg crossing and the traffic on it.
 *
 * <p>Usage from a {@link SimpleApplication}:
 * <pre>{@code
 * CrossingRenderer renderer = new CrossingRenderer(network, RenderQuality.HIGH);
 * stateManager.attach(renderer);
 * // every frame, after advancing the simulation:
 * renderer.update(simulation, alpha);   // alpha = fraction of a simulation step since the last step
 * }</pre>
 *
 * The renderer builds the static world (ground, buildings, trees, street lights, traffic
 * lights) when attached, adds the post-processing filters to the main view port and
 * installs a {@link CameraRig} (it replaces the default fly camera). Lighting follows
 * {@link SimulationView#timeOfDay()} unless {@link #setTimeOfDayOverride} is used.
 */
public final class CrossingRenderer extends BaseAppState {

    private final Network network;
    private final RenderQuality quality;
    private final Node scene = new Node("crossing");
    private LocalDate date = LocalDate.now();
    private OptionalDouble timeOverride = OptionalDouble.empty();
    private Materials materials;
    private Environment environment;
    private AgentRenderer agents;
    private TrafficSignals signals;
    private StreetLights streetLights;
    private BuildingBuilder.Buildings buildings;
    private CameraRig cameraRig;
    private FilterPostProcessor filters;
    private SimulationView lastView;
    private float pendingAlpha;
    private double animationClock;

    public CrossingRenderer(Network network, RenderQuality quality) {
        this.network = Objects.requireNonNull(network);
        this.quality = Objects.requireNonNull(quality);
    }

    @Override
    protected void initialize(Application app) {
        SimpleApplication simple = (SimpleApplication) app;
        materials = new Materials(app.getAssetManager());
        environment = new Environment(materials);
        scene.attachChild(environment.node());

        FacadeTextures textures = new FacadeTextures();
        buildings = new BuildingBuilder(materials, textures, environment.environmentMap())
                .build(BuildingData.loadDefault());
        scene.attachChild(buildings.node());
        scene.attachChild(new TreeBuilder(materials, quality.treeDetail()).build(TreeData.loadDefault()));
        streetLights = new StreetLights(materials, network);
        scene.attachChild(streetLights.node());
        signals = new TrafficSignals(materials, network);
        scene.attachChild(signals.node());
        agents = new AgentRenderer(materials);
        scene.attachChild(agents.node());

        scene.addLight(environment.sunLight());
        scene.addLight(environment.ambientLight());

        simple.getRootNode().attachChild(scene);
        int samples = app.getContext().getSettings().getSamples();
        filters = environment.createFilters(app.getViewPort(), quality, samples);

        cameraRig = new CameraRig(agents::pose, this::pick);
        app.getStateManager().attach(cameraRig);
    }

    @Override
    protected void cleanup(Application app) {
        scene.removeFromParent();
        app.getViewPort().removeProcessor(filters);
        app.getStateManager().detach(cameraRig);
    }

    @Override
    protected void onEnable() {
        scene.setCullHint(Node.CullHint.Inherit);
    }

    @Override
    protected void onDisable() {
        scene.setCullHint(Node.CullHint.Always);
    }

    /**
     * Shows the simulation. Call once per frame.
     *
     * @param view  the simulation
     * @param alpha fraction (0-1) of a simulation step elapsed since the latest step, used to
     *              interpolate positions; pass 1 when not interpolating
     */
    public void update(SimulationView view, float alpha) {
        lastView = view;
        pendingAlpha = alpha;
    }

    @Override
    public void update(float tpf) {
        animationClock += tpf;
        double seconds = timeOverride.orElse(lastView != null ? lastView.timeOfDay() : 14 * 3600);
        environment.update(date, seconds, animationClock);
        Atmosphere atmosphere = environment.atmosphere();
        streetLights.setNight(atmosphere.night());
        buildings.setNight(atmosphere.night());
        if (lastView != null) {
            SimulationView view = lastView;
            signals.update(view::signalState, animationClock);
            agents.update(view, pendingAlpha, tpf, atmosphere.night() > 0.25f);
        }
    }

    /** Calendar date for the position of the sun (defaults to today). */
    public void setDate(LocalDate date) {
        this.date = Objects.requireNonNull(date);
    }

    /**
     * Fixes the lighting at a time of day (seconds after midnight) regardless of the
     * simulation clock; {@code OptionalDouble.empty()} follows the simulation again.
     */
    public void setTimeOfDayOverride(OptionalDouble secondsOfDay) {
        timeOverride = Objects.requireNonNull(secondsOfDay);
    }

    public RenderQuality quality() {
        return quality;
    }

    public CameraRig cameraRig() {
        return cameraRig;
    }

    /** The agent under a ray from the camera (for example through the mouse cursor). */
    public Optional<Long> pick(Ray ray) {
        return agents.pick(ray);
    }

    /** Where an agent is currently drawn. */
    public Optional<Pose> agentPose(long id) {
        return agents.pose(id);
    }

    /** Number of road users currently drawn. */
    public int agentCount() {
        return agents.count();
    }

    /** The sun's elevation in degrees, for the user interface. */
    public double sunElevation() {
        return environment.sun().elevation();
    }
}
