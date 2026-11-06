package simplifiedsimulator.render.world;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import simplifiedsimulator.render.Materials;
import simplifiedsimulator.render.agents.Palette;
import simplifiedsimulator.render.mesh.MeshBuilder;

/**
 * Turns the 3D BAG buildings into a handful of batched meshes: one per wall style, one per
 * roof style and one for glass. Wall triangles get texture coordinates in metres along the
 * wall and up from the ground, so that windows line up storey by storey. At night about a
 * third of the windows light up.
 */
public final class BuildingBuilder {

    /** The built buildings and their lit windows. */
    public static final class Buildings {
        private final Node node;
        private final Geometry litWindows;
        private final Material windowMaterial;
        private float lastNight = -1;

        Buildings(Node node, Geometry litWindows, Material windowMaterial) {
            this.node = node;
            this.litWindows = litWindows;
            this.windowMaterial = windowMaterial;
        }

        public Node node() {
            return node;
        }

        /** @param night 0 by day, 1 when fully dark */
        public void setNight(float night) {
            if (litWindows == null || Math.abs(night - lastNight) < 0.01f) {
                return;
            }
            lastNight = night;
            litWindows.setCullHint(night > 0.05f ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            ColorRGBA warm = new ColorRGBA(1.0f, 0.78f, 0.48f, 1f);
            windowMaterial.setColor("Color", warm.mult(0.25f + 0.75f * night));
            windowMaterial.setColor("GlowColor", warm.mult(0.6f * night));
        }
    }

    /** Share of windows with the lights on at night. */
    private static final double LIT_SHARE = 0.32;

    /** Roofs steeper than this (degrees from horizontal) get tiles. */
    private static final float PITCHED = 12f;

    private final Materials materials;
    private final FacadeTextures textures;
    private final Texture environment;

    public BuildingBuilder(Materials materials, FacadeTextures textures, Texture environment) {
        this.materials = materials;
        this.textures = textures;
        this.environment = environment;
    }

    public Buildings build(List<BuildingData.Building> buildings) {
        Map<BuildingStyle.Facade, MeshBuilder> walls = new EnumMap<>(BuildingStyle.Facade.class);
        Map<BuildingStyle.Roof, MeshBuilder> roofs = new EnumMap<>(BuildingStyle.Roof.class);
        MeshBuilder glass = new MeshBuilder();
        MeshBuilder windows = new MeshBuilder();

        for (BuildingData.Building b : buildings) {
            float[] c = b.centre();
            int seed = Float.floatToIntBits(c[0] * 31 + c[1]);
            BuildingStyle.Facade facade = BuildingStyle.facade(b.height(), footprint(b), b.year(), seed);
            BuildingStyle.Roof tiles = BuildingStyle.tiles(b.year(), seed);
            // subtle per-building tint so that neighbours are not identical
            ColorRGBA tint = Palette.vary(ColorRGBA.White, seed, 3, 0.08f);
            for (int t = 0; t < b.triangleCount(); t++) {
                Vector3f p0 = vertex(b, t, 0);
                Vector3f p1 = vertex(b, t, 1);
                Vector3f p2 = vertex(b, t, 2);
                Vector3f normal = p1.subtract(p0).crossLocal(p2.subtract(p0));
                if (normal.lengthSquared() < 1e-8f) {
                    continue;
                }
                normal.normalizeLocal();
                if (b.glass()) {
                    addWall(glass.color(ColorRGBA.White), p0, p1, p2, normal);
                } else if (b.surfaces()[t] == BuildingData.Surface.WALL || Math.abs(normal.y) < 0.2f) {
                    addWall(walls.computeIfAbsent(facade, f -> new MeshBuilder()).color(tint), p0, p1, p2, normal);
                    addLitWindows(windows, facade, p0, p1, p2, normal, seed + t);
                } else {
                    float slope = (float) Math.toDegrees(Math.acos(Math.min(1, Math.abs(normal.y))));
                    BuildingStyle.Roof roof = slope > PITCHED ? tiles : BuildingStyle.Roof.FLAT;
                    addRoof(roofs.computeIfAbsent(roof, r -> new MeshBuilder()).color(tint), p0, p1, p2, normal);
                }
            }
        }

        Node node = new Node("buildings");
        walls.forEach((facade, mb) -> {
            Texture[] tex = textures.wall(facade);
            Material m = materials.textured("wall-" + facade, tex[0], tex[1], 64f);
            node.attachChild(geometry("walls-" + facade, mb, m));
        });
        roofs.forEach((roof, mb) -> {
            Material m = materials.textured("roof-" + roof, textures.roof(roof), null, 8f);
            node.attachChild(geometry("roof-" + roof, mb, m));
        });
        if (!glass.isEmpty()) {
            Texture[] tex = textures.glass();
            Material m = materials.glass("buildings", tex[0], tex[1], environment);
            node.attachChild(geometry("glass", glass, m));
        }
        Geometry lit = null;
        Material windowMaterial = materials.glow(new ColorRGBA(1.0f, 0.78f, 0.48f, 1f), 0.6f).clone();
        if (!windows.isEmpty()) {
            lit = new Geometry("lit windows", windows.build());
            lit.setMaterial(windowMaterial);
            lit.setShadowMode(RenderQueue.ShadowMode.Off);
            lit.setCullHint(Spatial.CullHint.Always);
            node.attachChild(lit);
        }
        return new Buildings(node, lit, windowMaterial);
    }

    /**
     * Adds a glowing pane in front of some of the windows of a wall triangle, at the same
     * positions as the windows of the facade texture.
     */
    private static void addLitWindows(MeshBuilder mb, BuildingStyle.Facade facade, Vector3f a, Vector3f b, Vector3f c,
                                      Vector3f normal, int seed) {
        float width;
        float bottom;
        float top;
        switch (facade) {
            case OFFICE -> {
                width = 0.92f;
                bottom = 0.08f;
                top = 0.72f;
            }
            case PLASTER, OLD_BRICK, RED_BRICK, PALE_BRICK, DARK_BRICK -> {
                width = 0.46f;
                bottom = 0.20f;
                top = 0.70f;
            }
            default -> {
                return;
            }
        }
        Vector3f along = Vector3f.UNIT_Y.cross(normal);
        if (along.lengthSquared() < 1e-6f) {
            return;
        }
        along.normalizeLocal();
        float bay = FacadeTextures.BAY;
        float storey = FacadeTextures.STOREY;
        float u0 = Math.min(a.dot(along), Math.min(b.dot(along), c.dot(along)));
        float u1 = Math.max(a.dot(along), Math.max(b.dot(along), c.dot(along)));
        float v1 = Math.max(a.y, Math.max(b.y, c.y));
        Vector3f offset = normal.mult(0.04f);
        for (int k = (int) Math.floor(u0 / bay); k * bay < u1; k++) {
            for (int m = 0; (m + 1) * storey <= v1 + 0.01f; m++) {
                if (Palette.unit(seed * 31 + k, m) > LIT_SHARE) {
                    continue;
                }
                float cu = (k + 0.5f) * bay;
                float halfW = width * bay / 2;
                float y0 = (m + bottom) * storey;
                float y1 = (m + top) * storey;
                Vector3f centre = pointOnWall(a, along, cu, (y0 + y1) / 2);
                if (!inside(centre, a, b, c, normal, halfW)) {
                    continue;
                }
                Vector3f left = pointOnWall(a, along, cu - halfW, y0).addLocal(offset);
                Vector3f right = pointOnWall(a, along, cu + halfW, y0).addLocal(offset);
                Vector3f rightTop = pointOnWall(a, along, cu + halfW, y1).addLocal(offset);
                Vector3f leftTop = pointOnWall(a, along, cu - halfW, y1).addLocal(offset);
                mb.color(Palette.vary(ColorRGBA.White, seed + k, m, 0.25f));
                mb.quad(left, right, rightTop, leftTop);
            }
        }
    }

    /** Point in the plane of a wall at horizontal coordinate {@code u} and height {@code y}. */
    private static Vector3f pointOnWall(Vector3f anchor, Vector3f along, float u, float y) {
        Vector3f p = anchor.add(along.mult(u - anchor.dot(along)));
        p.y = y;
        return p;
    }

    /** True when a window of half width {@code margin} centred on {@code p} fits in the triangle. */
    private static boolean inside(Vector3f p, Vector3f a, Vector3f b, Vector3f c, Vector3f n, float margin) {
        Vector3f[] tri = {a, b, c};
        for (int i = 0; i < 3; i++) {
            Vector3f e0 = tri[i];
            Vector3f e1 = tri[(i + 1) % 3];
            Vector3f edgeNormal = e1.subtract(e0).crossLocal(n).normalizeLocal();
            // edgeNormal points out of the triangle for counter-clockwise triangles
            if (p.subtract(e0).dot(edgeNormal) > -margin) {
                return false;
            }
        }
        return true;
    }

    private static Geometry geometry(String name, MeshBuilder mb, Material m) {
        Geometry g = new Geometry(name, mb.build());
        g.setMaterial(m);
        g.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        return g;
    }

    /** Converts a building vertex (x east, y north, z up) to jME coordinates. */
    private static Vector3f vertex(BuildingData.Building b, int triangle, int corner) {
        float[] v = b.vertices();
        int i = triangle * 9 + corner * 3;
        return new Vector3f(v[i], v[i + 2], -v[i + 1]);
    }

    private static void addWall(MeshBuilder mb, Vector3f a, Vector3f b, Vector3f c, Vector3f normal) {
        Vector3f along = Vector3f.UNIT_Y.cross(normal);
        if (along.lengthSquared() < 1e-6f) {
            along = Vector3f.UNIT_X.clone();
        }
        along.normalizeLocal();
        mb.triangle(a, b, c, wallUv(a, along), wallUv(b, along), wallUv(c, along));
    }

    private static Vector2f wallUv(Vector3f p, Vector3f along) {
        return new Vector2f(p.dot(along) / FacadeTextures.BAY, p.y / FacadeTextures.STOREY);
    }

    private static void addRoof(MeshBuilder mb, Vector3f a, Vector3f b, Vector3f c, Vector3f normal) {
        Vector3f across = Vector3f.UNIT_Y.cross(normal);
        if (across.lengthSquared() < 1e-6f) {
            across = Vector3f.UNIT_X.clone();
        }
        across.normalizeLocal();
        Vector3f up = normal.cross(across).normalizeLocal();
        float s = FacadeTextures.ROOF_TILE;
        mb.triangle(a, b, c, new Vector2f(a.dot(across) / s, a.dot(up) / s), new Vector2f(b.dot(across) / s, b.dot(up) / s),
                new Vector2f(c.dot(across) / s, c.dot(up) / s));
    }

    /** Ground area covered by the roof, in square metres. */
    static float footprint(BuildingData.Building b) {
        float area = 0;
        float[] v = b.vertices();
        for (int t = 0; t < b.triangleCount(); t++) {
            if (b.surfaces()[t] != BuildingData.Surface.ROOF) {
                continue;
            }
            int i = t * 9;
            float ax = v[i + 3] - v[i];
            float ay = v[i + 4] - v[i + 1];
            float bx = v[i + 6] - v[i];
            float by = v[i + 7] - v[i + 1];
            area += Math.abs(ax * by - ay * bx) / 2;
        }
        return area;
    }
}
