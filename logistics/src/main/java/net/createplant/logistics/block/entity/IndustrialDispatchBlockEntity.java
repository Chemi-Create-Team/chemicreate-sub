package net.createplant.logistics.block.entity;
import net.createplant.logistics.registry.CELogisticsRecipeTypes;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.createplant.logistics.recipe.IndustrialDeliveryRecipe;
import net.createplant.logistics.registry.CEIndustrialExpansion;
import net.createplant.registry.CERecipeTypes;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class IndustrialDispatchBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int CAPACITY = 16_000;
    private final ThermalFluidTank cargo = new ThermalFluidTank(CAPACITY, stack -> setChanged());
    private final ItemStackHandler reward = new ItemStackHandler(1) { @Override protected void onContentsChanged(int slot) { setChanged(); } };
    private String selected = "", status = "vault";
    private int progress, stampTicks;
    private long completed;
    public IndustrialDispatchBlockEntity(BlockPos pos, BlockState state) { super(CEIndustrialExpansion.DISPATCH_ENTITY.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }
    public String status() { return status; }
    public int progress() { return progress; }
    public long completed() { return completed; }
    public ThermalFluidTank cargo() { return cargo; }
    public ItemStackHandler rewards() { return reward; }
    public List<RecipeHolder<IndustrialDeliveryRecipe>> orders() {
        return level == null ? List.of() : level.getRecipeManager().getAllRecipesFor(CELogisticsRecipeTypes.INDUSTRIAL_DELIVERY.<RecipeInput, IndustrialDeliveryRecipe>getType()).stream()
            .sorted(java.util.Comparator.comparing(holder -> holder.id().toString())).toList();
    }
    public RecipeHolder<IndustrialDeliveryRecipe> order() {
        var orders = orders();
        return orders.stream().filter(holder -> holder.id().toString().equals(selected)).findFirst().orElse(orders.isEmpty() ? null : orders.getFirst());
    }
    public void nextOrder(int direction) {
        var orders = orders();if (orders.isEmpty()) return;
        var current = order();int index = orders.indexOf(current);
        selected = orders.get(Math.floorMod(index + direction, orders.size())).id().toString();progress = 0;notifyUpdate();
    }
    private IItemHandler warehouse() {
        BlockPos behind = worldPosition.relative(getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        if (!level.hasChunkAt(behind) || !AllBlocks.ITEM_VAULT.has(level.getBlockState(behind)) || !(level.getBlockEntity(behind) instanceof ItemVaultBlockEntity part)) return null;
        if (!level.hasChunkAt(part.getController())) return null;
        var owner = part.getControllerBE();if (owner == null) return null;
        for (int a = 0; a < owner.getHeight(); a++) for (int u = 0; u < owner.getWidth(); u++) for (int v = 0; v < owner.getWidth(); v++) {
            BlockPos at = owner.getMainConnectionAxis() == Direction.Axis.X ? owner.getBlockPos().offset(a, u, v) : owner.getBlockPos().offset(u, v, a);
            if (!level.hasChunkAt(at) || !(level.getBlockEntity(at) instanceof ItemVaultBlockEntity cell) || !cell.getController().equals(owner.getBlockPos())) return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, behind, getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
    }
    private int[] plan(IItemHandler warehouse, IndustrialDeliveryRecipe recipe) {
        int[] used = new int[warehouse.getSlots()];
        for (var ingredient : recipe.getIngredients()) {
            boolean found = false;
            for (int slot = 0; slot < used.length; slot++) {
                ItemStack stack = warehouse.getStackInSlot(slot);
                if (stack.getCount() > used[slot] && ingredient.test(stack) && warehouse.extractItem(slot, used[slot] + 1, true).getCount() == used[slot] + 1) { used[slot]++;found = true;break; }
            }
            if (!found) return null;
        }
        return used;
    }
    public void processOrder() {
        IItemHandler warehouse = warehouse();
        if (warehouse == null) { status = "vault";progress = 0;return; }
        if (level.hasNeighborSignal(worldPosition)) { status = "paused";return; }
        var holder = order();if (holder == null) { status = "orders";progress = 0;return; }
        if (!holder.id().toString().equals(selected)) { selected = holder.id().toString();progress = 0; }
        var recipe = holder.value();int[] used = plan(warehouse, recipe);
        if (used == null) { status = "supplies";progress = 0;return; }
        int fluid = 0;
        if (!recipe.getFluidIngredients().isEmpty()) {
            var ingredient = recipe.getFluidIngredients().getFirst();fluid = ingredient.amount();
            if (!ingredient.ingredient().test(cargo.getFluid()) || cargo.getFluidAmount() < fluid) { status = "supplies";progress = 0;return; }
        }
        if (recipe.getRollableResults().size() != 1 || recipe.getRollableResults().getFirst().getChance() != 1) { status = "orders";progress = 0;return; }
        ItemStack result = recipe.getRollableResults().getFirst().getStack();
        if (!reward.insertItem(0, result, true).isEmpty()) { status = "output";return; }
        status = "running";progress += 20;
        if (progress < Math.max(20, recipe.getProcessingDuration())) return;
        for (int slot = 0; slot < used.length; slot++) if (used[slot] > 0) warehouse.extractItem(slot, used[slot], false);
        cargo.drain(fluid, IFluidHandler.FluidAction.EXECUTE);reward.insertItem(0, result.copy(), false);
        completed++;progress = 0;stampTicks = 20;status = "delivered";
        level.setBlock(worldPosition, getBlockState().setValue(BlockStateProperties.TRIGGERED, true), 3);
        nextOrder(1);setChanged();
    }
    @Override public void tick() {
        super.tick();if (level == null || level.isClientSide) return;
        if (stampTicks > 0 && --stampTicks == 0) level.setBlock(worldPosition, getBlockState().setValue(BlockStateProperties.TRIGGERED, false), 3);
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), 20) != 0) return;
        String before = status;int oldProgress = progress;processOrder();
        if (!status.equals(before) || progress != oldProgress) notifyUpdate();
    }
    public IItemHandler itemPort() {
        return new IItemHandler() {
            @Override public int getSlots() { return 1; }
            @Override public ItemStack getStackInSlot(int slot) { return slot == 0 ? reward.getStackInSlot(slot).copy() : ItemStack.EMPTY; }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
            @Override public ItemStack extractItem(int slot, int count, boolean simulate) { return isRemoved() || slot != 0 ? ItemStack.EMPTY : reward.extractItem(slot, count, simulate); }
            @Override public int getSlotLimit(int slot) { return 64; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
        };
    }
    public IFluidHandler fluidPort() {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? cargo.getFluid().copy() : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int tank) { return tank == 0 ? CAPACITY : 0; }
            @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && !stack.isEmpty() && orders().stream().anyMatch(holder -> holder.value().getFluidIngredients().stream().anyMatch(input -> input.ingredient().test(stack))); }
            @Override public int fill(FluidStack stack, FluidAction action) { return !isRemoved() && isFluidValid(0, stack) ? cargo.fill(stack, action) : 0; }
            @Override public FluidStack drain(int count, FluidAction action) { return isRemoved() ? FluidStack.EMPTY : cargo.drain(count, action); }
            @Override public FluidStack drain(FluidStack stack, FluidAction action) { return isRemoved() ? FluidStack.EMPTY : cargo.drain(stack, action); }
        };
    }
    public Component orderDescription() {
        var holder = order();if (holder == null) return Component.translatable("industrial_dispatch.orders");
        var recipe = holder.value();
        if (!recipe.getIngredients().isEmpty()) {
            var options = recipe.getIngredients().getFirst().getItems();
            return options.length == 0 ? Component.translatable("industrial_dispatch.orders") : Component.translatable("industrial_dispatch.order_items", recipe.getIngredients().size(), options[0].getHoverName());
        }
        if (!recipe.getFluidIngredients().isEmpty()) { var input = recipe.getFluidIngredients().getFirst();return Component.translatable("industrial_dispatch.order_fluid", input.amount(), input.getFluids()[0].getHoverName()); }
        return Component.translatable("industrial_dispatch.orders");
    }
    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        tooltip.add(Component.translatable("block.create_refinery_n_reactors.industrial_dispatch_board"));
        tooltip.add(orderDescription());tooltip.add(Component.translatable("industrial_dispatch." + status));
        tooltip.add(Component.translatable("industrial_dispatch.completed", completed));return true;
    }
    public void release() { if (level != null && !level.isClientSide) { Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), reward.extractItem(0, 64, false)); } }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);tag.putString("Order", selected);tag.putString("Status", status);tag.putInt("Progress", progress);tag.putLong("Completed", completed);
        tag.putInt("StampTicks", stampTicks);tag.put("Reward", reward.serializeNBT(lookup));tag.put("Cargo", cargo.writeToNBT(lookup, new CompoundTag()));
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);selected = tag.getString("Order");status = tag.getString("Status");progress = Math.max(0, tag.getInt("Progress"));completed = Math.max(0, tag.getLong("Completed"));
        stampTicks = Math.clamp(tag.getInt("StampTicks"), 0, 20);reward.deserializeNBT(lookup, tag.getCompound("Reward"));cargo.readFromNBT(lookup, tag.getCompound("Cargo"));
    }
}
