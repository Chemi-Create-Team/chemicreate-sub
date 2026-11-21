package net.createplant.thermal;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.createplant.mixture.MixtureHelper;
import net.createplant.registry.CEThermal;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** A gas vessel's water sump. The ordinary gas connections also accept condensate delivered by the upstream exchanger. */
public final class GasTankDrain {
    private final ThermalFluidTank water;

    public GasTankDrain(Runnable changed) {
        water = new ThermalFluidTank(8000, fluid -> changed.run());
    }

    public void collect(ThermalFluidTank gas) {
        var stored = gas.getFluid();
        if (!MixtureHelper.isMixture(stored) || water.getSpace() <= 0) return;
        var split = SteamCondensate.separate(stored, 293.15, Math.min(100, water.getSpace()));
        if (split.water().isEmpty() || water.fill(split.water(), IFluidHandler.FluidAction.SIMULATE) != split.water().getAmount()) return;
        gas.setFluid(split.gas());
        water.fill(split.water(), IFluidHandler.FluidAction.EXECUTE);
    }

    public void read(CompoundTag tag, HolderLookup.Provider lookup) { water.readFromNBT(lookup, tag.getCompound("DrainWater")); }
    public void write(CompoundTag tag, HolderLookup.Provider lookup) {
        if (!water.isEmpty()) tag.put("DrainWater", water.writeToNBT(lookup, new CompoundTag()));
    }

    public void addReadout(List<Component> lines, boolean composition) {
        if (water.isEmpty()) return;
        if (!composition && lines.size() == 4) lines.removeLast();
        lines.add(Component.translatable("grade.create_refinery_n_reactors.condensate", water.getFluidAmount(), water.getCapacity()));
    }

    public static boolean sameGasWithSteam(FluidStack first, FluidStack second) {
        if (!SteamCondensate.vapour(first) && !SteamCondensate.vapour(second)) return false;
        var species=drySpecies(first);
        return !species.isEmpty() && species.equals(drySpecies(second));
    }

    private static Set<Fluid> drySpecies(FluidStack mixture) {
        return MixtureHelper.components(mixture).stream().filter(part -> SteamGrades.of(part)==null).map(FluidStack::getFluid).collect(Collectors.toSet());
    }

    private static FluidStack unmarked(FluidStack stack) {
        if (stack.isEmpty()) return stack;
        var copy = stack.copy();
        copy.remove(CEThermal.CONDENSATE.get());
        return copy;
    }

    public static IFluidHandler port(IFluidHandler gas, boolean bottom, Supplier<GasTankDrain> target) {
        return new IFluidHandler() {
            private ThermalFluidTank sump() { var drain=target.get();return drain==null ? null : drain.water; }
            @Override public int getTanks() { return gas.getTanks(); }
            @Override public FluidStack getFluidInTank(int tank) {
                if (!bottom) return gas.getFluidInTank(tank);
                var water=sump();
                return tank==0 && water!=null && !water.isEmpty() ? unmarked(water.getFluid()) : gas.getFluidInTank(tank);
            }
            @Override public int getTankCapacity(int tank) {
                if (!bottom) return gas.getTankCapacity(tank);
                var water=sump();
                return tank==0 && water!=null && !water.isEmpty() ? water.getCapacity() : gas.getTankCapacity(tank);
            }
            @Override public boolean isFluidValid(int tank, FluidStack fluid) {
                return SteamCondensate.water(fluid) ? tank==0 && sump()!=null : gas.isFluidValid(tank,fluid);
            }
            @Override public int fill(FluidStack fluid, FluidAction action) {
                if (!SteamCondensate.water(fluid)) return gas.fill(fluid,action);
                var water=sump();
                return water==null ? 0 : water.fill(fluid,action);
            }
            @Override public FluidStack drain(int amount, FluidAction action) {
                if (!bottom) return gas.drain(amount,action);
                var water=sump();
                return water==null || water.isEmpty() ? gas.drain(amount,action) : unmarked(water.drain(amount,action));
            }
            @Override public FluidStack drain(FluidStack requested, FluidAction action) {
                if (!bottom) return gas.drain(requested,action);
                var water=sump();
                if (requested.is(Fluids.WATER)) return water==null ? FluidStack.EMPTY : unmarked(water.drain(requested.getAmount(),action));
                return water==null || water.isEmpty() ? gas.drain(requested,action) : FluidStack.EMPTY;
            }
        };
    }
}
