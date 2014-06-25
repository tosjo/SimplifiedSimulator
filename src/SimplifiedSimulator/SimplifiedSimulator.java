package SimplifiedSimulator;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Toby Zeldenrust
 */
public class SimplifiedSimulator extends SimpleApplication implements ActionListener {

    /**
     * Variables
     */
    private BulletAppState bulletAppState;
    private Vector3f camStartLocation = new Vector3f(-100, 100, 100);
    public List<Car> CarList= new ArrayList<Car>();
    int carCounter = 0;

    public static void main(String[] args) {
        SimplifiedSimulator app = new SimplifiedSimulator();
        app.start();
    }

    @Override
    public void simpleInitApp() {
        /**
         * Set up Physics
         */
        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);
        
        
        //init keys
        setUpKeys();

        //setting up camera
        flyCam.setMoveSpeed(300);
        cam.setLocation(camStartLocation);
        cam.lookAt(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0));

        //creating the world
        PhysicsHelper.createPhysicsWorld(rootNode, assetManager, bulletAppState.getPhysicsSpace());
        
        //creating a test'car'
        Box car = new Box(2, 1, 1);
        Geometry geom = new Geometry("TEST", car);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", ColorRGBA.Blue);
        geom.setMaterial(mat);

        geom.setLocalTranslation(10, 1, 57);
        geom.addControl(new RigidBodyControl(2));
        bulletAppState.getPhysicsSpace().add(geom);

        rootNode.attachChild(geom);
    }

    @Override
    public void simpleUpdate(float tpf) {
        //TODO: add update code
    }

    @Override
    public void simpleRender(RenderManager rm) {
        //TODO: add render code
    }
    
    public void createCar()
    {        
        Car car = new Car(rootNode,14, 6, carCounter,assetManager, bulletAppState);
        CarList.add(car);
        carCounter++;
    }
    
    
    
    private void setUpKeys() {
        inputManager.addMapping("Start", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addListener(this, "Start");
    }
    
        /**
     * These are our custom actions triggered by key presses.
     */
    public void onAction(String binding, boolean isPressed, float tpf) {
        if (binding.equals("Start")) {
            if (isPressed) {
                createCar();
            }  

        }

    }
    
        public void stop(int id, Car car)
    {
        car.stop();
    }
}
