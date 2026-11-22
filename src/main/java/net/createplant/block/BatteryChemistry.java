package net.createplant.block;

import java.util.function.Supplier;
import net.createplant.registry.CEBattery;
import net.createplant.registry.CEMetals;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/** The two storage batteries: open-circuit volts when full, ohms of one casing (casings are in parallel), joules and electrolyte per casing. */
public enum BatteryChemistry {
    LEAD_ACID("lead_acid", 200, 40, 100_000, .90, .12, () -> CEBattery.LEAD_ACID_CASING.get(), () -> CEMetals.DILUTE_SULFURIC_ACID.get()),
    LITHIUM_ION("lithium_ion", 300, 24, 300_000, .96, .30, () -> CEBattery.LITHIUM_ION_CASING.get(), () -> CEProcessFluids.ACETONE.get());

    public static final int ELECTROLYTE_PER_CASING = 250;
    public final String id;
    public final double fullVolts, ohmsPerCasing, joulesPerCasing, efficiency, sag;
    private final Supplier<Block> casing;
    private final Supplier<Fluid> electrolyte;

    BatteryChemistry(String id, double fullVolts, double ohmsPerCasing, double joulesPerCasing, double efficiency, double sag, Supplier<Block> casing, Supplier<Fluid> electrolyte) {
        this.id = id;this.fullVolts = fullVolts;this.ohmsPerCasing = ohmsPerCasing;this.joulesPerCasing = joulesPerCasing;this.efficiency = efficiency;this.sag = sag;
        this.casing = casing;this.electrolyte = electrolyte;
    }

    public Block casing() { return casing.get(); }
    public Fluid electrolyte() { return electrolyte.get(); }
    /** Open-circuit voltage: it falls by {@code sag} of the full voltage as the charge runs out, and is zero when empty. */
    public double volts(double stateOfCharge) { return stateOfCharge <= 0 ? 0 : fullVolts * (1 - sag * (1 - Math.min(stateOfCharge, 1))); }
}
