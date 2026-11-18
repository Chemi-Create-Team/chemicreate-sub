package net.createplant.block;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import net.createplant.block.entity.PulsationTubeBlockEntity;
import net.createplant.registry.CETransport;
import net.minecraft.core.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** A gas reservoir in the pipe run, with one shared inventory along any of the three axes. */
public class PulsationTubeBlock extends PlantFluidTankBlock {
    public static final EnumProperty<Direction.Axis> AXIS=BlockStateProperties.AXIS;
    public PulsationTubeBlock(Properties properties){
        super(properties,()->CETransport.GAS_RECEIVER_ENTITY.get(),CETransport::gas);
        registerDefaultState(defaultBlockState().setValue(AXIS,Direction.Axis.Y).setValue(SHAPE,Shape.PLAIN));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(AXIS);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var against=context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        var axis=against.is(this) && (context.getPlayer()==null || !context.getPlayer().isShiftKeyDown()) ? against.getValue(AXIS) : context.getClickedFace().getAxis();
        return defaultBlockState().setValue(AXIS,axis);
    }
    @Override public InteractionResult onWrenched(BlockState state,UseOnContext context){
        if(!context.getLevel().isClientSide){
            if(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof PulsationTubeBlockEntity be)ConnectivityHandler.splitMulti(be);
            var next=Direction.Axis.values()[(state.getValue(AXIS).ordinal()+1)%3];
            context.getLevel().setBlock(context.getClickedPos(),state.setValue(AXIS,next),3);
        }
        return InteractionResult.SUCCESS;
    }
    @Override public BlockState rotate(BlockState state,Rotation rotation){return state.setValue(AXIS,rotation.rotate(Direction.fromAxisAndDirection(state.getValue(AXIS),Direction.AxisDirection.POSITIVE)).getAxis());}
    @Override public BlockState mirror(BlockState state,Mirror mirror){return state;}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        return switch(state.getValue(AXIS)){case X->Block.box(0,2,2,16,14,14);case Y->Block.box(2,0,2,14,16,14);case Z->Block.box(2,2,0,14,14,16);};
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
}
