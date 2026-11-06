package simplifiedsimulator.net;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.Locale;
import java.util.Optional;

/**
 * Encodes and decodes the JSON messages exchanged with the traffic light controller.
 *
 * <ul>
 *   <li>Simulator to controller, when a road user arrives at a light:
 *       {@code {"sensorstate":{"id":5}}}</li>
 *   <li>Controller to simulator, to switch a light:
 *       {@code {"lightstate":{"id":5,"state":2}}} (0 = red, 1 = orange, 2 = green)</li>
 * </ul>
 *
 * On the wire every message is terminated by a NUL character, see {@link ControllerClient}.
 * Incoming keys are matched case-insensitively, as controllers written by different
 * teams did not agree on capitalisation.
 */
public final class ProtocolCodec {

    private static final Gson GSON = new Gson();

    private ProtocolCodec() {
    }

    /** Builds the message announcing that a road user is waiting at {@code lightId}. */
    public static String encodeSensor(int lightId) {
        return GSON.toJson(new SensorMessage(new IdHolder(lightId)));
    }

    /**
     * Decodes a light state message.
     *
     * @return the update, or empty if the message is not a valid light state message
     */
    public static Optional<LightUpdate> decodeLight(String message) {
        try {
            LightMessage parsed = GSON.fromJson(message.toLowerCase(Locale.ROOT), LightMessage.class);
            if (parsed == null || parsed.lightstate == null
                    || parsed.lightstate.id == null || parsed.lightstate.state == null) {
                return Optional.empty();
            }
            return Optional.of(new LightUpdate(parsed.lightstate.id, LightState.fromCode(parsed.lightstate.state)));
        } catch (JsonParseException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // Gson data holders. Field names define the JSON keys.

    private record IdHolder(int id) {
    }

    private record SensorMessage(IdHolder sensorstate) {
    }

    private static final class LightMessage {
        LightStateHolder lightstate;
    }

    private static final class LightStateHolder {
        Integer id;
        Integer state;
    }
}
