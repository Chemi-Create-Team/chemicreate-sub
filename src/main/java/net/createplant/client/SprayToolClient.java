package net.createplant.client;

import net.createplant.CreatePlant;
import net.createplant.item.SprayToolItem;
import net.createplant.registry.CEPortableTools;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

@EventBusSubscriber(modid=CreatePlant.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class SprayToolClient {
    private static BlockEntityWithoutLevelRenderer sprayer;
    private SprayToolClient() { }
    public static IClientItemExtensions itemExtensions(SprayToolItem.Kind kind) {
        return new IClientItemExtensions() {
            @Override public HumanoidModel.ArmPose getArmPose(LivingEntity entity,InteractionHand hand,ItemStack stack) {
                return entity.isUsingItem() && entity.getUsedItemHand()==hand && kind!=SprayToolItem.Kind.EXTINGUISHER ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
            }
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if(kind!=SprayToolItem.Kind.LIQUID)return IClientItemExtensions.super.getCustomRenderer();
                if(sprayer==null)sprayer=new SprayerItemRenderer();
                return sprayer;
            }
        };
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()->{
            for(var tool:java.util.List.of(CEPortableTools.FIRE_EXTINGUISHER,CEPortableTools.FLAMETHROWER,CEPortableTools.CHEMICAL_SPRAYER))
                ItemProperties.register(tool.get(),CreatePlant.location("spraying"),(stack,level,entity,seed)->entity!=null && entity.isUsingItem() && entity.getUseItem()==stack ? 1 : 0);
        });
    }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        event.getModels().computeIfPresent(ModelResourceLocation.inventory(CreatePlant.location("chemical_sprayer")),(location,model)->new SprayerItemRenderer.Model(model));
    }

    /** Spraying does not slow the player: the input is raised by the same factor that using an item then takes off it. */
    @EventBusSubscriber(modid=CreatePlant.MOD_ID,value=Dist.CLIENT)
    public static final class Movement {
        private Movement() { }
        @SubscribeEvent public static void input(MovementInputUpdateEvent event) {
            var player=event.getEntity();
            if(!player.isUsingItem() || player.isPassenger() || !(player.getUseItem().getItem() instanceof SprayToolItem))return;
            event.getInput().leftImpulse*=5;
            event.getInput().forwardImpulse*=5;
        }
    }
}
