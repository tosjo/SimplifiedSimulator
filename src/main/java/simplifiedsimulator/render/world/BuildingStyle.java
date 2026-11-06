package simplifiedsimulator.render.world;

import com.jme3.math.ColorRGBA;
import java.util.List;
import simplifiedsimulator.render.agents.Palette;

/**
 * Facade and roof styles found around the crossing, and the rules that pick one for a
 * building from its height, size, age and position.
 */
public final class BuildingStyle {

    /** Wall finishes; each has its own procedural texture. */
    public enum Facade {
        /** Pre-1975 dark red-brown brick with small windows (the row houses north of the canal). */
        OLD_BRICK(new ColorRGBA(0.46f, 0.22f, 0.16f, 1f)),
        /** 1975-1995 orange-red brick. */
        RED_BRICK(new ColorRGBA(0.62f, 0.32f, 0.22f, 1f)),
        /** Newer pale yellow-grey brick. */
        PALE_BRICK(new ColorRGBA(0.72f, 0.64f, 0.52f, 1f)),
        /** Newer dark grey brick. */
        DARK_BRICK(new ColorRGBA(0.27f, 0.26f, 0.26f, 1f)),
        /** White rendered walls. */
        PLASTER(new ColorRGBA(0.86f, 0.85f, 0.82f, 1f)),
        /** Offices: light panels with bands of windows. */
        OFFICE(new ColorRGBA(0.70f, 0.71f, 0.72f, 1f)),
        /** Sheds, supermarkets and warehouses: profiled metal sheet, few windows. */
        INDUSTRIAL(new ColorRGBA(0.55f, 0.57f, 0.58f, 1f)),
        /** Small sheds and garages without windows. */
        SHED(new ColorRGBA(0.50f, 0.27f, 0.20f, 1f));

        private final ColorRGBA base;

        Facade(ColorRGBA base) {
            this.base = base;
        }

        public ColorRGBA base() {
            return base;
        }
    }

    /** Roof finishes. */
    public enum Roof {
        RED_TILES(new ColorRGBA(0.55f, 0.24f, 0.16f, 1f)),
        DARK_TILES(new ColorRGBA(0.20f, 0.20f, 0.22f, 1f)),
        BROWN_TILES(new ColorRGBA(0.35f, 0.22f, 0.17f, 1f)),
        FLAT(new ColorRGBA(0.30f, 0.30f, 0.30f, 1f));

        private final ColorRGBA base;

        Roof(ColorRGBA base) {
            this.base = base;
        }

        public ColorRGBA base() {
            return base;
        }
    }

    private static final List<Palette.Weighted<Facade>> MODERN_HOUSES = List.of(
            new Palette.Weighted<>(Facade.PALE_BRICK, 0.35), new Palette.Weighted<>(Facade.DARK_BRICK, 0.25),
            new Palette.Weighted<>(Facade.RED_BRICK, 0.25), new Palette.Weighted<>(Facade.PLASTER, 0.15));

    private static final List<Palette.Weighted<Roof>> TILES = List.of(
            new Palette.Weighted<>(Roof.RED_TILES, 0.4), new Palette.Weighted<>(Roof.DARK_TILES, 0.4),
            new Palette.Weighted<>(Roof.BROWN_TILES, 0.2));

    private BuildingStyle() {
    }

    /**
     * Chooses a facade.
     *
     * @param height    roof height (m)
     * @param footprint approximate ground area (m²)
     * @param year      construction year or 0
     * @param seed      stable per-building number
     */
    public static Facade facade(float height, float footprint, int year, int seed) {
        if (height < 3.6f && footprint < 60) {
            return Facade.SHED;
        }
        if (footprint > 1800 && height < 13) {
            return Facade.INDUSTRIAL;
        }
        if (height > 12 || footprint > 900) {
            return Facade.OFFICE;
        }
        if (year > 0 && year < 1975) {
            return Facade.OLD_BRICK;
        }
        if (year > 0 && year < 1995) {
            return Facade.RED_BRICK;
        }
        return Palette.pick(MODERN_HOUSES, seed, 11);
    }

    /** Chooses the tiles for a pitched roof. */
    public static Roof tiles(int year, int seed) {
        if (year > 0 && year < 1975) {
            return Palette.unit(seed, 5) < 0.7 ? Roof.RED_TILES : Roof.BROWN_TILES;
        }
        return Palette.pick(TILES, seed, 7);
    }
}
