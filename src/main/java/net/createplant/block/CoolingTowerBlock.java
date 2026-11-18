package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.CoolingTowerBlockEntity;
import net.createplant.registry.CEPlantSystems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CoolingTowerBlock extends Block implements IBE<CoolingTowerBlockEntity> {
    public CoolingTowerBlock(Properties properties) { super(properties); }
    @Override public Class<CoolingTowerBlockEntity> getBlockEntityClass() { return CoolingTowerBlockEntity.class; }
    @Override public BlockEntityType<? extends CoolingTowerBlockEntity> getBlockEntityType() { return CEPlantSystems.TOWER_ENTITY.get(); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!next.is(state.getBlock()) && level.getBlockEntity(pos) instanceof CoolingTowerBlockEntity tower) tower.release();
        super.onRemove(state, level, pos, next, moved);
    }
}
