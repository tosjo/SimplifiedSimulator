/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mygame;

import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.linearmath.MotionState;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.cinematic.MotionPath;
import com.jme3.cinematic.MotionPathListener;
import com.jme3.cinematic.events.MotionEvent;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
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
    public boolean pathLoaded = false;
    String generatedfilename;
    RigidBodyControl car_phys;
    private Vector3f flatLocation;
    private String waypointsFromFile;
    private String correctWPFromFile;
    private String correctWPFromFile2;
    private MotionEvent motionControl;
    PhysicsSpace space;

    public Car(Node rootNode, int startPoint, int endpoint, int id, AssetManager assetManager, BulletAppState bulletAppState) {
        this.startPoint = startPoint;
        this.endPoint = endpoint;
        this.id = id;
        this.bulletAppState = bulletAppState;
        this.rootNode = rootNode;
        this.assetManager = assetManager;
        space = bulletAppState.getPhysicsSpace();
        
        generatedfilename = "waypoints\\waypoints" + startPoint + "-" + endPoint + ".txt";        
        car_phys= new RigidBodyControl(1.0f ); // dynamic
        Material carMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        carMaterial.setColor("Color", ColorRGBA.Blue);
        Box car = new Box(1f, 1f, 2f);
        carGeometry = new Geometry("Car"+id, car);
        carGeometry.setMaterial(carMaterial);
        //carGeometry.setLocalTranslation(0, 15, 5);
        //RigidBodyControl automatically uses box collision shapes when attached to single geometry with box mesh
        
        
        MyCustomControl carControl = new MyCustomControl(bulletAppState);
        carControl.setApplyPhysicsLocal(true);
        carControl.setMass(1f);
        carGeometry.addControl(carControl);
        //rootNode.attachChild(carGeometry);
        space.add(carControl);
        rootNode.attachChild(carGeometry);
        loadWaypoints();
        
        
    }

    public void setPath() {
        if (pathLoaded) {
            //rootNode.attachChild(carGeometry);
            //bulletAppState.getPhysicsSpace().add(carGeometry);
            motionControl = new MotionEvent(carGeometry, this.getPath(id));
            motionControl.setDirectionType(MotionEvent.Direction.PathAndRotation);
            motionControl.setRotation(new Quaternion().fromAngleNormalAxis(-FastMath.PI, Vector3f.UNIT_Y));

            //motionControl.setInitialDuration(20f);

            //motionControl.setSpeed(3);
            motionControl.setSpeed(1 + (int) (Math.random() * ((4 - 1) + 1)));
            motionControl.play();
            
            
            path.enableDebugShape(assetManager, rootNode);
            
            
            this.getPath(id).addListener(new MotionPathListener() {
                public void onWayPointReach(MotionEvent control, int wayPointIndex) {
                    if (path.getNbWayPoints() == wayPointIndex + 1) {
                        delete();
                        System.out.println("CAR"+id+" DELETED");
                    } else {
                        System.out.println(control.getSpatial().getName() + id + " has reached way point " + wayPointIndex);
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

    public void loadWaypoints() {
        pathLoaded = false;
        float x = 0;
        float y = 0;
        float z = 0;
        try {
            Scanner in = new Scanner(new FileReader(generatedfilename));
            waypointsFromFile = in.nextLine();
            correctWPFromFile = waypointsFromFile.replace("(", "");
            correctWPFromFile2 = correctWPFromFile.replace(" ", "");
            String formattedWayPoints[] = correctWPFromFile2.split("\\)");
            for (int i = 0; i < formattedWayPoints.length; i++) {
                String[] vectorWP = formattedWayPoints[i].split(",");
                x = Float.valueOf(vectorWP[0]);
                y = Float.valueOf(vectorWP[1]);
                z = Float.valueOf(vectorWP[2]);
                //System.out.println("dit zijn xyz"+x + y + z);
                Vector3f test = new Vector3f(x, y, z);
                addWayPoint(test);
                pathLoaded = true;
                
            }
            setPath();

        } catch (FileNotFoundException ex) {
            Logger.getLogger(SimplifiedSimulator.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void addWayPoint(Vector3f waypointLocation) {
        flatLocation = waypointLocation;
        flatLocation.y = 1.5f;

        path.addWayPoint(flatLocation);
    }

    public void delete() {
        rootNode.detachChild(carGeometry);
        bulletAppState.getPhysicsSpace().remove(carGeometry);
    }

    public void stop() {
        motionControl.pause();
    }
}
