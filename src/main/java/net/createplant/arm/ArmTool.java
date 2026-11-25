package net.createplant.arm;

import java.util.Locale;

/** The five end effectors of the industrial arm. */
public enum ArmTool {
    GRIPPER, WELDING_TORCH, LASER_CUTTER, SPRAY_NOZZLE, WIRING_HEAD;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ArmTool byId(String id) {
        for (ArmTool t : values()) if (t.id().equals(id)) return t;
        return null;
    }
}
