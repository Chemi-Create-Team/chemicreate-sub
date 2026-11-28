package net.createplant.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.createplant.block.entity.ReciprocatingCompressorBlockEntity;
import net.createplant.registry.CEReciprocating;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The opposed-cylinder drive; old blockstates retain the face-mounted arrangement. */
public class ReciprocatingCompressorBlock extends FaceAttachedHorizontalDirectionalBlock implements IBE<ReciprocatingCompressorBlockEntity>, IWrenchable, ProperWaterloggedBlock {
    public static final MapCodec<ReciprocatingCompressorBlock> CODEC = simpleCodec(ReciprocatingCompressorBlock::new);
    public static final int MAX_ROWS = 3;
    public static final BooleanProperty OPPOSED = BooleanProperty.create("opposed"), WINDOW = BooleanProperty.create("window");

    public ReciprocatingCompressorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACE, AttachFace.FLOOR).setValue(FACING, Direction.NORTH)
            .setValue(WATERLOGGED, false).setValue(OPPOSED, false).setValue(WINDOW, false));
    }

    /** The way the crank and shaft lie from the engine: away from the cylinder. */
    public static Direction crankDirection(BlockState state) { return getConnectedDirection(state); }

    public static BlockPos shaftPos(BlockState state, BlockPos pos) {
        return state.getValue(OPPOSED) ? pos.relative(state.getValue(FACING).getOpposite()) : pos.relative(crankDirection(state), 2);
    }

    public static Direction.Axis shaftAxis(BlockState state) {
        Direction facing = state.getValue(FACING);
        return (state.getValue(OPPOSED) ? facing : facing.getClockWise()).getAxis();
    }

    public static Direction cylinderSide(BlockState state, int side) {
        Direction right = state.getValue(FACING).getClockWise();
        return side == 0 ? right.getOpposite() : right;
    }

    public static boolean sameLine(BlockState row, BlockState candidate) {
        return candidate.is(row.getBlock()) && candidate.getValue(OPPOSED) && candidate.getValue(FACING) == row.getValue(FACING);
    }

    public static boolean isShaftValid(BlockState engine, BlockState shaft) {
        return (AllBlocks.SHAFT.has(shaft) || shaft.is(CEReciprocating.SHAFT.get())) && shaft.getValue(ShaftBlock.AXIS) == shaftAxis(engine);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, WATERLOGGED, OPPOSED, WINDOW);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withWater(defaultBlockState().setValue(OPPOSED, true).setValue(FACING, context.getHorizontalDirection().getOpposite()), context);
    }

    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!state.getValue(OPPOSED)) return IWrenchable.super.onWrenched(state, context);
        if (context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide) {
            context.getLevel().setBlock(context.getClickedPos(), state.cycle(WINDOW), Block.UPDATE_ALL);
            IWrenchable.playRotateSound(context.getLevel(), context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }

    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return state.getValue(OPPOSED) || level.getBlockState(pos.relative(crankDirection(state).getOpposite())).getBlock() instanceof ReciprocatingCylinderBlock;
    }

    @Override public BlockState updateShape(BlockState state, Direction side, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        updateWater(level, state, pos);
        return super.updateShape(state, side, other, level, pos, otherPos);
    }

    @Override public FluidState getFluidState(BlockState state) { return fluidState(state); }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof ReciprocatingCompressorBlockEntity engine) engine.attachShaft();
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ReciprocatingCompressorBlockEntity engine) engine.releaseShaft(state);
        super.onRemove(state, level, pos, next, moving);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPPOSED)) return Shapes.block();
        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);
        if(face==AttachFace.CEILING)return box(0,-8,0,16,16,16);
        if(face==AttachFace.FLOOR)return box(0,0,0,16,24,16);
        return switch(facing) {
            case SOUTH->box(0,0,0,16,16,24);
            case EAST->box(0,0,0,24,16,16);
            case WEST->box(-8,0,0,16,16,16);
            default->box(0,0,-8,16,16,16);
        };
    }

    @Override protected boolean isPathfindable(BlockState state, PathComputationType type) { return false; }

    @Override protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() { return CODEC; }

    @Override public Class<ReciprocatingCompressorBlockEntity> getBlockEntityClass() { return ReciprocatingCompressorBlockEntity.class; }

    @Override public BlockEntityType<? extends ReciprocatingCompressorBlockEntity> getBlockEntityType() { return CEReciprocating.COMPRESSOR_ENTITY.get(); }
}
