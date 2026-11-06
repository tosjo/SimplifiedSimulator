package simplifiedsimulator.render.world;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture2D;
import java.util.ArrayList;
import java.util.List;
import simplifiedsimulator.network.Lane;
import simplifiedsimulator.network.Mode;
import simplifiedsimulator.network.Network;
import simplifiedsimulator.network.Vec2;
import simplifiedsimulator.render.Coords;
import simplifiedsimulator.render.Materials;
import simplifiedsimulator.render.mesh.MeshBuilder;
import simplifiedsimulator.render.mesh.Raster;

/**
 * Street lights along the carriageways: grey masts with a flat LED luminaire on a short
 * arm, every 30-35 metres along the outer edge of the road. At night the luminaires glow
 * and throw a pool of warm light on the road.
 */
public final class StreetLights {

    private static final float SPACING = 32f;
    private static final float HEIGHT = 9f;
    private static final float MAX_DISTANCE = 260f;

    /** Where a light stands and which way its arm points (towards the road). */
    public record Placement(Vec2 position, Vec2 towardsRoad) {
    }

    private final Node node = new Node("street lights");
    private final Geometry lenses;
    private final Geometry pools;
    private final Material lensOff;
    private final Material lensOn;
    private final Material poolMaterial;
    private float lastNight = -1;

    public StreetLights(Materials materials, Network network) {
        List<Placement> placements = place(network);
        MeshBuilder masts = new MeshBuilder();
        MeshBuilder lensMesh = new MeshBuilder();
        MeshBuilder poolMesh = new MeshBuilder();
        for (Placement p : placements) {
            build(masts, lensMesh, poolMesh, p);
        }
        Geometry mastGeom = new Geometry("street light masts", masts.build());
        mastGeom.setMaterial(materials.vertexColored("street-light", 0.3f, 24f));
        mastGeom.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        node.attachChild(mastGeom);

        lensOff = materials.glow(new ColorRGBA(0.75f, 0.75f, 0.72f, 1f), 0f);
        lensOn = materials.glow(new ColorRGBA(1.0f, 0.88f, 0.70f, 1f), 1.4f);
        lenses = new Geometry("street light lenses", lensMesh.build());
        lenses.setMaterial(lensOff);
        node.attachChild(lenses);

        Texture2D poolTex = new Raster(128, 128).fill(new ColorRGBA(0, 0, 0, 0))
                .radialGlow(64, 64, 63, ColorRGBA.White, 1.3f).toTexture(false);
        poolMaterial = materials.lightPool("street", poolTex, new ColorRGBA(0, 0, 0, 0));
        pools = new Geometry("street light pools", poolMesh.build());
        pools.setMaterial(poolMaterial);
        pools.setQueueBucket(RenderQueue.Bucket.Transparent);
        pools.setShadowMode(RenderQueue.ShadowMode.Off);
        node.attachChild(pools);
    }

    public Node node() {
        return node;
    }

    /** @param night 0 by day, 1 when fully dark */
    public void setNight(float night) {
        if (Math.abs(night - lastNight) < 0.01f) {
            return;
        }
        lastNight = night;
        boolean on = night > 0.3f;
        lenses.setMaterial(on ? lensOn : lensOff);
        poolMaterial.setColor("Color", new ColorRGBA(0.75f, 0.62f, 0.42f, 1f).mult(night * 0.7f));
        pools.setCullHint(night > 0.05f ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }

    /** Finds positions along the outer edges of the main carriageways. */
    static List<Placement> place(Network network) {
        List<Lane> road = new ArrayList<>();
        for (Lane lane : network.lanes()) {
            if (lane.mode() == Mode.CAR || lane.mode() == Mode.BUS) {
                road.add(lane);
            }
        }
        List<Placement> out = new ArrayList<>();
        for (Lane lane : road) {
            if (lane.length() < 40) {
                continue;
            }
            for (double s = SPACING / 2; s < lane.length(); s += SPACING) {
                Vec2 p = lane.path().pointAt(s);
                if (p.length() > MAX_DISTANCE) {
                    continue;
                }
                Vec2 right = lane.path().directionAt(s).rightNormal();
                Vec2 candidate = p.plus(right.times(lane.width() / 2 + 1.6));
                if (clearOfRoad(candidate, road, 1.2) && farFromOthers(candidate, out, SPACING * 0.6)) {
                    out.add(new Placement(candidate, right.times(-1)));
                }
            }
        }
        return out;
    }

    private static boolean clearOfRoad(Vec2 p, List<Lane> road, double margin) {
        for (Lane lane : road) {
            if (lane.path().distanceTo(p) < lane.width() / 2 + margin) {
                return false;
            }
        }
        return true;
    }

    private static boolean farFromOthers(Vec2 p, List<Placement> placed, double distance) {
        for (Placement other : placed) {
            if (other.position.distance(p) < distance) {
                return false;
            }
        }
        return true;
    }

    private static void build(MeshBuilder masts, MeshBuilder lenses, MeshBuilder pools, Placement p) {
        Vector3f base = Coords.world(p.position(), 0);
        masts.color(new ColorRGBA(0.50f, 0.52f, 0.53f, 1f));
        masts.cone(base, 0.09f, 0.055f, HEIGHT, 10, true);

        Matrix4f m = new Matrix4f();
        float angle = FastMath.atan2((float) p.towardsRoad().y(), (float) p.towardsRoad().x());
        m.setRotationQuaternion(new Quaternion().fromAngleAxis(angle, Vector3f.UNIT_Y));
        m.setTranslation(base.add(0, HEIGHT, 0));
        masts.push(m);
        lenses.push(m);
        masts.boxBetween(new Vector3f(0, -0.04f, -0.035f), new Vector3f(1.3f, 0.04f, 0.035f));
        masts.color(new ColorRGBA(0.32f, 0.33f, 0.34f, 1f));
        masts.boxBetween(new Vector3f(1.1f, -0.06f, -0.17f), new Vector3f(1.75f, 0.06f, 0.17f));
        lenses.boxBetween(new Vector3f(1.15f, -0.075f, -0.13f), new Vector3f(1.7f, -0.055f, 0.13f));
        masts.pop();
        lenses.pop();

        Vec2 under = p.position().plus(p.towardsRoad().times(1.4));
        float r = 11f;
        Vector3f c = Coords.world(under, 0.05);
        pools.quad(c.add(-r, 0, r), c.add(r, 0, r), c.add(r, 0, -r), c.add(-r, 0, -r));
    }
}
