package net.createplant.block;

import java.util.function.Supplier;
import net.createplant.block.entity.PlantFluidTankBlockEntity;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of a reaction tower: a closed 3x3 tank, hollow inside its 6 px walls, floor and roof, with a 22 px hatch in the middle of the roof and a
 * catalyst basket in each layer.
 * The side flags mark the faces that are the tower's outer walls; a block that is not part of a 3x3 tower has all four and is solid. The middle block of
 * each side has a sight glass, opened while the tower works as a gas-liquid separator. It accepts no fluid itself; the ports belong to the tower's bottom centre.
 */
public class ReactionTowerBlock extends PlantFluidTankBlock {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST, SOUTH = BlockStateProperties.SOUTH, WEST = BlockStateProperties.WEST;
    public static final BooleanProperty WINDOW = BooleanProperty.create("window");
    /** Wall, floor and roof thickness, in pixels. */
    public static final int WALL = 6, FLOOR = 2, ROOF = 2;
    /** The roof hatch's opening across the 48 px tower, matching a 3x3 lidded vessel's 22 px hatch. */
    public static final int HATCH_MIN = 13, HATCH_MAX = 35;
    /** Outline shapes by outer walls and by whether this is the bottom layer, which has the floor, and the top layer, which has the roof. */
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (int walls = 0; walls < 64; walls++) {
            int mask = walls & 15;
            int[] x = hollow(mask, 8, 2), z = hollow(mask, 1, 4);
            // Only the nine positions of a 3x3 tower are hollow; any other wall set is a lone, solid block.
            if (x[0] >= x[1] || z[0] >= z[1]) { SHAPES[walls] = Shapes.block();continue; }
            VoxelShape solid = Shapes.join(Shapes.block(), Block.box(x[0], 0, z[0], x[1], 16, z[1]), net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
            if ((walls & 16) != 0) solid = Shapes.or(solid, Block.box(x[0], 0, z[0], x[1], FLOOR, z[1]));
            if ((walls & 32) != 0) solid = Shapes.or(solid, Block.box(x[0], 16 - ROOF, z[0], x[1], 16, z[1]));
            SHAPES[walls] = solid.optimize();
        }
    }

    public ReactionTowerBlock(Properties properties, Supplier<? extends BlockEntityType<? extends PlantFluidTankBlockEntity>> type) {
        super(properties, type, stack -> false);
        registerDefaultState(defaultBlockState().setValue(NORTH, true).setValue(EAST, true).setValue(SOUTH, true).setValue(WEST, true).setValue(WINDOW, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NORTH, EAST, SOUTH, WEST, WINDOW);
    }

    /** The span of the hollow across this block along one axis, from the walls on its low (first flag) and high (second flag) sides. */
    public static int[] hollow(int mask, int low, int high) {
        boolean lowWall = (mask & low) != 0, highWall = (mask & high) != 0;
        return new int[] {lowWall ? WALL : 0, highWall ? 16 - WALL : 16};
    }

    public static int mask(BlockState state) {
        return (state.getValue(NORTH) ? 1 : 0) | (state.getValue(EAST) ? 2 : 0) | (state.getValue(SOUTH) ? 4 : 0) | (state.getValue(WEST) ? 8 : 0);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[mask(state) | (state.getValue(BOTTOM) ? 16 : 0) | (state.getValue(TOP) ? 32 : 0)];
    }

    /** Every block collides as a whole block, so nothing reaches the hollow. */
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }

    /** Through the open hatch a basket goes into the lowest layer without one, a catalyst into the lowest basket without one. */
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ReactionTowerBlockEntity part)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var owner = part.owner();
        if (owner == null || !ReactionTowerBlockEntity.basket(stack) && !ReactionTowerBlockEntity.catalyst(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!owner.hatchOpen() || !owner.canInsert(stack)) return ItemInteractionResult.FAIL;
        if (!level.isClientSide) {
            BlockPos at = owner.insert(stack.copyWithCount(1));
            if (!player.isCreative()) stack.shrink(1);
            level.playSound(null, at, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1, 1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** An empty hand on the top opens or shuts the hatch. */
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hit.getDirection() != Direction.UP || !(level.getBlockEntity(pos) instanceof ReactionTowerBlockEntity part)) return InteractionResult.PASS;
        var owner = part.owner();
        if (owner == null || pos.getY() != owner.getBlockPos().getY() + owner.layers() - 1) return InteractionResult.PASS;
        if (!level.isClientSide) owner.toggleHatch();
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A wrench on the top takes out the highest catalyst, then the highest basket, through the open hatch. */
    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (context.getClickedFace() == Direction.UP && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof ReactionTowerBlockEntity part) {
            var owner = part.owner();
            if (owner != null && owner.hatchOpen() && owner.hasBaskets()) {
                if (!context.getLevel().isClientSide) {
                    ItemStack taken = owner.takeTop();
                    var player = context.getPlayer();
                    if (player == null || !player.getInventory().add(taken)) Block.popResource(context.getLevel(), context.getClickedPos().above(), taken);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.onWrenched(state, context);
    }

    /** The side toward the player is the front, where the tower shows its gauges. */
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null && level.getBlockEntity(pos) instanceof ReactionTowerBlockEntity part) part.face(placer.getDirection().getOpposite());
    }

    /** A centre block drops its basket and catalyst; the bottom centre also releases the gas it held. */
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ReactionTowerBlockEntity part) part.removed();
        super.onRemove(state, level, pos, next, moving);
    }
}
