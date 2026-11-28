package net.createplant.logistics.block.entity;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.createmod.catnip.animation.LerpedFloat;
import net.createplant.logistics.arm.ArmEffects;
import net.createplant.logistics.arm.ArmPattern;
import net.createplant.logistics.arm.ArmPose;
import net.createplant.logistics.arm.ArmRecipe;
import net.createplant.logistics.arm.ArmRecipe.Step;
import net.createplant.logistics.arm.ArmTool;
import net.createplant.logistics.arm.Trajectory;
import net.createplant.logistics.item.ArmToolItem;
import net.createplant.logistics.registry.CEArm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The industrial arm works items lying on the depots and belts chosen as its places to take from: it holds the item still, as
 * Create's arm does, fetches the parts of the item's next {@link ArmRecipe} step from its other places to take from, works the item
 * along the step's path and leaves it with its progress, or carries the finished product to a place to put to. Wire threading takes a
 * second, bare arm holding the far end of the wire. The server decides everything; the tip point it is aiming at goes to the client,
 * which turns it into joint angles with Create's own arm maths and draws the arm with Create's arm models.
 */
public class IndustrialArmBlockEntity extends KineticBlockEntity {
    public static final int RANGE = 5;
    public static final byte IDLE = 0, MOVE = 1, WORK = 2, FETCH = 3, CARRY = 4, ASSIST = 5;
    /** Most points one arm may have, as with Create's arm a handful is plenty. */
    public static final int MAX_POINTS = 16;
    /** How far a tool hovers above the surface it works. */
    public static final double HOVER = .12;
    /** How far above the wire's end a helper's claw holds it. */
    public static final double HELPER_LIFT = .05;
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
    /** The parts for the current step, or a product being carried. */
    public ItemStack held = ItemStack.EMPTY;
    public byte phase = IDLE;
    /** Where the tool tip is headed; the client aims its joints at it. */
    public Vec3 tip;
    public float progress;
    /**
     * Points chosen by right-clicking with the arm in hand before placing it, as with Create's mechanical arm. Items are worked only
     * on depots and belts among {@link #inputs}; parts come from the other inputs (or, with none chosen, from whatever holds items
     * in reach); finished products go to the first of {@link #outputs} that takes them, or stay where they are.
     */
    public final List<BlockPos> inputs = new ArrayList<>(), outputs = new ArrayList<>();
    /** The bare arm helping with this arm's step, and for a helper the arm it helps. */
    public BlockPos helper, lead;
    /** The item worked: where it lies, the recipe and step. Synced, so the client draws the work over the item. */
    public Vec3 workAt;
    public ArmRecipe workRecipe;
    public int workStep;
    /** Server side, not saved (the parts go back on reload and the item keeps its progress): the item held still and its job. */
    private BlockPos beltPos;
    private TransportedItemStack beltItem;
    private RecipeHolder<ArmRecipe> job;
    /** Server side: the top of the container the parts came from, while the arm reaches over to it. */
    private Vec3 fetchFrom;
    /** The workpiece's position is excluded when returning parts, including after loading. */
    private BlockPos partWorkPos;
    /** Items some arm is working, so two arms never take the same one. Weak, so lost items drop out by themselves. */
    private static final Map<TransportedItemStack, BlockPos> CLAIMED = new WeakHashMap<>();
    public final LerpedFloat baseAngle = LerpedFloat.angular().startWithValue(ArmPose.REST[0]);
    public final LerpedFloat lowerArmAngle = LerpedFloat.angular().startWithValue(ArmPose.REST[1]);
    public final LerpedFloat upperArmAngle = LerpedFloat.angular().startWithValue(ArmPose.REST[2]);
    public final LerpedFloat headAngle = LerpedFloat.angular().startWithValue(ArmPose.REST[3]);
    public final LerpedFloat wristLift = LerpedFloat.linear();
    private float[] target = ArmPose.REST.clone();
    private int moveLeft, cooldown, syncIn;

    public IndustrialArmBlockEntity(BlockPos pos, BlockState state) {
        super(CEArm.ARM_ENTITY.get(), pos, state);
        baseAngle.startWithValue(rest()[0]);
        target = rest();
    }

    private float[] rest() {
        BlockState state = getBlockState();
        return ArmPose.rest(state.hasProperty(net.createplant.logistics.block.IndustrialArmBlock.FACING)
            ? state.getValue(net.createplant.logistics.block.IndustrialArmBlock.FACING) : Direction.NORTH);
    }

    public ItemStack tool() {
        return toolSlot.getStackInSlot(0);
    }

    public ArmTool toolType() {
        return tool().getItem() instanceof ArmToolItem t ? t.type : ArmTool.HAND;
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
        switch (phase) {
            case IDLE -> {
                if (!held.isEmpty()) abortJob(); // parts left from before a reload go back
                else if (getSpeed() != 0 && cooldown == 0 && level.getGameTime() % 10 == (worldPosition.hashCode() & 7)) findJob();
            }
            case CARRY -> carry();
            case ASSIST -> {
                // the lead moves this arm; it lets go once the lead no longer works with it
                if (lead == null || toolType() != ArmTool.HAND
                        || level.isLoaded(lead) && !(level.getBlockEntity(lead) instanceof IndustrialArmBlockEntity l && l.phase != IDLE && worldPosition.equals(l.helper))) endJob();
            }
            case FETCH -> {
                if (!holdItem()) { abortJob(); return; }
                tip = fetchFrom;
                if (speedFactor() > 0 && --moveLeft <= 0) {
                    phase = MOVE;
                    moveLeft = 12;
                }
            }
            case MOVE -> {
                if (!holdItem() || !driveHelper(0)) { abortJob(); return; }
                tip = stepTip(0);
                if (speedFactor() > 0 && --moveLeft <= 0) {
                    phase = WORK;
                    progress = 0;
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

    // ------------------------------------------------------------- geometry

    /** A point of a pattern's path, in path units, over an item lying at {@code at}, with the tool hovering above it. */
    public static Vec3 on(Vec3 at, Vec3 local, ArmPattern pattern) {
        double s = pattern.scale();
        return at.add((local.x - .5) * s, (local.y - 1) * s + HOVER, (local.z - .5) * s);
    }

    private Step step() {
        return job.value().steps().get(workStep);
    }

    private Vec3 stepTip(double p) {
        return on(workAt, Trajectory.of(step().pattern()).tip(p), step().pattern());
    }

    private Vec3 helperTip(double p) {
        return on(workAt, Trajectory.of(step().pattern()).runEnd(p), step().pattern()).add(0, HELPER_LIFT, 0);
    }

    /** Whether an arm at {@code arm} reaches every point of the step's path over an item at {@code at}. */
    public static boolean reachesPath(BlockPos arm, Vec3 at, Step s) {
        for (Vec3 p : Trajectory.of(s.pattern()).points) if (!ArmPose.reaches(arm, on(at, p, s.pattern()))) return false;
        return true;
    }

    // ------------------------------------------------------------------- jobs

    private static TransportedItemStackHandlerBehaviour belt(Level level, BlockPos pos) {
        return level.isLoaded(pos) ? BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE) : null;
    }

    /** Looks over the chosen depots and belts for an item whose next step this arm can do, with its parts and, if needed, a helper. */
    private void findJob() {
        ArmTool tool = toolType();
        boolean waiting = false;
        for (BlockPos p : inputs) {
            TransportedItemStackHandlerBehaviour belt = belt(level, p);
            if (belt == null) continue;
            List<TransportedItemStack> items = new ArrayList<>();
            belt.handleCenteredProcessingOnAllItems(.5f, t -> {
                if (!claimedByOther(t)) items.add(t);
                return TransportedResult.doNothing();
            });
            for (TransportedItemStack t : items) {
                Vec3 at = belt.getWorldPositionOf(t);
                for (ArmRecipe.Next next : ArmRecipe.next(level, t.stack)) {
                    Step s = next.get();
                    if (s.tool() != tool || !reachesPath(worldPosition, at, s)) continue;
                    // the helper is found before any part is taken, so waiting for one never costs parts
                    IndustrialArmBlockEntity h = s.helpers() > 0 ? findHelper(at, s) : null;
                    if (s.helpers() > 0 && h == null) { waiting = true; continue; }
                    ItemStack parts = s.consume().isPresent() ? fetch(s.consume().get(), p) : ItemStack.EMPTY;
                    if (s.consume().isPresent() && parts.isEmpty()) { waiting = true; continue; }
                    start(p, t, at, next, parts, h);
                    return;
                }
            }
        }
        if (waiting) cooldown = 60; // and look again in three seconds
    }

    private void start(BlockPos p, TransportedItemStack t, Vec3 at, ArmRecipe.Next next, ItemStack parts, IndustrialArmBlockEntity h) {
        CLAIMED.put(t, worldPosition.immutable());
        beltPos = p.immutable();
        beltItem = t;
        job = next.recipe();
        workRecipe = job.value();
        workStep = next.step();
        workAt = at;
        held = parts;
        partWorkPos = parts.isEmpty() ? null : beltPos;
        progress = 0;
        moveLeft = 12;
        phase = fetchFrom != null ? FETCH : MOVE;
        tip = fetchFrom != null ? fetchFrom : stepTip(0);
        if (h != null) {
            helper = h.worldPosition;
            h.lead = worldPosition;
            h.phase = ASSIST;
            h.tip = helperTip(0);
            h.setChanged();
            h.sendData();
        }
        holdItem();
        setChanged();
        sendData();
    }

    /** An idle, powered bare arm other than this one that reaches the step's path over the item. */
    private IndustrialArmBlockEntity findHelper(Vec3 at, Step s) {
        BlockPos c = BlockPos.containing(at);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-RANGE, -RANGE, -RANGE), c.offset(RANGE, RANGE, RANGE)))
            if (!p.equals(worldPosition) && level.isLoaded(p) && level.getBlockEntity(p) instanceof IndustrialArmBlockEntity h && h.phase == IDLE && h.held.isEmpty()
                    && h.toolType() == ArmTool.HAND && h.getSpeed() != 0 && reachesPath(h.worldPosition, at, s)) return h;
        return null;
    }

    /** The helper, while it still helps this arm; null otherwise. */
    private IndustrialArmBlockEntity helperArm() {
        return helper != null && level.getBlockEntity(helper) instanceof IndustrialArmBlockEntity h && h.phase == ASSIST && worldPosition.equals(h.lead) ? h : null;
    }

    /** Moves the helper to where the wire comes out; false when the step has a helper that is gone. */
    private boolean driveHelper(double p) {
        if (helper == null) return true;
        IndustrialArmBlockEntity h = helperArm();
        if (h == null) return false;
        h.tip = helperTip(p);
        return true;
    }

    /** Another arm is working the item. A claim left by this arm's earlier self (before a reload) or by a removed arm does not count. */
    private boolean claimedByOther(TransportedItemStack t) {
        BlockPos owner = CLAIMED.get(t);
        return owner != null && !owner.equals(worldPosition) && level.getBlockEntity(owner) instanceof IndustrialArmBlockEntity arm && arm.beltItem == t;
    }

    /** Stops the claimed item for this tick, as Create's own arm does. False once the item is gone or the arm has no power. */
    private boolean holdItem() {
        TransportedItemStackHandlerBehaviour belt = beltPos == null ? null : belt(level, beltPos);
        if (belt == null || getSpeed() == 0) return false; // without power the arm lets go, so the belt carries on
        boolean[] present = {false};
        belt.handleProcessingOnAllItems(t -> {
            if (t == beltItem) {
                present[0] = true;
                t.lockedExternally = true;
            }
            return TransportedResult.doNothing();
        });
        if (!present[0]) return false;
        workAt = belt.getWorldPositionOf(beltItem);
        return true;
    }

    private void work() {
        if (!holdItem()) { abortJob(); return; }
        IndustrialArmBlockEntity h = helper == null ? null : helperArm();
        if (helper != null && h == null) { abortJob(); return; } // the helper was taken away: the parts go back
        double f = h == null ? speedFactor() : Math.min(speedFactor(), h.speedFactor());
        if (f <= 0) return;
        Step s = step();
        progress = (float) Math.min(1, progress + f / s.time());
        tip = stepTip(progress);
        if (h != null) h.tip = helperTip(progress);
        if (level.getGameTime() % 4 == 0) sound(s.tool());
        if (progress < 1) return;
        finishStep();
    }

    /** One item of the stack becomes the next stage (or the product); the parts are used up. */
    private void finishStep() {
        ArmRecipe r = job.value();
        ItemStack out = r.after(job.id(), workStep);
        boolean last = workStep + 1 >= r.steps().size();
        TransportedItemStack worked = beltItem;
        TransportedItemStackHandlerBehaviour belt = belt(level, beltPos);
        held = ItemStack.EMPTY;
        if (last && !outputs.isEmpty()) {
            // take the product off and carry it to an output
            belt.handleProcessingOnAllItems(t -> t != worked ? TransportedResult.doNothing() : t.stack.getCount() == 1 ? TransportedResult.removeItem() : TransportedResult.convertTo(rest(t)));
            endJob();
            held = out;
            phase = CARRY;
            moveLeft = 12;
            tip = Vec3.atBottomCenterOf(outputs.get(0)).add(0, 1, 0);
            setChanged();
            sendData();
            return;
        }
        belt.handleProcessingOnAllItems(t -> {
            if (t != worked) return TransportedResult.doNothing();
            TransportedItemStack next = t.copy();
            next.stack = out;
            next.lockedExternally = false;
            return t.stack.getCount() == 1 ? TransportedResult.convertTo(next) : TransportedResult.convertTo(List.of(next, rest(t)));
        });
        endJob();
    }

    private static TransportedItemStack rest(TransportedItemStack t) {
        TransportedItemStack rest = t.copy();
        rest.stack = t.stack.copyWithCount(t.stack.getCount() - 1);
        rest.lockedExternally = false;
        return rest;
    }

    private void sound(ArmTool t) {
        if (tip == null) return;
        boolean torch = t == ArmTool.WELDING_TORCH;
        level.playSound(null, BlockPos.containing(tip), torch ? SoundEvents.FIRECHARGE_USE : SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, torch ? .35f : .25f, torch ? 1.7f : 1.2f);
    }

    // ------------------------------------------------------------------ parts

    /** The item handler of a container, belt or depot, or null. */
    public static IItemHandler items(Level level, BlockPos p) {
        if (!level.isLoaded(p)) return null;
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
        return h != null ? h : level.getCapability(Capabilities.ItemHandler.BLOCK, p, Direction.UP);
    }

    /** Parts come from containers and explicitly selected supply depots. */
    private record Source(BlockPos pos, IItemHandler items) { }

    private List<Source> sources(BlockPos workPos) {
        List<Source> out = new ArrayList<>();
        boolean explicit = !inputs.isEmpty();
        Iterable<BlockPos> around = explicit ? inputs : BlockPos.betweenClosed(worldPosition.offset(-RANGE, -RANGE, -RANGE), worldPosition.offset(RANGE, RANGE, RANGE));
        for (BlockPos p : around) {
            if (p.equals(worldPosition) || p.equals(workPos) || !level.isLoaded(p)) continue;
            BlockEntity be = level.getBlockEntity(p);
            if (be == null || be instanceof IndustrialArmBlockEntity) continue;
            var transported = belt(level, p);
            if (transported != null && (!explicit || !(be instanceof DepotBlockEntity) || hasClaimedItem(transported))) continue;
            IItemHandler h = items(level, p);
            if (h != null && ArmPose.reaches(worldPosition, Vec3.atBottomCenterOf(p).add(0, 1, 0))) out.add(new Source(p.immutable(), h));
        }
        return out;
    }

    private boolean hasClaimedItem(TransportedItemStackHandlerBehaviour transported) {
        boolean[] claimed = {false};
        transported.handleProcessingOnAllItems(stack -> {
            claimed[0] |= stack.lockedExternally || claimedByOther(stack);
            return TransportedResult.doNothing();
        });
        return claimed[0];
    }

    /**
     * Takes {@code part.count()} of the part from the containers the claw reaches, or nothing if they do not hold that many together.
     * The result may be larger than a stack (a core plane takes 144 cores); it is saved and given back in pieces.
     */
    private ItemStack fetch(ArmRecipe.Part part, BlockPos workPos) {
        fetchFrom = null;
        int count = part.count();
        // each stack is counted once: both halves of a large chest hand out the same stacks
        java.util.Set<ItemStack> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<IItemHandler> handlers = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        int available = 0;
        for (Source src : sources(workPos)) {
            IItemHandler h = src.items();
            for (int slot = 0; slot < h.getSlots() && available < count; slot++) {
                ItemStack st = h.getStackInSlot(slot);
                if (!st.is(part.item()) || !seen.add(st)) continue;
                int can = h.extractItem(slot, st.getCount(), true).getCount();
                if (can <= 0) continue;
                if (fetchFrom == null) fetchFrom = Vec3.atBottomCenterOf(src.pos()).add(0, 1, 0);
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
            giveBack(new ItemStack(part.item(), got), workPos);
            fetchFrom = null;
            return ItemStack.EMPTY;
        }
        return new ItemStack(part.item(), got);
    }

    /** Puts parts the arm no longer needs back where it takes them from; only what does not fit is dropped. */
    private void giveBack(ItemStack stack, BlockPos workPos) {
        if (stack.isEmpty()) return;
        ItemStack rest = stack.copy();
        for (Source src : sources(workPos)) if (!rest.isEmpty()) rest = ItemHandlerHelper.insertItem(src.items(), rest, false);
        while (!rest.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5, rest.split(rest.getMaxStackSize()));
    }

    /** Puts the carried product into the first output that takes all of it; waits over a full one and tries again. */
    private void carry() {
        if (held.isEmpty() || outputs.isEmpty()) { abortJob(); return; }
        if (speedFactor() <= 0 || --moveLeft > 0) return;
        for (BlockPos out : outputs) {
            IItemHandler h = level.isLoaded(out) ? level.getCapability(Capabilities.ItemHandler.BLOCK, out, Direction.UP) : null;
            if (h == null) continue;
            tip = Vec3.atBottomCenterOf(out).add(0, 1, 0);
            if (!ItemHandlerHelper.insertItem(h, held.copy(), true).isEmpty()) continue;
            ItemHandlerHelper.insertItem(h, held, false);
            held = ItemStack.EMPTY;
            endJob();
            return;
        }
        moveLeft = 10; // every output is full: hold on and try again
        sendData();
    }

    // ----------------------------------------------------------------- points

    /** Whether the arm can use the block at {@code p} as a point: something holding items or a belt or depot, whose top it reaches. */
    public static boolean validPoint(Level level, BlockPos arm, BlockPos p) {
        if (p.equals(arm) || Math.abs(p.getX() - arm.getX()) > RANGE || Math.abs(p.getY() - arm.getY()) > RANGE || Math.abs(p.getZ() - arm.getZ()) > RANGE) return false;
        if (!ArmPose.reaches(arm, Vec3.atBottomCenterOf(p).add(0, 1, 0))) return false;
        BlockEntity be = level.getBlockEntity(p);
        if (be == null || be instanceof IndustrialArmBlockEntity) return false;
        return belt(level, p) != null || items(level, p) != null;
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

    // ------------------------------------------------------------------ state

    private void endJob() {
        if (beltItem != null) {
            CLAIMED.remove(beltItem);
            beltItem.lockedExternally = false;
        }
        beltPos = null;
        beltItem = null;
        partWorkPos = null;
        job = null;
        workRecipe = null;
        workAt = null;
        IndustrialArmBlockEntity h = helper == null ? null : helperArm();
        helper = null;
        lead = null;
        if (h != null) h.endJob();
        phase = IDLE;
        tip = null;
        cooldown = 4;
        setChanged();
        sendData();
    }

    /** Stops the job and gives back the parts it holds; the item keeps the progress of the steps done. */
    public void abortJob() {
        if (level != null && !level.isClientSide) {
            giveBack(held, partWorkPos);
            held = ItemStack.EMPTY;
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
        String state = phase == IDLE ? "idle" : phase == ASSIST ? "assist" : phase == FETCH ? "fetch" : phase == MOVE ? "move" : phase == CARRY ? "carry" : getSpeed() == 0 ? "stalled" : "work";
        tooltip.add(Component.translatable("arm.create_refinery_n_reactors.status." + state));
        if (!tool().isEmpty()) tooltip.add(Component.translatable("arm.create_refinery_n_reactors.tool", tool().getHoverName()));
        if (workRecipe != null && phase != IDLE && phase != CARRY)
            tooltip.add(Component.translatable("arm.create_refinery_n_reactors.step", workRecipe.result().getHoverName(), workStep + 1, workRecipe.steps().size()));
        tooltip.add(Component.translatable("arm.create_refinery_n_reactors.points", inputs.size(), outputs.size()));
        return true;
    }

    // ---------------------------------------------------------------- client

    /** Client side: the tool tip the arm moves to, or null to go back to rest. The synced state and the Ponder scene both come through here. */
    public void aim(byte phase, Vec3 tip) {
        this.phase = phase;
        this.tip = tip;
        target = tip == null ? rest() : ArmPose.toward(worldPosition, tip);
    }

    public ArmPattern displayedPattern() {
        return workRecipe != null && workStep >= 0 && workStep < workRecipe.steps().size()
            ? workRecipe.steps().get(workStep).pattern() : null;
    }

    private void clientTick() {
        ArmPattern pattern = displayedPattern();
        boolean precision = phase == ASSIST || (phase == MOVE || phase == WORK) && (pattern == ArmPattern.CORE_SET || pattern == ArmPattern.WIRE_THREAD);
        float lift = precision && tip != null ? (float) ArmPose.wristClearance(worldPosition, tip, .44) : 0;
        wristLift.chase(lift, .45, LerpedFloat.Chaser.EXP);
        wristLift.tickChaser();
        float[] a = phase != IDLE && tip != null ? target : rest();
        if (tip != null && wristLift.getValue() > .001f)
            a = ArmPose.toward(worldPosition, tip.add(0, wristLift.getValue(), 0));
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
        // A held count above a stack (144 cores) is not a valid saved stack, so the count goes beside it.
        tag.put("held", held.isEmpty() ? new CompoundTag() : held.copyWithCount(1).save(r));
        tag.putInt("heldCount", held.getCount());
        tag.putLongArray("inputs", inputs.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("outputs", outputs.stream().mapToLong(BlockPos::asLong).toArray());
        if (phase == CARRY) tag.putBoolean("carry", true);
        if (!clientPacket) {
            if (partWorkPos != null && !held.isEmpty()) tag.putLong("partWorkPos", partWorkPos.asLong());
            return;
        }
        tag.putByte("shownPhase", phase);
        if (tip != null) {
            tag.putDouble("tx", tip.x);
            tag.putDouble("ty", tip.y);
            tag.putDouble("tz", tip.z);
        }
        if (job != null && workAt != null && (phase == MOVE || phase == WORK)) {
            tag.putString("workRecipe", job.id().toString());
            tag.putInt("workStep", workStep);
            tag.putFloat("workProgress", phase == WORK ? progress : 0);
            tag.putDouble("wx", workAt.x);
            tag.putDouble("wy", workAt.y);
            tag.putDouble("wz", workAt.z);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        toolSlot.deserializeNBT(r, tag.getCompound("tools"));
        held = ItemStack.parseOptional(r, tag.getCompound("held"));
        if (!held.isEmpty() && tag.contains("heldCount")) held = held.copyWithCount(Math.max(1, tag.getInt("heldCount")));
        inputs.clear();
        outputs.clear();
        for (long l : tag.getLongArray("inputs")) inputs.add(BlockPos.of(l));
        for (long l : tag.getLongArray("outputs")) outputs.add(BlockPos.of(l));
        if (!clientPacket) {
            // belt work is not saved: parts held go back, a carried product is carried on
            phase = tag.getBoolean("carry") && !held.isEmpty() ? CARRY : IDLE;
            partWorkPos = tag.contains("partWorkPos") ? BlockPos.of(tag.getLong("partWorkPos")) : null;
            moveLeft = 12;
            return;
        }
        aim(tag.getByte("shownPhase"), tag.contains("tx") ? new Vec3(tag.getDouble("tx"), tag.getDouble("ty"), tag.getDouble("tz")) : null);
        ResourceLocation id = tag.contains("workRecipe") ? ResourceLocation.tryParse(tag.getString("workRecipe")) : null;
        workRecipe = id == null || level == null ? null : level.getRecipeManager().byKey(id).map(h -> h.value() instanceof ArmRecipe a ? a : null).orElse(null);
        workStep = tag.getInt("workStep");
        progress = tag.getFloat("workProgress");
        workAt = workRecipe == null ? null : new Vec3(tag.getDouble("wx"), tag.getDouble("wy"), tag.getDouble("wz"));
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition).inflate(RANGE + 1);
    }
}
