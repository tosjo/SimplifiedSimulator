/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package SimplifiedSimulator;

/**
 *
 * @author Herman
 */
class SimulationEvent { 
    
    
    public int type;
    public int startPoint;
    public int endPoint;
    public int time;
    
    public SimulationEvent(int type, int start, int end, int time){
        this.type = type;
        this.startPoint = start;
        this.endPoint = end;
        this.time = time;
        
    }    
}
