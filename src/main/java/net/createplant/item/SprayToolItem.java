package net.createplant.item;

import java.util.List;
import net.createplant.registry.CEProcessFluids;
import net.createplant.safety.SprayEffects;
import net.createplant.thermal.Grade;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class SprayToolItem extends Item {
    public enum Kind { EXTINGUISHER, FLAME, LIQUID }
    public static final int DOSE=20, INTERVAL=4, SOUND_INTERVAL=2;
    public static final TagKey<Fluid> FUELS=TagKey.create(Registries.FLUID,net.createplant.CreatePlant.location("spray_fuels"));
    private final Kind kind;
    public SprayToolItem(Kind kind,Properties properties) { super(properties);this.kind=kind; }
    public Kind kind() { return kind; }
    public int capacity() { return kind==Kind.EXTINGUISHER ? 2000 : 4000; }
    public double range() { return kind==Kind.EXTINGUISHER ? 6 : kind==Kind.FLAME ? 8 : 10; }
    public boolean accepts(FluidStack fluid) {
        if(fluid.isEmpty() || net.createplant.mixture.MixtureHelper.isMixture(fluid) && !net.createplant.mixture.MixtureHelper.isMixableLiquid(fluid))return false;
        var properties=FluidHazards.of(fluid);
        if(properties.heat().compareTo(Grade.Heat.HOT)>0 || properties.heat()==Grade.Heat.CRYOGENIC)return false;
        boolean gas=properties.gas() || net.createplant.thermal.FluidMaterials.gas(fluid.getFluid());
        return switch(kind) {
            case EXTINGUISHER -> fluid.is(Fluids.WATER) || fluid.is(CEProcessFluids.CARBON_DIOXIDE.get());
            case FLAME -> !gas && fluid.is(FUELS);
            case LIQUID -> !gas && !fluid.is(Fluids.LAVA);
        };
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        if(player.isShiftKeyDown()) {
            if(!context.getLevel().mayInteract(player,context.getClickedPos()) || !player.mayUseItemAt(context.getClickedPos(),context.getClickedFace(),context.getItemInHand()))return InteractionResult.FAIL;
            var tank=context.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,context.getClickedPos(),context.getClickedFace());
            if(tank==null)return InteractionResult.PASS;
            if(context.getLevel().isClientSide())return InteractionResult.SUCCESS;
            var result=FluidUtil.tryFillContainer(context.getItemInHand(),tank,capacity(),player,true);
            if(result.isSuccess()) {
                player.setItemInHand(context.getHand(),result.getResult());refillSound(player);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.FAIL;
        }
        return use(context.getLevel(),player,context.getHand()).getResult();
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(player.isShiftKeyDown()) {
            if(!level.isClientSide()) {
                var other=hand==InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                var source=FluidUtil.getFluidHandler(player.getItemInHand(other));
                if(source.isPresent()) {
                    var moved=FluidUtil.tryFluidTransfer(new SprayToolFluidHandler(stack),source.get(),capacity(),true);
                    if(!moved.isEmpty()){player.setItemInHand(other,source.get().getContainer());refillSound(player);}
                }
            }
            return InteractionResultHolder.sidedSuccess(stack,level.isClientSide());
        }
        var fluid=SprayToolFluidHandler.contents(stack);
        if(fluid.getAmount()<DOSE || !accepts(fluid)) {
            if(!level.isClientSide())player.displayClientMessage(Component.translatable("tooltip.create_refinery_n_reactors.spray.empty"),true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    private static void refillSound(Player player) { player.level().playSound(null,player.blockPosition(),SoundEvents.BUCKET_FILL,SoundSource.PLAYERS,.5f,1); }
    @Override public int getUseDuration(ItemStack stack,LivingEntity entity) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(net.createplant.client.SprayToolClient.itemExtensions(kind));
    }
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack,ItemStack newStack,boolean slotChanged) { return slotChanged || oldStack.getItem()!=newStack.getItem(); }
    @Override public void onUseTick(Level level,LivingEntity entity,ItemStack stack,int remaining) {
        int elapsed=getUseDuration(stack,entity)-remaining;
        if(!(level instanceof ServerLevel server) || !(entity instanceof Player player))return;
        if(elapsed%SOUND_INTERVAL==0)SprayEffects.sound(server,player,this);
        if(elapsed%INTERVAL!=0)return;
        var handler=new SprayToolFluidHandler(stack);var fluid=handler.drain(DOSE,FluidAction.SIMULATE);
        if(fluid.getAmount()!=DOSE || !accepts(fluid)){entity.stopUsingItem();return;}
        if(!player.getAbilities().instabuild)handler.drain(DOSE,FluidAction.EXECUTE);
        SprayEffects.spray(server,player,stack,fluid,this);
    }
    @Override public boolean isBarVisible(ItemStack stack) { return !SprayToolFluidHandler.contents(stack).isEmpty(); }
    @Override public int getBarWidth(ItemStack stack) { return Math.clamp(Math.round(13f*SprayToolFluidHandler.contents(stack).getAmount()/capacity()),0,13); }
    @Override public int getBarColor(ItemStack stack) { return kind==Kind.FLAME ? 0xe78c35 : kind==Kind.EXTINGUISHER ? 0xd8e5df : 0x50aeb8; }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag) {
        var fluid=SprayToolFluidHandler.contents(stack);
        tooltip.add(Component.translatable("tooltip.create_refinery_n_reactors.spray.contents",fluid.isEmpty() ? Component.translatable("tooltip.create_refinery_n_reactors.spray.no_fluid") : fluid.getHoverName(),fluid.getAmount(),capacity()));
        tooltip.add(Component.translatable("tooltip.create_refinery_n_reactors.spray.refill"));
        tooltip.add(Component.translatable("tooltip.create_refinery_n_reactors.spray.use"));
    }
}
