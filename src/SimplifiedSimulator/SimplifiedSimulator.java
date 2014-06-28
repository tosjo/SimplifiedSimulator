package SimplifiedSimulator;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
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
    public List<Car> CarList = new ArrayList<Car>();
    int carCounter = 0;
    public WaypointLoader waypointLoader;// = new WaypointLoader("waypoints.txt");

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

        waypointLoader = new WaypointLoader("waypoints\\waypoints");
        waypointLoader.LoadRoadpaths();
        System.out.println("loaded paths");




        //creating the world
        PhysicsHelper.createPhysicsWorld(rootNode, assetManager, bulletAppState.getPhysicsSpace());

    }

    @Override
    public void simpleUpdate(float tpf) {
        //TODO: add update code
    }

    @Override
    public void simpleRender(RenderManager rm) {
        //TODO: add render code
    }

    public void createCar() {

        RoadPath path1 = waypointLoader.GetRoadPath(14, 6);
        if (path1 != null) {
            Car car = new Car(rootNode, path1, carCounter, assetManager, bulletAppState);
            carCounter++;
            CarList.add(car);
        }
        
        RoadPath path2 = waypointLoader.GetRoadPath(16, 13);
        if (path2 != null) {
            Car car2 = new Car(rootNode, path2, carCounter, assetManager, bulletAppState);
            carCounter++;
            CarList.add(car2);            
        }
        
        

    }

    private void setUpKeys() {
        inputManager.addMapping("Start", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addListener(this, "Start");

        inputManager.addMapping("Continue", new KeyTrigger(KeyInput.KEY_F));
        inputManager.addListener(this, "Continue");
    }

    /**
     * These are our custom actions triggered by key presses.
     */
    public void onAction(String binding, boolean isPressed, float tpf) {
        if (binding.equals("Start")) {
            if (isPressed) {
                createCar();
            }

        } else if (binding.equals("Continue")) {
            if (isPressed) {
                for (Car car : CarList) {
                    car.tryContinue();
                    PhysicsHelper.stoplichtGroen();
                }
            }

        }

    }

    public void stop(int id, Car car) {
        car.stop();
    }
}
