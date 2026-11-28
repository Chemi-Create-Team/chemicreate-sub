package net.createplant.block.entity;

import java.util.Objects;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createplant.block.ReciprocatingCompressorBlock;
import net.createplant.block.ReciprocatingShaftBlock;
import net.createplant.registry.CEReciprocating;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The shaft node a reciprocating compressor loads: it carries the engine's shaft work into the kinetic network. */
public class ReciprocatingShaftBlockEntity extends KineticBlockEntity {
    private BlockPos engine;
    private float load;
    private final float[] rowLoads = new float[ReciprocatingCompressorBlock.MAX_ROWS];
    private final long[] rowTicks = new long[rowLoads.length];

    public ReciprocatingShaftBlockEntity(BlockPos pos, BlockState state) { super(CEReciprocating.SHAFT_ENTITY.get(), pos, state); }

    public BlockPos engine() { return engine; }

    public void bind(BlockPos pos) {
        if (Objects.equals(engine, pos)) return;
        engine = pos;
        setChanged();
    }

    public void setLoad(BlockPos row, float next) {
        if (engine == null) return;
        int index = engine.distManhattan(row);
        if (index >= rowLoads.length) return;
        rowTicks[index] = level.getGameTime();
        if (next == rowLoads[index]) return;
        rowLoads[index] = next;
        updateLoad();
    }

    private void updateLoad() {
        float total = 0;
        for (float row : rowLoads) total += row;
        if (total == load) return;
        load = total;
        if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
    }

    @Override public float calculateStressApplied() { return lastStressApplied = load; }

    /** A shaft whose engine is gone is a plain Create shaft again. */
    @Override public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide || engine == null) return;
        if (level.isLoaded(engine) && !(level.getBlockEntity(engine) instanceof ReciprocatingCompressorBlockEntity)) {
            level.setBlock(worldPosition, ReciprocatingShaftBlock.released(getBlockState()), Block.UPDATE_ALL);
            return;
        }
        if (load == 0) return;
        for (int row = 0; row < rowLoads.length; row++)
            if (level.getGameTime() - rowTicks[row] > 2) rowLoads[row] = 0;
        updateLoad();
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.write(tag, lookup, clientPacket);
        if (engine != null) tag.putLong("Engine", engine.asLong());
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.read(tag, lookup, clientPacket);
        engine = tag.contains("Engine") ? BlockPos.of(tag.getLong("Engine")) : null;
    }
}
