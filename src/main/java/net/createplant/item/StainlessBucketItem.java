package net.createplant.item;

import net.createplant.block.WorldLiquidBlock;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.fluids.FluidStack;

public final class StainlessBucketItem extends BucketItem {
    public StainlessBucketItem(Properties properties) { super(Fluids.EMPTY, properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        var hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(held);
        BlockPos pos = hit.getBlockPos();
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos.relative(hit.getDirection()), hit.getDirection(), held))
            return InteractionResultHolder.fail(held);
        var state = level.getBlockState(pos);
        ItemStack filled = pickup(level, pos, state, player);
        if (filled.isEmpty()) return InteractionResultHolder.fail(held);
        player.awardStat(Stats.ITEM_USED.get(this));
        ((BucketPickup) state.getBlock()).getPickupSound(state).ifPresent(sound -> player.playSound(sound, 1, 1));
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        if (player instanceof ServerPlayer serverPlayer) CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, filled);
        return InteractionResultHolder.sidedSuccess(ItemUtils.createFilledResult(held, player, filled), level.isClientSide);
    }

    /** Takes a liquid source the stainless bucket holds as its marked bucket; anything else is left in place and gives nothing. */
    public static ItemStack pickup(LevelAccessor level, BlockPos pos, BlockState state, Player player) {
        var source = state.getFluidState();
        if (!source.isSource() || !(state.getBlock() instanceof BucketPickup block)) return ItemStack.EMPTY;
        if (block instanceof WorldLiquidBlock liquid) return liquid.pickupStainless(level, pos, state);
        if (!StainlessBuckets.holds(new FluidStack(source.getType(), 1000))) return ItemStack.EMPTY;
        return StainlessBuckets.mark(block.pickupBlock(player, level, pos, state));
    }
}
