package simplifiedsimulator.network;

import java.util.Locale;

/** What a route does at the crossing. */
public enum Movement {
    LEFT, THROUGH, RIGHT, CROSSING;

    static Movement parse(String s) {
        return valueOf(s.toUpperCase(Locale.ROOT));
    }
}
