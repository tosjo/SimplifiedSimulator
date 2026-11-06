package simplifiedsimulator.network;

import java.util.List;
import java.util.Optional;

/** An immutable polyline parameterised by arc length. */
public final class Polyline {

    private final Vec2[] points;
    private final double[] cumulative;

    public Polyline(List<Vec2> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("A polyline needs at least two points");
        }
        this.points = points.toArray(Vec2[]::new);
        this.cumulative = new double[this.points.length];
        for (int i = 1; i < this.points.length; i++) {
            cumulative[i] = cumulative[i - 1] + this.points[i].distance(this.points[i - 1]);
        }
    }

    public double length() {
        return cumulative[cumulative.length - 1];
    }

    public List<Vec2> points() {
        return List.of(points);
    }

    public Vec2 start() {
        return points[0];
    }

    public Vec2 end() {
        return points[points.length - 1];
    }

    /** Point at arc length {@code s}; values outside the polyline are extrapolated along the end segments. */
    public Vec2 pointAt(double s) {
        int i = segment(s);
        Vec2 a = points[i];
        Vec2 b = points[i + 1];
        double seg = cumulative[i + 1] - cumulative[i];
        double t = seg < 1e-12 ? 0 : (s - cumulative[i]) / seg;
        return a.plus(b.minus(a).times(t));
    }

    /** Unit direction of travel at arc length {@code s}, smoothly blended between segments. */
    public Vec2 directionAt(double s) {
        int i = segment(s);
        Vec2 dir = points[i + 1].minus(points[i]).normalized();
        // blend with the neighbouring segment near the joints to avoid sudden turns
        double blend = 1.0;
        double toEnd = cumulative[i + 1] - s;
        if (toEnd < blend && i + 2 < points.length) {
            Vec2 next = points[i + 2].minus(points[i + 1]).normalized();
            double t = 0.5 * (1 - toEnd / blend);
            dir = dir.times(1 - t).plus(next.times(t)).normalized();
        }
        double fromStart = s - cumulative[i];
        if (fromStart < blend && i > 0) {
            Vec2 prev = points[i].minus(points[i - 1]).normalized();
            double t = 0.5 * (1 - fromStart / blend);
            dir = dir.times(1 - t).plus(prev.times(t)).normalized();
        }
        return dir;
    }

    /** Heading in radians, counter-clockwise from east. */
    public double headingAt(double s) {
        return directionAt(s).angle();
    }

    /** Signed curvature (1/m, positive turning left) estimated over a window of a few metres. */
    public double curvatureAt(double s) {
        double h = 3.0;
        double a = headingAt(s - h);
        double b = headingAt(s + h);
        double d = Math.atan2(Math.sin(b - a), Math.cos(b - a));
        return d / (2 * h);
    }

    /** Arc length of the point on this polyline closest to {@code q}. */
    public double project(Vec2 q) {
        double best = Double.MAX_VALUE;
        double bestS = 0;
        for (int i = 0; i < points.length - 1; i++) {
            Vec2 a = points[i];
            Vec2 ab = points[i + 1].minus(a);
            double len2 = ab.dot(ab);
            double t = len2 < 1e-12 ? 0 : Math.max(0, Math.min(1, q.minus(a).dot(ab) / len2));
            double d = a.plus(ab.times(t)).distance(q);
            if (d < best) {
                best = d;
                bestS = cumulative[i] + t * Math.sqrt(len2);
            }
        }
        return bestS;
    }

    /** Distance from {@code q} to the closest point on this polyline. */
    public double distanceTo(Vec2 q) {
        return pointAt(project(q)).distance(q);
    }

    /** A point where this polyline crosses another one, as arc lengths along both. */
    public record Crossing(double s, double otherS, Vec2 point) {
    }

    /** All points where this polyline crosses {@code other}. */
    public List<Crossing> crossings(Polyline other) {
        java.util.ArrayList<Crossing> out = new java.util.ArrayList<>();
        for (int i = 0; i < points.length - 1; i++) {
            for (int j = 0; j < other.points.length - 1; j++) {
                Optional<double[]> hit = intersect(points[i], points[i + 1], other.points[j], other.points[j + 1]);
                if (hit.isPresent()) {
                    double[] tu = hit.get();
                    double s = cumulative[i] + tu[0] * (cumulative[i + 1] - cumulative[i]);
                    double os = other.cumulative[j] + tu[1] * (other.cumulative[j + 1] - other.cumulative[j]);
                    out.add(new Crossing(s, os, pointAt(s)));
                }
            }
        }
        return out;
    }

    private static Optional<double[]> intersect(Vec2 a, Vec2 b, Vec2 c, Vec2 d) {
        Vec2 r = b.minus(a);
        Vec2 q = d.minus(c);
        double den = r.cross(q);
        if (Math.abs(den) < 1e-12) {
            return Optional.empty();
        }
        Vec2 ac = c.minus(a);
        double t = ac.cross(q) / den;
        double u = ac.cross(r) / den;
        if (t < 0 || t > 1 || u < 0 || u > 1) {
            return Optional.empty();
        }
        return Optional.of(new double[] {t, u});
    }

    private int segment(double s) {
        if (s <= 0) {
            return 0;
        }
        if (s >= length()) {
            return points.length - 2;
        }
        int lo = 0;
        int hi = cumulative.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] <= s) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
