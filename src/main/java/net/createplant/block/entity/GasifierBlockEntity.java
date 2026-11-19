package net.createplant.block.entity;

import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.*;
import net.createplant.CreatePlant;
import net.createplant.block.GasifierBlock;
import net.createplant.recipe.EntrainedGasificationRecipe;
import net.createplant.registry.CEGasifier;
import net.createplant.registry.CERecipeTypes;
import net.createplant.thermal.Grade;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

public final class GasifierBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final int REACTION_TEMPERATURE = 800, HEATER_TARGET = 1000, BATCH_HEAT_LOSS = 60, SPEED = 10;
    private static final TagKey<Fluid> STEAM_INPUTS = TagKey.create(Registries.FLUID, CreatePlant.location("gasifier_steam"));
    // Tank indices are part of the saved inventory and include recovery-only slots.
    private static final int OXIDIZER = 0, STEAM = 1, GAS = 2, COOLANT_IN = 3, COOLANT_OUT = 4, QUENCH = 5;

    private BlockPos controller;
    private Set<BlockPos> structure = Set.of();
    private List<BlockPos> chunks = List.of();
    private long generation, recheckAt;
    private Direction front = Direction.NORTH;
    private boolean formed, glowing, syncPending, recoveryPortsChanged;
    private String status = "structure";
    private BlockPos mismatch;
    private Inventory inventory;

    private final class Inventory {
        final ThermalFluidTank[] tanks = { tank(8000), tank(8000), tank(16000), tank(8000), tank(8000), tank(4000) };
        final ItemStackHandler items = new ItemStackHandler(4) {
            @Override protected void onContentsChanged(int slot) { changed(); }
        };
        ItemStack hotSlag = ItemStack.EMPTY;
        int temperature = 20, progress, duration;
        ResourceLocation batch;
        boolean feedChanged = true;
        RecipeHolder<EntrainedGasificationRecipe> selected;
        int fuelSlot = -1;
    }

    public GasifierBlockEntity(BlockPos pos, BlockState state) { super(CEGasifier.PART.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }
    private ThermalFluidTank tank(int capacity) {
        return new ThermalFluidTank(capacity, stack -> changed()) {
            @Override protected int storageRatio() { return 1; }
        };
    }
    private void changed() {
        if (inventory != null) inventory.feedChanged = true;
        syncPending = true;
        setChanged();
    }
    public GasifierBlockEntity owner() {
        if (formed && (inventory != null || isVirtual())) return this;
        if (controller == null || level == null || !level.hasChunkAt(controller)) return null;
        if (level.getBlockEntity(controller) instanceof GasifierBlockEntity root && root.formed && !root.isRemoved()
            && (level.isClientSide || isVirtual() || root.structure.contains(worldPosition))) return root;
        return null;
    }
    public boolean formed() { return formed; }
    public Direction front() { return front; }
    public int temperature() { return inventory == null ? 20 : inventory.temperature; }
    public double progress() { return inventory == null ? 0 : inventory.progress / 10d; }
    public double duration() { return inventory == null ? 0 : inventory.duration / 10d; }
    public String status() { return status; }
    public FluidStack fluid(int index) { return inventory == null ? FluidStack.EMPTY : inventory.tanks[index].getFluid().copy(); }
    public int capacity(int index) { return inventory == null ? 0 : inventory.tanks[index].getCapacity(); }
    public int itemCount(int index) { return inventory == null ? 0 : inventory.items.getStackInSlot(index).getCount(); }
    public static Component message(String key, Object... values) { return Component.translatable("gasifier." + CreatePlant.MOD_ID + "." + key, values); }

    /** Forms the furnace whose bottom centre is the loose block at or near a newly placed one, if the 3x3x6 is complete. */
    public static void formAround(Level level, BlockPos placed) {
        for (int y = 0; y < 6; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            var origin = placed.offset(x, -y, z);
            if (!isGasifier(level, origin) || !isGasifier(level, origin.above(5))) continue;
            boolean centre = true;
            for (var side : Direction.Plane.HORIZONTAL) centre &= isGasifier(level, origin.relative(side));
            if (centre && level.getBlockEntity(origin) instanceof GasifierBlockEntity root && root.owner() == null && root.rebuild()) return;
        }
    }

    private static boolean isGasifier(Level level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(CEGasifier.GASIFIER.get());
    }

    public boolean rebuild() {
        if (level == null || level.isClientSide || isRemoved()) return false;
        if (formed) return true;
        var cells = new LinkedHashMap<BlockPos, BlockState>();
        var sectionColumns = new HashSet<BlockPos>();
        for (int y = 0; y < 6; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            var pos = worldPosition.offset(x, y, z);
            if (!level.hasChunkAt(pos)) return incomplete(pos);
            var state = level.getBlockState(pos);
            if (!state.is(CEGasifier.GASIFIER.get()) || !(level.getBlockEntity(pos) instanceof GasifierBlockEntity member)
                || member != this && (member.formed || member.inventory != null || member.owner() != null)) return incomplete(pos);
            cells.put(pos, state);
            sectionColumns.add(new BlockPos(pos.getX() >> 4 << 4, 0, pos.getZ() >> 4 << 4));
        }
        if (inventory == null) inventory = new Inventory();
        structure = Set.copyOf(cells.keySet());
        chunks = List.copyOf(sectionColumns);
        front = level.getBlockState(worldPosition.above(5)).getValue(GasifierBlock.FACING);
        glowing = inventory.temperature >= REACTION_TEMPERATURE;
        mismatch = null;
        formed = true;
        generation++;
        status = "heat_required";
        for (var pos : structure) {
            var delta = pos.subtract(worldPosition);
            int cell = GasifierBlock.cell(delta.getY(), delta.getX(), delta.getZ());
            show(pos, cells.get(pos).setValue(GasifierBlock.CELL, cell).setValue(GasifierBlock.FACING, front)
                .setValue(GasifierBlock.LIT, glowing && GasifierBlock.viewport(cell)));
            if (level.getBlockEntity(pos) instanceof GasifierBlockEntity part && part != this && !worldPosition.equals(part.controller)) {
                part.controller = worldPosition;
                part.setChanged();
                part.sendData();
            }
        }
        updatePorts();
        sendData();
        setChanged();
        return true;
    }

    /** Changes only how a member looks; the members' own state changes are not structure changes. */
    private void show(BlockPos pos, BlockState state) {
        if (level.getBlockState(pos) != state) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    /** Returns every loaded block of this furnace's footprint that still shows its cell here to a loose block. */
    private void dissolve() {
        for (int y = 0; y < 6; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            var pos = worldPosition.offset(x, y, z);
            if (!level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            if (state.is(CEGasifier.GASIFIER.get()) && state.getValue(GasifierBlock.CELL) == GasifierBlock.cell(y, x, z))
                show(pos, state.setValue(GasifierBlock.CELL, 0).setValue(GasifierBlock.LIT, false));
        }
    }

    private boolean incomplete(BlockPos pos) {
        if (!pos.equals(mismatch)) {
            mismatch = pos;
            sendData();
        }
        return false;
    }

    /** Unforms after a member is removed and returns the remaining blocks to loose blocks. */
    public void invalidateStructure(boolean removing) {
        if (level == null || level.isClientSide) return;
        var root = owner();
        if (root != null && root != this) {
            root.invalidateStructure(false);
            return;
        }
        if (!formed) return;
        unform();
        dissolve();
        if (!removing) {
            setChanged();
            sendData();
        }
    }

    private void unform() {
        formed = false;
        generation++;
        status = "structure";
        updatePorts();
        structure = Set.of();
        chunks = List.of();
    }

    @Override public void onChunkUnloaded() {
        if (formed && !level.isClientSide) unform();
        super.onChunkUnloaded();
    }

    /** The gas outlet on the roof of the front's middle block, or a steam inlet on the outer face of a base side's middle block. */
    private int bank(BlockPos pos, Direction side) {
        if (side == null) return -1;
        var delta = pos.subtract(worldPosition);
        if (delta.equals(new BlockPos(front.getStepX(), 5, front.getStepZ())) && side == Direction.UP) return GAS;
        return delta.getY() == 0 && side.getAxis().isHorizontal() && delta.equals(BlockPos.ZERO.relative(side)) ? STEAM : -1;
    }

    /** The saved fluid an inlet gives back from the furnace's former design: oxidizer, coolant, warm water or quench water. */
    private static int recoveryBank(Direction side) {
        return switch (side) {
            case NORTH -> OXIDIZER;
            case WEST -> COOLANT_IN;
            case EAST -> COOLANT_OUT;
            default -> QUENCH;
        };
    }

    private boolean slagFace(BlockPos pos, Direction face) {
        return pos.equals(worldPosition) && face == Direction.DOWN || pos.equals(worldPosition.north()) && face == Direction.NORTH;
    }

    private void updatePorts() {
        for (var pos : structure) {
            if (!level.hasChunkAt(pos)) continue;
            level.invalidateCapabilities(pos);
            for (var face : Direction.values()) {
                if (bank(pos, face) < 0) continue;
                var adjacent = pos.relative(face);
                if (!level.hasChunkAt(adjacent)) continue;
                var state = level.getBlockState(adjacent);
                if (state.getBlock() instanceof FluidPipeBlock pipe) {
                    var updated = pipe.updateBlockState(state, face, null, level, adjacent);
                    if (updated != state) level.setBlock(adjacent, updated, 2);
                    FluidPropagator.propagateChangedPipe(level, adjacent, updated);
                } else if (FluidPropagator.getPipe(level, adjacent) != null) {
                    FluidPropagator.propagateChangedPipe(level, adjacent, state);
                }
            }
        }
    }

    private boolean ready(long epoch) {
        if (!formed || isRemoved() || epoch != generation || level == null || inventory == null) return false;
        for (var pos : chunks) if (!level.hasChunkAt(pos)) return false;
        return true;
    }

    public IFluidHandler fluidPort(Direction side) {
        var root = owner();
        if (root == null || !root.ready(root.generation)) return null;
        int bank = root.bank(worldPosition, side);
        if (bank != STEAM) return bank == GAS ? root.fluidView(GAS, -1) : null;
        int recovery = recoveryBank(side);
        return root.fluidView(STEAM, root.inventory.tanks[recovery].isEmpty() ? -1 : recovery);
    }

    private IFluidHandler fluidView(int first, int recovery) {
        long epoch = generation;
        int count = recovery < 0 ? 1 : 2;
        return new IFluidHandler() {
            private boolean slot(int index) { return ready(epoch) && index >= 0 && index < count; }
            private int bank(int index) { return index == 0 ? first : recovery; }
            private int outputBank() { return recovery < 0 ? first : recovery; }
            private boolean canDrain() { return ready(epoch) && (first != STEAM || recovery >= 0); }
            private FluidStack drained(FluidStack stack, FluidAction action) {
                if (outputBank() != GAS && !stack.isEmpty() && action.execute() && inventory.tanks[outputBank()].isEmpty())
                    recoveryPortsChanged = true;
                return stack;
            }
            @Override public int getTanks() { return count; }
            @Override public FluidStack getFluidInTank(int index) { return slot(index) ? fluid(bank(index)) : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int index) { return slot(index) ? capacity(bank(index)) : 0; }
            @Override public boolean isFluidValid(int index, FluidStack stack) { return slot(index) && index == 0 && first == STEAM && stack.is(STEAM_INPUTS); }
            @Override public int fill(FluidStack stack, FluidAction action) {
                return isFluidValid(0, stack) ? inventory.tanks[STEAM].fill(stack, action) : 0;
            }
            @Override public FluidStack drain(FluidStack stack, FluidAction action) {
                return canDrain() ? drained(inventory.tanks[outputBank()].drain(stack, action), action) : FluidStack.EMPTY;
            }
            @Override public FluidStack drain(int amount, FluidAction action) {
                return canDrain() ? drained(inventory.tanks[outputBank()].drain(amount, action), action) : FluidStack.EMPTY;
            }
        };
    }

    public IItemHandler itemPort(Direction side) {
        var root = owner();
        if (root == null || !root.ready(root.generation)) return null;
        if (side == Direction.UP && worldPosition.getY() == root.worldPosition.getY() + 5) return root.itemView(false);
        return root.slagFace(worldPosition, side) && root.hasLegacySlag() ? root.itemView(true) : null;
    }

    private boolean hasLegacySlag() {
        return !inventory.hotSlag.isEmpty() || !inventory.items.getStackInSlot(2).isEmpty() || !inventory.items.getStackInSlot(3).isEmpty();
    }

    private IItemHandler itemView(boolean output) {
        long epoch = generation;
        int first = output ? 2 : 0;
        return new IItemHandler() {
            private boolean slot(int index) { return ready(epoch) && index >= 0 && index < getSlots(); }
            @Override public int getSlots() { return output ? 3 : 2; }
            @Override public ItemStack getStackInSlot(int index) {
                if (!slot(index)) return ItemStack.EMPTY;
                return (index == 2 ? inventory.hotSlag : inventory.items.getStackInSlot(first + index)).copy();
            }
            @Override public int getSlotLimit(int index) { return slot(index) ? 64 : 0; }
            @Override public boolean isItemValid(int index, ItemStack stack) {
                return !output && slot(index) && recipes().stream().anyMatch(holder -> holder.value().ingredient().test(stack));
            }
            @Override public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
                return isItemValid(index, stack) ? inventory.items.insertItem(first + index, stack, simulate) : stack;
            }
            @Override public ItemStack extractItem(int index, int count, boolean simulate) {
                if (!output || !slot(index) || count <= 0) return ItemStack.EMPTY;
                if (index != 2) return inventory.items.extractItem(first + index, count, simulate);
                var slag = inventory.hotSlag.copyWithCount(Math.min(count, inventory.hotSlag.getCount()));
                if (!simulate && !slag.isEmpty()) {
                    inventory.hotSlag.shrink(slag.getCount());
                    changed();
                }
                return slag;
            }
        };
    }

    private List<RecipeHolder<EntrainedGasificationRecipe>> recipes() {
        return level.getRecipeManager().getAllRecipesFor(CERecipeTypes.ENTRAINED_GASIFICATION.<RecipeInput, EntrainedGasificationRecipe>getType());
    }

    private int fuel(EntrainedGasificationRecipe recipe) {
        for (int slot = 0; slot < 2; slot++) if (recipe.ingredient().test(inventory.items.getStackInSlot(slot))) return slot;
        return -1;
    }

    private EntrainedGasificationRecipe findRecipe() {
        if (inventory.feedChanged || inventory.selected != null
            && level.getRecipeManager().byKey(inventory.selected.id()).map(RecipeHolder::value).orElse(null) != inventory.selected.value()) {
            inventory.selected = null;
            inventory.fuelSlot = -1;
            for (var holder : recipes()) {
                var recipe = holder.value();
                int slot = fuel(recipe);
                if (slot >= 0 && recipe.steam().test(inventory.tanks[STEAM].getFluid())) {
                    inventory.selected = holder;
                    inventory.fuelSlot = slot;
                    break;
                }
            }
            inventory.feedChanged = false;
        }
        return inventory.selected == null ? null : inventory.selected.value();
    }

    private void heat(int target, int rate) {
        inventory.temperature += Math.clamp(target - inventory.temperature, -rate, rate);
    }

    private void planProduction(EntrainedGasificationRecipe recipe) {
        ProductionMonitor.plan(this, ProductionMonitor.batchRate(recipe.hotGasResult().getAmount(), 1, recipe.duration(), 1), 0, 0);
    }

    private void process() {
        var recipe = findRecipe();
        var meter = ProductionMonitor.of(this);
        if (recipe != null) planProduction(recipe);
        else if (meter != null && meter.read(ProductionMonitor.Output.FLUID) == null) {
            var recipes = recipes();
            if (recipes.size() == 1) planProduction(recipes.getFirst().value());
        }
        var heater = worldPosition.below();
        if (!(BoilerHeater.findHeat(level, heater, level.getBlockState(heater)) > 0)) {
            status = "heat_required";
            heat(20, 4);
            return;
        }
        heat(HEATER_TARGET, 24);
        if (inventory.temperature < REACTION_TEMPERATURE) {
            status = "preheat";
            return;
        }
        if (recipe == null) {
            inventory.progress = 0;
            inventory.duration = 0;
            inventory.batch = null;
            status = "feed";
            return;
        }
        var id = inventory.selected.id();
        if (!id.equals(inventory.batch)) {
            inventory.batch = id;
            inventory.progress = 0;
        }
        inventory.duration = recipe.duration() * 10;
        var gas = recipe.hotGasResult();
        if (inventory.tanks[GAS].fill(gas, SIMULATE) != gas.getAmount()) {
            status = "gas_output";
            return;
        }
        status = "running";
        inventory.progress += SPEED;
        if (inventory.progress < inventory.duration) return;
        inventory.progress = Math.min(inventory.progress - inventory.duration, inventory.duration - 1);
        inventory.items.extractItem(inventory.fuelSlot, 1, false);
        inventory.tanks[STEAM].drain(recipe.steam().amount(), EXECUTE);
        ProductionMonitor.produced(this, inventory.tanks[GAS].fill(gas, EXECUTE), 0, 0);
        heat(20, BATCH_HEAT_LOSS);
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide || isVirtual()) return;
        long time = level.getGameTime();
        if (!formed) {
            if (time < recheckAt) return;
            recheckAt = time + 20;
            for (int x = -1; x <= 1; x += 2) for (int z = -1; z <= 1; z += 2)
                if (!level.hasChunkAt(worldPosition.offset(x, 0, z))) return;
            if (!rebuild()) dissolve();
            return;
        }
        if (!ready(generation)) {
            unform();
            return;
        }
        if (recoveryPortsChanged) {
            recoveryPortsChanged = false;
            updatePorts();
        }
        var previous = status;
        int before = inventory.temperature;
        int progress = inventory.progress;
        process();
        ProductionMonitor.limitedBy(this, switch (status) {
            case "heat_required", "preheat" -> "heat";
            case "gas_output" -> "output";
            case "running" -> "rated";
            default -> status;
        });
        if (glowing != inventory.temperature >= REACTION_TEMPERATURE) {
            glowing = !glowing;
            for (var pos : structure) {
                var delta = pos.subtract(worldPosition);
                int cell = GasifierBlock.cell(delta.getY(), delta.getX(), delta.getZ());
                if (GasifierBlock.viewport(cell) && level.hasChunkAt(pos)) show(pos, level.getBlockState(pos).setValue(GasifierBlock.LIT, glowing));
            }
        }
        if (before != inventory.temperature || progress != inventory.progress || !previous.equals(status)) {
            syncPending = true;
            setChanged();
        }
        if (syncPending && (time % 20 == 0 || !previous.equals(status))) {
            syncPending = false;
            sendData();
        }
    }

    public void preview(String state, int temperature) {
        if (!isVirtual()) return;
        if (inventory == null) inventory = new Inventory();
        formed = true;
        status = state;
        inventory.temperature = temperature;
        for (int y = 0; y < 6; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (level.getBlockEntity(worldPosition.offset(x, y, z)) instanceof GasifierBlockEntity part && part != this)
                part.controller = worldPosition;
        }
    }

    public boolean hasStoredContents() {
        return inventory != null && (!inventory.hotSlag.isEmpty() || Arrays.stream(inventory.tanks).anyMatch(tank -> !tank.isEmpty())
            || java.util.stream.IntStream.range(0, 4).anyMatch(slot -> !inventory.items.getStackInSlot(slot).isEmpty()));
    }

    @Override public boolean addToGoggleTooltip(List<Component> lines, boolean sneaking) {
        var root = owner();
        if (root != null && root != this) return root.addToGoggleTooltip(lines, sneaking);
        lines.add(message(status));
        if (!formed) {
            if (mismatch != null) lines.add(message("check", mismatch.getX(), mismatch.getY(), mismatch.getZ()));
            return true;
        }
        lines.add(message("heat", Grade.Heat.of(temperature() + 273.15).label()));
        lines.add(message("stock", fluid(GAS).getAmount(), fluid(STEAM).getAmount()));
        return true;
    }

    public void writeInventory(CompoundTag tag, HolderLookup.Provider lookup) {
        if (inventory == null) return;
        var data = new CompoundTag();
        for (int i = 0; i < inventory.tanks.length; i++) data.put("Tank" + i, inventory.tanks[i].writeToNBT(lookup, new CompoundTag()));
        data.put("Items", inventory.items.serializeNBT(lookup));
        if (!inventory.hotSlag.isEmpty()) data.put("HotSlag", inventory.hotSlag.save(lookup));
        data.putInt("Temperature", inventory.temperature);
        data.putInt("Progress", inventory.progress);
        data.putInt("Duration", inventory.duration);
        if (inventory.batch != null) data.putString("Batch", inventory.batch.toString());
        tag.put("Gasifier", data);
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        writeInventory(tag, lookup);
        if (controller != null) tag.putLong("Controller", controller.asLong());
        if (packet) {
            tag.putBoolean("Formed", formed);
            tag.putString("Status", status);
            if (mismatch != null) tag.putLong("Mismatch", mismatch.asLong());
        }
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        formed = packet && tag.getBoolean("Formed");
        status = packet ? tag.getString("Status") : "structure";
        if (status.isEmpty()) status = "structure";
        mismatch = tag.contains("Mismatch") ? BlockPos.of(tag.getLong("Mismatch")) : null;
        generation++;
        if (!tag.contains("Gasifier")) return;
        inventory = new Inventory();
        var data = tag.getCompound("Gasifier");
        for (int i = 0; i < inventory.tanks.length; i++) inventory.tanks[i].readFromNBT(lookup, data.getCompound("Tank" + i));
        inventory.items.deserializeNBT(lookup, data.getCompound("Items"));
        inventory.hotSlag = ItemStack.parseOptional(lookup, data.getCompound("HotSlag"));
        inventory.temperature = Math.clamp(data.getInt("Temperature"), 20, 1800);
        inventory.progress = Math.clamp(data.getInt("Progress"), 0, 720000);
        inventory.duration = Math.clamp(data.getInt("Duration"), 0, 720000);
        inventory.batch = ResourceLocation.tryParse(data.getString("Batch"));
    }
}
