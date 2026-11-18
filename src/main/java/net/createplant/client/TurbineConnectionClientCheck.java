package net.createplant.client;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.block.TurbineUnit;
import net.createplant.registry.CEItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in network placement and native inventory-model fixture in a disposable world. */
@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class TurbineConnectionClientCheck {
    private static final BlockPos ORIGIN = new BlockPos(0, 84, 0);
    private static int phase, ticks, sections = 1;

    private TurbineConnectionClientCheck() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("createplant.turbineConnectionClientCheck")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        if (phase == 0 && mc.screen instanceof TitleScreen && ++ticks > 60) {
            phase = 1;mc.options.pauseOnLostFocus = false;
            mc.createWorldOpenFlows().createFreshLevel("turbine-connection-" + System.currentTimeMillis(),
                new net.minecraft.world.level.LevelSettings("Turbine connection check", net.minecraft.world.level.GameType.CREATIVE, false,
                    net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                new net.minecraft.world.level.levelgen.WorldOptions(42L, false, false),
                access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                    .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (phase == 1 && mc.player != null && mc.level != null) {
            phase = 2;ticks = 0;mc.setScreen(null);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                level.setDayTime(6000);
                for (int x = -3; x < 15; x++) for (int z = -6; z < 8; z++)
                    level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState());
                var unit = new TurbineUnit.Layout(ORIGIN, Direction.Axis.X, TurbineUnit.Stage.LOW);
                for (var pos : unit.volume()) level.getChunkAt(pos);
                if (!TurbineUnit.place(level, unit, null, ItemStack.EMPTY)) throw new IllegalStateException("Initial LP section failed");
                player.getInventory().clearContent();player.getInventory().setItem(0, new ItemStack(CEItems.LOW_PRESSURE_TURBINE_UNIT.get(), 16));
                player.getAbilities().flying = true;player.onUpdateAbilities();
                player.connection.teleport(3.5, 82, 6.5, 90, 0);
            });
        }
        if (phase == 2 && ++ticks > 80) { phase = 3;ticks = 0; }
        if (phase == 3 && ++ticks > 20) {
            var clicked = ORIGIN.east(sections - 1).offset(0, -2, 4);
            phase = 4;ticks = 0;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(clicked.getX() + .5, clicked.getY() + .5, clicked.getZ() + 1), Direction.SOUTH, clicked, false));
        }
        if (phase == 4) {
            if (++ticks > 600) throw new IllegalStateException("LP extension packet timed out at section " + sections);
            var next = TurbineUnit.at(mc.level, ORIGIN.east(sections));
            if (next == null) return;
            if (next.axis() != Direction.Axis.X || next.origin().getY() != ORIGIN.getY())
                throw new IllegalStateException("LP extension changed axis or height");
            sections++;ticks = 0;phase = sections == 10 ? 5 : 3;
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                player.connection.teleport(sections + 2.5, 82, 6.5, 90, 0);
                if (sections == 10) {
                    var first = TurbineUnit.at(server.overworld(), ORIGIN);
                    if (TurbineUnit.assembly(server.overworld(), first).size() != 10)
                        throw new IllegalStateException("The actual server did not form ten LP sections");
                    mc.execute(() -> showIcons(mc));
                }
            });
        }
    }

    private static void showIcons(Minecraft mc) {
        var icons = List.of(CEItems.HIGH_PRESSURE_TURBINE_UNIT.get(), CEItems.LOW_PRESSURE_TURBINE_UNIT.get(),
            CEItems.HIGH_PRESSURE_TURBINE_CASING.get(), CEItems.LOW_PRESSURE_TURBINE_CASING.get(),
            CEItems.TARBIN_END.get(), CEItems.LOW_PRESSURE_TURBINE_END.get(), CEItems.TRIIRON_TETROXIDE_PILE.get());
        var inventory = new SimpleContainer(54);
        int[] slots = {10, 12, 19, 21, 28, 30, 37};
        for (int i = 0; i < icons.size(); i++) {
            var stack = icons.get(i).getDefaultInstance();
            var model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
            if (model == mc.getModelManager().getMissingModel() || i < 6 && !model.isCustomRenderer())
                throw new IllegalStateException("Item does not resolve its actual geometry: " + stack);
            inventory.setItem(slots[i], stack);
        }
        var menu = ChestMenu.sixRows(123, mc.player.getInventory(), inventory);
        mc.player.containerMenu = menu;
        mc.setScreen(new ContainerScreen(menu, mc.player.getInventory(), Component.literal("タービンの外観")));
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("turbine-connection-client-check.json"),
                new com.google.gson.Gson().toJson(Map.of("passed", true, "lpSections", 10, "networkExtensions", 9,
                    "modelCount", icons.size(), "preservedAxis", "X", "preservedCenterY", ORIGIN.getY())) + System.lineSeparator());
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
        org.slf4j.LoggerFactory.getLogger(TurbineConnectionClientCheck.class).info("TURBINE CONNECTION CLIENT CHECK PASSED");
    }
}
