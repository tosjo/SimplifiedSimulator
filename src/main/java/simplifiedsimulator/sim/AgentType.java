package simplifiedsimulator.sim;

/** The kinds of road user in the simulation, with their typical size in metres. */
public enum AgentType {
    CAR(4.4, 1.8),
    VAN(5.3, 2.0),
    TRUCK(10.0, 2.55),
    BUS(12.0, 2.55),
    BICYCLE(1.8, 0.65),
    PEDESTRIAN(0.5, 0.6);

    private final double length;
    private final double width;

    AgentType(double length, double width) {
        this.length = length;
        this.width = width;
    }

    public double length() {
        return length;
    }

    public double width() {
        return width;
    }

    public boolean isMotorVehicle() {
        return this == CAR || this == VAN || this == TRUCK || this == BUS;
    }
}
