package net.createplant.arm;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** How an arm moves over the item it works. */
public enum ArmPattern implements StringRepresentable {
    /** The bare arm sets a part on the item. */
    PLACE,
    /** The welding torch runs round the item's edge. */
    SEAM_WELD,
    /** The bare arm sets the ferrite cores row by row. */
    CORE_SET,
    /** The bare arm threads the X, Y and sense wires through cores already set; a helper arm holds the far end of each wire. */
    WIRE_THREAD;

    public static final Codec<ArmPattern> CODEC = StringRepresentable.fromEnum(ArmPattern::values);

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    /** The pattern a tool uses when a recipe step does not name one. */
    public static ArmPattern defaultFor(ArmTool tool) {
        return tool == ArmTool.WELDING_TORCH ? SEAM_WELD : PLACE;
    }

    /**
     * How large the pattern's path is drawn over the item, in blocks per path unit: the core plane is half a block wide as it
     * is, the other paths run round a whole block and are shrunk onto the item.
     */
    public double scale() {
        return this == CORE_SET || this == WIRE_THREAD ? 1 : .45;
    }
}
