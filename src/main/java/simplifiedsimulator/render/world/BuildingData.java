package simplifiedsimulator.render.world;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Buildings around the crossing, read from {@code buildings.bin} (written by
 * {@code tools/crossing/fetch_buildings.py} from 3D BAG).
 */
public final class BuildingData {

    /** Kind of surface a triangle belongs to. */
    public enum Surface {
        ROOF, WALL
    }

    /**
     * One building.
     *
     * @param glass     glass roof or glass building (greenhouse, Crystallic)
     * @param height    roof height above the ground in metres
     * @param year      construction year, 0 when unknown
     * @param surfaces  surface kind per triangle
     * @param vertices  9 floats per triangle: three (x east, y north, z up) corners in metres
     */
    public record Building(boolean glass, float height, int year, Surface[] surfaces, float[] vertices) {

        public int triangleCount() {
            return surfaces.length;
        }

        /** Centre of the building's bounding box in the ground plane (x, y). */
        public float[] centre() {
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (int i = 0; i < vertices.length; i += 3) {
                minX = Math.min(minX, vertices[i]);
                maxX = Math.max(maxX, vertices[i]);
                minY = Math.min(minY, vertices[i + 1]);
                maxY = Math.max(maxY, vertices[i + 1]);
            }
            return new float[] {(minX + maxX) / 2, (minY + maxY) / 2};
        }
    }

    public static final String RESOURCE = "/crossing/buildings.bin";

    private BuildingData() {
    }

    public static List<Building> loadDefault() {
        try (InputStream in = BuildingData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing from the classpath");
            }
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Building> read(InputStream input) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(new DataInputStream(input).readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        buf.get(magic);
        if (!"BLD1".equals(new String(magic, StandardCharsets.US_ASCII))) {
            throw new IOException("Not a building file (expected magic BLD1)");
        }
        int count = buf.getInt();
        List<Building> out = new ArrayList<>(count);
        for (int b = 0; b < count; b++) {
            int flags = buf.getInt();
            float height = buf.getFloat();
            int year = buf.getInt();
            int triangles = buf.getInt();
            Surface[] surfaces = new Surface[triangles];
            float[] vertices = new float[triangles * 9];
            for (int t = 0; t < triangles; t++) {
                surfaces[t] = buf.get() == 0 ? Surface.ROOF : Surface.WALL;
                for (int k = 0; k < 9; k++) {
                    vertices[t * 9 + k] = buf.getFloat();
                }
            }
            out.add(new Building((flags & 1) != 0, height, year, surfaces, vertices));
        }
        return out;
    }
}
