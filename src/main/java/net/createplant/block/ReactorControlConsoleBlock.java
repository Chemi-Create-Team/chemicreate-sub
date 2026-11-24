package net.createplant.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.ReactorControlConsoleBlockEntity;
import net.createplant.nuclear.ReactorConsoleButton;
import net.createplant.nuclear.ReactorConsoleLink;
import net.createplant.nuclear.ReactorConsoleStructure;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Identical desk units acquire their position in the four-unit operator desk when all four stand side by side. */
public class ReactorControlConsoleBlock extends Block implements IBE<ReactorControlConsoleBlockEntity>, IWrenchable {
    public static final DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, 4);
    // Coordinates are measured in pixels from the operator's left and front edges.
    private static final AABB CLOSED_GUARD = new AABB(4.5, 11.4, 3, 11.5, 13.15, 10.5);
    private static final double HINGE_U = ReactorConsoleButton.GUARD_HINGE_U, HINGE_Y = ReactorConsoleButton.GUARD_HINGE_Y;
    private static final AABB OPEN_GUARD = new AABB(HINGE_U + HINGE_Y - CLOSED_GUARD.maxY,
        HINGE_Y + CLOSED_GUARD.minX - HINGE_U, CLOSED_GUARD.minZ, HINGE_U + HINGE_Y - CLOSED_GUARD.minY,
        HINGE_Y + CLOSED_GUARD.maxX - HINGE_U, CLOSED_GUARD.maxZ);
    private static final VoxelShape[] DESKS = new VoxelShape[4];
    private static final VoxelShape[] DESKS_CLOSED = new VoxelShape[4], DESKS_OPEN = new VoxelShape[4];
    static {
        for (var facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = net.minecraft.world.phys.shapes.Shapes.empty();
            for (int step = 0; step < 4; step++) {
                double start = step * 4, end = start + 4, h = 11.5 + step;
                shape = net.minecraft.world.phys.shapes.Shapes.or(shape, deskBox(facing, new AABB(0, 0, start, 16, h, end)));
            }
            int index = facing.get2DDataValue();
            DESKS[index] = shape;
            DESKS_CLOSED[index] = net.minecraft.world.phys.shapes.Shapes.or(shape, deskBox(facing, CLOSED_GUARD));
            DESKS_OPEN[index] = net.minecraft.world.phys.shapes.Shapes.or(shape, deskBox(facing, OPEN_GUARD));
        }
    }

    private static VoxelShape deskBox(Direction facing, AABB bounds) {
        return switch (facing) {
            case SOUTH -> box(bounds.minX, bounds.minY, 16 - bounds.maxZ, bounds.maxX, bounds.maxY, 16 - bounds.minZ);
            case EAST -> box(16 - bounds.maxZ, bounds.minY, 16 - bounds.maxX, 16 - bounds.minZ, bounds.maxY, 16 - bounds.minX);
            case WEST -> box(bounds.minZ, bounds.minY, bounds.minX, bounds.maxZ, bounds.maxY, bounds.maxX);
            default -> box(16 - bounds.maxX, bounds.minY, bounds.minZ, 16 - bounds.minX, bounds.maxY, bounds.maxZ);
        };
    }
    public ReactorControlConsoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SECTION, 4));
    }
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SECTION);
    }
    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        int index = state.getValue(FACING).get2DDataValue();
        if (state.getValue(SECTION) == ReactorConsoleButton.AZ5.column
            && level.getBlockEntity(pos) instanceof ReactorControlConsoleBlockEntity console && console.assembled())
            return (console.guardOpen() ? DESKS_OPEN : DESKS_CLOSED)[index];
        return DESKS[index];
    }
    @Override
    protected VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return DESKS[state.getValue(FACING).get2DDataValue()];
    }
    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state, BlockEntityType<S> type) {
        return state.getValue(SECTION) == ReactorConsoleStructure.MASTER ? IBE.super.getTicker(level, state, type) : null;
    }
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState before, boolean moving) {
        super.onPlace(state, level, pos, before, moving);
        if (!level.isClientSide && (!before.is(this) || before.getValue(FACING) != state.getValue(FACING))) {
            if (before.is(this))
                ReactorConsoleStructure.DESK.changed(level, pos, before);
            level.scheduleTick(pos, this, 1);
        }
    }
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ReactorConsoleStructure.DESK.tryForm(level, pos);
    }
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!next.is(this))
            ReactorConsoleStructure.DESK.changed(level, pos, state);
        IBE.onRemove(state, level, pos, next);
    }
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide) {
            ReactorConsoleStructure.DESK.changed(context.getLevel(), context.getClickedPos(), state);
            context.getLevel().setBlock(context.getClickedPos(),
                state.setValue(FACING, state.getValue(FACING).getClockWise()).setValue(SECTION, ReactorConsoleStructure.DESK.unformed),
                UPDATE_ALL);
        }
        return InteractionResult.SUCCESS;
    }
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        var board = ReactorConsoleLink.remembered(stack);
        if (board != null && level.getBlockEntity(pos) instanceof ReactorControlConsoleBlockEntity unit)
            unit.remember(board);
    }
    /** A display-board item held against a formed desk remembers it; placing that board unit links its board to this desk. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
        BlockHitResult hit) {
        if (stack.is(CENuclear.REACTOR_CONTROL_CONSOLE_ITEM.get())) {
            BlockPos crane = ReactorConsoleLink.rememberedCrane(stack, level);
            if (crane != null) {
                BlockPos root = ReactorControlConsoleBlockEntity.masterAt(level, pos);
                if (root == null || !(level.getBlockEntity(root) instanceof ReactorControlConsoleBlockEntity console)) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("reactor_control_console.create_refinery_n_reactors.link.desk_incomplete"), true);
                    return ItemInteractionResult.FAIL;
                }
                if (!level.isClientSide) console.linkCrane(player, crane);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        if (!stack.is(CENuclear.REACTOR_CONSOLE_DISPLAY_ITEM.get()))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        BlockPos master = ReactorControlConsoleBlockEntity.masterAt(level, pos);
        if (master == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("reactor_control_console.create_refinery_n_reactors.link.desk_incomplete"), true);
            return ItemInteractionResult.FAIL;
        }
        if (!level.isClientSide)
            ReactorConsoleLink.remember(stack, master, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        BlockPos master = ReactorControlConsoleBlockEntity.masterAt(level, pos);
        if (master == null || !(level.getBlockEntity(master) instanceof ReactorControlConsoleBlockEntity console))
            return InteractionResult.PASS;
        Direction facing = state.getValue(FACING), right = facing.getCounterClockWise();
        double dx = hit.getLocation().x - pos.getX() - .5, dz = hit.getLocation().z - pos.getZ() - .5;
        double u = .5 + dx * right.getStepX() + dz * right.getStepZ(), v = .5 - dx * facing.getStepX() - dz * facing.getStepZ();
        int section = state.getValue(SECTION);
        if (section == ReactorConsoleButton.AZ5.column) {
            var guard = console.guardOpen() ? OPEN_GUARD : CLOSED_GUARD;
            double y = (hit.getLocation().y - pos.getY()) * 16;
            if (guard.inflate(1e-5).contains(u * 16, y, v * 16)) {
                if (!level.isClientSide) console.toggleGuard(player);
                return InteractionResult.SUCCESS;
            }
        }
        if (hit.getDirection() != Direction.UP && hit.getDirection() != facing)
            return InteractionResult.PASS;
        var button = ReactorConsoleButton.at(section, u, v);
        if (button == null || button == ReactorConsoleButton.AZ5 && !console.guardOpen())
            return InteractionResult.PASS;
        if (!level.isClientSide)
            console.press(player, button, u * 16 >= button.u);
        return InteractionResult.SUCCESS;
    }
    @Override
    public Class<ReactorControlConsoleBlockEntity> getBlockEntityClass() {
        return ReactorControlConsoleBlockEntity.class;
    }
    @Override
    public BlockEntityType<? extends ReactorControlConsoleBlockEntity> getBlockEntityType() {
        return CENuclear.REACTOR_CONTROL_CONSOLE_ENTITY.get();
    }
}
