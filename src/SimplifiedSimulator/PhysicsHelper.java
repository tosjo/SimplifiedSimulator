/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package SimplifiedSimulator;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.light.AmbientLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;

/**
 *
 * @author Gebruiker
 */
public class PhysicsHelper {
    
    //public static RigidBodyControl stoplichtControl;
    //public static Material stoplichtMaterial;
    
    public static void createPhysicsWorld(Node rootNode, AssetManager assetManager, PhysicsSpace space) {
        AmbientLight light = new AmbientLight();
        light.setColor(ColorRGBA.LightGray);
        rootNode.addLight(light);

        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", assetManager.loadTexture("Textures/CrossroadMapWithNumbers(METbeschrijvingen).png"));

        Box floorBox = new Box(250, 0.25f, 250);
        Geometry floorGeometry = new Geometry("Floor", floorBox);
        floorGeometry.setMaterial(material);
        floorGeometry.setLocalTranslation(0, 0, 0);
        floorGeometry.rotate(0, -FastMath.PI / 2, 0);
        floorGeometry.addControl(new RigidBodyControl(0));
        rootNode.attachChild(floorGeometry);
        
        space.add(floorGeometry);
        
        //stoplichtMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        

        //movable boxes
        //for (int i = 0; i < 1; i++) {
            
         //   Box stoplicht = new Box(1,5, 1);
         //   Geometry boxGeometry = new Geometry("Stoplicht", stoplicht);
         //   boxGeometry.setMaterial(stoplichtMaterial);
            
         //   stoplichtMaterial.setColor("Color", ColorRGBA.Red);
          //  boxGeometry.setLocalTranslation(-1.0f,6 , 85);
            //RigidBodyControl automatically uses box collision shapes when attached to single geometry with box mesh
         //   stoplichtControl = new RigidBodyControl(2);
            
         //   boxGeometry.addControl(stoplichtControl);
          //  stoplichtControl.setKinematic(true);
         //   rootNode.attachChild(boxGeometry);
         //   space.add(boxGeometry);
        }
    
   // public static void stoplichtGroen()
    //{
    //    stoplichtControl.setEnabled(false);
   //     stoplichtMaterial.setColor("Color", ColorRGBA.Green);
   // }
    
}
