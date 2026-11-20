package net.createplant.item;

import java.util.List;
import net.createplant.CreatePlant;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class HazardSuitItem extends ArmorItem {
    public HazardSuitItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var key = "hazard." + CreatePlant.MOD_ID + ".";
        lines.add(Component.translatable(key + "set"));
        if (getType() == Type.HELMET)
            lines.add(Component.translatable(key + "filter", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()));
        if (getType() == Type.BOOTS) lines.add(Component.translatable(key + "boots"));
    }
}
