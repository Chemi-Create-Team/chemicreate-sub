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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The jig knows which job is open, which arm has reserved it and how far it is. Every second it checks that loaded arms still hold
 * their steps so interrupted work can resume from a save and removed arms cannot block the assembly.
 * Everything, progress included, goes to the client in the update tag, which the overlay renderer reads.
 */
public class AssemblyJigBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public static final byte PENDING = 0, RESERVED = 1, DONE = 2;
    public static final int FINISH_DELAY = 40, SYNC_EVERY = 2;

    public final FluidTank tank = new FluidTank(1000) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    public ArmRecipe recipe;
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

    public void setRecipe(ArmRecipe r) {
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
        for (int k = 0; k < all.size(); k++) if (recipe != null && all.get(k).id().equals(recipe.id())) i = k;
        setRecipe(all.get((i + 1) % all.size()));
        return true;
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
        int stage = currentStage();
        if (stage < 0) return -1;
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < status.length; i++) {
            Step s = recipe.steps().get(i);
            if (status[i] != PENDING || s.stage() != stage || s.tool() != tool) continue;
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

    /** Sets the block of a placing step. Only into air; returns false, changing nothing, otherwise. */
    public boolean place(int step) {
        Step s = recipe.steps().get(step);
        BlockPos p = cell(step);
        if (!level.getBlockState(p).isAir()) return false;
        level.setBlock(p, s.block().defaultBlockState(), 3);
        level.playSound(null, p, s.block().defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 1f);
        complete(step);
        return true;
    }

    /** A cell's own fluid: whatever the spray nozzle may take when its arm has none. */
    public int drawFluid(int amount, boolean simulate) {
        return tank.drain(amount, simulate ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).getAmount();
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
        if (allDone() && now - lastDone >= FINISH_DELAY) finish();
        if (dirty && --syncIn <= 0) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            dirty = false;
            syncIn = SYNC_EVERY;
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
            if (s.place() && level.getBlockState(p).is(s.block())) level.removeBlock(p, false);
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
        setRecipe(recipe.id() != null && ArmRecipes.get(recipe.id()) != null ? ArmRecipes.get(recipe.id()) : recipe);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        if (recipe == null) {
            tooltip.add(Component.translatable("arm.create_refinery_n_reactors.jig.none"));
            return true;
        }
        int done = 0, working = 0;
        for (byte b : status) {
            if (b == DONE) done++;
            else if (b == RESERVED) working++;
        }
        tooltip.add(Component.translatable("arm.create_refinery_n_reactors.jig.recipe", AssemblyJigBlock.name(recipe)));
        tooltip.add(Component.translatable("arm.create_refinery_n_reactors.jig.stage", Math.max(0, currentStage() + 1), recipe.stages()));
        tooltip.add(Component.translatable("arm.create_refinery_n_reactors.jig.steps", done, status.length, working));
        tooltip.add(Component.translatable("arm.create_refinery_n_reactors.jig.tank", tank.getFluidAmount(), tank.getCapacity()));
        return true;
    }

    // ------------------------------------------------------------------ data

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        tag.put("tank", tank.writeToNBT(r, new CompoundTag()));
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
        tank.readFromNBT(r, tag.getCompound("tank"));
        if (!tag.contains("recipe")) { recipe = null; status = new byte[0]; progress = new float[0]; doneAt = new long[0]; reserved = new BlockPos[0]; return; }
        ArmRecipe saved = ArmRecipe.fromTag(tag.getCompound("recipe"));
        // a data pack may have changed the recipe since it was saved; the loaded one wins as long as it has the same steps
        ArmRecipe current = ArmRecipes.get(saved.id());
        recipe = current != null && current.steps().size() == saved.steps().size() ? current : saved;
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
