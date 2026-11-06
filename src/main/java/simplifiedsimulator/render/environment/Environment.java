package simplifiedsimulator.render.environment;

import com.jme3.asset.AssetManager;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import com.jme3.shadow.DirectionalLightShadowFilter;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.TextureCubeMap;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import simplifiedsimulator.render.Materials;
import simplifiedsimulator.render.RenderQuality;

/**
 * Everything that depends on the time of day: the sky dome, the textured ground, sun and
 * ambient light, distance haze and the post-processing filters (shadows, bloom, ambient
 * occlusion, anti-aliasing).
 */
public final class Environment {

    /** Sun direction used for moonlight when the sun is below the horizon. */
    private static final Vector3f MOON = new Vector3f(-0.35f, 0.55f, 0.6f).normalizeLocal();

    private final Materials materials;
    private final Node node = new Node("environment");
    private final DirectionalLight sunLight = new DirectionalLight();
    private final AmbientLight ambientLight = new AmbientLight();
    private final Material skyMaterial;
    private final Material groundMaterial;
    private final TextureCubeMap environmentMap;
    private final int envSize = 32;
    private DirectionalLightShadowFilter shadows;
    private BloomFilter bloom;
    private SunPosition sun = new SunPosition(45, 180);
    private Atmosphere atmosphere = Atmosphere.forElevation(45);
    private float lastEnvElevation = Float.NaN;

    public Environment(Materials materials) {
        this.materials = materials;
        AssetManager assets = materials.assets();

        skyMaterial = new Material(assets, "Shaders/Render/Sky.j3md");
        Geometry sky = new Geometry("sky", new Sphere(24, 48, 10f, false, true));
        sky.setMaterial(skyMaterial);
        sky.setQueueBucket(RenderQueue.Bucket.Sky);
        sky.setCullHint(Spatial.CullHint.Never);
        sky.setShadowMode(RenderQueue.ShadowMode.Off);
        node.attachChild(sky);

        groundMaterial = new Material(assets, "Shaders/Render/Ground.j3md");
        groundMaterial.setTexture("FineMap", aerial(assets, "crossing/aerial-160m.jpg"));
        groundMaterial.setTexture("MidMap", aerial(assets, "crossing/aerial-600m.jpg"));
        groundMaterial.setTexture("WideMap", aerial(assets, "crossing/aerial-3km.jpg"));
        groundMaterial.setColor("OuterColor", new ColorRGBA(0.42f, 0.47f, 0.33f, 1f));
        float size = 16000f;
        Geometry ground = new Geometry("ground", new Quad(size, size));
        ground.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        ground.setLocalTranslation(-size / 2, 0, size / 2);
        ground.setMaterial(groundMaterial);
        ground.setShadowMode(RenderQueue.ShadowMode.Receive);
        node.attachChild(ground);

        Image cube = new Image(Image.Format.RGBA8, envSize, envSize, 0, new ArrayList<>(), ColorSpace.sRGB);
        for (int i = 0; i < 6; i++) {
            cube.addData(BufferUtils.createByteBuffer(envSize * envSize * 4));
        }
        environmentMap = new TextureCubeMap(cube);
        environmentMap.setMagFilter(Texture.MagFilter.Bilinear);
        environmentMap.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        update(LocalDate.now(), 14 * 3600, 0);
    }

    private static Texture aerial(AssetManager assets, String path) {
        Texture t = assets.loadTexture(path);
        t.setWrap(Texture.WrapMode.EdgeClamp);
        t.setMinFilter(Texture.MinFilter.Trilinear);
        t.setMagFilter(Texture.MagFilter.Bilinear);
        t.setAnisotropicFilter(16);
        return t;
    }

    /** Scene node holding the sky and the ground. */
    public Node node() {
        return node;
    }

    /** Sunlight by day, moonlight at night; add it to the root of the scene. */
    public DirectionalLight sunLight() {
        return sunLight;
    }

    /** Light from the sky; add it to the root of the scene. */
    public AmbientLight ambientLight() {
        return ambientLight;
    }

    public SunPosition sun() {
        return sun;
    }

    public Atmosphere atmosphere() {
        return atmosphere;
    }

    /** Sky reflections for glass, regenerated as the sky changes. */
    public TextureCubeMap environmentMap() {
        return environmentMap;
    }

    /** Adds the post-processing filters for the chosen quality to a view port. */
    public FilterPostProcessor createFilters(ViewPort viewPort, RenderQuality quality, int samples) {
        AssetManager assets = materials.assets();
        FilterPostProcessor fpp = new FilterPostProcessor(assets);
        if (samples > 1) {
            fpp.setNumSamples(samples);
        }
        if (quality.ambientOcclusion()) {
            fpp.addFilter(new SSAOFilter(2.5f, 1.4f, 0.25f, 0.08f));
        }
        shadows = new DirectionalLightShadowFilter(assets, quality.shadowMapSize(), quality.shadowSplits());
        shadows.setLight(sunLight);
        shadows.setLambda(0.7f);
        shadows.setEdgeFilteringMode(EdgeFilteringMode.PCFPOISSON);
        shadows.setShadowZExtend(450f);
        shadows.setShadowZFadeLength(120f);
        shadows.setRenderBackFacesShadows(false);
        fpp.addFilter(shadows);
        if (quality.bloom()) {
            bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
            bloom.setBlurScale(1.6f);
            bloom.setExposurePower(2.2f);
            bloom.setBloomIntensity(2.0f);
            bloom.setDownSamplingFactor(2f);
            fpp.addFilter(bloom);
        }
        if (quality.antiAliasing() && samples <= 1) {
            fpp.addFilter(new FXAAFilter());
        }
        viewPort.addProcessor(fpp);
        applyLighting();
        return fpp;
    }

    /**
     * Moves the sun and recolours the sky for a moment in time.
     *
     * @param date         calendar date (sets the sun's path)
     * @param secondsOfDay local clock time
     * @param animationTime seconds since the start, used to drift the clouds
     */
    public void update(LocalDate date, double secondsOfDay, double animationTime) {
        sun = SunPosition.at(date, secondsOfDay);
        atmosphere = Atmosphere.forElevation(sun.elevation());
        Vector3f sunDir = sun.direction();

        skyMaterial.setVector3("SunDirection", sunDir);
        skyMaterial.setColor("ZenithColor", atmosphere.zenith());
        skyMaterial.setColor("HorizonColor", atmosphere.horizon());
        skyMaterial.setColor("GroundColor", atmosphere.ground());
        skyMaterial.setColor("SunColor", atmosphere.sun());
        skyMaterial.setColor("CloudColor", atmosphere.cloud());
        skyMaterial.setFloat("SunGlow", atmosphere.sunGlow());
        skyMaterial.setFloat("StarBrightness", atmosphere.starBrightness());
        skyMaterial.setFloat("Time", (float) (animationTime % 100000));
        skyMaterial.setFloat("CloudCover", 0.42f);

        materials.setFog(atmosphere.fog(), 500f, 3200f);
        applyLighting();

        float elevation = (float) sun.elevation();
        if (Float.isNaN(lastEnvElevation) || Math.abs(elevation - lastEnvElevation) > 2f) {
            lastEnvElevation = elevation;
            paintEnvironmentMap();
        }
    }

    private void applyLighting() {
        Vector3f sunDir = sun.direction();
        boolean daylight = sunDir.y > 0.02f;
        Vector3f lightDir = daylight ? sunDir : MOON;
        // keep the light a little above the horizon so that low sun still lights the scene
        Vector3f shading = new Vector3f(lightDir.x, Math.max(lightDir.y, 0.08f), lightDir.z).normalizeLocal();
        sunLight.setDirection(shading.negate());
        sunLight.setColor(atmosphere.sun().mult(daylight ? 1.15f : 1f));
        ambientLight.setColor(atmosphere.ambient().mult(1.1f));

        groundMaterial.setColor("AmbientColor", atmosphere.ambient().mult(1.05f));
        groundMaterial.setColor("SunColor", atmosphere.sun().mult(daylight ? 0.85f : 0.6f));
        groundMaterial.setVector3("SunDirection", shading);
        groundMaterial.setColor("FogColor", atmosphere.fog());
        groundMaterial.setFloat("FogDensity", 0.00035f);

        if (shadows != null) {
            shadows.setShadowIntensity(atmosphere.shadowStrength());
            shadows.setEnabled(atmosphere.shadowStrength() > 0.05f);
        }
        if (bloom != null) {
            bloom.setBloomIntensity(1.4f + 1.6f * atmosphere.night());
        }
    }

    /** Paints a small cube map of the current sky for reflections on glass. */
    private void paintEnvironmentMap() {
        Image image = environmentMap.getImage();
        List<ByteBuffer> faces = image.getData();
        // jME cube map face order: +X, -X, +Y, -Y, +Z, -Z
        for (int face = 0; face < 6; face++) {
            ByteBuffer buf = faces.get(face);
            buf.clear();
            for (int y = 0; y < envSize; y++) {
                for (int x = 0; x < envSize; x++) {
                    float u = (x + 0.5f) / envSize * 2 - 1;
                    float v = (y + 0.5f) / envSize * 2 - 1;
                    Vector3f d = switch (face) {
                        case 0 -> new Vector3f(1, -v, -u);
                        case 1 -> new Vector3f(-1, -v, u);
                        case 2 -> new Vector3f(u, 1, v);
                        case 3 -> new Vector3f(u, -1, -v);
                        case 4 -> new Vector3f(u, -v, 1);
                        default -> new Vector3f(-u, -v, -1);
                    };
                    d.normalizeLocal();
                    ColorRGBA c = skyColor(d);
                    buf.put((byte) (c.r * 255)).put((byte) (c.g * 255)).put((byte) (c.b * 255)).put((byte) 255);
                }
            }
            buf.flip();
        }
        image.setUpdateNeeded();
    }

    private ColorRGBA skyColor(Vector3f d) {
        if (d.y < 0) {
            return atmosphere.ground().clone().interpolateLocal(atmosphere.horizon(), Math.max(0, 1 + d.y * 6));
        }
        float t = (float) Math.pow(d.y, 0.45);
        ColorRGBA c = atmosphere.horizon().clone().interpolateLocal(atmosphere.zenith(), t);
        c.clamp();
        return c;
    }
}
