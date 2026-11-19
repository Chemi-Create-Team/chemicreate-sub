package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createplant.CreatePlant;
import net.createplant.item.SteamHammerAction;
import net.createplant.item.SteamHammerItem;
import net.createplant.registry.CEPortableTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SteamHammerClient {
    private SteamHammerClient() { }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(CEPortableTools.STEAM_HAMMER.get(), CreatePlant.location("steam_hammer_guard"),
            (stack, level, entity, seed) -> entity != null && SteamHammerItem.guarding(entity) && entity.getUseItem() == stack ? 1 : 0));
    }

    @SubscribeEvent public static void registerExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                if (SteamHammerItem.guarding(entity) && entity.getUsedItemHand() == hand) return HumanoidModel.ArmPose.BLOCK;
                return hand == InteractionHand.MAIN_HAND && swinging(entity) ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
            }

            @Override public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                float partialTick, float equipProgress, float swingProgress) {
                int sign = arm == HumanoidArm.RIGHT ? 1 : -1;
                if (SteamHammerItem.guarding(player) && player.getUseItem() == stack) {
                    pose.translate(sign * .25, -.37 - .6 * equipProgress, -.76);
                    pose.mulPose(Axis.YP.rotationDegrees(-sign * 22));
                    pose.mulPose(Axis.ZP.rotationDegrees(sign * 57));
                    return true;
                }
                if (arm != player.getMainArm()) return false;
                float wind = windup(swingProgress), strike = downswing(swingProgress);
                pose.translate(sign * (.56 - .20 * strike), -.51 + .23 * wind - .21 * strike - equipProgress * .6, -.86 - .26 * strike);
                pose.mulPose(Axis.YP.rotationDegrees(-sign * (14 + 14 * strike)));
                pose.mulPose(Axis.XP.rotationDegrees(-46 * wind + 76 * strike));
                pose.mulPose(Axis.ZP.rotationDegrees(-sign * (15 + 12 * wind - 13 * strike)));
                return true;
            }
        }, CEPortableTools.STEAM_HAMMER.get());
    }

    public static boolean swinging(LivingEntity entity) {
        return entity.getMainHandItem().getItem() instanceof SteamHammerItem && entity.swinging && entity.swingingArm == InteractionHand.MAIN_HAND;
    }

    public static float windup(float progress) { return Mth.sin(Mth.clamp(progress / .47f, 0, 1) * Mth.PI); }
    public static float downswing(float progress) { return Mth.sin(Mth.clamp((progress - .32f) / .68f, 0, 1) * Mth.PI); }

    @EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
    public static final class Controls {
        private Controls() { }

        @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
            var client = Minecraft.getInstance();
            var player = client.player;
            if (player == null || client.screen != null || client.getOverlay() != null || !player.isAlive() || player.isSpectator()
                || !(player.getMainHandItem().getItem() instanceof SteamHammerItem)) return;
            boolean swing = client.options.keyAttack.consumeClick();
            while (client.options.keyAttack.consumeClick()) { }
            if (swing && !player.isUsingItem() && !player.hasEffect(CEPortableTools.DAZED) && player.getAttackStrengthScale(.5f) >= 1) {
                player.resetAttackStrengthTicker();
                PacketDistributor.sendToServer(SteamHammerAction.SWING);
            }
        }

        @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered event) {
            var client = Minecraft.getInstance();
            var player = client.player;
            if (player == null || player.isSpectator() || !(player.getMainHandItem().getItem() instanceof SteamHammerItem)) return;
            if (event.isAttack()) {
                event.setSwingHand(false);
                event.setCanceled(true);
            } else if (event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND && !player.isShiftKeyDown() && client.gameMode != null) {
                client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        }

        @SubscribeEvent public static void dazed(MovementInputUpdateEvent event) {
            if (!event.getEntity().hasEffect(CEPortableTools.DAZED)) return;
            event.getInput().leftImpulse = 0;
            event.getInput().forwardImpulse = 0;
            event.getInput().jumping = false;
        }
    }
}
