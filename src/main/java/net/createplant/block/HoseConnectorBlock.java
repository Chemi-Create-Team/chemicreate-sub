package net.createplant.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.HoseConnectorBlockEntity;
import net.createplant.registry.CEPressureInfrastructure;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class HoseConnectorBlock extends DirectionalBlock implements IBE<HoseConnectorBlockEntity>, IWrenchable {
    private static final MapCodec<HoseConnectorBlock> CODEC = simpleCodec(HoseConnectorBlock::new);
    private static final VoxelShaper SHAPE = VoxelShaper.forDirectional(box(3, 3, 0, 13, 13, 16), Direction.NORTH);

    public HoseConnectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<HoseConnectorBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
    }

    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) { return InteractionResult.PASS; }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!level.isClientSide && !state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof HoseConnectorBlockEntity hose)
            hose.hose().disconnect(true);
        IBE.onRemove(state, level, pos, replacement);
    }

    @Override public Class<HoseConnectorBlockEntity> getBlockEntityClass() { return HoseConnectorBlockEntity.class; }
    @Override public BlockEntityType<? extends HoseConnectorBlockEntity> getBlockEntityType() {
        return CEPressureInfrastructure.CONNECTOR_ENTITY.get();
    }
}
