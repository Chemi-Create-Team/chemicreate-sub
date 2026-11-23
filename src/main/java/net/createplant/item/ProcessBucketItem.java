package net.createplant.item;

import net.createplant.chemistry.WoodCarbonization;
import net.createplant.registry.CECarbonization;
import net.createplant.registry.CEWorldFluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.fluids.FluidStack;

public final class ProcessBucketItem extends BucketItem {
    public ProcessBucketItem(Fluid fluid, Properties properties) { super(fluid, properties); }

    public boolean alloy() { return CEWorldFluids.acceptsAlloyBucket(new FluidStack(content, 1000)); }

    public ItemStack emptyBucket(ItemStack filled) {
        boolean alloyShell = alloy() && !filled.has(CEWorldFluids.IRON_BUCKET_SHELL.get());
        return StainlessBuckets.emptied(filled, new ItemStack(alloyShell ? CEWorldFluids.EMPTY_ALLOY_BUCKET.get() : Items.BUCKET));
    }

    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }
    @Override public ItemStack getCraftingRemainingItem(ItemStack stack) { return emptyBucket(stack); }

    public FluidStack contents(ItemStack stack) {
        return StatefulBuckets.contents(stack,content);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (BuiltInRegistries.FLUID.getKey(content).getPath().equals("sulfuric_acid")) {
            BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
            if (hit.getType() == HitResult.Type.BLOCK && level.mayInteract(player, hit.getBlockPos())
                && player.mayUseItemAt(hit.getBlockPos(), hit.getDirection(), held)
                && WoodCarbonization.accepts(level.getBlockState(hit.getBlockPos()))) {
                if (level.isClientSide) return InteractionResultHolder.success(held);
                if (WoodCarbonization.tryStart(level, hit.getBlockPos())) {
                    level.updateNeighborsAt(hit.getBlockPos(), CECarbonization.CHARRED_WOOD.get());
                    playEmptySound(player, level, hit.getBlockPos());
                    return InteractionResultHolder.success(player.hasInfiniteMaterials() ? held : emptyBucket(held));
                }
                return InteractionResultHolder.fail(held);
            }
        }
        InteractionResultHolder<ItemStack> result = super.use(level, player, hand);
        if (alloy() && result.getResult().consumesAction() && result.getObject().is(Items.BUCKET))
            return new InteractionResultHolder<>(result.getResult(), emptyBucket(held));
        return result;
    }

}
