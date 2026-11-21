package net.createplant.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.createplant.CreatePlant;
import net.createplant.transport.FluidHazards;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** A fluid type with its source and flowing forms, drawn with the tinted water textures. */
public final class PlantFluid {
    private static final List<PlantFluid> ALL = new ArrayList<>();
    final DeferredHolder<FluidType, FluidType> type;
    final DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
    private final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
    private final int color;
    private final CEWorldFluids.Entry world;
    private final Supplier<? extends net.minecraft.world.item.Item> bucket;

    PlantFluid(DeferredRegister<FluidType> types, DeferredRegister<Fluid> fluids, String name, int color,
        Supplier<FluidType.Properties> typeProperties, UnaryOperator<BaseFlowingFluid.Properties> fluidProperties) {
        this.color = color;
        type = types.register(name, () -> new FluidType(typeProperties.get().descriptionId("fluid." + CreatePlant.MOD_ID + "." + name).canConvertToSource(false)) {
            @Override public ItemStack getBucket(FluidStack stack) {
                if (world != null) return CEWorldFluids.bucket(stack);
                var result = new ItemStack(bucket.get());
                result.set(CEWorldFluids.BUCKET_CONTENTS.get(), net.createplant.item.BucketFluidContents.of(stack));
                return result;
            }
        });
        source = fluids.register(name, () -> new BaseFlowingFluid.Source(configure(fluidProperties)));
        flowing = fluids.register("flowing_" + name,
            () -> new BaseFlowingFluid.Flowing(configure(fluidProperties)));
        world = FluidHazards.named(name).gas() ? null : CEWorldFluids.define(name, color, source);
        bucket = world == null ? CEGasBuckets.define(name, source) : world.bucket();
        ALL.add(this);
    }

    private BaseFlowingFluid.Properties configure(UnaryOperator<BaseFlowingFluid.Properties> configure) {
        BaseFlowingFluid.Properties properties = configure.apply(properties());
        properties.bucket(bucket);
        if (world != null) properties.block(world.block());
        return properties;
    }

    private BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(type, source, flowing);
    }

    @EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ClientExtensions {
        @SubscribeEvent
        public static void register(RegisterClientExtensionsEvent event) {
            for (PlantFluid fluid : ALL) {
                event.registerFluidType(new IClientFluidTypeExtensions() {
                    @Override public ResourceLocation getStillTexture() {
                        return CEWetProcessing.slurry(fluid.source.get()) ? CreatePlant.location("block/wet_processing/slurry_still") : ResourceLocation.withDefaultNamespace("block/water_still");
                    }
                    @Override public ResourceLocation getFlowingTexture() {
                        return CEWetProcessing.slurry(fluid.source.get()) ? CreatePlant.location("block/wet_processing/slurry_flow") : ResourceLocation.withDefaultNamespace("block/water_flow");
                    }
                    @Override public int getTintColor() { return fluid.color; }
                    @Override public int getTintColor(FluidStack stack) { return net.createplant.client.FluidStateVisuals.tint(stack,fluid.color); }
                    @Override public ResourceLocation getStillTexture(FluidStack stack) { return net.createplant.client.FluidStateVisuals.texture(stack,getStillTexture()); }
                    @Override public ResourceLocation getFlowingTexture(FluidStack stack) { return net.createplant.client.FluidStateVisuals.texture(stack,getFlowingTexture()); }
                }, fluid.type);
            }
        }
    }
}
