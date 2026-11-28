package net.createplant.atomics.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.atomics.block.entity.FuelChannelBlockEntity;
import net.createplant.atomics.nuclear.ReactorCores;
import net.createplant.atomics.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** One block of fuel channels: a bundle of four fuel rods in graphite, cooled by the water of its column. */
public class FuelChannelBlock extends Block implements IBE<FuelChannelBlockEntity> {
    public FuelChannelBlock(Properties properties) { super(properties); }

    @Override public Class<FuelChannelBlockEntity> getBlockEntityClass() { return FuelChannelBlockEntity.class; }

    /** The block is mostly graphite: once alight it keeps burning like netherrack until it is put out. */
    @Override public boolean isFireSource(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos, net.minecraft.core.Direction direction) {
        return direction == net.minecraft.core.Direction.UP;
    }
    @Override public BlockEntityType<? extends FuelChannelBlockEntity> getBlockEntityType() { return CENuclear.FUEL_CHANNEL_ENTITY.get(); }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!oldState.is(state.getBlock())) ReactorCores.changed(level, pos);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!newState.is(state.getBlock())) withBlockEntityDo(level, pos, FuelChannelBlockEntity::dismantle);
        IBE.onRemove(state, level, pos, newState);
        if (!newState.is(state.getBlock())) ReactorCores.changed(level, pos);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide()) ReactorCores.signalChanged(level, pos);
    }
}
