package net.createplant.block;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.createplant.block.entity.SteamTurbineBlockEntity;
import net.createplant.registry.CEBlockEntityTypes;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.function.Supplier;

public class SteamTurbineBlock extends RotatedPillarKineticBlock implements IBE<SteamTurbineBlockEntity> {
    public static final IntegerProperty BLADES = IntegerProperty.create("blades", 1, 6);
    public static final IntegerProperty SIZE_1 = IntegerProperty.create("size_1", 1, 7);
    public static final IntegerProperty SIZE_2 = IntegerProperty.create("size_2", 1, 7);
    public static final IntegerProperty SIZE_3 = IntegerProperty.create("size_3", 1, 7);
    public static final BooleanProperty CASING = BooleanProperty.create("casing");
    private final Supplier<? extends ItemLike> bladeItem;
    private final boolean sizeSelectable;
    private final int maxSize;
    private final int maxBlades;
    private final boolean dropsTurbineShaft;

    public SteamTurbineBlock(Properties properties) {
        this(properties, CEItems.LOW_PRESSURE_TURBINE_BLADE::get, true);
    }

    public SteamTurbineBlock(Properties properties, Supplier<? extends ItemLike> bladeItem, boolean sizeSelectable) {
        this(properties, bladeItem, sizeSelectable, 4, 3);
    }

    public SteamTurbineBlock(Properties properties, Supplier<? extends ItemLike> bladeItem, boolean sizeSelectable,
        int maxSize) {
        this(properties, bladeItem, sizeSelectable, maxSize, 3);
    }

    public SteamTurbineBlock(Properties properties, Supplier<? extends ItemLike> bladeItem, boolean sizeSelectable,
        int maxSize, int maxBlades) {
        this(properties, bladeItem, sizeSelectable, maxSize, maxBlades, false);
    }

    public SteamTurbineBlock(Properties properties, Supplier<? extends ItemLike> bladeItem, boolean sizeSelectable,
        boolean dropsTurbineShaft) {
        this(properties, bladeItem, sizeSelectable, 4, 3, dropsTurbineShaft);
    }

    public SteamTurbineBlock(Properties properties, Supplier<? extends ItemLike> bladeItem, boolean sizeSelectable,
        int maxSize, int maxBlades, boolean dropsTurbineShaft) {
        super(properties);
        this.bladeItem = bladeItem;
        this.sizeSelectable = sizeSelectable;
        this.maxSize = maxSize;
        this.maxBlades = maxBlades;
        this.dropsTurbineShaft = dropsTurbineShaft;
        registerDefaultState(defaultBlockState()
            .setValue(AXIS, Direction.Axis.X)
            .setValue(BLADES, 1)
            .setValue(SIZE_1, 1)
            .setValue(SIZE_2, 1)
            .setValue(SIZE_3, 1)
            .setValue(CASING, false));
    }

    @Override
    public Class<SteamTurbineBlockEntity> getBlockEntityClass() {
        return SteamTurbineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SteamTurbineBlockEntity> getBlockEntityType() {
        return CEBlockEntityTypes.STEAM_TURBINE.get();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BLADES, SIZE_1, SIZE_2, SIZE_3, CASING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        net.createplant.thermal.FluidRoutes.changed(level);
        if (!level.isClientSide() && !TurbineUnit.isMutating(level,pos)
            && CEBlocks.isCasedSteamTurbine(state)) {
            HighPressureTurbinePartBlock.refreshCasingRing(level, pos, state.getValue(AXIS),
                HighPressureTurbineMultiblock.ringRadius(state));
        }
        HighPressureTurbineMultiblock.refreshAround(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if(TurbineUnit.isMutating(level,pos)) {super.onRemove(state,level,pos,newState,movedByPiston);return;}
        if(!state.is(newState.getBlock()) && TurbineUnit.at(level,pos)!=null) {
            boolean removed=TurbineUnit.remove(level,pos,false);
            if(!removed && level.getBlockEntity(pos) instanceof net.createplant.block.entity.SteamTurbineBlockEntity rotor)rotor.ventContents();
            super.onRemove(state,level,pos,newState,movedByPiston);return;
        }
        if(state.getBlock()!=newState.getBlock() && !level.isClientSide && level.getBlockEntity(pos) instanceof SteamTurbineBlockEntity rotor)rotor.ventContents();
        super.onRemove(state, level, pos, newState, movedByPiston);
        net.createplant.thermal.FluidRoutes.changed(level);
        if (!state.is(newState.getBlock())) {
            if (CEBlocks.isCasedSteamTurbine(state)) {
                int radius = HighPressureTurbineMultiblock.ringRadius(state);
                if (CEBlocks.isLowPressureSteamTurbine(state)) {
                    HighPressureTurbinePartBlock.removeCasingRing(level, pos, state.getValue(AXIS), radius);
                } else {
                    removeHighPressureParts(level, pos, state.getValue(AXIS), radius);
                }
                removeHighPressureEnds(level, pos, state.getValue(AXIS), radius);
            }
            HighPressureTurbineMultiblock.refreshAround(level, pos);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block sourceBlock, BlockPos sourcePos,
        boolean movedByPiston) {
        super.neighborChanged(state, level, pos, sourceBlock, sourcePos, movedByPiston);
        HighPressureTurbineMultiblock.refreshAround(level, pos);
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
        if (!level.isClientSide() && !player.isCreative()) {
            popResource(level, pos, new ItemStack(bladeItem.get(), state.getValue(BLADES)));
            if (dropsTurbineShaft) {
                popResource(level, pos, new ItemStack(com.simibubi.create.AllBlocks.SHAFT.asItem()));
            }
            if (state.getValue(CASING) && !CEBlocks.isHighPressureSteamTurbine(state)) {
                Item casingItem = CEBlocks.isHighPressureSteamTurbine(state)
                    ? CEItems.HIGH_PRESSURE_TURBINE_CASING.get()
                    : CEItems.LOW_PRESSURE_TURBINE_CASING.get();
                popResource(level, pos, new ItemStack(casingItem,
                    CEBlocks.isLowPressureSteamTurbine(state)
                        ? HighPressureTurbineMultiblock.LOW_PRESSURE_CASING_BLOCKS_PER_SHAFT
                        : 1));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    public static boolean isTurbine(BlockState state) {
        return state.getBlock() instanceof SteamTurbineBlock;
    }

    public Item bladeItem() {
        return bladeItem.get().asItem();
    }

    public boolean acceptsBlade(ItemStack stack) {
        return stack.is(bladeItem());
    }

    public boolean sizeSelectable() {
        return sizeSelectable;
    }

    public int maxSize() {
        return maxSize;
    }

    public int maxBlades() {
        return maxBlades;
    }

    public static IntegerProperty sizeProperty(int bladeIndex) {
        return switch (bladeIndex) {
            case 0 -> SIZE_1;
            case 1 -> SIZE_2;
            case 2 -> SIZE_3;
            default -> throw new IllegalArgumentException("Invalid steam turbine blade index: " + bladeIndex);
        };
    }

    private static void removeHighPressureParts(Level level, BlockPos center, Direction.Axis axis, int radius) {
        if (level.isClientSide()) {
            return;
        }
        for (int ringX = -radius; ringX <= radius; ringX++) {
            for (int ringY = -radius; ringY <= radius; ringY++) {
                if (ringX == 0 && ringY == 0) {
                    continue;
                }
                BlockPos partPos = HighPressureTurbinePartBlock.partPos(center, axis, ringX, ringY);
                BlockState partState = level.getBlockState(partPos);
                if (partState.is(CEBlocks.HIGH_PRESSURE_TURBINE_PART.get())
                    && partState.getValue(HighPressureTurbinePartBlock.ATTACHED)) {
                    level.setBlock(partPos, HighPressureTurbinePartBlock.standaloneState(axis)
                            .setValue(HighPressureTurbinePartBlock.WINDOW,
                                partState.getValue(HighPressureTurbinePartBlock.WINDOW)),
                        Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                }
            }
        }
    }

    private static void removeHighPressureEnds(Level level, BlockPos center, Direction.Axis axis, int radius) {
        if (level.isClientSide()) {
            return;
        }
        for (Direction.AxisDirection axisDirection : Direction.AxisDirection.values()) {
            Direction direction = Direction.fromAxisAndDirection(axis, axisDirection);
            BlockPos endCenter = center.relative(direction);
            BlockState endState = level.getBlockState(endCenter);
            if (endState.is(CEBlocks.HIGH_PRESSURE_TURBINE_END.get())
                || endState.is(CEBlocks.LOW_PRESSURE_TURBINE_END.get())) {
                HighPressureTurbineEndBlock.removeEnd(level, endCenter, axis,
                    axisDirection == Direction.AxisDirection.POSITIVE, radius);
            }
        }
    }

    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
        if(stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH) && net.createplant.thermal.TurbineThermal.serviceAt(level,pos))return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        return super.useItemOn(stack,state,level,pos,player,hand,hit);
    }
}
