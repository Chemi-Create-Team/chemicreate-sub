package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.WetProcessPartBlockEntity;
import net.createplant.registry.CEWetProcessing;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

public final class WetProcessPartBlock extends Block implements IBE<WetProcessPartBlockEntity>, com.simibubi.create.content.equipment.wrench.IWrenchable {
    public enum Role {
        FEED("clarifier_feed_port"),
        OVERFLOW("overflow_weir"), UNDERFLOW("underflow_drain");
        public final String id;
        Role(String id) { this.id = id; }
    }
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public final Role role;
    public WetProcessPartBlock(Role role, Properties properties) { super(properties);this.role = role;registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, role == Role.FEED ? Direction.DOWN : context.getClickedFace()); }
    @Override public Class<WetProcessPartBlockEntity> getBlockEntityClass() { return WetProcessPartBlockEntity.class; }
    @Override public BlockEntityType<? extends WetProcessPartBlockEntity> getBlockEntityType() { return CEWetProcessing.PART_ENTITY.get(); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return null; }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state != next && level.getBlockEntity(pos) instanceof WetProcessPartBlockEntity part) part.invalidateOwner();
        super.onRemove(state, level, pos, next, moving);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbour, boolean moving) {
        super.neighborChanged(state, level, pos, block, neighbour, moving);
        if (level.getBlockEntity(pos) instanceof WetProcessPartBlockEntity part) part.structureChanged(neighbour);
    }
}
