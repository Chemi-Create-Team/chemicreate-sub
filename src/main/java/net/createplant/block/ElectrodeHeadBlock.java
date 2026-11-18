package net.createplant.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.item.ItemHelper;
import net.createplant.block.entity.ElectrodeHeadBlockEntity;
import net.createplant.registry.CEElectrolysis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The hoist over one top electrolysis cell. It lowers the electrode plates it carries into the cell when the stack's START is pressed and
 * raises them on STOP, by itself: it needs no rotation.
 */
public class ElectrodeHeadBlock extends Block implements IBE<ElectrodeHeadBlockEntity>, IWrenchable {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public ElectrodeHeadBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Z));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AXIS); }

    /** Takes the axis of the cell it is set on, or of the head it is set beside. */
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var below = context.getLevel().getBlockState(context.getClickedPos().below());
        var beside = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        var axis = below.is(CEElectrolysis.CELL.get()) ? below.getValue(ElectrolysisCellBlock.AXIS)
            : beside.is(this) ? beside.getValue(AXIS) : context.getHorizontalDirection().getAxis();
        return defaultBlockState().setValue(AXIS, axis);
    }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        super.onPlace(state, level, pos, previous, moving);
        if (previous.is(this)) return;
        if (level.getBlockEntity(pos.below()) instanceof net.createplant.block.entity.ElectrolysisCellBlockEntity cell) cell.invalidateMachine();
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return level.getBlockEntity(pos) instanceof ElectrodeHeadBlockEntity head ? head.use(stack, player) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ElectrodeHeadBlockEntity head)) return InteractionResult.PASS;
        var result = head.use(ItemStack.EMPTY, player);
        return result.consumesAction() ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof ElectrodeHeadBlockEntity head) {
            head.invalidateMachine();
            ItemHelper.dropContents(level, pos, head.electrodes);ItemHelper.dropContents(level, pos, head.products);
        }
        super.onRemove(state, level, pos, next, moving);
        IBE.onRemove(state, level, pos, next);
    }

    @Override public Class<ElectrodeHeadBlockEntity> getBlockEntityClass() { return ElectrodeHeadBlockEntity.class; }
    @Override public BlockEntityType<? extends ElectrodeHeadBlockEntity> getBlockEntityType() { return CEElectrolysis.HEAD_ENTITY.get(); }
}
