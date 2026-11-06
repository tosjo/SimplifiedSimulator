package simplifiedsimulator.network;

import java.util.Locale;

/**
 * A detection zone on a lane: an inductive loop for vehicles, or a push button for pedestrians.
 *
 * @param id    unique name
 * @param lane  the lane it lies on
 * @param group the signal group it requests or extends
 * @param role  {@link Role#NEAR} loops sit at the stop line, {@link Role#FAR} loops about 80 m upstream
 * @param from  start of the zone along the lane (m)
 * @param to    end of the zone along the lane (m)
 */
public record Detector(String id, Lane lane, SignalGroup group, Role role, double from, double to) {

    public enum Role {
        NEAR, FAR, BUTTON;

        static Role parse(String s) {
            return valueOf(s.toUpperCase(Locale.ROOT));
        }
    }

    public boolean covers(double s, double halfLength) {
        return s + halfLength >= from && s - halfLength <= to;
    }
}
