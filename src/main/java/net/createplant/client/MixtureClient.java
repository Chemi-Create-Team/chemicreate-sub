package net.createplant.client;

import java.util.LinkedHashMap;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.mixture.*;
import net.createplant.registry.CEMixtures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.*;
import net.neoforged.neoforge.fluids.FluidStack;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MixtureClient {
    private record ColorKey(MixtureContents contents, MixtureData data) { }
    private static final Map<ColorKey, Integer> COLORS = new LinkedHashMap<>();
    private MixtureClient() { }
    public static int tint(FluidStack stack) {
        if (!MixtureHelper.isMixableLiquid(stack) && !MixtureHelper.isMixableGas(stack)) return 0xff3f76e4;
        var key = new ColorKey(MixtureHelper.extractContents(stack), MixtureHelper.data(stack));
        synchronized (COLORS) {
            var known = COLORS.get(key);if (known != null) return known;
            long red = 0, green = 0, blue = 0;
            for (var component : MixtureHelper.components(stack)) {
                int color = IClientFluidTypeExtensions.of(component.getFluid()).getTintColor(component);
                if ((color & 0xffffff) == 0xffffff && component.getFluid().isSame(Fluids.WATER)) color = 0xff3f76e4;
                int amount = component.getAmount();red += (long) ((color >> 16) & 255) * amount;green += (long) ((color >> 8) & 255) * amount;blue += (long) (color & 255) * amount;
            }
            int total = stack.getAmount();int color = 0xff000000 | (int) (red / total) << 16 | (int) (green / total) << 8 | (int) (blue / total);
            if (COLORS.size() >= 256) COLORS.remove(COLORS.keySet().iterator().next());COLORS.put(key, color);return color;
        }
    }
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return ResourceLocation.withDefaultNamespace("block/water_still"); }
            @Override public ResourceLocation getFlowingTexture() { return ResourceLocation.withDefaultNamespace("block/water_flow"); }
            @Override public int getTintColor(FluidStack stack) { return tint(stack); }
        }, CEMixtures.TYPE.get(), CEMixtures.GAS_TYPE.get());
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return ResourceLocation.withDefaultNamespace("block/water_still"); }
            @Override public ResourceLocation getFlowingTexture() { return ResourceLocation.withDefaultNamespace("block/water_flow"); }
            @Override public int getTintColor() { return 0x88C8C8D0; }
        }, CEMixtures.OTHER_GASES_TYPE.get());
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> { synchronized (COLORS) { COLORS.clear(); } });
    }
}
