package net.createplant.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import java.util.ArrayList;
import java.util.List;
import net.createplant.block.entity.ElectrolysisCellBlockEntity;
import net.createplant.registry.CEElectrolysis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ElectrolysisCellBlock extends Block implements IBE<ElectrolysisCellBlockEntity>, IWrenchable {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final List<BooleanProperty> JOIN = List.of(BlockStateProperties.DOWN, BlockStateProperties.UP,
        BlockStateProperties.NORTH, BlockStateProperties.SOUTH, BlockStateProperties.WEST, BlockStateProperties.EAST);
    private final java.util.Map<BlockState, VoxelShape> shapes = new java.util.HashMap<>();

    public ElectrolysisCellBlock(Properties properties) {
        super(properties);
        var state = defaultBlockState().setValue(AXIS, Direction.Axis.Z);
        for (var property : JOIN) state = state.setValue(property, false);
        registerDefaultState(state);
        for (var variant : getStateDefinition().getPossibleStates()) {
            VoxelShape shape = Shapes.empty();
            for (var direction : Direction.values()) if (!variant.getValue(JOIN.get(direction.ordinal()))) {
                var wall = switch (direction) {
                    case DOWN -> box(0, 0, 0, 16, 2, 16);
                    case UP -> openTop(variant);
                    case NORTH -> box(0, 0, 0, 16, 16, 2);
                    case SOUTH -> box(0, 0, 14, 16, 16, 16);
                    case WEST -> box(0, 0, 0, 2, 16, 16);
                    case EAST -> box(14, 0, 0, 16, 16, 16);
                };
                shape = Shapes.or(shape, wall);
            }
            shapes.put(variant, shape.optimize());
        }
    }

    /** Rails along the stack and end caps only where the column ends: the electrode head closes the rest from above. */
    private static VoxelShape openTop(BlockState state) {
        boolean z = state.getValue(AXIS) == Direction.Axis.Z;
        VoxelShape shape = z ? Shapes.or(box(0, 14, 0, 3, 16, 16), box(13, 14, 0, 16, 16, 16)) : Shapes.or(box(0, 14, 0, 16, 16, 3), box(0, 14, 13, 16, 16, 16));
        if (z && !state.getValue(BlockStateProperties.NORTH)) shape = Shapes.or(shape, box(3, 14, 0, 13, 16, 3));
        if (z && !state.getValue(BlockStateProperties.SOUTH)) shape = Shapes.or(shape, box(3, 14, 13, 13, 16, 16));
        if (!z && !state.getValue(BlockStateProperties.WEST)) shape = Shapes.or(shape, box(0, 14, 3, 3, 16, 13));
        if (!z && !state.getValue(BlockStateProperties.EAST)) shape = Shapes.or(shape, box(13, 14, 3, 16, 16, 13));
        return shape;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
        JOIN.forEach(builder::add);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var axis = context.getHorizontalDirection().getAxis();
        var neighbour = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        if (neighbour.is(this)) axis = neighbour.getValue(AXIS);
        var state = defaultBlockState().setValue(AXIS, axis);
        for (var direction : Direction.values()) {
            var at = context.getClickedPos().relative(direction);
            state = state.setValue(JOIN.get(direction.ordinal()), context.getLevel().hasChunkAt(at)
                && joins(state, context.getLevel().getBlockState(at), direction));
        }
        return state;
    }

    private boolean joins(BlockState state, BlockState next, Direction direction) {
        return next.is(this) && next.getValue(AXIS) == state.getValue(AXIS)
            || direction == Direction.UP && next.is(CEElectrolysis.HEAD.get()) && next.getValue(ElectrodeHeadBlock.AXIS) == state.getValue(AXIS)
            || next.is(CEElectrolysis.CONTROLLER.get()) && direction.getAxis() == state.getValue(AXIS)
                && next.getValue(ElectrolysisControllerBlock.FACING) == direction;
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        super.onPlace(state, level, pos, previous, moving);
        if (previous.is(this)) return;
        var connected = state;
        for (var direction : Direction.values()) {
            var at = pos.relative(direction);
            connected = connected.setValue(JOIN.get(direction.ordinal()), level.hasChunkAt(at) && joins(state, level.getBlockState(at), direction));
        }
        if (connected != state) level.setBlock(pos, connected, 3);
    }

    @Override protected BlockState updateShape(BlockState state, Direction side, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        var updated = state.setValue(JOIN.get(side.ordinal()), joins(state, other, side));
        if (state != updated && level.getBlockEntity(pos) instanceof ElectrolysisCellBlockEntity cell) {
            cell.invalidateMachine();
            if (level instanceof Level world) world.invalidateCapabilities(pos);
        }
        return updated;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shapes.get(state); }

    @Override public boolean skipRendering(BlockState state, BlockState next, Direction side) {
        return joins(state, next, side) || super.skipRendering(state, next, side);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
        Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ElectrolysisCellBlockEntity cell)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return cell.use(stack, player, hand, hit.getDirection());
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof ElectrolysisCellBlockEntity cell) cell.invalidateMachine();
        super.onRemove(state, level, pos, next, moving);
        IBE.onRemove(state, level, pos, next);
    }

    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        var result = new ArrayList<>(super.getDrops(state, builder));
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ElectrolysisCellBlockEntity cell)
            for (var stack : result) if (stack.is(asItem())) {
                var tag = new CompoundTag();
                cell.writeContents(tag, cell.getLevel().registryAccess());
                stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
            }
        return result;
    }

    @Override public Class<ElectrolysisCellBlockEntity> getBlockEntityClass() { return ElectrolysisCellBlockEntity.class; }
    @Override public BlockEntityType<? extends ElectrolysisCellBlockEntity> getBlockEntityType() { return CEElectrolysis.CELL_ENTITY.get(); }
}
