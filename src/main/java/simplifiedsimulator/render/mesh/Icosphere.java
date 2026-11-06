package simplifiedsimulator.render.mesh;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit icospheres, cached per subdivision level. */
final class Icosphere {

    private static final Map<Integer, Icosphere> CACHE = new HashMap<>();

    private final List<Vector3f> vertices;
    private final int[] triangles;

    private Icosphere(List<Vector3f> vertices, int[] triangles) {
        this.vertices = vertices;
        this.triangles = triangles;
    }

    static synchronized Icosphere get(int subdivisions) {
        return CACHE.computeIfAbsent(subdivisions, Icosphere::create);
    }

    List<Vector3f> vertices() {
        return vertices;
    }

    int[] triangles() {
        return triangles;
    }

    private static Icosphere create(int subdivisions) {
        float t = (1f + (float) Math.sqrt(5)) / 2f;
        List<Vector3f> v = new ArrayList<>();
        float[][] base = {{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t},
            {0, 1, -t}, {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
        for (float[] p : base) {
            v.add(new Vector3f(p[0], p[1], p[2]).normalizeLocal());
        }
        int[] faces = {0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1};
        for (int s = 0; s < subdivisions; s++) {
            Map<Long, Integer> mid = new HashMap<>();
            int[] next = new int[faces.length * 4];
            int n = 0;
            for (int i = 0; i < faces.length; i += 3) {
                int a = faces[i];
                int b = faces[i + 1];
                int c = faces[i + 2];
                int ab = midpoint(v, mid, a, b);
                int bc = midpoint(v, mid, b, c);
                int ca = midpoint(v, mid, c, a);
                int[] quad = {a, ab, ca, b, bc, ab, c, ca, bc, ab, bc, ca};
                System.arraycopy(quad, 0, next, n, quad.length);
                n += quad.length;
            }
            faces = next;
        }
        return new Icosphere(List.copyOf(v), faces);
    }

    private static int midpoint(List<Vector3f> v, Map<Long, Integer> cache, int a, int b) {
        long key = a < b ? ((long) a << 32) | b : ((long) b << 32) | a;
        Integer existing = cache.get(key);
        if (existing != null) {
            return existing;
        }
        v.add(v.get(a).add(v.get(b)).normalizeLocal());
        cache.put(key, v.size() - 1);
        return v.size() - 1;
    }
}
