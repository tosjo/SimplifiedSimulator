package simplifiedsimulator.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A set of lights that always show the same colour (a "signaalgroep"). Numbered the Dutch way:
 * 01-12 motor traffic, 2x cyclists, 3x pedestrians, 4x buses.
 */
public final class SignalGroup {

    private final String id;
    private final Mode mode;
    private final String name;
    private final String approach;
    private final List<SignalHead> heads;
    private final List<Route> routes = new ArrayList<>();

    SignalGroup(String id, Mode mode, String name, String approach, List<SignalHead> heads) {
        this.id = id;
        this.mode = mode;
        this.name = name;
        this.approach = approach;
        this.heads = List.copyOf(heads);
    }

    public String id() {
        return id;
    }

    /** Numeric id as used by the controller protocol. */
    public int number() {
        return Integer.parseInt(id);
    }

    public Mode mode() {
        return mode;
    }

    public String name() {
        return name;
    }

    /** Id of the approach ({@code N}, {@code E}, {@code S}, {@code W}). */
    public String approach() {
        return approach;
    }

    public List<SignalHead> heads() {
        return heads;
    }

    /** Routes controlled by this group. */
    public List<Route> routes() {
        return Collections.unmodifiableList(routes);
    }

    void addRoute(Route route) {
        routes.add(route);
    }

    @Override
    public String toString() {
        return id + " (" + name + ")";
    }
}
