package net.createplant.block;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createplant.block.entity.WetProcessBlockEntity;
import net.createplant.registry.CEWetProcessing;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

public final class WetProcessDriveBlock extends KineticBlock implements IBE<WetProcessBlockEntity>, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public WetProcessDriveBlock(Properties properties) {
        super(properties);registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder);builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return face == Direction.UP; }
    @Override public Class<WetProcessBlockEntity> getBlockEntityClass() { return WetProcessBlockEntity.class; }
    @Override public BlockEntityType<? extends WetProcessBlockEntity> getBlockEntityType() { return CEWetProcessing.DRIVE_ENTITY.get(); }
    @Override protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        var drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof WetProcessBlockEntity be)
            for (var stack : drops) if (stack.is(asItem())) {
                var tag = new net.minecraft.nbt.CompoundTag();be.writeContents(tag, be.getLevel().registryAccess());
                stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            }
        return drops;
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state != next && level.getBlockEntity(pos) instanceof WetProcessBlockEntity be) be.release();
        super.onRemove(state, level, pos, next, moving);
    }
}
