package net.createplant.logistics.item;

import java.util.List;
import net.createplant.logistics.arm.ArmTool;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** An end effector for the industrial arm; use it on the arm to fit it. */
public class ArmToolItem extends Item {
    public final ArmTool type;

    public ArmToolItem(ArmTool type, Properties properties) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.create_refinery_n_reactors." + type.id() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
