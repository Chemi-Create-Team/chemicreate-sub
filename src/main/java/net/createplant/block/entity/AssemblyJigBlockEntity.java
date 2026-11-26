package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.List;
import net.createplant.arm.ArmRecipe.Step;
import net.createplant.arm.ArmRecipe;
import net.createplant.arm.ArmRecipes;
import net.createplant.arm.ArmTool;
import net.createplant.block.AssemblyJigBlock;
import net.createplant.registry.CEArm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The jig knows which job is open, which arm has reserved it and how far it is. Every second it checks that loaded arms still hold
 * their steps so interrupted work can resume from a save and removed arms cannot block the assembly.
 * Everything, progress included, goes to the client in the update tag, which the overlay renderer reads.
 */
public class AssemblyJigBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public static final byte PENDING = 0, RESERVED = 1, DONE = 2;
    public static final int FINISH_DELAY = 40, SYNC_EVERY = 2;

    public ArmRecipe recipe;
    /**
     * Automatic: while no step has started, the jig makes whatever the arms around it can make from what is in their places to
     * take from (or, for an arm with none chosen, the containers it reaches). A wrench pins one recipe instead.
     */
    public boolean auto = true;
    private int autoIn;
    public byte[] status = new byte[0];
    public float[] progress = new float[0];
    public long[] doneAt = new long[0];
    /** Client only: progress as drawn, which closes in on the synced value so the overlay moves smoothly between updates. */
    public float[] shown = new float[0];
    private BlockPos[] reserved = new BlockPos[0];
    private long lastDone;
    private boolean dirty;
    private int syncIn;

    public AssemblyJigBlockEntity(BlockPos pos, BlockState state) {
        super(CEArm.JIG_ENTITY.get(), pos, state);
    }

    // ----------------------------------------------------------------- recipe

    /** Pins a recipe (the wrench, tests); automatic choice stops. */
    public void setRecipe(ArmRecipe r) {
        auto = false;
        select(r);
    }

    private void select(ArmRecipe r) {
        recipe = r;
        int n = r == null ? 0 : r.steps().size();
        status = new byte[n];
        progress = new float[n];
        doneAt = new long[n];
        shown = new float[n];
        reserved = new BlockPos[n];
        changed();
    }

    /** Switches to the next loaded recipe. Refused, returning false, once any step is under way. */
    public boolean cycleRecipe() {
        for (byte b : status) if (b != PENDING) return false;
        List<ArmRecipe> all = ArmRecipes.list();
        if (all.isEmpty()) return false;
        int i = -1;
        if (!auto) for (int k = 0; k < all.size(); k++) if (recipe != null && all.get(k).id().equals(recipe.id())) i = k;
        if (i == all.size() - 1) {
            // after the last one comes automatic again
            auto = true;
            autoIn = 0;
            select(null);
        } else setRecipe(all.get(i + 1));
        return true;
    }

    /**
     * Picks the recipe to make from what the nearby arms can do. A recipe qualifies when every step has an arm with its tool that
     * reaches all of the step's path (and a step with a helper a second bare arm that does), and the arms of each tool can take, from the places they take from, all the material their
     * steps use (each stack counted once, so the two halves of a large chest are not counted twice). Of those, the one using the
     * most kinds of material wins, so a sheet with cores and wire makes core memory rather than a beam.
     * ponytail: scans the arms around and their sources once a second per idle jig, and counts a chest shared by two tools for
     * both; fine for a few lines, a shared stock per item would be exact.
     */
    private void autoPick() {
        for (byte b : status) if (b != PENDING) return; // a job under way is kept
        List<IndustrialArmBlockEntity> arms = new java.util.ArrayList<>();
        int range = IndustrialArmBlockEntity.RANGE;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-range, -range, -range), worldPosition.offset(range, range, range)))
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof IndustrialArmBlockEntity arm) arms.add(arm);
        java.util.Map<ArmTool, java.util.Map<net.minecraft.world.item.Item, Integer>> stockOf = new java.util.EnumMap<>(ArmTool.class);
        ArmRecipe best = null;
        int bestKinds = -1, bestTotal = -1;
        for (ArmRecipe r : ArmRecipes.list()) {
            java.util.Map<ArmTool, java.util.Map<net.minecraft.world.item.Item, Integer>> need = new java.util.EnumMap<>(ArmTool.class);
            boolean ok = true;
            for (Step s : r.steps()) {
                // the step's own arm, and for threading a second bare arm to hold the wire
                long leads = arms.stream().filter(a -> a.toolType() == s.tool() && a.reachesStep(worldPosition, r, s)).count();
                long hands = arms.stream().filter(a -> a.toolType() == ArmTool.HAND && a.reachesStep(worldPosition, r, s)).count();
                ok &= leads >= 1 && (s.helpers() == 0 || hands >= (s.tool() == ArmTool.HAND ? 2 : 1));
                if (s.material() != null) need.computeIfAbsent(s.tool(), t -> new java.util.HashMap<>()).merge(s.material(), s.materialCount(), Integer::sum);
            }
            for (var e : need.entrySet()) {
                var stock = stockOf.computeIfAbsent(e.getKey(), t -> stock(arms, t));
                for (var m : e.getValue().entrySet()) ok &= stock.getOrDefault(m.getKey(), 0) >= m.getValue();
            }
            if (!ok) continue;
            int kinds = 0, total = 0;
            java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
            for (var m : need.values()) for (var e : m.entrySet()) { if (seen.add(e.getKey())) kinds++; total += e.getValue(); }
            if (kinds > bestKinds || kinds == bestKinds && total > bestTotal) { best = r; bestKinds = kinds; bestTotal = total; }
        }
        if (best == null ? recipe != null : recipe == null || !best.id().equals(recipe.id())) select(best);
    }

    /** What the arms with this tool can take for this jig, each stack counted once. */
    private java.util.Map<net.minecraft.world.item.Item, Integer> stock(List<IndustrialArmBlockEntity> arms, ArmTool tool) {
        java.util.Set<BlockPos> sources = new java.util.LinkedHashSet<>();
        for (IndustrialArmBlockEntity a : arms) if (a.toolType() == tool) a.addSources(sources, worldPosition);
        java.util.Map<net.minecraft.world.item.Item, Integer> stock = new java.util.HashMap<>();
        java.util.Set<ItemStack> counted = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (BlockPos p : sources) {
            var h = IndustrialArmBlockEntity.items(level, p);
            if (h == null) continue;
            for (int slot = 0; slot < h.getSlots(); slot++) {
                ItemStack st = h.getStackInSlot(slot);
                if (!st.isEmpty() && counted.add(st)) stock.merge(st.getItem(), st.getCount(), Integer::sum);
            }
        }
        return stock;
    }

    public BlockPos cell(int step) {
        return worldPosition.above().offset(recipe.steps().get(step).rel());
    }

    public Vec3 cellOrigin(int step) {
        BlockPos c = cell(step);
        return new Vec3(c.getX(), c.getY(), c.getZ());
    }

    // ------------------------------------------------------------ scheduling

    /** The stage whose steps may be worked now: the first with a step not done. -1 when everything is done or there is no recipe. */
    public int currentStage() {
        if (recipe == null) return -1;
        int stage = Integer.MAX_VALUE;
        for (int i = 0; i < status.length; i++) if (status[i] != DONE) stage = Math.min(stage, recipe.steps().get(i).stage());
        return stage == Integer.MAX_VALUE ? -1 : stage;
    }

    public boolean allDone() {
        if (recipe == null || status.length == 0) return false;
        for (byte b : status) if (b != DONE) return false;
        return true;
    }

    /** Gives the arm the open step of its tool that is nearest to it, or -1. A step is given to one arm only. */
    public int reserve(BlockPos arm, ArmTool tool, Vec3 armCenter) {
        return reserve(arm, tool, armCenter, step -> true);
    }

    /** As {@link #reserve(BlockPos, ArmTool, Vec3)}, among the steps {@code reachable} accepts. */
    public int reserve(BlockPos arm, ArmTool tool, Vec3 armCenter, java.util.function.IntPredicate reachable) {
        int stage = currentStage();
        if (stage < 0) return -1;
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < status.length; i++) {
            Step s = recipe.steps().get(i);
            if (status[i] != PENDING || s.stage() != stage || s.tool() != tool || !reachable.test(i)) continue;
            double d = armCenter.distanceToSqr(Vec3.atCenterOf(cell(i)));
            if (d < bestDistance) { bestDistance = d; best = i; }
        }
        if (best >= 0) {
            status[best] = RESERVED;
            reserved[best] = arm.immutable();
            progress[best] = 0;
            changed();
        }
        return best;
    }

    /** The arm gives a step back unfinished. */
    public void release(BlockPos arm, int step) {
        if (!reservedBy(arm, step)) return;
        status[step] = PENDING;
        reserved[step] = null;
        progress[step] = 0;
        changed();
    }

    public boolean reservedBy(BlockPos arm, int step) {
        return step >= 0 && step < status.length && status[step] == RESERVED && arm.equals(reserved[step]);
    }

    public void setProgress(int step, float p) {
        if (step < 0 || step >= progress.length) return;
        progress[step] = p;
        dirty = true;
    }

    public void complete(int step) {
        status[step] = DONE;
        progress[step] = 1;
        reserved[step] = null;
        doneAt[step] = level.getGameTime();
        lastDone = level.getGameTime();
        changed();
    }

    /** Whether a placing step's block stands in its cell (so a block put back by hand counts too). */
    public boolean standing(int step) {
        Step s = recipe.steps().get(step);
        return s.place() && !s.lay() && level.getBlockState(cell(step)).is(s.block());
    }

    /** Sets the block of a placing step, or lays its item. Only into air; returns false, changing nothing, otherwise. */
    public boolean place(int step) {
        Step s = recipe.steps().get(step);
        BlockPos p = cell(step);
        if (s.lay()) {
            level.playSound(null, p, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, .8f, 1.2f);
            complete(step);
            return true;
        }
        if (!level.getBlockState(p).isAir()) return false;
        level.setBlock(p, s.block().defaultBlockState(), 3);
        level.playSound(null, p, s.block().defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 1f);
        complete(step);
        return true;
    }

    private void changed() {
        setChanged();
        dirty = true;
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        if (level.isClientSide) {
            if (shown.length != progress.length) shown = progress.clone();
            for (int i = 0; i < shown.length; i++) {
                float t = status[i] == DONE ? 1 : progress[i];
                shown[i] = t < shown[i] ? t : shown[i] + (t - shown[i]) * .5f;
            }
            return;
        }
        long now = level.getGameTime();
        if (now % 20 == 0) releaseOrphans();
        checkPlaced();
        if (auto && --autoIn <= 0) {
            autoIn = 20;
            autoPick();
        }
        if (allDone() && now - lastDone >= FINISH_DELAY) finish();
        if (dirty && --syncIn <= 0) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            dirty = false;
            syncIn = SYNC_EVERY;
        }
    }

    /**
     * A placed block that is gone takes its step back, and with it all work of the later stages, which was done on the frame
     * that is no longer whole; so a product only comes out of a frame that is still standing.
     */
    private void checkPlaced() {
        if (recipe == null) return;
        int broken = Integer.MAX_VALUE;
        for (int i = 0; i < status.length; i++) {
            Step s = recipe.steps().get(i);
            if (!s.place() || s.lay()) continue;
            if (status[i] == DONE && !standing(i)) {
                broken = Math.min(broken, s.stage());
                status[i] = PENDING;
                progress[i] = 0;
                changed();
            } else if (status[i] == PENDING && standing(i)) {
                complete(i); // the block was put back by hand
            }
        }
        if (broken == Integer.MAX_VALUE) return;
        for (int i = 0; i < status.length; i++) {
            if (recipe.steps().get(i).stage() <= broken || status[i] == PENDING || standing(i)) continue; // blocks still standing stay done
            status[i] = PENDING; // a reserving arm sees the step is no longer its own and gives up
            reserved[i] = null;
            progress[i] = 0;
        }
    }

    private void releaseOrphans() {
        for (int i = 0; i < status.length; i++) {
            if (status[i] != RESERVED) continue;
            BlockPos a = reserved[i];
            if (a == null || level.isLoaded(a) && !(level.getBlockEntity(a) instanceof IndustrialArmBlockEntity arm && arm.holds(worldPosition, i))) {
                status[i] = PENDING;
                reserved[i] = null;
                progress[i] = 0;
                changed();
            }
        }
    }

    /** All stages are done: the temporary blocks vanish, the product appears and steam rises. */
    private void finish() {
        for (int i = 0; i < status.length; i++) {
            Step s = recipe.steps().get(i);
            BlockPos p = cell(i);
            if (standing(i)) level.removeBlock(p, false);
        }
        Vec3 top = Vec3.atBottomCenterOf(worldPosition.above()).add(0, .1, 0);
        ItemEntity item = new ItemEntity(level, top.x, top.y, top.z, recipe.resultStack());
        item.setDeltaMovement(0, .15, 0);
        level.addFreshEntity(item);
        if (level instanceof ServerLevel sl) {
            for (int i = 0; i < status.length; i++) {
                Vec3 c = Vec3.atCenterOf(cell(i));
                sl.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 6, .25, .25, .25, .03);
            }
            sl.sendParticles(ParticleTypes.CLOUD, top.x, top.y, top.z, 20, .3, .2, .3, .05);
        }
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 1f);
        level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, .6f, 1.4f);
        if (auto) {
            select(null); // choose again from what is there now
            autoIn = 0;
        } else select(recipe.id() != null && ArmRecipes.get(recipe.id()) != null ? ArmRecipes.get(recipe.id()) : recipe);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        if (recipe == null) {
            tooltip.add(Component.translatable(auto ? "arm.create_refinery_n_reactors.jig.auto_waiting" : "arm.create_refinery_n_reactors.jig.none"));
            return true;
        }
        int done = 0, working = 0;
        for (byte b : status) {
            if (b == DONE) done++;
            else if (b == RESERVED) working++;
        }
        tooltip.add(Component.translatable(auto ? "arm.create_refinery_n_reactors.jig.auto_recipe" : "arm.create_refinery_n_reactors.jig.recipe", AssemblyJigBlock.name(recipe)));
        tooltip.add(Component.translatable("arm.create_refinery_n_reactors.jig.stage", Math.max(0, currentStage() + 1), recipe.stages()));
        tooltip.add(Component.translatable("arm.create_refinery_n_reactors.jig.steps", done, status.length, working));
        return true;
    }

    // ------------------------------------------------------------------ data

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        tag.putBoolean("auto", auto);
        if (recipe == null) return;
        tag.put("recipe", recipe.toTag());
        tag.putByteArray("status", status);
        long[] owners = new long[reserved.length];
        for (int i = 0; i < reserved.length; i++) owners[i] = reserved[i] == null ? Long.MIN_VALUE : reserved[i].asLong();
        tag.putLongArray("reserved", owners);
        ListTag p = new ListTag();
        for (float f : progress) p.add(net.minecraft.nbt.FloatTag.valueOf(f));
        tag.put("progress", p);
        tag.putLongArray("doneAt", doneAt);
        tag.putLong("lastDone", lastDone);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r);
        auto = tag.contains("auto") ? tag.getBoolean("auto") : !tag.contains("recipe"); // older jigs with a recipe had it chosen by wrench
        if (!tag.contains("recipe")) { recipe = null; status = new byte[0]; progress = new float[0]; doneAt = new long[0]; reserved = new BlockPos[0]; return; }
        ArmRecipe saved = ArmRecipe.fromTag(tag.getCompound("recipe"));
        if (saved == null) { auto = true; recipe = null; status = new byte[0]; progress = new float[0]; doneAt = new long[0]; reserved = new BlockPos[0]; return; }
        // a data pack may have changed the recipe since it was saved: progress only carries over to an identical one,
        // otherwise the saved recipe is finished as it was
        ArmRecipe current = ArmRecipes.get(saved.id());
        recipe = current != null && current.equals(saved) ? current : saved;
        int n = recipe.steps().size();
        byte[] s = tag.getByteArray("status");
        status = s.length == n ? s : new byte[n];
        progress = new float[n];
        ListTag p = tag.getList("progress", Tag.TAG_FLOAT);
        for (int i = 0; i < n && i < p.size(); i++) progress[i] = p.getFloat(i);
        long[] d = tag.getLongArray("doneAt");
        doneAt = d.length == n ? d : new long[n];
        lastDone = tag.getLong("lastDone");
        reserved = new BlockPos[n];
        long[] owners = tag.getLongArray("reserved");
        if (owners.length == n) {
            for (int i = 0; i < n; i++)
                if (status[i] == RESERVED && owners[i] != Long.MIN_VALUE) reserved[i] = BlockPos.of(owners[i]);
        }
        if (shown.length != n) shown = progress.clone();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider r) {
        CompoundTag tag = saveWithoutMetadata(r);
        tag.remove("reserved");
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
