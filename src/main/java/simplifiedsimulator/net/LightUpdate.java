package simplifiedsimulator.net;

import java.util.Objects;

/**
 * Instruction from the controller to switch traffic light {@code lightId} to {@code state}.
 */
public record LightUpdate(int lightId, LightState state) {

    public LightUpdate {
        Objects.requireNonNull(state, "state");
    }
}
