package net.createplant.block;

import java.util.function.Supplier;
import net.createplant.block.entity.AmmoniaSeparatorBlockEntity;
import net.createplant.block.entity.PlantFluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A one-wide vertical separator with stepped heads and a front sight glass. */
public class AmmoniaSeparatorBlock extends PlantFluidTankBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape[] SHAPES = new VoxelShape[4];
    static {
        for (int ends = 0; ends < 4; ends++) {
            double low = (ends & 1) != 0 ? 4 : 0, high = (ends & 2) != 0 ? 12 : 16;
            var body = Shapes.or(Block.box(1, low, 5, 15, high, 11), Block.box(5, low, 1, 11, high, 15), Block.box(3, low, 3, 13, high, 13));
            if ((ends & 1) != 0) body = Shapes.or(body, Block.box(3, 2, 3, 13, 4, 13), Block.box(5, 1, 5, 11, 2, 11), Block.box(4, 0, 4, 12, 1, 12));
            if ((ends & 2) != 0) body = Shapes.or(body, Block.box(3, 12, 3, 13, 14, 13), Block.box(5, 14, 5, 11, 15, 11), Block.box(4, 15, 4, 12, 16, 12));
            SHAPES[ends] = body.optimize();
        }
    }
    public AmmoniaSeparatorBlock(Properties properties, Supplier<? extends BlockEntityType<? extends PlantFluidTankBlockEntity>> type) {
        super(properties, type, stack -> false);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(SHAPE, Shape.PLAIN));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder);builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return super.getStateForPlacement(context).setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(SHAPE, Shape.PLAIN); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null && level.getBlockEntity(pos) instanceof AmmoniaSeparatorBlockEntity part) part.face(placer.getDirection().getOpposite());
    }
    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) { return InteractionResult.SUCCESS; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPES[(state.getValue(BOTTOM) ? 1 : 0) | (state.getValue(TOP) ? 2 : 0)]; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return getShape(state, level, pos, context); }

    /** Breaking the controller releases its gas and liquid visibly instead of letting them vanish. */
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof AmmoniaSeparatorBlockEntity separator && separator.isController()) separator.vent();
        super.onRemove(state, level, pos, next, moving);
    }
}
