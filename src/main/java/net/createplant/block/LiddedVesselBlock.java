package net.createplant.block;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.blockEntity.ComparatorUtil;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.createmod.catnip.data.Iterate;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

/**
 * A vessel up to 3x3 across and four blocks high. Its top has a hinged hatch 22 px wide in the middle, opened and closed by hand: closed, the vessel
 * is a sealed tank; open, the blocks under the hatch are hollow like a basin. The hinge is on the side away from the player who placed it. The hatch
 * flag tells which part of the hatch lies over this block. Reaction-plant internals go in from the bottom layer up and a wrench takes them out from
 * the top down. The side flags mark which faces of this block are the vessel's square outer walls.
 */
public class LiddedVesselBlock extends com.simibubi.create.content.kinetics.base.KineticBlock implements IWrenchable, IBE<LiddedVesselBlockEntity> {
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final DirectionProperty HINGE = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST, SOUTH = BlockStateProperties.SOUTH, WEST = BlockStateProperties.WEST;
    public static final BooleanProperty BOTTOM = BlockStateProperties.BOTTOM;
    public static final BooleanProperty DRIVE = BooleanProperty.create("drive");
    public static final EnumProperty<Hatch> HATCH = EnumProperty.create("hatch", Hatch.class);
    /** The leftmost blocks of the left wall carry the ladder. */
    public static final BooleanProperty LADDER = BooleanProperty.create("ladder");
    /** Half the width of a wide vessel's hatch in pixels: it is 22 px across, centred on the top. */
    public static final int HATCH_HALF = 11;
    /** Open shapes of the blocks under a 2x2 vessel's hatch: the quarter of the hatch over this block is hollow, with and without the floor. */
    private static final Map<Hatch, VoxelShape[]> QUARTERS = new EnumMap<>(Hatch.class);

    /** The part of the hatch over a block: all of it (a 1x1 vessel, or the middle of a 3x3), one corner of it (a 2x2), or none. */
    public enum Hatch implements StringRepresentable {
        NONE(null, null), FULL(null, null), NW(Direction.NORTH, Direction.WEST), NE(Direction.NORTH, Direction.EAST), SW(Direction.SOUTH, Direction.WEST), SE(Direction.SOUTH, Direction.EAST);
        private final Direction ns, ew;
        Hatch(Direction ns, Direction ew) { this.ns = ns;this.ew = ew; }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
        public static Hatch quarter(Direction ns, Direction ew) {
            for (Hatch hatch : values()) if (hatch.ns == ns && hatch.ew == ew) return hatch;
            throw new IllegalArgumentException(ns + " " + ew);
        }
        public Hatch rotate(Rotation rotation) {
            if (ns == null) return this;
            Direction a = rotation.rotate(ns), b = rotation.rotate(ew);
            return a.getAxis() == Direction.Axis.Z ? quarter(a, b) : quarter(b, a);
        }
        public Hatch mirror(Mirror mirror) { return ns == null ? this : quarter(mirror.mirror(ns), mirror.mirror(ew)); }
        /** The pixel box of this quarter within its block: x0, z0, x1, z1. The hatch is 22 px across, so each quarter reaches 11 px from the corner. */
        int[] box() { return new int[] {ew == Direction.WEST ? 0 : 16 - HATCH_HALF, ns == Direction.NORTH ? 0 : 16 - HATCH_HALF, ew == Direction.WEST ? HATCH_HALF : 16, ns == Direction.NORTH ? HATCH_HALF : 16}; }
    }
    /** Open shapes by outer walls, with and without the floor of the bottom layer. */
    private static final VoxelShape[] OPEN_SHAPES = new VoxelShape[32];

    static {
        for (int walls = 0; walls < 32; walls++) {
            VoxelShape shape = (walls & 16) != 0 ? Block.box(0, 0, 0, 16, 2, 16) : Shapes.empty();
            if ((walls & 1) != 0) shape = Shapes.or(shape, Block.box(0, 0, 0, 16, 16, 2));
            if ((walls & 2) != 0) shape = Shapes.or(shape, Block.box(14, 0, 0, 16, 16, 16));
            if ((walls & 4) != 0) shape = Shapes.or(shape, Block.box(0, 0, 14, 16, 16, 16));
            if ((walls & 8) != 0) shape = Shapes.or(shape, Block.box(0, 0, 0, 2, 16, 16));
            OPEN_SHAPES[walls] = shape.optimize();
        }
        for (Hatch hatch : new Hatch[] {Hatch.NW, Hatch.NE, Hatch.SW, Hatch.SE}) {
            int[] b = hatch.box();
            QUARTERS.put(hatch, new VoxelShape[] {Shapes.join(Shapes.block(), Block.box(b[0], 0, b[1], b[2], 16, b[3]), BooleanOp.ONLY_FIRST).optimize(),
                Shapes.join(Shapes.block(), Block.box(b[0], 2, b[1], b[2], 16, b[3]), BooleanOp.ONLY_FIRST).optimize()});
        }
    }

    public LiddedVesselBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y).setValue(OPEN, false).setValue(HINGE, Direction.NORTH)
            .setValue(NORTH, true).setValue(EAST, true).setValue(SOUTH, true).setValue(WEST, true).setValue(BOTTOM, true).setValue(DRIVE, false).setValue(HATCH, Hatch.NONE).setValue(LADDER, true));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AXIS, OPEN, HINGE, NORTH, EAST, SOUTH, WEST, BOTTOM, DRIVE, HATCH, LADDER); }

    public static BooleanProperty wall(Direction side) {
        return switch (side) { case NORTH -> NORTH; case EAST -> EAST; case SOUTH -> SOUTH; case WEST -> WEST; default -> throw new IllegalArgumentException(side.toString()); };
    }

    /** The hatch lifts away from the player who places it. */
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(HINGE, context.getHorizontalDirection()); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Hatch hatch = state.getValue(HATCH);
        if (!state.getValue(OPEN) || hatch == Hatch.NONE) return Shapes.block();
        if (hatch == Hatch.FULL) return OPEN_SHAPES[mask(state) | (state.getValue(BOTTOM) ? 16 : 0)];
        return QUARTERS.get(hatch)[state.getValue(BOTTOM) ? 1 : 0];
    }

    /** Entering the open hatch puts the body in the contents; a vessel of acid or alkali burns it until death. */
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.Entity entity) {
        if (level.isClientSide || !(entity instanceof net.minecraft.world.entity.LivingEntity living)
            || !state.getValue(OPEN) || state.getValue(HATCH) == Hatch.NONE) return;
        if (level.getBlockEntity(pos) instanceof LiddedVesselBlockEntity vessel && vessel.getControllerBE() != null)
            net.createplant.safety.CorrosiveContact.soak(living, vessel.getControllerBE().getTankInventory().getFluid());
    }

    private static int mask(BlockState state) {
        return (state.getValue(NORTH) ? 1 : 0) | (state.getValue(EAST) ? 2 : 0) | (state.getValue(SOUTH) ? 4 : 0) | (state.getValue(WEST) ? 8 : 0);
    }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (oldState.getBlock() == state.getBlock() || moved) return;
        withBlockEntityDo(level, pos, LiddedVesselBlockEntity::connect);
        ReactorStructure.changed(level, pos);
    }

    /** Only removing the block splits the vessel; opening and closing the hatch keep it whole. */
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() == newState.getBlock() && newState.hasBlockEntity()) { super.onRemove(state, level, pos, newState, moved);return; }
        if (level.getBlockEntity(pos) instanceof LiddedVesselBlockEntity vessel) {
            if (vessel.isController()) vessel.dismantle();
            vessel.dropInternal();
            super.onRemove(state, level, pos, newState, moved);
            ConnectivityHandler.splitMulti(vessel);
        } else super.onRemove(state, level, pos, newState, moved);
        ReactorStructure.changed(level, pos);
    }

    /** Side faces switch the inspection windows; the top services the internals. */
    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (context.getClickedFace().getAxis().isHorizontal()) {
            if (!context.getLevel().isClientSide) withBlockEntityDo(context.getLevel(), context.getClickedPos(), LiddedVesselBlockEntity::toggleWindows);
            return InteractionResult.SUCCESS;
        }
        if (!context.getLevel().isClientSide && state.getValue(AXIS) != Direction.Axis.Y)
            context.getLevel().setBlock(context.getClickedPos(), state.setValue(AXIS, Direction.Axis.Y), Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }

    /**
     * With the hatch open, filled containers can feed the stirred process. The basin-style storage interaction also handles containers and other
     * items go into the solid charge.
     */
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        LiddedVesselBlockEntity vessel = ConnectivityHandler.partAt(getBlockEntityType(), level, pos);
        var owner = vessel == null ? null : vessel.getControllerBE();
        if (owner == null) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        // Anything else in the hand, a vessel to place on it included, never reaches the empty-hand hatch.
        if (!owner.open() || stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == this || stack.is(com.simibubi.create.AllItems.WRENCH.get())) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        var fluid = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
        if (owner.stirred() && !fluid.isEmpty()) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            var inlet = owner.port(owner.inlet());
            if (inlet != null && !FluidState.gas(fluid)) {
                var transfer = FluidUtil.tryEmptyContainerAndStow(stack, inlet, new PlayerMainInvWrapper(player.getInventory()), Integer.MAX_VALUE, player, true);
                if (transfer.isSuccess()) {
                    player.setItemInHand(hand, transfer.getResult());
                    return ItemInteractionResult.CONSUME;
                }
            }
        }
        if (FluidHelper.tryEmptyItemIntoBE(level, player, hand, stack, owner) || FluidHelper.tryFillItemFromBE(level, player, hand, stack, owner)) return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if (!fluid.isEmpty()) return ItemInteractionResult.FAIL;
        var inventory = owner.itemPort();
        if (inventory == null) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            var remaining = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(inventory, stack.copy(), false);
            if (!player.isCreative()) stack.setCount(remaining.getCount());
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Empty hands open the hatch, or recover the catalytic vessel's catalyst while sneaking. */
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof LiddedVesselBlockEntity vessel)) return InteractionResult.PASS;
        var owner = vessel.getControllerBE();
        if (owner == null) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ItemStack item = ItemStack.EMPTY;
                if (owner.open()) for (int slot = 0; slot < 13 && item.isEmpty(); slot++) item = owner.recoverStoredItem(slot, 64);
                if (!item.isEmpty() && !player.addItem(item)) player.drop(item, false);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) vessel.toggleLid();
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = state.setValue(HINGE, rotation.rotate(state.getValue(HINGE))).setValue(HATCH, state.getValue(HATCH).rotate(rotation));
        if ((rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) && state.getValue(AXIS) != Direction.Axis.Y)
            turned = turned.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        for (Direction side : Iterate.horizontalDirections) turned = turned.setValue(wall(rotation.rotate(side)), state.getValue(wall(side)));
        return turned;
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = state.setValue(HINGE, mirror.mirror(state.getValue(HINGE))).setValue(HATCH, state.getValue(HATCH).mirror(mirror));
        for (Direction side : Iterate.horizontalDirections) turned = turned.setValue(wall(mirror.mirror(side)), state.getValue(wall(side)));
        return turned;
    }

    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }

    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return getBlockEntityOptional(level, pos).map(LiddedVesselBlockEntity::getControllerBE)
            .map(owner -> ComparatorUtil.fractionToRedstoneLevel(owner.getFillState())).orElse(0);
    }

    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }

    /** The stirrer under a tank turns its impellers; the tank itself takes no shaft. */
    @Override public boolean hasShaftTowards(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state, Direction side) { return false; }

    @Override public Class<LiddedVesselBlockEntity> getBlockEntityClass() { return LiddedVesselBlockEntity.class; }

    @Override public BlockEntityType<? extends LiddedVesselBlockEntity> getBlockEntityType() { return CETransport.LIDDED_VESSEL_ENTITY.get(); }
}
