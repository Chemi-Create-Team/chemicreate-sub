package net.createplant.item;

import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.agriculture.FertilizedCrops;
import net.createplant.agriculture.FertilizerProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

public final class FertilizerItem extends Item {
    private static final String KEY = "fertilizer." + CreatePlant.MOD_ID + ".";
    private final FertilizerProfile profile;

    public FertilizerItem(FertilizerProfile profile, Properties properties) {
        super(properties);
        this.profile = profile;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        var world = context.getLevel();
        BlockPos center = context.getClickedPos();
        var player = context.getPlayer();
        if (!FertilizedCrops.growable(world.getBlockState(center))) return InteractionResult.PASS;
        if (!(world instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        int radius = profile.radius;
        boolean used = false;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius), center.offset(radius, 0, radius))) {
            if (player != null && !level.mayInteract(player, pos)) continue;
            if (FertilizedCrops.apply(level, pos, profile)) {
                level.levelEvent(1505, pos, 6);
                used = true;
            }
        }
        if (!used) return InteractionResult.PASS;
        if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flags) {
        int size = profile.radius * 2 + 1;
        tooltip.add(Component.translatable(KEY + "apply", size, size).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(KEY + "growth", profile.bonusStages, profile.growthUses, profile.duration / 1200).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(KEY + "harvest", profile.harvestChance, profile.minimumHarvest, profile.maximumHarvest).withStyle(ChatFormatting.GRAY));
    }
}
