package net.createplant.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.ReciprocatingCylinderBlockEntity;
import net.createplant.registry.CEReciprocating;
import net.createplant.thermal.FluidRoutes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The gas chamber beside the compressor: suction above, discharge below. */
public class ReciprocatingCylinderBlock extends Block implements IBE<ReciprocatingCylinderBlockEntity>, IWrenchable {
    public ReciprocatingCylinderBlock(Properties properties) { super(properties); }

    @Override public Class<ReciprocatingCylinderBlockEntity> getBlockEntityClass() { return ReciprocatingCylinderBlockEntity.class; }

    @Override public BlockEntityType<? extends ReciprocatingCylinderBlockEntity> getBlockEntityType() { return CEReciprocating.CYLINDER_ENTITY.get(); }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        FluidRoutes.changed(level);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ReciprocatingCylinderBlockEntity cylinder) cylinder.release();
        super.onRemove(state, level, pos, next, moving);
        FluidRoutes.changed(level);
    }
}
