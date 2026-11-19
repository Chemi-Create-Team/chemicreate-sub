package net.createplant.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createplant.CreatePlant;
import net.createplant.item.SteamGunAction;
import net.createplant.item.SteamGunItem;
import net.createplant.registry.CEPortableTools;
import net.minecraft.client.KeyMapping;
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
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SteamGunClient {
    private static final KeyMapping BAYONET = new KeyMapping(SteamGunItem.BAYONET_KEY, InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V, "key.categories.create_refinery_n_reactors");

    private SteamGunClient() { }

    @SubscribeEvent public static void registerKey(RegisterKeyMappingsEvent event) { event.register(BAYONET); }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(CEPortableTools.STEAM_GUN.get(), CreatePlant.location("steam_gun_raised"),
            (stack, level, entity, seed) -> entity != null && entity.getMainHandItem() == stack
                && (SteamGunItem.aiming(entity) || thrusting(entity)) ? 1 : 0));
    }

    @SubscribeEvent public static void registerItemExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return hand == InteractionHand.MAIN_HAND && (SteamGunItem.aiming(entity) || thrusting(entity))
                    ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
            }

            @Override public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                float partialTick, float equipProgress, float swingProgress) {
                if (arm != player.getMainArm()) return false;
                var direction = arm == HumanoidArm.RIGHT ? 1 : -1;
                float aim = aimProgress(player, partialTick);
                float thrust = thrusting(player) ? thrustAmount(swingProgress) : 0;
                float kick = thrusting(player) ? 0 : Math.max(0, player.getCooldowns().getCooldownPercent(stack.getItem(), partialTick) - .8f) * .5f;
                pose.translate(direction * Mth.lerp(aim, .57f, 0f), Mth.lerp(aim, -.47f, -.29f) - equipProgress * .6f,
                    Mth.lerp(aim, -.76f, -.85f) - .43f * thrust + kick);
                pose.mulPose(Axis.YP.rotationDegrees(direction * Mth.lerp(aim, -12f, 0f)));
                pose.mulPose(Axis.XP.rotationDegrees(2 * thrust - 13 * kick));
                return true;
            }
        }, CEPortableTools.STEAM_GUN.get());
    }

    public static boolean thrusting(LivingEntity entity) {
        return entity.getMainHandItem().getItem() instanceof SteamGunItem && entity.swinging && entity.swingingArm == InteractionHand.MAIN_HAND;
    }

    public static float thrustAmount(float progress) { return Mth.sin(Mth.clamp(progress, 0, 1) * Mth.PI); }

    private static float aimProgress(LivingEntity entity, float partialTick) {
        return SteamGunItem.aiming(entity) ? Mth.clamp((entity.getTicksUsingItem() + partialTick) / 4f, 0, 1) : 0;
    }

    @EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
    public static final class Controls {
        private Controls() { }

        @SubscribeEvent public static void onClientTick(ClientTickEvent.Pre event) {
            var client = Minecraft.getInstance();
            boolean thrust = BAYONET.consumeClick();
            while (BAYONET.consumeClick()) { }
            var player = client.player;
            if (player == null || client.screen != null || client.getOverlay() != null || !player.isAlive() || player.isSpectator()
                || !(player.getMainHandItem().getItem() instanceof SteamGunItem)) return;
            // Vanilla discards queued attack clicks while the main hand is in use.
            boolean fire = client.options.keyAttack.consumeClick();
            while (client.options.keyAttack.consumeClick()) { }
            if (player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem())) return;
            if (thrust || fire) {
                PacketDistributor.sendToServer(new SteamGunAction(thrust));
            }
        }

        @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered event) {
            var client = Minecraft.getInstance();
            var player = client.player;
            if (player == null || player.isSpectator() || !(player.getMainHandItem().getItem() instanceof SteamGunItem)) return;
            if (event.isAttack()) {
                event.setSwingHand(false);
                event.setCanceled(true);
            } else if (event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND && !player.isShiftKeyDown() && client.gameMode != null) {
                client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        }

        @SubscribeEvent public static void aim(ComputeFovModifierEvent event) {
            if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                float aim = aimProgress(event.getPlayer(), 0);
                if (aim > 0) event.setNewFovModifier(event.getNewFovModifier() * Mth.lerp(aim, 1f, .65f));
            }
        }
    }
}
