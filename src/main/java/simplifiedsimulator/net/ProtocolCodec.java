package simplifiedsimulator.net;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Locale;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Encodes and decodes the JSON messages exchanged with the traffic light controller.
 *
 * <ul>
 *   <li>Simulator to controller, when a road user arrives at a light:
 *       {@code {"sensorstate":{"id":5}}}</li>
 *   <li>Controller to simulator, to switch a light:
 *       {@code {"lightstate":{"id":5,"state":2}}} (0 = red, 1 = orange, 2 = green)</li>
 *   <li>Controller to simulator, optional, to say who it is:
 *       {@code {"controllername":{"name":"Smart"}}} (shown on screen)</li>
 *   <li>Optional, only for controllers that ask for it with {@code {"features":["sensorfree"]}}:
 *       when the last road user leaves a detector, {@code {"sensorfree":{"id":5}}}. Together
 *       with the sensor messages this tells the controller who is still standing on a loop.</li>
 *   <li>Lockstep, only when switched on (for testing controllers): after the sensor messages
 *       of a step the simulator sends {@code {"clock":{"time":12.3}}} and waits until the
 *       controller has sent its light states followed by {@code {"clockdone":{"time":12.3}}}.
 *       The first lockstep step starts with an extra clock message before any sensor message.</li>
 * </ul>
 *
 * On the wire every message is terminated by a NUL character, see {@link ControllerClient}.
 * Incoming keys are matched case-insensitively, as controllers written by different
 * teams did not agree on capitalisation.
 */
public final class ProtocolCodec {

    private static final Gson GSON = new Gson();
    /** Longest controller name shown on screen. */
    public static final int MAX_NAME_LENGTH = 20;

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

    /** Builds the message that the last road user left a detector of {@code lightId}. */
    public static String encodeSensorFree(int lightId) {
        return GSON.toJson(new SensorFreeMessage(new IdHolder(lightId)));
    }

    /**
     * Decodes the features a controller asks for, lower case.
     *
     * @return the features, or empty if the message is not a features message
     */
    public static Optional<Set<String>> decodeFeatures(String message) {
        try {
            JsonElement list = member(JsonParser.parseString(message), "features");
            if (list == null || !list.isJsonArray()) {
                return Optional.empty();
            }
            Set<String> features = new HashSet<>();
            for (JsonElement e : list.getAsJsonArray()) {
                if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
                    return Optional.empty();
                }
                features.add(e.getAsString().toLowerCase(Locale.ROOT));
            }
            return Optional.of(Set.copyOf(features));
        } catch (JsonParseException e) {
            return Optional.empty();
        }
    }

    /** Builds the lockstep clock message for simulated time {@code time}. */
    public static String encodeClock(double time) {
        return GSON.toJson(new ClockMessage(new TimeHolder(time)));
    }

    /**
     * Decodes the controller's lockstep reply.
     *
     * @return the simulated time it answers, or empty if the message is not a clock reply
     */
    public static OptionalDouble decodeClockDone(String message) {
        try {
            JsonElement holder = member(JsonParser.parseString(message), "clockdone");
            JsonElement time = holder == null ? null : member(holder, "time");
            if (time == null || !time.isJsonPrimitive() || !time.getAsJsonPrimitive().isNumber()) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(time.getAsDouble());
        } catch (JsonParseException | NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Decodes a controller name message. The name is shown on screen, so it must be 1 to
     * {@link #MAX_NAME_LENGTH} plain ASCII characters.
     *
     * @return the name, or empty if the message is not a valid name message
     */
    public static Optional<String> decodeName(String message) {
        try {
            JsonElement root = JsonParser.parseString(message);
            JsonElement holder = member(root, "controllername");
            JsonElement name = holder == null ? null : member(holder, "name");
            if (name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()) {
                return Optional.empty();
            }
            String text = name.getAsString().trim();
            boolean plain = text.chars().allMatch(c -> c >= 32 && c <= 126);
            return plain && !text.isEmpty() && text.length() <= MAX_NAME_LENGTH ? Optional.of(text) : Optional.empty();
        } catch (JsonParseException e) {
            return Optional.empty();
        }
    }

    /** A member of a JSON object by name, ignoring case like the light messages; null if absent. */
    private static JsonElement member(JsonElement element, String key) {
        if (!element.isJsonObject()) {
            return null;
        }
        for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    // Gson data holders. Field names define the JSON keys.

    private record IdHolder(int id) {
    }

    private record SensorMessage(IdHolder sensorstate) {
    }

    private record SensorFreeMessage(IdHolder sensorfree) {
    }

    private record TimeHolder(double time) {
    }

    private record ClockMessage(TimeHolder clock) {
    }

    private static final class LightMessage {
        LightStateHolder lightstate;
    }

    private static final class LightStateHolder {
        Integer id;
        Integer state;
    }
}
