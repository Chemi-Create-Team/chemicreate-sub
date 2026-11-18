package net.createplant.block;

import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.PipeGaugeBlockEntity;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.registry.CETransport;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A thermometer, pressure gauge or flow meter set into a pipe run: what flows through passes unchanged and is read on Create's gauge faces. */
public class PipeGaugeBlock extends AxisPipeBlock implements IBE<PipeGaugeBlockEntity> {
    public enum Kind { TEMPERATURE, PRESSURE, FLOW }
    public final Kind kind;
    public PipeGaugeBlock(Properties properties,Kind kind) { super(properties);this.kind=kind; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state=super.getStateForPlacement(context);
        if (state!=null && kind==Kind.PRESSURE && context.getClickedFace().getAxis().isHorizontal()
            && context.getLevel().getBlockEntity(context.getClickedPos().relative(context.getClickedFace().getOpposite())) instanceof LiddedVesselBlockEntity)
            return state.setValue(AXIS,context.getClickedFace().getAxis());
        return state;
    }
    /** A pressure tap touches an outside vessel wall along the same axis as its pipe stem. */
    private Direction vesselSide(BlockGetter level,BlockPos pos,BlockState state) {
        if (kind!=Kind.PRESSURE || !state.getValue(AXIS).isHorizontal()) return null;
        for (Direction side:Direction.Plane.HORIZONTAL) {
            if (side.getAxis()!=state.getValue(AXIS)) continue;
            BlockPos at=pos.relative(side);
            if (level instanceof Level world && !world.hasChunkAt(at)) continue;
            if (level.getBlockEntity(at) instanceof LiddedVesselBlockEntity vessel && vessel.getControllerBE()!=null) return side;
        }
        return null;
    }
    public LiddedVesselBlockEntity attachedVessel(BlockGetter level,BlockPos pos,BlockState state) {
        Direction side=vesselSide(level,pos,state);
        return side!=null && level.getBlockEntity(pos.relative(side)) instanceof LiddedVesselBlockEntity vessel ? vessel.getControllerBE() : null;
    }
    /** An inline pipe retains its perpendicular heads; an exposed tap has one head looking away from the wall. */
    public Direction mountedFace(BlockGetter level,BlockPos pos,BlockState state) {
        Direction side=vesselSide(level,pos,state);
        if (side==null) return null;
        Direction outward=side.getOpposite();BlockPos at=pos.relative(outward);
        if (level instanceof Level world && !world.hasChunkAt(at)) return outward;
        return com.simibubi.create.content.fluids.FluidPropagator.getPipe(level,at)!=null
            || level.getBlockState(at).getBlock() instanceof com.simibubi.create.content.fluids.pump.PumpBlock ? null : outward;
    }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> Block.box(0,2,2,16,14,14);
            case Y -> Block.box(2,0,2,14,16,14);
            case Z -> Block.box(2,2,0,14,14,16);
        };
    }
    /** Faces read like Create's gauges: on the horizontal sides across the pipe, where nothing covers them. */
    public boolean shouldRenderHeadOnFace(BlockGetter level,BlockPos pos,BlockState state,Direction face) {
        Direction mounted=mountedFace(level,pos,state);
        return (mounted!=null ? face==mounted : face.getAxis().isHorizontal() && face.getAxis()!=state.getValue(AXIS))
            && Block.shouldRenderFace(state,level,pos,face,pos.relative(face));
    }
    @Override protected boolean isSignalSource(BlockState state) { return true; }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return kind!=Kind.FLOW; }
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PipeGaugeBlockEntity gauge ? gauge.analogSignal() : 0;
    }
    /** The alarm contact powers the four sides off the pipe while its condition holds. */
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side) {
        return side.getAxis()!=state.getValue(AXIS) && level.getBlockEntity(pos) instanceof PipeGaugeBlockEntity gauge && gauge.alarm() ? 15 : 0;
    }
    @Override public Class<PipeGaugeBlockEntity> getBlockEntityClass() { return PipeGaugeBlockEntity.class; }
    @Override public BlockEntityType<? extends PipeGaugeBlockEntity> getBlockEntityType() { return CETransport.PIPE_GAUGE_ENTITY.get(); }
    @Override public InteractionResult onWrenched(BlockState state,UseOnContext context) { return InteractionResult.PASS; }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,InteractionHand hand,BlockHitResult hit) { return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; }
    @Override public ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult target,LevelReader level,BlockPos pos,net.minecraft.world.entity.player.Player player) { return new ItemStack(this); }
}
