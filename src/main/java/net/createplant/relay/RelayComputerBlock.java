package net.createplant.relay;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** Base of the relay computer: needs at least two relay racks stacked on top. */
public final class RelayComputerBlock extends HorizontalDirectionalBlock implements IBE<RelayComputerBlockEntity> {
    public static final com.mojang.serialization.MapCodec<RelayComputerBlock> CODEC = simpleCodec(RelayComputerBlock::new);
    public RelayComputerBlock(Properties properties) { super(properties); registerDefaultState(defaultBlockState().setValue(RelayRackBlock.ACTIVE, false)); }
    @Override protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, RelayRackBlock.ACTIVE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RelayComputerBlockEntity be) be.describeTo(player);
        return InteractionResult.SUCCESS;
    }
    @Override public Class<RelayComputerBlockEntity> getBlockEntityClass() { return RelayComputerBlockEntity.class; }
    @Override public BlockEntityType<? extends RelayComputerBlockEntity> getBlockEntityType() { return CERelayComputer.COMPUTER_ENTITY.get(); }
}
