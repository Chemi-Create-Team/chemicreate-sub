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
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;

public final class AlloyBucketItem extends BucketItem {
    public AlloyBucketItem(Properties properties) { super(Fluids.EMPTY, properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        var hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(held);
        BlockPos pos = hit.getBlockPos();
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos.relative(hit.getDirection()), hit.getDirection(), held))
            return InteractionResultHolder.fail(held);
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WorldLiquidBlock block)) return InteractionResultHolder.fail(held);
        ItemStack filled = block.pickupAlloy(level, pos, state);
        if (filled.isEmpty()) return InteractionResultHolder.fail(held);
        player.awardStat(Stats.ITEM_USED.get(this));
        block.getPickupSound(state).ifPresent(sound -> player.playSound(sound, 1, 1));
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        if (player instanceof ServerPlayer serverPlayer) CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, filled);
        return InteractionResultHolder.sidedSuccess(ItemUtils.createFilledResult(held, player, filled), level.isClientSide);
    }
}
