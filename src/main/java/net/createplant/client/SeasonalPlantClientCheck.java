package net.createplant.client;

import net.createplant.thermal.FluidForm;
import com.simibubi.create.AllItems;
import net.createplant.CreatePlant;
import net.createplant.block.CoolingTowerStructure;
import net.createplant.block.entity.CoolingTowerBlockEntity;
import net.createplant.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** Opt-in, disposable visual fixture; its water source and sink never run in normal worlds. */
@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class SeasonalPlantClientCheck {
    private static int phase, ticks;
    private static volatile boolean built;
    private static final java.util.List<BlockPos> ROOTS = java.util.List.of(new BlockPos(0, 80, 12), new BlockPos(38, 80, 12));
    private SeasonalPlantClientCheck() { }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("createplant.seasonalPlantClientCheck")) return;
        var mc = Minecraft.getInstance();if (mc.getOverlay() != null) return;
        if (phase == 3) { ClientQualityCheck.tick(event);return; }
        if (phase == 2) {
            if (built && ++ticks % 4 == 0) mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();
                for (var at : ROOTS) if (level.getBlockEntity(at) instanceof CoolingTowerBlockEntity tower && tower.formed()) {
                    tower.tank(0).fill(FluidForm.WARM_WATER.stack(16_000_000), EXECUTE);tower.tank(1).drain(16_000_000, EXECUTE);
                }
            });
            if (mc.screen == null && com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_P)) phase = 3;
        }
        if (phase == 0 && mc.screen instanceof TitleScreen && ++ticks > 60) {
            phase = 1;mc.options.pauseOnLostFocus = false;
            mc.createWorldOpenFlows().createFreshLevel("seasonal-plant-" + System.currentTimeMillis(),
                new net.minecraft.world.level.LevelSettings("Seasonal plant visual check", net.minecraft.world.level.GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                new net.minecraft.world.level.levelgen.WorldOptions(42L, false, false), access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (phase == 1 && mc.level != null && mc.player != null) {
            phase = 2;ticks = 0;mc.setScreen(null);mc.options.hideGui = true;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();level.setDayTime(6000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                for (int x = -12; x <= 50; x++) for (int z = 8; z <= 32; z++) level.setBlock(new BlockPos(x, 78, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (var root : ROOTS) {
                    var centre = root.offset(0, -1, 8);
                    for (var cell : CoolingTowerStructure.cells(0)) level.setBlock(centre.offset(cell.offset()), cell.block().defaultBlockState(), 2);
                    var tower = (CoolingTowerBlockEntity) level.getBlockEntity(root);for (int n = 0; n < 200 && !tower.formed(); n++) tower.validateSlice();
                    if (!tower.formed()) throw new IllegalStateException("Seasonal tower fixture did not form");
                    tower.tank(0).fill(FluidForm.WARM_WATER.stack(16_000_000), EXECUTE);tower.process();tower.sendData();
                }
                if (net.createplant.thermal.SeasonalThermal.available()) level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level), "season set mid_winter");
                var player = level.getServer().getPlayerList().getPlayer(mc.player.getUUID());player.getAbilities().flying = true;player.onUpdateAbilities();player.connection.teleport(19, 90, -45, 0, -10);
                player.getInventory().setItem(0, CEItems.STEEL_INGOT.toStack());player.getInventory().setItem(1, CEItems.ALUMINUM_INGOT.toStack());player.getInventory().setItem(2, CEItems.NICKEL_INGOT.toStack());
                built = true;level.getServer().getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("SEASONAL_PLANT_FIXTURE_READY"), false);
            });
        }
    }
}
