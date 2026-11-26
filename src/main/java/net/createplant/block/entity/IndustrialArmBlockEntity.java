package net.createplant.block.entity;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.List;
import net.createmod.catnip.animation.LerpedFloat;
import net.createplant.arm.ArmBeltRecipe;
import net.createplant.arm.ArmEffects;
import net.createplant.arm.ArmPattern;
import net.createplant.arm.ArmPose;
import net.createplant.arm.ArmRecipe.Step;
import net.createplant.arm.ArmRecipe;
import net.createplant.arm.ArmRecipes;
import net.createplant.arm.ArmTool;
import net.createplant.arm.Trajectory;
import net.createplant.item.ArmToolItem;
import net.createplant.registry.CEArm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The industrial arm: finds a jig within reach, takes an open step of its tool from it, fetches the material, moves to the cell and
 * works it. The server decides everything; the tip point it is aiming at goes to the client, which turns it into joint angles with
 * Create's own arm maths and draws the arm with Create's arm models.
 */
public class IndustrialArmBlockEntity extends KineticBlockEntity {
    public static final int RANGE = 5;
    public static final byte IDLE = 0, MOVE = 1, WORK = 2, FETCH = 3, CARRY = 4;
    /** Most points one arm may have, as with Create's arm a handful is plenty. */
    public static final int MAX_POINTS = 16;
    /** How far a tool hovers above the surface it works. */
    public static final double HOVER = .12;
    /** How wide the tool's path is over an item on a belt, in blocks. */
    private static final double BELT_PATH = .45;
    /** Rotation speed at which a step takes its recipe time. */
    public static final float BASE_RPM = 16;

    public final ItemStackHandler toolSlot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof ArmToolItem;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            toolChanged();
        }
    };
    public final FluidTank tank = new FluidTank(1000) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    public ItemStack held = ItemStack.EMPTY;
    public byte phase = IDLE;
    public BlockPos jigPos;
    public int step = -1;
    /** Where the tool tip is headed; the client aims its joints at it. */
    public Vec3 tip;
    public float progress;
    /** Server side: the top of the container the material came from, while the arm reaches over to it. */
    private Vec3 fetchFrom;
    /** Fluid drawn for the current jig step; it goes back if the step is not finished. */
    private FluidStack paid = FluidStack.EMPTY;
    /**
     * Points chosen by right-clicking with the arm in hand before placing it, as with Create's mechanical arm. Material comes
     * only from {@link #inputs} when there are any; belt and depot work happens only on inputs; finished belt work goes to the
     * first of {@link #outputs} that takes it, or stays where it is when there are none.
     */
    public final List<BlockPos> inputs = new java.util.ArrayList<>(), outputs = new java.util.ArrayList<>();
    /** Server side: the belt or depot item being worked, which the arm holds still every tick, and what it becomes. Not saved. */
    private BlockPos beltPos;
    private TransportedItemStack beltItem;
    private ArmBeltRecipe beltRecipe;
    /** Belt items some arm is working, so two arms never take the same one. Weak, so lost items drop out by themselves. */
    private static final Map<TransportedItemStack, BlockPos> CLAIMED = new WeakHashMap<>();
    public final LerpedFloat baseAngle = LerpedFloat.angular().startWithValue(ArmPose.REST[0]);
    public final LerpedFloat lowerArmAngle = LerpedFloat.angular().startWithValue(ArmPose.REST[1]);
    public final LerpedFloat upperArmAngle = LerpedFloat.angular().startWithValue(ArmPose.REST[2]);
    public final LerpedFloat headAngle = LerpedFloat.angular().startWithValue(ArmPose.REST[3]);
    private float[] target = ArmPose.REST.clone();
    private int moveLeft, cooldown, syncIn;

    public IndustrialArmBlockEntity(BlockPos pos, BlockState state) {
        super(CEArm.ARM_ENTITY.get(), pos, state);
    }

    public ItemStack tool() {
        return toolSlot.getStackInSlot(0);
    }

    public ArmTool toolType() {
        return tool().getItem() instanceof ArmToolItem t ? t.type : null;
    }

    public boolean holds(BlockPos jig, int s) {
        return phase != IDLE && step == s && jig.equals(jigPos);
    }

    private double speedFactor() {
        return Math.max(0, Math.min(8, Math.abs(getSpeed()) / BASE_RPM));
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) {
            clientTick();
            return;
        }
        if (cooldown > 0) cooldown--;
        if (phase != IDLE && jigPos != null && !level.isLoaded(jigPos)) return;
        switch (phase) {
            case IDLE -> {
                if (!held.isEmpty()) abortJob(); // Old saves may have the material without its unfinished job.
                else if (getSpeed() != 0 && cooldown == 0 && level.getGameTime() % 10 == (worldPosition.hashCode() & 7)) findJob();
            }
            case CARRY -> carry();
            case FETCH -> {
                if (jig() == null) { abortJob(); return; }
                tip = fetchFrom;
                if (fetchFrom == null || speedFactor() > 0 && --moveLeft <= 0) {
                    phase = MOVE;
                    moveLeft = 12;
                    fetchFrom = null;
                    setChanged();
                }
            }
            case MOVE -> {
                if (beltPos != null) {
                    if (!holdBeltItem()) { endJob(); return; }
                    if (speedFactor() > 0 && --moveLeft <= 0) {
                        phase = WORK;
                        progress = 0;
                    }
                    break;
                }
                AssemblyJigBlockEntity jig = jig();
                if (jig == null) { abortJob(); return; }
                tip = worldTip(jig, 0);
                if (speedFactor() > 0 && --moveLeft <= 0) {
                    phase = WORK;
                    progress = 0;
                    setChanged();
                }
            }
            case WORK -> work();
            default -> phase = IDLE;
        }
        if (phase != IDLE && --syncIn <= 0) {
            syncIn = 2;
            sendData();
        }
    }

    private AssemblyJigBlockEntity jig() {
        if (jigPos == null || !level.isLoaded(jigPos) || !(level.getBlockEntity(jigPos) instanceof AssemblyJigBlockEntity j) || j.recipe == null
                || !j.reservedBy(worldPosition, step)) return null;
        return j;
    }

    private Vec3 worldTip(AssemblyJigBlockEntity jig, double p) {
        Step s = jig.recipe.steps().get(step);
        Vec3 t = Trajectory.of(s.pattern(), s.seamDirection()).tip(p);
        return origin(jig.getBlockPos(), jig.recipe, s).add(t);
    }

    /** Where a step's path starts: its cell's corner, on the work surface, with the tool hovering above it. */
    private static Vec3 origin(BlockPos jig, net.createplant.arm.ArmRecipe r, Step s) {
        return Vec3.atLowerCornerOf(jig.above().offset(s.rel())).add(0, r.base(s) + (s.tool() == ArmTool.GRIPPER ? 0 : HOVER), 0);
    }

    /** Whether every point of the step's path, for a jig at {@code jig} making {@code r}, is within reach. */
    public boolean reachesStep(BlockPos jig, net.createplant.arm.ArmRecipe r, Step s) {
        Vec3 origin = origin(jig, r, s);
        for (Vec3 p : Trajectory.of(s.pattern(), s.seamDirection()).points) if (!ArmPose.reaches(worldPosition, origin.add(p))) return false;
        return true;
    }

    /** Every point the tool passes on the step lies within the arm's reach. */
    private boolean reaches(AssemblyJigBlockEntity jig, int step) {
        return reachesStep(jig.getBlockPos(), jig.recipe, jig.recipe.steps().get(step));
    }

    private void findJob() {
        ArmTool tool = toolType();
        if (tool == null) return;
        boolean waiting = false;
        Vec3 me = Vec3.atCenterOf(worldPosition);
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-RANGE, -RANGE, -RANGE), worldPosition.offset(RANGE, RANGE, RANGE))) {
            if (!level.isLoaded(p)) continue;
            if (!(level.getBlockEntity(p) instanceof AssemblyJigBlockEntity jig) || jig.recipe == null || Vec3.atCenterOf(p).distanceTo(me) > RANGE + .5) continue;
            int s = jig.reserve(worldPosition, tool, me, step -> reaches(jig, step));
            if (s < 0) continue;
            Step st = jig.recipe.steps().get(s);
            // the fluid is checked before any material is taken, so a missing fluid never costs material
            if (st.fluid() != null && !drawFluid(jig, st, true)) {
                jig.release(worldPosition, s);
                waiting = true;
                continue;
            }
            ItemStack material = ItemStack.EMPTY;
            if (st.material() != null) {
                material = fetch(st.material(), st.materialCount(), jig.getBlockPos());
                if (material.isEmpty()) {
                    jig.release(worldPosition, s);
                    waiting = true;
                    continue;
                }
            }
            if (st.fluid() != null) {
                drawFluid(jig, st, false);
                paid = new FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(st.fluid()), st.fluidAmount());
            }
            jigPos = p.immutable();
            step = s;
            held = material;
            phase = fetchFrom != null ? FETCH : MOVE;
            moveLeft = 12;
            progress = 0;
            tip = fetchFrom != null ? fetchFrom : worldTip(jig, 0);
            setChanged();
            sendData();
            return;
        }
        // a jig waiting for material does not keep the arm from its belts and depots
        findBeltJob(tool);
        if (waiting && phase == IDLE) cooldown = 60;
    }

    // ------------------------------------------------------------- belt work

    private static TransportedItemStackHandlerBehaviour belt(net.minecraft.world.level.Level level, BlockPos pos) {
        return level.isLoaded(pos) ? BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE) : null;
    }

    /** With no jig work, an item near the middle of a belt segment or on a depot in reach that this tool has belt work for. */
    private void findBeltJob(ArmTool tool) {
        if (tool == ArmTool.GRIPPER) return;
        for (BlockPos p : inputs) {
            TransportedItemStackHandlerBehaviour belt = belt(level, p);
            if (belt == null) continue;
            TransportedItemStack[] found = {null};
            ArmBeltRecipe[] recipe = {null};
            belt.handleCenteredProcessingOnAllItems(.5f, t -> {
                ArmBeltRecipe r = found[0] == null && !claimedByOther(t) ? ArmRecipes.belt(tool, t.stack) : null;
                if (r != null && ArmPose.reaches(worldPosition, belt.getWorldPositionOf(t).add(0, HOVER, 0))) {
                    found[0] = t;
                    recipe[0] = r;
                }
                return TransportedResult.doNothing();
            });
            if (found[0] == null) continue;
            ArmBeltRecipe r = recipe[0];
            // The fluid is only checked here and drawn when the item is done, as belt work is not saved.
            if (r.fluid() != null && !drawFluid(new FluidTank[] {tank}, r.fluid(), r.fluidAmount(), true)) {
                cooldown = 60;
                return;
            }
            CLAIMED.put(found[0], worldPosition.immutable());
            beltPos = p.immutable();
            beltItem = found[0];
            beltRecipe = r;
            phase = MOVE;
            moveLeft = 12;
            progress = 0;
            holdBeltItem();
            sendData();
            return;
        }
    }

    /** Another arm is working the item. A claim left by this arm's earlier self (before a reload) or by a removed arm does not count. */
    private boolean claimedByOther(TransportedItemStack t) {
        BlockPos owner = CLAIMED.get(t);
        return owner != null && !owner.equals(worldPosition) && level.getBlockEntity(owner) instanceof IndustrialArmBlockEntity arm && arm.beltItem == t;
    }

    /** Stops the claimed item for this tick, as Create's own arm does, and aims at it. False once the item is gone. */
    private boolean holdBeltItem() {
        TransportedItemStackHandlerBehaviour belt = belt(level, beltPos);
        if (belt == null || getSpeed() == 0) return false; // without power the arm lets go, so the belt carries on
        boolean[] present = {false};
        belt.handleProcessingOnAllItems(t -> {
            if (t == beltItem) {
                present[0] = true;
                t.lockedExternally = true;
            }
            return TransportedResult.doNothing();
        });
        if (!present[0] || !beltItem.stack.is(beltRecipe.input())) return false;
        Vec3 path = Trajectory.of(ArmPattern.defaultFor(beltRecipe.tool()), null).tip(phase == WORK ? progress : 0);
        tip = belt.getWorldPositionOf(beltItem).add((path.x - .5) * BELT_PATH, HOVER, (path.z - .5) * BELT_PATH);
        return true;
    }

    private void beltWork() {
        if (toolType() != beltRecipe.tool() || !holdBeltItem()) { endJob(); return; }
        double f = speedFactor();
        if (f <= 0) return;
        progress = (float) Math.min(1, progress + f / beltRecipe.time());
        if (level.getGameTime() % 4 == 0) sound(beltRecipe.tool());
        if (progress < 1) return;
        if (beltRecipe.fluid() != null && !drawFluid(new FluidTank[] {tank}, beltRecipe.fluid(), beltRecipe.fluidAmount(), false)) {
            endJob(); // the tank ran dry meanwhile: the item stays as it was
            return;
        }
        TransportedItemStack worked = beltItem;
        if (!outputs.isEmpty()) {
            // take the finished item off and carry it to an output
            belt(level, beltPos).handleProcessingOnAllItems(t -> {
                if (t != worked) return TransportedResult.doNothing();
                if (t.stack.getCount() == 1) return TransportedResult.removeItem();
                TransportedItemStack rest = t.copy();
                rest.stack = t.stack.copyWithCount(t.stack.getCount() - 1);
                rest.lockedExternally = false;
                return TransportedResult.convertTo(rest);
            });
            ItemStack product = beltRecipe.resultStack();
            endJob();
            held = product;
            phase = CARRY;
            moveLeft = 12;
            tip = Vec3.atBottomCenterOf(outputs.get(0)).add(0, 1, 0);
            setChanged();
            sendData();
            return;
        }
        belt(level, beltPos).handleProcessingOnAllItems(t -> {
            if (t != worked) return TransportedResult.doNothing();
            TransportedItemStack out = t.copy();
            out.stack = beltRecipe.resultStack();
            out.lockedExternally = false;
            if (t.stack.getCount() == 1) return TransportedResult.convertTo(out);
            TransportedItemStack rest = t.copy();
            rest.stack = t.stack.copyWithCount(t.stack.getCount() - 1);
            rest.lockedExternally = false;
            return TransportedResult.convertTo(List.of(out, rest));
        });
        endJob();
    }

    /** The item handler of a container, belt or depot, or null. */
    public static IItemHandler items(net.minecraft.world.level.Level level, BlockPos p) {
        if (!level.isLoaded(p)) return null;
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
        return h != null ? h : level.getCapability(Capabilities.ItemHandler.BLOCK, p, net.minecraft.core.Direction.UP);
    }

    /** Adds every block this arm would take material from for the jig at {@code jigAt}. */
    public void addSources(java.util.Set<BlockPos> into, BlockPos jigAt) {
        for (BlockPos p : candidates()) {
            if (p.equals(worldPosition) || p.equals(jigAt) || into.contains(p)) continue;
            BlockEntity be = level.isLoaded(p) ? level.getBlockEntity(p) : null;
            if (be == null || be instanceof IndustrialArmBlockEntity || be instanceof AssemblyJigBlockEntity) continue;
            if (items(level, p) != null && ArmPose.reaches(worldPosition, Vec3.atBottomCenterOf(p).add(0, 1, 0))) into.add(p.immutable());
        }
    }

    /** Puts items the arm no longer needs back where it takes material from; only what does not fit is dropped. */
    private void giveBack(ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack rest = stack.copy();
        for (BlockPos p : candidates()) {
            if (rest.isEmpty()) break;
            if (p.equals(worldPosition) || p.equals(jigPos)) continue;
            BlockEntity be = level.isLoaded(p) ? level.getBlockEntity(p) : null;
            if (be == null || be instanceof IndustrialArmBlockEntity || be instanceof AssemblyJigBlockEntity) continue;
            IItemHandler h = items(level, p);
            if (h != null && ArmPose.reaches(worldPosition, Vec3.atBottomCenterOf(p).add(0, 1, 0))) rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(h, rest, false);
        }
        if (!rest.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5, rest);
    }

    /** Where material may come from: the chosen inputs, or with none chosen every block in range. */
    private Iterable<BlockPos> candidates() {
        return inputs.isEmpty() ? BlockPos.betweenClosed(worldPosition.offset(-RANGE, -RANGE, -RANGE), worldPosition.offset(RANGE, RANGE, RANGE)) : inputs;
    }

    /** Puts the carried item into the first output that takes all of it; waits over a full one and tries again. */
    private void carry() {
        if (held.isEmpty() || outputs.isEmpty()) { abortJob(); return; }
        if (speedFactor() <= 0 || --moveLeft > 0) return;
        for (BlockPos out : outputs) {
            IItemHandler h = level.isLoaded(out) ? level.getCapability(Capabilities.ItemHandler.BLOCK, out, net.minecraft.core.Direction.UP) : null;
            if (h == null) continue;
            tip = Vec3.atBottomCenterOf(out).add(0, 1, 0);
            if (!net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(h, held.copy(), true).isEmpty()) continue;
            net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(h, held, false);
            held = ItemStack.EMPTY;
            endJob();
            return;
        }
        moveLeft = 10; // every output is full: hold on and try again
        sendData();
    }

    /** Whether the arm can use the block at {@code p} as a point: something holding items or a belt or depot, whose top it reaches. */
    public static boolean validPoint(net.minecraft.world.level.Level level, BlockPos arm, BlockPos p) {
        if (p.equals(arm) || Math.abs(p.getX() - arm.getX()) > RANGE || Math.abs(p.getY() - arm.getY()) > RANGE || Math.abs(p.getZ() - arm.getZ()) > RANGE) return false;
        if (!ArmPose.reaches(arm, Vec3.atBottomCenterOf(p).add(0, 1, 0))) return false;
        BlockEntity be = level.getBlockEntity(p);
        if (be == null || be instanceof IndustrialArmBlockEntity || be instanceof AssemblyJigBlockEntity) return false;
        return belt(level, p) != null || level.getCapability(Capabilities.ItemHandler.BLOCK, p, null) != null
                || level.getCapability(Capabilities.ItemHandler.BLOCK, p, net.minecraft.core.Direction.UP) != null;
    }

    /** Sets the chosen points, keeping only valid ones; returns how many were dropped. */
    public int setPoints(List<BlockPos> in, List<BlockPos> out) {
        inputs.clear();
        outputs.clear();
        int dropped = 0;
        for (BlockPos p : in) if (inputs.size() < MAX_POINTS && validPoint(level, worldPosition, p)) inputs.add(p.immutable()); else dropped++;
        for (BlockPos p : out) if (outputs.size() < MAX_POINTS && !inputs.contains(p) && validPoint(level, worldPosition, p)) outputs.add(p.immutable()); else dropped++;
        setChanged();
        sendData();
        return dropped;
    }

    /** Spray fluid comes from the arm's own tank, or from the jig's when the arm has none; a pipe can feed either. */
    private boolean drawFluid(AssemblyJigBlockEntity jig, Step s, boolean simulate) {
        return drawFluid(new FluidTank[] {tank, jig.tank}, s.fluid(), s.fluidAmount(), simulate);
    }

    private static boolean drawFluid(FluidTank[] tanks, net.minecraft.resources.ResourceLocation fluid, int amount, boolean simulate) {
        var action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
        for (FluidTank t : tanks) {
            FluidStack in = t.getFluid();
            if (!in.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(in.getFluid()).equals(fluid) && in.getAmount() >= amount) {
                t.drain(amount, action);
                return true;
            }
        }
        return false;
    }

    /** One item from a chest, depot, belt or whatever holds items around the arm; never from arms or jigs. */
    /**
     * Takes {@code count} of the item from the containers the claw reaches, or nothing if they do not hold that many together.
     * The result may be larger than a stack (a core plane takes 144 cores); it is saved and dropped in pieces.
     */
    private ItemStack fetch(net.minecraft.world.item.Item item, int count, BlockPos jigAt) {
        fetchFrom = null;
        // each stack is counted once: both halves of a large chest hand out the same stacks
        java.util.Set<ItemStack> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<IItemHandler> handlers = new java.util.ArrayList<>();
        List<Integer> slots = new java.util.ArrayList<>();
        int available = 0;
        for (BlockPos p : candidates()) {
            if (available >= count) break;
            if (p.equals(worldPosition) || p.equals(jigAt) || !level.isLoaded(p)) continue;
            BlockEntity be = level.getBlockEntity(p);
            if (be == null || be instanceof IndustrialArmBlockEntity || be instanceof AssemblyJigBlockEntity) continue;
            IItemHandler h = items(level, p);
            Vec3 top = Vec3.atBottomCenterOf(p).add(0, 1, 0);
            if (h == null || !ArmPose.reaches(worldPosition, top)) continue; // only from what the claw can reach
            for (int slot = 0; slot < h.getSlots() && available < count; slot++) {
                ItemStack st = h.getStackInSlot(slot);
                if (!st.is(item) || !seen.add(st)) continue;
                int can = h.extractItem(slot, st.getCount(), true).getCount();
                if (can <= 0) continue;
                if (fetchFrom == null) fetchFrom = top;
                handlers.add(h);
                slots.add(slot);
                available += can;
            }
        }
        if (available < count) {
            fetchFrom = null;
            return ItemStack.EMPTY;
        }
        int got = 0;
        for (int k = 0; k < handlers.size() && got < count; k++) got += handlers.get(k).extractItem(slots.get(k), count - got, false).getCount();
        if (got < count) {
            // something changed between counting and taking: never start short
            giveBack(new ItemStack(item, got));
            fetchFrom = null;
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, got);
    }

    private void work() {
        if (beltPos != null) {
            beltWork();
            return;
        }
        AssemblyJigBlockEntity jig = jig();
        if (jig == null) { abortJob(); return; }
        Step s = jig.recipe.steps().get(step);
        double f = speedFactor();
        if (f <= 0) return;
        if (progress < 1) {
            progress = (float) Math.min(1, progress + f / s.time());
            if (level.getGameTime() % 20 == 0) setChanged();
        }
        tip = worldTip(jig, progress);
        jig.setProgress(step, progress);
        if (level.getGameTime() % 4 == 0) sound(s.tool());
        if (progress < 1) return;
        if (s.place() && jig.standing(step)) {
            jig.complete(step); // put back by hand meanwhile: the arm keeps its block for later
            giveBack(held);
        } else if (s.place()) {
            if (!jig.place(step)) return; // the cell is not free: keep holding the block until it is
        } else {
            jig.complete(step);
        }
        held = ItemStack.EMPTY;
        paid = FluidStack.EMPTY;
        endJob();
    }

    private void sound(ArmTool t) {
        SoundEvent e = switch (t) {
            case WELDING_TORCH -> SoundEvents.FIRECHARGE_USE;
            case LASER_CUTTER -> SoundEvents.REDSTONE_TORCH_BURNOUT;
            case SPRAY_NOZZLE -> SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT;
            case WIRING_HEAD -> SoundEvents.STONE_BUTTON_CLICK_ON;
            default -> null;
        };
        if (e != null && tip != null) level.playSound(null, BlockPos.containing(tip), e, SoundSource.BLOCKS, t == ArmTool.WELDING_TORCH ? .35f : .25f, t == ArmTool.WELDING_TORCH ? 1.7f : 1.2f);
    }

    private void endJob() {
        if (beltItem != null) CLAIMED.remove(beltItem);
        beltPos = null;
        beltItem = null;
        beltRecipe = null;
        phase = IDLE;
        jigPos = null;
        step = -1;
        tip = null;
        cooldown = 4;
        setChanged();
        sendData();
    }

    /** Gives the step back and drops what the arm holds. */
    public void abortJob() {
        if (level != null && !level.isClientSide) {
            AssemblyJigBlockEntity j = jigPos != null && level.isLoaded(jigPos) && level.getBlockEntity(jigPos) instanceof AssemblyJigBlockEntity x ? x : null;
            if (j != null && step >= 0) j.release(worldPosition, step);
            giveBack(held);
            held = ItemStack.EMPTY;
            if (!paid.isEmpty()) {
                // the unused fluid goes back: into the arm's tank, what does not fit into the jig's
                int in = tank.fill(paid.copy(), IFluidHandler.FluidAction.EXECUTE);
                if (in < paid.getAmount() && j != null) j.tank.fill(paid.copyWithAmount(paid.getAmount() - in), IFluidHandler.FluidAction.EXECUTE);
                paid = FluidStack.EMPTY;
            }
        }
        endJob();
    }

    private void toolChanged() {
        if (level == null || level.isClientSide) return;
        if (phase != IDLE || !held.isEmpty()) abortJob();
        setChanged();
        sendData();
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        if (phase != IDLE || !held.isEmpty()) abortJob();
        Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, tool());
        toolSlot.setStackInSlot(0, ItemStack.EMPTY);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        super.addToGoggleTooltip(tooltip, sneaking);
        ArmTool tool = toolType();
        String state = tool == null ? "no_tool" : phase == IDLE ? "idle" : phase == FETCH ? "fetch" : phase == MOVE ? "move" : phase == CARRY ? "carry" : getSpeed() == 0 ? "stalled" : "work";
        tooltip.add(Component.translatable("arm.create_refinery_n_reactors.status." + state));
        if (tool != null) tooltip.add(Component.translatable("arm.create_refinery_n_reactors.tool", tool().getHoverName()));
        if (phase != IDLE && beltItem != null) tooltip.add(Component.translatable("arm.create_refinery_n_reactors.belt_job", beltItem.stack.getHoverName()));
        if (phase != IDLE && jigPos != null) tooltip.add(Component.translatable("arm.create_refinery_n_reactors.job", jigPos.getX(), jigPos.getY(), jigPos.getZ(), step + 1));
        tooltip.add(Component.translatable("arm.create_refinery_n_reactors.tank", tank.getFluidAmount(), tank.getCapacity()));
        tooltip.add(Component.translatable("arm.create_refinery_n_reactors.points", inputs.size(), outputs.size()));
        return true;
    }

    // ---------------------------------------------------------------- client

    /** Client side: the tool tip the arm moves to, or null to go back to rest. The synced state and the Ponder scene both come through here. */
    public void aim(byte phase, Vec3 tip) {
        this.phase = phase;
        this.tip = tip;
        target = tip == null ? ArmPose.REST : ArmPose.toward(worldPosition, tip);
    }

    private void clientTick() {
        float[] a = phase != IDLE && tip != null ? target : ArmPose.REST;
        baseAngle.chase(a[0], .35, LerpedFloat.Chaser.EXP);
        lowerArmAngle.chase(a[1], .35, LerpedFloat.Chaser.EXP);
        upperArmAngle.chase(a[2], .35, LerpedFloat.Chaser.EXP);
        headAngle.chase(a[3], .35, LerpedFloat.Chaser.EXP);
        baseAngle.tickChaser();
        lowerArmAngle.tickChaser();
        upperArmAngle.tickChaser();
        headAngle.tickChaser();
        if (phase == WORK) ArmEffects.arm(this);
    }

    // ------------------------------------------------------------------ data

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.write(tag, r, clientPacket);
        tag.put("tools", toolSlot.serializeNBT(r));
        tag.put("tank", tank.writeToNBT(r, new CompoundTag()));
        // A held count above a stack (144 cores) is not a valid saved stack, so the count goes beside it.
        tag.put("held", held.isEmpty() ? new CompoundTag() : held.copyWithCount(1).save(r));
        tag.putInt("heldCount", held.getCount());
        if (phase != IDLE && jigPos != null) {
            tag.putByte("phase", phase);
            tag.putInt("step", step);
            tag.putLong("jig", jigPos.asLong());
            tag.putInt("moveLeft", moveLeft);
            tag.putFloat("jobProgress", progress);
        }
        tag.putLongArray("inputs", inputs.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("outputs", outputs.stream().mapToLong(BlockPos::asLong).toArray());
        if (phase == CARRY) tag.putBoolean("carry", true);
        if (!paid.isEmpty()) tag.put("paid", paid.save(r));
        if (clientPacket) tag.putByte("shownPhase", phase);
        if (clientPacket && tip != null) {
            tag.putDouble("tx", tip.x);
            tag.putDouble("ty", tip.y);
            tag.putDouble("tz", tip.z);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        toolSlot.deserializeNBT(r, tag.getCompound("tools"));
        tank.readFromNBT(r, tag.getCompound("tank"));
        held = ItemStack.parseOptional(r, tag.getCompound("held"));
        if (!held.isEmpty() && tag.contains("heldCount")) held = held.copyWithCount(Math.max(1, tag.getInt("heldCount")));
        byte savedPhase = tag.getByte("phase");
        boolean hasJob = (savedPhase == FETCH || savedPhase == MOVE || savedPhase == WORK) && tag.contains("jig") && tag.contains("step");
        phase = !hasJob ? IDLE : savedPhase == FETCH && !clientPacket ? MOVE : savedPhase;
        step = hasJob ? tag.getInt("step") : -1;
        jigPos = hasJob ? BlockPos.of(tag.getLong("jig")) : null;
        moveLeft = tag.getInt("moveLeft");
        paid = tag.contains("paid") ? FluidStack.parseOptional(r, tag.getCompound("paid")) : FluidStack.EMPTY;
        inputs.clear();
        outputs.clear();
        for (long l : tag.getLongArray("inputs")) inputs.add(BlockPos.of(l));
        for (long l : tag.getLongArray("outputs")) outputs.add(BlockPos.of(l));
        if (!clientPacket && tag.getBoolean("carry") && !held.isEmpty()) {
            phase = CARRY;
            moveLeft = 12;
        }
        float savedProgress = tag.getFloat("jobProgress");
        progress = hasJob && Float.isFinite(savedProgress) ? Math.clamp(savedProgress, 0, 1) : 0;
        if (clientPacket) {
            aim(tag.getByte("shownPhase"), tag.contains("tx") ? new Vec3(tag.getDouble("tx"), tag.getDouble("ty"), tag.getDouble("tz")) : null);
        }
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition).inflate(RANGE + 1);
    }
}
