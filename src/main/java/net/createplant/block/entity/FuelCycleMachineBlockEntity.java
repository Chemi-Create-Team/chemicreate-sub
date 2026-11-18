package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.createplant.block.FuelCycleStructure;
import net.createplant.recipe.FuelCycleRecipe;
import net.createplant.registry.CEFuelCycle;
import net.createplant.registry.CERecipeTypes;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class FuelCycleMachineBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation, net.createplant.block.PlantStructureListener {
    public static final int CAPACITY = 64_000;
    public static final double OHMS = 96;
    private final ThermalFluidTank[] tanks = { tank(), tank(), tank(), tank(), tank(), tank() };
    private final ItemStackHandler items = new ItemStackHandler(3) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 ? stack.is(CEFuelCycle.GLASS_FRIT.get()) : slot == 1 && stack.is(CEFuelCycle.EMPTY_CANISTER.get().asItem());
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private List<FuelCycleStructure.Cell> structure;
    private boolean formed;
    private int clock;
    private double progress, watts;
    private volatile double voltageSquared;
    private long electricalTick = Long.MIN_VALUE;
    private String status = "structure", batch = "";
    public FuelCycleMachineBlockEntity(BlockPos pos, BlockState state) { super(CEFuelCycle.MACHINE_ENTITY.get(), pos, state); }
    private ThermalFluidTank tank() { return new ThermalFluidTank(CAPACITY, stack -> setChanged()); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { super.addBehaviours(behaviours); }
    public boolean melter() { return getBlockState().is(CEFuelCycle.MELTER.get()); }
    public Direction facing() { return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING); }
    public boolean formed() { return formed; }
    public String status() { return status; }
    public double watts() { return watts; }
    public double progress() { return progress; }
    public ThermalFluidTank tank(int index) { return tanks[index]; }
    public IItemHandler inventory() { return items; }
    @Override public void initialize() { super.initialize();net.createplant.event.PlantStructureEvents.register(level, worldPosition); }
    @Override public void invalidate() { net.createplant.event.PlantStructureEvents.remove(level, worldPosition);super.invalidate(); }
    @Override public boolean containsStructure(BlockPos pos) {
        if (structure == null) return pos.distManhattan(worldPosition) < 16;
        return structure.stream().anyMatch(cell -> cell.pos().equals(pos));
    }
    @Override public void structureChanged() { formed = false;watts = 0;voltageSquared = 0;status = "structure";setChanged(); }
    public void setVoltageSquared(double value) {
        voltageSquared = Double.isFinite(value) ? Math.clamp(value, 0, 192_000_000) : 0;
        if (level != null) electricalTick = level.getGameTime();
    }
    @Override public float calculateStressApplied() { lastStressApplied = !melter() && formed ? 16 : 0;return lastStressApplied; }
    @Override public void tick() {
        super.tick();if (level == null || level.isClientSide()) return;
        if (clock++ % 20 == 0) validate();
        watts = formed && melter() && electricalTick >= level.getGameTime() - 1 ? voltageSquared / OHMS : 0;
        if (formed) process();
        else status = "structure";
        if (clock % 20 == 0) { setChanged();sendData(); }
    }
    public boolean validate() {
        if (structure == null) structure = FuelCycleStructure.cells(worldPosition, facing(), melter());
        boolean previous = formed;formed = true;
        for (var cell : structure) {
            if (!level.hasChunkAt(cell.pos()) || !level.getBlockState(cell.pos()).is(cell.state().getBlock())) { formed = false;break; }
            if (cell.pos().equals(worldPosition) && !level.getBlockState(cell.pos()).equals(cell.state())) { formed = false;break; }
        }
        if (formed) for (var cell : structure) if ((cell.state().getBlock() instanceof net.createplant.block.ProcessFrameBlock || cell.state().getBlock() instanceof net.createplant.block.FuelCycleOutletBlock) && !level.getBlockState(cell.pos()).equals(cell.state()))
            level.setBlock(cell.pos(), cell.state(), 2);
        if (formed) for (var cell : structure) if (level.getBlockEntity(cell.pos()) instanceof FuelCycleOutletBlockEntity outlet) outlet.assign(worldPosition);
        if (previous != formed) {
            level.invalidateCapabilities(worldPosition);setChanged();sendData();
            if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        }
        return formed;
    }
    private List<RecipeHolder<FuelCycleRecipe>> recipes() {
        return level.getRecipeManager().getAllRecipesFor((melter() ? CERecipeTypes.VITRIFICATION : CERecipeTypes.SOLVENT_EXTRACTION).<RecipeInput, FuelCycleRecipe>getType());
    }
    private int[] itemRequirements(FuelCycleRecipe recipe) {
        int[] count = new int[2];
        for (var ingredient : recipe.getIngredients()) {
            int chosen = -1;
            for (int i = 0; i < 2; i++) if (ingredient.test(items.getStackInSlot(i)) && items.getStackInSlot(i).getCount() > count[i]) { chosen = i;break; }
            if (chosen < 0) return null;
            count[chosen]++;
        }
        return count;
    }
    private boolean inputs(FuelCycleRecipe recipe) {
        if (recipe.getFluidIngredients().size() > 2 || recipe.getFluidResults().size() > 4 || itemRequirements(recipe) == null) return false;
        for (int i = 0; i < recipe.getFluidIngredients().size(); i++) {
            var ingredient = recipe.getFluidIngredients().get(i);
            if (!ingredient.ingredient().test(tanks[i].getFluid()) || tanks[i].getFluidAmount() < ingredient.amount()) return false;
        }
        return true;
    }
    private boolean outputs(FuelCycleRecipe recipe) {
        for (int i = 0; i < recipe.getFluidResults().size(); i++) {
            FluidStack fluid = recipe.getFluidResults().get(i);
            if (tanks[i + 2].fill(fluid, IFluidHandler.FluidAction.SIMULATE) != fluid.getAmount()) return false;
        }
        if (recipe.getRollableResults().size() > 1) return false;
        if (!recipe.getRollableResults().isEmpty()) {
            ItemStack result = recipe.getRollableResults().getFirst().getStack();
            ItemStack stored = items.getStackInSlot(2);
            if (!stored.isEmpty() && !ItemStack.isSameItemSameComponents(stored, result)) return false;
            if (stored.getCount() + result.getCount() > result.getMaxStackSize()) return false;
        }
        return true;
    }
    public void process() {
        if (!formed || isRemoved()) return;
        if (melter() && watts < 100_000) { status = "power";return; }
        if (!melter() && Math.abs(getSpeed()) < 32) { status = "speed";return; }
        RecipeHolder<FuelCycleRecipe> holder = null;
        for (var candidate : recipes()) if (inputs(candidate.value())) { holder = candidate;break; }
        if (holder == null) { progress = 0;batch = "";status = "feed";return; }
        var recipe = holder.value();
        if (!outputs(recipe)) { status = "output";return; }
        if (!holder.id().toString().equals(batch)) { batch = holder.id().toString();progress = 0; }
        double rate = melter() ? Math.min(1, watts / 1_500_000) : Math.min(2, Math.abs(getSpeed()) / 64);
        progress += rate;status = melter() ? "vitrifying" : "extracting";setChanged();
        if (progress < Math.max(1, recipe.getProcessingDuration())) return;
        int[] required = itemRequirements(recipe);
        if (required == null || !inputs(recipe) || !outputs(recipe)) return;
        for (int i = 0; i < 2; i++) items.extractItem(i, required[i], false);
        for (int i = 0; i < recipe.getFluidIngredients().size(); i++) tanks[i].drain(recipe.getFluidIngredients().get(i).amount(), IFluidHandler.FluidAction.EXECUTE);
        for (int i = 0; i < recipe.getFluidResults().size(); i++) tanks[i + 2].fill(recipe.getFluidResults().get(i).copy(), IFluidHandler.FluidAction.EXECUTE);
        if (!recipe.getRollableResults().isEmpty()) {
            ItemStack result = recipe.getRollableResults().getFirst().getStack().copy();
            if (!items.getStackInSlot(2).isEmpty()) result.grow(items.getStackInSlot(2).getCount());
            items.setStackInSlot(2, result);
        }
        progress = 0;setChanged();
    }
    public IFluidHandler fluidPort(Direction side) {
        if (side == null) return null;
        if (melter()) return side == Direction.UP ? port(0, true) : null;
        if (side == Direction.UP) return port(0, true);
        return side == facing().getClockWise() ? port(1, true) : null;
    }
    public IFluidHandler outlet(int index) { return index >= 2 && index < 6 ? port(index, false) : null; }
    private IFluidHandler port(int index, boolean input) {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? tanks[index].getFluid().copy() : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int tank) { return tank == 0 ? CAPACITY : 0; }
            @Override public boolean isFluidValid(int tank, FluidStack stack) {
                if (!input || tank != 0 || level == null || stack.isEmpty() || !FluidThermal.valid(stack)) return false;
                for (var recipe : recipes()) if (recipe.value().getFluidIngredients().size() > index && recipe.value().getFluidIngredients().get(index).ingredient().test(stack)) return true;
                return false;
            }
            @Override public int fill(FluidStack stack, FluidAction action) { return formed && !isRemoved() && isFluidValid(0, stack) ? tanks[index].fill(stack, action) : 0; }
            @Override public FluidStack drain(int amount, FluidAction action) { return formed && !isRemoved() && !input ? tanks[index].drain(amount, action) : FluidStack.EMPTY; }
            @Override public FluidStack drain(FluidStack stack, FluidAction action) { return formed && !isRemoved() && !input ? tanks[index].drain(stack, action) : FluidStack.EMPTY; }
        };
    }
    public IItemHandler itemPort(Direction side) {
        if (!melter() || side == null) return null;
        boolean output = side == Direction.DOWN;
        return new IItemHandler() {
            @Override public int getSlots() { return output ? 1 : 2; }
            @Override public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(output ? 2 : slot); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return formed && !isRemoved() && !output ? items.insertItem(slot, stack, simulate) : stack; }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return formed && !isRemoved() && output ? items.extractItem(2, amount, simulate) : ItemStack.EMPTY; }
            @Override public int getSlotLimit(int slot) { return 64; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return !output && items.isItemValid(slot, stack); }
        };
    }
    public void release() {
        if (level == null || level.isClientSide()) return;
        for (int i = 0; i < items.getSlots(); i++) { Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, items.getStackInSlot(i));items.setStackInSlot(i, ItemStack.EMPTY); }
        if (level instanceof ServerLevel server && java.util.Arrays.stream(tanks).anyMatch(tank -> !tank.isEmpty()))
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, 30, 1, 1, 1, .04);
        for (var tank : tanks) tank.setFluid(FluidStack.EMPTY);
        formed = false;voltageSquared = 0;
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);for (int i = 0; i < 6; i++) tag.put("ProcessTank" + i, tanks[i].writeToNBT(lookup, new CompoundTag()));
        tag.put("ProcessItems", items.serializeNBT(lookup));tag.putBoolean("Formed", formed);
        tag.putDouble("Progress", progress);tag.putDouble("Watts", watts);tag.putString("Batch", batch);tag.putString("Status", status);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);for (int i = 0; i < 6; i++) tanks[i].readFromNBT(lookup, tag.getCompound("ProcessTank" + i));
        if (tag.contains("ProcessItems")) items.deserializeNBT(lookup, tag.getCompound("ProcessItems"));
        formed = packet && tag.getBoolean("Formed");progress = finite(tag.getDouble("Progress"), 0, 100_000);
        watts = packet ? finite(tag.getDouble("Watts"), 0, 2_000_000) : 0;batch = tag.getString("Batch");status = tag.getString("Status");structure = null;
    }
    private static double finite(double value, double fallback, double max) { return Double.isFinite(value) && value >= 0 ? Math.min(value, max) : fallback; }
    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        text.add(Component.translatable("plant_systems.fuel_cycle.state", Component.translatable("plant_systems.status." + status)));
        text.add(Component.translatable("plant_systems.fuel_cycle.process", Math.round(progress), (melter() && watts >= 100_000 ? net.createplant.thermal.Grade.Heat.VERY_HOT : net.createplant.thermal.Grade.Heat.AMBIENT).label(), Math.round(watts / 1000)));
        text.add(Component.translatable("plant_systems.fuel_cycle.inputs", tanks[0].getFluidAmount(), tanks[1].getFluidAmount()));
        text.add(Component.translatable("plant_systems.fuel_cycle.outputs", tanks[2].getFluidAmount(), tanks[3].getFluidAmount(), tanks[4].getFluidAmount(), tanks[5].getFluidAmount(), items.getStackInSlot(2).getCount()));
        return true;
    }
}
