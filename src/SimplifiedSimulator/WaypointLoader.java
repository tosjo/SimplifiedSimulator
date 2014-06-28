/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package SimplifiedSimulator;

import com.jme3.cinematic.MotionPath;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Herman
 */
public class WaypointLoader {

    List<RoadPath> paths = new ArrayList<RoadPath>();
    String filePath;

    public WaypointLoader(String filePath) {
        this.filePath = filePath;
    }

    public void NewRoadPath(int StartPoint, int Endpoint) {
        RoadPath path = new RoadPath(StartPoint, Endpoint, filePath);

        paths.add(path);
    }

    public void LoadRoadpaths() {
        NewRoadPath(14, 6);
        NewRoadPath(16, 13);
    }

    public RoadPath GetRoadPath(int start, int end) {

        RoadPath returner = null;

        if (roadExists(start, end)) {
            for (RoadPath road : paths) {
                if (road.StartPoint == start && road.EndPoint == end) {
                    returner = road;
                }
            }
        } else {
            returner = null;
        }

        return returner;

    }

    private Boolean roadExists(int start, int end) {
        Boolean returner = false;
        for (RoadPath road : paths) {
            if (road.StartPoint == start && road.EndPoint == end) {
                returner = true;
                break;
            }
        }
        return returner;
    }
}
