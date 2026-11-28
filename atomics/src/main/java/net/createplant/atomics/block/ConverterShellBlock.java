package net.createplant.atomics.block;

import net.createplant.block.CompressorShapes;
import java.util.Locale;

import com.simibubi.create.foundation.block.IBE;

import net.createplant.atomics.block.entity.ConverterShellBlockEntity;
import net.createplant.atomics.block.entity.ConverterTrunnionBearingBlockEntity;
import net.createplant.atomics.registry.CEConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Stationary converter casing used only for the passive bearing and four physical ports. */
public class ConverterShellBlock extends Block implements IBE<ConverterShellBlockEntity> {
    public enum Part implements StringRepresentable {
        LOOSE, LANCE, RAW_PORT, GAS_PORT, TAP, BEARING;

        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private static final VoxelShape[] LOOSE = shapes(new double[][] {
        { 1, 0, 1, 15, 16, 15 }, { 0, 1, 0, 16, 4, 16 }, { 0, 12, 0, 16, 15, 16 }
    });
    private static final VoxelShape[] LANCE = shapes(new double[][] {
        { 2, 0, 2, 14, 6, 14 }, { 4, 4, 4, 12, 16, 12 },
        { 6, -8, 6, 10, 16, 10 }, { 5, -2, 5, 11, 2, 11 }
    });
    private static final VoxelShape[] RAW_PORT = shapes(new double[][] {
        { 2, 0, 2, 14, 5, 14 }, { 4, -4, 4, 12, 16, 12 }, { 2, 5, 2, 14, 9, 14 }
    });
    private static final VoxelShape[] GAS_PORT = shapes(new double[][] {
        { 5, 5, 8, 11, 11, 24 }, { 2, 2, 2, 14, 14, 14 }, { 3, 3, 11, 13, 13, 16 }
    });
    private static final VoxelShape[] TAP = shapes(new double[][] {
        { 5, 5, -8, 11, 11, 12 }, { 2, 2, 2, 14, 14, 14 }, { 3, 3, -1, 13, 13, 4 }
    });
    private static final VoxelShape[] BEARING = shapes(new double[][] {
        { 0, 0, 0, 16, 16, 16 }, { -4, 4, 4, 4, 12, 12 }
    });

    public ConverterShellBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PART, Part.LOOSE));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int facing = state.getValue(FACING).get2DDataValue();
        return switch (state.getValue(PART)) {
            case LANCE -> LANCE[facing];
            case RAW_PORT -> RAW_PORT[facing];
            case GAS_PORT -> GAS_PORT[facing];
            case TAP -> TAP[facing];
            case BEARING -> BEARING[facing];
            default -> LOOSE[facing];
        };
    }

    private static VoxelShape[] shapes(double[][] boxes) {
        VoxelShape[] result = new VoxelShape[4];
        for (Direction facing : Direction.Plane.HORIZONTAL)
            result[facing.get2DDataValue()] = CompressorShapes.of(boxes, facing);
        return result;
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ConverterShellBlockEntity part) {
            ConverterTrunnionBearingBlockEntity controller = part.controller();
            if (controller != null) controller.structureChanged();
        }
        super.onRemove(state, level, pos, next, moving);
        IBE.onRemove(state, level, pos, next);
    }

    public static void setAppearance(Level level, BlockPos pos, BlockState next) {
        if (!level.getBlockState(pos).equals(next)) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
    }

    @Override public Class<ConverterShellBlockEntity> getBlockEntityClass() { return ConverterShellBlockEntity.class; }
    @Override public BlockEntityType<? extends ConverterShellBlockEntity> getBlockEntityType() { return CEConverter.SHELL_ENTITY.get(); }
}
