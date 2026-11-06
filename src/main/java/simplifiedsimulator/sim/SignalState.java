package simplifiedsimulator.sim;

/** What a traffic light shows. Dutch lights go green, amber (geel), red. */
public enum SignalState {
    RED, AMBER, GREEN;

    public boolean allowsEntry() {
        return this == GREEN;
    }
}
