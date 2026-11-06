package simplifiedsimulator.net;

/** State of a traffic light, with the numeric codes used by the controller protocol. */
public enum LightState {
    RED(0),
    ORANGE(1),
    GREEN(2);

    private final int code;

    LightState(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /**
     * @throws IllegalArgumentException if {@code code} is not a known state
     */
    public static LightState fromCode(int code) {
        for (LightState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown light state: " + code);
    }
}
