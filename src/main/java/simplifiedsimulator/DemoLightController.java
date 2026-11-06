package simplifiedsimulator;

import java.util.List;
import java.util.function.BiConsumer;
import simplifiedsimulator.net.LightState;

/**
 * A simple fixed-time light controller, so the simulator can be watched without
 * running an external controller. Each phase is green, then orange, then all lights
 * are red for a moment before the next phase starts.
 */
public final class DemoLightController {

    static final float GREEN_SECONDS = 8f;
    static final float ORANGE_SECONDS = 2f;
    static final float CLEARANCE_SECONDS = 1f;

    private final List<List<Integer>> phases;
    private final BiConsumer<Integer, LightState> output;

    private int phase;
    private float phaseTime;
    private LightState current;

    /**
     * @param phases groups of light ids that are green at the same time
     * @param output receives every light state change
     */
    public DemoLightController(List<List<Integer>> phases, BiConsumer<Integer, LightState> output) {
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("At least one phase is required");
        }
        this.phases = List.copyOf(phases);
        this.output = output;
    }

    /** Sets every light to red and starts at the first phase. */
    public void reset() {
        phases.forEach(group -> set(group, LightState.RED));
        phase = 0;
        phaseTime = 0f;
        current = null;
    }

    /** Advances the controller by {@code tpf} seconds. */
    public void update(float tpf) {
        phaseTime += tpf;
        float cycle = GREEN_SECONDS + ORANGE_SECONDS + CLEARANCE_SECONDS;
        while (phaseTime >= cycle) {
            phaseTime -= cycle;
            if (current != LightState.RED) {
                // A long frame skipped the clearance time; make sure nothing stays green.
                set(phases.get(phase), LightState.RED);
            }
            phase = (phase + 1) % phases.size();
            current = null;
        }

        LightState wanted;
        if (phaseTime < GREEN_SECONDS) {
            wanted = LightState.GREEN;
        } else if (phaseTime < GREEN_SECONDS + ORANGE_SECONDS) {
            wanted = LightState.ORANGE;
        } else {
            wanted = LightState.RED;
        }

        if (wanted != current) {
            set(phases.get(phase), wanted);
            current = wanted;
        }
    }

    private void set(List<Integer> group, LightState state) {
        group.forEach(id -> output.accept(id, state));
    }
}
