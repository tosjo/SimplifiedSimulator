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
import com.jme3.collision.CollisionResults;

public class MyCustomControl extends RigidBodyControl implements PhysicsCollisionListener {

    Node rootNode;
    String collisionString;
    Car car;
    int counter = 0;
    Boolean Collided = false;

    public MyCustomControl(BulletAppState bulletAppState, Node rootNode, Car car) {

        this.rootNode = rootNode;
        this.car = car;
        bulletAppState.getPhysicsSpace().addCollisionListener(this);
    }

    public void collision(PhysicsCollisionEvent event) {
//        if (!Collided) {
//            if (event.getNodeA().getName().contains("vehicleNode")) {
//                final Node node = (Node) event.getNodeA();
//                System.out.println("Collision nodeA: " + node.getName());
//                //Collided = true;
//            }
//            if (event.getNodeB().getName().contains("Stoplicht")) {
//                //final Node node =  event.getNodeB();
//                System.out.println("Collision nodeB: " + event.getNodeB().getName());
//                //Collided= true;
//            }
//        }
    }
    
    
//    public void collision(PhysicsCollisionEvent event) {
//        try {
//            if (car.isMoving) {
//                if (!collisionString.equals(event.getNodeA().getName() + " botst met " + event.getNodeB().getName())) {
//                    if (event.getNodeA().getName().contains("vehicleNode") && event.getNodeB().getName().contains("vehicleNode")) {
//                        collisionString = event.getNodeA().getName() + " botst met " + event.getNodeB().getName();
//                        System.out.println(collisionString);
//                        //event.getNodeA().removeFromParent();
//                        //event.getNodeA().removeControl(event.getNodeA().getControl(0));
//                        String nodeString = event.getNodeA().getName().substring(11);
//                        int nodeId = Integer.parseInt(nodeString);
//                        System.out.println("node id is " + nodeId + "en car id is " + car.id);
//                        System.out.println("Number is collisions = " + counter);
//                        counter++;
//                        if (nodeId == car.id) {
//                            car.stop();
//                        }
//                        String nodeString2 = event.getNodeB().getName().substring(11);
//                        int nodeId2 = Integer.parseInt(nodeString2);
//                        System.out.println("node id is " + nodeId2 + "en car id is " + car.id);
//                        if (nodeId == car.id) {
//                            car.stop();
//                        }
//                    } else if (event.getNodeA().getName().contains("vehicleNode") && event.getNodeB().getName().contains("Stoplicht")) {
//                        System.out.println("STOPLICHT BEREIKT");
//                        String nodeString = event.getNodeA().getName().substring(11);
//                        int nodeId = Integer.parseInt(nodeString);
//                        if (nodeId == car.id) {
//                            car.stop();
//
//                            counter++;
//                        }
//                    }
//                }
//            }
//        } catch (NullPointerException e) {
//        }
//    }
}
