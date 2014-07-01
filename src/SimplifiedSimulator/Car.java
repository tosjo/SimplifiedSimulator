/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package SimplifiedSimulator;

import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.MotionState;
import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.control.GhostControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.cinematic.MotionPath;
import com.jme3.cinematic.MotionPathListener;
import com.jme3.cinematic.events.MotionEvent;
import com.jme3.collision.CollisionResults;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Spline.SplineType;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import java.io.Console;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Toby Zeldenrust
 */
public class Car {

    Node rootNode;
    int startPoint;
    int endPoint;
    int id;
    Geometry carGeometry;
    BulletAppState bulletAppState;
    AssetManager assetManager;
    public MotionPath path = new MotionPath();
    public RoadPath roadPath;
    public boolean pathLoaded = false;
    private boolean DetectLight = true;
    String generatedfilename;
    //RigidBodyControl car_phys;
    //VehicleControl carControl;
    Node vehicleNode;
    private Vector3f flatLocation;
    //private String waypointsFromFile;
    //private String correctWPFromFile;
    //private String correctWPFromFile2;
    private MotionEvent motionControl;
    PhysicsSpace space;
    public boolean isMoving;
    private Geometry detecionSpatial;
    private final Geometry detecionGeometry;
    
    boolean canContinue;

    //private Boolean IsMBoolean
    public Car(Node rootNode, RoadPath roadpath, int id, AssetManager assetManager, BulletAppState bulletAppState) {
        //this.startPoint = startPoint;
        //this.endPoint = endpoint;
        this.id = id;
        this.bulletAppState = bulletAppState;
        this.rootNode = rootNode;
        this.assetManager = assetManager;
        space = bulletAppState.getPhysicsSpace();

        this.roadPath = roadpath;
        LoadRoad();

        //generatedfilename = "waypoints\\waypoints" + startPoint + "-" + endPoint + ".txt";
        //System.out.println(generatedfilename);
        //car_phys = new RigidBodyControl(1.0f); // dynamic
        Material carMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        //carMaterial.getAdditionalRenderState().setWireframe(true);
        carMaterial.setColor("Color", ColorRGBA.Blue);
        Box car = new Box(1f, 1f, 2f);
        carGeometry = new Geometry("Car" + id, car);
        carGeometry.setMaterial(carMaterial);

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        //mat.getAdditionalRenderState().setWireframe(true);
        mat.setColor("Color", ColorRGBA.Red);
        MyCustomControl CarControl = new MyCustomControl(bulletAppState, rootNode, this);
        CompoundCollisionShape compoundShape = new CompoundCollisionShape();
        BoxCollisionShape box = new BoxCollisionShape(new Vector3f(1f, 1f, 2f));
        compoundShape.addChildShape(box, new Vector3f(0, 1, 0));

        //creating detection spatial
        Material detectionMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        //carMaterial.getAdditionalRenderState().setWireframe(true);
        detectionMaterial.setColor("Color", ColorRGBA.Blue);
        Box detection = new Box(0.5f, 1f, 3f);
        detecionGeometry = new Geometry("Detection" + id, detection);
        detecionGeometry.setMaterial(detectionMaterial);

        detectionMaterial.setColor("Color", ColorRGBA.Red);
        BoxCollisionShape detectionShape = new BoxCollisionShape(new Vector3f(0.5f, 0.5f, 1f));
        compoundShape.addChildShape(detectionShape, new Vector3f(1, 0, 0));


        vehicleNode = new Node("vehicleNode" + id);
        //vehicleNode.attachChild(detecionGeometry);
        vehicleNode.attachChild(carGeometry);
        //vehicleNode.addControl(CarControl);
        //CarControl.setKinematic(true);
        //CarControl.setMass(1f);


        //carGeometry.setLocalTranslation(0, 15, 5);
        //RigidBodyControl automatically uses box collision shapes when attached to single geometry with box mesh


        //MyCustomControl carControl = new MyCustomControl(bulletAppState);
        //carControl.setApplyPhysicsLocal(true);
        //carGeometry.addControl(carControl);
        //rootNode.attachChild(carGeometry);
        //space.add(CarControl);
        rootNode.attachChild(vehicleNode);
        //loadWaypoints();

        System.out.println(this.roadPath.ToString());

        SetCollisionGroup();
        setPath();


    }

    public void SetCollisionGroup() {
        //if (rootNode != null) {
        //car_phys.setCollisionGroup(PhysicsCollisionObject.COLLISION_GROUP_02);
        //car_phys.setCollideWithGroups(PhysicsCollisionObject.COLLISION_GROUP_02);
        //}
    }

    public void setPath() {
        if (pathLoaded) {
            //rootNode.attachChild(carGeometry);
            //bulletAppState.getPhysicsSpace().add(carGeometry);
            motionControl = new MotionEvent(vehicleNode, this.getPath(id));
            motionControl.setDirectionType(MotionEvent.Direction.PathAndRotation);
            motionControl.setRotation(new Quaternion().fromAngleNormalAxis(-FastMath.PI, Vector3f.UNIT_Y));
            path.setCurveTension(0f);

            //motionControl.setInitialDuration(20f);


            path.setPathSplineType(SplineType.Linear);
            motionControl.setSpeed(0.5f);
            //motionControl.setSpeed(1 + (int) (Math.random() * ((4 - 1) + 1)));
            motionControl.play();
            isMoving = true;


            path.enableDebugShape(assetManager, rootNode);


            this.getPath(id).addListener(new MotionPathListener() {
                public void onWayPointReach(MotionEvent control, int wayPointIndex) {
                    if (path.getNbWayPoints() == wayPointIndex + 1) {
                        if (Car.this.id == Integer.parseInt(control.getSpatial().getName().substring(11))) {
                            delete();
                        }

                    } else {
                        //System.out.println(control.getSpatial().getName() + " has reached way point " + wayPointIndex);
                    }
                }
            });


        } else {
            System.out.println("path didn't load");
        }
    }

    public MotionPath getPath(int id) {
        //loadWaypoints();
        if (pathLoaded) {
            System.out.println("Path is loaded");
            return this.path;
        } else {
            System.out.println("no path in car class yet. loading it now");
            return null;
        }

    }

    final public void LoadRoad() {
        path = roadPath.path;
        pathLoaded = true;
    }

    private void addWayPoint(Vector3f waypointLocation) {
        flatLocation = waypointLocation;
        flatLocation.y = 1f;

        path.addWayPoint(flatLocation);
    }

    public void delete() {

        vehicleNode.detachAllChildren();
        rootNode.detachChild(vehicleNode);
        //bulletAppState.getPhysicsSpace().remove(vehicleNode);
        System.out.println("CAR" + id + " DELETED");
        carGeometry.removeFromParent();
        motionControl.dispose();
        SimplifiedSimulator.getInstance().RemoveCar(this);

    }

    public void stop() {
        motionControl.pause();
        isMoving = false;
        //carControl.setKinematic(true);
    }

    public void tryContinue() {
        if(canContinue == true){
        motionControl.play();
        isMoving = true;
        }
        //if (carControl != null) {
        //   carControl.setKinematic(false);
        //   System.out.println(id + "is vrijgegeven");
        //}
    }

    public void GetCollisionCar(Car car) {
        if (isMoving) {
            CollisionResults results = new CollisionResults();
            BoundingVolume bv = carGeometry.getWorldBound();
            car.carGeometry.collideWith(bv, results);

            if (results.size() > 0 && results.size() < 10) {
                System.out.println("Car: " + this.id + " Collision Results: " + results.size() + " with car: " + car.id);
                //this.stop
                SimplifiedSimulator.getInstance().StopCar(this);
            } else {
                //System.out.println("No collisons");
            }
        }
    }

    public void GetCollisionLight(TrafficLight tl) {
        SetDetect(tl);
        if (DetectLight) {
            CollisionResults results = new CollisionResults();
            BoundingVolume bv = carGeometry.getWorldBound();
            tl.lightGeometry.collideWith(bv, results);
            

            if (results.size() > 0) {
                //System.out.println("Car: " + this.id + " Collision Results: " + results.size() + " with Light: " + tl.ID);
                this.stop();
                canContinue = false;
                //SimplifiedSimulator.getInstance().StopCar(car);
            } else {
                //System.out.println("No collisons");
            }
        }

    }
    
    public void SetDetect(TrafficLight tl)
    {
        if(tl.GetState() == TrafficLight.States.Green)
        {
            DetectLight = false;
            canContinue = true;
        }
        else
        {
            DetectLight = true;            
        }
    }
}
