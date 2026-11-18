package net.createplant.block.entity;

import java.util.List;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import net.createplant.block.CubicPressBlock;
import net.createplant.recipe.CubicPressRecipe;
import net.createplant.registry.CEHighStressMachines;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.items.*;

public final class CubicPressBlockEntity extends KineticBlockEntity {
    private ItemStack paidFeed = ItemStack.EMPTY, paidCatalyst = ItemStack.EMPTY, pending = ItemStack.EMPTY, returned = ItemStack.EMPTY;
    private double progress;
    private int duration;
    private boolean running;
    private String status = "materials";
    private long lastSync = -20, clientSyncAt;
    public final ItemStackHandler inventory = new ItemStackHandler(3) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == 2) return false;
            if (slot == 1 && !returned.isEmpty()) return ItemStack.isSameItemSameComponents(stack, returned);
            return level != null && level.getRecipeManager().getAllRecipesFor(CERecipeTypes.CUBIC_PRESSING.<CubicPressRecipe.Input, CubicPressRecipe>getType())
                .stream().anyMatch(holder -> holder.value().getIngredients().size() > slot && holder.value().getIngredients().get(slot).test(stack));
        }
        @Override protected int getStackLimit(int slot, ItemStack stack) {
            return super.getStackLimit(slot, stack) - (slot == 1 && !returned.isEmpty() ? returned.getCount() : 0);
        }
    };
    private final IItemHandler port = new IItemHandler() {
        @Override public int getSlots() { return 3; }
        @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return slot < 2 ? inventory.insertItem(slot, stack, simulate) : stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return slot == 2 ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot) - (slot == 1 ? returned.getCount() : 0); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return inventory.isItemValid(slot, stack); }
    };
    public CubicPressBlockEntity(BlockPos pos, BlockState state) { super(CEHighStressMachines.PRESS_ENTITY.get(), pos, state); }
    public IItemHandler port() { return port; }
    public String status() { return status; }
    public boolean running() { return running; }
    public double progress() { return progress; }
    public int duration() { return duration; }
    public double animationProgress(float partialTicks) {
        return progress + (running && level != null && level.isClientSide ? Math.max(0, level.getGameTime() - clientSyncAt + partialTicks) * Math.min(4, Math.abs(getSpeed()) / 64d) : 0);
    }
    public void preview(boolean active) { if (isVirtual()) { running = active;duration = active ? 400 : 0;progress = active ? 100 : 0;clientSyncAt = level.getGameTime(); } }
    public boolean superheated() {
        var heat = level.getBlockState(worldPosition.below());
        return heat.getBlock() instanceof BlazeBurnerBlock && heat.hasProperty(BlazeBurnerBlock.HEAT_LEVEL) && HeatCondition.SUPERHEATED.testBlazeBurner(heat.getValue(BlazeBurnerBlock.HEAT_LEVEL));
    }
    private boolean outputFits(ItemStack stack) {
        var held = inventory.getStackInSlot(2);
        return !stack.isEmpty() && (held.isEmpty() || ItemStack.isSameItemSameComponents(held, stack))
            && held.getCount() + stack.getCount() <= stack.getMaxStackSize();
    }
    @Override public void tick() {
        super.tick();if (level != null && !level.isClientSide) process();
    }
    public void process() {
        if (level == null || level.isClientSide) return;
        boolean previous = running;String old = status;running = false;
        if (Math.abs(getSpeed()) < 64) status = "speed";
        else if (!superheated()) status = "heat";
        else {
            if (pending.isEmpty()) start();
            if (!pending.isEmpty()) {
                if (!outputFits(pending)) status = "output";
                else {
                    running = true;status = "running";progress += Math.min(4, Math.abs(getSpeed()) / 64d);setChanged();
                    if (progress >= duration) {
                        var output = inventory.getStackInSlot(2).copy();
                        if (output.isEmpty()) output = pending.copy();else output.grow(pending.getCount());
                        inventory.setStackInSlot(2, output);
                        if (!returned.isEmpty()) {
                            var catalyst = inventory.getStackInSlot(1).copy();
                            if (catalyst.isEmpty()) catalyst = returned.copy();else catalyst.grow(returned.getCount());
                            inventory.setStackInSlot(1, catalyst);
                        }
                        paidFeed = paidCatalyst = pending = returned = ItemStack.EMPTY;progress = 0;duration = 0;running = false;
                    }
                }
            }
        }
        if (!old.equals(status) || previous != running || level.getGameTime() - lastSync >= 10 && running) { lastSync = level.getGameTime();sendData(); }
    }
    private void start() {
        var input = new CubicPressRecipe.Input(inventory.getStackInSlot(0), inventory.getStackInSlot(1));
        var found = level.getRecipeManager().getRecipeFor(CERecipeTypes.CUBIC_PRESSING.<CubicPressRecipe.Input, CubicPressRecipe>getType(), input, level);
        if (found.isEmpty()) { status = "materials";return; }
        var recipe = found.get().value();
        if (!recipe.getRequiredHeat().testBlazeBurner(BlazeBurnerBlock.HeatLevel.SEETHING) || recipe.getRollableResults().isEmpty()) { status = "materials";return; }
        var main = recipe.getRollableResults().getFirst().getStack();
        if (!outputFits(main)) { status = "output";return; }
        var results = recipe.rollResults(level.random);
        if (results.isEmpty()) return;
        pending = results.getFirst().copy();returned = ItemStack.EMPTY;
        for (int i = 1; i < results.size(); i++) {
            if (!recipe.getIngredients().get(1).test(results.get(i))) { pending = ItemStack.EMPTY;status = "materials";return; }
            returned = input.catalyst().copyWithCount(results.get(i).getCount());
        }
        paidFeed = input.feed().copyWithCount(1);paidCatalyst = input.catalyst().copyWithCount(1);
        inventory.extractItem(0, 1, false);inventory.extractItem(1, 1, false);
        duration = Math.max(1, recipe.getProcessingDuration());progress = 0;setChanged();
    }
    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int i = 0; i < 3; i++) { Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), inventory.getStackInSlot(i));inventory.setStackInSlot(i, ItemStack.EMPTY); }
        for (var stack : List.of(paidFeed, paidCatalyst)) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        paidFeed = paidCatalyst = pending = returned = ItemStack.EMPTY;
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag, registries, packet);tag.put("Inventory", inventory.serializeNBT(registries));
        if (!pending.isEmpty()) tag.put("Pending", pending.save(registries));
        if (!returned.isEmpty()) tag.put("Returned", returned.save(registries));
        if (!paidFeed.isEmpty()) tag.put("PaidFeed", paidFeed.save(registries));
        if (!paidCatalyst.isEmpty()) tag.put("PaidCatalyst", paidCatalyst.save(registries));
        tag.putDouble("Progress", progress);tag.putInt("Duration", duration);tag.putBoolean("Running", running);tag.putString("Status", status);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.read(tag, registries, packet);if (level != null) clientSyncAt = level.getGameTime();inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        pending = ItemStack.parseOptional(registries, tag.getCompound("Pending"));returned = ItemStack.parseOptional(registries, tag.getCompound("Returned"));
        paidFeed = ItemStack.parseOptional(registries, tag.getCompound("PaidFeed"));paidCatalyst = ItemStack.parseOptional(registries, tag.getCompound("PaidCatalyst"));
        progress = Double.isFinite(tag.getDouble("Progress")) ? Math.max(0, tag.getDouble("Progress")) : 0;duration = Math.max(1, tag.getInt("Duration"));running = packet && tag.getBoolean("Running");status = tag.getString("Status");
    }
    @Override public boolean addToGoggleTooltip(List<Component> lines, boolean sneaking) {
        String key = "cubic_press.create_refinery_n_reactors.";
        lines.add(Component.translatable(key + "drive", Math.round(getSpeed()), Math.round(256 * Math.abs(getSpeed()))));
        lines.add(Component.translatable(key + (superheated() ? "superheated" : "cold")));
        lines.add(Component.translatable(key + "progress", duration == 0 ? 0 : Math.round(100 * progress / duration)));
        lines.add(Component.translatable(key + "status." + status));return true;
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CEHighStressMachines.PRESS_ENTITY.get(), (be, side) ->
            side != null && side != Direction.DOWN && side.getAxis() != be.getBlockState().getValue(CubicPressBlock.HORIZONTAL_AXIS) ? be.port() : null);
    }
}
