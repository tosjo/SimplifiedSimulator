package simplifiedsimulator.render;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.texture.Texture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates and shares the materials of the scene. Every lit material is registered so that
 * the fog colour can follow the time of day.
 */
public final class Materials {

    private final AssetManager assets;
    private final List<Material> foggy = new ArrayList<>();
    private final Map<String, Material> cache = new HashMap<>();
    private ColorRGBA fogColor = new ColorRGBA(0.7f, 0.78f, 0.86f, 1f);
    private float fogStart = 600f;
    private float fogEnd = 4000f;

    public Materials(AssetManager assets) {
        this.assets = assets;
    }

    public AssetManager assets() {
        return assets;
    }


    /**
     * A lit material whose colour comes from the mesh's vertex colours.
     *
     * @param shininess specular exponent; 0 for matte surfaces
     */
    public Material vertexColored(String key, float specular, float shininess) {
        return cache.computeIfAbsent("vc:" + key, k -> {
            Material m = lit();
            m.setBoolean("UseVertexColor", true);
            m.setColor("Diffuse", ColorRGBA.White);
            m.setColor("Ambient", ColorRGBA.White);
            m.setColor("Specular", new ColorRGBA(specular, specular, specular, 1f));
            m.setFloat("Shininess", Math.max(1f, shininess));
            return m;
        });
    }

    /** Vertex-coloured material multiplied by a paint colour (vehicle bodies). */
    public Material paint(ColorRGBA paint) {
        String key = String.format("paint:%.3f,%.3f,%.3f", paint.r, paint.g, paint.b);
        return cache.computeIfAbsent(key, k -> {
            Material m = lit();
            m.setBoolean("UseVertexColor", true);
            m.setColor("Diffuse", paint);
            m.setColor("Ambient", paint);
            m.setColor("Specular", new ColorRGBA(0.7f, 0.7f, 0.7f, 1f));
            m.setFloat("Shininess", 56f);
            return m;
        });
    }

    /** Lit, textured material with vertex colours (facades, roofs). */
    public Material textured(String key, Texture diffuse, Texture specularMap, float shininess) {
        return cache.computeIfAbsent("tex:" + key, k -> {
            Material m = lit();
            m.setBoolean("UseVertexColor", true);
            m.setTexture("DiffuseMap", diffuse);
            m.setColor("Diffuse", new ColorRGBA(0.92f, 0.92f, 0.92f, 1f));
            m.setColor("Ambient", new ColorRGBA(0.85f, 0.85f, 0.85f, 1f));
            if (specularMap != null) {
                m.setTexture("SpecularMap", specularMap);
                m.setColor("Specular", ColorRGBA.White);
            } else {
                m.setColor("Specular", ColorRGBA.Black);
            }
            m.setFloat("Shininess", shininess);
            return m;
        });
    }

    /** Self-lit colour that also feeds the bloom pass (lit lamps). */
    public Material glow(ColorRGBA color, float glowStrength) {
        String key = String.format("glow:%.3f,%.3f,%.3f,%.2f", color.r, color.g, color.b, glowStrength);
        return cache.computeIfAbsent(key, k -> {
            Material m = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setColor("Color", color);
            m.setColor("GlowColor", color.mult(glowStrength));
            return m;
        });
    }

    /** Self-lit textured symbol with transparency (signal symbols); glows when {@code glow > 0}. */
    public Material symbol(String key, Texture texture, ColorRGBA tint, float glow) {
        return cache.computeIfAbsent("sym:" + key, k -> {
            Material m = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setTexture("ColorMap", texture);
            m.setColor("Color", tint);
            if (glow > 0) {
                m.setTexture("GlowMap", texture);
                m.setColor("GlowColor", tint.mult(glow));
            }
            m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            m.setFloat("AlphaDiscardThreshold", 0.05f);
            return m;
        });
    }

    /** Additive light splash on the ground (street light and headlight pools at night). */
    public Material lightPool(String key, Texture texture, ColorRGBA color) {
        return cache.computeIfAbsent("pool:" + key, k -> {
            Material m = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setTexture("ColorMap", texture);
            m.setColor("Color", color);
            m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
            m.getAdditionalRenderState().setDepthWrite(false);
            m.getAdditionalRenderState().setPolyOffset(-2, -2);
            return m;
        });
    }

    /**
     * Glass facades: opaque, dark and reflective, as glass looks from outside in daylight.
     * {@code frames} supplies panes and frames, {@code reflectivity} how much each part
     * reflects, and the environment map the sky that is reflected.
     */
    public Material glass(String key, Texture frames, Texture reflectivity, Texture environment) {
        return cache.computeIfAbsent("glass:" + key, k -> {
            // no fog: jME's Lighting shader cannot combine fog with reflections
            Material m = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
            m.setBoolean("UseMaterialColors", true);
            m.setBoolean("UseVertexColor", true);
            m.setTexture("DiffuseMap", frames);
            m.setColor("Diffuse", ColorRGBA.White);
            m.setColor("Ambient", new ColorRGBA(0.8f, 0.8f, 0.8f, 1f));
            // jME scales the reflection by the specular map only
            m.setTexture("SpecularMap", reflectivity);
            m.setColor("Specular", ColorRGBA.White);
            m.setFloat("Shininess", 96f);
            if (environment != null) {
                m.setTexture("EnvMap", environment);
                m.setVector3("FresnelParams", new com.jme3.math.Vector3f(0.18f, 0.8f, 2.0f));
            }
            return m;
        });
    }

    /** Updates the distance fog of every lit material. */
    public void setFog(ColorRGBA color, float start, float end) {
        fogColor = color.clone();
        fogStart = start;
        fogEnd = end;
        for (Material m : foggy) {
            applyFog(m);
        }
    }

    private Material lit() {
        Material m = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setBoolean("UseFog", true);
        applyFog(m);
        foggy.add(m);
        return m;
    }

    private void applyFog(Material m) {
        m.setColor("FogColor", fogColor);
        m.setVector2("LinearFog", new Vector2f(fogStart, fogEnd));
    }
}
