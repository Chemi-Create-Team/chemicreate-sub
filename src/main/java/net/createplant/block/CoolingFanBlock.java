package net.createplant.block;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.CoolingFanBlockEntity;
import net.createplant.registry.CEProcessBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Center of a 3x3 fan cell on top of a radiator bank; driven from below through the formed radiator column and orients the eight shrouds around it. */
public class CoolingFanBlock extends KineticBlock implements IBE<CoolingFanBlockEntity> {
    private static final VoxelShape SHAPE = Shapes.or(box(4,0,4,12,2,6),box(4,0,10,12,2,12),box(4,0,6,6,2,10),box(10,0,6,12,2,10),box(0,14,0,16,16,16));
    public CoolingFanBlock(Properties properties) { super(properties); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return face==Direction.DOWN; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state,level,pos,oldState,moving);
        if (!oldState.is(this)) orientShrouds(level,pos,true);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!next.is(this)) orientShrouds(level,pos,false);
        super.onRemove(state,level,pos,next,moving);
    }
    private static void orientShrouds(Level level, BlockPos hub, boolean attach) {
        for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++) {
            BlockPos pos=hub.offset(dx,0,dz);
            BlockState shroud=level.getBlockState(pos);
            if ((dx!=0 || dz!=0) && shroud.getBlock() instanceof CoolingFanShroudBlock)
                level.setBlock(pos,attach ? CoolingFanShroudBlock.around(shroud,-dx,-dz) : shroud.setValue(CoolingFanShroudBlock.PART,CoolingFanShroudBlock.Part.NONE),3);
        }
    }
    @Override public Class<CoolingFanBlockEntity> getBlockEntityClass() { return CoolingFanBlockEntity.class; }
    @Override public BlockEntityType<? extends CoolingFanBlockEntity> getBlockEntityType() { return CEProcessBlocks.COOLING_FAN_ENTITY.get(); }
}
