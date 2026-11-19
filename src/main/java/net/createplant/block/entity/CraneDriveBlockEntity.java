package net.createplant.block.entity;

import java.util.List;
import java.util.Locale;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.createplant.block.CraneDriveBlock;
import net.createplant.block.CraneFrame;
import net.createplant.block.CraneStructure;
import net.createplant.block.entity.ReactorControlConsoleBlockEntity.CraneMode;
import net.createplant.nuclear.Finite;
import net.createplant.nuclear.ReactorCores;
import net.createplant.nuclear.ReactorPhysics;
import net.createplant.nuclear.ReactorFuel;
import net.createplant.registry.CECrane;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Runs a gantry crane while it has a redstone signal and turns. Until its frame stands it builds the legs and rails of its range
 * from metal girders in its store. The bridge travels along the rails and the trolley across the bridge at the drive's speed. In reactor service the fixed handling mast only moves horizontally and docks on the operating-floor
 * caps of the reactor cores under the crane: it takes spent fuel rods out and lowers fresh ones in, one channel a trip, and exchanges
 * worn control rods through their integrated cap drives; the fusion crane exchanges the blanket and divertor of a fusion reactor. Fuel travels
 * through the pool when one is present; other items use the drive's store. Quarrying has its own drive, {@link QuarryDriveBlockEntity}.
 */
public class CraneDriveBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation {
    /** Fuel rods: lift one out of a pool, lower it into a hole, pull a spent one out of a hole, lower one into a pool. */
    private enum Job { PICK, INSERT, EXTRACT, DROP, ROD, FUSION }

    /** A place to go and what to do there, at the service mast's fixed depth. */
    private record Task(Job job, double u, double v, double depth, double travel, int ticks, BlockPos target, int index) { }

    public static final int SLOTS = 27;
    public static final int LOWER_MAST_LENGTH = 10;
    public static final int UPPER_MAST_LENGTH = 5;
    public static final int SERVICE_MAST_LENGTH = LOWER_MAST_LENGTH + UPPER_MAST_LENGTH;
    /** Control rods this worn are exchanged when withdrawn. */
    public static final double RENEW_WEAR = 0.25;
    /** The complete fuel assembly spans the core, shield plug and suspension space below the operating-floor cap. */
    public static final int ROD_LENGTH = ReactorPhysics.FUEL_ASSEMBLY_LENGTH;
    private static final int FUEL_TICKS = 60;
    public static final int SERVICE_MAST_DEPTH = SERVICE_MAST_LENGTH - 1;
    private static final String KEY = "crane.create_refinery_n_reactors.";
    private static final double EPSILON = 1e-3;
    private static final BehaviourType<ScrollValueBehaviour> LENGTH_TYPE = new BehaviourType<>("crane_length");
    private static final BehaviourType<ScrollValueBehaviour> WIDTH_TYPE = new BehaviourType<>("crane_width");
    private static final String LENGTH_KEY = "CraneLength", WIDTH_KEY = "CraneWidth";
    private ScrollValueBehaviour lengthSetting, widthSetting;
    /** The configured frame while it is being built, how much of it stands and how many girders are still short. */
    private CraneFrame.Plan plan;
    private int frameDone, frameTotal, frameShortGirders;
    private final ItemStackHandler store = new ItemStackHandler(SLOTS) {
        @Override protected void onContentsChanged(int slot) {
            setChanged();
            storeChanged = true;
        }
    };
    private CraneStructure.Result structure;
    private boolean working, storeChanged;
    /** The fuel rod in the grapple: none, fresh or spent; and whether the grapple is lowering it rather than lifting it. */
    private int carried;
    private int carriedLength = ROD_LENGTH;
    private ReactorFuel carriedFuelKind = ReactorFuel.URANIUM;
    private int fusionComponent;
    private boolean lowering;
    /** How far the rod travels between the grapple and its place: from the cap to the core bottom, or into the pool. */
    private double reach = ROD_LENGTH;
    private static final int NONE = 0, FRESH = 1, SPENT = 2;
    private double u, v, depth;
    private Task task;
    private BlockPos console;
    private boolean jogging;
    private double jogU, jogV;
    private int remaining, clock, fuelCursor;
    private String status = "structure";
    private String waitReason = "waiting";
    private BlockPos fault;
    // Client: the drawn position eases towards the synchronised one.
    private double shownU, shownV, shownDepth, previousU, previousV, previousDepth;
    private int supplies, outputs;

    public CraneDriveBlockEntity(BlockPos pos, BlockState state) {
        super(CECrane.DRIVE_ENTITY.get(), pos, state);
        fault = pos;
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        lengthSetting = new CraneRangeSetting(Component.translatable(KEY + "crane_length"), this,
            (state, side) -> side == state.getValue(CraneDriveBlock.FACING).getCounterClockWise(), LENGTH_TYPE, LENGTH_KEY,
            CraneStructure.MIN_LENGTH, CraneStructure.MAX_LENGTH, 0);
        widthSetting = new CraneRangeSetting(Component.translatable(KEY + "crane_width"), this,
            (state, side) -> side == state.getValue(CraneDriveBlock.FACING).getClockWise(), WIDTH_TYPE, WIDTH_KEY,
            CraneStructure.MIN_SPAN - 1, CraneStructure.MAX_SPAN - 1, 1);
        for (var setting : List.of(lengthSetting, widthSetting))
            setting.withCallback(value -> configurationChanged()).onlyActiveWhen(() -> level == null || !level.hasNeighborSignal(worldPosition));
        behaviours.add(lengthSetting);behaviours.add(widthSetting);
    }

    public int length() { return lengthSetting == null ? 16 : lengthSetting.getValue(); }
    public int width() { return widthSetting == null ? 16 : widthSetting.getValue(); }
    public void size(int length, int width) { lengthSetting.setValue(length);widthSetting.setValue(width); }

    public static CompoundTag rangeSettings(int length, int width) {
        var tag = new CompoundTag();
        tag.putInt(LENGTH_KEY, length);tag.putInt(WIDTH_KEY, width);
        return tag;
    }

    /** The rails run forward from the drive; the far rail lies to its right. */
    private CraneFrame.Plan configuredFrame() {
        Direction along = getBlockState().getValue(CraneDriveBlock.FACING), across = along.getClockWise();
        BlockPos start = worldPosition.relative(along);
        return level == null ? new CraneFrame.Plan(start, along, across, length(), width(), new int[4]) : CraneFrame.plan(level, start, along, across, length(), width());
    }

    private void configurationChanged() {
        if (level == null || level.isClientSide()) return;
        cancelTask();clearJog();structure = null;plan = null;
        if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        changed();
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CECrane.DRIVE_ENTITY.get(), (crane, side) -> crane.port);
    }

    /** Whether this is the fusion maintenance crane rather than the RBMK fuel crane. */
    public boolean fusion() { return getBlockState().getBlock() instanceof CraneDriveBlock drive && drive.fusion(); }
    public int fusionComponent() { return fusionComponent; }
    public CraneStructure.Result structure() { return structure; }
    public boolean working() { return working; }
    public String status() { return status; }
    public BlockPos console() { return console; }

    public boolean bindConsole(BlockPos desk) {
        if (console != null && !console.equals(desk) && (!level.hasChunkAt(console)
            || level.getBlockEntity(console) instanceof ReactorControlConsoleBlockEntity old && old.linksCrane(worldPosition))) return false;
        console = desk.immutable();
        clearJog();
        changed();
        return true;
    }

    public void unbindConsole(BlockPos desk) {
        if (!desk.equals(console)) return;
        console = null;
        clearJog();
        changed();
    }

    private CraneMode remoteMode() {
        if (console == null) return null;
        if (!level.hasChunkAt(console) || !(level.getBlockEntity(console) instanceof ReactorControlConsoleBlockEntity desk)) return CraneMode.STOPPED;
        if (!desk.linksCrane(worldPosition) && desk.stationComplete()) {
            unbindConsole(console);
            return null;
        }
        return desk.craneModeFor(worldPosition);
    }

    public void clearJog() { jogging = false; }

    /** Requests one block of travel along or across the rails, without operating the grapple. */
    public String jog(boolean along, int step) {
        if (structure == null) return "structure";
        if (remaining > 0) return "console_load";
        if (getSpeed() == 0) return "speed";
        double fromU = jogging ? jogU : u, fromV = jogging ? jogV : v;
        jogU = Math.clamp(fromU + (along ? step : 0), 0, structure.length() - 1);
        jogV = Math.clamp(fromV + (along ? 0 : step), 1, structure.span() - 1);
        if (jogU == fromU && jogV == fromV) return "console_limit";
        jogging = true;
        return null;
    }
    public double depth() { return depth; }
    public int carried() { return carried; }
    public int carriedLength() { return carriedLength; }
    /**
     * How far the fuel rod in the grapple has gone down out of it. A carried rod rides above the grapple, in the crane, and goes
     * its whole reach down through the operating-floor cap into its channel, or into the pool.
     */
    public double slide() {
        if (carried == NONE || remaining <= 0) return 0;
        double done = 1 - remaining / (double) FUEL_TICKS;
        return reach * (lowering ? done : 1 - done);
    }
    public IItemHandler store() { return store; }

    public double renderedU(float pt) { return previousU + (shownU - previousU) * pt; }
    public double renderedV(float pt) { return previousV + (shownV - previousV) * pt; }
    public double renderedDepth(float pt) { return previousDepth + (shownDepth - previousDepth) * pt; }

    /** Girders for the frame and the parts this crane fits go in; everything the crane takes out comes out. */
    private boolean supply(ItemStack stack) {
        if (stack.is(AllBlocks.METAL_GIRDER.asItem())) return true;
        if (fusion()) return stack.is(net.createplant.registry.CEFusion.LITHIUM_LEAD_BLANKET.get()) || stack.is(net.createplant.registry.CEFusion.DIVERTOR_CASSETTE.get())
            || stack.is(net.createplant.registry.CEFusion.IRRADIATION_CARTRIDGE.get());
        return CENuclear.isFuel(stack) || stack.is(CENuclear.CONTROL_ROD.get());
    }

    private final IItemHandler port = new IItemHandler() {
        @Override public int getSlots() { return SLOTS; }
        @Override public ItemStack getStackInSlot(int slot) { return store.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return supply(stack) ? store.insertItem(slot, stack, simulate) : stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack held = store.getStackInSlot(slot);
            return supply(held) && !held.isDamaged() ? ItemStack.EMPTY : store.extractItem(slot, amount, simulate);
        }
        @Override public int getSlotLimit(int slot) { return store.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return supply(stack); }
    };

    @Override public float calculateStressApplied() {
        float impact = structure == null ? 0 : 8;
        lastStressApplied = impact;
        return impact;
    }

    // Server

    @Override public void tick() {
        super.tick();
        if (level == null) return;
        if (level.isClientSide()) {
            previousU = shownU;
            previousV = shownV;
            previousDepth = shownDepth;
            shownU += (u - shownU) * 0.4;
            shownV += (v - shownV) * 0.4;
            shownDepth += (depth - shownDepth) * 0.4;
            if (working && remaining > 0) remaining--;
            return;
        }
        clock++;
        if (structure != null && (structure.along() != getBlockState().getValue(CraneDriveBlock.FACING)
            || structure.length() != length() || structure.span() != width() + 1)) configurationChanged();
        if (structure == null || clock % 40 == 0) validate();
        CraneMode remote = remoteMode();
        boolean enabled = remote == null ? level.hasNeighborSignal(worldPosition) : remote != CraneMode.STOPPED;
        String disabled = remote == null ? "redstone" : "console_paused";
        if (structure == null) {
            buildFrame(enabled && remote != CraneMode.MANUAL, remote == CraneMode.MANUAL ? "structure" : disabled);
            return;
        }
        if (!fusion() && Math.abs(depth - SERVICE_MAST_DEPTH) > EPSILON) {
            depth = SERVICE_MAST_DEPTH;
            moved();
        }
        if (!enabled) {
            clearJog();
            stop(disabled);
            return;
        }
        float speed = Math.abs(getSpeed());
        if (speed == 0) {
            clearJog();
            stop("speed");
            return;
        }
        double step = Math.min(0.49, speed / 256);
        if (remote == CraneMode.MANUAL) {
            tickJog(step);
            return;
        }
        if (task == null) {
            waitReason = "waiting";
            task = plan();
        }
        if (task == null) {
            stop(waitReason);
            return;
        }
        if (!level.hasChunkAt(task.target())) {
            cancelTask();
            stop("unloaded");
            return;
        }
        boolean over = Math.abs(u - task.u()) < EPSILON && Math.abs(v - task.v()) < EPSILON;
        if (!over) {
            working = false;
            if (depth > task.travel() + EPSILON) depth = Math.max(task.travel(), depth - step);
            else {
                u = approach(u, task.u(), step);
                v = approach(v, task.v(), step);
            }
            status("moving");
            moved();
            return;
        }
        if (Math.abs(depth - task.depth()) > EPSILON) {
            depth = approach(depth, task.depth(), step);
            status("moving");
            moved();
            return;
        }
        if (remaining <= 0) {
            remaining = task.ticks();
            if (!begin(task)) {
                cancelTask();
                return;
            }
        }
        working = true;
        status(task.job() == Job.ROD ? "renewing" : task.job() == Job.FUSION ? "fusion_service" : "refuelling");
        if (--remaining > 0) return;
        if (perform(task)) {
            task = null;
            working = false;
        }
        moved();
    }


    private static double approach(double from, double to, double step) {
        return Math.abs(to - from) <= step ? to : from + Math.signum(to - from) * step;
    }

    private void tickJog(double step) {
        if (!jogging || remaining > 0) {
            stop(remaining > 0 ? "console_load" : "console_manual");
            return;
        }
        working = false;
        if (fusion() && depth > 0) depth = Math.max(0, depth - step);
        else {
            u = approach(u, jogU, step);
            v = approach(v, jogV, step);
            if (u == jogU && v == jogV) clearJog();
        }
        status(jogging ? "moving" : "console_manual");
        moved();
    }

    private void carry(int next, boolean down) {
        carried = next;
        lowering = down;
        changed();
    }

    private void stop(String reason) {
        working = false;
        status(reason);
    }

    private void cancelTask() {
        task = null;
        remaining = 0;
        working = false;
        lowering = false;
    }

    private Task waitFor(String reason) { waitReason = reason; return null; }

    private void moved() {
        setChanged();
        if (clock % 4 == 0 || storeChanged) {
            storeChanged = false;
            sendData();
        }
    }

    private void status(String next) {
        if (status.equals(next)) return;
        status = next;
        sendData();
    }

    private void changed() {
        setChanged();
        sendData();
    }

    private void validate() {
        var desired = configuredFrame();
        var result = CraneStructure.scan(level, worldPosition, getBlockState().getValue(BlockStateProperties.AXIS));
        if (result.formed() && (!result.start().equals(desired.start()) || result.across() != desired.across()
            || result.length() != desired.length() || result.span() != desired.width() + 1))
            result = new CraneStructure.Result(null, null, null, 0, 0, 0, desired.start(), "frame_dimensions");
        boolean was = structure != null;
        if (result.formed() && (structure == null || !result.equals(structure))) {
            structure = result;
            u = Math.clamp(u, 0, result.length() - 1);
            v = Math.clamp(v, 1, result.span() - 1);
            depth = fusion() ? Math.max(0, depth) : SERVICE_MAST_DEPTH;
            plan = null;
            clearJog();
            cancelTask();
        } else if (!result.formed()) {
            clearJog();
            cancelTask();
            structure = null;
            fault = result.fault();
            if (plan == null) stop(result.reason());
        }
        if (was != (structure != null)) {
            if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
            if (structure != null) status("idle");
            changed();
        }
    }

    private double depthAbove(BlockPos pos) { return structure.start().getY() - (pos.getY() + 1); }

    private double u(BlockPos pos) { return (pos.getX() - structure.start().getX()) * structure.along().getStepX() + (pos.getZ() - structure.start().getZ()) * structure.along().getStepZ(); }

    private double v(BlockPos pos) { return (pos.getX() - structure.start().getX()) * structure.across().getStepX() + (pos.getZ() - structure.start().getZ()) * structure.across().getStepZ(); }

    private boolean reachable(double u, double v) { return u >= 0 && u <= structure.length() - 1 && v >= 1 && v <= structure.span() - 1; }

    private boolean serviceAligned(BlockPos pos) { return Math.abs(depthAbove(pos) - SERVICE_MAST_DEPTH) < EPSILON; }

    private Task plan() { return fusion() ? planFusionService() : planService(); }

    private Task planFusionService() {
        for (BlockPos pos : FusionReactorControllerBlockEntity.controllers(level)) {
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof FusionReactorControllerBlockEntity reactor) || !reactor.formed()) continue;
            // Any block of the top blanket serves; the crane takes the reachable one nearest its tool.
            BlockPos top = null;
            double nearest = Double.MAX_VALUE;
            for (BlockPos candidate : net.createplant.fusion.FusionStructure.serviceTops(pos, reactor.inward(), reactor.structureWidth())) {
                double cu = u(candidate), cv = v(candidate), distance = (cu - u) * (cu - u) + (cv - v) * (cv - v);
                if (reachable(cu, cv) && depthAbove(candidate) >= 1 && depthAbove(candidate) <= SERVICE_MAST_DEPTH && distance < nearest) { top = candidate;nearest = distance; }
            }
            if (top == null) continue;
            double ru = u(top), rv = v(top), serviceDepth = depthAbove(top);
            if (!reactor.maintenanceReady()) { waitReason = "fusion_unsafe";continue; }
            for (int index = 0; index < 2; index++) {
                var item = fusionSupply(index);
                int lifetime = index == 0 ? 24_000 : 16_000;
                // An irradiation cartridge is only worth its yield once used up, so it stays in until then.
                boolean irradiating = reactor.blanketPort().getStackInSlot(index).is(net.createplant.registry.CEFusion.IRRADIATION_CARTRIDGE.get());
                if (reactor.componentRemaining(index) > (irradiating ? 0 : lifetime * .75) || item == null) continue;
                fusionComponent = index;
                return new Task(Job.FUSION, ru, rv, serviceDepth, 0, 120, pos, index);
            }
        }
        return null;
    }

    // Reactor service

    private Task planService() {
        BlockPos a = structure.start(), b = structure.at(structure.length() - 1, structure.span());
        var tops = new java.util.ArrayList<BlockPos>();
        boolean clearance = false;
        for (BlockPos controller : ReactorCores.controllersIn(level, Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ()))) {
            if (!(level.getBlockEntity(controller) instanceof FuelChannelBlockEntity lead) || lead.core() == null || !lead.core().formed()) continue;
            var core = lead.core();
            for (int j = 0; j < core.rodColumns(); j++) {
                BlockPos top = core.rodTop(j);
                if (!core.rodWithdrawn(j) || core.rodWear(j) < RENEW_WEAR || count(CENuclear.CONTROL_ROD.get()) == 0) continue;
                float[] at = FuelChannelBlockEntity.HOLES[core.rodSlot(j)];
                double ru = u(top) + at[0] * structure.along().getStepX() + at[1] * structure.along().getStepZ();
                double rv = v(top) + at[0] * structure.across().getStepX() + at[1] * structure.across().getStepZ();
                if (!reachable(ru, rv)) continue;
                if (!serviceAligned(top)) {
                    clearance = true;
                    continue;
                }
                return new Task(Job.ROD, ru, rv, SERVICE_MAST_DEPTH, SERVICE_MAST_DEPTH, 60, controller, j);
            }
            for (BlockPos top : core.fuelTops()) if (reachable(u(top), v(top))) {
                if (serviceAligned(top)) tops.add(top);
                else clearance = true;
            }
        }
        return planFuel(tops, clearance);
    }

    /**
     * One fuel rod a trip. A rod already in the grapple goes where it belongs: a fresh one into an empty hole, a spent one into a
     * pool. With the grapple empty, a spent rod is pulled out of its hole, or a fresh one fetched from a pool for an empty hole.
     * Without a pool under the crane, the drive's store stands in for one, at once.
     */
    private Task planFuel(List<BlockPos> tops, boolean clearance) {
        BlockPos a = structure.start(), b = structure.at(structure.length() - 1, structure.span());
        List<BlockPos> allPools = FuelPoolBlockEntity.within(level, Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ()))
            .stream().filter(p -> reachable(u(p), v(p))).toList();
        List<BlockPos> pools = allPools.stream().filter(p -> pool(p) != null && pool(p).ready() && serviceAligned(pool(p).surface())).toList();
        if (carried == SPENT) {
            Task drop = poolTask(pools, Job.DROP, FuelPoolBlockEntity.EMPTY, tops);
            if (drop != null) return drop;
            if (!allPools.isEmpty()) return waitFor(poolWaitReason(allPools, pools));
            if (!ItemHandlerHelper.insertItem(store, new ItemStack(CENuclear.SPENT_FUEL_ROD.get()), false).isEmpty()) {
                return waitFor("full");
            }
            carry(NONE, false);
        }
        int spent = -1, empty = -1, spentSlot = 0, emptySlot = 0;
        ItemStack freshFuel = carried == FRESH ? carriedFuel() : new ItemStack(CENuclear.FUEL_ROD.get());
        for (int i = 0; i < tops.size() && spent < 0; i++) {
            int c = (fuelCursor + i) % tops.size();
            var column = level.getCapability(Capabilities.ItemHandler.BLOCK, tops.get(c), Direction.UP);
            if (column == null) continue;
            for (int slot = 0; slot < column.getSlots(); slot++) {
                if (spent < 0 && !column.extractItem(slot, 1, true).isEmpty()) { spent = c; spentSlot = slot; }
                if (empty < 0 && column.getStackInSlot(slot).isEmpty() && column.isItemValid(slot, freshFuel)) { empty = c; emptySlot = slot; }
            }
        }
        if (carried == FRESH) {
            if (empty >= 0) return holeTask(Job.INSERT, tops.get(fuelCursor = empty), emptySlot);
            Task back = poolTask(pools, Job.DROP, FuelPoolBlockEntity.EMPTY, tops);
            if (back != null) return back;
            if (!allPools.isEmpty()) return waitFor(poolWaitReason(allPools, pools));
            if (!ItemHandlerHelper.insertItem(store, carriedFuel(), false).isEmpty()) {
                return waitFor("full");
            }
            carry(NONE, false);
            return null;
        }
        boolean room = pools.stream().anyMatch(p -> pool(p).find(FuelPoolBlockEntity.EMPTY) >= 0)
            || allPools.isEmpty() && ItemHandlerHelper.insertItem(store, new ItemStack(CENuclear.SPENT_FUEL_ROD.get()), true).isEmpty();
        if (spent >= 0 && room) return holeTask(Job.EXTRACT, tops.get(fuelCursor = spent), spentSlot);
        if (empty < 0) return spent >= 0 && !allPools.isEmpty() ? waitFor(poolWaitReason(allPools, pools))
            : waitFor(tops.isEmpty() ? (clearance ? "clearance" : "reactor") : "waiting");
        Task fetch = poolTask(pools, Job.PICK, FuelPoolBlockEntity.FRESH, tops);
        if (fetch != null) return fetch;
        if (!allPools.isEmpty()) return waitFor(pools.isEmpty() ? poolWaitReason(allPools, pools) : "pool_fuel");
        for (int slot = 0; slot < SLOTS; slot++)
            if (CENuclear.isFuel(store.getStackInSlot(slot)) && acceptsFuel(tops, store.getStackInSlot(slot))) {
                carriedFuelKind = ReactorFuel.of(store.getStackInSlot(slot));
                carriedLength = carriedFuelKind == ReactorFuel.COMPACT_ENRICHED ? ReactorPhysics.COMPACT_LAYOUT.fuelAssemblyLength() : ROD_LENGTH;
                store.extractItem(slot, 1, false);
                carry(FRESH, false);
                return null;
            }
        return null;
    }

    private String poolWaitReason(List<BlockPos> allPools, List<BlockPos> pools) {
        if (!pools.isEmpty()) return "pool_full";
        return allPools.stream().anyMatch(p -> pool(p) != null && serviceAligned(pool(p).surface())) ? "pool_unavailable" : "clearance";
    }

    private FuelPoolBlockEntity pool(BlockPos pos) { return level.getBlockEntity(pos) instanceof FuelPoolBlockEntity pool ? pool : null; }

    /** Over the place of the first pool cell holding {@code state}, lowered to the water. */
    private Task poolTask(List<BlockPos> pools, Job job, byte state, List<BlockPos> tops) {
        for (BlockPos pos : pools) {
            var pool = pool(pos);
            if (pool == null || !pool.ready() || !serviceAligned(pool.surface())) continue;
            for (int slot = 0; slot < FuelChannelBlockEntity.RODS; slot++)
                if (pool.slot(slot) == state && (job != Job.PICK || acceptsFuel(tops, new ItemStack(pool.fuel(slot).item())))) return over(job, pos, slot);
        }
        return null;
    }

    private boolean acceptsFuel(List<BlockPos> tops, ItemStack fuel) {
        for (BlockPos top : tops) {
            var column = level.getCapability(Capabilities.ItemHandler.BLOCK, top, Direction.UP);
            if (column == null) continue;
            for (int slot = 0; slot < column.getSlots(); slot++)
                if (column.getStackInSlot(slot).isEmpty() && column.isItemValid(slot, fuel)) return true;
        }
        return false;
    }

    /** Over one of the four channel positions in an operating-floor cap. */
    private Task holeTask(Job job, BlockPos top, int slot) { return over(job, top, slot); }

    /** Over the centre of place {@code slot} of a column or pool cell, with the handling mast at its fixed height. */
    private Task over(Job job, BlockPos pos, int slot) {
        float[] at = FuelChannelBlockEntity.HOLES[slot];
        double du = at[0] * structure.along().getStepX() + at[1] * structure.along().getStepZ(), dv = at[0] * structure.across().getStepX() + at[1] * structure.across().getStepZ();
        return new Task(job, u(pos) + du, v(pos) + dv, SERVICE_MAST_DEPTH, SERVICE_MAST_DEPTH, FUEL_TICKS, pos, slot);
    }

    private int count(net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < SLOTS; slot++) if (store.getStackInSlot(slot).is(item)) total += store.getStackInSlot(slot).getCount();
        return total;
    }

    // Quarry

    // Doing the work

    /**
     * Starts the work once the hoist is there: a rod lifted out of a hole or a pool is in the grapple from now on, and one lowered
     * is going down. False drops a task the world has overtaken.
     */
    private boolean begin(Task task) {
        if (task.job() == Job.INSERT || task.job() == Job.EXTRACT) {
            var column = level.getBlockEntity(task.target()) instanceof RodCapBlockEntity cap ? cap.column() : null;
            carriedLength = column == null ? ROD_LENGTH : column.layout().fuelAssemblyLength();
            reach = carriedLength;
        } else reach = net.createplant.nuclear.FuelPoolStructure.DEPTH + .75;
        switch (task.job()) {
            case EXTRACT -> {
                var column = level.getCapability(Capabilities.ItemHandler.BLOCK, task.target(), Direction.UP);
                if (column == null || column.extractItem(task.index(), 1, false).isEmpty()) return false;
                carry(SPENT, false);
            }
            case PICK -> {
                var pool = pool(task.target());
                if (pool == null || !pool.ready() || pool.slot(task.index()) != FuelPoolBlockEntity.FRESH) return false;
                carriedFuelKind = pool.fuel(task.index());
                carriedLength = carriedFuelKind == ReactorFuel.COMPACT_ENRICHED ? ReactorPhysics.COMPACT_LAYOUT.fuelAssemblyLength() : ROD_LENGTH;
                pool.set(task.index(), FuelPoolBlockEntity.EMPTY);
                carry(FRESH, false);
            }
            case INSERT, DROP -> carry(carried, true);
            default -> { }
        }
        return true;
    }

    /** Finishes the work; false keeps the hoist waiting, as when the store is full. */
    private boolean perform(Task task) {
        return switch (task.job()) {
            case INSERT -> insert(task.target(), task.index());
            case DROP -> drop(task.target(), task.index());
            case EXTRACT, PICK -> true;
            case ROD -> renew(task.target(), task.index());
            case FUSION -> serviceFusion(task.target(), task.index());
        };
    }

    /** The fresh part for a fusion slot: an irradiation cartridge in the store takes the blanket's place before lithium-lead does. */
    private net.minecraft.world.item.Item fusionSupply(int index) {
        if (index == 1) return count(net.createplant.registry.CEFusion.DIVERTOR_CASSETTE.get()) > 0 ? net.createplant.registry.CEFusion.DIVERTOR_CASSETTE.get() : null;
        for (var item : List.of(net.createplant.registry.CEFusion.IRRADIATION_CARTRIDGE.get(), net.createplant.registry.CEFusion.LITHIUM_LEAD_BLANKET.get()))
            if (count(item) > 0) return item;
        return null;
    }

    private static ItemStack spent(ItemStack installed, int index) {
        if (installed.isEmpty()) return ItemStack.EMPTY;
        if (index == 1) return new ItemStack(net.createplant.registry.CEFusion.SPENT_DIVERTOR.get());
        if (!installed.is(net.createplant.registry.CEFusion.IRRADIATION_CARTRIDGE.get())) return new ItemStack(net.createplant.registry.CEFusion.SPENT_BLANKET.get());
        return installed.getDamageValue() >= installed.getMaxDamage() ? new ItemStack(net.createplant.registry.CEFusion.IRRADIATED_CARTRIDGE.get()) : installed.copy();
    }

    private boolean serviceFusion(BlockPos pos, int index) {
        if (!(level.getBlockEntity(pos) instanceof FusionReactorControllerBlockEntity reactor) || !reactor.formed() || !reactor.maintenanceReady()) {
            cancelTask();stop("fusion_unsafe");return false;
        }
        var item = fusionSupply(index);
        if (item == null) { cancelTask();stop("waiting");return false; }
        ItemStack spent = spent(reactor.blanketPort().getStackInSlot(index), index);
        if (!spent.isEmpty() && !ItemHandlerHelper.insertItem(store, spent, true).isEmpty()) { stop("output");return false; }
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack fresh = store.getStackInSlot(slot);
            if (!fresh.is(item) || fresh.getDamageValue() != 0) continue;
            if (!reactor.exchangeComponent(index, fresh.copyWithCount(1))) return false;
            store.extractItem(slot, 1, false);
            if (!spent.isEmpty()) ItemHandlerHelper.insertItem(store, spent, false);
            changed();return true;
        }
        cancelTask();stop("waiting");return false;
    }

    /** The fresh rod in the grapple goes into its hole; if the hole has been filled meanwhile it stays in the grapple. */
    private boolean insert(BlockPos top, int slot) {
        var column = level.getCapability(Capabilities.ItemHandler.BLOCK, top, Direction.UP);
        if (column != null && column.insertItem(slot, carriedFuel(), false).isEmpty()) carry(NONE, false);
        else carry(FRESH, false);
        return true;
    }
    private ItemStack carriedFuel() { return new ItemStack(carriedFuelKind.item()); }

    /** The rod in the grapple goes into its place in the pool; if the place has been filled meanwhile it stays in the grapple. */
    private boolean drop(BlockPos pos, int slot) {
        var pool = pool(pos);
        if (pool != null && pool.ready() && pool.slot(slot) == FuelPoolBlockEntity.EMPTY) {
            pool.setFuel(slot, carried == FRESH ? carriedFuelKind : ReactorFuel.URANIUM);
            pool.set(slot, carried == SPENT ? FuelPoolBlockEntity.SPENT : FuelPoolBlockEntity.FRESH);
            carry(NONE, false);
        } else carry(carried, false);
        return true;
    }

    /** Lifts a withdrawn worn control rod through its cap and installs a fresh absorber from the store. */
    private boolean renew(BlockPos controller, int rod) {
        if (!(level.getBlockEntity(controller) instanceof FuelChannelBlockEntity lead) || lead.core() == null || !lead.core().formed()) return true;
        var core = lead.core();
        if (rod >= core.rodColumns() || !core.rodWithdrawn(rod) || count(CENuclear.CONTROL_ROD.get()) == 0) return true;
        if (!ItemHandlerHelper.insertItem(store, new ItemStack(CENuclear.SPENT_CONTROL_ROD.get()), true).isEmpty()) {
            status("full");
            return false;
        }
        if (!core.renewRod(level, rod)) return true;
        for (int slot = 0; slot < SLOTS; slot++)
            if (store.getStackInSlot(slot).is(CENuclear.CONTROL_ROD.get())) {
                store.extractItem(slot, 1, false);
                break;
            }
        ItemHandlerHelper.insertItem(store, new ItemStack(CENuclear.SPENT_CONTROL_ROD.get()), false);
        return true;
    }

    // Building the frame

    /** Sets the next missing girder from the store when automatic construction is enabled. */
    private void buildFrame(boolean enabled, String disabled) {
        if (plan == null) plan = configuredFrame();
        if (!enabled) {
            stop(disabled);
            return;
        }
        if (!plan.reachable()) {
            fault = plan.corner(java.util.stream.IntStream.range(0, 4).filter(index -> plan.legHeights()[index] < 0).findFirst().orElse(0));
            stop("legs");
            return;
        }
        var progress = CraneFrame.progress(level, plan);
        if (progress == null) {
            stop("unloaded");
            return;
        }
        int shortGirders = Math.max(0, progress.missing() - count(AllBlocks.METAL_GIRDER.asItem()));
        if (plan.total() - progress.missing() != frameDone || plan.total() != frameTotal || shortGirders != frameShortGirders) {
            frameDone = plan.total() - progress.missing();frameTotal = plan.total();frameShortGirders = shortGirders;
            changed();
        }
        if (progress.next() < 0) {
            stop("frame_dimensions");
            return;
        }
        BlockPos pos = plan.pos(progress.next());
        BlockState there = level.getBlockState(pos);
        if (!there.isAir() && !there.canBeReplaced()) {
            fault = pos;
            stop("frame_blocked");
            return;
        }
        if (!take(AllBlocks.METAL_GIRDER.asItem())) {
            fault = pos;
            stop("frame_supplies");
            return;
        }
        CraneFrame.place(level, plan, progress.next());
        status("frame_build");
    }

    private boolean take(Item item) {
        for (int slot = 0; slot < SLOTS; slot++)
            if (store.getStackInSlot(slot).is(item)) return !store.extractItem(slot, 1, false).isEmpty();
        return false;
    }

    public void dismantle() {
        if (level == null || level.isClientSide()) return;
        stop("structure");
        double x = worldPosition.getX() + .5, y = worldPosition.getY() + .5, z = worldPosition.getZ() + .5;
        for (int slot = 0; slot < SLOTS; slot++) {
            Containers.dropItemStack(level, x, y, z, store.getStackInSlot(slot));
            store.setStackInSlot(slot, ItemStack.EMPTY);
        }
        if (carried != NONE) Containers.dropItemStack(level, x, y, z, carried == FRESH ? carriedFuel() : new ItemStack(CENuclear.SPENT_FUEL_ROD.get()));
        carried = NONE;
        cancelTask();
    }

    // Information

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        tooltip.add(Component.translatable(KEY + "status." + status));
        if (structure == null) {
            if (frameTotal > 0) tooltip.add(Component.translatable(KEY + "range_progress", length(), width(), frameDone, frameTotal));
            if (frameShortGirders > 0) tooltip.add(Component.translatable(KEY + "frame_missing", frameShortGirders));
            tooltip.add(Component.translatable(KEY + "fault", fault.getX(), fault.getY(), fault.getZ()));
            return true;
        }
        Component work = getBlockState().getBlock().getName();
        Component head = Component.translatable(KEY + "grapple");
        tooltip.add(structure.legs() == 0
            ? Component.translatable(KEY + "frame_overhead", structure.length(), structure.span() + 1, work, head)
            : Component.translatable(KEY + "frame", structure.length(), structure.span() + 1, structure.legs(), work, head));
        tooltip.add(Component.translatable(KEY + "hoist", String.format(Locale.ROOT, "%.1f", depth),
            "--"));
        tooltip.add(Component.translatable(KEY + "store", supplies, outputs));
        return true;
    }

    @Override protected AABB createRenderBoundingBox() {
        if (structure == null) return super.createRenderBoundingBox();
        BlockPos far = structure.at(structure.length() - 1, structure.span());
        return AABB.encapsulatingFullBlocks(structure.start(), far).inflate(1, 2, 1)
            .expandTowards(0, ROD_LENGTH, 0).expandTowards(0, -Math.max(4, depth + ROD_LENGTH), 0);
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Working", working);
        tag.putDouble("U", u);
        tag.putDouble("V", v);
        tag.putDouble("Depth", depth);
        tag.putInt("Carried", carried);
        tag.putInt("CarriedLength", carriedLength);
        tag.putByte("CarriedFuelKind", (byte) carriedFuelKind.ordinal());
        tag.putInt("FusionComponent", fusionComponent);
        tag.putBoolean("Lowering", lowering);
        tag.putDouble("Reach", reach);
        if (clientPacket) tag.putInt("Remaining", remaining);
        tag.putString("Status", status);
        if (console != null) tag.putLong("Console", console.asLong());
        tag.putLong("Fault", fault.asLong());
        if (clientPacket) { tag.putInt("FrameDone", frameDone);tag.putInt("FrameTotal", frameTotal);tag.putInt("FrameShortGirders", frameShortGirders); }
        if (structure != null) {
            tag.putLong("Start", structure.start().asLong());
            tag.putInt("Along", structure.along().get3DDataValue());
            tag.putInt("Across", structure.across().get3DDataValue());
            tag.putInt("Length", structure.length());
            tag.putInt("Span", structure.span());
            tag.putInt("Legs", structure.legs());
        }
        if (clientPacket) {
            int in = 0, out = 0;
            for (int slot = 0; slot < SLOTS; slot++) {
                ItemStack stack = store.getStackInSlot(slot);
                if (supply(stack)) in += stack.getCount();
                else out += stack.getCount();
            }
            tag.putInt("Supplies", in);
            tag.putInt("Outputs", out);
        } else {
            tag.put("Store", store.serializeNBT(registries));
        }
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        working = tag.getBoolean("Working");
        u = Finite.of(tag.getDouble("U"), 0, 0, Double.MAX_VALUE);
        v = Finite.of(tag.getDouble("V"), 1, 1, Double.MAX_VALUE);
        depth = Finite.of(tag.getDouble("Depth"), 0, 0, Double.MAX_VALUE);
        carried = tag.getInt("Carried");
        carriedLength = tag.getInt("CarriedLength") == ReactorPhysics.COMPACT_LAYOUT.fuelAssemblyLength() ? ReactorPhysics.COMPACT_LAYOUT.fuelAssemblyLength() : ROD_LENGTH;
        carriedFuelKind = ReactorFuel.fromId(tag.contains("CarriedFuelKind") ? tag.getByte("CarriedFuelKind") : tag.getBoolean("CarriedMox") ? 1 : 0);
        fusionComponent = Math.clamp(tag.getInt("FusionComponent"), 0, 1);
        lowering = tag.getBoolean("Lowering");
        reach = tag.contains("Reach") ? Finite.of(tag.getDouble("Reach"), ROD_LENGTH, 0, Double.MAX_VALUE) : ROD_LENGTH;
        if (clientPacket) remaining = tag.getInt("Remaining");
        status = tag.getString("Status");
        console = tag.contains("Console") ? BlockPos.of(tag.getLong("Console")) : null;
        if (status.isEmpty()) status = "structure";
        fault = BlockPos.of(tag.getLong("Fault"));
        structure = tag.contains("Start") ? new CraneStructure.Result(BlockPos.of(tag.getLong("Start")), Direction.from3DDataValue(tag.getInt("Along")),
            Direction.from3DDataValue(tag.getInt("Across")), tag.getInt("Length"), tag.getInt("Span"), tag.getInt("Legs"), worldPosition, "ready") : null;
        if (clientPacket) {
            frameDone = tag.getInt("FrameDone");frameTotal = tag.getInt("FrameTotal");frameShortGirders = tag.getInt("FrameShortGirders");
            supplies = tag.getInt("Supplies");
            outputs = tag.getInt("Outputs");
            if (structure != null) {
                u = Math.min(u, structure.length() - 1);
                v = Math.min(v, structure.span() - 1);
            }
            if (structure != null && shownDepth == 0 && previousDepth == 0 && shownU == 0) {
                shownU = previousU = u;
                shownV = previousV = v;
                shownDepth = previousDepth = depth;
            }
        } else {
            if (level == null || !level.isClientSide()) {
                cancelTask();
                structure = null;
            }
            store.deserializeNBT(registries, tag.getCompound("Store"));
        }
    }
}
