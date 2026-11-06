package simplifiedsimulator.sim;

import java.util.Set;
import simplifiedsimulator.control.ControlContext;
import simplifiedsimulator.control.SignalController;
import simplifiedsimulator.network.SignalGroup;

/** Simple controllers for tests. */
final class TestControllers {

    private TestControllers() {
    }

    /** Never gives green. */
    static SignalController allRed() {
        return new SignalController() {
            @Override
            public String name() {
                return "all red";
            }

            @Override
            public void attach(ControlContext context) {
            }

            @Override
            public void update(double dt) {
            }
        };
    }

    /** Turns the given groups green as soon as allowed and keeps them green. */
    static SignalController alwaysGreen(Set<String> groups) {
        return new SignalController() {
            private ControlContext ctx;

            @Override
            public String name() {
                return "always green";
            }

            @Override
            public void attach(ControlContext context) {
                ctx = context;
            }

            @Override
            public void update(double dt) {
                for (SignalGroup g : ctx.plan().groups()) {
                    if (groups.contains(g.id()) && ctx.plan().mayStartGreen(g)) {
                        ctx.plan().startGreen(g);
                    }
                }
            }
        };
    }
}
