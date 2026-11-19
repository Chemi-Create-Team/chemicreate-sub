package net.createplant.item;

import java.util.List;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEFusion;
import net.createplant.registry.CESafety;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;

public final class GasCylinderItem extends Item {
    public static final int CAPACITY = 4000;

    public GasCylinderItem(Properties properties) { super(properties); }

    public static FluidHandlerItemStack handler(ItemStack cylinder) {
        return new FluidHandlerItemStack(CESafety.CYLINDER_CONTENTS, cylinder, CAPACITY) {
            @Override public boolean canFillFluidType(FluidStack fluid) {
                return fluid.is(CEFluids.NITROGEN_GAS.get()) || fluid.is(CEFluids.ARGON_GAS.get()) || fluid.is(CEFusion.HELIUM_GAS.get());
            }
        };
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var face = context.getClickedFace();
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, face, context.getItemInHand())) return InteractionResult.FAIL;
        if (level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face) == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return FluidUtil.interactWithFluidHandler(player, context.getHand(), level, pos, face)
            ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var gas = handler(stack).getFluid();
        lines.add(gas.isEmpty() ? Component.translatable("item.create_refinery_n_reactors.inert_gas_cylinder.empty", CAPACITY)
            : Component.translatable("item.create_refinery_n_reactors.inert_gas_cylinder.contents", gas.getHoverName(), gas.getAmount(), CAPACITY));
    }
}
