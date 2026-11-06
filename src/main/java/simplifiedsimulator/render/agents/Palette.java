package simplifiedsimulator.render.agents;

import com.jme3.math.ColorRGBA;
import java.util.List;

/**
 * Colours and body styles for road users, chosen deterministically from an agent's
 * {@code variant} so a vehicle keeps its look for its whole life.
 *
 * <p>The car colour shares follow recent Dutch new-car registrations: grey, black and
 * white dominate, with some blue, silver and red.
 */
public final class Palette {

    /** A colour with a relative frequency. */
    public record Weighted<T>(T value, double weight) {
    }

    public static final List<Weighted<ColorRGBA>> CAR_PAINT = List.of(
            new Weighted<>(rgb(0.36f, 0.37f, 0.39f), 0.18),  // dark grey
            new Weighted<>(rgb(0.55f, 0.56f, 0.57f), 0.12),  // mid grey
            new Weighted<>(rgb(0.04f, 0.04f, 0.05f), 0.20),  // black
            new Weighted<>(rgb(0.88f, 0.89f, 0.90f), 0.17),  // white
            new Weighted<>(rgb(0.10f, 0.20f, 0.42f), 0.08),  // dark blue
            new Weighted<>(rgb(0.25f, 0.45f, 0.70f), 0.04),  // light blue
            new Weighted<>(rgb(0.72f, 0.73f, 0.75f), 0.08),  // silver
            new Weighted<>(rgb(0.60f, 0.06f, 0.07f), 0.07),  // red
            new Weighted<>(rgb(0.15f, 0.28f, 0.20f), 0.02),  // dark green
            new Weighted<>(rgb(0.40f, 0.30f, 0.22f), 0.02),  // brown
            new Weighted<>(rgb(0.80f, 0.55f, 0.10f), 0.01),  // orange
            new Weighted<>(rgb(0.85f, 0.80f, 0.62f), 0.01)); // beige

    public static final List<Weighted<ColorRGBA>> VAN_PAINT = List.of(
            new Weighted<>(rgb(0.92f, 0.93f, 0.94f), 0.65),
            new Weighted<>(rgb(0.55f, 0.56f, 0.57f), 0.15),
            new Weighted<>(rgb(0.04f, 0.04f, 0.05f), 0.08),
            new Weighted<>(rgb(0.10f, 0.20f, 0.42f), 0.07),
            new Weighted<>(rgb(0.60f, 0.06f, 0.07f), 0.05));

    public static final List<Weighted<ColorRGBA>> TRUCK_CAB = List.of(
            new Weighted<>(rgb(0.92f, 0.93f, 0.94f), 0.45),
            new Weighted<>(rgb(0.60f, 0.06f, 0.07f), 0.15),
            new Weighted<>(rgb(0.10f, 0.20f, 0.42f), 0.15),
            new Weighted<>(rgb(0.15f, 0.15f, 0.16f), 0.15),
            new Weighted<>(rgb(0.85f, 0.65f, 0.10f), 0.10));

    /** Two-tone regional bus liveries (lower body, upper body). */
    public static final List<Weighted<ColorRGBA[]>> BUS_LIVERY = List.of(
            new Weighted<>(new ColorRGBA[] {rgb(0.80f, 0.10f, 0.12f), rgb(0.93f, 0.93f, 0.92f)}, 0.4),
            new Weighted<>(new ColorRGBA[] {rgb(0.05f, 0.25f, 0.55f), rgb(0.93f, 0.93f, 0.92f)}, 0.35),
            new Weighted<>(new ColorRGBA[] {rgb(0.12f, 0.45f, 0.25f), rgb(0.93f, 0.93f, 0.92f)}, 0.25));

    /** Typical Dutch city bikes: mostly black, some dark blue, green, grey or white. */
    public static final List<Weighted<ColorRGBA>> BIKE_FRAME = List.of(
            new Weighted<>(rgb(0.05f, 0.05f, 0.06f), 0.55),
            new Weighted<>(rgb(0.10f, 0.15f, 0.30f), 0.12),
            new Weighted<>(rgb(0.15f, 0.25f, 0.18f), 0.10),
            new Weighted<>(rgb(0.50f, 0.50f, 0.52f), 0.10),
            new Weighted<>(rgb(0.85f, 0.85f, 0.84f), 0.08),
            new Weighted<>(rgb(0.55f, 0.15f, 0.12f), 0.05));

    /** Coats and tops; Dutch streets are dark-clothed most of the year. */
    public static final List<Weighted<ColorRGBA>> CLOTHING = List.of(
            new Weighted<>(rgb(0.08f, 0.08f, 0.09f), 0.25),
            new Weighted<>(rgb(0.15f, 0.17f, 0.24f), 0.18),
            new Weighted<>(rgb(0.35f, 0.36f, 0.38f), 0.12),
            new Weighted<>(rgb(0.45f, 0.30f, 0.20f), 0.08),
            new Weighted<>(rgb(0.20f, 0.30f, 0.22f), 0.08),
            new Weighted<>(rgb(0.70f, 0.70f, 0.68f), 0.07),
            new Weighted<>(rgb(0.60f, 0.12f, 0.12f), 0.07),
            new Weighted<>(rgb(0.25f, 0.40f, 0.65f), 0.08),
            new Weighted<>(rgb(0.85f, 0.65f, 0.20f), 0.04),
            new Weighted<>(rgb(0.80f, 0.45f, 0.55f), 0.03));

    public static final List<Weighted<ColorRGBA>> TROUSERS = List.of(
            new Weighted<>(rgb(0.10f, 0.12f, 0.20f), 0.45),
            new Weighted<>(rgb(0.06f, 0.06f, 0.07f), 0.30),
            new Weighted<>(rgb(0.35f, 0.33f, 0.30f), 0.15),
            new Weighted<>(rgb(0.55f, 0.50f, 0.40f), 0.10));

    public static final List<Weighted<ColorRGBA>> SKIN = List.of(
            new Weighted<>(rgb(0.93f, 0.78f, 0.67f), 0.45),
            new Weighted<>(rgb(0.85f, 0.67f, 0.53f), 0.30),
            new Weighted<>(rgb(0.62f, 0.44f, 0.32f), 0.15),
            new Weighted<>(rgb(0.40f, 0.27f, 0.20f), 0.10));

    public static final List<Weighted<ColorRGBA>> HAIR = List.of(
            new Weighted<>(rgb(0.25f, 0.17f, 0.10f), 0.35),
            new Weighted<>(rgb(0.08f, 0.06f, 0.05f), 0.25),
            new Weighted<>(rgb(0.70f, 0.58f, 0.35f), 0.20),
            new Weighted<>(rgb(0.55f, 0.55f, 0.55f), 0.12),
            new Weighted<>(rgb(0.55f, 0.25f, 0.12f), 0.08));

    private Palette() {
    }

    /**
     * Picks an entry with probability proportional to its weight.
     *
     * @param variant agent variant (any int)
     * @param salt    different salts give independent choices for the same variant
     */
    public static <T> T pick(List<Weighted<T>> options, int variant, int salt) {
        double total = 0;
        for (Weighted<T> w : options) {
            total += w.weight();
        }
        double r = unit(variant, salt) * total;
        for (Weighted<T> w : options) {
            r -= w.weight();
            if (r < 0) {
                return w.value();
            }
        }
        return options.get(options.size() - 1).value();
    }

    /** A well-mixed number in [0, 1) derived from a variant and a salt. */
    public static double unit(int variant, int salt) {
        long h = variant * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Slightly varies a colour's brightness so that identical colours do not look cloned. */
    public static ColorRGBA vary(ColorRGBA c, int variant, int salt, float amount) {
        float f = 1 + (float) (unit(variant, salt) * 2 - 1) * amount;
        return new ColorRGBA(Math.min(1, c.r * f), Math.min(1, c.g * f), Math.min(1, c.b * f), c.a);
    }

    static ColorRGBA rgb(float r, float g, float b) {
        return new ColorRGBA(r, g, b, 1f);
    }
}
