package net.createplant.block.entity;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import java.util.List;
import net.createplant.block.TankCells;
import net.createplant.registry.CEProcessFluids;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.SteamGrades;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Create's standing tank holding water over lit burners, with no steam engine or whistle, boils it into ordinary steam. The steam is an
 * ordinary fluid kept by the controller and taken from the roof with a pump, like the horizontal tank's boiler.
 */
public final class TankBoiler {
    public interface Holder { TankBoiler createPlant$boiler(); }

    public static final int CAPACITY = 16_000;
    private static final double MAX_HEAT = 400_000, JOULES_PER_HEAT_LEVEL = 4000;
    private final FluidTankBlockEntity owner;
    private final ThermalFluidTank steam;
    private final IFluidHandler outlet;
    private double heatJoules, heatWatts;
    private String status = "heat";

    public TankBoiler(FluidTankBlockEntity owner) {
        this.owner = owner;
        steam = new ThermalFluidTank(CAPACITY, stack -> owner.setChanged()) { @Override protected int storageRatio() { return 1; } };
        outlet = new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? steam.getFluid().copy() : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int tank) { return tank == 0 ? CAPACITY : 0; }
            @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
            @Override public int fill(FluidStack stack, FluidAction action) { return 0; }
            @Override public FluidStack drain(int amount, FluidAction action) { return owner.isRemoved() ? FluidStack.EMPTY : steam.drain(amount, action); }
            @Override public FluidStack drain(FluidStack request, FluidAction action) { return FluidThermal.compatible(steam.getFluid(), request) ? drain(request.getAmount(), action) : FluidStack.EMPTY; }
        };
    }

    public static TankBoiler of(FluidTankBlockEntity tank) { return tank == null ? null : ((Holder) tank).createPlant$boiler(); }
    /** Only Create's own standing tank boils this way; the horizontal tank and the plant's vessels have their own rules. */
    public static boolean applies(FluidTankBlockEntity tank) { return tank.getType() == AllBlockEntityTypes.FLUID_TANK.get(); }
    public ThermalFluidTank steam() { return steam; }
    public boolean boiling() { return !steam.isEmpty() || status.equals("running") || status.equals("working") || status.equals("output"); }

    /** The roof of a boiling tank gives its steam; while nothing boils, and on every other face, the tank is a tank. */
    public static IFluidHandler port(FluidTankBlockEntity part, Direction side, IFluidHandler tank) {
        if (side != Direction.UP || !applies(part)) return tank;
        return new IFluidHandler() {
            private IFluidHandler held() {
                var controller = part.getControllerBE();
                if (controller == null || part.getBlockPos().getY() != controller.getBlockPos().getY() + controller.getHeight() - 1) return tank;
                var boiler = of(controller);
                return boiler.boiling() ? boiler.outlet : tank;
            }
            @Override public int getTanks() { return held().getTanks(); }
            @Override public FluidStack getFluidInTank(int index) { return held().getFluidInTank(index); }
            @Override public int getTankCapacity(int index) { return held().getTankCapacity(index); }
            @Override public boolean isFluidValid(int index, FluidStack stack) { return held().isFluidValid(index, stack); }
            @Override public int fill(FluidStack stack, FluidAction action) { return held().fill(stack, action); }
            @Override public FluidStack drain(int amount, FluidAction action) { return held().drain(amount, action); }
            @Override public FluidStack drain(FluidStack request, FluidAction action) { return held().drain(request, action); }
        };
    }

    public void tick() {
        var level = owner.getLevel();
        if (level == null || level.isClientSide) return;
        if (!owner.isController()) {
            if (!steam.isEmpty() && of(owner.getControllerBE()) instanceof TankBoiler root) steam.drain(root.steam.fill(steam.getFluid(), IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
            return;
        }
        String previous = status;
        int amount = steam.getFluidAmount();
        boil();
        if (!previous.equals(status) || amount != steam.getFluidAmount()) owner.sendData();
    }

    private void boil() {
        var level = owner.getLevel();
        heatWatts = 0;
        if (owner.boiler.isActive()) { status = "engines"; return; }
        var feed = owner.getTankInventory();
        var water = feed.getFluid();
        if (!water.is(Fluids.WATER) && !water.is(CEProcessFluids.WARM_WATER.get())) { status = "water"; return; }
        // An unheated tank looks under itself again once a second.
        if (status.equals("heat") && Math.floorMod(level.getGameTime() + owner.getBlockPos().asLong(), 20) != 0) return;
        var sample = new FluidStack(SteamGrades.ORDINARY.fluid(), 1);
        FluidThermal.processComponents(water, sample);
        if (steam.fill(sample, IFluidHandler.FluidAction.SIMULATE) != 1) { status = "output"; return; }
        double[] provided = {0};
        TankCells.forEachBase(owner, cell -> {
            var burner = cell.below();
            if (!level.hasChunkAt(burner)) return;
            float available = BoilerHeater.findHeat(level, burner, level.getBlockState(burner));
            if (available > 0) provided[0] += Math.min(Math.max(0, MAX_HEAT - heatJoules - provided[0]), available * JOULES_PER_HEAT_LEVEL);
        });
        if (provided[0] <= 0) { status = "heat"; return; }
        heatJoules += provided[0];
        heatWatts = provided[0] * 20;
        int amount = Math.min(feed.getFluidAmount(), (int) Math.floor(heatJoules / SteamGrades.ORDINARY.joulesPerMb()));
        amount = Math.min(amount, steam.fill(sample.copyWithAmount(amount), IFluidHandler.FluidAction.SIMULATE));
        owner.setChanged();
        if (amount <= 0) { status = "working"; return; }
        feed.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        steam.fill(sample.copyWithAmount(amount), IFluidHandler.FluidAction.EXECUTE);
        heatJoules -= amount * SteamGrades.ORDINARY.joulesPerMb();
        status = "running";
    }

    public void goggle(List<Component> tooltip) {
        if (!boiling()) return;
        tooltip.add(Component.translatable("tubular_boiler." + status));
        if (!steam.isEmpty()) tooltip.add(FluidThermal.readout(steam.getFluid()));
        tooltip.add(Component.translatable("tubular_boiler.contents", steam.getFluidAmount(), heatWatts / 1000));
    }

    public void write(CompoundTag tag, HolderLookup.Provider lookup) {
        if (steam.isEmpty() && heatJoules == 0 && status.equals("heat")) return;
        tag.put("BoilerSteam", steam.writeToNBT(lookup, new CompoundTag()));
        tag.putDouble("BoilerHeat", heatJoules);
        tag.putDouble("BoilerWatts", heatWatts);
        tag.putString("BoilerStatus", status);
    }

    public void read(CompoundTag tag, HolderLookup.Provider lookup) {
        steam.readFromNBT(lookup, tag.getCompound("BoilerSteam"));
        heatJoules = Double.isFinite(tag.getDouble("BoilerHeat")) ? Math.clamp(tag.getDouble("BoilerHeat"), 0, MAX_HEAT) : 0;
        heatWatts = Double.isFinite(tag.getDouble("BoilerWatts")) ? Math.max(0, tag.getDouble("BoilerWatts")) : 0;
        status = tag.contains("BoilerStatus") ? tag.getString("BoilerStatus") : "heat";
    }
}
