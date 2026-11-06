package simplifiedsimulator.render.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildingDataTest {

    @Test
    void readsTheDocumentedFormat() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4 + 4 + 16 + 2 * 37).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("BLD1".getBytes(StandardCharsets.US_ASCII)).putInt(1);
        buf.putInt(1).putFloat(12.5f).putInt(1998).putInt(2);
        buf.put((byte) 0);
        for (float f : new float[] {0, 0, 10, 4, 0, 10, 4, 3, 10}) {
            buf.putFloat(f);
        }
        buf.put((byte) 1);
        for (float f : new float[] {0, 0, 0, 4, 0, 0, 4, 0, 10}) {
            buf.putFloat(f);
        }
        List<BuildingData.Building> buildings = BuildingData.read(new ByteArrayInputStream(buf.array()));
        assertEquals(1, buildings.size());
        BuildingData.Building b = buildings.get(0);
        assertTrue(b.glass());
        assertEquals(12.5f, b.height());
        assertEquals(1998, b.year());
        assertEquals(BuildingData.Surface.ROOF, b.surfaces()[0]);
        assertEquals(BuildingData.Surface.WALL, b.surfaces()[1]);
        assertEquals(6f, BuildingBuilder.footprint(b), 1e-4f);
        assertEquals(2f, b.centre()[0], 1e-6f);
    }

    @Test
    void rejectsOtherFiles() {
        assertThrows(IOException.class, () -> BuildingData.read(new ByteArrayInputStream("NOPE1234".getBytes())));
    }

    @Test
    void bundledBuildingsIncludeTheGlassHall() {
        List<BuildingData.Building> buildings = BuildingData.loadDefault();
        assertTrue(buildings.size() > 300);
        assertTrue(buildings.stream().anyMatch(b -> b.glass() && b.height() > 25), "Crystallic");
    }

    @Test
    void bundledTreesHavePlausibleSizes() {
        List<TreeData.Tree> trees = TreeData.loadDefault();
        assertTrue(trees.size() > 300);
        for (TreeData.Tree t : trees) {
            assertTrue(t.height() >= 3 && t.height() <= 35, "height " + t.height());
            assertTrue(t.crown() > 0 && t.crown() < t.height(), "crown " + t.crown());
        }
    }
}
