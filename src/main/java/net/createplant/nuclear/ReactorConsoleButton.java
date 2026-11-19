package net.createplant.nuclear;

/** Physical control centres on the four desk units in operator-facing pixels; shared with the model generator. */
public enum ReactorConsoleButton {
    AZ5(0, 8, 7, 3.5, "AZ-5", "red"),
    AZ5_RESET(0, 8, 13, 2, "RESET", "grey"),
    PREVIOUS_ROD(1, 4, 13, 2, "PREV", "grey"),
    NEXT_ROD(1, 12, 13, 2, "NEXT", "grey"),
    ROD_DIAL(1, 6, 6, 3.5, "ROD", "dial"),
    RELEASE_ROD(1, 13, 6, 2, "LOCAL", "green"),
    MODE(2, 8, 12, 2.5, "MODE", "switch"),
    BANK_DIAL(2, 8, 5, 3.5, "BANK", "dial"),
    CHART(3, 8, 10.5, 6, "PAPER", "area"),
    ACKNOWLEDGE(3, 8, 2.5, 1.5, "ACK", "amber"),
    CRANE_MODE(0, 2.15, 2.6, 1.35, "CRANE", "amber"),
    CRANE_ALONG(0, 2.15, 10.5, 1.55, "RAIL", "grey"),
    CRANE_ACROSS(0, 13.85, 10.5, 1.55, "CROSS", "grey");

    public final int column;
    public final double u, v, radius;
    public final String label, material;
    private static final ReactorConsoleButton[] BUTTONS = values();
    ReactorConsoleButton(int column, double u, double v, double radius, String label, String material) {
        this.column = column;
        this.u = u;
        this.v = v;
        this.radius = radius;
        this.label = label;
        this.material = material;
    }
    /** Dials step down on the operator's left half and up on the right half. */
    public boolean dial() {
        return material.equals("dial");
    }
    public boolean crane() { return this == CRANE_MODE || this == CRANE_ALONG || this == CRANE_ACROSS; }
    public static ReactorConsoleButton at(int section, double u, double v) {
        for (var button : BUTTONS)
            if (button.column == section && Math.abs(u * 16 - button.u) <= button.radius && Math.abs(v * 16 - button.v) <= button.radius)
                return button;
        return null;
    }
}
