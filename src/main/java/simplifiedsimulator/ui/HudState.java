package simplifiedsimulator.ui;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapFont;
import com.jme3.scene.Node;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.function.Supplier;
import simplifiedsimulator.render.CrossingRenderer;
import simplifiedsimulator.sim.Agent;
import simplifiedsimulator.sim.Simulation;

/**
 * The head-up display: status, statistics per approach, the signal timeline, details about
 * the followed road user, short messages and the help screen.
 */
public final class HudState extends BaseAppState {

    private static final float REFRESH = 0.25f;
    private static final float MESSAGE_TIME = 3f;

    private final Supplier<Simulation> simulation;
    private final CrossingRenderer renderer;
    private final Supplier<AppStatus> status;
    private final Node root = new Node("HUD");
    private Panel statusPanel;
    private Panel statsPanel;
    private Panel agentPanel;
    private Panel messagePanel;
    private Panel helpPanel;
    private SignalTimeline timeline;
    private boolean showTimeline = true;
    private boolean showStats = true;
    private boolean showHelp;
    private float sinceRefresh = REFRESH;
    private float messageAge = MESSAGE_TIME;

    /**
     * @param simulation supplies the running simulation (it can be replaced, for example on restart)
     * @param renderer   the renderer, for the followed road user
     * @param status     speed, pause and frame rate of the application
     */
    public HudState(Supplier<Simulation> simulation, CrossingRenderer renderer, Supplier<AppStatus> status) {
        this.simulation = simulation;
        this.renderer = renderer;
        this.status = status;
    }

    @Override
    protected void initialize(Application app) {
        SimpleApplication simple = (SimpleApplication) app;
        BitmapFont font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        BitmapFont mono = app.getAssetManager().loadFont("Interface/Fonts/Console.fnt");
        statusPanel = new Panel("status", app.getAssetManager(), font).anchor(Panel.Corner.TOP_LEFT, 12);
        statsPanel = new Panel("statistics", app.getAssetManager(), mono).anchor(Panel.Corner.TOP_RIGHT, 12);
        agentPanel = new Panel("agent", app.getAssetManager(), font).anchor(Panel.Corner.BOTTOM_RIGHT, 12)
                .minWidth(300);
        messagePanel = new Panel("message", app.getAssetManager(), font);
        helpPanel = new Panel("help", app.getAssetManager(), mono);
        helpPanel.setText(HudText.HELP);
        timeline = new SignalTimeline(app.getAssetManager(), mono, simulation.get().network().signalGroups());
        root.attachChild(statusPanel);
        root.attachChild(statsPanel);
        root.attachChild(timeline);
        simple.getGuiNode().attachChild(root);
    }

    @Override
    protected void cleanup(Application app) {
        root.removeFromParent();
    }

    @Override
    protected void onEnable() {
        root.setCullHint(Node.CullHint.Inherit);
    }

    @Override
    protected void onDisable() {
        root.setCullHint(Node.CullHint.Always);
    }

    public void toggleHelp() {
        showHelp = !showHelp;
        sinceRefresh = REFRESH;
    }

    public void toggleTimeline() {
        showTimeline = !showTimeline;
        sinceRefresh = REFRESH;
    }

    public void toggleStatistics() {
        showStats = !showStats;
        sinceRefresh = REFRESH;
    }

    /** Shows a short message in the middle of the screen for a few seconds. */
    public void flash(String message) {
        messagePanel.setText(message);
        messageAge = 0;
        sinceRefresh = REFRESH;
    }

    @Override
    public void update(float tpf) {
        messageAge += tpf;
        sinceRefresh += tpf;
        if (sinceRefresh < REFRESH) {
            return;
        }
        sinceRefresh = 0;
        Simulation sim = simulation.get();
        int w = getApplication().getCamera().getWidth();
        int h = getApplication().getCamera().getHeight();

        statusPanel.setText(statusText(sim, status.get()));
        statusPanel.layout(w, h);

        setVisible(statsPanel, showStats);
        if (showStats) {
            statsPanel.setText(HudText.GREY + "Since the start, per approach\n" + HudText.WHITE
                    + HudText.table(HudText.rows(sim.statistics().movements(sim.time()))));
            statsPanel.layout(w, h);
        }

        setVisible(timeline, showTimeline);
        if (showTimeline) {
            timeline.refresh(sim.signalPlan());
            timeline.setLocalTranslation(12, 12, 0);
        }

        OptionalLong followed = renderer.cameraRig().followed();
        Agent agent = followed.isPresent() ? sim.agent(followed.getAsLong()) : null;
        setVisible(agentPanel, agent != null);
        if (agent != null) {
            agentPanel.setText(HudText.agentInfo(agent, sim.signalState(agent.geometry().route().group())));
            agentPanel.layout(w, h);
        }

        setVisible(messagePanel, messageAge < MESSAGE_TIME);
        if (messageAge < MESSAGE_TIME) {
            messagePanel.layout(w, h);
            messagePanel.setLocalTranslation(Math.round((w - messagePanel.width()) / 2), Math.round(h * 0.78f), 0);
        }

        setVisible(helpPanel, showHelp);
        if (showHelp) {
            helpPanel.layout(w, h);
            helpPanel.setLocalTranslation(Math.round((w - helpPanel.width()) / 2),
                    Math.round((h - helpPanel.height()) / 2), 0);
        }
    }

    private static String statusText(Simulation sim, AppStatus app) {
        StringBuilder sb = new StringBuilder();
        sb.append("Harlingerstraatweg, Leeuwarden\n");
        sb.append(HudText.GREY).append(HudText.clock(sim.timeOfDay())).append("   ")
                .append(HudText.WHITE).append(HudText.speed(app.speed(), app.paused()));
        if (app.fps() >= 0) {
            sb.append(HudText.GREY).append(String.format(Locale.ROOT, "   %d fps", app.fps()));
        }
        sb.append('\n');
        sb.append(HudText.WHITE).append(sim.controller().name());
        String controllerStatus = sim.controller().status();
        if (!controllerStatus.isBlank()) {
            sb.append(HudText.GREY).append(" - ").append(controllerStatus);
        }
        sb.append('\n');
        sb.append(HudText.GREY).append(String.format(Locale.ROOT, "Traffic %.0f%%   %d road users",
                sim.demandScale() * 100, sim.agents().size()));
        int waiting = sim.totalEntryQueue();
        if (waiting > 0) {
            sb.append(String.format(Locale.ROOT, ", %d waiting to enter", waiting));
        }
        int conflicts = sim.signalConflicts() + sim.conflictViolations();
        if (conflicts > 0) {
            sb.append('\n').append(HudText.RED).append(String.format(Locale.ROOT,
                    "Safety: %d conflicting greens, %d collisions", sim.signalConflicts(), sim.conflictViolations()));
        }
        sb.append('\n').append(HudText.GREY).append("H for help");
        return sb.toString();
    }

    private void setVisible(Node node, boolean visible) {
        if (visible && node.getParent() == null) {
            root.attachChild(node);
        } else if (!visible && node.getParent() != null) {
            node.removeFromParent();
        }
    }
}
