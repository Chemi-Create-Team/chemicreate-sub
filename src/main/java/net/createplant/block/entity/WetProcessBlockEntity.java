package net.createplant.block.entity;

import java.util.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.createplant.block.*;
import net.createplant.registry.*;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;
import static net.createplant.block.WetProcessPartBlock.Role.*;

public final class WetProcessBlockEntity extends KineticBlockEntity {
    public final FluidTank[] tanks = {tank(), tank(), tank()};
    public final ItemStackHandler items = new ItemStackHandler(5) {
        @Override protected void onContentsChanged(int slot) { contentsChanged = true;changed(); }
    };
    private Set<BlockPos> members = Set.of();
    private Map<BlockPos, BlockState> expected = Map.of();
    private List<BlockPos> chunks = List.of();
    private boolean dirty = true, formed, contentsChanged = true, boosted, clientDirty = true;
    private int width, height = 3, duration = 200;
    private long generation, synced = -20, nextRebuild;
    private double progress;
    private String batch = "", status = "structure", lastStatus = "";
    private RecipeHolder<? extends StandardProcessingRecipe<RecipeInput>> cached;

    public WetProcessBlockEntity(BlockPos pos, BlockState state) { super(CEWetProcessing.DRIVE_ENTITY.get(), pos, state); }
    private void changed() { clientDirty = true;setChanged(); }
    private FluidTank tank() { return new ThermalFluidTank(64000, fluid -> { contentsChanged = true;changed(); }); }
    public boolean formed() { return formed; }
    public int width() { return width; }
    public int height() { return height; }
    public String status() { return status; }
    public double progress() { return progress; }
    public int duration() { return duration; }
    public boolean owns(BlockPos pos) { return members.contains(pos); }
    public int capacity() { return width == 0 ? 64000 : Math.min(64000, (width - 2) * (width - 2) * (height() - 2) * 4000); }
    public static int impurity(float speed) { return Math.clamp(Math.round((Math.abs(speed) - 4) * 1000 / 13), 0, 1000); }
    public void cellChanged(BlockPos pos) {
        if (formed && expected.containsKey(pos) && level.hasChunkAt(pos) && level.getBlockState(pos) != expected.get(pos)) structureChanged();
    }
    public void structureChanged() {
        if (level == null || level.isClientSide || !formed) return;
        dirty = true;formed = false;status = "structure";generation++;
        for (var pos : members) if (level.hasChunkAt(pos)) level.invalidateCapabilities(pos);
        changed();
    }
    public void rebuild() {
        if (level == null || level.isClientSide) return;
        boolean previouslyFormed = formed;int previousWidth = width, previousHeight = height;String previousStatus = status;
        net.createplant.event.WetProcessChanges.forget(level, worldPosition);
        formed = false;dirty = false;generation++;
        Set<BlockPos> linked = Set.of();
        var watched = new HashSet<BlockPos>();
        var chunkPositions = new HashSet<BlockPos>();
        var facing = getBlockState().getValue(WetProcessDriveBlock.FACING);
        for (int candidateHeight : WetProcessStructure.CLARIFIER_HEIGHTS) for (int candidate : WetProcessStructure.CLARIFIER_WIDTHS) {
            if (formed) break;
            var feed = worldPosition.below(candidateHeight - 1);
            var rim = worldPosition.relative(facing, candidate / 2);
            watched.add(feed);watched.add(rim);
            if (!level.hasChunkAt(feed) || !level.hasChunkAt(rim)
                || !level.getBlockState(feed).is(CEWetProcessing.PARTS.get(FEED).get()) || !rimWall(level.getBlockState(rim))) continue;
            var cells = WetProcessStructure.cells(worldPosition, facing, candidate, candidateHeight);
            var states = new HashMap<BlockPos, BlockState>();
            var ports = new HashSet<BlockPos>();
            boolean valid = true;
            for (var cell : cells) {
                var pos = cell.pos();
                watched.add(pos);
                if (!level.hasChunkAt(pos)) { valid = false;continue; }
                var state = level.getBlockState(pos);
                var role = WetProcessStructure.rimRole(worldPosition, facing, candidate, candidateHeight, pos);
                if (cell.state().is(CEBlocks.TANK_CASING.get())) {
                    if (level.getBlockEntity(pos) instanceof TankCasingBlockEntity casing) {
                        if (role != null && casing.clarifier() != null && casing.clarifier() != this) valid = false;
                    } else if (state.is(CEWetProcessing.PARTS.get(BASIN).get())) {
                        // Existing odd-sized basins can retain their original lining.
                    } else if (role == null || !state.is(CEWetProcessing.PARTS.get(role).get())
                        || state.getValue(WetProcessPartBlock.FACING) != (role == OVERFLOW ? facing : facing.getOpposite())) valid = false;
                } else if (state != cell.state()) valid = false;
                if (role != null || pos.equals(feed)) {
                    ports.add(pos);
                    if (level.getBlockEntity(pos) instanceof WetProcessPartBlockEntity part && part.owner() != null && part.owner() != this && part.owner().formed()) valid = false;
                }
                states.put(pos, state);
            }
            if (!valid) continue;
            width = candidate;height = candidateHeight;
            if (Arrays.stream(tanks).anyMatch(tank -> tank.getFluidAmount() > capacity())) { status = "capacity";break; }
            formed = true;watched.clear();watched.addAll(states.keySet());
            expected = Map.copyOf(states);linked = Set.copyOf(ports);break;
        }
        members = linked;
        for (var pos : watched) chunkPositions.add(new BlockPos(pos.getX() >> 4 << 4, 0, pos.getZ() >> 4 << 4));
        chunks = List.copyOf(chunkPositions);
        net.createplant.event.WetProcessChanges.watch(level, worldPosition, watched);
        if (!formed && !status.equals("capacity")) status = "structure";
        if (formed) for (var pos : members) {
            var entity = level.getBlockEntity(pos);
            if (entity instanceof WetProcessPartBlockEntity part) part.bind(worldPosition);
            if (entity instanceof TankCasingBlockEntity casing) casing.bindClarifier(worldPosition);
            level.invalidateCapabilities(pos);
            var role = rimRole(pos);
            if (role == UNDERFLOW) continue;
            var side = role == OVERFLOW ? facing : Direction.DOWN;
            var adjacent = pos.relative(side);
            if (!level.hasChunkAt(adjacent)) continue;
            var pipe = level.getBlockState(adjacent);
            if (pipe.getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock block) {
                var updated = block.updateBlockState(pipe, side, null, level, adjacent);
                if (updated != pipe) level.setBlock(adjacent, updated, 2);
                pipe = updated;
            }
            com.simibubi.create.content.fluids.FluidPropagator.propagateChangedPipe(level, adjacent, pipe);
        }
        contentsChanged = true;nextRebuild = level.getGameTime() + 20;
        if (formed != previouslyFormed || width != previousWidth || height != previousHeight || !status.equals(previousStatus)) { changed();sendData(); }
    }
    public WetProcessPartBlock.Role rimRole(BlockPos pos) {
        return formed ? WetProcessStructure.rimRole(worldPosition, getBlockState().getValue(WetProcessDriveBlock.FACING), width, height, pos) : null;
    }
    public Direction front() { return getBlockState().getValue(WetProcessDriveBlock.FACING); }
    private static boolean rimWall(BlockState state) {
        return state.is(CEBlocks.TANK_CASING.get()) || state.is(CEWetProcessing.PARTS.get(BASIN).get()) || state.is(CEWetProcessing.PARTS.get(OVERFLOW).get());
    }
    @Override public void initialize() { super.initialize();if (members.isEmpty()) dirty = true; }
    @Override public void onChunkUnloaded() {
        structureChanged();if (level != null) net.createplant.event.WetProcessChanges.forget(level, worldPosition);super.onChunkUnloaded();
    }
    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (formed) for (var pos : chunks) if (!level.hasChunkAt(pos)) { structureChanged();break; }
        if ((dirty || !formed) && level.getGameTime() >= nextRebuild) rebuild();
        if (!formed) return;
        process();
        if (level.getGameTime() - synced >= 20 && (clientDirty || !status.equals(lastStatus))) { synced = level.getGameTime();lastStatus = status;clientDirty = false;sendData(); }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<RecipeHolder<? extends StandardProcessingRecipe<RecipeInput>>> recipes() {
        return (List) level.getRecipeManager().getAllRecipesFor(CERecipeTypes.CLARIFYING.getType());
    }
    private boolean inputs(StandardProcessingRecipe<RecipeInput> recipe) {
        if (recipe.getFluidIngredients().size() != 1 || recipe.getFluidResults().size() != 1 || recipe.getRollableResults().size() > 1 || !recipe.getIngredients().isEmpty()) return false;
        for (int i = 0; i < recipe.getFluidIngredients().size(); i++) {
            var ingredient = recipe.getFluidIngredients().get(i);
            if (!ingredient.ingredient().test(tanks[i].getFluid()) || tanks[i].getFluidAmount() < ingredient.amount()) return false;
        }
        return true;
    }
    private FluidStack result(StandardProcessingRecipe<RecipeInput> recipe) {
        if (Math.abs(getSpeed()) >= 17) return tanks[0].getFluid().copyWithAmount(recipe.getFluidIngredients().getFirst().amount());
        FluidStack fluid = recipe.getFluidResults().getFirst().copy();
        if (impurity(getSpeed()) > 0) fluid.set(CEThermal.SUSPENDED_IMPURITY.get(), impurity(getSpeed()));
        return fluid;
    }
    private ItemStack mud(StandardProcessingRecipe<RecipeInput> recipe) {
        return Math.abs(getSpeed()) < 17 && !recipe.getRollableResults().isEmpty() ? recipe.getRollableResults().getFirst().getStack().copy() : ItemStack.EMPTY;
    }
    private boolean outputs(StandardProcessingRecipe<RecipeInput> recipe) {
        var fluid = result(recipe);var mud = mud(recipe);
        return tanks[2].fill(fluid, SIMULATE) == fluid.getAmount() && tanks[2].getFluidAmount() + fluid.getAmount() <= capacity()
            && (mud.isEmpty() || items.insertItem(4, mud, true).isEmpty());
    }
    public void process() {
        if (!formed || isRemoved()) { ProductionMonitor.limitedBy(this, "structure");return; }
        if (contentsChanged || cached != null && level.getRecipeManager().byKey(cached.id()).map(RecipeHolder::value).orElse(null) != cached.value()) {
            cached = null;
            for (var recipe : recipes()) if (inputs(recipe.value())) { cached = recipe;break; }
            contentsChanged = false;
        }
        if (cached == null || !inputs(cached.value())) { progress = 0;boosted = false;batch = "";status = "feed";ProductionMonitor.limitedBy(this, "feed");return; }
        var recipe = cached.value();
        var fluid = result(recipe);var mud = mud(recipe);
        double batches = ProductionMonitor.batchRate(1, 1, recipe.getProcessingDuration(), 2);
        ProductionMonitor.plan(this, fluid.getAmount() * batches, mud.getCount() * batches, 0);
        if (!outputs(recipe)) { status = "output";ProductionMonitor.limitedBy(this, "output");return; }
        if (!cached.id().toString().equals(batch)) { batch = cached.id().toString();progress = 0;boosted = false; }
        duration = Math.max(1, recipe.getProcessingDuration());
        if (!boosted && progress == 0 && items.getStackInSlot(3).is(CEWetProcessing.FLOCCULANT.get()) && Math.abs(getSpeed()) < 17) {
            items.extractItem(3, 1, false);boosted = true;
        }
        progress += boosted ? 2 : 1;
        status = (Math.abs(getSpeed()) >= 17 ? "turbulent" : impurity(getSpeed()) > 0 ? "dirty" : getSpeed() == 0 ? "stagnant" : "clarifying");
        ProductionMonitor.limitedBy(this, switch (status) {
            case "turbulent" -> "reduce_speed";
            case "dirty", "stagnant" -> "clarifier_speed";
            default -> boosted ? "rated" : "flocculant";
        });
        changed();
        if (progress < duration) return;
        if (!inputs(recipe) || !outputs(recipe)) return;
        for (int i = 0; i < recipe.getFluidIngredients().size(); i++) tanks[i].drain(recipe.getFluidIngredients().get(i).amount(), EXECUTE);
        tanks[2].fill(fluid, EXECUTE);
        if (!mud.isEmpty()) items.insertItem(4, mud, false);
        ProductionMonitor.produced(this, fluid.getAmount(), mud.getCount(), 0);
        progress = 0;boosted = false;contentsChanged = true;
    }
    private boolean validInput(int index, FluidStack fluid) {
        if (fluid.isEmpty()) return false;
        if (index == 0 && (net.createplant.transport.FluidHazards.of(fluid).gas() || fluid.getFluidType().isLighterThanAir())) return false;
        for (var holder : recipes()) if (holder.value().getFluidIngredients().size() > index && holder.value().getFluidIngredients().get(index).ingredient().test(fluid)) return true;
        return false;
    }
    private boolean lease(long epoch) { return formed && !dirty && generation == epoch && !isRemoved() && level != null && level.hasChunkAt(worldPosition); }
    public IFluidHandler fluidPort(WetProcessPartBlock.Role role) {
        int index = switch (role) { case FEED -> 0;case OVERFLOW -> 2;default -> -1; };
        if (index < 0 || !formed) return null;
        long epoch = generation;boolean output = index == 2;
        return new IFluidHandler() {
            private boolean ready() { return lease(epoch); }
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int tank) { return tank == 0 && ready() ? tanks[index].getFluid().copy() : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int tank) { return tank == 0 && ready() ? capacity() : 0; }
            @Override public boolean isFluidValid(int tank, FluidStack fluid) { return tank == 0 && ready() && !output && validInput(index, fluid); }
            @Override public int fill(FluidStack fluid, FluidAction action) {
                if (!isFluidValid(0, fluid)) return 0;
                return tanks[index].fill(fluid.copyWithAmount(Math.min(fluid.getAmount(), Math.max(0, capacity() - tanks[index].getFluidAmount()))), action);
            }
            @Override public FluidStack drain(int amount, FluidAction action) { return ready() && output ? tanks[index].drain(amount, action) : FluidStack.EMPTY; }
            @Override public FluidStack drain(FluidStack fluid, FluidAction action) { return ready() && output ? tanks[index].drain(fluid, action) : FluidStack.EMPTY; }
        };
    }
    public IItemHandler itemPort(WetProcessPartBlock.Role role) {
        if (!formed || role != UNDERFLOW) return null;
        long epoch = generation;
        return new IItemHandler() {
            @Override public int getSlots() { return 2; }
            private int index(int slot) { return slot == 0 ? 3 : 4; }
            private boolean ready(int slot) { return slot >= 0 && slot < getSlots() && lease(epoch); }
            @Override public ItemStack getStackInSlot(int slot) { return ready(slot) ? items.getStackInSlot(index(slot)).copy() : ItemStack.EMPTY; }
            @Override public int getSlotLimit(int slot) { return ready(slot) ? 64 : 0; }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                if (!ready(slot) || stack.isEmpty()) return false;
                return slot == 0 && stack.is(CEWetProcessing.FLOCCULANT.get());
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return isItemValid(slot, stack) ? items.insertItem(index(slot), stack, simulate) : stack; }
            @Override public ItemStack extractItem(int slot, int count, boolean simulate) { return ready(slot) && slot == 1 && getSpeed() != 0 ? items.extractItem(4, count, simulate) : ItemStack.EMPTY; }
        };
    }

    public ItemStack takeFlocculant(boolean simulate) {
        return formed && progress == 0 ? items.extractItem(3, 64, simulate) : ItemStack.EMPTY;
    }
    public void preview(int size, int tankHeight, FluidStack feed, FluidStack product, String state) {
        if (!isVirtual()) return;
        width = size;height = tankHeight;formed = true;status = state;
        for (var cell : WetProcessStructure.cells(worldPosition, front(), width, height)) {
            if (level.getBlockEntity(cell.pos()) instanceof WetProcessPartBlockEntity part) part.bind(worldPosition);
            if (rimRole(cell.pos()) != null && level.getBlockEntity(cell.pos()) instanceof TankCasingBlockEntity casing) casing.bindClarifier(worldPosition);
        }
        tanks[0].setFluid(feed.copy());tanks[2].setFluid(product.copy());
    }
    public void release() {
        structureChanged();if (level != null) net.createplant.event.WetProcessChanges.forget(level, worldPosition);
    }
    @Override public float calculateStressApplied() { return lastStressApplied = 8; }
    @Override public AABB getRenderBoundingBox() { int radius = Math.max(3, width) / 2;return new AABB(worldPosition.offset(-radius, 1 - height(), -radius)).minmax(new AABB(worldPosition.offset(radius, 1, radius))); }
    @Override public boolean addToGoggleTooltip(List<Component> lines, boolean sneaking) {
        String key = "wet_process.create_refinery_n_reactors.";
        lines.add(Component.translatable(key + "dimensions", width, width, height()));
        lines.add(Component.translatable(key + "status." + status));
        lines.add(Component.translatable(key + "drive", Math.round(getSpeed()), Math.round(Math.abs(getSpeed()) * 8)));
        lines.add(Component.translatable(key + "separation", 100 - impurity(getSpeed()) / 10));return true;
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);writeContents(tag, lookup);
    }
    public void writeContents(CompoundTag tag, HolderLookup.Provider lookup) {
        for (int i = 0; i < 3; i++) tag.put("Tank" + i, tanks[i].writeToNBT(lookup, new CompoundTag()));
        tag.put("Items", items.serializeNBT(lookup));tag.putInt("Width", width);tag.putInt("Height", height);tag.putBoolean("Formed", formed);
        tag.putDouble("Progress", progress);tag.putInt("Duration", duration);tag.putString("Batch", batch);tag.putString("Status", status);
        tag.putBoolean("Boosted", boosted);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        for (int i = 0; i < 3; i++) tanks[i].readFromNBT(lookup, tag.getCompound("Tank" + i));
        items.deserializeNBT(lookup, tag.getCompound("Items"));width = Math.clamp(tag.getInt("Width"), 0, 30);height = Math.clamp(tag.getInt("Height"), 3, 4);
        formed = packet && tag.getBoolean("Formed");progress = Double.isFinite(tag.getDouble("Progress")) ? Math.max(0, tag.getDouble("Progress")) : 0;
        duration = Math.max(1, tag.getInt("Duration"));batch = tag.getString("Batch");status = tag.getString("Status");
        boosted = tag.getBoolean("Boosted");
        if (!packet) { dirty = true;cached = null;contentsChanged = true; }
    }
}
