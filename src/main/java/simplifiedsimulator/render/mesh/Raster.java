package simplifiedsimulator.render.mesh;

import com.jme3.math.ColorRGBA;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * A tiny RGBA canvas for generating textures procedurally (facades, roofs, signal symbols,
 * light pools) without depending on AWT, which does not mix well with LWJGL on macOS.
 * Coordinates are in pixels with the origin in the top-left corner; shapes are anti-aliased
 * by 4x4 supersampling where it matters.
 */
public final class Raster {

    private final int width;
    private final int height;
    private final float[] rgba;

    public Raster(int width, int height) {
        this.width = width;
        this.height = height;
        this.rgba = new float[width * height * 4];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public ColorRGBA get(int x, int y) {
        int i = index(x, y);
        return new ColorRGBA(rgba[i], rgba[i + 1], rgba[i + 2], rgba[i + 3]);
    }

    /** Fills the whole canvas. */
    public Raster fill(ColorRGBA c) {
        for (int i = 0; i < rgba.length; i += 4) {
            rgba[i] = c.r;
            rgba[i + 1] = c.g;
            rgba[i + 2] = c.b;
            rgba[i + 3] = c.a;
        }
        return this;
    }

    /** Blends colour {@code c} over pixel (x, y) with the given coverage (0-1). */
    public void blend(int x, int y, ColorRGBA c, float coverage) {
        if (x < 0 || y < 0 || x >= width || y >= height || coverage <= 0) {
            return;
        }
        int i = index(x, y);
        float a = c.a * Math.min(1f, coverage);
        float outA = a + rgba[i + 3] * (1 - a);
        if (outA <= 0) {
            return;
        }
        rgba[i] = (c.r * a + rgba[i] * rgba[i + 3] * (1 - a)) / outA;
        rgba[i + 1] = (c.g * a + rgba[i + 1] * rgba[i + 3] * (1 - a)) / outA;
        rgba[i + 2] = (c.b * a + rgba[i + 2] * rgba[i + 3] * (1 - a)) / outA;
        rgba[i + 3] = outA;
    }

    /** Sets a pixel without blending. */
    public void set(int x, int y, ColorRGBA c) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        int i = index(x, y);
        rgba[i] = c.r;
        rgba[i + 1] = c.g;
        rgba[i + 2] = c.b;
        rgba[i + 3] = c.a;
    }

    /** Axis-aligned rectangle [x0, x1) x [y0, y1) with fractional edges. */
    public Raster rect(float x0, float y0, float x1, float y1, ColorRGBA c) {
        for (int y = (int) Math.floor(y0); y < Math.ceil(y1); y++) {
            float cy = Math.min(y + 1, y1) - Math.max(y, y0);
            for (int x = (int) Math.floor(x0); x < Math.ceil(x1); x++) {
                float cx = Math.min(x + 1, x1) - Math.max(x, x0);
                blend(x, y, c, cx * cy);
            }
        }
        return this;
    }

    /** Filled circle. */
    public Raster circle(float cx, float cy, float r, ColorRGBA c) {
        return shape(cx - r, cy - r, cx + r, cy + r, c, (x, y) -> (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r);
    }

    /** Filled ring between radii {@code r0} and {@code r1}. */
    public Raster ring(float cx, float cy, float r0, float r1, ColorRGBA c) {
        return shape(cx - r1, cy - r1, cx + r1, cy + r1, c, (x, y) -> {
            float d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
            return d >= r0 * r0 && d <= r1 * r1;
        });
    }

    /** Thick line segment with round caps. */
    public Raster line(float x0, float y0, float x1, float y1, float thickness, ColorRGBA c) {
        float r = thickness / 2;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len2 = dx * dx + dy * dy;
        return shape(Math.min(x0, x1) - r, Math.min(y0, y1) - r, Math.max(x0, x1) + r, Math.max(y0, y1) + r, c,
                (x, y) -> {
                    float t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - x0) * dx + (y - y0) * dy) / len2));
                    float px = x0 + t * dx - x;
                    float py = y0 + t * dy - y;
                    return px * px + py * py <= r * r;
                });
    }

    /** Filled convex or concave polygon (even-odd rule). */
    public Raster polygon(float[] xs, float[] ys, ColorRGBA c) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        return shape(minX, minY, maxX, maxY, c, (x, y) -> {
            boolean inside = false;
            for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
                if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
                    inside = !inside;
                }
            }
            return inside;
        });
    }

    /** Radial gradient from {@code inner} at the centre to transparent at radius {@code r}. */
    public Raster radialGlow(float cx, float cy, float r, ColorRGBA inner, float falloff) {
        for (int y = (int) (cy - r); y <= cy + r; y++) {
            for (int x = (int) (cx - r); x <= cx + r; x++) {
                float d = (float) Math.hypot(x + 0.5f - cx, y + 0.5f - cy) / r;
                if (d < 1) {
                    blend(x, y, inner, (float) Math.pow(1 - d, falloff));
                }
            }
        }
        return this;
    }

    /** Adds value noise in [-amount, amount] to the colour channels, keeping the image tileable. */
    public Raster noise(float amount, int cell, long seed) {
        int gw = Math.max(1, width / cell);
        int gh = Math.max(1, height / cell);
        float[] grid = new float[gw * gh];
        Random rnd = new Random(seed);
        for (int i = 0; i < grid.length; i++) {
            grid[i] = rnd.nextFloat() * 2 - 1;
        }
        Random fine = new Random(seed * 31 + 7);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float gx = (float) x / cell;
                float gy = (float) y / cell;
                int ix = (int) gx;
                int iy = (int) gy;
                float fx = smooth(gx - ix);
                float fy = smooth(gy - iy);
                float a = grid[(iy % gh) * gw + ix % gw];
                float b = grid[(iy % gh) * gw + (ix + 1) % gw];
                float c = grid[((iy + 1) % gh) * gw + ix % gw];
                float d = grid[((iy + 1) % gh) * gw + (ix + 1) % gw];
                float v = (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
                v = (v * 0.75f + (fine.nextFloat() * 2 - 1) * 0.25f) * amount;
                int i = index(x, y);
                rgba[i] = clamp(rgba[i] + v);
                rgba[i + 1] = clamp(rgba[i + 1] + v);
                rgba[i + 2] = clamp(rgba[i + 2] + v);
            }
        }
        return this;
    }

    public Texture2D toTexture(boolean repeat) {
        ByteBuffer data = BufferUtils.createByteBuffer(width * height * 4);
        // jME images have their origin at the bottom-left
        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++) {
                int i = index(x, y);
                data.put(toByte(rgba[i])).put(toByte(rgba[i + 1])).put(toByte(rgba[i + 2])).put(toByte(rgba[i + 3]));
            }
        }
        data.flip();
        Image image = new Image(Image.Format.RGBA8, width, height, data, ColorSpace.sRGB);
        Texture2D tex = new Texture2D(image);
        tex.setMagFilter(Texture.MagFilter.Bilinear);
        tex.setMinFilter(Texture.MinFilter.Trilinear);
        tex.setAnisotropicFilter(8);
        if (repeat) {
            tex.setWrap(Texture.WrapMode.Repeat);
        } else {
            tex.setWrap(Texture.WrapMode.EdgeClamp);
        }
        return tex;
    }

    private interface Inside {
        boolean test(float x, float y);
    }

    private Raster shape(float x0, float y0, float x1, float y1, ColorRGBA c, Inside inside) {
        for (int y = (int) Math.floor(y0) - 1; y <= Math.ceil(y1); y++) {
            for (int x = (int) Math.floor(x0) - 1; x <= Math.ceil(x1); x++) {
                int hits = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        if (inside.test(x + (sx + 0.5f) / 4f, y + (sy + 0.5f) / 4f)) {
                            hits++;
                        }
                    }
                }
                blend(x, y, c, hits / 16f);
            }
        }
        return this;
    }

    private int index(int x, int y) {
        return (y * width + x) * 4;
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private static byte toByte(float v) {
        return (byte) Math.round(clamp(v) * 255);
    }
}
