package net.createplant.block;

import net.createplant.block.entity.HighPressureTurbinePartBlockEntity;
import net.createplant.registry.CEBlockEntityTypes;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class HighPressureTurbinePartBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final IntegerProperty RING_X = IntegerProperty.create("ring_x", 0, 8);
    public static final IntegerProperty RING_Y = IntegerProperty.create("ring_y", 0, 8);
    public static final BooleanProperty SECTION = BooleanProperty.create("section");
    public static final BooleanProperty CASING = BooleanProperty.create("casing");
    public static final BooleanProperty ATTACHED = BooleanProperty.create("attached");
    public static final BooleanProperty WINDOW = BooleanProperty.create("window");
    public static final EnumProperty<CasingState> STATOR_STATE = EnumProperty.create("stator_state", CasingState.class);
    public static final BooleanProperty VALUE = BooleanProperty.create("value");
    private static final VoxelShape EMPTY = Shapes.empty();

    public HighPressureTurbinePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(AXIS, Direction.Axis.X)
            .setValue(RING_X, HighPressureTurbineEndBlock.ringStateValue(1))
            .setValue(RING_Y, HighPressureTurbineEndBlock.ringStateValue(0))
            .setValue(CASING, true)
            .setValue(ATTACHED, false)
            .setValue(WINDOW, false)
            .setValue(STATOR_STATE, CasingState.SIDE)
            .setValue(VALUE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SECTION, AXIS, RING_X, RING_Y, CASING, ATTACHED, WINDOW, STATOR_STATE, VALUE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SECTION) ? TurbineUnit.shape(level,pos,state) : state.getValue(ATTACHED) ? Shapes.block() : shapeFor(state, level, pos, 16);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SECTION) ? TurbineUnit.shape(level,pos,state) : state.getValue(ATTACHED) ? Shapes.block() : shapeFor(state, level, pos, 16);
    }
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState attached = attachedStateAt(context.getLevel(), context.getClickedPos());
        if (attached != null) {
            return attached;
        }
        return standaloneState(context.getClickedFace());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block sourceBlock, BlockPos sourcePos,
        boolean movedByPiston) {
        super.neighborChanged(state, level, pos, sourceBlock, sourcePos, movedByPiston);
        if(TurbineUnit.isMutating(level,pos) || TurbineUnit.at(level,pos)!=null)return;
        if (!level.isClientSide() && state.getValue(CASING)) {
            refreshAttachment(level, pos, state);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        net.createplant.thermal.FluidRoutes.changed(level);
        if(TurbineUnit.isMutating(level,pos))return;
        if (!level.isClientSide() && state.getValue(CASING)) {
            refreshAttachment(level, pos, state);
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
        BlockState centerState = level.getBlockState(centerPos(pos, state));
        boolean lowPressurePart = state.getValue(ATTACHED)
            && (CEBlocks.isLowPressureSteamTurbine(centerState)
                || com.simibubi.create.AllBlocks.SHAFT.has(centerState));
        if (!level.isClientSide() && !player.isCreative()) {
            popResource(level, pos, new ItemStack(lowPressurePart
                ? CEItems.LOW_PRESSURE_TURBINE_CASING.get()
                : CEItems.HIGH_PRESSURE_TURBINE_CASING.get()));
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
        if (!state.is(newState.getBlock()) && !level.isClientSide() && state.getValue(CASING)
            && state.getValue(ATTACHED)) {
            refreshAroundOldCenter(level, pos, state);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return CEBlockEntityTypes.HIGH_PRESSURE_TURBINE_PART.get().create(pos, state);
    }

    public static BlockPos centerPos(BlockPos partPos, BlockState state) {
        int ringX = HighPressureTurbineEndBlock.ringOffsetFromState(state.getValue(RING_X));
        int ringY = HighPressureTurbineEndBlock.ringOffsetFromState(state.getValue(RING_Y));
        Direction.Axis axis = state.getValue(AXIS);
        return partPos.offset(-HighPressureTurbineEndBlock.worldOffset(axis, ringX, ringY).getX(),
            -HighPressureTurbineEndBlock.worldOffset(axis, ringX, ringY).getY(),
            -HighPressureTurbineEndBlock.worldOffset(axis, ringX, ringY).getZ());
    }

    public static BlockPos partPos(BlockPos center, Direction.Axis axis, int ringX, int ringY) {
        return center.offset(HighPressureTurbineEndBlock.worldOffset(axis, ringX, ringY));
    }

    public static boolean isCompleteCasing(BlockGetter level, BlockPos center, Direction.Axis axis, int radius) {
        if(level instanceof Level world)for(int x:new int[]{-radius,radius})for(int y:new int[]{-radius,radius})if(!world.hasChunkAt(partPos(center,axis,x,y)))return false;
        for (int ringX = -radius; ringX <= radius; ringX++) {
            for (int ringY = -radius; ringY <= radius; ringY++) {
                if (!isRequiredCasingPosition(level, center, ringX, ringY, radius)) {
                    continue;
                }
                BlockState state = level.getBlockState(partPos(center, axis, ringX, ringY));
                if (!isCasingPart(state, axis, ringX, ringY)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean canPlaceCasingRing(Level level, BlockPos center, Direction.Axis axis, int radius) {
        for (int ringX = -radius; ringX <= radius; ringX++) {
            for (int ringY = -radius; ringY <= radius; ringY++) {
                if (!isRequiredCasingPosition(level, center, ringX, ringY, radius)) {
                    continue;
                }
                BlockState state = level.getBlockState(partPos(center, axis, ringX, ringY));
                if (!state.isAir() && !isCasingPart(state, axis, ringX, ringY)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void placeCasingRing(Level level, BlockPos center, Direction.Axis axis, int radius) {
        for (int ringX = -radius; ringX <= radius; ringX++) {
            for (int ringY = -radius; ringY <= radius; ringY++) {
                if (!isRequiredCasingPosition(level, center, ringX, ringY, radius)) {
                    continue;
                }
                level.setBlock(partPos(center, axis, ringX, ringY),
                    expectedCasingState(axis, ringX, ringY),
                    Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            }
        }
    }

    public static void removeCasingRing(Level level, BlockPos center, Direction.Axis axis, int radius) {
        for (int ringX = -radius; ringX <= radius; ringX++) {
            for (int ringY = -radius; ringY <= radius; ringY++) {
                if (!isRequiredCasingPosition(level, center, ringX, ringY, radius)) {
                    continue;
                }
                BlockPos partPos = partPos(center, axis, ringX, ringY);
                BlockState state = level.getBlockState(partPos);
                if (isCasingPart(state, axis, ringX, ringY)) {
                    level.removeBlock(partPos, false);
                }
            }
        }
    }

    public static boolean isCasingPart(BlockState state, Direction.Axis axis, int ringX, int ringY) {
        return state.is(CEBlocks.HIGH_PRESSURE_TURBINE_PART.get())
            && state.getValue(AXIS) == axis
            && HighPressureTurbineEndBlock.ringOffsetFromState(state.getValue(RING_X)) == ringX
            && HighPressureTurbineEndBlock.ringOffsetFromState(state.getValue(RING_Y)) == ringY
            && state.getValue(ATTACHED)
            && state.getValue(CASING);
    }

    public static boolean isLowPressureCasingCenter(BlockGetter level, BlockPos center) {
        BlockState centerState = level.getBlockState(center);
        return CEBlocks.isLowPressureSteamTurbine(centerState)
            || com.simibubi.create.AllBlocks.SHAFT.has(centerState);
    }

    public static boolean isRequiredCasingPosition(BlockGetter level, BlockPos center, int ringX, int ringY,
        int radius) {
        if (ringX == 0 && ringY == 0) {
            return false;
        }
        return !isLowPressureCasingCenter(level, center)
            || Math.max(Math.abs(ringX), Math.abs(ringY)) == radius;
    }

    public static BlockState expectedCasingState(Direction.Axis axis, int ringX, int ringY) {
        return expectedCasingState(axis, ringX, ringY, false);
    }

    public static BlockState expectedCasingState(Direction.Axis axis, int ringX, int ringY, boolean window) {
        return CEBlocks.HIGH_PRESSURE_TURBINE_PART.get().defaultBlockState()
            .setValue(AXIS, axis)
            .setValue(RING_X, HighPressureTurbineEndBlock.ringStateValue(ringX))
            .setValue(RING_Y, HighPressureTurbineEndBlock.ringStateValue(ringY))
            .setValue(CASING, true)
            .setValue(ATTACHED, true)
            .setValue(WINDOW, window && canHaveWindow(ringX, ringY))
            .setValue(STATOR_STATE, casingStateFor(ringX, ringY))
            .setValue(VALUE, valueFor(ringX, ringY));
    }

    public static void refreshCasingRing(Level level, BlockPos center, Direction.Axis axis, int radius) {
        for (int ringX = -radius; ringX <= radius; ringX++) {
            for (int ringY = -radius; ringY <= radius; ringY++) {
                if (ringX == 0 && ringY == 0) {
                    continue;
                }
                BlockPos partPos = partPos(center, axis, ringX, ringY);
                BlockState state = level.getBlockState(partPos);
                if (!state.is(CEBlocks.HIGH_PRESSURE_TURBINE_PART.get()) || !state.getValue(CASING)) {
                    continue;
                }
                BlockState expected = expectedCasingState(axis, ringX, ringY, state.getValue(WINDOW));
                if (!state.equals(expected)) {
                    level.setBlock(partPos, expected, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                }
            }
        }
    }

    public static BlockState standaloneState(Direction.Axis axis) {
        return CEBlocks.HIGH_PRESSURE_TURBINE_PART.get().defaultBlockState()
            .setValue(AXIS, axis)
            .setValue(RING_X, HighPressureTurbineEndBlock.ringStateValue(0))
            .setValue(RING_Y, HighPressureTurbineEndBlock.ringStateValue(1))
            .setValue(CASING, true)
            .setValue(ATTACHED, false)
            .setValue(WINDOW, false)
            .setValue(STATOR_STATE, CasingState.SIDE)
            .setValue(VALUE, true);
    }

    public static BlockState standaloneState(Direction face) {
        Direction.Axis axis;
        int ringX = 0;
        int ringY = 0;
        switch (face) {
            case UP -> {
                axis = Direction.Axis.X;
                ringY = 1;
            }
            case DOWN -> {
                axis = Direction.Axis.X;
                ringY = -1;
            }
            case SOUTH -> {
                axis = Direction.Axis.X;
                ringX = 1;
            }
            case NORTH -> {
                axis = Direction.Axis.X;
                ringX = -1;
            }
            case EAST -> {
                axis = Direction.Axis.Z;
                ringX = 1;
            }
            case WEST -> {
                axis = Direction.Axis.Z;
                ringX = -1;
            }
            default -> {
                axis = Direction.Axis.X;
                ringY = 1;
            }
        }
        return standaloneState(axis)
            .setValue(RING_X, HighPressureTurbineEndBlock.ringStateValue(ringX))
            .setValue(RING_Y, HighPressureTurbineEndBlock.ringStateValue(ringY))
            .setValue(STATOR_STATE, casingStateFor(ringX, ringY))
            .setValue(VALUE, valueFor(ringX, ringY));
    }

    @Nullable
    public static BlockState attachedStateAt(BlockGetter level, BlockPos pos) {
        return attachedStateAt(level, pos, false);
    }

    @Nullable
    public static BlockState attachedStateAt(BlockGetter level, BlockPos pos, boolean window) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            for (int ringX = -2; ringX <= 2; ringX++) {
                for (int ringY = -2; ringY <= 2; ringY++) {
                    if (ringX == 0 && ringY == 0) {
                        continue;
                    }
                    BlockPos center = pos.offset(-HighPressureTurbineEndBlock.worldOffset(axis, ringX, ringY).getX(),
                        -HighPressureTurbineEndBlock.worldOffset(axis, ringX, ringY).getY(),
                        -HighPressureTurbineEndBlock.worldOffset(axis, ringX, ringY).getZ());
                    BlockState centerState = level.getBlockState(center);
                    int radius = HighPressureTurbineMultiblock.ringRadius(centerState);
                    if (isLowPressureCasingCenter(level, center)
                        && Math.max(Math.abs(ringX), Math.abs(ringY)) != radius) {
                        continue;
                    }
                    if ((CEBlocks.isHighPressureSteamTurbine(centerState)
                        || CEBlocks.isLowPressureSteamTurbine(centerState)
                        || com.simibubi.create.AllBlocks.SHAFT.has(centerState))
                        && centerState.hasProperty(BlockStateProperties.AXIS)
                        && centerState.getValue(BlockStateProperties.AXIS) == axis
                        && Math.max(Math.abs(ringX), Math.abs(ringY))
                            <= HighPressureTurbineMultiblock.ringRadius(centerState)) {
                        return expectedCasingState(axis, ringX, ringY, window);
                    }
                }
            }
        }
        return null;
    }

    public static void refreshAttachment(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(SECTION)) return;
        BlockState attached = attachedStateAt(level, pos, state.getValue(WINDOW));
        if (attached != null) {
            if (!state.equals(attached)) {
                level.setBlock(pos, attached, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                HighPressureTurbineMultiblock.refreshAround(level, centerPos(pos, attached));
                return;
            }
            BlockPos center = centerPos(pos, state);
            refreshCasingRing(level, center, state.getValue(AXIS),
                HighPressureTurbineMultiblock.ringRadius(level.getBlockState(center)));
            HighPressureTurbineMultiblock.refreshAround(level, centerPos(pos, state));
            return;
        }

        if (state.getValue(ATTACHED)) {
            BlockPos oldCenter = centerPos(pos, state);
            BlockState standalone = standaloneState(state.getValue(AXIS))
                .setValue(WINDOW, state.getValue(WINDOW));
            if (!state.equals(standalone)) {
                level.setBlock(pos, standalone, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            }
            HighPressureTurbineMultiblock.refreshAround(level, oldCenter);
        }
        HighPressureTurbineMultiblock.refreshAround(level, pos);
    }

    private static CasingState casingStateFor(int ringX, int ringY) {
        return ringX != 0 && ringY != 0 ? CasingState.CORNER : CasingState.SIDE;
    }

    public static boolean canHaveWindow(BlockState state) {
        if (!state.is(CEBlocks.HIGH_PRESSURE_TURBINE_PART.get()) || !state.getValue(CASING)) {
            return false;
        }
        if (!state.getValue(ATTACHED)) {
            return true;
        }
        return canHaveWindow(ringOffset(state.getValue(RING_X)), ringOffset(state.getValue(RING_Y)));
    }

    private static boolean canHaveWindow(int ringX, int ringY) {
        return ringX == 0 || ringY == 0;
    }

    private static boolean valueFor(int ringX, int ringY) {
        return ringX != 0 && ringY != 0 ? ringX == ringY : ringY != 0;
    }

    private static void refreshAroundOldCenter(Level level, BlockPos pos, BlockState state) {
        BlockPos center = centerPos(pos, state);
        HighPressureTurbineMultiblock.refreshAround(level, center);
        HighPressureTurbineMultiblock.refreshAround(level, pos);
    }

    private static VoxelShape shapeFor(BlockState state, BlockGetter level, BlockPos pos, int cornerSlices) {
        if (!state.getValue(CASING)) {
            return EMPTY;
        }

        Direction.Axis axis = state.getValue(AXIS);

        int ringX = HighPressureTurbineEndBlock.ringOffsetFromState(state.getValue(RING_X));

        int ringY = HighPressureTurbineEndBlock.ringOffsetFromState(state.getValue(RING_Y));

        if (!state.getValue(ATTACHED) && ringX == 0 && ringY == 0) {
            ringY = 1;
        }

        if (state.getValue(ATTACHED)) {
            BlockPos center = centerPos(pos, state);
            if (isLowPressureCasingCenter(level, center)) {
                return lowPressurePanelShape(axis, ringX, ringY, cornerSlices);
            }
        }

        if (state.getValue(ATTACHED) && axis == Direction.Axis.Z) {
            ringX = -ringX;
        }

        return panelShape(axis, ringX, ringY, cornerSlices);
    }

    private static VoxelShape lowPressurePanelShape(Direction.Axis axis, int ringX, int ringY, int cornerSlices) {
        int radius = HighPressureTurbineMultiblock.LOW_PRESSURE_RING_RADIUS;
        if (Math.abs(ringX) == radius && Math.abs(ringY) == radius) {
            return HighPressureTurbineShapes.lowPressureCasingCornerPanel(
                axis, ringX, ringY, cornerSlices);
        }

        if (Math.abs(ringY) == radius) {
            return HighPressureTurbineShapes.localBox(
                axis,
                0.0, 16.0,
                0.0, 16.0,
                ringY > 0 ? 8.27 : 0.0,
                ringY > 0 ? 16.0 : 7.73
            );
        }

        return HighPressureTurbineShapes.localBox(
            axis,
            0.0, 16.0,
            ringX > 0 ? 8.27 : 0.0,
            ringX > 0 ? 16.0 : 7.73,
            0.0, 16.0
        );
    }

    private static VoxelShape panelShape(Direction.Axis axis, int ringX, int ringY, int cornerSlices) {
        if (ringX != 0 && ringY != 0) {
            return cornerShape(axis, ringX, ringY, cornerSlices);
        }

        if (ringY != 0) {
            return HighPressureTurbineShapes.localBox(
                axis,
                0.0, 16.0,
                -2.92, 18.92,
                ringY > 0 ? 11.36 : 0.0,
                ringY > 0 ? 16.0 : 4.64
            );
        }

        return HighPressureTurbineShapes.localBox(
            axis,
            0.0, 16.0,
            ringX > 0 ? 11.36 : 0.0,
            ringX > 0 ? 16.0 : 4.64,
            -2.92, 18.92
        );
    }

    private static VoxelShape cornerShape(Direction.Axis axis, int ringX, int ringY, int slices) {
        return HighPressureTurbineShapes.casingCornerPanel(axis, ringX, ringY, slices);
    }

    private static int ringOffset(int stateValue) {
        return HighPressureTurbineEndBlock.ringOffsetFromState(stateValue);
    }

    public enum CasingState implements StringRepresentable {
        SIDE("side"),
        CORNER("corner");

        private final String serializedName;

        CasingState(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
        if(stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH) && net.createplant.thermal.TurbineThermal.serviceAt(level,pos))return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        return super.useItemOn(stack,state,level,pos,player,hand,hit);
    }
}
