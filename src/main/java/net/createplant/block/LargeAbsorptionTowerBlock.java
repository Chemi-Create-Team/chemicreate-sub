package net.createplant.block;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.AbsorptionTowerBlockEntity;
import net.createplant.block.entity.StackedReactorBlockEntity;
import net.createplant.registry.CEReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** One octagonal storey of the large absorption tower, placed as a whole 3 × 3 ring like the extraction column's storeys. */
public class LargeAbsorptionTowerBlock extends ExtractionColumnSectionBlock implements IBE<AbsorptionTowerBlockEntity>, IRotate {
    public LargeAbsorptionTowerBlock(Properties properties) { super(properties); }

    @Override protected BlockState ringCell(int x, int z) {
        return CEReactor.LARGE_ABSORPTION_RING.get().defaultBlockState().setValue(ExtractionColumnStructureBlock.FACING, ring(x, z).getValue(ExtractionColumnStructureBlock.FACING));
    }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(this)) StackedReactorBlockEntity.changed(level, pos, this);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!newState.is(this) && level.getBlockEntity(pos) instanceof AbsorptionTowerBlockEntity storey && storey.bottom()) storey.dismantle();
        super.onRemove(state, level, pos, newState, moved);
        if (!newState.is(this)) StackedReactorBlockEntity.changed(level, pos, this);
    }

    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }

    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return false; }

    @Override public Class<AbsorptionTowerBlockEntity> getBlockEntityClass() { return AbsorptionTowerBlockEntity.class; }

    @Override public BlockEntityType<? extends AbsorptionTowerBlockEntity> getBlockEntityType() { return CEReactor.ABSORPTION_ENTITY.get(); }
}
