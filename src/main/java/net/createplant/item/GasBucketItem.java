package net.createplant.item;

import com.simibubi.create.AllBlocks;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class GasBucketItem extends BucketItem {
    public GasBucketItem(Fluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (content.isSame(CEProcessFluids.WATER_GAS.get())) {
            var hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
            if (hit.getType() == HitResult.Type.BLOCK && level.getBlockState(hit.getBlockPos()).is(AllBlocks.BLAZE_BURNER.get()))
                return InteractionResultHolder.fail(stack);
        }
        releaseGas(level, player.blockPosition());
        if (player.getAbilities().instabuild) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        ItemStack result = ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET));
        return InteractionResultHolder.sidedSuccess(result, level.isClientSide());
    }

    @Override
    public boolean emptyContents(Player player, Level level, BlockPos pos, BlockHitResult hitResult) {
        releaseGas(level, pos);
        return true;
    }

    public boolean emptyContents(Player player, Level level, BlockPos pos, BlockHitResult hitResult, ItemStack stack) {
        return emptyContents(player, level, pos, hitResult);
    }

    private static void releaseGas(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }

        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, .45f, 1.65f);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                10, .35, .35, .35, .015);
        }
    }
}
