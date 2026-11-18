package net.createplant.block;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.createplant.block.entity.HighPressureTurbineEndBlockEntity;
import net.createplant.registry.CEBlockEntityTypes;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HighPressureTurbineEndBlock extends RotatedPillarKineticBlock implements IBE<HighPressureTurbineEndBlockEntity> {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final IntegerProperty RING_X = IntegerProperty.create("ring_x", 0, 4);
    public static final IntegerProperty RING_Y = IntegerProperty.create("ring_y", 0, 4);
    public static final BooleanProperty POSITIVE = BooleanProperty.create("positive");
    private static final VoxelShape FULL = Shapes.block();
    private final boolean lowPressure;

    public HighPressureTurbineEndBlock(Properties properties) {
        this(properties, false);
    }

    public HighPressureTurbineEndBlock(Properties properties, boolean lowPressure) {
        super(properties);
        this.lowPressure = lowPressure;
        registerDefaultState(defaultBlockState()
            .setValue(AXIS, Direction.Axis.X)
            .setValue(RING_X, ringStateValue(0))
            .setValue(RING_Y, ringStateValue(0))
            .setValue(POSITIVE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RING_X, RING_Y, POSITIVE);
    }

    @Override
    public Class<HighPressureTurbineEndBlockEntity> getBlockEntityClass() {
        return HighPressureTurbineEndBlockEntity.class;
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntityType<? extends HighPressureTurbineEndBlockEntity> getBlockEntityType() {
        return CEBlockEntityTypes.HIGH_PRESSURE_TURBINE_END.get();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS)
            && ringOffsetFromState(state.getValue(RING_X)) == 0
            && ringOffsetFromState(state.getValue(RING_Y)) == 0;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction.Axis axis = state.getValue(AXIS);
        BlockPos center = centerPos(pos, state);
        BlockState centerState = level.getBlockState(center);
        if (!isMatchingEnd(centerState, axis, state.getValue(POSITIVE), 0, 0, lowPressure)) {
            return false;
        }

        Direction inner = Direction.fromAxisAndDirection(axis,
            state.getValue(POSITIVE) ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
        BlockState turbineState = level.getBlockState(center.relative(inner));
        boolean matchingTurbine = lowPressure
            ? CEBlocks.isLowPressureSteamTurbine(turbineState)
                || com.simibubi.create.AllBlocks.SHAFT.has(turbineState)
            : CEBlocks.isHighPressureSteamTurbine(turbineState);
        return matchingTurbine
            && turbineState.hasProperty(BlockStateProperties.AXIS)
            && turbineState.getValue(BlockStateProperties.AXIS) == axis;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block sourceBlock, BlockPos sourcePos,
        boolean movedByPiston) {
        super.neighborChanged(state, level, pos, sourceBlock, sourcePos, movedByPiston);
        if(TurbineUnit.isMutating(level,pos) || TurbineUnit.at(level,pos)!=null)return;
        if (!level.isClientSide() && !canSurvive(state, level, pos)) {
            BlockPos center = centerPos(pos, state);
            removeEnd(level, center, state.getValue(AXIS), state.getValue(POSITIVE),
                radiusForEnd(level, center, state), state.is(CEBlocks.LOW_PRESSURE_TURBINE_END.get()));
        }
    }

    @Override
    public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult target,LevelReader level,BlockPos pos,Player player) {
        BlockPos center=state.getBlock() instanceof HighPressureTurbinePartBlock ? HighPressureTurbinePartBlock.centerPos(pos,state) : pos;
        if(level.getBlockEntity(center) instanceof net.createplant.block.entity.SteamTurbineBlockEntity rotor && rotor.unitLayout()!=null)
            return new ItemStack(rotor.unitLayout().stage().item());
        boolean low=CEBlocks.isLowPressureSteamTurbine(level.getBlockState(center));
        return new ItemStack((low ? CEItems.LOW_PRESSURE_TURBINE_UNIT : CEItems.HIGH_PRESSURE_TURBINE_UNIT).get());
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if(TurbineUnit.at(level,pos)!=null) {
            TurbineUnit.remove(level,pos,!player.isCreative());
            return super.playerWillDestroy(level,pos,state,player);
        }
        if (!level.isClientSide()) {
            BlockPos center = centerPos(pos, state);
            if (!pos.equals(center)) {
                level.destroyBlock(center, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if(TurbineUnit.isMutating(level,pos)) {super.onRemove(state,level,pos,newState,movedByPiston);return;}
        if(!state.is(newState.getBlock()) && TurbineUnit.at(level,pos)!=null) {
            boolean removed=TurbineUnit.remove(level,pos,false);
            super.onRemove(state,level,pos,newState,movedByPiston);return;
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        net.createplant.thermal.FluidRoutes.changed(level);
        if (!state.is(newState.getBlock()) && !level.isClientSide()
            && ringOffsetFromState(state.getValue(RING_X)) == 0
            && ringOffsetFromState(state.getValue(RING_Y)) == 0) {
            removeEnd(level, pos, state.getValue(AXIS), state.getValue(POSITIVE),
                radiusForEnd(level, pos, state), state.is(CEBlocks.LOW_PRESSURE_TURBINE_END.get()));
            HighPressureTurbineMultiblock.refreshAround(level, pos);
        }
    }

    public static boolean canPlaceEnd(Level level, BlockPos center, Direction.Axis axis, boolean positive) {
        return canPlaceEnd(level, center, axis, positive, HighPressureTurbineMultiblock.HIGH_PRESSURE_RING_RADIUS);
    }

    public static boolean canPlaceEnd(Level level, BlockPos center, Direction.Axis axis, boolean positive,
        int radius) {
        boolean lowPressure = expectsLowPressureEnd(level, center, axis, positive);
        for (int ringX = -radius; ringX <= radius; ringX++) {
            for (int ringY = -radius; ringY <= radius; ringY++) {
                BlockPos endPos = partPos(center, axis, ringX, ringY);
                BlockState existing = level.getBlockState(endPos);
                if (!existing.isAir()
                    && !isMatchingEnd(existing, axis, positive, ringX, ringY, lowPressure)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void placeEnd(Level level, BlockPos center, Direction.Axis axis, boolean positive) {
        placeEnd(level, center, axis, positive, HighPressureTurbineMultiblock.HIGH_PRESSURE_RING_RADIUS);
    }

    public static void placeEnd(Level level, BlockPos center, Direction.Axis axis, boolean positive, int radius) {
        boolean lowPressure = expectsLowPressureEnd(level, center, axis, positive);
        BlockState centerState = endState(axis, positive, 0, 0, lowPressure);
        level.setBlock(center, centerState, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);

        for (int ringX = -radius; ringX <= radius; ringX++) {
            for (int ringY = -radius; ringY <= radius; ringY++) {
                if (ringX == 0 && ringY == 0) {
                    continue;
                }
                level.setBlock(partPos(center, axis, ringX, ringY),
                    endState(axis, positive, ringX, ringY, lowPressure),
                    Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            }
        }
    }

    public static BlockState endState(Direction.Axis axis, boolean positive, int ringX, int ringY,
        boolean lowPressure) {
        return (lowPressure
            ? CEBlocks.LOW_PRESSURE_TURBINE_END.get()
            : CEBlocks.HIGH_PRESSURE_TURBINE_END.get()).defaultBlockState()
            .setValue(AXIS, axis)
            .setValue(RING_X, ringStateValue(ringX))
            .setValue(RING_Y, ringStateValue(ringY))
            .setValue(POSITIVE, positive);
    }

    public static void removeEnd(Level level, BlockPos center, Direction.Axis axis, boolean positive) {
        removeEnd(level, center, axis, positive, HighPressureTurbineMultiblock.HIGH_PRESSURE_RING_RADIUS);
    }

    public static void removeEnd(Level level, BlockPos center, Direction.Axis axis, boolean positive, int radius) {
        boolean lowPressure = level.getBlockState(center).is(CEBlocks.LOW_PRESSURE_TURBINE_END.get());
        removeEnd(level, center, axis, positive, radius, lowPressure);
    }

    private static void removeEnd(Level level, BlockPos center, Direction.Axis axis, boolean positive, int radius,
        boolean lowPressure) {
        for (int ringX = -radius; ringX <= radius; ringX++) {
            for (int ringY = -radius; ringY <= radius; ringY++) {
                BlockPos endPos = partPos(center, axis, ringX, ringY);
                BlockState state = level.getBlockState(endPos);
                if (isMatchingAnyEnd(state, axis, positive, ringX, ringY)) {
                    level.removeBlock(endPos, false);
                }
            }
        }
    }

    public static boolean isCompleteEnd(BlockGetter level, BlockPos center, Direction.Axis axis, boolean positive) {
        return isCompleteEnd(level, center, axis, positive, HighPressureTurbineMultiblock.HIGH_PRESSURE_RING_RADIUS);
    }

    public static boolean isCompleteEnd(BlockGetter level, BlockPos center, Direction.Axis axis, boolean positive,
        int radius) {
        if(level instanceof Level world){if(!world.hasChunkAt(center.relative(Direction.fromAxisAndDirection(axis,positive ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE))))return false;for(int x:new int[]{-radius,radius})for(int y:new int[]{-radius,radius})if(!world.hasChunkAt(partPos(center,axis,x,y)))return false;}
        boolean lowPressure = expectsLowPressureEnd(level, center, axis, positive);
        for (int ringX = -radius; ringX <= radius; ringX++) {
            for (int ringY = -radius; ringY <= radius; ringY++) {
                if (!isMatchingEnd(level.getBlockState(partPos(center, axis, ringX, ringY)),
                    axis, positive, ringX, ringY, lowPressure)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isEndCenter(BlockState state, Direction.Axis axis, boolean positive) {
        return (state.is(CEBlocks.HIGH_PRESSURE_TURBINE_END.get())
            || state.is(CEBlocks.LOW_PRESSURE_TURBINE_END.get()))
            && state.getValue(AXIS) == axis
            && state.getValue(POSITIVE) == positive
            && ringOffsetFromState(state.getValue(RING_X)) == 0
            && ringOffsetFromState(state.getValue(RING_Y)) == 0;
    }

    public static BlockPos centerPos(BlockPos partPos, BlockState state) {
        BlockPos offset = worldOffset(state.getValue(AXIS),
            ringOffsetFromState(state.getValue(RING_X)),
            ringOffsetFromState(state.getValue(RING_Y)));
        return partPos.offset(-offset.getX(), -offset.getY(), -offset.getZ());
    }

    public static BlockPos partPos(BlockPos center, Direction.Axis axis, int ringX, int ringY) {
        return center.offset(worldOffset(axis, ringX, ringY));
    }

    private static boolean isMatchingEnd(BlockState state, Direction.Axis axis, boolean positive, int ringX,
        int ringY, boolean lowPressure) {
        return state.is(lowPressure
                ? CEBlocks.LOW_PRESSURE_TURBINE_END.get()
                : CEBlocks.HIGH_PRESSURE_TURBINE_END.get())
            && state.getValue(AXIS) == axis
            && state.getValue(POSITIVE) == positive
            && ringOffsetFromState(state.getValue(RING_X)) == ringX
            && ringOffsetFromState(state.getValue(RING_Y)) == ringY;
    }

    private static boolean isMatchingAnyEnd(BlockState state, Direction.Axis axis, boolean positive, int ringX,
        int ringY) {
        return (state.is(CEBlocks.HIGH_PRESSURE_TURBINE_END.get())
            || state.is(CEBlocks.LOW_PRESSURE_TURBINE_END.get()))
            && state.getValue(AXIS) == axis
            && state.getValue(POSITIVE) == positive
            && ringOffsetFromState(state.getValue(RING_X)) == ringX
            && ringOffsetFromState(state.getValue(RING_Y)) == ringY;
    }

    static BlockPos worldOffset(Direction.Axis axis, int ringX, int ringY) {
        return switch (axis) {
            case X -> new BlockPos(0, ringY, ringX);
            case Y -> new BlockPos(ringX, 0, ringY);
            case Z -> new BlockPos(ringX, ringY, 0);
        };
    }

    private static VoxelShape shapeFor(BlockState state) {
        int ringX = ringOffsetFromState(state.getValue(RING_X));
        int ringY = ringOffsetFromState(state.getValue(RING_Y));
        if (ringX != 0 && ringY != 0) {
            return HighPressureTurbineShapes.endCorner(state.getValue(AXIS), ringX, ringY);
        }
        return FULL;
    }

    public static int ringStateValue(int ringOffset) {
        return switch (ringOffset) {
            case -1 -> 0;
            case 0 -> 1;
            case 1 -> 2;
            case -2 -> 3;
            case 2 -> 4;
            case -3 -> 5;
            case 3 -> 6;
            case -4 -> 7;
            case 4 -> 8;
            default -> throw new IllegalArgumentException("Invalid turbine ring offset: " + ringOffset);
        };
    }

    public static int ringOffsetFromState(int stateValue) {
        return switch (stateValue) {
            case 0 -> -1;
            case 1 -> 0;
            case 2 -> 1;
            case 3 -> -2;
            case 4 -> 2;
            case 5 -> -3;
            case 6 -> 3;
            case 7 -> -4;
            case 8 -> 4;
            default -> 0;
        };
    }

    private static int ringOffset(int stateValue) {
        return ringOffsetFromState(stateValue);
    }

    private static int radiusForEnd(LevelReader level, BlockPos center, BlockState state) {
        Direction.Axis axis = state.getValue(AXIS);
        Direction inner = Direction.fromAxisAndDirection(axis,
            state.getValue(POSITIVE) ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
        return HighPressureTurbineMultiblock.ringRadius(level.getBlockState(center.relative(inner)));
    }

    private static boolean expectsLowPressureEnd(BlockGetter level, BlockPos center, Direction.Axis axis,
        boolean positive) {
        Direction inner = Direction.fromAxisAndDirection(axis,
            positive ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
        BlockState state = level.getBlockState(center.relative(inner));
        return CEBlocks.isLowPressureSteamTurbine(state) || com.simibubi.create.AllBlocks.SHAFT.has(state);
    }
    @Override public void onPlace(BlockState state,net.minecraft.world.level.Level level,BlockPos pos,BlockState previous,boolean moving){super.onPlace(state,level,pos,previous,moving);net.createplant.thermal.FluidRoutes.changed(level);}

    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
        if(stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH) && net.createplant.thermal.TurbineThermal.serviceAt(level,pos))return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        return super.useItemOn(stack,state,level,pos,player,hand,hit);
    }
}
