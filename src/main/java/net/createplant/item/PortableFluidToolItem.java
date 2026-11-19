package net.createplant.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

public abstract class PortableFluidToolItem extends Item {
    protected PortableFluidToolItem(Properties properties) { super(properties); }

    public abstract int capacity();
    public abstract boolean accepts(FluidStack fluid);

    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.isShiftKeyDown()) return use(context.getLevel(), player, context.getHand()).getResult();
        var level = context.getLevel();
        var position = context.getClickedPos();
        if (!level.mayInteract(player, position) || !player.mayUseItemAt(position, context.getClickedFace(), context.getItemInHand()))
            return InteractionResult.FAIL;
        var source = level.getCapability(Capabilities.FluidHandler.BLOCK, position, context.getClickedFace());
        if (source == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var result = FluidUtil.tryFillContainer(context.getItemInHand(), source, capacity(), player, true);
        if (!result.isSuccess()) return InteractionResult.FAIL;
        player.setItemInHand(context.getHand(), result.getResult());
        refillSound(player);
        return InteractionResult.SUCCESS;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return beginUse(level, player, hand, stack);
        if (!level.isClientSide()) {
            var other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            FluidUtil.getFluidHandler(player.getItemInHand(other)).ifPresent(source -> {
                var moved = FluidUtil.tryFluidTransfer(new PortableToolFluidHandler(stack), source, capacity(), true);
                if (!moved.isEmpty()) {
                    player.setItemInHand(other, source.getContainer());
                    refillSound(player);
                }
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    protected abstract InteractionResultHolder<ItemStack> beginUse(Level level, Player player, InteractionHand hand, ItemStack stack);

    private static void refillSound(Player player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, .5f, 1);
    }

    protected Component contentsText(ItemStack stack) {
        var fluid = PortableToolFluidHandler.contents(stack);
        return Component.translatable("tooltip.create_refinery_n_reactors.spray.contents",
            fluid.isEmpty() ? Component.translatable("tooltip.create_refinery_n_reactors.spray.no_fluid") : fluid.getHoverName(), fluid.getAmount(), capacity());
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }
    @Override public boolean isBarVisible(ItemStack stack) { return !PortableToolFluidHandler.contents(stack).isEmpty(); }
    @Override public int getBarWidth(ItemStack stack) {
        return Math.clamp(Math.round(13f * PortableToolFluidHandler.contents(stack).getAmount() / capacity()), 0, 13);
    }
}
