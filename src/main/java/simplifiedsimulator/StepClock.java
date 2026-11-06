package simplifiedsimulator;

/**
 * Turns frame times into fixed simulation steps, at an adjustable speed. Shared by the single
 * window and the duel, so both sides of a duel always take the same number of steps.
 */
final class StepClock {

    static final double MIN_SPEED = 0.25;
    static final double MAX_SPEED = 64;
    /** Upper limit of simulation work per frame, so the window stays responsive at high speeds. */
    static final double MAX_STEPS_PER_FRAME = 400;

    private final double timeStep;
    private double speed;
    private boolean paused;
    private double accumulator;

    StepClock(double timeStep, double speed) {
        if (!(timeStep > 0)) {
            throw new IllegalArgumentException("Time step must be positive, got " + timeStep);
        }
        this.timeStep = timeStep;
        setSpeed(speed);
    }

    /** Adds a frame of {@code tpf} seconds and returns how many simulation steps to take now. */
    int advance(float tpf) {
        if (paused) {
            return 0;
        }
        accumulator = Math.min(accumulator + tpf * speed, MAX_STEPS_PER_FRAME * timeStep);
        // counted, not subtracted step by step, so rounding cannot lose the last step
        int steps = (int) Math.floor(accumulator / timeStep + 1e-9);
        accumulator = Math.max(0, accumulator - steps * timeStep);
        return steps;
    }

    /** Fraction (0 to 1) of a step that has passed since the last step, for smooth drawing. */
    float alpha() {
        return (float) Math.min(accumulator / timeStep, 0.999);
    }

    double speed() {
        return speed;
    }

    void setSpeed(double value) {
        speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, value));
    }

    boolean paused() {
        return paused;
    }

    void setPaused(boolean value) {
        paused = value;
    }

    void togglePause() {
        paused = !paused;
    }
}
