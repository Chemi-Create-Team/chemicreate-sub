package net.createplant.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Four ANFO cartridges with a detonator, for blasting rock. A redstone signal lights the fuse; two seconds later the charge breaks the stone,
 * earth and ore within three blocks and drops all of it, as mined without a tool. Machines, containers and anything as hard as obsidian are
 * left standing; living things nearby are hurt by the blast, and other charges in reach go off with it.
 */
public class BlastingChargeBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final int FUSE = 40, CHAIN_FUSE = 4;
    public static final double RADIUS = 3;
    /** Obsidian and the like resist far more; stone and deepslate are 6. */
    private static final float MAX_RESISTANCE = 9;
    /** The blast hurts living things but not the rock it has just brought down, nor what earlier charges brought down. */
    private static final ExplosionDamageCalculator SPARES_DROPS = new ExplosionDamageCalculator() {
        @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) { return !(entity instanceof ItemEntity) && super.shouldDamageEntity(explosion, entity); }
    };
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 12, 14), Block.box(4, 12, 4, 12, 13, 12), Block.box(7, 13, 7, 9, 15, 9));

    public BlastingChargeBlock(Properties properties) { super(properties);registerDefaultState(defaultBlockState().setValue(LIT, false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LIT); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState(); }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!old.is(this) && level.hasNeighborSignal(pos)) light(level, pos, state, FUSE);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moved) {
        if (level.hasNeighborSignal(pos)) light(level, pos, state, FUSE);
    }

    private static void light(Level level, BlockPos pos, BlockState state, int fuse) {
        if (level.isClientSide() || state.getValue(LIT)) return;
        level.setBlock(pos, state.setValue(LIT, true), 3);
        level.playSound(null, pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1, 1);
        level.scheduleTick(pos, state.getBlock(), fuse);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) detonate(level, pos);
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) level.addParticle(ParticleTypes.SMOKE, pos.getX() + .5, pos.getY() + .95, pos.getZ() + .5, 0, .03, 0);
    }

    /** Breaks the rock around the charge with its drops, sets off the charges in reach, and lets the blast hurt what stands near. */
    public static void detonate(ServerLevel level, BlockPos centre) {
        level.removeBlock(centre, false);
        int reach = (int) Math.ceil(RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-reach, -reach, -reach), centre.offset(reach, reach, reach))) {
            if (pos.distSqr(centre) > RADIUS * RADIUS) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BlastingChargeBlock) light(level, pos.immutable(), state, CHAIN_FUSE);
            else if (breakable(level, pos, state)) {
                Block.dropResources(state, level, pos);
                level.removeBlock(pos, false);
            }
        }
        level.explode(null, null, SPARES_DROPS, centre.getX() + .5, centre.getY() + .5, centre.getZ() + .5, (float) RADIUS, false, Level.ExplosionInteraction.NONE);
    }

    private static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0 && state.getBlock().getExplosionResistance() <= MAX_RESISTANCE
            && (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL));
    }
}
