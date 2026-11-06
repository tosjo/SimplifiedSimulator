package simplifiedsimulator.ui;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.texture.image.ImageRaster;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.List;
import simplifiedsimulator.control.SignalPlan;
import simplifiedsimulator.network.SignalGroup;
import simplifiedsimulator.sim.SignalState;

/**
 * The last two minutes of every signal group as coloured bars, the way traffic engineers
 * look at a controller: one row per group, time running from left to right.
 */
final class SignalTimeline extends Node {

    static final double SECONDS = 120;
    private static final int COLUMNS = 480;
    private static final int ROW = 9;
    private static final int GAP = 2;
    private static final float LABEL_WIDTH = 34;
    private static final ColorRGBA GREEN = new ColorRGBA(0.20f, 0.80f, 0.30f, 1);
    private static final ColorRGBA AMBER = new ColorRGBA(1.00f, 0.70f, 0.05f, 1);
    private static final ColorRGBA RED = new ColorRGBA(0.80f, 0.12f, 0.12f, 1);
    private static final ColorRGBA EMPTY = new ColorRGBA(0.15f, 0.15f, 0.17f, 1);
    private static final ColorRGBA TICK = new ColorRGBA(0.30f, 0.30f, 0.34f, 1);

    private final List<SignalGroup> groups = new ArrayList<>();
    private final Image image;
    private final ImageRaster raster;
    private final Geometry background;
    private final Quad backgroundQuad = new Quad(1, 1);
    private final float width;
    private final float height;

    SignalTimeline(AssetManager assets, BitmapFont font, Iterable<SignalGroup> signalGroups) {
        super("Signal timeline");
        signalGroups.forEach(groups::add);
        int rows = groups.size();
        int pixelHeight = rows * (ROW + GAP);
        image = new Image(Image.Format.RGBA8, COLUMNS, pixelHeight,
                BufferUtils.createByteBuffer(COLUMNS * pixelHeight * 4), ColorSpace.sRGB);
        raster = ImageRaster.create(image);
        Texture2D texture = new Texture2D(image);
        texture.setMagFilter(Texture.MagFilter.Nearest);
        texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);

        width = LABEL_WIDTH + COLUMNS + 2 * Panel.PADDING;
        BitmapText title = new BitmapText(font);
        title.setText("Signal groups, last " + (int) SECONDS + " s");
        title.setSize(font.getCharSet().getRenderedSize());
        float titleHeight = title.getLineHeight() + 4;
        height = pixelHeight + titleHeight + 2 * Panel.PADDING;

        background = new Geometry("timeline.background", backgroundQuad);
        backgroundQuad.updateGeometry(width, height);
        background.setMaterial(Panel.translucent(assets, Panel.BACKGROUND));
        background.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(background);

        title.setLocalTranslation(Panel.PADDING, height - Panel.PADDING, 1);
        attachChild(title);

        Geometry bars = new Geometry("timeline.bars", new Quad(COLUMNS, pixelHeight));
        Material m = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setTexture("ColorMap", texture);
        bars.setMaterial(m);
        bars.setQueueBucket(RenderQueue.Bucket.Gui);
        bars.setLocalTranslation(Panel.PADDING + LABEL_WIDTH, Panel.PADDING, 1);
        attachChild(bars);

        // labels, top row first; the image's row 0 is at the bottom
        for (int i = 0; i < rows; i++) {
            BitmapText label = new BitmapText(font);
            label.setSize(ROW + 3f);
            label.setText(groups.get(i).id());
            float y = Panel.PADDING + pixelHeight - i * (ROW + GAP) + 1;
            label.setLocalTranslation(Panel.PADDING, y, 1);
            label.setColor(new ColorRGBA(0.85f, 0.85f, 0.85f, 1));
            attachChild(label);
        }
    }

    float width() {
        return width;
    }

    float height() {
        return height;
    }

    /** Redraws the bars from the controller's history. */
    void refresh(SignalPlan plan) {
        int rows = groups.size();
        double now = plan.now();
        for (int i = 0; i < rows; i++) {
            SignalGroup g = groups.get(i);
            SignalState[] states = TimelineColumns.compute(plan.history(g), plan.state(g), now, COLUMNS, SECONDS);
            int top = (rows - i) * (ROW + GAP) - 1;
            for (int c = 0; c < COLUMNS; c++) {
                ColorRGBA colour = colourOf(states[c]);
                for (int r = 0; r < ROW; r++) {
                    raster.setPixel(c, top - r, colour);
                }
                for (int r = ROW; r < ROW + GAP; r++) {
                    raster.setPixel(c, top - r, ColorRGBA.BlackNoAlpha);
                }
            }
            // a faint tick every 10 seconds, counted back from now
            for (double t = 10; t < SECONDS; t += 10) {
                int c = (int) Math.round((COLUMNS - 1) * (1 - t / SECONDS));
                raster.setPixel(c, top - ROW, TICK);
            }
        }
        image.setUpdateNeeded();
    }

    private static ColorRGBA colourOf(SignalState s) {
        if (s == null) {
            return EMPTY;
        }
        return switch (s) {
            case GREEN -> GREEN;
            case AMBER -> AMBER;
            case RED -> RED;
        };
    }
}
