package net.createplant.thermal;

import java.util.List;
import java.util.Set;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.createplant.block.entity.AmmoniaSeparatorBlockEntity;
import net.createplant.block.entity.GasHolderBlockEntity;
import net.createplant.block.entity.GasTankContainerBlockEntity;
import net.createplant.block.entity.HorizontalTankBlockEntity;
import net.createplant.registry.CETransport;
import net.createplant.transport.FluidHazards;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

public enum TankGrade {
    STANDARD(Grade.Heat.COLD, Grade.Heat.HOT, Grade.Pressure.PRESSURISED, Kind.ALL),
    LIQUID(Grade.Heat.COLD, Grade.Heat.HOT, Grade.Pressure.PRESSURISED, Kind.LIQUID),
    GAS(Grade.Heat.COLD, Grade.Heat.HOT, Grade.Pressure.HIGH, Kind.GAS),
    PRESSURE(Grade.Heat.COLD, Grade.Heat.HOT, Grade.Pressure.HIGH, Kind.LIQUID),
    STAINLESS(Grade.Heat.COLD, Grade.Heat.VERY_HOT, Grade.Pressure.PRESSURISED, Kind.LIQUID, FluidHazards.Corrosion.ACID),
    CRYOGENIC(Grade.Heat.CRYOGENIC, Grade.Heat.AMBIENT, Grade.Pressure.PRESSURISED, Kind.LIQUID),
    OPEN(Grade.Heat.COLD, Grade.Heat.AMBIENT, Grade.Pressure.ATMOSPHERIC, Kind.LIQUID),
    HOLDER(Grade.Heat.COLD, Grade.Heat.AMBIENT, Grade.Pressure.ATMOSPHERIC, Kind.GAS);

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
            for (var part : net.createplant.mixture.MixtureHelper.components(stack)) { var reason = incompatibility(part);if (!reason.equals("safe")) return reason; }
            return "safe";
        }
        var properties=FluidHazards.of(stack);boolean gas=properties.gas() || stack.getFluidType().isLighterThanAir();
        if (kind==Kind.GAS && !gas) return "liquid";
        if (kind==Kind.LIQUID && gas) return "gas";
        if (properties.corrosion()!=FluidHazards.Corrosion.NONE && !resistance.contains(properties.corrosion())) return "corrosion";
        if (properties.heat().compareTo(coldest)<0) return "cold";
        if (properties.heat().compareTo(hottest)>0) return "hot";
        return properties.pressure().compareTo(pressure)>0 ? "pressure" : "safe";
    }

    public void describe(List<Component> lines) {
        lines.add(Component.translatable("grade.create_refinery_n_reactors.tank.range",coldest.detail(),hottest.detail(),pressure.detail()));
        lines.add(Component.translatable("grade.create_refinery_n_reactors.tank.kind."+kind.name().toLowerCase(java.util.Locale.ROOT),
            Component.translatable("grade.create_refinery_n_reactors.tank."+(resistance.isEmpty() ? "no_acid" : "acid"))));
    }

    public static TankGrade of(com.simibubi.create.foundation.blockEntity.SmartBlockEntity owner) {
        if (owner instanceof AmmoniaSeparatorBlockEntity) return null;
        if (owner instanceof GasHolderBlockEntity) return HOLDER;
        if (owner instanceof GasTankContainerBlockEntity) return GAS;
        if (owner instanceof HorizontalTankBlockEntity) return LIQUID;
        if (!(owner instanceof FluidTankBlockEntity)) return null;
        var state=owner.getBlockState();
        if (state.is(com.simibubi.create.AllBlocks.FLUID_TANK.get())) return LIQUID;
        if (state.is(CETransport.GAS_RECEIVER.get())) return GAS;
        if (state.is(CETransport.PRESSURE_TANK.get())) return PRESSURE;
        if (state.is(CETransport.STAINLESS_STEEL_TANK.get())) return STAINLESS;
        if (state.is(CETransport.CRYOGENIC_TANK.get())) return CRYOGENIC;
        return STANDARD;
    }
}
