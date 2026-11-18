package net.createplant.item;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A piece of a reactor hall worker's clothing, which intercepts beta particles and surface contamination; gamma rays pass through. */
public class ProtectiveClothingItem extends ArmorItem {
    private final double protection;

    public ProtectiveClothingItem(Holder<ArmorMaterial> material, Type type, double protection, Properties properties) {
        super(material, type, properties);
        this.protection = protection;
    }

    public double protection() {
        return protection;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.create_refinery_n_reactors.radiation_protection", Math.round(protection * 100)));
    }
}
