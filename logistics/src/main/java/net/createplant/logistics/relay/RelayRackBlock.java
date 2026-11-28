package net.createplant.logistics.relay;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Bank of large relays; stack two or more on a relay computer. Lit (and clicking) while the computer runs. */
public final class RelayRackBlock extends Block {
    public static final BooleanProperty ACTIVE = BlockStateProperties.POWERED;
    public RelayRackBlock(Properties properties) { super(properties); registerDefaultState(defaultBlockState().setValue(ACTIVE, false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(ACTIVE); }
}
