package net.createplant.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createplant.mixture.MixtureHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;
import net.neoforged.neoforge.fluids.FluidStack;

public final class TankLayerColors {
    private static final int[] PALETTE = {0xDD8954, 0x80BBE7, 0xC29AE2, 0x94C978, 0xDECA76, 0xE294AB};

    private TankLayerColors() { }

    public static FluidStack surface(FluidStack layer) {
        return layer;
    }

    public static VertexConsumer tint(VertexConsumer target, FluidStack layer, int index, boolean separated) {
        boolean gas = MixtureHelper.gas(layer);
        if (!gas && (!separated || layer.is(Fluids.WATER))) return target;
        int tint = gas ? (separated ? PALETTE[index % PALETTE.length]
            : IClientFluidTypeExtensions.of(layer.getFluid()).getTintColor(layer)) : PALETTE[(index + 3) % PALETTE.length];
        int red = tint >> 16 & 255, green = tint >> 8 & 255, blue = tint & 255;
        return new VertexConsumerWrapper(target) {
            @Override public VertexConsumer setColor(int r, int g, int b, int a) {
                if (gas) parent.setColor((r+red)/2, (g+green)/2, (b+blue)/2, Math.min(a,150));
                else parent.setColor((2 * r + red) / 3, (2 * g + green) / 3, (2 * b + blue) / 3, Math.max(180, a));
                return this;
            }
        };
    }
}
