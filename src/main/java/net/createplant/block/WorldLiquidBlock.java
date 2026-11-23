package net.createplant.block;

import net.createplant.chemistry.WoodCarbonization;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CEWorldFluids;
import net.createplant.safety.CorrosiveContact;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.fluids.FluidStack;

public final class WorldLiquidBlock extends LiquidBlock {
    public WorldLiquidBlock(FlowingFluid fluid, Properties properties) { super(fluid, properties); }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (state.getFluidState().isSource()) level.scheduleTick(pos, this, 1);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbour, boolean moved) {
        super.neighborChanged(state, level, pos, block, neighbour, moved);
        if (state.getFluidState().isSource()) level.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        WoodCarbonization.tryFromSource(level, pos);
    }

    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (net.createplant.registry.CEWetProcessing.slurry(state.getFluidState().getType())) entity.makeStuckInBlock(state, new net.minecraft.world.phys.Vec3(.45, .7, .45));
        CorrosiveContact.touch(level, pos, entity, state.getFluidState());
    }

    @Override public ItemStack pickupBlock(Player player, LevelAccessor accessor, BlockPos pos, BlockState state) {
        if (CEChlorAlkali.BRINE.get().isSame(state.getFluidState().getType())) {
            ItemStack filled = pickup(accessor, pos, state, fluid -> true);
            if (!filled.isEmpty()) filled.set(CEWorldFluids.IRON_BUCKET_SHELL.get(), net.minecraft.util.Unit.INSTANCE);
            return filled;
        }
        return pickup(accessor, pos, state, fluid -> !FluidHazards.of(fluid).corrosive());
    }

    public ItemStack pickupAlloy(LevelAccessor accessor, BlockPos pos, BlockState state) {
        return pickup(accessor, pos, state, CEWorldFluids::acceptsAlloyBucket);
    }

    public ItemStack pickupStainless(LevelAccessor accessor, BlockPos pos, BlockState state) {
        return net.createplant.item.StainlessBuckets.mark(pickup(accessor, pos, state, net.createplant.item.StainlessBuckets::holds));
    }

    private ItemStack pickup(LevelAccessor accessor, BlockPos pos, BlockState state, java.util.function.Predicate<FluidStack> holds) {
        if (!state.getFluidState().isSource()) return ItemStack.EMPTY;
        FluidStack fluid = new FluidStack(state.getFluidState().getType(), 1000);
        if (accessor instanceof net.minecraft.server.level.ServerLevel level) {
            var material = net.createplant.nuclear.RadiationWorldData.get(level).blockActivity.get(pos);
            if (material != null) fluid.set(net.createplant.registry.CERadiation.MATERIAL.get(), material);
        }
        if (!holds.test(fluid)) return ItemStack.EMPTY;
        ItemStack bucket = CEWorldFluids.bucket(fluid);
        if (bucket.isEmpty()) return ItemStack.EMPTY;
        return accessor.setBlock(pos, Blocks.AIR.defaultBlockState(), 11) ? bucket : ItemStack.EMPTY;
    }
}
