package simplifiedsimulator.render.environment;

import com.jme3.math.Vector3f;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Position of the sun in the sky, using the NOAA solar position equations (accurate to
 * about a tenth of a degree, plenty for lighting a scene).
 *
 * @param elevation degrees above the horizon (negative at night)
 * @param azimuth   compass bearing in degrees, 0 = north, 90 = east
 */
public record SunPosition(double elevation, double azimuth) {

    /** Leeuwarden, the crossing of the Harlingerstraatweg. */
    public static final double LATITUDE = 53.2057;
    public static final double LONGITUDE = 5.7680;
    public static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

    /**
     * Sun position at the crossing.
     *
     * @param date          local date
     * @param secondsOfDay  local (Dutch) clock time in seconds after midnight
     */
    public static SunPosition at(LocalDate date, double secondsOfDay) {
        double s = ((secondsOfDay % 86400) + 86400) % 86400;
        ZonedDateTime local = date.atStartOfDay(ZONE).plusNanos((long) (s * 1e9));
        return at(local, LATITUDE, LONGITUDE);
    }

    public static SunPosition at(ZonedDateTime time, double latitude, double longitude) {
        ZonedDateTime utc = time.withZoneSameInstant(ZoneId.of("UTC"));
        double dayOfYear = utc.getDayOfYear();
        double hours = utc.getHour() + utc.getMinute() / 60.0 + utc.getSecond() / 3600.0 + utc.getNano() / 3.6e12;
        double gamma = 2 * Math.PI / (utc.toLocalDate().isLeapYear() ? 366 : 365) * (dayOfYear - 1 + (hours - 12) / 24);

        double eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma) - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma) - 0.040849 * Math.sin(2 * gamma));
        double decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma)
                - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma);

        double trueSolarMinutes = hours * 60 + eqTime + 4 * longitude;
        double hourAngle = Math.toRadians(trueSolarMinutes / 4 - 180);
        double lat = Math.toRadians(latitude);

        double cosZenith = Math.sin(lat) * Math.sin(decl) + Math.cos(lat) * Math.cos(decl) * Math.cos(hourAngle);
        double zenith = Math.acos(Math.max(-1, Math.min(1, cosZenith)));
        double azimuth = Math.toDegrees(Math.atan2(Math.sin(hourAngle),
                Math.cos(hourAngle) * Math.sin(lat) - Math.tan(decl) * Math.cos(lat))) + 180;
        return new SunPosition(90 - Math.toDegrees(zenith), azimuth % 360);
    }

    /**
     * Unit vector from the scene towards the sun in jME coordinates (X east, Y up, Z south).
     */
    public Vector3f direction() {
        double el = Math.toRadians(elevation);
        double az = Math.toRadians(azimuth);
        float east = (float) (Math.cos(el) * Math.sin(az));
        float north = (float) (Math.cos(el) * Math.cos(az));
        return new Vector3f(east, (float) Math.sin(el), -north).normalizeLocal();
    }
}
