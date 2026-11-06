package simplifiedsimulator.sim;

import simplifiedsimulator.network.Route;

/**
 * A place where two routes cross or merge.
 *
 * @param route          the route this point belongs to
 * @param s              position along {@code route}
 * @param halfLength     half the length of the zone around the point that a road user on
 *                       {@code route} occupies while crossing the other route
 * @param other          the other route
 * @param otherS         position along {@code other}
 * @param otherHalfLength half the zone length along {@code other}
 * @param kind           crossing or merge
 */
public record ConflictPoint(Route route, double s, double halfLength, Route other, double otherS,
                            double otherHalfLength, Kind kind) {

    public enum Kind {
        /** The paths cross each other. */
        CROSSING,
        /** The paths join into the same lane. */
        MERGE
    }

    public double zoneStart() {
        return s - halfLength;
    }

    public double zoneEnd() {
        return s + halfLength;
    }

    public double otherZoneStart() {
        return otherS - otherHalfLength;
    }

    public double otherZoneEnd() {
        return otherS + otherHalfLength;
    }
}
