package net.createplant.item;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public record BucketFluidContents(Holder<Fluid> fluid, DataComponentPatch components) {
    public static final Codec<BucketFluidContents> CODEC = FluidStack.fixedAmountCodec(1000)
        .xmap(BucketFluidContents::of, BucketFluidContents::stack);
    public static final StreamCodec<RegistryFriendlyByteBuf, BucketFluidContents> STREAM_CODEC = FluidStack.STREAM_CODEC
        .map(BucketFluidContents::of, BucketFluidContents::stack);

    public static BucketFluidContents of(FluidStack stack) {
        return new BucketFluidContents(stack.getFluidHolder(), stack.getComponentsPatch());
    }

    public FluidStack stack() { return new FluidStack(fluid, 1000, components); }
}
