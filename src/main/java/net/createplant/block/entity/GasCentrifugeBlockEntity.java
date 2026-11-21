package net.createplant.block.entity;

import java.util.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createplant.block.GasCentrifugeBlock;
import net.createplant.chemistry.ProcessModule;
import net.createplant.chemistry.ProcessRecipe;
import net.createplant.registry.CEHighStressMachines;
import net.createplant.safety.ToxicGas;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class GasCentrifugeBlockEntity extends KineticBlockEntity {
    public static final int CAPACITY = 4000, MAX_UNITS = 16, SEARCH_LIMIT = 256;
    public final FluidTank feed = tank(), product = tank(), tails = tank();
    private List<BlockPos> members = List.of();
    private boolean dirty = true, limited, contentsChanged = true;
    private ProcessRecipe cachedRecipe;
    private double progress;
    private String recipeId = "", status = "feed";
    private int stages, required = 1;
    private long synced = -20;

    public GasCentrifugeBlockEntity(BlockPos pos, BlockState state) { super(CEHighStressMachines.CENTRIFUGE_ENTITY.get(), pos, state); }
    private FluidTank tank() { return new FluidTank(CAPACITY) { @Override protected void onContentsChanged() { contentsChanged = true;GasCentrifugeBlockEntity.this.setChanged(); } }; }
    public static int requiredStages(String id) { return switch (id) { case "concentrate_heavy_water" -> 2; case "enrich_uranium" -> 4; case "enrich_compact_uranium" -> 8; default -> 1; }; }
    public static GasCentrifugeBlockEntity at(Level level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof GasCentrifugeBlockEntity be && !be.isRemoved() ? be : null;
    }
    public static void changed(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        var affected = new HashSet<BlockPos>();affected.add(pos);
        for (Direction side : Direction.Plane.HORIZONTAL) affected.add(pos.relative(side));
        for (var candidate : List.copyOf(affected)) {
            var be = at(level, candidate);
            if (be != null) affected.addAll(be.members);
        }
        for (var member : affected) { var be = at(level, member);if (be != null) be.dirty = true; }
    }
    @Override public void initialize() { super.initialize();if (level != null && !level.isClientSide) changed(level, worldPosition); }
    @Override public void onChunkUnloaded() { if (level != null && !level.isClientSide) changed(level, worldPosition);super.onChunkUnloaded(); }
    private List<GasCentrifugeBlockEntity> units() {
        if (dirty && level != null && !level.isClientSide) rebuild();
        var result = new ArrayList<GasCentrifugeBlockEntity>();
        for (var pos : members) { var be = at(level, pos);if (be == null) { dirty = true;return List.of(); }result.add(be); }
        return result;
    }
    public GasCentrifugeBlockEntity leader() { var units = units();return units.isEmpty() ? this : units.getFirst(); }
    public boolean spinningCascade() { return (members.isEmpty() || members.getFirst().equals(worldPosition)) && stages > 0; }
    public int unitCount() { return members.size(); }
    public int stages() { return leader().stages; }
    public int required() { return leader().required; }
    public double progress() { var lead = leader();return lead.progress; }
    public Component processName() { String id = recipeId();return Component.translatable(id.isEmpty() ? "centrifuge.create_refinery_n_reactors.idle" : "process.create_refinery_n_reactors." + id); }
    public String recipeId() { return leader().recipeId; }
    public String status() { return leader().status; }
    public void rebuild() {
        dirty = false;
        var found = new TreeSet<BlockPos>();var queue = new ArrayDeque<BlockPos>();queue.add(worldPosition);
        while (!queue.isEmpty()) {
            var pos = queue.removeFirst();if (found.contains(pos) || at(level, pos) == null) continue;
            found.add(pos);
            if (found.size() > SEARCH_LIMIT) {
                for (var blocked : found) {
                    var be = at(level, blocked);be.status = "limit";be.members = List.of();be.dirty = false;
                    level.invalidateCapabilities(blocked);level.invalidateCapabilities(blocked.above());be.sendData();
                }
                return;
            }
            for (Direction side : Direction.Plane.HORIZONTAL) queue.add(pos.relative(side));
        }
        var remaining = new TreeSet<>(found);
        while (!remaining.isEmpty()) {
            var group = new TreeSet<BlockPos>();queue.clear();queue.add(remaining.first());
            while (!queue.isEmpty() && group.size() < MAX_UNITS) {
                var pos = queue.removeFirst();if (!remaining.remove(pos)) continue;group.add(pos);
                for (Direction side : Direction.Plane.HORIZONTAL) if (remaining.contains(pos.relative(side))) queue.add(pos.relative(side));
            }
            var shared = List.copyOf(group);
            for (var pos : shared) {
                var be = at(level, pos);if (!be.members.equals(shared)) { be.progress = 0;be.recipeId = ""; }
                be.members = shared;be.dirty = false;be.contentsChanged = true;be.limited = false;
                var upper = pos.above();var state = level.getBlockState(upper);
                if (!state.is(getBlockState().getBlock())) continue;
                var next = state;
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    boolean connected = group.contains(pos.relative(side));
                    next = next.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), connected);
                    if (!connected && found.contains(pos.relative(side))) be.limited = true;
                }
                if (next != state) level.setBlock(upper, next, 2);
                level.invalidateCapabilities(pos);level.invalidateCapabilities(upper);be.setChanged();be.sendData();
            }
        }
    }
    @Override public void onSpeedChanged(float before) {
        super.onSpeedChanged(before);
        if (level == null || level.isClientSide) return;
        boolean active = Math.abs(getSpeed()) >= 32;
        for (var pos : List.of(worldPosition, worldPosition.above())) {
            var state = level.getBlockState(pos);
            if (state.is(getBlockState().getBlock()) && state.getValue(GasCentrifugeBlock.ACTIVE) != active)
                level.setBlock(pos, state.setValue(GasCentrifugeBlock.ACTIVE, active), 2);
        }
    }
    @Override public void tick() {
        super.tick();if (level != null && !level.isClientSide) process();
    }
    public void process() {
        if (level == null || level.isClientSide) return;
        String oldStatus = status;String oldRecipe = recipeId;int oldStages = stages;
        var group = units();if (group.isEmpty()) { status = "unloaded";ProductionMonitor.limitedBy(this, "structure");return; }
        if (group.getFirst() != this) return;
        stages = (int) group.stream().filter(be -> Math.abs(be.getSpeed()) >= 32).count();
        if (group.stream().anyMatch(be -> be.contentsChanged)) {
            cachedRecipe = ProcessRecipe.all().stream().filter(r -> r.module() == ProcessModule.GAS_CENTRIFUGE && available(group, 0, r.inputs().getFirst()) >= r.inputs().getFirst().getAmount()).findFirst().orElse(null);
            group.forEach(be -> be.contentsChanged = false);
        }
        var recipe = cachedRecipe;
        if (recipe == null) { status = "feed";progress = 0;recipeId = ""; }
        else {
            if (!recipe.id().equals(recipeId)) { progress = 0;recipeId = recipe.id(); }
            required = requiredStages(recipeId);
            double speed = Math.min(4, (double) group.size() / required);
            ProductionMonitor.plan(this, group.size() < required ? 0 : ProductionMonitor.fluids(recipe.outputs()) * 20 * Math.min(1, speed / recipe.ticks()), 0, 0);
            if (stages < required) status = limited && group.size() == 1 ? "limit" : stages == 0 ? "speed" : "stages";
            else if (space(group, 1, recipe.outputs().get(0)) < recipe.outputs().get(0).getAmount() || space(group, 2, recipe.outputs().get(1)) < recipe.outputs().get(1).getAmount()) status = "output";
            else {
                status = "running";progress += Math.min(4, (double) stages / required);setChanged();
                if (progress >= recipe.ticks()) {
                    drain(group, 0, recipe.inputs().getFirst(), recipe.inputs().getFirst().getAmount(), IFluidHandler.FluidAction.EXECUTE);
                    int made = fill(group, 1, recipe.outputs().get(0), IFluidHandler.FluidAction.EXECUTE);
                    made += fill(group, 2, recipe.outputs().get(1), IFluidHandler.FluidAction.EXECUTE);progress -= recipe.ticks();
                    ProductionMonitor.produced(this, made, 0, 0);
                }
            }
        }
        ProductionMonitor.limitedBy(this, switch (status) {
            case "limit", "stages" -> group.size() < required ? "stages" : "speed";
            case "running" -> stages < Math.min(group.size(), required * 4) ? "speed" : "rated";
            default -> status;
        });
        if (!oldStatus.equals(status) || !oldRecipe.equals(recipeId) || oldStages != stages || status.equals("running") && level.getGameTime() - synced >= 10) { synced = level.getGameTime();sendData(); }
    }
    public void preview(ProcessRecipe recipe, boolean complete) {
        if (!isVirtual()) return;
        recipeId = recipe.id();required = requiredStages(recipeId);stages = complete ? 0 : required;status = complete ? "feed" : "running";progress = complete ? 0 : recipe.ticks() / 2d;
        feed.setFluid(complete ? FluidStack.EMPTY : recipe.inputs().getFirst().copy());
        product.setFluid(complete ? recipe.outputs().get(0).copy() : FluidStack.EMPTY);tails.setFluid(complete ? recipe.outputs().get(1).copy() : FluidStack.EMPTY);
    }
    public int cycleTicks() { return ProcessRecipe.all().stream().filter(r -> r.id().equals(recipeId())).mapToInt(ProcessRecipe::ticks).findFirst().orElse(1); }
    public int amount(int bank) { return units().stream().mapToInt(be -> be.tank(bank).getFluidAmount()).sum(); }
    private FluidTank tank(int bank) { return bank == 0 ? feed : bank == 1 ? product : tails; }
    private static int available(List<GasCentrifugeBlockEntity> group, int bank, FluidStack fluid) {
        return group.stream().map(be -> be.tank(bank).getFluid()).filter(stack -> (bank == 0 ? net.createplant.thermal.ThermalStreams.accepts(stack, fluid) : FluidThermal.compatible(stack, fluid))).mapToInt(FluidStack::getAmount).sum();
    }
    private static int space(List<GasCentrifugeBlockEntity> group, int bank, FluidStack fluid) {
        return group.stream().mapToInt(be -> be.tank(bank).fill(fluid.copyWithAmount(Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE)).sum();
    }
    private static int fill(List<GasCentrifugeBlockEntity> group, int bank, FluidStack fluid, IFluidHandler.FluidAction action) {
        int remaining = fluid.getAmount();
        for (var be : group) { int n = be.tank(bank).fill(fluid.copyWithAmount(remaining), action);remaining -= n;if (remaining == 0) break; }
        return fluid.getAmount() - remaining;
    }
    private static FluidStack drain(List<GasCentrifugeBlockEntity> group, int bank, FluidStack fluid, int amount, IFluidHandler.FluidAction action) {
        fluid = fluid.copy();
        int remaining = amount;
        for (var be : group) { if (!(bank == 0 ? net.createplant.thermal.ThermalStreams.accepts(be.tank(bank).getFluid(), fluid) : FluidThermal.compatible(be.tank(bank).getFluid(), fluid))) continue;remaining -= be.tank(bank).drain(remaining, action).getAmount();if (remaining == 0) break; }
        return fluid.copyWithAmount(amount - remaining);
    }
    public IFluidHandler port(int bank) {
        return new IFluidHandler() {
            private List<GasCentrifugeBlockEntity> group() { return isRemoved() || level == null ? List.of() : units(); }
            @Override public int getTanks() { return group().size(); }
            @Override public FluidStack getFluidInTank(int index) { var g = group();return index >= 0 && index < g.size() ? g.get(index).tank(bank).getFluid().copy() : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int index) { return CAPACITY; }
            @Override public boolean isFluidValid(int index, FluidStack fluid) { return bank == 0 && ProcessRecipe.all().stream().anyMatch(r -> r.module() == ProcessModule.GAS_CENTRIFUGE && net.createplant.thermal.ThermalStreams.accepts(fluid, r.inputs().getFirst())); }
            @Override public int fill(FluidStack fluid, FluidAction action) { return fluid.isEmpty() || !isFluidValid(0, fluid) ? 0 : GasCentrifugeBlockEntity.fill(group(), 0, fluid, action); }
            @Override public FluidStack drain(FluidStack fluid, FluidAction action) { return bank == 0 || fluid.isEmpty() ? FluidStack.EMPTY : GasCentrifugeBlockEntity.drain(group(), bank, fluid, fluid.getAmount(), action); }
            @Override public FluidStack drain(int amount, FluidAction action) {
                if (bank == 0 || amount <= 0) return FluidStack.EMPTY;
                var g = group();for (var be : g) if (!be.tank(bank).isEmpty()) return GasCentrifugeBlockEntity.drain(g, bank, be.tank(bank).getFluid(), amount, action);
                return FluidStack.EMPTY;
            }
        };
    }
    public void release() {
        for (var tank : List.of(feed, product, tails)) { ToxicGas.release(level, worldPosition, tank.getFluid().copy());tank.setFluid(FluidStack.EMPTY); }
        changed(level, worldPosition);
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag, registries, packet);tag.put("Feed", feed.writeToNBT(registries, new CompoundTag()));tag.put("Product", product.writeToNBT(registries, new CompoundTag()));tag.put("Tails", tails.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("Progress", progress);tag.putString("Recipe", recipeId);tag.putString("Status", status);tag.putInt("Stages", stages);tag.putInt("Required", required);
        tag.putLongArray("Members", members.stream().mapToLong(BlockPos::asLong).toArray());
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.read(tag, registries, packet);feed.readFromNBT(registries, tag.getCompound("Feed"));product.readFromNBT(registries, tag.getCompound("Product"));tails.readFromNBT(registries, tag.getCompound("Tails"));
        progress = Double.isFinite(tag.getDouble("Progress")) ? Math.clamp(tag.getDouble("Progress"), 0, 240) : 0;recipeId = tag.getString("Recipe");status = tag.getString("Status");stages = tag.getInt("Stages");required = Math.max(1, tag.getInt("Required"));
        var saved = new ArrayList<BlockPos>();for (long pos : tag.getLongArray("Members")) saved.add(BlockPos.of(pos));
        members = List.copyOf(saved);dirty = true;contentsChanged = true;cachedRecipe = null;
    }
    @Override public boolean addToGoggleTooltip(List<Component> lines, boolean sneaking) {
        var lead = leader();String prefix = "centrifuge.create_refinery_n_reactors.";
        lines.add(Component.translatable(prefix + "cascade", unitCount(), lead.stages, lead.required));
        lines.add(Component.translatable(prefix + "process", lead.processName(), Math.round(lead.progress)));
        lines.add(Component.translatable(prefix + "drive", Math.round(getSpeed()), Math.round(32 * Math.abs(getSpeed()))));
        lines.add(Component.translatable(prefix + "status." + (limited && members.size() == 1 ? "limit" : lead.status)));return true;
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, blockEntity, side) -> {
            if (side == null || side == Direction.DOWN) return null;
            var lower = GasCentrifugeBlock.lower(state, pos);var be = at(level, lower);if (be == null) return null;
            boolean upper = state.getValue(GasCentrifugeBlock.HALF) == DoubleBlockHalf.UPPER;
            if (!upper && side == Direction.UP) return null;
            return !upper ? net.createplant.mixture.GasMixturePort.inlet(be.port(0), be.leader()) : net.createplant.mixture.GasMixturePort.outlet(be.port(side == Direction.UP ? 1 : 2), be.leader());
        }, CEHighStressMachines.CENTRIFUGE.get());
    }
}
