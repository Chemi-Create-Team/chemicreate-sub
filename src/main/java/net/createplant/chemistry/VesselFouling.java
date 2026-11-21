package net.createplant.chemistry;

import java.util.Set;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.recipe.PressureReactionRecipe;
import net.createplant.registry.CEProcessFluids;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Trace maintenance load, never a second material inventory or an extra recipe product. */
public final class VesselFouling {
    public static final int MAX_DIRT = 400, SLOW_START = 200, BRUSH_REMOVAL = 40;
    public static final int WASH_MB = 1000, WASH_REMOVAL = 100, WASTE_CAPACITY = 2000;
    private static final Set<String> DIRTY_STREAMS = Set.of("tar", "coal_tar", "wood_tar", "slurry", "coal_slurry", "ore_slurry");
    private final LiddedVesselBlockEntity owner;
    private int dirt;
    private boolean drainOpen;
    private FluidStack waste = FluidStack.EMPTY;
    private long lastWashTick = Long.MIN_VALUE;
    private final IFluidHandler drainPort = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? waste.copy() : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? WASTE_CAPACITY : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack stack, FluidAction action) { return 0; }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            return !stack.isEmpty() && !waste.isEmpty() && FluidThermal.compatible(waste, stack) ? drain(stack.getAmount(), action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maximum, FluidAction action) {
            if (maximum <= 0 || !live() || waste.isEmpty()) return FluidStack.EMPTY;
            int amount = Math.min(maximum, waste.getAmount());
            var result = waste.copyWithAmount(amount);
            if (action.execute()) {
                waste = amount == waste.getAmount() ? FluidStack.EMPTY : waste.copyWithAmount(waste.getAmount() - amount);
                changed();
            }
            return result;
        }
    };

    public VesselFouling(LiddedVesselBlockEntity owner) { this.owner = owner; }
    public int dirtLevel() { return dirt; }
    public float foulingFraction() { return dirt / (float) MAX_DIRT; }
    public boolean blocked() { return dirt >= MAX_DIRT; }
    public float speedFactor() { return dirt <= SLOW_START ? 1 : 1 - .75f * (dirt - SLOW_START) / (MAX_DIRT - SLOW_START); }
    public boolean drainOpen() { return drainOpen; }
    public IFluidHandler drainPort() { return drainPort; }

    /** Destruction bypasses the valve; the caller releases this exact, already-owned parcel once. */
    public FluidStack clear() {
        var released = waste.copy();
        dirt = 0;drainOpen = false;waste = FluidStack.EMPTY;lastWashTick = Long.MIN_VALUE;
        changed();
        return released;
    }

    /** Call only after a successful, committed batch; pure fluid mixing and rejected batches add nothing. */
    public void onBatch(Recipe<?> recipe, int actualCount) {
        if (actualCount <= 0 || !(recipe instanceof PressureReactionRecipe pressure)) return;
        boolean heavy = pressure.getIngredients().stream().anyMatch(ingredient -> !ingredient.isEmpty());
        if (!heavy) for (var ingredient : pressure.getFluidIngredients()) for (var fluid : ingredient.getFluids())
            if (DIRTY_STREAMS.contains(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).getPath())) heavy = true;
        if (!heavy) return;
        int next = (int) Math.min(MAX_DIRT, (long) dirt + actualCount);
        if (next != dirt) { dirt = next;changed(); }
    }

    /** The caller spends one brush durability only when true is returned. */
    public boolean brush() {
        if (dirt == 0 || !owner.open() || !safe()) return false;
        dirt = Math.max(0, dirt - BRUSH_REMOVAL);
        changed();
        return true;
    }

    /** Return the current valve state, leaving it unchanged when unsafe. */
    public boolean toggleDrain() {
        if (!safe()) return drainOpen;
        drainOpen = !drainOpen;
        changed();
        return drainOpen;
    }

    private boolean safe() {
        return owner.getLevel() != null && !owner.getLevel().isClientSide && !owner.isRemoved() && owner.isController() && owner.maintenanceSafe();
    }
    private boolean live() { return drainOpen && safe(); }
    private void changed() { owner.thermalChanged(); }

    /** Once a second, and at most once for the same world tick. */
    public void tick() {
        if (!live()) return;
        long now = owner.getLevel().getGameTime();
        if (now == lastWashTick || Math.floorMod(now + owner.getBlockPos().asLong(), 20) != 0) return;
        lastWashTick = now;
        flush(FluidAction.EXECUTE);
    }

    /** A finite water-to-waste transaction: simulation and blocked output cannot consume water or dirt. */
    public boolean flush(FluidAction action) {
        if (!live() || dirt == 0 || WASTE_CAPACITY - waste.getAmount() < WASH_MB) return false;
        var preview = owner.consumeWashWater(WASH_MB, FluidAction.SIMULATE);
        if (preview.getAmount() != WASH_MB || !net.createplant.thermal.FluidState.liquidWater(preview) || !FluidThermal.valid(preview)) return false;
        if (!action.execute()) return true;
        var water = owner.consumeWashWater(WASH_MB, FluidAction.EXECUTE);
        if (water.isEmpty()) return false; // The owner guarantees all-or-nothing source removal.
        if (water.getAmount() != WASH_MB || !net.createplant.thermal.FluidState.liquidWater(water)) throw new IllegalStateException("Non-atomic vessel wash-water source");
        var parcel = new FluidStack(CEProcessFluids.WASTEWATER.get(), WASH_MB);
        // Whole liquid views may retain sub-mB vapour latent heat; condensing it into non-phase wastewater must retain that energy.
        parcel = FluidThermal.at(parcel, FluidThermal.energy(water) / FluidThermal.heatCapacity(parcel));
        waste = waste.isEmpty() ? parcel : FluidThermal.mix(waste, parcel, WASH_MB);
        dirt = Math.max(0, dirt - WASH_REMOVAL);
        changed();
        return true;
    }

    /** The owner stores this compound under its Fouling key. */
    public void write(CompoundTag tag) {
        tag.putInt("Dirt", dirt);
        tag.putBoolean("DrainOpen", drainOpen);
        if (!waste.isEmpty()) { tag.putInt("WasteAmount", waste.getAmount());tag.putDouble("WasteKelvin", FluidThermal.kelvin(waste)); }
    }

    public void read(CompoundTag tag) {
        dirt = Math.clamp(tag.getInt("Dirt"), 0, MAX_DIRT);
        drainOpen = tag.getBoolean("DrainOpen");
        int amount = Math.clamp(tag.getInt("WasteAmount"), 0, WASTE_CAPACITY);
        double kelvin = tag.getDouble("WasteKelvin");
        if (!Double.isFinite(kelvin) || kelvin < 1 || kelvin > 10000) kelvin = 293.15;
        waste = amount == 0 ? FluidStack.EMPTY : FluidThermal.at(new FluidStack(CEProcessFluids.WASTEWATER.get(), amount), kelvin);
        lastWashTick = Long.MIN_VALUE;
    }
}
