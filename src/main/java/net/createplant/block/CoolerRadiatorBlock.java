package net.createplant.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.registry.CEProcessBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;

/** Existing fin-fan geometry, formed and controlled by the radiator bank itself. */
public class CoolerRadiatorBlock extends KineticBlock implements IBE<net.createplant.block.entity.RadiatorBlockEntity> {
    public enum Section implements StringRepresentable {
        NONE, BOTTOM, MIDDLE, TOP;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final EnumProperty<Section> SECTION = EnumProperty.create("section",Section.class);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty PORT = BooleanProperty.create("port");
    public static final BooleanProperty DRIVE = BooleanProperty.create("drive");
    public static final BooleanProperty OUTLET = BooleanProperty.create("outlet");
    public CoolerRadiatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PORT,false).setValue(SECTION,Section.NONE).setValue(FACING,Direction.SOUTH).setValue(DRIVE,false).setValue(OUTLET,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(SECTION,FACING,DRIVE,OUTLET,PORT); }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
    }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return state.getValue(DRIVE) && face.getAxis()==Direction.Axis.Y;
    }
    @Override protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
        return super.areStatesKineticallyEquivalent(oldState,newState) && oldState.getValue(DRIVE)==newState.getValue(DRIVE);
    }
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block changed,BlockPos from,boolean moving) {
        super.neighborChanged(state,level,pos,changed,from,moving);
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof net.createplant.block.entity.RadiatorBlockEntity radiator) radiator.partChanged(from);
    }
    @Override public InteractionResult onWrenched(BlockState state,UseOnContext context) { return InteractionResult.PASS; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return IBE.super.newBlockEntity(pos,state); }
    @Override public Class<net.createplant.block.entity.RadiatorBlockEntity> getBlockEntityClass() { return net.createplant.block.entity.RadiatorBlockEntity.class; }
    @Override public BlockEntityType<? extends net.createplant.block.entity.RadiatorBlockEntity> getBlockEntityType() { return CEProcessBlocks.COOLER_RADIATOR_DRIVE.get(); }
}
