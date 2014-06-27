/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package SimplifiedSimulator;

import com.jme3.scene.Node;

/**
 *
 * @author Toby Zeldenrust <toby.zeldenrust.org>
 */
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.scene.Spatial;
import java.util.ResourceBundle.Control;

public class MyCustomControl extends RigidBodyControl implements PhysicsCollisionListener {

    Node rootNode;
    String collisionString;
    Car car;

    public void collision(PhysicsCollisionEvent event) {
        try {
            if (car.isMoving) {
                if (collisionString != event.getNodeA().getName() + " botst met " + event.getNodeB().getName()) {
                    if (event.getNodeA().getName().contains("vehicleNode") && event.getNodeB().getName().contains("vehicleNode")) {
                        collisionString = event.getNodeA().getName() + " botst met " + event.getNodeB().getName();
                        System.out.println(collisionString);
                        //event.getNodeA().removeFromParent();
                        //event.getNodeA().removeControl(event.getNodeA().getControl(0));
                        String nodeString = event.getNodeA().getName().substring(11);
                        int nodeId = Integer.parseInt(nodeString);
                        System.out.println("node id is " + nodeId + "en car id is " + car.id);
                        if (nodeId == car.id) {
                            car.stop();
                        }
                    }
                }
            }
        } catch (NullPointerException e) {
        }
    }

    public MyCustomControl(BulletAppState bulletAppState, Node rootNode, Car car) {
        bulletAppState.getPhysicsSpace().addCollisionListener(this);
        this.rootNode = rootNode;
        this.car = car;
    }
}
