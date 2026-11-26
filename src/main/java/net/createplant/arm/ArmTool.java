package net.createplant.arm;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** What the industrial arm works with: its own claw with nothing fitted, or the welding torch, its only tool. */
public enum ArmTool implements StringRepresentable {
    HAND, WELDING_TORCH;

    public static final Codec<ArmTool> CODEC = StringRepresentable.fromEnum(ArmTool::values);

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return id();
    }
}
