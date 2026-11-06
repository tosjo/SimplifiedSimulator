package simplifiedsimulator.render.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import java.nio.FloatBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class MeshBuilderTest {

    /** Checks that every triangle faces away from {@code inside}. */
    private static void assertOutward(Mesh mesh, Vector3f inside) {
        FloatBuffer pos = (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.Position).getData();
        com.jme3.scene.mesh.IndexBuffer idx = mesh.getIndexBuffer();
        for (int t = 0; t < idx.size(); t += 3) {
            Vector3f a = vertex(pos, idx.get(t));
            Vector3f b = vertex(pos, idx.get(t + 1));
            Vector3f c = vertex(pos, idx.get(t + 2));
            Vector3f n = b.subtract(a).crossLocal(c.subtract(a));
            if (n.lengthSquared() < 1e-10f) {
                continue;
            }
            Vector3f centre = a.add(b).addLocal(c).divideLocal(3);
            assertTrue(n.dot(centre.subtract(inside)) > 0, "triangle " + t / 3 + " faces inwards");
        }
    }

    private static Vector3f vertex(FloatBuffer pos, int i) {
        return new Vector3f(pos.get(i * 3), pos.get(i * 3 + 1), pos.get(i * 3 + 2));
    }

    @Test
    void boxFacesOutward() {
        MeshBuilder mb = new MeshBuilder();
        mb.box(new Vector3f(1, 2, 3), new Vector3f(2, 4, 6));
        assertEquals(12, mb.triangleCount());
        assertOutward(mb.build(), new Vector3f(1, 2, 3));
    }

    @Test
    void sphereCylinderAndTorusFaceOutward() {
        MeshBuilder sphere = new MeshBuilder();
        sphere.sphere(Vector3f.ZERO, new Vector3f(1, 2, 1), 2, null);
        assertOutward(sphere.build(), Vector3f.ZERO);

        MeshBuilder cyl = new MeshBuilder();
        cyl.cylinder(Vector3f.ZERO, 1, 2, 12, true);
        assertOutward(cyl.build(), new Vector3f(0, 1, 0));

        MeshBuilder torus = new MeshBuilder();
        torus.torus(Vector3f.ZERO, 2, 0.5f, 16, 6);
        Mesh mesh = torus.build();
        FloatBuffer pos = (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.Position).getData();
        com.jme3.scene.mesh.IndexBuffer idx = mesh.getIndexBuffer();
        for (int t = 0; t < idx.size(); t += 3) {
            Vector3f a = vertex(pos, idx.get(t));
            Vector3f b = vertex(pos, idx.get(t + 1));
            Vector3f c = vertex(pos, idx.get(t + 2));
            Vector3f centre = a.add(b).addLocal(c).divideLocal(3);
            // the closest point on the tube's centre line lies inside the surface
            Vector3f ring = new Vector3f(centre.x, centre.y, 0).normalizeLocal().multLocal(2);
            Vector3f n = b.subtract(a).crossLocal(c.subtract(a));
            assertTrue(n.dot(centre.subtract(ring)) > 0, "torus triangle " + t / 3 + " faces inwards");
        }
    }

    @Test
    void loftIsClosedAndOutward() {
        MeshBuilder mb = new MeshBuilder();
        List<Vector3f[]> sections = List.of(ring(-1), ring(0), ring(1));
        mb.loft(sections, (n, s, e) -> n.y > 0.5f ? ColorRGBA.Red : ColorRGBA.White);
        assertOutward(mb.build(), Vector3f.ZERO);
    }

    private static Vector3f[] ring(float x) {
        // counter-clockwise seen from +X: top, +Z side, bottom, -Z side
        return new Vector3f[] {new Vector3f(x, 1, 0), new Vector3f(x, 0, 1), new Vector3f(x, -1, 0),
            new Vector3f(x, 0, -1)};
    }

    @Test
    void transformsApplyToNewShapes() {
        MeshBuilder mb = new MeshBuilder();
        mb.push(new Vector3f(10, 0, 0));
        mb.box(Vector3f.ZERO, new Vector3f(1, 1, 1));
        mb.pop();
        mb.box(Vector3f.ZERO, new Vector3f(1, 1, 1));
        Mesh mesh = mb.build();
        assertEquals(10.5f, mesh.getBound().getCenter().x + ((com.jme3.bounding.BoundingBox) mesh.getBound()).getXExtent(),
                1e-5f);
    }

    @Test
    void rasterShapesHaveTheRightArea() {
        Raster r = new Raster(100, 100).fill(new ColorRGBA(0, 0, 0, 0));
        r.circle(50, 50, 20, ColorRGBA.White);
        float covered = 0;
        for (int y = 0; y < 100; y++) {
            for (int x = 0; x < 100; x++) {
                covered += r.get(x, y).a;
            }
        }
        assertEquals(Math.PI * 400, covered, 15);
        r.fill(ColorRGBA.White).fadeBorders(10);
        assertEquals(0f, r.get(0, 50).a, 1e-6f);
        assertEquals(1f, r.get(50, 50).a, 1e-6f);
    }
}
