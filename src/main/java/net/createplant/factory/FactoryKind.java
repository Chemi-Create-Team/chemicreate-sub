package net.createplant.factory;

import net.minecraft.util.StringRepresentable;

public enum FactoryKind implements StringRepresentable {
    KILN("rotary_kiln", 96), MILL("rolling_mill", 0), WINDER("coil_winder", 48);

    public static final StringRepresentable.EnumCodec<FactoryKind> CODEC = StringRepresentable.fromEnum(FactoryKind::values);
    public final String id;
    public final double stress;

    FactoryKind(String id, double stress) { this.id = id; this.stress = stress; }
    @Override public String getSerializedName() { return id; }
}
