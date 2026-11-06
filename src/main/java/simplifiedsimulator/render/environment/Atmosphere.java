package simplifiedsimulator.render.environment;

import com.jme3.math.ColorRGBA;

/**
 * Colours of the sky and the light for a given sun elevation, from deep night through
 * twilight, golden hour and full daylight. Values are interpolated between key frames
 * that were tuned by eye against photos of the Dutch sky.
 *
 * @param zenith        sky colour straight up
 * @param horizon       sky colour at the horizon
 * @param ground        colour of the haze below the horizon
 * @param sun           colour and intensity of direct sunlight (or moonlight at night)
 * @param ambient       colour and intensity of the light from the whole sky
 * @param fog           colour of distant haze
 * @param cloud         colour of the clouds
 * @param sunGlow       strength of the glow around the sun disc
 * @param starBrightness 0 by day, up to 1 on a dark night
 * @param night         0 in daylight, 1 when street lights and headlights are fully on
 * @param shadowStrength darkness of shadows, 0 = no visible shadows
 */
public record Atmosphere(ColorRGBA zenith, ColorRGBA horizon, ColorRGBA ground, ColorRGBA sun, ColorRGBA ambient,
                         ColorRGBA fog, ColorRGBA cloud, float sunGlow, float starBrightness, float night,
                         float shadowStrength) {

    private record Key(float elevation, Atmosphere value) {
    }

    private static final Key[] KEYS = {
        new Key(-18, new Atmosphere(rgb(0.005f, 0.010f, 0.030f), rgb(0.020f, 0.028f, 0.055f), rgb(0.015f, 0.02f, 0.03f),
                rgb(0.10f, 0.12f, 0.20f), rgb(0.050f, 0.060f, 0.095f), rgb(0.025f, 0.03f, 0.05f),
                rgb(0.04f, 0.045f, 0.06f), 0f, 1f, 1f, 0.15f)),
        new Key(-8, new Atmosphere(rgb(0.03f, 0.05f, 0.13f), rgb(0.16f, 0.13f, 0.20f), rgb(0.05f, 0.05f, 0.07f),
                rgb(0.12f, 0.13f, 0.22f), rgb(0.10f, 0.11f, 0.17f), rgb(0.12f, 0.11f, 0.16f),
                rgb(0.15f, 0.12f, 0.16f), 0.2f, 0.5f, 1f, 0.15f)),
        new Key(-2, new Atmosphere(rgb(0.12f, 0.18f, 0.35f), rgb(0.80f, 0.42f, 0.25f), rgb(0.18f, 0.15f, 0.16f),
                rgb(0.45f, 0.25f, 0.18f), rgb(0.24f, 0.22f, 0.30f), rgb(0.48f, 0.36f, 0.34f),
                rgb(0.55f, 0.35f, 0.32f), 0.9f, 0.05f, 0.75f, 0.2f)),
        new Key(3, new Atmosphere(rgb(0.22f, 0.34f, 0.58f), rgb(0.95f, 0.62f, 0.38f), rgb(0.35f, 0.32f, 0.30f),
                rgb(1.00f, 0.62f, 0.36f), rgb(0.34f, 0.33f, 0.40f), rgb(0.78f, 0.66f, 0.58f),
                rgb(0.95f, 0.72f, 0.60f), 1f, 0f, 0.2f, 0.45f)),
        new Key(10, new Atmosphere(rgb(0.24f, 0.42f, 0.72f), rgb(0.85f, 0.80f, 0.72f), rgb(0.55f, 0.56f, 0.55f),
                rgb(1.00f, 0.84f, 0.64f), rgb(0.40f, 0.42f, 0.50f), rgb(0.78f, 0.80f, 0.82f),
                rgb(0.95f, 0.90f, 0.85f), 0.8f, 0f, 0f, 0.55f)),
        new Key(25, new Atmosphere(rgb(0.22f, 0.43f, 0.78f), rgb(0.72f, 0.81f, 0.91f), rgb(0.62f, 0.66f, 0.68f),
                rgb(1.00f, 0.95f, 0.86f), rgb(0.44f, 0.47f, 0.55f), rgb(0.74f, 0.81f, 0.89f),
                rgb(0.97f, 0.97f, 0.98f), 0.6f, 0f, 0f, 0.6f)),
        new Key(60, new Atmosphere(rgb(0.18f, 0.40f, 0.80f), rgb(0.70f, 0.80f, 0.92f), rgb(0.62f, 0.66f, 0.68f),
                rgb(1.00f, 0.97f, 0.92f), rgb(0.46f, 0.49f, 0.57f), rgb(0.74f, 0.81f, 0.90f),
                rgb(1.00f, 1.00f, 1.00f), 0.5f, 0f, 0f, 0.62f)),
    };

    /** Atmosphere for a sun elevation in degrees. */
    public static Atmosphere forElevation(double elevation) {
        if (elevation <= KEYS[0].elevation) {
            return KEYS[0].value;
        }
        for (int i = 0; i + 1 < KEYS.length; i++) {
            Key a = KEYS[i];
            Key b = KEYS[i + 1];
            if (elevation <= b.elevation) {
                float t = (float) ((elevation - a.elevation) / (b.elevation - a.elevation));
                return mix(a.value, b.value, t);
            }
        }
        return KEYS[KEYS.length - 1].value;
    }

    private static Atmosphere mix(Atmosphere a, Atmosphere b, float t) {
        return new Atmosphere(mix(a.zenith, b.zenith, t), mix(a.horizon, b.horizon, t), mix(a.ground, b.ground, t),
                mix(a.sun, b.sun, t), mix(a.ambient, b.ambient, t), mix(a.fog, b.fog, t), mix(a.cloud, b.cloud, t),
                lerp(a.sunGlow, b.sunGlow, t), lerp(a.starBrightness, b.starBrightness, t), lerp(a.night, b.night, t),
                lerp(a.shadowStrength, b.shadowStrength, t));
    }

    private static ColorRGBA mix(ColorRGBA a, ColorRGBA b, float t) {
        return new ColorRGBA().interpolateLocal(a, b, t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static ColorRGBA rgb(float r, float g, float b) {
        return new ColorRGBA(r, g, b, 1f);
    }
}
