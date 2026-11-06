package simplifiedsimulator;

import com.jme3.input.KeyInput;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import simplifiedsimulator.sim.Simulation;

/**
 * Ready-made situations on the function keys: the clock jumps to the time of day (traffic
 * volumes and daylight follow it) and the traffic level changes. The road users on the map
 * stay and the statistics start again, so each situation gets its own score.
 */
public enum Preset {
    MORNING_RUSH("Morning rush", 7.5 * 3600, 1.0, KeyInput.KEY_F5, "F5"),
    EVENING_RUSH("Evening rush", 17 * 3600, 1.1, KeyInput.KEY_F6, "F6"),
    STRESS_TEST("Stress test", 8 * 3600, 2.0, KeyInput.KEY_F7, "F7");

    private final String label;
    private final int key;
    private final String keyName;
    private final double timeOfDay;
    private final double demandScale;

    Preset(String label, double timeOfDay, double demandScale, int key, String keyName) {
        this.label = label;
        this.timeOfDay = timeOfDay;
        this.demandScale = demandScale;
        this.key = key;
        this.keyName = keyName;
    }

    /** The jME key code that starts this preset. */
    public int key() {
        return key;
    }

    public String keyName() {
        return keyName;
    }

    /** Input mapping name for the key. */
    String mapping() {
        return "Preset." + name();
    }

    /** The preset behind an input mapping name, if it is one. */
    static Optional<Preset> forMapping(String mapping) {
        return Arrays.stream(values()).filter(p -> p.mapping().equals(mapping)).findFirst();
    }

    public String label() {
        return label;
    }

    public double timeOfDay() {
        return timeOfDay;
    }

    public double demandScale() {
        return demandScale;
    }

    public void apply(Simulation simulation) {
        simulation.jumpToTimeOfDay(timeOfDay);
        simulation.setDemandScale(demandScale);
        simulation.resetStatistics();
    }

    /** For example "Stress test: 08:00, traffic 200%". */
    public String description() {
        int minutes = (int) Math.round(timeOfDay / 60);
        return String.format(Locale.ROOT, "%s: %02d:%02d, traffic %.0f%%", label, minutes / 60, minutes % 60,
                demandScale * 100);
    }
}
