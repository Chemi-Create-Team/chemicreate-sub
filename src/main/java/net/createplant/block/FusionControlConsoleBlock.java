package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.FusionControlConsoleBlockEntity;
import net.createplant.registry.CEFusion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Master section of the six-block electromechanical desk; its guarded red switch commands a magnet-energy dump. */
public class FusionControlConsoleBlock extends Block implements IBE<FusionControlConsoleBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public FusionControlConsoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, ACTIVE); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) withBlockEntityDo(level, pos, console -> console.emergency(player, player.isShiftKeyDown()));
        return InteractionResult.SUCCESS;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) withBlockEntityDo(level, pos, FusionControlConsoleBlockEntity::stationChanged);
    }
    @Override public Class<FusionControlConsoleBlockEntity> getBlockEntityClass() { return FusionControlConsoleBlockEntity.class; }
    @Override public BlockEntityType<? extends FusionControlConsoleBlockEntity> getBlockEntityType() { return CEFusion.CONSOLE_ENTITY.get(); }
}
