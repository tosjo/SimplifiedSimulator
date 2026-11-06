package simplifiedsimulator.render.world;

import com.jme3.math.ColorRGBA;
import com.jme3.texture.Texture2D;
import java.util.EnumMap;
import java.util.Map;
import simplifiedsimulator.render.mesh.Raster;

/**
 * Procedural, tileable textures for walls and roofs. A wall tile covers one window bay of
 * {@link #BAY} metres wide and one storey of {@link #STOREY} metres high; roof tiles cover
 * {@link #ROOF_TILE} metres square. Each texture has a matching specular map in which only
 * the glass is shiny.
 */
public final class FacadeTextures {

    public static final float BAY = 3.0f;
    public static final float STOREY = 3.0f;
    public static final float ROOF_TILE = 2.0f;

    private static final int SIZE = 256;

    private final Map<BuildingStyle.Facade, Texture2D[]> walls = new EnumMap<>(BuildingStyle.Facade.class);
    private final Map<BuildingStyle.Roof, Texture2D> roofs = new EnumMap<>(BuildingStyle.Roof.class);
    private Texture2D[] glass;

    /** Diffuse and specular texture of a wall style. */
    public Texture2D[] wall(BuildingStyle.Facade facade) {
        return walls.computeIfAbsent(facade, FacadeTextures::paintWall);
    }

    public Texture2D roof(BuildingStyle.Roof roof) {
        return roofs.computeIfAbsent(roof, FacadeTextures::paintRoof);
    }

    /**
     * Dark glass panes with thin aluminium frames, and the matching specular map that sets
     * how strongly each part reflects the sky.
     */
    public Texture2D[] glass() {
        if (glass == null) {
            Raster diffuse = new Raster(SIZE, SIZE).fill(new ColorRGBA(0.10f, 0.14f, 0.18f, 1f)).noise(0.02f, 64, 3);
            Raster spec = new Raster(SIZE, SIZE).fill(new ColorRGBA(0.30f, 0.32f, 0.34f, 1f));
            ColorRGBA frame = new ColorRGBA(0.50f, 0.52f, 0.55f, 1f);
            ColorRGBA dull = new ColorRGBA(0.06f, 0.06f, 0.06f, 1f);
            float step = SIZE / 2f;
            for (int i = 0; i <= 2; i++) {
                diffuse.rect(i * step - 3, 0, i * step + 3, SIZE, frame);
                diffuse.rect(0, i * step - 3, SIZE, i * step + 3, frame);
                spec.rect(i * step - 3, 0, i * step + 3, SIZE, dull);
                spec.rect(0, i * step - 3, SIZE, i * step + 3, dull);
            }
            glass = new Texture2D[] {diffuse.toTexture(true), spec.toTexture(true)};
        }
        return glass;
    }

    private static Texture2D[] paintWall(BuildingStyle.Facade facade) {
        Raster diffuse = new Raster(SIZE, SIZE);
        Raster spec = new Raster(SIZE, SIZE).fill(new ColorRGBA(0.03f, 0.03f, 0.03f, 1f));
        ColorRGBA base = facade.base();
        long seed = facade.ordinal() * 7919L;
        switch (facade) {
            case OFFICE -> {
                diffuse.fill(base).noise(0.03f, 32, seed);
                // window band with mullions over most of the storey height
                window(diffuse, spec, 0.04f, 0.28f, 0.96f, 0.92f, new ColorRGBA(0.20f, 0.22f, 0.24f, 1f), 3);
                diffuse.rect(0, SIZE * 0.92f, SIZE, SIZE * 0.95f, base.mult(0.85f));
            }
            case INDUSTRIAL -> {
                diffuse.fill(base);
                for (int x = 0; x < SIZE; x += 16) {
                    diffuse.rect(x, 0, x + 5, SIZE, base.mult(0.86f));
                    diffuse.rect(x + 5, 0, x + 7, SIZE, base.mult(1.08f));
                }
                diffuse.noise(0.025f, 64, seed);
            }
            case PLASTER -> {
                diffuse.fill(base).noise(0.04f, 16, seed);
                window(diffuse, spec, 0.28f, 0.30f, 0.72f, 0.80f, new ColorRGBA(0.92f, 0.92f, 0.90f, 1f), 1);
            }
            case SHED -> {
                bricks(diffuse, base, seed);
                diffuse.rect(0, 0, SIZE, SIZE * 0.05f, base.mult(0.7f));
            }
            default -> {
                bricks(diffuse, base, seed);
                ColorRGBA frame = facade == BuildingStyle.Facade.DARK_BRICK
                        ? new ColorRGBA(0.12f, 0.12f, 0.13f, 1f) : new ColorRGBA(0.93f, 0.93f, 0.90f, 1f);
                window(diffuse, spec, 0.27f, 0.30f, 0.73f, 0.80f, frame, 2);
                // concrete lintel above the window
                diffuse.rect(SIZE * 0.25f, SIZE * 0.17f, SIZE * 0.75f, SIZE * 0.20f, new ColorRGBA(0.6f, 0.6f, 0.58f, 1f));
            }
        }
        return new Texture2D[] {diffuse.toTexture(true), spec.toTexture(true)};
    }

    private static void bricks(Raster r, ColorRGBA base, long seed) {
        ColorRGBA mortar = new ColorRGBA(0.70f, 0.68f, 0.64f, 1f);
        r.fill(mortar);
        int courses = 40; // 75 mm per course over a 3 m storey
        float course = (float) SIZE / courses;
        float brick = course * 2.9f;
        java.util.Random rnd = new java.util.Random(seed);
        for (int c = 0; c < courses; c++) {
            float y0 = c * course;
            float offset = (c % 2) * brick / 2;
            for (float x = -brick; x < SIZE + brick; x += brick) {
                float v = 0.85f + rnd.nextFloat() * 0.25f;
                ColorRGBA col = new ColorRGBA(base.r * v, base.g * v, base.b * v, 1f);
                float x0 = x + offset;
                r.rect(Math.max(0, x0 + 1), y0 + 1, Math.min(SIZE, x0 + brick - 1), y0 + course - 0.8f, col);
            }
        }
        r.noise(0.03f, 32, seed + 1);
    }

    /**
     * Paints a window opening; coordinates are fractions of the tile, y measured from the top.
     *
     * @param mullions number of glass panes side by side
     */
    private static void window(Raster d, Raster spec, float x0, float y0, float x1, float y1, ColorRGBA frame,
                               int mullions) {
        float l = x0 * SIZE;
        float t = y0 * SIZE;
        float r = x1 * SIZE;
        float b = y1 * SIZE;
        d.rect(l - 3, t - 3, r + 3, b + 3, frame);
        // glass: reflects a little sky at the top, darker towards the bottom
        int rows = (int) (b - t);
        for (int i = 0; i < rows; i++) {
            float f = (float) i / rows;
            ColorRGBA glass = new ColorRGBA(0.30f - 0.16f * f, 0.36f - 0.18f * f, 0.42f - 0.18f * f, 1f);
            d.rect(l, t + i, r, t + i + 1, glass);
        }
        spec.rect(l, t, r, b, new ColorRGBA(0.85f, 0.85f, 0.85f, 1f));
        float pane = (r - l) / mullions;
        for (int m = 1; m < mullions; m++) {
            d.rect(l + m * pane - 2, t, l + m * pane + 2, b, frame);
            spec.rect(l + m * pane - 2, t, l + m * pane + 2, b, new ColorRGBA(0.03f, 0.03f, 0.03f, 1f));
        }
        // sill
        d.rect(l - 6, b + 3, r + 6, b + 7, new ColorRGBA(0.55f, 0.55f, 0.53f, 1f));
    }

    private static Texture2D paintRoof(BuildingStyle.Roof roof) {
        Raster r = new Raster(SIZE, SIZE);
        ColorRGBA base = roof.base();
        if (roof == BuildingStyle.Roof.FLAT) {
            r.fill(base).noise(0.08f, 8, 99).noise(0.05f, 64, 98);
            return r.toTexture(true);
        }
        r.fill(base.mult(0.6f));
        int rows = 6; // ~33 cm per row of tiles over a 2 m texture
        float row = (float) SIZE / rows;
        int perRow = 8;
        float tile = (float) SIZE / perRow;
        java.util.Random rnd = new java.util.Random(roof.ordinal());
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < perRow; x++) {
                float v = 0.85f + rnd.nextFloat() * 0.25f;
                ColorRGBA c = new ColorRGBA(base.r * v, base.g * v, base.b * v, 1f);
                float x0 = x * tile + (y % 2) * tile / 2;
                for (float dx = -SIZE; dx <= 0; dx += SIZE) {
                    r.rect(x0 + dx + 1, y * row + 1, x0 + dx + tile - 1, (y + 1) * row - 3, c);
                    // shadow under the overlapping tile edge
                    r.rect(x0 + dx + 1, (y + 1) * row - 3, x0 + dx + tile - 1, (y + 1) * row, c.mult(0.6f));
                }
                if (x0 + tile > SIZE) {
                    r.rect(0, y * row + 1, x0 + tile - SIZE - 1, (y + 1) * row - 3, c);
                }
            }
        }
        r.noise(0.04f, 16, roof.ordinal() + 5L);
        return r.toTexture(true);
    }
}
