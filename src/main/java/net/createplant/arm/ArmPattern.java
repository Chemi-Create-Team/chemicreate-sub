package net.createplant.arm;

import java.util.Locale;

/** How an arm moves over its cell. PLACE and LAY are the bare arm's own drops and are not written in recipes. */
public enum ArmPattern {
    PLACE, SEAM_WELD,
    /** The bare arm sets the ferrite cores row by row. */
    CORE_SET,
    /** The bare arm threads the X, Y and sense wires through cores already set; a helper arm holds the far end of each wire. */
    WIRE_THREAD,
    /** The bare arm's drop onto the jig plate for a laid item. */
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
        return tool == ArmTool.WELDING_TORCH ? SEAM_WELD : PLACE;
    }
}
