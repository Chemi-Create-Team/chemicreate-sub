package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.FuelPoolBlockEntity;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** A steel-lined floor tile and four storage positions in an open fuel pool. */
public class FuelPoolBlock extends net.minecraft.world.level.block.Block implements IBE<FuelPoolBlockEntity> {
    public FuelPoolBlock(Properties properties) { super(properties); }

    @Override public Class<FuelPoolBlockEntity> getBlockEntityClass() { return FuelPoolBlockEntity.class; }
    @Override public BlockEntityType<? extends FuelPoolBlockEntity> getBlockEntityType() { return CENuclear.FUEL_POOL_ENTITY.get(); }

    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return box(0, 0, 0, 16, 4, 16);
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        net.createplant.nuclear.FuelPoolStructure.invalidate(level);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!newState.is(state.getBlock())) withBlockEntityDo(level, pos, FuelPoolBlockEntity::dismantle);
        IBE.onRemove(state, level, pos, newState);
    }
}
