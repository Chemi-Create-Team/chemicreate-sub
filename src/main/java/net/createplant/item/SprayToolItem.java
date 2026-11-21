package net.createplant.item;

import java.util.List;
import net.createplant.registry.CEProcessFluids;
import net.createplant.safety.SprayEffects;
import net.createplant.thermal.Grade;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class SprayToolItem extends PortableFluidToolItem {
    public enum Kind { EXTINGUISHER, FLAME, LIQUID }
    public static final int DOSE=20, FERTILIZER_DOSE=100, INTERVAL=4, SOUND_INTERVAL=2;
    public static final TagKey<Fluid> FUELS=TagKey.create(Registries.FLUID,net.createplant.CreatePlant.location("spray_fuels"));
    private final Kind kind;
    public SprayToolItem(Kind kind,Properties properties) { super(properties);this.kind=kind; }
    public Kind kind() { return kind; }
    @Override public int capacity() { return kind==Kind.EXTINGUISHER ? 2000 : 4000; }
    public double range() { return kind==Kind.EXTINGUISHER ? 6 : kind==Kind.FLAME ? 8 : 10; }
    @Override public boolean accepts(FluidStack fluid) {
        if(fluid.isEmpty() || net.createplant.mixture.MixtureHelper.isMixture(fluid) && !net.createplant.mixture.MixtureHelper.isMixableLiquid(fluid))return false;
        var properties=FluidHazards.of(fluid);
        if(properties.heat().compareTo(Grade.Heat.HOT)>0 || properties.heat()==Grade.Heat.CRYOGENIC)return false;
        boolean gas=properties.gas() || net.createplant.thermal.FluidState.gas(fluid);
        return switch(kind) {
            case EXTINGUISHER -> fluid.is(Fluids.WATER) || fluid.is(CEProcessFluids.CARBON_DIOXIDE.get());
            case FLAME -> !gas && fluid.is(FUELS);
            case LIQUID -> !gas && !fluid.is(Fluids.LAVA);
        };
    }

    @Override protected InteractionResultHolder<ItemStack> beginUse(Level level,Player player,InteractionHand hand,ItemStack stack) {
        var fluid=PortableToolFluidHandler.contents(stack);
        if(fluid.getAmount()<dose(fluid) || !accepts(fluid)) {
            if(!level.isClientSide())player.displayClientMessage(Component.translatable("tooltip.create_refinery_n_reactors.spray.empty"),true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(net.createplant.client.SprayToolClient.itemExtensions(kind));
    }
    @Override public void onUseTick(Level level,LivingEntity entity,ItemStack stack,int remaining) {
        int elapsed=getUseDuration(stack,entity)-remaining;
        if(!(level instanceof ServerLevel server) || !(entity instanceof Player player))return;
        if(elapsed%SOUND_INTERVAL==0)SprayEffects.sound(server,player,this);
        if(elapsed%INTERVAL!=0)return;
        var handler=new PortableToolFluidHandler(stack);var fluid=PortableToolFluidHandler.contents(stack);
        int amount=dose(fluid);
        if(fluid.getAmount()<amount || !accepts(fluid)){entity.stopUsingItem();return;}
        if (isFertilizer(fluid)) {
            if (SprayEffects.fertilize(server,player,stack,range()) && !player.getAbilities().instabuild)
                handler.drain(amount,FluidAction.EXECUTE);
            return;
        }
        if(!player.getAbilities().instabuild)handler.drain(amount,FluidAction.EXECUTE);
        SprayEffects.spray(server,player,stack,fluid.copyWithAmount(amount),this);
    }
    private int dose(FluidStack fluid) {
        return isFertilizer(fluid) ? FERTILIZER_DOSE : DOSE;
    }
    private boolean isFertilizer(FluidStack fluid) {
        return kind==Kind.LIQUID && fluid.is(CEProcessFluids.LIQUID_FERTILIZER.get());
    }
    @Override public int getBarColor(ItemStack stack) { return kind==Kind.FLAME ? 0xe78c35 : kind==Kind.EXTINGUISHER ? 0xd8e5df : 0x50aeb8; }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag) {
        tooltip.add(contentsText(stack));
        tooltip.add(Component.translatable("tooltip.create_refinery_n_reactors.spray.refill"));
        tooltip.add(Component.translatable("tooltip.create_refinery_n_reactors.spray.use"));
    }
}
