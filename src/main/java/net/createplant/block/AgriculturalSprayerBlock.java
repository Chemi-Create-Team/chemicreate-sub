package net.createplant.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.AgriculturalSprayerBlockEntity;
import net.createplant.registry.CEAgriculture;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AgriculturalSprayerBlock extends HorizontalDirectionalBlock
        implements IBE<AgriculturalSprayerBlockEntity>, IWrenchable {
    private static final MapCodec<AgriculturalSprayerBlock> CODEC = simpleCodec(AgriculturalSprayerBlock::new);
    private static final VoxelShaper SHAPES = VoxelShaper.forHorizontal(Shapes.or(
        box(2, 2, 4, 14, 13, 14), box(3, 5, 1, 13, 11, 4), box(5, 5, 14, 11, 11, 16)), Direction.NORTH);

    public AgriculturalSprayerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<AgriculturalSprayerBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var side = context.getClickedFace();
        var front = side.getAxis().isHorizontal() ? side : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, front);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (state.is(replacement.getBlock()) && state.getValue(FACING) != replacement.getValue(FACING)) {
            level.invalidateCapabilities(pos);
        }
        IBE.onRemove(state, level, pos, replacement);
    }

    @Override public Class<AgriculturalSprayerBlockEntity> getBlockEntityClass() { return AgriculturalSprayerBlockEntity.class; }

    @Override public BlockEntityType<? extends AgriculturalSprayerBlockEntity> getBlockEntityType() {
        return CEAgriculture.SPRAYER_ENTITY.get();
    }
}
