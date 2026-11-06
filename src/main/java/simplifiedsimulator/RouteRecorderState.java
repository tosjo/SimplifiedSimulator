package simplifiedsimulator;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Sphere;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import simplifiedsimulator.scenario.RouteRecorder;

/**
 * Lets the user draw a new route on the map: right-click adds a waypoint where the
 * mouse points at the floor, and the result is saved in the route file format.
 */
public final class RouteRecorderState extends BaseAppState implements ActionListener {

    private static final Logger LOG = Logger.getLogger(RouteRecorderState.class.getName());

    private static final String ADD = "RecordAdd";
    private static final String UNDO = "RecordUndo";
    private static final String CLEAR = "RecordClear";
    private static final String SAVE = "RecordSave";

    private final Spatial floor;
    private final Path output;
    private final RouteRecorder recorder = new RouteRecorder();
    private final Node markers = new Node("RouteMarkers");

    private Material markerMaterial;
    private BitmapText help;
    private String lastMessage = "";

    /**
     * @param floor  the geometry waypoints are placed on
     * @param output file the route is written to
     */
    public RouteRecorderState(Spatial floor, Path output) {
        this.floor = floor;
        this.output = output;
    }

    @Override
    protected void initialize(Application app) {
        markerMaterial = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        markerMaterial.setColor("Color", ColorRGBA.Magenta);

        BitmapFont font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        help = new BitmapText(font);
        help.setSize(font.getCharSet().getRenderedSize());
        help.setColor(ColorRGBA.Yellow);
        help.setLocalTranslation(10f, help.getLineHeight() * 2 + 10f, 0f);
    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    protected void onEnable() {
        SimpleApplication app = (SimpleApplication) getApplication();
        app.getRootNode().attachChild(markers);
        app.getGuiNode().attachChild(help);

        InputManager input = app.getInputManager();
        input.addMapping(ADD, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        input.addMapping(UNDO, new KeyTrigger(KeyInput.KEY_BACK));
        input.addMapping(CLEAR, new KeyTrigger(KeyInput.KEY_C));
        input.addMapping(SAVE, new KeyTrigger(KeyInput.KEY_RETURN));
        input.addListener(this, ADD, UNDO, CLEAR, SAVE);
        updateHelp();
    }

    @Override
    protected void onDisable() {
        SimpleApplication app = (SimpleApplication) getApplication();
        markers.removeFromParent();
        help.removeFromParent();
        InputManager input = app.getInputManager();
        for (String mapping : new String[] {ADD, UNDO, CLEAR, SAVE}) {
            input.deleteMapping(mapping);
        }
        input.removeListener(this);
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!isPressed) {
            return;
        }
        switch (name) {
            case ADD -> addAtCursor();
            case UNDO -> {
                recorder.undo();
                if (markers.getQuantity() > 0) {
                    markers.detachChildAt(markers.getQuantity() - 1);
                }
            }
            case CLEAR -> {
                recorder.clear();
                markers.detachAllChildren();
            }
            case SAVE -> save();
            default -> {
            }
        }
        updateHelp();
    }

    private void addAtCursor() {
        Camera cam = getApplication().getCamera();
        Vector2f cursor = getApplication().getInputManager().getCursorPosition();
        Vector3f origin = cam.getWorldCoordinates(cursor, 0f);
        Vector3f direction = cam.getWorldCoordinates(cursor, 1f).subtractLocal(origin).normalizeLocal();

        CollisionResults results = new CollisionResults();
        floor.collideWith(new Ray(origin, direction), results);
        if (results.size() == 0) {
            lastMessage = "Point at the map to add a waypoint";
            return;
        }
        recorder.add(results.getClosestCollision().getContactPoint());

        Geometry marker = new Geometry("Waypoint", new Sphere(10, 10, 2f));
        marker.setMaterial(markerMaterial);
        marker.setLocalTranslation(recorder.waypoints().get(recorder.waypoints().size() - 1));
        markers.attachChild(marker);
        lastMessage = "";
    }

    private void save() {
        if (recorder.waypoints().size() < 2) {
            lastMessage = "A route needs at least two waypoints";
            return;
        }
        try {
            Files.writeString(output, recorder.format());
            lastMessage = "Saved to " + output.toAbsolutePath();
            LOG.info(lastMessage);
        } catch (IOException e) {
            lastMessage = "Could not save: " + e.getMessage();
            LOG.warning(lastMessage);
        }
    }

    private void updateHelp() {
        help.setText(String.format(
                "Recording route: %d waypoints   [Right click] add  [Backspace] undo  [C] clear  [Enter] save%n%s",
                recorder.waypoints().size(), lastMessage));
    }
}
