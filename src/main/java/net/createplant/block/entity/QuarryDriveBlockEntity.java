package net.createplant.block.entity;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import net.createplant.block.CraneFrame;
import net.createplant.block.CraneStructure;
import net.createplant.block.QuarryDriveBlock;
import net.createplant.block.QuarryBeltLayout;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.createplant.nuclear.Finite;
import net.createplant.registry.CECrane;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Runs a gantry quarry. Its two range settings determine the legs, the two runway rails and native belts along the outside of
 * the near rail, then quarries the span layer by layer from under the rails down while it has a redstone signal and turns.
 * What the drill breaks goes into the bin on the drill head, rises up the bellows chute to the bridge, crosses the bridge on its
 * belt to the near rail, and drops onto the side belt, which this drive turns so that it always runs back towards the drive
 * and throws the ore off its end. The drive's shaft lies across the rails: rotation enters on the inside and leaves to the belt.
 */
public class QuarryDriveBlockEntity extends SplitShaftBlockEntity implements IHaveGoggleInformation {
    private record Task(double u, double v, double depth, double travel, int ticks, BlockPos target) { }

    public static final int SLOTS = 27;
    public static final int WORK_STEPS = 8;
    private static final String KEY = "crane.create_refinery_n_reactors.";
    private static final ItemStack PICKAXE = new ItemStack(Items.IRON_PICKAXE);
    private static final double EPSILON = 1e-3;
    private static final int ETA_SAMPLE_TICKS = 40;
    private static final int LIQUID_CHECKS_PER_TICK = 256;
    private final ItemStackHandler store = new ItemStackHandler(SLOTS) {
        @Override protected void onContentsChanged(int slot) {
            setChanged();
            storeChanged = true;
        }
    };
    private final QuarryConveyor conveyor = new QuarryConveyor(this::setChanged);
    private final QuarryChunkLoader chunkLoader;
    private Direction configuredFacing;
    private CraneStructure.Result structure;
    private boolean drill, working, belt, storeChanged, drainPending, outletWasEmpty;
    private double u, v, depth;
    private Task task;
    private int remaining, clock, layer = Integer.MIN_VALUE, cell;
    private int nextLiquidRow, liquidChecksLeft;
    /** The configured frame, placed parts and remaining supplies. */
    private CraneFrame.Plan plan;
    private CraneStructure.Result visibleStructure;
    private int frameDone, frameShortGirders, shortDrills, shortBelts, shortBeltShafts, shortChains;
    private ScrollValueBehaviour lengthSetting, widthSetting;
    private static final BehaviourType<ScrollValueBehaviour> LENGTH_TYPE = new BehaviourType<>("quarry_length");
    private static final BehaviourType<ScrollValueBehaviour> WIDTH_TYPE = new BehaviourType<>("quarry_width");
    private boolean materialsKnown, batchingSync, syncPending;
    private String status = "structure";
    private String frameProblem = "rails";
    private BlockPos fault;
    private boolean measuringEta;
    private float etaSpeed;
    private int sampledTicks, sampledCells;
    private double cellsPerTick;
    private long etaSeconds = -1;
    // Client: the drawn position eases towards the synchronised one, and parcels move on the server's clock.
    private double shownU, shownV, shownDepth, previousU, previousV, previousDepth;
    private double beltTravel,previousBeltTravel;
    private int supplies, outputs, headItems;
    private long conveyorClock;

    public QuarryDriveBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        chunkLoader = new QuarryChunkLoader(pos);
        configuredFacing = state.getValue(QuarryDriveBlock.FACING);
        fault = pos;
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        lengthSetting = new CraneRangeSetting(Component.translatable(KEY + "range_length"), this,
            (state, side) -> side == state.getValue(QuarryDriveBlock.FACING).getClockWise(), LENGTH_TYPE, "QuarryLength",
            CraneStructure.MIN_LENGTH, CraneStructure.MAX_LENGTH, 0);
        widthSetting = new CraneRangeSetting(Component.translatable(KEY + "range_width"), this,
            (state, side) -> side == state.getValue(QuarryDriveBlock.FACING).getOpposite(), WIDTH_TYPE, "QuarryWidth",
            CraneStructure.MIN_SPAN - 1, CraneStructure.MAX_SPAN - 1, 1);
        for (var setting : List.of(lengthSetting, widthSetting))
            setting.withCallback(value -> configurationChanged()).onlyActiveWhen(() -> level == null || !level.hasNeighborSignal(worldPosition));
        behaviours.add(lengthSetting);behaviours.add(widthSetting);
    }

    public int length() { return lengthSetting == null ? 16 : lengthSetting.getValue(); }
    public int width() { return widthSetting == null ? 16 : widthSetting.getValue(); }
    public void size(int length, int width) { lengthSetting.setValue(length);widthSetting.setValue(width); }
    private CraneFrame.Plan configuredFrame(QuarryBeltLayout layout) {
        BlockPos start = layout.start(), far = start.relative(layout.across(), width() + 1);
        if (!level.hasChunkAt(start) || !level.hasChunkAt(far) || !level.hasChunkAt(start.relative(layout.along(), layout.length() - 1))
            || !level.hasChunkAt(far.relative(layout.along(), layout.length() - 1))) return null;
        return CraneFrame.plan(level, start, layout.along(), layout.across(), layout.length(), width());
    }
    private void configurationChanged() {
        if (!(level instanceof ServerLevel server)) return;
        chunkLoader.release(server);
        cancelTask();structure = null;plan = null;belt = false;materialsKnown = false;layer = Integer.MIN_VALUE;cell = 0;
        nextLiquidRow = 0;
        drainPending = false;
        outletWasEmpty = false;
        resetEstimate();
        invalidateRenderBoundingBox();
        if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        changed();
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CECrane.QUARRY_DRIVE_ENTITY.get(), (quarry, side) -> quarry.port);
    }

    public static void registerChunkLoader(RegisterTicketControllersEvent event) { QuarryChunkLoader.register(event); }

    void restoreChunkTickets(TicketHelper helper, TicketSet tickets) { chunkLoader.restore(helper, tickets, beltLayout(), width()); }

    public CraneStructure.Result structure() { return structure; }
    public boolean drill() { return drill; }
    public boolean working() { return working; }
    public String status() { return status; }
    public BlockPos constructionObstruction() {
        return status.equals("frame_blocked") || status.equals("belt_blocked") || status.equals("frame_dimensions") ? fault : null;
    }
    public IItemHandler store() { return store; }
    public QuarryConveyor conveyor() { return conveyor; }
    public CraneStructure.Result visibleStructure() { return level!=null && level.isClientSide() && visibleStructure!=null ? visibleStructure : structure; }
    public double renderedBeltTravel(float partialTicks) { return previousBeltTravel+(beltTravel-previousBeltTravel)*partialTicks; }
    public long conveyorTime() { return conveyorClock; }
    public boolean conveyorRunning() { return structure!=null && belt && getSpeed()!=0; }
    public float conveyorPartial(float partialTicks) { return conveyorRunning() ? partialTicks*WORK_STEPS : 0; }
    public double renderedU(float pt) { return previousU + (shownU - previousU) * pt; }
    public double renderedV(float pt) { return previousV + (shownV - previousV) * pt; }
    public double renderedDepth(float pt) { return previousDepth + (shownDepth - previousDepth) * pt; }

    public boolean fitDrill() {
        if (drill) return false;
        drill = true;
        changed();
        return true;
    }

    public boolean removeDrill() {
        if (!drill) return false;
        drill = false;
        cancelTask();
        changed();
        return true;
    }

    /** The frame's rails and legs, the belt and a drill go in; what the frame building cleared comes out. */
    private static boolean supply(ItemStack stack) {
        return stack.is(AllBlocks.METAL_GIRDER.asItem()) || stack.is(AllBlocks.MECHANICAL_DRILL.asItem())
            || stack.is(com.simibubi.create.AllItems.BELT_CONNECTOR.get()) || stack.is(AllBlocks.SHAFT.asItem()) || stack.is(AllBlocks.ENCASED_CHAIN_DRIVE.asItem());
    }

    private final IItemHandler port = new IItemHandler() {
        @Override public int getSlots() { return SLOTS; }
        @Override public ItemStack getStackInSlot(int slot) { return store.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return supply(stack) ? store.insertItem(slot, stack, simulate) : stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return supply(store.getStackInSlot(slot)) ? ItemStack.EMPTY : store.extractItem(slot, amount, simulate);
        }
        @Override public int getSlotLimit(int slot) { return store.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return supply(stack); }
    };

    @Override public float calculateStressApplied() {
        float impact = structure == null ? 0 : 16;
        lastStressApplied = impact;
        return impact;
    }

    // The side belt

    /** The side belt's blocks, from the far end of the near rail to the one beside the drive. */
    private BlockPos beltAt(int u) { return structure.start().relative(structure.across().getOpposite()).relative(structure.along(), u); }

    /**
     * The belt turns off the drive's outer face; this sign keeps it running towards the drive whichever way the drive turns.
     * A horizontal belt moves to the negative end of its axis when its speed is positive on X or negative on Z.
     */
    @Override public float getRotationSpeedModifier(Direction face) {
        Direction across = getBlockState().getValue(QuarryDriveBlock.FACING);
        if (!hasSource() || face != across.getOpposite() || getSourceFacing() == face || getSpeed() == 0) return 1;
        Direction along = across.getCounterClockWise();
        boolean towardsNegative = along.getOpposite().getAxisDirection() == Direction.AxisDirection.NEGATIVE;
        boolean positiveSpeed = (along.getAxis() == Direction.Axis.X) == towardsNegative;
        return (positiveSpeed == getSpeed() > 0) ? 1 : -1;
    }

    /** Sets a parcel on the side belt beside the bridge; returns what the belt did not take. */
    private ItemStack unload(ItemStack stack) {
        int at = (int) Math.round(u);
        var target = level.getCapability(Capabilities.ItemHandler.BLOCK, beltAt(at), Direction.UP);
        return target == null ? stack : ItemHandlerHelper.insertItem(target, stack, false);
    }

    private QuarryBeltLayout beltLayout() {
        Direction across = getBlockState().getValue(QuarryDriveBlock.FACING), along = across.getCounterClockWise();
        return new QuarryBeltLayout(worldPosition.relative(along), along, across, length());
    }

    private boolean clearBeltSpace(BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) return true;
        if (!mineable(state, pos)) blocked(pos, "belt_blocked");
        else if (dig(pos, state, false)) status("belt_build");
        return false;
    }

    private void buildBelt(QuarryBeltLayout layout) {
        if (!layout.loaded(level)) { stop("unloaded");return; }
        for (int u = -1; u < layout.length(); u++) {
            if (layout.chain(level, u)) continue;
            BlockPos pos = layout.chain(u);
            if (!clearBeltSpace(pos)) return;
            if (!take(AllBlocks.ENCASED_CHAIN_DRIVE.asItem())) { fault = pos;stop("belt_supplies");return; }
            var state = Block.updateFromNeighbourShapes(layout.chainState(), level, pos);
            level.setBlock(pos, state, Block.UPDATE_ALL);
            status("belt_build");changed();return;
        }
        for (var segment : layout.segments()) {
            if (layout.complete(level, segment)) continue;
            if (layout.repairReversed(level, segment)) { status("belt_build");changed();return; }
            for (int u = segment.first(); u <= segment.last(); u++) {
                BlockPos pos = layout.belt(u);
                if (AllBlocks.BELT.has(level.getBlockState(pos))) { blocked(pos, "belt_blocked");return; }
                if ((u == segment.first() || u == segment.last()) && layout.shaft(level, u)) continue;
                if (!clearBeltSpace(pos)) return;
            }
            int shafts = (layout.shaft(level, segment.first()) ? 0 : 1) + (layout.shaft(level, segment.last()) ? 0 : 1);
            if (count(com.simibubi.create.AllItems.BELT_CONNECTOR.get()) < 1 || count(AllBlocks.SHAFT.asItem()) < shafts) {
                fault = layout.belt(segment.first());stop("belt_supplies");return;
            }
            for (int end : new int[] {segment.first(), segment.last()}) if (!layout.shaft(level, end)) {
                take(AllBlocks.SHAFT.asItem());
                level.setBlock(layout.belt(end), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, layout.across().getAxis()), Block.UPDATE_ALL);
            }
            if (!BeltConnectorItem.canConnect(level, layout.belt(segment.last()), layout.belt(segment.first()))) {
                fault = layout.belt(segment.first());stop("belt_layout");return;
            }
            take(com.simibubi.create.AllItems.BELT_CONNECTOR.get());
            BeltConnectorItem.createBelts(level, layout.belt(segment.last()), layout.belt(segment.first()));
            level.playSound(null, layout.belt(segment.first()), SoundType.WOOL.getPlaceSound(), SoundSource.BLOCKS, .8f, 1f);
            status("belt_build");changed();return;
        }
        belt = layout.complete(level);
        if (!belt) { stop("belt_power");return; }
        detachKinetics();attachKinetics();changed();
    }

    // Server

    @Override public void tick() {
        super.tick();
        if (level == null) return;
        if (level.isClientSide()) {
            previousBeltTravel=beltTravel;
            if(conveyorRunning()) {beltTravel+=QuarryConveyor.SPEED*WORK_STEPS;conveyorClock+=WORK_STEPS;}
            previousU = shownU;
            previousV = shownV;
            previousDepth = shownDepth;
            shownU += (u - shownU) * 0.4;
            shownV += (v - shownV) * 0.4;
            shownDepth += (depth - shownDepth) * 0.4;
            return;
        }
        batchingSync = true;
        try { if (level instanceof ServerLevel server) tickServer(server); }
        finally {
            batchingSync = false;
            if (syncPending) { syncPending = false;sendData(); }
        }
    }

    private void tickServer(ServerLevel server) {
        clock++;
        var layout = beltLayout();
        if (configuredFacing != layout.across()) {
            configuredFacing = layout.across();
            configurationChanged();
        }
        boolean signal = level.hasNeighborSignal(worldPosition);
        boolean finished = excavationFinished() && nextLiquidRow >= liquidRows(layout.length());
        if (finished && !drainPending && conveyor.hasItems()) {
            drainPending = true;
            setChanged();
        }
        if (signal && (!finished || drainPending)) {
            if (!chunkLoader.load(server, layout, width())) {
                stop("unloaded");
                return;
            }
        } else {
            chunkLoader.release(server);
            outletWasEmpty = false;
        }
        if (structure == null || clock % 40 == 0) validate(layout);
        if (structure == null) {
            var desired = configuredFrame(layout);
            if (!Objects.equals(desired, plan)) { plan = desired;materialsKnown = false; }
        }
        boolean inventoryChanged = storeChanged;
        storeChanged = false;
        if (inventoryChanged || clock % 10 == 0 || !materialsKnown) refreshMaterials(layout);
        if (inventoryChanged) sync();
        boolean movedParcels = false;
        if (conveyorRunning()) {
            for (int i = 0; i < WORK_STEPS; i++) movedParcels |= conveyor.tick(conveyorClock + i,
                depth + QuarryConveyor.BELT_HEIGHT + QuarryConveyor.ITEM_CLEARANCE, v + 1 - QuarryConveyor.CHUTE_OFFSET, this::unload);
            conveyorClock += WORK_STEPS;
            if (movedParcels) changed();
            else if (conveyor.parcelCount() != 0) setChanged();
        }
        if (finished && !conveyor.hasItems()) {
            if (drainPending && signal) {
                if (movedParcels) outletWasEmpty = false;
                if (clock % 20 != 0) {
                    stop("quarry_draining");
                    return;
                }
                boolean empty = !movedParcels && !layout.hasItems(level);
                if (!empty || !outletWasEmpty) {
                    outletWasEmpty = empty;
                    stop("quarry_draining");
                    return;
                }
                drainPending = false;
                setChanged();
                chunkLoader.release(server);
            }
            stop(drainPending ? "redstone" : "quarry_done");
            return;
        }
        if (!signal) {
            stop("redstone");
            return;
        }
        if (structure == null) {
            if (plan != null) for (int i = 0; i < WORK_STEPS; i += 2) buildFrame();
            return;
        }
        float speed = Math.abs(getSpeed());
        if (speed == 0) {
            stop("speed");
            return;
        }
        if (!belt) {
            for (int i = 0; i < WORK_STEPS && !belt; i += 2) buildBelt(layout);
            return;
        }
        if (finished) {
            stop("quarry_draining");
            return;
        }
        if (!drill && take(AllBlocks.MECHANICAL_DRILL.asItem())) {
            drill = true;
            changed();
        }
        if (!drill) {
            stop("drill");
            return;
        }
        int before = cellsLeft(), liquidRowBefore = nextLiquidRow;
        liquidChecksLeft = LIQUID_CHECKS_PER_TICK;
        for (int i = 0; i < WORK_STEPS; i++) if (!work(speed)) break;
        int after = cellsLeft();
        if (before != after || liquidRowBefore != nextLiquidRow) setChanged();
        updateEstimate(server, speed, before - after, after);
    }

    private boolean work(float speed) {
        if (task == null) task = planQuarry();
        if (task == null) return false;
        if (!level.hasChunkAt(task.target())) {
            cancelTask();
            stop("unloaded");
            return false;
        }
        double step = Math.min(0.49, speed / 256);
        if (Math.abs(u - task.u()) > EPSILON || Math.abs(v - task.v()) > EPSILON) {
            working = false;
            if (depth > task.travel() + EPSILON) depth = Math.max(task.travel(), depth - step);
            else {
                u = approach(u, task.u(), step);
                v = approach(v, task.v(), step);
            }
            status("moving");
            moved();
            return true;
        }
        if (Math.abs(depth - task.depth()) > EPSILON) {
            depth = approach(depth, task.depth(), step);
            status("moving");
            moved();
            return true;
        }
        if (remaining <= 0) remaining = task.ticks();
        working = true;
        if (!status.equals("full")) status("mining");
        if (level instanceof ServerLevel server) server.destroyBlockProgress(breakerId(), task.target(), 9 - 10 * remaining / Math.max(1, task.ticks()));
        if (--remaining > 0) return true;
        BlockPos target = task.target();
        BlockState state = level.getBlockState(target);
        if (openLiquid(state)) {
            cancelTask();
            return true;
        }
        boolean mined = mine(target, state);
        if (mined) {
            task = null;
            working = false;
            status("mining");
        } else stop("full");
        moved();
        return mined;
    }

    private int breakerId() { return -(int) worldPosition.asLong() - 11; }

    private static double approach(double from, double to, double step) {
        return Math.abs(to - from) <= step ? to : from + Math.signum(to - from) * step;
    }

    private void stop(String reason) {
        if (working && task != null && level instanceof ServerLevel server) server.destroyBlockProgress(breakerId(), task.target(), -1);
        working = false;
        status(reason);
    }

    private void cancelTask() {
        if (working && task != null && level instanceof ServerLevel server) server.destroyBlockProgress(breakerId(), task.target(), -1);
        task = null;
        remaining = 0;
        working = false;
    }

    private void moved() {
        setChanged();
        if (clock % 4 == 0 || storeChanged) {
            storeChanged = false;
            sync();
        }
    }

    private void status(String next) {
        if (status.equals(next)) return;
        status = next;
        sync();
    }

    private void blocked(BlockPos pos, String reason) {
        boolean moved = status.equals(reason) && !fault.equals(pos);
        fault = pos;
        stop(reason);
        if (moved) sync();
    }

    private void sync() {
        if (batchingSync) syncPending = true;
        else sendData();
    }

    private void changed() {
        setChanged();
        sync();
    }

    private void validate(QuarryBeltLayout layout) {
        var result = CraneStructure.scanConfigured(level, worldPosition, layout.along(), layout.across(), layout.length(), width() + 1);
        boolean was = structure != null;
        if (result.formed() && (structure == null || !result.equals(structure))) {
            structure = result;
            u = Math.clamp(u, 0, result.length() - 1);
            v = Math.clamp(v, 1, result.span() - 1);
            depth = Math.max(0, depth);
            plan = null;
            cancelTask();
        } else if (!result.formed()) {
            cancelTask();
            structure = null;
            if (!status.equals("frame_blocked") && !status.equals("belt_blocked")) {
                boolean moved = constructionObstruction() != null && !fault.equals(result.fault());
                fault = result.fault();
                if (moved) sync();
            }
            frameProblem = result.reason();
            if (plan == null) stop(frameProblem);
        }
        boolean stands = structure != null && layout.complete(level);
        if (stands != belt || stands && !was) {
            belt = stands;
            detachKinetics();
            attachKinetics();
            changed();
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

    private int count(Item item) {
        int total = 0;
        for (int slot = 0; slot < SLOTS; slot++) if (store.getStackInSlot(slot).is(item)) total += store.getStackInSlot(slot).getCount();
        return total;
    }

    private void refreshMaterials(QuarryBeltLayout layout) {
        int girders = 0, done = 0;
        boolean known = structure != null || plan != null;
        if (plan != null) for (int n = 0; n < plan.total(); n++) {
            if (!level.hasChunkAt(plan.pos(n))) { known = false;break; }
            if (CraneFrame.placed(level, plan, n)) done++;
            else girders++;
        }
        int missingGirders = Math.max(0, girders - count(AllBlocks.METAL_GIRDER.asItem()));
        int missingDrills = drill ? 0 : Math.max(0, 1 - count(AllBlocks.MECHANICAL_DRILL.asItem()));
        known &= layout.loaded(level);
        int belts = 0, beltShafts = 0, chains = 0;
        if (known) {
            for (var segment : layout.segments()) if (!layout.complete(level, segment)) {
                belts++;beltShafts += (layout.shaft(level, segment.first()) ? 0 : 1) + (layout.shaft(level, segment.last()) ? 0 : 1);
            }
            for (int u = -1; u < layout.length(); u++) if (!layout.chain(level, u)) chains++;
        }
        int missingBelts = Math.max(0, belts - count(com.simibubi.create.AllItems.BELT_CONNECTOR.get()));
        int missingBeltShafts = Math.max(0, beltShafts - count(AllBlocks.SHAFT.asItem()));
        int missingChains = Math.max(0, chains - count(AllBlocks.ENCASED_CHAIN_DRIVE.asItem()));
        if (materialsKnown != known || frameDone != done || frameShortGirders != missingGirders
            || shortDrills != missingDrills || shortBelts != missingBelts || shortBeltShafts != missingBeltShafts || shortChains != missingChains) {
            materialsKnown = known;frameDone = done;frameShortGirders = missingGirders;
            shortDrills = missingDrills;shortBelts = missingBelts;shortBeltShafts = missingBeltShafts;shortChains = missingChains;
            sync();
        }
    }

    // Quarrying

    private int cells() { return structure.length() * (structure.span() - 1); }
    private boolean excavationFinished() { return layer != Integer.MIN_VALUE && layer < level.getMinBuildHeight(); }
    private int liquidRows(int length) { return (worldPosition.getY() - level.getMinBuildHeight()) * length; }

    private int cellsLeft() {
        int firstLayer = structure.start().getY() - 1, area = cells();
        if (layer == Integer.MIN_VALUE || layer > firstLayer) return Math.max(0, firstLayer - level.getMinBuildHeight() + 1) * area;
        return Math.max(0, (layer - level.getMinBuildHeight() + 1) * area - cell);
    }

    private boolean estimating() {
        return status.equals("moving") || status.equals("mining") || status.equals("quarry_scanning") || status.equals("quarry_pumping");
    }

    private void resetEstimate() {
        measuringEta = false;
        sampledTicks = sampledCells = 0;
        cellsPerTick = 0;
        if (etaSeconds != -1) { etaSeconds = -1;sync(); }
    }

    private void updateEstimate(ServerLevel server, float speed, int advanced, int left) {
        if (etaSpeed != speed) {
            etaSpeed = speed;
            resetEstimate();
        }
        if (status.equals("mining") || status.equals("moving")) measuringEta = true;
        if (!measuringEta || !estimating() || left == 0) return;
        sampledTicks++;
        sampledCells += advanced;
        if (sampledTicks < ETA_SAMPLE_TICKS || sampledCells == 0) return;
        double sample = (double) sampledCells / sampledTicks;
        cellsPerTick = cellsPerTick == 0 ? sample : cellsPerTick * .75 + sample * .25;
        sampledTicks = sampledCells = 0;
        var game = server.getServer();
        double secondsPerTick = Math.max(game.getAverageTickTimeNanos(), game.tickRateManager().nanosecondsPerTick()) / 1e9;
        long next = (long) Math.ceil(left / cellsPerTick * secondsPerTick);
        if (etaSeconds != next) { etaSeconds = next;sync(); }
    }

    /** The cells between the rails, row by row along them and back and forth across, so the bridge seldom travels far. */
    private BlockPos cell(int index, int y) {
        int row = index / (structure.span() - 1), col = index % (structure.span() - 1);
        if (row % 2 == 1) col = structure.span() - 2 - col;
        return structure.at(row, col + 1).atY(y);
    }

    private Task planQuarry() {
        if (layer == Integer.MIN_VALUE || layer >= structure.start().getY()) {
            layer = structure.start().getY() - 1;
            cell = 0;
        }
        for (int checked = 0; checked < 256 && layer >= level.getMinBuildHeight(); checked++) {
            if (cell >= cells()) {
                layer--;
                cell = 0;
                continue;
            }
            int row = (worldPosition.getY() - 1 - layer) * structure.length() + cell / (structure.span() - 1);
            if (nextLiquidRow <= row && !drainThrough(row)) return null;
            BlockPos pos = cell(cell, layer);
            if (!level.isLoaded(pos)) {
                fault = pos;
                stop("unloaded");
                return null;
            }
            BlockState state = level.getBlockState(pos);
            if (openLiquid(state)) {
                if (level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())) cell++;
                stop("quarry_pumping");
                return null;
            }
            if (mineable(state, pos)) {
                double above = depthAbove(pos);
                return new Task(u(pos), v(pos), above, Math.min(above, Math.max(0, depth)), breakTicks(state, pos), pos);
            }
            cell++;
        }
        if (excavationFinished()) {
            if (!drainThrough(liquidRows(structure.length()) - 1)) return null;
            drainPending = true;
            setChanged();
            stop("quarry_draining");
        } else stop("quarry_scanning");
        return null;
    }

    private static boolean openLiquid(BlockState state) { return state.is(Blocks.WATER) || state.is(Blocks.LAVA); }

    /** Clear whole rows so adjacent water sources cannot recreate a source halfway through a row. */
    private boolean drainThrough(int targetRow) {
        int length = structure.length(), width = structure.span() - 1;
        while (nextLiquidRow <= targetRow && liquidChecksLeft >= width) {
            int y = worldPosition.getY() - 1 - nextLiquidRow / length;
            BlockPos first = structure.at(nextLiquidRow % length, 1).atY(y);
            boolean wet = false;
            for (int i = 0; i < width; i++) {
                BlockPos pos = first.relative(structure.across(), i);
                if (!level.isLoaded(pos)) {
                    fault = pos;
                    stop("unloaded");
                    return false;
                }
                if (!openLiquid(level.getBlockState(pos))) continue;
                if (!level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())) {
                    stop("quarry_pumping");
                    return false;
                }
                wet = true;
            }
            liquidChecksLeft -= width;
            nextLiquidRow++;
            if (wet) {
                stop("quarry_pumping");
                return false;
            }
        }
        if (nextLiquidRow > targetRow) return true;
        stop("quarry_pumping");
        return false;
    }

    /** Waterlogged blocks, other fluids, unbreakable blocks and blocks with contents are left in place. */
    private boolean mineable(BlockState state, BlockPos pos) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) return false;
        return !AllBlocks.METAL_GIRDER.has(state);
    }

    /** Ticks a Create mechanical drill at this speed takes on the block: ten steps, each waiting hardness over speed. */
    private int breakTicks(BlockState state, BlockPos pos) {
        float hardness = Math.max(0.05f, state.getDestroySpeed(level, pos)), speed = Math.abs(getSpeed()) / 100f;
        int progress = 0, ticks = 0;
        while (progress < 10) {
            progress += Math.clamp((int) (speed / hardness), 1, 10 - progress);
            ticks += 1 + (int) (hardness / speed);
        }
        return ticks;
    }

    private boolean mine(BlockPos pos, BlockState state) {
        if (mineable(state, pos) && !dig(pos, state, true)) return false;
        cell++;
        return true;
    }

    /**
     * Takes the block with an iron pickaxe's drops, quarried into the head bin or, clearing ground for the frame, into the store;
     * false, leaving it, if the drops would not fit.
     */
    private boolean dig(BlockPos pos, BlockState state, boolean quarried) {
        if (!(level instanceof ServerLevel server)) return false;
        List<ItemStack> drops = Block.getDrops(state, server, pos, null, null, PICKAXE);
        if (quarried ? !conveyor.fits(drops) : !fitsStore(drops)) {
            status("full");
            return false;
        }
        if (quarried) conveyor.collect(drops);
        else for (ItemStack drop : drops) ItemHandlerHelper.insertItem(store, drop.copy(), false);
        server.destroyBlockProgress(breakerId(), pos, -1);
        server.levelEvent(2001, pos, Block.getId(state));
        server.removeBlock(pos, false);
        return true;
    }

    private boolean fitsStore(List<ItemStack> drops) {
        var trial = new ItemStackHandler(SLOTS);
        for (int slot = 0; slot < SLOTS; slot++) trial.setStackInSlot(slot, store.getStackInSlot(slot).copy());
        for (ItemStack drop : drops) if (!ItemHandlerHelper.insertItem(trial, drop.copy(), false).isEmpty()) return false;
        return true;
    }

    // Building the frame

    /** One building step: clear an obstruction or set the next missing part from the store. */
    private void buildFrame() {
        if (!plan.reachable()) {
            // No ground within reach of a leg under one of the corners.
            fault = plan.corner(java.util.stream.IntStream.range(0, 4).filter(index -> plan.legHeights()[index] < 0).findFirst().orElse(0));
            status("legs");
            return;
        }
        var progress = CraneFrame.progress(level, plan);
        if (progress == null) {
            stop("unloaded");
            return;
        }
        int next = progress.next(), shortGirders = Math.max(0, progress.missing() - count(AllBlocks.METAL_GIRDER.asItem()));
        if (plan.total() - progress.missing() != frameDone || shortGirders != frameShortGirders) {
            frameDone = plan.total() - progress.missing();
            frameShortGirders = shortGirders;
            changed();
        }
        if (next < 0) { stop(frameProblem);return; }
        BlockPos pos = plan.pos(next);
        BlockState there = level.getBlockState(pos);
        if (!there.isAir() && !there.canBeReplaced()) {
            if (!mineable(there, pos)) blocked(pos, "frame_blocked");
            else if (dig(pos, there, false)) status("frame_build");
            return;
        }
        if (!take(AllBlocks.METAL_GIRDER.asItem())) {
            fault = pos;
            status("frame_supplies");
            return;
        }
        CraneFrame.place(level, plan, next);
        status("frame_build");
    }

    /** One of the item from the store; false if there is none. */
    private boolean take(Item item) {
        for (int slot = 0; slot < SLOTS; slot++)
            if (store.getStackInSlot(slot).is(item)) return !store.extractItem(slot, 1, false).isEmpty();
        return false;
    }

    @Override public void remove() {
        if (level instanceof ServerLevel server) chunkLoader.release(server);
        super.remove();
    }

    public void dismantle() {
        if (level == null || level.isClientSide()) return;
        stop("structure");
        double x = worldPosition.getX() + .5, y = worldPosition.getY() + .5, z = worldPosition.getZ() + .5;
        for (int slot = 0; slot < SLOTS; slot++) {
            Containers.dropItemStack(level, x, y, z, store.getStackInSlot(slot));
            store.setStackInSlot(slot, ItemStack.EMPTY);
        }
        if (drill) Containers.dropItemStack(level, x, y, z, AllBlocks.MECHANICAL_DRILL.asStack());
        for (ItemStack stack : conveyor.contents()) Containers.dropItemStack(level, x, y, z, stack);
        conveyor.clear();
        drill = false;
        cancelTask();
    }

    // Information

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        tooltip.add(Component.translatable(KEY + "status." + status));
        if (materialsKnown && frameShortGirders + shortDrills + shortBelts + shortBeltShafts + shortChains > 0) {
            tooltip.add(Component.translatable(KEY + "quarry_frame_missing", frameShortGirders, shortDrills));
            tooltip.add(Component.translatable(KEY + "quarry_belt_missing", shortBelts, shortBeltShafts, shortChains));
            if (plan != null && !status.equals("frame_blocked") && !status.equals("unloaded"))
                tooltip.add(Component.translatable(KEY + "range_progress", length(), width(), frameDone, plan.total()));
            else tooltip.add(Component.translatable(KEY + "fault", fault.getX(), fault.getY(), fault.getZ()));
            return true;
        }
        if (structure == null) {
            if (plan != null) tooltip.add(Component.translatable(KEY + "range_progress", length(), width(), frameDone, plan.total()));
            tooltip.add(Component.translatable(KEY + "fault", fault.getX(), fault.getY(), fault.getZ()));
            return true;
        }
        tooltip.add(Component.translatable(KEY + "quarry_frame", structure.length(), structure.span() - 1, structure.legs()));
        if (sneaking || !estimating())
            tooltip.add(Component.translatable(KEY + "hoist", String.format(Locale.ROOT, "%.1f", depth), layer != Integer.MIN_VALUE ? String.valueOf(layer) : "--"));
        else if (etaSeconds < 0) tooltip.add(Component.translatable(KEY + "quarry_eta_measuring"));
        else tooltip.add(Component.translatable(KEY + "quarry_eta", String.format(Locale.ROOT, "%d:%02d:%02d", etaSeconds / 3600, etaSeconds / 60 % 60, etaSeconds % 60)));
        tooltip.add(sneaking ? Component.translatable(KEY + "store", supplies, outputs)
            : Component.translatable(KEY + "quarry_flow", headItems, conveyor.parcelCount()));
        return true;
    }

    @Override protected AABB createRenderBoundingBox() {
        var layout=beltLayout();
        BlockPos far=layout.start().relative(layout.along(),layout.length()-1).relative(layout.across(),width()+1);
        var bounds=AABB.encapsulatingFullBlocks(worldPosition,far).inflate(2,3,2);
        return new AABB(bounds.minX,level==null ? bounds.minY-384 : level.getMinBuildHeight()-2,bounds.minZ,bounds.maxX,bounds.maxY,bounds.maxZ);
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Drill", drill);
        tag.putBoolean("Working", working);
        tag.putBoolean("Belt", belt);
        tag.putDouble("U", u);
        tag.putDouble("V", v);
        tag.putDouble("Depth", depth);
        tag.putInt("Layer", layer);
        tag.putString("Status", status);
        tag.putLong("Fault", fault.asLong());
        if (clientPacket) {
            tag.putLong("EtaSeconds", etaSeconds);
            tag.putBoolean("MaterialsKnown", materialsKnown);
            tag.putInt("MissingGirders", frameShortGirders);
            tag.putInt("MissingDrills", shortDrills);tag.putInt("MissingBelts", shortBelts);
            tag.putInt("MissingBeltShafts", shortBeltShafts);tag.putInt("MissingChains", shortChains);
        }
        conveyor.write(tag, registries, clientPacket, conveyorClock);
        if (structure != null) {
            tag.putLong("Start", structure.start().asLong());
            tag.putInt("Along", structure.along().get3DDataValue());
            tag.putInt("Across", structure.across().get3DDataValue());
            tag.putInt("Length", structure.length());
            tag.putInt("Span", structure.span());
            tag.putInt("Legs", structure.legs());
        }
        if (plan != null) {
            tag.putLong("FrameStart", plan.start().asLong());
            tag.putInt("FrameAlong", plan.along().get3DDataValue());
            tag.putInt("FrameAcross", plan.across().get3DDataValue());
            tag.putInt("FrameLength", plan.length());
            tag.putInt("FrameWidth", plan.width());
            tag.putIntArray("FrameLegs", plan.legHeights());
            tag.putInt("FrameDone", frameDone);
            tag.putInt("FrameShortGirders", frameShortGirders);
        }
        if (clientPacket) {
            int in = 0, out = 0, head = 0;
            for (int slot = 0; slot < SLOTS; slot++) {
                ItemStack stack = store.getStackInSlot(slot);
                if (supply(stack)) in += stack.getCount();
                else out += stack.getCount();
            }
            for (int slot = 0; slot < QuarryConveyor.HEAD_SLOTS; slot++) head += conveyor.head().getStackInSlot(slot).getCount();
            tag.putInt("Supplies", in);
            tag.putInt("Outputs", out);
            tag.putInt("HeadItems", head);
        } else {
            tag.put("Store", store.serializeNBT(registries));
            tag.putInt("Cell", cell);
            tag.putInt("NextLiquidRow", nextLiquidRow);
            tag.putBoolean("DrainPending", drainPending);
        }
    }

    private static int[] legHeights(int[] synced) { return synced.length == 4 ? synced : new int[4]; }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        int oldLength=length(),oldWidth=width();
        var oldVisible=visibleStructure;
        super.read(tag, registries, clientPacket);
        drill = tag.getBoolean("Drill");
        working = tag.getBoolean("Working");
        belt = tag.getBoolean("Belt");
        u = Finite.of(tag.getDouble("U"), 0, 0, Double.MAX_VALUE);
        v = Finite.of(tag.getDouble("V"), 1, 1, Double.MAX_VALUE);
        depth = Finite.of(tag.getDouble("Depth"), 0, 0, level==null ? 1024 : Math.max(0,worldPosition.getY()-level.getMinBuildHeight()+2));
        layer = tag.contains("Layer") ? tag.getInt("Layer") : Integer.MIN_VALUE;
        status = tag.getString("Status");
        if (status.isEmpty()) status = "structure";
        fault = BlockPos.of(tag.getLong("Fault"));
        conveyorClock = conveyor.read(tag, registries, clientPacket);
        structure = tag.contains("Start") ? new CraneStructure.Result(BlockPos.of(tag.getLong("Start")), Direction.from3DDataValue(tag.getInt("Along")),
            Direction.from3DDataValue(tag.getInt("Across")), tag.getInt("Length"), tag.getInt("Span"), tag.getInt("Legs"), worldPosition, "ready") : null;
        plan = clientPacket && tag.contains("FrameStart") ? new CraneFrame.Plan(BlockPos.of(tag.getLong("FrameStart")), Direction.from3DDataValue(tag.getInt("FrameAlong")),
            Direction.from3DDataValue(tag.getInt("FrameAcross")), tag.getInt("FrameLength"), tag.getInt("FrameWidth"), legHeights(tag.getIntArray("FrameLegs"))) : null;
        frameDone = tag.getInt("FrameDone");
        frameShortGirders = tag.getInt("FrameShortGirders");
        if (clientPacket) {
            etaSeconds = tag.contains("EtaSeconds") ? tag.getLong("EtaSeconds") : -1;
            if(structure!=null)visibleStructure=structure;
            else if(!status.equals("unloaded"))visibleStructure=null;
            boolean geometryChanged=!java.util.Objects.equals(oldVisible,visibleStructure) || oldLength!=length() || oldWidth!=width();
            if(geometryChanged) {
                invalidateRenderBoundingBox();
                shownU=previousU=u;shownV=previousV=v;shownDepth=previousDepth=depth;
                if(level!=null && level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_IMMEDIATE);
            }
            materialsKnown = tag.getBoolean("MaterialsKnown");
            frameShortGirders = Math.max(0, tag.getInt("MissingGirders"));
            shortDrills = Math.max(0, tag.getInt("MissingDrills"));shortBelts = Math.max(0, tag.getInt("MissingBelts"));
            shortBeltShafts = Math.max(0, tag.getInt("MissingBeltShafts"));shortChains = Math.max(0, tag.getInt("MissingChains"));
            supplies = tag.getInt("Supplies");
            outputs = tag.getInt("Outputs");
            headItems = tag.getInt("HeadItems");
            if (structure != null && shownDepth == 0 && previousDepth == 0 && shownU == 0) {
                shownU = previousU = u;
                shownV = previousV = v;
                shownDepth = previousDepth = depth;
            }
        } else {
            if (level == null || !level.isClientSide()) {
                cancelTask();
                structure = null;
                resetEstimate();
                outletWasEmpty = false;
            }
            store.deserializeNBT(registries, tag.getCompound("Store"));
            cell = tag.getInt("Cell");
            nextLiquidRow = Math.max(0, tag.getInt("NextLiquidRow"));
            drainPending = tag.getBoolean("DrainPending");
        }
    }
}
