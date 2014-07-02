package SimplifiedSimulator;

import com.google.gson.Gson;
import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @author Toby Zeldenrust
 */
public class SimplifiedSimulator extends SimpleApplication implements ActionListener {

    /**
     * Variables
     */
    int PORT = 1337;
    String HOSTNAME = "141.252.228.33";
    private static SimplifiedSimulator _instance = null;
    private BulletAppState bulletAppState;
    private Vector3f camStartLocation = new Vector3f(-100, 100, 100);
    public List<Car> CarList = new ArrayList<Car>();
    public List<TrafficLight> LightList = new ArrayList<TrafficLight>();
    List<SimulationEvent> events = new ArrayList<SimulationEvent>();
    int carCounter = 0;
    int lightCounter = 0;
    float timer = 0;
    public WaypointLoader waypointLoader;// = new WaypointLoader("waypoints.txt");
    Thread ReceiverThread;
    Client client;
    float T_timer;
    int time = 0;
    URL urlSmall = SimplifiedSimulator.class.getResource("/xml/small.xml");
    String SMALL_XML = urlSmall.getPath();
    URL urlMedium = SimplifiedSimulator.class.getResource("/xml/medium.xml");
    String MEDIUM_XML = urlMedium.getPath();
    URL urlLarge = SimplifiedSimulator.class.getResource("/xml/large.xml");
    String LARGE_XML = urlLarge.getPath();
    TrafficlightCreater creater = new TrafficlightCreater();
    boolean clientIsSetup;
    boolean clientstarted = false;

    public static void main(String[] args) {
        SimplifiedSimulator app = getInstance();
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

        //bulletAppState.getPhysicsSpace().enableDebug(assetManager);

        //creating the world
        PhysicsHelper.createPhysicsWorld(rootNode, assetManager, bulletAppState.getPhysicsSpace());


        creater.createLights();
        //initiate the client
        this.client = new Client(PORT, HOSTNAME);

        SetupTimer();
        try{
            LoadLargeXml();
        }
        catch(Exception e){
           e.printStackTrace();
        }


        //this.ui = ui;


    }

    @Override
    public void simpleUpdate(float tpf) {
        //TODO: add update code
        timer += tpf;
        T_timer += tpf;

        for (Car car1 : CarList) {
            for (Car car2 : CarList) {
                if (car1.id > car2.id) {
                    car1.GetCollisionCar(car2);
                }
            }

            for (TrafficLight tl : LightList) {
                car1.GetCollisionLight(tl);
                tl.checkCollision(car1);
            }

            //System.out.println(car1.id);
        }

        if (timer >= 1.0f) {
            UpdateCars();
            time++;
            T_timer = 0;
            CheckForEvent(time);
            //System.out.println("time: " + time);
            //System.out.println("timer: " + car1.id);
            timer = 0;
        }        


    }

    public void StartReceiver() {
        //set the client up
        clientIsSetup = client.setup();
        //check if we can start the client en start it.
        if (clientIsSetup == true) {
            ReceiverThread = new Thread(client);
            ReceiverThread.start();
            //timer.start();
            System.out.println("thread running..");
            clientstarted = true;
        } else {
            System.out.println("not setup correctly..");
        }

    }

    public void SetupTimer() {
//        this.T_timer = new Timer(1000, new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                System.out.println("Time: " + time);
//                CheckForEvent(time);
//                time++;
//
//            }
//        });     
        //timer.start();
    }

    public void addEvent(SimulationEvent event) {
        events.add(event);
    }

    public void CheckForEvent(int i) {
        for (SimulationEvent sime : events) {
            if (sime.time == i) {
                //SendMessage(CreateMessage(sime.startPoint));
                createCar(sime.startPoint, sime.endPoint, sime.type);                
            }
        }
    }

    public String CreateMessage(int id) {
        Gson gson = new Gson();
        SensorMessage message = new SensorMessage(new sensorstate(id));

        String msg = gson.toJson(message);
        return msg;
    }

    public void LoadSmallXml() throws Exception {
        try {

            XMLReader reader = new XMLReader(SMALL_XML);
            reader.Parse();

            System.out.println("events count : " + events.size());
        } catch (ExecutionException e) {
        }

    }

    public void HandleMessage(LightMessage message) {
        lightstate state = message.lightstate;
        
        

        for (TrafficLight tl : LightList) {

            if (tl.ID == state.id) {
                switch (state.state) {
                    case 0:
                        tl.SetRed();
                        break;
                    case 1:
                        tl.SetOrange();
                        break;
                    case 2:
                        tl.SetGreen();
                        System.out.println("set green: " + state.id);
                        break;
                    default:
                        break;
                }
            
        }
    }
}
public void SetLightState(TrafficLight tl, int state){
        
        switch(state){
            case 0: tl.SetRed();
                break;
            case 1: tl.SetOrange();
                break;
            case 2: tl.SetGreen();
                break;
            default: break;
        }
        
        
    }

    public void LoadMediumXml() throws Exception {
        try {

            XMLReader reader = new XMLReader(MEDIUM_XML);
            reader.Parse();
        } catch (ExecutionException e) {

        }
    }

    public void LoadLargeXml() throws Exception {
        try {

            XMLReader reader = new XMLReader(LARGE_XML);
            reader.Parse();
        } catch (ExecutionException e) {

        }

    }
    
    public void SendMessage(String msg) {
        client.WriteToServer(msg + '\0');

        System.out.println(msg);

    }

    public void UpdateCars() {
        for (Car car : CarList) {
            //if(car.isMoving)
            //{
            car.tryContinue();
        //}
            //}
        }
    }

    @Override
        public void simpleRender(RenderManager rm) {
        //TODO: add render code
    }

    public void createCar(int start, int end, int type) {

        ColorRGBA color;
        
        switch(type)
        {
            case 0: color = ColorRGBA.Blue;
                    break;
            case 1: color = ColorRGBA.Red;
                break;
            case 2: color = ColorRGBA.Green;
                break;
            case 3: color = ColorRGBA.Yellow;
                break;
            default: color = ColorRGBA.Black;
                break;
        }
        waypointLoader.NewRoadPath(start, end);
        RoadPath path1 = waypointLoader.GetRoadPath(start, end);
        if (path1 != null && type != 1) {
            Car car = new Car(rootNode, path1, carCounter, assetManager, bulletAppState,color);
            carCounter++;
            CarList.add(car);
        }

    }

    public void CreateTrafficLight(int id, float x, float z) {

        TrafficLight tl = new TrafficLight(rootNode, id, x, z, assetManager, bulletAppState);

        LightList.add(tl);
    }

    public void RemoveCar(Car car) {

        CarList.remove(car);
    }

    public void StopCar(Car carToStop) {
        for (Car car : CarList) {
            if (car == carToStop) {
                car.stop();
            }
        }
    }

    private void setUpKeys() {
        inputManager.addMapping("Start", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addListener(this, "Start");

        inputManager.addMapping("Continue", new KeyTrigger(KeyInput.KEY_F));
        inputManager.addListener(this, "Continue");

        inputManager.addMapping("LightRed", new KeyTrigger(KeyInput.KEY_R));
        inputManager.addListener(this, "LightRed");

        inputManager.addMapping("LightGreen", new KeyTrigger(KeyInput.KEY_G));
        inputManager.addListener(this, "LightGreen");
    }

    /**
     * These are our custom actions triggered by key presses.
     */
    public void onAction(String binding, boolean isPressed, float tpf) {
        if (binding.equals("Start")) {
            if (isPressed) {
                //createCar(14,6);
                if(!clientstarted)
                {
                StartReceiver();
                }
            }

        } else if (binding.equals("Continue")) {
            if (isPressed) {
                for (Car car : CarList) {
                    car.tryContinue();
                    //PhysicsHelper.stoplichtGroen();
                }

            }

        } else if (binding.equals("LightRed")) {
            for (TrafficLight tl : LightList) {
                tl.SetRed();
            }

        } else if (binding.equals("LightGreen")) {
            for (TrafficLight tl : LightList) {
                tl.SetGreen();
            }
        }

    }

    public void stop(int id, Car car) {
        car.stop();
    }    
    

    private synchronized static void createInstance() {
        if (_instance == null) {
            _instance = new SimplifiedSimulator();
        }
    }

    public static SimplifiedSimulator getInstance() {
        if (_instance == null) {
            createInstance();
        }
        return _instance;
    }

    
}
