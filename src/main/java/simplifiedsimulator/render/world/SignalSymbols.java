package simplifiedsimulator.render.world;

import com.jme3.math.ColorRGBA;
import com.jme3.texture.Texture2D;
import java.util.EnumMap;
import java.util.Map;
import simplifiedsimulator.render.mesh.Raster;

/**
 * White-on-transparent pictograms for the lenses of Dutch traffic lights. They are tinted
 * by the lamp colour when drawn.
 */
public final class SignalSymbols {

    /** The pictograms used on the crossing. */
    public enum Symbol {
        /** Full round lens (car lights). */
        DISC,
        /** Bicycle (cyclist lights). */
        BICYCLE,
        /** Standing figure (pedestrian red). */
        STANDING,
        /** Walking figure (pedestrian green). */
        WALKING,
        /** Horizontal bar: bus must stop. */
        BAR_STOP,
        /** Dot: bus prepares to stop. */
        DOT,
        /** Vertical bar: bus may go. */
        BAR_GO
    }

    private static final int SIZE = 128;
    private static final ColorRGBA WHITE = ColorRGBA.White;

    private final Map<Symbol, Texture2D> textures = new EnumMap<>(Symbol.class);

    public Texture2D texture(Symbol symbol) {
        return textures.computeIfAbsent(symbol, SignalSymbols::paint);
    }

    static Raster raster(Symbol symbol) {
        Raster r = new Raster(SIZE, SIZE).fill(new ColorRGBA(1, 1, 1, 0));
        float c = SIZE / 2f;
        switch (symbol) {
            case DISC -> r.circle(c, c, SIZE * 0.47f, WHITE);
            case BICYCLE -> {
                float wheel = 19;
                r.ring(36, 82, wheel - 6, wheel, WHITE);
                r.ring(92, 82, wheel - 6, wheel, WHITE);
                r.line(36, 82, 58, 82, 6, WHITE);   // chain stay
                r.line(58, 82, 84, 56, 6, WHITE);   // down tube
                r.line(48, 56, 84, 56, 6, WHITE);   // top tube
                r.line(36, 82, 48, 56, 6, WHITE);   // seat stay
                r.line(58, 82, 46, 50, 6, WHITE);   // seat tube
                r.line(84, 56, 92, 82, 6, WHITE);   // fork
                r.line(80, 46, 88, 46, 6, WHITE);   // handlebar
                r.line(84, 56, 84, 46, 5, WHITE);
                r.line(40, 50, 52, 50, 7, WHITE);   // saddle
            }
            case STANDING -> {
                r.circle(c, 22, 11, WHITE);
                r.rect(c - 14, 37, c + 14, 78, WHITE);
                r.rect(c - 20, 39, c - 15, 72, WHITE);
                r.rect(c + 15, 39, c + 20, 72, WHITE);
                r.rect(c - 13, 76, c - 2, 116, WHITE);
                r.rect(c + 2, 76, c + 13, 116, WHITE);
            }
            case WALKING -> {
                r.circle(c + 4, 22, 11, WHITE);
                r.polygon(new float[] {c - 8, c + 12, c + 8, c - 12}, new float[] {37, 37, 78, 78}, WHITE);
                r.line(c - 6, 42, c - 22, 66, 8, WHITE);   // back arm
                r.line(c + 10, 42, c + 26, 62, 8, WHITE);  // front arm
                r.line(c - 6, 76, c - 26, 114, 11, WHITE); // back leg
                r.line(c + 4, 76, c + 22, 114, 11, WHITE); // front leg
            }
            case BAR_STOP -> r.rect(16, c - 10, SIZE - 16, c + 10, WHITE);
            case DOT -> r.circle(c, c, 16, WHITE);
            case BAR_GO -> r.rect(c - 10, 16, c + 10, SIZE - 16, WHITE);
            default -> throw new IllegalArgumentException(symbol.toString());
        }
        return r;
    }

    private static Texture2D paint(Symbol symbol) {
        return raster(symbol).toTexture(false);
    }
}
