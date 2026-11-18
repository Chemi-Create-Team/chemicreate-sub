package net.createplant.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.block.GasCentrifugeBlock;
import net.createplant.block.CubicPressBlock;
import net.createplant.block.entity.CubicPressBlockEntity;
import net.createplant.block.entity.GasCentrifugeBlockEntity;
import net.createplant.registry.CEHighStressMachines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in native GPU fixture; it is inactive in ordinary worlds. */
@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class HighStressClientCheck {
    private static int phase, ticks;
    private HighStressClientCheck() { }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("createplant.highStressClientCheck")) return;
        var mc = Minecraft.getInstance();if (mc.getOverlay() != null) return;
        if (phase == 3) { ClientQualityCheck.tick(event);return; }
        if (phase == 2 && com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_P)) {
            phase = 3;return;
        }
        if (phase == 0 && mc.screen instanceof TitleScreen && ++ticks > 60) {
            phase = 1;mc.options.pauseOnLostFocus = false;
            mc.createWorldOpenFlows().createFreshLevel("centrifuge-press-" + System.currentTimeMillis(),
                new net.minecraft.world.level.LevelSettings("Centrifuges and presses", net.minecraft.world.level.GameType.CREATIVE, false,
                    net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                new net.minecraft.world.level.levelgen.WorldOptions(42L, false, false),
                access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                    .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (phase == 1 && mc.player != null && mc.level != null) {
            phase = 2;mc.setScreen(null);mc.options.hideGui = true;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();var player = level.getServer().getPlayerList().getPlayer(mc.player.getUUID());
                level.setDayTime(6000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                for (int x = -3; x <= 27; x++) for (int z = -3; z <= 10; z++) level.setBlockAndUpdate(new BlockPos(x, 78, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int group = 0; group < 3; group++) {
                    int start = group == 0 ? 0 : group == 1 ? 5 : 13;int count = group == 0 ? 1 : group == 1 ? 4 : 8;
                    for (int i = 0; i < count; i++) {
                        var pos = new BlockPos(start + i % 4, 81, 3 + i / 4);
                        level.setBlock(pos, CEHighStressMachines.CENTRIFUGE.get().defaultBlockState(), 2);
                        level.setBlock(pos.above(), CEHighStressMachines.CENTRIFUGE.get().defaultBlockState().setValue(GasCentrifugeBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
                        level.setBlockAndUpdate(pos.below(), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
                        level.setBlockAndUpdate(pos.below(2), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
                        ((CreativeMotorBlockEntity) level.getBlockEntity(pos.below(2))).generatedSpeed.setValue(64);
                    }
                    GasCentrifugeBlockEntity.at(level, new BlockPos(start, 81, 3)).rebuild();
                }
                for (int i = 0; i < 2; i++) {
                    var pos = new BlockPos(21 + i * 4, 80, 3);
                    level.setBlockAndUpdate(pos.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, i == 0 ? BlazeBurnerBlock.HeatLevel.KINDLED : BlazeBurnerBlock.HeatLevel.SEETHING));
                    ((com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity) level.getBlockEntity(pos.below())).isCreative = true;
                    level.setBlockAndUpdate(pos, CEHighStressMachines.PRESS.get().defaultBlockState().setValue(CubicPressBlock.HORIZONTAL_AXIS, Direction.Axis.X));
                    level.setBlockAndUpdate(pos.east(), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                    level.setBlockAndUpdate(pos.east(2), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST));
                    ((CreativeMotorBlockEntity) level.getBlockEntity(pos.east(2))).generatedSpeed.setValue(64);
                    var press = (CubicPressBlockEntity) level.getBlockEntity(pos);
                    press.port().insertItem(0, new ItemStack(BuiltInRegistries.ITEM.get(CreatePlant.location("reactor_graphite")), 64), false);
                    press.port().insertItem(1, new ItemStack(BuiltInRegistries.ITEM.get(CreatePlant.location("nickel_ingot")), 64), false);
                }
                player.teleportTo(9, 86, 15);player.setYRot(180);player.setXRot(25);
                player.getAbilities().flying = true;player.onUpdateAbilities();
                level.getServer().getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("HIGH_STRESS_FIXTURE_READY"), false);
            });
        }
    }
}
