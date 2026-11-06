package simplifiedsimulator.render.world;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import simplifiedsimulator.render.Materials;
import simplifiedsimulator.render.agents.Palette;
import simplifiedsimulator.render.mesh.MeshBuilder;

/**
 * Builds the trees found in the height model. Each tree is a tapered trunk with a crown
 * made of several lumpy, overlapping blobs; trees are merged per 100 m square so that the
 * whole forest costs a few dozen draw calls and distant squares can be culled.
 */
public final class TreeBuilder {

    private static final float CHUNK = 100f;

    private static final List<Palette.Weighted<ColorRGBA>> LEAVES = List.of(
            new Palette.Weighted<>(new ColorRGBA(0.20f, 0.33f, 0.13f, 1f), 0.35),
            new Palette.Weighted<>(new ColorRGBA(0.26f, 0.38f, 0.15f, 1f), 0.30),
            new Palette.Weighted<>(new ColorRGBA(0.17f, 0.28f, 0.14f, 1f), 0.20),
            new Palette.Weighted<>(new ColorRGBA(0.32f, 0.40f, 0.18f, 1f), 0.15));

    private final Materials materials;
    private final int detail;

    /**
     * @param detail icosphere subdivisions for the crown blobs (1 = low, 2 = normal, 3 = high)
     */
    public TreeBuilder(Materials materials, int detail) {
        this.materials = materials;
        this.detail = Math.max(1, Math.min(3, detail));
    }

    public Node build(List<TreeData.Tree> trees) {
        Map<Long, MeshBuilder> chunks = new HashMap<>();
        for (TreeData.Tree tree : trees) {
            long key = (long) Math.floor(tree.x() / CHUNK) * 100_003L + (long) Math.floor(tree.y() / CHUNK);
            addTree(chunks.computeIfAbsent(key, k -> new MeshBuilder()), tree);
        }
        Node node = new Node("trees");
        int i = 0;
        for (MeshBuilder mb : chunks.values()) {
            Geometry g = new Geometry("trees-" + i++, mb.build());
            g.setMaterial(materials.vertexColored("trees", 0.04f, 4f));
            g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            node.attachChild(g);
        }
        return node;
    }

    private void addTree(MeshBuilder mb, TreeData.Tree t) {
        int seed = Float.floatToIntBits(t.x() * 13.7f + t.y());
        float height = t.height();
        float crown = Math.min(t.crown(), height * 0.45f);
        boolean narrow = Palette.unit(seed, 1) < 0.15; // poplars and other columnar trees
        if (narrow) {
            crown *= 0.6f;
        }
        float crownBottom = Math.max(1.8f, height * (narrow ? 0.18f : 0.32f));
        float crownHalfHeight = (height - crownBottom) / 2;
        Vector3f base = new Vector3f(t.x(), 0, -t.y());

        // trunk
        float trunkRadius = Math.max(0.08f, height * 0.018f);
        mb.color(new ColorRGBA(0.27f, 0.22f, 0.18f, 1f));
        mb.cone(base, trunkRadius, trunkRadius * 0.55f, crownBottom + crownHalfHeight * 0.8f, 6, false);

        // crown: a central blob plus a few lumps around it
        ColorRGBA leaf = Palette.vary(Palette.pick(LEAVES, seed, 2), seed, 3, 0.12f);
        Vector3f centre = base.add(0, crownBottom + crownHalfHeight, 0);
        int lumps = narrow ? 2 : 3 + (int) (Palette.unit(seed, 4) * 3);
        mb.color(leaf.mult(0.85f));
        mb.sphere(centre, new Vector3f(crown * 0.85f, crownHalfHeight, crown * 0.85f), detail,
                d -> lumpy(d, seed));
        for (int i = 0; i < lumps; i++) {
            float a = FastMath.TWO_PI * (i + (float) Palette.unit(seed, 10 + i)) / lumps;
            float r = crown * (0.35f + 0.2f * (float) Palette.unit(seed, 20 + i));
            float up = crownHalfHeight * (0.1f + 0.5f * (float) Palette.unit(seed, 30 + i));
            Vector3f c = centre.add(FastMath.cos(a) * r, up, FastMath.sin(a) * r);
            float s = crown * (0.45f + 0.2f * (float) Palette.unit(seed, 40 + i));
            // upper lumps catch more light
            mb.color(Palette.vary(leaf, seed, 50 + i, 0.08f).mult(1.0f + 0.15f * up / crownHalfHeight));
            int lumpSeed = seed + i * 7;
            mb.sphere(c, new Vector3f(s, Math.min(s, crownHalfHeight * 0.7f), s), Math.max(1, detail - 1),
                    d -> lumpy(d, lumpSeed));
        }
    }

    /** Irregular radius factor so that crowns look leafy rather than round. */
    private static double lumpy(Vector3f d, int seed) {
        double phase = (seed & 0xff) * 0.1;
        return 1.0 + 0.13 * Math.sin(d.x * 5.1 + phase) * Math.cos(d.z * 4.3 - phase)
                + 0.08 * Math.sin(d.y * 7.7 + d.x * 3.1 + phase * 2) - 0.12 * Math.max(0, -d.y);
    }
}
