package simplifiedsimulator.demand;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.Route;

/**
 * How many road users want to use each route: a peak-hour volume per route
 * ({@code demand.csv}) multiplied by the share of the peak for the time of day
 * ({@code profile.csv}).
 */
public final class DemandModel {

    private final Map<String, Double> peakPerHour;
    private final DemandProfile profile;

    public DemandModel(Map<String, Double> peakPerHour, DemandProfile profile) {
        this.peakPerHour = Collections.unmodifiableMap(new LinkedHashMap<>(peakPerHour));
        this.profile = profile;
    }

    /** The bundled estimates for the Harlingerstraatweg crossing. */
    public static DemandModel loadDefault() {
        try (InputStream in = DemandModel.class.getResourceAsStream("/crossing/demand.csv")) {
            if (in == null) {
                throw new IllegalStateException("/crossing/demand.csv is missing from the classpath");
            }
            return new DemandModel(readVolumes(in), DemandProfile.loadDefault());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads peak-hour volumes from a CSV file, with the default daily profile. */
    public static DemandModel load(Path csv) throws IOException {
        try (InputStream in = Files.newInputStream(csv)) {
            return new DemandModel(readVolumes(in), DemandProfile.loadDefault());
        }
    }

    /** Parses lines of the form {@code route,perHour[,description]}; '#' starts a comment line. */
    public static Map<String, Double> readVolumes(InputStream in) throws IOException {
        Map<String, Double> volumes = new LinkedHashMap<>();
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
                String[] f = line.split(",", 3);
                if (f.length < 2) {
                    throw new IllegalArgumentException("Bad demand line: " + line);
                }
                double value = Double.parseDouble(f[1].strip());
                if (value < 0) {
                    throw new IllegalArgumentException("Negative demand for " + f[0] + ": " + value);
                }
                volumes.put(f[0].strip(), value);
            }
        }
        return volumes;
    }

    /** Checks that every route named in the demand exists in the network. */
    public void validate(Network network) {
        for (String id : peakPerHour.keySet()) {
            network.route(id);
        }
    }

    /** Road users per hour on {@code route} in the peak hour. */
    public double peakPerHour(Route route) {
        return peakPerHour.getOrDefault(route.id(), 0.0);
    }

    /** Road users per second on {@code route} at a time of day (seconds after midnight). */
    public double rate(Route route, double timeOfDay) {
        return peakPerHour(route) / 3600.0 * profile.factor(route.primaryMode(), timeOfDay);
    }

    public DemandProfile profile() {
        return profile;
    }
}
