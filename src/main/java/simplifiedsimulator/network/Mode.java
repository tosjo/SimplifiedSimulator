package simplifiedsimulator.network;

import java.util.Locale;

/** The kind of traffic a lane, route or signal group is meant for. */
public enum Mode {
    CAR, BUS, BIKE, FOOT;

    static Mode parse(String s) {
        return valueOf(s.toUpperCase(Locale.ROOT));
    }
}
