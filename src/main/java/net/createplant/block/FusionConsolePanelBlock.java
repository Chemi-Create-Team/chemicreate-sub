package net.createplant.block;

import net.createplant.block.entity.FusionControlConsoleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Indexed switch/gauge section around the master; each top plate is a separate physical control, with no GUI. */
public class FusionConsolePanelBlock extends Block {
    public static final DirectionProperty FACING = FusionControlConsoleBlock.FACING;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 2);
    public static final IntegerProperty DEPTH = IntegerProperty.create("depth", 0, 1);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public FusionConsolePanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(COLUMN, 0).setValue(DEPTH, 0).setValue(ACTIVE, false));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN, DEPTH, ACTIVE);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos master = FusionControlConsoleBlockEntity.masterAt(level, pos);
        if (master == null) return InteractionResult.FAIL;
        if (!level.isClientSide() && level.getBlockEntity(master) instanceof FusionControlConsoleBlockEntity console)
            console.operate(state.getValue(COLUMN) + state.getValue(DEPTH) * 3, player, player.isShiftKeyDown());
        return InteractionResult.SUCCESS;
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!next.is(state.getBlock())) {
            BlockPos master = FusionControlConsoleBlockEntity.masterAt(level, pos);
            if (master != null && level.getBlockEntity(master) instanceof FusionControlConsoleBlockEntity console) console.stationChanged();
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
