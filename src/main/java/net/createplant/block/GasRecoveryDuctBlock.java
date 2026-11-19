package net.createplant.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.GasRecoveryDuctBlockEntity;
import net.createplant.registry.CESafety;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** Horizontal air intake and pipe outlet, powered by a downward-facing fan above it. */
public class GasRecoveryDuctBlock extends Block implements IBE<GasRecoveryDuctBlockEntity>, IWrenchable {
    public static final DirectionProperty OUTLET = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public GasRecoveryDuctBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(OUTLET, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OUTLET, ACTIVE);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(OUTLET, context.getHorizontalDirection().getOpposite());
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(OUTLET, rotation.rotate(state.getValue(OUTLET)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(OUTLET)));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (!level.isClientSide && previous.is(this) && previous.getValue(OUTLET) != state.getValue(OUTLET))
            level.invalidateCapabilities(pos);
    }

    @Override public Class<GasRecoveryDuctBlockEntity> getBlockEntityClass() { return GasRecoveryDuctBlockEntity.class; }
    @Override public BlockEntityType<? extends GasRecoveryDuctBlockEntity> getBlockEntityType() { return CESafety.GAS_RECOVERY_DUCT_ENTITY.get(); }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        super.onRemove(state, level, pos, next, moved);
        IBE.onRemove(state, level, pos, next);
    }
}
