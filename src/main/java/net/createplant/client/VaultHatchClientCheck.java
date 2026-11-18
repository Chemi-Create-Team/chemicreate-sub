package net.createplant.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import net.createplant.CreatePlant;
import net.createplant.block.VaultHatchBlock;
import net.createplant.inventory.VaultHatchMenu;
import net.createplant.registry.CEVault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in real-client fixture; leaves the world available for inspecting the hatch and its Ponder. */
@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class VaultHatchClientCheck {
    private static final BlockPos ORIGIN = new BlockPos(0, 80, 0);
    private static int phase, ticks;
    private VaultHatchClientCheck() { }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("createplant.vaultHatchClientCheck")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        if (phase == 0 && mc.screen instanceof TitleScreen && ++ticks > 60) {
            phase = 1;
            mc.options.pauseOnLostFocus = false;
            mc.createWorldOpenFlows().createFreshLevel("vault-hatch-check-" + System.currentTimeMillis(),
                new net.minecraft.world.level.LevelSettings("Vault hatch check", net.minecraft.world.level.GameType.CREATIVE, false,
                    net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                new net.minecraft.world.level.levelgen.WorldOptions(42L, false, false),
                access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                    .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (phase == 1 && mc.player != null && mc.level != null) {
            phase = 2;ticks = 0;
            mc.setScreen(null);
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                level.setDayTime(6000);
                for (int x = -4; x < 8; x++) for (int z = -5; z < 6; z++)
                    level.setBlockAndUpdate(ORIGIN.offset(x, -2, z), Blocks.STONE.defaultBlockState());
                for (int x = 0; x < 3; x++) level.setBlockAndUpdate(ORIGIN.offset(x, 0, 0),
                    AllBlocks.ITEM_VAULT.getDefaultState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, Direction.Axis.X));
                for (Direction face : Direction.values()) {
                    BlockPos support = face == Direction.EAST ? ORIGIN.offset(2, 0, 0) : face == Direction.WEST ? ORIGIN : ORIGIN.offset(1, 0, 0);
                    level.setBlockAndUpdate(support.relative(face), CEVault.HATCH.get().defaultBlockState().setValue(VaultHatchBlock.FACING, face));
                }
                player.getAbilities().flying = true;player.onUpdateAbilities();
                player.getInventory().clearContent();
                player.connection.teleport(1.5, 80, -4, 0, 18);
            });
        }
        if (phase == 2 && ++ticks == 60) {
            phase = 3;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getToasts().clear();
            mc.getSingleplayerServer().execute(() -> {
                var hatch = (net.createplant.block.entity.VaultHatchBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(ORIGIN.offset(1, 0, -1));
                var inventory = VaultHatchMenu.inventoryAt(hatch);
                var samples = new net.minecraft.world.item.Item[]{Items.IRON_INGOT, Items.COPPER_INGOT, Items.GOLD_INGOT, Items.REDSTONE, Items.QUARTZ, Items.COAL, Items.DIAMOND, Items.LAPIS_LAZULI, Items.EMERALD};
                for (int i = 0; i < inventory.getSlots(); i++) inventory.setStackInSlot(i, new ItemStack(samples[i % samples.length], i % 64 + 1));
                var named = new ItemStack(Items.DIAMOND, 13);
                named.set(DataComponents.CUSTOM_NAME, Component.literal("Second page sample"));
                inventory.setStackInSlot(54, named);
            });
        }
        if (phase == 3 && ++ticks > 100) {
            phase = 4;
            BlockPos at = ORIGIN.offset(1, 0, -1);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
        }
    }
}
