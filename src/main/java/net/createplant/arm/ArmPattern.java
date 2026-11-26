package net.createplant.arm;

import java.util.Locale;

/** How a tool moves over its cell. PLACE is the gripper's own drop and is not written in recipes. */
public enum ArmPattern {
    PLACE, SEAM_WELD, PERIMETER_CUT, DIAGONAL_CUT, GRID_WIRE, FULL_SPRAY,
    /** The first half of GRID_WIRE alone: setting the ferrite cores. */
    CORE_SET,
    /** The second half of GRID_WIRE alone: threading the X, Y and sense wires through cores already set. */
    WIRE_THREAD,
    /** The gripper's drop onto the jig plate for a laid item. Not written in recipes. */
    LAY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ArmPattern byId(String id) {
        for (ArmPattern p : values()) if (p.id().equals(id)) return p;
        return null;
    }

    /** The pattern a tool uses when a recipe step does not name one. */
    public static ArmPattern defaultFor(ArmTool tool) {
        return switch (tool) {
            case GRIPPER -> PLACE;
            case WELDING_TORCH -> SEAM_WELD;
            case LASER_CUTTER -> PERIMETER_CUT;
            case SPRAY_NOZZLE -> FULL_SPRAY;
            case WIRING_HEAD -> GRID_WIRE;
        };
    }
}
