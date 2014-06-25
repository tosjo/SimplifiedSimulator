/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package SimplifiedSimulator;

import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.scene.Node;

/**
 *
 * @author Toby Zeldenrust <toby.zeldenrust.org>
 */
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.bullet.control.RigidBodyControl;

public class MyCustomControl extends RigidBodyControl implements PhysicsCollisionListener {

    public void collision(PhysicsCollisionEvent event) {
        try {
            if (event.getNodeA().getName().contains("Car") || event.getNodeB().getName().contains("Car")) {
                System.out.println(event.getNodeA().getName() + "   " + event.getNodeB().getName());
            } else if (event.getNodeA().getName().contains("Box") && event.getNodeB().getName().contains("Car")) {
                System.out.println(event.getNodeA().getName() + "   " + event.getNodeB().getName());
            } else if (event.getNodeA().getName().contains("Car") && event.getNodeB().getName().contains("Box")) {
                System.out.println(event.getNodeA().getName() + "   " + event.getNodeB().getName());
            }
        } catch (NullPointerException e) {
        }
    }

    public MyCustomControl(BulletAppState bulletAppState) {
        bulletAppState.getPhysicsSpace().addCollisionListener(this);
    }
}
