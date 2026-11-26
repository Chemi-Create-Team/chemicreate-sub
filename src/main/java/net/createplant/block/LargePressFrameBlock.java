package net.createplant.block;

import com.simibubi.create.content.logistics.depot.SharedDepotBlockMethods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LargePressFrameBlock extends Block {
    public static final EnumProperty<LargePressFootprint.Edge> EDGE = EnumProperty.create("edge", LargePressFootprint.Edge.class);
    public static final BooleanProperty DEPOT = BooleanProperty.create("depot");
    static final VoxelShape DECK = box(0, 0, 0, 16, 13, 16);
    static final VoxelShape BEAM = box(0, 5, 0, 16, 16, 16);

    public LargePressFrameBlock(Properties properties) { super(properties.pushReaction(PushReaction.BLOCK).randomTicks()); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(EDGE, DEPOT); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(DEPOT) ? DECK : BEAM;
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        BlockPos offset = state.getValue(EDGE).at(BlockPos.ZERO).rotate(rotation);
        return state.setValue(EDGE, LargePressFootprint.Edge.atOffset(offset.getX(), offset.getZ()));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        var edge = state.getValue(EDGE);
        int x = mirror == Mirror.FRONT_BACK ? -edge.x : edge.x;
        int z = mirror == Mirror.LEFT_RIGHT ? -edge.z : edge.z;
        return state.setValue(EDGE, LargePressFootprint.Edge.atOffset(x, z));
    }

    public static BlockPos center(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos center = state.getValue(EDGE).center(pos);
        return level.hasChunkAt(center) && LargePressFootprint.isCenter(level.getBlockState(center), state.getValue(DEPOT)) ? center : null;
    }

    public static boolean exposesDepot(BlockState state, Direction face) {
        if (!state.getValue(DEPOT) || face == null || !face.getAxis().isHorizontal()) return false;
        var edge = state.getValue(EDGE);
        return edge.x * face.getStepX() + edge.z * face.getStepZ() > 0;
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                        InteractionHand hand, BlockHitResult hit) {
        BlockPos center = state.getValue(DEPOT) ? center(level, pos, state) : null;
        return center == null ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
            : SharedDepotBlockMethods.onUse(stack, level.getBlockState(center), level, center, player, hand, hit);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()) {
            BlockPos center = center(level, pos, state);
            if (center != null) level.destroyBlock(center, false, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide() && !moving && !level.restoringBlockSnapshots && state != next) {
            BlockPos center = center(level, pos, state);
            if (center != null) level.destroyBlock(center, true);
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.hasChunkAt(state.getValue(EDGE).center(pos)) && center(level, pos, state) == null)
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
    }

    @Override public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        BlockPos center = center(level, pos, state);
        return center == null ? ItemStack.EMPTY : new ItemStack(level.getBlockState(center).getBlock());
    }
}
