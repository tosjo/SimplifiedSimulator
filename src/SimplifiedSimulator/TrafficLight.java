/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package SimplifiedSimulator;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.BulletAppState;
import com.jme3.collision.CollisionResults;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

/**
 *
 * @author Herman
 */
public class TrafficLight {

    public Geometry lightGeometry;
    public Material material;
    public Spatial lightSpatial;
    public int ID;
    
    //boolean carWaiting;

    public enum States {

        Red, Orange, Green
    };
    States state;
    boolean sendMessage = true;

    public TrafficLight(Node rootNode, int id, float x, float z, AssetManager assetmanager, BulletAppState bulletAppState) {

        this.ID = id;

        state = States.Red;
        Box lightBox = new Box(1, 5, 1);

        lightGeometry = new Geometry("TrafficLight" + id, lightBox);
        material = new Material(assetmanager, "Common/MatDefs/Misc/Unshaded.j3md");

        material.setColor("Color", ColorRGBA.Red);

        lightGeometry.setMaterial(material);
        lightGeometry.setLocalTranslation(x, 6, z);

        rootNode.attachChild(lightGeometry);

        //lightSpatial = assetmanager.loadModel("Common/MatDefs/Misc/Unshaded.j3md");
    }

    public void SetGreen() {

        this.state = States.Green;
        material.setColor("Color", ColorRGBA.Green);
        sendMessage = true;

    }

    public void SetOrange() {
        this.state = States.Orange;
        material.setColor("Color", ColorRGBA.Orange);

    }

    public void SetRed() {
        this.state = States.Red;
        material.setColor("Color", ColorRGBA.Red);
        sendMessage = true;

    }

    public States GetState() {
        return state;
    }

    public void setWaiting() {
        sendMessage = false;
    }

    public void SendMessage() {
        if (sendMessage) {
            String msg = SimplifiedSimulator.getInstance().CreateMessage(this.ID);
            SimplifiedSimulator.getInstance().SendMessage(msg);
        }
        //setWaiting();

    }
    
    public void checkCollision(Car car)
    {
        CollisionResults results = new CollisionResults();
            BoundingVolume bv = lightGeometry.getWorldBound();
            car.carGeometry.collideWith(bv, results);
            

            if (results.size() > 0) {
                //System.out.println("Car: " + this.id + " Collision Results: " + results.size() + " with Light: " + tl.ID);
                SendMessage();
                setWaiting();
                //canContinue = false;
                //SimplifiedSimulator.getInstance().StopCar(car);
            } else {
                //System.out.println("No collisons");
            }
    }
}
