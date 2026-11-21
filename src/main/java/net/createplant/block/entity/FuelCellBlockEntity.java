package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import java.util.Locale;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEFuelCell;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * An alkaline fuel cell stack with nickel electrodes. In the circuit it is a 400 V source behind 20 ohms, so it gives at most 2 kW, into a
 * matched 20 ohm load. The current it delivers burns hydrogen: at the open-circuit voltage a millibucket of hydrogen gives 15 J, three fifths of
 * the 25 J the filter-press electrolyzer spends to make it, and the rest of the energy is lost in the stack. Each millibucket of hydrogen takes half a
 * millibucket of oxygen and leaves one and a half of water at 80 C, the reverse of the electrolyzer's 3 mB of water to 2 of hydrogen and 1 of oxygen.
 */
public class FuelCellBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final double OPEN_VOLTS = 400, OHMS = 20, JOULES_PER_HYDROGEN = 15, WATER_PER_HYDROGEN = 1.5, WATER_KELVIN = 353.15;
    public static final int CAPACITY = 4000;
    /** Enough reactants and outlet room for the highest current the 20 ohm source can supply in one tick. */
    private static final int MAX_REACTIONS_PER_TICK = 14;
    /** A source driven backwards by a higher voltage disconnects itself and looks again after this many ticks. */
    private static final int BACKFED_PAUSE = 20;
    private static final double BACKFED_AMPS = .01;
    private FluidStack hydrogen = FluidStack.EMPTY, oxygen = FluidStack.EMPTY, water = FluidStack.EMPTY;
    /** Fractions of one 2 H2 + O2 -> 3 H2O reaction, carried to the next tick. */
    private double reactionDue;
    private double volts, amps;
    private long backfedUntil, lastSupplied = Long.MIN_VALUE / 2;
    private String status = "fuel";
    private final IFluidHandler ports = new Ports();

    public FuelCellBlockEntity(BlockPos pos, BlockState state) { super(CEFuelCell.FUEL_CELL_ENTITY.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CEFuelCell.FUEL_CELL_ENTITY.get(), (be, side) -> net.createplant.mixture.GasMixturePort.both(be.ports, be));
    }

    /** Whether the stack can give current this tick: fuel on both electrodes, room for its water, and not backfed. */
    public boolean ready(long gameTime) {
        return hydrogen.getAmount() >= 2 * MAX_REACTIONS_PER_TICK && oxygen.getAmount() >= MAX_REACTIONS_PER_TICK
            && water.getAmount() <= CAPACITY - 3 * MAX_REACTIONS_PER_TICK && gameTime >= backfedUntil;
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        planProduction();
        if (level.getGameTime() - lastSupplied > 2) ProductionMonitor.limitedBy(this, "unwired");
    }

    private void planProduction() {
        double maxWater = OPEN_VOLTS * OPEN_VOLTS / OHMS / JOULES_PER_HYDROGEN * WATER_PER_HYDROGEN;
        double maxPower = OPEN_VOLTS * OPEN_VOLTS / (4 * OHMS) / 1000;
        ProductionMonitor.plan(this, maxWater, 0, maxPower);
    }

    /** The circuit's answer: the voltage across the terminals, node 0 positive, while the stack was a source; zero when it was not. */
    public void supplied(double terminalVolts, boolean sourced, long gameTime) {
        planProduction();
        String before = status;
        lastSupplied = gameTime;
        volts = sourced ? terminalVolts : 0;
        amps = sourced ? (OPEN_VOLTS - terminalVolts) / OHMS : 0;
        // A few milliamps either way is the solver's rounding with nothing on the terminals.
        if (sourced && amps < -BACKFED_AMPS) {
            backfedUntil = gameTime + BACKFED_PAUSE;
            amps = 0;
            status = "backfed";
        } else if (amps > BACKFED_AMPS && ready(gameTime)) {
            burn(OPEN_VOLTS * amps / 20 / JOULES_PER_HYDROGEN);
            ProductionMonitor.produced(this, 0, 0, watts() / 20);
            status = "running";
        } else {
            amps = 0;
            status = hydrogen.getAmount() < 2 * MAX_REACTIONS_PER_TICK ? "fuel"
                : oxygen.getAmount() < MAX_REACTIONS_PER_TICK ? "oxygen"
                : water.getAmount() > CAPACITY - 3 * MAX_REACTIONS_PER_TICK ? "water"
                : gameTime < backfedUntil ? "backfed" : "idle";
        }
        ProductionMonitor.limitedBy(this, switch (status) {
            case "fuel", "oxygen" -> "feed";
            case "water" -> "output";
            case "idle" -> "demand";
            case "running" -> watts() < .99 * OPEN_VOLTS * OPEN_VOLTS / (4 * OHMS) ? "load_match" : "rated";
            default -> status;
        });
        setChanged();
        if (!status.equals(before) || gameTime % 20 == 0) sendData();
    }

    /** Completes only whole stoichiometric reactions; a fractional reaction waits for the next tick. */
    void burn(double hydrogenBurnt) {
        if (!(hydrogenBurnt > 0) || !Double.isFinite(hydrogenBurnt)) return;
        reactionDue += hydrogenBurnt / 2;
        int reactions = Math.min(whole(reactionDue), Math.min(hydrogen.getAmount() / 2,
            Math.min(oxygen.getAmount(), (CAPACITY - water.getAmount()) / 3)));
        if (reactions <= 0) return;
        hydrogen = shrink(hydrogen, reactions * 2);
        oxygen = shrink(oxygen, reactions);
        int produced = reactions * 3;
        water = FluidThermal.add(water, FluidThermal.at(new FluidStack(Fluids.WATER, produced), WATER_KELVIN), produced);
        ProductionMonitor.produced(this, produced, 0, 0);
        reactionDue = Math.clamp(reactionDue - reactions, 0, 1);
    }

    /** Whole millibuckets of what is owed; thirds that add up to one are not lost to rounding. */
    private static int whole(double due) { return (int) (due + 1e-9); }

    private static FluidStack shrink(FluidStack stack, int amount) {
        if (amount <= 0) return stack;
        FluidStack left = stack.copyWithAmount(stack.getAmount() - amount);
        return left.isEmpty() ? FluidStack.EMPTY : left;
    }

    public double watts() { return volts * amps; }
    public double volts() { return volts; }
    /** The circuit asks every tick; a cell it has not asked lately has no wires on its terminals. */
    public String status() { return level != null && level.getGameTime() - lastSupplied > 2 ? "unwired" : status; }
    public FluidStack hydrogen() { return hydrogen; }
    public FluidStack oxygen() { return oxygen; }
    public FluidStack water() { return water; }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!hydrogen.isEmpty()) tag.put("Hydrogen", hydrogen.save(registries));
        if (!oxygen.isEmpty()) tag.put("Oxygen", oxygen.save(registries));
        if (!water.isEmpty()) tag.put("Water", water.save(registries));
        tag.putDouble("ReactionDue", reactionDue);
        tag.putString("Status", status);
        if (clientPacket) { tag.putDouble("Volts", volts);tag.putDouble("Amps", amps);tag.putLong("LastSupplied", lastSupplied); }
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        hydrogen = FluidStack.parseOptional(registries, tag.getCompound("Hydrogen"));
        oxygen = FluidStack.parseOptional(registries, tag.getCompound("Oxygen"));
        water = FluidStack.parseOptional(registries, tag.getCompound("Water"));
        if (tag.contains("ReactionDue")) reactionDue = fraction(tag.getDouble("ReactionDue"));
        else reactionDue = Math.max(fraction(tag.getDouble("HydrogenDue")) / 2,
            Math.max(fraction(tag.getDouble("OxygenDue")), fraction(tag.getDouble("WaterDue")) / 3));
        status = tag.contains("Status") ? tag.getString("Status") : "fuel";
        if (clientPacket) { volts = tag.getDouble("Volts");amps = tag.getDouble("Amps");lastSupplied = tag.getLong("LastSupplied"); }
    }

    private static double fraction(double value) { return Double.isFinite(value) ? Math.clamp(value, 0, 1) : 0; }

    @Override protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (level == null) return;
        CompoundTag data = saveCustomOnly(level.registryAccess());
        BlockEntity.addEntityType(data, getType());
        components.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
    }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        String key = "fuel_cell.create_refinery_n_reactors.";
        text.add(Component.translatable(key + "status." + status()));
        text.add(Component.translatable(key + "power", String.format(Locale.ROOT, "%.2f", watts() / 1000), String.format(Locale.ROOT, "%.0f", volts), String.format(Locale.ROOT, "%.1f", amps)));
        text.add(Component.translatable(key + "tanks", hydrogen.getAmount(), oxygen.getAmount(), water.getAmount()));
        return true;
    }

    /** Hydrogen and oxygen go in at any face and the water comes out at any face. */
    private final class Ports implements IFluidHandler {
        @Override public int getTanks() { return 3; }
        @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? hydrogen.copy() : tank == 1 ? oxygen.copy() : tank == 2 ? water.copy() : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return tank >= 0 && tank < getTanks() ? CAPACITY : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && stack.is(CEFluids.HYDROGEN_GAS.get()) || tank == 1 && stack.is(CEFluids.OXYGEN_GAS.get()); }

        @Override public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            Fluid fluid = resource.getFluid();
            boolean isHydrogen = fluid == CEFluids.HYDROGEN_GAS.get();
            if (!isHydrogen && fluid != CEFluids.OXYGEN_GAS.get()) return 0;
            FluidStack stored = isHydrogen ? hydrogen : oxygen;
            if (!stored.isEmpty() && !FluidThermal.compatible(stored, resource)) return 0;
            int accepted = Math.min(resource.getAmount(), CAPACITY - stored.getAmount());
            if (accepted <= 0 || action.simulate()) return Math.max(0, accepted);
            FluidStack filled = FluidThermal.add(stored, resource, accepted);
            if (isHydrogen) hydrogen = filled; else oxygen = filled;
            setChanged();
            return accepted;
        }

        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            return !resource.isEmpty() && resource.is(Fluids.WATER) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
        }

        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            int drained = Math.min(maxDrain, water.getAmount());
            if (drained <= 0) return FluidStack.EMPTY;
            FluidStack result = water.copyWithAmount(drained);
            if (action.execute()) {
                water = shrink(water, drained);
                setChanged();
            }
            return result;
        }
    }
}
