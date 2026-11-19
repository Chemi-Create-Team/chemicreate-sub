package net.createplant.item;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class GasMaskItem extends ArmorItem {
    public GasMaskItem(Holder<ArmorMaterial> material, Properties properties) { super(material, Type.HELMET, properties); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("item.create_refinery_n_reactors.gas_mask.filter", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()));
    }
}
