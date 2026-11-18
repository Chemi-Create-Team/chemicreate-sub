package net.createplant.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.EffectCures;

/** Washing with soap clears what milk clears, the toxic-gas effect included. */
public class SoapItem extends Item {
    public SoapItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getActiveEffects().isEmpty() && !net.createplant.nuclear.RadiationContamination.dirty(player)) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            player.removeEffectsCuredBy(EffectCures.MILK);
            if (player instanceof net.minecraft.server.level.ServerPlayer server) net.createplant.nuclear.RadiationContamination.wash(server, .9);
            level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, .6f, 1.4f);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        if (!(context.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || context.getPlayer() == null) return net.minecraft.world.InteractionResult.PASS;
        var target = context.getClickedPos().relative(context.getClickedFace());
        if (net.createplant.nuclear.RadiationContamination.at(level, target).total() <= 0) target = context.getClickedPos();
        var removed = net.createplant.nuclear.RadiationContamination.take(level, target, .9);
        if (removed.total() <= 0) return net.minecraft.world.InteractionResult.PASS;
        net.createplant.nuclear.RadiationContamination.deposit(level, target.below(), removed);
        if (!context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        level.playSound(null, target, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, .6f, 1.4f);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

}
