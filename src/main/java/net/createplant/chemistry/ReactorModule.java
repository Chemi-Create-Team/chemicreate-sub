package net.createplant.chemistry;

import net.minecraft.util.StringRepresentable;

public enum ReactorModule implements StringRepresentable {
    NONE(null,0,1), PREHEATER(ProcessModule.PROCESS_HEATER,8,1),
    STIRRED(ProcessModule.STIRRED_REACTOR,16,1),
    /** Marks the heated gas-solid reactions that run on the reaction tower's trays without a catalyst. */
    ABSORBER(null,0,2),
    /** Counter-current gas absorption in the absorption tower, without rotation or heat. */
    TOWER(ProcessModule.ABSORPTION_TRAYS,0,4);
    public final ProcessModule process;
    public final int stress, minSections;
    ReactorModule(ProcessModule process,int stress,int minSections) { this.process=process;this.stress=stress;this.minSections=minSections; }
    @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
}
