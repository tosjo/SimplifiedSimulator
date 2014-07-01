/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package SimplifiedSimulator;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;

/**
 *
 * @author Herman
 */
public class Client  implements Runnable {

    String hostName;
    int portNumber;
    InetAddress address;
    Socket Socket;
    

    char endOfLine = '\0';
    int bEndofLine;
    
    boolean SendHello = false;

    Writer out;
    BufferedReader in;
    BufferedReader stdIn;

    public Client(int port, String hostname) {
        this.hostName = hostname;
        this.portNumber = port;        
    }

    public boolean setup() {
        try {

            this.Socket = new Socket(hostName, portNumber);
            out = new OutputStreamWriter(Socket.getOutputStream());
            in = new BufferedReader(new InputStreamReader(Socket.getInputStream()));
            stdIn = new BufferedReader(new InputStreamReader(System.in));

            return true;
        } catch (Exception e) {
            System.out.println("setup error");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void run() {
        try {
            String newLine = "";
            while (true) {
                
                //if(!SendHello){
                 //   out.write("Hello server!");
                //out.write(sim.CreateMessage());
                //System.out.println(sim.CreateMessage());
                //out.flush();
                //SendHello = true;
                //}

                //out.write("Hello server!, client here!! \n");
                //out.write(sim.CreateMessage());
                //System.out.println(sim.CreateMessage());
                //out.flush();

                //System.out.println("no new line..");
                

                //char[] cBuffer = new char[50];
                int cIn;
                
                cIn = in.read();
                
                if ((cIn == (int)endOfLine)) {

                    HandleIncomingMessage(newLine);
                    System.out.println(newLine);
                    newLine = "";
                } 
                else {
                    newLine += (char) cIn;
                }

                //Thread.sleep(10);
            }
        } catch (Exception e) {
            System.out.println("error in run");
        }
    }
    
    public void WriteToServer(String msg)
    {
        try
        {
            out.write(msg);
            out.flush();
            Thread.sleep(50);
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    public void HandleIncomingMessage(String msg) {
        Gson gson = new Gson();
        
        msg = msg.toLowerCase();

        try {
            LightMessage message = gson.fromJson(msg, LightMessage.class);

            lightstate state = message.lightstate;

            //System.out.println("id: " + state.id);
            //sim.appendText("Location: " + state.Location);
            //sim.appendText("Direction: " + state.Direction);
            //System.out.println("State: " + state.state);
            SimplifiedSimulator.getInstance().HandleMessage(message);
        }
        catch(Exception e)
        {
            System.out.println("Wrong message: " + msg);
        }
    }


//    String fromServer;
//    String fromUser;
//
//    
//        try {
//            echoSocket = new Socket(hostName, portNumber);
//        Writer out = new OutputStreamWriter(echoSocket.getOutputStream());
//        BufferedReader in = new BufferedReader(new InputStreamReader(echoSocket.getInputStream()));
//        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
//        String inputLine;
//
//        fromUser = stdIn.readLine();
//        if (fromUser != null) {
//            System.out.println("Client: " + fromUser);
//            out.write(fromUser);
//            out.flush();
//        }
//
//        while ((fromServer = in.readLine()) != null) {
//            System.out.println("Server: " + fromServer);
//            break;
//
//        }
//    }
//    catch (IOException e
//
//    
//        ) {
//            System.out.println(e.toString());
//    }
}

    

