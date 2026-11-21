package net.createplant.client;

import net.createplant.CreatePlant;
import net.createplant.item.StatefulBuckets;
import net.createplant.thermal.FluidState;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

@EventBusSubscriber(modid=CreatePlant.MOD_ID,value=Dist.CLIENT)
public final class FluidStateVisuals {
    public static final ResourceLocation VAPOUR=CreatePlant.location("block/fluid_state/vapour");
    public static final ModelResourceLocation LIQUID_BUCKET=ModelResourceLocation.standalone(CreatePlant.location("item/state_liquid_bucket"));
    public static final ModelResourceLocation GAS_BUCKET=ModelResourceLocation.standalone(CreatePlant.location("item/state_gas_bucket"));

    private FluidStateVisuals() { }

    public static int tint(FluidStack stack,int original) {
        if(stack.isEmpty())return original;
        int temperature=FluidState.temperature(stack);
        boolean gas=FluidState.gas(stack);
        int rgb=original&0xFFFFFF;
        if(gas && stack.is(net.minecraft.world.level.material.Fluids.WATER))rgb=0xEEF4FF;
        if(temperature>8)rgb=blend(rgb,0xFF5841,(temperature-8)*.095);
        if(temperature<8)rgb=blend(rgb,0xF1F9FF,(8-temperature)*.105);
        return (gas ? 140 : original>>>24)<<24 | rgb;
    }

    private static int blend(int a,int b,double fraction) {
        int rgb=0;
        for(int shift=0;shift<24;shift+=8) {
            int x=(a>>shift)&255,y=(b>>shift)&255;
            rgb|=(int)Math.round(x+(y-x)*fraction)<<shift;
        }
        return rgb;
    }

    public static ResourceLocation texture(FluidStack stack,ResourceLocation fallback) {
        return FluidState.gas(stack) ? VAPOUR : fallback;
    }

    public static int luminosity(FluidStack stack,int original) {
        return stack.isEmpty() ? original : Math.max(original,(FluidState.temperature(stack)-12)*2);
    }

    public static boolean hasRenderingExtension(FluidStack stack) {
        var fluid=stack.getFluid();
        return net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid).getNamespace().equals(CreatePlant.MOD_ID)
            && !(fluid instanceof net.createplant.mixture.MixtureFluid);
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        event.register(LIQUID_BUCKET);
        event.register(GAS_BUCKET);
    }

    @SubscribeEvent
    public static void colors(RegisterColorHandlersEvent.Item event) {
        var buckets=net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(item->item instanceof BucketItem bucket
            && (item==net.minecraft.world.item.Items.WATER_BUCKET || net.createplant.thermal.FluidIdentity.controlled(bucket.content))).toArray(net.minecraft.world.item.Item[]::new);
        event.register((item,index)->{
            if(index!=1 || !(item.getItem() instanceof BucketItem bucket))return -1;
            var fluid=StatefulBuckets.contents(item,bucket.content);
            var extensions=IClientFluidTypeExtensions.of(fluid.getFluid());
            int base=extensions.getTintColor(fluid);
            return (hasRenderingExtension(fluid) ? base : tint(fluid,base))|0xFF000000;
        },buckets);
    }
}
