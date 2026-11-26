package net.createplant.arm;

import java.util.Locale;

/** What the industrial arm works with: its own claw with nothing fitted, or the welding torch, its only tool. */
public enum ArmTool {
    HAND, WELDING_TORCH;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ArmTool byId(String id) {
        for (ArmTool t : values()) if (t.id().equals(id)) return t;
        return null;
    }
}
