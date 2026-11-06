package simplifiedsimulator.demand;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import simplifiedsimulator.network.Mode;

/** How busy each hour of the day is compared with the peak hour, per kind of traffic. */
public final class DemandProfile {

    private final double[] motor = new double[24];
    private final double[] bike = new double[24];
    private final double[] foot = new double[24];

    private DemandProfile() {
    }

    /** A profile that is the same all day (useful for tests and experiments). */
    public static DemandProfile flat(double factor) {
        DemandProfile p = new DemandProfile();
        java.util.Arrays.fill(p.motor, factor);
        java.util.Arrays.fill(p.bike, factor);
        java.util.Arrays.fill(p.foot, factor);
        return p;
    }

    public static DemandProfile loadDefault() {
        try (InputStream in = DemandProfile.class.getResourceAsStream("/crossing/profile.csv")) {
            if (in == null) {
                throw new IllegalStateException("/crossing/profile.csv is missing from the classpath");
            }
            return load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads a CSV with columns hour, motor, bike, foot; lines starting with '#' are comments. */
    public static DemandProfile load(InputStream in) throws IOException {
        DemandProfile p = new DemandProfile();
        boolean[] seen = new boolean[24];
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = r.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                String[] f = line.split(",");
                int hour = Integer.parseInt(f[0].strip());
                if (hour < 0 || hour > 23 || f.length < 4) {
                    throw new IllegalArgumentException("Bad profile line: " + line);
                }
                p.motor[hour] = Double.parseDouble(f[1].strip());
                p.bike[hour] = Double.parseDouble(f[2].strip());
                p.foot[hour] = Double.parseDouble(f[3].strip());
                seen[hour] = true;
            }
        }
        for (int h = 0; h < 24; h++) {
            if (!seen[h]) {
                throw new IllegalArgumentException("Profile has no value for hour " + h);
            }
        }
        return p;
    }

    /**
     * Share of the peak-hour demand at a time of day (seconds after midnight), interpolated
     * between the middles of the hours.
     */
    public double factor(Mode mode, double timeOfDay) {
        double[] values = switch (mode) {
            case CAR, BUS -> motor;
            case BIKE -> bike;
            case FOOT -> foot;
        };
        double h = ((timeOfDay / 3600.0) % 24 + 24) % 24 - 0.5;
        int i = (int) Math.floor(h);
        double t = h - i;
        double a = values[(i + 24) % 24];
        double b = values[(i + 1) % 24];
        return a + (b - a) * t;
    }
}
