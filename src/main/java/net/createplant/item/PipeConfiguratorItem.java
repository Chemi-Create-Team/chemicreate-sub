package net.createplant.item;

import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.transport.PipeArm;
import net.createplant.transport.PipeConnections;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class PipeConfiguratorItem extends Item {
    public static final String KEY = "item." + CreatePlant.MOD_ID + ".pipe_configurator.";

    public PipeConfiguratorItem(Properties properties) { super(properties); }

    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return interact(context.getLevel(), context.getPlayer());
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return new InteractionResultHolder<>(interact(level, player), player.getItemInHand(hand));
    }

    private InteractionResult interact(Level level, Player player) {
        if (player == null || !player.mayBuild() || player.isSpectator()) return InteractionResult.PASS;
        var arm = PipeArm.target(level, player);
        if (arm == null) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) {
            if (!server.mayInteract(player, arm.pipe()) || !server.mayInteract(player, arm.pipe().relative(arm.side()))
                || !PipeConnections.toggle(server, arm.pipe(), arm.side())) return InteractionResult.PASS;
            var sound = arm.cut() ? SoundEvents.COPPER_PLACE : SoundEvents.IRON_TRAPDOOR_CLOSE;
            level.playSound(null, arm.pipe(), sound, SoundSource.BLOCKS, .65f, arm.cut() ? 1.15f : .85f);
            player.displayClientMessage(Component.translatable(KEY + (arm.cut() ? "joined" : "cut")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(KEY + "use").withStyle(ChatFormatting.GRAY));
    }
}
