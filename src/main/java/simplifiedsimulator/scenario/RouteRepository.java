package simplifiedsimulator.scenario;

import com.jme3.math.Vector3f;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and caches routes from the classpath. The route from entry point {@code a} to
 * exit point {@code b} is stored in {@code /routes/a-b.txt}.
 */
public final class RouteRepository {

    private static final Logger LOG = Logger.getLogger(RouteRepository.class.getName());

    private final String basePath;
    private final Map<String, Optional<List<Vector3f>>> cache = new HashMap<>();

    /** Creates a repository that reads from {@code /routes/} on the classpath. */
    public RouteRepository() {
        this("/routes/");
    }

    /**
     * Creates a repository that reads from a custom classpath directory.
     *
     * @param basePath absolute classpath directory, ending with a slash
     */
    public RouteRepository(String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath : basePath + "/";
    }

    /**
     * Returns the waypoints from {@code start} to {@code end}, or an empty optional if
     * no such route exists or the route file is invalid. Problems are logged once.
     */
    public Optional<List<Vector3f>> find(int start, int end) {
        return cache.computeIfAbsent(start + "-" + end, this::load);
    }

    private Optional<List<Vector3f>> load(String key) {
        String resource = basePath + key + ".txt";
        try (InputStream in = RouteRepository.class.getResourceAsStream(resource)) {
            if (in == null) {
                LOG.warning(() -> "No route " + key + " (" + resource + " not found)");
                return Optional.empty();
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(RouteParser.parse(text));
        } catch (IllegalArgumentException e) {
            LOG.log(Level.WARNING, "Invalid route file " + resource + ": " + e.getMessage());
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource, e);
        }
    }
}
