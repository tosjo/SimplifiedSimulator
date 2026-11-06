package simplifiedsimulator;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.texture.Texture;

/** Builds the static part of the scene: the textured crossroad floor. */
public final class CrossroadScene {

    /** Half the width of the square floor, in world units. */
    public static final float HALF_SIZE = 250f;

    private CrossroadScene() {
    }

    public static Geometry createFloor(AssetManager assetManager) {
        Texture map = assetManager.loadTexture("Textures/crossroad-map.png");
        map.setAnisotropicFilter(8);
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", map);

        Geometry floor = new Geometry("Floor", new Box(HALF_SIZE, 0.25f, HALF_SIZE));
        floor.setMaterial(material);
        // The routes were recorded on the map in this orientation.
        floor.rotate(0f, -FastMath.HALF_PI, 0f);
        return floor;
    }
}
