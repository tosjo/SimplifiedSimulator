package simplifiedsimulator.ui;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

/** A block of text on a translucent dark background, for the head-up display. */
final class Panel extends Node {

    static final float PADDING = 10;
    static final ColorRGBA BACKGROUND = new ColorRGBA(0.04f, 0.05f, 0.07f, 0.72f);

    /** Which screen corner the panel is anchored to. */
    enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    private final BitmapText text;
    private final Geometry background;
    private final Quad quad = new Quad(1, 1);
    private Corner corner = Corner.TOP_LEFT;
    private float margin = 12;
    private float minWidth;
    private String current = "";

    Panel(String name, AssetManager assets, BitmapFont font) {
        super(name);
        background = new Geometry(name + ".background", quad);
        background.setMaterial(translucent(assets, BACKGROUND));
        background.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(background);
        text = new BitmapText(font);
        text.setColor(ColorRGBA.White);
        text.setLocalTranslation(PADDING, 0, 1);
        attachChild(text);
    }

    static Material translucent(AssetManager assets, ColorRGBA colour) {
        Material m = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", colour);
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        return m;
    }

    Panel anchor(Corner c, float marginPixels) {
        this.corner = c;
        this.margin = marginPixels;
        return this;
    }

    Panel minWidth(float width) {
        this.minWidth = width;
        return this;
    }

    BitmapText text() {
        return text;
    }

    /** Sets the text; jME's bitmap fonts understand colour tags such as {@code \#7fd27f#}. */
    void setText(String value) {
        if (value.equals(current)) {
            return;
        }
        current = value;
        text.setText(value);
    }

    float width() {
        return Math.max(minWidth, text.getLineWidth() + 2 * PADDING);
    }

    float height() {
        return text.getHeight() + 2 * PADDING;
    }

    /** Resizes the background and places the panel for a screen of the given size. */
    void layout(int screenWidth, int screenHeight) {
        float w = width();
        float h = height();
        quad.updateGeometry(w, h);
        background.updateModelBound();
        text.setLocalTranslation(PADDING, h - PADDING, 1);
        float x = switch (corner) {
            case TOP_LEFT, BOTTOM_LEFT -> margin;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - margin - w;
        };
        float y = switch (corner) {
            case TOP_LEFT, TOP_RIGHT -> screenHeight - margin - h;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> margin;
        };
        setLocalTranslation(Math.round(x), Math.round(y), 0);
    }
}
