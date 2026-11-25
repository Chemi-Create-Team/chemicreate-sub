package net.createplant.thermal;

import java.util.List;
import java.util.Set;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.createplant.block.entity.AmmoniaSeparatorBlockEntity;
import net.createplant.block.entity.GasHolderBlockEntity;
import net.createplant.block.entity.GasTankContainerBlockEntity;
import net.createplant.block.entity.HighPressureSeparatorBlockEntity;
import net.createplant.block.entity.HorizontalTankBlockEntity;
import net.createplant.registry.CETransport;
import net.createplant.transport.FluidHazards;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;

public enum TankGrade {
    STANDARD(Grade.Heat.COLD, Grade.Heat.HOT, Grade.Pressure.PRESSURISED, Kind.ALL),
    LIQUID(Grade.Heat.COLD, Grade.Heat.HOT, Grade.Pressure.PRESSURISED, Kind.LIQUID),
    GAS(Grade.Heat.COLD, Grade.Heat.HOT, Grade.Pressure.HIGH, Kind.GAS),
    PRESSURE(Grade.Heat.COLD, Grade.Heat.HOT, Grade.Pressure.HIGH, Kind.LIQUID),
    STAINLESS(Grade.Heat.COLD, Grade.Heat.VERY_HOT, Grade.Pressure.PRESSURISED, Kind.LIQUID, FluidHazards.Corrosion.ACID, FluidHazards.Corrosion.HALOGEN, FluidHazards.Corrosion.BASE),
    CRYOGENIC(Grade.Heat.CRYOGENIC, Grade.Heat.AMBIENT, Grade.Pressure.PRESSURISED, Kind.LIQUID),
    OPEN(Grade.Heat.COLD, Grade.Heat.AMBIENT, Grade.Pressure.ATMOSPHERIC, Kind.LIQUID),
    HOLDER(Grade.Heat.COLD, Grade.Heat.AMBIENT, Grade.Pressure.ATMOSPHERIC, Kind.GAS),
    SEPARATOR(Grade.Heat.COLD, Grade.Heat.HOT, Grade.Pressure.HIGH, Kind.ALL);

    private enum Kind { ALL, LIQUID, GAS }
    public final Grade.Heat coldest, hottest;
    public final Grade.Pressure pressure;
    private final Kind kind;
    private final Set<FluidHazards.Corrosion> resistance;

    TankGrade(Grade.Heat coldest, Grade.Heat hottest, Grade.Pressure pressure, Kind kind, FluidHazards.Corrosion... resistance) {
        this.coldest=coldest;this.hottest=hottest;this.pressure=pressure;this.kind=kind;this.resistance=Set.of(resistance);
    }

    public int gasRatio() { return pressure.storageRatio(); }
    public boolean accepts(FluidStack stack) { return incompatibility(stack).equals("safe"); }
    /** Whether the tank can hold the fluid at all; a fluid it is not rated for still goes in and wears it (see ContainmentWear). */
    public boolean storable(FluidStack stack) { return !incompatibility(stack).equals("fluid"); }
    public String incompatibility(FluidStack stack) {
        if (stack.isEmpty() || !FluidThermal.valid(stack)) return "fluid";
        if (net.createplant.mixture.MixtureHelper.isMixture(stack)) {
            if (!net.createplant.mixture.MixtureHelper.isMixableLiquid(stack) && !net.createplant.mixture.MixtureHelper.isMixableGas(stack)) return "fluid";

        }
        var properties=FluidHazards.of(stack);boolean gas=properties.gas();
        if (kind==Kind.GAS && !gas) return "liquid";
        if (kind==Kind.LIQUID && gas) return "gas";
        if (properties.corrosion()!=FluidHazards.Corrosion.NONE && !resistance.contains(properties.corrosion())) return "corrosion";
        if (properties.heat().compareTo(coldest)<0) return "cold";
        if (properties.heat().compareTo(hottest)>0) return "hot";
        return properties.pressure().compareTo(pressure)>0 ? "pressure" : "safe";
    }

    public void describe(List<Component> lines) { describe(lines, net.neoforged.neoforge.fluids.FluidStack.EMPTY, false); }

    /** The rated range as a scale with the contents' level on it, or with Shift as the grade names, and whether it holds liquids or gases. */
    public void describe(List<Component> lines, net.neoforged.neoforge.fluids.FluidStack contents, boolean sneaking) {
        if (sneaking) lines.add(Component.translatable("grade.create_refinery_n_reactors.tank.range",coldest.label(),hottest.label(),pressure.label()));
        else lines.add(GradeScale.line(coldest, hottest, pressure, contents.isEmpty() ? -1 : FluidState.temperature(contents), contents.isEmpty() ? -1 : FluidState.pressure(contents)));
        lines.add(Component.translatable("grade.create_refinery_n_reactors.tank.kind."+kind.name().toLowerCase(java.util.Locale.ROOT)));
    }

    /** An item's lines: the scale and what it holds, or with Shift the ranges, what it holds and the corrosive fluids it holds against. */
    public void tooltip(List<Component> lines, boolean shift) {
        describe(lines, FluidStack.EMPTY, shift);
        if (shift) lines.add(FluidHazards.resistance(resistance));
    }

    /** The corrosion in a fluid that this tank is not rated for; NONE when it holds. */
    public FluidHazards.Corrosion attacking(FluidStack stack) {
        var corrosion=FluidHazards.of(stack).corrosion();
        return corrosion==FluidHazards.Corrosion.NONE || resistance.contains(corrosion) ? FluidHazards.Corrosion.NONE : corrosion;
    }

    public static TankGrade of(com.simibubi.create.foundation.blockEntity.SmartBlockEntity owner) {
        if (owner instanceof AmmoniaSeparatorBlockEntity) return null;
        if (owner instanceof GasHolderBlockEntity) return HOLDER;
        if (owner instanceof HighPressureSeparatorBlockEntity) return SEPARATOR;
        if (owner instanceof GasTankContainerBlockEntity) return GAS;
        if (owner instanceof HorizontalTankBlockEntity) return LIQUID;
        if (!(owner instanceof FluidTankBlockEntity)) return null;
        var grade=of(owner.getBlockState().getBlock());
        return grade==null ? STANDARD : grade;
    }

    /** The grade of a tank block, for its item as well as the placed tank; null for any other block. */
    public static TankGrade of(Block block) {
        if (block==com.simibubi.create.AllBlocks.FLUID_TANK.get() || block==CETransport.HORIZONTAL_FLUID_TANK.get()) return LIQUID;
        if (block==CETransport.GAS_RECEIVER.get() || block==CETransport.GAS_TANK_CONTAINER.get()) return GAS;
        if (block==CETransport.HIGH_PRESSURE_SEPARATOR.get()) return SEPARATOR;
        if (block==CETransport.PRESSURE_TANK.get()) return PRESSURE;
        if (block==CETransport.STAINLESS_STEEL_TANK.get()) return STAINLESS;
        if (block==CETransport.CRYOGENIC_TANK.get()) return CRYOGENIC;
        if (block==net.createplant.registry.CEGasBell.TANK.get()) return HOLDER;
        if (block==net.createplant.registry.CEBlocks.TANK_CASING.get()) return OPEN;
        return null;
    }
}
