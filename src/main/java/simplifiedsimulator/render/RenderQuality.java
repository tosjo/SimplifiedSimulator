package simplifiedsimulator.render;

/**
 * Graphics presets. {@link #HIGH} is meant for a dedicated graphics card, {@link #LOW} for
 * integrated graphics or software rendering.
 */
public enum RenderQuality {
    HIGH(4096, 4, true, true, true, 2),
    MEDIUM(2048, 3, false, true, true, 2),
    LOW(2048, 2, false, false, false, 1);

    private final int shadowMapSize;
    private final int shadowSplits;
    private final boolean ambientOcclusion;
    private final boolean bloom;
    private final boolean antiAliasing;
    private final int treeDetail;

    RenderQuality(int shadowMapSize, int shadowSplits, boolean ambientOcclusion, boolean bloom, boolean antiAliasing,
                  int treeDetail) {
        this.shadowMapSize = shadowMapSize;
        this.shadowSplits = shadowSplits;
        this.ambientOcclusion = ambientOcclusion;
        this.bloom = bloom;
        this.antiAliasing = antiAliasing;
        this.treeDetail = treeDetail;
    }

    public int shadowMapSize() {
        return shadowMapSize;
    }

    public int shadowSplits() {
        return shadowSplits;
    }

    public boolean ambientOcclusion() {
        return ambientOcclusion;
    }

    public boolean bloom() {
        return bloom;
    }

    public boolean antiAliasing() {
        return antiAliasing;
    }

    /** Icosphere subdivisions used for tree crowns. */
    public int treeDetail() {
        return treeDetail;
    }
}
