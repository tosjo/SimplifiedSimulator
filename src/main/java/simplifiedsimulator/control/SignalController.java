package simplifiedsimulator.control;

/** Decides when each signal group turns green. */
public interface SignalController extends AutoCloseable {

    /** Short name for display, for example "Vehicle actuated". */
    String name();

    /** Called once before the first {@link #update}. */
    void attach(ControlContext context);

    /** Advances the controller by {@code dt} seconds. */
    void update(double dt);

    /** One line about what the controller is doing, for display. */
    default String status() {
        return "";
    }

    @Override
    default void close() {
    }
}
