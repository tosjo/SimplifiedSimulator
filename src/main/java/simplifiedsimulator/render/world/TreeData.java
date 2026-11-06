package simplifiedsimulator.render.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Trees around the crossing, read from {@code trees.json} (found in the AHN height model by
 * {@code tools/crossing/fetch_trees.py}).
 */
public final class TreeData {

    /**
     * One tree.
     *
     * @param x      metres east of the crossing
     * @param y      metres north of the crossing
     * @param height top of the crown above the ground (m)
     * @param crown  crown radius (m)
     */
    public record Tree(float x, float y, float height, float crown) {
    }

    public static final String RESOURCE = "/crossing/trees.json";

    private TreeData() {
    }

    public static List<Tree> loadDefault() {
        try (InputStream in = TreeData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing from the classpath");
            }
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Tree> read(InputStream in) {
        JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        List<Tree> trees = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("trees")) {
            JsonObject t = el.getAsJsonObject();
            trees.add(new Tree(t.get("x").getAsFloat(), t.get("y").getAsFloat(), t.get("height").getAsFloat(),
                    t.get("crown").getAsFloat()));
        }
        return trees;
    }
}
