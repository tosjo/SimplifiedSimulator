package simplifiedsimulator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import simplifiedsimulator.net.LightState;

class DemoLightControllerTest {

    private final Map<Integer, LightState> lights = new HashMap<>();
    private final DemoLightController controller =
            new DemoLightController(List.of(List.of(1, 2), List.of(3)), lights::put);

    @Test
    void resetTurnsEverythingRed() {
        controller.reset();

        assertEquals(Map.of(1, LightState.RED, 2, LightState.RED, 3, LightState.RED), lights);
    }

    @Test
    void cyclesThroughPhases() {
        controller.reset();

        controller.update(0.1f);
        assertEquals(Map.of(1, LightState.GREEN, 2, LightState.GREEN, 3, LightState.RED), lights);

        controller.update(DemoLightController.GREEN_SECONDS);
        assertEquals(LightState.ORANGE, lights.get(1));

        controller.update(DemoLightController.ORANGE_SECONDS);
        assertEquals(Map.of(1, LightState.RED, 2, LightState.RED, 3, LightState.RED), lights);

        controller.update(DemoLightController.CLEARANCE_SECONDS);
        assertEquals(Map.of(1, LightState.RED, 2, LightState.RED, 3, LightState.GREEN), lights);
    }

    @Test
    void longFrameDoesNotLeaveAPhaseGreen() {
        controller.reset();
        controller.update(0.1f);

        // Jump from the middle of phase 0 straight into phase 1.
        controller.update(DemoLightController.GREEN_SECONDS + DemoLightController.ORANGE_SECONDS
                + DemoLightController.CLEARANCE_SECONDS);

        assertEquals(Map.of(1, LightState.RED, 2, LightState.RED, 3, LightState.GREEN), lights);
    }
}
