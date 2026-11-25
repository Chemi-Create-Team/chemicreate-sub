package net.createplant.item;

import net.createplant.cloud.GasClouds;
import net.createplant.registry.CEWorldFluids;
import net.createplant.thermal.FluidState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.jetbrains.annotations.Nullable;

public final class StatefulBuckets {
    private StatefulBuckets() { }

    public static boolean emptyProcessBucket(ItemStack stack) {
        return stack.is(CEWorldFluids.STAINLESS_BUCKET.get()) || stack.is(CEWorldFluids.EMPTY_ALLOY_BUCKET.get());
    }

    public static ItemInteractionResult fillFromTank(ItemStack stack, Player player, InteractionHand hand, @Nullable IFluidHandler source) {
        if (source == null) return ItemInteractionResult.FAIL;
        boolean client = player.level().isClientSide;
        boolean creative = player.hasInfiniteMaterials();
        var transfer = creative
            ? FluidUtil.tryFillContainer(stack, source, FluidType.BUCKET_VOLUME, player, false)
            : FluidUtil.tryFillContainerAndStow(stack, source, new PlayerMainInvWrapper(player.getInventory()), FluidType.BUCKET_VOLUME, player, !client);
        if (!transfer.isSuccess()) return ItemInteractionResult.FAIL;
        if (!client) player.setItemInHand(hand, creative ? ItemUtils.createFilledResult(stack, player, transfer.getResult()) : transfer.getResult());
        return ItemInteractionResult.sidedSuccess(client);
    }

    public static FluidStack contents(ItemStack container,Fluid nominal) {
        var saved=container.get(CEWorldFluids.BUCKET_CONTENTS.get());
        return saved!=null && nominal.isSame(saved.fluid().value()) ? saved.stack() : new FluidStack(nominal,1000);
    }

    public static boolean defaultState(FluidStack stack) {
        FluidStack nominal=new FluidStack(stack.getFluid(),1);
        return FluidState.temperature(stack)==FluidState.temperature(nominal) && FluidState.pressure(stack)==FluidState.pressure(nominal);
    }

    public static ItemStack write(FluidStack fluid,ItemStack item) {
        if(!item.isEmpty() && !fluid.isEmpty() && !fluid.getComponentsPatch().isEmpty()) {
            var contents=net.createplant.mixture.MixtureHelper.portion(fluid,1000);
            item.set(CEWorldFluids.BUCKET_CONTENTS.get(),BucketFluidContents.of(contents));
        }
        return item;
    }

    public static boolean release(Level level,BlockPos pos,FluidStack fluid) {
        if(!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir())return false;
        if(level instanceof ServerLevel server) {
            net.createplant.nuclear.RadiationContamination.spill(server,pos,fluid);
            GasClouds.release(server,pos,fluid);
        }
        return true;
    }
}
