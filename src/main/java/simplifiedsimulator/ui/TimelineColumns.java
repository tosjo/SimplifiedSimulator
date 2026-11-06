package simplifiedsimulator.ui;

import java.util.List;
import simplifiedsimulator.control.SignalPlan;
import simplifiedsimulator.sim.SignalState;

/** Turns the colour history of one signal group into one state per pixel column. */
final class TimelineColumns {

    private TimelineColumns() {
    }

    /**
     * @param history  colour changes in time order
     * @param current  the colour shown now (used when the history is empty)
     * @param now      the current simulation time
     * @param columns  number of columns; the last one is {@code now}
     * @param seconds  time span shown, in seconds
     * @return the state for every column, oldest first; {@code null} where nothing is known yet
     */
    static SignalState[] compute(List<SignalPlan.Change> history, SignalState current, double now, int columns,
                                 double seconds) {
        SignalState[] out = new SignalState[columns];
        if (history.isEmpty()) {
            java.util.Arrays.fill(out, current);
            return out;
        }
        int index = 0;
        SignalState state = null;
        for (int c = 0; c < columns; c++) {
            double t = now - seconds * (columns - 1 - c) / (columns - 1);
            while (index < history.size() && history.get(index).time() <= t) {
                state = history.get(index).state();
                index++;
            }
            out[c] = state;
        }
        return out;
    }
}
