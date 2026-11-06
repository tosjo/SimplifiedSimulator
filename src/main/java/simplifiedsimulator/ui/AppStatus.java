package simplifiedsimulator.ui;

/**
 * What the application tells the head-up display each frame.
 *
 * @param speed  simulation speed relative to real time
 * @param paused whether the simulation is paused
 * @param fps    frames per second, or -1 to leave it out
 */
public record AppStatus(double speed, boolean paused, int fps) {
}
