package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.IndustrialDispatchBlockEntity;
import net.createplant.registry.CEIndustrialExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class IndustrialDispatchBlock extends Block implements IBE<IndustrialDispatchBlockEntity> {
    public IndustrialDispatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(BlockStateProperties.TRIGGERED, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.TRIGGERED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction side = context.getClickedFace().getAxis().isHorizontal() ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, side);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(BlockStateProperties.HORIZONTAL_FACING, rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING))); }
    @Override public Class<IndustrialDispatchBlockEntity> getBlockEntityClass() { return IndustrialDispatchBlockEntity.class; }
    @Override public BlockEntityType<? extends IndustrialDispatchBlockEntity> getBlockEntityType() { return CEIndustrialExpansion.DISPATCH_ENTITY.get(); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof IndustrialDispatchBlockEntity board) board.nextOrder(player.isShiftKeyDown() ? -1 : 1);
        return InteractionResult.SUCCESS;
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            case SOUTH -> box(1, 1, 0, 15, 15, 6);
            case EAST -> box(0, 1, 1, 6, 15, 15);
            case WEST -> box(10, 1, 1, 16, 15, 15);
            default -> box(1, 1, 10, 15, 15, 16);
        };
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof IndustrialDispatchBlockEntity board) board.release();
        IBE.onRemove(state, level, pos, next);
        super.onRemove(state, level, pos, next, moving);
    }
    @Override protected java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        var drops = super.getDrops(state, params);
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof IndustrialDispatchBlockEntity board && !board.cargo().isEmpty()) {
            var tag = new net.minecraft.nbt.CompoundTag();tag.putString("id", "create_refinery_n_reactors:industrial_dispatch_board");
            tag.put("Cargo", board.cargo().writeToNBT(board.getLevel().registryAccess(), new net.minecraft.nbt.CompoundTag()));
            for (var stack : drops) if (stack.is(asItem())) stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        }
        return drops;
    }
}
