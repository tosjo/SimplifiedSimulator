/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package SimplifiedSimulator;

import com.jme3.cinematic.MotionPath;
import com.jme3.math.Vector3f;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Herman
 */
public class RoadPath {
    
    public int StartPoint;
    public int EndPoint;
    
    private float x;
    private float y;
    private float z;
    
    String fileName;
    
    MotionPath path = new MotionPath();  
    
    public RoadPath(int StartPoint, int EndPoint, String fileName)
    {
        this.StartPoint = StartPoint;
        this.EndPoint = EndPoint;
        this.fileName = fileName + StartPoint + "-" + EndPoint + ".txt";
        System.out.println(this.fileName);
        CreatePath();
    }
    
    private void CreatePath()
    {
        try {
            Scanner in = new Scanner(new FileReader(fileName));
            String waypointsFromFile = in.nextLine();
            String correctWPFromFile = waypointsFromFile.replace("(", "");
            String correctWPFromFile2 = correctWPFromFile.replace(" ", "");
            String formattedWayPoints[] = correctWPFromFile2.split("\\)");
            for (int i = 0; i < formattedWayPoints.length; i++) {
                String[] vectorWP = formattedWayPoints[i].split(",");
                  x = Float.valueOf(vectorWP[0]);
                y = Float.valueOf(vectorWP[1]);
                z = Float.valueOf(vectorWP[2]);
                //System.out.println("dit zijn xyz"+x + y + z);
                Vector3f road = new Vector3f(x, y, z);
                addWayPoint(road);
                System.out.println("added waypoint: " + road.toString());
                //pathLoaded = true;

            }
            //setPath();

        } catch (FileNotFoundException ex) {
            Logger.getLogger(SimplifiedSimulator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void addWayPoint(Vector3f waypointLocation) {
        Vector3f flatLocation = waypointLocation;
        flatLocation.y = 1.5f;

        path.addWayPoint(flatLocation);
    }
    
    
    public String ToString()
    {
        String returner = "";
        
        path.getWayPoint(1).toString();
        
        return returner;
        
    }
}
