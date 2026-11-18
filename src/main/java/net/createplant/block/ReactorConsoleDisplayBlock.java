package net.createplant.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.ReactorConsoleDisplayBlockEntity;
import net.createplant.block.entity.ReactorControlConsoleBlockEntity;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Identical display units stacked into the 7 x 4 board: core map, meters, annunciators and nixie readouts of the linked desk. */
public class ReactorConsoleDisplayBlock extends Block implements IBE<ReactorConsoleDisplayBlockEntity>, IWrenchable {
    public static final DirectionProperty FACING = ReactorControlConsoleBlock.FACING;
    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, 28);

    public ReactorConsoleDisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SECTION, 28));
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> box(0, 0, 0, 16, 16, 13);
            case EAST -> box(0, 0, 0, 13, 16, 16);
            case WEST -> box(3, 0, 0, 16, 16, 16);
            default -> box(0, 0, 3, 16, 16, 16);
        };
    }
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState before, boolean moving) {
        super.onPlace(state, level, pos, before, moving);
        if (!level.isClientSide && (!before.is(this) || before.getValue(FACING) != state.getValue(FACING))) {
            if (before.is(this))
                ReactorConsoleStructure.DISPLAY.changed(level, pos, before);
            level.scheduleTick(pos, this, 1);
        }
    }
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        var desk = ReactorConsoleLink.remembered(stack);
        if (desk != null && level.getBlockEntity(pos) instanceof ReactorConsoleDisplayBlockEntity display)
            display.remember(desk);
    }
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ReactorConsoleStructure.DISPLAY.tryForm(level, pos);
    }
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!next.is(this))
            ReactorConsoleStructure.DISPLAY.changed(level, pos, state);
        IBE.onRemove(state, level, pos, next);
    }
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide) {
            ReactorConsoleStructure.DISPLAY.changed(context.getLevel(), context.getClickedPos(), state);
            context.getLevel().setBlock(context.getClickedPos(),
                state.setValue(FACING, state.getValue(FACING).getClockWise()).setValue(SECTION, ReactorConsoleStructure.DISPLAY.unformed), UPDATE_ALL);
        }
        return InteractionResult.SUCCESS;
    }
    /** A desk unit item held against a formed board remembers it; placing that unit links its desk to this board. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
        BlockHitResult hit) {
        if (!stack.is(CENuclear.REACTOR_CONTROL_CONSOLE_ITEM.get()))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        BlockPos master = ReactorConsoleDisplayBlockEntity.masterAt(level, pos);
        if (master == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("reactor_control_console.create_refinery_n_reactors.link.display_incomplete"), true);
            return ItemInteractionResult.FAIL;
        }
        if (!level.isClientSide)
            ReactorConsoleLink.remember(stack, master, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction facing = state.getValue(FACING), right = facing.getCounterClockWise();
        BlockPos master = ReactorConsoleDisplayBlockEntity.masterAt(level, pos);
        if (master == null || hit.getDirection() != facing || !(level.getBlockEntity(master) instanceof ReactorConsoleDisplayBlockEntity display))
            return InteractionResult.PASS;
        int part = state.getValue(SECTION), width = ReactorConsoleStructure.DISPLAY.width;
        if (part % width >= ReactorConsoleDisplayBlockEntity.MAP_SIZE)
            return InteractionResult.PASS;
        double dx = hit.getLocation().x - pos.getX() - .5, dz = hit.getLocation().z - pos.getZ() - .5;
        double u = .5 + dx * right.getStepX() + dz * right.getStepZ();
        if (!level.isClientSide) {
            ReactorControlConsoleBlockEntity console = display.linkedConsole();
            if (console == null)
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("reactor_control_console.create_refinery_n_reactors.link.display_unlinked"), true);
            else
                console.selectRod(player, ReactorConsoleDisplayBlockEntity.mapFraction(part % width + u),
                    ReactorConsoleDisplayBlockEntity.mapFraction(part / width + hit.getLocation().y - pos.getY()));
        }
        return InteractionResult.SUCCESS;
    }
    @Override
    public Class<ReactorConsoleDisplayBlockEntity> getBlockEntityClass() {
        return ReactorConsoleDisplayBlockEntity.class;
    }
    @Override
    public BlockEntityType<? extends ReactorConsoleDisplayBlockEntity> getBlockEntityType() {
        return CENuclear.REACTOR_CONSOLE_DISPLAY_ENTITY.get();
    }
}
