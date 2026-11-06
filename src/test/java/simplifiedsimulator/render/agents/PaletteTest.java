package simplifiedsimulator.render.agents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.ColorRGBA;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaletteTest {

    @Test
    void sameVariantAlwaysGetsTheSameColour() {
        for (int v = -50; v < 50; v++) {
            assertEquals(Palette.pick(Palette.CAR_PAINT, v, 2), Palette.pick(Palette.CAR_PAINT, v, 2));
        }
    }

    @Test
    void choicesFollowTheWeights() {
        List<Palette.Weighted<String>> options = List.of(new Palette.Weighted<>("a", 0.7),
                new Palette.Weighted<>("b", 0.2), new Palette.Weighted<>("c", 0.1));
        Map<String, Integer> counts = new HashMap<>();
        int n = 100_000;
        for (int v = 0; v < n; v++) {
            counts.merge(Palette.pick(options, v, 1), 1, Integer::sum);
        }
        assertEquals(0.7, counts.get("a") / (double) n, 0.01);
        assertEquals(0.2, counts.get("b") / (double) n, 0.01);
        assertEquals(0.1, counts.get("c") / (double) n, 0.01);
    }

    @Test
    void unitIsInRangeAndSaltsAreIndependent() {
        int same = 0;
        for (int v = 0; v < 10_000; v++) {
            double a = Palette.unit(v, 1);
            double b = Palette.unit(v, 2);
            assertTrue(a >= 0 && a < 1);
            if (Math.abs(a - b) < 1e-3) {
                same++;
            }
        }
        assertTrue(same < 50, "salts must give different numbers");
    }

    @Test
    void varyKeepsColoursClose() {
        ColorRGBA base = new ColorRGBA(0.5f, 0.4f, 0.3f, 1f);
        ColorRGBA varied = Palette.vary(base, 7, 3, 0.1f);
        assertEquals(base.r, varied.r, 0.051f);
        assertNotEquals(-1f, varied.g);
    }
}
