package net.createplant.agriculture;

import java.util.HashSet;
import java.util.List;
import net.createplant.recipe.SprayingRecipe;
import net.createplant.safety.LoadedRay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class AgriculturalSpraying {
    public static final double NOZZLE_OFFSET = .5;

    private AgriculturalSpraying() { }

    public static void spray(ServerLevel level, IFluidHandler tank, Vec3 nozzle, Vec3 direction) {
        if (tank.getTanks() == 0 || !level.hasChunkAt(BlockPos.containing(nozzle))) return;
        Vec3 center = nozzle.subtract(direction.scale(NOZZLE_OFFSET));
        Vec3 sprayEnd = null;
        List<RecipeHolder<SprayingRecipe>> recipes = null;
        var visited = new HashSet<BlockPos>(3);
        for (int distance = 1; distance <= 3; distance++) {
            Vec3 ahead = center.add(direction.scale(distance));
            BlockPos pos = BlockPos.containing(ahead);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) break;
            Vec3 loadedEnd = LoadedRay.end(level, nozzle, ahead);
            if (!loadedEnd.equals(ahead)) break;
            var hit = level.clip(new ClipContext(nozzle, ahead, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()) {
                @Override public VoxelShape getBlockShape(BlockState state, BlockGetter getter, BlockPos at) {
                    return state.getBlock() instanceof FarmBlock ? Shapes.empty() : super.getBlockShape(state, getter, at);
                }
            });
            boolean blocked = hit.getType() == HitResult.Type.BLOCK;
            if (blocked && !hit.getBlockPos().equals(pos)) break;
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof FarmBlock) {
                pos = pos.above();
                state = level.getBlockState(pos);
            }
            if (state.getBlock() instanceof BonemealableBlock && visited.add(pos)) {
                if (recipes == null) recipes = SprayingRecipe.available(level);
                if (treat(level, pos, state, tank, recipes)) sprayEnd = ahead;
            }
            if (blocked) break;
        }
        if (sprayEnd != null) mist(level, nozzle, sprayEnd);
    }

    private static boolean treat(ServerLevel level, BlockPos pos, BlockState state, IFluidHandler tank,
            List<RecipeHolder<SprayingRecipe>> recipes) {
        for (int slot = 0; slot < tank.getTanks(); slot++) {
            var contents = tank.getFluidInTank(slot);
            if (contents.isEmpty()) continue;
            for (var holder : recipes) {
                var recipe = holder.value();
                if (!recipe.liquid().ingredient().test(contents)) continue;
                var dose = contents.copyWithAmount(recipe.liquid().amount());
                if (tank.drain(dose, IFluidHandler.FluidAction.SIMULATE).getAmount() != dose.getAmount()) continue;
                if (!apply(level, pos, state, recipe.effect())) continue;
                tank.drain(dose, IFluidHandler.FluidAction.EXECUTE);
                level.levelEvent(1505, pos, 3);
                return true;
            }
        }
        return false;
    }

    private static boolean apply(ServerLevel level, BlockPos pos, BlockState state, SprayingRecipe.Effect effect) {
        if (effect == SprayingRecipe.Effect.PROTECTION) return FertilizedCrops.protect(level, pos, state);
        if (state.getBlock() instanceof CropBlock) return FertilizedCrops.apply(level, pos, FertilizerProfile.NPK);
        if (!(state.getBlock() instanceof BonemealableBlock growing)
                || !level.hasChunksAt(pos.offset(-8, 0, -8), pos.offset(8, 0, 8))
                || !growing.isValidBonemealTarget(level, pos, state)
                || !growing.isBonemealSuccess(level, level.random, pos, state)) return false;
        growing.performBonemeal(level, level.random, pos, state);
        return true;
    }

    private static void mist(ServerLevel level, Vec3 nozzle, Vec3 end) {
        level.playSound(null, nozzle.x, nozzle.y, nozzle.z, SoundEvents.FIRE_EXTINGUISH,
            SoundSource.BLOCKS, .22f, 1.5f);
        for (int step = 0; step < 4; step++) {
            var at = nozzle.lerp(end, step / 4.0);
            double spread = .03 + step * .025;
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 2, spread, spread, spread, .012);
        }
    }
}
