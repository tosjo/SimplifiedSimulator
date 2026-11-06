package simplifiedsimulator.ui;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapFont;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import simplifiedsimulator.sim.Simulation;
import simplifiedsimulator.stats.MovementStats;
import simplifiedsimulator.stats.Summary;

/**
 * The head-up display of a controller duel: the shared status, the scoreboard in the middle,
 * a label per side with its controller and safety record, the divider, messages and help.
 */
public final class DuelHud extends BaseAppState {

    private static final float REFRESH = 0.5f;
    private static final float MESSAGE_TIME = 3f;
    private static final float DIVIDER_WIDTH = 2;
    private static final float SCORE_SCALE = 1.4f;

    private final Simulation left;
    private final Simulation right;
    private final Supplier<AppStatus> status;
    private final Node root = new Node("Duel HUD");
    private Panel statusPanel;
    private Panel scorePanel;
    private Panel leftPanel;
    private Panel rightPanel;
    private Panel messagePanel;
    private Panel helpPanel;
    private Geometry divider;
    private final Quad dividerQuad = new Quad(DIVIDER_WIDTH, 1);
    private boolean showHelp;
    private float sinceRefresh = REFRESH;
    private float messageAge = MESSAGE_TIME;

    public DuelHud(Simulation left, Simulation right, Supplier<AppStatus> status) {
        this.left = Objects.requireNonNull(left);
        this.right = Objects.requireNonNull(right);
        this.status = Objects.requireNonNull(status);
    }

    @Override
    protected void initialize(Application app) {
        BitmapFont font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        BitmapFont mono = app.getAssetManager().loadFont("Interface/Fonts/Console.fnt");
        statusPanel = new Panel("status", app.getAssetManager(), font).anchor(Panel.Corner.TOP_LEFT, 12);
        scorePanel = new Panel("score", app.getAssetManager(), mono);
        // the scoreboard is the point of the duel, so it is larger than the other panels
        scorePanel.text().setSize(mono.getCharSet().getRenderedSize() * SCORE_SCALE);
        leftPanel = new Panel("left", app.getAssetManager(), font).anchor(Panel.Corner.BOTTOM_LEFT, 12);
        rightPanel = new Panel("right", app.getAssetManager(), font).anchor(Panel.Corner.BOTTOM_RIGHT, 12);
        messagePanel = new Panel("message", app.getAssetManager(), font);
        helpPanel = new Panel("help", app.getAssetManager(), mono);
        helpPanel.setText(HudText.HELP_DUEL);
        divider = new Geometry("divider", dividerQuad);
        divider.setMaterial(Panel.translucent(app.getAssetManager(), new ColorRGBA(0.04f, 0.05f, 0.07f, 0.9f)));
        divider.setQueueBucket(RenderQueue.Bucket.Gui);
        root.attachChild(divider);
        root.attachChild(statusPanel);
        root.attachChild(scorePanel);
        root.attachChild(leftPanel);
        root.attachChild(rightPanel);
        ((SimpleApplication) app).getGuiNode().attachChild(root);
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

    /** Shows a short message in the middle of the screen for a few seconds. */
    public void flash(String message) {
        messagePanel.setText(message);
        messageAge = 0;
        sinceRefresh = REFRESH;
    }

    /** Names for the two sides; identical controllers are told apart by their side. */
    public String[] sideNames() {
        String a = left.controller().name();
        String b = right.controller().name();
        return a.equals(b) ? new String[] {a + " (left)", b + " (right)"} : new String[] {a, b};
    }

    @Override
    public void update(float tpf) {
        messageAge += tpf;
        sinceRefresh += tpf;
        if (sinceRefresh < REFRESH) {
            return;
        }
        sinceRefresh = 0;
        int w = getApplication().getCamera().getWidth();
        int h = getApplication().getCamera().getHeight();

        dividerQuad.updateGeometry(DIVIDER_WIDTH, h);
        divider.updateModelBound();
        divider.setLocalTranslation(Math.round(w / 2f - DIVIDER_WIDTH / 2), 0, -1);

        statusPanel.setText(statusText(status.get()));
        statusPanel.layout(w, h);

        String[] names = sideNames();
        Summary a = left.summary();
        Summary b = right.summary();
        scorePanel.setText(HudText.duelTable(new DuelScore.Side(names[0], a, queueNow(left)),
                new DuelScore.Side(names[1], b, queueNow(right))));
        scorePanel.layout(w, h);
        scorePanel.setLocalTranslation(Math.round((w - scorePanel.width()) / 2), Math.round(h - 12 - scorePanel.height()), 0);

        leftPanel.setText(sideText(names[0], left, a));
        leftPanel.layout(w, h);
        rightPanel.setText(sideText(names[1], right, b));
        rightPanel.layout(w, h);

        setVisible(messagePanel, messageAge < MESSAGE_TIME);
        if (messageAge < MESSAGE_TIME) {
            messagePanel.layout(w, h);
            messagePanel.setLocalTranslation(Math.round((w - messagePanel.width()) / 2), Math.round(h * 0.45f), 0);
        }

        setVisible(helpPanel, showHelp);
        if (showHelp) {
            helpPanel.layout(w, h);
            helpPanel.setLocalTranslation(Math.round((w - helpPanel.width()) / 2),
                    Math.round((h - helpPanel.height()) / 2), 0);
        }
    }

    private String statusText(AppStatus app) {
        StringBuilder sb = new StringBuilder("Controller duel\n");
        sb.append(HudText.GREY).append(HudText.clock(left.timeOfDay())).append("   ").append(HudText.WHITE)
                .append(HudText.speed(app.speed(), app.paused()));
        if (app.fps() >= 0) {
            sb.append(HudText.GREY).append(String.format(Locale.ROOT, "   %d fps", app.fps()));
        }
        sb.append('\n').append(HudText.GREY)
                .append(String.format(Locale.ROOT, "Traffic %.0f%%, same arrivals on both sides", left.demandScale() * 100))
                .append('\n').append("H for help");
        return sb.toString();
    }

    private static String sideText(String name, Simulation sim, Summary summary) {
        StringBuilder sb = new StringBuilder(HudText.WHITE).append(name);
        String controllerStatus = sim.controller().status();
        if (!controllerStatus.isBlank()) {
            sb.append(HudText.GREY).append(" - ").append(controllerStatus);
        }
        sb.append('\n').append(HudText.GREY)
                .append(String.format(Locale.ROOT, "%d road users", sim.agents().size()))
                .append('\n').append(DuelScore.unsafe(summary) ? HudText.RED : HudText.GREY)
                .append("Safety: ").append(DuelScore.safety(summary));
        return sb.toString();
    }

    private static int queueNow(Simulation sim) {
        return sim.statistics().movements(sim.time()).stream().mapToInt(MovementStats::queue).sum();
    }

    private void setVisible(Node node, boolean visible) {
        if (visible && node.getParent() == null) {
            root.attachChild(node);
        } else if (!visible && node.getParent() != null) {
            node.removeFromParent();
        }
    }
}
