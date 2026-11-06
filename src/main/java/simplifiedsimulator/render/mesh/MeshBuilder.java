package simplifiedsimulator.render.mesh;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects triangles with normals, vertex colours and texture coordinates and turns them
 * into a jME {@link Mesh}. All procedural models (vehicles, trees, signal heads, street
 * furniture) are built with it, so that each model ends up as one or a few draw calls.
 *
 * <p>Shapes are added in the builder's current coordinate frame, which can be changed with
 * {@link #push(Matrix4f)} / {@link #pop()}. Hard-edged shapes (boxes, prisms) get separate
 * vertices per face so that colours and normals stay crisp.
 */
public final class MeshBuilder {

    private final FloatList positions = new FloatList();
    private final FloatList normals = new FloatList();
    private final FloatList colors = new FloatList();
    private final FloatList uvs = new FloatList();
    private final IntList indices = new IntList();
    private final List<Matrix4f> stack = new ArrayList<>();
    private Matrix4f transform = Matrix4f.IDENTITY.clone();
    private Matrix4f normalTransform = Matrix4f.IDENTITY.clone();
    private ColorRGBA color = ColorRGBA.White.clone();

    /** Sets the vertex colour used for the shapes added from now on. */
    public MeshBuilder color(ColorRGBA c) {
        color = c.clone();
        return this;
    }

    public ColorRGBA color() {
        return color.clone();
    }

    /** Multiplies the current frame with {@code m}; undo with {@link #pop()}. */
    public MeshBuilder push(Matrix4f m) {
        stack.add(transform);
        transform = transform.mult(m);
        normalTransform = transform.invert().transposeLocal();
        return this;
    }

    public MeshBuilder push(Vector3f translation) {
        Matrix4f m = new Matrix4f();
        m.setTranslation(translation);
        return push(m);
    }

    public MeshBuilder pop() {
        transform = stack.remove(stack.size() - 1);
        normalTransform = transform.invert().transposeLocal();
        return this;
    }

    public int vertexCount() {
        return positions.size() / 3;
    }

    public int triangleCount() {
        return indices.size() / 3;
    }

    public boolean isEmpty() {
        return indices.size() == 0;
    }

    /** Adds a vertex in the current frame and returns its index. */
    public int vertex(Vector3f p, Vector3f n, float u, float v) {
        Vector3f wp = transform.mult(p, null);
        Vector3f wn = normalTransform.multNormal(n, null).normalizeLocal();
        positions.add(wp.x, wp.y, wp.z);
        normals.add(wn.x, wn.y, wn.z);
        colors.add(color.r, color.g, color.b, color.a);
        uvs.add(u, v);
        return vertexCount() - 1;
    }

    public void triangle(int a, int b, int c) {
        indices.add(a, b, c);
    }

    /** Adds a flat triangle with counter-clockwise winding seen from the front. */
    public void triangle(Vector3f a, Vector3f b, Vector3f c) {
        Vector3f n = b.subtract(a).crossLocal(c.subtract(a)).normalizeLocal();
        int i = vertex(a, n, 0, 0);
        int j = vertex(b, n, 1, 0);
        int k = vertex(c, n, 1, 1);
        triangle(i, j, k);
    }

    /** Adds a flat triangle with explicit texture coordinates. */
    public void triangle(Vector3f a, Vector3f b, Vector3f c, Vector2f ta, Vector2f tb, Vector2f tc) {
        Vector3f n = b.subtract(a).crossLocal(c.subtract(a)).normalizeLocal();
        triangle(vertex(a, n, ta.x, ta.y), vertex(b, n, tb.x, tb.y), vertex(c, n, tc.x, tc.y));
    }

    /** Adds a flat quad a-b-c-d (counter-clockwise seen from the front). */
    public void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d) {
        Vector3f n = b.subtract(a).crossLocal(c.subtract(a));
        if (n.lengthSquared() < 1e-12f) {
            n = c.subtract(a).crossLocal(d.subtract(a));
        }
        n.normalizeLocal();
        int i = vertex(a, n, 0, 0);
        int j = vertex(b, n, 1, 0);
        int k = vertex(c, n, 1, 1);
        int l = vertex(d, n, 0, 1);
        triangle(i, j, k);
        triangle(i, k, l);
    }

    /** Axis-aligned box given by its centre and full size, in the current frame. */
    public void box(Vector3f center, Vector3f size) {
        float x = size.x / 2;
        float y = size.y / 2;
        float z = size.z / 2;
        Vector3f[] c = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            c[i] = new Vector3f(center.x + ((i & 1) == 0 ? -x : x), center.y + ((i & 2) == 0 ? -y : y),
                    center.z + ((i & 4) == 0 ? -z : z));
        }
        quad(c[4], c[5], c[7], c[6]); // +z
        quad(c[1], c[0], c[2], c[3]); // -z
        quad(c[5], c[1], c[3], c[7]); // +x
        quad(c[0], c[4], c[6], c[2]); // -x
        quad(c[2], c[6], c[7], c[3]); // +y
        quad(c[0], c[1], c[5], c[4]); // -y
    }

    /** Box between two corners. */
    public void boxBetween(Vector3f min, Vector3f max) {
        box(min.add(max).multLocal(0.5f), max.subtract(min));
    }

    /**
     * Smooth-shaded cylinder along the local Y axis.
     *
     * @param caps whether to close the ends
     */
    public void cylinder(Vector3f base, float radius, float height, int sides, boolean caps) {
        cone(base, radius, radius, height, sides, caps);
    }

    /** Truncated cone along the local Y axis from {@code base}. */
    public void cone(Vector3f base, float bottomRadius, float topRadius, float height, int sides, boolean caps) {
        int start = vertexCount();
        float slope = (bottomRadius - topRadius) / height;
        for (int i = 0; i <= sides; i++) {
            float a = FastMath.TWO_PI * i / sides;
            float cx = FastMath.cos(a);
            float cz = FastMath.sin(a);
            Vector3f n = new Vector3f(cx, slope, cz).normalizeLocal();
            vertex(base.add(cx * bottomRadius, 0, cz * bottomRadius), n, (float) i / sides, 0);
            vertex(base.add(cx * topRadius, height, cz * topRadius), n, (float) i / sides, 1);
        }
        for (int i = 0; i < sides; i++) {
            int b0 = start + 2 * i;
            triangle(b0, b0 + 1, b0 + 3);
            triangle(b0, b0 + 3, b0 + 2);
        }
        if (caps) {
            disc(base.add(0, height, 0), topRadius, sides, Vector3f.UNIT_Y);
            disc(base, bottomRadius, sides, Vector3f.UNIT_Y.negate());
        }
    }

    /** Flat disc facing {@code normal} (which must be +Y, -Y, +Z, -Z, +X or -X). */
    public void disc(Vector3f center, float radius, int sides, Vector3f normal) {
        Vector3f u = Math.abs(normal.y) > 0.5f ? Vector3f.UNIT_X : Vector3f.UNIT_Y;
        Vector3f v = normal.cross(u).normalizeLocal();
        u = v.cross(normal).normalizeLocal();
        int c = vertex(center, normal, 0.5f, 0.5f);
        int first = vertexCount();
        for (int i = 0; i <= sides; i++) {
            float a = FastMath.TWO_PI * i / sides;
            float cu = FastMath.cos(a);
            float sv = FastMath.sin(a);
            vertex(center.add(u.mult(cu * radius)).addLocal(v.mult(sv * radius)), normal, 0.5f + 0.5f * cu,
                    0.5f + 0.5f * sv);
        }
        for (int i = 0; i < sides; i++) {
            triangle(c, first + i, first + i + 1);
        }
    }

    /**
     * Torus around the local Z axis (a wheel lying in the XY plane).
     *
     * @param radius     distance from the centre to the middle of the tube
     * @param tube       tube radius
     */
    public void torus(Vector3f center, float radius, float tube, int segments, int sides) {
        int start = vertexCount();
        for (int i = 0; i <= segments; i++) {
            float a = FastMath.TWO_PI * i / segments;
            Vector3f dir = new Vector3f(FastMath.cos(a), FastMath.sin(a), 0);
            for (int j = 0; j <= sides; j++) {
                float b = FastMath.TWO_PI * j / sides;
                Vector3f n = dir.mult(FastMath.cos(b)).addLocal(0, 0, FastMath.sin(b));
                vertex(center.add(dir.mult(radius)).addLocal(n.mult(tube)), n, (float) i / segments,
                        (float) j / sides);
            }
        }
        int ring = sides + 1;
        for (int i = 0; i < segments; i++) {
            for (int j = 0; j < sides; j++) {
                int a = start + i * ring + j;
                int b = a + ring;
                triangle(a, b, b + 1);
                triangle(a, b + 1, a + 1);
            }
        }
    }

    /**
     * Smooth sphere (subdivided icosahedron), optionally deformed per vertex.
     *
     * @param deform maps a unit direction to a radius factor; {@code null} for a perfect sphere
     */
    public void sphere(Vector3f center, Vector3f radii, int subdivisions, java.util.function.ToDoubleFunction<Vector3f> deform) {
        Icosphere ico = Icosphere.get(subdivisions);
        int start = vertexCount();
        for (Vector3f dir : ico.vertices()) {
            float f = deform == null ? 1f : (float) deform.applyAsDouble(dir);
            Vector3f p = new Vector3f(dir.x * radii.x * f, dir.y * radii.y * f, dir.z * radii.z * f).addLocal(center);
            Vector3f n = new Vector3f(dir.x / radii.x, dir.y / radii.y, dir.z / radii.z).normalizeLocal();
            vertex(p, n, 0.5f + dir.x * 0.5f, 0.5f + dir.y * 0.5f);
        }
        int[] tris = ico.triangles();
        for (int i = 0; i < tris.length; i += 3) {
            triangle(start + tris[i], start + tris[i + 1], start + tris[i + 2]);
        }
    }

    /**
     * A closed, hard-edged solid built from a series of cross-sections (a "loft"). Every
     * section has the same number of points, given counter-clockwise seen from the front
     * (looking towards -X, i.e. from the end of the solid). Consecutive sections are joined
     * with quads; the first and last section are capped.
     *
     * @param faceColor chooses the colour of each side quad from its outward normal and the
     *                  section index, so that windows, pillars and paint can be coloured
     */
    public void loft(List<Vector3f[]> sections, FaceColor faceColor) {
        ColorRGBA saved = color;
        for (int s = 0; s + 1 < sections.size(); s++) {
            Vector3f[] a = sections.get(s);
            Vector3f[] b = sections.get(s + 1);
            for (int i = 0; i < a.length; i++) {
                int j = (i + 1) % a.length;
                Vector3f n = a[j].subtract(a[i]).crossLocal(b[i].subtract(a[i]));
                if (n.lengthSquared() < 1e-10f) {
                    n = b[j].subtract(b[i]).crossLocal(a[j].subtract(b[i]));
                }
                if (n.lengthSquared() < 1e-10f) {
                    continue;
                }
                n.normalizeLocal();
                color = faceColor == null ? saved : faceColor.color(n, s, i);
                quad(a[i], a[j], b[j], b[i]);
            }
        }
        color = faceColor == null ? saved : faceColor.color(Vector3f.UNIT_X.negate(), -1, -1);
        cap(sections.get(0), true);
        color = faceColor == null ? saved : faceColor.color(Vector3f.UNIT_X, sections.size(), -1);
        cap(sections.get(sections.size() - 1), false);
        color = saved;
    }

    private void cap(Vector3f[] ring, boolean reverse) {
        Vector3f c = new Vector3f();
        for (Vector3f p : ring) {
            c.addLocal(p);
        }
        c.divideLocal(ring.length);
        for (int i = 0; i < ring.length; i++) {
            Vector3f a = ring[i];
            Vector3f b = ring[(i + 1) % ring.length];
            if (reverse) {
                triangle(c, b, a);
            } else {
                triangle(c, a, b);
            }
        }
    }

    /** Chooses the colour of a face of a {@link #loft}. */
    @FunctionalInterface
    public interface FaceColor {
        /**
         * @param normal  outward normal of the face
         * @param section index of the first of the two sections it joins (-1 / size for the caps)
         * @param edge    index of the section edge (-1 for the caps)
         */
        ColorRGBA color(Vector3f normal, int section, int edge);
    }

    /** Appends another builder's geometry (already in its final coordinates). */
    public void append(MeshBuilder other) {
        int offset = vertexCount();
        positions.addAll(other.positions);
        normals.addAll(other.normals);
        colors.addAll(other.colors);
        uvs.addAll(other.uvs);
        for (int i = 0; i < other.indices.size(); i++) {
            indices.add(other.indices.get(i) + offset);
        }
    }

    public Mesh build() {
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions.toArray()));
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(normals.toArray()));
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colors.toArray()));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(uvs.toArray()));
        if (vertexCount() > 65535) {
            mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices.toArray()));
        } else {
            int[] idx = indices.toArray();
            short[] shorts = new short[idx.length];
            for (int i = 0; i < idx.length; i++) {
                shorts[i] = (short) idx[i];
            }
            mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createShortBuffer(shorts));
        }
        mesh.updateBound();
        mesh.setStatic();
        return mesh;
    }

    /** Growable float array. */
    static final class FloatList {
        private float[] data = new float[1024];
        private int size;

        void add(float... values) {
            ensure(size + values.length);
            System.arraycopy(values, 0, data, size, values.length);
            size += values.length;
        }

        void addAll(FloatList other) {
            ensure(size + other.size);
            System.arraycopy(other.data, 0, data, size, other.size);
            size += other.size;
        }

        int size() {
            return size;
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }

        private void ensure(int capacity) {
            if (capacity > data.length) {
                data = java.util.Arrays.copyOf(data, Math.max(capacity, data.length * 2));
            }
        }
    }

    /** Growable int array. */
    static final class IntList {
        private int[] data = new int[1024];
        private int size;

        void add(int... values) {
            if (size + values.length > data.length) {
                data = java.util.Arrays.copyOf(data, Math.max(size + values.length, data.length * 2));
            }
            System.arraycopy(values, 0, data, size, values.length);
            size += values.length;
        }

        int get(int i) {
            return data[i];
        }

        int size() {
            return size;
        }

        int[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }
}
