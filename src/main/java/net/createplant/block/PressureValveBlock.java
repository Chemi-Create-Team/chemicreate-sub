package net.createplant.block;

import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.PressureValveBlockEntity;
import net.createplant.registry.CEPressure;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.item.context.BlockPlaceContext;

public class PressureValveBlock extends AxisPipeBlock implements IBE<PressureValveBlockEntity> {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public PressureValveBlock(Properties p) {super(p);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(AXIS,Direction.Axis.Z));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {super.createBlockStateDefinition(b);b.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {var d=c.getHorizontalDirection().getOpposite();return defaultBlockState().setValue(FACING,d).setValue(AXIS,d.getAxis());}
    @Override public BlockState rotate(BlockState s,Rotation r) {var d=r.rotate(s.getValue(FACING));return s.setValue(FACING,d).setValue(AXIS,d.getAxis());}
    @Override public BlockState mirror(BlockState s,Mirror m) {return rotate(s,m.getRotation(s.getValue(FACING)));}
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState s,net.minecraft.world.level.BlockGetter l,BlockPos p,net.minecraft.world.phys.shapes.CollisionContext c) {return s.getValue(AXIS)==Direction.Axis.X ? Block.box(0,0,2,16,16,14) : Block.box(2,0,0,14,16,16);}
    @Override public Class<PressureValveBlockEntity> getBlockEntityClass() {return PressureValveBlockEntity.class;}
    @Override public BlockEntityType<? extends PressureValveBlockEntity> getBlockEntityType() {return CEPressure.VALVE_ENTITY.get();}
    @Override public net.minecraft.world.InteractionResult onWrenched(BlockState s,net.minecraft.world.item.context.UseOnContext c) {if(c.getClickedFace().getAxis()!=s.getValue(AXIS)) return net.minecraft.world.InteractionResult.PASS;
        if(!c.getLevel().isClientSide) {c.getLevel().setBlock(c.getClickedPos(),s.setValue(FACING,s.getValue(FACING).getOpposite()),3);com.simibubi.create.content.fluids.FluidPropagator.propagateChangedPipe(c.getLevel(),c.getClickedPos(),s);}
        return net.minecraft.world.InteractionResult.SUCCESS;}
}
